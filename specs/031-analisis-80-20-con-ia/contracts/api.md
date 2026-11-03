# Contrato: Análisis 80/20 con IA

Solo lectura. **Ninguna ruta genera ni llama a la IA** (FR-006): el análisis lo deja el barrido horario. Respuestas con `Cache-Control: no-store` (datos de una persona).

## 1. La propia persona

`GET /api/v1/analisis-80-20?periodo=SEMANA|MES&inicio=YYYY-MM-DD`

- `periodo` (opcional, por defecto `SEMANA`); `inicio` (opcional): el primer día de un periodo, tal como lo devolvieron `anterior` o `siguiente`. Sin `inicio`: el periodo en curso, en la zona de la persona.
- Permiso `USE_APP`, funcionalidad `plan` (con `ia.analisis` apagada las cifras se siguen viendo; el texto lo esconde la app).
- Siempre sobre quien llama: no hay parámetro para otra persona.

## 2. Mentor o guía (ficha)

`GET /api/v1/mentor/groups/{groupId}/learners/{learnerId}/analisis-80-20?periodo=&inicio=` — funcionalidad `mentor.mi_grupo`; el mismo guardia que la ficha del aprendiz (acompañante vigente del grupo).

## 3. Líder de mentores, Administración y Alquimista (ficha)

`GET /api/v1/leadership/groups/{groupId}/people/{personId}/analisis-80-20?periodo=&inicio=`
`GET /api/v1/leadership/people/{personId}/analisis-80-20?periodo=&inicio=` (sin grupo, spec 021)

Funcionalidad `lider.cumplimiento`; `AccesoDeLiderazgo` decide (MENTOR_LEAD/ADMIN/ALCHEMIST activo). Cada lectura deja constancia en `consultas_de_ficha`.

## Respuesta (200, las tres rutas)

```json
{
  "tipo": "SEMANA",
  "numero": 10,
  "inicio": "2026-10-26",
  "fin": "2026-11-01",
  "cerrado": true,
  "anterior": "2026-10-19",
  "siguiente": null,
  "cifras": {
    "desde": "2026-10-26", "hasta": "2026-11-01",
    "planificadas": 12, "cumplidas": 9, "diasConAcciones": 5, "porVenir": 0,
    "impactoPlanificado": 60, "impactoCumplido": 45, "porcentajeCumplidas": 75,
    "verdes": { "planificadas": 5, "cumplidas": 3, "impactoCumplido": 25, "porcentajeDelImpacto": 55 },
    "veintePorCiento": { "acciones": 2, "impacto": 17, "porcentajeDelImpacto": 38 },
    "porEje": [ { "eje": "CUERPO", "planificadas": 4, "cumplidas": 3, "impactoCumplido": 22, "porcentajeDelImpacto": 49 } ],
    "lasQueMasMovieron": [ { "eje": "CUERPO", "color": "VERDE", "impacto": 10, "fecha": "2026-10-27", "titulo": "Entrenar" } ],
    "lasQueSeFueron": [ { "eje": "TRABAJO", "color": "VERDE", "impacto": 9, "fecha": "2026-10-29", "titulo": "Cerrar la propuesta" } ],
    "cumplioLoQueMenosMovia": true,
    "cierre": { "diasCerrados": 4, "diasSinCerrar": 1, "motivos": [ { "clave": "SIN_TIEMPO", "veces": 3 } ], "planes": [ { "clave": "OTRA_HORA", "veces": 2 } ] }
  },
  "semanas": [],
  "estado": "LISTO",
  "falta": [],
  "minimoDeAcciones": 5,
  "codigoRenaser": { "respuestas": 12, "dictadas": 3, "energiaPromedio": 6.4, "energiaMinima": 3, "energiaMaxima": 9,
                     "porMomento": { "manana": 7.1, "tarde": 6.0, "noche": null }, "porSemana": [] },
  "analisis": {
    "loQueMasMovio": "…", "loQueSeTeFue": "…", "comoEstuviste": "…", "proximosPasos": ["…", "…"]
  },
  "generadoEn": "2026-11-02T13:15:00Z"
}
```

- `estado`: `EN_CURSO` (no cerró: solo cifras) · `LISTO` · `PENDIENTE` (cerró; falta generarlo) · `SIN_DATOS` (no alcanzó el mínimo; ver `falta`: `ACCIONES` y/o `DIAS`) · `SIN_ANALISIS` (se acabaron los intentos).
- `analisis` y `generadoEn` solo con `LISTO`. `verdes`/`veintePorCiento` pueden ser `null` (una regla sin base no se dice). `semanas` solo en un mes. `codigoRenaser` es `null` con menos de 3 respuestas.
- `cifras` de un periodo LISTO son la foto que el texto cita; las de uno en curso, las de ahora.
- Campos nuevos pueden sumarse: los lectores ignoran lo que no conocen.

## Rechazos

| Código | Cuándo |
|---|---|
| 400 | `periodo` que no es `SEMANA` ni `MES`; `inicio` que no es el primer día de un periodo, o de un periodo que todavía no llega |
| 403 | quien llama no puede ver a esa persona (rol sin acceso, no es su acompañante) |
| 404 | la persona no eligió su Día 1; la persona es de otra formación (nunca se dice que existe); `radar.fichas` apagada para el rol del mentor/guía/líder (`FUNCIONALIDAD_NO_DISPONIBLE`). El 403 de siempre gana al 404 |
| 503 | la funcionalidad (`plan`, `mentor.mi_grupo`, `lider.cumplimiento`) está en mantenimiento |

## Aviso del teléfono

Tipo `ANALISIS_80_20`; texto fijo: «Tu análisis de la semana está listo» / «Tu análisis del mes está listo», cuerpo «Entra a Plan y míralo.»; ruta `/plan/80-20?periodo=SEMANA|MES&inicio=YYYY-MM-DD`. Preferencia `ANALISIS_80_20` en `notification-preferences` (encendida de fábrica). Nunca lleva el análisis.

## Herramienta de SER

`consultar_analisis_80_20` (sin parámetros): devuelve, de la persona que conversa, el último análisis LISTO de la semana y del mes (cuatro partes y fecha), o dice que todavía no hay. Solo lectura. SER solo lo explica; no cambia el plan.

## Interruptores

- `ia.analisis` (hijo de `ia`): apagado, el barrido difiere (no gasta intentos) y la app cambia el texto por el aviso de mantenimiento.
- `radar.fichas` (spec 034): gobierna a mentor, guía y líder en las rutas 2 y 3.
