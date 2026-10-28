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

mkdir -p "$certs/facturacion"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

if [[ -s "$certs/facturacion/server.crt" && -s "$certs/facturacion/server.key" ]]; then
  printf 'Keeping existing certificate: %s/facturacion/server.crt\n' "$certs"
elif [[ -e "$certs/facturacion/server.crt" || -e "$certs/facturacion/server.key" ]]; then
  printf 'Partial facturacion certificate/key pair; refusing to overwrite.\n' >&2
  exit 1
else
  # Stage the pair so a failed CA signing operation does not leave a partial setup.
  (
    umask 077
    openssl req -new -newkey rsa:2048 -noenc \
      -keyout "$tmp/server.key" \
      -out "$tmp/server.csr" \
      -subj "/CN=facturacion"
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
      'subjectAltName=DNS:facturacion')
  install -m 600 "$tmp/server.key" "$certs/facturacion/server.key"
  install -m 644 "$tmp/server.crt" "$certs/facturacion/server.crt"
fi

cp "$certs/ca/ca.crt" "$certs/facturacion/ca.crt"
chmod 644 "$certs/facturacion/ca.crt"
chmod 600 "$certs/facturacion/server.key"

# Packaged Quarkus images run as UID 185. Dev containers run as root.
if command -v setfacl >/dev/null; then
  setfacl -m u:185:r "$certs/facturacion/server.key"
else
  printf 'Grant UID 185 read access to the facturacion server key before running packaged images.\n' >&2
fi
printf 'facturacion certificates ready under %s/facturacion. No existing complete key pairs were replaced.\n' "$certs"
