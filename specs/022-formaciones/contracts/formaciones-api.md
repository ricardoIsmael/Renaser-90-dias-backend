# Contrato: endpoints de formaciones (spec 022)

Todo es **aditivo**. Un cliente anterior ignora los campos nuevos. Todos los errores usan el cuerpo de siempre (`{ message }`).

## Sesión

### `GET /api/v1/auth/me`, `POST /api/v1/auth/login`, `POST /api/v1/auth/social`, `POST /api/v1/users/me` — campo nuevo `formacion`
```json
"formacion": {
  "activa": { "codigo": "DIPLOMADO", "nombre": "Diplomado Renaser" },
  "disponibles": [ { "codigo": "ESCUELA", "nombre": "Escuela Renaser" }, { "codigo": "DIPLOMADO", "nombre": "Diplomado Renaser" } ],
  "puedeElegir": true,
  "veTodas": false,
  "sinFormacion": false,
  "duracionDias": 365,
  "fases": [ { "numero": 1, "primerDia": 1, "ultimoDia": 28 }, { "numero": 2, "primerDia": 29, "ultimoDia": 137 }, … ]
}
```
- Aprendiz: `activa` = la suya, `disponibles` = `[activa]`, `puedeElegir=false`, `duracionDias` y `fases` de su duración.
- Personal con una formación: igual que el aprendiz (sin selector). Con varias: `puedeElegir=true`. Sin ninguna: `activa=null`, `sinFormacion=true`.
- Administración, Alquimista, Soporte TI: `veTodas=true`, `activa=null`, `disponibles` = las tres.
- `duracionDias` y `fases` salen de su participación (si no tiene, de 90 / las fases de siempre).

### `GET /api/v1/me/formacion` → el mismo objeto
Permiso `USE_APP`. La app lo relee al volver al primer plano (detecta un traslado o un cambio de asignaciones).

### `PUT /api/v1/me/formacion/activa`  `{ "codigo": "ESCUELA" }` → el objeto de arriba
Solo personal con esa formación asignada; otro caso → 403 `No tienes asignada esa formación.` Un aprendiz / Administración → 403.

## Administración (permiso `MANAGE_TRAINEES`: Administración y Alquimista)

### `GET /api/v1/admin/formaciones`
`[{ codigo, nombre, descripcion, activa, duracionesDias:[90,365], etiquetasDeDuracion:["3 meses","1 año"], aprendicesActivos, personal, grupos, porAtender }]` (3 elementos, en orden).

### `GET /api/v1/admin/formaciones/{codigo}`
```json
{ "resumen": { codigo, nombre, descripcion, activa, duracionesDias, etiquetasDeDuracion, version },
  "personas": { "porEmpezar": 0, "enCurso": 0, "terminaron": 0, "personal": 0 },
  "grupos": { "total": 0, "conMentor": 0, "sinMentor": 0, "recepcion": 0 },
  "porAtender": { "gruposSinMentor": 0, "personasSinGrupo": 0, "personalSinFormacion": 0 } }
```
Cada cifra corresponde a una lista existente con `?formacion={codigo}`. `404` si el código no existe.

### `PUT /api/v1/admin/formaciones/{codigo}`  `{ nombre, descripcion, activa, duracionesDias:[…], version }`
Valida: nombre 3–80, descripción ≤ 600, 1–4 duraciones únicas entre 1 y 365, nunca quitar una duración que… (las personas que ya empezaron **no cambian**, FR-004). `409` si `version` no coincide.

### `PUT /api/v1/admin/users/{id}/formaciones`  `{ "formaciones": ["ESCUELA","DIPLOMADO"], "activa": "ESCUELA"? }`
Solo personal. Al menos una (salvo vaciar). No quita una formación en la que lidera un grupo o tiene aprendices (`409` con el grupo). Rige en su siguiente pedido (Q11-A).

### `POST /api/v1/admin/trainees/{id}/formacion`  `{ "formacion": "DIPLOMADO", "duracionDias": 365, "darPorTerminada": false, "motivo": null }`
Traslado. `409` si el programa no terminó y no se dio por terminado con motivo (3–500). `400` si la formación no ofrece esa duración. `404` si la persona no existe.
Sin Día 1 elegido: corrige formación/duración sin historial. Responde el detalle actualizado.

### Cambios en endpoints existentes (todos con `formacion` aditivo)
- `GET /api/v1/admin/trainees` (y detalle): cada fila trae `formacion` (`codigo`), `duracionDias`; filtro `?formacion=`. El detalle suma `historial[]` y, si es personal, `formaciones[]`.
- `GET /api/v1/admin/cells/dashboard`, `GET /api/v1/admin/cells`: filtro `?formacion=`; cada grupo trae `formacion`.
- `POST /api/v1/admin/cells`: acepta `formacion` (opcional, Escuela por defecto). Asignar un mentor o un aprendiz de otra formación → `409`
  `No se puede sumar a …: es de la formación X y el grupo es de la formación Y.`
- Listas de mentor/líder y búsquedas de personas: filtradas por el alcance de quien pide (aislamiento).
