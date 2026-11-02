# Contrato — Identidad por formación (spec 035)

Todo aditivo. Sin `formation`, las rutas de Bienvenida editan **la Escuela**, como hoy. Solo `ADMIN` y `ALCHEMIST` con la cuenta activa (403 a los demás, 403 a un
ADMIN suspendido). Un código de formación que no existe: **400** en query/cuerpo, **404** en la ruta de la imagen.

## Bienvenida (ya existe; ahora con formación)

`@Funcionalidad("admin.bienvenida")`, permiso `MANAGE_WELCOME`.

| Ruta | Cambio |
|---|---|
| `GET /api/v1/admin/bienvenida?formation=DIPLOMADO` | La bienvenida de esa formación. |
| `PUT /api/v1/admin/bienvenida/textos/{clave}?formation=` | Cambia el texto de esa formación. |
| `DELETE /api/v1/admin/bienvenida/textos/{clave}?formation=` | Vuelve al original **de esa formación**. |
| `POST /api/v1/admin/bienvenida/portada/confirm?formation=` | Deja vigente la portada subida para esa formación. |
| `DELETE /api/v1/admin/bienvenida/portada?formation=` | La Escuela vuelve a la original; las otras, a la vigente de la Escuela. |
| `GET /api/v1/admin/bienvenida/tarjeta?formation=&nombre=&portada=` | Vista previa sobre la portada vigente de esa formación o la candidata. |
| `POST /api/v1/admin/bienvenida/portada/upload-url` | Sin cambios (la URL no depende de la formación). |

Respuesta (`BienvenidaResponse`), campos nuevos: `formation` (código) y `portada.heredada` (`true` si la formación no tiene portada propia y usa la de la Escuela;
siempre `false` en la Escuela). Todo lo demás igual.

## Imagen de la formación (nuevo)

`@Funcionalidad("chat")`, permiso `MANAGE_WELCOME`.

| Ruta | Respuesta |
|---|---|
| `GET /api/v1/admin/formaciones/{codigo}/imagen` | `ImagenDeLaFormacionResponse` |
| `POST /api/v1/admin/formaciones/{codigo}/imagen/upload-url` `{contentType}` | `{url, ruta}` (prefirmada, 10 min; JPEG o PNG) |
| `POST /api/v1/admin/formaciones/{codigo}/imagen/confirm` `{ruta}` | `ImagenDeLaFormacionResponse` (revisa la imagen; 400 con el motivo, 404 si no hay nada subido, 409 si el servidor no guarda imágenes) |
| `DELETE /api/v1/admin/formaciones/{codigo}/imagen` | `ImagenDeLaFormacionResponse` (vuelve a la de la Escuela, o a la de la app en la Escuela) |
| `GET /api/v1/admin/formaciones/{codigo}/imagen/vista?ruta=` | `image/jpeg` 512 × 512, sin caché: la vigente o la candidata `ruta`. **404** si no hay ninguna (la app muestra la suya). |

`ImagenDeLaFormacionResponse`: `{ formation, hayImagen, cambiada, heredada, sePuedeCambiar, ultimoCambio {por, en, volvioAlOriginal} | null }`.
`hayImagen`: hay una imagen subida en efecto (propia o la de la Escuela). `cambiada`: la formación tiene una propia. `heredada`: usa la de la Escuela.

## Chat (ya existe; ahora con la imagen de la formación)

- `GET /api/v1/chat/conversations`: `photoPath` (ya existe) ahora también viene en la **comunidad general** y en los **grupos sin
  foto propia**, solo si la formación tiene imagen: `/api/v1/chat/conversations/{id}/foto?v=<ms>`. Sin imagen, `null` como hoy. `ownPhoto` (nuevo, solo en un grupo): `true` si esa foto es la PROPIA del grupo (D-212) y `false` si es la de la formación; así «Volver a la foto de Renaser» solo se ofrece con una propia. `null` en lo demás.
- `GET /api/v1/chat/conversations/{id}/foto`: comunidad → la imagen de su formación; grupo → su foto propia o, si no tiene, la de su formación; 404 si no hay ninguna.
  Soporte: la tarjeta con nombre sobre la portada de **su** formación. `GET .../miembros/{usuarioId}/foto`: igual, sobre la portada de la conversación.
- `GET /api/v1/chat/conversations/{id}/messages`: `senderName` de un mensaje del programa = «Formación Renaser» en la Escuela, «Diplomado Renaser» y «Certificación Renaser» en las otras (`NombreDelChat.delPrograma`). Lo mismo el autor del aviso de un mensaje del programa.
