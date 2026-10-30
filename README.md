# Benito Ventas

Prueba técnica para el equipo de Toke.

Sistema de gestión de productos, clientes, órdenes de compra y balances. Las órdenes se procesan mediante Kafka. Los depósitos requieren aprobación administrativa.

## Stack

- Java 25 y Quarkus.
- MongoDB 8.0.
- Apache Kafka.
- Traefik.
- Docker Compose.
- TLS/mTLS y JWT.

## Requisitos

- Linux y Bash.
- Git.
- Docker con Compose.
- JDK 25.
- OpenSSL.
- Puertos `80` y `443` disponibles.

## Setup

### 1. Clonar

```bash
git clone https://github.com/rafieltq/benito_ventas.git
cd benito_ventas
```

### 2. Preparar certificados

Los certificados y las claves se almacenan fuera del repositorio, en `~/certs`.

Crear la CA interna antes de ejecutar los scripts:

```bash
mkdir -p ~/certs/ca

openssl req -x509 -newkey rsa:2048 -sha256 -days 365 \
  -keyout ~/certs/ca/ca.key \
  -out ~/certs/ca/ca.crt \
  -subj "/CN=Benito Ventas Local CA" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign"

chmod 600 ~/certs/ca/ca.key
```

Desde la raíz del repositorio:

```bash
bash infra/certs/setup-traefik.sh
bash infra/certs/setup-productos.sh
bash infra/certs/setup-clientes.sh
bash infra/certs/setup-facturacion.sh
bash infra/certs/setup-kafka.sh
bash infra/certs/setup-admin.sh
bash infra/certs/setup-auth.sh

cp ~/certs/admin/ca.crt ~/certs/traefik/admin-ca.crt
chmod 644 ~/certs/traefik/admin-ca.crt
```

Los scripts pueden solicitar la contraseña de la CA. Conservan los certificados completos existentes; no los renuevan automáticamente.

### 3. Crear la red y compilar

Desde la raíz:

```bash
docker network inspect proxy >/dev/null 2>&1 ||
  docker network create proxy
```

### 4. Levantar el entorno

```bash
docker compose up --build -d
docker compose ps
```

###4.1 Modo desarrollo con hot-reload 

```bash
docker compose -f compose.yml -f compose.dev.yml up --build -d
docker compose ps
```

### 5. Comprobar acceso

```bash
curl --cacert ~/certs/traefik/local.crt \
  https://productos.benito.localhost/productos
```

## Servicios

| Servicio    | URL                                    |
| ----------- | -------------------------------------- |
| Auth        | `https://auth.benito.localhost`        |
| Clientes    | `https://clientes.benito.localhost`    |
| Productos   | `https://productos.benito.localhost`   |
| Facturación | `https://facturacion.benito.localhost` |

Las entradas administrativas utilizan `<servicio>.admin.benito.localhost` y requieren certificado cliente administrativo.

## Uso

- Registro: `POST /auth/clientes/signup`
- Login: `POST /auth/clientes/login`
- Login administrativo: `POST /auth/admin/login`, mediante el host administrativo.

Las operaciones protegidas requieren:

```http
Authorization: Bearer <token>
```

En Postman, mantener la verificación SSL activada:

- Confiar en `~/certs/traefik/local.crt` para la entrada autofirmada.
- Confiar en `~/certs/ca/ca.crt` para las entradas firmadas por la CA interna.
- Configurar `admin/operador.crt` y `admin/operador.key` como identidad cliente para los hosts administrativos.

## Operación

```bash
# Consultar logs
docker compose logs --tail=100

# Detener sin eliminar los volúmenes de MongoDB
docker compose down

# Crear roles y administrador inicial
   ./infra/db/seed-roles.sh
   ./infra/db/seed-admin.sh
```
## Colección de Postman
Todos los endpoints y flujos e2e de peticiones se encuentran en 
```bash

./docs/BENITO VENTAS.postman_collection.json
```
### Para utilizar se deben agregar los certificados:
placeholder para imagen
Para utilizar la colección de Postman necestia cargar los certificados
Dirigirse a: File > Settings > Certificate
local.crt para la certificación de la comunicación TLS, en el folder de certs. ~/certs/traefik/local.crt
Para certificación mTLS para Administrador al gateway.
HOST: <servicio>.admin.benito.localhost Puerto: 443
~/certs/admin/operador.crt
~/certs/admin/operador.key
