package ec.johnfarez.evaluacion.cuentas.service;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
@ApplicationScoped
public class RelojFinanciero {
    public OffsetDateTime ahora() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
