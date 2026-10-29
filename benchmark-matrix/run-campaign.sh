#!/usr/bin/env sh
# Runs several blocks of benchmark cells one after another with the run-matrix driver on Linux, and continues
# where it stopped when started again, like run-campaign.ps1 on Windows. Run it on the application server,
# from the repository root after mvn -P benchmark-bundle package, or from the folder that holds the core and
# versioning bundles:
#
#   DB_HOST=<database server IP> TELEMETRY_AGENT_URL=http://<database server IP>:9500 \
#   JDBC_PASSWORD=<password> PGPASSWORD=<password> sh benchmark-matrix/run-campaign.sh
#
# Settings, all optional:
#   DB_HOST   the database server, localhost by default
#   BLOCKS    the blocks in their order, by default "rq2-writes rq1-rest typed-msjson w7-sf10 w7-concurrency":
#             the names of cell files in benchmark-matrix/cells, or the paths of any cell files
#   RESULTS   the results folder, results/<host>-postgresql by default, which records its database server
#             and refuses another one, so that a resumed campaign never mixes the forks of two servers
# Any arguments go to the driver, e.g., --until 07:30.
#
# At every start, it dumps the version and the settings of the PostgreSQL server into the results folder and checks
# the cells of every pending block. A block that stops with forks left (Ctrl+C, run-matrix stop, a deadline, or
# failures) ends the campaign window, so the next block never starts before the previous one is complete.

set -u

MATRIX=$(cd "$(dirname "$0")" && pwd)
DRIVER="$MATRIX/RunMatrix.java"
DB_HOST="${DB_HOST:-localhost}"
BLOCKS="${BLOCKS:-rq2-writes rq1-rest typed-msjson w7-sf10 w7-concurrency}"
RESULTS="${RESULTS:-results/$(hostname)-postgresql}"

mkdir -p "$RESULTS" || exit 1
MARKER="$RESULTS/database.txt"
if [ -f "$MARKER" ]; then
    recorded=$(cat "$MARKER")
    if [ "$recorded" != "POSTGRESQL on $DB_HOST" ]; then
        echo "The results in $RESULTS come from $recorded, not from PostgreSQL on $DB_HOST: choose another RESULTS folder."
        exit 1
    fi
else
    echo "POSTGRESQL on $DB_HOST" > "$MARKER"
fi

log() {
    line="$(date '+%Y-%m-%d %H:%M:%S')  $*"
    echo "$line"
    echo "$line" >> "$RESULTS/campaign.log"
}

cells_file() {
    case "$1" in
        *.txt) echo "$1" ;;
        *) echo "$MATRIX/cells/$1.txt" ;;
    esac
}

forks_left() {
    status=$(java "$DRIVER" status "$(cells_file "$1")" --results "$RESULTS" 2>&1)
    left=$(echo "$status" | grep -o '[0-9][0-9]* forks left' | head -1 | cut -d' ' -f1)
    if [ -z "$left" ]; then
        left=$(echo "$status" | grep -o 'has not started yet: [0-9][0-9]* cells, [0-9][0-9]* forks' | head -1 | sed 's/.* \([0-9][0-9]*\) forks$/\1/')
    fi
    if [ -z "$left" ]; then
        echo "Cannot read the status of block $1: $status" >&2
        exit 1
    fi
    echo "$left"
}

log "Campaign window started: blocks $BLOCKS, PostgreSQL on $DB_HOST, agent ${TELEMETRY_AGENT_URL:-none}, results in $RESULTS"
dump="$RESULTS/pg-settings-$(date +%Y%m%d-%H%M%S).txt"
if sh "$MATRIX/dump-pg-settings.sh" "$DB_HOST" "$dump" >/dev/null; then
    log "PostgreSQL settings: $dump"
else
    log "Cannot dump the PostgreSQL settings of $DB_HOST: the fork logs still record the server version and the key settings"
fi

for block in $BLOCKS; do
    left=$(forks_left "$block") || exit 1
    if [ "$left" -eq 0 ]; then
        continue
    fi
    if ! check=$(java "$DRIVER" check "$(cells_file "$block")" 2>&1); then
        log "Block $block does not match the bundles: $check"
        exit 1
    fi
done

for block in $BLOCKS; do
    left=$(forks_left "$block") || exit 1
    if [ "$left" -eq 0 ]; then
        log "Block $block is complete"
        continue
    fi
    log "Block $block: $left forks left"
    java "$DRIVER" run "$(cells_file "$block")" --results "$RESULTS" --db-host "$DB_HOST" "$@"
    exit_code=$?
    left=$(forks_left "$block") || exit 1
    if [ "$exit_code" -ne 0 ] || [ "$left" -gt 0 ]; then
        log "Campaign window ended in block $block (driver exit code $exit_code, $left forks left); start the same command again to continue"
        exit "$exit_code"
    fi
    log "Block $block is complete"
done
log "Campaign complete: every block is done"
