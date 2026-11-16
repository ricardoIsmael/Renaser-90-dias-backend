# Feature Specification: La transcripción de cada sesión por Meet se guarda sola, cifrada y sin mostrarse

**Feature Branch**: `047-transcripcion-de-eventos`
**Created**: 2026-11-06
**Status**: Aprobada por el dueño (2026-11-06) con las decisiones de abajo. Implementada (D-332): backend V184 y la línea de la app.
**Input**: Pedido del dueño (2026-11-06), textual: «el link es automático pero lo más importante: que puede ingresar cualquier persona si está abierto y se guarde la transcripción, igual que el repo que te pasé guarda muy bien esa información, no mostrándola, pero que cumpla». Y después: «lo de RenaserOs funciona SIN Google Workspace, con una cuenta de Gmail normal».
**Se apoya en**: spec 040 (salas de Meet automáticas, V169 y V171). No cambia nada de lo que la 040 ya hace.

## Decisiones del dueño (2026-11-06)

| Pregunta | Decisión |
|---|---|
| P1 · Quién la ve | **A**: nadie en la app; ADMIN y ALCHEMIST la descargan desde el servidor con un motivo escrito y queda constancia. |
| P2 · Qué se guarda | **B**: la transcripción y el enlace a la grabación (el video sigue en el Drive de la cuenta). |
| P3 · Cuánto tiempo | **B**: 6 meses. |
| P4 · Cómo se avisa | **B**: la Política de privacidad, el aviso de Meet y una línea en el detalle del evento: «Esta sesión se graba y se transcribe.» (autorizado tocar Comunidad → Eventos solo para esa línea). |
| P5 · Sin transcripción de Meet | **A**: nada más; queda «Sin transcripción». |

## Resumen

Cuando termina una sesión de un evento con sala de Meet creada por Renaser (spec 040), el servidor busca su transcripción en Google con la **Meet REST API**, **la guarda cifrada** junto al evento y su fecha, y **no la muestra en la app**. Se reintenta solo mientras Google la prepara. Todo nace **apagado** (interruptor de Soporte TI) y se prende cuando la Política de privacidad ya lo diga.

## Lo que hace RenaserOs (leído el 2026-11-06, solo como datos, sin ejecutar nada)

Repositorio privado `renaserlab/RenaserOs`, punta `1b5a2a9`. Rutas del repo de ellos.

| Qué | Cómo lo hacen | Dónde |
|---|---|---|
| Cuenta | `renasercorporation@gmail.com`, **Gmail sin Workspace, con Google One**. En producción desde el 31/10/2026. | `docs/sdd/citas-transcripciones/spec.md` (cabecera y «Por qué») |
| Ajustar la sala | `PATCH meet.googleapis.com/v2/spaces/{sala}` un campo por pedido: `accessType` (`OPEN`/`TRUSTED`), `autoRecordingGeneration`, `autoTranscriptionGeneration`, `autoSmartNotesGeneration`, y **se lee de vuelta** | `supabase/functions/_shared/google.ts` 325-358 (`ajustarSala`) |
| Encontrar la reunión | `GET /v2/conferenceRecords?filter=space.meeting_code="<código>"` (todas las páginas); se quedan con los tramos de la ventana de la cita (si la sala se vació y se volvió a entrar hay varios) | `google.ts` 376-391 (`registrosDeSala`), 393-437; `citas-transcripciones/index.ts` 22-29 y 104-115 |
| Leer la transcripción | Por cada tramo: `/participants` (nombres), `/transcripts`, `/transcripts/{t}/entries` → texto «Nombre: frase», tramos separados por `---` | `google.ts` 410-426 (`reunionesDeSala`) |
| Si no hay transcripción | Notas de Gemini: `/smartNotes` → `docsDestination.document`, exportado con Drive `files/{id}/export?mimeType=text/plain` | `google.ts` 427-436 y 438-445; `index.ts` 158-169 |
| Último recurso | El documento «… - Transcript» adjunto al evento de Calendar o buscado en Drive por título (en las series ya no lo usan: mezclaba reuniones del mismo título) | `google.ts` 359-375 (`buscarTranscripcion`); `index.ts` 12-17 y 170-205 |
| Permisos | Opcionales: `meetings.space.readonly` (leer reuniones), `drive.meet.readonly` (leer en Drive solo lo de Meet; **restringido**), `meetings.space.settings` (ajustar salas) | `google.ts` 24-31 |
| Cuándo | Reloj cada 5 min (`pg_cron`, `citas_llamar_transcripciones`), desde 5 min después del fin agendado (o al marcar «Se hizo»), 2 citas por vuelta; reintenta cada 10 min **hasta 8 h** después del fin; luego `SIN_TRANSCRIPCION` | migración `20261031090000_citas_transcripciones.sql` 361-410, 519-550 |
| Qué guardan | El texto en `citas.notas` (hasta 120 000 caracteres), el id del documento en `citas.transcripcion_archivo`, estado, intentos, error y fecha | misma migración 33-45 y 417-461 |
| Protección | RLS por cita (lo ven quienes la atienden y con quien se comparte), un trigger impide que el navegador toque las columnas de transcripción, el refresh token en Supabase Vault, la función solo la llama el reloj con un pase interno | migraciones `20261105140000_citas_visibilidad.sql` 381-391, `20261031090000` 55-87, `20261030140000_citas_google_calendar.sql` 361-367; `index.ts` 265 |
| Uso | La pasan a DeepSeek para resumen y acuerdos, y avisan en la campana | `index.ts` 208-222, migración 470-510 |

**Lo que nosotros NO copiamos**: el texto **no** va en claro en una columna de notas (allá no está cifrado y la ficha lo muestra con el enlace al Doc: `src/views/citas/FichaCita.jsx` 113-117); no se muestra en la app; no se pasa a ninguna IA en esta spec; no hay búsqueda por título en Drive. Allá no vimos política de retención ni de borrado de la transcripción: acá sí la hay.

## Viabilidad con nuestra cuenta

- **Sin Workspace.** La cuenta es la misma de RenaserOs (spec 040, decisión 1), un Gmail con Google One, y RenaserOs ya lee transcripciones con ella en producción. No se compra ni se exige Workspace.
- **Corrige a la spec 040 FR-037**, que decía que transcribir exige Workspace: la evidencia práctica de RenaserOs dice que no. FR-037 se mantiene como red de seguridad (si Google deja la transcripción apagada, se avisa y nada se rompe).
- **Plan mínimo de Google One**: el que tenga hoy la cuenta de RenaserOs. SC-001 lo confirma con una sesión real de prueba (nunca con datos de producción) antes de prender el interruptor.
- **Plazo de Google**: «Transcript entries provided by the Meet REST API are deleted 30 days after the conference ends» (documentación oficial de artefactos de Meet). Por eso se lee en las primeras horas, y si en 30 días no se pudo, se pierde la vía de la API (queda el documento de Drive, si se decide usarlo).
- **Permisos nuevos**: `meetings.space.readonly` (Meet REST API, para `conferenceRecords`, `participants`, `transcripts`, `entries` y `recordings`). Hay que sumarlo en «Acceso a los datos» de la pantalla de consentimiento de Google Cloud y **volver a conectar** la cuenta (la 040 ya tiene el flujo). `drive.meet.readonly` solo si el dueño elige guardar las notas de Gemini (P2-C); es un permiso **restringido** de Google.

## Sala abierta: cualquiera con el link entra sin que nadie lo admita (evidencia de la 040)

- **Código**: `GoogleCalendarMeetAdapter.fijarAcceso` (`src/main/java/com/renaser/os/calendar/infrastructure/adapter/out/google/GoogleCalendarMeetAdapter.java` 119-139) hace `PATCH …/spaces/{sala}?updateMask=config.accessType` con `accessType: OPEN` y **lee de vuelta** la sala (`accesoDeLaSala`, 288-299: `OPEN` → `ABIERTA`). La spec 040 FR-023 (corregida el 05/11) dice que la sala es **siempre abierta**. Según la API de Meet, `OPEN` = cualquiera con el link o el teléfono entra sin pedir permiso («without knocking»); `TRUSTED` obliga a pedir permiso a quien no es de la organización ni invitado.
- **Pruebas que existen**: `GoogleCalendarMeetAdapterTest.dejarLaSalaAbierta` (335-346: se manda `OPEN` con su `updateMask` y se confirma lo leído) y `AccesoYReintentoDeLasSalasTest.porDefectoLaSalaQuedaAbierta` y `conPermisoYaNoExisteLaSalaQuedaAbierta` (45-70: por defecto y aunque un llamador pida «con permiso», queda `ABIERTA`).
- **Lo que falta**: esas pruebas usan un Google simulado: prueban que **pedimos** `OPEN` y **guardamos** lo que Google confirma, no que una persona de afuera entre de verdad sin tocar «Pedir unirse». RenaserOs dice haberlo probado en vivo con la cuenta personal. Esta spec suma **SC-002**: una prueba manual real con una sala de prueba y un navegador sin sesión de Google y otro con una cuenta ajena, ambos sin invitación.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - La transcripción de la sesión queda guardada sola (Priority: P1)

Administración crea un evento con «Por Meet» (spec 040). Se hace la sesión. Sin que nadie toque nada, unas horas después la transcripción de esa fecha está guardada cifrada en Renaser.

**Independent Test**: con una sala de prueba que se transcribió, correr el buscador (con Google simulado en las pruebas automáticas; con la cuenta real solo en SC-001) y comprobar que hay una fila `LISTA` con el texto cifrado.

1. **Given** el interruptor `eventos.meet.transcripcion` encendido y la cuenta con `meetings.space.readonly`, **When** pasan 5 minutos del fin agendado de una fecha del evento, **Then** el servidor busca sus conferencias por el código de la sala, dentro de la ventana de esa fecha.
2. **Given** la transcripción en estado `FILE_GENERATED` y con entradas, **When** se lee, **Then** se guarda el texto «Nombre: frase» de todos los tramos de esa fecha, cifrado, con su fecha, la conferencia y cuántas entradas tenía.
3. **Given** la transcripción todavía `STARTED`/`ENDED` o sin conferencia, **When** se busca, **Then** queda `ESPERANDO` y se reintenta (FR-008).

### User Story 2 - Nadie la ve en la app (Priority: P1)

Ninguna pantalla de la app, ningún listado, aviso, chat, ficha ni respuesta de la API de eventos devuelve el texto. Lo que se sabe en Administración es solo el **estado** (FR-012).

### User Story 3 - Se puede sacar cuando hace falta, con motivo y constancia (Priority: P2)

Según P1, quien tenga permiso pide la transcripción de una fecha con un motivo; queda una constancia de quién, cuándo, qué y por qué.

### User Story 4 - Se borra a tiempo (Priority: P1)

Pasado el plazo de P3, la transcripción se borra sola; también al borrar el evento (si el dueño lo elige así) y al pedirlo la persona por sus derechos ARCO cuando corresponda (FR-016).

### User Story 5 - Sin plan, sin permiso o con el interruptor apagado, nada se rompe (Priority: P1)

El evento, el enlace y la sala siguen igual. Administración ve en el detalle del evento por qué no hay transcripción.

### Edge Cases

- **Eventos que se repiten**: todas las fechas comparten la sala (o no, según la 040: hoy una sala por fecha en `eventos_meet`). Se filtran las conferencias por la ventana de esa fecha (inicio − 30 min, fin + 3 h) y, si hay conferencias que caen en dos fechas, se marca `DUDA` y no se guarda nada hasta que Soporte TI lo resuelva (como `citas_apuntar_duda` de RenaserOs).
- **Varios tramos** (se vació la sala y se volvió a entrar): van todos, en orden, separados.
- **Sesión que no se hizo** o nadie entró: sin conferencia en 8 h → `SIN_REUNION`.
- **Hubo reunión pero no transcripción** (alguien la apagó, Google no la generó): `SIN_TRANSCRIPCION` con el motivo de Google; según P5, nada más.
- **Transcripción vacía o mínima** (menos de 40 caracteres): se trata como sin transcripción.
- **Muy larga**: tope de 500 000 caracteres; lo que pasa se corta y se anota `recortada = true`.
- **Cuenta revocada o permiso faltante (401/403)**: no se reintenta en bucle; se marca la cuenta como en la 040 (`CUENTA_REVOCADA` o «falta el permiso») y el aviso de Administración lo dice.
- **Evento borrado o fecha cancelada antes de leer**: no se busca.
- **El interruptor se apaga con filas `ESPERANDO`**: se detiene la búsqueda; lo ya guardado sigue su retención.
- **Clave de cifrado sin configurar**: no se busca nada (sin cifrar no se guarda), y se avisa a Soporte TI.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** Solo aplica a fechas de eventos con sala creada por Renaser (`eventos_meet` en `CREADO`), con el interruptor `eventos.meet.transcripcion` (hijo de `eventos.meet`) **encendido**. Nace **apagado**; se enciende solo después de publicar el aviso de privacidad (FR-018).
- **FR-002** Un trabajo programado del servidor (cada 5 min, con bloqueo para que dos instancias no lo corran a la vez) toma las fechas cuyo fin agendado pasó hace ≥ 5 min y no hace más de 8 h, sin transcripción `LISTA`, hasta N por vuelta (propuesto 5).
- **FR-003** Busca con la Meet REST API v2 y la cuenta de la 040: `conferenceRecords` filtrado por `space.meeting_code` del enlace, los tramos dentro de la ventana de la fecha, y de cada tramo `participants`, `transcripts` y `transcripts/{t}/entries` (todas las páginas). Arma «Nombre: frase» en orden. Un participante anónimo va como «Participante»; con número de teléfono, nunca el número.
- **FR-004** Si P2 lo incluye: guarda también el **enlace a la grabación** (`recordings` → `driveDestination.exportUri`, sin bajar el video) y/o las **notas de Gemini** (exportadas como texto con `drive.meet.readonly`).
- **FR-005** **Nunca** busca por título en Drive.
- **FR-006** Guarda una fila por evento y fecha (por fila de `eventos_meet`). El texto, las notas y el enlace a la grabación van **cifrados** (FR-010); en claro solo lo que no identifica: estado, intentos, conferencias leídas, cantidad de entradas, largo, fechas, motivo del último fallo (sin cuerpo de Google), `recortada`.
- **FR-007** Estados: `ESPERANDO`, `LISTA`, `SIN_REUNION`, `SIN_TRANSCRIPCION`, `SIN_PERMISO`, `DUDA`, `BORRADA`.
- **FR-008** **Reintentos**: cada 10 min mientras la transcripción no está lista, hasta 8 h después del fin; 429 y 5xx con espera creciente; 401/403 no se reintentan (marcan la cuenta); a las 8 h queda `SIN_REUNION` o `SIN_TRANSCRIPCION`. Si el servidor estuvo apagado, al volver retoma lo pendiente que tenga menos de **29 días** (Google borra las entradas a los 30).
- **FR-009** **Sin plan**: si la 040 ya confirmó `transcripcion_confirmada = false` para esa sala, no se busca: queda `SIN_TRANSCRIPCION` con «La cuenta no dejó transcribir esta sala». Si la confirmó `true` o `NULL`, se busca igual.
- **FR-010** **Cifrado en reposo**: el mismo `CifradoDeSecretosPort` / `AesGcmCifrado` de la 040 (AES-GCM, la clave fuera de la base y del repo, versión de clave guardada con el dato para rotarla). Sin clave configurada no se guarda nada. La transcripción **nunca** se escribe en logs, trazas, errores, auditoría, avisos ni métricas (lo frena una prueba).
- **FR-011** **Quién accede**: según P1. En ningún caso el aprendiz, el mentor, la guía ni el líder; nunca en la app ni en un endpoint de lectura general de eventos.
- **FR-012** ADMIN y ALCHEMIST leen del servidor, por fecha, solo el **estado** y su motivo en palabras (`GET /api/v1/calendar/events/{id}/meet/transcripciones`: «LISTA», «SIN_TRANSCRIPCION: La cuenta no dejó transcribir esta sala.», «SIN_PERMISO: Falta volver a conectar Google…»). Sin el texto. Dibujarlo en la pantalla de Administración queda para una entrega aparte.
- **FR-013** **Auditoría** (tabla nueva o la de la 040 con acciones nuevas, solo INSERT): `TRANSCRIPCION_GUARDADA`, `TRANSCRIPCION_SIN_DATOS`, `TRANSCRIPCION_LEIDA` (quién, rol, motivo, evento, fecha), `TRANSCRIPCION_BORRADA` (por retención, por evento borrado, por pedido ARCO). Sin texto ni enlaces. Retención de la auditoría: 12 meses, como la 040.
- **FR-014** **Retención**: según P3. Un trabajo diario borra (pone a `NULL` lo cifrado y marca `BORRADA`) lo vencido.
- **FR-015** Al borrar un evento o una fecha, su transcripción se borra: si la fila de la sala se elimina, en el acto (`ON DELETE CASCADE`); si la sala queda `BORRADO`, en la siguiente corrida de la retención.
- **FR-016** **Derechos ARCO** (Ley 29733 y su reglamento): ante un pedido de cancelación, ADMIN o ALCHEMIST borran la transcripción de una fecha con un motivo (`POST …/transcripciones/{fecha}/borrar`); queda la constancia (`BORRADO`) y la auditoría. Al eliminarse una cuenta (D-243) **no** se borran solas (son de la sesión, con varias personas), salvo que el dueño decida otra cosa.
- **FR-017** **Lo que queda en Google**: la grabación, el documento de transcripción y las notas siguen en el Drive de la cuenta conectada (Meet los guarda ahí). Esta spec no los borra; el plan anota si se ofrece borrarlos del Drive al vencer la retención (queda como tarea opcional). [sin prueba automática: Renaser no toca el Drive de la cuenta]
- **FR-018** **Política de privacidad** (`public/privacidad/` de la app, y la del servidor si la hay): suma que las sesiones por Meet se graban y se transcriben, para qué (constancia y calidad del programa, según P1), quién accede, cuánto tiempo se guarda, que se guarda cifrado y cómo pedir su borrado. Según P4, también una línea en el detalle del evento. **Se publica antes de encender el interruptor.**
- **FR-019** La 040 pide hoy `meetings.space.settings`; esta spec suma `meetings.space.readonly` a los permisos por defecto (variable `EVENTOS_GOOGLE_PERMISOS`). La cuenta tiene que volver a conectarse; hasta entonces las filas quedan `SIN_PERMISO` y el estado de la cuenta (`GET /events/meet/status`) dice «Falta el permiso de leer las reuniones».
- **FR-020** Las respuestas de Google se leen con tolerancia: una entrada rota se pierde ella, no la transcripción.
- **FR-022** (issue #151, 14/11) **Diagnóstico de lo que Meet no grabó**. Cuando una fecha termina en `SIN_TRANSCRIPCION` o `SIN_REUNION`, la fila guarda (`eventos_meet_transcripciones.diagnostico`, jsonb, V194) un resumen que **no identifica a nadie**: por conferencia, inicio, fin y minutos; cuántos participantes hubo con cuenta de Google, anónimos y por teléfono (`conferenceRecords.participants.list`; la API no dice si la cuenta es de la organización); el estado de cada grabación y transcripción que devolvió Google; y la configuración automática de la sala (`spaces.get`, opcional). Más un `motivoProbable` (`SIN_CONFERENCIAS`, `CONEXIONES_MUY_CORTAS`, `SOLO_ANONIMOS_O_TELEFONO`, `ARCHIVO_NO_GENERADO`, `AUTOMATICO_APAGADO`, `CON_CUENTAS_Y_SIN_ARCHIVOS`) y su explicación en español. Nunca nombres, correos, ids ni teléfonos. Si Google falla, no hay diagnóstico y nada se rompe. `GET …/meet/transcripciones` lo devuelve por fecha y `POST …/meet/transcripciones/{fecha}/diagnostico` (ADMIN y ALCHEMIST) lo vuelve a leer en Google para sesiones ya terminadas, mientras Google las guarde (unos 30 días). Mismo permiso `meetings.space.readonly` de FR-019: no pide ninguno nuevo. Dibujarlo en la app queda para una entrega aparte.

### Key Entities

- **TranscripcionDeSesion** (`eventos_meet_transcripciones`, migración nueva; número al implementar, hoy el último es V183): `id`, `evento_meet_id` (FK con `ON DELETE CASCADE`), `estado`, `conferencias` (nombres de `conferenceRecords`), `entradas`, `largo`, `recortada`, `texto_cifrado`, `notas_cifradas`, `grabacion_cifrada`, `version_clave`, `intentos`, `proximo_intento_en`, `motivo`, `obtenida_en`, `borrar_despues_de`, `borrada_en`, `creada_en`, `actualizada_en`. Índice parcial por `proximo_intento_en` de las `ESPERANDO`.

## Success Criteria *(mandatory)*

- **SC-001** [sin prueba automática: prueba manual con la cuenta real de Google y una sala de prueba] Con la cuenta de Renaser y una sala de **prueba** (personas del equipo, nada de producción), una sesión de 10 min con dos personas deja una fila `LISTA` en menos de 2 h, con los nombres y las frases.
- **SC-002** [sin prueba automática: prueba manual con navegadores reales] En esa sala, una persona **no invitada** entra con el link desde un navegador sin sesión de Google y desde otra cuenta de Gmail **sin pedir permiso** y sin que nadie la admita. Se anota con capturas.
- **SC-003** Ninguna respuesta de la API para roles distintos de P1 contiene el texto (prueba sobre todos los endpoints de eventos y Administración).
- **SC-004** El texto no aparece en claro en la base, en logs ni en la auditoría (prueba con un texto testigo).
- **SC-005** Con el interruptor apagado, sin permiso, sin plan o sin clave, crear y editar eventos funciona igual que antes (pruebas de la 040 sin cambios).
- **SC-006** Una transcripción vencida se borra en la siguiente corrida diaria y queda su constancia.

## Fuera de alcance

- Mostrar la transcripción en la app, pasarla a SER o a cualquier IA, resumirla o sacar acuerdos.
- Transcribir nosotros el audio de la grabación (según P5 puede volver como spec aparte).
- Bajar el video a Renaser.
- Salas que no creó Renaser (un link pegado a mano, Zoom).

## [DECISIÓN DEL DUEÑO] (respondidas el 06/11: ver «Decisiones del dueño» arriba)

**P1 · ¿Quién puede ver la transcripción guardada?**
- **A (recomendada)**: nadie en la app. ADMIN y ALCHEMIST pueden **descargarla** desde el servidor solo con un motivo escrito; queda la constancia (quién, cuándo, por qué).
- B: ADMIN y ALCHEMIST la leen en el detalle del evento, con constancia.
- C: nadie; queda guardada solo para un uso futuro (por ejemplo, SER), que se decidiría aparte.

**P2 · ¿Qué se guarda de cada sesión?**
- A: solo la transcripción.
- **B (recomendada)**: la transcripción y el enlace a la grabación (que sigue en el Drive de la cuenta; no se baja el video).
- C: lo de B y además las notas de Gemini (obliga a pedir el permiso restringido de Drive `drive.meet.readonly`).

**P3 · ¿Cuánto tiempo se guarda?**
- A: 90 días después de la sesión.
- **B (recomendada)**: 6 meses después de la sesión.
- C: 12 meses después de la sesión.

**P4 · ¿Cómo se avisa a las personas que la sesión se graba y se transcribe?**
- A: solo la Política de privacidad y el aviso que Meet ya muestra dentro de la sala.
- **B (recomendada)**: lo de A y una línea en el detalle del evento de la app: «Esta sesión se graba y se transcribe.» (toca Comunidad → Eventos: necesita tu autorización de tabs).

**P5 · Si una sesión no tiene transcripción de Meet, ¿qué hacemos?**
- **A (recomendada)**: nada más: queda «Sin transcripción» y Administración lo ve en el evento.
- B: transcribir nosotros el audio de la grabación (bajar el video del Drive y pasarlo por un servicio de voz a texto; tiene costo y pide el permiso de Drive). Iría en una spec aparte.
