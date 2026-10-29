# Contrato: huecos de horario (spec 015, primera versión)

Solo lectura para el acompañamiento. La persona no tiene endpoint en esta versión.

## Rutas (las dos con la misma respuesta)
- Mentor o guía vigente del grupo: `GET /api/v1/mentor/groups/{groupId}/learners/{userId}/huecos?semana=YYYY-MM-DD`
  (mismo guardián que `/mentor/groups/{g}/learners/{u}/radar`).
- Líder de mentores, administración y alquimista: `GET /api/v1/leadership/groups/{groupId}/people/{personId}/huecos?semana=YYYY-MM-DD`
  (mismo guardián `AccesoDeLiderazgo` que `.../week` y `.../radar`).
- `semana` es el lunes; sin ella, la semana en curso de la persona. Una semana futura o anterior a su primera semana da 400.
- 403 a cualquier otro rol o a una cuenta suspendida. `Cache-Control: no-store`.

## Respuesta 200
```json
{
  "semana": "2026-10-26",
  "primeraSemana": "2026-10-12",
  "cerrada": false,
  "dias": [
    {
      "fecha": "2026-10-26",
      "ventana": { "desde": "05:30", "hasta": "22:30" },
      "minutosOcupados": 410,
      "minutosLibres": 390,
      "huecos": [ { "desde": "15:00", "hasta": "17:00", "minutos": 120 } ]
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
