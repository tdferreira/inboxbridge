#!/usr/bin/env python3
"""Run disjoint backend coverage groups, or report coverage of added executable lines.

Run from the repository root. Toolchains and services stay inside scripts/dev Docker.
Each group deletes its prior data before JaCoCo appends across test JVMs.
Integration includes CDI component tests as well as external-service tests; no such
execution is counted as a unit test. Use --run sequentially (shared Maven target).
"""
import argparse
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path


def test_groups():
    groups = {'unit': [], 'integration': []}
    for path in sorted(Path('src/test/java').rglob('*Test.java')):
        source = path.read_text()
        if not re.search(r'@(?:Test|ParameterizedTest|RepeatedTest|TestFactory)\b', source):
            continue
        integration = re.search(r'Quarkus(?:Component|Integration)?Test|GreenMail|testcontainers', source)
        name = '.'.join(path.relative_to('src/test/java').with_suffix('').parts)
        groups['integration' if integration else 'unit'].append(name)
    return groups


def run_groups():
    groups = test_groups()
    output = Path('target/coverage')
    output.mkdir(parents=True, exist_ok=True)
    (output / 'test-groups.json').write_text(json.dumps(groups, indent=2) + '\n')
    for group, names in groups.items():
        (output / f'{group}.exec').unlink(missing_ok=True)
        with (output / f'{group}.log').open('w') as log:
            subprocess.run(['./scripts/dev', 'backend', '-B', '-Psplit-coverage',
                            f'-Dcoverage.group={group}', '-Dtest=' + ','.join(names), 'test'],
                           stdout=log, stderr=subprocess.STDOUT, check=True)
        print(f'{group}: passed ({len(names)} classes)', flush=True)


def changed_lines(base):
    diff = subprocess.check_output(['git', 'diff', '--no-ext-diff', '--unified=0',
                                    '--no-renames', base, '--', 'src/main/java', 'admin-ui/src'], text=True)
    files, path = {}, None
    for line in diff.splitlines():
        if line.startswith('+++ b/'):
            path = line[6:]
        elif line.startswith('@@'):
            match = re.search(r'\+(\d+)(?:,(\d+))? @@', line)
            start, count = int(match[1]), int(match[2] or 1)
            files.setdefault(path, set()).update(range(start, start + count))
    return files


def java_coverage(path):
    coverage = {}
    for package in ET.parse(path).getroot().findall('package'):
        for source in package.findall('sourcefile'):
            name = f'src/main/java/{package.attrib["name"]}/{source.attrib["name"]}'
            coverage[name] = {int(line.attrib['nr']): (
                int(line.attrib['ci']), int(line.attrib['mi']),
                int(line.attrib['cb']), int(line.attrib['mb'])) for line in source.findall('line')}
    return coverage


def frontend_coverage(path):
    coverage, name = {}, None
    for line in Path(path).read_text().splitlines():
        if line.startswith('SF:'):
            raw = line[3:]
            name = 'admin-ui/' + raw.split('admin-ui/')[-1].lstrip('./')
            coverage.setdefault(name, {})
        elif line.startswith('DA:'):
            number, hits, *_ = line[3:].split(',')
            old = coverage[name].get(int(number), (0, 0, 0, 0))
            coverage[name][int(number)] = (int(hits), 0 if int(hits) else 1, old[2], old[3])
        elif line.startswith('BRDA:'):
            number, _, _, hits = line[5:].split(',')
            old = coverage[name].get(int(number), (0, 0, 0, 0))
            covered = hits != '-' and int(hits) > 0
            coverage[name][int(number)] = (*old[:2], old[2] + int(covered), old[3] + int(not covered))
    return coverage


def summarize(changed, coverage):
    rows = {}
    for path, lines in changed.items():
        stats = [0, 0, 0, 0]  # covered lines, executable lines, covered branches, total branches
        for number in lines:
            ci, mi, cb, mb = coverage.get(path, {}).get(number, (0, 0, 0, 0))
            stats[0] += int(ci > 0)
            stats[1] += int(ci + mi > 0)
            stats[2] += cb
            stats[3] += cb + mb
        if stats[1] or stats[3]:
            rows[path] = stats
    return {'total': [sum(row[i] for row in rows.values()) for i in range(4)], 'files': rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', action='store_true')
    parser.add_argument('--base', default='upstream/main')
    parser.add_argument('--check', action='store_true', help='Require full added-line/branch unit coverage and frontend coverage')
    args = parser.parse_args()
    if args.run:
        run_groups()
        return
    changed = changed_lines(args.base)
    result = {'base': args.base, 'backend': {group: summarize(changed, java_coverage(
        f'target/coverage/{group}/jacoco.xml')) for group in ('unit', 'integration')},
        'frontend': summarize(changed, frontend_coverage('admin-ui/coverage/lcov.info'))}
    print(json.dumps(result, indent=2))
    if args.check:
        for group, stats in [('backend unit', result['backend']['unit']), ('frontend', result['frontend'])]:
            lines, total_lines, branches, total_branches = stats['total']
            if lines != total_lines or branches != total_branches:
                raise SystemExit(f'{group}: added executable code is not fully covered')


if __name__ == '__main__':
    main()
