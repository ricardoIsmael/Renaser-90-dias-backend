# Contrato: catálogo global de hábitos con audiencia por rol

Base: `/api/v1/admin/habits`. Todos los endpoints: `@RequiresPermission(MANAGE_HABIT_CATALOG)` +
`HabitoAdminGuard` (cuenta ACTIVE y rol ADMIN o ALCHEMIST). Errores con el formato existente
`ApiErrorResponse { message, timestamp }`.

| Caso | Código |
|---|---|
| Sin sesión | 401 |
| Rol sin permiso o cuenta `SUSPENDED` | 403 |
| Hábito inexistente o personal | 404 |
| Rol desconocido o campo inválido | 400 |
| Eliminar un hábito con registros | 409 con motivo |

Los campos nuevos son **opcionales** en la entrada y **aditivos** en la salida: un cliente que no los
conoce funciona igual.

## `GET /roles` (nuevo)

Roles elegibles para la audiencia, en el orden del sistema.

```json
[
  { "code": "TRAINEE",     "name": "Aprendiz" },
  { "code": "MENTOR",      "name": "Mentor" },
  { "code": "MENTOR_LEAD", "name": "Líder de mentores" },
  { "code": "ADMIN",       "name": "Administrador" },
  { "code": "ALCHEMIST",   "name": "Alquimista" }
]
```

## `GET /?role={code}` (extendido)

Sin `role`: todo el catálogo global (activos e inactivos). Con `role`: los que aplican a ese rol
según la **última** audiencia guardada (los "para todos" incluidos). `role` desconocido → 400.

```json
[
  {
    "id": "…",
    "title": "Clase diaria",
    "description": "…",
    "habitType": "CHECKBOX",
    "category": "MIND",
    "evidenceRequirement": "OPTIONAL",
    "isOptional": false,
    "mandatoryOnIntoxication": false,
    "isActive": true,
    "systemKey": "DAILY_CLASS",
    "isDeactivatable": false,
    "targetRoles": ["MENTOR", "MENTOR_LEAD"],
    "targetRolesEffectiveFrom": "2026-10-12T15:04:00Z",
    "audienceChangedAt": "2026-10-12T15:04:00Z",
    "audienceChangedBy": { "id": "…", "name": "Ana Pérez" },
    "targetRolesStartTomorrow": true
  }
]
```

- `targetRoles: []` = para todos. Un hábito sin versiones devuelve `[]`, los tres campos de cambio
  en `null` y `targetRolesStartTomorrow: false`.
- `targetRolesEffectiveFrom` es el instante del cambio.
- `targetRolesStartTomorrow` lo calcula el servidor: `true` solo si hay un cambio de roles guardado
  hoy (zona America/Lima) que todavía no rige. La versión 1 de un hábito recién creado rige desde
  la creación, así que da `false`. La app dice "Rige desde mañana" solo con este campo.

  > **Corregido 2026-10-12 (E-601, hallado en el E2E).** Decía que la app mostraba "Rige desde
  > mañana" si `targetRolesEffectiveFrom` era de hoy. Con esa regla un hábito recién creado "solo
  > Mentor" aparecía como pendiente aunque ya regía. Ahora decide el servidor
  > (`HistorialDeAudiencia.cambioPendienteHasta`).
- `audienceChangedBy.name` puede ser `null` si la cuenta ya no existe.

## `POST /` (extendido)

```json
{
  "title": "Revisión del grupo",
  "description": "…",
  "habitType": "CHECKBOX",
  "category": "MIND",
  "evidenceRequirement": "OPTIONAL",
  "isOptional": false,
  "mandatoryOnIntoxication": false,
  "targetRoles": ["MENTOR"]
}
```

- `targetRoles` ausente o `null` → para todos. `[]` → para todos. Con códigos → esos roles.
- Crea la versión 1 de la audiencia (rige desde la creación, research R-2). → `201` + el hábito.

## `POST /{id}` (extendido)

Mismo cuerpo que hoy (`UpdateHabitRequest`) más `targetRoles` opcional:
- ausente o `null` → la audiencia no se toca (no se crea versión);
- presente → si difiere de la última versión, se agrega una versión que rige desde el día siguiente
  de cada persona; si es igual, no se crea versión (idempotente).

## `POST /{id}/toggle`, `DELETE /{id}`

Sin cambios de forma. `DELETE` de un hábito con registros → `409`:
`"Este hábito ya tiene registros de personas. Desactívalo en lugar de eliminarlo."`

## Lado participante (sin cambio de forma)

`GET /api/v1/habits`, `GET /api/v1/habit-tracks/today`, `/api/v1/habit-preferences/*`,
`/api/v1/habit-unlocks`, y las vistas de mentor y administración de los hábitos de una persona
devuelven **menos** hábitos cuando alguno no le aplica. Ningún campo nuevo.
