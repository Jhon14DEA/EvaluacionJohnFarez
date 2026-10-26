IF DB_ID('evaluacion_john_farez') IS NULL
    CREATE DATABASE evaluacion_john_farez;
GO
IF DB_ID('evaluacion_john_farez_test') IS NULL
    CREATE DATABASE evaluacion_john_farez_test;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'evaluacion_app')
    CREATE LOGIN evaluacion_app WITH PASSWORD = '$(DB_PASSWORD)';
GO
USE evaluacion_john_farez;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'evaluacion_app')
    CREATE USER evaluacion_app FOR LOGIN evaluacion_app;
ALTER ROLE db_owner ADD MEMBER evaluacion_app;
GO
USE evaluacion_john_farez_test;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'evaluacion_app')
    CREATE USER evaluacion_app FOR LOGIN evaluacion_app;
ALTER ROLE db_owner ADD MEMBER evaluacion_app;
GO
