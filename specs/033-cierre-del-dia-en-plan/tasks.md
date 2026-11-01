# Tasks: «Cierra tu día» pasa a Plan

**Input**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/ui.md`. Estado: **[x]** hecho y probado · **[ ]** pendiente.
Solo la app (rama `033-034-cierre-en-plan-e-interruptor`); el servidor no cambia. Decisión D-298. Commits por paso, en castellano y sin coautoría; sin emulador (máquina compartida).

## Phase 1: Foundational

- [x] T001 `features/cierre/utils/semanaDelCierre.ts` (puro): los días de la tarjeta (del Día 1 o el lunes a ayer; hoy aparte) y el texto de cada día; `ejeDeLasAcciones.ts`: `idDeLaAccion → eje` desde `RocaDiariaApi[]`; pruebas
- [x] T002 `HojaDelCierre`: prop opcional `ejeDe` (ícono de eje en cada pendiente), pie «Ver mañana en Plan» (cualquier día), sin tocar el guardado; `hojaDelCierre.test.ts` y `useHojaDelCierre.test.ts` **sin cambiar** salvo el texto del enlace

## Phase 2: User Story 1 — Cierro mi día desde Plan (P1) 🎯 MVP

**Prueba independiente**: abrir Plan, tocar «Cierra tu día», guardar, ver «Día cerrado»; en la ficha del mentor el día aparece «Cerrado».

- [x] T003 [US1] `TarjetaDelCierreEnPlan` (medallón `moon`/`checkCircle`, título, segunda línea, chevron; `Presionable`; verde al cerrar) y `CierreEnPlan` (lectura de hoy cuando Plan está a la vista, hoja, «ID original» del día; hecho como un componente que junta los hooks `useCierreDeHoy` y `useCierresAnteriores` de la 017, sin hook nuevo)
- [x] T004 [US1] `PlanDelEje` (ranura bajo «Acciones que mueven la meta») y `PlanScreen` (sin Mapa, bajo «Primero tu Mapa de Renacimiento»; `useAccionesDeHoy` para el eje; interruptor `plan`); Jest de `cierreEnPlan` (los casos 1 a 4 de quien trabaja de noche, el 409 y el cambio de día con la hoja abierta, sin Mapa, `plan` apagado, servidor anterior)

## Phase 3: User Story 2 — Training deja de tener «Cierra tu día» (P1)

- [x] T005 [US2] Training: quitar la fila de hoy, las de los días anteriores, `ListaDelDia.cierre`, `itemDelCierre`, `cierrePrendido` y `FilaDelCierre`; adaptar `cierreEnTraining.test.ts` y las pruebas de la lista (sin hueco)

## Phase 4: User Story 3 y 4 — Mañana y días anteriores (P2)

- [x] T006 [US3] «Ver mañana en Plan»: cierra la hoja y lleva el scroll de Plan a «Acciones que mueven la meta» (solo con Mapa activo)
- [x] T007 [US4] `CierresAnterioresDeLaSemana`: la semana en la misma tarjeta, un día se lee al tocarlo, «Cerrado»/«Sin cerrar», lo respondido de solo lectura, sin botón de cerrar; Jest

## Phase 5: User Story 5 — Mi acompañamiento ve lo mismo (P3)

- [x] T008 [US5] Las fichas («Cierres del día») no se tocan: sus pruebas siguen en verde (`cierresDelDia`, `cierresEnLaFicha*`)

## Phase 6: Polish

- [x] T009 `AGENTS.md` (entrada 2026-10-31 y «Corregido 2026-10-31» en la 017), Maestro `.maestro/cierre-en-plan/`, `tsc --noEmit` y Jest, `graphify update .`
- [x] T010 D-298 en `docs/MODULOS_A_AVANZAR.md` §8
