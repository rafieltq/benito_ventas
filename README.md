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

Docker compila `productos` y Traefik publica la API en
`http://benito.localhost/productos`. Solo Traefik publica HTTP, en
`127.0.0.1:80`; Quarkus y MongoDB se comunican por la red interna de Compose.
Esta configuración usa HTTP local, sin TLS ni dashboard público.

Para probar sin depender de la resolución local del dominio:

```bash
curl --resolve benito.localhost:80:127.0.0.1 http://benito.localhost/productos
```

Para revisar los logs y detener los servicios:

```bash
docker compose logs -f traefik productos
docker compose down
```

