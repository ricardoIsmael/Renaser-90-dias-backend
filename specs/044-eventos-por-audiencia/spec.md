# Feature Specification: Eventos para una audiencia elegida (personas, grupo, semáforo o personal)

**Feature Branch**: `044-eventos-por-audiencia` (backend y app)
**Created**: 2026-11-06
**Status**: Aprobada por el dueño el 2026-11-06 (spec y vista previa https://claude.ai/artifact/EVHGP8P1mWqgwz7B7Nu3Uc), con sus respuestas
a las nueve preguntas en «Decisiones del dueño». Implementada en el backend y en la app (ramas `044-eventos-por-audiencia`).
**Input** (pedido del dueño, 2026-11-06, resumido): «El líder de mentores puede agendar una sesión de Meet para un mentor o una persona
específica, o para todos los de semáforo verde, rojo o amarillo. El líder de mentores también puede crear un evento o sesión de Meet con el
mismo alquimista o administrador. Y el mentor también puede crear una sesión con todos sus alumnos, con alumnos específicos, o por semáforo.
**Prioridad: los eventos y los recordatorios a los números específicos.**»

**Cómo se entendió:**

1. **Más roles crean eventos**: además de Administración y Alquimista, el **líder de mentores**, el **mentor** y la **guía de aprendiz**
   (la guía es «lo mismo que el mentor», D-265).
2. Cada evento tiene una **audiencia**: personas elegidas por nombre, un grupo, todos los de un color del semáforo, los mentores o el
   personal (Administración y Alquimista). Lo que no dice audiencia sigue siendo «todos», como hoy.
3. **Solo la audiencia** ve el evento en Comunidad → Eventos y recibe sus avisos: el de la app, el del chat y el WhatsApp de la spec 041.
4. «Los números específicos» = el WhatsApp de cada persona invitada (el de su Ficha Inicial, spec 041): el recordatorio tiene que llegar
   a la persona que se eligió, no a todo el programa.

**Esto deshace D-186 (2026-09-26)**, cuando el dueño le quitó al mentor la capacidad de crear eventos («los crea el Alquimista, y el Admin,
que puede todo»). Ahora vuelve, acotado a sus alumnos y con la audiencia a la vista.

**Se apoya en**: el módulo `calendar` (eventos, audiencias `TODOS`/`NIVEL_MINIMO`/`CURSO`/`ROLES`/`CELULA`, `AudienciaDelEventoService`,
`AccesoEventoService`, `ResolverAudiencia`); la spec 024 (cada evento es de una formación y la audiencia se resuelve dentro de ella); la
spec 020 (la Mentoría del Alquimista solo con semáforo verde, que ya pregunta el semáforo en lote); el semáforo del aprendiz
(`docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md`, `points.api.SemaforoFinder.vigenteDe`); la spec 040 (Meet automático); la spec 041
(recordatorios por chat, app y WhatsApp; `AudienciaDelMensaje`, `EVENTOS_MENSAJES_ROLES`); D-256 (Quién respondió y pasar lista); los grupos
y sus mentores (`celulas`, asignaciones vigentes); la spec 021 (lo que el líder ve de su formación) y el catálogo de Soporte TI.

## Contexto: lo que ya existe (verificado en el código el 2026-11-06, `origin/master` 3a9f0b8b)

| # | Qué hay hoy | Dónde |
|---|---|---|
| E1 | Crear, editar y cancelar eventos es **solo** de ADMIN y ALCHEMIST (D-186), interruptor `eventos.gestion` (roles de Administración). | `EventoService.requireRolCreador`, `EntradasDelCatalogo` |
| E2 | La audiencia de un evento es **una** de cinco: `TODOS`, `NIVEL_MINIMO`, `CURSO`, `ROLES` (lista de roles) o `CELULA` (**un** grupo). No existe «estas personas» ni «este color del semáforo». | `TipoAudiencia`, tabla `eventos`, `roles_destino_evento` |
| E3 | Quién **ve** un evento lo decide `ResolverAudiencia.puedeVer`: Administración y Alquimista ven todos; el resto, según la audiencia. Quién **recibe** sus avisos, `AudienciaDelEventoService.destinatarios` (la misma audiencia, dentro de su formación, más la elegibilidad del tipo). | `calendar/application/services` |
| E4 | La Mentoría del Alquimista se filtra por **semáforo verde, en cada momento** (spec 020): se pregunta al listar, al abrir y al enviar cada aviso; si dejó de ser verde, el aviso se cancela con `dejo_de_ser_elegible`. | `ElegibilidadPorSemaforoAdapter`, `ElegibilidadDeLosAvisos` |
| E5 | Los recordatorios llegan por la app, por el chat de soporte y por WhatsApp (041). Por configuración, el chat y el WhatsApp van **solo a quien dijo «Voy»** (`EVENTOS_MENSAJES_AUDIENCIA=VOY`) y **solo a aprendices** (`EVENTOS_MENSAJES_ROLES`). El WhatsApp nace apagado (`eventos.whatsapp`). | `ReglasDelMensajeDeEvento`, `AudienciaDelMensaje` |
| E6 | Meet automático (040): el servidor crea la sala con la cuenta de Google de Renaser si el formulario lo pide; interruptor `eventos.meet` (Administración). | `EventoConSalaDeMeetService` |
| E7 | «Quién respondió» y «Pasar lista» (D-256): `eventos.asistencia` para líder, Administración, Alquimista y Soporte TI, en **todos** los eventos. La «audiencia» de esa hoja es la misma de los avisos. | `PersonasConvocadasService`, `ListaDeAsistenciaService` |
| E8 | En la app, el formulario «Crear evento» lo ven solo ADMIN y ALCHEMIST (`permisosDeEventos.ts`, una lista de roles en el teléfono); al editar conserva la audiencia que no muestra. | `src/features/eventos/` |
| E9 | En `calendar`, la guía de aprendiz (`GUIA_APRENDIZ` / `TRAINEE_GUIDE`) se lee como `MENTOR`. | `RolUsuarioSqlMapper` |

**Conclusión:** falta (1) que más roles puedan crear, cada uno con su alcance; (2) una audiencia **compuesta** que nombre personas, grupos y
colores; (3) que la vista, los avisos, la asistencia y los mensajes de la 041 lean esa audiencia; (4) la parte de la app.

## Clarifications

### Decisiones del dueño (2026-11-06)

| # | Pregunta | Respuesta del dueño | Efecto en la spec |
|---|---|---|---|
| P1 | ¿Cuándo se mira el color del semáforo? | **A.** Una foto al guardar, con «Volver a calcular» al editar. Es el semáforo propio de cada persona. | FR-009 a FR-012 |
| P2 | ¿A quién le llegan los recordatorios de un evento con invitados? | **A.** Todos los invitados lo ven en su calendario (Eventos y «Mi agenda») y reciben el recordatorio por WhatsApp, por el chat de soporte y por el aviso de la app. **Al que responde «No voy» ya no le llega.** Ejemplo del dueño: el líder crea un evento para los rojos y les llega a todos ellos; si agrega a una persona, sea aprendiz o mentor, a esa persona le aparece en su calendario y le llega el recordatorio. | FR-020 a FR-023 |
| P3 | ¿Los mentores, el líder y el personal invitados reciben WhatsApp? | **A.** Sí. El líder puede programar sesiones con Administración y con Alquimista. | FR-024 |
| P4 | ¿Se pueden juntar varias opciones? | **A.** Sí, se suman. | FR-003 |
| P5 | ¿Cómo nace el interruptor? | **Prendido para todos al publicar.** `eventos.convocar` nace prendido y se apaga desde Soporte TI si algo falla. | FR-028 |
| P6 | Además de los invitados, ¿quién ve el evento? | **A.** Solo quien lo creó, Administración y Alquimista. | FR-013 (con la excepción de P7 para el líder) |
| P7 | ¿El líder edita o cancela los eventos de sus mentores? | **B.** Sí. | FR-006, FR-013 |
| P8 | ¿Se ofrece «Sin datos»? | **A.** Solo los tres colores. A una persona nueva, sin color todavía, se la agrega buscándola por nombre en «Personas». | FR-009 |
| P9 | ¿Qué tipos eligen el mentor y el líder? | **A.** Solo «Sesión especial» y «Espontáneo» («Encuentro espontáneo» en la app). No les aparece «Mentoría del Alquimista» ni la «Semana de Manifestación». | FR-007 |
| S7 | «Te invitó Rocío, tu líder» | **Se queda.** | FR-019 |
| 11 | «En el grupo de soporte también está el líder de mentores» | Revisado en el código (ver abajo): **hoy NO está**, así que se suma. | FR-037 a FR-042 |

**Lectura de P9.** El pedido decía «solo Sesión especial y Espontáneo» y también «no les aparece Mentoría del Alquimista ni Encuentro». El único tipo
que la app llama «Encuentro…» es el espontáneo («Encuentro espontáneo»), que la misma respuesta permite. Se tomó lo que la respuesta elige de forma
explícita (A: Sesión especial y Espontáneo) y se esconden los otros dos tipos (Mentoría del Alquimista y Semana de Manifestación). Si el dueño quería
decir que el espontáneo tampoco, es quitar una línea (`TiposDeEventoDelRol`).

**P7, qué son «sus mentores».** Los mentores y guías de aprendiz **de la formación** del líder: el mismo padrón que «Mis mentores» y «Todos los
aprendices» (spec 021), porque en el modelo el líder no lidera grupos sino el cuerpo de mentores de su formación. En concreto: el líder puede ver,
editar, cancelar y borrar un evento con audiencia elegida si lo creó una cuenta que hoy actúa como mentor (MENTOR o TRAINEE_GUIDE) y el evento es de
una formación que el líder ve. Esa es la única excepción a P6: para editar algo hay que verlo.

**Adicional 11, lo que había en el código (2026-11-06).** El chat de soporte de cada aprendiz (`TipoConversacion.SOPORTE`, clave
`soporte:<aprendiz>`) tenía como participantes al aprendiz y al **staff administrativo**, ADMIN y ALCHEMIST (`ConversacionSoporteService.STAFF_ADMINISTRATIVO`),
con la nota «Confirmado por el dueño del proyecto: administrador o alquimista, nadie más — ni mentor, ni líder de mentores» (D-136). Las cuatro copias
de la regla de acceso (`AutorizacionDeConversacionService`, `ConversacionService`, `MensajeService`, `PresenciaService`) exigían `canManageRoles()`. El
líder de mentores **no estaba**: no veía esos chats ni los recordatorios que la 041 escribe ahí. Ahora se suma, **solo en los soportes de los
aprendices de su formación** (FR-037 a FR-042). El mentor sigue sin estar (no se pidió).

### Supuestos del equipo (el dueño los puede cambiar)

- **S1. «Mis alumnos» de un mentor** = los integrantes vigentes de **todos** los grupos que acompaña hoy (no solo el principal), sin él mismo.
- **S2. «Personas» del líder** = cualquier persona activa de la formación del líder (aprendices, mentores y guías), la misma regla que «Todos los
  aprendices» (spec 021).
- **S3. «Personal»** = Administración y Alquimista, por nombre o «Todo el personal».
- **S4. El semáforo solo lo tienen los aprendices.** Elegir «Rojo» es elegir a los aprendices en rojo.
- **S5. Quien crea el evento siempre está dentro**: lo ve y recibe sus recordatorios como organizador.
- **S6. Los tipos de evento no cambian** sus reglas: el tipo sigue decidiendo los recordatorios por defecto, y la Mentoría del Alquimista sigue
  exigiendo verde además de la audiencia.

## User Scenarios & Testing

### Historia 1 — El líder convoca a los de semáforo rojo a una sesión de Meet (Prioridad: P1)

La líder de mentores ve que varios aprendices están en rojo. Crea «Sesión de rescate» el jueves a las 20:00, con Meet automático, y en «¿Para quién
es?» elige «Por semáforo» → «Rojo» y además a una persona por nombre. La app le dice «Llega a 16 personas». Guarda. Esos 16 ven el evento en Comunidad →
Eventos y en «Mi agenda», reciben el aviso de evento nuevo y, antes de empezar, el recordatorio en la app, en su chat de soporte y por WhatsApp. Nadie
más lo ve. Quien responde «No voy» deja de recibir los recordatorios.

**Pruebas de aceptación:**
1. **Dado** un evento con audiencia «Rojo», **cuando** un aprendiz en verde abre Comunidad → Eventos, **entonces** no lo ve, y si abre su enlace recibe
   el 403 de siempre («Este evento ya no está disponible»).
2. **Dado** el mismo evento, **cuando** se generan los recordatorios, **entonces** solo hay filas para los invitados y para la creadora.
3. **Dado** un invitado que respondió «No voy», **cuando** vence su recordatorio, **entonces** se cancela con el motivo `dijo_no_voy` y no le llega
   por ningún canal.
4. **Dado** el aviso de un aprendiz invitado, **entonces** nunca dice por qué se lo invitó (ni «rojo» ni «semáforo»).

### Historia 2 — El líder agenda con un mentor, o con una persona (P1)

El líder elige «Personas», busca «Luisa», la marca (es mentora) y suma a un aprendiz. «Llega a 2 personas». Solo esas dos lo ven y reciben los
recordatorios, también por WhatsApp a su número (la mentora también, P3).

### Historia 3 — El líder agenda con el Alquimista o con Administración (P1)

El líder elige «Personal» y marca al Alquimista. El evento lo ven el líder y el Alquimista (y Administración, que ve todo). Solo el Alquimista
recibe sus recordatorios, por la app y por WhatsApp.

### Historia 4 — El mentor convoca a sus alumnos (P1)

Un mentor crea «Repaso de la semana». En «¿Para quién es?» solo le aparecen «Mis alumnos», «Personas» (solo sus alumnos) y «Por semáforo» (los
colores de sus alumnos). Elige «Amarillo» y «Rojo»: «Llega a 6 alumnos». En el tipo de evento solo ve «Sesión especial» y «Encuentro espontáneo».

**Pruebas:** el mentor no ve «Personal», «Mentores» ni «Todos»; si el pedido incluye a alguien que no es su alumno, el servidor responde 403 y no
guarda nada; un alumno de otro grupo, en rojo, no recibe nada.

### Historia 5 — La guía de aprendiz hace lo mismo que el mentor (P2)

Igual que la historia 4 (la guía «es lo mismo que el mentor para grupos», D-265).

### Historia 6 — El aprendiz invitado (P1)

El aprendiz ve la tarjeta del evento como cualquier otro, con «Te invitó Rocío, líder de mentores». No ve quién más fue invitado ni por qué. Dice
«Voy» y recibe los recordatorios.

### Historia 7 — Quien lo creó ve su audiencia (P2)

En el detalle, quien lo creó (y Administración, Alquimista y el líder si lo creó uno de sus mentores) ve «Para quién es»: «Rojo · 15», «1 persona»,
«Llega a 16 personas», y «Ver quién respondió». Al editar puede tocar «Volver a calcular» el semáforo.

### Historia 8 — Administración y Alquimista lo pueden todo (P2)

Ven el mismo paso con todas las opciones: «Todos» (lo de hoy, por defecto), «Personas», «Grupos», «Mentores», «Por semáforo» y «Personal».

### Historia 9 — El líder en el chat de soporte (P2)

El líder de mentores ve en Comunidad → Tribu la sección «Soporte» con los chats de soporte de los aprendices de su formación, como Administración, y
lee ahí los recordatorios que la 041 escribe a cada aprendiz.

### Casos de borde

- **Cambio de color antes del evento.** La lista de colores es una foto del momento de guardar (P1): quien estaba en rojo sigue invitado aunque
  mejore, y quien pasa a rojo después no entra solo. Al editar, «Volver a calcular» toma una foto nueva.
- **El evento se edita y cambia la audiencia.** Quien sale deja de verlo; sus recordatorios pendientes se borran al guardar (como cualquier edición) y
  la generación no los vuelve a crear. Quien entra recibe sus recordatorios en la siguiente pasada; el aviso de «evento nuevo» solo sale en las
  primeras 24 horas desde que se creó el evento (la regla de siempre).
- **Un alumno cambia de grupo o de mentor.** Si entró por «Mis alumnos» o por un grupo, deja de estar (se leen en cada momento). Si entró por nombre o
  por color (lista fija), sigue invitado hasta el evento.
- **Una persona suspendida o de prueba** no recibe avisos (como hoy).
- **Audiencia vacía** (nadie en rojo hoy): la app dice «Llega a 0 personas» y no deja guardar; el servidor responde 400 `AUDIENCIA_VACIA`.
- **Persona en dos partes** (por nombre y por color): una sola vez, un solo aviso.
- **App anterior** (1.7.x): un aprendiz invitado ve el evento (el filtro es del servidor); un mentor no ve «Crear evento» hasta actualizar;
  Administración puede editar un evento con audiencia elegida y **su audiencia no se pierde** (FR-030).

## Requirements

### Quién crea y qué puede elegir

- **FR-001**: Pueden crear eventos **ADMIN, ALCHEMIST, MENTOR_LEAD, MENTOR y TRAINEE_GUIDE**. TRAINEE no crea (403).
- **FR-002**: Las partes de audiencia que puede elegir cada rol (el servidor lo decide y lo vuelve a comprobar al guardar):

  | Parte (`kind`) | ADMIN / ALCHEMIST | MENTOR_LEAD | MENTOR / TRAINEE_GUIDE |
  |---|---|---|---|
  | Todos (lo de hoy, `ALL_MEMBERS`) | Sí | No | No |
  | Personas (`PERSON`) | Cualquier persona activa | De su formación (S2) | Solo sus alumnos (S1) |
  | Mis alumnos (`MY_LEARNERS`) | No | No | Sí |
  | Grupo (`GROUP`) | Cualquier grupo | Los de su formación | No |
  | Mentores (`MENTORS`): todos los mentores y guías de la formación | Sí | Sí | No |
  | Por semáforo (`TRAFFIC_LIGHT`): Verde, Amarillo, Rojo | Aprendices de la formación | Aprendices de su formación | Solo sus alumnos |
  | Personal (`STAFF`): Administración y Alquimista, por nombre o todos | Sí | Sí | No |

- **FR-003**: Una audiencia puede sumar varias partes (P4). Cada persona cuenta una vez.
- **FR-004**: Al guardar, el servidor valida cada persona y cada grupo contra el alcance de quien guarda. Si alguno no le corresponde, responde **403**
  `FUERA_DE_TU_ALCANCE` con la lista de ids rechazados y no guarda nada.
- **FR-005**: Una audiencia elegida no puede quedar vacía al guardar: 400 `AUDIENCIA_VACIA`. Sin partes: 400 `AUDIENCIA_INVALIDA`.
- **FR-006**: Editan, cancelan y borran un evento: quien lo creó; ADMIN y ALCHEMIST, cualquiera; y el **líder**, los que crearon sus mentores (P7).
  Nadie más (403).
- **FR-007**: Mentor, guía y líder solo crean eventos de tipo «Sesión especial» o «Espontáneo» y solo con audiencia elegida (P9). Otro tipo u otra
  audiencia: 403.
- **FR-008**: Un evento creado por mentor, guía o líder nace en la formación activa de quien lo crea (spec 024).

### El semáforo

- **FR-009**: El color es el del semáforo vigente de cada aprendiz (`SemaforoFinder.vigenteDe`, los últimos 7 días cerrados en su zona), el mismo que
  ve en Hoy. Solo Verde, Amarillo y Rojo: «Sin datos», «En pausa» y quien no tiene semáforo no entran por ningún color (P8).
- **FR-010**: El color se resuelve **al guardar** y se guarda la lista de quienes entraron por color (P1). La lista no cambia sola.
- **FR-011**: Al editar, con `recalculate: true` («Volver a calcular») se toma una foto nueva; sin él, la foto guardada se conserva aunque se reenvíe
  la misma parte de color.
- **FR-012**: Lo que entra por color se lee en lote: una lectura del semáforo para todos los candidatos, nunca una por persona.

### Quién lo ve

- **FR-013**: Un evento con audiencia elegida lo ven: la audiencia, quien lo creó, ADMIN, ALCHEMIST y el líder si lo creó uno de sus mentores (P6 y
  P7). Nadie más: ni en la lista, ni en el calendario, ni en «Mi agenda», ni en «Próximo evento» de Hoy; al abrirlo, el 403 de siempre.
- **FR-014**: «Mis alumnos», «Grupo» y «Mentores» se leen en cada momento; «Personas», «Personal» por nombre y lo que entró por color son una lista fija.
- **FR-015**: Un evento sin audiencia elegida se comporta exactamente como hoy.

### Privacidad

- **FR-016**: Un invitado no ve a quién más se invitó ni por qué: la respuesta del evento no trae `audience` para él (`null`).
- **FR-017**: Ningún aviso, mensaje del chat ni WhatsApp nombra el motivo de la invitación ni el color.
- **FR-018**: La audiencia («Para quién es», sus cifras y nombres) la ven quien lo creó, ADMIN, ALCHEMIST y el líder de los mentores (FR-006).
- **FR-019**: El evento le dice a todos quién lo organizó (`organizer`: nombre y rol), para «Te invitó Rocío, líder de mentores» (S7).

### Avisos y recordatorios

- **FR-020**: El aviso de «evento nuevo» y los recordatorios llegan solo a la audiencia y a quien lo creó (S5).
- **FR-021**: En un evento con audiencia elegida, el chat de soporte y el WhatsApp de la 041 le llegan a **todos los invitados**, hayan dicho «Voy» o
  no, sin mirar `EVENTOS_MENSAJES_AUDIENCIA` (P2). Los eventos sin audiencia elegida siguen con la regla global.
- **FR-022**: A quien respondió **«No voy»** a esa fecha no le llega ningún recordatorio de ella (app, chat ni WhatsApp): al despacharlo se cancela
  con el motivo `dijo_no_voy`. Si después cambia a «Voy», los siguientes sí le llegan.
- **FR-023**: Al despachar cada recordatorio se vuelve a preguntar si la persona sigue en la audiencia; si no, se cancela con `fuera_de_la_audiencia`.
- **FR-024**: Mentores, guías, líder y personal invitados reciben el WhatsApp aunque `EVENTOS_MENSAJES_ROLES` diga «solo aprendices» (P3). Al
  personal sin chat de soporte propio no se le escribe en el chat: le llegan la app y el WhatsApp.
- **FR-025**: Las reglas de cuándo se avisa no cambian. `eventos.whatsapp` manda igual que hoy.

### Meet, asistencia y Soporte TI

- **FR-026**: El Meet automático (040) funciona igual para un evento de mentor, guía o líder. `eventos.meet` suma esos roles a los que la usan.
- **FR-027**: «Quién respondió» y «Pasar lista» los ven quien creó el evento (también un mentor), ADMIN, ALCHEMIST y el líder (como hoy). La lista es
  la audiencia elegida.
- **FR-028**: Interruptor nuevo de Soporte TI, `eventos.convocar` («Eventos para personas elegidas»), hijo de `eventos`, para MENTOR, TRAINEE_GUIDE,
  MENTOR_LEAD, ADMIN, ALCHEMIST e IT_SUPPORT, **prendido al publicar** (P5). Apagado: el mentor, la guía y el líder no pueden crear ni editar
  (404 `FUNCIONALIDAD_NO_DISPONIBLE`) y la app no les muestra «Crear evento»; Administración crea como hoy, sin audiencia elegida. Los eventos ya
  creados siguen visibles solo para su audiencia.
- **FR-029**: La app no decide quién crea con una lista de roles: lo pregunta al servidor (`GET /api/v1/calendar/audience/options`).

### Compatibilidad

- **FR-030**: Un pedido sin audiencia es «todos», como hoy. Al editar, `SELECTION` sin `audience` conserva las partes guardadas.
- **FR-031**: Los eventos que ya existen no cambian.
- **FR-032**: Una app anterior que recibe `audienceType = "SELECTION"` no se rompe.

### La app

- **FR-033**: El formulario «Crear evento» suma el paso «¿Para quién es?»: chips de las partes que el servidor ofrece, el buscador de personas (foto o
  iniciales, nombre, rol o grupo), los colores con palabra e ícono, «Personal» con «Todo el personal» o por nombre, y la línea «Llega a N personas»
  que recalcula el servidor (`POST …/audience/preview`).
- **FR-034**: El paso abre en «Todos» para Administración y Alquimista, en «Mis alumnos» para el mentor y la guía, y sin elección para el líder.
- **FR-035**: El detalle muestra «Para quién es» solo si el servidor lo manda (FR-018), y «Te invitó …» a los invitados (FR-019).
- **FR-036**: Textos en tipo oración y tuteo, toques de 48 o más, un solo scroll por pantalla, sin animaciones nuevas salvo `Presionable` y
  `tacto.seleccion()`.

### El líder en el chat de soporte (adicional 11)

- **FR-037**: El líder de mentores es participante del chat de soporte de **cada aprendiz de su formación**: entra al crearse el soporte, al pasar a ser
  líder y en el relleno de soportes.
- **FR-038**: El acceso al soporte se decide con la regla de AHORA en las cuatro copias de la regla: ADMIN y ALCHEMIST activos, o MENTOR_LEAD activo
  cuya formación incluye la del soporte. Un líder de otra formación, o que dejó de ser líder, no entra aunque tenga la fila.
- **FR-039**: Al dejar de ser líder se lo retira de los soportes (como al staff administrativo).
- **FR-040**: `GET /api/v1/chat/support-conversations` (la sección «Soporte» paginada) también responde al líder, con los soportes de su formación.
- **FR-041**: El aprendiz y el mentor no ven ningún cambio. `chat.soporte` suma MENTOR_LEAD.
- **FR-042**: En la app, la sección «Soporte · N» de Tribu también se le muestra al líder de mentores.

## Success Criteria

- **SC-001**: Con un evento de audiencia «Rojo», ningún aprendiz fuera de la foto lo ve en la lista ni lo abre (403).
- **SC-002**: El número de destinatarios de los recordatorios es igual a «Llega a N personas» del momento de guardar, más quien lo creó.
- **SC-003**: Un mentor no puede crear un evento con una persona que no es su alumna: 403 y ninguna fila escrita.
- **SC-004**: Ningún texto que recibe un invitado (aviso, chat, WhatsApp, respuesta del evento) contiene el color, «semáforo» ni la lista.
- **SC-005**: Un evento editado sin `audience` (app anterior) conserva su audiencia elegida.
- **SC-006**: Con `eventos.convocar` apagado, el mentor recibe 404 al crear y Administración crea como hoy.
- **SC-007**: Elegir «Rojo» hace una lectura del semáforo, no una por aprendiz.
- **SC-008**: Los eventos de antes reciben exactamente los mismos avisos que antes.
- **SC-009**: El WhatsApp de un recordatorio llega al número de la ficha de cada invitado y de nadie más (probado con un YCloud falso; el envío real
  lo prueba el dueño con un teléfono de prueba). [sin prueba automática del envío real: necesita el número, la plantilla aprobada y saldo]
- **SC-010**: El líder de una formación lee el soporte de un aprendiz de su formación y no el de otra.

## Fuera de alcance

- Que el aprendiz cree eventos.
- Enviar un WhatsApp suelto sin evento.
- Invitar a personas sin cuenta.
- Sumar al mentor al chat de soporte.
