# Uncomment ONE of the following pairs to render to file:
# set terminal svg enhanced font "arial,11" size 900,550
# set output 'UpdatePostWithCommentsAndTags.svg'
#
# set terminal pngcairo enhanced font "arial,11" fontscale 1.0 size 900, 550
# set output 'UpdatePostWithCommentsAndTags.png'

set style fill solid 1.00 noborder

unset key

set title "Update Post With Comments And Tags" font ",13"
set ylabel "Average Response Time (ms)"
set yrange [ 0.0 : 35.0 ] noreverse writeback
set grid ytics lt 0 lw 0.5 lc rgb "#cccccc"

set boxwidth 0.8 absolute
set style data boxes

set xtics ( \
  "JDBC\nMO" 0, \
  "JDBC\nDM" 1, \
  "jOOQ\nMO" 2.5, \
  "jOOQ\nDM" 3.5, \
  "JPA\nDM" 5 \
) font ",9" offset 0,0.5
set xrange [-0.8:5.8]

plot \
  'ubar1.dat' using 1:2 notitle lc rgb "#64B5F6", \
  'ubar2.dat' using 1:2 notitle lc rgb "#1565C0", \
  'ubar3.dat' using 1:2 notitle lc rgb "#81C784", \
  'ubar4.dat' using 1:2 notitle lc rgb "#2E7D32", \
  'ubar5.dat' using 1:2 notitle lc rgb "#C62828"
