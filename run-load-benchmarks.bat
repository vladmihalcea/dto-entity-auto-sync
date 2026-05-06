@echo off

call mvn -P benchmark-tests clean process-test-resources

call mvn -P benchmark-tests -Dit.test=JdbcLoadBenchmarkTest integration-test &if not errorlevel 1 ^
call mvn -P benchmark-tests -Dit.test=JooqLoadBenchmarkTest integration-test &if not errorlevel 1 ^ 
call mvn -P benchmark-tests -Dit.test=JpaLoadBenchmarkTest integration-test

