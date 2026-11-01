# Quickstart: probar la comunidad por formación

1. Backend con la base nueva: `TZ=UTC ./mvnw -B test -Dtest=ComunidadPorFormacionIT,VisibilidadPorFormacionTest,FormacionDelContenidoTest` (Testcontainers; levanta V1–V139).
2. A mano (con la base clonada, nunca producción): crear tres aprendices, uno por formación (`POST /admin/trainees/{id}/formacion`, 022), y desde cada uno:
   - `POST /wall {text, media}` → aparece solo en el `GET /wall` de su formación; un `GET /wall/{id}/comments` de una publicación ajena da `404`.
   - `GET /chat/conversations` → su «Global» y su soporte; nada de las otras formaciones.
   - `GET /ranking` → la posición es dentro de su formación.
3. Como Administración: `GET /wall?formation=DIPLOMADO` (o sin parámetro para «Todas», cada fila con `formation`) y `POST /wall {…, formation:"DIPLOMADO"}`
   (sin `formation` da `400 «Elige la formación en la que lo quieres crear.»`).
4. Apagar un tipo: `PUT /admin/formaciones/DIPLOMADO/comunidad/DIRECTO {"prendido": false}` → los directos del Diplomado salen de la lista y no se puede abrir uno;
   `GET /admin/formaciones/DIPLOMADO/comunidad` dice cuántas conversaciones esconde cada tipo.
5. Traslado: `POST /admin/trainees/{id}/formacion` → el aprendiz sale de la comunidad general y del soporte de la anterior y entra a los de la nueva; lo que escribió se queda.
