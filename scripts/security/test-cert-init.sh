#!/bin/sh
# Exercise certificate isolation using disposable in-memory keys, not deployment secrets.
set -eu
root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
docker run --rm -i --tmpfs /certs -v "$root/docker/cert-init.sh:/cert-init.sh:ro" \
  -e PUBLIC_HOSTNAME=security-test.example alpine:3.24 sh <<'SH'
set -eu
sh /cert-init.sh
test "$(stat -c '%a' /certs/ca.key)" = 600
for name in backend frontend postgres; do
  test "$(find "/certs/$name" -type f | wc -l | tr -d ' ')" = 3
  test ! -e "/certs/$name/ca.key"
  test "$(stat -c '%a' "/certs/$name/$name.key")" = 640
  openssl verify -CAfile "/certs/$name/ca.crt" "/certs/$name/$name.crt"
done
test "$(stat -c '%g' /certs/backend/backend.key)" = 10001
test "$(stat -c '%g' /certs/frontend/frontend.key)" = 101
openssl x509 -in /certs/frontend/frontend.crt -noout -checkhost security-test.example
before="$(sha256sum /certs/ca.crt /certs/backend/backend.crt /certs/backend/backend.key)"
sh /cert-init.sh
test "$before" = "$(sha256sum /certs/ca.crt /certs/backend/backend.crt /certs/backend/backend.key)"
echo 'Certificate isolation, permissions, hostname and repeat bootstrap checks passed.'
SH
