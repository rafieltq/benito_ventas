#!/usr/bin/env bash
set -euo pipefail
umask 077

# Kafka uses the existing services CA, never the administrative CA.
# Certificates, stores, and password files stay outside the repository.
certs="${CERTS_DIR:-$HOME/certs}"
kafka="$certs/kafka"
kafka_uid="${KAFKA_UID:-1000}"
if [[ ! -s "$certs/ca/ca.crt" || ! -s "$certs/ca/ca.key" ]]; then
  printf 'Missing existing CA certificate/key under %s/ca\n' "$certs" >&2
  exit 1
fi
if [[ ! "$kafka_uid" =~ ^[0-9]+$ ]]; then
  printf 'KAFKA_UID must be a numeric user ID.\n' >&2
  exit 1
fi
command -v openssl >/dev/null
command -v keytool >/dev/null

mkdir -p "$kafka"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

files=(kafka.keystore.p12 kafka.truststore.p12 keystore.password truststore.password)
existing=0
for name in "${files[@]}"; do
  if [[ -e "$kafka/$name" || -L "$kafka/$name" ]]; then
    existing=$((existing + 1))
  fi
done

if (( existing > 0 )); then
  for name in "${files[@]}"; do
    if [[ ! -s "$kafka/$name" ]]; then
      printf 'Partial Kafka TLS setup under %s; refusing to overwrite.\n' "$kafka" >&2
      exit 1
    fi
  done
  printf 'Keeping existing Kafka stores and password files under %s\n' "$kafka"
else
  # Stage all artifacts so a failed signing operation leaves no partial setup.
  # OpenSSL prompts for the existing CA passphrase; never pass it on the command line.
  openssl req -new -newkey rsa:3072 -noenc -sha256 \
    -keyout "$tmp/kafka.key" \
    -out "$tmp/kafka.csr" \
    -subj '/CN=kafka'
  openssl x509 -req \
    -in "$tmp/kafka.csr" \
    -CA "$certs/ca/ca.crt" \
    -CAkey "$certs/ca/ca.key" \
    -CAcreateserial \
    -out "$tmp/kafka.crt" \
    -days 365 -sha256 \
    -extfile <(printf '%s\n' \
      'basicConstraints=critical,CA:FALSE' \
      'keyUsage=critical,digitalSignature,keyEncipherment' \
      'extendedKeyUsage=serverAuth,clientAuth' \
      'subjectAltName=DNS:kafka')
  openssl verify -purpose sslserver -verify_hostname kafka \
    -CAfile "$certs/ca/ca.crt" "$tmp/kafka.crt"
  openssl verify -purpose sslclient -CAfile "$certs/ca/ca.crt" "$tmp/kafka.crt"

  openssl rand -hex 32 > "$tmp/keystore.password"
  openssl rand -hex 32 > "$tmp/truststore.password"
  openssl pkcs12 -export -name kafka \
    -inkey "$tmp/kafka.key" \
    -in "$tmp/kafka.crt" \
    -certfile "$certs/ca/ca.crt" \
    -out "$tmp/kafka.keystore.p12" \
    -passout "file:$tmp/keystore.password"
  keytool -importcert -noprompt -alias services-ca \
    -file "$certs/ca/ca.crt" \
    -keystore "$tmp/kafka.truststore.p12" \
    -storetype PKCS12 \
    -storepass:file "$tmp/truststore.password"
  keytool -list -alias kafka -keystore "$tmp/kafka.keystore.p12" \
    -storetype PKCS12 -storepass:file "$tmp/keystore.password" >/dev/null

  for name in "${files[@]}"; do
    install -m 600 "$tmp/$name" "$kafka/$name"
  done
fi

keytool -list -alias kafka -keystore "$kafka/kafka.keystore.p12" \
  -storetype PKCS12 -storepass:file "$kafka/keystore.password" >/dev/null
keytool -list -alias services-ca -keystore "$kafka/kafka.truststore.p12" \
  -storetype PKCS12 -storepass:file "$kafka/truststore.password" >/dev/null

# The Apache Kafka image currently runs as UID 1000. Override KAFKA_UID if needed.
for name in "${files[@]}"; do
  chmod 600 "$kafka/$name"
done
if command -v setfacl >/dev/null; then
  setfacl -m "u:$kafka_uid:rx" "$kafka"
  for name in "${files[@]}"; do
    setfacl -m "u:$kafka_uid:r" "$kafka/$name"
  done
else
  printf 'Grant UID %s access to the Kafka directory and its four files before starting the container.\n' "$kafka_uid" >&2
fi

printf 'Kafka PKCS12 stores ready under %s. No existing complete setup was replaced.\n' "$kafka"
printf 'Mount this directory at /etc/kafka/secrets. Do not mount the CA private key.\n'
printf 'Use keystore.password for both Kafka key and keystore credentials, and truststore.password for truststore credentials.\n'
