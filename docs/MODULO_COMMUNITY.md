# Módulo `community` — Muro, Categorías, Cohortes/Células, Testimonios

**Fecha:** 2026-08-24
**Documentos hermanos:** `CLAUDE.MD` (cómo) · `docs/PLAN_DE_MODULOS.md` §7 (agregados sugeridos) · `docs/MODULO_ROCKS.md`, `docs/MODULO_PHASECONTRACTS.md`, `docs/MODULO_SUPPORT.md` (patrones replicados acá)

---

## 0. Estado

🔄 **Construido. `./mvnw clean test` lo corrió el supervisor** (regla del encargo: este agente no corre Maven) — compiló bien; 1 test en rojo, corregido — ver CM-19.

**Corrección del supervisor durante la construcción (prioridad sobre el encargo original):** la base de datos es inmutable en esta fase — no se creó ninguna migración Flyway (ni la `V4__community_seed_categorias_muro.sql` originalmente pedida). Ver CM-15.

**Cambio del supervisor sobre un archivo de este módulo (no deshacer):** `infrastructure/adapter/out/persistence/participante/ConsultarMiembrosCelulaPersistenceAdapter` se renombró a **`ConsultarMiembrosCelulaCommunityPersistenceAdapter`** — el agente de `calendar` creó en paralelo una clase con el mismo nombre simple; Spring deriva el nombre del bean del nombre simple de la clase, así que colisionaban (`ConflictingBeanDefinitionException`) y tumbaban el `ApplicationContext` de toda la suite (afectaba también los tests en verde de `habits`/`notifications`). Sigue la convención ya usada en el repo (`ConsultarProgresoParticipanteRocksPersistenceAdapter`).

---

## 1. Paso 0 — reglas extraídas del código viejo

Repo Next.js clonado en `C:\Users\Usuario\Documents\Backend90dias\RenaserBack`. Archivos leídos completos, citas `archivo:línea`.

### 1.1 Muro (`features/wall/**`)

- **Categoría es opcional** en la publicación (`wall/schema.ts:51-53`): un cliente viejo que no manda `category` sigue publicando igual, queda `null` ("sin categorizar", no un error).
- **Media es OBLIGATORIA**, 1 a 10 archivos — "el Muro es un feed visual, una publicación sin foto/video rompe la retícula" (`wall/schema.ts:41-50`). Carrusel estilo Instagram.
- **`type`**: `'MANUAL' | 'MILESTONE_AUTO' | 'GUERRERO_CAIDO'` (`wall/schema.ts:98`). El comentario de la ruta (`app/api/v1/wall/route.ts:9-11`) dice que `MILESTONE_AUTO`/`GUERRERO_CAIDO` "vienen de system triggers, no de un POST del cliente" — **pero esos triggers no existen en ningún lugar del repo viejo** (búsqueda completa de `MILESTONE_AUTO`/`GUERRERO_CAIDO`/`wallPost.create`, sin resultados fuera del propio schema y ese comentario). Ver CM-7.
- **Reacciones**: `LIKE`/`DISLIKE` mutuamente excluyentes desde 2026-08-11 — PK/unique es `(postId, userId)`, no `(postId, userId, type)` (`wall/schema.ts:130-137`). Tocar el mismo tipo la saca (un-react); tocar el otro la reemplaza en un solo `upsert` (`wall/service.ts:412-432`).
- **Moderación**: "borrar" un post/comentario **oculta**, nunca destruye (`wall/service.ts:162-180`, `:372-387`) — el autor puede ocultar lo propio; ADMIN/ALCHEMIST (por `realRole`, no `role` enmascarado — `app/api/v1/wall/[id]/route.ts:53-56`) pueden ocultar cualquiera. Restaurar y borrado físico son solo de moderación (`wall/service.ts:182-208`).
- **Paginación**: cursor por `createdAt` (keyset), no offset — pide `limite+1` para saber si hay más sin un `COUNT` aparte (`wall/repository.ts:37-39`). Feed descendente; comentarios ascendente ("una conversación se lee de arriba abajo", `wall/repository.ts:190-192`).
- **Contadores por request**: reacciones y comentarios se cuentan/agregan en cada consulta del feed (`wall/repository.ts:56-70`, `_count`), no están denormalizados en `wall_posts`. El baseline nuevo (`publicaciones_muro`) tampoco los denormaliza — se resuelve con agregación en una sola consulta (`GROUP BY`), no con columnas nuevas (instrucción del supervisor, ver CM-15). Queda anotado como posible mejora de rendimiento futura, no como bloqueante.
- **Compatibilidad de media legacy**: posts viejos sin fila en `WallPostMedia` caían a una columna suelta `mediaUrl` (`wall/service.ts:59-68`). El baseline nuevo eliminó esas columnas (P-30) — no aplica a datos nuevos, y no hay datos viejos todavía (BD vacía hasta la migración de datos).

### 1.2 Categorías del Muro (`features/wall-categories/**`)

- Hasta 2026-08-10 era un `z.enum` de 5 literales; pasó a tabla (`wall_categories`) gestionada desde el panel (`wall-categories/schema.ts:1-9`).
- **Catálogo real, migración `20260810150000_wall_categories_tabla/migration.sql:122-128`** (esto NO se convirtió en SQL ejecutable — instrucción del supervisor, CM-15 — queda documentado aquí para que la fase de migración de datos lo use):

  | `key` | `label` | `emoji` | `order` | `is_active` | `is_system` |
  |---|---|---|---|---|---|
  | `REVELACIONES` | Revelaciones | ✨ | 1 | true | false |
  | `AGRADECIMIENTO` | Agradecimiento | 🙏 | 2 | true | false |
  | `LOGROS` | Logros | 🏆 | 3 | true | false |
  | `AYUDA` | Ayuda | 🤝 | 4 | true | false |
  | `PRESENTACION` | Presentación | 👋 | 5 | true | **true** |

  `PRESENTACION` es la única `is_system = true`: la secuencia de bienvenida abre el editor con `comunidad?publicar=PRESENTACION` (`wall-categories/service.ts:194-198`) y no se puede retirar ni borrar. `HUMANOS_SONADORES`/`NOVEDADES` (categorías viejas del enum) se retiraron y no entran al catálogo nuevo.
- **Solo ADMIN/ALCHEMIST gestionan** (`can(role, 'manage', 'wall-categories')`, `src/lib/can.ts:79,119` — MENTOR queda afuera explícitamente).
- Borrado real solo si no es de sistema y no tiene publicaciones (`wall-categories/service.ts:190-246`); si tiene publicaciones, se retira (`is_active = false`), nunca se borra.
- Reordenar manda la lista COMPLETA en el orden final, no un delta (`wall-categories/schema.ts:67-73`).

### 1.3 Comunidad — cohortes/células (`features/community/**`)

- 1 mentor : 1 célula (`Cell.mentorId` es FK única) — "habilitar N células por mentor es trabajo futuro" (`community/repository.ts:460-464`).
- 1 mentor : 10 aprendices máximo (`MAX_TRAINEES_PER_CELL`, `community/service.ts:63-65`) — regla de **asignación de aprendices**, fuera de alcance de este módulo (ver CM-2).
- Transición de estado de cohorte: solo hacia adelante y de a un paso, `PLANNED → ACTIVE → COMPLETED` (`community/service.ts:69-72`).
- Borrar una célula NO exige vaciarla primero — los aprendices quedan sin célula (antes SÍ exigía, se relajó — `community/service.ts:466-489`, comentario "por qué ya no hay guardia").
- MENTOR ve solo su propia cohorte/célula (la que lidera); ADMIN/ALCHEMIST ven todo (`community/service.ts:97-121`, `:221-249`).
- CM-01 (tag del código viejo, no confundir con las decisiones `CM-N` de este documento): `GET /api/v1/me/cell` es **TRAINEE-únicamente**, nunca tuvo rama MENTOR (`app/api/v1/me/cell/route.ts:26`). Sin célula no es error — el Next.js de origen respondía 404 `{assigned:false}` (`:32-34`); **corregido en la migración (comunidad-mentor-y-tribu, 2026-09-02) a 200 `{assigned:false}`**, porque un cliente no puede distinguir un 404 semántico de un error real de red/ruta. Ver `docs/api/CONTRATO_COMUNIDAD.md` §6.1.
- El ranking (`community/service.ts:658-867`, `getRanking`) combina `coherenceScore`/`leaguePoints` (tablas de `points`) con una función SQL (`general_ranking_scores()`) que suma hábitos+rocas+cursos — fuera de alcance de este módulo (ver CM-1).

### 1.4 Testimonios (`features/testimonios/**`)

- Formulario público, puede llegar **sin sesión** (`userId` null) — `testimonios/repository.ts:24-37`.
- `createTestimonio` **nunca** escribe `avatarUrl` en la fila — la respuesta del listado lo resuelve en lectura como `t.avatarUrl || t.user?.avatarUrl` (`testimonios/service.ts:19`). Ver CM-9.
- Siempre nace `isFeatured = true`; no hay ningún endpoint viejo para retirar uno. Ver CM-10.
- Promover una publicación del Muro a testimonio es solo ADMIN/ALCHEMIST (`testimonios/service.ts:64-66`); usa la primera foto del carrusel como portada (`testimonios/service.ts:73-75`).

---

## 2. Integración con `users` — perfiles y progreso

`community` no posee `usuarios`, `perfiles_mentor` ni `participantes_programa` (dueño futuro: `users`). Dos formas de leerlos, según lo que hace falta:

1. **`users.api.UserSummaryFinder`** (puerto público sancionado) — rol/estado del actor, usado en todas las guardas de autorización (`requireAdmin`, moderación).
2. **Copias propias de `community` con query nativa** (mismo patrón que `rocks`/`ConsultarProgresoParticipanteRocksPort`, documentado en `docs/MODULO_ROCKS.md` §2):
   - `ConsultarCelulaDeParticipantePort` — `participantes_programa.celula_id WHERE usuario_id = ?` (una sola columna).
   - `ConsultarMiembrosCelulaPort` — el sentido contrario: quiénes tienen `celula_id = X`.
   - `ConsultarPerfilUsuarioPort` — `usuarios.nombre_completo`/`avatar_url`. Ver CM-5: existe porque `UserSummary` **no expone `avatarUrl`** todavía; se pide sumarlo para poder retirar este puerto.
   - `ExistePerfilMentorPort` — solo existencia (`SELECT 1 FROM perfiles_mentor WHERE usuario_id = ?`), nunca escribe (ver CM-4).

---

## 3. Qué se construyó

### 3.1 Agregados

`cohorte/`, `celula/`, `categoria/`, `publicacion/` (con `MediaPublicacion`, `ReaccionMuro`, `Comentario` — agrupados según el criterio de "una sola historia" de CLAUDE.MD sec. 5.1.2, todos dependen de `Publicacion` para tener sentido), `testimonio/`.

### 3.2 Casos de uso construidos

| Agregado | Casos de uso |
|---|---|
| `cohorte` | Crear, Actualizar, CambiarEstado, Eliminar, Consultar(listar/obtener, con alcance por rol) |
| `celula` | Crear, Actualizar, AsignarMentor, QuitarMentor, ProgramarSesion, Eliminar, Consultar(listar/obtener), ConsultarMiCelula (CM-01) |
| `categoria` | Crear, Actualizar, Eliminar, Reordenar, Consultar(públicas/panel/claves existentes) |
| `publicacion` | Publicar, Editar, Ocultar, Restaurar, EliminarPermanente, Reaccionar, ConsultarFeed(feed/oculto/mías/último autor), SolicitarUrlSubidaMedia, EscribirComentario, EditarComentario, OcultarComentario, ConsultarComentarios |
| `testimonio` | Crear, PromoverPublicación, ConsultarDestacados |

### 3.3 Endpoints

| Método | Ruta | Notas |
|---|---|---|
| GET | `/api/v1/wall` | feed, cursor+categoría |
| POST | `/api/v1/wall` | publicar (siempre MANUAL) |
| PATCH/DELETE | `/api/v1/wall/{id}` | editar / ocultar |
| POST | `/api/v1/wall/{id}/restore` | solo moderación |
| DELETE | `/api/v1/wall/{id}/permanent` | solo moderación |
| GET | `/api/v1/wall/hidden` | cola de moderación |
| GET | `/api/v1/wall/mine` | contador propio |
| GET | `/api/v1/wall/latest-author` | invitación en Inicio |
| POST | `/api/v1/wall/{id}/react` | toggle LIKE/DISLIKE |
| POST | `/api/v1/wall/media/upload-url` | nuevo — presign vía `AlmacenamientoPort` |
| GET/POST | `/api/v1/wall/{id}/comments` | |
| PATCH/DELETE | `/api/v1/wall/{id}/comments/{commentId}` | |
| GET | `/api/v1/wall/categories` | catálogo público (activas) |
| CRUD | `/api/v1/admin/wall-categories` | nuevo — no existía como ruta REST (CM-13) |
| CRUD | `/api/v1/admin/cohorts` | + `/status`, `/cells` |
| CRUD | `/api/v1/admin/cells` | + `/mentor`, `/session` |
| GET | `/api/v1/admin/cells/dashboard` | nuevo (#25, §8) — cross-cohorte, ADMIN/ALCHEMIST |
| GET | `/api/v1/admin/cells/mentores-disponibles`, `/mentores`, `/aprendices-disponibles` | nuevo (#25, §8) — pickers del panel admin |
| GET | `/api/v1/me/cell`, `/api/v1/me/cell/members` | TRAINEE-únicamente |
| GET/POST | `/api/v1/testimonios` | `wallPostId` en el body ⇒ promoción (admin) |

Todos los controllers reciben el actor por `X-Actor-Id` (temporal, sin JWT — bloqueante del usuario, CLAUDE.MD).

---

## 4. Integraciones y eventos publicados

- **`community.api.PublicacionCreadaEvent`** (record, implementa `DomainEvent`) — publicado por `PublicacionMuroService.publicar()` vía `ApplicationEventPublisher`, dentro de la misma transacción. `notifications` lo consumirá (Ola 3) — el listener no se tocó, es integración de otro agente.
- **Outbox de Spring Modulith**: la migración `V2__spring_modulith_event_publication.sql` ya existe (D-37) — publicar el evento no necesitó ninguna migración nueva, se usó tal cual está.
- **`users.api`**: `UserSummaryFinder`/`UserSummary`/`UserRole`/`UserStatus` para autorización (ver §2). No se importó nada fuera de `users.api`.
- **`shared.application.ports.out.AlmacenamientoPort`**: usado para media del Muro (`solicitarUrl`/lectura firmada de cada archivo) y para `testimonios.foto_evento_ruta`. Hoy con el adapter NoOp (faltan credenciales AWS, D-34) — el puerto se usa igual.

---

## 5. Decisiones propias de este módulo (prefijo `CM-`)

| # | Decisión |
|---|---|
| CM-1 | El ranking (células/aprendices, `community/service.ts:658-867`) **no se construyó**. Combina `coherenceScore`/`leaguePoints` (`puntajes_participante`, tabla de `points`) con una función SQL que suma hábitos+rocas+cursos — ninguno de esos datos es de `community`. Construirlo aquí habría significado leer tablas de `points` sin puerto público, violando CLAUDE.MD sec. 5.1. Queda para `points` o para un agregador post-integración. |
| CM-2 | Asignar/quitar un aprendiz de una célula (`assignTraineeToCell`/`removeTraineeFromCell`) **no se construyó** — escribe `participantes_programa.celula_id`/`mentor_id`, tabla cuyo dueño futuro es `users` (instrucción explícita del encargo: "los otros módulos solo guardan el UUID"). |
| CM-3 | Los pickers de administración (`listMentorsWithoutCell`, `listAllMentors`, `listTraineesWithoutCell`) **no se construyeron** — necesitan leer `usuarios`+`perfiles_mentor`+`participantes_programa` en combinaciones que no son "un usuario, dame su perfil" sino listados completos filtrados; el costo de construir puertos de solo-lectura tan anchos sobre tablas ajenas no se justificaba para esta pasada. |
| CM-4 | `celulas.mentor_id` referencia `perfiles_mentor.usuario_id`, pero `community` **nunca crea** un `perfiles_mentor` (a diferencia del código viejo, que auto-creaba el perfil con `ensureCellLeaderMentorProfile` si un ADMIN/ALCHEMIST elegible no tenía uno). `ExistePerfilMentorPort` solo comprueba existencia; si no existe, `asignarMentor` falla con un 409 legible en vez de una violación de FK cruda. Escribir en `perfiles_mentor` es responsabilidad de `users`. |
| CM-5 | `ConsultarPerfilUsuarioPort` (query nativa propia sobre `usuarios.nombre_completo`/`avatar_url`) existe porque `users.api.UserSummary` **no expone `avatarUrl`** hoy. Se usa en autores de publicaciones/comentarios, mentor de célula y testimonios promovidos. Pedido explícito para el dueño de `users`: sumar `avatarUrl` a `UserSummary` permitiría retirar este puerto. |
| CM-6 | Media del Muro pasa a `bucket`+`ruta` vía `AlmacenamientoPort` (como pidió el encargo), pero el **wire REST sigue aceptando `{url, mimeType}`** tal cual la app publicada ya manda (nunca hizo un flujo de URL prefirmada) — la traducción vive en `MediaItemRequest.aArchivoEntrada()`, la única frontera que conoce esta compatibilidad hacia atrás, mismo criterio que `AbrirTicketSoporteRequest.bucketEfectivo()` de `support`. `POST /api/v1/wall/media/upload-url` es un endpoint nuevo, listo para cuando el cliente migre al flujo de presign real. |
| CM-7 | `HITO_AUTOMATICO`/`GUERRERO_CAIDO` **no se generan** desde ningún lugar de este módulo — el código viejo los documenta como originados en "system triggers" que no existen en ningún archivo del repo (búsqueda completa sin resultados). `PublicarUseCase` siempre produce `MANUAL`. |
| CM-8 | **D-36 (global):** el dominio y la base hablan español (`tipo_publicacion`, `tipo_reaccion`, `estado_cohorte` — mismos nombres que los tipos Postgres); el wire REST habla el inglés que la app publicada ya consume (`MILESTONE_AUTO`, `LIKE`/`DISLIKE`, `PLANNED`/`ACTIVE`/`COMPLETED`...). La traducción vive exclusivamente en `infrastructure/adapter/in/rest/**` (`toWireXxx`/`parseXxx`), nunca en dominio ni persistencia — mismo patrón que `TicketSoporteResponse`/`TicketSoporteAdminController` de `support`. |
| CM-9 | `Testimonio.avatarUrl` se guarda tal cual llega (casi siempre `null`, igual que el código viejo — `createTestimonio` nunca lo setea) y se resuelve **en lectura** contra el perfil vivo del usuario vinculado (`TestimonioService.aVista`), no al crear. Así un cambio de avatar del usuario se refleja sin reescribir testimonios viejos. **Excepción: `promover` sí congela el avatar del autor** — un testimonio promovido es la foto de un momento. Eso era un problema mientras `usuarios.avatar_url` guardaba una URL **prefirmada**, porque la copia heredaba el vencimiento; desde D-55 (2026-08-31) esa columna guarda una URL permanente y el snapshot ya no caduca. `V13__avatar_url_permanente.sql` repara las filas de `testimonios` que quedaron con una firma congelada y agrega el `CHECK` que impide volver a guardar una prefirmada. Ver `docs/BITACORA_ERRORES.md` E-57. |
| CM-10 | `Testimonio` siempre nace `destacado = true` — el código viejo no tiene ningún endpoint para crearlo sin destacar ni para retirarlo después. El dominio expone `retirar()`/`destacar()` (la columna ya existe) para una futura moderación administrativa, no usados hoy por ningún caso de uso. |
| CM-11 | `role` de un mentor promovido a testimonio se guarda como el nombre del enum `UserRole` (`resumenAutor.role().name()`, ej. `"TRAINEE"`) en vez del texto libre que el código viejo guardaba desde `WallPost.author.role` (un string de Prisma). Es información equivalente; se documenta porque cambia de forma. |
| CM-12 | Ranking y `coherenceScore` de los miembros de una célula (`CellMemberItem.coherenceScore`, `CellOverviewResponse.coherenceScoreGroup`/`rankingPosition`) se **excluyen** de las respuestas de `community` — viven en `puntajes_participante`/`ranking_celulas`, tablas de `points` (P-16/P-18 del baseline las sacó explícitamente de `celulas`). Un futuro agregador entre `community` y `points` puede sumarlas sin tocar este módulo. |
| CM-13 | `POST/GET/PATCH/DELETE /api/v1/admin/wall-categories` es una ruta **nueva** — el código viejo solo tenía server actions para el panel Next.js (`app/(admin)/wall-categories/actions.ts`), sin ruta REST propia. Se expone para que un futuro panel que consuma esta API pueda administrar el catálogo. |
| CM-14 | `ActualizarCohorteRequest`/`ActualizarCelulaRequest` **simplifican** el `'campo' in body` del código viejo (que distinguía "omitido" de "`null` explícito" para poder borrar `endDate`/`videoCallUrl`): en esta primera pasada, el PATCH siempre aplica el valor recibido (incluido `null` para borrarlo). Documentado como simplificación menor, no confirmada con el dueño del producto. |
| CM-15 | **La BD es inmutable en esta fase** (corrección del supervisor, tiene prioridad sobre el encargo original). No se creó `V4__community_seed_categorias_muro.sql` ni ninguna otra migración: `categorias_muro` se lee tal cual esté (incluso vacía) — el catálogo real (§1.2) queda documentado como referencia para la fase de migración de datos desde producción, que es posterior y separada de la construcción de módulos. Tampoco se agregaron columnas denormalizadas para los contadores de reacciones/comentarios (§1.1): se resuelven con agregación SQL en la consulta, no con esquema nuevo. |
| CM-18 | `ConsultarMiembrosCelulaPersistenceAdapter` → **`ConsultarMiembrosCelulaCommunityPersistenceAdapter`** (renombrado por el supervisor, no revertir). Colisión de nombre de bean con una clase homónima creada en paralelo por el agente de `calendar` — Spring deriva el nombre del bean del nombre simple de la clase, y la colisión tumbaba el `ApplicationContext` de toda la suite (incluidos tests en verde de `habits`/`notifications`, no solo los de `community`). Convención ya usada en el repo: prefijo del módulo dueño en la clase (`ConsultarProgresoParticipanteRocksPersistenceAdapter`). |
| CM-20 | **`ConsultarFeedUseCase.ultimoAutor()` pasó a `ultimoAutor(UserId actorId)` y exige cuenta activa**, igual que `feed()`. No tenía ningún guard: cualquiera (sin sesión válida, o con la cuenta suspendida) obtenía el **nombre completo** de la última persona que publicó en el Muro. `WallController.latestAuthor` ya recibía `@ActorAutenticado UserId actorId` y no lo usaba — el hueco era del servicio, no del transporte. Se eligió el guard de `feed()` (`requireActorActivo`) y no el de `solicitarUrl()` (`requireActorPuedePublicar`): es una **lectura** del Muro, no una publicación. En la misma pasada, `contarMisPublicaciones()` (que tampoco tenía guard, `GET /api/v1/wall/mine`) recibió el mismo `requireActorActivo` — misma clase de falla en la misma clase, criterio de la lección 2 de E-42. Ver **E-50**. |
| CM-21 | **Las mutaciones de célula y cohorte devuelven la proyección de respuesta**, no la entidad: `CrearCelulaUseCase`/`ActualizarCelulaUseCase`/`AsignarMentorCelulaUseCase`/`QuitarMentorCelulaUseCase`/`ProgramarSesionCelulaUseCase`/`AsignarAprendizCelulaUseCase` devuelven `CelulaDetalle`, y `CrearCohorteUseCase`/`ActualizarCohorteUseCase`/`CambiarEstadoCohorteUseCase` devuelven `CohorteResumen` (los mismos records de `ConsultarCelulasUseCase`/`ConsultarCohortesUseCase`, sin duplicar la forma ni los mappers de respuesta). Motivo en §9.2. El caso de uso de consulta **no se retiró**: sigue sirviendo a `GET /admin/cells`, `GET /admin/cells/{id}`, `GET /admin/cohorts` y `GET /admin/cohorts/{id}`. |
| CM-19 | **`esModerador()` (en `PublicacionMuroService` y `ComentarioMuroService`) pasó de lanzar `NoSuchElementException` a devolver `false`** cuando el actor no existe en `usuarios`. Hallazgo real de `./mvnw clean test` corrido por el supervisor: `ComentarioMuroServiceTest.ocultarUnComentarioAjenoSinModerarFalla` esperaba 403 y recibía 404 porque el actor de prueba (`otro`) no tenía `UserSummary` stubeado. Investigado antes de tocar nada (instrucción explícita del supervisor): no era solo un olvido del test — `esModerador` es un predicado (`esXxx`, deberia devolver `boolean`) que lanzaba una excepción, rompiendo su propio contrato, y el orden real de los checks en `ocultar()` (primero existencia del recurso, despues existencia+rol del actor) hacía que un actor inexistente devolviera 404 "Actor no encontrado" — un mensaje que solo se alcanza si el comentario/publicación YA se confirmó que existe, filtrando esa existencia a quien prueba con un actor falso. Se corrigió el predicado para fallar cerrado a "no autorizado" (403) en vez de "no encontrado" (404): un actor que no existe simplemente no es moderador, nunca es un error de recurso. Se agregaron pruebas de regresión explícitas (`ocultarConActorInexistenteEsRechazadoComo403NoComo404`) en ambos `*ServiceTest`, no solo se relajó el assert existente. `requireActorActivo`/`requireAdmin` (el actor PRINCIPAL de una acción, no el que modera contenido ajeno) siguen devolviendo 404 vía `NoSuchElementException` si no existen — mismo patrón que el resto del repo (`TicketSoporteService.requireAdmin`), no tocado: la distinción es que esos SÍ son el sujeto de la operación, no un tercero cuyo rol solo se está consultando. |

---

## 6. Qué NO se construyó / preguntas abiertas

- **Ranking de células/aprendices** (CM-1) — depende de `points` y de una función SQL que combina módulos que no existen todavía (`academy`, `rocks` ya sí). Pregunta abierta real: ¿este módulo debería exponer un puerto de lectura (`community.api`) para que `points` arme el ranking con los nombres/avatares de célula, o al revés, `points` expone los puntajes y `community` arma la vista? No decidido — nota 2026-08-26: `CelulaFinder.celulaDeParticipante` (agregado por el agente de `points` en paralelo con este mismo trabajo) ya resuelve el sentido "dame la célula de un participante", pero deliberadamente sin `rankingPosition`/`coherenceScoreGroup` (esas siguen siendo Q-1/Q-1b de `docs/MODULO_POINTS.md`, sin resolver).
- **Asignación de aprendices a células** (CM-2) — **sigue bloqueada, confirmado de nuevo 2026-08-26** al construir el panel admin (#25, ver §8): `participantes_programa.celula_id` no tiene NINGÚN puerto de escritura en `users.api` (se confirmó no reinventar uno — `AssignMentorToTraineeUseCase` en `users` es el precedente exacto a replicar para célula, pero tocar `users` está fuera del alcance de este agente). `community` no escribe en una tabla que no es suya (CLAUDE.MD sec. 5.1) — la solución correcta es un puerto nuevo en `users.api` (ej. `AsignarCelulaAParticipanteUseCase`/`Port`), no un bypass.
- **Pickers de administración de mentores/aprendices sin célula** (CM-3) — **cerrado 2026-08-26**, ver §8: `users.api.ParticipacionProgramaFinder.usuariosActivosConRol`/`miembrosDeCelula` (agregados después de que se escribió esta sección) lo desbloquearon sin N+1.
- **`avatarUrl` en `users.api.UserSummary`** (CM-5) — **cerrado**: `UserSummary` ya expone `avatarUrl` (verificado 2026-08-26). `ConsultarPerfilUsuarioPort` sigue en uso donde ya estaba (no se migró en esta pasada, fuera del alcance puntual de #25/#17), pero los pickers nuevos del panel admin (§8) usan `UserSummaryFinder` directo, sin sumar otro consumidor de `ConsultarPerfilUsuarioPort`.
- **Flujo real de subida de media firmada** — el puerto (`SolicitarUrlSubidaMediaUseCase`) y el endpoint existen, pero como el `AlmacenamientoPort` es NoOp (faltan credenciales AWS, D-34), no hay forma de probarlo de punta a punta todavía. El wire de creación de posts sigue aceptando `{url, mimeType}` (CM-6) mientras tanto.
- **Contadores denormalizados de reacciones/comentarios** — hoy se calculan por request con una agregación SQL (CM-15). Si el volumen de publicaciones crece mucho, valdría medir antes de decidir si hace falta una columna cacheada — explícitamente no se tocó el esquema para esto ahora.
- **`@RequiresPermission`/`@PublicEndpoint` + test de reflexión** — el mecanismo no existe todavía en `shared/` (mismo bloqueante que el resto de los módulos ya construidos).
- **Filtro JWT real** — todos los controllers usan `X-Actor-Id` (bloqueante del usuario, CLAUDE.MD "Bloqueado").

---

## 7. Pruebas

| Tipo | Cobertura |
|---|---|
| Unitarias de dominio | `CohorteTest`, `CelulaTest`, `CategoriaMuroTest`, `PublicacionTest`, `ReaccionMuroTest` (regla de toggle, pura), `ComentarioTest`, `TestimonioTest` — sin Spring, sin Postgres. |
| Unitarias de `application/services` (Mockito) | `CohorteServiceTest`, `CelulaServiceTest`, `CategoriaMuroServiceTest`, `PublicacionMuroServiceTest`, `ComentarioMuroServiceTest`, `TestimonioServiceTest` — autorización negativa (no-admin → `NotAuthorizedException`, cuenta suspendida → `NotAuthorizedException`), reglas de negocio clave (transición de estado inválida, mentor sin perfil, categoría de sistema, toggle de reacción, moderación de publicaciones/comentarios ajenos). |
| Integración con Testcontainers (Postgres real) | `PublicacionPersistenceAdapterTest` (`feed` en sus 4 combinaciones con/sin cursor x con/sin categoría, `feedOculto` con/sin cursor) y `ComentarioPersistenceAdapterTest` (`pagina` con/sin cursor) — agregados al arreglar E-31 (ver abajo). Cubren el camino que los tests con mocks no pueden: el `prepare` real de Postgres. |
| Pendiente (otro agente) | Integración con Testcontainers contra Postgres real para lo que sigue sin cubrir: el `CAST(?  AS renaser.tipo_reaccion)` de `ReaccionMuroPersistenceAdapter`, el reemplazo transaccional de `medias_publicacion` en `PublicacionPersistenceAdapter.save()`, y las queries nativas cross-módulo (`participantes_programa`, `perfiles_mentor`, `usuarios`) contra el esquema real. |
| Autorización negativa (§0.3) | **Cerrado 2026-08-31 — ver §9.1.** Cada método protegido de los 6 servicios tiene su prueba de 403 por rol sin permiso y su prueba de 403 por cuenta `SUSPENDED`. Sigue pendiente (bloqueado, ver §6) el test de reflexión de `@RequiresPermission`: el mecanismo todavía no existe en `shared/`. |

### 7.1 E-31 — `feed`/`feedOculto`/`pagina` en 500 contra Postgres real

Sondeo manual contra Postgres real (no detectado por los tests con mocks) encontró que `GET /wall`, `GET /wall/hidden` y `GET /wall/{id}/comments` devolvían 500: `ERROR: could not determine data type of parameter $1`. Causa: las tres consultas usaban el patrón de "parámetro opcional" `(:x IS NULL OR col < :x)` en JPQL — Postgres no puede inferir el tipo de un parámetro que aparece **solo** dentro de `? IS NULL`, sin ningún otro contexto tipado en esa posición, así que el `prepare` fallaba antes de ejecutar. Rompía justo en el caso más común: pedir la primera página (sin cursor).

**Alcance del defecto:** 3 queries, las 3 que ya usaban el patrón — no había más ocurrencias de `IS NULL OR` en `community` (`SpringDataPublicacionRepository.feed`/`feedOculto`, `SpringDataComentarioRepository.pagina`).

**Arreglo elegido:** separar cada consulta en un método de repositorio por combinación de filtro opcional (`feedSinCursorSinCategoria`/`feedSinCursorConCategoria`/`feedConCursorSinCategoria`/`feedConCursorConCategoria` para `feed` — dos filtros opcionales, 4 combinaciones; `feedOcultoSinCursor`/`feedOcultoConCursor`; `paginaSinCursor`/`paginaConCursor`), con el adaptador de persistencia (`PublicacionPersistenceAdapter`, `ComentarioPersistenceAdapter`) eligiendo cuál llamar según qué venga `null`. Se prefirió esto sobre `CAST(:cursor AS ...)` en el JPQL o una query nativa con `?::timestamptz`: cada método queda con SQL simple, sin casts que dependan de que Hibernate los traduzca bien, y es el criterio que ya pide CLAUDE.MD §5.2 (RabbitMQ vs Kafka) y §9 en general — preferir lo explícito y sin magia en el hot path, que es exactamente lo que es el feed del Muro.

Detalle completo, síntoma literal y cómo evitarlo: `docs/BITACORA_ERRORES.md` **E-31**.

---

## 8. 2026-08-26 — Panel admin de células/cohortes (#25) y puerto de publicación en el Muro (Hueco #17)

Encargo nuevo, dos partes, ambas cerradas salvo lo explícitamente documentado como bloqueado.

### 8.1 Panel admin de células (#25, `docs/PLAN_INTEGRACION_FRONTEND.md` §5)

Construido usando `users.api.ParticipacionProgramaFinder.usuariosActivosConRol`/`miembrosDeCelula` — puertos EN LOTE que no existían cuando se escribió CM-2/CM-3 (§6) y que desbloquean los pickers sin N+1:

- **`GET /api/v1/admin/cells/dashboard`** (`ConsultarDashboardCelulasUseCase`) — todas las células, de cualquier cohorte, con su cohorte ya resuelta (`nombre`/`estado`). Ruta propia, no `GET /api/v1/admin/cells` a secas: ese endpoint ya existe y exige `cohortId` (`listarPorCohorte`); reutilizar la misma ruta sin parámetro habría hecho que un mismo endpoint devolviera dos shapes distintas según presencia de query param, lo que se prefirió evitar. Solo ADMIN/ALCHEMIST.
- **`GET /api/v1/admin/cells/mentores-disponibles`** / **`/mentores`** (`ConsultarCandidatosCelulaUseCase`) — mentores ACTIVOS sin/con marca de célula actual. `LoadCelulaPort.todas()` resuelve quién ya lidera sin tocar `perfiles_mentor` (tabla ajena), mismo criterio que ya usaba `asignar()`.
- **`GET /api/v1/admin/cells/aprendices-disponibles`** — aprendices ACTIVOS sin célula, **alcance GLOBAL, no por cohorte**: un aprendiz sin célula no tiene ninguna columna que diga a qué cohorte "pertenece" todavía (`participantes_programa` no tiene `cohorte_id`, solo `celula_id`) — esa relación nace recién al asignarlo. No se inventó una columna que no existe.
- Construido recorriendo las células existentes (acotadas) para armar el conjunto de "ya asignados", nunca participante por participante — mismo criterio anti-N+1 que `PorcentajeRocasFinder` (D-43).

**Lo que sigue sin construir, documentado explícitamente:**

- **Asignar/quitar aprendiz de célula.** Escribe `participantes_programa.celula_id`, tabla que no es de `community` (CLAUDE.MD sec. 5.1: un módulo no escribe la tabla de otro). `users.api` no expone ningún puerto de escritura para esto — el precedente exacto a replicar es `users.application.ports.in.AssignMentorToTraineeUseCase` (asigna `mentor_id`), pero construir su equivalente para `celula_id` requiere tocar el módulo `users`, fuera del alcance de este encargo (acotado a `community` + una extensión mínima en `rocks`). Queda para quien tenga alcance sobre `users`.
- **`rankingPosition`/`coherenceScoreGroup`** en las respuestas del dashboard — siguen sin puerto público en `points` (`points.api` solo expone finders de porcentaje EN LOTE de hábitos/rocas/cursos, ninguno de ranking por célula). Mismo Q-1/Q-1b de `docs/MODULO_POINTS.md` que ya documenta el gap #24 (agregador de ranking, cerrado en paralelo por otro agente hoy mismo — ver esa entrada en `docs/PLAN_INTEGRACION_FRONTEND.md`).
- Los campos de `CellDetail`/`CellTrainee` que el frontend (`C:\renaserPlayStore\src\services\cells.ts`) espera pero que dependen de un `TraineeProfile` que `users` todavía no construyó como dominio (`programDay`, `currentPhase`, `startDate`, `expectedGraduationDate`, `goalType`, `coherenceScore` — gap #1) — no se inventaron valores default para rellenarlos.

### 8.2 `community.api.PublicarEnMuroPort` — cierre del Hueco #17 del lado de `community`

`docs/MODULO_ROCKS.md` §11.2 documentó el gap: `rocks` no tenía forma de publicar en el Muro la evidencia de una Roca completada porque `community.api` no exponía ningún puerto para que OTRO módulo creara una publicación. Se construyó el "Camino B" que ese documento dejó planteado:

- **`PublicarEnMuroPort.publicarDesdeEvidencia(...)`** (nuevo, `community/api/PublicarEnMuroPort.java`) — recibe `autorId`/`texto`/`bucket`/`ruta`/`mime` (evidencia YA subida por el módulo llamador) y crea una `Publicacion` real vía un nuevo factory de dominio, `Publicacion.publicarAutomatica(...)`, que produce **`HITO_AUTOMATICO`** — nunca `MANUAL` (reservado a un POST directo del autor) ni `GUERRERO_CAIDO`. Sin categoría: clasificar en una categoría del Muro es una decisión manual del autor, no aplica a un post automático.
- Implementado en `PublicacionMuroService` (ya implementaba `PublicarUseCase` y el resto de casos de uso de publicación) — reutiliza las mismas guardas de autorización que `publicar()` (`requireActorPuedePublicar`: cuenta activa + rol habilitado). Un módulo que llama esto con un `autorId` suspendido recibe la misma `NotAuthorizedException` que un POST normal — no hay bypass por venir de otro módulo.
- **Consumidor:** `rocks.application.services.RocaDiariaService.completar()` — nuevo campo `publishedToWall` en `CompletarRocaDiariaCommand`/`CompletarRocaDiariaRequest` (default `false`, a diferencia de `esPrincipal` que ya existía y por compatibilidad default a `true`). Solo válido para evidencia visual (`FOTO`/`VIDEO`/`CAPTURA` — el Muro nunca acepta audio/texto puro, mismo CHECK que `MediaPublicacion`); `TEXTO`/`AUDIO` con `publishedToWall=true` se rechaza con 400 en el constructor del comando, antes de tocar la base. La leyenda del post se arma con el título de la Roca (`"Complete mi Roca: " + roca.titulo()`) — no hay campo de "caption" propio en el flujo de evidencia de rocas, así que se usa el dato que ya existe en vez de inventar uno nuevo. El mime se infiere del tipo de evidencia (`FOTO`/`CAPTURA` → `image/jpeg`, `VIDEO` → `video/mp4`) porque el comando de evidencia de rocas no transporta un mime propio.
- Todo dentro de la MISMA transacción que completa la roca (CLAUDE.MD sec. 9.1): si `community` rechaza la publicación, la roca tampoco queda completada.
- **Único archivo de negocio de `rocks` tocado además del comando/DTO/controller de completar evidencia**: `RocaDiariaService.java` (inyecta el nuevo puerto, agrega el método privado `publicarEnMuro`). Ningún otro caso de uso de `rocks` se reabrió.

### 8.3 Pruebas agregadas

| Archivo | Cobertura nueva |
|---|---|
| `PublicacionMuroServiceTest` | `publicarDesdeEvidenciaCreaUnaPublicacionHitoAutomatico`, `publicarDesdeEvidenciaConActorSuspendidoFalla` |
| `CelulaServiceTest` | `dashboardComoMentorEsRechazado`, `dashboardListaCelulasDeTodasLasCohortesConSuCohorte`, `mentoresDisponiblesComoMentorEsRechazado`, `mentoresDisponiblesExcluyeAQuienesYaLideranUnaCelula`, `mentoresMarcaLaCelulaActualDeQuienYaLidera`, `aprendicesDisponiblesComoMentorEsRechazado`, `aprendicesDisponiblesExcluyeAQuienesYaTienenCelula` |
| `RocaDiariaServiceTest` (módulo `rocks`) | `publishedToWallConEvidenciaNoVisualEsRechazado`, `publishedToWallLlamaAlPuertoDeCommunity`, `publishedToWallFalseNoLlamaANadie` |

No se corrió `./mvnw clean test` (regla del encargo: lo corre el supervisor junto con otros agentes en paralelo). Riesgo concreto a vigilar: este mismo módulo (`community`) estaba siendo tocado en simultáneo por el agente de `points` (agregó `CelulaFinder.celulaDeParticipante` y el campo `participacionProgramaFinder` a `CelulaServiceTest` mientras este trabajo estaba en curso) — se verificó en vivo que ambos cambios conviven sin pisarse (mismo constructor de `CelulaService`, sin colisión de nombres), pero vale una relectura rápida de `CelulaService.java`/`CelulaServiceTest.java` si el build falla justo ahí.

## Auditoría de arquitectura (2026-08-28) — agente automático

Auditoría de solo lectura de `src/main/java/com/renaser/os/community/`. No se corrió `./mvnw` (fuera de alcance del encargo). 45 endpoints REST confirmados (`grep` de las 5 anotaciones `@*Mapping` en `infrastructure/adapter/in/rest/`).

**1. Hallazgo de seguridad — `TestimonioController` no migró de `X-Actor-Id` a `@ActorAutenticado`**

`src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/testimonio/TestimonioController.java:49-51` sigue leyendo el actor con:

```java
@RequestHeader(value = "X-Actor-Id", required = false) String actorId
```

Es el **único** controller de `community` que hace esto — los otros 10 (`WallController`, `WallCommentController`, `CelulaAdminController`, `MiCelulaController`, `WallCategoryAdminController`, `CohorteAdminController`, etc.) usan `@ActorAutenticado UserId actorId` (`src/main/java/com/renaser/os/shared/web/security/ActorAutenticado.java`), que resuelve primero la sesión y solo cae al header como respaldo (`ActorAutenticadoArgumentResolver.java:18,28`). El commit `b824c4b` ("Auth: los 64 controllers pasan de X-Actor-Id a la sesion propia") no alcanzó a este archivo. Efecto concreto: `POST /api/v1/testimonios` sin `wallPostId` acepta un `X-Actor-Id` arbitrario (o ninguno — `actorId` queda `null` y el testimonio se crea sin autor, ver línea 59) sin pasar por la sesión real; con `wallPostId` (rama de promoción, exige rol ADMIN/ALCHEMIST) el chequeo de rol se hace en `TestimonioService.requireAdmin` contra el `actorId` que vino del header, es decir, un header falsificado con el UUID de un admin real bypasea la sesión. Recomendado: cambiar a `@ActorAutenticado UserId actorId` (con `required=false` si el flujo anónimo de creación manual de testimonios es intencional) antes de dar el módulo por cerrado en seguridad.

**2. Controller con lógica de despacho por `if` — mismo `TestimonioController`**

`crear()` (líneas 48-63) decide, con un `if (request.wallPostId() != null && !request.wallPostId().isBlank())`, si invoca `promoverUseCase` o `crearUseCase` — dos casos de uso distintos elegidos por contenido del body. La regla del proyecto prohíbe explícitamente "orquestar varios casos de uso" y "`if` que decida reglas de negocio" en el controller (CLAUDE.md §5.4.6). No es catastrófico (la decisión es de enrutamiento, no de negocio de dominio), pero técnicamente el controller sabe algo que debería resolver un solo caso de uso o dos endpoints separados (`POST /testimonios` vs `POST /testimonios/from-post`).

**3. Dos clases de `application/services/` superan el techo de 300 líneas / 10 métodos públicos**

| Clase | Líneas | Interfaces `implements` | Métodos públicos |
|---|---|---|---|
| `CelulaService.java` | 417 | 12 (`CrearCelulaUseCase`, `ActualizarCelulaUseCase`, `AsignarMentorCelulaUseCase`, `QuitarMentorCelulaUseCase`, `ProgramarSesionCelulaUseCase`, `EliminarCelulaUseCase`, `ConsultarCelulasUseCase`, `ConsultarMiCelulaUseCase`, `ConsultarDashboardCelulasUseCase`, `ConsultarCandidatosCelulaUseCase`, `CelulaFinder`, `AsignarAprendizCelulaUseCase`, `QuitarAprendizCelulaUseCase`) | 18 (`crear`, `actualizar`, `asignar`×2, `quitar`×2, `programar`, `eliminar`, `listarPorCohorte`, `obtener`, `miCelula`, `misCompaneros`, `dashboard`, `mentoresDisponibles`, `mentores`, `aprendicesDisponibles`, `mentorDe`, `celulaDeParticipante`) |
| `PublicacionMuroService.java` | 332 | 9 (`PublicarUseCase`, `EditarPublicacionUseCase`, `OcultarPublicacionUseCase`, `RestaurarPublicacionUseCase`, `EliminarPublicacionUseCase`, `ReaccionarUseCase`, `ConsultarFeedUseCase`, `SolicitarUrlSubidaMediaUseCase`, `PublicarEnMuroPort`) | 12 (`publicar`, `editar`, `ocultar`, `restaurar`, `eliminarPermanente`, `reaccionar`, `feed`, `feedOculto`, `contarMisPublicaciones`, `ultimoAutor`, `solicitarUrl`, `publicarDesdeEvidencia`) |

Ambas violan el techo duro de CLAUDE.md §5.4.8 (clase ≤300 líneas, ≤10 métodos públicos por clase) y contradicen el principio de §5.4.8 SRP ("una clase por caso de uso... no un `UserService` con 30 métodos"). Cada método individual sigue siendo corto y legible (ninguno pasa de ~40 líneas), así que no es un problema de complejidad ciclomática — es que un solo `@Service` implementa demasiadas interfaces `port/in`. Nota: esto NO rompe `ArchitectureTest`/Spring Modulith (los límites de Modulith son por paquete, no por clase), así que un build en verde no lo habría detectado.

**4. `domain/` — cumple, ya subdividido correctamente por agregado**

15 clases en `domain/model/`, repartidas en 5 subcarpetas por agregado real (no por capa): `categoria/` (1: `CategoriaMuro`), `celula/` (2: `Celula`, `CelulaId`), `cohorte/` (3: `Cohorte`, `CohorteId`, `EstadoCohorte`), `publicacion/` (8: `Publicacion`, `PublicacionId`, `Comentario`, `ComentarioId`, `ReaccionMuro`, `MediaPublicacion`, `TipoPublicacion`, `TipoReaccion`), `testimonio/` (2: `Testimonio`, `TestimonioId`). Ninguna subcarpeta supera 10 clases. `Comentario`/`ReaccionMuro` conviven con `Publicacion` en la misma carpeta con criterio correcto: son entidades/value objects que no tienen sentido sin la publicación que referencian (`publicacionId` obligatorio en ambos constructores), no agregados independientes — no es el "por si acaso" que la regla prohíbe.

Sin imports prohibidos: `grep` de `org.springframework.*`/`jakarta.persistence.*`/paquetes `adapter`/`infrastructure` sobre `domain/` no devolvió coincidencias. Sin nombres prohibidos (`Util`/`Helper`/`Manager`/`Processor`): ninguna clase de `community` los usa.

**5. Controllers — muestra de 6 revisados, "controller tonto" cumplido salvo el hallazgo 2**

> **Corregido 2026-08-31.** Esta afirmación era **falsa** para 9 handlers y por eso el módulo figuraba como limpio: `CelulaAdminController` (`crear`, `actualizar`, `asignarMentor`, `quitarMentor`, `asignarAprendiz`, `programarSesion`) y `CohorteAdminController` (`crear`, `actualizar`, `cambiarEstado`) mutaban con un caso de uso y **después llamaban a un segundo** (`consultarUseCase.obtener(...)`) para armar la respuesta — exactamente lo que CLAUDE.MD §5.4.6 prohíbe ("si hacen falta dos, falta un caso de uso que los componga"), con el agravante de que las dos operaciones caían en **transacciones distintas** y la respuesta podía reflejar un estado ya cambiado por otro actor. Resuelto en §9.2: la mutación devuelve la proyección dentro de su propia transacción. La frase de abajo describe el estado **posterior** a esa corrección.

`CelulaAdminController` (11 casos de uso inyectados), `WallController` (8 casos de uso), `WallCommentController`, `WallCategoryAdminController`, `CohorteAdminController`, `MiCelulaController`: cada endpoint deserializa, valida (`@Valid`), llama un único caso de uso y mapea a DTO de salida — ninguno inyecta un puerto `out`, tiene `@Transactional` ni contiene un `if` de negocio (los `switch` de `parseTipoReaccion`/`parseEstado` en `WallController`/`CohorteAdminController` son mapeo de formato HTTP→enum de dominio, no reglas de negocio). Observación menor: `CelulaAdminController` (11 parámetros de constructor) y `WallController` (8) exceden el techo de "≤4 parámetros" de CLAUDE.md §5.4.8 si se aplica literalmente a constructores — es consecuencia directa de que cada controller agrupa **todos** los casos de uso de su recurso HTTP, patrón consistente en todo el módulo y no exclusivo de `community`; se documenta pero no se considera un hallazgo grave por sí solo.

**6. Los 5 archivos más grandes del módulo**

1. `application/services/CelulaService.java` — 417 líneas (hallazgo 3)
2. `application/services/PublicacionMuroService.java` — 332 líneas (hallazgo 3)
3. `infrastructure/adapter/in/rest/celula/CelulaAdminController.java` — 185 líneas
4. `infrastructure/adapter/in/rest/publicacion/WallController.java` — 171 líneas
5. `infrastructure/adapter/out/persistence/publicacion/PublicacionPersistenceAdapter.java` — 164 líneas

## 9. 2026-08-31 — Autorización negativa completa, `latest-author` cerrado y 9 handlers corregidos

Tres trabajos, todos cerrados. `./mvnw clean test`: **`Tests run: 1729, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS** (21:36 min, 251 clases). Son **59 pruebas nuevas** sobre las 1670 de la base del worktree, medido corriendo la suite completa ANTES de tocar nada (también en verde) y después.

### 9.1 Autorización negativa: de 17/70 a 72/72

CLAUDE.MD §0.3 pide, para **cada** endpoint protegido, una prueba de que un rol sin permiso recibe 403 y otra de que un actor `SUSPENDED` recibe 403 aunque su token sea válido. Se midió primero, método por método, en vez de asumir.

**Unidad de medida:** cada método público de servicio que tiene guard, contado por los dos chequeos que exige §0.3. Cuando el método no distingue rol (guard `requireActorActivo` a secas, o todos los roles habilitados como en `publicar()`), cuenta **una** unidad en vez de dos.

| Servicio | Métodos protegidos | Unidades | Cubiertas antes | Cubiertas después |
|---|---|---|---|---|
| `CategoriaMuroService` | 5 | 10 | 1 | 10 |
| `CohorteService` | 6 | 12 | 2 | 12 |
| `CelulaService` | 16 | 30 | 6 | 30 |
| `PublicacionMuroService` | 10 → **12** (CM-20) | 13 → **15** | 4 | 15 |
| `ComentarioMuroService` | 3 | 3 | 3 | 3 |
| `TestimonioService` | 1 | 2 | 1 | 2 |
| **Total** | **41 → 43** | **70 → 72** | **17 (24 %)** | **72 (100 %)** |

El diagnóstico previo era correcto: **los guards ya estaban; lo que faltaba era verificarlos.** Se había probado un método representativo por servicio (`crear` en categorías y cohortes, `crear`/pickers en células) y no sus hermanos. Ninguna de las 59 pruebas nuevas encontró un guard faltante en esos métodos — el único hueco real de autorización apareció en los dos métodos que directamente no tenían guard (§9.2).

Las pruebas viven **dentro del servicio** (`src/test/java/com/renaser/os/community/application/services/*ServiceTest.java`), no en el controller: es donde vive el guard, y es la forma que ya usan los módulos al 100 % (`points`, `phasecontracts`, `evidence`) — `@DisplayName` que nombra el método y el motivo, `assertThatThrownBy(...).isInstanceOf(NotAuthorizedException.class)` y un `verify(port, never())` que comprueba que el efecto no ocurrió.

Detalle de por qué "cuenta SUSPENDIDA" no se prueba con un usuario cualquiera suspendido: donde el guard también mira el rol, el actor de la prueba es un **ADMIN suspendido** (`adminSuspendido`). Con un TRAINEE suspendido la prueba pasaría por el motivo equivocado (el rol) y no probaría nada sobre la suspensión.

### 9.2 `GET /api/v1/wall/latest-author` no tenía ningún guard (E-50)

`WallController.latestAuthor` recibía `@ActorAutenticado UserId actorId` y **nunca lo usaba**; `PublicacionMuroService.ultimoAutor()` ni siquiera lo pedía. Efecto real: cualquiera —incluida una cuenta suspendida— obtenía el **nombre completo** de la última persona que publicó en el Muro. No estaba documentado como decisión en ningún lado (a diferencia de `listarPublicas()` del catálogo de categorías o de `crear()` de testimonios, que sí son públicos **a propósito** y así está escrito).

Se le puso el guard de sus hermanos y su prueba negativa — decisión completa en **CM-20**, síntoma y lección en **E-50** de `docs/BITACORA_ERRORES.md`. `contarMisPublicaciones()` (`GET /api/v1/wall/mine`) tenía el mismo hueco y se corrigió en la misma pasada.

### 9.3 Los 9 handlers que orquestaban dos casos de uso

`CelulaAdminController` (6) y `CohorteAdminController` (3) hacían:

```java
actualizarUseCase.actualizar(...);                                    // transacción 1
return CelulaDetalleResponse.from(consultarUseCase.obtener(...));     // transacción 2
```

Dos problemas, no uno: CLAUDE.MD §5.4.6 lo prohíbe explícitamente ("si hacen falta dos, falta un caso de uso que los componga"), y **entre las dos transacciones la fila puede cambiar** — la respuesta podía describir un estado que ya no era el que la mutación produjo.

**Arreglo:** la mutación devuelve lo que la respuesta necesita, armado dentro de su propia transacción (CM-21). Concretamente, `CelulaService.aDetalle(Celula)` y `CohorteService.aResumen(Cohorte)` son ahora métodos privados que usan tanto la consulta como las mutaciones — la forma de la respuesta es literalmente la misma, así que `CelulaDetalleResponse.from(...)`/`CohorteResponse.from(...)` no cambiaron y **el contrato REST que la app ya consume queda idéntico** (§8 de CLAUDE.MD).

Los 9 handlers quedaron en una sola expresión con un solo caso de uso. `AsignarAprendizCelulaUseCase.asignar` pasó de `void` a `CelulaDetalle`: la lista de miembros que devuelve ya incluye al aprendiz recién asignado porque la lectura ocurre después de la escritura y dentro de la misma transacción.

**Efecto colateral aceptado:** `CelulaService` sube de 417 a 426 líneas, con lo que sigue por encima del techo de 300 de CLAUDE.MD §5.4.8 — hallazgo 3 de la auditoría del 2026-08-28, que **no** se cerró acá (partir ese servicio es un trabajo aparte, y hacerlo en la misma pasada que un cambio de contrato de 9 endpoints habría mezclado dos riesgos distintos en un solo cambio).

### 9.4 Lo que NO se hizo

- **`ComentarioMuroService.pagina(publicacionId, cursor)` no recibe actor y no tiene guard de actor** (`GET /api/v1/wall/{id}/comments`). Solo comprueba que la publicación sea visible. No se tocó: cambiarlo es el mismo tipo de decisión que CM-20 pero sobre un endpoint del camino de lectura más caliente del Muro, y **no está claro si es deliberado** (el código viejo tampoco pedía sesión ahí). Queda como pregunta abierta para el dueño del producto, no como bug silencioso.
- **Test de reflexión de `@RequiresPermission`/`@PublicEndpoint`** — sigue bloqueado: el mecanismo no existe en `shared/` (§6).
- **`TestimonioController` sigue con `X-Actor-Id`** (hallazgo 1 de la auditoría del 2026-08-28). No entra en el alcance de este encargo; sigue abierto.
- **Partir `CelulaService`/`PublicacionMuroService`** (hallazgo 3 de esa misma auditoría) — sigue abierto.
