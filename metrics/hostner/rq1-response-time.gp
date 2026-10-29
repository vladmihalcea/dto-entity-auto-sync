# RQ1: mean response time of MQ-DTO and MS-JSON against the fan-out on each stack.
# The error bars show the 95% two-level bootstrap CI of the mean over the ten forks of a cell.
# The fan-out levels are equally spaced, so that the levels 0, 5, and 10 stay apart.
NAME = 'rq1-response-time'
W = 6.2
H = 2.3
load 'style.gp'
FILE = 'data/rq1-response-time.csv'

array STACK[3] = ['JDBC', 'jOOQ', 'JPA']
array PREFIX[3] = ['jdbc', 'jooq', 'jpa']

set multiplot layout 1,3 margins 0.075,0.99,0.2,0.88 spacing 0.035,0
set xrange [-0.4:4.4]
set yrange [0:4]
set ytics 1
do for [i = 1:3] {
    set title STACK[i] font ',11' offset 0,-0.7
    if (i == 1) {
        set ylabel 'Mean response time (ms)' offset 1,0
        set key top left reverse Left samplen 2.5 spacing 1.15
    } else {
        unset ylabel
        unset key
        set format y ''
    }
    if (i == 2) {
        set xlabel 'Fan-out (comments and tags of the post)'
    } else {
        unset xlabel
    }
    plot FILE using (column('position')):(column(PREFIX[i].'_mqdto')):(column(PREFIX[i].'_mqdto_lo')):(column(PREFIX[i].'_mqdto_hi')):xtic(2) \
            with yerrorlines title 'MQ-DTO' lc rgb BASE lw 1.6 pt 7 ps 0.55, \
         '' using (column('position')):(column(PREFIX[i].'_msjson')):(column(PREFIX[i].'_msjson_lo')):(column(PREFIX[i].'_msjson_hi')) \
            with yerrorlines title 'MS-JSON' lc rgb ALT lw 1.6 pt 5 ps 0.5
}
unset multiplot
