@echo off
rem Serves the CPU telemetry of this host, e.g., the database host, to the TelemetryProfiler
rem of the benchmarks running on the application host, which requires a Java runtime only.
rem
rem On the application host, point the benchmarks to this agent before running them:
rem
rem   set TELEMETRY_AGENT_URL=http://<this-host-ip>:9500
rem
rem Usage: run-telemetry-agent.bat [port]

pushd "%~dp0"
java -Xmx64m -cp "test-classes;lib/*" com.vladmihalcea.phd.common.telemetry.TelemetryAgent %*
popd
