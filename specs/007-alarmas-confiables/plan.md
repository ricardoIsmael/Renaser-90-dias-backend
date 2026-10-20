# Implementation Plan: Alarmas confiables de hábitos y avisos sin cruces

**Branch**: `007-alarmas-confiables` (backend y app) | **Date**: 2026-10-19 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/007-alarmas-confiables/spec.md`

## Summary

Tres frentes, cada uno se puede entregar solo:

1. **App · alarma tipo despertador** (P1): un parche de `postinstall` a `expo-notifications` hace que toda alarma programada (salvo la prueba de sonido) use `AlarmManager.setAlarmClock` cuando hay permiso de alarmas exactas. Como Expo 57 enlaza ese módulo precompilado, `package.json` lo declara en `expo.autolinking.android.buildFromSource` para que se compile el código parchado (E-637). El rearmado que ya corre al abrir la app convierte las instaladas. Ver [research R1](./research.md#r1-cómo-hacer-que-el-recordatorio-sea-una-alarma-tipo-despertador).
2. **App · detención forzada y guía por marca** (P1): el módulo nativo `renaser-alarmas` suma dos funciones (detectar la detención forzada; abrir la pantalla de la marca). Un componente global, montado junto a `RearmadorDeAlarmas`, muestra una hoja corta una vez por día, y la guía de Yo → Alarmas pasa a ser por marca. Ver R2 y R3.
3. **Backend · avisos sin cruces** (P2):
   - no avisar hábitos apagados para el día;
   - texto con la hora y tildes;
   - clave con el momento, para volver a avisar si cambia la hora;
   - `tag` por hábito y tipo, compartido con la alarma local;
   - descartar avisos vencidos.

   Sin endpoints ni migraciones. Ver R4 a R8.

## Technical Context

**Language/Version**: Java 25 (Spring Boot, Modulith) · TypeScript estricto + Kotlin del módulo Expo (Expo SDK 57, RN 0.86)

**Primary Dependencies**: `expo-notifications` 57.0.17 (parchado en `postinstall`), módulo local `modules/renaser-alarmas`, AlarmManager/ActivityManager de Android; backend sin dependencias nuevas

**Storage**: PostgreSQL sin cambios de esquema; AsyncStorage en la app para dos marcas de tiempo por cuenta

**Testing**: JUnit 5 + Mockito + Testcontainers (`./mvnw clean verify`), ArchitectureTest; Jest + `tsc --noEmit`; E2E con Maestro y mobile-mcp en el emulador; prueba manual en un Honor real con build de prueba

**Target Platform**: Android 8+ (exacto 12+); servidor Linux

**Project Type**: mobile-app + web-service

**Performance Goals**: alarma dentro de ±60 s de su hora (SC-001); el barrido de 5 min del backend no suma consultas por participante salvo una lectura de apagados del día (ya indexada)

**Constraints**:
- Sin `USE_EXACT_ALARM` ni `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- La app nueva tolera el backend anterior y la publicada tolera el nuevo.
- APK nuevo obligatorio.
- No se tocan `alarmas/api/preferenciasDeNotificacion.ts`, `alarmas/components/InterruptoresDeAvisos.tsx` ni `TipoNotificacion` (spec 005).

**Scale/Scope**: cientos de participantes; ~15 hábitos por persona; 7 marcas con guía propia

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo se cumple | Estado |
|---|---|---|
| I. Dependencias al dominio | Texto, vencimiento y etiqueta del aviso en un record de dominio `notifications/domain/model/habito/AvisoDeHabito` sin Spring. Clave en `habits/domain/model/aviso/TipoAvisoHabito`. `notifications` solo lee `habits.api` (campos nuevos del evento como `Instant`/`String`). App: lógica pura en `alarmas/utils/` y `alarmas/data/`. | ✅ |
| II. No inventar reglas | Decisión del dueño anotada (alarma como la de Android, todos los recordatorios). Lo interpretado está en Assumptions de la spec y va a D-nn. El borde «todas las alarmas de la app pasan a despertador» se anota y se avisa. | ✅ con aviso |
| III. Pruebas en verde | Unitarias de dominio (clave, texto, vencido, apagados) con reloj en madrugada UTC; listener con `FixedClock`; `clean verify`. App: Jest del parche (paquete falso), de la guía por marca, del aviso una vez por día y del identificador de la alarma; `tsc`. Maestro completo + mobile-mcp. Sin endpoints nuevos, así que no hay 403 nuevos. | ✅ |
| IV. Historia | Sin migración; no se borra ni reescribe ningún registro. El índice V16 no cambia. | ✅ |
| V. Tiempo del participante | Fecha y hora del aviso en `progreso.timezone()`; `Clock` inyectado en el listener; sin `now()` estático. | ✅ |
| VI. Tabs | Cambia la guía de batería de Yo → Alarmas y aparece una hoja global al abrir. Se anota la excepción en `AGENTS.md` con «lo que cambia y nada más» (autorización: «procede», 2026-10-19). | ✅ con nota |
| VII. Documentar | D-nn (decisiones) y E-nnn (los tres bugs del servidor y el de Honor) en el mismo cambio, con el siguiente número libre al integrar: master llega a D-265 / E-607; 002, 005 y 006 tienen números en curso. | ✅ |

**Re-check después del diseño**: sin cambios. La única tensión es el parche a una dependencia, justificado abajo.

## Project Structure

### Documentation (this feature)

```text
specs/007-alarmas-confiables/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── push-aviso-habito.md        # payload del push y evento AvisoHabitoDebidoEvent
│   └── modulo-renaser-alarmas.md   # API JS del módulo nativo
├── checklists/requirements.md
└── tasks.md                        # /speckit-tasks
```

### Source Code

**Backend** (`Renaser-90-dias-backend/.claude/worktrees/alarmas-confiables`, rama `007-alarmas-confiables`):

```text
src/main/java/com/renaser/os/
├── habits/
│   ├── api/AvisoHabitoDebidoEvent.java                  # + momentoAvisado, zonaHoraria
│   ├── application/services/AvisoHabitoService.java     # apagados del día; clave con momento
│   └── domain/model/aviso/TipoAvisoHabito.java          # claveIdempotencia(registro, momento)
└── notifications/
    ├── domain/model/habito/AvisoDeHabito.java           # NUEVO: título, cuerpo, etiqueta, yaNoSirve
    ├── domain/model/notificacion/EntregaPush.java       # (o MensajePush) lleva la etiqueta
    ├── application/services/NotificacionService.java    # copia la etiqueta al MensajePush
    └── infrastructure/adapter/in/event/AvisoHabitoNotificationListener.java  # Clock, texto, vencido
src/test/java/com/renaser/os/…                           # pruebas espejo de cada uno
docs/MODULOS_A_AVANZAR.md, docs/BITACORA_ERRORES.md
```

**App** (`Renaser-90-dias-frontend-/.claude/worktrees/alarmas-confiables`, rama `007-alarmas-confiables`, desde `master`):

```text
scripts/arreglar-alarmas-tipo-despertador.js             # NUEVO, en postinstall
src/__tests__/alarmasTipoDespertador.test.ts             # NUEVO (patrón screensRemovalListener)
modules/renaser-alarmas/android/.../RenaserAlarmasModule.kt  # + detencionForzadaDesde, abrirAjusteDeLaMarca
src/features/alarmas/
├── alarmaExactaNativa.ts            # + envoltorios opcionales de las dos funciones
├── data/guiasPorMarca.ts            # NUEVO: marca → línea de texto
├── utils/marcaDelTelefono.ts        # NUEVO: Platform.constants → marca
├── detencionForzada.ts              # NUEVO: revisar, una vez por día por cuenta
├── components/AvisoDeAlarmasBorradas.tsx  # NUEVO: HojaDesdeAbajo al abrir
├── components/GuiaDeBateria.tsx     # texto y botón por marca
└── __tests__/…                      # guía por marca, aviso diario, detección
src/features/habits/notificaciones/recordatoriosDeHabito.ts  # identifier fijo para la antelación más temprana
App.tsx                              # monta AvisoDeAlarmasBorradas junto a RearmadorDeAlarmas
AGENTS.md                            # excepción anotada
```

**Structure Decision**: mobile + API, dos repos con la misma rama. El backend sigue la hexagonal existente; la app concentra todo en `src/features/alarmas/` y el módulo local. `habits` solo cambia el identificador de una alarma.

## Orden de entrega

1. Backend (P2). Va primero y por separado: arregla los cruces para la app publicada sin APK nuevo. Despliegue en la madrugada de Lima (research R6).
2. App: parche (P1) + identificador de la alarma (R7) + detección y guía (P1), en un solo APK.
3. Validación (`quickstart.md`): Maestro completo y mobile-mcp en el emulador por turnos, y Honor real con build de prueba.

## Agentes y coordinación

- Implementan agentes Sonnet 5.5 con esfuerzo xhigh, uno por repo; esta sesión supervisa.
- El emulador, el backend local, Metro y `clean verify` se usan por turnos, avisando a las otras sesiones (d8 → 89 → …).
- Base local: `renaser_alarmas` (ya clonada).
- Sin migración (si aparece, se pide la V111).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Parche en `node_modules/expo-notifications` (Kotlin) vía `postinstall` | Es el único punto donde se elige el tipo de alarma; cubre programar, rearmar tras sonar y arranque | Un módulo nativo propio duplicaría canales, voces, ruta, agrupación y cancelación; un config plugin no puede reemplazar el delegado. El parche se vigila con una prueba que falla si cambia la versión o el fragmento |
| Todas las alarmas de la app pasan a despertador, no solo las de hábitos | El delegado no distingue el origen (ids UUID) | Filtrar por origen exigiría cambiar todos los llamadores; el dueño pidió «como la alarma de Android» |
