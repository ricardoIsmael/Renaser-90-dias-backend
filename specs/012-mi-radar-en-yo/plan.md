# Implementation Plan: Mi Radar en Yo, desde el día 8

**Branch**: `012-mi-radar-en-yo` (backend y app) | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/012-mi-radar-en-yo/spec.md`

## Summary

> **Corregido 2026-10-30 (spec 019).** Las horas ya no se eligen a mano ni se guardan (`horas_mi_radar` se elimina en la V122): salen del
> Despertar y el Dormir. Ver `specs/019-radar-automatico/plan.md`.

Desde el día 8, la persona enciende el Código Renaser en Yo → Mi Radar y elige de 1 a 12 horas en punto, entre 03:00
y 23:00. El teléfono le avisa a esas horas y el formulario es el de siempre. Lo que responde lo ven ella y su
acompañamiento (mentor, líder, administración y alquimista). Los días 1 a 7 no cambian.

Se construye **sin tablas nuevas**:
- dos columnas en `participantes_programa`: las horas de todos los días y los días de la semana con horas propias (jsonb; cuarta
  ronda, 2026-10-29);
- una columna en `registros_radar` para la hora prevista;
- reutilizando la autorización de liderazgo (`AccesoDeLiderazgo`), el endpoint del mentor y el formulario, los avisos
  y el componente del historial que ya tiene la app.

La invitación del día 8 es un aviso local del teléfono.

## Technical Context

**Language/Version**: Java 21 + Spring Boot (backend); TypeScript + React Native / Expo SDK 57 (app)
**Primary Dependencies**: Spring Modulith, Flyway, JPA/JDBC (backend); `expo-notifications`, React Navigation, zod
(app). Sin dependencias nuevas.
**Storage**: PostgreSQL. Tres columnas nuevas en V119 (`horas_mi_radar`, `dias_mi_radar`, `hora_prevista`); ninguna tabla nueva.
**Testing**: JUnit 5 sin Spring para el dominio, Testcontainers para los adaptadores e integración HTTP con pruebas
de autorización negativa (backend); Jest (app); Maestro y mobile-mcp en el emulador (E2E).
**Target Platform**: servidor Linux; Android (y web, sin avisos).
**Project Type**: mobile + API (dos repos).
**Performance Goals**: las lecturas de siempre. El historial se pagina como hoy (84 por página).
**Constraints**: los días 1 a 7 sin un solo cambio; compatibilidad en los dos sentidos (APK viejo con backend nuevo,
y app nueva con backend viejo); nunca contra producción; máquina compartida (sin `mvnw verify`, Testcontainers,
backend ni emulador hasta tener el turno).
**Scale/Scope**: hasta 12 registros por persona y por día, durante 83 días o más.

## Constitution Check

| Principio | Cómo se cumple |
|---|---|
| I. Dependencias al dominio | `MiRadar` y `HorarioDelRadar` son dominio puro. Puertos de salida para leer y guardar Mi Radar. Liderazgo lee el Radar por `habits.api`, nunca por clases internas de `habits` (Spring Modulith lo verifica en `ArchitectureTest`). |
| II. No inventar reglas | Las reglas salen de las tres rondas de Clarifications (horas, plazo, apagado, día 8, día 90, quién ve). Los patrones son los de la vista previa de gerencia. |
| III. Pruebas en verde | Unitarias del dominio con el reloj en la madrugada UTC; adaptador con Testcontainers; HTTP con 403 por rol y por `SUSPENDED`; Jest. `mvnw clean verify` completo al tener la máquina. |
| IV. Historia | V119 con cabecera; `hora_prevista` se guarda al registrar (no se deriva después de las horas actuales, que cambian). Los registros viejos quedan sin hora prevista: no se recalculan. |
| V. Tiempo del participante | La hora prevista y el corte 03:00–23:59 se calculan con `Clock` en la zona de la persona (`participantes_programa.timezone`). Sin `@Scheduled` nuevo. |
| VI. Tabs | Pedido del dueño del 2026-10-29: fila y pantalla en Yo y la sección en las fichas del mentor, del líder y de Administración. Hoy no cambia. Se anota en `docs/producto/` (o en `AGENTS.md` si el PR #48 del harness todavía no está en master). |
| VII. Documentar | D-nn en `docs/MODULOS_A_AVANZAR.md` §8, `docs/MODULO_HABITS.md` §8 (Radar) y, si aparece un error, E-nn en `docs/BITACORA_ERRORES.md`. |

**Gates**: sin violaciones. Complejidad: ninguna justificación pendiente.

## Project Structure

### Documentation (this feature)

```text
specs/012-mi-radar-en-yo/
├── spec.md
├── plan.md            # este archivo
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/api.md
└── checklists/requirements.md
```

### Source Code

```text
Renaser-90-dias-backend/src/main/
├── resources/db/migration/V119__mi_radar_desde_el_dia_8.sql
└── java/com/renaser/os/
    ├── habits/
    │   ├── domain/model/radar/            MiRadar (nuevo), HorarioDelRadar (nuevo), RegistroRadar (+horaPrevista)
    │   ├── application/ports/in/radar/    ConsultarMiRadarUseCase, ConfigurarMiRadarUseCase (nuevos)
    │   ├── application/ports/out/radar/   LoadMiRadarPort, SaveMiRadarPort (nuevos)
    │   ├── application/services/          RadarService (regla del día 8 y hora prevista), MiRadarService (nuevo)
    │   ├── api/                           CodigoRenaserDePersonaFinder (nuevo, para liderazgo)
    │   └── infrastructure/adapter/
    │       ├── in/rest/radar/             RadarController (+GET/PUT /mi-radar), respuestas con horaPrevista y miRadar
    │       ├── in/rest/acompanamiento/    CodigoRenaserDelAlumnoController (respuesta con miRadar)
    │       └── out/persistence/radar/     MiRadarPersistenceAdapter (nuevo, sobre users.api), entidad y mapper (+hora_prevista)
    └── leadership/
        ├── application/services/          RadarDePersonaService (nuevo, con AccesoDeLiderazgo)
        └── infrastructure/adapter/in/rest/CumplimientoDelLiderController (+GET .../people/{p}/radar)

Renaser-90-dias-frontend-/src/
├── features/radar/
│   ├── config/configRadar.ts              + MI_RADAR (desde el día 8, 03–23, 12, sugeridas 9/14/20)
│   ├── utils/miRadar.ts                   (nuevo, puro) validar horas, horas de aviso, patrones, textos
│   ├── api/radarApi.ts, radarSchemas.ts   + obtener/guardar Mi Radar; horaPrevista y miRadar tolerantes
│   ├── hooks/useMiRadar.ts                (nuevo)
│   ├── notificaciones/recordatoriosDeRadar.ts  horas según el día y Mi Radar; invitación del día 8
│   ├── screens/MiRadarScreen.tsx          (nuevo) interruptor, registrar, hoy, horarios, historial, patrones
│   └── RadarContext.tsx                   pasa Mi Radar a la sincronización (días 1–7 igual)
├── features/mentor/components/CodigoRenaserDelAlumno.tsx  recibe cómo cargar (inversión de dependencia) + estado
├── features/admin/screens/FichaAprendizScreen.tsx         usa el mismo componente con la ruta de liderazgo
├── features/lider-mentores/screens/PersonaDelLiderScreen.tsx  ídem
├── screens/YoScreen.tsx                   fila «Mi Radar» desde el día 8, bajo «Tuve una emergencia»
└── navigation/abrirAviso.ts               ruta `/mi-radar` → Yo con `abrirMiRadar`
```

**Structure Decision**: mobile + API, en los módulos donde ya vive el Radar (`habits` y `features/radar`). Liderazgo
solo suma una lectura detrás de la autorización que ya tiene.

## Diseño, en corto

1. **Dominio (`habits`)**
   - `MiRadar`: valor inmutable, apagado o encendido con horas ordenadas para todos los días y, por día de la semana, horas
     propias (`DiaPropio`) o «Sin Radar ese día». Valida de 1 a 12 horas, cada una entre 3 y 23, sin repetir, y los días de 1 a
     7 sin repetir. Si no cumple, lanza la excepción de regla de negocio que ya existe (400). `horasDelDia(DayOfWeek)` da las
     propias del día o las de todos los días.
   - `HorarioDelRadar`: funciones puras con el día del programa, Mi Radar y el momento local (`ZonedDateTime`: hora y día de la
     semana de la persona).
     - `horaPrevista`: en los días 1 a 7, la hora si está entre 8 y 19; desde el día 8, la hora si es una de las de ese día de
       la semana. Si no, vacío.
     - `puedeRegistrar`: en los días 0 a 7, siempre, como hoy; desde el día 8, solo encendido, en un día con Radar y entre
       03:00 y 23:59.
2. **`RadarService.registrar`**: calcula la hora local con `Clock` en la zona de la persona, rechaza con 409 (regla de
   negocio) si no `puedeRegistrar` y guarda `horaPrevista`. La regla de un registro por hora y el índice de V52 no
   cambian. SER pasa por el mismo servicio y hereda la regla.
3. **`MiRadarService`**: leer y configurar, solo la propia persona (`requireSelf`) y con el participante habilitado,
   como el resto del Radar.
4. **Persistencia**: `participantes_programa` es del módulo `users`, y la regla 01 prohíbe el SQL de otro módulo sobre
   esa tabla. Por eso `users` expone `users.api.HorasDeMiRadarPort` (con su `HorasDeMiRadarJdbcAdapter`) y `habits` lo
   envuelve con `MiRadarPersistenceAdapter` detrás de `LoadMiRadarPort` y `SaveMiRadarPort`. `NULL` significa apagado.
   `registros_radar.hora_prevista` va a la entidad y al mapper. *(Corregido 2026-10-29 al implementar: el plan decía un
   `MiRadarJdbcAdapter` en `habits`.)*
5. **Lecturas del acompañamiento**: la página del historial (`RadarHistoryPageResponse`) suma `miRadar` y cada
   registro suma `horaPrevista`, ambos opcionales.
   - El mentor sigue con su endpoint.
   - Líder, administración y alquimista usan `GET /api/v1/leadership/groups/{g}/people/{p}/radar`, con el mismo
     guardián que `.../week`.
   - Liderazgo lee el Radar por `habits.api.CodigoRenaserDePersonaFinder`.
6. **App**
   - Una función pura decide qué horas avisan: 8 a 19 en los días 1 a 7; desde el día 8, las elegidas si está
     encendido; si no, ninguna.
   - Se reutilizan los mismos identificadores `renaser.radar.slot.HH`, el mismo canal y el mismo `MINUTO_DEL_AVISO`.
     Desde el día 8 el aviso lleva `data.route = '/mi-radar'`.
   - Invitación: una notificación de fecha (`renaser.radar.invitacion`) para el día 8 a las 09:00, programada una vez
     por cuenta y cancelada si se enciende antes.
   - `MiRadarScreen` sigue el patrón de `EmergenciaScreen`: pantalla completa desde Yo, `CabeceraAdmin`,
     `useSystemBackHandler`, `InterruptorGrande`, `ChipDeOpcion` para las horas, el `CodigoRenaserModal` de siempre
     (que se puede cerrar) y `ConfirmacionEnLinea` al guardar.
   - `CodigoRenaserDelAlumno` recibe una función `cargar(cursor)`, así sirve igual a las tres fichas.

## Complexity Tracking

Sin desvíos que justificar.
