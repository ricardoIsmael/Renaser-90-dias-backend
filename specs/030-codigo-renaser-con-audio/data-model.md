# Data model: Código Renaser con la voz

## `renaser.registros_radar` (existente) + una columna

| Columna | Tipo | Reglas |
|---|---|---|
| `dictado` (nueva, V132) | `boolean not null default false` | `true` si al menos una de las cuatro respuestas se dictó con el micrófono y sigue en el texto registrado. Nace con el registro; la tabla sigue siendo append-only, nunca se edita. Registros anteriores a V132 y los de SER: `false`. |

El resto (`que_hago`, `que_pienso`, `que_siento`, `que_evito`, `nivel_energia`, `creado_en`, `hora_prevista`) no cambia. El índice único
`radar_un_registro_por_hora` tampoco: un segundo registro en la hora sigue siendo 409 y devuelve el primero, con **su** `dictado`.

## Dominio

`RegistroRadar` gana el campo `dictado` (boolean). Se crea con `registrar(...)` en `false` y se marca con el método de intención
`comoDictado()` (copia, como `conHoraPrevista`). `rehydrate(...)` recibe el valor guardado.

## Dictado (transitorio, solo en el teléfono)

Lo que la persona dice mientras el micrófono está abierto. Se convierte en texto del campo; **no se guarda ni se envía como audio**.
El borrador local (AsyncStorage, `renaser.radar.borrador.*`) suma `dictados: CampoRadar[]` (qué respuestas llevan algo dictado) para que el
dato sobreviva a cerrar y reabrir el formulario; un borrador viejo sin ese campo se lee como «ninguna».

## Pregunta hablada (contenido fijo de la app)

Cuatro MP3 por pregunta, uno por campo (`whatAmIDoing`, `whatAmIThinking`, `whatAmIFeeling`, `whatAmIAvoiding`), con lo que dice cada
uno en `vocesDelRadar.json`. No pertenecen a la persona.
