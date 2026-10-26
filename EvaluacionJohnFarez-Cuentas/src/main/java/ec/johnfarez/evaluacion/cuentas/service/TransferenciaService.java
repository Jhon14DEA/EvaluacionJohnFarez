package ec.johnfarez.evaluacion.cuentas.service;
import ec.johnfarez.evaluacion.cuentas.dto.*;
import ec.johnfarez.evaluacion.cuentas.exception.OperacionFinancieraException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
@ApplicationScoped
public class TransferenciaService {
    private final TransferenciaOperacion operacion;

    @Inject
    public TransferenciaService(TransferenciaOperacion operacion) {
        this.operacion = operacion;
    }

    /**
     * Orquesta la ejecución de una transferencia (CA-02).
     * <p>
     * Delega la decisión y las escrituras atómicas en {@link TransferenciaOperacion}, que corre en
     * su propia transacción. Este método se declara {@code NOT_SUPPORTED} para que, cuando la
     * operación termine en rechazo, esa transacción interna confirme el registro del rechazo antes
     * de que aquí se lance el error 422 hacia el cliente.
     *
     * @param request cuentas origen/destino y monto de la transferencia
     * @param clave clave de idempotencia (puede ser nula)
     * @return la transferencia ejecutada
     * @throws ec.johnfarez.evaluacion.cuentas.exception.OperacionFinancieraException si la transferencia es rechazada por reglas de negocio
     */
    // Un bean CDI aparte confirma la transacción local antes de exponer un rechazo.
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public TransferenciaResponse ejecutar(CrearTransferenciaRequest request, String clave) {
        var resultado = operacion.decidir(request, clave);
        if (resultado.rechazo() != null) {
            var error = resultado.rechazo();
            throw new OperacionFinancieraException(422, error.codigo(), error.mensaje(), error.montoRestanteDisponible());
        }
        return resultado.transferencia();
    }
}
