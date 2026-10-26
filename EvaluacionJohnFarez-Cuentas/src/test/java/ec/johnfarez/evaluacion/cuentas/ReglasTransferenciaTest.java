package ec.johnfarez.evaluacion.cuentas;
import ec.johnfarez.evaluacion.cuentas.service.ReglasTransferencia;
import ec.johnfarez.evaluacion.cuentas.exception.OperacionFinancieraException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
class ReglasTransferenciaTest {
    final ReglasTransferencia reglas = new ReglasTransferencia();
    @ParameterizedTest @CsvSource({"5000.00,4,false", "5000.01,0,true", "1,4,false", "1,5,true", "5000,5,true"})
    void umbralesIndependientes(String monto,long anteriores,boolean esperado) {
        assertEquals(esperado,reglas.sospechosa(new BigDecimal(monto),anteriores));
    }
    @ParameterizedTest @CsvSource({"9999.99,0.01", "0,10000", "8000,2000"})
    void limiteInclusivo(String acumulado,String monto) {
        assertDoesNotThrow(()->reglas.validarLimite(new BigDecimal(acumulado),new BigDecimal(monto)));
    }
    @ParameterizedTest @CsvSource({"8000,2500,2000", "10000,0.01,0", "0,10000.01,10000"})
    void disponible(String acumulado,String monto,String disponible) {
        var acumuladoDecimal=new BigDecimal(acumulado);
        var montoDecimal=new BigDecimal(monto);
        var e=assertThrows(OperacionFinancieraException.class,()->reglas.validarLimite(acumuladoDecimal,montoDecimal));
        assertEquals(422,e.status());assertEquals("LIMITE_DIARIO_EXCEDIDO",e.codigo());
        assertEquals(0,new BigDecimal(disponible).compareTo(e.montoRestanteDisponible()));
    }
    @Test void calendarioGuayaquilEnUTC() {
        var dia=reglas.dia(OffsetDateTime.parse("2026-10-24T04:59:59.999999Z"));
        assertEquals(OffsetDateTime.parse("2026-10-23T05:00:00Z"),dia.inicio());
        assertEquals(OffsetDateTime.parse("2026-10-24T05:00:00Z"),dia.fin());
        assertEquals(dia.fin(),reglas.dia(dia.fin()).inicio());
    }
}
