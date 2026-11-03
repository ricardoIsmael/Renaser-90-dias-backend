# Contrato: la Comunidad por formación (spec 024)

Todo es **aditivo**. Un cliente anterior (la app 1.6.0) ignora los campos nuevos y no manda nada nuevo: una cuenta de la Escuela recorre las seis secciones
como siempre. Los errores usan el cuerpo de siempre (`{ message }`). El **filtro lo hace siempre el servidor**; la app nunca decide qué es de qué formación.

## Reglas comunes

- **Quién lee qué**: la persona de una formación lee la suya (aprendiz: la suya; personal con varias: la **activa**, `PUT /me/formacion/activa`, 022).
  Administración, Alquimista y Soporte TI leen las tres.
- **Parámetro `formation`** (código `ESCUELA` | `DIPLOMADO` | `CERTIFICACION`, opcional) en las **lecturas** de la Comunidad. Solo cambia algo para quien ve
  las tres: acota a esa formación; sin él leen «Todas», **cada fila con su `formation`** y, en el ranking, con **su posición dentro de su formación**. Para los
  demás se ignora si es la suya, y **una formación que no ven da una lista vacía** (no revela nada). Un código que no existe → `400`.
- **Campo `formation`** (código) en cada fila de: publicación del Muro, testimonio, evento, curso, conversación y entrada del ranking.
- **Crear** (publicación, testimonio, evento): nace en la formación de quien crea (aprendiz o personal: la suya/activa). **Administración tiene que elegirla**:
  `formation` en el cuerpo; sin ella `400 «Elige la formación en la que lo quieres crear.»`. Nada se crea «para todas» ni «sin formación». Una formación que
  quien crea no ve → `404`. Sin ninguna formación asignada → `403`.
- **Pedir por id lo de otra formación = `404` con el mismo texto que un inexistente** (publicación, comentario, evento, curso, lección, conversación,
  «Quién respondió»/«Pasar lista», fotos e integrantes de un chat). No hay `403` que delate que existe.

## Muro (`/api/v1/wall`)

| Endpoint | Cambio |
|---|---|
| `GET /wall?cursor&category&formation` | feed de la formación; `formation` solo Admin/Alq (sin él, Todas) |
| `POST /wall` `{ text, media, category?, formation? }` | `formation` solo la manda Administración |
| `GET /wall/hidden?cursor&formation` | cola de ocultas por formación (Admin/Alq) |
| `GET /wall/mine`, `GET /wall/latest-author` | cuentan / nombran solo lo de las formaciones que lee la persona |
| `GET/POST/PATCH/DELETE /wall/{id}/comments…`, `POST /wall/{id}/react`, `GET /wall/{id}/reactions` | `404` si la publicación es de otra formación |
| `POST /wall/{id}/restore`, `DELETE /wall/{id}`, `DELETE /wall/{id}/permanent` | sin cambios (moderan ADMIN/ALCHEMIST; el autor oculta lo propio) |
| `WallPostResponse.formation` | nuevo |

Compartir (`POST /chat/conversations/{id}/messages/share-wall-post`): la publicación solo se comparte en una conversación de **su** formación (si no, `404 «Publicacion no encontrada»`).

## Testimonios (`/api/v1/testimonios`)

`GET ?formation` (lista de la formación), `POST { …, formation? }` (el escrito a mano lo crea Administración y **elige** la formación; el promovido desde el Muro
queda en la de la publicación). `TestimonioResponse.formation`.

## Eventos (`/api/v1/calendar/events`)

`GET ?from&to&formation`, `POST/PUT` con `formation` (obligatoria al **crear** como Admin/Alq; al editar no cambia). `EventoResponse.formation`. La audiencia
(`ALL_MEMBERS`, nivel, curso, roles, grupo) se resuelve **dentro de la formación del evento**: recordatorios, anuncios, alarmas, «Voy» y asistencia solo alcanzan
a personas de esa formación. «Próximo evento» de Hoy y «Mi agenda» salen de la misma lectura. `404` en el detalle, el «Voy» y la asistencia de otra formación.
El aviso de un evento nuevo/recordatorio: sin cambio en la ruta (`/eventos/{id}`).

## Cursos (`/api/v1/cursos`)

`GET ?formation`, `GET /bloqueados?formation`. El catálogo, los bloqueados, el curso, la sección, la lección, el progreso, la Clase diaria y la recomendación son
**solo los cursos de la formación**; el desbloqueo por día usa el día del programa de la persona en su formación. `CursoResponse.formation` (junto a los campos de
siempre). El porcentaje de cursos del ranking cuenta solo los cursos de la formación de cada aprendiz. El contexto de SER (lecciones visibles) también.

## Tribu (`/api/v1/chat`)

| Endpoint | Cambio |
|---|---|
| `GET /chat/conversations?formation` | lista solo lo de la formación de la persona **con el tipo prendido**; un directo solo si la otra persona también pertenece a esa formación (Admin/Alq cuentan como de cualquiera) |
| `POST /chat/conversations/direct` `{ otherUserId, formation? }` | el directo se abre en la formación que **las dos personas comparten**; `formation` solo desempata cuando comparten varias (dos administradores). Sin formación en común → `404`. Tipo apagado → `403` |
| `GET /chat/conversations/global/members?cursor&limit&formation` | la ficha de la comunidad general de la formación |
| `PATCH /chat/conversations/global/name` `{ name, formation? }` | renombra la comunidad general de esa formación (default Escuela) |
| `GET /chat/members` | el directorio junta las comunidades de las formaciones que la persona ve |
| `GET /chat/support-conversations?formation` | «Soporte · N» por formación (Admin/Alq); excluye las formaciones con el soporte apagado |
| `ConversacionResponse.formation` | nuevo |
| `GET /admin/ranking-semanal/vista-previa?formation` | el podio de esa formación (default Escuela) |

- **Una comunidad general por formación.** La de la Escuela se llama «Formación Renaser Global» (la de siempre); las demás «Diplomado Renaser Global»,
  «Certificación Renaser Global». Toda persona nueva se une a la de **su** formación; el personal, a la de cada formación que tiene asignada (aunque no sea la
  activa, para que le lleguen los avisos); Administración y Alquimista, a las tres.
- **Nombres derivados**: grupo «`<Mentor>` y sus aprendices» (igual en todas); soporte «`<Nombre>` – Formación Renaser» en la Escuela y «`<Nombre>` –
  `<nombre de la formación>`» en las otras.
- **Soporte**: uno por aprendiz **por formación** (clave `soporte:<id>` en la Escuela, `soporte:<id>:<CÓDIGO>` en las otras). Al pasar de formación el soporte
  de la anterior se conserva sin verse y nace el de la nueva.
- **Podio semanal** (lunes 09:00 Lima): uno **por formación**, en su comunidad general, con el ranking general de sus aprendices. Los ids del mensaje de la
  Escuela son los de siempre (la semana ya publicada no se repite al desplegar).
- **Aviso de un mensaje**: la ruta es `/chat/{id}` en la Escuela y `/chat/{id}?formacion=CÓDIGO` en las demás. El personal con varias formaciones **cambia su
  formación activa** a la del chat al tocarlo (FR-032). Le llegan aunque no sea la activa.

### Tipos de comunidad

- `GET /api/v1/admin/formaciones/{codigo}/comunidad` (ADMIN/ALCHEMIST) →
  `{ "formation": "DIPLOMADO", "tipos": [ { "tipo": "GENERAL", "prendido": true, "conversaciones": 1 }, { "tipo": "GRUPO", … }, { "tipo": "SOPORTE", … }, { "tipo": "DIRECTO", … } ] }`
  — `conversaciones` es cuántas esconde el tipo si se apaga (para la confirmación).
- `PUT /api/v1/admin/formaciones/{codigo}/comunidad/{tipo}` `{ "prendido": false }` (ADMIN/ALCHEMIST, `403` a cualquier otro rol o cuenta suspendida) →
  `{ "prendidos": ["GENERAL","GRUPO","SOPORTE"] }`. Idempotente. Apagar **esconde sin borrar** (mensajes intactos); prender los devuelve. No crea
  conversaciones nuevas de ese tipo mientras está apagado. Rige al instante, sin app nueva.

## Ranking (`/api/v1/ranking`)

`GET /ranking?fecha&formation` y `GET /ranking/{tipo}?fecha&formation`. Cada formación compara **solo a los suyos** y la posición de cada fila es **dentro de su
formación**. `EntradaRankingResponse.formation`. El corte diario (`ranking_aprendices`) guarda la formación de cada aprendiz del día, así que el historial no se
reescribe si cambia de formación. El personal no entra, como hoy. `Kilómetros` y `Gimnasio` salen siempre; ocultarlas según los hábitos de la formación depende
de la spec 023.

## Traslado de formación (evento de la 022)

Escuchado por `chat`: el aprendiz sale de la comunidad general y del soporte de la anterior (se conservan sus mensajes allá) y entra a los de la nueva. Los
directos y el chat del grupo se esconden solos. Lo que escribió (publicaciones, comentarios, mensajes, testimonios, «Voy», progreso de cursos) **se queda en la
formación de origen**; el ranking de la nueva empieza de cero (022: el ledger se reinicia).
