# Contrato: huecos de horario (spec 015, primera versión)

Solo lectura para el acompañamiento. La persona no tiene endpoint en esta versión.

## Rutas (las dos con la misma respuesta)
- Mentor o guía vigente del grupo: `GET /api/v1/mentor/groups/{groupId}/learners/{userId}/huecos?semana=YYYY-MM-DD`
  (mismo guardián que `/mentor/groups/{g}/learners/{u}/radar`).
- Líder de mentores, administración y alquimista: `GET /api/v1/leadership/groups/{groupId}/people/{personId}/huecos?semana=YYYY-MM-DD`
  (mismo guardián `AccesoDeLiderazgo` que `.../week` y `.../radar`).
- `semana` es el lunes; sin ella, la semana en curso de la persona. Una semana futura o anterior a su primera semana da 400.
- 403 a cualquier otro rol o a una cuenta suspendida. `Cache-Control: no-store`.
- **Constancia de la consulta (FR-027).** La ruta del líder, administración y alquimista deja una fila en
  `consultas_de_ficha` (la misma de `.../week`, spec 005 FR-016) con el lunes de la semana que se miró, solo si la
  respuesta es 200. Un 400, 403 o 404 no deja nada. La ruta del mentor o la guía no deja constancia. Sin cambio en el
  JSON ni en las rutas.

## Respuesta 200
```json
{
  "semana": "2026-10-26",
  "primeraSemana": "2026-10-12",
  "hoy": "2026-10-29",
  "cerrada": false,
  "dias": [
    {
      "fecha": "2026-10-26",
      "ventana": { "desde": "05:30", "hasta": "22:30" },
      "minutosOcupados": 410,
      "minutosLibres": 390,
      "huecos": [ { "desde": "15:00", "hasta": "17:00", "minutos": 120 } ],
      "sinVentanaPor": null,
      "ocupados": [
        { "desde": "05:30", "hasta": "06:00", "tipo": "HABITO", "titulo": "Despertar" },
        { "desde": "09:00", "hasta": "10:30", "tipo": "EVENTO", "titulo": "Reunión de equipo" },
        { "desde": "14:00", "hasta": "15:00", "tipo": "AGENDA", "titulo": null }
      ],
      "cumplidosATiempo": 5,
      "cumplidosTarde": 1,
      "noCumplidos": 0
    }
  ],
  "resumen": {
    "minutosOcupados": 2870,
    "minutosLibres": 2730,
    "huecosQueSeRepiten": [ { "desde": "15:00", "hasta": "17:00", "dias": [1, 3, 4] } ],
    "cumplidosATiempo": 32,
    "cumplidosTarde": 6,
    "noCumplidos": 4
  }
}
```
- `ventana` es `null` si ese día no hay Despertar ni Dormir (entonces es de 00:00 a 24:00 y se dice así en la app).
- `huecos`: solo los de 30 minutos o más, dentro de la ventana, en orden.
- `cerrada: true` sale de la foto guardada al cerrar la semana; `false` es la semana en curso, calculada al pedirla.
- `dias` lleva los 7 días de la semana (lunes a domingo). Un día que viene muestra lo planificado; uno pasado, también lo real en `resumen`.

## Corregido 2026-10-29: la agenda del día (aditivo; lo de arriba no cambia)
El dueño pidió ver el tiempo libre «por día». Cada día suma, y la semana suma `hoy`:
- `hoy`: el día de hoy en la zona de la persona (`yyyy-MM-dd`). Con él la app marca hoy y dice «Todavía no llega» en los días que vienen.
- `ocupados`: los bloques de lo planificado ese día, uno por hábito, acción, evento o tramo de agenda personal. `tipo` es `HABITO`,
  `ACCION`, `EVENTO` o `AGENDA`; `titulo` es el nombre y es `null` en `AGENDA` (la persona los dijo sin etiqueta). Las horas son
  `HH:mm`. Van **recortados a la ventana** del día, **en orden de hora** (inicio, fin, tipo, nombre) y **sin unir**: dos que se cruzan vienen
  los dos. Lo que cae fuera de la ventana (Dormir, que empieza donde termina) no sale. Sin ventana, el día entero.
- `cumplidosATiempo`, `cumplidosTarde`, `noCumplidos`: lo de ese día, con la cuenta del `resumen`. Ceros en un día que todavía no llega.
- `sinVentanaPor`: solo cuando `ventana` es `null`. `SIN_DESPERTAR`, `SIN_DORMIR`, `SIN_DESPERTAR_NI_DORMIR` o `DORMIR_ANTES_DE_DESPERTAR`.

**Un día de una semana `cerrada: true` no trae `ocupados`, los tres conteos ni `sinVentanaPor`** (vienen en `null`): la foto de la semana
(`huecos_semana`, V120) guarda tramos de tiempo y no títulos. Un cliente tiene que tolerar su ausencia; los campos de antes (`ventana`,
`minutosOcupados`, `minutosLibres`, `huecos`) y el `resumen` siguen siempre.
