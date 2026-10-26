# EvaluacionJohnFarez-Frontend

Angular 21, PrimeNG 21.1.10, Aura (@primeuix/themes 2.0.3), PrimeIcons 7 y TypeScript. Standalone, HttpClient tipado y Reactive Forms. [Guía integral](../README.md).

## Instalación y desarrollo

Entorno: Node.js 24.19.0 / npm 11.17.0. Sin Angular CLI global; npm usa CLI local y versiones resueltas en package-lock.json.

```powershell
node --version
npm.cmd --version
npm.cmd ci
npm.cmd start
```

Abrir http://localhost:4200. Iniciar antes SQL Server y el backend (Cuentas) en :8080. Datos reales; sin backend se muestra error y opción de recarga.

angular.json aplica proxy.conf.json al ejecutar ng serve:
- /api → http://127.0.0.1:8080 (cuentas y transferencias en el mismo backend).

Environments desarrollo/producción usan /api relativo. El navegador habla con mismo origen; no necesita CORS directo en este flujo. Si cambian puertos, adaptar proxy. No introducir secretos en environments: el bundle es público.

## Build y pruebas

```powershell
npm.cmd run build
npm.cmd test -- --watch=false
```

Build de producción por defecto: dist/evaluacion-john-farez-frontend/browser. El servidor estático requiere fallback a index.html y reverse proxy /api hacia el backend. proxy.conf.json solo actúa en desarrollo.

## Docker

`Dockerfile` (multi-stage) instala dependencias con `npm ci`, genera el bundle de producción y lo sirve con nginx. `nginx.conf` resuelve el fallback a index.html (SPA) y hace de reverse proxy de `/api` hacia el servicio `backend:8080`, de modo que el navegador habla con un mismo origen. Para levantar el entorno completo (base + backend + frontend) con un solo comando, ver `compose.full.yaml` en la raíz y la [guía integral](../README.md).

Vitest/HttpTestingController verifican dashboard, formulario, precisión, errores y recuperación. Para pruebas reales: Python 3, Microsoft Edge instalado (Playwright ya declarado), JDK 25, el JAR del backend compilado y base test migrada:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot' # ajustar a su JDK 25
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:TEST_DB_NAME='evaluacion_john_farez_test'
python -B scripts/verify-ca06.py
python -B scripts/verify-ca07.py
python -B scripts/verify-ca10.py
```

Los tres scripts validan `JAVA_HOME` (JDK 25 completo) y `TEST_DB_NAME` antes de arrancar; rechazan la base principal y las bases de sistema. No descargan ni eligen otro JDK automáticamente.

Arrancan y cierran procesos propios. CA06 usa 8080/4200; CA07 8080/4200; CA10 8080 y Edge headless para Swagger, sin Angular dev server. Puertos libres, sin detener servicios ajenos. CA06/CA10 son lectura; CA07 crea cuentas/movimientos nuevos, nunca seleccionar la principal. Evidencias/capturas en tmp. Sin descarga de navegadores Playwright si Edge ya está instalado.

## Funcionalidad y precisión

Dashboard: número, titular, tipo, saldo, estado; orden exacto, búsqueda local, actualización y carga/error/vacío. Formulario: selectores reales, requeridos, origen distinto, monto positivo hasta dos decimales. InputText con inputmode decimal evita pérdida de precisión del modelo number; conserva PrimeNG/Aura. No duplica reglas financieras.

Importes e IDs JSON son cadenas. USD se formatea sin number; BigInt compara centavos. Números de cuenta conservan ceros. POST envía IDs, no números de cuenta. Completada/en_revision proceden del backend; en_revision ya ejecutó el movimiento.
