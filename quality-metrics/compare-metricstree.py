"""Compares the package metrics that run-metricstree exported with the ones in metrics/CodeQuality.

For every <module>-package.csv that the MetricsTree runner wrote, the script
  * writes <module>-package-agr.csv in the layout of metrics/CodeQuality (one row per metric, one column
    per package, only the metrics and packages of the reference file), and
  * writes <module>-package-mi.csv with the package Maintainability Index that MetricsTree reports (PAMI,
    which uses ln CC) next to the one of Coleman et al. (0.23 CC), both rescaled to [0, 100], and
  * compares every value of the reference file with the exported one, within the rounding of the
    reference (MetricsTree prints at most four decimals).
It exits with status 1 if a value differs or a reference file has no export.

Usage: python compare-metricstree.py [export-dir] [reference-dir]
"""
import csv
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXPORT_DIR = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / 'target' / 'metricstree'
REFERENCE_DIR = Path(sys.argv[2]) if len(sys.argv) > 2 else HERE.parent / 'metrics' / 'CodeQuality'


def number(text):
    text = text.strip()
    if text in ('', 'N/A'):
        return None
    return float(text.replace(',', '.'))


def read_export(path):
    """MetricsTree's package export: 'Package Name;M1;M2;...', one row per package."""
    with open(path, encoding='utf-8-sig', newline='') as f:
        rows = list(csv.reader(f, delimiter=';'))
    header = rows[0][1:]
    return {row[0]: {metric: number(value) for metric, value in zip(header, row[1:])} for row in rows[1:] if row}


def read_cc(path):
    if not path.exists():
        return {}
    with open(path, encoding='utf-8', newline='') as f:
        return {row[0]: float(row[1]) for row in list(csv.reader(f, delimiter=';'))[1:] if row}


def read_reference(path):
    """metrics/CodeQuality layout: 'Metric,Description,package1,package2,...', one row per metric."""
    with open(path, encoding='utf-8-sig', newline='') as f:
        rows = list(csv.reader(f))
    packages = rows[0][2:]
    return packages, [(row[0], row[1], row[2:]) for row in rows[1:] if row]


def tolerance(reference_text):
    """Half a unit of the last decimal the reference prints, plus floating-point slack."""
    decimals = len(reference_text.split('.')[1]) if '.' in reference_text else 0
    return 0.5 * 10 ** -decimals + 1e-9 if decimals else 1e-9


def maintainability(volume, cc, loc, log_cc):
    if not volume or not cc or not loc:
        return 0.0
    cc_term = 0.23 * (math.log(cc) if log_cc else cc)
    return max(0.0, (171 - 5.2 * math.log(volume) - cc_term - 16.2 * math.log(loc)) * 100 / 171)


def main():
    exports = sorted(EXPORT_DIR.glob('*-package.csv'))
    references = sorted(REFERENCE_DIR.glob('*/*-package-agr.csv'))
    if not references:
        sys.exit(f'No reference files under {REFERENCE_DIR}')
    exported_modules = {p.name[:-len('-package.csv')]: p for p in exports}
    mismatches, compared, missing = 0, 0, []

    for reference in references:
        module = reference.name[:-len('-package-agr.csv')]
        export = exported_modules.get(module)
        if export is None:
            missing.append(module)
            continue
        values = read_export(export)
        cc = read_cc(export.with_name(f'{module}-package-cc.csv'))
        packages, rows = read_reference(reference)

        with open(export.with_name(f'{module}-package-agr.csv'), 'w', encoding='utf-8', newline='') as f:
            writer = csv.writer(f)
            writer.writerow(['Metric', 'Description'] + packages)
            for metric, description, _ in rows:
                writer.writerow([metric, description] + [
                    '' if values.get(p, {}).get(metric) is None else f'{values[p][metric]:.4f}'.rstrip('0').rstrip('.')
                    for p in packages])

        with open(export.with_name(f'{module}-package-mi.csv'), 'w', encoding='utf-8', newline='') as f:
            writer = csv.writer(f)
            writer.writerow(['Package', 'PAHVL', 'CC', 'PLOC', 'PAMI (MetricsTree, ln CC)', 'MI (Coleman et al., CC), rescaled'])
            for p in sorted(values):
                v, loc, c = values[p].get('PAHVL'), values[p].get('PLOC'), cc.get(p)
                writer.writerow([p, v, c, loc, values[p].get('PAMI'),
                                 '' if c is None else f'{maintainability(v, c, loc, log_cc=False):.4f}'])

        for metric, _, reference_values in rows:
            for package, reference_text in zip(packages, reference_values):
                expected = number(reference_text)
                actual = values.get(package, {}).get(metric)
                compared += 1
                if expected is None and actual is None:
                    continue
                if expected is None or actual is None or abs(actual - expected) > tolerance(reference_text.strip()):
                    mismatches += 1
                    print(f'{module}: {package} {metric}: reference {reference_text.strip()}, MetricsTree {actual}')

    print(f'{compared} values compared in {len(references) - len(missing)} modules, {mismatches} differ')
    for module in missing:
        print(f'{module}: no export in {EXPORT_DIR}')
    sys.exit(1 if mismatches or missing else 0)


if __name__ == '__main__':
    main()
