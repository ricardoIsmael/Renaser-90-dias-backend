# Data Model: Tipos de alarma por día (spec 009)

## Columnas nuevas (V112)

En `preferencias_horario` (general) **y** en `horario_semanal_habito` (por día):

| Columna | Tipo | `NULL` |
|---|---|---|
| `alarma_sonido` | boolean | hereda (general: sí) |
| `alarma_vibracion` | boolean | hereda (general: sí) |
| `alarma_voz` | boolean | hereda (general: según el sonido de Yo → Alarmas) |
| `alarma_repetir` | boolean | hereda (general: no) |

**CHECK** en las dos tablas: `NOT (alarma_sonido IS FALSE AND alarma_vibracion IS FALSE)`.

**CHECK** de `horario_semanal_habito` (amplía el de la 008): `NOT activo OR hora_disparo IS NOT NULL OR recordatorio_activo IS NOT NULL OR alarma_sonido IS NOT NULL OR alarma_vibracion IS NOT NULL OR alarma_voz IS NOT NULL OR alarma_repetir IS NOT NULL`.

## Resolución por día

Se resuelve campo por campo, en este orden: general (o lo de hoy si es `NULL`) → día de la semana. La fecha puntual no lleva combinación en esta spec.

Reglas al resolver:
- Voz sin sonido → voz apagada.
- Sonido y vibración los dos apagados no puede pasar: lo frena la validación al guardar y el CHECK.

## Transiciones

- `PUT /{id}/alarm`: guarda la combinación general.
- `PUT …/weekdays/{wd}/alarm`: guarda la del día. Si queda igual a la general, vuelve a `NULL`; si la fila queda sin nada propio, se borra.
- `DELETE …/weekdays/{wd}`: borra la fila entera, incluida la combinación (FR-010).
- No cobran cupo.

## Alarma local

- **Canal**: sale de la combinación del día (research R1).
- **Repeticiones**: `DATE` a +5 y +10 min de la hora del hábito, en los próximos 7 días con `repetir`. Identificadores `habito-{id}-rep-{AAAAMMDD}-{5|10}`. Las de hoy se cancelan al tocar la alarma o completar el hábito.
