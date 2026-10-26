package ec.johnfarez.evaluacion.cuentas;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
@QuarkusTest
class PrecisionSqlTest {
    String account(String balance) {
        return given().contentType("application/json").body(Map.of(
            "numeroCuenta", "0"+String.format("%09d",new SecureRandom().nextInt(1000000000)),
            "titular","Precision audit91","tipo","ahorro","estado","activo","saldoInicial",balance))
            .post("/api/cuentas").then().statusCode(201).body("id",instanceOf(String.class),"saldo",equalTo(balance))
            .extract().jsonPath().getString("id");
    }
    @Test void openApiDeclaresExactStringContract() {
        given().accept("application/json").get("/q/openapi").then().statusCode(200)
            .body("components.schemas.CrearTransferenciaRequest.properties.monto.type",equalTo("string"),
                  "components.schemas.CuentaResponse.properties.id.type",equalTo("string"),
                  "components.schemas.CuentaResponse.properties.saldo.type",equalTo("string"));
    }
    @Test void movesOneCentWithoutRoundingMaximumSqlBalance() {
        var origin=account("99999999999999999.99"); var destination=account("0.00");
        var key=UUID.randomUUID().toString();
        var body=Map.of("cuentaOrigenId",origin,"cuentaDestinoId",destination,"monto","0.01");
        var id=given().contentType("application/json").header("Idempotency-Key",key).body(body)
            .post("/api/transferencias").then().statusCode(201)
            .body("monto",equalTo("0.01"),"cuentaOrigenId",equalTo(origin)).extract().jsonPath().getString("id");
        given().contentType("application/json").header("Idempotency-Key",key).body(body)
            .post("/api/transferencias").then().statusCode(201).body("id",equalTo(id));
        given().get("/api/cuentas/"+origin+"/resumen").then().statusCode(200)
            .body("saldoActual",equalTo("99999999999999999.98"),"totalTransferidoHoy",equalTo("0.01"),"cantidadTransferenciasHoy",equalTo(1));
        given().get("/api/cuentas/"+destination+"/resumen").then().statusCode(200).body("saldoActual",equalTo("0.01"));
    }
}
