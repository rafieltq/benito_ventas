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

mkdir -p "$certs/traefik"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

has_complete_pair() {
  local name="$1"
  if [[ -s "$certs/traefik/$name.crt" && -s "$certs/traefik/$name.key" ]]; then
    printf 'Keeping existing certificate: %s/traefik/%s.crt\n' "$certs" "$name"
    return 0
  fi
  if [[ -e "$certs/traefik/$name.crt" || -e "$certs/traefik/$name.key" ]]; then
    printf 'Partial Traefik certificate/key pair for %s; refusing to overwrite.\n' "$name" >&2
    exit 1
  fi
  return 1
}

# Preserve the existing self-signed public gateway trust pattern.
if ! has_complete_pair local; then
  (
    umask 077
    openssl req -x509 -newkey rsa:2048 -noenc -sha256 -days 365 \
      -keyout "$tmp/local.key" \
      -out "$tmp/local.crt" \
      -subj "/CN=productos.benito.localhost" \
      -addext "basicConstraints=critical,CA:FALSE" \
      -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
      -addext "extendedKeyUsage=serverAuth" \
      -addext "subjectAltName=DNS:productos.benito.localhost,DNS:auth.benito.localhost,DNS:dashboard.docker.localhost"
  )
  install -m 600 "$tmp/local.key" "$certs/traefik/local.key"
  install -m 644 "$tmp/local.crt" "$certs/traefik/local.crt"
fi

issue_certificate() {
  local name="$1" cn="$2" usage="$3" san="$4"
  if has_complete_pair "$name"; then
    return
  fi
  (
    umask 077
    openssl req -new -newkey rsa:2048 -noenc \
      -keyout "$tmp/$name.key" \
      -out "$tmp/$name.csr" \
      -subj "/CN=$cn"
  )
  openssl x509 -req \
    -in "$tmp/$name.csr" \
    -CA "$certs/ca/ca.crt" \
    -CAkey "$certs/ca/ca.key" \
    -CAcreateserial \
    -out "$tmp/$name.crt" \
    -days 365 -sha256 \
    -extfile <(printf '%s\n' \
      'basicConstraints=critical,CA:FALSE' \
      'keyUsage=critical,digitalSignature,keyEncipherment' \
      "extendedKeyUsage=$usage" \
      "subjectAltName=$san")
  install -m 600 "$tmp/$name.key" "$certs/traefik/$name.key"
  install -m 644 "$tmp/$name.crt" "$certs/traefik/$name.crt"
}

# Traefik presents this client certificate to all internal mTLS backends.
issue_certificate client traefik clientAuth DNS:traefik
# This separate public certificate is also reused by setup-clientes.sh.
issue_certificate clientes-local clientes.benito.localhost serverAuth DNS:clientes.benito.localhost
issue_certificate admin-local admin.benito.localhost serverAuth DNS:admin.benito.localhost

cp "$certs/ca/ca.crt" "$certs/traefik/ca.crt"
chmod 644 "$certs/traefik/ca.crt"
chmod 600 "$certs/traefik/local.key" "$certs/traefik/client.key" "$certs/traefik/clientes-local.key"

printf 'Traefik certificates ready under %s/traefik. No existing complete key pairs were replaced.\n' "$certs"
printf 'Trust traefik/local.crt for Auth/Productos/dashboard and ca/ca.crt for Clientes in your HTTP client.\n'
