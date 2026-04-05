#!/bin/sh
set -eu

apk add --no-cache openssl >/dev/null

mkdir -p /certs

PUBLIC_HOSTNAME="${PUBLIC_HOSTNAME:-localhost}"
PUBLIC_PORT="${PUBLIC_PORT:-3000}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://${PUBLIC_HOSTNAME}:${PUBLIC_PORT}}"
TLS_FRONTEND_CERT_HOSTNAMES="${TLS_FRONTEND_CERT_HOSTNAMES:-}"
TLS_BACKEND_CERT_HOSTNAMES="${TLS_BACKEND_CERT_HOSTNAMES:-}"

if [ ! -f /certs/ca.key ] || [ ! -f /certs/ca.crt ]; then
  openssl genrsa -out /certs/ca.key 4096 >/dev/null 2>&1
  openssl req -x509 -new -nodes -key /certs/ca.key -sha256 -days 3650 \
    -subj "/CN=InboxBridge Local CA" \
    -out /certs/ca.crt >/dev/null 2>&1
fi

trim() {
  printf '%s' "$1" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

host_from_url() {
  value="$(trim "$1")"
  value="${value#*://}"
  value="${value%%/*}"
  value="${value%%\?*}"
  value="${value%%#*}"
  if printf '%s' "$value" | grep -q '^\['; then
    value="${value#\[}"
    value="${value%%]*}"
  else
    value="${value%%:*}"
  fi
  trim "$value"
}

append_unique_csv() {
  current="$1"
  candidate="$(trim "$2")"

  if [ -z "$candidate" ]; then
    printf '%s' "$current"
    return
  fi

  OLD_IFS="$IFS"
  IFS=','
  for existing in $current; do
    if [ "$(trim "$existing")" = "$candidate" ]; then
      IFS="$OLD_IFS"
      printf '%s' "$current"
      return
    fi
  done
  IFS="$OLD_IFS"

  if [ -z "$current" ]; then
    printf '%s' "$candidate"
  else
    printf '%s,%s' "$current" "$candidate"
  fi
}

merge_hostnames() {
  merged=""
  for raw_list in "$@"; do
    OLD_IFS="$IFS"
    IFS=','
    for raw_value in $raw_list; do
      merged="$(append_unique_csv "$merged" "$raw_value")"
    done
    IFS="$OLD_IFS"
  done
  printf '%s' "$merged"
}

san_entry_for_host() {
  host="$(trim "$1")"
  if [ -z "$host" ]; then
    return
  fi
  if printf '%s' "$host" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
    printf 'IP:%s' "$host"
  else
    printf 'DNS:%s' "$host"
  fi
}

san_from_hosts() {
  san=""
  OLD_IFS="$IFS"
  IFS=','
  for raw_host in $1; do
    entry="$(san_entry_for_host "$raw_host")"
    if [ -z "$entry" ]; then
      continue
    fi
    if [ -z "$san" ]; then
      san="$entry"
    else
      san="${san},${entry}"
    fi
  done
  IFS="$OLD_IFS"
  printf '%s' "$san"
}

cert_matches_expected_hosts() {
  cert_path="$1"
  expected_hosts="$2"

  if [ ! -f "$cert_path" ]; then
    return 1
  fi

  san_output="$(openssl x509 -in "$cert_path" -noout -ext subjectAltName 2>/dev/null || true)"
  if [ -z "$san_output" ]; then
    return 1
  fi

  OLD_IFS="$IFS"
  IFS=','
  for raw_host in $expected_hosts; do
    host="$(trim "$raw_host")"
    if [ -z "$host" ]; then
      continue
    fi
    entry="$(san_entry_for_host "$host")"
    if ! printf '%s' "$san_output" | grep -Fq "$entry"; then
      IFS="$OLD_IFS"
      return 1
    fi
  done
  IFS="$OLD_IFS"

  return 0
}

generate_signed_cert() {
  name="$1"
  cn="$2"
  hosts="$3"
  san="$(san_from_hosts "$hosts")"

  if [ -z "$san" ]; then
    echo "No certificate SAN entries configured for ${name}" >&2
    exit 1
  fi

  if [ -f "/certs/${name}.key" ] && cert_matches_expected_hosts "/certs/${name}.crt" "$hosts"; then
    return
  fi

  rm -f "/certs/${name}.key" "/certs/${name}.crt" "/tmp/${name}.csr" "/tmp/${name}.cnf"

  cat >"/tmp/${name}.cnf" <<EOF
[req]
distinguished_name = dn
prompt = no
req_extensions = req_ext

[dn]
CN = ${cn}

[req_ext]
subjectAltName = ${san}
EOF

  openssl genrsa -out "/certs/${name}.key" 2048 >/dev/null 2>&1
  openssl req -new -key "/certs/${name}.key" -out "/tmp/${name}.csr" -config "/tmp/${name}.cnf" >/dev/null 2>&1
  openssl x509 -req -in "/tmp/${name}.csr" -CA /certs/ca.crt -CAkey /certs/ca.key -CAcreateserial \
    -out "/certs/${name}.crt" -days 825 -sha256 -copy_extensions copy >/dev/null 2>&1
}

public_host="$(host_from_url "$PUBLIC_BASE_URL")"
frontend_hosts="$(merge_hostnames "localhost,inboxbridge-admin" "$public_host" "$TLS_FRONTEND_CERT_HOSTNAMES")"
backend_hosts="$(merge_hostnames "localhost,inboxbridge" "$public_host" "$TLS_BACKEND_CERT_HOSTNAMES")"
frontend_cn="$(trim "${public_host:-localhost}")"

generate_signed_cert backend inboxbridge "$backend_hosts"
generate_signed_cert frontend "$frontend_cn" "$frontend_hosts"
