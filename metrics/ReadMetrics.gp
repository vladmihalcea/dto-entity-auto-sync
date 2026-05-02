# set terminal pngcairo  transparent enhanced font "arial,10" fontscale 1.0 size 800, 500
# set output 'ReadMetrics.png'
set boxwidth 0.9 absolute
set style fill   solid 1.00 border lt -1
set key outside right top vertical Right noreverse noenhanced autotitle nobox offset -1,0
set style histogram clustered gap 1 title textcolor lt -1
set datafile missing '-'
set datafile separator ','
set style data histograms
set xtics border in scale 0,0 nomirror rotate by -45  autojustify
set xtics  norangelimit
set xtics   ()
set title "Read Metrics Benchmark\nPlot Min/Avg/Max as histogram of clustered boxes"
set trange [ * : * ] noreverse nowriteback
set urange [ * : * ] noreverse nowriteback
set vrange [ * : * ] noreverse nowriteback
set xrange [ * : * ] noreverse writeback
set x2range [ * : * ] noreverse writeback
set yrange [ 0.00000 : 4.0 ] noreverse writeback
set y2range [ * : * ] noreverse writeback
set zrange [ * : * ] noreverse writeback
set cbrange [ * : * ] noreverse writeback
set rrange [ * : * ] noreverse writeback
set colorbox vertical origin screen 0.9, 0.2 size screen 0.05, 0.6 front  noinvert bdefault
set ylabel "Time (ms)"
VERSION = "gnuplot version 6.0.3"
NO_ANIMATION = 1
## Last datafile plotted: "ReadMetrics.csv"
plot 'ReadMetrics.csv' using 2:xtic(1) ti col, '' u 3 ti col, '' u 4 ti col
