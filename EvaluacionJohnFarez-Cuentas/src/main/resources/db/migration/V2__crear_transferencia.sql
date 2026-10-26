CREATE TABLE transferencia (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_transferencia PRIMARY KEY,
    cuenta_origen_id BIGINT NOT NULL,
    cuenta_destino_id BIGINT NOT NULL,
    monto DECIMAL(19,2) NOT NULL,
    fecha DATETIMEOFFSET(6) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    clave_idempotencia VARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT fk_transferencia_origen FOREIGN KEY (cuenta_origen_id) REFERENCES cuenta(id),
    CONSTRAINT fk_transferencia_destino FOREIGN KEY (cuenta_destino_id) REFERENCES cuenta(id),
    CONSTRAINT uk_transferencia_clave UNIQUE (clave_idempotencia),
    CONSTRAINT ck_transferencia_monto CHECK (monto > 0),
    CONSTRAINT ck_transferencia_cuentas CHECK (cuenta_origen_id <> cuenta_destino_id),
    CONSTRAINT ck_transferencia_estado CHECK (estado IN ('completada', 'en_revision', 'rechazada'))
);
CREATE INDEX ix_transferencia_origen_fecha ON transferencia(cuenta_origen_id, fecha) INCLUDE(monto, estado);
CREATE INDEX ix_transferencia_destino_fecha ON transferencia(cuenta_destino_id, fecha);
