package ec.johnfarez.evaluacion.cuentas.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;

public record HistorialTransferenciaResponse(
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
    String claveIdempotencia,
    @JsonInclude(JsonInclude.Include.NON_NULL) ErrorResponse rechazo) {}
