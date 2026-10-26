package ec.johnfarez.evaluacion.cuentas.dto;

/**
 * Parámetros de la consulta paginada de cuentas (búsqueda, orden y paginación ya resueltos).
 * Agrupa los argumentos para que los métodos de servicio no superen el límite recomendado.
 */
public record ConsultaCuentas(String texto, String campo, boolean ascendente, int pagina, int tamano) {
}
