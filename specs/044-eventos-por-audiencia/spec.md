# Feature Specification: Eventos para una audiencia elegida (personas, grupo, semáforo o personal)

**Feature Branch**: `044-eventos-por-audiencia` (backend y app)
**Created**: 2026-11-06
**Status**: Borrador para que el dueño lo apruebe junto con la vista previa. **No se implementa nada todavía.** Las preguntas abiertas
están marcadas «[DECISIÓN DEL DUEÑO]» y juntas al final, cada una con sus opciones (la recomendada primero).
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

### Supuestos del equipo para escribir la spec (el dueño los puede cambiar)

- **S1. «Mis alumnos» de un mentor** = los aprendices vigentes de **todos** los grupos que acompaña hoy (no solo el principal). «Mi grupo» de
  un líder que también acompaña un grupo = ese grupo.
- **S2. «Personas» del líder** = cualquier persona activa de las formaciones que el líder tiene activas (aprendices, mentores y guías), la
  misma regla que «Todos los aprendices» (spec 021).
- **S3. «Personal»** = Administración y Alquimista. Se eligen por nombre (el pedido dice «con el mismo alquimista o administrador»), con un
  atajo «Todo el personal».
- **S4. El semáforo solo lo tienen los aprendices.** Elegir «Rojo» es elegir a los aprendices en rojo; el personal con programa propio no
  entra por color.
- **S5. El creador siempre está dentro** de su evento: lo ve y recibe sus recordatorios como organizador, aunque no se haya elegido a sí mismo.
- **S7. El invitado ve quién lo invitó** («Te invitó Rocío, tu líder», con el nombre del creador; nunca el motivo). Si el dueño no lo quiere,
  se quita sin tocar nada más.
- **S6. Los tipos de evento no cambian.** El tipo sigue decidiendo los recordatorios por defecto (`ReglasPorTipoEvento`) y la elegibilidad
  (la Mentoría del Alquimista sigue exigiendo verde, sumada a la audiencia: se cumplen las dos).

## User Scenarios & Testing

### Historia 1 — El líder convoca a los de semáforo rojo a una sesión de Meet (Prioridad: P1)

La líder de mentores ve que varios aprendices están en rojo. Crea «Sesión de rescate» el jueves a las 20:00, con Meet automático, y en
«¿Para quién es?» elige «Por semáforo» → «Rojo». La app le dice «Llega a 15 personas». Guarda. Esos 15 ven el evento en Comunidad →
Eventos, reciben el aviso de evento nuevo y, el día anterior y antes de empezar, el recordatorio en la app, en su chat y por WhatsApp.
Nadie más lo ve.

**Pruebas de aceptación:**
1. **Dado** un evento con audiencia «Rojo», **cuando** un aprendiz en verde abre Comunidad → Eventos, **entonces** no lo ve, y si abre su
   enlace recibe 404 (como un evento que no existe).
2. **Dado** el mismo evento, **cuando** se generan los recordatorios, **entonces** solo hay filas para los 15 (y para la creadora).
3. **Dado** el aviso de un aprendiz invitado, **entonces** el texto **nunca** dice por qué se lo invitó (ni «rojo» ni «semáforo»).

### Historia 2 — El líder agenda con un mentor, o con una persona (P1)

El líder elige «Personas», busca «Luisa», la marca (es mentora) y suma a un aprendiz por nombre. «Llega a 2 personas». Solo esas dos lo ven
y reciben los recordatorios, también por WhatsApp a su número.

**Pruebas:** un mentor no elegido no lo ve; la mentora elegida lo ve aunque el evento sea de otra persona; su WhatsApp llega aunque la 041
diga «solo aprendices» si el dueño elige así (P3).

### Historia 3 — El líder agenda con el Alquimista o con Administración (P1)

El líder elige «Personal» y marca al Alquimista. El evento lo ven el líder y el Alquimista. Administración y Alquimista lo ven en la lista
general como hoy (ven todo, E3), pero solo el Alquimista recibe sus recordatorios.

### Historia 4 — El mentor convoca a sus alumnos (P1)

Un mentor crea «Repaso de la semana» y en «¿Para quién es?» solo le aparecen sus opciones: «Mis alumnos», «Personas» (el buscador muestra
**solo** sus alumnos) y «Por semáforo» (los colores **de sus alumnos**). Elige «Amarillo» y «Rojo»: «Llega a 6 alumnos».

**Pruebas:**
1. El mentor no ve «Personal», «Mentores» ni «Todos».
2. Si el pedido incluye a alguien que no es su alumno (un id cualquiera), el servidor responde 403 y no guarda nada.
3. Un alumno de otro grupo, en rojo, no recibe nada.

### Historia 5 — La guía de aprendiz hace lo mismo que el mentor (P2)

Igual que la historia 4 (la guía «es lo mismo que el mentor para grupos», D-265).

### Historia 6 — El aprendiz invitado (P1)

El aprendiz ve la tarjeta del evento como cualquier otro: título, hora, «Unirme» (Meet), «¿Vas a ir?». No ve quién más fue invitado ni por
qué. Dice «Voy» y recibe los recordatorios.

### Historia 7 — Quien lo creó ve su audiencia (P2)

En el detalle, solo quien lo creó (y Administración y Alquimista) ve «Para quién es»: «Rojo · 15», «2 personas», «Llega a 17 personas»,
y «Van 9 · No van 2 · Sin respuesta 6». El mentor creador puede abrir «Quién respondió» de **su** evento.

### Historia 8 — Administración y Alquimista lo pueden todo (P2)

Ven el formulario de hoy con el mismo paso «¿Para quién es?» y todas las opciones: «Todos» (como hoy, por defecto), «Personas»,
«Grupos», «Mentores», «Por semáforo» y «Personal».

### Casos de borde

- **Cambio de color antes del evento.** Depende de P1. Con la opción recomendada (foto al guardar), quien estaba en rojo al guardar sigue
  invitado aunque pase a amarillo, y quien pasa a rojo después no entra solo: el creador puede tocar «Volver a calcular» al editar.
- **El evento se edita y cambia la audiencia.** Quien entra recibe el aviso de evento nuevo; quien sale deja de verlo y sus recordatorios
  pendientes se cancelan con el motivo `fuera_de_la_audiencia`. Su «Voy» se conserva en la base (no se borra nada) pero no se muestra.
- **Un alumno cambia de grupo o de mentor.** Si entró por «Mis alumnos» o por un grupo, deja de estar (esas partes se leen en cada momento).
  Si entró por nombre o por color (lista fija), sigue invitado hasta el evento; el nuevo mentor no lo ve.
- **El creador deja de ser mentor o se suspende su cuenta.** Sus eventos siguen; Administración puede editarlos o cancelarlos.
- **Una persona suspendida o de prueba** no recibe avisos (como hoy, E5 de la 041).
- **Audiencia que queda vacía** (nadie en rojo hoy): la app dice «Nadie cumple esto hoy» y no deja guardar; el servidor responde 400.
- **Mentoría del Alquimista con «Rojo»**: la audiencia queda vacía (exige verde). La app lo avisa con el mismo «Llega a 0 personas».
- **Evento que se repite** con lista fija: la misma lista para todas las fechas.
- **Persona en dos partes** (por nombre y por color): una sola vez, un solo aviso.
- **App anterior** (1.7.x): un aprendiz invitado ve el evento (el filtro es del servidor); un mentor no ve «Crear evento» (el teléfono no lo
  sabe) hasta actualizar; Administración puede editar un evento con audiencia elegida y **su audiencia no se pierde** (FR-030).

## Requirements

### Quién crea y qué puede elegir

- **FR-001**: Pueden crear eventos **ADMIN, ALCHEMIST, MENTOR_LEAD, MENTOR y TRAINEE_GUIDE**. TRAINEE no crea. Soporte TI no crea (solo
  con «ver como», que es solo lectura).
- **FR-002**: Las partes de audiencia que puede elegir cada rol (el servidor lo decide y lo vuelve a comprobar al guardar):

  | Parte | ADMIN / ALCHEMIST | MENTOR_LEAD | MENTOR / TRAINEE_GUIDE |
  |---|---|---|---|
  | Todos (lo de hoy, `TODOS`) | Sí | No | No |
  | Personas (por nombre) | Cualquiera | De sus formaciones (S2) | Solo sus alumnos (S1) |
  | Mi grupo / Mis alumnos | Cualquier grupo («Grupos») | Su grupo, si acompaña uno | Todos sus alumnos |
  | Mentores (todos los mentores y guías de la formación) | Sí | Sí | No |
  | Por semáforo: Verde, Amarillo, Rojo | Todos los aprendices de la formación | Los de sus formaciones | Solo sus alumnos |
  | Personal (Administración y Alquimista) | Sí | Sí | No |

- **FR-003**: Una audiencia puede **sumar varias partes** (por ejemplo «Rojo» + 2 personas) [DECISIÓN DEL DUEÑO P4]. Cada persona cuenta una
  vez.
- **FR-004**: Al guardar, el servidor valida **cada** persona y grupo contra el alcance del actor. Si alguna no le corresponde, responde **403**
  con la lista de ids rechazados y no guarda nada (todo o nada).
- **FR-005**: Una audiencia elegida no puede quedar vacía al guardar: 400 `AUDIENCIA_VACIA` («Nadie cumple esto hoy»).
- **FR-006**: El creador edita, cancela y elimina **sus** eventos. ADMIN y ALCHEMIST, cualquiera (como hoy). El líder no edita los de sus
  mentores [DECISIÓN DEL DUEÑO P7].
- **FR-007**: Mentor, guía y líder eligen el tipo entre «Sesión especial» y «Espontáneo» [DECISIÓN DEL DUEÑO P9]. La Mentoría del Alquimista y
  la Semana de Manifestación siguen siendo de Administración y Alquimista.
- **FR-008**: Un evento creado por mentor, guía o líder nace en la formación activa de quien lo crea (spec 024); su audiencia se resuelve
  dentro de ella, salvo «Personal», que no pertenece a una formación (como hoy con `ROLES`).

### El semáforo

- **FR-009**: El color es el del **semáforo vigente** de cada aprendiz (`SemaforoFinder.vigenteDe`, los últimos 7 días cerrados en su zona), el
  mismo que ve en Hoy. «Sin datos», «En pausa» y quien no tiene semáforo **no** entran por ningún color [DECISIÓN DEL DUEÑO P8].
- **FR-010**: **Cuándo se mira el color** [DECISIÓN DEL DUEÑO P1]. Recomendado: **una foto al guardar**: el servidor resuelve el color en ese
  momento y guarda la lista de personas que entraron por color. La lista no cambia sola; al editar, el creador puede tocar «Volver a calcular»
  y entonces se toma una foto nueva (quien sale y quien entra siguen FR-022). Alternativa: leerlo en cada momento, como la spec 020.
- **FR-011**: El conteo por color que muestra la app sale del servidor en el mismo momento (FR-033) y coincide con la foto que se guarda si
  se guarda en los 5 minutos siguientes; si cambió, el servidor guarda lo de ese instante y lo devuelve.
- **FR-012**: Lo que entró por color se lee en lote (una lectura del semáforo para todos), nunca una por persona.

### Quién lo ve

- **FR-013**: Un evento con audiencia elegida lo ven: **la audiencia, el creador, ADMIN y ALCHEMIST** [DECISIÓN DEL DUEÑO P6]. Nadie más: ni
  en la lista, ni en el calendario, ni en «Mi agenda», ni en «Próximo evento» de Hoy, ni en el detalle (404 para quien no lo ve, no 403, para
  no confirmar que existe).
- **FR-014**: Las partes «Mis alumnos», «Mi grupo», «Grupos» y «Mentores» se leen **en cada momento** (quien entra al grupo lo ve; quien sale,
  no). «Personas» y lo que entró por color son una **lista fija**.
- **FR-015**: Un evento sin audiencia elegida (todos los de hoy) se comporta **exactamente** como hoy.

### Privacidad

- **FR-016**: Un invitado **no** ve a quién más se invitó ni por qué (ni el color, ni «Por semáforo», ni la lista). La respuesta del evento
  para él no trae la audiencia (`audience: null`), igual que hoy no trae la asistencia.
- **FR-017**: Ningún aviso, mensaje del chat ni WhatsApp nombra el motivo de la invitación ni el color.
- **FR-018**: La audiencia («Para quién es», sus cifras y sus nombres) la ven solo el creador, ADMIN y ALCHEMIST. El formulario le recuerda al
  creador, en una línea: «Tus invitados no ven por qué los elegiste».
- **FR-019**: Elegir por color deja constancia de quién y cuándo armó la lista (la misma auditoría de las fichas: se leyó el semáforo de N
  personas).

### Avisos y recordatorios

- **FR-020**: El aviso de «evento nuevo» (si el formulario lo pide) y los recordatorios de la app llegan **solo a la audiencia** (y al creador,
  S5). Lo hace `AudienciaDelEventoService.destinatarios`, que pasa a leer las partes.
- **FR-021**: El chat y el WhatsApp de la 041 llegan **solo a la audiencia**, y dentro de ella [DECISIÓN DEL DUEÑO P2]: recomendado **a todos
  los invitados salvo a quien dijo «No voy»**, porque se los convocó a propósito («los números específicos»). Los eventos sin audiencia
  elegida siguen con la regla global de hoy (`EVENTOS_MENSAJES_AUDIENCIA`).
- **FR-022**: Si la audiencia cambia, los recordatorios pendientes de quien sale se cancelan con el motivo `fuera_de_la_audiencia` y a quien
  entra se le generan los suyos. Un aviso ya enviado no se deshace.
- **FR-023**: Al despachar cada recordatorio se vuelve a preguntar si la persona sigue en la audiencia (como la elegibilidad de la 020); si no,
  se cancela con `fuera_de_la_audiencia`.
- **FR-024**: Mentores, guías, líder y personal invitados reciben también el WhatsApp aunque `EVENTOS_MENSAJES_ROLES` diga «solo aprendices»
  [DECISIÓN DEL DUEÑO P3]. No tienen chat de soporte (041), así que a ellos les llega la app y el WhatsApp, no el mensaje del chat.
- **FR-025**: Las reglas de cuándo se avisa (1 día, 1 hora, 30 y 10 minutos antes; las del tipo) no cambian. El interruptor `eventos.whatsapp`
  manda igual que hoy.

### Meet, asistencia y Soporte TI

- **FR-026**: El Meet automático (040) funciona igual para un evento creado por mentor, guía o líder: la sala la crea la cuenta de Google de
  Renaser y queda abierta. El interruptor `eventos.meet` suma esos roles.
- **FR-027**: «Quién respondió» y «Pasar lista» de un evento con audiencia elegida: los ven su creador (aunque sea mentor o guía), ADMIN,
  ALCHEMIST y el líder (como hoy, E7). La «audiencia» de esa hoja es la audiencia elegida.
- **FR-028**: Interruptor nuevo de Soporte TI, **`eventos.convocar`** («Eventos para personas elegidas»: crear eventos para una audiencia
  elegida), hija de `eventos`, para MENTOR, TRAINEE_GUIDE, MENTOR_LEAD, ADMIN y ALCHEMIST [DECISIÓN DEL DUEÑO P5]. Apagado o no disponible para
  el rol: el mentor, la guía y el líder no ven «Crear evento» (404 `FUNCIONALIDAD_NO_DISPONIBLE`), y Administración ve el formulario de hoy,
  sin el paso «¿Para quién es?». Los eventos ya creados siguen visibles solo para su audiencia (apagar no los abre a todos).
- **FR-029**: La app no decide quién crea con una lista de roles: lo pregunta al servidor (`GET /api/v1/calendar/audience/options`, que dice si
  puede crear y qué partes puede elegir).

### Compatibilidad

- **FR-030**: Un pedido sin datos de audiencia es «todos» (`ALL_MEMBERS`), como hoy. **Al editar**, si el pedido trae el tipo `SELECTION` sin
  sus partes (una app anterior que reenvía lo que no muestra), el servidor **conserva** las partes guardadas; nunca abre el evento a todos.
- **FR-031**: Los eventos que ya existen no cambian (ninguna migración toca sus filas).
- **FR-032**: Una app anterior que recibe `audienceType = "SELECTION"` no se rompe: el esquema del evento lo lee como un texto más.

### La app

- **FR-033**: El formulario «Crear evento» suma el paso **«¿Para quién es?»** con chips por parte (solo las que el servidor ofrece), el
  buscador de personas (foto o iniciales, nombre, rol o grupo), los colores con **palabra e ícono** (no solo color) y una línea fija abajo
  «Llega a N personas» que se recalcula al cambiar la elección (`POST …/audience/preview`, con 300 ms de espera).
- **FR-034**: Para Administración y Alquimista el paso abre en «Todos» (lo de hoy); para el mentor, en «Mis alumnos»; para el líder, sin
  elección (tiene que elegir).
- **FR-035**: El detalle muestra «Para quién es» solo a quien la puede ver (FR-018), con las cifras del servidor.
- **FR-036**: Textos en tipo oración y tuteo de Perú, toques de 48 o más, un solo scroll por pantalla, sin animaciones nuevas salvo
  `Presionable` y `tacto.seleccion()` al elegir.

## Success Criteria

- **SC-001**: Con un evento de audiencia «Rojo», ningún aprendiz fuera de la foto lo ve en lista, calendario, agenda, Hoy ni detalle (404).
- **SC-002**: El número de destinatarios de los recordatorios es igual a «Llega a N personas» que vio el creador al guardar (más el creador).
- **SC-003**: Un mentor no puede crear un evento con una persona que no es su alumna: 403 y cero filas escritas.
- **SC-004**: Ningún texto que recibe un invitado (aviso, chat, WhatsApp, respuesta del evento) contiene el color, «semáforo» ni la lista de
  invitados.
- **SC-005**: Un evento editado por una app anterior conserva su audiencia elegida.
- **SC-006**: Con `eventos.convocar` apagado, el mentor recibe 404 al crear y Administración crea como hoy.
- **SC-007**: Elegir «Rojo» en un programa de 500 aprendices hace **una** lectura del semáforo, no 500.
- **SC-008**: Los eventos de antes de esta spec reciben exactamente los mismos avisos que antes (mismas filas en la cola).
- **SC-009**: El WhatsApp de un recordatorio llega al número de la ficha de cada invitado y de nadie más (con `eventos.whatsapp` prendido).
  [sin prueba automática del envío real: lo hace el dueño con un teléfono de prueba; el filtro por audiencia sí se prueba]

## Preguntas abiertas para el dueño

Cada una con sus opciones; la recomendada va primero.

- **P1 · ¿Cuándo se mira el color del semáforo?** [DECISIÓN DEL DUEÑO]
  - **A (recomendada)** Una foto al guardar: quien está en rojo cuando guardas queda invitado, aunque después mejore; si quieres, al editar
    tocas «Volver a calcular». Es lo que ves en «Llega a 15 personas».
  - B En cada momento: si alguien pasa de rojo a amarillo, deja de ver el evento (como la Mentoría del Alquimista con verde).
- **P2 · ¿A quién le llegan el chat y el WhatsApp de un evento con invitados?** [DECISIÓN DEL DUEÑO]
  - **A (recomendada)** A todos los invitados, salvo a quien dijo «No voy».
  - B Solo a quien dijo «Voy» (como hoy en todos los eventos).
- **P3 · ¿Los mentores, el líder y el personal invitados reciben WhatsApp?** [DECISIÓN DEL DUEÑO]
  - **A (recomendada)** Sí, cuando se los invitó por nombre, por «Mentores» o por «Personal».
  - B No, solo aprendices, como decidiste en la 041.
- **P4 · ¿Se pueden juntar varias opciones en un mismo evento?** [DECISIÓN DEL DUEÑO]
  - **A (recomendada)** Sí, se suman (por ejemplo «Rojo» y además dos personas).
  - B Una sola opción por evento.
- **P5 · ¿Cómo nace el interruptor de Soporte TI?** [DECISIÓN DEL DUEÑO]
  - **A (recomendada)** Prendido para Administración y Alquimista, apagado para mentor, guía y líder hasta que lo probemos juntos.
  - B Prendido para todos desde el primer día.
- **P6 · Además de los invitados, ¿quién ve el evento?** [DECISIÓN DEL DUEÑO]
  - **A (recomendada)** El que lo creó, Administración y Alquimista.
  - B También el líder de mentores de esa formación.
  - C También el mentor de cada aprendiz invitado.
- **P7 · ¿El líder puede editar o cancelar los eventos que crearon sus mentores?** [DECISIÓN DEL DUEÑO]
  - **A (recomendada)** No: solo quien lo creó, Administración y Alquimista.
  - B Sí.
- **P8 · ¿Se ofrece «Sin datos» en el semáforo?** [DECISIÓN DEL DUEÑO]
  - **A (recomendada)** No: solo verde, amarillo y rojo, como pediste.
  - B Sí, para convocar a quien todavía no tiene datos.
- **P9 · ¿Qué tipos de evento eligen el mentor y el líder?** [DECISIÓN DEL DUEÑO]
  - **A (recomendada)** «Sesión especial» y «Espontáneo».
  - B Todos menos la Mentoría del Alquimista.

## Fuera de alcance

- Que el aprendiz cree eventos.
- Enviar un WhatsApp suelto sin evento.
- Invitar a personas de fuera de la app (un correo o un número sin cuenta).
- Cambiar los avisos de los eventos «para todos» que ya existen.
