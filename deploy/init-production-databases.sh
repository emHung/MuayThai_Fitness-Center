#!/bin/sh
set -eu

provision() {
    database=$1
    runtime=$2
    runtime_password=$3
    migrator=$4
    migration_password=$5

    psql -X -h db -U db_bootstrap -d postgres -v ON_ERROR_STOP=1 \
        -v database="$database" -v runtime="$runtime" -v runtime_password="$runtime_password" \
        -v migrator="$migrator" -v migration_password="$migration_password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', :'runtime')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'runtime') \gexec
SELECT format('CREATE ROLE %I LOGIN', :'migrator')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'migrator') \gexec
ALTER ROLE :"runtime" NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD :'runtime_password';
ALTER ROLE :"migrator" NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD :'migration_password';
SELECT format('CREATE DATABASE %I OWNER %I', :'database', :'migrator')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'database') \gexec
SELECT EXISTS (SELECT FROM pg_database WHERE datname = :'database' AND pg_get_userbyid(datdba) = :'migrator') AS correct_owner \gset
\if :correct_owner
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"database" TO :"runtime";
\else
\echo 'Database owner mismatch. Use a dedicated production volume.'
\quit 1
\endif
SQL
    psql -X -h db -U db_bootstrap -d "$database" -v ON_ERROR_STOP=1 -v runtime="$runtime" <<'SQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"runtime";
SQL
}

provision auth_db auth_app "$AUTH_DB_PASSWORD" auth_migrate "$AUTH_MIGRATION_PASSWORD"
provision schedule_db schedule_app "$SCHEDULE_DB_PASSWORD" schedule_migrate "$SCHEDULE_MIGRATION_PASSWORD"
provision checkin_db checkin_app "$CHECKIN_DB_PASSWORD" checkin_migrate "$CHECKIN_MIGRATION_PASSWORD"
provision progress_db progress_app "$PROGRESS_DB_PASSWORD" progress_migrate "$PROGRESS_MIGRATION_PASSWORD"
