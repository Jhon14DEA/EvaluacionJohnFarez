package ec.johnfarez.evaluacion.cuentas.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cuenta", uniqueConstraints = @UniqueConstraint(name = "uk_cuenta_numero", columnNames = "numero_cuenta"))
public class Cuenta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "numero_cuenta", nullable = false, length = 10)
    public String numeroCuenta;
    @Column(nullable = false, length = 150)
    @org.hibernate.annotations.Nationalized
    public String titular;
    @Column(nullable = false, length = 10)
    public String tipo;
    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal saldo;
    @Column(nullable = false, length = 10)
    public String estado;
}
