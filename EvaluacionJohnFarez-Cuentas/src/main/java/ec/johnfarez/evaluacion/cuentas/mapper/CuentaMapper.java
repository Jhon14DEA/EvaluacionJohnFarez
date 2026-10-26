package ec.johnfarez.evaluacion.cuentas.mapper;

import ec.johnfarez.evaluacion.cuentas.dto.*;
import ec.johnfarez.evaluacion.cuentas.entity.Cuenta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CuentaMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "saldo", source = "saldoInicial")
    Cuenta toEntity(CrearCuentaRequest request);
    CuentaResponse toResponse(Cuenta cuenta);
}
