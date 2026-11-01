# Data model: Hábitos de todos (spec 029)

**Sin cambios de base de datos.** No hay migraciones (V151–V152 quedan sin usar) ni tablas ni columnas nuevas: todo se deriva de `registros_habito`, `habitos`, `categorias_habito`
(spec 023), `participantes_programa` y los grupos.

## Estados (`EstadoEnElDia`, dominio puro)
| Registro del día | Estado |
|---|---|
| `COMPLETADO` | CUMPLIDO |
| `PENDIENTE`, `EN_CURSO` (exigible) | PENDIENTE |
| `FALLIDO`, `EXPIRADO` (exigible) | SIN_CUMPLIR |
| opcional sin cumplir, o sin registro | SIN_DATOS (motivo: NO_EXIGIBLE, NO_LE_TOCABA o ANTES_DEL_DIA_1) — **nunca una falta** |

## Resumen de un hábito (`ResumenDeUnHabito`)
`habitoId, titulo (del catálogo), categoriaClave, categoriaCodigo, formacion, cumplieron, pendientes, sinCumplir`; `esperados = suma`; orden: fracción `cumplieron/esperados`
ascendente (multiplicación cruzada), luego título sin tildes ni mayúsculas, luego id. Un hábito con `esperados = 0` no existe.

## Ventana (`VentanaDeLosHabitos`)
`[hoy − 7, hoy]`; `hoy` = fecha de la zona por defecto del padrón.
