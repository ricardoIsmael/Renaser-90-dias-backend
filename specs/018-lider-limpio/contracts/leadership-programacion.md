# Contrato: `programacion` en la semana de una persona (spec 018)

Amplía `specs/005-lider-cumplimiento-por-persona/contracts/leadership-cumplimiento.md` §2. **No hay endpoint nuevo.**

## `GET /api/v1/leadership/groups/{groupId}/people/{personId}/week?semanaHasta=YYYY-MM-DD`

Mismo permiso (`VIEW_MENTOR_CORPS`), mismo guard (`AccesoDeLiderazgo`: líder de mentores, Administrador o Alquimista con la
cuenta activa; grupo regular con mentor y persona de ese grupo hoy), mismo `Cache-Control: no-store`, mismo registro de
consulta. Los 403 y 404 no cambian y **no leen** nada de `habits` ni de `rocks`.

### Campo nuevo (opcional) en la respuesta

```json
{
  "...": "todo lo de siempre",
  "programacion": {
    "habitosConHora": 5,
    "habitosTotal": 17,
    "accionesConHora": 0,
    "accionesTotal": 2,
    "programo": false
  }
}
```

| Campo | Significado |
|---|---|
| `habitosTotal` | Hábitos que le tocan **hoy** (en su zona): ni apagados ni dentro de una pausa de su plan. Siempre ≥ 1 si el campo viene. |
| `habitosConHora` | De esos, los que tienen una hora que **ella eligió** (no la del catálogo). 0 ≤ … ≤ `habitosTotal`. |
| `accionesTotal` | Acciones de sus objetivos para **hoy**. Puede ser 0. |
| `accionesConHora` | De esas, las que tienen hora de inicio. 0 ≤ … ≤ `accionesTotal`. |
| `programo` | `habitosConHora × 2 ≥ habitosTotal` **y** `accionesConHora = accionesTotal`. Lo decide el servidor (`ProgramacionDelDia`); el cliente no lo recalcula. |

**El campo falta (`null`) cuando** no hay nada que programar: el programa no está activado o ningún hábito le toca hoy. Nunca
es un `false` por falta de datos.

**No depende de `semanaHasta`**: siempre es el día de hoy de la persona, aunque se mire una semana cerrada del pasado.

### Compatibilidad

- Aditivo y opcional: un cliente anterior lo ignora; el cliente nuevo, contra un servidor anterior, no dibuja la tarjeta.
- El cliente descarta (`null`) un `programacion` mal formado o con cuentas imposibles, sin perder el resto de la ficha.
