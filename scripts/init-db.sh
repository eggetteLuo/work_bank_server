#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-bank_opengauss}"
DB_USER="${DB_USER:-bank_admin}"
DB_PASSWORD="${DB_PASSWORD:-Gauss@123}"
DB_NAME="${DB_NAME:-bank_system}"

run_gsql_file() {
  local file_path="$1"
  docker exec -i \
    -e LD_LIBRARY_PATH=/usr/local/opengauss/lib:/scws/lib \
    "$CONTAINER_NAME" \
    /usr/local/opengauss/bin/gsql \
    -U "$DB_USER" \
    -W "$DB_PASSWORD" \
    -d "$DB_NAME" \
    -v ON_ERROR_STOP=1 < "$file_path"
}

run_gsql_file "src/main/resources/schema.sql"
run_gsql_file "src/main/resources/data.sql"

echo "openGauss schema and seed data initialized successfully."
