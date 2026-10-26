package ec.johnfarez.evaluacion.cuentas;

import io.quarkus.test.junit.QuarkusTest;
import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CuentaResourceTest {
    private static final SecureRandom RANDOM = new SecureRandom();
    @Inject AgroalDataSource dataSource;

    private Map<String, Object> cuenta() {
        var request = new HashMap<String, Object>();
        request.put("numeroCuenta", "0" + String.format("%09d", RANDOM.nextInt(1_000_000_000)));
        request.put("titular", "Prueba CA01");
        request.put("tipo", "ahorro");
        request.put("saldoInicial", 125.50);
        request.put("estado", "activo");
        return request;
    }

    private void invalida(Map<String, Object> request, String campo) {
        given().contentType("application/json").body(request).when().post("/api/cuentas")
            .then().statusCode(400).contentType("application/json")
            .body("codigo", equalTo("VALIDACION"), "mensaje", not(blankOrNullString()),
                  "erroresCampos.campo", hasItem(campo), "id", nullValue());
    }

    @Test void creaCuentaCon201YConservaCeros() {
        var request = cuenta();
        given().contentType("application/json").body(request).when().post("/api/cuentas")
            .then().statusCode(201).contentType("application/json")
            .body("id", notNullValue(), "numeroCuenta", equalTo(request.get("numeroCuenta")),
                  "titular", equalTo("Prueba CA01"), "tipo", equalTo("ahorro"),
                  "saldo", equalTo("125.5"), "estado", equalTo("activo"))
            .body("keySet().size()", equalTo(6));
    }

    @Test void aceptaSaldoCeroCorrienteEInactivo() {
        var request = cuenta();
        request.put("tipo", "corriente");request.put("estado", "inactivo");request.put("saldoInicial", 0);
        given().contentType("application/json").body(request).post("/api/cuentas")
            .then().statusCode(201).body("saldo", equalTo("0"), "estado", equalTo("inactivo"));
    }

    @Test void rechazaDuplicadoConFormatoUniforme() {
        var request = cuenta();
        given().contentType("application/json").body(request).post("/api/cuentas").then().statusCode(201);
        given().contentType("application/json").body(request).post("/api/cuentas")
            .then().statusCode(409).body("codigo", equalTo("CUENTA_DUPLICADA"),
                 "mensaje", not(blankOrNullString()), "erroresCampos", empty());
    }

    @ParameterizedTest @ValueSource(strings = {"123456789", "12345678901", "abcdefghij", ""})
    void rechazaNumeroInvalido(String numero) {
        var request = cuenta();request.put("numeroCuenta", numero);invalida(request, "numeroCuenta");
    }

    @Test void rechazaSaldoNegativo() {
        var request = cuenta();request.put("saldoInicial", -0.01);invalida(request, "saldoInicial");
    }
    @Test void rechazaDecimalesQueSeRedondearian() {
        var request = cuenta();request.put("saldoInicial", 1.001);invalida(request, "saldoInicial");
    }
    @Test void rechazaTipoInvalido() {
        var request = cuenta();request.put("tipo", "credito");invalida(request, "tipo");
    }
    @Test void rechazaEstadoInvalido() {
        var request = cuenta();request.put("estado", "pendiente");invalida(request, "estado");
    }
    @Test void rechazaTitularVacio() {
        var request = cuenta();request.put("titular", "   ");invalida(request, "titular");
    }
    @ParameterizedTest @ValueSource(strings = {"numeroCuenta", "titular", "tipo", "saldoInicial", "estado"})
    void rechazaCamposAusentes(String campo) {
        var request = cuenta();request.remove(campo);invalida(request, campo);
    }
    @Test void listaCuentasPersistidas() {
        var request = cuenta();
        given().contentType("application/json").body(request).post("/api/cuentas").then().statusCode(201);
        given().get("/api/cuentas").then().statusCode(200).contentType("application/json")
            .body("numeroCuenta", hasItem(request.get("numeroCuenta")));
    }
    @ParameterizedTest @ValueSource(strings = {"{", "null", "{\"saldoInicial\":\"no-es-numero\"}"})
    void erroresJsonTienenFormatoUniforme(String body) {
        given().contentType("application/json").body(body).post("/api/cuentas")
            .then().statusCode(400).contentType("application/json")
            .body("codigo", not(blankOrNullString()), "mensaje", not(blankOrNullString()),
                  "erroresCampos", notNullValue());
    }
    @Test void restriccionSqlImpideDuplicados() throws Exception {
        var request = cuenta();
        given().contentType("application/json").body(request).post("/api/cuentas").then().statusCode(201);
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("INSERT INTO cuenta(numero_cuenta,titular,tipo,saldo,estado) VALUES (?, 'Prueba SQL', 'ahorro', 0, 'activo')")) {
            statement.setString(1, request.get("numeroCuenta").toString());
            var error = assertThrows(SQLException.class, statement::executeUpdate);
            assertTrue(error.getErrorCode() == 2601 || error.getErrorCode() == 2627);
        }
    }
    @Test void solicitudesConcurrentesNoCreanDuplicados() {
        var request = cuenta();
        var first = CompletableFuture.supplyAsync(() -> given().contentType("application/json").body(request).post("/api/cuentas").statusCode());
        var second = CompletableFuture.supplyAsync(() -> given().contentType("application/json").body(request).post("/api/cuentas").statusCode());
        assertEquals(java.util.List.of(201, 409), java.util.stream.Stream.of(first.join(), second.join()).sorted().toList());
    }
}
