# Data model: «Cierra tu día» pasa a Plan

**No hay datos nuevos ni cambios en el servidor.** El cierre es el de la spec 017 (V121): una marca de «día cerrado» y la nota del día (`entradas_diario`, tipo `EVALUACION_DIA`) y, por cada acción que quedó pendiente, motivo, plan de mañana, nota y hora (`rocas_diarias.cierre_*`).

## Lo único nuevo es de la app (en memoria)

| Pieza | Qué es | Dónde |
|---|---|---|
| Lectura del cierre de hoy | `sin-leer` / `listo` / `no-disponible` / `error` (la de la 017) | `useCierreDeHoy` |
| Lectura de un día anterior | `cargando` / `datos` / `sin-datos` / `error`, una por fecha y cuenta | `useCierresAnteriores` |
| Eje de cada acción | `idDeLaAccion → EjeObjetivo` sacado de `GET /rocks/today` | `utils/ejeDeLasAcciones` |
| Días de la tarjeta | del Día 1 (o el lunes) a ayer, en orden; hoy aparte | `utils/semanaDelCierre` |

## Reglas

- La tarjeta existe si y solo si: la lectura de hoy es `listo` y `plan` está prendido y disponible.
- Un día anterior se puede leer si es ≥ el Día 1, ≥ el lunes de la semana y < hoy (`sePuedeLeerElDia`); nunca uno que viene.
- Guardar nunca completa una acción ni paga puntos (017, sin cambios).
