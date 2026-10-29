#!/usr/bin/env sh
# Runs a block of benchmark cells in randomized interleaved rounds, one JMH fork at a time,
# and continues where it stopped when started again. It requires a JDK, since the Java
# source launcher compiles RunMatrix.java on the fly.
#
# Run it from the folder that holds the core and versioning benchmark bundles,
# or from the repository root after mvn -P benchmark-bundle package:
#
#   sh run-matrix.sh [run] <cells file> [--db-host <host>] [--agent <url>] [--until <HH:mm> | --hours <hours>]
#   sh run-matrix.sh status <cells file>
#   sh run-matrix.sh stop <cells file>
#   sh run-matrix.sh check <cells file>
#
# e.g.:
#
#   sh run-matrix.sh cells/rq1-rest.txt --db-host 192.168.1.20 --agent http://192.168.1.20:9500 --until 07:30

exec java "$(dirname "$0")/RunMatrix.java" "$@"
