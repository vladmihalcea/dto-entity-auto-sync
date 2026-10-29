@echo off
rem Runs the JMH benchmarks of this bundle, which requires only a Java runtime.
rem
rem The database connection is configured with environment variables,
rem e.g., for a PostgreSQL server running on another machine:
rem
rem   set JDBC_URL=jdbc:postgresql://192.168.1.20:5432/dto2entity
rem   (the versioning bundle uses the dto2entity_versioning database)
rem   set JDBC_USER=postgres
rem   set JDBC_PASSWORD=admin
rem
rem The TelemetryProfiler reports the CPU frequency, utilization, performance limit, temperature, and thermal throttling
rem of this host for every iteration and, if TELEMETRY_AGENT_URL points to the run-telemetry-agent
rem script running on the database host, of the database host as well:
rem
rem   set TELEMETRY_AGENT_URL=http://192.168.1.20:9500
rem
rem Usage: run-benchmarks.bat <benchmark regex> [JMH options], e.g.:
rem
rem   run-benchmarks.bat "Benchmark.benchmark(MultiOps|DiffMerge)Sync" -f 5 -rf json -rff results.json
rem   run-benchmarks.bat -l

if "%~1" == "" (
    echo Usage: %~nx0 ^<benchmark regex^> [JMH options]
    echo Run "%~nx0 -l" to list the available benchmarks.
    exit /b 1
)

pushd "%~dp0"
java -Xms4g -Xmx4g -XX:+AlwaysPreTouch -Djmh.separateClasspathJAR=true -cp "classes;test-classes;lib/*" org.openjdk.jmh.Main -prof com.vladmihalcea.phd.common.telemetry.TelemetryProfiler -prof com.vladmihalcea.phd.common.timing.LayerProfiler %*
popd
