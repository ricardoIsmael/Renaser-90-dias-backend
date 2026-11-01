# Tasks: Hábitos de todos (spec 029)

Estado al 2026-10-31. `[x]` = hecho y probado.

## Backend
- [x] T001 Contrato público `habits.api.CumplimientoDelDiaFinder` + puerto + adaptador JDBC (una consulta, filtro de formación)
- [x] T002 Dominio puro en `leadership`: `EstadoEnElDia`, `ResumenDeUnHabito` (orden), `VentanaDeLosHabitos`
- [x] T003 `PersonasDelPrograma.alumnos(alcance, ahora)` (en lote) y `HabitosDeTodosService` (resumen y personas de un hábito)
- [x] T004 `HabitosDeTodosController` y respuestas (`/leadership/habits`, `/leadership/habits/{id}/people`)
- [x] T005 Pruebas: dominio, servicio (permisos, cifras, filtros, ventana, madrugada UTC, formación, 404) e IT de punta a punta
- [x] T006 Documentación: D-305, `MAPA_ENDPOINTS`

## App
- [ ] T020 Fila «Hábitos de todos» en Yo → «Mis mentores» (solo si el servidor la ofrece), pantalla del día y lista de un hábito, abrir ficha y evidencia
- [ ] T021 Pruebas Jest, Maestro (`.maestro/habitos-de-todos/`) y `AGENTS.md`
