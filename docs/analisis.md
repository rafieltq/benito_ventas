Analisis prelimiar del proyecto
---
El cliente mira la lista de productos (GET /productos), El cliente selecciona productos especificando cantidades y coloca una orden de compra (POST orders/place-order/).

Voy a asumir que si el cliente tiene saldo disponible la orden se paga de manera automática descontando balance de su cuenta y recibe una actualización de estado. De no haber balance disponible se coloca la orden como pendiente de pago.

El usuario puede ver los productos sin compromiso en la tienda. Para poder poner una orden de compra debe identificarse primero, lo que crearía un cliente en la base de datos. Esta orden pasa a Facturación, donde se recibe y se cobra.

# Analisis de actores

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

Se trata de tener responsabilidades bien limitadas para poder manejar de manera más limpia la operación y que cada servicio solo acceda a los datos que necesita para ejecutar.

Lo delicado será definir cómo evitar compras duplicadas, cobros repetidos y balances inconsistentes cuando se procesen los mensajes. El documento deja reglas abiertas intencionalmente y también evaluará tus decisiones.

Auth
Debe manejar credenciales y roles en el sistema.

- Registro e inicio de sesión.
- Emisión de tokens JWT firmados.
- Validación de identidad y permisos.
- Manejo de usuarios activos y registros pendientes.
- Los roles estarán fijos; no se evaluará su administración en el alcance del proyecto.

Clientes

Maneja los perfiles de los clientes.

- Guarda la identificación y los datos del perfil.
- Cada perfil está asociado a un user_id.
- Solo puede existir un perfil de cliente para un mismo user_id.

Productos
Maneja los productos.
- Catálogo y precios.
- Operaciones administrativas sobre productos.
- Consulta pública sin autenticación.

Facturación
Maneja órdenes y cuentas de clientes y depositos modificando sus balances.
- Recepción y procesamiento de órdenes.
- Cuentas identificadas por el ID del cliente.
- Balance de cada cuenta.
- Movimientos asociados al balance.
- Solicitudes de depósito y su aprobación o rechazo.
- Publicación y consumo de mensajes de Kafka.

Inicialmente consideré que Órdenes fuera un servicio aparte. Decidí mantener recepción y procesamiento de órdenes junto con el balance y los movimientos, porque estas operaciones deben confirmarse juntas y no identifiqué un beneficio suficiente que justificara separarlas.

## Validación de identidad y autorización

El requisito de TLS y mTLS protege el canal de comunicación. Adicionalmente, mTLS permite autenticar a los componentes que se comunican.

Para transportar la identidad del usuario y sus roles utilizaré un JWT firmado, por ser un formato compacto cuya integridad y origen pueden verificarse. El token no sustituye la comprobación de permisos ni de propiedad sobre los recursos.

Dentro del diseño usaré ForwardAuth en el gateway para delegar en Auth la validación inicial del usuario, antes de reenviar peticiones protegidas. Los servicios mantienen la validación del JWT, los permisos por rol y la comprobación de propiedad de los recursos. Las comunicaciones internas utilizan mTLS.

Acepto una consulta adicional y la dependencia de Auth por cada petición protegida. Si la validación no puede completarse, no se permite ejecutar la operación. Los endpoints públicos de registro, login y consulta del catálogo quedan fuera de ForwardAuth.

