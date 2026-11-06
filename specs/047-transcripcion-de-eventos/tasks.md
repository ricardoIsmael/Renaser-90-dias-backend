# Tareas: spec 047 (decisiones del dueño: P1-A, P2-B, P3-B, P4-B, P5-A)

Orden por dependencias. `[P]` = puede ir en paralelo. Cada tarea de código lleva su prueba con la marca `spec 047 FR-NNN`.

## Fase 0 · Antes de codificar
- [x] T001 Confirmar con el dueño P1–P5 y ajustar `plan.md` si alguna cambia.
- [x] T002 Revisar el último número de migración en `master` y en las ramas abiertas y reservar el siguiente.

## Fase 1 · Datos y catálogo
- [x] T010 Migración: `eventos_meet_transcripciones` con sus CHECK e índices; CHECK de acciones y columna `motivo` en `integracion_google_auditoria` (plan § Datos). FR-006, FR-007, FR-013.
- [x] T011 [P] Catálogo de Soporte TI: `eventos.meet.transcripcion`, hija de `eventos.meet`, apagada de fábrica. FR-001.
- [x] T012 [P] `meetings.space.readonly` en los permisos por defecto (`application.yaml`, `ConfiguracionDeGoogle`, `CuentaDeGoogle.PERMISO_LEER_REUNIONES`) y el estado «Falta el permiso de leer las reuniones» en `GET /events/meet/status`. FR-019.

## Fase 2 · Dominio y puertos
- [x] T020 Dominio `TranscripcionDeSesion` (estados y transiciones), `VentanaDeLaFecha`, `TextoDeTranscripcion` (armar, ordenar, tope, sin teléfonos, «Participante»). FR-003, FR-007, edge cases.
- [x] T021 Puertos `ReunionesDeMeetPort`, `LoadTranscripcionPort`, `SaveTranscripcionPort`.

## Fase 3 · Adaptadores
- [x] T030 `GoogleMeetReunionesAdapter`: conferencias por código y ventana, participantes, transcripts y entries paginados, grabaciones (`exportUri`); lectura tolerante; prueba con `MockRestServiceServer` (incluye 401, 403, 429, 5xx y páginas). FR-003, FR-004, FR-005, FR-020.
- [x] T031 `TranscripcionesJdbcAdapter` con cifrado AES-GCM de la 040; IT que comprueba que en la base no queda texto en claro. FR-006, FR-010, SC-004.

## Fase 4 · Servicios y relojes
- [x] T040 `BusquedaDeTranscripcionesService`: pendientes, `transcripcion_confirmada = false`, reintentos, 8 h, 29 días, `DUDA`, sin clave no busca. FR-002, FR-008, FR-009.
- [x] T041 `TranscripcionesScheduler` cada 5 min con bloqueo y respetando el interruptor. FR-001, FR-002.
- [x] T042 `RetencionDeTranscripcionesService` + reloj diario; borrado al borrar evento o fecha con constancia previa. FR-014, FR-015, SC-006.
- [x] T043 Auditoría de guardado, sin datos, borrado. FR-013.
- [x] T044 Prueba de que ningún log, error ni auditoría contiene el texto testigo. FR-010, SC-004.

## Fase 5 · Acceso
- [x] T050 `POST /api/v1/admin/events/{id}/meet/{fecha}/transcripcion/descarga` (ADMIN, ALCHEMIST; motivo; `no-store`; constancia `TRANSCRIPCION_LEIDA`). FR-011, US3.
- [x] T051 Estado por fecha para Administración, sin texto: `GET …/meet/transcripciones`. FR-012.
- [x] T052 Prueba sobre todos los endpoints de eventos y Administración con cada rol: ningún cuerpo trae el texto. SC-003.
- [x] T053 Borrado a pedido (ARCO) por ADMIN o ALCHEMIST con motivo: `POST …/{fecha}/borrar`. FR-016.

## Fase 6 · Regresión y documentación
- [x] T060 Pruebas de la 040 sin cambios con el interruptor apagado, sin permiso y sin clave. SC-005.
- [x] T061 `047` en `specs/trazabilidad.json`; decisión `D-3NN` en el registro de decisiones.
- [x] T062 `./mvnw verify` completo.

## Fase 7 · App (otra rama del frontend)
- [x] T070 Política de privacidad: grabación, transcripción, finalidad, acceso, 6 meses, cifrado, borrado. FR-018.
- [x] T071 (P4-B, con autorización de tabs) línea «Esta sesión se graba y se transcribe.» en el detalle del evento; prueba Jest con la marca.
- [ ] T072 (para después) Dibujar el estado de la transcripción por fecha en el detalle del evento de Administración; el servidor ya lo da.

## Fase 8 · Manual (equipo y dueño)
- [ ] T080 Google Cloud: sumar `meetings.space.readonly`; volver a conectar la cuenta.
- [ ] T081 SC-001 y SC-002 con una sala de prueba (sin datos de producción), con capturas.
- [ ] T082 Publicar la política y recién entonces encender `eventos.meet.transcripcion`.

**Nota de la implementación (06/11):** la constancia de quién sacó o borró una transcripción, con su motivo, va en `eventos_meet_transcripciones_accesos` (V184) en vez de una columna `motivo` en la auditoría de la 040; los endpoints quedaron bajo `/api/v1/calendar/events/{id}/meet/transcripciones`.
