@echo off

call build-without-tests
mvn test %*