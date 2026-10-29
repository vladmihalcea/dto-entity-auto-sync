#!/bin/sh
# Builds the chart data from the raw results and renders every chart of the Hostner campaign to PDF.
# Needs Python 3, a JDK (for analysis/Stats.java), and Gnuplot 5.4 or later with the pdfcairo terminal.
#
#   sh metrics/hostner/make-charts.sh [folder to copy the PDF files to, e.g., the paper's images folder]
#
# The data files are rebuilt only where missing: delete metrics/hostner/data (and results/<name>/analysis)
# to build everything again from the raw results.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
python3 "$HERE/make-chart-data.py"
cd "$HERE"
for chart in rq1-response-time rq1-layers rq2-writes rq5-threads typed-msjson versioning robustness telemetry; do
    gnuplot "$chart.gp"
    echo "rendered $chart.pdf"
done
if [ $# -gt 0 ]; then
    for chart in rq1-response-time rq1-layers rq2-writes rq5-threads typed-msjson versioning robustness telemetry; do
        cp "$chart.pdf" "$1/hostner-$chart.pdf"
    done
    echo "copied to $1"
fi
