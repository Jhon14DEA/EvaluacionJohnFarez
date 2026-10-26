package ec.johnfarez.evaluacion.cuentas.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
public class JsonExceptionMapper implements ExceptionMapper<JsonProcessingException> {
    @Override
    public Response toResponse(JsonProcessingException exception) {
        return ApiExceptionMapper.error(400, "JSON_INVALIDO", "El cuerpo JSON no es valido", List.of());
    }
}
