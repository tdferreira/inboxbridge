#!/usr/bin/env python3
"""Create a runtime-only Docker context from the already tested build outputs."""
import argparse
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]


def prepare(destination):
    destination = Path(destination).resolve()
    if destination.exists():
        raise ValueError('Use a new, empty output path for the runtime context')
    sources = {'backend': ROOT / 'target/quarkus-app', 'frontend': ROOT / 'admin-ui/dist'}
    for source in sources.values():
        if not source.is_dir():
            raise ValueError(f'Missing build output: {source.relative_to(ROOT)}')
    destination.mkdir(parents=True)
    for name, source in sources.items():
        shutil.copytree(source, destination / name)
    for name in ('nginx.conf', 'nginx-main.conf'):
        shutil.copyfile(ROOT / 'admin-ui' / name, destination / name)
    shutil.copyfile(ROOT / 'LICENSE', destination / 'LICENSE')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination')
    prepare(parser.parse_args().destination)
