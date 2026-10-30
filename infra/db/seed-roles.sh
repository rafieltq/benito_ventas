#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
command -v docker >/dev/null

docker compose exec -T auth-mongodb mongosh --quiet auth --file /dev/stdin <<'JS'
db.Roles.createIndex({ name: 1 }, { unique: true });

for (const name of ["Admin", "cliente"]) {
  const result = db.Roles.updateOne(
    { name },
    { $setOnInsert: { name } },
    { upsert: true }
  );
  print(result.upsertedCount === 1 ? `Rol creado: ${name}` : `Rol existente: ${name}`);
}
JS
