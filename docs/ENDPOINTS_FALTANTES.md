# Endpoints faltantes y huecos de integración

**Última actualización:** 2026-09-01
**Qué es esto:** lo que la app necesita y el backend todavía no da, más lo que el backend ya da pero nadie consume. Sale de integrar de verdad el frontend (`C:/Diseño Opusplan tab 01/renaser-rn/renaser`) contra el backend, no de un análisis en papel.

**Cómo leerlo:** cada fila dice si el hueco es de **backend** (falta construirlo), de **frontend** (falta consumirlo), de **configuración** (existe y está apagado) o de **producto** (falta decidir la regla). No se inventa ninguna regla de negocio acá — lo que no está decidido queda marcado como pregunta abierta (CLAUDE.MD §0.6).

---

## 1. Bloqueantes de configuración — el código existe y está apagado

Estos no requieren escribir código. Son variables de entorno que hoy no están puestas, y cada una deja un flujo entero sin funcionar.

| # | Qué | Síntoma exacto | Cómo se cierra |
|---|---|---|---|
| C-1 | **Almacenamiento en NoOp** | `POST /api/v1/wall/media/upload-url` responde `200` con `uploadUrl: "about:blank#pendiente-s3/..."`. Verificado el 2026-09-01. Ningún archivo se puede subir: ni foto del Muro, ni evidencia, ni avatar, ni adjunto de ticket | Solo **dos** variables: `STORAGE_PROVEEDOR=s3` y `AWS_PROFILE=renaser`. La clave secreta **no** se declara: `AlmacenamientoS3Config` usa `DefaultCredentialsProvider`, que resuelve el perfil de `~/.aws/credentials` (ya configurado, cuenta 526338061654). `AWS_S3_BUCKET` y `AWS_REGION` ya tienen por defecto `s3-renaser90dias` y `us-east-1`. El adaptador real (`S3AlmacenamientoAdapter`) ya está construido. **Requiere reiniciar la JVM**: devtools recarga clases, no variables de entorno |
| C-2 | **OAuth client de Google sin cliente nativo** | En Android/iOS el redirect es `renaser://` o `exp://`, y un OAuth client de tipo *Aplicación web* los rechaza. Solo funciona en web | Crear clients Android/iOS en Google Cloud. **Ojo:** son públicos y no emiten `client_secret`, así que además hay que cambiar `GoogleIdentidadAdapter.requireConfigurado()`, que hoy rechaza el secreto vacío |
| C-3 | **`"scheme": "renaser"` ausente en `app.json`** | Un build nativo de producción lanza excepción al resolver el redirect (`Cannot make a deep link into a standalone app with no custom scheme defined`) | Agregar el campo en `app.json` del frontend |

---

## 2. Módulo `community` — Muro

Estado al 2026-09-01: feed, reacciones y comentarios **conectados y funcionando**. Lo que sigue es lo que quedó afuera.

| # | Hueco | Tipo | Detalle |
|---|---|---|---|
| M-1 | ~~Publicar en el Muro~~ **HECHO 2026-09-01** | Frontend + C-1 | `POST /api/v1/wall` existe, pero el backend exige **mínimo un archivo** (`Publicacion.MEDIA_MIN = 1`, *"una publicación sin foto rompe la retícula"*). Confirmado por el dueño el 2026-09-01: **la imagen es obligatoria y así se queda**. Depende de C-1 para tener dónde subirla |
| M-2 | **Reacciones a comentarios** | Backend | El diseño las muestra, pero `WallCommentResponse` no tiene campos de reacción y no hay endpoint. Hoy la interacción es visual y no persiste |
| M-3 | **Foto adjunta en un comentario** | Backend | Mismo caso: el diseño la contempla, el contrato del backend no |
| M-4 | **Célula y racha del autor en el feed** | Backend | El diseño de cada publicación muestra la célula del autor y su racha de días. `WallPostResponse` no los expone. Quedaron vacíos, **no inventados** |
| M-5 | **Contador de comentarios** | Backend / Frontend | El feed trae `commentCount`, pero el diseño usa `comments.length`, que está en 0 hasta abrir la sección. Se arregla del lado del frontend usando `commentCount`, o exponiendo los comentarios en el feed |
| M-6 | **Categorías del Muro** | Frontend | `GET /api/v1/wall/categories` existe y está implementado en `wallApi.ts`, pero el diseño de la pestaña muro no tiene selector de categorías. Endpoint sin consumidor |
| M-7 | **Editar / ocultar publicación propia** | Frontend | `PATCH /api/v1/wall/{id}` y `DELETE /api/v1/wall/{id}` existen; el diseño no tiene botones para invocarlos |
| M-8 | **Moderación** | Producto | `GET /wall/hidden`, `POST /{id}/restore`, `DELETE /{id}/permanent` existen y requieren `MODERATE_WALL`. Decisión del dueño (2026-09-01): **en Comunidad todo lo hace el aprendiz**, así que la moderación no va en esta pantalla. Falta definir dónde vive: ¿panel de admin propio? |
| M-9 | ~~El feed no muestra las fotos~~ **HECHO 2026-09-01** | Diseño | Descubierto al integrar publicar. Las cajas de media del Muro (`mediaSingleBox`, `mediaHalfBox`, `mediaLargeLeft`, `mediaSmallRight`) **no tienen ningún `<Image>` ni `resizeMode`**: renderizan `post.media[0].title` como texto plano dentro de recuadros de alto fijo. Las fotos se suben bien a S3 y quedan referenciadas en la publicación, pero **la app no las pinta**. No es un defecto de integración: el diseño llegó al marcador y no a la imagen. Consecuencia práctica: hasta que se agregue el `<Image>`, subir una foto no tiene efecto visible. Como no hay proporción declarada en el diseño, tampoco hay regla de recorte, y por eso la normalización **no recorta** — solo corrige orientación EXIF, redimensiona a 1440 px y comprime |

---

## 2.bis Módulo `academy` — Recursos Exclusivos (cursos)

Estado al 2026-09-01: listado de cursos, secciones, detalle de lección y marcar/desmarcar completada **conectados**. Videos de YouTube con carga diferida.

| # | Hueco | Tipo | Detalle |
|---|---|---|---|
| AC-1 | **No se sabe qué lecciones completó cada aprendiz** | Backend | El backend expone solo el **agregado** (`progreso.completadas` / `total_lecciones`), nunca la lista de ids completados. Consecuencia: al abrir un curso **todas las lecciones figuran sin completar**, y el estado real solo se conoce dentro de la sesión, después de que la persona toca el botón. Si cierra la app, pierde la marca visual aunque el backend la tenga guardada. **Hace falta un endpoint** que devuelva los ids completados del curso (o el flag `completada` dentro de `LeccionLiteResponse`) |
| AC-2 | **El porcentaje del curso no se recalcula al completar** | Backend / Frontend | Deriva de AC-1: sin saber cuáles estaban completas, no hay base confiable a la que sumar o restar. El `% Completado` queda como lo devolvió el último `GET /cursos` |
| AC-3 | **El cuerpo de la lección pierde el formato** | Frontend | `LeccionResponse` trae `cuerpoHtml` y `cuerpoMd`, pero no hay renderizador instalado (ni de HTML ni de Markdown), así que se muestra como **texto plano**: se pierden títulos, listas, negritas y enlaces. Instalar un renderizador es una decisión pendiente |
| AC-4 | **Sin reproductor nativo de video** | Frontend | Los videos se reproducen dentro de un `WebView` (`react-native-webview@13.16.1`), tanto los de YouTube como los mp4 de S3. No hay `expo-video` ni `expo-av` instalados. Funciona, pero un reproductor nativo daría mejor control (velocidad, subtítulos, pantalla completa real) |
| AC-5 | **Cursos bloqueados sin representación visual** | Diseño | `GET /cursos/bloqueados` y los campos `diaDesbloqueo`/`diasFaltantes` existen, pero el diseño no tiene un estado "bloqueado". Hoy `GET /cursos` solo devuelve los accesibles, así que **no se muestran**: el aprendiz no ve qué le falta desbloquear. Una lección bloqueada dentro de un curso accesible se intercepta con un aviso, sin UI nueva |

---

## 2.ter Módulo `chat` — Atención Personalizada

Estado al 2026-09-01: conversaciones, mensajes, envío de texto y marcar como leída **conectados**. Solo 3 conversaciones y 5 mensajes en la base, así que el estado vacío es el caso común.

| # | Hueco | Tipo | Detalle |
|---|---|---|---|
| CH-1 | **No se sabe con quién es un chat 1 a 1** | Backend | `GET /api/v1/chat/conversations` devuelve `nombre: null` para conversaciones DIRECT y de célula, y el último mensaje trae `senderName: null`. La única pista es `senderId`, inútil si el último mensaje lo mandó el propio actor o si la conversación está vacía. **La lista de chats no puede mostrar con quién hablás.** Mitigado a medias resolviendo contra `GET /chat/members` y refinando al abrir. **El arreglo real: exponer el otro participante (o el nombre de la célula) en `ConversacionResponse`** |
| CH-2 | **Sin endpoint para subir media de chat** | Backend | El Muro tiene `POST /wall/media/upload-url`; el chat **no tiene equivalente**. `EnviarMensajeRequest` acepta `mediaBucket`/`mediaPath`, pero no hay forma de obtener valores reales. Consecuencia: enviar **audio, foto y video queda cosmético** — no persiste. Solo el texto llega al backend |
| CH-3 | **El tipo GIF no existe en el backend** | Backend / Diseño | `TipoMensaje` es `TEXTO/IMAGEN/AUDIO/VIDEO/SISTEMA`. El diseño tiene selector de GIFs y no hay dónde guardarlos. Decidir: agregar el tipo, mapearlo a IMAGEN, o quitar el selector |
| CH-4 | **El perfil del integrante es mock** | Backend | `GET /api/v1/chat/members` devuelve `{id, fullName, avatarUrl, role}`. El diseño del modal pide **insignia, racha de días, célula y foco**, que el backend no expone. Además `avatarUrl` es una URL y el diseño pinta un emoji — mismo choque que en el Muro (M-4) |
| CH-5 | **Sin tiempo real** | Backend / Frontend | Los mensajes se cargan al abrir la conversación; no llegan solos. Hace falta el fan-out entre instancias de `CLAUDE.MD` §5.2.1 (Redis Pub/Sub) más un cliente WebSocket en la app. Es una tarea propia, no un ajuste |
| CH-6 | **Iniciar un chat 1 a 1 no está cableado** | Frontend | `POST /conversations/direct` está implementado en `chatApi.ts` pero no se invoca: los ids del roster mock no son UUID reales, y llamarlo abriría un chat con una persona equivocada. Depende de CH-4 |

---

## 2.quater Módulo `onboarding` — Ficha Inicial

**Estado al 2026-09-01: la app NO guarda nada de lo que la persona escribe.** Sabe si el onboarding está completo y sabe marcarlo como completo; el contenido se pierde.

El backend expone **13 endpoints**; la app consume **2**.

| # | Hueco | Tipo | Detalle |
|---|---|---|---|
| ON-1 | **Las respuestas de la Ficha Inicial no se guardan** | Frontend | `completeOnboarding(data)` guarda la ficha en **estado local de React** y llama a `POST /onboarding/complete`, pero **nunca llama a `POST /api/v1/onboarding/answers`**. Al cerrar la app se pierde todo. **Y como el onboarding queda marcado completo, nunca se vuelve a pedir: la pérdida es permanente.** Es el hueco más grave del módulo |
| ON-2 | **La firma del Pacto y la aceptación de términos no se persisten** | Frontend | `POST /api/v1/onboarding/milestones` existe y no se llama. `EstadoOnboardingResponse` tiene `termsAcceptedAt`, `pactAcceptedAt` y `pactSignedAt`, que quedan siempre en null. Es un compromiso firmado sin registro |
| ON-3 | **El avance no se guarda** | Frontend | `PUT /api/v1/onboarding/state` no se llama. Quien cierre la app a mitad del flujo lo pierde entero y vuelve a empezar desde el principio |
| ON-4 | **El cuestionario vive en el código de la app** | Frontend | `GET /api/v1/onboarding/questionnaire` existe; la app usa `chaptersConfig.ts` local. Cambiar una pregunta obliga a publicar una versión nueva de la app en vez de tocar el backend |
| ON-5 | **Grabaciones V90 sin conectar** | Frontend | `POST /v90-recordings`, `GET`, y los dos de `/validation` existen y no se usan. Es el flujo async con reintentos y caída a revisión manual que `CLAUDE.MD` §7 describe como ya resuelto del lado del servidor |
| ON-6 | **Media del onboarding sin conectar** | Frontend | `POST /onboarding/media/upload-url` y `POST /onboarding/media` existen y no se usan. Mismo patrón de dos pasos que ya funciona en el Muro |
| ON-7 | **Validación de la meta maestra por IA** | Frontend + IA | `POST /onboarding/master-goal/validation` existe. Recordar que **los 7 adaptadores de IA son `NoOp`** (`CLAUDE.MD` §7): aunque se conecte, hoy no piensa nada |
| ON-8 | ~~`POST`/`GET /api/v1/onboarding/activate-program` no existían~~ **HECHO 2026-09-01 (D-67)** | Backend | Cerraba `docs/GAPS_FRONTEND_BACKEND.md` §7. El aprendiz ahora puede elegir su Día 1 (mañana, +2 o +3 en su propia zona horaria — nunca hoy) y un cron nocturno (`AvanzarDiaProgramaScheduler`, `users`) avanza `dia_programa`/recalcula `fase` cada madrugada. **Diferencia de contrato contra lo que el cliente móvil real ya llama:** `startDate` es obligatorio (sin default de "mañana") y reactivar con una fecha *distinta* a la ya guardada devuelve 409 en vez de un 200 idempotente — verificar contra el cliente antes de conectarlo. `PATCH /api/v1/onboarding/start-date` (corregir la fecha antes de que llegue) sigue sin existir, fuera del alcance de D-66 |

---

## 3. Autorización — el hueco transversal más importante

| # | Hueco | Tipo | Detalle |
|---|---|---|---|
| A-1 | **La matriz rol → permiso — PARCIAL: aprendiz verificado, 4 roles pendientes** | Backend | Cerrado a medias el 2026-09-01 (D-64). `UserRole.can(Permission)` existe y `PermissionEnforcementInterceptor` (`users/infrastructure/adapter/in/web/security`) **ya hace cumplir `@RequiresPermission` en cada request**, no solo lo declara. Pero el alcance es explícitamente parcial: **solo TRAINEE tiene matriz real** (8 permisos: `USE_APP`, `FOLLOW_OWN_PROGRAM`, `PUBLISH_ON_WALL`, `OPEN_SUPPORT_TICKET`, `USE_MENTOR_TICKETS`, `OPEN_MENTOR_TICKET`, `VIEW_OWN_PHASE_CONTRACTS`, `SIGN_PHASE_CONTRACT`) y es el único rol para el que el interceptor corta con 403 si falta el permiso o si la cuenta está `SUSPENDED` (con la excepción documentada de `OPEN_SUPPORT_TICKET`, que tolera cuenta suspendida). **MENTOR, MENTOR_LEAD, ADMIN y ALCHEMIST siguen sin matriz definida y el interceptor los deja pasar sin tocarlos — falla-abierto deliberado, no un olvido.** Sigue siendo posible, hoy, llamar un endpoint de moderación siendo MENTOR/ADMIN/ALCHEMIST sin que el permiso real se verifique; lo que se cerró es que un TRAINEE ya no puede. Definir la matriz de esos 4 roles es una regla de negocio pendiente de decisión del dueño del proyecto (CLAUDE.MD §0.6) |
| A-2 | **Los ADMIN no tienen contraseña** | Datos | Los 2 usuarios `ADMIN` de la base tienen `hash_contrasena` en NULL: **no pueden iniciar sesión**. Aprobar solicitudes hoy solo se puede con el respaldo `X-Actor-Id`. Cuando A-1 se cierre y ese respaldo se retire, no habrá forma de administrar |
| A-3 | **Desvincular identidad social** | Producto | `POST /api/v1/auth/social/link` ya existe (D-60). Falta el inverso. Pregunta abierta: si alguien entró por Google y **no tiene contraseña**, desvincular lo dejaría sin ninguna forma de entrar. Es regla de producto, no se inventa |
| A-4 | ~~El alta social se completaba en una sola llamada, sin poder prellenar el formulario~~ **HECHO 2026-09-01** | Backend | D-65: `POST /api/v1/auth/social` para identidad nueva ya no crea la `AccountRequest` en el momento — retiene la identidad verificada en Redis (10 min, `TokenRegistroPendienteSocialPort`) y devuelve `{registroPendienteToken, email, fullName}` para que la app muestre un formulario de confirmación, como hacen las redes sociales. `POST /api/v1/auth/social/complete` (nuevo) recibe la confirmación y recién ahí abre la solicitud. El correo nunca viaja en el segundo paso: sale siempre del registro retenido, nunca del cuerpo del request (mismo blindaje anti mass-assignment que el `role` ausente del alta pública) |

---

## 4. Ranking (`points`)

| # | Hueco | Tipo | Detalle |
|---|---|---|---|
| R-1 | ~~Ranking sin caché~~ **HECHO 2026-09-01** | Backend | `GET /api/v1/ranking` y `GET /api/v1/ranking/{tipo}` ya no pegan la base en cada consulta: `RankingPersistenceAdapter.porTipoYFecha` está cacheado en memoria (Caffeine, TTL 20s, clave `tipo+fecha`, invalidado al toque cuando `SnapshotRankingScheduler` regenera un snapshot). Ver D-63 en el registro de decisiones — se prefirió esto a procedimientos almacenados/vista materializada/tabla desnormalizada porque el cálculo hoy es trivial (18 aprendices, 2 puntajes) y la caché no compromete ninguna de esas opciones si hace falta escalar después |
| R-2 | **Sin medición** | Backend | El dashboard de Micrometer/OTel de `CLAUDE.MD` §12 sigue pendiente. Es la señal que debe disparar cualquier optimización mayor del ranking (tabla desnormalizada por eventos), y hasta que exista, optimizar es adivinar |

---

## 5. Testimonios

**No tocar.** Orden explícita del dueño (2026-09-01). `TestimonioController` tiene 2 endpoints y la pestaña existe en la app. Cualquier hueco acá se releva cuando él lo indique.

---

## 6. Lo que todavía no se relevó

Comunidad tiene tres partes y solo se integró la primera. Falta relevar los huecos de:

- **Recursos Exclusivos** (cursos y lecciones) → módulo `academy`
- **Atención Personalizada** (chats de célula, 1 a 1, global) → módulo `chat`, más el fan-out entre instancias de `CLAUDE.MD` §5.2.1
- **Eventos** propiamente dichos → módulo `calendar` (`EventoController`)

Y de la app entera: `Hoy`, `Plan`, `Training`, `Yo`.

---

## Registro de cambios

- **2026-09-01** — ON-8 cerrado: `GET`/`POST /api/v1/onboarding/activate-program` (elegir Día 1 tras Términos) y el cron `AvanzarDiaProgramaScheduler` (avanza `dia_programa` y recalcula `fase` cada madrugada). Ver D-67 en `MODULOS_A_AVANZAR.md` §8.
- **2026-09-01** — A-1 cerrado PARCIALMENTE: `@RequiresPermission` ya se ejecuta (`PermissionEnforcementInterceptor`), pero solo para TRAINEE (8 permisos con evidencia en el código). MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST siguen sin matriz definida y pasan sin verificación — hueco deliberado y documentado, no un olvido. Ver D-64 en `MODULOS_A_AVANZAR.md` §8.
- **2026-09-01** — R-1 cerrado: caché Caffeine en `RankingPersistenceAdapter` (TTL 20s, clave `tipo+fecha`, sin actor). Ver D-63 en `MODULOS_A_AVANZAR.md` §8.
- **2026-09-01** — Relevado `onboarding`: sección 2.quater con ON-1..ON-7. **La Ficha Inicial no persiste nada**; 13 endpoints disponibles, 2 consumidos.
- **2026-09-01** — Integrado `chat` (Atención Personalizada). Sección 2.ter con CH-1..CH-6. **Comunidad queda completa salvo testimonios.** A-1 parcialmente cerrado: aprendiz verificado por interceptor, 4 roles pendientes. R-1 cerrado (caché Caffeine). Suite: **2000/2000**.
- **2026-09-01** — Integrado `academy` (Recursos Exclusivos): 25 cursos, 172 secciones, 474 lecciones, 124 con video. Sección 2.bis nueva con AC-1..AC-5.
- **2026-09-01** — M-9 cerrado: `FotoMuro` con `expo-image`, `cacheKey` estable (URL sin query string, regla de E-57) y `cachePolicy="memory-disk"`. Los videos y los errores de carga dejan el recuadro como estaba. **Pendiente nuevo: reproducir videos del Muro** — no hay player para media de tipo `video/`.
- **2026-09-01** — M-1 cerrado (publicar con foto, con normalización EXIF). M-9 agregado: el feed no renderiza imágenes. C-1 sigue abierto: falta reiniciar el backend con `STORAGE_PROVEEDOR=s3`.
- **2026-09-01** — Documento creado al integrar el Muro. Bloqueantes C-1/C-2/C-3 y huecos M-1..M-8, A-1..A-3, R-1/R-2 verificados contra el código y contra el backend corriendo, no supuestos.
