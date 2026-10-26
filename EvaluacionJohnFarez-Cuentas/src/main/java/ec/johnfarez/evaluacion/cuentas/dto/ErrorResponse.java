package ec.johnfarez.evaluacion.cuentas.dto;

import java.util.List;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ErrorResponse(
    String codigo,
    String mensaje,
    List<ErrorCampo> erroresCampos,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal montoRestanteDisponible) {
    public ErrorResponse(String codigo, String mensaje, List<ErrorCampo> erroresCampos) {
        this(codigo, mensaje, erroresCampos, null);
    }
    public record ErrorCampo(String campo, String mensaje) {}
}
