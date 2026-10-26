package ec.johnfarez.evaluacion.cuentas.resource;

import ec.johnfarez.evaluacion.cuentas.exception.OperacionFinancieraException;

/**
 * Normaliza y valida los parámetros de paginación (page/size) de las consultas REST.
 * Centraliza los valores por defecto y las reglas de rango para no repetir validaciones
 * en cada recurso.
 */
public record Paginacion(int pagina, int tamano) {
    private static final int TAMANO_POR_DEFECTO = 20;
    private static final int TAMANO_MAXIMO = 200;

    public static Paginacion validar(Integer page, Integer size) {
        int pagina = page == null ? 0 : page;
        int tamano = size == null ? TAMANO_POR_DEFECTO : size;
        if (pagina < 0) {
            throw new OperacionFinancieraException(400, "PAGINACION_INVALIDA", "page debe ser mayor o igual a 0");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new OperacionFinancieraException(400, "PAGINACION_INVALIDA",
                "size debe estar entre 1 y " + TAMANO_MAXIMO);
        }
        return new Paginacion(pagina, tamano);
    }
}
