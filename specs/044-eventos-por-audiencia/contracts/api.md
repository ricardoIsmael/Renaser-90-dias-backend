# Contrato de la API — spec 044

Los nombres del cable siguen al resto del contrato de eventos (en inglés: `audienceType`, `targetRoles`…). Todo campo nuevo es **opcional**:
una app anterior que no los manda ni los lee sigue funcionando (FR-030, FR-032). Errores con el formato `ApiErrorConCodigo` de siempre.

## 1. `POST /api/v1/calendar/events` y `PUT /api/v1/calendar/events/{id}` (campos nuevos)

```jsonc
{
  "title": "Sesión de rescate",
  "eventType": "SESION_ESPECIAL",
  "startsAt": "2026-11-12T01:00:00Z",
  "durationMinutes": 60,
  "timezone": "America/Lima",
  "locationType": "MEET",
  "crearEnlaceMeet": true,            // spec 040, sin cambios
  "notifyOnCreate": true,
  "audienceType": "SELECTION",        // nuevo valor; sin él, "ALL_MEMBERS" como hoy
  "audience": {                       // nuevo; solo con SELECTION
    "parts": [
      { "kind": "TRAFFIC_LIGHT", "color": "RED" },
      { "kind": "PERSON", "id": "uuid-de-la-persona" },
      { "kind": "PERSON", "id": "uuid-de-otra" }
    ],
    "recalculate": false              // solo al editar: true = foto nueva de los colores (P1)
  }
}
```

`kind` y lo que exige cada uno:

| `kind` | Campo | Quién lo puede mandar | Se lee |
|---|---|---|---|
| `PERSON` | `id` (usuario) | ADMIN/ALCHEMIST: cualquiera; MENTOR_LEAD: personas de sus formaciones; MENTOR/TRAINEE_GUIDE: sus alumnos | lista fija |
| `GROUP` | `id` (grupo) | ADMIN/ALCHEMIST: cualquiera; MENTOR_LEAD: el que acompaña | en cada momento |
| `MY_LEARNERS` | — | MENTOR/TRAINEE_GUIDE | en cada momento |
| `MENTORS` | — | ADMIN/ALCHEMIST, MENTOR_LEAD | en cada momento |
| `TRAFFIC_LIGHT` | `color`: `GREEN` \| `YELLOW` \| `RED` | todos los que crean (cada uno dentro de su alcance) | foto al guardar (P1) |
| `STAFF` | `id` opcional (sin `id` = todo el personal) | ADMIN/ALCHEMIST, MENTOR_LEAD | `id`: lista fija; sin `id`: en cada momento |

Respuestas:

| Código | `codigo` | Cuándo |
|---|---|---|
| 201 / 200 | — | Guardado. Devuelve el evento (§3) con `audience` para el creador. |
| 400 | `AUDIENCIA_VACIA` | La unión de las partes no tiene a nadie hoy. |
| 400 | `AUDIENCIA_INVALIDA` | `SELECTION` sin partes al crear; parte sin su campo; color desconocido; más de 500 personas por nombre. |
| 403 | `FUERA_DE_TU_ALCANCE` | Alguna persona o grupo no le corresponde al actor. Cuerpo: `{ "ids": ["…"] }`. No se guarda nada. |
| 403 | — | El rol no puede crear, o un mentor manda `ALL_MEMBERS`, `ROLES` u otro tipo de evento (FR-007). |
| 404 | `FUNCIONALIDAD_NO_DISPONIBLE` | `eventos.convocar` apagado o no disponible para el rol. |

**Editar desde una app anterior** (FR-030): `audienceType: "SELECTION"` sin `audience` → se conservan las partes guardadas.

## 2. Nuevos: opciones, personas y vista previa (`@Funcionalidad("eventos.convocar")`)

### 2.1 `GET /api/v1/calendar/audience/options?formation=`

Qué puede elegir quien llama. La app arma los chips con esto (FR-029).

```json
{
  "canCreate": true,
  "eventTypes": ["SESION_ESPECIAL", "ESPONTANEO"],
  "parts": ["PERSON", "MY_LEARNERS", "TRAFFIC_LIGHT"],
  "defaultPart": "MY_LEARNERS",
  "trafficLight": { "GREEN": 4, "YELLOW": 2, "RED": 4 },
  "myLearners": 10,
  "groups": [],
  "staff": []
}
```

Para ADMIN/ALCHEMIST `parts` incluye `ALL`, `GROUP`, `MENTORS`, `STAFF`; `groups` trae `{ id, name, members }` y `staff` `{ id, name,
photoPath, role }`. Sin permiso: `canCreate: false` y listas vacías (no 403: la app solo esconde el botón).

### 2.2 `GET /api/v1/calendar/audience/people?q=&page=&size=30`

El buscador de «Personas», **solo dentro del alcance** del actor. Sin tildes y sin mayúsculas (como `/leadership/people`).

```json
{
  "items": [
    { "id": "…", "name": "Luisa Quispe", "photoPath": "/api/v1/users/…/photo", "role": "MENTOR", "groupName": "Grupo Fénix" }
  ],
  "page": 0, "hasMore": false
}
```

**No trae el color del semáforo** de cada persona: elegir por color se hace con la parte `TRAFFIC_LIGHT`, no mirando el color de alguien.

### 2.3 `POST /api/v1/calendar/audience/preview`

Cuerpo: `{ "eventType": "…", "formation": "…", "parts": [ … ] }` (las mismas partes de §1). Respuesta:

```json
{ "total": 23, "byPart": [ { "kind": "TRAFFIC_LIGHT", "color": "RED", "count": 15 }, { "kind": "PERSON", "count": 2 } ],
  "excludedByEventType": 0 }
```

`total` cuenta cada persona una vez (sin el creador). `excludedByEventType`: los que el tipo deja fuera (Mentoría del Alquimista sin verde). Los
mismos errores 400/403 que §1. Solo lee: no escribe ni deja constancia (la constancia del semáforo se escribe al **guardar**, FR-019).

## 3. Lectura del evento (`GET /calendar/events`, `/calendar/events/{id}`) — campo nuevo `audience`

Solo para el **creador, ADMIN y ALCHEMIST** (FR-018); para cualquier otro, `audience: null`. `audienceType` viaja para todos (vale
`"SELECTION"`), pero no dice a quién.

```json
"audience": {
  "parts": [
    { "kind": "TRAFFIC_LIGHT", "color": "RED", "count": 15, "resolvedAt": "2026-11-06T15:02:00Z" },
    { "kind": "PERSON", "count": 2, "people": [ { "id": "…", "name": "Luisa Quispe", "photoPath": "…" } ] }
  ],
  "total": 17
}
```

Un evento `SELECTION` que el visor no ve: **404** en el detalle y ausente de la lista (FR-013).

## 4. Asistencia (`/calendar/events/{id}/responses` y `/attendance`, D-256)

Sin cambios de forma. El creador MENTOR/TRAINEE_GUIDE de **ese** evento la puede leer (FR-027); los demás roles, como hoy. «Sin respuesta» =
audiencia elegida menos quienes respondieron.

## 5. Interno: `calendar.api.AudienciaDelEventoFinder` (para `notifications`, spec 041)

```java
boolean esElegida(EventoId evento);
boolean incluye(EventoId evento, UserId persona);
```

`ReglasDelMensajeDeEvento` lo usa para aplicar P2/P3 a los eventos con audiencia elegida.

## 6. Lo que NO cambia

- `GET /calendar/events` sigue respondiendo la misma forma; un evento «para todos» no trae `audience` (o `null`).
- Los endpoints de Meet (040), WhatsApp (041) y la cuenta de Google.
- `eventType`, `reminderRules`, `notifyOnCreate` y la recurrencia.
