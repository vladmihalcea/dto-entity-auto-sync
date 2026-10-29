#!/usr/bin/env sh
# Exports the MetricsTree package metrics of every quality-metrics module (or of the modules given as
# arguments, e.g. ./run-metricstree.sh write_jpa_diff_merge) into target/metricstree, and compares them
# with the ones in ../metrics/CodeQuality (compare-metricstree.py).
#
# Requires JDK 21 in JAVA_HOME (the JDK the module sources resolve against), Maven, and Python 3.
# The runner itself needs JDK 25, which Gradle takes from the installed JDKs or downloads.
# The first run downloads Gradle, the IntelliJ Platform test framework, MetricsTree, and
# IntelliJ IDEA; set IDEA_HOME to an installed IntelliJ IDEA 2026.2.4 to use it instead.
set -e

QM=$(cd "$(dirname "$0")" && pwd)
OUT="$QM/target/metricstree"
mkdir -p "$OUT"

if [ $# -gt 0 ]; then
    MODULES="$*"
else
    MODULES=$(cd "$QM" && ls -d read_* write_*)
fi

for M in $MODULES; do
    echo "=== $M"
    mvn -q -f "$QM/$M/pom.xml" dependency:copy-dependencies -DincludeScope=compile -DoutputDirectory=target/metricstree-deps
    set -- -p "$QM/metricstree-runner" test --console=plain \
        "-Pmetricstree.sources=$QM/$M/src/main/java" \
        "-Pmetricstree.dependencies=$QM/$M/target/metricstree-deps" \
        "-Pmetricstree.jdk=$JAVA_HOME" \
        "-Pmetricstree.output=$OUT/$M-package.csv"
    if [ -n "$IDEA_HOME" ]; then
        set -- "$@" "-PideaLocalPath=$IDEA_HOME"
    fi
    sh "$QM/metricstree-runner/gradlew" "$@"
done

python3 "$QM/compare-metricstree.py" "$OUT" "$QM/../metrics/CodeQuality"
