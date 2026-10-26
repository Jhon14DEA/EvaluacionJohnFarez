package ec.johnfarez.evaluacion.cuentas.resource;

import ec.johnfarez.evaluacion.cuentas.dto.*;
import ec.johnfarez.evaluacion.cuentas.exception.OperacionFinancieraException;
import ec.johnfarez.evaluacion.cuentas.service.ConsultaTransferenciaService;
import ec.johnfarez.evaluacion.cuentas.service.CuentaService;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.Set;

@Path("/api/cuentas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CuentaResource {
    private static final Set<String> CAMPOS_ORDEN = Set.of("saldo", "id", "numeroCuenta", "titular");

    private final CuentaService service;
    private final ConsultaTransferenciaService consultas;

    @Inject
    public CuentaResource(CuentaService service, ConsultaTransferenciaService consultas) {
        this.service = service;
        this.consultas = consultas;
    }

    /**
     * Registra una cuenta bancaria vía {@code POST /api/cuentas} (CA-01).
     *
     * @param request datos de la cuenta a crear
     * @return respuesta HTTP 201 con la cuenta creada y su identificador
     */
    @POST
    public Response crear(CrearCuentaRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.crear(request)).build();
    }

    /**
     * Lista las cuentas registradas vía {@code GET /api/cuentas} (CA-01).
     * <p>
     * Sin parámetros devuelve el arreglo plano de todas las cuentas (compatibilidad).
     * Con {@code page}/{@code size}/{@code q}/{@code sort} devuelve una página con metadatos
     * y la cabecera {@code X-Total-Count}.
     *
     * @param params filtros opcionales de búsqueda, orden y paginación
     * @return lista plana de cuentas o una página según los parámetros recibidos
     */
    @GET
    public Response listar(@BeanParam ParametrosListadoCuentas params) {
        if (sinParametros(params)) {
            return Response.ok(service.listar()).build();
        }
        var paginacion = Paginacion.validar(params.page, params.size);
        var consulta = new ConsultaCuentas(params.q, validarCampo(params.sort), ascendente(params.order),
            paginacion.pagina(), paginacion.tamano());
        var resultado = service.listarPaginado(consulta);
        return Response.ok(resultado).header("X-Total-Count", resultado.totalElementos()).build();
    }

    private static boolean sinParametros(ParametrosListadoCuentas p) {
        return p.page == null && p.size == null && (p.q == null || p.q.isBlank()) && p.sort == null && p.order == null;
    }

    private static String validarCampo(String sort) {
        String campo = sort == null ? "id" : sort;
        if (!CAMPOS_ORDEN.contains(campo)) {
            throw new OperacionFinancieraException(400, "ORDEN_INVALIDO", "sort admite: " + CAMPOS_ORDEN);
        }
        return campo;
    }

    private static boolean ascendente(String order) {
        if (order != null && !order.equals("asc") && !order.equals("desc")) {
            throw new OperacionFinancieraException(400, "ORDEN_INVALIDO", "order admite asc o desc");
        }
        return !"desc".equals(order);
    }

    /**
     * Devuelve el resumen de una cuenta vía {@code GET /api/cuentas/{id}/resumen} (CA-05).
     * <p>
     * Incluye el saldo actual, el total transferido hoy, la cantidad de transferencias del día
     * y cuántas quedaron marcadas como sospechosas.
     *
     * @param id identificador de la cuenta
     * @return resumen de la cuenta calculado en tiempo real
     */
    @GET
    @Path("/{id}/resumen")
    public ResumenCuentaResponse resumen(@PathParam("id") long id) {
        return consultas.resumen(id);
    }
}
