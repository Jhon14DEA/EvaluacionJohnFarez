package ec.johnfarez.evaluacion.cuentas.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record ResumenCuentaResponse(
    @JsonSerialize(using = ToStringSerializer.class)
    Long cuentaId,
    @JsonSerialize(using = DecimalStringSerializer.class)
    BigDecimal saldoActual,
    @JsonSerialize(using = DecimalStringSerializer.class)
    BigDecimal totalTransferidoHoy,
    long cantidadTransferenciasHoy,
    long cantidadTransferenciasSospechosas) {}
