package ec.johnfarez.evaluacion.cuentas.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public record CrearCuentaRequest(

    @NotBlank(message = "El numero de cuenta es obligatorio")
    @Pattern(regexp = "\\d{10}", message = "El numero de cuenta debe tener exactamente 10 digitos")
    String numeroCuenta,

    @NotBlank(message = "El titular es obligatorio")
    @Size(max = 150, message = "El titular admite hasta 150 caracteres")
    String titular,

    @NotNull(message = "El tipo es obligatorio")
    @Pattern(regexp = "ahorro|corriente", message = "El tipo debe ser ahorro o corriente")
    String tipo,

    @NotNull(message = "El saldo inicial es obligatorio")
    @DecimalMin(value = "0", message = "El saldo inicial no puede ser negativo")
    @Digits(integer = 17, fraction = 2, message = "El saldo admite hasta 17 enteros y 2 decimales")
    @JsonSerialize(using = DecimalStringSerializer.class)
    BigDecimal saldoInicial,

    @NotNull(message = "El estado es obligatorio")
    @Pattern(regexp = "activo|inactivo", message = "El estado debe ser activo o inactivo")
    String estado
    
) {}
