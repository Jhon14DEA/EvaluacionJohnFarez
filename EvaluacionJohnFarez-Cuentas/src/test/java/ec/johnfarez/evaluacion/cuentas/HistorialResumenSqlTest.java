package ec.johnfarez.evaluacion.cuentas;
import ec.johnfarez.evaluacion.cuentas.dto.*;
import ec.johnfarez.evaluacion.cuentas.entity.Transferencia;
import ec.johnfarez.evaluacion.cuentas.service.*;
import ec.johnfarez.evaluacion.cuentas.repository.TransferenciaRepository;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@QuarkusTest
class HistorialResumenSqlTest {
    @Inject CuentaService cuentas;
    @Inject AgroalDataSource db;
    @InjectMock RelojFinanciero reloj;
    @InjectSpy TransferenciaRepository repository;
    final AtomicReference<OffsetDateTime> hora=new AtomicReference<>();
    static final SecureRandom RANDOM=new SecureRandom();
    @BeforeEach void iniciar() {
        hora.set(OffsetDateTime.parse("2026-10-23T17:00:00Z"));
        when(reloj.ahora()).thenAnswer(inv->hora.get());
    }
    @AfterEach void restaurar() { reset(repository); }
    long cuenta(String saldo) { return cuenta(saldo,"activo"); }
    long cuenta(String saldo,String estado) {
        return cuentas.crear(new CrearCuentaRequest("4"+String.format("%09d",RANDOM.nextInt(1_000_000_000)),
            "Prueba CA05","ahorro",new BigDecimal(saldo),estado)).id();
    }
    String clave() { return UUID.randomUUID().toString(); }
    io.restassured.response.Response enviar(long a,long b,String monto,String key) {
        return given().contentType("application/json").header("Idempotency-Key",key)
            .body(Map.of("cuentaOrigenId",a,"cuentaDestinoId",b,"monto",new BigDecimal(monto)))
            .post("/api/transferencias");
    }
    io.restassured.response.Response enviar(long a,long b,String monto) { return enviar(a,b,monto,clave()); }
    List<Map<String,Object>> historia(Map<String,?> filtros) {
        return given().queryParams(filtros).get("/api/transferencias").then().statusCode(200).extract().jsonPath().getList("");
    }
    List<Map<String,Object>> historia(long origen) { return historia(Map.of("cuentaOrigenId",origen)); }
    io.restassured.response.Response resumen(long id) {return given().get("/api/cuentas/"+id+"/resumen");}
    void saldoEs(long id,String valor) {
        var response=resumen(id);response.then().statusCode(200);
        assertEquals(0,new BigDecimal(valor).compareTo(new BigDecimal(response.jsonPath().getString("saldoActual"))));
    }
    @Test void historialSinFiltrosOrdenaFechaEIdentificador() {
        long a=cuenta("100"),b=cuenta("0");
        long primero=enviar(a,b,"1").then().statusCode(201).extract().jsonPath().getLong("id");
        long segundo=enviar(a,b,"1").then().statusCode(201).extract().jsonPath().getLong("id");
        hora.set(hora.get().plusSeconds(1));
        long tercero=enviar(a,b,"1").then().statusCode(201).extract().jsonPath().getLong("id");
        var rows=historia(Map.of());assertFalse(rows.isEmpty());
        for(int i=1;i<rows.size();i++) {
            var x=rows.get(i-1);var y=rows.get(i);
            int cmp=OffsetDateTime.parse((String)x.get("fecha")).toInstant().compareTo(OffsetDateTime.parse((String)y.get("fecha")).toInstant());
            assertTrue(cmp>0 || (cmp==0 && Long.parseLong((String)x.get("id"))>Long.parseLong((String)y.get("id"))));
        }
        assertEquals(List.of(tercero,segundo,primero),historia(a).stream().map(x->Long.parseLong((String)x.get("id"))).toList());
    }
    @Test void filtrosOrigenDestinoYCombinados() {
        long a=cuenta("100"),b=cuenta("100"),c=cuenta("0");
        enviar(a,b,"1").then().statusCode(201);enviar(a,c,"2").then().statusCode(201);enviar(b,c,"3").then().statusCode(201);
        assertEquals(2,historia(a).size());
        assertEquals(2,historia(Map.of("cuentaDestinoId",c)).size());
        assertEquals(1,historia(Map.of("cuentaOrigenId",a,"cuentaDestinoId",c,"estado","completada",
            "fechaDesde",hora.get().toString(),"fechaHasta",hora.get().toString())).size());
        assertTrue(historia(Map.of("cuentaOrigenId",Long.MAX_VALUE)).isEmpty());
    }
    @Test void filtrosFechasInclusivosConOffset() {
        long a=cuenta("100"),b=cuenta("0");enviar(a,b,"1").then().statusCode(201);
        hora.set(hora.get().plusHours(2));enviar(a,b,"1").then().statusCode(201);
        assertEquals(1,historia(Map.of("cuentaOrigenId",a,"fechaHasta","2026-10-23T12:00:00-05:00")).size());
        assertEquals(1,historia(Map.of("cuentaOrigenId",a,"fechaDesde","2026-10-23T19:00:00Z")).size());
        assertTrue(historia(Map.of("cuentaOrigenId",a,"fechaDesde","2026-10-24T00:00:00Z")).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"completada","en_revision","rechazada"})
    void filtrosEstado(String estado) {
        long a=cuenta("20000"),b=cuenta("0");
        enviar(a,b,"1").then().statusCode(201);enviar(a,b,"6000").then().statusCode(201);enviar(a,b,"5000").then().statusCode(422);
        var rows=historia(Map.of("cuentaOrigenId",a,"estado",estado));assertEquals(1,rows.size());assertEquals(estado,rows.get(0).get("estado"));
    }
    @ParameterizedTest @ValueSource(strings={"fechaDesde=ayer","fechaHasta=2026-10-23","estado=foo","cuentaOrigenId=-1","cuentaDestinoId=0"})
    void filtrosInvalidos(String filtro) {
        given().get("/api/transferencias?"+filtro).then().statusCode(400).body("codigo",equalTo("FILTROS_INVALIDOS"));
    }
    @Test void intervaloInvertido() {
        given().queryParam("fechaDesde","2026-10-24T00:00:00Z").queryParam("fechaHasta","2026-10-23T00:00:00Z")
            .get("/api/transferencias").then().statusCode(400);
    }
    @Test void saldoInsuficienteRechazoPersistidoEIdempotenteTrasIngreso() {
        long a=cuenta("0"),b=cuenta("0"),donante=cuenta("100");String key=clave();
        var first=enviar(a,b,"10",key);first.then().statusCode(422).body("codigo",equalTo("SALDO_INSUFICIENTE"));
        var rows=historia(a);assertEquals(1,rows.size());assertEquals("rechazada",rows.get(0).get("estado"));
        assertEquals(first.jsonPath().getMap(""),rows.get(0).get("rechazo"));saldoEs(a,"0");saldoEs(b,"0");
        enviar(donante,a,"100").then().statusCode(201);
        assertEquals(first.jsonPath().getMap(""),enviar(a,b,"10",key).then().statusCode(422).extract().jsonPath().getMap(""));
        enviar(a,b,"11",key).then().statusCode(409);assertEquals(1,historia(a).size());saldoEs(a,"100");
        enviar(a,b,"10").then().statusCode(201);saldoEs(a,"90");
    }
    @Test void cuentaInactivaSeRegistraSinMovimiento() {
        long a=cuenta("100","inactivo"),b=cuenta("0");
        enviar(a,b,"10").then().statusCode(422).body("codigo",equalTo("CUENTA_INACTIVA"));
        assertEquals("rechazada",historia(a).get(0).get("estado"));saldoEs(a,"100");saldoEs(b,"0");
        resumen(a).then().body("totalTransferidoHoy",equalTo("0"),"cantidadTransferenciasHoy",equalTo(0));
    }
    @Test void limiteRechazadoPreservaDisponibleInclusoAlCambiarDia() {
        long a=cuenta("30000"),b=cuenta("0");String key=clave();
        enviar(a,b,"8000").then().statusCode(201);
        var rejected=enviar(a,b,"2500",key);rejected.then().statusCode(422);
        assertEquals(rejected.jsonPath().getMap(""),historia(a).get(0).get("rechazo"));
        enviar(a,b,"2000").then().statusCode(201);saldoEs(a,"20000");
        hora.set(hora.get().plusDays(1));
        assertEquals(rejected.jsonPath().getMap(""),enviar(a,b,"2500",key).then().statusCode(422).extract().jsonPath().getMap(""));
        resumen(a).then().body("totalTransferidoHoy",equalTo("0"),"cantidadTransferenciasHoy",equalTo(0),"cantidadTransferenciasSospechosas",equalTo(0));
        assertEquals(3,historia(a).size());
    }
    @Test void rechazosNoConsumenFrecuenciaNiLimite() {
        long a=cuenta("0"),b=cuenta("0"),donante=cuenta("10000");
        for(int i=0;i<6;i++) enviar(a,b,"1").then().statusCode(422);
        enviar(donante,a,"10000").then().statusCode(201);
        enviar(a,b,"1").then().statusCode(201).body("estado",equalTo("completada"));
        enviar(a,b,"9999").then().statusCode(201);saldoEs(a,"0");saldoEs(b,"10000");
        resumen(a).then().body("cantidadTransferenciasHoy",equalTo(2),"cantidadTransferenciasSospechosas",equalTo(1));
    }
    @Test void resumenCuentaMovimientosDelDiaSoloSalidasEjecutadas() {
        long a=cuenta("30000"),b=cuenta("0");
        enviar(a,b,"100").then().statusCode(201);enviar(a,b,"6000").then().statusCode(201);enviar(a,b,"4000").then().statusCode(422);
        enviar(b,a,"50").then().statusCode(201);
        resumen(a).then().statusCode(200).body("saldoActual",equalTo("23950.00"),"totalTransferidoHoy",equalTo("6100.00"),
            "cantidadTransferenciasHoy",equalTo(2),"cantidadTransferenciasSospechosas",equalTo(1));
    }
    @Test void resumenCambioDiaGuayaquil() {
        long a=cuenta("30000"),b=cuenta("0");hora.set(OffsetDateTime.parse("2026-10-24T04:59:59.999999Z"));
        enviar(a,b,"6000").then().statusCode(201);
        resumen(a).then().body("cantidadTransferenciasSospechosas",equalTo(1));
        hora.set(OffsetDateTime.parse("2026-10-24T05:00:00Z"));
        resumen(a).then().body("totalTransferidoHoy",equalTo("0"),"cantidadTransferenciasHoy",equalTo(0),"cantidadTransferenciasSospechosas",equalTo(0));
        saldoEs(a,"24000");assertEquals(1,historia(a).size());
        enviar(a,b,"1").then().statusCode(201);resumen(a).then().body("cantidadTransferenciasHoy",equalTo(1));
    }
    @Test void cuentaInexistenteResumen404() {
        resumen(Long.MAX_VALUE).then().statusCode(404).body("codigo",equalTo("CUENTA_NO_EXISTE"));
    }
    @Test void solicitudesInvalidasOReferenciasInexistentesNoCreanRechazo() {
        long a=cuenta("100"),b=cuenta("0");
        enviar(a,b,"0").then().statusCode(400);enviar(a,Long.MAX_VALUE,"1").then().statusCode(404);
        enviar(a,b,"1","clave/invalida").then().statusCode(400);assertTrue(historia(a).isEmpty());
    }
    @Test void falloTecnicoAlPersistirRechazoRevierteYNoConfirma422() {
        long a=cuenta("0"),b=cuenta("0");String key=clave();
        doAnswer(call->{call.callRealMethod();throw new IllegalStateException("Fallo controlado tras INSERT de rechazo");})
            .when(repository).registrar(org.mockito.ArgumentMatchers.any(Transferencia.class));
        enviar(a,b,"10",key).then().statusCode(500);reset(repository);
        assertTrue(historia(a).isEmpty());saldoEs(a,"0");saldoEs(b,"0");
        enviar(a,b,"10",key).then().statusCode(422);assertEquals(1,historia(a).size());
    }
    @Test void rechazosConcurrentesMismaClaveSoloUnRegistro() throws Exception {
        long a=cuenta("0"),b=cuenta("0");String key=clave();var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<io.restassured.response.Response> task=()->{barrier.await(5,TimeUnit.SECONDS);return enviar(a,b,"1",key);};
            var x=pool.submit(task);var y=pool.submit(task);var first=x.get(25,TimeUnit.SECONDS);var second=y.get(25,TimeUnit.SECONDS);
            first.then().statusCode(422);second.then().statusCode(422);assertEquals(first.jsonPath().getMap(""),second.jsonPath().getMap(""));
            assertEquals(1,historia(a).size());saldoEs(a,"0");
        } finally {pool.shutdownNow();}
    }
}
