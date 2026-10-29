# RQ1: where the time of a read goes, at fan-outs 0, 10, and 100, for MQ-DTO (MQ) and MS-JSON (MS) on each stack.
# Mean time per layer from the layer profiler; the layers add up to the response time that the client measures.
NAME = 'rq1-layers'
W = 6.2
H = 2.55
load 'style.gp'
FILE = 'data/rq1-layers.csv'

array STACK[3] = ['JDBC', 'jOOQ', 'JPA']

set multiplot layout 1,3 margins 0.075,0.99,0.2,0.89 spacing 0.035,0
set xrange [-0.8:7.8]
set yrange [0:3.8]
set ytics 1
set boxwidth 0.84
set xtics ('MQ' 0, 'MS' 1, 'MQ' 3, 'MS' 4, 'MQ' 6, 'MS' 7) scale 0
set label 1 'fan-out 0' at first 0.5, graph -0.15 center
set label 2 'fan-out 10' at first 3.5, graph -0.15 center
set label 3 'fan-out 100' at first 6.5, graph -0.15 center
do for [i = 1:3] {
    set title STACK[i] font ',11' offset 0,-0.7
    if (i == 1) {
        set ylabel 'Mean time per read (ms)' offset 1,0
        set key top left reverse Left samplen 1.2 spacing 1.1 width -2
    } else {
        unset ylabel
        unset key
        set format y ''
    }
    plot FILE index (i - 1) using 1:(sum5('sql', 'commit', 'service', 'http_server', 'http_client')) with boxes lc rgb L_CLIENT title 'HTTP transport and client', \
         '' index (i - 1) using 1:(sum4('sql', 'commit', 'service', 'http_server')) with boxes lc rgb L_SERVER title 'HTTP server', \
         '' index (i - 1) using 1:(sum3('sql', 'commit', 'service')) with boxes lc rgb L_SERVICE title 'service code', \
         '' index (i - 1) using 1:(sum2('sql', 'commit')) with boxes lc rgb L_COMMIT title 'commit', \
         '' index (i - 1) using 1:(column('sql')) with boxes lc rgb L_SQL title 'SQL statements'
}
unset multiplot
