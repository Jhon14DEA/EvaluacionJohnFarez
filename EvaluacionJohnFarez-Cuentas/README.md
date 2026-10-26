# EvaluacionJohnFarez-BACK

Backend único de la aplicación: API REST de cuentas y transferencias, dueño de SQL Server y de toda la lógica financiera. Java 25 (bytecode `release 25`), Quarkus 3.40.1, Maven Wrapper 3.9.12, Panache, MapStruct 1.6.3, Jakarta Validation y Flyway. JUnit 5 (JUnit Platform) desde el BOM de Quarkus. [Guía integral](../README.md).

## SQL Server

Requisitos: Docker con Compose, puerto local 14333 disponible y memoria para el contenedor limitado a 4 GiB, además de JVM y Angular. Imagen SQL Server 2022 CU20 Developer fijada en compose.yaml. Revisar sin detener otros contenedores:

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object FreePhysicalMemory,TotalVisibleMemorySize
Get-NetTCPConnection -LocalPort 14333 -ErrorAction SilentlyContinue
docker compose ps
```

Si el puerto pertenece a esta instancia ya instalada, reutilizarla. En instalación nueva:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
```

Editar el archivo local con contraseñas propias, distintas, de al menos 16 caracteres, incluyendo mayúsculas, minúsculas y dígitos. El bootstrap usa sustitución sqlcmd: usar solo letras, dígitos y !. No compartir el archivo ni cambiar credenciales de un volumen existente. La plantilla no contiene secretos. Quarkus carga la configuración local desde el directorio de trabajo.

| Variable | Uso / valor no secreto |
|---|---|
| MSSQL_SA_PASSWORD | Contraseña local de administrador SQL para Compose/bootstrap |
| DB_PASSWORD | Contraseña local de evaluacion_app para JDBC/bootstrap |
| DB_USERNAME | evaluacion_app por defecto |
| SQLSERVER_PORT | 14333; publicación solo en 127.0.0.1 |
| DB_NAME | evaluacion_john_farez por defecto en dev/JAR |
| TEST_DB_NAME | Obligatoria al probar; base aislada ya creada |

Desde esta carpeta, en instalación nueva:

```powershell
docker compose up -d --wait sqlserver
docker compose exec -T sqlserver sh /bootstrap.sh
```

docker/bootstrap.sql crea si faltan evaluacion_john_farez y evaluacion_john_farez_test, login evaluacion_app y usuarios; concede db_owner en esas bases para Flyway. No elimina registros/tablas. Volumen persistente propio sqlserver-data; no ejecutar down -v ni reset. No toca fut-postgres. El bootstrap no sincroniza contraseñas de logins existentes: conservar las originales.

## Migraciones, compilación y ejecución

Flyway aplica V1 (cuenta), V2 (transferencia) y V3 (detalle de rechazo) al iniciar aplicación/pruebas. Versionadas en src/main/resources/db/migration; no editar versiones aplicadas. Hibernate valida, no genera esquema. Flyway clean está deshabilitado. UTC, DECIMAL(19,2) y BIGINT en SQL; cadenas exactas en JSON.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot' # JDK 25; ajustar ruta; solo esta terminal
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
$env:TEST_DB_NAME='evaluacion_john_farez_test'
.\mvnw.cmd -B -ntp verify
java -jar target/quarkus-app/quarkus-run.jar
```

Puerto 8080. Desarrollo: `.\mvnw.cmd quarkus:dev`. Empaquetar sin pruebas: `.\mvnw.cmd -B -ntp package -DskipTests` (no equivale a regresión). DB_NAME selecciona la base del JAR, no TEST_DB_NAME. Para demostración aislada establecer `$env:DB_NAME='evaluacion_john_farez_test'` antes del JAR.

## API pública

| Método y ruta | Resultado |
|---|---|
| POST /api/cuentas | 201 CuentaResponse; 400 validación/JSON, 409 duplicado, 500 técnico |
| GET /api/cuentas | 200 CuentaResponse[] (orden ID ascendente) o PaginaResponse (con page/size/q/sort/order); 400 PAGINACION_INVALIDA/ORDEN_INVALIDO, 500 técnico |
| GET /api/cuentas/{id}/resumen | 200 ResumenCuentaResponse; 404 inexistente, errores de conversión de parámetro y 500 técnico |

Solicitud:

```json
{"numeroCuenta":"0012345678","titular":"Titular de ejemplo","tipo":"ahorro","saldoInicial":"100.00","estado":"activo"}
```

Respuesta ilustrativa 201:

```json
{"id":"1","numeroCuenta":"0012345678","titular":"Titular de ejemplo","tipo":"ahorro","saldo":"100.00","estado":"activo"}
```

SQL asigna IDs reales; no asumir 1. Número único de 10 dígitos, titular hasta 150 caracteres, tipo ahorro/corriente, estado activo/inactivo, saldo no negativo y máximo dos decimales. Resumen: cuentaId, saldoActual y totalTransferidoHoy como cadenas; cantidadTransferenciasHoy/cantidadTransferenciasSospechosas como números. Contadores de salidas ejecutadas hoy en America/Guayaquil.

## API de transferencias

| Método y ruta | Resultado |
|---|---|
| POST /api/transferencias | 201 TransferenciaResponse (completada o en_revision); 400 validación, 404 ORIGEN_NO_EXISTE/DESTINO_NO_EXISTE, 409 conflicto de idempotencia, 422 regla de negocio, 500 técnico, 503 OPERACION_OCUPADA |
| GET /api/transferencias | 200 HistorialTransferenciaResponse[] o PaginaResponse (con page/size); 400 FILTROS_INVALIDOS, 500 técnico |

POST recibe CrearTransferenciaRequest e Idempotency-Key opcional; devuelve TransferenciaResponse y clave en cabecera. Misma clave/datos reproduce resultado o rechazo persistido sin nuevos movimientos. Fallos técnicos hacen rollback; conservar clave ante incertidumbre. Débito, crédito y registro ocurren bajo una sola transacción local `@Transactional`.

GET acepta cuentaOrigenId, cuentaDestinoId, fechaDesde, fechaHasta y estado, opcionales/combinables. Fechas inclusivas ISO-8601 con zona; estado completada/rechazada/en_revision. Orden fecha DESC e ID DESC. HistorialTransferenciaResponse[] con rechazo opcional. Paginación opcional: sin page/size devuelve el arreglo plano; con page/size devuelve PaginaResponse con metadatos. 400 FILTROS_INVALIDOS; parámetro numérico no convertible puede producir HTTP_404 del framework.

## Swagger y pruebas

UI: http://localhost:8080/q/swagger-ui/ (dev y JAR). JSON: http://localhost:8080/q/openapi?format=json. Etiquetas Cuentas y Transferencias.

```powershell
$env:TEST_DB_NAME='evaluacion_john_farez_test'
.\mvnw.cmd -B -ntp verify
Invoke-RestMethod 'http://localhost:8080/q/openapi?format=json'
```

SQL real, registros nuevos, sin borrados. TEST_DB_NAME debe existir con usuario/permisos; bootstrap provee la base test en instalación nueva. En el entorno auditado se reutiliza evaluacion_john_farez_audit91_20261023. Nunca elegir la principal. Reportes en target/surefire-reports; cubren rollback, concurrencia, idempotencia, límites, historial/resumen y precisión. Sin H2 ni Dev Services.

## CORS

Habilitado para que el frontend Angular consuma la API directamente cuando no usa el proxy del dev server. Configurado en `application.properties`: `quarkus.http.cors.enabled=true`, métodos `GET,POST,OPTIONS`, y expone las cabeceras `Idempotency-Key` y `X-Total-Count` que el cliente necesita leer. Los orígenes permitidos son configurables con la variable `CORS_ORIGINS` (por defecto `http://localhost:4200,http://127.0.0.1:4200`).

## Logs de operaciones críticas

`TransferenciaOperacion` registra con `org.jboss.logging.Logger` los hitos del flujo financiero sin exponer datos sensibles (solo IDs, monto, estado y código): solicitud recibida, reutilización por idempotencia, rechazo por regla de negocio, marcado como sospechosa (detección de fraude) y ejecución confirmada. `ApiExceptionMapper` registra los errores técnicos no controlados.

## Empaquetado Docker

`Dockerfile` (multi-stage) compila el JAR con la imagen oficial de Maven + Temurin 25 y lo ejecuta sobre un JRE ligero; expone el puerto 8080. Para levantar el entorno completo (base + backend + frontend) con un solo comando, ver `compose.full.yaml` en la raíz del repositorio y la [guía integral](../README.md). El `compose.yaml` de esta carpeta sigue sirviendo para levantar solo SQL Server.


### Alternativas con artefactos compilados en el host

El `Dockerfile` principal compila desde `src` y mantiene `target` fuera de su contexto.
Los Dockerfiles alternativos tienen archivos `.dockerignore` propios: solo incluyen
los artefactos necesarios de `target` y excluyen `.env` y el resto del proyecto.
Todos los runtimes JVM usan Java 25.

```powershell
# Fast JAR (formato habitual)
.\mvnw.cmd -B -ntp package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm -t evaluacion-cuentas-java25 .

# Legacy JAR (solo si se necesita ese formato)
.\mvnw.cmd -B -ntp package -DskipTests '-Dquarkus.package.jar.type=legacy-jar'
docker build -f src/main/docker/Dockerfile.legacy-jar -t evaluacion-cuentas-legacy-java25 .
```

Estos comandos omiten la ejecuci?n de pruebas. La regresi?n requiere una base SQL aislada.
Los Dockerfiles `native` corresponden a ejecutables nativos y no son una alternativa JVM;
no forman parte de la validaci?n de ejecuci?n con JDK 25.
