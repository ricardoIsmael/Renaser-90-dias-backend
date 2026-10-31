# Tasks: Mentoría del Alquimista solo con semáforo verde

Estado al 2026-10-30: **[x]** hecho y probado · **[ ]** pendiente. Alcance: backend; la app no cambia.

## Backend (`020-mentoria-semaforo-verde`)

- [x] T001 `ConsultarElegibilidadEventoPort`: `elegiblesEntre` por lote (con `default`) y Javadoc con el criterio
- [x] T002 `ElegibilidadPorSemaforoAdapter` (reemplaza a `ElegibilidadSegunAudienciaAdapter` y a su prueba)
- [x] T003 `AccesoEventoService.puedeAcceder`: audiencia primero, elegibilidad del aprendiz después
- [x] T004 `AudienciaDelEventoService.quienesPasan` y su uso en `destinatarios` e `incluye`
- [x] T005 `ElegibilidadDeLosAvisos` y `DespachoDeRecordatoriosService`: el aviso de quien dejó de ser elegible se cancela con
      `dejo_de_ser_elegible` y no se publica
- [x] T006 Pruebas unitarias: `ElegibilidadPorSemaforoAdapterTest` (cada color, sin semáforo, lote, otros tipos),
      `MentoriaConSemaforoVerdePorRolTest` (cada rol), `ElegibilidadDeLosAvisosTest`, `DespachoDeRecordatoriosServiceTest`
- [x] T007 `MentoriaConSemaforoVerdeIT` (Postgres real, reloj en la madrugada UTC): verde, amarillo, rojo, sin datos, sin
      semáforo, mentor y el personal; avisos; cambio de color; recordatorio ya generado. `EventosDeGrupoYMentoriaIT` ajustada
- [x] T008 `ArchitectureTest` (9 pruebas) y el resto de las pruebas de `calendar`
- [x] T009 Documentar: D-293 y «Corregido» en D-213 (`docs/MODULOS_A_AVANZAR.md`), `docs/MODULO_CALENDAR.md` §1.5, §1.6 y §6,
      `docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md`, Javadoc de `ReglasPorTipoEvento`
- [x] T010 `./mvnw clean verify` completo: surefire 7758 y failsafe 613, 0 fallos, BUILD SUCCESS (21 min 27 s)

## App (`020-mentoria-semaforo-verde`)

- [x] T020 Confirmar que no cambia: `src/features/eventos/` y «Próximo evento» de Hoy leen del servidor (sin textos nuevos)
- [x] T021 `AGENTS.md`: una entrada fechada

## E2E (emulador, base clonada, nunca producción)

- [x] T030 Base `renaser_mentoria020` (clon de `renaser_integ29`), backend local en el 8097; Mentoría del Alquimista y una Sesión
      especial para todos; un aprendiz en verde (100 %) y otro en amarillo (70 %) con `semaforo_dias` sembrado; un mentor (su propio
      semáforo en rojo)
- [x] T031 Verde: la ve en Hoy («Próximo evento»), en el detalle y en Comunidad → Eventos. Amarillo: no la ve en Hoy, en las tarjetas, en el
      calendario (31 de octubre «sin eventos») ni en «Mi agenda», y sigue viendo los demás eventos. Mentor: la ve aunque esté en rojo.
      Por API local, además: pasar a verde la muestra y dejar de estarlo la esconde (detalle 403). Capturas en
      `~/.config/renaser-e2e/capturas-notas/mentoria-020/`
- [x] T032 Flujos de Maestro en `.maestro/mentoria-semaforo/` (01 verde, 02 amarillo, 03 mentor): los tres pasan en el emulador

## Anotado, no hecho (fuera de alcance)

- **Primera semana y mínimo de días medidos:** si el dueño lo pide, es un cambio del adaptador (ver «Dudas» en la spec).
- **Cancelar desde el servidor la alarma local de un «Voy»:** exigiría notificación y cambio de app.
