package ec.johnfarez.evaluacion.cuentas.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name = "transferencia", uniqueConstraints = @UniqueConstraint(
    name = "uk_transferencia_clave", columnNames = "clave_idempotencia"))
public class Transferencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "cuenta_origen_id", nullable = false)
    public Long cuentaOrigenId;
    @Column(name = "cuenta_destino_id", nullable = false)
    public Long cuentaDestinoId;
    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal monto;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(nullable = false)
    public OffsetDateTime fecha;
    @Column(nullable = false, length = 20)
    public String estado;
    @Column(name = "clave_idempotencia", nullable = false, length = 128)
    public String claveIdempotencia;
    @Column(name = "codigo_rechazo", length = 64)
    public String codigoRechazo;
    @org.hibernate.annotations.Nationalized
    @Column(name = "mensaje_rechazo", length = 500)
    public String mensajeRechazo;
    @Column(name = "disponible_rechazo", precision = 19, scale = 2)
    public BigDecimal disponibleRechazo;
}
