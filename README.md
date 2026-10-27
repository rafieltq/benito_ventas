Benito Ventas
---

Prueba técnica para el equipo de Toke

Stack
---
    • Lenguaje de programación: Java
    • Framework: Quarkus
    • Base de datos: MongoDB
    • Sistema de pub-sub: Apache Kafka

Ejecución local con Docker Compose
---
Desde la raíz del repositorio:

```bash
docker compose up --build -d
```

TLS/mTLS
---

```markdown

Cliente ── TLS ──→ Traefik ── mTLS ──→ Productos:8443
```
TLS cifra la comunicación entre el cliente y Traefik como gateway. El cliente verifica el certificado de servidor de Traefik; en este entorno local se configura explícitamente la confianza en su certificado autofirmado. En la comunicación interna, Traefik verifica el certificado de servidor de Productos y presenta su certificado cliente, que Productos también verifica. Ambos certificados internos están firmados por nuestra CA privada.

## Configuración
- compose.yml: red, montajes y labels de enrutamiento.
- infra/traefik/dynamic/tls.yaml: certificado público y ServersTransport.
- productos/src/main/resources/application.properties: HTTPS y certificado cliente obligatorio.

## Generación de certificados
### Preparar el directorio fuera del repositorio para almacenar los certificados y claves privadas
```bash
mkdir -p ~/certs/{ca,traefik,productos}
cd ~/certs
```

### CA: ca.crt y ca.key; su clave privada no se monta en contenedores.
```bash
openssl req -x509 -newkey rsa:2048 -sha256 -days 365 \
  -keyout ca/ca.key \
  -out ca/ca.crt \
  -subj "/CN=Benito Ventas Local CA" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign"

chmod 600 ca/ca.key
```
### Traefik, entrada pública: local.crt y local.key.
```bash
openssl req -x509 -newkey rsa:2048 -noenc -sha256 -days 365 \
  -keyout traefik/local.key \
  -out traefik/local.crt \
  -subj "/CN=productos.benito.localhost" \
  -addext "basicConstraints=critical,CA:FALSE" \
  -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage=serverAuth" \
  -addext "subjectAltName=DNS:productos.benito.localhost,DNS:dashboard.docker.localhost"

chmod 600 traefik/local.key
```
Para confiar en esta entrada, Postman utiliza traefik/local.crt, no la CA interna.
### Productos: server.crt, server.key y ca.crt.
```bash
openssl req -new -newkey rsa:2048 -noenc \
  -keyout productos/server.key \
  -out productos/server.csr \
  -subj "/CN=productos"

```
#### Firmar con CA
```bash
openssl x509 -req \
  -in productos/server.csr \
  -CA ca/ca.crt \
  -CAkey ca/ca.key \
  -CAcreateserial \
  -out productos/server.crt \
  -days 365 -sha256 \
  -extfile <(printf '%s\n' \
    'basicConstraints=critical,CA:FALSE' \
    'keyUsage=critical,digitalSignature,keyEncipherment' \
    'extendedKeyUsage=serverAuth' \
    'subjectAltName=DNS:productos')
```
### Copiar el certificado público de la CA para que Productos pueda verificar los certificados cliente que recibe.
```bash
cp ca/ca.crt productos/ca.crt
chmod 600 productos/server.key
```

### Traefik, cliente interno: client.crt, client.key y ca.crt.
```bash
openssl req -new -newkey rsa:2048 -noenc \
  -keyout traefik/client.key \
  -out traefik/client.csr \
  -subj "/CN=traefik"
```
#### Firmar con CA
```bash
openssl x509 -req \
  -in traefik/client.csr \
  -CA ca/ca.crt \
  -CAkey ca/ca.key \
  -CAcreateserial \
  -out traefik/client.crt \
  -days 365 -sha256 \
  -extfile <(printf '%s\n' \
    'basicConstraints=critical,CA:FALSE' \
    'keyUsage=critical,digitalSignature' \
    'extendedKeyUsage=clientAuth')
```
#### Preparar confianza y permisos
```bash
cp ca/ca.crt traefik/ca.crt
chmod 600 traefik/client.key
```
## Otorgar permiso de lectura para el UID del proceso de quarkus en el entorno
```bash
# Verificar UID del proceso de Quarkus
docker compose run --rm --no-deps --entrypoint sh productos -c 'id'

# Otorgar permiso al proceso
setfacl -m u:<uid>:r productos/server.key


# Comprobar el acceso al archivo
docker compose run --rm --no-deps --entrypoint sh productos -c '
  id
  ls -ln /certs/server.key
  if test -r /certs/server.key; then
    echo "Clave legible"
  else
    echo "Clave NO legible"
  fi
'
```
## Verificación de mTLS
Al retirar temporalmente el certificado cliente del ServersTransport y reiniciar Traefik, la petición devuelve 502. El log confirma que Productos rechaza la conexión interna por falta del certificado cliente:
```bash
traefik  | 2026-10-27T11:48:59Z DBG github.com/traefik/traefik/v3/pkg/proxy/httputil/proxy.go:199 > 502 Bad Gateway error="remote error: tls: certificate required"
```

Con certificado cliente: al restaurarlo y reiniciar Traefik, la misma petición devuelve 200 OK, utilizando el destino HTTPS de Productos:
```bash
traefik      | 172.25.0.1 - - [27/Oct/2026:13:28:48 +0000] "GET /productos HTTP/2.0" 200 273 "-" "-" 3 "productos@docker" "https://172.25.0.4:8443" 23ms
```
