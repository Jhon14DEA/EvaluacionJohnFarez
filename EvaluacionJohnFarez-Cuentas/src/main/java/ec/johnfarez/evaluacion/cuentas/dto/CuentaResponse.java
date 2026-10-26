package ec.johnfarez.evaluacion.cuentas.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record CuentaResponse(
    @JsonSerialize(using = ToStringSerializer.class) 
    Long id,
    String numeroCuenta,
    String titular,
    String tipo,
    @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal saldo,
    String estado) {}
