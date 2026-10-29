#!/usr/bin/env sh
# Records the PostgreSQL version and every server setting that the benchmarks run with, such as
# shared_buffers, which the paper reports. It requires psql: run it on the database host, or on
# any machine with a PostgreSQL client, once per run window, and keep the file with the results
# of the window.
#
#   sh dump-pg-settings.sh [host] [output file]
#
# e.g.:
#
#   sh dump-pg-settings.sh 192.168.1.20 results/rq1-rest/pg-settings-20261105.txt
#
# The user and the password come from PGUSER and PGPASSWORD, postgres and admin by default.

HOST="${1:-localhost}"
OUT="${2:-pg-settings-$HOST-$(date +%Y%m%d-%H%M%S).txt}"
export PGUSER="${PGUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-admin}"

psql -X -h "$HOST" -d dto2entity \
    -c "SELECT version(), now() AS dumped_at" \
    -c "SELECT name, setting, unit, source FROM pg_settings ORDER BY name" > "$OUT" || exit 1
echo "Wrote $OUT"
