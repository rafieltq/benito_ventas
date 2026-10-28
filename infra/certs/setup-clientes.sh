#!/usr/bin/env bash
set -euo pipefail

# Local-development certificates and JWT keys stay outside the repository.
# OpenSSL prompts for the existing CA passphrase; never pass it on the command line.
certs="${CERTS_DIR:-$HOME/certs}"
if [[ ! -s "$certs/ca/ca.crt" || ! -s "$certs/ca/ca.key" ]]; then
  printf 'Missing existing CA certificate/key under %s/ca\n' "$certs" >&2
  exit 1
fi
command -v openssl >/dev/null

tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

issue_certificate() {
  local directory="$1" name="$2" cn="$3" usage="$4" san="$5"
  mkdir -p "$directory"
  if [[ -s "$directory/$name.crt" && -s "$directory/$name.key" ]]; then
    printf 'Keeping existing certificate: %s/%s.crt\n' "$directory" "$name"
    return
  fi
  if [[ -e "$directory/$name.crt" || -e "$directory/$name.key" ]]; then
    printf 'Partial certificate/key pair at %s/%s; refusing to overwrite.\n' "$directory" "$name" >&2
    exit 1
  fi
  (
    umask 077
    openssl req -new -newkey rsa:2048 -noenc \
      -keyout "$directory/$name.key" \
      -out "$tmp/$cn.csr" \
      -subj "/CN=$cn"
  )
  openssl x509 -req \
    -in "$tmp/$cn.csr" \
    -CA "$certs/ca/ca.crt" \
    -CAkey "$certs/ca/ca.key" \
    -CAcreateserial \
    -out "$directory/$name.crt" \
    -days 365 -sha256 \
    -extfile <(printf '%s\n' \
      'basicConstraints=critical,CA:FALSE' \
      'keyUsage=critical,digitalSignature,keyEncipherment' \
      "extendedKeyUsage=$usage" \
      "subjectAltName=$san")
  chmod 644 "$directory/$name.crt"
  chmod 600 "$directory/$name.key"
}

# Separate client identity for Auth's outbound mTLS calls to Clientes.
issue_certificate "$certs/auth" client auth-client clientAuth DNS:auth
issue_certificate "$certs/clientes" server clientes serverAuth DNS:clientes
# Separate gateway certificate: do not rotate the existing Auth/Productos gateway certificate.
issue_certificate "$certs/traefik" clientes-local clientes.benito.localhost serverAuth DNS:clientes.benito.localhost
cp "$certs/ca/ca.crt" "$certs/clientes/ca.crt"

mkdir -p "$certs/jwt"
if [[ ! -s "$certs/jwt/privateKey.pem" ]]; then
  if [[ -e "$certs/jwt/privateKey.pem" || -e "$certs/jwt/publicKey.pem" ]]; then
    printf 'Partial JWT key pair under %s/jwt; refusing to overwrite.\n' "$certs" >&2
    exit 1
  fi
  (
    umask 077
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
      -out "$certs/jwt/privateKey.pem"
  )
fi
if [[ ! -s "$certs/jwt/publicKey.pem" ]]; then
  openssl pkey -in "$certs/jwt/privateKey.pem" -pubout -out "$certs/jwt/publicKey.pem"
fi
chmod 600 "$certs/jwt/privateKey.pem"
chmod 644 "$certs/jwt/publicKey.pem"

# Packaged Quarkus images run as UID 185. Dev containers run as root.
if command -v setfacl >/dev/null; then
  setfacl -m u:185:r "$certs/auth/client.key" "$certs/clientes/server.key" "$certs/jwt/privateKey.pem"
else
  printf 'Grant UID 185 read access to Auth client, Clientes server, and JWT private keys before running packaged images.\n' >&2
fi
printf 'Certificates and JWT keys ready under %s. No existing complete key pairs were replaced.\n' "$certs"
