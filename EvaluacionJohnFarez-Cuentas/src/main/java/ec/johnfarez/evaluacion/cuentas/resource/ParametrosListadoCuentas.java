package ec.johnfarez.evaluacion.cuentas.resource;

import jakarta.ws.rs.QueryParam;

/**
 * Parámetros de consulta del listado de cuentas agrupados con @BeanParam.
 * Evita que el método del recurso reciba demasiados argumentos sueltos.
 */
public class ParametrosListadoCuentas {
    @QueryParam("page")
    public Integer page;

    @QueryParam("size")
    public Integer size;

    @QueryParam("q")
    public String q;

    @QueryParam("sort")
    public String sort;

    @QueryParam("order")
    public String order;
}
