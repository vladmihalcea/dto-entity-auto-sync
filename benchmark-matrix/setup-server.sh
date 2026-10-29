#!/usr/bin/env sh
# Prepares a fresh Ubuntu 26.04 LTS server for the benchmark campaign. Each of the two servers runs
# the application in some windows and PostgreSQL in others (the role swap), and the database host
# also runs the telemetry agent, so every server gets both PostgreSQL and the JDK.
#
# Run it as root on both servers (running it again is harmless, and it switches the role):
#
#   ROLE=db  PEER_IP=<application server IP> PG_PASSWORD=<password> NTP_SERVER=<NTP server> sh setup-server.sh 2>&1 | tee setup-server.log
#   ROLE=app PEER_IP=<database server IP>    PG_PASSWORD=<password> NTP_SERVER=<NTP server> sh setup-server.sh 2>&1 | tee setup-server.log
#
# ROLE is db (the default) or app: the database server runs PostgreSQL, and the application server stops it,
# so that an idle PostgreSQL does not run next to the benchmarks. PEER_IP is the address of the other server,
# preferably on the private network between the two, to which the firewall opens PostgreSQL (5432) and the
# telemetry agent (9500); without it, PostgreSQL listens on localhost only. PG_PASSWORD is the password of the
# postgres user, which the benchmarks take from JDBC_PASSWORD and dump-pg-settings.sh from PGPASSWORD on the
# application server; it is required with PEER_IP, since the servers are on the public internet. NTP_SERVER is
# an NTP server that the network lets through, ntp.hostner.com on the Hostner network, which blocks the public
# NTP servers; it is optional.
#
# It changes only what the campaign requires:
#
#   1. It stops the background jobs that would otherwise run during a window: the automatic updates,
#      the other maintenance timers, snapd, and thermald, whose own power limits would lower the clock
#      without a kernel thermal throttling event, so the telemetry would not show them as throttling.
#   2. It installs PostgreSQL 18.6 from the Ubuntu archive, holds its version, sizes its memory for the
#      32 GB servers, keeps the durable default synchronous_commit = on, and creates the two databases.
#   3. It installs Temurin 21.0.4+7, the JDK of every campaign, after checking its SHA-256.
#   4. It sets the performance CPU governor, limits the CPU idle states to C1, and loads the msr module,
#      now and at every boot, and installs sysstat for pidstat.
#   5. It synchronizes the clock with chrony, from NTP_SERVER if set, and waits for the synchronization,
#      or, when no NTP source is reachable, sets the clock once from an HTTPS server and stops chrony,
#      so that the clock runs free during the campaign.
#   6. It enables the firewall: SSH from anywhere, and PostgreSQL and the telemetry agent from PEER_IP only.
#   7. It writes the server record, with the hardware, kernel, power settings, telemetry sources, settings,
#      and package versions, so that the records of the two servers can be compared with diff,
#      and starts or stops PostgreSQL for the ROLE of the server.

set -eu

if [ "$(id -u)" -ne 0 ]; then
    echo "Run $0 as root"
    exit 1
fi

ROLE="${ROLE:-db}"
PEER_IP="${PEER_IP:-}"
NTP_SERVER="${NTP_SERVER:-}"
case "$ROLE" in
    db | app) ;;
    *) echo "ROLE must be db or app, not $ROLE"; exit 1 ;;
esac
if [ -n "$PEER_IP" ] && [ -z "${PG_PASSWORD:-}" ]; then
    echo "Set PG_PASSWORD: with PEER_IP, PostgreSQL accepts connections from the other server"
    exit 1
fi

PG_MAJOR=18
PG_PACKAGE_VERSION=18.6-0ubuntu0.26.04.1
PG_PASSWORD="${PG_PASSWORD:-admin}"
PG_HBA="/etc/postgresql/$PG_MAJOR/main/pg_hba.conf"
TEMURIN_HOME=/opt/temurin-21.0.4+7
TEMURIN_TARBALL=OpenJDK21U-jdk_x64_linux_hotspot_21.0.4_7.tar.gz
TEMURIN_URL=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.4%2B7/$TEMURIN_TARBALL
# The deepest idle state that the CPUs may enter, by its exit latency: C1 (2 us on the Xeon E-2276G),
# as the latency-performance profile of tuned does, instead of C10 (890 us), so that a core that waits
# for the next statement or response does not add a wake-up latency of the order of the network round trip
MAX_IDLE_LATENCY_US=2
BACKGROUND_UNITS="unattended-upgrades.service apt-daily.timer apt-daily-upgrade.timer
    update-notifier-download.timer update-notifier-motd.timer motd-news.timer fwupd-refresh.timer
    man-db.timer dpkg-db-backup.timer e2scrub_all.timer xfs_scrub_all.timer fstrim.timer
    apport-autoreport.timer ua-timer.timer sysstat-collect.timer sysstat-summary.timer sysstat-rotate.timer
    snapd.snap-repair.timer snapd.socket snapd.service thermald.service"

stop_background_jobs() {
    systemctl stop apt-daily.service apt-daily-upgrade.service 2>/dev/null || true
    for unit in $BACKGROUND_UNITS; do
        if systemctl cat "$unit" >/dev/null 2>&1; then
            systemctl disable --now "$unit" 2>/dev/null || true
        fi
    done
    # The automatic updates are also masked, so that no package upgrade can enable them again,
    # and switched off in the APT configuration, which unattended-upgrades reads
    for unit in unattended-upgrades.service apt-daily.timer apt-daily-upgrade.timer apt-daily.service apt-daily-upgrade.service; do
        systemctl mask "$unit" >/dev/null 2>&1 || true
    done
    cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "0";
APT::Periodic::Unattended-Upgrade "0";
EOF
}

postgres_sql() {
    (cd /tmp && runuser -u postgres -- psql -X -q -v ON_ERROR_STOP=1 "$@")
}

echo "== 1. Background jobs"
stop_background_jobs

echo "== 2. PostgreSQL $PG_PACKAGE_VERSION"
export DEBIAN_FRONTEND=noninteractive
apt-get -y -q -o DPkg::Lock::Timeout=600 update
apt-get -y -q -o DPkg::Lock::Timeout=600 install \
    "postgresql-$PG_MAJOR=$PG_PACKAGE_VERSION" "postgresql-client-$PG_MAJOR=$PG_PACKAGE_VERSION" sysstat ufw
apt-mark hold "postgresql-$PG_MAJOR" "postgresql-client-$PG_MAJOR"
# The packages may have enabled timers of their own, such as the sysstat ones
stop_background_jobs

systemctl start postgresql
until runuser -u postgres -- pg_isready -q; do
    sleep 1
done
# Sized for the 32 GB servers, whose data sets fit in memory at SF1 and SF10:
#   shared_buffers        25% of the memory, the starting value of the PostgreSQL documentation
#   effective_cache_size  75% of the memory, the shared buffers plus the page cache of the rest
#   work_mem              enough for the sorts and hashes of the JSON aggregation in memory
#   maintenance_work_mem  the index builds after the data set load and the vacuum between iterations
#   random_page_cost      SSD storage, instead of the 4 of spinning disks
#   max_wal_size          the WAL of a load or of an hour of edits never forces a checkpoint
#   checkpoint_timeout    a timed checkpoint every 30 minutes instead of every 5; log_checkpoints, on by default,
#                         records them, and log_autovacuum_min_duration records every autovacuum
# synchronous_commit keeps its default, on, so every commit waits for its WAL flush, as in production.
postgres_sql <<EOF
ALTER USER postgres PASSWORD '$PG_PASSWORD';
ALTER SYSTEM RESET synchronous_commit;
ALTER SYSTEM SET shared_buffers = '8GB';
ALTER SYSTEM SET effective_cache_size = '24GB';
ALTER SYSTEM SET work_mem = '16MB';
ALTER SYSTEM SET maintenance_work_mem = '1GB';
ALTER SYSTEM SET random_page_cost = 1.1;
ALTER SYSTEM SET max_wal_size = '16GB';
ALTER SYSTEM SET checkpoint_timeout = '30min';
ALTER SYSTEM SET log_autovacuum_min_duration = 0;
EOF
if [ -n "$PEER_IP" ]; then
    postgres_sql -c "ALTER SYSTEM SET listen_addresses = '*'"
else
    postgres_sql -c "ALTER SYSTEM RESET listen_addresses"
fi
# The other server may connect to the two databases only; the firewall admits no other host
sed -i '/# benchmark-peer$/d' "$PG_HBA"
if [ -n "$PEER_IP" ]; then
    echo "host    dto2entity,dto2entity_versioning    postgres    $PEER_IP/32    scram-sha-256    # benchmark-peer" >> "$PG_HBA"
fi
systemctl restart postgresql
until runuser -u postgres -- pg_isready -q; do
    sleep 1
done
for database in dto2entity dto2entity_versioning; do
    if [ -z "$(postgres_sql -tAc "SELECT 1 FROM pg_database WHERE datname = '$database'")" ]; then
        (cd /tmp && runuser -u postgres -- createdb "$database")
    fi
done

echo "== 3. Temurin 21.0.4+7"
if [ ! -x "$TEMURIN_HOME/bin/java" ]; then
    download=$(mktemp -d)
    curl -fsSL -o "$download/$TEMURIN_TARBALL" "$TEMURIN_URL"
    curl -fsSL -o "$download/$TEMURIN_TARBALL.sha256.txt" "$TEMURIN_URL.sha256.txt"
    (cd "$download" && sha256sum -c "$TEMURIN_TARBALL.sha256.txt")
    mkdir -p "$TEMURIN_HOME"
    tar -xzf "$download/$TEMURIN_TARBALL" -C "$TEMURIN_HOME" --strip-components=1
    rm -rf "$download"
fi
ln -sf "$TEMURIN_HOME/bin/java" /usr/local/bin/java
cat > /etc/profile.d/temurin.sh <<EOF
export JAVA_HOME=$TEMURIN_HOME
export PATH=\$JAVA_HOME/bin:\$PATH
EOF

echo "== 4. CPU power settings and telemetry sources"
cat > /etc/systemd/system/cpu-performance-governor.service <<'EOF'
[Unit]
Description=Performance CPU governor for the benchmark campaign

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/bin/sh -c 'for governor in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > "$$governor"; done'

[Install]
WantedBy=multi-user.target
EOF
cat > /etc/systemd/system/cpu-idle-limit.service <<EOF
[Unit]
Description=CPU idle states limited to an exit latency of $MAX_IDLE_LATENCY_US us for the benchmark campaign

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/bin/sh -c 'for state in /sys/devices/system/cpu/cpu*/cpuidle/state*; do if [ "\$\$(cat "\$\$state/latency")" -gt $MAX_IDLE_LATENCY_US ]; then echo 1 > "\$\$state/disable"; else echo 0 > "\$\$state/disable"; fi; done'

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
for unit in cpu-performance-governor.service cpu-idle-limit.service; do
    systemctl enable "$unit"
    systemctl restart "$unit"
done
# The telemetry reads the clock of the running cores from the APERF and MPERF registers through the msr driver
modprobe msr
echo msr > /etc/modules-load.d/msr.conf

echo "== 5. Clock"
# JMH times with the monotonic clock, whose rate an NTP daemon adjusts while it corrects the time, and the logs,
# the run windows (--until), and the records of the two servers use the wall clock. The Hostner network lets
# neither NTP nor NTS through to the public servers (s66, 5 Nov: chrony never reached a source, and the clock ran
# 2 hours fast), but Hostner runs ntp.hostner.com, which NTP_SERVER adds to the sources of chrony. Once chrony is
# synchronized, it adjusts the rate of the monotonic clock by a few parts per million, far below the run-to-run
# variation of the benchmarks. The setup waits for the synchronization and steps the clock by whatever offset is
# left, so that no large correction happens during the campaign. Without a reachable source, the clock is set once
# from the Date header of an HTTPS server, accurate to a second, and chrony is stopped, so that the clock runs free
# during the campaign and no late correction changes its rate.
if [ -n "$NTP_SERVER" ]; then
    mkdir -p /etc/chrony/sources.d /etc/chrony/conf.d
    echo "server $NTP_SERVER iburst" > /etc/chrony/sources.d/benchmark.sources
    # Ubuntu configures NTS sources, and in the default authselectmode (mix) chrony does not select an
    # unauthenticated source such as NTP_SERVER while none of them is reachable, so it would never synchronize
    # (s66, 6 Nov: ntp.hostner.com answered, at a 4 ms error bound, but stayed unselected)
    echo "authselectmode ignore" > /etc/chrony/conf.d/benchmark.conf
    systemctl enable chrony >/dev/null 2>&1 || true
    # A restart applies the makestep directive of chrony.conf, which steps a clock that is far off
    systemctl restart chrony 2>/dev/null || true
fi
if chronyc waitsync 30 0 0 1 >/dev/null 2>&1; then
    chronyc makestep >/dev/null 2>&1 || true
    echo "The clock is synchronized by chrony: $(chronyc -n tracking | grep -E 'Reference ID|System time' | tr -s ' ' | tr '\n' ';')"
else
    if [ -n "$NTP_SERVER" ]; then
        echo "chrony did not synchronize with $NTP_SERVER within 30 seconds"
    fi
    date_header=$(curl -fsSI --max-time 10 https://www.google.com | tr -d '\r' | sed -n 's/^[Dd]ate: //p')
    if [ -n "$date_header" ]; then
        systemctl disable --now chrony 2>/dev/null || true
        date -s "$date_header" >/dev/null
        hwclock --systohc 2>/dev/null || true
        echo "No NTP source is reachable: the clock was set from the Date header of www.google.com to $(date -u), and chrony is off"
    else
        echo "No NTP source is reachable, and the Date header of www.google.com could not be read: set the clock by hand"
    fi
fi

echo "== 6. Firewall"
ufw --force reset >/dev/null
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
if [ -n "$PEER_IP" ]; then
    ufw allow from "$PEER_IP" to any port 5432 proto tcp
    ufw allow from "$PEER_IP" to any port 9500 proto tcp
fi
# Logging the packets of internet scans would add disk writes during the windows
ufw logging off
ufw --force enable

echo "== 7. Server record"
RECORD="/root/server-record-$(hostname)-$(date +%Y%m%d-%H%M%S).txt"
{
    echo "== host"
    hostname
    grep PRETTY_NAME /etc/os-release
    uname -r
    cat /proc/cmdline
    echo "role: $ROLE, peer: ${PEER_IP:-none}"
    echo "== system"
    dmidecode -s system-manufacturer
    dmidecode -s system-product-name
    dmidecode -s bios-version
    printf 'virtualization: '
    systemd-detect-virt || true
    echo "== cpu"
    lscpu
    echo "== cpu vulnerabilities and mitigations"
    grep -r . /sys/devices/system/cpu/vulnerabilities/ | sed 's|.*/||'
    echo "== cpufreq"
    cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_driver
    printf 'base frequency (kHz): '
    cat /sys/devices/system/cpu/cpu0/cpufreq/base_frequency 2>/dev/null || echo unknown
    printf 'governors: '
    cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor | sort | uniq -c
    printf 'energy performance preferences: '
    cat /sys/devices/system/cpu/cpu*/cpufreq/energy_performance_preference 2>/dev/null | sort | uniq -c || true
    printf 'no_turbo: '
    cat /sys/devices/system/cpu/intel_pstate/no_turbo
    echo "== idle states (cpu0: name, exit latency in us, disabled)"
    for state in /sys/devices/system/cpu/cpu0/cpuidle/state*; do
        echo "$(cat "$state/name") $(cat "$state/latency") $(cat "$state/disable")"
    done
    printf 'disabled idle states over all CPUs: '
    cat /sys/devices/system/cpu/cpu*/cpuidle/state*/disable | sort | uniq -c | tr '\n' ' '
    echo
    echo "== telemetry sources"
    ls /sys/devices/system/cpu/cpu0/thermal_throttle/
    cat /sys/class/hwmon/hwmon*/name
    printf 'msr device: '
    ls /dev/cpu/0/msr
    for zone in /sys/class/powercap/intel-rapl:*; do
        if [ -f "$zone/constraint_0_power_limit_uw" ]; then
            echo "$(basename "$zone") $(cat "$zone/name"): $(cat "$zone/constraint_0_name") $(cat "$zone/constraint_0_power_limit_uw") uW, $(cat "$zone/constraint_1_name" 2>/dev/null) $(cat "$zone/constraint_1_power_limit_uw" 2>/dev/null) uW"
        fi
    done
    echo "== memory"
    free -m
    echo "== disks"
    lsblk -o NAME,SIZE,TYPE,FSTYPE,MOUNTPOINT,MODEL
    echo "== network"
    ip -br addr
    for interface in /sys/class/net/*; do
        echo "$(basename "$interface") speed=$(cat "$interface/speed" 2>/dev/null || true) state=$(cat "$interface/operstate")"
    done
    echo "== clock"
    timedatectl | grep -E "Universal time|synchronized|NTP service"
    printf 'chrony: '
    systemctl is-active chrony 2>/dev/null || true
    if systemctl is-active --quiet chrony 2>/dev/null; then
        chronyc -n tracking | grep -E "Reference ID|Stratum|System time|Frequency|Skew" || true
    fi
    echo "== firewall"
    ufw status verbose
    echo "== java"
    java -version 2>&1
    echo "== postgresql"
    postgres_sql -tAc "SELECT version()"
    postgres_sql -tAc "SELECT name || ' = ' || current_setting(name) || ' (' || source || ')' FROM pg_settings WHERE source NOT IN ('default', 'override', 'client', 'session') ORDER BY name"
    grep -v '^\s*#' "$PG_HBA" | grep .
    echo "== background units"
    for unit in $BACKGROUND_UNITS; do
        echo "$unit $(systemctl is-enabled "$unit" 2>/dev/null || true) $(systemctl is-active "$unit" 2>/dev/null || true)"
    done
    echo "== packages"
    dpkg-query -W -f '${Package} ${Version}\n'
} > "$RECORD"

if [ "$ROLE" = db ]; then
    systemctl enable --now postgresql
else
    systemctl disable --now postgresql
fi
echo "postgresql: $(systemctl is-enabled postgresql 2>/dev/null || true) $(systemctl is-active postgresql 2>/dev/null || true)" >> "$RECORD"
echo "Wrote $RECORD"
