# Cost of optimistic locking: every write cell without (core) and with (versioning) the aggregate-root version,
# over REST and at the service level, for Multi-Ops (MO) and Diff-Merge (DM). Both sides ran in one block.
# The error bars show the 95% two-level bootstrap CI of the mean over the ten forks of a cell.
NAME = 'versioning'
W = 6.2
H = 2.6
load 'style.gp'
FILE = 'data/versioning.csv'

set lmargin at screen 0.075
set rmargin at screen 0.99
set bmargin at screen 0.24
set tmargin at screen 0.9
set xrange [-0.9:16.4]
set yrange [0:9]
set ytics 2
set boxwidth 0.4
set pointintervalbox 0
set xtics scale 0
set xtics ('MO' 0, 'DM' 1, 'MO' 2.5, 'DM' 3.5, 'MO' 6, 'DM' 7, 'MO' 8.5, 'DM' 9.5, 'MO' 12, 'DM' 13, 'MO' 14.5, 'DM' 15.5)
do for [i = 0:2] {
    set label (10 + i) 'REST' at first (6 * i + 0.5), graph -0.15 center
    set label (20 + i) 'service level' at first (6 * i + 3), graph -0.15 center
}
set label 30 'JDBC' at first 1.75, graph -0.27 center font ',11'
set label 31 'jOOQ' at first 7.75, graph -0.27 center font ',11'
set label 32 'JPA' at first 13.75, graph -0.27 center font ',11'
set ylabel 'Mean response time (ms)' offset 1,0
set key top left reverse Left samplen 1.2 spacing 1.1 maxrows 1 width 2
plot FILE using (column('x') - 0.21):(column('core')) with boxes lc rgb BASE_LIGHT title 'without version', \
     FILE using (column('x') + 0.21):(column('versioning')) with boxes lc rgb BASE title 'with the aggregate-root version', \
     FILE using (column('x') - 0.21):(column('core')):(column('core_lo')):(column('core_hi')) with yerrorbars lc rgb INK lw 0.8 ps 0 notitle, \
     FILE using (column('x') + 0.21):(column('versioning')):(column('versioning_lo')):(column('versioning_hi')) with yerrorbars lc rgb INK lw 0.8 ps 0 notitle
