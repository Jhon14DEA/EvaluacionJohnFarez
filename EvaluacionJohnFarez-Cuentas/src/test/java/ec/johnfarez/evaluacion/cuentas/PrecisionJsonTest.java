package ec.johnfarez.evaluacion.cuentas;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.johnfarez.evaluacion.cuentas.dto.CrearTransferenciaRequest;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class PrecisionJsonTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void preservesBigintAndDecimalStrings() throws Exception {
        var request = new CrearTransferenciaRequest(9007199254740993L, Long.MAX_VALUE, new BigDecimal("99999999999999999.99"));
        var tree = json.readTree(json.writeValueAsString(request));
        assertTrue(tree.get("cuentaOrigenId").isTextual());
        assertEquals("9007199254740993", tree.get("cuentaOrigenId").textValue());
        assertEquals("9223372036854775807", tree.get("cuentaDestinoId").textValue());
        assertEquals("99999999999999999.99", tree.get("monto").textValue());
        assertEquals(request, json.treeToValue(tree, CrearTransferenciaRequest.class));
    }
    @Test void decimalNeverUsesScientificNotation() throws Exception {
        var request = new CrearTransferenciaRequest(1L, 2L, new BigDecimal("1E+3"));
        assertEquals("1000", json.readTree(json.writeValueAsString(request)).get("monto").textValue());
    }
}
