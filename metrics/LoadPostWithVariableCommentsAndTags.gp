# Uncomment ONE of the following pairs to render to file:
# set terminal svg enhanced font "arial,11" size 1200,550
# set output 'LoadPostWithVariableCommentsAndTags.svg'
#
# set terminal pngcairo enhanced font "arial,11" fontscale 1.0 size 1200, 550
# set output 'LoadPostWithVariableCommentsAndTags.png'

set style fill solid 1.00 noborder
set datafile separator "\t"

set key outside right top vertical Left noreverse noenhanced autotitle nobox offset -1,0 font ",9"
set rmargin 22

set title "Load Post With Variable Comments And Tags" font ",13"
set xlabel "Collection Size"
set ylabel "Average Response Time (ms)"
set yrange [ 0.0 : 5.0 ] noreverse writeback
set grid ytics lt 0 lw 0.5 lc rgb "#cccccc"

# Bar layout: 6 bars + 1 bar-width gap between clusters = 7 units per cluster
bw = 1.0/7.0
o1 = -2.5 * bw
o2 = -1.5 * bw
o3 = -0.5 * bw
o4 =  0.5 * bw
o5 =  1.5 * bw
o6 =  2.5 * bw

set boxwidth bw absolute
set style data boxes

set xtics ("0" 0, "1" 1, "5" 2, "25" 3, "100" 4)
set xrange [-0.6:4.6]

# JDBC  shades: light blue / dark blue
# jOOQ  shades: light green / dark green
# JPA   shades: light red / dark red
plot \
  'LoadPostWithVariableCommentsAndTags.dat' every ::1 using ($0+o1):2 ti "JDBC-MQ-DTO"  lc rgb "#64B5F6", \
  ''                                        every ::1 using ($0+o2):3 ti "JDBC-MS-JSON" lc rgb "#1565C0", \
  ''                                        every ::1 using ($0+o3):4 ti "jOOQ-MQ-DTO"  lc rgb "#81C784", \
  ''                                        every ::1 using ($0+o4):5 ti "jOOQ-MS-JSON" lc rgb "#2E7D32", \
  ''                                        every ::1 using ($0+o5):6 ti "JPA-MQ-DTO"   lc rgb "#E57373", \
  ''                                        every ::1 using ($0+o6):7 ti "JPA-MS-JSON"  lc rgb "#C62828"
