package ec.johnfarez.evaluacion.cuentas.service;

import ec.johnfarez.evaluacion.cuentas.dto.*;
import ec.johnfarez.evaluacion.cuentas.exception.CuentaDuplicadaException;
import ec.johnfarez.evaluacion.cuentas.mapper.CuentaMapper;
import ec.johnfarez.evaluacion.cuentas.repository.CriterioOrden;
import ec.johnfarez.evaluacion.cuentas.repository.CuentaRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.ws.rs.BadRequestException;
import java.sql.SQLException;
import java.util.List;

@ApplicationScoped
public class CuentaService {
    private final CuentaRepository repository;
    private final CuentaMapper mapper;
    private final Validator validator;

    @Inject
    public CuentaService(CuentaRepository repository, CuentaMapper mapper, Validator validator) {
        this.repository = repository;
        this.mapper = mapper;
        this.validator = validator;
    }

    /**
     * Registra una nueva cuenta bancaria (CA-01).
     * <p>
     * Valida los campos del request, rechaza números de cuenta duplicados y persiste la cuenta
     * dentro de una transacción. Devuelve la cuenta creada con su identificador generado.
     *
     * @param request datos de la cuenta a registrar (número de 10 dígitos, titular, tipo, saldo inicial ≥ 0, estado)
     * @return la cuenta persistida con su identificador
     * @throws jakarta.ws.rs.BadRequestException si el cuerpo es nulo
     * @throws jakarta.validation.ConstraintViolationException si algún campo no cumple las validaciones
     * @throws ec.johnfarez.evaluacion.cuentas.exception.CuentaDuplicadaException si el número de cuenta ya existe
     */
    @Transactional
    public CuentaResponse crear(CrearCuentaRequest request) {
        if (request == null) {
            throw new BadRequestException("El cuerpo es obligatorio");
        }
        var errores = validator.validate(request);
        if (!errores.isEmpty()) {
            throw new ConstraintViolationException(errores);
        }
        if (repository.existeNumero(request.numeroCuenta())) {
            throw new CuentaDuplicadaException();
        }
        var cuenta = mapper.toEntity(request);
        persistirDetectandoDuplicado(cuenta);
        return mapper.toResponse(cuenta);
    }

    private void persistirDetectandoDuplicado(ec.johnfarez.evaluacion.cuentas.entity.Cuenta cuenta) {
        try {
            // Se hace flush dentro de esta transacción para detectar también inserciones duplicadas concurrentes.
            repository.persistAndFlush(cuenta);
        } catch (PersistenceException exception) {
            if (esDuplicado(exception)) {
                throw new CuentaDuplicadaException();
            }
            throw exception;
        }
    }

    /**
     * Lista todas las cuentas registradas ordenadas por identificador (CA-01).
     *
     * @return todas las cuentas registradas
     */
    public List<CuentaResponse> listar() {
        return repository.listAll(Sort.by("id")).stream().map(mapper::toResponse).toList();
    }

    public PaginaResponse<CuentaResponse> listarPaginado(ConsultaCuentas consulta) {
        var orden = new CriterioOrden(consulta.texto(), consulta.campo(), consulta.ascendente());
        var contenido = repository.listarPagina(orden, consulta.pagina(), consulta.tamano())
            .stream().map(mapper::toResponse).toList();
        return PaginaResponse.de(contenido, consulta.pagina(), consulta.tamano(), repository.contar(consulta.texto()));
    }

    private boolean esDuplicado(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 2601 || sql.getErrorCode() == 2627)) {
                return true;
            }
        }
        return false;
    }
}
