package ec.johnfarez.evaluacion.cuentas.exception;

public class CuentaDuplicadaException extends RuntimeException {
    public CuentaDuplicadaException() {
        super("Ya existe una cuenta con ese numero");
    }
}
