package ec.johnfarez.evaluacion.cuentas.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.OffsetDateTime;

public record TransferenciaResponse(
    @JsonSerialize(using = ToStringSerializer.class)
    Long id,
    @JsonSerialize(using = ToStringSerializer.class)
    Long cuentaOrigenId,
    @JsonSerialize(using = ToStringSerializer.class)
    Long cuentaDestinoId,
    @JsonSerialize(using = DecimalStringSerializer.class)
    BigDecimal monto,
    OffsetDateTime fecha,
    String estado,
    String claveIdempotencia) 
    {}
