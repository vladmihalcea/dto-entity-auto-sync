#!/usr/bin/env sh
# Runs the JMH benchmarks of this bundle, which requires only a Java runtime.
#
# The database connection is configured with environment variables,
# e.g., for a PostgreSQL server running on another machine:
#
#   export JDBC_URL=jdbc:postgresql://192.168.1.20:5432/dto2entity
#   (the versioning bundle uses the dto2entity_versioning database)
#   export JDBC_USER=postgres
#   export JDBC_PASSWORD=admin
#
# The TelemetryProfiler reports the CPU frequency, utilization, performance limit, temperature, and thermal throttling
# of this host for every iteration and, if TELEMETRY_AGENT_URL points to the run-telemetry-agent
# script running on the database host, of the database host as well:
#
#   export TELEMETRY_AGENT_URL=http://192.168.1.20:9500
#
# Usage: sh run-benchmarks.sh <benchmark regex> [JMH options], e.g.:
#
#   sh run-benchmarks.sh "Benchmark.benchmark(MultiOps|DiffMerge)Sync" -f 5 -rf json -rff results.json
#   sh run-benchmarks.sh -l

if [ $# -eq 0 ]; then
    echo "Usage: $0 <benchmark regex> [JMH options]"
    echo "Run \"$0 -l\" to list the available benchmarks."
    exit 1
fi

cd "$(dirname "$0")" || exit 1
exec java -Xms4g -Xmx4g -XX:+AlwaysPreTouch -cp "classes:test-classes:lib/*" org.openjdk.jmh.Main -prof com.vladmihalcea.phd.common.telemetry.TelemetryProfiler -prof com.vladmihalcea.phd.common.timing.LayerProfiler "$@"
