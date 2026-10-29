# RQ5, load: mean response time of the twelve headline cells with 1, 4, 8, and 16 benchmark threads.
# Top row: reads at fan-out 10. Bottom row: REST writes. The 1-thread points come from the RQ1 and RQ2 blocks.
# The error bars show the 95% two-level bootstrap CI of the mean over the ten forks of a cell.
NAME = 'rq5-threads'
W = 6.2
H = 4.1
load 'style.gp'
FILE = 'data/rq5-threads.csv'

array STACK[3] = ['JDBC', 'jOOQ', 'JPA']
array PREFIX[3] = ['jdbc', 'jooq', 'jpa']
series(prefix, cell, suffix) = column(prefix.'_'.cell.suffix)

set multiplot layout 2,3 margins 0.075,0.99,0.11,0.93 spacing 0.035,0.13
set xrange [0:17]
set xtics (1, 4, 8, 16)
do for [row = 1:2] {
    if (row == 1) {
        set yrange [0:2.2]
        set ytics 0.5
    } else {
        set yrange [0:9.5]
        set ytics 2
    }
    do for [i = 1:3] {
        if (row == 1) {
            set title STACK[i] font ',11' offset 0,-0.7
        } else {
            unset title
        }
        set format y '%g'
        if (i == 1) {
            set ylabel (row == 1 ? 'Read response time (ms)' : 'Write response time (ms)') offset 0.5,0
            set key top left reverse Left samplen 2.5 spacing 1.15
        } else {
            unset ylabel
            unset key
            set format y ''
        }
        if (row == 2 && i == 2) {
            set xlabel 'Benchmark threads'
        } else {
            unset xlabel
        }
        a = (row == 1 ? 'mqdto' : 'multiops')
        b = (row == 1 ? 'msjson' : 'diffmerge')
        plot FILE using 1:(series(PREFIX[i], a, '')):(series(PREFIX[i], a, '_lo')):(series(PREFIX[i], a, '_hi')) \
                with yerrorlines title (row == 1 ? 'MQ-DTO' : 'Multi-Ops') lc rgb BASE lw 1.6 pt 7 ps 0.55, \
             '' using 1:(series(PREFIX[i], b, '')):(series(PREFIX[i], b, '_lo')):(series(PREFIX[i], b, '_hi')) \
                with yerrorlines title (row == 1 ? 'MS-JSON' : 'Diff-Merge') lc rgb ALT lw 1.6 pt 5 ps 0.5
    }
}
unset multiplot
