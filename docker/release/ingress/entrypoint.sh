#!/bin/sh
set -eu
# Default stays fail-closed TLS; missing certificates never imply an HTTP fallback.
case "${TLS_ENABLED-true}" in
  true)
    for leaf in frontend.crt frontend.key ca.crt; do
      if [ ! -s "/etc/inboxbridge/certs/$leaf" ] || [ ! -r "/etc/inboxbridge/certs/$leaf" ]; then
        echo 'TLS_ENABLED=true requires readable frontend.crt, frontend.key and ca.crt in /etc/inboxbridge/certs.' >&2
        exit 1
      fi
    done
    cp /etc/nginx/inboxbridge/internal-tls.conf /tmp/inboxbridge-ingress.conf
    ;;
  false)
    # A hostname is configuration data, never arbitrary Nginx source.
    case "${PUBLIC_HOSTNAME:-}" in
      ''|*[!a-z0-9.-]*|.*|*..*|-*|*-)
        echo 'PUBLIC_HOSTNAME must be a lowercase DNS hostname without scheme, port or path.' >&2
        exit 1
        ;;
    esac
    # Expand only the validated hostname; preserve Nginx variables verbatim.
    envsubst '${PUBLIC_HOSTNAME}' < /etc/nginx/inboxbridge/proxy.conf.template > /tmp/inboxbridge-ingress.conf
    ;;
  *)
    echo 'TLS_ENABLED must be exactly true or false.' >&2
    exit 1
    ;;
esac
exec "$@"
