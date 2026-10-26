package ec.johnfarez.evaluacion.cuentas.service;
import ec.johnfarez.evaluacion.cuentas.exception.OperacionFinancieraException;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
@ApplicationScoped
public class ReglasTransferencia {
    private static final BigDecimal UMBRAL = new BigDecimal("5000.00");
    private static final BigDecimal LIMITE = new BigDecimal("10000.00");
    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    public record Dia(OffsetDateTime inicio, OffsetDateTime fin) {}
    public Dia dia(OffsetDateTime ahora) {
        var fecha = ahora.atZoneSameInstant(ZONA).toLocalDate();
        var inicio = fecha.atStartOfDay(ZONA).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        var fin = fecha.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        return new Dia(inicio, fin);
    }

    /**
     * Determina si una transferencia debe marcarse como sospechosa (CA-03).
     * <p>
     * Es sospechosa cuando el monto individual supera los $5.000 o cuando la cuenta origen
     * ya acumula más de 5 transferencias en la última hora.
     *
     * @param monto monto de la transferencia a evaluar
     * @param anterioresEnHora cantidad de transferencias de la cuenta origen en la última hora
     * @return {@code true} si la operación cumple alguna condición de sospecha
     */
    public boolean sospechosa(BigDecimal monto, long anterioresEnHora) {
        return monto.compareTo(UMBRAL) > 0 || anterioresEnHora >= 5;
    }

    /**
     * Valida el límite diario acumulado por cuenta (CA-04).
     * <p>
     * Ninguna cuenta puede transferir más de $10.000 acumulados en un día calendario. Si el monto
     * haría superar ese tope, la transferencia se rechaza informando el monto restante disponible.
     *
     * @param acumulado total ya transferido por la cuenta en el día
     * @param monto monto de la transferencia que se intenta ejecutar
     * @throws OperacionFinancieraException con código {@code LIMITE_DIARIO_EXCEDIDO} y el monto restante si se excede el límite
     */
    public void validarLimite(BigDecimal acumulado, BigDecimal monto) {
        var disponible = LIMITE.subtract(acumulado).max(new BigDecimal("0.00"));
        if (monto.compareTo(disponible) > 0) {
            throw new OperacionFinancieraException(422, "LIMITE_DIARIO_EXCEDIDO",
                "Limite diario excedido; monto restante disponible: " + disponible.toPlainString(), disponible);
        }
    }
}
