# Uncomment ONE of the following pairs to render to file:
# set terminal svg enhanced font "arial,11" size 900,550
# set output 'LoadPostDTOForWriteWorkflow.svg'
#
# set terminal pngcairo enhanced font "arial,11" fontscale 1.0 size 900, 550
# set output 'LoadPostDTOForWriteWorkflow.png'

set style fill solid 1.00 noborder
set datafile separator "\t"

set key outside right top vertical Left noreverse noenhanced autotitle nobox offset -1,0 font ",9"
set rmargin 22

set title "Load Post DTO For Write Workflow" font ",13"
set ylabel "Average Response Time (ms)"
set yrange [ 0.0 : 3.5 ] noreverse writeback
set grid ytics lt 0 lw 0.5 lc rgb "#cccccc"

set boxwidth 0.8 absolute
set style data boxes

# 8 bars: JDBC group (0,1,2), gap, jOOQ group (3.5,4.5,5.5), gap, JPA group (7,8)
set xtics ( \
  "JDBC\nMQ-DTO" 0, \
  "JDBC\nMS-JSON-O" 1, \
  "JDBC\nMS-JSON-S" 2, \
  "jOOQ\nMQ-DTO" 3.5, \
  "jOOQ\nMS-JSON-O" 4.5, \
  "jOOQ\nMS-JSON-S" 5.5, \
  "JPA\nMQ-DTO" 7, \
  "JPA\nMS-JSON" 8 \
) font ",8"
set xrange [-0.8:8.8]

# JDBC  shades: light blue / medium blue / dark blue
# jOOQ  shades: light green / medium green / dark green
# JPA   shades: light red / dark red
plot \
  '-' using 1:2 ti "JDBC-MQ-DTO"     lc rgb "#64B5F6", \
  '-' using 1:2 ti "JDBC-MS-JSON-O"  lc rgb "#1E88E5", \
  '-' using 1:2 ti "JDBC-MS-JSON-S"  lc rgb "#1565C0", \
  '-' using 1:2 ti "jOOQ-MQ-DTO"     lc rgb "#81C784", \
  '-' using 1:2 ti "jOOQ-MS-JSON-O"  lc rgb "#43A047", \
  '-' using 1:2 ti "jOOQ-MS-JSON-S"  lc rgb "#2E7D32", \
  '-' using 1:2 ti "JPA-MQ-DTO"      lc rgb "#E57373", \
  '-' using 1:2 ti "JPA-MS-JSON"     lc rgb "#C62828"
0 1.954
e
1 1.551
e
2 1.463
e
3.5 2.509
e
4.5 2.042
e
5.5 1.928
e
7 2.019
e
8 2.1
e
