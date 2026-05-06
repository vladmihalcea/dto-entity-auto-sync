# Uncomment ONE of the following pairs to render to file:
# set terminal svg enhanced font "arial,11" size 900,550
# set output 'LoadPostWithVariableCommentsAndTagsFunction.svg'
#
# set terminal pngcairo enhanced font "arial,11" fontscale 1.0 size 900, 550
# set output 'LoadPostWithVariableCommentsAndTagsFunction.png'

set datafile separator ','

set key outside right top vertical Left noreverse noenhanced autotitle nobox offset 1,0 font ",9"
set rmargin 24

set title "Load Post With Variable Comments And Tags" font ",13"
set xlabel "Collection Size"
set ylabel "Average Response Time (ms)"
set yrange [ 0.0 : 5.5 ] noreverse writeback
set grid ytics lt 0 lw 0.5 lc rgb "#cccccc"

set style data linespoints
set pointsize 1.2

# JDBC  shades: light blue / dark blue
# jOOQ  shades: light green / dark green
# JPA   shades: light red / dark red
plot \
  'LoadPostWithVariableCommentsAndTagsFunction.csv' using 1:2 ti "JDBC-MQ-DTO"  lc rgb "#64B5F6" lw 2 pt 7, \
  ''                                                using 1:3 ti "JDBC-MS-JSON" lc rgb "#1565C0" lw 2 pt 7, \
  ''                                                using 1:4 ti "jOOQ-MQ-DTO"  lc rgb "#81C784" lw 2 pt 7, \
  ''                                                using 1:5 ti "jOOQ-MS-JSON" lc rgb "#2E7D32" lw 2 pt 7, \
  ''                                                using 1:6 ti "JPA-MQ-DTO"   lc rgb "#E57373" lw 2 pt 7, \
  ''                                                using 1:7 ti "JPA-MS-JSON"  lc rgb "#C62828" lw 2 pt 7
