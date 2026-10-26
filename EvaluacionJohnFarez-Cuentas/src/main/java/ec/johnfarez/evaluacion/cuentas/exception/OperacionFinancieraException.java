package ec.johnfarez.evaluacion.cuentas.exception;

public class OperacionFinancieraException extends RuntimeException {
    private final java.math.BigDecimal montoRestanteDisponible;
    private final int status;
    private final String codigo;

    public OperacionFinancieraException(int status, String codigo, String mensaje) {
        this(status, codigo, mensaje, null);
    }
    public OperacionFinancieraException(int status, String codigo, String mensaje, java.math.BigDecimal disponible) {
        super(mensaje);
        this.montoRestanteDisponible = disponible;
        this.status = status;
        this.codigo = codigo;
    }
    public java.math.BigDecimal montoRestanteDisponible() { return montoRestanteDisponible; }
    public int status() { return status; }
    public String codigo() { return codigo; }
}
