# Feature Specification: Mentoría del Alquimista solo con semáforo verde

**Feature Branch**: `020-mentoria-semaforo-verde` (backend; la app no cambia, ver «Alcance»)
**Created**: 2026-10-30
**Status**: Implementada y probada en el emulador (ver `tasks.md`)
**Input**: Decisión del dueño (2026-10-30), textual: «en eventos, si eres aprendiz solo te saldrá la mentoría con
alquimista si tienes semáforo verde». Cierra la pregunta abierta de **D-213** (E-362): ¿alcanza la audiencia que el
evento declara o hace falta un criterio extra para el aprendiz? Es el criterio (b) que D-213 ya proponía.

El puerto `ConsultarElegibilidadEventoPort` y la marca `ReglasPorTipoEvento.REQUIERE_ELEGIBILIDAD = true` para
`MENTORIA_ALQUIMISTA` ya existen: el servidor ya le pregunta al puerto en la lista, el detalle, los avisos y la hoja de
asistencia. Lo único que falta es el adaptador que responda con el semáforo (hoy `ElegibilidadSegunAudienciaAdapter` dice
siempre «sí»).

## Clarifications

### Decisión del dueño (2026-10-30)

- **Aprendiz:** la Mentoría del Alquimista solo le sale si su **semáforo vigente** (D-168, el mismo que Hoy le muestra
  en `/home` → `semaforo`) está en **verde**.
- **Personal** (guía de aprendiz, mentor, líder de mentores, administrador, alquimista y Soporte TI): **no cambia**. Ve lo
  que su audiencia dice.
- **Los demás tipos de evento** (Espontáneo, Semana de Manifestación, Sesión especial): **no cambian**.

### Lo que decidió quien implementa (a revisar por el dueño; nada nuevo para el usuario final)

1. **«Verde» es el color del semáforo vigente, y nada más.** Se lee con `points.api.SemaforoFinder.vigenteDe` (los últimos
   7 días cerrados en la zona de la persona), la misma lectura que alimenta `/home`. No se calcula ningún color en
   `calendar` ni hay tabla nueva. Amarillo (60–79,9), rojo (< 60) y «sin datos» **no** la ven (nunca verde por falta de
   datos, D-128); quien no tiene semáforo (no activó su programa) tampoco: no se inventa un verde.
2. **Los días 1 a 7 no son un caso aparte.** El Día 1 el semáforo es «sin datos» (ayer fue antes del programa), así que
   no la ve. Desde el Día 2 el semáforo se calcula con los días ya cerrados y **sigue al color**: un aprendiz del Día 4
   con todo cumplido está en verde y la ve. *Si el dueño quiere que la primera semana nunca la vea, o exigir un mínimo
   de días medidos, es otra regla y hay que confirmarla* (ver «Dudas»).
3. **«En pausa»:** solo el personal con programa propio puede pausar el semáforo (D-168); un aprendiz no. Los días con la
   cuenta suspendida no se miden (D-209). Una ventana donde ningún día se midió es «sin datos»: tampoco la ve.
4. **Se evalúa en el momento de cada consulta, sin recordar nada.** No se guarda «era verde» ni «dejó de serlo». Pasar a
   verde la muestra en la siguiente lectura; dejar de estarlo la esconde.
5. **Un recordatorio ya generado se vuelve a evaluar al enviarlo.** Se genera cuando la persona está en verde (el
   servidor genera con la misma audiencia filtrada); a la hora de enviarlo se pregunta otra vez y, si ya no está en
   verde, el aviso **se cancela con el motivo `dejo_de_ser_elegible`** y no se publica. Ese motivo ya estaba en la cola
   (`RecordatorioEvento.MOTIVO_NO_ELEGIBLE`, del repo viejo) y ningún código lo escribía: se reutiliza el mismo camino
   que el evento cancelado (`cancelarPorIds`). Si vuelve a verde **antes** de la hora de su aviso, el aviso sale; un aviso
   ya cancelado no se resucita.
6. **Su «Voy» no se borra.** Si el servidor guardó la respuesta, se conserva. Mientras no sea elegible no puede cambiarla
   (el servidor responde 403 como a cualquier evento al que no tiene acceso).
7. **La hoja «Quién respondió» y «Pasar lista» (D-256)** usan la misma audiencia que los avisos: un aprendiz que no es
   elegible y no respondió no aparece; el que respondió «Voy» antes de dejar de ser verde **sigue apareciendo** (quien
   pasa lista ve a quien dijo que iba).
8. **Rendimiento:** la audiencia pregunta la elegibilidad **en lote** (una lectura del semáforo para todos los aprendices
   del evento) y no una por persona. El acceso a un solo evento lee el semáforo de esa persona, y solo si la audiencia
   ya la deja pasar.

### Alcance

Backend. **La app no cambia:** el filtro es 100 % del servidor. Lo comprobé en `src/features/eventos/` y en Hoy
(«Próximo evento»): la lista, el calendario, «Mi agenda» y la tarjeta salen de `GET /calendar/events` y de
`/home → proximoEvento`; el detalle de un evento al que ya no se tiene acceso muestra el aviso que ya existía («Este
evento ya no está disponible. Puede que lo hayan cancelado.»); la alarma local de un «Voy» se quita sola cuando el evento
ya no viene en la lista (`planDeSincronizacion` → `desaparecio`), que la app lee al abrirse (`sincronizarAlAbrir`) y al
entrar a Eventos. **Sin textos nuevos y sin APK nuevo.** Los tabs no se tocan.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El aprendiz en verde ve la mentoría (Priority: P1)

**Acceptance Scenarios**:

1. **Given** un aprendiz con semáforo verde y una Mentoría del Alquimista para todos, **When** abre Comunidad → Eventos,
   **Then** la ve en la lista y en el calendario, en «Mi agenda» y como «Próximo evento» de Hoy, abre su detalle y puede
   responder «Voy».
2. **Given** ese aprendiz, **When** se acerca la hora, **Then** recibe sus recordatorios (y el aviso de «evento nuevo»
   si el evento se guardó con «Avisar a todos al guardarlo»).

### User Story 2 - El aprendiz sin verde no la ve (Priority: P1)

**Acceptance Scenarios**:

1. **Given** un aprendiz con amarillo, rojo, sin datos, sin semáforo (no activó su programa) o en el Día 1, **When** abre
   la lista, el calendario, «Mi agenda» o Hoy, **Then** la mentoría no está en ninguna.
2. **Given** ese aprendiz, **When** pide el detalle (por ejemplo, desde un aviso viejo), **Then** el servidor responde 403
   «No tienes acceso a este evento», igual que a cualquier evento que no es para él.
3. **Given** ese aprendiz, **When** se acerca la hora, **Then** no se le genera ni se le envía ningún aviso ni recordatorio de
   esa mentoría, ni el de «evento nuevo».

### User Story 3 - Si el color cambia, manda el de ahora (Priority: P1)

**Acceptance Scenarios**:

1. **Given** un aprendiz en amarillo que pasa a verde, **When** vuelve a consultar, **Then** la ve y se le generan sus
   recordatorios que aún no vencieron.
2. **Given** un aprendiz en verde que dijo «Voy» y deja de estar en verde, **When** consulta, **Then** ya no la ve ni puede
   cambiar su respuesta, **And** su «Voy» sigue guardado, **And** el recordatorio que ya estaba en la cola se cancela al
   llegar su hora con `dejo_de_ser_elegible` y no se envía.
3. **Given** ese aprendiz que vuelve a verde antes de la hora del recordatorio, **When** llega la hora, **Then** el
   recordatorio sale.

### User Story 4 - El personal y los demás eventos no cambian (Priority: P1)

**Acceptance Scenarios**:

1. **Given** una guía, un mentor, un líder, un administrador, una alquimista o Soporte TI, **When** abren la mentoría,
   **Then** la ven con o sin semáforo, como hasta hoy.
2. **Given** un aprendiz en amarillo, **When** abre una Sesión especial o un evento espontáneo para todos, **Then** los ve.

### Edge Cases

- **Dos aprendices en el mismo lote** (uno verde, uno amarillo): la audiencia los separa con una sola lectura del semáforo.
- **Reloj en la madrugada UTC:** el «ayer» del semáforo es el de Lima, no el de UTC (lo resuelve `points` con el `Clock`).
- **Un evento recurrente** se evalúa una vez por consulta, no por ocurrencia.
- **La alarma local de la app** (quien dijo «Voy» desde el teléfono) se quita en la siguiente lectura de la lista; si la
  app no se abre antes de la hora, esa alarma local suena (el servidor ya no envía la suya). *Límite aceptado,
  documentado en el plan.*

## Requirements *(mandatory)*

- **FR-001**: Para un aprendiz, `MENTORIA_ALQUIMISTA` MUST ser elegible si y solo si su semáforo vigente es VERDE.
- **FR-002**: El criterio MUST leerse por `points.api.SemaforoFinder` (sin tabla nueva ni cálculo del color en `calendar`).
- **FR-003**: Lista, calendario, «Mi agenda», «Próximo evento», detalle y «Voy» MUST pasar por el mismo acceso
  (`AccesoEventoService.puedeAcceder`); un aprendiz no elegible recibe 403 en el detalle y no ve el evento en ninguna lista.
- **FR-004**: La audiencia de los avisos (recordatorios, aviso de «evento nuevo» y hoja «Quién respondió») MUST filtrar por
  la misma elegibilidad, en lote.
- **FR-005**: Un recordatorio ya generado MUST evaluarse al despacharlo y cancelarse con `dejo_de_ser_elegible`, sin
  publicarse, si su destinatario ya no es elegible.
- **FR-006**: Una respuesta «Voy» guardada MUST conservarse al dejar de ser elegible.
- **FR-007**: El personal y los demás tipos de evento MUST NOT cambiar.
- **FR-008**: MUST NOT haber migración, tabla, endpoint ni campo nuevo; MUST NOT cambiar la app ni sus textos.

## Success Criteria

- **SC-001**: El adaptador responde `true` solo con VERDE: pruebas por cada color y para quien no tiene semáforo.
- **SC-002**: El personal (los seis roles) pasa sin que el puerto se le pregunte; un aprendiz en amarillo ve un evento de
  otro tipo.
- **SC-003**: Con dos aprendices (verde y amarillo) y un mentor, sobre Postgres real, el verde y el mentor la ven y reciben
  sus avisos, y el amarillo no (`MentoriaConSemaforoVerdeIT`).
- **SC-004**: En el emulador, el verde ve la mentoría en Comunidad → Eventos y en Hoy, el amarillo no, y el mentor sí.

## Dudas para el dueño

1. **Primera semana.** Hoy un aprendiz del Día 2 al 7 con todo cumplido está en verde y ve la mentoría. ¿Debe esperar al
   Día 8, o a tener un mínimo de días medidos? (Solo cambiaría el adaptador.)
2. **Alarma local de la app.** Quien dijo «Voy» desde el teléfono conserva su alarma hasta que la app relea la lista. ¿Se
   acepta, o se quiere un aviso de servidor que la cancele? (Exigiría un cambio de app y de notificaciones.)
