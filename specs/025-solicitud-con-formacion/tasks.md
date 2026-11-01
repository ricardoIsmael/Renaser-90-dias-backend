# Tareas: aceptar una solicitud con formación (spec 025)

## Backend
- [x] T001 V140: foto de lo elegido en `solicitudes_cuenta` (+ backfill de las aceptadas a «Escuela, sin diagnóstico»)
- [x] T002 `users.api.FichaAlAceptar` (la implementa `onboarding`, spec 026)
- [x] T003 Dominio: `PedidoDeIngreso`, `EleccionDeIngreso`, `ReglasDeIngreso` + pruebas
- [x] T004 `AccountRequest` guarda la foto; `verificarQuePuedeDecidir`
- [x] T005 `IngresoPorAceptacion` (participación, formaciones del personal, ficha) y `approve` reordenado (rol → formación → solicitud)
- [x] T006 Candado de la fila al decidir (FR-018)
- [x] T007 `GET /account-requests/acceptance-options`; `approve` con `formations`/`durationDays`/`withDiagnosis`
- [x] T008 Listado: `reviewedByName`, `acceptance` y filtro `formation`
- [x] T009 V141 + `POST /admin/trainees/{id}/duracion` (025-2 A) + `lastDayAdjustment` con la duración
- [x] T009b V142: el rol personalizado (id y nombre de ese día) en la foto de lo aceptado
- [x] T009c `GET /admin/trainees/{id}` suma `acceptance`, `acceptedByName` y `acceptedAt` (US4, escenario 1)
- [x] T010 `AceptarConFormacionIT` (Postgres): Diplomado 1 año, recepción por formación, personal en dos formaciones, Administración, app anterior, pedidos inválidos, atomicidad, concurrencia, filtro, opciones, 403, cambio de duración
- [x] T011 `FichaAlAceptarImplementadaTest` (rompe al integrar la 026 si nadie implementa la interfaz)
- [x] T012 D-302 en `docs/MODULOS_A_AVANZAR.md`

## App
- [x] T020 API y esquemas tolerantes (`acceptance`, opciones, filtro)
- [x] T021 Utilidades puras: borrador, reglas por rol, frase, aceptar varias, «la misma que la última»
- [x] T022 `AceptarSolicitudSheet` (bloques Formación/Rol/Ficha + resumen) y `SolicitudesAdminScreen` (vistas, filtro, selección múltiple)
- [x] T023 `CambiarDuracionSheet` en la ficha de la persona
- [x] T024 Jest (utilidades, API, cambio de duración)
- [x] T025 Entrada en `AGENTS.md`; flujos de Maestro en `.maestro/solicitud-con-formacion/`

## Fuera de esta entrega (lo dice el reporte)
- La marca «con diagnóstico» de la persona y corregir el tipo de ficha: spec 026.
