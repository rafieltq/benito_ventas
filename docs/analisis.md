Analisis de actores
---

| Actor                  | Acciones                                           |
|------------------------|---------------------------------------------------|
| Usuario no autenticado | Puede ver la lista de productos y sus precios     |
|                        | Puede crear una cuenta de cliente                 |
| Cliente                | Puede poner una orden de compra                    |
|                        | Puede depositar dinero                             |
| Administrador          | Puede manejar productos (CRUD)                     |
|                        | Puede manejar clientes (CRUD)                      |
|                        | Puede manejar ordenes de compra (CRUD)            |

## Comportamiento requerido
- Las órdenes de compra deben ser encoladas como un mensaje en un sistema de publish-subscribe (pub-sub).
- Las comunicaciones entre microservicios y otros microservicios y agentes administrativos deben estar asegurados por MTLS.
- La comunicación entre los microservicios visibles por agentes externos debe estar asegurada por TLS.
- Mantener el desarrollo en un sistema de control de versiones (como git).
Subir el proyecto a un repositorio online (como github).
