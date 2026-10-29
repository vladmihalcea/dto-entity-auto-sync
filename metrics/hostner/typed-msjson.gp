# Typed MS-JSON: the pass-through (the database JSON forwarded as a string) against the typed variant (a PostDto that
# Jackson serializes), on JDBC and jOOQ at fan-outs 0, 10, and 100. The error bars show the 95% two-level bootstrap
# CI of the mean over the ten forks of a cell.
NAME = 'typed-msjson'
W = 6.2
H = 2.3
load 'style.gp'
FILE = 'data/typed-msjson.csv'

set multiplot layout 1,2 margins 0.075,0.99,0.2,0.88 spacing 0.035,0
set xrange [-0.6:2.6]
set yrange [0:4]
set ytics 1
set boxwidth 0.36
set pointintervalbox 0
set xtics ('fan-out 0' 0, 'fan-out 10' 1, 'fan-out 100' 2) scale 0
array STACK[2] = ['jdbc', 'jooq']
array TITLE[2] = ['JDBC', 'jOOQ']
do for [i = 1:2] {
    set title TITLE[i] font ',11' offset 0,-0.7
    if (i == 1) {
        set ylabel 'Mean response time (ms)' offset 0,0
        set key top left reverse Left samplen 1.2 spacing 1.1
        set format y '%g'
    } else {
        unset ylabel
        unset key
        set format y ''
    }
    pos(dx) = strcol('stack') eq STACK[i] ? (column('fan_out') == 0 ? 0 : column('fan_out') == 10 ? 1 : 2) + dx : NaN
    plot FILE using (pos(-0.19)):(column('string')) with boxes lc rgb BASE title 'pass-through (JSON string)', \
         FILE using (pos(0.19)):(column('dto')) with boxes lc rgb ALT title 'typed (PostDto)', \
         FILE using (pos(-0.19)):(column('string')):(column('string_lo')):(column('string_hi')) with yerrorbars lc rgb INK lw 0.8 ps 0 notitle, \
         FILE using (pos(0.19)):(column('dto')):(column('dto_lo')):(column('dto_hi')) with yerrorbars lc rgb INK lw 0.8 ps 0 notitle
}
unset multiplot
