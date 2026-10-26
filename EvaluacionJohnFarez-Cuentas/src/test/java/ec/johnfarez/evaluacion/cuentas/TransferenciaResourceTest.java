package ec.johnfarez.evaluacion.cuentas;

import ec.johnfarez.evaluacion.cuentas.dto.*;
import ec.johnfarez.evaluacion.cuentas.entity.Transferencia;
import ec.johnfarez.evaluacion.cuentas.repository.TransferenciaRepository;
import ec.johnfarez.evaluacion.cuentas.service.CuentaService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class TransferenciaResourceTest {
    @Inject CuentaService cuentas;
    @Inject AgroalDataSource db;
    @InjectSpy TransferenciaRepository repository;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ENDPOINT = "/api/transferencias";

    @AfterEach void restaurarSpy() { reset(repository); }
    private String clave() { return UUID.randomUUID().toString(); }
    private long cuenta(String saldo, String estado) {
        return cuentas.crear(new CrearCuentaRequest("1" + String.format("%09d", RANDOM.nextInt(1_000_000_000)),
            "Prueba CA02", "ahorro", new BigDecimal(saldo), estado)).id();
    }
    private Map<String, Object> request(long origen, long destino, String monto) {
        return Map.of("cuentaOrigenId", origen, "cuentaDestinoId", destino, "monto", new BigDecimal(monto));
    }
    private io.restassured.response.Response enviar(Map<String, Object> body, String key) {
        return given().contentType("application/json").header("Idempotency-Key", key).body(body).post(ENDPOINT);
    }
    private BigDecimal saldo(long id) throws Exception {
        try (var c = db.getConnection(); var s = c.prepareStatement("SELECT saldo FROM cuenta WHERE id=?")) {
            s.setLong(1, id);
            try (var rs = s.executeQuery()) { assertTrue(rs.next());return rs.getBigDecimal(1); }
        }
    }
    private long registros(String key) throws Exception {
        try (var c = db.getConnection(); var s = c.prepareStatement("SELECT COUNT(*) FROM transferencia WHERE clave_idempotencia=?")) {
            s.setString(1, key);
            try (var rs = s.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }
    private void saldoEs(long id, String esperado) throws Exception {
        assertEquals(0, new BigDecimal(esperado).compareTo(saldo(id)));
    }
    private void rechaza(Map<String,Object> body, int status, String codigo) throws Exception {
        String key = clave();
        enviar(body,key).then().statusCode(status).body("codigo",equalTo(codigo),
            "mensaje",not(blankOrNullString()),"erroresCampos",notNullValue()); 
        assertEquals(status == 422 ? 1 : 0, registros(key));
    }

    @Test void transferenciaValidaConservaTotalYPersiste() throws Exception {
        long a=cuenta("100.00","activo"), b=cuenta("20.00","activo");String key=clave();
        enviar(request(a,b,"30.25"),key).then().statusCode(201)
            .header("Idempotency-Key",key).body("id",notNullValue(),"estado",equalTo("completada"),
                "cuentaOrigenId",equalTo(Long.toString(a)),"cuentaDestinoId",equalTo(Long.toString(b)),"fecha",notNullValue());
        saldoEs(a,"69.75");saldoEs(b,"50.25");assertEquals(1,registros(key));
        assertEquals(0,new BigDecimal("120.00").compareTo(saldo(a).add(saldo(b))));
    }
    @Test void saldoInsuficiente() throws Exception {
        long a=cuenta("10","activo"),b=cuenta("0","activo");
        rechaza(request(a,b,"10.01"),422,"SALDO_INSUFICIENTE");saldoEs(a,"10");saldoEs(b,"0");
    }
    @Test void origenInexistente() throws Exception {
        rechaza(request(Long.MAX_VALUE,cuenta("0","activo"),"1"),404,"ORIGEN_NO_EXISTE");
    }
    @Test void destinoInexistente() throws Exception {
        long a=cuenta("10","activo");rechaza(request(a,Long.MAX_VALUE,"1"),404,"DESTINO_NO_EXISTE");saldoEs(a,"10");
    }
    @Test void origenInactivo() throws Exception {
        rechaza(request(cuenta("10","inactivo"),cuenta("0","activo"),"1"),422,"CUENTA_INACTIVA");
    }
    @Test void destinoInactivo() throws Exception {
        rechaza(request(cuenta("10","activo"),cuenta("0","inactivo"),"1"),422,"CUENTA_INACTIVA");
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","100000000000000000.00"})
    void montoInvalido(String monto) throws Exception {
        rechaza(request(cuenta("10","activo"),cuenta("0","activo"),monto),400,"VALIDACION");
    }
    @Test void mismaCuenta() throws Exception {
        long a=cuenta("10","activo");rechaza(request(a,a,"1"),400,"CUENTAS_IGUALES");saldoEs(a,"10");
    }
    @Test void rechazaClaveConCaracteresInvalidos() {
        rechazaConClave("clave/invalida");
    }
    @Test void rechazaClaveDemasiadoLarga() {
        rechazaConClave("a".repeat(129));
    }
    private void rechazaConClave(String key) {
        enviar(request(cuenta("10","activo"),cuenta("0","activo"),"1"),key)
            .then().statusCode(400).body("codigo",equalTo("CLAVE_INVALIDA"));
    }
    @Test void sinCabeceraConservaContratoOriginal() {
        given().contentType("application/json").body(request(cuenta("10","activo"),cuenta("0","activo"),"1"))
            .post(ENDPOINT).then().statusCode(201).header("Idempotency-Key",not(blankOrNullString()));
    }
    @Test void reintentoDevuelveMismoResultadoSinOtroDebito() throws Exception {
        long a=cuenta("100","activo"),b=cuenta("0","activo");String key=clave();
        var primero=enviar(request(a,b,"10.0"),key);primero.then().statusCode(201);
        var segundo=enviar(request(a,b,"10.00"),key);segundo.then().statusCode(201);
        assertEquals(primero.jsonPath().getMap(""),segundo.jsonPath().getMap(""));
        saldoEs(a,"90");saldoEs(b,"10");assertEquals(1,registros(key));
    }
    @Test void conflictoMontoDistinto() throws Exception {
        long a=cuenta("100","activo"),b=cuenta("0","activo");String key=clave();
        enviar(request(a,b,"10"),key).then().statusCode(201);
        enviar(request(a,b,"11"),key).then().statusCode(409).body("codigo",equalTo("CONFLICTO_IDEMPOTENCIA"));
        saldoEs(a,"90");saldoEs(b,"10");assertEquals(1,registros(key));
    }
    @Test void conflictoCuentasDistintasMismaClave() throws Exception {
        long a=cuenta("100","activo"),b=cuenta("0","activo"),c=cuenta("100","activo"),d=cuenta("0","activo");String key=clave();
        enviar(request(a,b,"10"),key).then().statusCode(201);
        enviar(request(c,d,"10"),key).then().statusCode(409);
        saldoEs(c,"100");saldoEs(d,"0");assertEquals(1,registros(key));
    }
    @Test void clavesDistinguenMayusculas() throws Exception {
        long a=cuenta("100","activo"),b=cuenta("0","activo");String key=clave();
        enviar(request(a,b,"10"),"a"+key).then().statusCode(201);
        enviar(request(a,b,"10"),"A"+key).then().statusCode(201);
        saldoEs(a,"80");saldoEs(b,"20");
    }
    @Test void rollbackTrasActualizarAmbosSaldosEInsertarRegistro() throws Exception {
        long a=cuenta("100","activo"),b=cuenta("20","activo");String key=clave();
        AtomicBoolean escriturasVistas=new AtomicBoolean();
        doAnswer(call -> {
            call.callRealMethod(); // INSERT y flush reales, no una base de datos simulada.
            var t=(Transferencia)call.getArgument(0);
            assertEquals(1,registros(t.claveIdempotencia));
            saldoEs(a,"70");saldoEs(b,"50"); // La misma conexión JDBC enlistada ve todas las escrituras sin confirmar.
            escriturasVistas.set(true);
            throw new IllegalStateException("Fallo controlado de prueba tras las tres escrituras SQL");
        }).when(repository).registrar(org.mockito.ArgumentMatchers.any(Transferencia.class));
        enviar(request(a,b,"30"),key).then().statusCode(500).body("codigo",equalTo("ERROR_INTERNO"));
        assertTrue(escriturasVistas.get());saldoEs(a,"100");saldoEs(b,"20");assertEquals(0,registros(key));
        reset(repository);
        enviar(request(a,b,"30"),key).then().statusCode(201); // El rollback también liberó la clave.
        saldoEs(a,"70");saldoEs(b,"50");assertEquals(1,registros(key));
    }
    private List<io.restassured.response.Response> simultaneas(Map<String,Object> first,String k1,Map<String,Object> second,String k2) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<io.restassured.response.Response> one=()->{ready.countDown();start.await();return enviar(first,k1);};
            Callable<io.restassured.response.Response> two=()->{ready.countDown();start.await();return enviar(second,k2);};
            var f1=executor.submit(one);var f2=executor.submit(two);
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            return List.of(f1.get(25,TimeUnit.SECONDS),f2.get(25,TimeUnit.SECONDS));
        } finally { start.countDown();executor.shutdownNow(); }
    }
    @Test void concurrenciaNoConsumeDosVecesMismoSaldo() throws Exception {
        long a=cuenta("100","activo"),b=cuenta("0","activo"),c=cuenta("0","activo");
        String k1=clave(),k2=clave();
        var respuestas=simultaneas(request(a,b,"80"),k1,request(a,c,"80"),k2);
        assertEquals(List.of(201,422),respuestas.stream().map(x->x.statusCode()).sorted().toList());
        saldoEs(a,"20");assertEquals(0,new BigDecimal("80").compareTo(saldo(b).add(saldo(c))));
        assertEquals(2,registros(k1)+registros(k2));
    }
    @Test void reintentosConcurrentesMismaClave() throws Exception {
        long a=cuenta("100","activo"),b=cuenta("0","activo");String key=clave();
        var respuestas=simultaneas(request(a,b,"80"),key,request(a,b,"80"),key);
        respuestas.forEach(x->x.then().statusCode(201));
        assertEquals(respuestas.get(0).jsonPath().getLong("id"),respuestas.get(1).jsonPath().getLong("id"));
        saldoEs(a,"20");saldoEs(b,"80");assertEquals(1,registros(key));
    }
    @Test void mismaClaveConcurrenteConCuentasDisjuntas() throws Exception {
        long a=cuenta("100","activo"),b=cuenta("0","activo"),c=cuenta("100","activo"),d=cuenta("0","activo");String key=clave();
        var respuestas=simultaneas(request(a,b,"10"),key,request(c,d,"10"),key);
        assertEquals(List.of(201,409),respuestas.stream().map(x->x.statusCode()).sorted().toList());
        assertEquals(1,registros(key));
        assertEquals(0,new BigDecimal("190").compareTo(saldo(a).add(saldo(c))));
        assertEquals(0,new BigDecimal("10").compareTo(saldo(b).add(saldo(d))));
    }
    @Test void transferenciasOpuestasConOrdenDeterminista() throws Exception {
        long a=cuenta("100","activo"),b=cuenta("100","activo");
        var respuestas=simultaneas(request(a,b,"20"),clave(),request(b,a,"30"),clave());
        respuestas.forEach(x->x.then().statusCode(201));saldoEs(a,"110");saldoEs(b,"90");
    }
    @Test void aplicaLimiteCa04SinModificarSaldos() throws Exception {
        long a=cuenta("20000","activo"),b=cuenta("0","activo");
        enviar(request(a,b,"11000"),clave()).then().statusCode(422).body("codigo",equalTo("LIMITE_DIARIO_EXCEDIDO"));
        saldoEs(a,"20000");saldoEs(b,"0");
    }
    @Test void evitaDesbordamientoDestino() throws Exception {
        long a=cuenta("100","activo"),b=cuenta("99999999999999999.99","activo");
        rechaza(request(a,b,"1"),422,"SALDO_FUERA_DE_RANGO");saldoEs(a,"100");saldoEs(b,"99999999999999999.99");
    }
}
