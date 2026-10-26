package ec.johnfarez.evaluacion.cuentas.dto;

/**
 * Filtros de historial tal como llegan de la petición HTTP (fechas aún sin parsear).
 * Agrupa los parámetros de consulta para evitar métodos con listas largas de argumentos.
 */
public record FiltroHistorial(Long origen, Long destino, String desde, String hasta, String estado) {
}
