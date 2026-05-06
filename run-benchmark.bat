@echo off

if "%1" == "" goto usage

SET type=%1
call mvn -P benchmark-tests clean process-test-resources
mvn -P benchmark-tests -Dit.test=%type%BenchmarkTest integration-test
goto:eof

:usage
echo Usage: %0 Jdbc^|jOOQ^|^Jpa|^JpaLoad|^JdbcLoad|^JooqLoad|^JpaUpdate|^JdbcUpdate|^JooqUpdate

