# Contrato: Cierra tu día (spec 017)

Todo campo nuevo del que dependa la app es **opcional para ella** (esquemas tolerantes): una app con un campo de menos no se rompe.
Los enums viajan como el nombre de la constante.

## Valores

| Campo | Valores |
|---|---|
| `motivo` (¿Qué pasó?) | `SIN_TIEMPO` «No me alcanzó el tiempo» · `SURGIO_OTRA_COSA` «Surgió otra cosa» · `SIN_ENERGIA` «Me faltó energía» · `PARA_DESPUES` «Lo dejé para después» |
| `manana` (¿Qué harás mañana?) | `OTRA_HORA` «Hacerla a otra hora» · `MAS_CORTA` «Hacerla más corta» · `PEDIR_AYUDA` «Pedir ayuda» · `DEJARLA` «Dejarla» |
| `estado` del día | `ABIERTO` (hoy, sin cerrar) · `CERRADO` · `SIN_CERRAR` (un día que ya terminó sin cierre) |

## La persona (Training)

Permiso `FOLLOW_OWN_PROGRAM`; `@Funcionalidad("plan")`. Aprendiz, o personal con su programa activado; cuenta suspendida o sin programa → 403.

### `GET /api/v1/rocks/day-closure?date=YYYY-MM-DD`
`date` es opcional y por defecto **hoy en la zona de la persona**. Una fecha futura es **400**. Cualquier otra se lee.

```json
{
  "fecha": "2026-10-29",
  "estado": "ABIERTO",
  "puedeCerrar": true,
  "cerradoEn": null,
  "nota": null,
  "acciones": [
    { "id": "…", "titulo": "Enviar la propuesta al cliente", "horaInicio": "10:00:00", "cumplida": true,
      "cumplidaEn": "2026-10-29T15:20:00Z", "cierre": null },
    { "id": "…", "titulo": "Revisar las facturas", "horaInicio": "20:30:00", "cumplida": false, "cumplidaEn": null,
      "cierre": { "motivo": "SIN_TIEMPO", "manana": "OTRA_HORA", "nota": "…" } }
  ],
  "semana": { "cumplidas": 7, "planificadas": 12 }
}
```
- `puedeCerrar` es `true` solo si `fecha` es hoy.
- `cierre` es `null` en una acción cumplida y en una pendiente sin respuesta guardada.
- `nota` es la **nota del día** (la de `entradas_diario`).
- `semana` (acciones cumplidas / planificadas de los días ya vividos de la semana de programa, hoy incluido) viene solo si la fecha es
  hoy y la persona ya eligió su Día 1; si no, `null`.
- Orden de `acciones`: hora de inicio (sin hora, al final), luego posición.
- `horaInicio` viaja como `HH:mm:ss` (la serialización de siempre del servidor); la app acepta también `HH:mm`.
- > **Agregado 2026-10-30.** Cada acción de esta ruta lleva `exigeRespuesta` (booleano): `true` si al cerrar hoy se pregunta por ella
  > —está pendiente y su `horaInicio` **ya llegó** en la zona de la persona, o no tiene hora— y `false` si no se pregunta (cumplida, o
  > pendiente de **hora futura**). Lo calcula el servidor con su `Clock` y la zona de la persona; en un día que no es hoy vale `false`
  > para todas. Es propio de `GET/PUT /rocks/day-closure`: la ficha del acompañamiento no lo lleva. Es opcional para la app: sin él
  > (un servidor anterior) se pregunta por todas las pendientes, como siempre.
- `semana` también es `null` cuando la semana no tiene acciones planificadas hasta hoy (nunca «0 de 0»).

### `PUT /api/v1/rocks/day-closure`
Cierra (o vuelve a cerrar) **el día de hoy**. Idempotente: reemplaza todas las respuestas del día.
```json
{ "fecha": "2026-10-29",
  "acciones": [ { "id": "…", "motivo": "SIN_TIEMPO", "manana": "OTRA_HORA", "nota": "opcional" } ],
  "nota": "opcional" }
```
- Respuesta **200** con la misma forma del `GET` (estado `CERRADO`).
- Hace falta **una respuesta por cada acción que siga pendiente**; las de acciones ya cumplidas se ignoran (y limpian cualquier respuesta vieja).
  > **Corregido 2026-10-30 (decisión del dueño).** Solo se exige una respuesta por cada pendiente **cuya hora de inicio ya llegó** en la zona
  > de la persona (con su `Clock`), y por las que **no tienen hora** (como hoy). Una pendiente de **hora futura** no se exige: si viene
  > con respuesta se guarda, y si no viene queda sin respuesta (guardar reemplaza lo del día, así que también limpia la que tuviera).
  > Una hora igual a la actual ya llegó. Sin la respuesta de una exigida, 400 con el nombre de la acción, y no se guarda nada.
- **400**: motivo o plan desconocido; falta la respuesta de una pendiente; una acción que no es de ese día o de esa persona; una acción
  repetida; nota > 500 caracteres.
- **409**: `fecha` no es hoy en la zona de la persona. Mensaje: «Ese día ya cerró y no se puede cerrar tarde.» (lleva «ese día ya cerró»,
  que la app ya reconoce como rechazo por día cerrado). **Nada se guarda.**
- **403**: cuenta suspendida o programa sin activar. **503** `FUNCIONALIDAD_APAGADA` si Soporte TI apagó `plan`.
- Nunca completa una acción, paga puntos ni publica eventos.

## El acompañamiento (fichas)

Solo lectura, `Cache-Control: no-store`, la misma respuesta en las dos rutas.

- Mentor o guía vigente del grupo: `GET /api/v1/mentor/groups/{groupId}/learners/{userId}/cierres?semana=YYYY-MM-DD`
  (`USE_APP` + el guardián del servicio: acompañante vigente del grupo y alumno de ese grupo, cuenta activa; `@Funcionalidad("mentor.mi_grupo")`).
- Líder de mentores, Administración y Alquimista: `GET /api/v1/leadership/groups/{groupId}/people/{personId}/cierres?semana=YYYY-MM-DD`
  (`VIEW_MENTOR_CORPS` + `AccesoDeLiderazgo`; `@Funcionalidad("lider.cumplimiento")`). Deja una fila en `consultas_de_ficha` con el lunes
  que se miró, solo si la respuesta es 200.
- `semana` es el lunes; sin ella, la semana en curso de la persona (en SU zona). Una semana futura, anterior a su primera semana, o que
  no es lunes es **400**. 403 a cualquier otro rol.

```json
{
  "semana": "2026-10-26",
  "primeraSemana": "2026-10-12",
  "dias": [
    { "fecha": "2026-10-28", "estado": "CERRADO", "cerradoEn": "2026-10-29T03:02:00Z", "nota": null,
      "acciones": [ { "id": "…", "titulo": "…", "horaInicio": "20:30:00", "cumplida": false, "cumplidaEn": null,
                       "cierre": { "motivo": "SIN_TIEMPO", "manana": "OTRA_HORA", "nota": null } } ] },
    { "fecha": "2026-10-29", "estado": "ABIERTO", "cerradoEn": null, "nota": null, "acciones": [] }
  ],
  "resumen": { "cerrados": 1, "sinCerrar": 0, "motivos": [ { "motivo": "SIN_TIEMPO", "veces": 1 } ] }
}
```
- `dias`: del Día 1 de la persona (o el lunes) a hoy (o el domingo), en orden. Nunca un día que viene. Sin Día 1 elegido: `[]` y `primeraSemana: null`.
- `resumen.motivos`: cuántas veces aparece cada motivo en las acciones **pendientes** de esos días, de más a menos.
- `resumen.cerrados` / `sinCerrar` cuentan los días listados (el día de hoy abierto no cuenta como «sin cerrar»).
