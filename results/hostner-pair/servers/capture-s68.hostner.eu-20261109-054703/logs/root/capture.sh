#!/bin/sh
# Captures the configuration and the logs of this server after the Hostner campaign, before the role swap.
# public/  can go into the replication repository: specs, JDK, PostgreSQL configuration and statistics, logs
# private/ stays on the PC: the full system journal (SSH logins and their source addresses) and the full
#          dmidecode output (serial numbers)
# Secrets are never copied: the passwords of /root/.benchmark-env are redacted, and autoinstall.yaml
# (password hash) and the TLS private key are skipped.
set -u
TS=$(date +%Y%m%d-%H%M%S)
H=$(hostname)
D=/root/capture-$H-$TS
P=$D/public
mkdir -p $P/specs $P/os $P/jdk $P/postgresql $P/logs $D/private
run() { echo "\$ $*"; "$@" 2>&1; echo; }

# Specs
{ run hostname; run uname -a; run cat /etc/os-release; run cat /proc/cmdline; run uptime; run date; } > $P/specs/host.txt
run lscpu > $P/specs/lscpu.txt
{ echo "microcode per CPU:"; grep microcode /proc/cpuinfo | sort | uniq -c; } > $P/specs/microcode.txt
dmidecode > $D/private/dmidecode-full.txt 2>&1
dmidecode -t bios -t system -t baseboard -t processor -t memory -t chassis 2>&1 \
    | grep -v -E 'Serial Number|UUID|Asset Tag|Part Number:.*[0-9A-F]{8}' > $P/specs/dmidecode.txt
{ run free -m; run cat /proc/meminfo; } > $P/specs/memory.txt
{ run lsblk -o NAME,SIZE,TYPE,FSTYPE,MOUNTPOINT,MODEL,ROTA,DISC-GRAN; run cat /sys/block/sda/queue/scheduler; run cat /sys/block/sda/queue/rotational; run df -h; run mount; } > $P/specs/disks.txt
{ run ip -br addr; run ip route; run ethtool eno1; run ethtool eno2; run ethtool -k eno1; } > $P/specs/network.txt
{
    for f in scaling_driver scaling_governor energy_performance_preference scaling_min_freq scaling_max_freq cpuinfo_min_freq cpuinfo_max_freq base_frequency; do
        echo "cpu0 $f: $(cat /sys/devices/system/cpu/cpu0/cpufreq/$f 2>/dev/null)"
    done
    echo "governors: $(cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor | sort | uniq -c | tr '\n' ' ')"
    echo "no_turbo: $(cat /sys/devices/system/cpu/intel_pstate/no_turbo 2>/dev/null)"
    echo "intel_pstate status: $(cat /sys/devices/system/cpu/intel_pstate/status 2>/dev/null)"
    echo "idle states of cpu0 (name, latency us, disabled):"
    for s in /sys/devices/system/cpu/cpu0/cpuidle/state*; do echo "  $(cat $s/name) $(cat $s/latency) $(cat $s/disable)"; done
    echo "disabled flags over all CPUs: $(cat /sys/devices/system/cpu/cpu*/cpuidle/state*/disable | sort | uniq -c | tr '\n' ' ')"
    for z in /sys/class/powercap/intel-rapl:*; do
        [ -d "$z" ] || continue
        echo "$z $(cat $z/name):"
        for c in $z/constraint_*_name; do n=${c%_name}; echo "  $(cat $c) power_limit_uw=$(cat ${n}_power_limit_uw 2>/dev/null) time_window_us=$(cat ${n}_time_window_us 2>/dev/null)"; done
    done
    echo "thermal throttle counters (cpu0):"
    for t in /sys/devices/system/cpu/cpu0/thermal_throttle/*; do echo "  $(basename $t): $(cat $t)"; done
    echo "sum of core_throttle_count over all CPUs: $(cat /sys/devices/system/cpu/cpu*/thermal_throttle/core_throttle_count | awk '{s+=$1} END {print s}')"
    echo "sum of package_throttle_count over all CPUs: $(cat /sys/devices/system/cpu/cpu*/thermal_throttle/package_throttle_count | awk '{s+=$1} END {print s}')"
} > $P/specs/cpu-power.txt 2>&1

# Operating system
sysctl -a > $P/os/sysctl.txt 2>/dev/null
dpkg -l > $P/os/packages.txt
{ run systemctl list-unit-files --no-pager; run systemctl list-units --all --no-pager; run systemctl list-timers --all --no-pager; } > $P/os/systemd.txt
run ufw status verbose > $P/os/firewall.txt
{ run chronyc -n tracking; run chronyc -n sources -v; run chronyc -n sourcestats; run timedatectl; for f in /etc/chrony/chrony.conf /etc/chrony/conf.d/* /etc/chrony/sources.d/*; do [ -f "$f" ] && run cat "$f"; done; } > $P/os/clock.txt
{ run cat /var/log/apt/history.log; run ls -la /var/log/apt; } > $P/os/apt-history.txt
[ -f /root/.benchmark-env ] && sed -E 's/(PASSWORD=).*/\1<redacted>/' /root/.benchmark-env > $P/os/benchmark-env-redacted.txt
if [ -d /root/dto2entity ]; then
    (cd /root/dto2entity && find . -path ./results -prune -o -type f -print | sort | xargs sha256sum) > $P/os/dto2entity-sha256.txt 2>/dev/null
fi

# JDK
for j in /opt/temurin-*/bin/java; do
    {
        run $j -version
        run $j -XshowSettings:vm -XshowSettings:system -version
        echo "== the options of the benchmark forks (RunMatrix adds -Djmh.separateClasspathJAR=true)"
        run $j -Xms4g -Xmx4g -XX:+AlwaysPreTouch -XX:+PrintCommandLineFlags -version
    } > $P/jdk/java-version-and-settings.txt
    $j -Xms4g -Xmx4g -XX:+AlwaysPreTouch -XX:+PrintFlagsFinal -version > $P/jdk/java-flags-final.txt 2>&1
    $j -XshowSettings:properties -version > $P/jdk/java-properties.txt 2>&1
done

# PostgreSQL: the configuration files of the cluster, without the TLS key
if [ -d /etc/postgresql ]; then
    cp -a /etc/postgresql $P/postgresql/etc
fi
if systemctl is-active --quiet postgresql; then
    q() { sudo -u postgres psql -X -P pager=off "$@" 2>&1; }
    {
        q -c "select version()"
        q -c "select now(), pg_postmaster_start_time(), pg_conf_load_time()"
        q -c "select datname, pg_database_size(datname) as bytes, pg_size_pretty(pg_database_size(datname)) from pg_database order by datname"
    } > $P/postgresql/server.txt
    q -A -F '|' -c "select name, setting, unit, source, sourcefile, sourceline, pending_restart from pg_settings order by name" > $P/postgresql/pg-settings-final.txt
    DATA=$(sudo -u postgres psql -X -A -t -c "show data_directory")
    /usr/lib/postgresql/18/bin/pg_controldata "$DATA" > $P/postgresql/pg-controldata.txt 2>&1
    {
        q -c "select * from pg_stat_checkpointer"
        q -c "select * from pg_stat_bgwriter"
        q -c "select * from pg_stat_wal"
        q -x -c "select * from pg_stat_database where datname like 'dto2entity%'"
        q -c "select backend_type, object, context, reads, writes, extends, hits, evictions, fsyncs from pg_stat_io where reads > 0 or writes > 0 or hits > 0 order by backend_type, object, context"
    } > $P/postgresql/statistics.txt
    for db in dto2entity dto2entity_versioning; do
        {
            q -d $db -c "select relname, n_live_tup, n_dead_tup, seq_scan, idx_scan, n_tup_ins, n_tup_upd, n_tup_del, last_vacuum, last_autovacuum, last_analyze, last_autoanalyze, vacuum_count, autovacuum_count, analyze_count, autoanalyze_count from pg_stat_user_tables order by relname"
            q -d $db -c "select c.relname, c.reloptions from pg_class c join pg_namespace n on n.oid = c.relnamespace where n.nspname = 'public' and c.reloptions is not null"
            q -d $db -c "select relname, pg_size_pretty(pg_total_relation_size(relid)) as total, pg_total_relation_size(relid) as bytes from pg_stat_user_tables order by bytes desc"
            q -d $db -c "select * from benchmark_dataset"
            q -d $db -c "select * from benchmark_module"
            q -d $db -c "select status, count(*), min(started_at), max(ended_at) from benchmark_run group by status order by status"
        } > $P/postgresql/database-$db.txt
        q -d $db -A -F '|' -c "select * from benchmark_run order by id" > $P/postgresql/benchmark-run-$db.txt
    done
fi

# Logs: the PostgreSQL logs, every log and helper file in /root (not autoinstall.yaml), the pilot and old records
cp -a /var/log/postgresql $P/logs/postgresql 2>/dev/null
mkdir -p $P/logs/root
for f in /root/*.log /root/*.txt /root/*.sh; do [ -f "$f" ] && cp -a "$f" $P/logs/root/; done
for d in /root/pilot /root/old-records; do [ -d "$d" ] && cp -a "$d" $P/logs/root/; done
journalctl -k -b --no-pager > $P/logs/kernel-since-boot.txt 2>&1
journalctl -k -b --no-pager | grep -i -E 'mce|machine check|thermal|throttl|hardware error|edac|watchdog|oom|segfault|i/o error|link is (up|down)' > $P/logs/kernel-warnings.txt 2>&1
journalctl -b --no-pager > $D/private/journal-since-boot.txt 2>&1
journalctl --list-boots --no-pager > $P/logs/boots.txt 2>&1

tar czf $D.tar.gz -C /root $(basename $D)
ls -la $D.tar.gz
du -sh $P $D/private
