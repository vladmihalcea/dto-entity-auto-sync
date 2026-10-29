@echo off
rem Exports the MetricsTree package metrics of every quality-metrics module (or of the modules given as
rem arguments, e.g. run-metricstree.bat write_jpa_diff_merge) into target\metricstree, and compares them
rem with the ones in ..\metrics\CodeQuality (compare-metricstree.py).
rem
rem Requires JDK 21 in JAVA_HOME (the JDK the module sources resolve against), Maven, and Python 3.
rem The runner itself needs JDK 25, which Gradle takes from the installed JDKs or downloads.
rem The first run downloads Gradle, the IntelliJ Platform test framework, MetricsTree, and
rem IntelliJ IDEA; set IDEA_HOME to an installed IntelliJ IDEA 2026.2.4 to use it instead.
setlocal enabledelayedexpansion

set "QM=%~dp0"
set "QM=%QM:~0,-1%"
set "OUT=%QM%\target\metricstree"
if not exist "%OUT%" mkdir "%OUT%"

set "IDEA_ARG="
rem The quotes are part of the value, since the path may contain spaces, and an unset IDEA_HOME adds no argument.
if defined IDEA_HOME set IDEA_ARG="-PideaLocalPath=%IDEA_HOME%"

set "MODULES=%*"
if "%MODULES%"=="" (
    for /d %%M in ("%QM%\read_*" "%QM%\write_*") do set "MODULES=!MODULES! %%~nxM"
)

for %%M in (%MODULES%) do (
    echo === %%M
    call mvn -q -f "%QM%\%%M\pom.xml" dependency:copy-dependencies -DincludeScope=compile -DoutputDirectory=target/metricstree-deps
    if errorlevel 1 exit /b 1
    call "%QM%\metricstree-runner\gradlew.bat" -p "%QM%\metricstree-runner" test --console=plain !IDEA_ARG! ^
        "-Pmetricstree.sources=%QM%\%%M\src\main\java" ^
        "-Pmetricstree.dependencies=%QM%\%%M\target\metricstree-deps" ^
        "-Pmetricstree.jdk=%JAVA_HOME%" ^
        "-Pmetricstree.output=%OUT%\%%M-package.csv"
    if errorlevel 1 exit /b 1
)

python "%QM%\compare-metricstree.py" "%OUT%" "%QM%\..\metrics\CodeQuality"
exit /b %errorlevel%
