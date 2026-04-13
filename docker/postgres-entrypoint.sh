#!/bin/bash
set -euo pipefail

cp /etc/inboxbridge/certs/postgres.crt /var/lib/postgresql/data/server.crt
cp /etc/inboxbridge/certs/postgres.key /var/lib/postgresql/data/server.key
chown postgres:postgres /var/lib/postgresql/data/server.crt /var/lib/postgresql/data/server.key
chmod 600 /var/lib/postgresql/data/server.key

exec /usr/local/bin/docker-entrypoint.sh postgres \
  -c ssl=on \
  -c ssl_cert_file=/var/lib/postgresql/data/server.crt \
  -c ssl_key_file=/var/lib/postgresql/data/server.key
