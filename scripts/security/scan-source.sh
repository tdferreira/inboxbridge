#!/bin/sh
# Scan only explicit source paths; never include local .env files or TLS keys.
set -eu
root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
snapshot="$(mktemp -d "${TMPDIR:-/tmp}/inboxbridge-source-scan.XXXXXX")"
trap 'rm -rf "$snapshot"' EXIT HUP INT TERM
report="${1:-/tmp/inboxbridge-source-semgrep.json}"
mkdir -p "$(dirname -- "$report")"
report_dir="$(CDPATH= cd -- "$(dirname -- "$report")" && pwd)"
report_name="$(basename -- "$report")"
python3 - "$root" "$snapshot" <<'PY'
from pathlib import Path
import shutil
import sys
root, snapshot = map(Path, sys.argv[1:])
directories = ('src/main', 'admin-ui/src', 'admin-ui/public', 'docker',
               'browser-extensions/shared/src', 'browser-extensions/chromium/src',
               'browser-extensions/firefox/src', 'site', '.github/workflows', 'scripts/security')
for directory in directories:
    shutil.copytree(root / directory, snapshot / directory,
                    ignore=shutil.ignore_patterns('.env*', '*.test.*', 'test-*', '__pycache__'))
for name in ('Dockerfile', 'docker-compose.yml', 'pom.xml', 'admin-ui/Dockerfile',
             'admin-ui/nginx.conf', 'admin-ui/nginx-main.conf', 'admin-ui/package.json'):
    target = snapshot / name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(root / name, target)
PY
docker run --rm -v "$snapshot:/src:ro" -v "$report_dir:/reports" \
  -e SEMGREP_SEND_METRICS=off -w /src semgrep/semgrep:1.182.0 \
  semgrep scan --metrics=off --disable-version-check --no-git-ignore \
  --config p/java --config p/javascript --config p/react \
  --config p/owasp-top-ten --config p/security-audit \
  --json --output "/reports/$report_name" .
