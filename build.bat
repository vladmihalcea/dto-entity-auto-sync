@echo off

mvn -Dskip.unit.tests=true clean install

goto:eof