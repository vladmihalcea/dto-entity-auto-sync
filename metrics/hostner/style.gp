# Shared style of the charts of the Hostner campaign.
# A chart sets NAME and its size in inches, W and H, and then loads this file; it writes NAME.pdf.
# Run the charts from this folder, e.g.: gnuplot rq1-response-time.gp
set datafile separator ','
set datafile columnheaders
set encoding utf8

# A color-vision-safe categorical palette. In every chart, the baseline pattern (MQ-DTO, Multi-Ops) is blue
# and the pattern under study (MS-JSON, Diff-Merge) is orange, with a second encoding next to the color
# (marker shape, bar label, or position). The five layers of an operation have their own colors.
BASE = '#2a78d6'
ALT = '#eb6834'
BASE_LIGHT = '#a9c9ef'
ALT_LIGHT = '#f7c2ad'
L_SQL = '#1baf7a'
L_COMMIT = '#eda100'
L_SERVICE = '#e87ba4'
L_SERVER = '#008300'
L_CLIENT = '#6250d6'
INK = '#0b0b0b'
MUTED = '#52514e'
GRID = '#d9d8d4'

set terminal pdfcairo enhanced font 'Arial,10.5' size W,H
set output NAME.'.pdf'

set border 3 lc rgb MUTED lw 0.8
set tics nomirror out scale 0.6 textcolor rgb INK
set grid ytics lt 1 lw 0.5 lc rgb GRID
set key textcolor rgb INK
set style fill solid 1.0 border rgb 'white'

# The stacked layers of an operation, from the bottom up: SQL statements, commit, the rest of the service method,
# the HTTP server outside the service, and the HTTP transport with the client
sum2(a, b) = column(a) + column(b)
sum3(a, b, c) = column(a) + column(b) + column(c)
sum4(a, b, c, d) = column(a) + column(b) + column(c) + column(d)
sum5(a, b, c, d, e) = column(a) + column(b) + column(c) + column(d) + column(e)
