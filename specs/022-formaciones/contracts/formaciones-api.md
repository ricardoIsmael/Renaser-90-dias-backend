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

## Administración (ADMIN y ALQUIMISTA activos; el guard real es `RequireAdminGuard` / el servicio, y responde 403 a cualquier otro rol o a una cuenta suspendida)

Los nombres de las rutas y de los campos son los **implementados** (esta sección reemplaza al borrador del plan).

### `GET /api/v1/admin/formaciones`  →  `[TarjetaResponse]` (3, en orden Escuela, Diplomado, Certificación)
```json
{ "resumen": { "codigo": "DIPLOMADO", "nombre": "Diplomado Renaser", "descripcion": "…", "activa": true,
               "duracionesDias": [90, 365], "etiquetasDeDuracion": ["3 meses", "1 año"], "version": 3 },
  "aprendicesActivos": 12, "personal": 3, "grupos": 2, "porAtender": 5 }
```
`porAtender` de la tarjeta = **grupos sin mentor + aprendices sin grupo de esa formación** (el personal sin ninguna formación no es de ninguna: se cuenta aparte).

### `GET /api/v1/admin/formaciones/{codigo}`  →  `DetalleResponse`
```json
{ "resumen": { … },
  "personas":  { "aprendices": 0, "porEmpezar": 0, "enCurso": 0, "terminaron": 0, "personal": 0 },
  "grupos":    { "total": 0, "conMentor": 0, "sinMentor": 0, "recepcion": 0 },
  "porAtender":{ "gruposSinMentor": 0, "aprendicesSinGrupo": 0, "personalSinFormacion": 0 } }
```
Las cifras salen de las mismas tablas que las listas: `personas.aprendices` = `GET /admin/trainees?role=TRAINEE&formation={codigo}` (total); `grupos.total` = los grupos de esa
formación (recepción incluida). `404` si el código no existe. `aprendices`/`porEmpezar`/… excluyen las cuentas de prueba.

### `PUT /api/v1/admin/formaciones/{codigo}`  `{ nombre, descripcion, activa, duracionesDias:[…], version }`  →  `FormacionResponse` (el resumen nuevo)
Valida nombre 3–80, descripción ≤ 600, 1–4 duraciones únicas entre 1 y 365. Las personas que ya empezaron **no cambian** de duración (FR-004). `409` si `version` no es la vigente
(«otra persona la cambió»). Permiso `MANAGE_ROLES`.

### `GET /api/v1/admin/users/{id}/formaciones`  →  `FormacionDeUnaPersonaResponse`
```json
{ "formacion": "DIPLOMADO", "duracionDias": 365, "programaActivado": true,
  "formacionesDelPersonal": [ { "formacion": "ESCUELA", "activa": true } ],
  "historial": [ { "formacion": "ESCUELA", "duracionDias": 90, "fechaInicio": "2026-07-01", "fechaFin": "2026-10-30", "diaFinal": 90,
                   "resultado": "TERMINADA", "motivo": null, "puntosLiga": 240, "trasladadoA": "DIPLOMADO" } ] }
```
`resultado` = `TERMINADA | DADA_POR_TERMINADA` (esta última siempre con `motivo`). Permiso `MANAGE_TRAINEES`.

### `PUT /api/v1/admin/users/{id}/formaciones`  `{ "formaciones": ["ESCUELA","DIPLOMADO"], "activa": "ESCUELA"? }`  →  204
Solo mentor, guía de aprendiz y líder de mentores. Al menos una. No quita una formación en la que lidera un grupo (`409` con el nombre del grupo). Rige en su siguiente pedido (Q11-A).
Permiso `MANAGE_ROLES`.

### `POST /api/v1/admin/trainees/{id}/formacion`  `{ "formacion": "DIPLOMADO", "duracionDias": 365, "darPorTerminada": false, "motivo": null }`  →  204
Solo aprendices. `409` si el programa no terminó y no se dio por terminado con motivo (3–500) o si ya está en esa formación; `400` si la formación está apagada o no ofrece esa
duración; `404` si la persona no existe. Sin Día 1 elegido: corrige formación/duración sin historial. Con historial: guarda la foto en `participaciones_anteriores`, reinicia la
participación (día 0, sin grupo ni mentor) y publica `AprendizTrasladadoDeFormacionEvent` (la comunidad cierra sus grupos con motivo `TRASLADO` y la mete en la recepción de la
nueva; `points` reinicia 100 / coherencia 100 / racha 0). Permiso `MANAGE_TRAINEES`.

### Cambios en endpoints existentes (todos aditivos, con los nombres de siempre en inglés)
- `GET /api/v1/admin/trainees` (y `/{id}`): cada fila suma `formation` (código), `programDuration` y, si es personal, `staffFormations[]`; filtro `?formation=` (vacío = todas).
- `GET /api/v1/admin/cells/dashboard`, `/mentores`, `/aprendices-disponibles` y sus candidatos: filtro `?formation=`; `CelulaResponse`/`CelulaDashboardResponse` suman `formation`.
  `GET /admin/cells?cohortId=` lista los grupos de la cohorte con su `formation` (Administración ve todas).
- `POST /api/v1/admin/cells` acepta `formation` (opcional; Escuela por defecto). Asignar un mentor o un aprendiz de otra formación → `409`
  «No se puede sumar a esta persona al grupo «X»: es de … y el grupo es de la formación …» (Administración y Alquimista, que lideran en cualquiera, quedan exentos).
- Listas de mentor/líder, padrón y reporte de mentores, semáforo por grupos, directorio de chat y «Mi grupo»: filtradas por el alcance de quien pide (aislamiento); una persona o un
  grupo de otra formación responde `404` (`ObjetoDeOtraFormacionException`).
- `GET /api/v1/home` suma `duracionDias` y `formacion` (código).

## Compatibilidad
- Todo campo nuevo es opcional para el cliente. La app 1.6.0 ignora lo nuevo y sigue en la Escuela; contra un backend anterior, la app nueva trata a todos como de la Escuela.
- Los caminos que reciben un cuerpo sin `formacion` caen en `ESCUELA` (`DEFAULT 'ESCUELA'` / 90 en la base).

## Contrato que las specs siguientes usan encima de la 022

| Spec | Qué usa de la 022 | Cómo |
|---|---|---|
| **023** hábitos por formación | `AlcanceDeFormacionFinder` + columna de formación en el catálogo | Una columna `formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones(codigo)` en la tabla de hábitos (o la audiencia que ya existe) y el filtro `FiltroDeFormacionSql.predicado("h.formacion", "formaciones")` con `FiltroDeFormacionSql.codigos(alcance)` en cada lectura (hoy, planificar, generación diaria). El día del hábito se compara en la escala del programa de la persona (`DuracionDelPrograma.enEscalaBase`). Hoy los topes de 90 de `DesbloqueoHabitoService`, `dia_inicio` y `HabitosDeUnDia` siguen en 90 |
| **024** comunidad por formación | lo mismo, para Muro, cursos, eventos, testimonios, ranking y chats de comunidad | Columna `formacion` en cada contenido; `alcance.exigirQueIncluya(formacion)` al abrir un objeto concreto (→ 404); el ranking se calcula por formación; el chat global se divide por formación (hoy `chat` ya filtra el directorio y los grupos) |
| **025** solicitud con formación | `ParticipacionPrograma.inscribirTraineeAprobado(id, clock, formacion, duracionDias)` y `FormacionesFinder` (nombres, duraciones y `activa` del catálogo) | La solicitud guarda la formación que Administración elige al aceptar; `AccountRequestService` crea la participación con ella; «Solicitudes» gana el filtro cuando exista la columna |
| **027** términos editables | `FormacionesFinder` + `AlcanceDeFormacionFinder.formacionDe(persona)` | Los términos vigentes se leen por la formación de la persona; el centro «Formaciones» ya tiene el hueco para mostrar «Ficha y términos» |
| **029** líder ve todos los hábitos | `AlcanceDeFormacionFinder.de(actor)` en `leadership` (`PersonasDelPrograma`/`PersonasDelGrupo` ya filtran por el alcance del líder) | La lectura de hábitos de una persona exige `exigirQueLaVea(actor, persona)` antes de leer; el líder con varias formaciones ve solo la activa |

Reglas comunes: (1) **una persona de otra formación o un objeto de otra formación = 404**; (2) el filtro lo hace el servidor siempre, nunca la app; (3) para reaccionar a un traslado,
escuchar `users.api.AprendizTrasladadoDeFormacionEvent` con `@ApplicationModuleListener` (así lo hacen `community` y `points`); (4) `users` no importa a nadie: la información inversa
se pide con una interfaz en `users.api` implementada por el otro módulo (`GruposDelPersonalFinder`, `PuntajeDelAprendizFinder`).
