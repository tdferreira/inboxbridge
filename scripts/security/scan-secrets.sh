#!/bin/sh
# Scan only Git-tracked source or bundled history; never traverse a local .env.
set -eu
root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
mode="${1:-worktree}"
case "$mode" in worktree|history) ;; *) echo 'Usage: scan-secrets.sh [worktree|history] [report.json]' >&2; exit 2 ;; esac
report="${2:-/tmp/inboxbridge-gitleaks-${mode}.json}"
mkdir -p "$(dirname -- "$report")"
report_dir="$(CDPATH= cd -- "$(dirname -- "$report")" && pwd)"
report_name="$(basename -- "$report")"
snapshot="$(mktemp -d "${TMPDIR:-/tmp}/inboxbridge-private-data-scan.XXXXXX")"
trap 'rm -rf "$snapshot"' EXIT HUP INT TERM

if [ "$mode" = history ]; then
  # A bundle includes committed objects, without local Git credentials/config.
  git -C "$root" bundle create "$snapshot/history.bundle" --all
  docker run --rm --entrypoint sh -v "$snapshot/history.bundle:/history.bundle:ro" \
    -v "$root/.gitleaks.toml:/gitleaks.toml:ro" -v "$report_dir:/reports" \
    -e REPORT_NAME="$report_name" ghcr.io/gitleaks/gitleaks:v8.30.1 -c '
      git clone --mirror /history.bundle /tmp/repository.git >/dev/null 2>&1
      exec gitleaks git /tmp/repository.git --log-opts=--all --config /gitleaks.toml \
        --redact=100 --no-banner --report-format json --report-path "/reports/$REPORT_NAME"
    '
else
  python3 - "$root" "$snapshot" <<'PY'
from pathlib import Path
import os
import shutil
import subprocess
import sys
root, snapshot = map(Path, sys.argv[1:])
for name in subprocess.check_output(['git', '-C', str(root), 'ls-files', '-z']).decode().split('\0'):
    if not name:
        continue
    source = root / name
    # Flag an accidentally tracked environment file without opening its contents.
    if source.name == '.env' or (source.name.startswith('.env.') and source.name != '.env.example'):
        raise SystemExit('A private environment file is tracked; remove it from Git before scanning.')
    target = snapshot / name
    target.parent.mkdir(parents=True, exist_ok=True)
    if source.is_symlink():
        target.write_text(os.readlink(source))
    else:
        shutil.copyfile(source, target)
PY
  docker run --rm -v "$snapshot:/src:ro" -v "$root/.gitleaks.toml:/gitleaks.toml:ro" \
    -v "$report_dir:/reports" ghcr.io/gitleaks/gitleaks:v8.30.1 dir /src \
    --config /gitleaks.toml --redact=100 --no-banner --report-format json \
    --report-path "/reports/$report_name"
fi
