#!/usr/bin/env python3
"""Validate release data and derive portable GHCR names without shell evaluation."""
import argparse
import re


def metadata(repository, tag, revision):
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('Expected owner/repository')
    if not re.fullmatch(r'v\d+\.\d+\.\d+(?:[.-][0-9A-Za-z.-]+)?', tag) or len(tag) > 128:
        raise ValueError('Expected a semantic release tag of at most 128 characters')
    if not re.fullmatch(r'[0-9a-f]{40}', revision):
        raise ValueError('Expected an exact source commit')
    return {'backend_image': 'ghcr.io/' + repository.lower() + '-backend',
            'frontend_image': 'ghcr.io/' + repository.lower() + '-frontend',
            'image_tag': tag, 'revision': revision}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--revision', required=True)
    args = parser.parse_args()
    for key, value in metadata(args.repository, args.tag, args.revision).items():
        print(f'{key}={value}')
