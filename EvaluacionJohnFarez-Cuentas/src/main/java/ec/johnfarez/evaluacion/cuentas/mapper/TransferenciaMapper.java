package ec.johnfarez.evaluacion.cuentas.mapper;

import ec.johnfarez.evaluacion.cuentas.dto.*;
import ec.johnfarez.evaluacion.cuentas.entity.Transferencia;
import org.mapstruct.*;

@Mapper
public interface TransferenciaMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fecha", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "claveIdempotencia", ignore = true)
    @Mapping(target = "codigoRechazo", ignore = true)
    @Mapping(target = "mensajeRechazo", ignore = true)
    @Mapping(target = "disponibleRechazo", ignore = true)
    Transferencia toEntity(CrearTransferenciaRequest request);
    @Mapping(target = "rechazo", expression = "java(errorRechazo(transferencia))")
    HistorialTransferenciaResponse toHistorial(Transferencia transferencia);
    default ErrorResponse errorRechazo(Transferencia t) {
        if (!"rechazada".equals(t.estado)) {
            return null;
        }
        String codigo = t.codigoRechazo == null ? "RECHAZO_REGISTRADO" : t.codigoRechazo;
        String mensaje = t.mensajeRechazo == null ? "Rechazo historico sin detalle" : t.mensajeRechazo;
        return new ErrorResponse(codigo, mensaje, java.util.List.of(), t.disponibleRechazo);
    }
    TransferenciaResponse toResponse(Transferencia transferencia);
}
