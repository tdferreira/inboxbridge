#!/bin/sh
set -eu

apk add --no-cache openssl >/dev/null

mkdir -p /certs

if [ ! -f /certs/ca.key ] || [ ! -f /certs/ca.crt ]; then
  openssl genrsa -out /certs/ca.key 4096 >/dev/null 2>&1
  openssl req -x509 -new -nodes -key /certs/ca.key -sha256 -days 3650 \
    -subj "/CN=InboxBridge Local CA" \
    -out /certs/ca.crt >/dev/null 2>&1
fi

generate_signed_cert() {
  name="$1"
  cn="$2"
  san="$3"

  if [ -f "/certs/${name}.key" ] && [ -f "/certs/${name}.crt" ]; then
    return
  fi

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

generate_signed_cert backend inboxbridge "DNS:inboxbridge,DNS:localhost"
generate_signed_cert frontend localhost "DNS:localhost,DNS:inboxbridge-admin"
