# Contratos — Spec 056

JSON; fechas ISO (`LocalDate` sin zona, `Instant` UTC); errores `{ codigo, message }`. Todo lo del líder: `AccesoDeLiderazgo` (MENTOR_LEAD/ADMIN/ALCHEMIST activo) + la persona dentro del alcance de sus formaciones; fuera → 403 `FUERA_DE_SU_ALCANCE`. Cambios a respuestas existentes: solo campos nuevos.

## Persona (prefijo `/api/v1/leadership/people/{personId}`, `VIEW_MENTOR_CORPS`)

### `GET /semana?semanaHasta=AAAA-MM-DD`
Misma forma que `GET /mentor/groups/{g}/learners/{u}/semana` (055): `{ aprendizId, nombre, avatarUrl, zona, desde, hasta, diaUno, primeraSemana, semanaEnCurso, cerrada, color, porcentaje, dias: [ { fecha, diaDePrograma, motivo, obligaciones: [… + pilar] } ] }`. Persona sin programa → 404 `SIN_PROGRAMA`. Errores de fecha: los de la 055.

### `GET /dias?desde&hasta`
Misma forma que el `/dias` del mentor (055). Máx. 62 días; recorta a `[diaUno, hoy]`.

### `GET /objetivos`
200: `{ noventaDias: [ { id, titulo, avance } ], mes: { titulo, avance } | null, semana: { titulo, planificadas, cumplidas } | null }` (lo que da `RocasDelAprendizFinder`).

### `POST /avisar-al-mentor`  body `{ texto, operationKey }`
204. El mentor vigente recibe el mensaje en su chat con el actor (con el enlace a la ficha) y un aviso. Sin mentor → 409 `SIN_MENTOR`. Mismo `operationKey` → 204 sin duplicar.

### `POST /documento`  body `{ tipo: APRENDIZ|MENTOR, semanaHasta?: AAAA-MM-DD }`
200: `{ url, venceEn, reutilizado, semana: { desde, hasta } }`. Permite también al MENTOR que acompaña hoy a la persona (tipo `APRENDIZ`) — 403 `NO_ACOMPANA` si no. Semana sin datos → el documento lo dice (FR-038), no es error.

## Mentores (`/api/v1/leadership/mentors…`)

- `GET /` (padrón) y `GET /{id}` (ficha): + `cupo: { acompanados, maximo }` y + `porColor: { rojo, ambar, verde, sinDatos }`.
- `POST /{id}/observations`: igual; además el servidor manda el mensaje y el aviso (ignora `sentByChat` de una app vieja si ya lo mandó la app: `messageId` presente → no reenvía).
- `POST /{id}/documento`  body `{ semanaHasta? }` → como el de persona, tipo `MENTOR`.
- `GET /por-resolver` → `{ gruposSinMentor, consultas: { cantidad, diasDeLaMasVieja }, peorMentor: { id, nombre, evaluacion } | null }`.

## Grupos (`/api/v1/admin/cells…`, `MANAGE_CELLS`)

Sin rutas nuevas. El líder (con `MANAGE_CELLS`) opera solo sobre grupos y personas de sus formaciones; fuera → 403 `FUERA_DE_SU_ALCANCE`. `GET /dashboard`: + `capacity` por grupo.

## Consultas

- `POST /api/v1/tickets/{id}/answer`: también el líder de la formación del aprendiz.
- `TicketMentorResponse`: + `respondidoPor: { id, nombre } | null`.
- `POST /api/v1/leadership/tickets/{id}/recordar`  body `{ operationKey }` → 204; mensaje directo al mentor asignado con la consulta.

## Reporte del mes

- `POST /api/v1/leadership/report/documento`  body `{ month: AAAA-MM }` → como `/documento`, tipo `MES`.

## Sin cambios

`POST /api/v1/calendar/events` (sesión personalizada con PERSONA/GRUPO/MENTORES y `crearEnlaceMeet`); lecturas del líder de radar, cierres, huecos, diagnóstico, 80/20 y notas.
