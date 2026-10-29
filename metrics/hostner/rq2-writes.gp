# RQ2: Multi-Ops (MO) against Diff-Merge (DM) on each stack, over REST and at the service level.
# (a) Mean response time with the 95% two-level bootstrap CI of the mean over the ten forks of a cell.
# (b) Mean time per layer from the layer profiler; the service-level cells have no HTTP layers.
NAME = 'rq2-writes'
W = 6.2
H = 4.7
load 'style.gp'
FILE = 'data/rq2-writes.csv'

is(level, pattern) = (strcol('level') eq level && strcol('pattern') eq pattern) ? column('x') : NaN

set multiplot layout 2,1 margins 0.075,0.99,0.12,0.95 spacing 0,0.2
set xrange [-0.9:16.4]
set yrange [0:7]
set ytics 1
set boxwidth 0.86
set pointintervalbox 0
set xtics ('MO' 0, 'DM' 1, 'MO' 2.5, 'DM' 3.5, 'MO' 6, 'DM' 7, 'MO' 8.5, 'DM' 9.5, 'MO' 12, 'DM' 13, 'MO' 14.5, 'DM' 15.5) scale 0
do for [i = 0:2] {
    set label (10 + i) 'REST' at first (6 * i + 0.5), graph -0.15 center
    set label (20 + i) 'service level' at first (6 * i + 3), graph -0.15 center
}
set label 30 'JDBC' at first 1.75, graph -0.26 center font ',11'
set label 31 'jOOQ' at first 7.75, graph -0.26 center font ',11'
set label 32 'JPA' at first 13.75, graph -0.26 center font ',11'

set label 1 '(a) Mean response time, with the 95% confidence interval of the mean' at graph 0, graph 1.08 left
set ylabel 'Mean response time (ms)' offset 1,0
set key top left reverse Left samplen 1.2 spacing 1.1 maxrows 2 width 1
plot FILE using (is('rest', 'MO')):(column('mean')) with boxes lc rgb BASE title 'Multi-Ops, REST', \
     '' using (is('rest', 'DM')):(column('mean')) with boxes lc rgb ALT title 'Diff-Merge, REST', \
     '' using (is('service', 'MO')):(column('mean')) with boxes lc rgb BASE_LIGHT title 'Multi-Ops, service level', \
     '' using (is('service', 'DM')):(column('mean')) with boxes lc rgb ALT_LIGHT title 'Diff-Merge, service level', \
     '' using (column('x')):(column('mean')):(column('mean_lo')):(column('mean_hi')) with yerrorbars lc rgb INK lw 0.8 ps 0 notitle

set label 1 '(b) Mean time per layer'
set ylabel 'Mean time per edit (ms)' offset 1,0
set key top left reverse Left samplen 1.2 spacing 1.1 maxrows 3 width 1
plot FILE using 1:(sum5('sql', 'commit', 'service', 'http_server', 'http_client')) with boxes lc rgb L_CLIENT title 'HTTP transport and client', \
     '' using 1:(sum4('sql', 'commit', 'service', 'http_server')) with boxes lc rgb L_SERVER title 'HTTP server', \
     '' using 1:(sum3('sql', 'commit', 'service')) with boxes lc rgb L_SERVICE title 'service code', \
     '' using 1:(sum2('sql', 'commit')) with boxes lc rgb L_COMMIT title 'commits', \
     '' using 1:(column('sql')) with boxes lc rgb L_SQL title 'SQL statements'
unset multiplot
