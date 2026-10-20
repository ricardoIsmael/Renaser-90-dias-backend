# Research: 007 Alarmas confiables de hábitos y avisos sin cruces

Fecha: 2026-10-19. Fuentes: código de los dos repos, `node_modules/expo-notifications` 57.0.17, documentación oficial de Android y la prueba local del mismo día (ver Clarifications de la spec).

## R1. Cómo hacer que el recordatorio sea una alarma tipo despertador

- **Decisión**: parchar `expo-notifications` en `postinstall` para que `ExpoSchedulingDelegate.setupAlarm` use `AlarmManager.setAlarmClock(AlarmClockInfo(hora, showIntent), operation)` cuando `canScheduleExactAlarms()` es verdadero y el disparador **no** es `TimeIntervalTrigger`. Sin permiso queda `setAndAllowWhileIdle`, como hoy.
  - Dos ediciones: la llamada de `scheduleNotification` (`ExpoSchedulingDelegate.kt:67`) pasa `request.trigger !is TimeIntervalTrigger`, y `setupAlarm(..., comoDespertador: Boolean = false)`.
  - `showIntent` = intent de arranque de la app (`getLaunchIntentForPackage`) con `FLAG_IMMUTABLE`. Puede ser nulo.
  - Script `scripts/arreglar-alarmas-tipo-despertador.js` con el patrón de `arreglar-screens-removal-listener.js`: `aplicar(raiz)` devuelve `sin-paquete | otra-version | ya-aplicado | aplicado`, falla fuerte si no encuentra el fragmento, `MARCA` para no aplicar dos veces, exportado y probado en Jest con un paquete falso en `os.tmpdir()`.
- **Rationale**:
  - Android: «Alarms set with `setAlarmClock()` continue to fire normally. The system exits Doze shortly before those alarms fire» y «the system never adjusts their delivery time». `setExactAndAllowWhileIdle` está limitado a una vez cada 9 min por app en Doze.
  - Todos los disparadores programables (`Date`, `Daily`, `Weekly`…) pasan por esa línea, también el rearmado diario tras sonar (`triggerNotification` → `schedule`) y el arranque (`setupScheduledNotifications`). Un solo punto cubre todo.
  - Reprogramar con el mismo identificador reemplaza la alarma: mismo `PendingIntent` (URI con el identificador, request code constante, `NotificationsService.kt:416-440`). `rearmarAlarmas.ts` ya reprograma todo al abrir: las alarmas instaladas pasan solas al tipo nuevo.
  - La única `TimeIntervalTrigger` de la app es la prueba de sonido (`probarSonido.ts:46`), que así no aparece como próxima alarma (FR-005).
- **Alternativas descartadas**:
  - Módulo nativo propio que programe y muestre las notificaciones: duplica canales, voces, ruta, agrupación (E-410) y cancelación; más superficie y más riesgo.
  - Config plugin de Expo que reemplace la clase: el delegado no es configurable; el parche es más chico y la prueba lo vigila.
  - `USE_EXACT_ALARM`: Play lo reserva a apps de reloj o calendario.
  - Pantalla completa que suene hasta apagarla (`USE_FULL_SCREEN_INTENT`): restringida por Play; fuera de alcance (Assumptions de la spec).
- **Alcance**: aplica a todos los avisos programados de la app (hábitos, Despertar, eventos, objetivos, Código Renaser, repaso del domingo), no solo a hábitos: el delegado no distingue y el resultado buscado es el mismo. Queda en la spec como borde esperado.

## R2. Detectar que el sistema detuvo la app a la fuerza

- **Decisión**: función nativa nueva en `modules/renaser-alarmas`, `detencionForzadaDesde(desdeMs): { detenida: boolean, cuando: number | null, motivo: string | null }`:
  - Android 15+ (API 35): `getHistoricalProcessStartReasons(1)` → `ApplicationStartInfo.wasForceStopped()` del arranque actual.
  - Android 11–14 (API 30–34): `getHistoricalProcessExitReasons(paquete, 0, 16)`. Cuenta los `REASON_USER_REQUESTED` más nuevos que `desdeMs` cuyo `toString()` no indique quitar de recientes (`subreason=22`, «remove task»).
  - Android 10 o menos: `detenida = false`.
  - La app guarda por cuenta el último instante revisado (`renaser.alarmas.detencionRevisada.<id>`) y la última vez que mostró el aviso (`renaser.alarmas.avisoDetencion.<id>`, una vez por día, FR-007).
- **Rationale**: `REASON_USER_REQUESTED` (10) es «Force stop» en Ajustes o quitar de recientes; la sub-razón no es pública pero `toString()` la imprime. En un Android sin capa del fabricante quitar de recientes **no** borra las alarmas (prueba local), así que no debe avisar. Android 15 documenta `wasForceStopped()` justo para «re-register for any alarms».
- **Riesgo conocido**: no hay fuente oficial de qué motivo registra Honor al cerrar desde recientes con «Gestionar automáticamente». Se registra `reason`, `description` y `toString()` en el log de la app para medirlo en el Honor real (SC-002). Si Honor lo reporta como «remove task», el aviso no saldría: el plan B es avisar ante cualquier `REASON_USER_REQUESTED` solo en marcas Honor/Huawei/Xiaomi/Oppo/Vivo.
- **Alternativas descartadas**: comprobar las alarmas con `PendingIntent.FLAG_NO_CREATE` (los `PendingIntent` sobreviven antes de Android 15); `getNextAlarmClock()` (es global, puede ser de otra app).

## R3. Guía y pantalla por marca

- **Decisión**: función nativa `abrirAjusteDeLaMarca(): 'pantalla_de_la_marca' | 'lista_de_bateria' | 'ajustes_de_la_app' | 'ninguna'`. Elige la lista por `Build.MANUFACTURER`/`Build.BRAND` en minúsculas y prueba en orden con `startActivity` dentro de try/catch (`ActivityNotFoundException`, `SecurityException`). Después cae a `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` y a `ACTION_APPLICATION_DETAILS_SETTINGS` con `package:`.
  - Honor: `com.hihonor.systemmanager/.startupmgr.ui.StartupNormalAppListActivity`, `…appcontrol.activity.StartupAppControlActivity`, luego la lista de Huawei.
  - Huawei: `com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity`, `….appcontrol.activity.StartupAppControlActivity`, `….optimize.process.ProtectActivity`.
  - Xiaomi/Redmi/POCO: `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity`, `com.miui.powerkeeper/…HiddenAppsConfigActivity` (con `package_name`, `package_label`).
  - Samsung: `com.samsung.android.lool/com.samsung.android.sm.battery.ui.BatteryActivity` y variantes; si no, detalles de la app (Batería → Sin restricciones).
  - Oppo/Realme/OnePlus nuevos: `com.coloros.safecenter/.permission.startup.StartupAppListActivity`, `….startupapp.StartupAppListActivity`, `com.oplus.safecenter/…StartupAppListActivity`, `com.oppo.safe/…`.
  - Vivo/iQOO: `com.vivo.permissionmanager/.activity.BgStartUpManagerActivity`, `com.iqoo.secure/.ui.phoneoptimize.BgStartUpManager`, `…AddWhiteListActivity`.
  - OnePlus viejo: `com.oneplus.security/.chainlaunch.view.ChainLaunchAppListActivity`.
- La línea de texto por marca vive en JS (`alarmas/data/guiasPorMarca.ts`), con la marca leída de `Platform.constants` (`Manufacturer`, `Brand`).
- **Rationale**: son los componentes que usan AutoStarter, auto_start_flutter y otras apps mantenidas; todos frágiles, por eso el orden y la caída. `startActivity` no necesita `<queries>`; no se pre-comprueba, así que no se declaran.
- **Prohibido**: `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` y el permiso `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (política de Play, FR-009).

## R4. Hábitos apagados por hoy (FR-011)

- **Decisión**: en `AvisoHabitoService.despacharDe`, calcular la fecha una vez y descartar los registros cuyos hábitos estén en `habitosApagadosEn(participante, fecha)` ∪ `habitosApagadosEnDiaSemana(participante, fecha.getDayOfWeek())` (`LoadPreferenciaHorarioPort`, ya inyectado).
- **Rationale**: es el mismo par que usa `RegistroService:237-242` para no generar registros de días futuros. Sin puerto ni consulta nueva.
- **Alternativa descartada**: borrar el registro pendiente al apagar el día. Toca la regla de snapshot (principio IV) y lo que ya vio la app.

## R5. Texto con la hora y tildes (FR-012, FR-016)

- **Decisión**: `AvisoHabitoDebidoEvent` suma al final `Instant momentoAvisado` y `String zonaHoraria` (nulos aceptados para eventos viejos del outbox). Un record de dominio nuevo `notifications/domain/model/habito/AvisoDeHabito` arma título y cuerpo:
  - INICIO: «Tu hábito está por empezar» / «{hábito}: te toca a las 07:00. En juego: N puntos.»
  - POR_VENCER: «Se te vence un hábito» / «{hábito} vence a las 21:30. En juego: N puntos.»
  - Sin momento o sin zona (evento viejo): el texto de minutos de hoy, con tildes.
  - Formato `HH:mm` en la zona del participante, como `AvisoDeEvento`.
- **Rationale**: el momento exacto ya existe (`AvisoHabito.momento()`) y la zona también (`progreso.timezone()`); el texto de minutos sale del instante del barrido y por eso no cuadra con la alarma.

## R6. Volver a avisar si cambia la hora (FR-013)

- **Decisión**: `TipoAvisoHabito.claveIdempotencia(registroId, momento)` = `nameUUIDFromBytes(prefijo + registroId + ":" + momento.getEpochSecond())`.
- **Rationale**: `momento` sale de la ventana calculada con la hora del hábito, no del reloj del barrido: los barridos de 5 min dan la misma clave y una hora nueva da una clave nueva. El índice único `notificaciones_origen_evento_uk` (V16) no cambia: **sin migración**.
- **Efectos aceptados y anotados**:
  - Cambiar de A a B y volver a A no avisa de nuevo para A (ya existe).
  - El aviso viejo queda en la bandeja.
  - POR_VENCER también vuelve a avisar si se mueve su plazo.
  - El día del despliegue cambian todas las claves: un aviso ya enviado y todavía dentro de su franja (15 min el de inicio, 30 el de vencimiento) saldría una vez más. Se despliega en la madrugada de Lima, cuando casi no hay hábitos con hora; antes se cuenta en la base cuántos avisos caen en la hora elegida (`quickstart.md`, paso de despliegue).
  - El chat del acompañante (`AvisoHabitoEnChatService`) también usa `claveEvento`; está apagado por defecto (`IA_ACOMPANANTE_AVISOS_EN_CHAT=false`).

## R7. Un solo aviso por hábito y momento (FR-014)

- **Decisión**:
  - Backend: el push de hábitos lleva `tag` = `habito-{habitoId}-inicio` o `habito-{habitoId}-por-vencer`. Viaja por un campo nuevo de `EntregaPush`/`MensajePush`, no por `EmitirNotificacionCommand` (que tiene ~20 llamadores y un único constructor por `SelfValidating`). `ExpoPushTransporte` ya manda `tag` y `threadId` cuando hay etiqueta (D-221) y `WebPushAdapter` también.
  - App: la alarma local de la antelación **más temprana** del hábito (la que el servidor conoce como `minutos_recordatorio`) se programa con `identifier = habito-{habitoId}-inicio`. `expo-notifications` usa el identificador como `tag` al mostrarla, y `FirebaseMessagingDelegate.kt:129` usa `data.tag` del push como identificador: el que llega segundo reemplaza al primero.
  - Las demás antelaciones y las alarmas de fecha de un cambio diferido siguen con UUID (los identificadores deben ser únicos por pedido).
- **Rationale**: es lo único que el teléfono puede predecir sin conocer el registro del día (las alarmas diarias se repiten).
- **Límite**: en iOS `threadId` solo agrupa; hoy la app es solo Android.

## R8. No enviar avisos vencidos (FR-015)

- **Decisión**: `AvisoHabitoNotificationListener` inyecta `Clock` y, como `RecordatorioEventoNotificationListener:57-75`, descarta el evento si `!clock.now().isBefore(momento)` (momento del evento o, en eventos viejos, `occurredAt + minutosQueFaltan`). La regla vive en `AvisoDeHabito.yaNoSirve(ahora)`.
- Igual que en eventos, se descarta también la fila de la bandeja.

## R9. Compatibilidad

- App publicada + backend nuevo: recibe textos nuevos y `tag`; el APK 1.5.0 ya usa `data.tag` (D-221). Sin cambios visibles más allá del texto.
- App nueva + backend anterior: el `tag` no llega; puede verse el push de respaldo y la alarma por separado, como hoy. Todo lo demás funciona.
- Outbox: eventos viejos sin los campos nuevos usan el texto con minutos.
