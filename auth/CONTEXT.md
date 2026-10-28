# Authentication

User identities, their password credentials, and their authorization roles.

## Language

**User**:
A person identified by a name, email, and phone number. Each user has exactly one credential and one or more assigned roles.
_Avoid_: Credential when referring to the person

**Credential**:
The email and password proof used to authenticate exactly one user. A credential belongs to one user and does not own roles.
_Avoid_: User when referring to login details

**Role**:
A named authorization responsibility with a description. A user starts with one role and may later have multiple roles; a role may be shared by many users.
