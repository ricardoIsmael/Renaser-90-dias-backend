# Contrato: Código Renaser en las fichas

## 1. El ajuste (Administración, Alquimista y Soporte TI)

`GET /api/v1/admin/codigo-renaser-en-fichas` · `PUT /api/v1/admin/codigo-renaser-en-fichas`

- Autorización: `@RequiresPermission(Permission.MANAGE_RADAR_VISIBILITY)`, `@Funcionalidad("admin")`. La guardia del servicio exige un usuario `ACTIVE` con rol ADMIN, ALCHEMIST o IT_SUPPORT.
  Cualquier otro rol (TRAINEE, MENTOR, TRAINEE_GUIDE, MENTOR_LEAD), sin sesión o con cuenta suspendida: **403**.
- Respuestas sin caché (`Cache-Control: no-store`).

### GET → 200

```json
{
  "visible": false,
  "mixto": false,
  "porRol": { "MENTOR": false, "TRAINEE_GUIDE": false, "MENTOR_LEAD": false },
  "ultimoCambio": { "porId": "uuid", "porNombre": "Ana Pérez", "en": "2026-10-31T23:40:12Z" }
}
```

- `visible`: el mentor, la guía y el líder ven el Código Renaser en las fichas.
- `mixto`: `true` si los tres roles no coinciden (solo si Soporte TI los dejó distintos desde su consola); en ese caso `visible` es `false`.
- `ultimoCambio`: `null` mientras nadie lo cambió (rige el valor por defecto: apagado). `porNombre` puede ser `null` si la cuenta ya no existe.

### PUT `{"visible": true|false}` → 200 con el mismo cuerpo

- Rige en el acto (esta instancia recarga su copia y avisa a las demás por Redis, ≤ 5 s).
- Idempotente: pedir el valor que ya rige responde 200 sin escribir.
- 400 si falta `visible` o no es booleano.
- Deja una fila de auditoría (`MANUAL`, clave `radar.fichas`, motivo fijo «Cambio desde Administración (Código Renaser en las fichas)») y publica `FuncionalidadCambiadaEvent`.
  **No** crea ninguna notificación para el aprendiz, el mentor ni el líder.

## 2. Las lecturas que el interruptor controla (no cambian de forma)

| Ruta | Quién | Apagado |
|---|---|---|
| `GET /api/v1/mentor/groups/{g}/learners/{u}/radar` | mentor / guía vigente del grupo | **404** `FUNCIONALIDAD_NO_DISPONIBLE` |
| `GET /api/v1/leadership/groups/{g}/people/{p}/radar` | líder (y Administración / Alquimista) | líder: **404**; Administración y Alquimista: 200 |
| `GET /api/v1/leadership/people/{p}/radar` (spec 021) | líder (y Administración / Alquimista) | líder: **404**; Administración y Alquimista: 200 |

- El 403 de siempre (sin permiso, cuenta suspendida, no acompaña ese grupo) **sigue ganando** al 404: primero se autoriza, después se pregunta al interruptor.
- Encendido: respuestas idénticas a las de hoy.
- La propia persona (`/api/v1/radar/**`) no pasa por este interruptor.

## 3. Lo que ve la app

- `GET /api/v1/funcionalidades/mias` → `noDisponibles` incluye `"radar.fichas"` para el mentor, la guía y el líder mientras esté apagado. La sección «Código Renaser» de las fichas no se dibuja ni se pide.
- La consola de Soporte TI lista `radar.fichas` («Código Renaser en las fichas», área «Programa») con un interruptor por MENTOR, TRAINEE_GUIDE y MENTOR_LEAD.
