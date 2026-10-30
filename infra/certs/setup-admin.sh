#!/usr/bin/env bash
set -euo pipefail
umask 077

# Administrative identities use a separate CA, outside the repository.
# OpenSSL prompts for private-key passphrases; never pass them on the command line.
certs="${CERTS_DIR:-$HOME/certs}"
admin="$certs/admin"
command -v openssl >/dev/null

mkdir -p "$admin"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

has_complete_pair() {
  local name="$1"
  if [[ -s "$admin/$name.crt" && -s "$admin/$name.key" ]]; then
    printf 'Keeping existing certificate: %s/%s.crt\n' "$admin" "$name"
    return 0
  fi
  if [[ -e "$admin/$name.crt" || -e "$admin/$name.key" ]]; then
    printf 'Partial administrative certificate/key pair for %s; refusing to overwrite.\n' "$name" >&2
    exit 1
  fi
  return 1
}

if ! has_complete_pair ca; then
  # Stage the encrypted key and certificate before installing either file.
  openssl req -x509 -newkey rsa:3072 -sha256 -days 3650 \
    -keyout "$tmp/ca.key" \
    -out "$tmp/ca.crt" \
    -subj "/CN=Benito Administrative CA" \
    -addext 'basicConstraints=critical,CA:TRUE,pathlen:0' \
    -addext 'keyUsage=critical,keyCertSign,cRLSign'
  install -m 600 "$tmp/ca.key" "$admin/ca.key"
  install -m 644 "$tmp/ca.crt" "$admin/ca.crt"
fi

if ! has_complete_pair operador; then
  openssl req -new -newkey rsa:3072 -sha256 \
    -keyout "$tmp/operador.key" \
    -out "$tmp/operador.csr" \
    -subj "/CN=benito-admin"
  openssl x509 -req \
    -in "$tmp/operador.csr" \
    -CA "$admin/ca.crt" \
    -CAkey "$admin/ca.key" \
    -CAcreateserial \
    -out "$tmp/operador.crt" \
    -days 365 -sha256 \
    -extfile <(printf '%s\n' \
      'basicConstraints=critical,CA:FALSE' \
      'keyUsage=critical,digitalSignature' \
      'extendedKeyUsage=clientAuth')
  openssl verify -purpose sslclient -CAfile "$admin/ca.crt" "$tmp/operador.crt"
  install -m 600 "$tmp/operador.key" "$admin/operador.key"
  install -m 644 "$tmp/operador.crt" "$admin/operador.crt"
fi

openssl verify -purpose sslclient -CAfile "$admin/ca.crt" "$admin/operador.crt"
printf 'Administrative certificates ready under %s. No existing complete key pairs were replaced.\n' "$admin"
printf 'Traefik only needs the public admin/ca.crt; never mount admin/ca.key or operador.key in Traefik.\n'
