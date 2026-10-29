#!/usr/bin/env python3
"""Builds the data files of the Gnuplot charts of the Hostner campaign (metrics/hostner/*.gp).

Run it from the repository root, with a JDK and Python 3 on the PATH:

    python3 metrics/hostner/make-chart-data.py

It reads the raw results in results/hostner-pair (the campaign), results/hostner-swap (the role swap),
results/hostner-pilot (the pilot that preceded the campaign), and results/hostner-overhead (the layer profiler
with and without), runs analysis/Stats.java on each of them
(outputs in results/<name>/analysis), and writes one CSV file per chart to metrics/hostner/data.

The comparisons of one cell across two runs (role swap against campaign, SF10 against SF1, pilot against
campaign) use Stats.java as well: the forks of the two cells are copied into one block as
core-<cell> and versioning-<cell>, the pair that Stats.java compares, with the pattern tokens shortened
so that no other pair forms. Their comparisons.csv files are kept as data/cross-<name>.csv.

The charts themselves need only Gnuplot: gnuplot metrics/hostner/<chart>.gp
"""
import csv
import glob
import json
import os
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RESULTS = os.path.join(ROOT, 'results')
DATA = os.path.join(ROOT, 'metrics', 'hostner', 'data')
STATS = os.path.join(ROOT, 'analysis', 'Stats.java')
STACKS = ['jdbc', 'jooq', 'jpa']
FAN_OUTS = [0, 5, 10, 25, 100]
THREADS = [1, 4, 8, 16]
SHORT = {'mqdto': 'mq', 'msjson': 'ms', 'multiops': 'mops', 'diffmerge': 'dmerge'}


def stats(results, output):
    """Runs Stats.java unless its output already exists (delete the analysis folder to run it again)."""
    marker = os.path.join(output, 'cells.csv')
    if not os.path.exists(marker):
        print('Stats.java', os.path.relpath(results, ROOT))
        subprocess.run(['java', STATS, results, output], check=True, stdout=subprocess.DEVNULL)
    return output


def rows(path):
    with open(path, newline='', encoding='utf-8') as f:
        return list(csv.DictReader(f))


def number(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return float('nan')


def write(name, header, lines, comment=None):
    os.makedirs(DATA, exist_ok=True)
    path = os.path.join(DATA, name)
    with open(path, 'w', newline='\n', encoding='utf-8') as f:
        if comment:
            for line in comment.split('\n'):
                f.write('# ' + line + '\n')
        f.write(','.join(header) + '\n')
        for line in lines:
            if line is None:  # a new Gnuplot index, which repeats the column headers
                f.write('\n\n' + ','.join(header) + '\n')
                continue
            f.write(','.join(x if isinstance(x, str) else (str(x) if isinstance(x, int) else f'{x:.6g}') for x in line) + '\n')
    print('wrote', os.path.relpath(path, ROOT))


def short(cell):
    for token, replacement in SHORT.items():
        cell = cell.replace(token, replacement)
    return cell


def restore(cell):
    for token, replacement in SHORT.items():
        cell = cell.replace('-' + replacement + '-', '-' + token + '-')
    return cell


def cross(name, pairs, rounds_a=None):
    """Compares the cells of pairs = [(results A, block A, cell A, results B, block B, cell B, label)] with Stats.java."""
    out = os.path.join(DATA, f'cross-{name}.csv')
    if not os.path.exists(out):
        with tempfile.TemporaryDirectory() as tmp:
            block = os.path.join(tmp, 'in', 'b')
            for res_a, block_a, cell_a, res_b, block_b, cell_b, label in pairs:
                for source, tag, limit in ((os.path.join(RESULTS, res_a, block_a, 'round-*', cell_a + '.json'), 'core', rounds_a),
                                           (os.path.join(RESULTS, res_b, block_b, 'round-*', cell_b + '.json'), 'versioning', None)):
                    for file in sorted(glob.glob(source)):
                        round_name = os.path.basename(os.path.dirname(file))
                        if limit and int(round_name.split('-')[1]) > limit:
                            continue
                        os.makedirs(os.path.join(block, round_name), exist_ok=True)
                        shutil.copy(file, os.path.join(block, round_name, f'{tag}-{short(label)}.json'))
            print('Stats.java cross', name)
            subprocess.run(['java', STATS, os.path.join(tmp, 'in'), os.path.join(tmp, 'out')], check=True, stdout=subprocess.DEVNULL)
            os.makedirs(DATA, exist_ok=True)
            shutil.copy(os.path.join(tmp, 'out', 'comparisons.csv'), out)
    return {r['baseline'][len('core-'):]: r for r in rows(out)}


def main():
    pair = stats(os.path.join(RESULTS, 'hostner-pair'), os.path.join(RESULTS, 'hostner-pair', 'analysis'))
    swap = stats(os.path.join(RESULTS, 'hostner-swap'), os.path.join(RESULTS, 'hostner-swap', 'analysis'))
    cells = {(r['block'], r['cell']): r for r in rows(os.path.join(pair, 'cells.csv'))}
    layers = {(r['block'], r['cell']): r for r in rows(os.path.join(pair, 'layers.csv'))}
    comparisons = {(r['block'], r['baseline']): r for r in rows(os.path.join(pair, 'comparisons.csv'))}

    def mean(block, cell):
        r = cells[(block, cell)]
        return [number(r['mean']), number(r['mean_ci_low']), number(r['mean_ci_high'])]

    def layer_split(block, cell):
        r = layers[(block, cell)]
        sql, commit, service = number(r['layer.sql.ms']), number(r['layer.commit.ms']), number(r['layer.service.ms'])
        server, client = number(r.get('layer.http.server.ms')), number(r.get('layer.http.client.ms'))
        rest = server == server  # not NaN: the cell goes through HTTP
        return [sql, commit, service - sql - commit, server - service if rest else 0.0, client - server if rest else 0.0]

    # 1. RQ1: response time against the fan-out
    lines = []
    for position, fan_out in enumerate(FAN_OUTS):
        line = [position, fan_out]
        for stack in STACKS:
            line += mean('rq1-rest', f'{stack}-mqdto-{fan_out}') + mean('rq1-rest', f'{stack}-msjson-{fan_out}')
        lines.append(line)
    header = ['position', 'fan_out'] + [f'{s}_{t}{x}' for s in STACKS for t in ('mqdto', 'msjson') for x in ('', '_lo', '_hi')]
    write('rq1-response-time.csv', header, lines,
          'RQ1 (block rq1-rest): mean response time in ms/op with its 95% two-level bootstrap CI, per fan-out')

    # 2. RQ1: time per layer at fan-outs 0, 10, and 100 (one Gnuplot index per stack)
    lines = []
    for stack in STACKS:
        x = 0
        for fan_out in (0, 10, 100):
            for technique, label in (('mqdto', 'MQ-DTO'), ('msjson', 'MS-JSON')):
                lines.append([x, f'{label}', fan_out] + layer_split('rq1-rest', f'{stack}-{technique}-{fan_out}'))
                x += 1
            x += 1
        lines.append(None)
    write('rq1-layers.csv', ['x', 'technique', 'fan_out', 'sql', 'commit', 'service', 'http_server', 'http_client'], lines[:-1],
          'RQ1: mean time per layer in ms/op (SQL statements, commit, rest of the service, HTTP server outside the service,\n'
          'HTTP transport and client); Gnuplot index 0 = JDBC, 1 = jOOQ, 2 = JPA')

    # 3. RQ2: response time over REST and at the service level, with the counts and the time per layer
    lines = []
    x = 0
    for stack in STACKS:
        for level in ('rest', 'service'):
            for pattern, label in (('multiops', 'MO'), ('diffmerge', 'DM')):
                cell = f'{stack}-{pattern}-{level}'
                r = layers[('rq2-writes', cell)]
                lines.append([x, stack, level, label] + mean('rq2-writes', cell)
                             + [int(number(r['layer.sql.statements']) + 0.5), int(number(r['layer.commits']) + 0.5),
                                int(number(r['layer.http.calls']) + 0.5) if level == 'rest' else 0] + layer_split('rq2-writes', cell))
                x += 1
            x += 0.5
        x += 1
    write('rq2-writes.csv', ['x', 'stack', 'level', 'pattern', 'mean', 'mean_lo', 'mean_hi', 'statements', 'commits', 'http_calls',
                             'sql', 'commit', 'service', 'http_server', 'http_client'], lines,
          'RQ2 (block rq2-writes): mean response time in ms/op with its 95% two-level bootstrap CI; SQL statements, commits, and\n'
          'HTTP calls per edit; and the mean time per layer in ms/op (MO = Multi-Ops, DM = Diff-Merge)')

    # 4. RQ5: response time against the benchmark threads (1 thread from rq1-rest and rq2-writes)
    lines = []
    for threads in THREADS:
        line = [threads]
        for stack in STACKS:
            for cell in (f'{stack}-mqdto-10', f'{stack}-msjson-10', f'{stack}-multiops-rest', f'{stack}-diffmerge-rest'):
                if threads == 1:
                    line += mean('rq1-rest' if cell.endswith('-10') else 'rq2-writes', cell)
                else:
                    line += mean('w7-concurrency', f'{cell}-t{threads}')
        lines.append(line)
    header = ['threads'] + [f'{s}_{c}{x}' for s in STACKS for c in ('mqdto', 'msjson', 'multiops', 'diffmerge') for x in ('', '_lo', '_hi')]
    write('rq5-threads.csv', header, lines, 'RQ5 (block w7-concurrency): mean response time in ms/op with its 95% two-level bootstrap CI, per thread count')

    # 5. RQ5: SF10 against SF1, and the role swap against the campaign, as ratios of the means of the same cell
    headline = [(f'{s}-{t}-10', 'rq1-rest') for s in STACKS for t in ('mqdto', 'msjson')] + \
               [(f'{s}-{p}-rest', 'rq2-writes') for s in STACKS for p in ('multiops', 'diffmerge')]
    sf10 = cross('sf10', [('hostner-pair', block, cell, 'hostner-pair', 'w7-sf10', cell + '-sf10', cell) for cell, block in headline])
    role = cross('role-swap', [('hostner-pair', block, cell, 'hostner-swap', 'role-swap', cell, cell) for cell, block in headline])
    labels = {'mqdto': 'MQ-DTO', 'msjson': 'MS-JSON', 'multiops': 'Multi-Ops', 'diffmerge': 'Diff-Merge'}
    names = {'jdbc': 'JDBC', 'jooq': 'jOOQ', 'jpa': 'JPA'}
    for name, comparison in (('rq5-sf10-ratio.csv', sf10), ('role-swap-ratio.csv', role)):
        lines = []
        x = 0
        for kind in ('read', 'write'):
            for cell, block in headline:
                if (block == 'rq1-rest') != (kind == 'read'):
                    continue
                stack, pattern = cell.split('-')[0], cell.split('-')[1]
                r = comparison[short(cell)]
                lines.append([x, f'{names[stack]} {labels[pattern]}', kind, number(r['ratio']), number(r['ratio_ci_low']), number(r['ratio_ci_high']),
                              number(r['mean_baseline']), number(r['mean_alternative'])])
                x += 1
            x += 1
        write(name, ['x', 'cell', 'kind', 'ratio', 'ratio_lo', 'ratio_hi', 'mean_baseline', 'mean_alternative'], lines,
              'Ratio of the mean response times of the same cell in two runs, with its 95% two-level bootstrap CI\n'
              + ('(SF10 / SF1)' if 'sf10' in name else '(swapped roles / campaign roles)'))
    swap_layers = {r['cell']: r for r in rows(os.path.join(swap, 'layers.csv'))}
    lines = []
    for x, (cell, block) in enumerate(c for c in headline if c[1] == 'rq2-writes'):
        campaign, swapped = layers[(block, cell)], swap_layers[cell]
        commits = number(campaign['layer.commits'])
        lines.append([x, cell, int(commits + 0.5), number(campaign['layer.commit.ms']) / commits, number(swapped['layer.commit.ms']) / commits])
    write('role-swap-commit.csv', ['x', 'cell', 'commits', 'commit_ms_campaign', 'commit_ms_swap'], lines,
          'Mean time of one commit of a write transaction in ms: campaign (database on s66) and role swap (database on s68)')

    # 6. Typed MS-JSON against the pass-through
    lines = []
    x = 0
    for stack in ('jdbc', 'jooq'):
        for fan_out in (0, 10, 100):
            lines.append([x, stack, fan_out] + mean('typed-msjson', f'{stack}-msjson-string-{fan_out}') + mean('typed-msjson', f'{stack}-msjson-dto-{fan_out}'))
            x += 1
        x += 1
    write('typed-msjson.csv', ['x', 'stack', 'fan_out', 'string', 'string_lo', 'string_hi', 'dto', 'dto_lo', 'dto_hi'], lines,
          'Typed MS-JSON (block typed-msjson): mean response time in ms/op with its 95% two-level bootstrap CI')

    # 7. Optimistic locking: every core cell against its versioning twin
    lines = []
    x = 0
    for stack in STACKS:
        for level in ('rest', 'service'):
            for pattern in ('multiops', 'diffmerge'):
                cell = f'{stack}-{pattern}-{level}'
                lines.append([x, stack, level, pattern] + mean('versioning', 'core-' + cell) + mean('versioning', 'versioning-' + cell))
                x += 1
            x += 0.5
        x += 1
    write('versioning.csv', ['x', 'stack', 'level', 'pattern', 'core', 'core_lo', 'core_hi', 'versioning', 'versioning_lo', 'versioning_hi'], lines,
          'Cost of optimistic locking (block versioning): mean response time in ms/op with its 95% two-level bootstrap CI')

    # 8. Repeatability: the pilot against the campaign (RQ2: 10 rounds; RQ1: the 8 complete rounds of the pilot)
    pilot2 = cross('pilot-rq2', [('hostner-pilot', 'rq2-writes', cell, 'hostner-pair', 'rq2-writes', cell, cell)
                                 for cell in (f'{s}-{p}-{l}' for s in STACKS for p in ('multiops', 'diffmerge') for l in ('rest', 'service'))])
    pilot1 = cross('pilot-rq1', [('hostner-pilot', 'rq1-rest', cell, 'hostner-pair', 'rq1-rest', cell, cell)
                                 for cell in (f'{s}-{t}-{n}' for s in STACKS for t in ('mqdto', 'msjson') for n in FAN_OUTS)], rounds_a=8)
    lines = [[block, restore(cell), number(r['mean_baseline']), number(r['mean_alternative']), number(r['ratio']), number(r['ratio_ci_low']), number(r['ratio_ci_high'])]
             for block, comparison in (('rq2-writes', pilot2), ('rq1-rest', pilot1)) for cell, r in sorted(comparison.items())]
    write('repeatability.csv', ['block', 'cell', 'pilot', 'campaign', 'ratio', 'ratio_lo', 'ratio_hi'], lines,
          'Mean response time of every RQ1 and RQ2 cell in the pilot (before the BIOS update) and in the campaign, ms/op')

    # 9. Telemetry of every fork of the campaign, in the order in which the forks ran
    forks = []
    blocks = []
    for block in ('rq2-writes', 'rq1-rest', 'typed-msjson', 'w7-sf10', 'w7-concurrency', 'versioning'):
        block_forks = rows(os.path.join(RESULTS, 'hostner-pair', block, 'forks.csv'))
        blocks.append((block, block_forks[0]['start'], block_forks[-1]['end']))
        for fork in block_forks:
            file = os.path.join(RESULTS, 'hostner-pair', block, f"round-{int(fork['round']):02d}", fork['cell'] + '.json')
            with open(file, encoding='utf-8') as f:
                result = json.load(f)[0]
            metrics = result['secondaryMetrics']
            forks.append((fork['start'], fork['end'], block, fork['cell'], result['threads'],
                          [metrics[m]['score'] if m in metrics else float('nan') for m in (
                              'app.cpu.mhz', 'db.cpu.mhz', 'app.temp', 'db.temp', 'app.cpu.power', 'db.cpu.power',
                              'app.cpu.util', 'db.cpu.util', 'app.thermal.throttles', 'db.thermal.throttles')]))
    parse = lambda text: datetime.strptime(text, '%Y-%m-%d %H:%M:%S')
    origin = parse(forks[0][0])
    hours = lambda text: (parse(text) - origin).total_seconds() / 3600
    lines = [[(hours(start) + hours(end)) / 2, block, cell, threads] + values for start, end, block, cell, threads, values in forks]
    write('telemetry-forks.csv', ['hours', 'block', 'cell', 'threads', 'app_mhz', 'db_mhz', 'app_temp', 'db_temp', 'app_power', 'db_power',
                                  'app_util', 'db_util', 'app_throttles', 'db_throttles'], lines,
          'Telemetry of every fork of the campaign: hours since the first fork (middle of the fork), fork means of the busy clock in MHz,\n'
          'the hottest temperature in degrees Celsius, the mean package power in W, the mean CPU utilization in percent, and the\n'
          'thermal throttling events, on the application server (app) and on the database server (db)')
    block_labels = {'rq2-writes': 'RQ2', 'rq1-rest': 'RQ1', 'typed-msjson': 'typed', 'w7-sf10': 'SF10',
                    'w7-concurrency': 'RQ5 threads', 'versioning': 'versioning'}
    write('telemetry-blocks.csv', ['block', 'label', 'start_hours', 'end_hours'],
          [[block, block_labels[block], hours(start), hours(end)] for block, start, end in blocks],
          'Start and end of every block of the campaign, in hours since the first fork')

    # 10. Overhead of the layer profiler (block layer-overhead, in the layout of the campaign): every cell without the
    # layer profiler (baseline) against its twin with it, in the same rounds, and the twins with it against the same
    # cells of the campaign, which checks that the restored layout reproduces the campaign
    overhead_cells = [('jdbc-mqdto-0', 'rq1-rest'), ('jdbc-diffmerge-rest', 'rq2-writes'), ('jpa-multiops-rest', 'rq2-writes')]
    if os.path.isdir(os.path.join(RESULTS, 'hostner-overhead', 'layer-overhead')):
        stats(os.path.join(RESULTS, 'hostner-overhead'), os.path.join(RESULTS, 'hostner-overhead', 'analysis'))
        overhead = cross('layer-overhead', [('hostner-overhead', 'layer-overhead', cell + '-nolayer', 'hostner-overhead', 'layer-overhead', cell, cell)
                                            for cell, _ in overhead_cells])
        repeat = cross('layer-overhead-repeat', [('hostner-pair', block, cell, 'hostner-overhead', 'layer-overhead', cell, cell)
                                                  for cell, block in overhead_cells])
        write('layer-overhead.csv', ['cell', 'without', 'with', 'ratio', 'ratio_lo', 'ratio_hi', 'p_fork_holm', 'cliffs_delta',
                                     'campaign', 'repeat_ratio', 'repeat_ratio_lo', 'repeat_ratio_hi'],
              [[cell, number(overhead[short(cell)]['mean_baseline']), number(overhead[short(cell)]['mean_alternative']),
                number(overhead[short(cell)]['ratio']), number(overhead[short(cell)]['ratio_ci_low']), number(overhead[short(cell)]['ratio_ci_high']),
                number(overhead[short(cell)]['p_fork_holm']), number(overhead[short(cell)]['cliffs_delta']),
                number(repeat[short(cell)]['mean_baseline']), number(repeat[short(cell)]['ratio']),
                number(repeat[short(cell)]['ratio_ci_low']), number(repeat[short(cell)]['ratio_ci_high'])]
               for cell, _ in overhead_cells],
              'Overhead of the layer profiler: mean response time in ms/op without and with it (same rounds), the ratio with/without\n'
              'with its 95% two-level bootstrap CI, and the ratio of the cell with the profiler to the same cell of the campaign')


if __name__ == '__main__':
    sys.exit(main())
