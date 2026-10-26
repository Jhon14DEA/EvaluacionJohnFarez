package ec.johnfarez.evaluacion.cuentas.repository;

/**
 * Criterio de búsqueda y ordenamiento para el listado paginado de cuentas.
 * Agrupa el texto de búsqueda, el campo de orden y la dirección para evitar
 * métodos con demasiados parámetros.
 */
public record CriterioOrden(String texto, String campo, boolean ascendente) {
}
