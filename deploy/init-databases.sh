#!/bin/sh
set -eu

ensure_database() {
    db="$1"
    role="$2"
    password="$3"
    psql -X -h db -U auth_app -d auth_db --set=ON_ERROR_STOP=1 \
        --set=db="$db" --set=role="$role" --set=password="$password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'role') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'role')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'db') \gexec
REVOKE CONNECT ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"role";
SQL
}
ensure_database schedule_db schedule_app "$SCHEDULE_DB_PASSWORD"
ensure_database checkin_db checkin_app "$CHECKIN_DB_PASSWORD"
ensure_database progress_db progress_app "$PROGRESS_DB_PASSWORD"
