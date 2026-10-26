package ec.johnfarez.evaluacion.cuentas.repository;

import ec.johnfarez.evaluacion.cuentas.entity.Transferencia;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class TransferenciaRepository implements PanacheRepository<Transferencia> {
    public Optional<Transferencia> buscarPorClave(String clave) {
        return find("claveIdempotencia", clave).firstResultOptional();
    }

    // Los locks de aplicación de SQL Server son compartidos por todas las instancias del servicio y se liberan al commit/rollback.
    // Se adquiere antes de los locks de cuenta, incluso cuando la clave aún no tiene una fila persistida.
    public int bloquearClave(String clave) {
        Number result = (Number) getEntityManager().createNativeQuery("""
            SET NOCOUNT ON;
            -- JDBC inicia las transacciones implícitas de SQL Server al acceder a una tabla, no en DECLARE/EXEC.
            -- Esta consulta vacía activa la misma transacción gestionada por JTA antes de sp_getapplock.
            DECLARE @inicio bigint;
            SELECT @inicio = id FROM transferencia WHERE id = -1;
            DECLARE @resultado int;
            EXEC @resultado = sys.sp_getapplock @Resource = :recurso,
                @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
            SELECT @resultado;
            """).setParameter("recurso", "transferencia:" + clave).getSingleResult();
        if (result.intValue() < 0) {
            org.jboss.logging.Logger.getLogger(TransferenciaRepository.class).warnf(
                "sp_getapplock returned %s; SQL transaction count=%s", result,
                getEntityManager().createNativeQuery("SELECT @@TRANCOUNT").getSingleResult());
        }
        return result.intValue();
    }

    public void registrar(Transferencia transferencia) {
        persistAndFlush(transferencia);
    }
    // Se invoca solo después de bloquear la cuenta origen dentro de la transacción financiera.
    public java.math.BigDecimal totalEjecutado(long origen, java.time.OffsetDateTime inicio, java.time.OffsetDateTime fin) {
        var total = getEntityManager().createQuery("""
            select sum(t.monto) from Transferencia t where t.cuentaOrigenId = :origen
            and t.fecha >= :inicio and t.fecha < :fin
            and t.estado in ('completada', 'en_revision')
            """, java.math.BigDecimal.class).setParameter("origen", origen)
            .setParameter("inicio", inicio).setParameter("fin", fin).getSingleResult();
        return total == null ? java.math.BigDecimal.ZERO : total;
    }
    public long ejecutadasEnHora(long origen, java.time.OffsetDateTime ahora) {
        return count("cuentaOrigenId = ?1 and fecha > ?2 and fecha <= ?3 and estado in ('completada', 'en_revision')",
            origen, ahora.minusHours(1), ahora);
    }
    public java.util.List<Transferencia> historial(CriterioHistorial criterio) {
        var params = new java.util.HashMap<String,Object>();
        var filtro = filtroHistorial(criterio, params);
        return list(filtro + " order by fecha desc, id desc", params);
    }

    public java.util.List<Transferencia> historialPagina(CriterioHistorial criterio, int pagina, int tamano) {
        var params = new java.util.HashMap<String,Object>();
        var filtro = filtroHistorial(criterio, params);
        return find(filtro + " order by fecha desc, id desc", params)
            .page(io.quarkus.panache.common.Page.of(pagina, tamano)).list();
    }

    public long contarHistorial(CriterioHistorial criterio) {
        var params = new java.util.HashMap<String,Object>();
        var filtro = filtroHistorial(criterio, params);
        return count(filtro, params);
    }

    private String filtroHistorial(CriterioHistorial criterio, java.util.Map<String,Object> params) {
        var query = new StringBuilder("1 = 1");
        if (criterio.origen() != null) { query.append(" and cuentaOrigenId = :origen"); params.put("origen",criterio.origen()); }
        if (criterio.destino() != null) { query.append(" and cuentaDestinoId = :destino"); params.put("destino",criterio.destino()); }
        if (criterio.desde() != null) { query.append(" and fecha >= :desde"); params.put("desde",criterio.desde()); }
        if (criterio.hasta() != null) { query.append(" and fecha <= :hasta"); params.put("hasta",criterio.hasta()); }
        if (criterio.estado() != null) { query.append(" and estado = :estado"); params.put("estado",criterio.estado()); }
        return query.toString();
    }
    public long contarEjecutadas(long origen, java.time.OffsetDateTime inicio, java.time.OffsetDateTime fin, boolean sospechosas) {
        return count("cuentaOrigenId = ?1 and fecha >= ?2 and fecha < ?3 and "
            + (sospechosas ? "estado = 'en_revision'" : "estado in ('completada', 'en_revision')"), origen,inicio,fin);
    }
}
