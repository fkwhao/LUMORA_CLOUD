#!/usr/bin/env bash
set -Eeuo pipefail

required_variables=(
  MYSQL_ROOT_PASSWORD
  MYSQL_USER
  LUMORA_USER_DATABASE
  LUMORA_BILLING_DATABASE
  LUMORA_MODEL_CATALOG_DATABASE
)

for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "Missing required variable: ${variable_name}" >&2
    exit 1
  fi
done

if [[ ! "${MYSQL_USER}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "MYSQL_USER contains unsupported characters." >&2
  exit 1
fi

databases=(
  "${LUMORA_USER_DATABASE}"
  "${LUMORA_BILLING_DATABASE}"
  "${LUMORA_MODEL_CATALOG_DATABASE}"
)

for database_name in "${databases[@]}"; do
  if [[ ! "${database_name}" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "Database name contains unsupported characters: ${database_name}" >&2
    exit 1
  fi

  mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${database_name}\`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON \`${database_name}\`.* TO '${MYSQL_USER}'@'%';
SQL
done

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "FLUSH PRIVILEGES;"
