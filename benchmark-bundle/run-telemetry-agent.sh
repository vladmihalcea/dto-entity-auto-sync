#!/usr/bin/env sh
# Serves the CPU telemetry of this host, e.g., the database host, to the TelemetryProfiler
# of the benchmarks running on the application host, which requires a Java runtime only.
# Run it as root, which reads the APERF, MPERF, and RAPL counters of the CPU clock and the package power.
#
# On the application host, point the benchmarks to this agent before running them:
#
#   export TELEMETRY_AGENT_URL=http://<this-host-ip>:9500
#
# While the agent runs, pidstat (from the sysstat package) logs the CPU use of every process of this host
# every 10 s, to processes-<host>-<time>.txt next to this script, which shows whether anything else ran.
#
# Usage: sh run-telemetry-agent.sh [port]

cd "$(dirname "$0")" || exit 1
if command -v pidstat >/dev/null 2>&1; then
    log="processes-$(hostname)-$(date +%Y%m%d-%H%M%S).txt"
    pidstat -u -h -l 10 > "$log" &
    logger=$!
    trap 'kill $logger 2>/dev/null' EXIT INT TERM
    echo "Per-process CPU log: $(pwd)/$log"
else
    echo "No per-process CPU log: pidstat not found, install the sysstat package"
fi
java -Xmx64m -cp "test-classes:lib/*" com.vladmihalcea.phd.common.telemetry.TelemetryAgent "$@"
