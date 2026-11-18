# Investigación — Spec 056

Lo que ya existe y se reutiliza, verificado en el código el 2026-11-18 (backend y app, con graphify y lectura de los archivos).

## Servidor

| Necesidad | Existe | Qué se hace |
|---|---|---|
| Grupos (crear, mentor, aprendices) | `CelulaAdminController` `/api/v1/admin/cells…`, todo con `MANAGE_CELLS`; los servicios exigen `requireAdmin` (ADMIN/ALCHEMIST) | `MANAGE_CELLS` a MENTOR_LEAD en `UserRole`; `requireAdmin` acepta al líder dentro de su alcance (`AlcanceDeFormacionFinder`, como `AccesoDeLiderazgo`). Cambian `UserRoleTest.mentorLeadNoEsAdministrador` y `MatrizPorRolEnTodoEndpointTest` (CL-04). |
| Cupo | Por grupo (`CupoCelula` 10–15; `Celula.cupo(política)`); no por mentor | Derivado (decisión 18/11): suma de cupos de sus grupos y aprendices vigentes, en padrón y ficha del mentor; `capacity` al dashboard. |
| Días con color | `DiasDelAprendizService` sobre `points.api.DiasDelSemaforoFinder` (ruta del mentor) | `GET /leadership/people/{p}/dias` con el mismo finder y recorte. |
| Semana sábado a viernes | `GET /mentor/groups/{g}/learners/{u}/semana` (055); el líder tiene `/week` (7 días hasta ayer, sin color por día) | `GET /leadership/people/{p}/semana` con la forma de la 055, por puerto. |
| Objetivos de otra persona | `rocks.api.RocasDelAprendizFinder` (objetivosDeNoventaDias, delMes, deLaSemana, progresoDeLaSemana), hoy solo lo usa `rag` | `GET /leadership/people/{p}/objetivos`. |
| Observación al mentor | `POST /leadership/mentors/{id}/observations` guarda; no avisa (`sentByChat` lo hacía la app) | Evento `ObservacionDelLiderEvent` → listener push en `notifications`; mensaje por un puerto nuevo `chat.api.MensajeDirectoPort` (el guardado del mensaje ya dispara su propio push). |
| Avisar a su mentor | La app manda por chat (`enviarPorChat`) | El servidor lo hace: `POST /leadership/people/{p}/avisar-al-mentor` (mensaje + aviso), un toque un pedido con `operationKey`. |
| Consultas | `GET /admin/tickets` (`VIEW_ALL_MENTOR_TICKETS`, el líder lo tiene); responder exige mentor asignado; `respondidoPor` existe (V88) sin exponer | El líder de la formación responde; `respondidoPor {id, nombre}` en la respuesta; `TicketMentorRespondidoEvent` ya avisa al aprendiz. «Recordar» = mensaje directo al mentor. |
| Reporte del mes | `GET /leadership/report` | Sin cambios; «Exportar» reutiliza el documento (tipo `MES`). |
| Sesión | `POST /calendar/events`, el líder crea `SESION_PERSONALIZADA` con PERSONA, GRUPO, MENTORES y `crearEnlaceMeet` | Sin cambios de servidor. |
| Documento | `AlmacenamientoPort.subir`/`firmarLectura`; patrón de `PodioDeLaSemanaService` (ruta determinista + subir); HTML con text blocks (`PlantillasEmail`) | `DocumentoSemanal` + V199; huella SHA-256 de los datos; `reportes/{persona}/{semana}-{tipo}.html`; `text/html; charset=utf-8`; todo escapado con `HtmlUtils.htmlEscape`. |
| Notas privadas | `mentoring.api.NotasDelMentorFinder` | El generador no lo llama (FR-037). |

## App

| Necesidad | Existe | Qué se hace |
|---|---|---|
| Ficha | `AlumnoScreen` (055) atada a rutas del mentor; componentes sin rutas (`AvatarConSemaforo`, `SemanaDeCirculos`, `MesDeColores`, `PilarDeHabitos`, `EvidenciaDeObligacion`, `HojaDeNotas`, `NotasDeLectura`, `FilaDeAprendiz`, `Pildora`, `ordenDeAprendices`) | Extraer `FichaDePersona` con `origen`; hooks reciben funciones `cargar`. |
| Lecturas del líder | `cumplimientoDelLiderApi` (week, radar), `cierreApi`, `huecosApi`, `diagnosticoLecturaApi`, `ochentaVeinteApi`, notas — todas con `grupoId|null` | Se suman `dias`, `semana`, `objetivos`. |
| Pantallas del líder | `MisMentoresEnYo`, `LiderMentoresScreen` (padron/reporte/ficha/dias/observar), `FichaMentorScreen`, `PersonaDelLiderScreen` (también la usa Administración), `GrupoDelLiderScreen`, `AprendicesDelLiderScreen`, `BandejaTicketsScreen` | Se rehacen las del líder; Administración queda igual. Se reescriben ~15 pruebas (`diasDelMentor`, `avisarYEscribirle`, `delGrupoALaPersona`, `panelesDeRol`…). |
| Grupos | `adminApi` (crear, asignar, sumar, retirar) | Se reutilizan las funciones; las pantallas de Administración no encajan con el asistente: es nuevo. |
| Hojas, botones, volver | `HojaDesdeAbajo`, `Presionable`, `GoldButton`, `Legible`, `Alerta`, `useSystemBackHandler`, `useResponsive`, `ControlSegmentado`, `Checkbox` | Se reutilizan. |
| Anillo | No existe; `react-native-svg` instalado | `AnilloDeCupo` con `Circle` y `accessibilityRole="progressbar"`. |
| Íconos de estado | `Icon` tiene `check`, `x`, `alertTriangle`, `star`; no cuadrado ni triángulo simple | `EstadoConForma` dibuja forma + símbolo con `react-native-svg` (trazo `GROSOR_TRAZO_PX`). |
| Sesión | `FormularioDelEvento` sin audiencia inicial | Prop opcional `audienciaPrevia`. |
| Abrir/compartir | `Linking.openURL` en varios lugares; `expo-sharing` instalado sin usar | `useDescargarSemana`. |

## Decisiones técnicas

- **HTML y no PDF**: decisión del dueño (sin librerías); el navegador lo guarda como PDF.
- **Huella**: SHA-256 del JSON canónico de los datos del documento; igual huella → se reutiliza el objeto y solo se firma.
- **Enlace**: 10 minutos (`Duration.ofMinutes(10)`).
- **Mensaje directo**: un puerto en `chat.api` para no importar `chat.application` desde `leadership` (`ArchitectureTest`).
