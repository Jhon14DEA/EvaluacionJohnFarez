ALTER TABLE transferencia ADD
    codigo_rechazo VARCHAR(64) NULL,
    mensaje_rechazo NVARCHAR(500) NULL,
    disponible_rechazo DECIMAL(19,2) NULL;
GO
-- Legacy rejected rows may have no details. Never rewrite historical data.
ALTER TABLE transferencia ADD CONSTRAINT ck_transferencia_detalle_rechazo CHECK (
    (codigo_rechazo IS NULL AND mensaje_rechazo IS NULL AND disponible_rechazo IS NULL)
    OR (estado = 'rechazada' AND codigo_rechazo IS NOT NULL AND mensaje_rechazo IS NOT NULL
        AND (disponible_rechazo IS NULL OR disponible_rechazo >= 0))
);
GO
CREATE INDEX ix_transferencia_fecha_id ON transferencia(fecha DESC, id DESC);
