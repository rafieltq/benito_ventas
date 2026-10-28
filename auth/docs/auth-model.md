# Authentication persistence model

## Entities

| Entity | Collection | Fields |
| --- | --- | --- |
| `User` | `Users` | `id`, `nombre`, `email`, `phone`, `roleIds` |
| `Credential` | `Credentials` | `userId`, `email`, `passwordHash` |
| `Role` | `Roles` | `id`, `name`, `description` |

`User` and `Role` extend `PanacheMongoEntity`, which supplies an `ObjectId` ID.
`Credential` extends `PanacheMongoEntityBase` to supply its own ID.

## Relationships

- `Credential.userId` is annotated with `@BsonId`: it is stored as `_id`, not as a separate `userId` BSON field. It must equal the owning `User.id`.
- MongoDB's built-in unique `_id` index prevents two credentials for the same user. Retrieve a credential with `Credential.findById(user.id)`.
- Always set `Credential.userId` explicitly before insertion. The BSON driver can generate an ID when it is absent; that would not establish a valid user relationship.
- `User.roleIds` is a `Set<ObjectId>` referencing `Role.id`. It supports one initial role and additional roles without duplicate assignments. No role is seeded or automatically assigned by these entities.
- Profile email and credential login email are separate stored fields. Registration and email-change workflows must keep them consistent.

## Enforcement boundary

These classes provide the persistence structure, not registration or account-deletion workflows. MongoDB does not enforce cross-collection foreign keys. A shared primary key enforces **at most one** credential per user, not the existence of a user or credential on both sides.

To fulfill the domain's strict one-to-one lifecycle, a service must create and delete the user and credential together in a MongoDB transaction, reject orphan credential writes, and prevent independent lifecycle writes. Transactions require a replica set or sharded deployment. That service is not implemented here.

A service must also validate required fields, assign one existing role during registration, reject nonexistent role references, and decide how deletion of assigned roles is handled. Unique login-email and role-name indexes are not included in this structural change and must be established before implementing login and role management.

## Secrets

`passwordHash` is excluded from Jackson JSON serialization and deserialization with `@JsonIgnore`, but remains persisted by the BSON codec. Use dedicated request/response DTOs; hash passwords in the authentication service before creating credentials. No password hashing or login behavior is implemented here.

The old `User.password` Java field has been removed. This does not remove or migrate existing MongoDB fields: any existing password data needs a separate, reviewed migration before deployment.
