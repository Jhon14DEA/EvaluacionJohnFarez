package ec.johnfarez.evaluacion.cuentas.service;

import ec.johnfarez.evaluacion.cuentas.dto.*;
import ec.johnfarez.evaluacion.cuentas.entity.Cuenta;
import ec.johnfarez.evaluacion.cuentas.exception.OperacionFinancieraException;
import ec.johnfarez.evaluacion.cuentas.mapper.TransferenciaMapper;
import ec.johnfarez.evaluacion.cuentas.repository.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TransferenciaOperacion {
    private static final Logger LOG = Logger.getLogger(TransferenciaOperacion.class);
    private static final BigDecimal MAX_SALDO = new BigDecimal("99999999999999999.99");

    private final CuentaRepository cuentas;
    private final TransferenciaRepository transferencias;
    private final TransferenciaMapper mapper;
    private final Validator validator;
    private final RelojFinanciero reloj;
    private final ReglasTransferencia reglas;

    @Inject
    public TransferenciaOperacion(CuentaRepository cuentas, TransferenciaRepository transferencias,
            TransferenciaMapper mapper, Validator validator, RelojFinanciero reloj, ReglasTransferencia reglas) {
        this.cuentas = cuentas;
        this.transferencias = transferencias;
        this.mapper = mapper;
        this.validator = validator;
        this.reloj = reloj;
        this.reglas = reglas;
    }

    /**
     * Decide y aplica una transferencia de forma atómica (CA-02, CA-03 y CA-04).
     * <p>
     * En una sola transacción valida la solicitud, resuelve la idempotencia, bloquea ambas cuentas,
     * aplica las reglas de negocio (cuentas activas, saldo suficiente, rango de saldo, límite diario
     * de CA-04), marca la operación como sospechosa cuando corresponde (CA-03) y actualiza ambos
     * saldos a la vez. Si alguna validación de negocio falla se registra el rechazo; si ocurre un
     * error técnico, la transacción completa se revierte.
     *
     * @param request cuentas origen/destino y monto de la transferencia
     * @param claveSolicitada clave de idempotencia opcional; si es nula se genera una
     * @return el resultado con la transferencia ejecutada o el detalle del rechazo
     */
    @Transactional(rollbackOn = Exception.class)
    public Resultado decidir(CrearTransferenciaRequest request, String claveSolicitada) {
        validarSolicitud(request);
        String clave = resolverClave(claveSolicitada);
        LOG.infof("Transferencia solicitada: origen=%s destino=%s monto=%s clave=%s",
            request.cuentaOrigenId(), request.cuentaDestinoId(), request.monto().toPlainString(), clave);
        adquirirBloqueo(clave);

        var previa = resolverIdempotencia(request, clave);
        if (previa.isPresent()) {
            LOG.infof("Idempotencia: se reutiliza el resultado previo de la clave=%s", clave);
            return previa.get();
        }

        var par = bloquearCuentas(request);
        BigDecimal monto = request.monto().setScale(2, RoundingMode.UNNECESSARY);
        var contexto = new ContextoEjecucion(par, monto, reloj.ahora(), clave);
        var rechazo = intentarRechazo(request, contexto);
        if (rechazo.isPresent()) {
            return rechazo.get();
        }
        return aplicar(request, contexto);
    }

    private void validarSolicitud(CrearTransferenciaRequest request) {
        if (request == null) {
            throw fallo(400, "SOLICITUD_INVALIDA", "El cuerpo es obligatorio");
        }
        var errores = validator.validate(request);
        if (!errores.isEmpty()) {
            throw new ConstraintViolationException(errores);
        }
        if (request.cuentaOrigenId().equals(request.cuentaDestinoId())) {
            throw fallo(400, "CUENTAS_IGUALES", "Las cuentas origen y destino deben ser diferentes");
        }
    }

    private String resolverClave(String claveSolicitada) {
        String clave = claveSolicitada == null ? UUID.randomUUID().toString() : claveSolicitada;
        if (!clave.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw fallo(400, "CLAVE_INVALIDA", "Idempotency-Key admite 1 a 128 letras, digitos o . _ : -");
        }
        return clave;
    }

    private void adquirirBloqueo(String clave) {
        if (transferencias.bloquearClave(clave) < 0) {
            throw fallo(503, "OPERACION_OCUPADA",
                "No se pudo adquirir el bloqueo de idempotencia; no se ejecuto la transferencia");
        }
    }

    private Optional<Resultado> resolverIdempotencia(CrearTransferenciaRequest request, String clave) {
        var anterior = transferencias.buscarPorClave(clave);
        if (anterior.isEmpty()) {
            return Optional.empty();
        }
        var t = anterior.get();
        if (!t.cuentaOrigenId.equals(request.cuentaOrigenId())
                || !t.cuentaDestinoId.equals(request.cuentaDestinoId())
                || t.monto.compareTo(request.monto()) != 0) {
            throw fallo(409, "CONFLICTO_IDEMPOTENCIA", "La clave ya fue utilizada con datos diferentes");
        }
        if ("rechazada".equals(t.estado)) {
            return Optional.of(new Resultado(null, mapper.errorRechazo(t)));
        }
        return Optional.of(new Resultado(mapper.toResponse(t), null));
    }

    private ParCuentas bloquearCuentas(CrearTransferenciaRequest request) {
        // Siempre se bloquea primero el identificador menor, sin importar la dirección de la transferencia.
        long primero = Math.min(request.cuentaOrigenId(), request.cuentaDestinoId());
        long segundo = Math.max(request.cuentaOrigenId(), request.cuentaDestinoId());
        Cuenta menor = cuentas.findById(primero, LockModeType.PESSIMISTIC_WRITE);
        Cuenta mayor = cuentas.findById(segundo, LockModeType.PESSIMISTIC_WRITE);
        Cuenta origen = request.cuentaOrigenId() == primero ? menor : mayor;
        Cuenta destino = request.cuentaDestinoId() == primero ? menor : mayor;
        if (origen == null) {
            throw fallo(404, "ORIGEN_NO_EXISTE", "La cuenta origen no existe");
        }
        if (destino == null) {
            throw fallo(404, "DESTINO_NO_EXISTE", "La cuenta destino no existe");
        }
        return new ParCuentas(origen, destino);
    }

    private Optional<Resultado> intentarRechazo(CrearTransferenciaRequest request, ContextoEjecucion ctx) {
        try {
            validarNegocio(ctx.par().origen(), ctx.par().destino(), ctx.monto(), ctx.ahora());
            return Optional.empty();
        } catch (OperacionFinancieraException rechazo) {
            // Aquí solo llegan validaciones esperadas, antes de escribir ningún saldo.
            var t = mapper.toEntity(request);
            t.monto = ctx.monto();
            t.fecha = ctx.ahora();
            t.estado = "rechazada";
            t.claveIdempotencia = ctx.clave();
            t.codigoRechazo = rechazo.codigo();
            t.mensajeRechazo = rechazo.getMessage();
            t.disponibleRechazo = rechazo.montoRestanteDisponible();
            transferencias.registrar(t); // Los errores técnicos se propagan y revierten; nunca se convierten en 422.
            LOG.warnf("Transferencia rechazada por regla de negocio: codigo=%s origen=%s destino=%s monto=%s clave=%s",
                rechazo.codigo(), request.cuentaOrigenId(), request.cuentaDestinoId(),
                ctx.monto().toPlainString(), ctx.clave());
            return Optional.of(new Resultado(null, mapper.errorRechazo(t)));
        }
    }

    private Resultado aplicar(CrearTransferenciaRequest request, ContextoEjecucion ctx) {
        Cuenta origen = ctx.par().origen();
        Cuenta destino = ctx.par().destino();
        BigDecimal monto = ctx.monto();
        boolean sospechosa = reglas.sospechosa(monto, transferencias.ejecutadasEnHora(origen.id, ctx.ahora()));
        if (sospechosa) {
            LOG.warnf("Deteccion de fraude: transferencia marcada en_revision por monto o frecuencia; origen=%s monto=%s clave=%s",
                origen.id, monto.toPlainString(), ctx.clave());
        }
        origen.saldo = origen.saldo.subtract(monto);
        destino.saldo = destino.saldo.add(monto);

        cuentas.flush();
        var transferencia = mapper.toEntity(request);
        transferencia.monto = monto;
        transferencia.fecha = ctx.ahora();
        transferencia.estado = sospechosa ? "en_revision" : "completada";
        transferencia.claveIdempotencia = ctx.clave();
        transferencias.registrar(transferencia);
        LOG.infof("Transferencia ejecutada: id=%s estado=%s origen=%s destino=%s monto=%s clave=%s",
            transferencia.id, transferencia.estado, origen.id, destino.id, monto.toPlainString(), ctx.clave());
        return new Resultado(mapper.toResponse(transferencia), null);
    }

    public record Resultado(TransferenciaResponse transferencia, ErrorResponse rechazo) {}

    private record ParCuentas(Cuenta origen, Cuenta destino) {}

    private record ContextoEjecucion(ParCuentas par, BigDecimal monto, OffsetDateTime ahora, String clave) {}

    private void validarNegocio(Cuenta origen, Cuenta destino, BigDecimal monto, OffsetDateTime ahora) {
        if (!"activo".equals(origen.estado) || !"activo".equals(destino.estado)) {
            throw fallo(422, "CUENTA_INACTIVA", "Ambas cuentas deben estar activas");
        }
        if (origen.saldo.compareTo(monto) < 0) {
            throw fallo(422, "SALDO_INSUFICIENTE", "La cuenta origen no dispone de saldo suficiente");
        }
        if (destino.saldo.add(monto).compareTo(MAX_SALDO) > 0) {
            throw fallo(422, "SALDO_FUERA_DE_RANGO", "El saldo destino excederia la precision monetaria disponible");
        }
        var dia = reglas.dia(ahora);
        reglas.validarLimite(transferencias.totalEjecutado(origen.id, dia.inicio(), dia.fin()), monto);
    }

    private OperacionFinancieraException fallo(int status, String codigo, String mensaje) {
        return new OperacionFinancieraException(status, codigo, mensaje);
    }
}
