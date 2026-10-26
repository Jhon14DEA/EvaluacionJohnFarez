package ec.johnfarez.evaluacion.cuentas.repository;

import java.time.OffsetDateTime;

/**
 * Filtros de historial ya validados, con las fechas parseadas a OffsetDateTime.
 * Se usa en el repositorio para construir la consulta sin pasar múltiples argumentos sueltos.
 */
public record CriterioHistorial(Long origen, Long destino, OffsetDateTime desde,
        OffsetDateTime hasta, String estado) {
}
