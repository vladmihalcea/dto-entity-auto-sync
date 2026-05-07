@echo off

pushd core
mvn test %*
popd