# Contrato: hábitos y categorías por formación (spec 023)

Todo es **aditivo**. La app 1.6.0 (sin formaciones) sigue funcionando para las cuentas de la Escuela: el campo `category` de siempre conserva sus valores
(`BODY`, `MIND`, `CONSCIENCE`, `SPIRIT`) para las cuatro categorías de la Escuela. Errores con el cuerpo de siempre (`{ message }`).

## Para quien hace el programa

### `GET /api/v1/habit-categories[?formation=CODIGO]` — USE_APP
```json
{ "formation": "DIPLOMADO",
  "categories": [ { "key": "DIPLOMADO_ESTUDIO", "code": "DIPLOMADO_ESTUDIO", "name": "Estudio", "icon": "bookOpen", "order": 1 } ] }
```
Las categorías **activas** de la formación en que la persona hace su programa (o la activa del personal que no hace programa), en el orden de Administración.
`code` es lo que lleva el campo `category` de los hábitos (el inglés de siempre en las cuatro de la Escuela, la clave en las demás). `icon` es uno de
`activity, brain, heart, sparkles, briefcase, bookOpen, flame, target, compass, users, star, moon, sunrise, handHeart, award, bulb` o `null`.
`formation` solo lo honra quien ve las tres formaciones; otra persona pidiendo otra formación recibe **404**. Sin ninguna formación: `formation: null`, `categories: []`.

### `GET /api/v1/habits` y `GET /api/v1/admin/trainees/{id}/habits` — campos nuevos
`category` es un string abierto y se suma `categoryKey` (la clave de la base). Los hábitos ya vienen filtrados por la formación de la persona (y, en la vista del
acompañante, por la formación en que se mira a la persona). Pedir por id (`/habit-unlocks/{id}`, `/habit-preferences/{id}/…`, `/habits/{id}/rename`,
`/weekly-habit-days/{id}`) un hábito del catálogo de **otra formación** responde **404** (como un id que no existe).

## Administración (ADMIN y ALQUIMISTA activos; otro rol o cuenta suspendida → 403)

### `GET /api/v1/admin/habits?formation=ESCUELA|DIPLOMADO|CERTIFICACION|ALL[&role=]`
Sin `formation` = la Escuela (el cliente anterior). `ALL` = las tres (solo lectura; cada hábito lleva su `formation`). `AdminHabitResponse` suma `categoryKey` y `formation`.

### `POST /api/v1/admin/habits` — `formation` opcional (ausente = ESCUELA)
`category` es el código en inglés o la clave; debe ser una categoría **activa de esa formación** (400 si no existe, es de otra formación o está desactivada).
`POST /api/v1/admin/habits/{id}` mueve a otra categoría solo dentro de la formación del hábito.

### `POST /api/v1/admin/habits/{id}/copy` `{ "formation": "CERTIFICACION", "category": "CERTIFICACION_PRACTICA" }` → 201 `AdminHabitResponse`
Crea un hábito **independiente** (mismo contenido, audiencia por rol, horarios y guía —textos—; conserva la `systemKey`). 400: destino igual al origen, categoría
inexistente/de otra formación/desactivada. 409: el destino ya tiene un hábito con ese título (sin tildes ni mayúsculas) o con la misma `systemKey`.

### Categorías: `/api/v1/admin/habit-categories`
| Verbo y ruta | Cuerpo | Respuesta |
|---|---|---|
| `GET ?formation=` | — (sin `formation` = las tres) | `[ { key, code, formation, name, icon, order, isActive, activeHabits, totalHabits, canBeDeactivated, canBeDeleted } ]` |
| `POST` | `{ formation, name, icon? }` | 201, la categoría |
| `PUT /{key}` | `{ name?, icon?, changesIcon }` (`changesIcon:true` + `icon` vacío quita el ícono) | la categoría |
| `PUT /{key}/active` | `{ isActive }` | la categoría |
| `PUT /order` | `{ formation, keys:[todas las de la formación] }` | las categorías en su nuevo orden |
| `DELETE /{key}` | — | 204 |

Errores: **409** «Esta categoría tiene hábitos activos. Muévelos o desactívalos primero.» · **409** «Esta categoría tiene hábitos con historial. Desactívala en lugar de
eliminarla.» · **409** «Ya hay una categoría con ese nombre en esta formación.» · **400** nombre fuera de 2–40 caracteres, ícono fuera de la lista u orden que no
trae todas las claves una sola vez · **404** categoría inexistente.

## Compatibilidad
Un cliente que no manda `formation` crea en la Escuela. La app nueva contra un backend anterior (404 en `/habit-categories`) usa las cuatro categorías de siempre.
