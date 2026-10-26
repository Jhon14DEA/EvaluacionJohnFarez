package ec.johnfarez.evaluacion.cuentas.dto;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;
/** Exact decimal JSON contract, without binary floating point or scientific notation. */
public class DecimalStringSerializer extends JsonSerializer<BigDecimal> {
    @Override
    public void serialize(BigDecimal value, JsonGenerator json, SerializerProvider provider) throws IOException {
        json.writeString(value.toPlainString());
    }
}
