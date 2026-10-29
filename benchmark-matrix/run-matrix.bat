@echo off
rem Runs a block of benchmark cells in randomized interleaved rounds, one JMH fork at a time,
rem and continues where it stopped when started again. It requires a JDK, since the Java
rem source launcher compiles RunMatrix.java on the fly.
rem
rem Run it from the folder that holds the core and versioning benchmark bundles,
rem or from the repository root after mvn -P benchmark-bundle package:
rem
rem   run-matrix.bat [run] <cells file> [--db-host <host>] [--agent <url>] [--until <HH:mm> | --hours <hours>]
rem   run-matrix.bat status <cells file>
rem   run-matrix.bat stop <cells file>
rem   run-matrix.bat check <cells file>
rem
rem e.g.:
rem
rem   run-matrix.bat cells\rq1-rest.txt --db-host 192.168.1.20 --agent http://192.168.1.20:9500 --until 07:30

java "%~dp0RunMatrix.java" %*
