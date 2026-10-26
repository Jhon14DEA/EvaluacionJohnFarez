# FASE 7 — CA-06

Dashboard terminado, exclusivamente en Frontend. El PDF oficial exige los cinco campos, orden por saldo en ambos sentidos y consumo real de GET /api/cuentas; se verificaron estos requisitos.

## Dependencias

Instaladas con versiones exactas y fijadas en package-lock.json:

| Paquete | Versión |
|---|---|
| primeng | 21.1.10 |
| @primeuix/themes | 2.0.3 |
| primeicons | 7.0.0 |
| @angular/cdk | 21.2.14 |
| @playwright/test, solo desarrollo | 1.64.1 |

Angular core resuelto: 21.2.25; Node 24.19.0; npm 11.17.0. Peers compatibles, sin force ni legacy-peer-deps. Auditoría de instalación: cero vulnerabilidades.

Configuración contrastada con [instalación v21](https://v21.primeng.org/installation), [tema](https://v21.primeng.org/theming), [tabla](https://v21.primeng.org/table), [migración oficial v21](https://primeng.dev/migration/v21) y APIs del paquete instalado. Se usan providePrimeNG, definePreset(Aura) y animaciones CSS; no se añade Angular animations.

## Componentes y servicios

- core/models/cuenta.ts: contrato tipado; número de cuenta como string para conservar ceros.
- core/services/cuentas.service.ts: HttpClient y Observable<Cuenta[]>.
- core/theme.ts: Aura personalizado, paleta acordada y modo claro.
- features/cuentas/cuentas-dashboard: carga, error, actualización, búsqueda Reactive Forms e indicadores del conjunto filtrado.
- features/cuentas/cuentas-table: PrimeNG Table, cinco columnas, saldo ascendente inicial, alternancia descendente/ascendente y paginación desde once cuentas.
- App: marco, enlace accesible y ruta con carga diferida. No se agregaron componentes shared sin reutilización necesaria.

Importes USD con dos decimales y locale es-EC. Estados Activo/Inactivo con texto. Búsqueda local por número o titular. Carga, vacío real, búsqueda sin coincidencias y error tienen presentaciones diferentes. No hay mocks en la aplicación; los datos sintéticos existen solo en pruebas.

## Interfaz verificable

Cabecera blanca, verde #194E2E, títulos #0C2139 y fondo #F6F8F1. Tres indicadores compactos y tabla central. La página móvil no desborda: la tabla tiene desplazamiento horizontal interno con indicación visible. Se conservan las siete variables de paleta; textos secundarios pequeños usan un gris más oscuro para legibilidad.

- [Captura escritorio 1440×1000](tmp/dashboard-desktop.png).
- [Captura móvil 390×844](tmp/dashboard-mobile.png).

Las capturas muestran la única cuenta existente en la base principal; no se crearon datos para ilustrarlas.

## Validación

`npm.cmd run build`: aprobado, bundle inicial 478.09 kB dentro del presupuesto; dashboard diferido 603.98 kB, transferencia estimada 104.38 kB.

`npm.cmd test -- --watch=false`: **11 pruebas aprobadas en 2 archivos**. Comprueban HttpClient GET, campos, orden ascendente y descendente, estados, USD, carga, vacío, error/reintento, actualización mediante botón, búsqueda y marco accesible.

`python -B scripts/verify-ca06.py`: verificación real en Edge aprobada. Angular → proxy → Quarkus → SQL Server. HTTP 200, una cuenta real, coincidencia con respuesta API, estado de ordenamiento, otro GET al actualizar, cero errores de página y sin desbordamiento móvil. El orden entre varias filas se demuestra con tres saldos distintos en las pruebas de componentes, ya que la base principal contiene solo una cuenta.

Evidencias: tmp/integration-ca06.json, capturas y tmp/process-4200.log / process-8080.log. Los procesos propios se cierran al finalizar. Las 125 pruebas backend anteriores no se reejecutaron: esta fase no modifica backend y agrega 11 pruebas frontend más la comprobación real de navegador.

## CORS y ejecución

Con Cuentas en http://127.0.0.1:8080 y SQL activo, ejecutar `npm.cmd start` y abrir http://localhost:4200.

El endpoint directo devuelve 200 pero no Access-Control-Allow-Origin: CORS permanece deshabilitado. Angular usa `/api` en el mismo origen; proxy.conf.json reenvía `/api/**` a Cuentas. No se modificó backend ni se necesitó autorización para cambiar CORS.

environment.ts y environment.development.ts definen cuentasApiUrl. Producción utiliza `/api` y necesita un reverse proxy del servidor de alojamiento; el proxy de Angular solo funciona en desarrollo. No se desplegó infraestructura.

El script de verificación requiere Edge existente, JDK 17 en la ruta local, JAR de Cuentas compilado, su .env y SQL activo. Comprueba puertos libres 8080/4200, inicia procesos propios, desactiva migraciones y solo hace consultas. No instala navegador ni escribe en proyectos backend.

## Archivos

Creados:

- proxy.conf.json.
- src/environments/environment.ts y environment.development.ts.
- src/app/core/models/cuenta.ts, core/services/cuentas.service.ts y core/theme.ts.
- src/app/features/cuentas/cuentas-dashboard.ts, .html, .css y .spec.ts.
- src/app/features/cuentas/cuentas-table.ts, .html y .css.
- scripts/verify-ca06.py y verify-ca06.mjs.
- FASE_7_CA06.md.

Modificados: package.json, package-lock.json, angular.json, src/styles.css, src/index.html y src/app/app.ts, app.html, app.css, app.config.ts, app.routes.ts y app.spec.ts. Artefactos locales en node_modules, dist, .angular y tmp, ya excluidos por .gitignore.

## Problemas y alcance

El primer ensayo de navegador no encontraba Actualizar por su nombre accesible exacto: el icono afectaba el nombre calculado. Se añadieron ariaLabel explícitos a Actualizar/Reintentar y se repitieron build, pruebas y navegador con éxito. La prueba de actualización pulsa ahora el botón real.

npm avisó de scripts transitivos pendientes de aprobación; no se habilitaron globalmente y build/pruebas funcionaron. No se instaló Angular CLI globalmente.

Sin cambios a reglas financieras, migraciones, datos SQL, Transferencias, fut-postgres, Git ni infraestructura. CA-07 queda pendiente de autorización.
