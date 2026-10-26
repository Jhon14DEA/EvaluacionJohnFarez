package ec.johnfarez.evaluacion.cuentas;
import ec.johnfarez.evaluacion.cuentas.dto.*;
import ec.johnfarez.evaluacion.cuentas.service.*;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class ReglasTransferenciaSqlTest {
    @Inject CuentaService cuentas;
    @Inject AgroalDataSource db;
    @InjectMock RelojFinanciero reloj;
    final AtomicReference<OffsetDateTime> hora=new AtomicReference<>();
    static final SecureRandom RANDOM=new SecureRandom();
    @BeforeEach void iniciar() {
        hora.set(OffsetDateTime.parse("2026-10-23T17:00:00Z"));
        when(reloj.ahora()).thenAnswer(inv->hora.get());
    }
    long cuenta(String saldo) {
        return cuentas.crear(new CrearCuentaRequest("3"+String.format("%09d",RANDOM.nextInt(1_000_000_000)),
            "Prueba CA03 CA04","ahorro",new BigDecimal(saldo),"activo")).id();
    }
    String clave() { return UUID.randomUUID().toString(); }
    io.restassured.response.Response enviar(long a,long b,String monto,String clave) {
        return given().contentType("application/json").header("Idempotency-Key",clave)
            .body(Map.of("cuentaOrigenId",a,"cuentaDestinoId",b,"monto",new BigDecimal(monto)))
            .post("/api/transferencias");
    }
    io.restassured.response.Response enviar(long a,long b,String monto) {return enviar(a,b,monto,clave());}
    BigDecimal saldo(long id) throws Exception {
        try(var c=db.getConnection();var s=c.prepareStatement("SELECT saldo FROM cuenta WHERE id=?")) {
            s.setLong(1,id);try(var rs=s.executeQuery()){assertTrue(rs.next());return rs.getBigDecimal(1);}
        }
    }
    long registrosEjecutados(long origen) throws Exception {
        try(var c=db.getConnection();var s=c.prepareStatement("SELECT COUNT(*) FROM transferencia WHERE cuenta_origen_id=? AND estado IN ('completada','en_revision')")) {
            s.setLong(1,origen);try(var rs=s.executeQuery()){rs.next();return rs.getLong(1);}
        }
    }
    void saldoEs(long id,String valor) throws Exception {assertEquals(0,new BigDecimal(valor).compareTo(saldo(id)));}
    void limite(io.restassured.response.Response r,String disponible) {
        r.then().statusCode(422).body("codigo",equalTo("LIMITE_DIARIO_EXCEDIDO"),"erroresCampos",empty());
        assertEquals(0,new BigDecimal(disponible).compareTo(new BigDecimal(r.jsonPath().getString("montoRestanteDisponible"))));
    }
    @ParameterizedTest @CsvSource({"5000.00,completada", "5000.01,en_revision"})
    void umbralMontoEjecutaMovimiento(String monto,String estado) throws Exception {
        long a=cuenta("20000"),b=cuenta("0");
        enviar(a,b,monto).then().statusCode(201).body("estado",equalTo(estado));
        assertEquals(0,new BigDecimal(monto).compareTo(saldo(b)));
        assertEquals(0,new BigDecimal("20000").compareTo(saldo(a).add(saldo(b))));
        assertEquals(1,registrosEjecutados(a));
    }
    @Test void quintaNormalSextaYSeptimaEnRevision() throws Exception {
        long a=cuenta("100"),b=cuenta("0");
        for(int i=1;i<=7;i++) enviar(a,b,"1").then().statusCode(201).body("estado",equalTo(i<=5?"completada":"en_revision"));
        saldoEs(a,"93");saldoEs(b,"7");assertEquals(7,registrosEjecutados(a));
    }
    @ParameterizedTest @CsvSource({"3599999999,en_revision", "3600000000,completada", "3600000001,completada"})
    void ventanaMovilLimiteAbierto(long microsegundos,String esperado) {
        long a=cuenta("100"),b=cuenta("0");
        for(int i=0;i<5;i++) enviar(a,b,"1").then().statusCode(201);
        hora.set(hora.get().plusNanos(microsegundos*1000));
        enviar(a,b,"1").then().statusCode(201).body("estado",equalTo(esperado));
    }
    @Test void limiteExactoYRechazoSinConsumir() throws Exception {
        long a=cuenta("20000"),b=cuenta("0");
        enviar(a,b,"9999.99").then().statusCode(201).body("estado",equalTo("en_revision"));
        enviar(a,b,"0.01").then().statusCode(201).body("estado",equalTo("completada"));
        limite(enviar(a,b,"0.01"),"0");
        saldoEs(a,"10000");saldoEs(b,"10000");assertEquals(2,registrosEjecutados(a));
    }
    @Test void revisionConsumeLimiteRechazoNoYReintentoNoReevalua() throws Exception {
        long a=cuenta("30000"),b=cuenta("0");String key=clave();
        var original=enviar(a,b,"8000",key);original.then().statusCode(201).body("estado",equalTo("en_revision"));
        limite(enviar(a,b,"2500"),"2000");saldoEs(a,"22000");saldoEs(b,"8000");assertEquals(1,registrosEjecutados(a));
        enviar(a,b,"2000").then().statusCode(201);
        clearInvocations(reloj);
        var replay=enviar(a,b,"8000",key);replay.then().statusCode(201);
        assertEquals(original.jsonPath().getMap(""),replay.jsonPath().getMap(""));
        verify(reloj,never()).ahora();
        limite(enviar(a,b,"0.01"),"0");saldoEs(a,"20000");assertEquals(2,registrosEjecutados(a));
    }
    @Test void cambioDiaGuayaquilConservaHistoriaYReplay() throws Exception {
        long a=cuenta("30000"),b=cuenta("0");String key=clave();
        hora.set(OffsetDateTime.parse("2026-10-24T04:59:59.999999Z"));
        var anterior=enviar(a,b,"10000",key);anterior.then().statusCode(201);
        hora.set(OffsetDateTime.parse("2026-10-24T05:00:00Z"));
        enviar(a,b,"10000").then().statusCode(201);
        assertEquals(anterior.jsonPath().getMap(""),enviar(a,b,"10000",key).jsonPath().getMap(""));
        limite(enviar(a,b,"0.01"),"0");saldoEs(a,"10000");saldoEs(b,"20000");assertEquals(2,registrosEjecutados(a));
    }
    @Test void medianocheUTCNoReiniciaDiaDeNegocio() {
        long a=cuenta("30000"),b=cuenta("0");
        hora.set(OffsetDateTime.parse("2026-10-23T23:59:59Z"));enviar(a,b,"10000").then().statusCode(201);
        hora.set(OffsetDateTime.parse("2026-10-24T00:00:00Z"));limite(enviar(a,b,"1"),"0");
    }
    @Test void rechazadasHistoricasNoCuentanEnMontoNiFrecuencia() throws Exception {
        long a=cuenta("20000"),b=cuenta("0");
        // Solo datos de prueba: producción aún no persiste rechazos de negocio (CA-05 diferido).
        try(var c=db.getConnection();var s=c.prepareStatement("INSERT INTO transferencia(cuenta_origen_id,cuenta_destino_id,monto,fecha,estado,clave_idempotencia) VALUES(?,?,?,?,?,?)")) {
            for(int i=0;i<6;i++) {
                s.setLong(1,a);s.setLong(2,b);s.setBigDecimal(3,new BigDecimal("9000"));s.setObject(4,hora.get());
                s.setString(5,"rechazada");s.setString(6,clave());s.executeUpdate();
            }
        }
        enviar(a,b,"1").then().statusCode(201).body("estado",equalTo("completada"));
        enviar(a,b,"9999").then().statusCode(201);saldoEs(a,"10000");saldoEs(b,"10000");
    }
    List<io.restassured.response.Response> concurrentes(long a,long b,long c,String monto,String k1,String k2) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var f1=pool.submit(()->{ready.countDown();start.await();return enviar(a,b,monto,k1);});
            var f2=pool.submit(()->{ready.countDown();start.await();return enviar(a,c,monto,k2);});
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            return List.of(f1.get(25,TimeUnit.SECONDS),f2.get(25,TimeUnit.SECONDS));
        } finally {start.countDown();pool.shutdownNow();}
    }
    @Test void concurrenciaSerializaAcumuladoDiario() throws Exception {
        long a=cuenta("30000"),b=cuenta("0"),c=cuenta("0");enviar(a,b,"8000").then().statusCode(201);
        var respuestas=concurrentes(a,b,c,"1500",clave(),clave());
        assertEquals(List.of(201,422),respuestas.stream().map(r->r.statusCode()).sorted().toList());
        limite(respuestas.stream().filter(r->r.statusCode()==422).findFirst().orElseThrow(),"500");
        saldoEs(a,"20500");assertEquals(0,new BigDecimal("9500").compareTo(saldo(b).add(saldo(c))));assertEquals(2,registrosEjecutados(a));
    }
    @Test void concurrenciaSerializaFrecuencia() throws Exception {
        long a=cuenta("100"),b=cuenta("0"),c=cuenta("0");
        for(int i=0;i<4;i++) enviar(a,b,"1").then().statusCode(201);
        var respuestas=concurrentes(a,b,c,"1",clave(),clave());respuestas.forEach(r->r.then().statusCode(201));
        assertEquals(List.of("completada","en_revision"),respuestas.stream().map(r->r.jsonPath().getString("estado")).sorted().toList());
        saldoEs(a,"94");assertEquals(6,registrosEjecutados(a));
    }
    @Test void concurrenciaIdempotenteAlAgotarLimite() throws Exception {
        long a=cuenta("30000"),b=cuenta("0");enviar(a,b,"9000").then().statusCode(201);String key=clave();
        var respuestas=concurrentes(a,b,b,"1000",key,key);respuestas.forEach(r->r.then().statusCode(201));
        assertEquals(respuestas.get(0).jsonPath().getMap(""),respuestas.get(1).jsonPath().getMap(""));
        saldoEs(a,"20000");saldoEs(b,"10000");assertEquals(2,registrosEjecutados(a));
    }
    @Test void ingresosNoConsumenLimiteNiFrecuenciaDeDestino() {
        long a=cuenta("20000"),b=cuenta("0");
        for(int i=0;i<5;i++) enviar(a,b,"1000").then().statusCode(201);
        enviar(b,a,"1").then().statusCode(201).body("estado",equalTo("completada"));
    }
}
