#!/bin/sh
set -eu
export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"
exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -Q "SELECT DB_NAME() AS contexto, SERVERPROPERTY('ProductVersion') AS version; SELECT 'evaluacion_john_farez' AS base, version, script, success FROM evaluacion_john_farez.dbo.flyway_schema_history WHERE version IS NOT NULL; SELECT 'evaluacion_john_farez_test' AS base, version, script, success FROM evaluacion_john_farez_test.dbo.flyway_schema_history WHERE version IS NOT NULL;"
