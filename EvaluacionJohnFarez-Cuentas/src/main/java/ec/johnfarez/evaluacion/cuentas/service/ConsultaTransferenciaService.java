package ec.johnfarez.evaluacion.cuentas.service;

import ec.johnfarez.evaluacion.cuentas.dto.*;
import ec.johnfarez.evaluacion.cuentas.exception.OperacionFinancieraException;
import ec.johnfarez.evaluacion.cuentas.mapper.TransferenciaMapper;
import ec.johnfarez.evaluacion.cuentas.repository.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@ApplicationScoped
public class ConsultaTransferenciaService {
    private static final Set<String> ESTADOS = Set.of("completada", "rechazada", "en_revision");

    private final TransferenciaRepository transferencias;
    private final CuentaRepository cuentas;
    private final TransferenciaMapper mapper;
    private final RelojFinanciero reloj;
    private final ReglasTransferencia reglas;

    @Inject
    public ConsultaTransferenciaService(TransferenciaRepository transferencias, CuentaRepository cuentas,
            TransferenciaMapper mapper, RelojFinanciero reloj, ReglasTransferencia reglas) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.mapper = mapper;
        this.reloj = reloj;
        this.reglas = reglas;
    }

    /**
     * Consulta el historial de transferencias aplicando los filtros recibidos (CA-05).
     * <p>
     * Valida los filtros y devuelve los resultados ordenados por fecha descendente.
     *
     * @param filtro filtros opcionales por cuenta origen/destino, rango de fechas y estado
     * @return historial de transferencias que cumple los filtros, ordenado por fecha descendente
     */
    public List<HistorialTransferenciaResponse> historial(FiltroHistorial filtro) {
        var criterio = validar(filtro);
        return transferencias.historial(criterio).stream()
            .map(mapper::toHistorial)
            .toList();
    }

    public PaginaResponse<HistorialTransferenciaResponse> historialPaginado(FiltroHistorial filtro, int pagina, int tamano) {
        var criterio = validar(filtro);
        var contenido = transferencias.historialPagina(criterio, pagina, tamano).stream()
            .map(mapper::toHistorial)
            .toList();
        long total = transferencias.contarHistorial(criterio);
        return PaginaResponse.de(contenido, pagina, tamano, total);
    }

    private CriterioHistorial validar(FiltroHistorial filtro) {
        validarIdentificadores(filtro);
        validarEstado(filtro.estado());
        var inicio = fecha(filtro.desde());
        var fin = fecha(filtro.hasta());
        validarRangoFechas(inicio, fin);
        return new CriterioHistorial(filtro.origen(), filtro.destino(), inicio, fin, filtro.estado());
    }

    private void validarIdentificadores(FiltroHistorial filtro) {
        boolean origenInvalido = filtro.origen() != null && filtro.origen() <= 0;
        boolean destinoInvalido = filtro.destino() != null && filtro.destino() <= 0;
        if (origenInvalido || destinoInvalido) {
            throw invalido("Los identificadores deben ser positivos");
        }
    }

    private void validarEstado(String estado) {
        if (estado != null && !ESTADOS.contains(estado)) {
            throw invalido("Estado invalido");
        }
    }

    private void validarRangoFechas(OffsetDateTime inicio, OffsetDateTime fin) {
        if (inicio != null && fin != null && inicio.isAfter(fin)) {
            throw invalido("fechaDesde debe ser anterior o igual a fechaHasta");
        }
    }

    private OffsetDateTime fecha(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor);
        } catch (DateTimeParseException _) {
            throw invalido("Las fechas requieren ISO-8601 con offset, por ejemplo 2026-10-23T05:00:00Z");
        }
    }

    private OperacionFinancieraException invalido(String mensaje) {
        return new OperacionFinancieraException(400, "FILTROS_INVALIDOS", mensaje);
    }

    /**
     * Calcula el resumen en tiempo real de una cuenta (CA-05).
     * <p>
     * Devuelve el saldo actual, el total transferido hoy, la cantidad de transferencias del día
     * y cuántas quedaron marcadas como sospechosas.
     *
     * @param id identificador de la cuenta
     * @return resumen de la cuenta
     * @throws OperacionFinancieraException con código {@code CUENTA_NO_EXISTE} si la cuenta no existe
     */
    @Transactional
    public ResumenCuentaResponse resumen(long id) {
        // Toma el mismo bloqueo de cuenta que los escritores, para que saldo y agregados reflejen un estado consistente.
        var cuenta = cuentas.findById(id, LockModeType.PESSIMISTIC_WRITE);
        if (cuenta == null) {
            throw new OperacionFinancieraException(404, "CUENTA_NO_EXISTE", "La cuenta no existe");
        }
        var dia = reglas.dia(reloj.ahora());
        return new ResumenCuentaResponse(id, cuenta.saldo,
            transferencias.totalEjecutado(id, dia.inicio(), dia.fin()),
            transferencias.contarEjecutadas(id, dia.inicio(), dia.fin(), false),
            transferencias.contarEjecutadas(id, dia.inicio(), dia.fin(), true));
    }
}
