# FASE 8 — CA-07 completado

Fecha: 2026-10-23. Alcance exclusivo: formulario de transferencias. Contrastado con CA-07 del PDF oficial: selectores, monto, validaciones del cliente, POST real y mensajes de respuesta. No se modificaron proyectos backend, reglas financieras ni migraciones.

## Componentes, servicios y archivos

Creados en Frontend:

- `src/app/core/models/transferencia.ts`: solicitud, respuesta, error e intento tipados.
- `src/app/core/services/transferencias.service.ts`: POST con Idempotency-Key, timeout y conservación del intento en sessionStorage.
- `src/app/features/transferencias/transferencia-form.ts`, `.html`, `.css` y `.spec.ts`: pantalla standalone con Reactive Forms, Select, InputNumber, Button y Message.
- `scripts/verify-ca07.py` y `verify-ca07.mjs`: recorrido real en Edge contra ambos microservicios y SQL Server.
- `FASE_8_CA07.md`: este informe.

Modificados:

- `src/app/app.ts`, `app.html`, `app.css`, `app.routes.ts`: navegación, ruta diferida y protección de navegación mientras hay un envío en curso.
- `src/app/app.spec.ts`: proveedores de router para el marco con navegación.
- `src/environments/environment.ts` y `environment.development.ts`: URL de Transferencias.
- `proxy.conf.json`: `/api/transferencias` hacia 8082 y el resto de `/api/**` hacia Cuentas en 8080.

No se instalaron dependencias nuevas ni se modificó package-lock.json. Se mantienen Angular 21.2.25, PrimeNG 21.1.10, Aura 2.0.3 y PrimeIcons 7.0.0. Playwright ya estaba disponible como dependencia de pruebas. Se consultaron las referencias oficiales [Select v21](https://v21.primeng.org/select) e [InputNumber v21](https://v21.primeng.org/inputnumber), y se verificó la compilación con las APIs instaladas.

Artefactos locales: build en dist, caché Angular y evidencias en tmp, excluidos por la configuración Git existente. No se inicializó Git.

## Formulario y validaciones

Ruta `/transferencias/nueva`, accesible desde la cabecera y con enlace de regreso a Cuentas. Selectores muestran número y titular; el POST envía exclusivamente cuentaOrigenId, cuentaDestinoId y monto.

Se validan campos obligatorios, cuentas diferentes, monto numérico finito positivo y precisión de dos decimales. Los errores aparecen junto a los campos; el botón no permite enviar datos inválidos ni enviar nuevamente mientras se procesa. InputNumber usa USD con dos decimales y locale es-EC. No se validan saldo, actividad, frecuencia o límite diario en Angular: esas reglas siguen siendo exclusivas del backend.

La confirmación muestra ID, monto, IDs origen/destino y estado. Distingue completada de ejecutada en revisión, permite otra transferencia y consulta nuevamente GET /api/cuentas. Los rechazos muestran el mensaje real y, para límite diario, montoRestanteDisponible. El servicio también presenta errores 404 y conflictos 409 sin convertirlos en éxito.

## Idempotencia e incertidumbre

Se crea una UUID por intento lógico. Antes del POST se guarda una copia de los datos con su clave en un servicio global y sessionStorage de la pestaña. No hay retry automático. El timeout del cliente es de 20 segundos.

Ante pérdida de conexión, timeout, HTTP 5xx o RESULTADO_DESCONOCIDO, se conserva esa copia, el formulario queda bloqueado y aparece “Reintentar misma operación”. El reintento usa el objeto almacenado, incluso si se intentase alterar programáticamente el formulario. Se bloquea la navegación interna durante el envío; una recarga de la misma pestaña recupera un intento pendiente como desconocido y exige reintento explícito.

Al confirmar se elimina el pendiente. Tras un rechazo definitivo 400/404/409/422 se muestra el error, se permite editar y un nuevo envío genera otra clave. Un RESULTADO_DESCONOCIDO conserva la clave incluso si llega con otro estado HTTP.

La recuperación está acotada a la pestaña: cerrar la pestaña o borrar su almacenamiento puede perder el contexto local. Si sessionStorage no está disponible, el intento se conserva en memoria y el mensaje indica mantener la pestaña abierta. No se creó un mecanismo de conciliación adicional fuera del alcance.

## Capturas

- [Formulario escritorio](tmp/transferencia-desktop.png).
- [Formulario móvil 390×844](tmp/transferencia-mobile.png).

Capturas con cuentas nuevas reales de la base de pruebas, no mocks. Se conserva Aura y la paleta previa. Los controles son fluidos y la comprobación móvil confirma ausencia de desbordamiento horizontal.

## Pruebas y build

`npm.cmd run build`: aprobado, bundle inicial 492.44 kB dentro del presupuesto de 500 kB.

`npm.cmd test -- --watch=false`: **32 pruebas aprobadas en 3 archivos**: 11 anteriores de CA-06/marco y 21 nuevas. Se verifican validaciones, petición HTTP, confirmación, revisión, errores 422/404/409, disponible, comunicación, timeout, RESULTADO_DESCONOCIDO, ausencia de reintentos automáticos, misma clave/mismo payload, doble envío, recuperación del pendiente y GET de actualización.

`python -B scripts/verify-ca07.py`: recorrido real aprobado en Microsoft Edge. Inicia temporalmente Cuentas y Transferencias usando sus JAR existentes y Angular dev server. Cuentas apunta a `evaluacion_john_farez_test`, con migraciones desactivadas. Crea solamente cuentas nuevas y opera sobre ellas; no elimina ni modifica registros financieros anteriores. Los intentos de diagnóstico también dejaron únicamente sus propios registros nuevos en esa base. Los procesos propios se cierran al finalizar.

Las 125 pruebas backend no se reejecutaron porque no hubo cambios backend. La integración real sí atraviesa Angular → Transferencias → Cuentas → SQL Server.

## Evidencia real de POST, errores y reintento

Ejecución final con cuentas nuevas 10426, 10427, 10428 y 10429. POST público:

```json
{"cuentaOrigenId":10426,"cuentaDestinoId":10427,"monto":10.25}
```

Clave `93c21324-6dbd-47d4-9ac2-9e5bacf58dd4`. El navegador de pruebas dejó ejecutar el POST real y descartó su respuesta después del commit, sin sustituir datos del backend. Angular mostró resultado desconocido. Tras recargar la página y reintentar explícitamente, se observó:

- Misma clave y mismo payload en ambas peticiones.
- Mismo ID de transferencia **279** y fecha original.
- Saldo origen **19989.75**, desde 20000.
- Cantidad de movimientos ejecutados **1**, sin doble débito.

Después se transfirieron 6000 reales y se mostró `en_revision`. Intentar 5000 adicionales produjo límite diario con disponible **3989.75**. También se verificaron en navegador los mensajes reales de cuenta inactiva y saldo insuficiente. La cuenta inexistente se cubre en las pruebas HTTP unitarias, ya que los selectores solo ofrecen cuentas devueltas por GET.

Resumen final del origen: saldo **13989.75**, total transferido **6010.25**, **2** movimientos ejecutados y **1** sospechoso. Los rechazos no alteraron esos totales. Se navegó de vuelta al dashboard y se encontró la cuenta mediante búsqueda. Cero errores JavaScript de página.

Evidencia detallada: `tmp/integration-ca07.json`; logs `tmp/process-8080.log`, `process-8082.log` y `process-4200.log`.

## CORS, regresión y problemas resueltos

No se cambió CORS. El navegador usa el mismo origen y el proxy de Angular dirige cada endpoint al microservicio correcto. En producción sigue siendo necesario un reverse proxy equivalente; no se desplegó infraestructura.

CA-06 conserva sus pruebas, consulta real, tabla, estados, búsqueda y navegación. Se corrigieron anchuras de controles móviles y la comprobación visual espera el cierre de las animaciones del selector antes de cambiar el viewport. Se corrigieron caracteres acentuados afectados por la escritura inicial desde PowerShell. El recorrido automatizado espera que termine cada navegación antes de seleccionar cuentas y evita buscar alertas duplicadas: PrimeNG Message ya aporta su rol accesible.

fut-postgres permanece exited, con las mismas fechas de inicio y fin anteriores. Sin cambios en SQL principal, reglas, migraciones, Git, AWS, Terraform, Ansible o AMQ. CA-07 finalizado; las fases siguientes requieren autorización.
