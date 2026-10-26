package ec.johnfarez.evaluacion.cuentas.exception;

import ec.johnfarez.evaluacion.cuentas.dto.ErrorResponse;
import ec.johnfarez.evaluacion.cuentas.dto.ErrorResponse.ErrorCampo;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.util.List;
import org.jboss.logging.Logger;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof OperacionFinancieraException financiera)
            return Response.status(financiera.status()).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(financiera.codigo(), financiera.getMessage(), List.of(),
                    financiera.montoRestanteDisponible())).build();
        if (exception instanceof CuentaDuplicadaException)
            return error(409, "CUENTA_DUPLICADA", exception.getMessage(), List.of());
        if (exception instanceof WebApplicationException web) {
            int status = web.getResponse().getStatus();
            return error(status, "HTTP_" + status,
                status == 400 ? "El cuerpo de la solicitud no es valido" : "La solicitud no pudo procesarse", List.of());
        }
        LOG.error("Error no controlado al procesar cuentas", exception);
        return error(500, "ERROR_INTERNO", "No se pudo procesar la solicitud", List.of());
    }

    static Response error(int status, String codigo, String mensaje, List<ErrorCampo> campos) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
            .entity(new ErrorResponse(codigo, mensaje, campos)).build();
    }
}
