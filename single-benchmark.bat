@echo off

if "%1" == "" goto usage

SET type=%1
call build
mvn -P benchmark-tests -Dit.test=%type%BenchmarkTest integration-test
goto:eof

:usage
echo Usage: %0 Jdbc^|jOOQ^|^Jpa

