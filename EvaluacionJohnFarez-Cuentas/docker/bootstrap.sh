#!/bin/sh
set -eu
export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"
exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -i /bootstrap.sql
