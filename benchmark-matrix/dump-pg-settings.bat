@echo off
rem Records the PostgreSQL version and every server setting that the benchmarks run with, such as
rem shared_buffers, which the paper reports. It requires psql: run it on the database host from the
rem bin folder of PostgreSQL, or on any machine with a PostgreSQL client, once per run window,
rem and keep the file with the results of the window.
rem
rem   dump-pg-settings.bat [host] [output file]
rem
rem e.g.:
rem
rem   dump-pg-settings.bat 192.168.1.20 results\rq1-rest\pg-settings-20261105.txt
rem
rem The user and the password come from PGUSER and PGPASSWORD, postgres and admin by default.

setlocal
set HOST=%~1
if "%HOST%"=="" set HOST=localhost
set OUT=%~2
if "%OUT%"=="" set OUT=pg-settings-%HOST%.txt
if "%PGUSER%"=="" set PGUSER=postgres
if "%PGPASSWORD%"=="" set PGPASSWORD=admin

psql -X -h %HOST% -d dto2entity -c "SELECT version(), now() AS dumped_at" -c "SELECT name, setting, unit, source FROM pg_settings ORDER BY name" > "%OUT%"
if errorlevel 1 exit /b 1
echo Wrote %OUT%
