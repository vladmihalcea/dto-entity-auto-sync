#
# Bar chart of benchmarkDiffMergeSyncUsingRest across JDBC, jOOQ and JPA.
#
# Usage:
#   gnuplot diff-merge-sync-using-rest.gp
#
# Input:  diff-merge-sync-using-rest.csv (Technology,Score,Error,Units)
# Output: diff-merge-sync-using-rest.svg
#         diff-merge-sync-using-rest.pdf (only if pdfcairo terminal is available)

set encoding utf8
set datafile separator ","

set title "benchmarkDiffMergeSyncUsingRest — average time per operation (lower is better)"
set ylabel "Average time (ms/op)"
set xlabel "Technology"

set style data histograms
set style histogram errorbars gap 2 lw 1.5
set style fill solid 0.85 border -1
set boxwidth 0.6
set grid ytics
set yrange [0:*]
set key off

# SVG output
set terminal svg size 800,500 enhanced font 'Verdana,11' background rgb 'white'
set output 'diff-merge-sync-using-rest.svg'
plot 'diff-merge-sync-using-rest.csv' every ::1 using 2:3:xtic(1) \
        title 'Score (ms/op)' \
        lc rgb '#1f77b4', \
     '' every ::1 using ($0):($2 + $3 + 0.4):(sprintf("%.3f ± %.3f", $2, $3)) \
        with labels font 'Verdana,10' notitle

# PDF output (only emitted when pdfcairo is compiled into gnuplot)
if (strstrt(GPVAL_TERMINALS, "pdfcairo") > 0) {
    set terminal pdfcairo size 8in,5in enhanced font 'Verdana,11'
    set output 'diff-merge-sync-using-rest.pdf'
    replot
}

unset output
