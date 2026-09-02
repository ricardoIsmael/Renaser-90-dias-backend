# Quién reaccionó a una publicación del Muro (modal "Reacciones del post")

**Fecha:** 2026-09-02
**Repos tocados:** backend (`renaser-backend`, este repo) y frontend
(`C:\Diseño Opusplan tab 01\renaser-rn\renaser`).

## Qué pedía la tarea

El modal "Reacciones del post" del Muro (pestañas TODOS/LIKES/DISLIKES + lista de quién
reaccionó) tenía la lista escrita a mano en el frontend (`REACTION_USERS_MOCK`). El dueño del
proyecto pidió explícitamente que esa lista deje de ser un dato de prueba. Faltaba el endpoint:
`reaccionar` (toggle) ya existía, pero ningún endpoint devolvía *quiénes* reaccionaron.

## Qué construí — backend

1. **`ReaccionMuroPort.listarDe(PublicacionId)`** (nuevo método, mismo puerto que ya tenía
   `contarPorTipo`/`deUsuario`/etc.) — devuelve todas las filas de `reacciones_muro` de una
   publicación, más reciente primero. Implementado en `ReaccionMuroPersistenceAdapter` con una
   consulta nativa (`SELECT usuario_id, tipo ... WHERE publicacion_id = ?1 ORDER BY creado_en
   DESC`), mismo estilo que el resto del adaptador (sin `@Entity`, la tabla no tiene identidad
   propia).
2. **`ConsultarReaccionesUseCase`** (nuevo puerto `in`, paquete
   `community/application/ports/in/publicacion/`) — `reacciones(actorId, publicacionId) ->
   List<ReaccionVista>`. `ReaccionVista` lleva `usuarioId`, `nombre`, `avatarUrl`, `rol`
   (`users.api.UserRole`) y `tipo` — nada más (sin email, sin nada que el modal no pinte).
3. **`PublicacionMuroService.reacciones(...)`** implementa el caso de uso, con la misma puerta
   de seguridad que `reaccionar()`: `requireVisible(publicacionId)` (404 si la publicación no
   existe o está oculta) y **después** `requireActorHabilitado(actorId)` (403 fail-closed si el
   actor no existe o está suspendido — nunca delata si el recurso existe). Resuelve los datos de
   persona con **una sola llamada en lote**: `userSummaryFinder.findByIds(...)` sobre el set de
   `usuarioId` distintos de la lista de reacciones.
4. **`GET /api/v1/wall/{id}/reactions`** en `WallController`, con `@RequiresPermission(USE_APP)`
   — el mismo permiso que `feed()`/`reaccionar()`, no el patrón "sin clasificar" que tiene hoy
   `WallCommentController#listar` (ese es un TODO de auth fase 4 documentado, no algo a copiar:
   lo dejé explícito en un comentario en el controller para que no se repita el error al leer el
   código más adelante).
5. **`WallReactionsResponse`/`WallReactionItemResponse`** — DTOs de salida. `type` sigue el
   criterio D-36 ya establecido (`LIKE`/`DISLIKE` en el wire, `ME_GUSTA`/`NO_ME_GUSTA` en
   dominio). `role` viaja tal cual el nombre del enum (`TRAINEE`/`MENTOR`/`MENTOR_LEAD`/
   `ADMIN`/`ALCHEMIST`), sin traducir — la traducción a español vive en el frontend (ver abajo).

### Cómo evité el N+1

- **Lista de reacciones de la publicación:** una sola consulta (`listarDe`), sin importar
  cuántas reacciones tenga el post.
- **Nombre/avatar/rol de cada persona:** una sola consulta en lote
  (`UserSummaryFinder.findByIds`), nunca `findById` en un bucle. Cubierto por el test
  `reaccionesResuelveDatosDeUsuarioEnLote` (`PublicacionMuroServiceTest`), que verifica
  explícitamente `findByIds` una vez y `findById` cero veces para los reactores.
- **Lo que NO batcheé, a propósito, y por qué está bien:** a diferencia del feed (que pagina
  hasta 20 publicaciones por request y por eso *sí* necesita las versiones en lote de
  `contarPorTipoDeVarias`/`deUsuarioEnVarias`), este endpoint resuelve **una sola publicación
  por request** — no hay "N publicaciones" sobre las que multiplicar consultas. El único vector
  de N+1 real era "una consulta de usuario por reactor", y ese es el que se cerró.

## Qué expone `users.api` — y qué NO alcanzó

`users.api.UserSummaryFinder.findByIds` alcanzó para nombre, avatar y rol en lote — eso cubre el
avatar y el "quién es" de cada fila. **No alcanzó para la célula** ("Célula 04" en el mock del
diseño):

- La célula de un participante vive en `participantes_programa.celula_id` (tabla de `users`),
  resuelta hoy vía `users.api.ParticipacionProgramaFinder.deParticipante(UserId)` — **solo de a
  uno**. No existe una versión en lote ("dado un conjunto de usuarios, la célula de cada uno")
  en el contrato público de `users`.
- El nombre de la célula (`community.celulas.nombre`) sí es de `community`, pero sin el primer
  dato (celulaId por usuario, en lote) no hay nada que resolver con él.
- **No agregué ese método a `users.api`** porque la instrucción de la tarea fue explícita:
  reportar el hueco, no meter mano en `users`. Llamar a `deParticipante` una vez por reactor
  hubiera sido exactamente el N+1 que la tarea pidió evitar, así que preferí no hacerlo y usar
  el rol como subtítulo (ver frontend) en vez de inventar una consulta directa a
  `participantes_programa` desde `community` (eso además violaría D-41: "ningún módulo consulta
  la tabla de otro de frente", ya cerrado una vez en este módulo).
- **Lo que pediría, si esto se retoma:** `ParticipacionProgramaFinder.celulasDeUsuarios(Collection<UserId>)`
  (batch) en `users.api`, o que `UserSummary` sume `cellName` directamente. Cualquiera de las
  dos cierra el hueco sin que `community` vuelva a tocar la tabla ajena.

## Forma real de la respuesta

```json
GET /api/v1/wall/{id}/reactions

{
  "reactions": [
    {
      "userId": "af4984b2-bf75-4b56-8f1a-f8d6b957d2ab",
      "name": "Nombre Apellido",
      "avatarUrl": null,
      "role": "TRAINEE",
      "type": "LIKE"
    }
  ]
}
```

Sin conteos ni paginación: los conteos de las tres pestañas salen de contar esta misma lista del
lado del cliente (nunca se desincronizan porque no hay dos fuentes), y el modal no pagina (es un
`ScrollView` de altura fija, no una lista infinita como el feed).

**No pude verificar la forma en vivo contra el backend levantado**, porque el `X-Actor-Id` de
aprendiz que tengo (`af4984b2-bf75-4b56-8f1a-f8d6b957d2ab`) no tiene ninguna publicación con
reacciones reales a mano para probar contra `localhost:8080` sin antes reaccionar algo — y el
backend corriendo es el código de ANTES de este cambio, así que el endpoint nuevo no existe ahí
todavía (esperable, avisado de antemano). La forma de arriba sale de leer
`WallReactionItemResponse`/`WallReactionsResponse` directamente, no de una respuesta real capturada.

## Qué construí — frontend

Archivos nuevos:

- `src/features/community/hooks/useWallReactions.ts` — hook chico (`reacciones`, `cargando`,
  `error`, `cargarReacciones(postId)`), mismo patrón que `useWallFeed.cargarComentarios`: se
  pide bajo demanda, no en el render de cada post. A diferencia de los comentarios (que se
  cachean por post), acá se vuelve a pedir cada vez que se abre el modal — las reacciones
  cambian mientras la persona navega el muro y el modal es una vista puntual, no algo que quede
  montado.

Archivos editados:

- `src/features/community/types/community.types.ts` — `WallReactionItem`/`WallReactionsPage`
  (forma cruda del backend, inglés).
- `src/features/community/api/wallSchemas.ts` — `wallReactionItemSchema`/`wallReactionsPageSchema`
  (zod, `.passthrough()`, mismo criterio que el resto del archivo).
- `src/features/community/api/wallApi.ts` — `obtenerReacciones(postId)`.
- `src/features/community/api/wallMappers.ts` — `mapearReaccion(item): ReactionUser`. Exporté
  `AVATAR_POR_DEFECTO` (antes privado del archivo) para reusarlo acá: el diseño pinta el avatar
  como emoji (`<Text>`, no `<Image>`), igual que ya resolvieron `mapearPublicacion`/
  `mapearComentario`, así que no hay nada nuevo que inventar ahí. Agregué `ETIQUETA_ROL`
  (duplicada de `chat/api/chatMappers.ts`, mismo criterio de "cada feature dueña de su propia
  traducción" que ya usa esa segunda copia) para traducir `role` a español como subtítulo
  ("Aprendiz"/"Mentor"/"Líder de Mentores"/"Administrador"/"Alquimista") — **no** "Célula 04",
  por el hueco de `users.api` explicado arriba.
- `src/screens/ComunidadScreen.tsx` — único archivo de pantalla tocado. Cambios, todos de capa de
  datos, ningún `StyleSheet` ni JSX estructural nuevo:
  1. Import del hook + llamada (`reactionUsers`/`cargandoReacciones`/`errorReacciones`/
     `cargarReacciones`).
  2. Se saca `REACTION_USERS_MOCK` (quedaba muerto).
  3. `filteredReactions` ahora filtra `reactionUsers` (antes el mock).
  4. El `onPress` que abre el modal ahora también llama `cargarReacciones(post.id)`.
  5. Los tres contadores de pestaña (`TODOS (4)`, `LIKES (3)`, `DISLIKES (1)`) pasan a
     `TODOS ({reactionUsers.length})`, etc. — reales, no hardcodeados.
  6. Dentro del `ScrollView` de la lista, se agregan los tres estados (cargando/error/vacío) con
     los mismos tokens de texto que ya usan los estados del feed principal más arriba en la
     misma pantalla (`t.micro`, `c.textSoft`, `'#f28e8e'` para error) — ningún componente nuevo.
     Una publicación sin reacciones muestra "Todavía nadie reaccionó a esta publicación.", no un
     error.

## `npx tsc --noEmit`

Corrido desde `C:\Diseño Opusplan tab 01\renaser-rn\renaser`: **exit code 0, sin salida** (cero
errores de tipos) sobre el árbol completo, incluyendo los archivos nuevos/tocados de esta
integración.

## Para la bitácora

- No hubo ningún error de configuración ni bug sorpresa en este cambio — es la primera vez que
  se agrega este endpoint, no una repetición de un problema ya conocido. Nada que registrar en
  `docs/BITACORA_ERRORES.md`.

## Para el registro de decisiones

- **D-XX (a numerar por quien mantiene `docs/MODULOS_A_AVANZAR.md` §8):** el subtítulo del modal
  "Reacciones del post" usa el **rol** de la persona (Aprendiz/Mentor/...), no su **célula**,
  porque `users.api` no expone célula-por-usuario en lote y agregarla estaba fuera de alcance de
  esta tarea (no tocar `users`). Si se quiere el subtítulo de célula tal como lo muestra el
  diseño original, hace falta primero un método en lote en `users.api`
  (`ParticipacionProgramaFinder.celulasDeUsuarios` o que `UserSummary` sume `cellName`).
- **Permiso del nuevo endpoint:** `GET /api/v1/wall/{id}/reactions` usa `Permission.USE_APP`,
  igual que `GET /api/v1/wall` y `POST /api/v1/wall/{id}/react` — no se copió el patrón "sin
  clasificar" de `WallCommentController#listar` (ese es un hueco de auth fase 4 ya documentado,
  no un criterio a propagar a código nuevo).

## Riesgos que le dejo a quien verifique

- **No corrí `./mvnw clean test`** (regla explícita de esta tarea: "yo compilo y verifico"). Los
  archivos nuevos/tocados son:
  - `ReaccionMuroPort.java`, `ReaccionMuroPersistenceAdapter.java`
  - `ConsultarReaccionesUseCase.java` (nuevo)
  - `PublicacionMuroService.java`
  - `WallController.java`
  - `WallReactionsResponse.java`, `WallReactionItemResponse.java` (nuevos)
  - Tests: `PublicacionMuroServiceTest.java` (agregué 5 casos para `reacciones()`),
    `WallControllerAuthorizationTest.java` (agregué 2 casos + el `@MockitoBean` nuevo),
    `ReaccionMuroPersistenceAdapterTest.java` (nuevo, Testcontainers).
- **Pruebas de autorización negativa (CLAUDE.MD §0.3): escritas, NO ejecutadas.**
  `WallControllerAuthorizationTest.traineeSuspendidoNoPuedeLeerLasReacciones` cubre "SUSPENDED
  con token válido -> 403". No hay un caso real de "rol sin permiso -> 403" para `USE_APP`
  específicamente: los 5 roles del sistema hoy pasan `USE_APP` (`TRAINEE` porque está en su
  matriz explícita, los otros 4 por el hueco fail-open A-1 ya documentado en `UserRole.can()`)
  — mismo criterio que ya usa el test existente `traineeActivoLeeElFeed` para el mismo permiso,
  no es una omisión mía.
- **`ReaccionMuroPersistenceAdapterTest` es nuevo para un adaptador que antes no tenía prueba de
  integración propia** (solo tenía el test unitario `ReaccionMuroTest` sobre el dominio puro).
  Cubrí `listarDe` (el método que agregué) contra Testcontainers; el resto de métodos del
  adaptador (`deUsuario`/`contarPorTipo`/`upsert`/`eliminar`/las versiones en lote del feed)
  sigue sin prueba de integración dedicada — preexistente a este cambio, lo dejo señalado en vez
  de ampliarlo por mi cuenta (fuera del alcance pedido).
- **No pude verificar en vivo** la forma exacta de la respuesta JSON contra `localhost:8080`
  (ver la sección de arriba) — el backend corriendo es el código previo a este cambio, así que
  el endpoint nuevo no existe ahí todavía.
- **`npx tsc --noEmit` sí se corrió y quedó en cero** (ver sección de arriba) — no es un riesgo
  pendiente, se deja la constancia acá para que quede junto al resto del cierre.
