# Tasks: Mi Radar en Yo

**Input**: [plan.md](plan.md), [spec.md](spec.md), [data-model.md](data-model.md), [contracts/api.md](contracts/api.md)
**Rutas**: backend = `Renaser-90-dias-backend/.claude/worktrees/mi-radar`; app = `Renaser-90-dias-frontend-/.claude/worktrees/mi-radar`.

## Phase 1: Setup

- [ ] T001 Backend: `src/main/resources/db/migration/V119__mi_radar_desde_el_dia_8.sql`, con cabecera y las dos
  columnas con CHECK (data-model).
- [ ] T002 [P] App: enlazar `node_modules` del checkout principal en el worktree (o la receta del issue #47) para
  correr `tsc` y Jest.

## Phase 2: Foundational (backend, bloquea US1–US4)

- [ ] T003 [P] `habits/domain/model/radar/MiRadar.java` y su prueba unitaria `MiRadarTest` (1–12 horas, 3–23, sin
  repetir, ordenadas, apagado).
- [ ] T004 [P] `habits/domain/model/radar/HorarioDelRadar.java` y `HorarioDelRadarTest` (tabla del data-model, con
  días 0, 7, 8 y 90; horas 02, 03, 23 y 00; reloj en la madrugada UTC = día anterior en Lima).
- [ ] T005 `RegistroRadar` suma `horaPrevista` (nullable): entidad JPA, mapper y `RegistroRadarTest`.
- [ ] T006 Puertos `LoadMiRadarPort` / `SaveMiRadarPort` y `MiRadarJdbcAdapter` sobre
  `participantes_programa.horas_mi_radar`, con prueba de Testcontainers.

## Phase 3: US1 + US2 — encender, horarios y registrar (P1) 🎯 MVP

- [ ] T007 Backend: `ConsultarMiRadarUseCase`, `ConfigurarMiRadarUseCase` y `MiRadarService` (requireSelf +
  participante habilitado) con pruebas unitarias.
- [ ] T008 Backend: `RadarService.registrar` calcula la hora local en la zona de la persona, rechaza con 409 si no
  `puedeRegistrar` y guarda `horaPrevista`. Pruebas en `RadarServiceTest` (días 1–7 sin cambios, día 8 apagado,
  fuera de 03–23, hora elegida y hora libre).
- [ ] T009 Backend: `GET/PUT /api/v1/radar/mi-radar` en `RadarController`; `horaPrevista` en las respuestas; pruebas
  HTTP con 400, 403 por rol sin permiso y 403 por `SUSPENDED`.
- [ ] T010 [P] App: `MI_RADAR` en `configRadar.ts`; `utils/miRadar.ts` puro (validar horas, `horasDeAviso`,
  patrones, texto de la fila), con pruebas.
- [ ] T011 [P] App: `radarApi.ts` y `radarSchemas.ts` (obtener y guardar; `horaPrevista` y `miRadar` tolerantes; un
  404 = backend anterior), con pruebas de esquema.
- [ ] T012 App: `recordatoriosDeRadar.ts` usa `horasDeAviso(dia, miRadar)` (mismos ids, canal y minuto; cancela de 03
  a 23; `data.route = '/mi-radar'` desde el día 8) + invitación del día 8; `RadarContext` le pasa Mi Radar. Pruebas:
  los días 1–7 siguen programando 08–19 igual que hoy.
- [ ] T013 App: `hooks/useMiRadar.ts` y `screens/MiRadarScreen.tsx` (interruptor, registrar, hoy, horarios,
  historial, patrones, texto de quién lo ve; back del sistema).
- [ ] T014 App: fila «Mi Radar» en `YoScreen.tsx` bajo «Tuve una emergencia», solo desde el día 8 y si el backend
  la sabe; ruta `/mi-radar` en `abrirAviso.ts` → Yo con `abrirMiRadar`.

## Phase 4: US3 — historial y patrones (P2)

- [ ] T015 App: historial por día con «Ver más» (cursor) y «Prevista HH:00 · respondida HH:MM»; patrones desde 3
  registros (dentro de T013 si cabe; si no, aquí).

## Phase 5: US4 — el acompañamiento lo ve (P2)

- [ ] T016 Backend: la página del historial suma `miRadar` (self y mentor).
- [ ] T017 Backend: `habits.api.CodigoRenaserDePersonaFinder` + `leadership` `RadarDePersonaService` (con
  `AccesoDeLiderazgo`) + `GET /api/v1/leadership/groups/{g}/people/{p}/radar` con pruebas HTTP (200 para
  MENTOR_LEAD/ADMIN/ALCHEMIST de ese grupo; 403 para MENTOR, TRAINEE, IT_SUPPORT, SUSPENDED y persona de otro
  grupo). `ArchitectureTest` en verde.
- [ ] T018 App: `CodigoRenaserDelAlumno` recibe `cargar(cursor)` y muestra el estado de Mi Radar y la hora prevista;
  se usa en `AlumnoScreen` (mentor), `FichaAprendizScreen` (admin y alquimista, con `cellId`) y
  `PersonaDelLiderScreen` (líder).

## Phase 6: US5 y cierre

- [ ] T019 App: invitación del día 8 (si no entró en T012), una vez por cuenta y cancelada al encender.
- [ ] T020 Docs backend: D-nn en `docs/MODULOS_A_AVANZAR.md` §8 y la sección del Radar en `docs/MODULO_HABITS.md`.
- [ ] T021 Docs app: entrada en `AGENTS.md` (o `docs/producto/` si el harness ya está en master) con «lo que cambia y
  nada más».
- [ ] T022 Verificación: `tsc` 0 errores y Jest en verde (app); unitarias del backend. `./mvnw clean verify`,
  Maestro y mobile-mcp (quickstart) cuando la máquina esté libre.
- [ ] T023 `graphify update .` en los dos worktrees; commits por funcionalidad (uno por repo), sin coautoría.

## Dependencies

- T001 → T005, T006. T003 y T004 → T007 y T008. T009 depende de T007 y T008. T016 y T017 dependen de T005 y T006.
- App: T010 y T011 → T012, T013 → T014, T018. La app se puede escribir en paralelo al backend contra el contrato.
