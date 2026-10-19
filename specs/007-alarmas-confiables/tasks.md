---
description: "Tareas de la spec 007: alarmas confiables de hábitos y avisos sin cruces"
---

# Tasks: Alarmas confiables de hábitos y avisos sin cruces

**Input**: `specs/007-alarmas-confiables/` (plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md)

**Tests**: TDD obligatorio (constitución III y pedido del dueño). En cada historia, las pruebas se escriben primero y deben **fallar** antes de implementar.

**Rutas**: dos repos con la misma rama `007-alarmas-confiables`.
- `back:` = `Renaser-90-dias-backend/.claude/worktrees/alarmas-confiables/` (Java en `src/main/java/com/renaser/os/`, pruebas en `src/test/java/com/renaser/os/`).
- `app:` = `Renaser-90-dias-frontend-/.claude/worktrees/alarmas-confiables/`.

**Reglas que valen para todas las tareas**:
- No tocar `app:src/features/alarmas/api/preferenciasDeNotificacion.ts`, `app:src/features/alarmas/components/InterruptoresDeAvisos.tsx` ni `back:…/notifications/domain/model/TipoNotificacion.java` (spec 005).
- Textos en español de Perú con tuteo, máximo dos frases.
- Nunca probar contra producción.
- El emulador, el backend local, Metro, `clean verify`, Testcontainers y jest completo se piden por turno a las otras sesiones (`ListAgents` + `SendMessage`) y se liberan avisando.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [x] T001 Crear el worktree de la app: `git -C Renaser-90-dias-frontend- worktree add -b 007-alarmas-confiables .claude/worktrees/alarmas-confiables master`, y dentro `npm ci` (no `npm install`). Ver la receta de node en la memoria `node-en-toolbox-deb12`.
- [x] T002 [P] En `back:`, confirmar que compila: `./mvnw -q -DskipTests compile` (no es `verify`; no necesita turno).
- [x] T003 [P] En `app:`, línea base: `node node_modules/typescript/bin/tsc --noEmit` y Jest solo de `src/features/alarmas` y `src/features/habits/notificaciones` (no la suite completa).

---

## Phase 2: Foundational

Ninguna. Las tres historias son independientes: US1 y US2 solo tocan la app y US3 solo el backend, salvo T040, que une US3 con la alarma local.

---

## Phase 3: User Story 1 — La alarma suena como el despertador (P1) 🎯 MVP

**Goal**: toda alarma programada por la app usa `AlarmManager.setAlarmClock` cuando hay permiso de alarmas exactas (FR-001 a FR-005, research R1).

**Independent Test**: quickstart A1 a A6 en el emulador. `dumpsys alarm` muestra las alarmas de Renaser como alarma del usuario, suenan en Doze forzado y la prueba de sonido no aparece como próxima alarma.

### Tests (primero, deben fallar)

- [x] T010 [P] [US1] Escribir `app:src/__tests__/alarmasTipoDespertador.test.ts` con el patrón de `src/__tests__/screensRemovalListener.test.ts`:
  - el script se requiere como `{ aplicar, MARCA }`;
  - en `os.tmpdir()` se arma un paquete falso con un `ExpoSchedulingDelegate.kt` que contiene los fragmentos reales de 57.0.17 (la llamada `setupAlarm(nextTriggerDate.time, NotificationsService.createNotificationTrigger(context, request.identifier))` y el cuerpo de `setupAlarm` con `AlarmManagerCompat.setExactAndAllowWhileIdle`);
  - casos: `aplicar` devuelve `'aplicado'` y el archivo contiene `setAlarmClock`, `AlarmClockInfo`, `TimeIntervalTrigger` y `FLAG_IMMUTABLE`; la segunda vez `'ya-aplicado'`; sin paquete `'sin-paquete'`; otra versión `'otra-version'`; si falta un fragmento, lanza; `package.json` incluye el script en `postinstall`.

### Implementation

- [x] T011 [US1] Crear `app:scripts/arreglar-alarmas-tipo-despertador.js` (copia del patrón de `scripts/arreglar-screens-removal-listener.js`) que parcha `node_modules/expo-notifications/android/src/main/java/expo/modules/notifications/service/delegates/ExpoSchedulingDelegate.kt`:
  - **(a)** `scheduleNotification` (línea ~67) llama `setupAlarm(nextTriggerDate.time, NotificationsService.createNotificationTrigger(context, request.identifier), request.trigger !is TimeIntervalTrigger)` e importa `expo.modules.notifications.notifications.triggers.TimeIntervalTrigger`.
  - **(b)** `setupAlarm(triggerAtMillis: Long, operation: PendingIntent, comoDespertador: Boolean = false)`. En la rama exacta, si `comoDespertador`, arma `show` = `context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }` y llama `alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), operation)`. Si no, como hoy. La rama sin permiso (`setAndAllowWhileIdle`) no cambia.
  - Comprueba la versión `57.0.17` y antepone `MARCA`. Comentario de cabecera en español: qué, por qué (research R1) y cómo se prueba.
- [x] T012 [US1] Agregar `node scripts/arreglar-alarmas-tipo-despertador.js` al final de `"postinstall"` en `app:package.json`, sin tocar ninguna otra línea (ojo: el checkout principal tiene un `lottie-react-native` sin commitear que no es nuestro; trabajar solo en el worktree). Correr el script una vez y ver T010 en verde.
- [x] T013 [US1] Ajustar el comentario de `app:src/features/alarmas/rearmarAlarmas.ts`: el rearmado al abrir también convierte las alarmas viejas al tipo despertador, porque el `PendingIntent` es el mismo. Sin cambio de lógica.
- [x] T014 [US1] En `app:src/features/alarmas/probarSonido.ts`, confirmar con una prueba en `app:src/features/alarmas/__tests__/escucharSonido.test.ts` que la prueba de sonido sigue usando un disparador de intervalo (`TIME_INTERVAL`, 3 s): es lo que la excluye del despertador (FR-005). Ver la prueba en verde.

**Checkpoint**: Jest de US1 en verde y `tsc` sin errores. La validación en el emulador (A1–A6) va en la fase final, con turno.

---

## Phase 4: User Story 2 — Saber que el teléfono borró mis alarmas y cómo evitarlo (P1)

**Goal**: detectar la detención forzada al abrir, reprogramar, avisar una vez por día y guiar por marca (FR-006 a FR-010; research R2 y R3; `contracts/modulo-renaser-alarmas.md`).

**Independent Test**: quickstart B1 a B5. Tras `am force-stop`, al abrir aparece la hoja una sola vez en el día; quitar de recientes no la muestra; el botón abre una pantalla sin error.

### Tests (primero, deben fallar)

- [x] T020 [P] [US2] `app:src/features/alarmas/__tests__/guiasPorMarca.test.ts`. Para `Manufacturer`/`Brand` (`HONOR`, `HUAWEI`, `Xiaomi`, `Redmi`, `POCO`, `samsung`, `OPPO`, `realme`, `OnePlus`, `vivo`, `iQOO`, `Google`), la línea de `data-model.md` §Guía por marca. Con otra marca, el texto actual `TEXTO_DE_BATERIA`. Ninguna línea supera dos frases ni usa voseo.
- [x] T021 [P] [US2] `app:src/features/alarmas/__tests__/detencionForzada.test.ts`, con el módulo falso por `usarModuloDeAlarmas` y AsyncStorage en memoria:
  - (a) `detenida: true` y no se avisó hoy → devuelve «avisar», guarda `renaser.alarmas.avisoDetencion.<id>` con la fecha local y `renaser.alarmas.detencionRevisada.<id>` con `cuando`;
  - (b) mismo día otra detención → no avisa;
  - (c) `detenida: false` → no avisa y guarda el instante revisado;
  - (d) módulo ausente (APK viejo) → no avisa y no lanza;
  - (e) otra cuenta → claves separadas;
  - (f) al cerrar sesión, `soltarAlarmasDeLaCuenta` borra las dos claves (prefijos en `alarmasDeLaCuenta.ts`).
- [x] T022 [P] [US2] `app:src/features/alarmas/__tests__/avisoDeAlarmasBorradas.test.tsx`:
  - con «avisar», la hoja muestra dos frases y los botones «Arreglarlo» y «Ahora no»;
  - «Arreglarlo» llama `abrirAjusteDeLaMarca` y, si devuelve `'ninguna'`, `Linking.openSettings`;
  - el atrás del sistema cierra la hoja (`useSystemBackHandler`);
  - sin sesión o en web no renderiza nada.
- [x] T023 [P] [US2] Ampliar `app:src/features/alarmas/__tests__/` con una prueba de `GuiaDeBateria`: con marca Honor muestra la línea de Honor y el botón abre `abrirAjusteDeLaMarca`; con marca desconocida, el texto y el comportamiento de hoy.

### Implementation

- [x] T024 [US2] Kotlin en `app:modules/renaser-alarmas/android/src/main/java/expo/modules/renaseralarmas/RenaserAlarmasModule.kt`. Se agregan dos funciones según `contracts/modulo-renaser-alarmas.md`, sin tocar las dos existentes:
  - **`detencionForzadaDesde(desdeMs: Double)`**:
    - API ≥ 35: `getHistoricalProcessStartReasons(1)` y `wasForceStopped()`;
    - API 30–34: `getHistoricalProcessExitReasons(packageName, 0, 16)`, filtra `reason == REASON_USER_REQUESTED`, `timestamp > desdeMs` y que `toString()` no contenga `subreason=22` ni «remove task»;
    - en los dos casos devuelve `mapOf("detenida", "cuando", "motivo")` y escribe `Log.i("RenaserAlarmas", …)` con `toString()`;
    - API < 30 o cualquier excepción: `detenida=false`.
  - **`abrirAjusteDeLaMarca()`**: lista por `Build.MANUFACTURER`/`Build.BRAND` en minúsculas con los componentes de research R3, en ese orden. Cada intento es `startActivity` en try/catch (`ActivityNotFoundException`, `SecurityException`, `Exception`); para Xiaomi `HiddenAppsConfigActivity` lleva los extras `package_name` y `package_label`. Después intenta `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` y luego `ACTION_APPLICATION_DETAILS_SETTINGS` con `package:`. Agrega `FLAG_ACTIVITY_NEW_TASK` si no hay actividad actual. Devuelve el nombre del paso.
  - **Prohibido**: `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` y el permiso `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Sin `<queries>`.
- [x] T025 [US2] En `app:src/features/alarmas/alarmaExactaNativa.ts`, ampliar `ModuloDeAlarmas` con las dos funciones como **opcionales** y exportar `detencionForzadaDesde(desdeMs)` y `abrirAjusteDeLaMarca()`. Devuelven el valor neutro del contrato si el módulo o la función faltan, o si lanzan.
- [x] T026 [P] [US2] Crear `app:src/features/alarmas/utils/marcaDelTelefono.ts`: lee `Platform.constants.Manufacturer` y `Brand` (Android), normaliza a una clave (`honor`, `huawei`, `xiaomi`, `samsung`, `oppo`, `vivo`, `otra`) y es inyectable para las pruebas.
- [x] T027 [P] [US2] Crear `app:src/features/alarmas/data/guiasPorMarca.ts` con las líneas de `data-model.md` (hace pasar T020).
- [x] T028 [US2] Crear `app:src/features/alarmas/detencionForzada.ts`: `revisarDetencionForzada(userId, ahora, deps)` devuelve `'avisar' | 'nada'` con las reglas de T021. Agregar los dos prefijos nuevos a `PREFIJOS_DE_ALARMAS` en `app:src/features/alarmas/alarmasDeLaCuenta.ts`.
- [x] T029 [US2] Crear `app:src/features/alarmas/components/AvisoDeAlarmasBorradas.tsx` sobre `HojaDesdeAbajo`:
  - texto: «Tu teléfono cerró Renaser y por eso no sonaron tus alarmas. Ya las volvimos a poner.» y la línea de la marca;
  - botones «Arreglarlo» (`GoldButton`) y «Ahora no»;
  - `Presionable`, `useSystemBackHandler`, tema claro y oscuro.
  - Corre una vez por apertura con sesión: primero `ponerAlDiaLasAlarmas` y después `revisarDetencionForzada`.
  - Movimiento: lo de `HojaDesdeAbajo`, nada nuevo (skills `emil-design-eng` y `animate-expo`).
- [x] T030 [US2] Montar `<AvisoDeAlarmasBorradas />` en `app:App.tsx` junto a `RearmadorDeAlarmas`, sin mover nada más.
- [x] T031 [US2] En `app:src/features/alarmas/components/GuiaDeBateria.tsx`, mostrar la línea de la marca y abrir `abrirAjusteDeLaMarca()`, con `Linking.openSettings()` si devuelve `'ninguna'`. `TEXTO_DE_BATERIA` sigue exportado para la marca desconocida.

**Checkpoint**: Jest de US2 en verde y `tsc` sin errores.

---

## Phase 5: User Story 3 — Un solo aviso por hábito y a la hora correcta (P2)

**Goal**: FR-011 a FR-017 en el backend (research R4 a R8; `contracts/push-aviso-habito.md`), más la identidad compartida con la alarma local (T040).

**Independent Test**: quickstart C1 a C5 contra el backend local (8097, `renaser_alarmas`) y `TZ=UTC ./mvnw clean verify` en verde.

### Tests (primero, deben fallar)

- [x] T032 [P] [US3] En `back:src/test/java/com/renaser/os/habits/application/services/AvisoHabitoServiceTest.java`, con `MADRUGADA_UTC`/`DIA_EN_LIMA` y `FixedClock`:
  - (a) registro en su franja con el hábito en `habitosApagadosEn(participante, DIA_EN_LIMA)` → no publica;
  - (b) el hábito en `habitosApagadosEnDiaSemana(participante, DIA_EN_LIMA.getDayOfWeek())` → no publica;
  - (c) la consulta usa el día en Lima, no el de UTC;
  - (d) el evento lleva `momentoAvisado` = `aviso.momento()` y `zonaHoraria` = zona del participante;
  - (e) `claveEvento == TipoAvisoHabito.INICIO.claveIdempotencia(REGISTRO, momento)`; (f) un registro `EN_CURSO` no recibe aviso de INICIO (FR-015b), sí el de vencimiento si corresponde.
- [x] T033 [P] [US3] En `back:src/test/java/com/renaser/os/habits/domain/model/aviso/CalculadoraAvisosHabitoTest.java` (o un `TipoAvisoHabitoTest` nuevo):
  - mismo registro y momento → misma clave;
  - otro momento → otra clave;
  - INICIO ≠ POR_VENCER con el mismo momento.
  - Actualizar los usos existentes a la firma nueva.
- [x] T034 [P] [US3] Crear `back:src/test/java/com/renaser/os/notifications/domain/model/habito/AvisoDeHabitoTest.java`. Usa como modelo `AvisoDeEventoTest` («con la hora de Lima, no la UTC»):
  - títulos «Tu hábito está por empezar» y «Se te vence un hábito»;
  - cuerpo «{hábito}: te toca a las 06:00. En juego: 10 puntos.» y «{hábito} vence a las 21:30. En juego: 10 puntos.» con un momento en la madrugada UTC;
  - sin momento o sin zona, el texto con minutos (con tildes);
  - `etiqueta()` = `habito-{habitoId}-inicio` / `-por-vencer`, y nula sin `habitoId`;
  - `yaNoSirve(ahora)` verdadero si `ahora >= momento`, incluido el respaldo `occurredAt + minutosQueFaltan` truncado al minuto.
- [x] T035 [P] [US3] Ampliar `back:src/test/java/com/renaser/os/notifications/infrastructure/adapter/in/event/AvisoHabitoNotificationListenerTest.java` con `FixedClock`:
  - emite el título y el cuerpo de `AvisoDeHabito`;
  - con el momento vencido no emite nada;
  - el push lleva la etiqueta;
  - siguen pasando las verificaciones actuales de ruta, tipo y entrega.
- [x] T036 [P] [US3] Pruebas del transporte:
  - en `back:src/test/java/com/renaser/os/notifications/application/services/NotificacionServiceTest.java`, la etiqueta del aviso de hábito llega al `MensajePush`;
  - en `WebPushAdapterTest` y en una prueba de `ExpoPushTransporte` (crear `ExpoPushTransporteTest` si no existe), el JSON lleva `tag` (y `threadId` en Expo) cuando hay etiqueta, y no lo lleva cuando no.
  - La etiqueta de chat (D-221) no cambia.

### Implementation

- [x] T037 [US3] `back:habits/domain/model/aviso/TipoAvisoHabito.java`: `claveIdempotencia(RegistroHabitoId, Instant momento)` = `UUID.nameUUIDFromBytes((prefijo + registroId.value() + ":" + momento.getEpochSecond()).getBytes(UTF_8))`. Javadoc: por qué el momento (FR-013) y el efecto del día del despliegue (research R6).
- [x] T038 [US3] Hacer dos cambios en `habits`:
  - `back:habits/api/AvisoHabitoDebidoEvent.java`: agregar **al final** `Instant momentoAvisado` y `String zonaHoraria` (pueden ser nulos), con javadoc de compatibilidad con el outbox.
  - `back:habits/application/services/AvisoHabitoService.java`: en `despacharDe`, calcular la fecha una vez, armar `apagados = habitosApagadosEn ∪ habitosApagadosEnDiaSemana` con `loadPreferenciaPort` (ya inyectado) y filtrarlos en `registrosVivosDe`; además, no publicar INICIO para un registro `EN_CURSO` (FR-015b). En `eventoDe`, usar la clave con `aviso.momento()` y llenar los campos nuevos. Métodos ≤ 20 líneas.
- [x] T039 [US3] Crear `back:notifications/domain/model/habito/AvisoDeHabito.java` (record de dominio sin Spring, formato `HH:mm` como `AvisoDeEvento`). Después, cuatro cambios en el transporte:
  - En `back:notifications/infrastructure/adapter/in/event/AvisoHabitoNotificationListener.java`: inyectar `Clock`, descartar con `yaNoSirve`, usar título y cuerpo nuevos y pasar la etiqueta.
  - La etiqueta viaja por `EntregaPush` o por una sobrecarga de `emitir`. **No** por `EmitirNotificacionCommand` (research R7).
  - `back:notifications/application/services/NotificacionService.java` la copia al `MensajePush`.
  - `back:notifications/application/ports/out/push/MensajePush.java` usa la etiqueta explícita si viene y, si no, `EtiquetaDelAviso.de(...)` como hoy.
  - Hace pasar T034–T036.
- [x] T040 [US3] App: en `app:src/features/habits/notificaciones/recordatoriosDeHabito.ts` (`programar`, ~l.497-511), programar la antelación **más temprana** con `identifier: \`habito-${habitoId}-inicio\``; las demás siguen sin identificador (UUID).
  - Primero, prueba en `app:src/features/habits/notificaciones/__tests__/` (mock de `expo-notifications` en memoria): con antelaciones `[10, 0]`, el pedido de 10 lleva ese identificador y el de 0 no.
  - Al reprogramar no queda duplicado: cancelar por ids guardados sigue funcionando.
  - `rearmarAlarmas` conserva el identificador.
  - Las alarmas de fecha de `programarConCambioDiferido` no cambian.

**Checkpoint**: en `back:` las pruebas nuevas en verde (se pueden correr sueltas con `-Dtest=…` sin turno); `clean verify` completo en la fase final, con turno. En `app:` Jest de `habits/notificaciones` en verde.

---

## Phase 6: Polish & validación

- [x] T041 Documentar en `back:docs/MODULOS_A_AVANZAR.md` §8 y `back:docs/BITACORA_ERRORES.md`.
  - D-nn: alarma tipo despertador para todos los avisos, guía por marca sin excepción directa de batería, texto con la hora, clave con el momento y despliegue de madrugada, y etiqueta compartida.
  - E-nnn: Honor borra las alarmas; aviso de un hábito apagado por hoy; sin aviso tras cambiar la hora; push con minutos que no cuadran y duplicado; textos sin tildes.
  - Antes de numerar, `git fetch` y revisar los últimos números de `origin/master` y de las ramas 002, 005 y 006, y avisar a d8 y 89 por mensaje.
- [x] T042 [P] Anotar en `app:AGENTS.md` (sección de Integridad del Core) la excepción del 2026-10-19 con «lo que cambia y nada más»:
  - la guía de batería por marca en Yo → Alarmas;
  - la hoja global al abrir tras una detención forzada;
  - las alarmas tipo despertador (próxima alarma e ícono en la barra);
  - que hace falta un APK nuevo y el backend nuevo.
- [ ] T043 Pedir turno y correr `TZ=UTC ./mvnw clean verify` en `back:`; leer `Tests run:` de surefire y failsafe; liberar el turno avisando.
- [ ] T044 Pedir turno y, en `app:`, correr `tsc --noEmit` y la suite de Jest completa; liberar el turno.
- [ ] T045 Pedir turno de emulador y backend. Compilar el APK debug del worktree (lleva el parche y el módulo), backend `back:` en 8097 con `renaser_alarmas`, y correr los escenarios A1–A6 (con A3b), B1–B5 y C1–C5 de `quickstart.md` con mobile-mcp, con captura por caso en `~/Imágenes/e2e-2026-10-19/` o la fecha del día.
- [ ] T046 Con el mismo turno, correr la suite completa de Maestro (`.maestro/`), actualizando en el mismo trabajo los flujos que hayan quedado viejos. Liberar el emulador y avisar.
- [ ] T047 Prueba en un Honor real (quickstart, sección Honor) con build de prueba apuntando al backend local por la red; registrar el `motivo` del módulo en la bitácora. Si Honor no reporta la detención, aplicar el plan B de research R2 (avisar ante cualquier `REASON_USER_REQUESTED` en esas marcas) con su prueba.
- [ ] T048 `graphify update .` en los dos worktrees y dejar los commits listos por funcionalidad (backend y app por separado, en español, sin coautoría). Commit solo cuando el usuario lo pida.

---

## Dependencies & Execution Order

- **Setup** (T001–T003) antes de todo.
- **US1** (T010–T014) solo depende de T001.
- **US2** (T020–T031) solo depende de T001. Toca `alarmaExactaNativa.ts`, que US1 no toca: pueden ir en paralelo con agentes distintos si no comparten archivos. `App.tsx` solo lo toca US2.
- **US3**: el backend (T032–T039) depende de T002 y es independiente de la app. T040 es de la app y no depende del backend; el push sin etiqueta no rompe nada.
- **Polish** (T041–T048) al final. T045 y T046 necesitan US1, US2 y T040 en el APK.
- Dentro de cada historia: pruebas → implementación → checkpoint.

## Parallel Example

```text
Agente backend (Sonnet xhigh): T032, T033, T034, T035, T036 (pruebas, en paralelo) → T037 → T038 → T039
Agente app A   (Sonnet xhigh): T010 → T011 → T012 → T013 → T014 → T040
Agente app B   (Sonnet xhigh): T020, T021, T022, T023 (pruebas) → T024 → T025 → T026, T027 → T028 → T029 → T030 → T031
```

Los dos agentes de la app trabajan en el mismo worktree con archivos distintos. Si se cruzan, se ordenan (A primero).

## Implementation Strategy

1. **Primero el backend (US3, T032–T039 + T041 parcial)**: se puede publicar solo, en la madrugada de Lima, y arregla los cruces para la app que ya tienen los usuarios.
2. **MVP de la app: US1** (alarma tipo despertador), que ataca la queja principal en todas las marcas que no detienen la app.
3. **US2** en el mismo APK: es lo que realmente ayuda en Honor.
4. Validación (T043–T047) por turnos y entrega por funcionalidad.
