#!/bin/sh
# Verify the existing HTTP proxy deployment contract with disposable data only.
set -eu
: "${BACKEND_IMAGE:?Set BACKEND_IMAGE}"
: "${FRONTEND_IMAGE:?Set FRONTEND_IMAGE}"
task_tmp=$(mktemp -d "${TMPDIR:-/tmp}/inboxbridge-proxy-smoke.XXXXXX")
task_project="proxy-smoke-$(date +%s)-$$"
compose() { docker compose --env-file /dev/null -p "$task_project" -f "$task_tmp/compose.json" "$@"; }
cleanup() {
  if [ -f "$task_tmp/compose.json" ]; then compose down --volumes --remove-orphans >/dev/null 2>&1 || true; fi
  rm -rf "$task_tmp"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' HUP TERM
chmod 700 "$task_tmp"
python3 - "$task_tmp/compose.json" <<'PY'
import base64, json, os, secrets, sys
from pathlib import Path
config={
 'services':{
  'postgres':{'image':'postgres:16','environment':{'POSTGRES_DB':'inboxbridge','POSTGRES_USER':'inboxbridge','POSTGRES_PASSWORD':'smoke-only'},
    'volumes':['database:/var/lib/postgresql/data'],
    'healthcheck':{'test':['CMD-SHELL','pg_isready -U inboxbridge -d inboxbridge'],'interval':'2s','timeout':'5s','retries':30},'networks':['database_net']},
  'inboxbridge':{'image':os.environ['BACKEND_IMAGE'],'user':'10001:10001',
    # This exact command must replace CMD, not append to an image ENTRYPOINT.
    'command':['java','-XX:MaxRAMPercentage=65','-jar','/app/quarkus-run.jar'],
    'environment':{
      'JDBC_URL':'jdbc:postgresql://postgres:5432/inboxbridge?sslmode=disable','JDBC_USERNAME':'inboxbridge','JDBC_PASSWORD':'smoke-only',
      'HTTP_PORT':'8080','QUARKUS_HTTP_INSECURE_REQUESTS':'enabled',
      'QUARKUS_HTTP_SSL_CERTIFICATE_FILES':'','QUARKUS_HTTP_SSL_CERTIFICATE_KEY_FILES':'',
      'QUARKUS_HTTP_PROXY_PROXY_ADDRESS_FORWARDING':'true','QUARKUS_HTTP_PROXY_ALLOW_FORWARDED':'false',
      'QUARKUS_HTTP_PROXY_ALLOW_X_FORWARDED':'true','QUARKUS_HTTP_PROXY_ENABLE_FORWARDED_HOST':'true',
      'PUBLIC_HOSTNAME':'inboxbridge.example.test','PUBLIC_BASE_URL':'https://inboxbridge.example.test','PUBLIC_PORT':'443',
      'SECURITY_PASSKEY_RP_ID':'inboxbridge.example.test','SECURITY_PASSKEY_ORIGINS':'https://inboxbridge.example.test',
      'GOOGLE_REDIRECT_URI':'https://inboxbridge.example.test/api/google-oauth/callback',
      'MICROSOFT_REDIRECT_URI':'https://inboxbridge.example.test/api/microsoft-oauth/callback',
      'SECURITY_TOKEN_ENCRYPTION_KEY':base64.b64encode(secrets.token_bytes(32)).decode(),
      'SECURITY_ALLOW_ENV_MANAGED_MAILBOX_SECRETS':'false','SECURITY_AUTH_GEO_IP_ENABLED':'false',
      'POLL_ENABLED':'false','MAIL_ACCOUNT_0__ENABLED':'false'},
    'read_only':True,'tmpfs':['/tmp'],'cap_drop':['ALL'],'security_opt':['no-new-privileges:true'],
    'depends_on':{'postgres':{'condition':'service_healthy'}},'networks':['database_net','application_net','outbound_net']},
  'frontend':{'image':os.environ['FRONTEND_IMAGE'],'read_only':True,'tmpfs':['/tmp'],
    'cap_drop':['ALL'],'security_opt':['no-new-privileges:true'],
    'environment':{'PUBLIC_HOSTNAME':'inboxbridge.example.test','TLS_ENABLED':'false','NGINX_ENVSUBST_FILTER':'PUBLIC_HOSTNAME'},
    'ports':[{'target':8080,'published':'0','host_ip':'127.0.0.1','protocol':'tcp'}],
    'depends_on':['inboxbridge'],'networks':['application_net','frontend_net']}},
 'volumes':{'database':{}},
 'networks':{'database_net':{'internal':True},'application_net':{'internal':True},'outbound_net':{},'frontend_net':{}}}
Path(sys.argv[1]).write_text(json.dumps(config))
PY
# Invalid configuration must fail rather than becoming Nginx source or plaintext TLS.
for hostname in '' 'https://example.test' 'example.test;return 200;' 'example.test/path' 'EXAMPLE.test'; do
  if docker run --rm --network none -e TLS_ENABLED=false -e PUBLIC_HOSTNAME="$hostname" "$FRONTEND_IMAGE" nginx -t > "$task_tmp/invalid.log" 2>&1; then
    echo 'Proxy image accepted an invalid PUBLIC_HOSTNAME.' >&2; exit 1
  fi
done
for mode in true auto FALSE ''; do
  if docker run --rm --network none -e PUBLIC_HOSTNAME=inboxbridge.example.test -e TLS_ENABLED="$mode" \
    "$FRONTEND_IMAGE" nginx -t > "$task_tmp/invalid.log" 2>&1; then
    echo 'Frontend accepted missing TLS certificates or an invalid mode.' >&2; exit 1
  fi
done
if docker run --rm --network none -e PUBLIC_HOSTNAME=inboxbridge.example.test \
  "$FRONTEND_IMAGE" nginx -t > "$task_tmp/invalid.log" 2>&1; then
  echo 'Frontend silently downgraded to HTTP without an explicit TLS_ENABLED=false.' >&2; exit 1
fi
compose up -d
address=$(compose port frontend 8080)
ready=false
for attempt in $(seq 1 90); do
  if curl --noproxy '*' --max-time 10 -fsS -H 'Host: inboxbridge.example.test' \
    "http://$address/api/health/summary" > "$task_tmp/health.json" 2>/dev/null; then ready=true; break; fi
  sleep 2
done
if [ "$ready" != true ]; then compose logs --tail 60; exit 1; fi
python3 - "$task_tmp/health.json" <<'PY'
import json, sys
assert json.load(open(sys.argv[1])) == {'status':'UP'}
PY
curl --noproxy '*' --max-time 10 -fsS -D "$task_tmp/headers" -H 'Host: inboxbridge.example.test' \
  -H 'X-Forwarded-Host: attacker.example.test' -H 'X-Forwarded-Proto: http' -H 'Forwarded: proto=http' \
  "http://$address/" > "$task_tmp/index.html"
python3 - "$task_tmp" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1]);assert '<html' in (root/'index.html').read_text()
headers=(root/'headers').read_text().lower()
for name in ('content-security-policy:','strict-transport-security:','x-content-type-options:','x-frame-options:'):
 assert name in headers, name
PY
test "$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' -H 'Host: wrong.example.test' "http://$address/")" = 421
status=$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' -H 'Host: inboxbridge.example.test' "http://$address/api/auth/me")
case "$status" in 401|403) ;; *) echo 'Proxy exposed an authenticated route.' >&2; exit 1;; esac
for service in inboxbridge frontend; do
  test "$(compose exec -T "$service" id -u)" != 0
  compose exec -T "$service" sh -c 'test ! -e /etc/inboxbridge/certs/ca.key'
done
for service in inboxbridge postgres; do
  docker inspect --format '{{json .HostConfig.PortBindings}}' "$(compose ps -q "$service")" > "$task_tmp/ports.json"
  python3 - "$task_tmp/ports.json" <<'PY'
import json, sys
assert not json.load(open(sys.argv[1]))
PY
done
printf '%s\n' 'Proxy images: legacy Java command, read-only non-root services, HTTP/DB settings, canonical host, secure headers and private ports verified.'
