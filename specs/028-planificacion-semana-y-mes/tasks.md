# Tasks: Planificación de semana y mes

**Input**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Rutas**: `back:` = `Renaser-90-dias-backend/.claude/worktrees/impl-028/` · `app:` = `Renaser-90-dias-frontend-/.claude/worktrees/impl-028/` (frentes B y C en `impl-028-alarmas` e `impl-028-mes`, unidos a la rama de la app).

**Reglas**: un commit por funcionalidad, en español, imperativo y sin coautoría. Máquina compartida: Maven con `-Xmx1500m`, sin `verify` completo, y todo comando pesado bajo `flock /tmp/renaser-pesado.lock`. Sin emulador. Sin push.

## Phase 1: Setup

- [x] T001 Confirmar numeración reservada: migraciones V130 y V131, decisión D-296 (la última era V124 / D-294).
- [x] T002 Poner al día la spec con las decisiones del dueño y quitar los tres `NEEDS CLARIFICATION`.
- [x] T003 Plan, investigación, modelo de datos y contratos.

## Phase 2: Backend (base de todo)

- [x] T004 `back:` V130 (antelaciones y «Cómo suena» por fecha) y V131 (aviso que hereda; fila con hora, aviso o alarma).
- [x] T005 [P] Dominio con pruebas sin Spring: `SemanaCalendaria`, `FechaPropia`, `CambioDeFecha`, `PlanDeLaSemana`, `RepeticionDeSemana` (40 pruebas).
- [x] T006 Adaptador: entidad y repositorio ampliados; la capa de fecha por campo en `resolverPorCapas`; `fechasPropiasEntre`, `guardarFechasPropias`, `borrarFechasPropias`; la ruta de antes conserva «Cómo suena».
- [x] T007 Servicios `PlanDeLaSemanaService` y `RepeticionDeSemanaService`; `CatalogoAplicable.aplicablesPara`; `dateOverrides` en `ConsultaPreferenciasHorarioService`.
- [x] T008 Endpoints: `/{id}/week` (GET, PUT, DELETE), `/week-repeat` (GET, POST), `/weeks/{lunes}` (DELETE); controladores con 403 para la cuenta suspendida.
- [x] T009 Pruebas de servicios, controladores, adaptador (Testcontainers) y `PlanificacionDeLaSemanaIT`; `ArchitectureTest` y las pruebas de declaración de permiso y funcionalidad.
- [x] T010 D-296 en `docs/MODULOS_A_AVANZAR.md`, `docs/MODULO_HABITS.md` §12.5 y `docs/MAPA_ENDPOINTS.md`.

## Phase 3: App (tres frentes en paralelo, con el contrato ya fijado)

- [x] T011 `app:` tipos, esquemas y cliente HTTP de la semana, la oferta y deshacer (commit de contrato).
- [x] T012 [P] Frente A: editor «Solo esta semana» (`PlanificarDimensionModal`), oferta de repetir y deshacer en «Tus prácticas», «Ahora no» por cuenta, flujos de Maestro `solo-esta-semana` y `repetir-la-semana`.
- [x] T013 [P] Frente B: alarmas del teléfono con fechas propias (`planDeAlarmas`, puente del #33, repeticiones de la spec 009, `programar` y la puesta al día).
- [x] T014 [P] Frente C: «Planificar mi mes» en Plan (`PlanDelMesModal`, `mesDelPrograma`) y flujo de Maestro `planificar-el-mes`.

## Phase 4: Cierre

- [x] T015 Unir los tres frentes en la rama de la app, resolver conflictos de `.maestro/README.md` y correr `tsc` y Jest. Comprobado el 2026-10-31 sobre el árbol final: `tsc --noEmit` sin errores y Jest completo de la app, 563 suites y 6161 pruebas en verde. Las ramas ayudantes `028-alarmas` y `028-mes` ya estaban dentro (cherry-pick; fusionarlas no cambia el árbol) y se borraron con sus worktrees.
- [x] T016 Entrada fechada 2026-10-31 en `AGENTS.md` de la app (solo suma).
- [x] T017 `graphify update .` en los dos repos (hecho el 2026-10-31 después de la integración final; `graphify-out/` no se versiona).
- [x] T019 `./mvnw -o verify` completo del backend sobre esta rama, el 2026-10-31 (sin producción, base de Testcontainers): BUILD SUCCESS en 12 min; 7942 pruebas de unidad y 636 de integración, 0 fallos, 0 errores, 0 omitidas (incluye `ArchitectureTest`, las pruebas de declaración de permiso y de funcionalidad y las de las V130 y V131).
- [ ] T018 (pendiente del dueño / siguiente turno) E2E en el emulador y Maestro de los tres flujos; verificar con `dumpsys alarm` el SC-005. No se hizo: la regla de esta entrega fue no usar el emulador.
