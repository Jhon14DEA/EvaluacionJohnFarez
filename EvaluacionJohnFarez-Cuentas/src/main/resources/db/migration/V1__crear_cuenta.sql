CREATE TABLE cuenta (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_cuenta PRIMARY KEY,
    numero_cuenta VARCHAR(10) NOT NULL,
    titular NVARCHAR(150) NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    saldo DECIMAL(19,2) NOT NULL,
    estado VARCHAR(10) NOT NULL,
    CONSTRAINT uk_cuenta_numero UNIQUE (numero_cuenta),
    CONSTRAINT ck_cuenta_numero CHECK (DATALENGTH(numero_cuenta) = 10 AND numero_cuenta COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9]%'),
    CONSTRAINT ck_cuenta_titular CHECK (LEN(LTRIM(RTRIM(titular))) > 0),
    CONSTRAINT ck_cuenta_tipo CHECK (tipo IN ('ahorro', 'corriente')),
    CONSTRAINT ck_cuenta_saldo CHECK (saldo >= 0),
    CONSTRAINT ck_cuenta_estado CHECK (estado IN ('activo', 'inactivo'))
);
