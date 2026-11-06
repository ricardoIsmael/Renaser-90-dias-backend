# Implementation Plan (alto nivel): Eventos para una audiencia elegida

**Branch**: `044-eventos-por-audiencia` (backend y app) | **Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md) | **Contrato**: [contracts/api.md](./contracts/api.md)

> **Corregido 2026-11-06 (implementado, D-326).** El dueño eligió las opciones recomendadas salvo P5 (prendido para todos) y P7 (el líder sí
> edita los eventos de sus mentores). Las migraciones son **V182** (el valor `SELECCION`) y **V183** (`reglas_audiencia_evento` e
> `invitados_evento`; la primera se llamaba `reglas_audiencia_evento` en este plan). Se renumeraron de V172 y V173 a V182 y V183 porque las
> specs 045 y 046 usan V180 y V181 y producción corre Flyway sin `out-of-order`. Los endpoints de gestión no cambiaron de clave: quedan con
> `@Funcionalidad(value = "eventos.gestion", laResuelveElCasoDeUso = true)` y el servicio exige el interruptor según el rol. El líder en el
> chat de soporte (FR-037 a FR-042) se sumó con `StaffDelSoporte`.

## Summary

Se agrega un sexto tipo de audiencia, `SELECCION` (wire `SELECTION`), que guarda la audiencia como **partes** (persona, grupo, mis alumnos,
mentores, color, personal). Las partes que «se leen en cada momento» (grupo, mis alumnos, mentores) se resuelven al consultar, como hoy
`CELULA`; las que son **lista fija** (personas y lo que entró por color, según P1) se guardan ya resueltas en `invitados_evento`. Todo lo que
hoy pregunta la audiencia —`ResolverAudiencia.puedeVer`, `AudienciaDelEventoService.destinatarios/incluye`, la lista de eventos, la asistencia
y el despacho de la 041— pasa por un solo servicio nuevo, `AudienciaElegidaService`, para que no haya dos copias de la regla.

Crear deja de ser solo de ADMIN/ALCHEMIST: `requireRolCreador` pasa a una política `QuienPuedeConvocar` (rol + alcance), y el interruptor
nuevo `eventos.convocar` decide si mentor, guía y líder ven el formulario.

## Technical Context

**Backend:** Java 25 / Spring Boot, módulo `calendar` (hexagonal). Lee de otros módulos solo por su `api`: `points.api.SemaforoFinder`
(colores en lote), `users.api.AlcanceDeFormacionFinder` (formación), los grupos y su mentor (puerto nuevo `ConsultarAlumnosDelMentorPort`,
adaptador sobre las asignaciones vigentes que ya usa `ConsultarMiembrosCelulaPort`), y `notifications` lee la audiencia por un
`calendar.api` nuevo (`AudienciaDelEventoFinder`). Sin dependencias nuevas.
**App:** Expo SDK 57. Sin dependencias ni código nativo nuevos: solo JavaScript. Hace falta un APK nuevo.
**Testing:** unitarias del dominio (`AudienciaElegida`, partes, unión sin duplicados), del servicio (alcance por rol, 403 con ids, vacía,
foto del color), `@WebMvcTest` del contrato, IT con Testcontainers (lista, detalle 404, recordatorios solo a la audiencia, cambio de audiencia
cancela con `fuera_de_la_audiencia`, app anterior que edita sin partes), y una prueba que cuenta las lecturas del semáforo (SC-007). App:
Jest del paso «¿Para quién es?», del conteo y de los permisos; Maestro escrito.

## Constitution Check (reglas del repo)

| Regla | Cómo se cumple |
|---|---|
| Hexagonal (01) | Dominio puro `AudienciaElegida` + `ParteDeAudiencia`; casos de uso `OpcionesDeAudienciaUseCase`, `BuscarPersonasParaConvocarUseCase`, `VistaPreviaDeAudienciaUseCase`; puertos `out` para alumnos del mentor, personas del líder y semáforo. |
| Tiempo y zonas (02) | El color es el del semáforo vigente en la zona de cada persona (`SemaforoFinder`); la foto guarda `resuelto_en` (instante). Nada se calcula con la zona del servidor. |
| Pruebas (03) | Cada FR/SC con su marca `spec 044 FR-NNN`; la spec entra a `specs/trazabilidad.json` en el mismo cambio que pone las marcas (no antes). |
| Migraciones (04) | Dos migraciones (el `ALTER TYPE … ADD VALUE` va solo, sin usar el valor en la misma transacción) y cabecera que justifica cada tabla. |
| Documentación (05) | D-NNN en `docs/MODULOS_A_AVANZAR.md` §8 al implementar; D-186 marcado «Corregido» con el motivo; `AGENTS.md` de la app con su entrada. |

## Modelo de datos (resumen; migraciones a crear al implementar)

1. **Migración A** — `ALTER TYPE renaser.tipo_audiencia ADD VALUE IF NOT EXISTS 'SELECCION';` (sola).
2. **Migración B**:
   - `renaser.reglas_audiencia_evento`
     | columna | tipo | nota |
     |---|---|---|
     | `id` | uuid PK | |
     | `evento_id` | uuid NOT NULL FK `eventos(id)` ON DELETE CASCADE | |
     | `tipo_parte` | text NOT NULL CHECK in (`PERSONAS`,`GRUPO`,`MIS_ALUMNOS`,`MENTORES`,`COLOR`,`PERSONAL`) | |
     | `grupo_id` | uuid NULL FK `celulas(id)` ON DELETE CASCADE | solo `GRUPO` |
     | `color` | text NULL CHECK in (`VERDE`,`AMARILLO`,`ROJO`) | solo `COLOR` |
     | `resuelto_en` | timestamptz NULL | instante de la foto (`COLOR`, según P1) |
     | `creado_en` | timestamptz NOT NULL default now() | |
     CHECK de coherencia (grupo solo con `GRUPO`, color solo con `COLOR`); único `(evento_id, tipo_parte, grupo_id, color)` con `NULLS NOT DISTINCT`.
   - `renaser.invitados_evento` — la lista fija.
     | columna | tipo | nota |
     |---|---|---|
     | `evento_id` | uuid FK `eventos(id)` ON DELETE CASCADE | |
     | `participante_id` | uuid FK `usuarios(id)` ON DELETE CASCADE | borrar la cuenta lo quita |
     | `origen` | text CHECK in (`PERSONA`,`COLOR`,`PERSONAL`) | por qué está (solo para el creador) |
     | `creado_en` | timestamptz | |
     PK `(evento_id, participante_id, origen)`; índice `(participante_id)` para listar los eventos de una persona sin recorrer todos.
   - Cola de recordatorios: el motivo `fuera_de_la_audiencia` (texto, junto a `dejo_de_ser_elegible`); si el motivo es un CHECK o un enum, se
     amplía en esta migración.
   - El borrado de cuenta (`BorradoDeCuentaEnLosModulos`) suma `invitados_evento` a su lista de tablas (la FK en cascada ya lo cubre; la prueba
     de claves compartidas lo exige igual).
3. **Catálogo de Soporte TI** (código, `EntradasDelCatalogo`): `eventos.convocar`, hija de `eventos`, roles MENTOR, TRAINEE_GUIDE, MENTOR_LEAD,
   ADMIN, ALCHEMIST, IT_SUPPORT; nace como dice P5 (recomendada: prendida para ADMIN/ALCHEMIST, apagada para el resto). `eventos.meet` suma
   MENTOR, TRAINEE_GUIDE y MENTOR_LEAD (FR-026). `eventos.asistencia` suma MENTOR y TRAINEE_GUIDE, acotado en el servicio a sus propios eventos
   (FR-027).

La tabla `eventos` **no cambia** (el CHECK `audiencia_coherente` no mira `SELECCION`). Ninguna fila existente se toca (FR-031).

## Lógica del servidor

1. **Crear / editar** (`EventoService`):
   1. `QuienPuedeConvocar.puede(actor)`: ADMIN/ALCHEMIST con `eventos.gestion`; MENTOR/TRAINEE_GUIDE/MENTOR_LEAD con `eventos.convocar`.
      Como `@Funcionalidad` admite una clave por endpoint, `POST/PUT/DELETE /calendar/events` quedan con la madre `eventos` y el servicio
      consulta el interruptor que corresponde al rol con `InterruptoresFinder` (verificar al implementar que `EndpointAuthorizationDeclarationTest`
      lo acepta; si no, un endpoint aparte `/calendar/events/convocados` con `eventos.convocar`).
   2. Mentor/guía/líder: tipo solo `SESION_ESPECIAL` o `ESPONTANEO` (FR-007), audiencia solo `SELECTION` (FR-002).
   3. `AlcanceDeConvocatoria.de(actor)` arma los conjuntos permitidos (alumnos del mentor; personas de las formaciones del líder; personal) y
      valida cada id: rechazados → 403 `FUERA_DE_TU_ALCANCE` con `ids` (FR-004).
   4. Resolver las listas fijas: personas tal cual; colores con **una** lectura `SemaforoFinder.vigenteDe(candidatos)` (FR-009, FR-012).
   5. Si la unión queda vacía → 400 `AUDIENCIA_VACIA` (FR-005).
   6. Guardar evento + partes + invitados en una transacción. Al editar: si el pedido es `SELECTION` sin `audience`, conservar (FR-030); si
      trae `recalcular: true`, foto nueva de los colores.
   7. Diferencia entre audiencia vieja y nueva → `AudienciaDelEventoCambio` (evento interno): cancelar pendientes de quien salió
      (`fuera_de_la_audiencia`), generar los de quien entró, aviso de evento nuevo a quien entró si el evento lo pide (FR-022).
   8. Auditoría: una fila «leyó el semáforo de N personas para convocar» cuando hubo partes `COLOR` (FR-019).
2. **Ver**: `ResolverAudiencia.puedeVer` suma el caso `SELECCION` → `perteneceAlDestino`, que calcula `AudienciaElegidaService.incluye(evento,
   persona)` (creador ∪ invitados ∪ partes dinámicas). La lista (`EventoService.listar`) pre-carga en lote los eventos `SELECCION` del rango y
   sus invitados para el visor (una consulta por `participante_id`), sin N+1. Detalle de un evento que no se ve → 404 (FR-013).
3. **Avisos** (`AudienciaDelEventoService.audiencia`): `SELECCION` → `AudienciaElegidaService.destinatarios(evento)` (unión, sin duplicados,
   dentro de la formación salvo `PERSONAL`) + creador. El despacho vuelve a preguntar `incluye` (FR-023).
4. **041** (`notifications`): `ReglasDelMensajeDeEvento` pregunta por `calendar.api.AudienciaDelEventoFinder.esElegida(eventoId)`; si lo es,
   aplica la regla de P2 (todos menos «No voy») y P3 (todos los roles invitados) en vez de `EVENTOS_MENSAJES_AUDIENCIA` y
   `EVENTOS_MENSAJES_ROLES`. A quien no tiene chat de soporte (personal) no se le escribe en el chat.
5. **Respuesta del evento**: `audience` solo para el creador, ADMIN y ALCHEMIST (FR-016, FR-018).

## La app (resumen)

- `utils/permisosDeEventos.ts` deja la lista de roles: el botón «Crear evento» sale si `GET …/audience/options` dice `canCreate`.
- `FormularioDelEvento`: paso nuevo **«¿Para quién es?»** (`ParaQuienEs.tsx`): chips de las partes que manda el servidor, sub-hojas
  `ElegirPersonas` (`HojaDesdeAbajo` grande con buscador, filas de 56 con foto o iniciales y casilla), `ElegirColores` (tres filas con ícono y
  palabra), `ElegirPersonal`; barra fija «Llega a N personas» (`useVistaPreviaDeAudiencia`, 300 ms, turno por lectura como el buscador del
  líder). La línea «Tus invitados no ven por qué los elegiste».
- `DetalleDelEvento`: sección «Para quién es» con las partes y cifras, solo si `audience` viene.
- `HojaQuienRespondio` y «Pasar lista» abren para el creador mentor.
- `eventosSchemas`: `audienceType` sigue siendo texto abierto; `audience` opcional y tolerante.
- Movimiento: ninguno nuevo (chips instantáneos con `Presionable` y `tacto.seleccion()`).
- `AGENTS.md`: entrada «Cambio por pedido del dueño — Comunidad → Eventos: eventos para una audiencia elegida (spec 044)».

## Orden sugerido

1. Backend: dominio y migraciones → crear/validar → ver/listar → avisos → 041 → endpoints de opciones, personas y vista previa → interruptor.
2. App: permisos por servidor → paso «¿Para quién es?» → detalle → asistencia del creador.
3. E2E local (nunca producción): líder con «Rojo», mentor con «Mis alumnos», líder con el Alquimista, app anterior editando.

## Riesgos

- **Costo del WhatsApp** (041): más creadores → más mensajes. La audiencia elegida suele ser chica, pero un admin que elige «Verde» en toda la
  Escuela puede mandar cientos. El resumen de la 041 ya los cuenta.
- **Privacidad**: el color de un aprendiz es un dato sensible; por eso la audiencia no viaja al invitado (FR-016) y queda constancia (FR-019).
- **Rendimiento de la lista**: hoy `candidatosParaVisor` trae todos los eventos del rango y filtra en memoria; con muchos eventos privados conviene
  filtrar `SELECCION` en SQL por `invitados_evento`/creador. Medir al implementar.
