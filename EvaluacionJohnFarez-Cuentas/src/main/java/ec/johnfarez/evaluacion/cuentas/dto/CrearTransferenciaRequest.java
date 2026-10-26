package ec.johnfarez.evaluacion.cuentas.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record CrearTransferenciaRequest(

    @NotNull @Positive @JsonSerialize(using = ToStringSerializer.class)
    Long cuentaOrigenId,

    @NotNull @Positive @JsonSerialize(using = ToStringSerializer.class)
    Long cuentaDestinoId,

    @NotNull @DecimalMin(value = "0", inclusive = false, message = "El monto debe ser positivo")
    @Digits(integer = 17, fraction = 2, message = "El monto admite hasta 17 enteros y 2 decimales")
    @JsonSerialize(using = DecimalStringSerializer.class)
    BigDecimal monto
    
) {}
