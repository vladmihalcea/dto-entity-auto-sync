# Telemetry of the 1,260 forks of the campaign in the order in which they ran: one point per fork and server.
# Top: mean busy clock (APERF/MPERF). Middle: hottest temperature. Bottom: mean package power (Intel RAPL),
# with the 80 W long-term limit (PL1). The vertical lines separate the six blocks.
NAME = 'telemetry'
W = 6.2
H = 4.5
load 'style.gp'
FILE = 'data/telemetry-forks.csv'
BLOCKS = 'data/telemetry-blocks.csv'

set multiplot layout 3,1 margins 0.085,0.99,0.1,0.97 spacing 0,0.055
set xrange [0:57.6]
set xtics 6
set format x ''
set pointintervalbox 0
unset key

# Top: the busy clock in GHz, with the names of the blocks above the points
set yrange [4.1:4.8]
set ytics 4.2,0.2,4.6
set ylabel 'Busy clock (GHz)' offset 0.5,0
plot BLOCKS using (column('start_hours')):(4.1):(0):(0.7) with vectors nohead lc rgb MUTED dt 3 lw 0.6 notitle, \
     BLOCKS using ((column('start_hours') + column('end_hours')) / 2):(4.74):(strcol('label')) with labels font ',9' textcolor rgb MUTED notitle, \
     FILE using (column('hours')):(column('app_mhz') / 1000) with points pt 7 ps 0.16 lc rgb BASE notitle, \
     FILE using (column('hours')):(column('db_mhz') / 1000) with points pt 5 ps 0.14 lc rgb ALT notitle

# Middle: the hottest temperature of the fork, with the legend in the empty corner
set key at graph 0.012, graph 0.95 left top reverse Left samplen 1 spacing 1.15
set yrange [30:100]
set ytics 40,20,100
set ylabel 'Temperature (°C)' offset 0.5,0
plot BLOCKS using (column('start_hours')):(30):(0):(70) with vectors nohead lc rgb MUTED dt 3 lw 0.6 notitle, \
     FILE using (column('hours')):(column('app_temp')) with points pt 7 ps 0.16 lc rgb BASE notitle, \
     FILE using (column('hours')):(column('db_temp')) with points pt 5 ps 0.14 lc rgb ALT notitle, \
     keyentry with points pt 7 ps 0.6 lc rgb BASE title 'application server', \
     keyentry with points pt 5 ps 0.55 lc rgb ALT title 'database server'

# Bottom: the mean package power, with the long-term power limit
unset key
set yrange [0:95]
set ytics 0,20,80
set ylabel 'Package power (W)' offset 0.5,0
set format x '%g'
set xlabel 'Hours since the start of the campaign'
set arrow 1 from 0, 80 to 57.6, 80 nohead lc rgb MUTED dt 2 lw 0.8
set label 1 'long-term power limit (80 W)' at 0.6, 87 left font ',9' textcolor rgb MUTED
plot BLOCKS using (column('start_hours')):(0):(0):(95) with vectors nohead lc rgb MUTED dt 3 lw 0.6 notitle, \
     FILE using (column('hours')):(column('app_power')) with points pt 7 ps 0.16 lc rgb BASE notitle, \
     FILE using (column('hours')):(column('db_power')) with points pt 5 ps 0.14 lc rgb ALT notitle
unset multiplot
