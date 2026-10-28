Benito Ventas
---

Prueba técnica para el equipo de Toke

## Índice

- [Stack](#stack)
- [Ejecución local con Docker Compose](#ejecución-local-con-docker-compose)
- [Clientes y registro recuperable](#clientes-y-registro-recuperable)
- [Servicio Auth](#servicio-auth)
- [TLS/mTLS](#tlsmtls)
  - [Configuración](#configuración)
  - [Generación de certificados](#generación-de-certificados)
    - [Scripts de preparación](#scripts-de-preparación)
  - [Permisos de lectura para Quarkus](#otorgar-permiso-de-lectura-para-el-uid-del-proceso-de-quarkus-en-el-entorno)
  - [Verificación de mTLS](#verificación-de-mtls)

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

Clientes y registro recuperable
---
Clientes tiene MongoDB propio, CRUD en un único recurso, validación JWT y el mismo TLS/mTLS de los otros servicios. Auth completa el registro creando el cliente antes de activar el usuario; un signup pendiente se recupera con correo y contraseña correctos. Los logins de cliente y administrador devuelven mensaje y token.

Servicio Auth
---
`auth` se publica en `https://auth.benito.localhost` mediante Traefik y mTLS interno, igual que Productos. `auth-mongodb` usa MongoDB 8.0 independiente, base `auth`, volúmenes persistentes y un healthcheck de ping. Auth espera a que MongoDB esté saludable. Ninguno publica puertos directamente en el host.

Antes de levantar Auth:
1. Preparar los certificados de Auth y actualizar el certificado público de Traefik según las instrucciones siguientes. Si ya existe, regenerarlo incluyendo `auth.benito.localhost` en sus SAN y actualizar la confianza en el cliente.
2. Desde `auth/`, ejecutar `./mvnw package` para generar `target/quarkus-app`, que utiliza el Dockerfile JVM. Resolver primero cualquier error de compilación del código de Auth.
3. Desde la raíz, ejecutar `docker compose up --build -d auth-mongodb auth traefik`.

La configuración TLS de Auth aplica tanto al servicio empaquetado como al modo de desarrollo. Los certificados se montan desde `~/certs/auth`, con acceso de lectura para el UID 185 del contenedor. Auth también monta las claves JWT externas y utiliza una identidad cliente mTLS propia para contactar a Clientes; preparar estos archivos según [docs/clientes.md](docs/clientes.md).

TLS/mTLS
---

```markdown
                             |---> mTLS --> Facturacion:8443
                             |---> mTLS ---> Clientes:8443
Cliente --- TLS --- Traefik --- mTLS ---> Productos:8443
                             |---> mTLS ---> Auth:8443


                             |---> mTLS --> Facturacion:8443
                             |---> mTLS ---> Clientes:8443
Cliente Admin --- mTLS --- Traefik --- mTLS ---> Productos:8443
                             |---> mTLS ---> Auth:8443
```

TLS cifra la comunicación entre el cliente y Traefik como gateway. El cliente verifica el certificado de servidor de Traefik; en este entorno local se configura explícitamente la confianza en su certificado autofirmado. En la comunicación interna, Traefik verifica el certificado de servidor de Productos y presenta su certificado cliente, que Productos también verifica. Ambos certificados internos están firmados por nuestra CA privada.

## Configuración
- compose.yml: red, montajes y labels de enrutamiento.
- infra/traefik/dynamic/tls.yaml: certificado público y ServersTransport.
- productos/src/main/resources/application.properties: HTTPS y certificado cliente obligatorio.
- auth/src/main/resources/application.properties: HTTPS, certificado cliente obligatorio y cliente mTLS hacia Clientes.
- clientes/src/main/resources/application.properties: HTTPS, certificado cliente obligatorio y verificación JWT.

## Generación de certificados
### Preparar el directorio fuera del repositorio para almacenar los certificados y claves privadas
```bash
mkdir -p ~/certs/{ca,traefik,productos,auth}
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
  -addext "subjectAltName=DNS:productos.benito.localhost,DNS:auth.benito.localhost,DNS:dashboard.docker.localhost"

chmod 600 traefik/local.key
```
Para confiar en esta entrada, Postman utiliza traefik/local.crt, no la CA interna.

### Scripts de preparación
Con la CA existente preparada, ejecutar desde la raíz del repositorio:

```bash
bash infra/certs/setup-traefik.sh
bash infra/certs/setup-productos.sh
bash infra/certs/setup-clientes.sh
```

Los scripts usan `~/certs` (o `CERTS_DIR`), solicitan localmente la contraseña de la CA cuando necesitan firmar y conservan los pares completos existentes. No renuevan certificados existentes ni sobrescriben pares incompletos. Las claves privadas no se guardan en el repositorio. Si se cambia `CERTS_DIR`, también hay que ajustar los montajes de Compose.

`setup-traefik.sh` prepara la entrada pública de Auth/Productos/dashboard, la identidad cliente mTLS de Traefik, el certificado público separado de Clientes y la CA de confianza. `setup-productos.sh` prepara el certificado servidor de Productos, copia la CA y concede lectura de la clave al UID 185 cuando `setfacl` está disponible. La creación inicial de la CA y del certificado servidor de Auth sigue documentada abajo.
### Auth: server.crt, server.key y ca.crt.
Usa la misma CA interna y verifica el certificado cliente de Traefik. Desde `~/certs`:
```bash
mkdir -p ~/certs/auth
cd ~/certs

openssl req -new -newkey rsa:2048 -noenc \
  -keyout auth/server.key \
  -out auth/server.csr \
  -subj "/CN=auth"

openssl x509 -req \
  -in auth/server.csr \
  -CA ca/ca.crt \
  -CAkey ca/ca.key \
  -CAcreateserial \
  -out auth/server.crt \
  -days 365 -sha256 \
  -extfile <(printf '%s\n' \
    'basicConstraints=critical,CA:FALSE' \
    'keyUsage=critical,digitalSignature,keyEncipherment' \
    'extendedKeyUsage=serverAuth' \
    'subjectAltName=DNS:auth')

cp ca/ca.crt auth/ca.crt
chmod 600 auth/server.key
setfacl -m u:185:r auth/server.key
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
