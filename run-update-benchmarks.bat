@echo off

pushd core
call mvn -P benchmark-tests clean process-test-resources

call mvn -P benchmark-tests -Dit.test=JdbcUpdateBenchmarkTest integration-test &if not errorlevel 1 ^
call mvn -P benchmark-tests -Dit.test=JooqUpdateBenchmarkTest integration-test &if not errorlevel 1 ^ 
call mvn -P benchmark-tests -Dit.test=JpaUpdateBenchmarkTest integration-test
popd
