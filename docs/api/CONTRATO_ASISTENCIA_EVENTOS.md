# Contrato REST — Asistencia a eventos (`calendar`, D-256)

Quién respondió «Voy» / «No voy» a una fecha de un evento, y pasar lista el día del evento. Generado leyendo
el código real de `calendar/infrastructure/adapter/in/rest/evento/AsistenciaController.java`,
`AsistenciaResponses.java`, `application/services/{AccesoALaListaService,ListaDeAsistenciaService,
RespuestasDelEventoService,PersonasConvocadasService}.java` y `domain/model/asistencia/**`, y verificado por
`AsistenciaAEventosIT` (Tomcat + Postgres reales).

**Ningún endpoint existente cambió.** `PUT /rsvp` responde lo mismo que antes; lo único nuevo es que, además
de guardar la respuesta, deja una fila en `historial_confirmaciones_evento` cuando la respuesta cambia.

## 0. Reglas comunes

**Quién (regla del dueño, 2026-10-06, literal):** ven y pasan lista **quien creó el evento + Admin, Alquimista
y Líder de mentores**, en cualquier evento (también en uno que el Admin creó para un mentor). Nadie más.

| Quién | Resultado |
|---|---|
| ADMIN, ALCHEMIST, MENTOR_LEAD (activos) | 200 en cualquier evento |
| El que creó el evento (`eventos.creado_por`), sea del rol que sea | 200 en ese evento |
| MENTOR que no lo creó, aprendiz (también sobre sí mismo) | **403** `Solo quien creó el evento, el Admin, el Alquimista o el Líder de mentores ven la asistencia` |
| Cuenta suspendida | **403** |
| Sin sesión | 403 |

**Para qué:** «simplemente es seguimiento». No da puntos ni toca coherencia, semáforo ni racha: ningún
endpoint publica un evento ni llama a otro módulo. El aprendiz no ve su propia asistencia ni la de otros.

**`occurrenceStart`:** el `inicioOcurrencia` de la fecha (el mismo valor que la app manda en `PUT /rsvp`),
ISO-8601 con `Z`. Se acepta con hasta 3 min de diferencia (la misma tolerancia que RSVP); las respuestas
devuelven el valor canónico. Una fecha que no es del evento, o que se canceló → **400**. Evento inexistente →
**404**. Formato inválido → 400.

**Ventana para pasar lista (supuesto S-1, a confirmar):** desde **30 min antes del inicio** hasta **12 h
después del fin** de la fecha (inicio efectivo si se reprogramó; sin duración, el fin es el inicio). Son
duraciones desde instantes: no dependen de la zona del servidor ni de la fecha UTC.

**Errores:** forma estándar `{ "message": "...", "timestamp": "..." }` (`GlobalExceptionHandler`): 400
(`IllegalArgumentException`), 403, 404, 409 (`IllegalStateException`).

## 1. `GET /api/v1/calendar/events/{id}/responses?occurrenceStart=` — quién respondió

Permiso declarado `USE_APP`; la regla de «Quién» la aplica el servicio.

```json
{
  "occurrenceStart": "2026-10-06T01:00:00Z",
  "people": [
    {
      "userId": "4b0d…",
      "fullName": "Ana Ríos",
      "avatarUrl": null,
      "status": "GOING",
      "respondedAt": "2026-10-04T14:10:00Z",
      "history": [
        { "status": "NOT_GOING", "at": "2026-10-04T02:05:00Z" },
        { "status": "GOING",     "at": "2026-10-04T14:10:00Z" }
      ]
    },
    { "userId": "…", "fullName": "Ciro Paz", "avatarUrl": null, "status": null, "respondedAt": null, "history": [] }
  ]
}
```

- `people` = **la audiencia del evento** (la MISMA que reciben los recordatorios: `AudienciaDelEventoService`,
  con la elegibilidad del tipo de evento) **más** quien respondió o fue marcado aunque no esté en ella.
  Ordenado por nombre. Solo cuentas existentes.
- `status`: `GOING`, `NOT_GOING`, `MAYBE` o `null` (sin respuesta). La app actual no ofrece «Quizás»: lo
  muestra como «Sin respuesta» (supuesto S-5).
- `history`: cada cambio de respuesta **desde la V93** (append-only), del más viejo al más nuevo; el último
  es el vigente. Las respuestas anteriores a la V93 no tienen historia (no se inventa).
- No trae la asistencia (`estado`): eso es `GET /attendance`.

## 2. `GET /api/v1/calendar/events/{id}/attendance?occurrenceStart=` — la lista

```json
{
  "occurrenceStart": "2026-10-06T01:00:00Z",
  "opensAt": "2026-10-06T00:30:00Z",
  "closesAt": "2026-10-06T14:00:00Z",
  "open": true,
  "closed": null,
  "people": [
    { "userId": "…", "fullName": "Ana Ríos", "avatarUrl": null, "status": "GOING",
      "respondedAt": "2026-10-04T14:10:00Z", "estado": "A_TIEMPO", "markedAt": "2026-10-06T01:02:00Z" },
    { "userId": "…", "fullName": "Ciro Paz", "avatarUrl": null, "status": null,
      "respondedAt": null, "estado": null, "markedAt": null }
  ]
}
```

- `open`: si **ahora** se puede marcar (ventana abierta y lista no cerrada).
- `closed`: `null` o `{ "at": "…", "byUserId": "…|null", "byName": "Kelin Rojas|null" }` (`null` si la
  cuenta de quien la cerró se borró).
- `estado`: `A_TIEMPO`, `TARDE` o `null` = **ausente / sin marcar** (no hay valor «ausente»: es no tener fila).
- Mismas personas que `/responses`. Se puede leer siempre, también fuera de la ventana.

## 3. `PUT /api/v1/calendar/events/{id}/attendance/{userId}` — marcar

```json
{ "occurrenceStart": "2026-10-06T01:00:00Z", "estado": "A_TIEMPO" }
```

`estado`: `A_TIEMPO`, `TARDE` o `null` (quitar la marca = ausente). Otro valor → 400.
Respuesta 200: la fila de esa persona, con la forma de `people[]` de §2.

- **Idempotente:** repetir el mismo estado no cambia nada (ni `markedAt` ni quién marcó); quitar una marca que
  no existe responde 200. Cambiar de estado guarda quién y cuándo.
- Fuera de la ventana → **409** `Todavía no se puede pasar lista: se abre 30 min antes del inicio` o
  `Ya pasó el plazo para pasar lista: se cierra 12 h después del fin`.
- Lista cerrada → **409** `La lista está cerrada. Reábrela para corregirla`.
- `userId` que no está en la audiencia, no respondió y no estaba marcado → **400** `Esa persona no está en la
  lista de este evento`.

## 4. `POST /api/v1/calendar/events/{id}/attendance/close` y `/reopen`

Cuerpo `{ "occurrenceStart": "…" }`. Respuesta 200: la lista completa (§2).

- **Cerrar:** se puede desde que abre la ventana, también después de que venció. Idempotente: cerrar una lista
  cerrada no cambia quién ni cuándo. Antes de la ventana → 409.
- **Reabrir** (para corregir): solo dentro de la ventana (después → 409). Idempotente. No borra marcas.
- Corregir = reabrir, marcar, volver a cerrar. Lo pueden hacer los mismos roles de §0 (supuesto S-3).

## 5. Persistencia (V93)

| Tabla | Qué guarda | Borrar la cuenta (D-243) |
|---|---|---|
| `historial_confirmaciones_evento` | cada cambio de respuesta (append-only) | se borran sus filas |
| `asistencias_evento` | una fila por persona marcada (A_TIEMPO/TARDE) | se borran sus marcas; las que ELLA puso a otros pierden `marcado_por` |
| `listas_asistencia_evento` | una fila por fecha cerrada | sobrevive, pierde `cerrada_por` |

Borrar el evento borra todo lo suyo en cascada.
