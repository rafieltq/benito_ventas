#!/usr/bin/env bash
set +x
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
command -v docker >/dev/null
if ! command -v htpasswd >/dev/null; then
  printf 'Falta htpasswd. Instala apache2-utils (Debian/Ubuntu) o httpd-tools (Fedora).\n' >&2
  exit 1
fi

read -r -p 'Nombre del administrador: ' admin_name
read -r -p 'Correo del administrador: ' admin_email
read -r -p 'Teléfono del administrador (opcional): ' admin_phone
if [[ ! "$admin_name" =~ [^[:space:]] || ! "$admin_email" =~ [^[:space:]] ]]; then
  printf 'El nombre y el correo son obligatorios.\n' >&2
  exit 1
fi

read -r -s -p 'Contraseña: ' password
printf '\n'
read -r -s -p 'Confirmar contraseña: ' confirmation
printf '\n'
if [[ "$password" != "$confirmation" ]]; then
  printf 'Las contraseñas no coinciden.\n' >&2
  exit 1
fi
password_bytes="$(printf '%s' "$password" | wc -c)"
if [[ ! "$password" =~ [^[:space:]] ]] || (( password_bytes > 72 )); then
  printf 'La contraseña no puede estar vacía ni superar 72 bytes.\n' >&2
  exit 1
fi

password_hash="$(printf '%s\n' "$password" | htpasswd -niBC 10 admin)"
password_hash="${password_hash#*:}"
unset password confirmation

export ADMIN_NAME="$admin_name" ADMIN_EMAIL="$admin_email" ADMIN_PHONE="$admin_phone"
export ADMIN_PASSWORD_HASH="$password_hash"
unset password_hash

docker compose exec -T \
  -e ADMIN_NAME -e ADMIN_EMAIL -e ADMIN_PHONE -e ADMIN_PASSWORD_HASH \
  auth-mongodb mongosh --quiet auth --file /dev/stdin <<'JS'
const name = process.env.ADMIN_NAME.trim();
const email = process.env.ADMIN_EMAIL.trim().toLowerCase();
const phone = process.env.ADMIN_PHONE.trim();
const passwordHash = process.env.ADMIN_PASSWORD_HASH;

if (!name || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
  throw new Error("Nombre o correo inválido.");
}
if (!/^\$2y\$10\$[./A-Za-z0-9]{53}$/.test(passwordHash)) {
  throw new Error("No se generó un hash bcrypt válido.");
}
const role = db.Roles.findOne({ name: "Admin" });
if (!role) {
  throw new Error("Falta el rol Admin. Ejecuta infra/db/seed-roles.sh primero.");
}

db.Users.createIndex({ email: 1 }, { unique: true });
db.Credentials.createIndex({ email: 1 }, { unique: true });

const existingUser = db.Users.findOne({ email });
const existingCredential = db.Credentials.findOne({ email });
if (existingUser || existingCredential) {
  const isAdmin = existingUser && (existingUser.roleIds || []).some(id => id.equals(role._id));
  if (!isAdmin || existingUser.isActive !== true || !existingCredential
      || !existingCredential._id.equals(existingUser._id) || !existingCredential.passwordHash) {
    throw new Error("El correo ya tiene un registro incompatible o incompleto. No se modificó el usuario.");
  }
  print("El administrador ya existe. No se modificaron sus datos ni su contraseña.");
} else {
  const userId = new ObjectId();
  // Auth uses standalone MongoDB: activate only after both documents are saved.
  db.Users.insertOne({ _id: userId, name, email, phone, roleIds: [role._id], isActive: false });
  db.Credentials.insertOne({ _id: userId, email, passwordHash });
  db.Users.updateOne({ _id: userId }, { $set: { isActive: true } });
  print(`Administrador creado: ${email}`);
}
JS
