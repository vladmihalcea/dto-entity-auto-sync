# Uncomment ONE of the following pairs to render to file:
# set terminal svg enhanced font "arial,11" size 1200,550
# set output 'LoadPostDTOForWriteWorkflow.svg'
#
# set terminal pngcairo enhanced font "arial,11" fontscale 1.0 size 1200, 550
# set output 'LoadPostDTOForWriteWorkflow.png'

set style fill solid 1.00 noborder

set key outside right top vertical Left noreverse noenhanced autotitle nobox offset 1,0 font ",9"
set rmargin 24
set lmargin 8
set bmargin 6

set title "Load Post DTO For Write Workflow" font ",13"
set ylabel "Average Response Time (ms)"
set yrange [ 0.0 : 3.5 ] noreverse writeback
set grid ytics lt 0 lw 0.5 lc rgb "#cccccc"

set boxwidth 0.8 absolute
set style data boxes

set xtics ( \
  "JDBC\nMQ\nDTO" 0, \
  "JDBC\nMS\nJSON-O" 1, \
  "JDBC\nMS\nJSON-S" 2, \
  "jOOQ\nMQ\nDTO" 3.5, \
  "jOOQ\nMS\nJSON-O" 4.5, \
  "jOOQ\nMS\nJSON-S" 5.5, \
  "JPA\nMQ\nDTO" 7, \
  "JPA\nMS\nJSON" 8 \
) font ",9"
set xrange [-0.8:8.8]

# JDBC  shades: light blue / medium blue / dark blue
# jOOQ  shades: light green / medium green / dark green
# JPA   shades: light red / dark red
plot \
  'bar1.dat' using 1:2 ti "JDBC-MQ-DTO"     lc rgb "#64B5F6", \
  'bar2.dat' using 1:2 ti "JDBC-MS-JSON-O"  lc rgb "#1E88E5", \
  'bar3.dat' using 1:2 ti "JDBC-MS-JSON-S"  lc rgb "#1565C0", \
  'bar4.dat' using 1:2 ti "jOOQ-MQ-DTO"     lc rgb "#81C784", \
  'bar5.dat' using 1:2 ti "jOOQ-MS-JSON-O"  lc rgb "#43A047", \
  'bar6.dat' using 1:2 ti "jOOQ-MS-JSON-S"  lc rgb "#2E7D32", \
  'bar7.dat' using 1:2 ti "JPA-MQ-DTO"      lc rgb "#E57373", \
  'bar8.dat' using 1:2 ti "JPA-MS-JSON"     lc rgb "#C62828"
