#!/bin/sh
# Exercise release images on a disposable stack, without operator .env or data.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
: "${BACKEND_IMAGE:?Set BACKEND_IMAGE}"
: "${FRONTEND_IMAGE:?Set FRONTEND_IMAGE}"
task_tmp=$(mktemp -d "${TMPDIR:-/tmp}/inboxbridge-image-smoke.XXXXXX")
task_project="image-smoke-$(date +%s)-$$"
compose() { docker compose --project-directory "$task_tmp" --env-file "$task_tmp/.env" -p "$task_project" -f "$task_tmp/compose.json" "$@"; }
cleanup() {
  if [ -f "$task_tmp/compose.json" ]; then compose down --volumes --remove-orphans >/dev/null 2>&1 || true; fi
  rm -rf "$task_tmp"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' HUP TERM
chmod 700 "$task_tmp"
cp "$root/docker-compose.yml" "$task_tmp/base.yaml"
cp -R "$root/docker" "$task_tmp/docker"
(umask 077; {
  echo 'PUBLIC_HOSTNAME=localhost'
  echo 'PUBLIC_PORT=3000'
  echo 'PUBLIC_BIND_ADDRESS=127.0.0.1'
  echo 'POLL_ENABLED=false'
  echo 'SECURITY_ALLOW_ENV_MANAGED_MAILBOX_SECRETS=false'
  printf 'SECURITY_TOKEN_ENCRYPTION_KEY='
  openssl rand -base64 32
} > "$task_tmp/.env")
docker compose --project-directory "$task_tmp" --env-file "$task_tmp/.env" -p "$task_project" \
  -f "$task_tmp/base.yaml" config --format json > "$task_tmp/resolved.json"
python3 - "$task_tmp" <<'PY'
import json, os, sys
from pathlib import Path
root=Path(sys.argv[1]); data=json.loads((root/'resolved.json').read_text())
for service in data['services'].values():
    service.pop('container_name',None)
    service.pop('build',None)
data['services']['inboxbridge']['image']=os.environ['BACKEND_IMAGE']
data['services']['inboxbridge-admin']['image']=os.environ['FRONTEND_IMAGE']
data['services']['inboxbridge-admin']['ports']=[{'target':8443,'published':'0','host_ip':'127.0.0.1','protocol':'tcp'}]
data['services']['postgres']['environment']['PGDATA']='/var/lib/postgresql/data/pgdata'
(root/'compose.json').write_text(json.dumps(data))
PY
compose up -d --no-build
# The certificate directory is root-owned; export only its public trust anchor.
compose cp cert-init:/certs/ca.crt "$task_tmp/public-ca.crt"
address=$(compose port inboxbridge-admin 8443)
# The generated frontend certificate covers localhost, not its mapped IP.
base_url="https://localhost:${address##*:}"
ready=false
for attempt in $(seq 1 90); do
  if [ -f "$task_tmp/public-ca.crt" ] && curl --noproxy '*' --max-time 10 -fsS --cacert "$task_tmp/public-ca.crt" \
    "$base_url/api/health/summary" > "$task_tmp/health.json" 2>/dev/null; then ready=true; break; fi
  sleep 2
done
if [ "$ready" != true ]; then compose logs --tail 60; exit 1; fi
python3 - "$task_tmp/health.json" <<'PY'
import json, sys
assert json.load(open(sys.argv[1])) == {'status':'UP'}
PY
curl --noproxy '*' --max-time 10 -fsS --cacert "$task_tmp/public-ca.crt" "$base_url/" > "$task_tmp/index.html"
python3 - "$task_tmp/index.html" <<'PY'
from pathlib import Path
import sys
assert '<html' in Path(sys.argv[1]).read_text()
PY
for service in inboxbridge inboxbridge-admin; do
  test "$(compose exec -T "$service" id -u)" != 0
  compose exec -T "$service" sh -c 'test ! -e /etc/inboxbridge/certs/ca.key'
done
for service in inboxbridge postgres; do
  container=$(compose ps -q "$service")
  docker inspect --format '{{json .HostConfig.PortBindings}}' "$container" > "$task_tmp/ports.json"
  python3 - "$task_tmp/ports.json" <<'PY'
import json, sys
assert not json.load(open(sys.argv[1]))
PY
done
printf '%s\n' 'Release images: verified HTTPS health/UI, non-root users, CA-key isolation and no backend/database host ports.'
