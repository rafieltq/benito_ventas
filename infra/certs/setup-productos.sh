#!/usr/bin/env bash
set -euo pipefail

# Local-development certificates stay outside the repository.
# OpenSSL prompts for the existing CA passphrase when signing is required.
certs="${CERTS_DIR:-$HOME/certs}"
if [[ ! -s "$certs/ca/ca.crt" || ! -s "$certs/ca/ca.key" ]]; then
  printf 'Missing existing CA certificate/key under %s/ca\n' "$certs" >&2
  exit 1
fi
command -v openssl >/dev/null

mkdir -p "$certs/productos"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

if [[ -s "$certs/productos/server.crt" && -s "$certs/productos/server.key" ]]; then
  printf 'Keeping existing certificate: %s/productos/server.crt\n' "$certs"
elif [[ -e "$certs/productos/server.crt" || -e "$certs/productos/server.key" ]]; then
  printf 'Partial Productos certificate/key pair; refusing to overwrite.\n' >&2
  exit 1
else
  # Stage the pair so a failed CA signing operation does not leave a partial setup.
  (
    umask 077
    openssl req -new -newkey rsa:2048 -noenc \
      -keyout "$tmp/server.key" \
      -out "$tmp/server.csr" \
      -subj "/CN=productos"
  )
  openssl x509 -req \
    -in "$tmp/server.csr" \
    -CA "$certs/ca/ca.crt" \
    -CAkey "$certs/ca/ca.key" \
    -CAcreateserial \
    -out "$tmp/server.crt" \
    -days 365 -sha256 \
    -extfile <(printf '%s\n' \
      'basicConstraints=critical,CA:FALSE' \
      'keyUsage=critical,digitalSignature,keyEncipherment' \
      'extendedKeyUsage=serverAuth' \
      'subjectAltName=DNS:productos')
  install -m 600 "$tmp/server.key" "$certs/productos/server.key"
  install -m 644 "$tmp/server.crt" "$certs/productos/server.crt"
fi

cp "$certs/ca/ca.crt" "$certs/productos/ca.crt"
chmod 644 "$certs/productos/ca.crt"
chmod 600 "$certs/productos/server.key"

# Packaged Quarkus images run as UID 185. Dev containers run as root.
if command -v setfacl >/dev/null; then
  setfacl -m u:185:r "$certs/productos/server.key"
else
  printf 'Grant UID 185 read access to the Productos server key before running packaged images.\n' >&2
fi
printf 'Productos certificates ready under %s/productos. No existing complete key pairs were replaced.\n' "$certs"
