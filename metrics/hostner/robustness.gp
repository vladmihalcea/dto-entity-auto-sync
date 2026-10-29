# The same cells measured in two runs: the ratio of their mean response times with its 95% two-level bootstrap CI.
# (a) The campaign against the pilot that ran before the BIOS update (RQ1: the 8 complete rounds of the pilot).
# (b) SF10 against SF1 (the 12 headline cells, 1 thread).
# (c) The role swap (database on s68) against the campaign (database on s66), the same 12 cells.
# Reads are blue circles, writes orange squares; the dashed line marks equal means.
NAME = 'robustness'
W = 6.2
H = 2.4
load 'style.gp'

set multiplot layout 1,3 margins 0.085,0.99,0.13,0.86 spacing 0.03,0
set yrange [0.96:1.16]
set ytics 0.96,0.04,1.16
set format y '%.2f'
set xtics scale 0
set pointintervalbox 0

# (a) The repeatability file lists the 12 RQ2 cells first, then the 30 RQ1 cells
set title '(a) Campaign / pilot' font ',10.5' offset 0,-0.7
set ylabel 'Ratio of the mean response times' offset 1,0
set xrange [-2:45]
set xtics ('reads' 14.5, 'writes' 37.5)
set arrow 1 from -2, 1 to 45, 1 nohead lc rgb MUTED dt 2 lw 0.8
set key top left reverse Left samplen 1 spacing 1.1
xa(row) = row < 12 ? row + 32 : row - 12
FILE = 'data/repeatability.csv'
plot FILE using (strcol('block') eq 'rq1-rest' ? xa($0) : NaN):(column('ratio')):(column('ratio_lo')):(column('ratio_hi')) \
        with yerrorbars pt 7 ps 0.4 lc rgb BASE title 'read', \
     FILE using (strcol('block') eq 'rq2-writes' ? xa($0) : NaN):(column('ratio')):(column('ratio_lo')):(column('ratio_hi')) \
        with yerrorbars pt 5 ps 0.38 lc rgb ALT title 'write'

# (b) and (c): reads at x 0-5, writes at x 7-12
unset ylabel
unset key
set format y ''
set xrange [-1:13]
set xtics ('reads' 2.5, 'writes' 9.5)
set arrow 1 from -1, 1 to 13, 1 nohead lc rgb MUTED dt 2 lw 0.8
do for [i = 1:2] {
    FILE = (i == 1 ? 'data/rq5-sf10-ratio.csv' : 'data/role-swap-ratio.csv')
    set title (i == 1 ? '(b) SF10 / SF1' : '(c) Swapped roles / campaign') font ',10.5' offset 0,-0.7
    plot FILE using (strcol('kind') eq 'read' ? column('x') : NaN):(column('ratio')):(column('ratio_lo')):(column('ratio_hi')) \
            with yerrorbars pt 7 ps 0.45 lc rgb BASE notitle, \
         FILE using (strcol('kind') eq 'write' ? column('x') : NaN):(column('ratio')):(column('ratio_lo')):(column('ratio_hi')) \
            with yerrorbars pt 5 ps 0.42 lc rgb ALT notitle
}
unset multiplot
