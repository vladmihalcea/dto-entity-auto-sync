#!/usr/bin/env sh
# Prepares a fresh Ubuntu 26.04 LTS server for the benchmark campaign. Each of the two servers runs
# the application in some windows and PostgreSQL in others (the role swap), and the database host
# also runs the telemetry agent, so every server gets both PostgreSQL and the JDK.
#
# Run it as root, once per server (running it again is harmless):
#
#   sh setup-server.sh 2>&1 | tee setup-server.log
#
# It changes only what the campaign requires:
#
#   1. It stops the background jobs that would otherwise run during a window: the automatic updates,
#      the other maintenance timers, snapd, and thermald, whose own power limits would lower the clock
#      without a kernel thermal throttling event, so the telemetry would not show them as throttling.
#   2. It installs PostgreSQL 18.6 from the Ubuntu archive, holds its version, applies the settings of
#      the Aiven service, so that the database settings do not differ between configurations, and
#      creates the two databases. PostgreSQL listens on localhost only: opening it to the other server
#      is a separate step.
#   3. It installs Temurin 21.0.4+7, the JDK of every campaign, after checking its SHA-256.
#   4. It sets the performance CPU governor now and at every boot, and installs sysstat for pidstat.
#   5. It writes the server record, with the hardware, kernel, governor, settings, and package
#      versions, so that the records of the two servers can be compared with diff.

set -eu

if [ "$(id -u)" -ne 0 ]; then
    echo "Run $0 as root"
    exit 1
fi

PG_MAJOR=18
PG_PACKAGE_VERSION=18.6-0ubuntu0.26.04.1
PG_PASSWORD="${PG_PASSWORD:-admin}"
TEMURIN_HOME=/opt/temurin-21.0.4+7
TEMURIN_TARBALL=OpenJDK21U-jdk_x64_linux_hotspot_21.0.4_7.tar.gz
TEMURIN_URL=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.4%2B7/$TEMURIN_TARBALL
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
    "postgresql-$PG_MAJOR=$PG_PACKAGE_VERSION" "postgresql-client-$PG_MAJOR=$PG_PACKAGE_VERSION" sysstat
apt-mark hold "postgresql-$PG_MAJOR" "postgresql-client-$PG_MAJOR"
# The packages may have enabled timers of their own, such as the sysstat ones
stop_background_jobs

postgres_sql <<EOF
ALTER USER postgres PASSWORD '$PG_PASSWORD';
ALTER SYSTEM SET synchronous_commit = off;
ALTER SYSTEM SET random_page_cost = 1;
ALTER SYSTEM SET shared_buffers = '189MB';
ALTER SYSTEM SET effective_cache_size = '568MB';
ALTER SYSTEM SET work_mem = '2MB';
ALTER SYSTEM SET maintenance_work_mem = '89MB';
ALTER SYSTEM SET log_autovacuum_min_duration = 0;
EOF
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

echo "== 4. Performance CPU governor"
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
systemctl daemon-reload
systemctl enable cpu-performance-governor.service
systemctl restart cpu-performance-governor.service

echo "== 5. Server record"
RECORD="/root/server-record-$(hostname)-$(date +%Y%m%d-%H%M%S).txt"
{
    echo "== host"
    hostname
    grep PRETTY_NAME /etc/os-release
    uname -r
    echo "== system"
    dmidecode -s system-manufacturer
    dmidecode -s system-product-name
    dmidecode -s bios-version
    printf 'virtualization: '
    systemd-detect-virt || true
    echo "== cpu"
    lscpu
    echo "== cpufreq"
    cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_driver
    printf 'governors: '
    cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor | sort | uniq -c
    printf 'energy performance preferences: '
    cat /sys/devices/system/cpu/cpu*/cpufreq/energy_performance_preference 2>/dev/null | sort | uniq -c || true
    printf 'no_turbo: '
    cat /sys/devices/system/cpu/intel_pstate/no_turbo
    ls /sys/devices/system/cpu/cpu0/thermal_throttle/
    cat /sys/class/hwmon/hwmon*/name
    echo "== memory"
    free -m
    echo "== disks"
    lsblk -o NAME,SIZE,TYPE,FSTYPE,MOUNTPOINT,MODEL
    echo "== network"
    ip -br addr
    for interface in /sys/class/net/*; do
        echo "$(basename "$interface") speed=$(cat "$interface/speed" 2>/dev/null || true) state=$(cat "$interface/operstate")"
    done
    echo "== java"
    java -version 2>&1
    echo "== postgresql"
    postgres_sql -tAc "SELECT version()"
    postgres_sql -tAc "SELECT name || ' = ' || current_setting(name) || ' (' || source || ')' FROM pg_settings WHERE source NOT IN ('default', 'override', 'client', 'session') ORDER BY name"
    echo "== background units"
    for unit in $BACKGROUND_UNITS; do
        echo "$unit $(systemctl is-enabled "$unit" 2>/dev/null || true) $(systemctl is-active "$unit" 2>/dev/null || true)"
    done
    echo "== packages"
    dpkg-query -W -f '${Package} ${Version}\n'
} > "$RECORD"
echo "Wrote $RECORD"
