package ec.johnfarez.evaluacion.cuentas.exception;

import ec.johnfarez.evaluacion.cuentas.dto.ErrorResponse.ErrorCampo;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Comparator;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(ConstraintViolationException exception) {
        var campos = exception.getConstraintViolations().stream()
            .map(v -> new ErrorCampo(v.getPropertyPath().toString(), v.getMessage()))
            .sorted(Comparator.comparing(ErrorCampo::campo).thenComparing(ErrorCampo::mensaje)).toList();
        return ApiExceptionMapper.error(400, "VALIDACION", "La solicitud contiene campos invalidos", campos);
    }
}
