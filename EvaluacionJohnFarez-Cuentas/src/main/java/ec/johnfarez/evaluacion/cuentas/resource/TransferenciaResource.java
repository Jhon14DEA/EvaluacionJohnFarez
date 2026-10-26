package ec.johnfarez.evaluacion.cuentas.resource;

import ec.johnfarez.evaluacion.cuentas.dto.*;
import ec.johnfarez.evaluacion.cuentas.service.ConsultaTransferenciaService;
import ec.johnfarez.evaluacion.cuentas.service.TransferenciaService;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/api/transferencias")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TransferenciaResource {
    private final TransferenciaService service;
    private final ConsultaTransferenciaService consultas;

    @Inject
    public TransferenciaResource(TransferenciaService service, ConsultaTransferenciaService consultas) {
        this.service = service;
        this.consultas = consultas;
    }

    /**
     * Ejecuta una transferencia entre cuentas vía {@code POST /api/transferencias} (CA-02).
     * <p>
     * La cabecera opcional {@code Idempotency-Key} evita ejecutar dos veces la misma operación;
     * la respuesta la devuelve para que el cliente pueda reutilizarla en reintentos.
     *
     * @param request cuentas origen/destino y monto de la transferencia
     * @param clave clave de idempotencia opcional enviada en la cabecera {@code Idempotency-Key}
     * @return respuesta HTTP 201 con la transferencia ejecutada
     */
    @POST
    public Response ejecutar(CrearTransferenciaRequest request, @HeaderParam("Idempotency-Key") String clave) {
        var resultado = service.ejecutar(request, clave);
        return Response.status(201).header("Idempotency-Key", resultado.claveIdempotencia()).entity(resultado).build();
    }

    /**
     * Consulta el historial de transferencias vía {@code GET /api/transferencias} (CA-05).
     * <p>
     * Admite filtros opcionales por cuenta origen, cuenta destino, rango de fechas y estado,
     * devolviendo los resultados ordenados por fecha descendente. Sin {@code page}/{@code size}
     * devuelve el arreglo plano (compatibilidad); con ellos devuelve una página con metadatos.
     *
     * @param origen filtro opcional por identificador de cuenta origen
     * @param destino filtro opcional por identificador de cuenta destino
     * @param desde filtro opcional de fecha inicial (ISO-8601 con offset)
     * @param hasta filtro opcional de fecha final (ISO-8601 con offset)
     * @param estado filtro opcional por estado (completada, rechazada o en_revision)
     * @param page número de página (0-based) cuando se solicita paginación
     * @param size tamaño de página cuando se solicita paginación
     * @return historial de transferencias, como arreglo plano o página según los parámetros
     */
    @GET
    public Response historial(@QueryParam("cuentaOrigenId") Long origen,
            @QueryParam("cuentaDestinoId") Long destino, @QueryParam("fechaDesde") String desde,
            @QueryParam("fechaHasta") String hasta, @QueryParam("estado") String estado,
            @QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        var filtro = new FiltroHistorial(origen, destino, desde, hasta, estado);
        if (page == null && size == null) {
            return Response.ok(consultas.historial(filtro)).build();
        }
        var paginacion = Paginacion.validar(page, size);
        var resultado = consultas.historialPaginado(filtro, paginacion.pagina(), paginacion.tamano());
        return Response.ok(resultado).header("X-Total-Count", resultado.totalElementos()).build();
    }
}
