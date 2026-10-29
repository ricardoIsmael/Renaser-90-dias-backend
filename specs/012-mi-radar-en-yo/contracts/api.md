# Contratos HTTP: Mi Radar en Yo

Todo es aditivo: ningún campo existente cambia de nombre ni de tipo.

## GET /api/v1/radar/mi-radar (nuevo)

- **Permiso**: `FOLLOW_OWN_PROGRAM`. Solo la propia persona, con el participante habilitado, como el resto de `/radar`.
- **200**: `{ "encendido": true, "horas": [9, 14, 20], "dias": [ { "dia": 6, "horas": [11] }, { "dia": 7, "horas": [] } ] }`.
  `horas` son las de todos los días. `dias` son los días con horas propias (ISO: lunes = 1 … domingo = 7, cada día una
  vez); `horas: []` es «Sin Radar ese día». Un día que no está en `dias` sigue las de todos los días.
  Apagado: `{ "encendido": false, "horas": [], "dias": [] }`.
- **403**: rol sin permiso o cuenta `SUSPENDED`.

## PUT /api/v1/radar/mi-radar (nuevo)

- **Permiso**: el mismo que GET.
- **Cuerpo**: la misma forma que GET (`dias` opcional; sin él, todos los días siguen `horas`). Con `encendido: false` se
  ignoran las horas y los días.
- **200**: la configuración guardada, con la misma forma que GET.
- **400**: horas fuera de las reglas (vacías estando encendido, más de 12, fuera de 3–23 o repetidas), un día fuera de
  1–7 o repetido, o horas propias de un día fuera de las mismas reglas.
- **403**: como en GET.

## POST /api/v1/radar (cambia la regla, no la forma)

- Desde el día 8, con Mi Radar apagado, en un día sin Radar o fuera de 03:00–23:59 locales (hora y día de la semana de la
  persona, no los de UTC): **409** «Mi Radar está apagado», «Hoy no tienes Mi Radar» (día «Sin Radar») o «Mi Radar funciona de 03:00 a
  23:59».
- Los días 0 a 7 no cambian.
- La respuesta suma `horaPrevista` (`number | null`).

## GET /api/v1/radar/history y GET /api/v1/mentor/groups/{g}/learners/{u}/radar (cambia la respuesta)

- Cada ítem suma `horaPrevista: number | null`.
- La página suma `miRadar: { encendido, horas, dias } | null`.
- Paginación, permisos y tamaño de página, como hoy.

## GET /api/v1/leadership/groups/{groupId}/people/{personId}/radar?cursor= (nuevo)

- **Permiso**: `VIEW_MENTOR_CORPS` con el guardián `AccesoDeLiderazgo` (MENTOR_LEAD, ADMIN o ALCHEMIST activo; grupo
  regular con mentor; la persona pertenece hoy a ese grupo), igual que `.../week`.
- **200**: la misma forma que `/radar/history`, con `miRadar`. Página de 84.
- **403**: otro rol, cuenta suspendida o persona fuera de ese grupo. **404**: grupo inexistente, como `.../week`.
- `Cache-Control: no-store`.

## Aviso del teléfono (contrato interno de la app)

- Desde el día 8, cada hora elegida: título `Código Renaser · HH:00`, cuerpo «¿Dónde estás ahora mismo? Cinco
  preguntas, un minuto.», `data.route = '/mi-radar'`.
- Invitación del día 8: «Terminaste tu primera semana del Código Renaser», cuerpo «Si quieres seguir, enciende Mi
  Radar.», `data.route = '/mi-radar'`.
- La ruta `/mi-radar` abre Yo → Mi Radar. Si la hora en curso está abierta y sin registro, abre también el formulario.
