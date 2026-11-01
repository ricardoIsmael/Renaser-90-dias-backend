# Contrato: endpoints del diagnóstico (spec 026)

Todo es **aditivo** salvo lo que dice «Retiro del Mapa». Los errores usan el cuerpo de siempre (`{ message }`); los que traen `codigo` usan `{ message, codigo, ... }`. Nombres de campos en inglés como el resto de la API,
**excepto** los que ya vienen del servidor en español en este contrato (los de arriba, tal cual). Valores de eje: `CUERPO`, `TRABAJO`, `RELACIONES`.

## 1. La persona

### `GET /api/v1/onboarding/state` — campo nuevo `ficha`
`"ficha": "ESTANDAR" | "CON_DIAGNOSTICO"`. La app decide con él qué ficha mostrar en `OnboardingFlow` (CON_DIAGNOSTICO reemplaza a la ficha estándar; sigue Términos y Día 1). Ausente = ESTANDAR.

### `GET /api/v1/diagnostico/mio/estado` (USE_APP, siempre accesible)
```json
{ "ficha": "CON_DIAGNOSTICO", "estado": "EN_CURSO", "version": 3, "completedAt": null }
```
`estado`: `NO_APLICA` (ficha estándar) · `PENDIENTE` (con diagnóstico, sin respuestas) · `EN_CURSO` · `COMPLETADO`. `version` es la versión con la que empezó (null si aún no abrió el diagnóstico).
Es lo que consultan Hoy y Yo para ofrecer «Completa tu diagnóstico» a quien ya estaba dentro y recibió el diagnóstico (026-9).

### `GET /api/v1/diagnostico/mio`
Abre el diagnóstico: la primera vez fija la versión publicada vigente como la suya (no cambia). `404` si no tiene el diagnóstico asignado o no hay versión publicada; `409` si ya lo terminó (usa `/lectura`).
```json
{
  "version": 3,
  "estado": "EN_CURSO",
  "titulo": "Diagnóstico inicial · Diplomado Renaser",
  "portada": { "titulo": "…", "mensaje": "Tiempo estimado: 15 minutos\nNo buscamos respuestas perfectas. Buscamos respuestas verdaderas." },
  "formacion": { "codigo": "DIPLOMADO", "nombre": "Diplomado Renaser" },
  "duracion": { "dias": 365, "texto": "1 año" },
  "paso": 4,
  "secciones": [
    { "clave": "conociendote", "titulo": "Conociéndote", "descripcion": "…", "preguntas": [
      { "id": 812, "clave": "diag_p1", "texto": "¿Cuál es tu nombre completo?", "ayuda": null, "tipo": "TEXTO",
        "requerida": true, "maximoOpciones": null, "largoMaximo": null,
        "opciones": [], "escala": null,
        "seMuestraSi": null, "alimenta": null, "precarga": "María Pérez" } ] } ],
  "respuestas": [ { "preguntaId": 812, "valorTexto": "María Pérez", "valorNumero": null, "valorBooleano": null, "valorEscala": null, "valorJson": null } ]
}
```
- `tipo`: `TEXTO` · `AREA_TEXTO` · `NUMERO` · `SELECCION_UNICA` · `SELECCION_MULTIPLE` · `ESCALA` · `CUADRICULA` · `FECHA` · `CASILLA` (el consentimiento). Un tipo que la app no conoce se salta sin romper (esquema tolerante).
- `opciones`: `[{ "valor": "Sí.", "etiqueta": "Sí." }]`. En `CUADRICULA` son las **filas** (`valor` = identificador de la fila). En el consentimiento (`CASILLA`) la etiqueta de la única opción es el texto de la casilla.
- `escala`: `{ "min": 1, "max": 5, "etiquetaMin": "Muy insatisfecho(a)", "etiquetaMax": "Muy satisfecho(a)" }` en `ESCALA` y `CUADRICULA` (la cuadrícula usa la misma escala en cada fila).
- `maximoOpciones`: solo `SELECCION_MULTIPLE`. `largoMaximo`: 500 en las preguntas que alimentan un objetivo (contador en el campo; el servidor lo exige).
- `seMuestraSi`: `{ "preguntaId": 820, "valor": "Sí." }` — la pregunta se muestra solo si la respuesta de `preguntaId` es ese `valor` (en `SELECCION_MULTIPLE`, si lo incluye). Las que no se muestran **no se piden ni cuentan**; sus respuestas guardadas se conservan.
- `ayuda` en el consentimiento es el **texto de la declaración**, que se muestra encima de la casilla.
- Los textos ya vienen con `{duracion}` y `{formacion}` reemplazados para esta persona («1 año», «90 días»; «Diplomado Renaser»).
- Solo vienen las preguntas de la formación de la persona (`formaciones` de cada pregunta); las secciones sin preguntas visibles no vienen. La **portada** no es un paso: es la primera pantalla.
- Valores de respuesta: texto/párrafo/fecha (`DD/MM/AAAA`) → `valorTexto`; número → `valorNumero`; única → `valorTexto` = `valor` de la opción; múltiple → `valorJson` = `["valor1","valor2"]` (texto JSON); escala → `valorEscala`; cuadrícula → `valorJson` = `{"<valor de la fila>": 4, …}`; consentimiento → `valorBooleano: true`.
- `paso`: el paso en que se quedó (lo guarda la app con `PUT /onboarding/state`, `flow: "diagnostico"`, `step`); null si empieza.

### `PUT /api/v1/diagnostico/mio/respuestas`
`{ "respuestas": [ { "preguntaId": 812, "valorTexto": "…", "valorNumero": null, "valorBooleano": null, "valorEscala": null, "valorJson": null } ] }` → `200 { "guardadas": 3 }`.
Guarda un paso entero en una transacción (upsert por pregunta). `400` si una respuesta no cuadra con su pregunta (más opciones que el máximo, una opción que no existe, texto de objetivo de más de 500, escala fuera de rango); `404` si la pregunta no es de su versión; `409` si ya terminó el diagnóstico. Una respuesta vacía (todos los valores null) borra la anterior.

### `POST /api/v1/diagnostico/mio/terminar`
Valida, marca el diagnóstico como terminado y **prepara los objetivos de Plan**. `200`:
```json
{ "estado": "COMPLETADO", "objetivos": [ { "eje": "TRABAJO", "texto": "…" } ], "prioridad": "TRABAJO", "primeraAccion": "El lunes revisaré…" }
```
`400 { codigo: "DIAGNOSTICO_INCOMPLETO", message, faltan: [812, 815] }` con los ids de las preguntas obligatorias visibles sin responder (la app lleva a la primera). Idempotente: terminar dos veces devuelve lo mismo.

### `GET /api/v1/diagnostico/mio/resumen`
`{ "completado": true, "prioridad": "TRABAJO"|null, "primeraAccion": "…"|null, "objetivos": [ { "eje": "CUERPO", "texto": "…" } ] }`. Lo que lee Plan para ordenar los ejes y sugerir la primera acción (reemplaza a lo que leía del Mapa). Sin diagnóstico terminado: `completado:false` y el resto vacío. Nunca falla por eso.

### `GET /api/v1/diagnostico/mio/lectura`
La lectura de solo lectura de su propio diagnóstico terminado o en curso (misma forma que la que ven los demás, §4).

## 2. Plan (rocas)

### `GET /api/v1/rocks/master`: cada fila suma `origin` (`PLAN` | `MAPA` | `DIAGNOSTICO`) y `confirmed` (boolean).
Una fila `origin: DIAGNOSTICO, confirmed: false` es un objetivo «Por confirmar».
### `PUT /api/v1/rocks/master/{eje}`
Reglas nuevas (reemplazan las del Mapa, D-234/D-284):
- No existe → se crea (`origin: PLAN`, confirmado).
- Existe y es **por confirmar** (`DIAGNOSTICO` sin confirmar) → el objetivo se puede reescribir **una vez**: el pedido lo reemplaza (o lo deja igual) y lo marca confirmado. Desde ahí es fijo.
- Existe y está confirmado → como siempre: la misma definición es idempotente, otro avance se registra y cualquier cambio de objetivo/meta/unidad/línea base responde `409 ROCA_MAESTRA_FIJA`.
- Ya **no existe** el `409 MAPA_PENDIENTE` ni la exigencia del Mapa (`ROCKS_EXIGIR_MAPA_ACTIVO` queda sin efecto). El campo `desdeElMapa` se acepta y se ignora (apps anteriores).

## 3. Retiro del Mapa (compatibilidad con la app anterior)
- `GET /api/v1/mapa-renacimiento`: sigue respondiendo con los datos que haya, y **`stageCompleted` pasa a `true` para todos** (la app anterior decide con él si muestra la puerta del Mapa; así nadie queda bloqueado). Campo nuevo `retired: true`.
- `PUT /mapa-renacimiento/acciones`, `PUT /mapa-renacimiento/reemplazos`, `POST /mapa-renacimiento/completar` y `POST /onboarding/answers` sobre preguntas del flujo `mapa_dia7` → `409 { codigo: "MAPA_RETIRADO", message: "El Mapa de Renacimiento ya no está disponible. Tus objetivos los escribes en Plan." }`. Nada se borra.

## 4. Lectura del diagnóstico de otra persona (solo lectura)
Misma forma en los cuatro caminos; autoriza cada uno con sus reglas de hoy y el aislamiento de formaciones (otra formación → 404):
- Administración/Alquimista: `GET /api/v1/admin/diagnostico/personas/{id}`
- Mentor o guía: `GET /api/v1/mentor/groups/{g}/learners/{u}/diagnostico`
- Líder: `GET /api/v1/leadership/groups/{g}/people/{p}/diagnostico` y `GET /api/v1/leadership/people/{p}/diagnostico` (sin grupo)
- La propia persona: `GET /api/v1/diagnostico/mio/lectura`
```json
{ "persona": { "id": "…", "nombre": "María Pérez" }, "ficha": "CON_DIAGNOSTICO", "estado": "COMPLETADO", "version": 3, "completadoEn": "2026-11-02T15:04:00Z",
  "formacion": { "codigo": "DIPLOMADO", "nombre": "Diplomado Renaser" },
  "secciones": [ { "titulo": "Tu vida hoy", "preguntas": [
     { "id": 812, "texto": "Tal como se le hizo a esta persona", "tipo": "SELECCION_UNICA", "retirada": false,
       "respuesta": { "texto": "Sí.", "valores": [], "filas": [] } } ] } ] }
```
`respuesta` es null si no respondió (solo se listan las preguntas de su versión que **respondió o que debía ver**). Cuadrícula: `filas: [{ "etiqueta": "Mi salud…", "valor": 4 }]`; casillas: `valores: ["…","…"]`; consentimiento: `texto: "Aceptó"`. `retirada: true` si Administración la quitó después («Pregunta retirada»). Con `ficha: "ESTANDAR"` o sin diagnóstico: `estado: "NO_APLICA"` y `secciones: []`.

## 5. Administración (ADMIN y ALQUIMISTA; cualquier otro rol o cuenta suspendida → 403)
Base `/api/v1/admin/diagnostico`. Todo lo que cambia el contenido toca el **borrador**; las personas ven solo lo **publicado**.

| Método y ruta | Cuerpo → respuesta |
|---|---|
| `GET /` | el borrador completo (abajo) |
| `GET /vista-previa?formacion=DIPLOMADO&duracionDias=365` | el mismo objeto que `GET /diagnostico/mio` (sin `respuestas` ni `paso`) armado desde el borrador, para esa formación y duración (sin parámetros: todas las preguntas, 90 días) |
| `POST /secciones` | `{titulo, descripcion?}` → la sección |
| `PUT /secciones/{id}` | `{titulo, descripcion?}` → la sección |
| `DELETE /secciones/{id}` | quita sus preguntas con las reglas de «quitar» → `ResultadoDeQuitar` |
| `PUT /secciones/orden` | `{ids:[…]}` (todas, en el orden nuevo) → 204 |
| `POST /preguntas` | `PreguntaRequest` → la pregunta |
| `PUT /preguntas/{id}` | `PreguntaRequest` → la pregunta |
| `PUT /preguntas/{id}/mover` | `{seccionId, posicion}` (0 = primera) → 204 |
| `POST /preguntas/{id}/duplicar` | → la copia (clave nueva, sin respuestas, sin «alimenta») |
| `POST /preguntas/quitar` | `{ids:[…], quitarDependientes:false}` → `ResultadoDeQuitar` |
| `POST /preguntas/{id}/restaurar` | → la pregunta (409 si otra activa ya tiene su «alimenta») |
| `POST /publicar` | `{versionBase: 3}` → `{version: 4, publicadaEn, publicadaPor}` |
| `GET /versiones` | `[{version, publicadaEn, publicadaPor{id,nombre}, preguntas}]`, de la más nueva a la más vieja |
| `POST /importacion/vista-previa` | `{texto}` → `VistaPreviaDeImportacion` |
| `POST /importacion` | `{texto, modo:"ANADIR"|"REEMPLAZAR"}` → `ResultadoDeImportacion` (400 con los errores si hay alguno: no se guarda nada) |
| `GET /plantilla.csv` · `GET /exportar.csv` | `text/csv; charset=utf-8` (con BOM) |
| `GET /personas/{id}` | la lectura de §4 |
| `POST /personas/ficha` | `{ids:[…], ficha:"CON_DIAGNOSTICO"|"ESTANDAR"}` → `{asignadas:n, omitidas:[{id, nombre, motivo}]}` (026-9) |

**Borrador** (`GET /`):
```json
{ "publicada": { "version": 3, "publicadaEn": "…", "publicadaPor": { "id": "…", "nombre": "Ana" } },
  "hayCambiosSinPublicar": true,
  "puedePublicar": { "ok": false, "motivos": ["Falta el texto de la declaración de responsabilidad"] },
  "secciones": [ { "id": 31, "clave": "portada", "titulo": "…", "descripcion": "…", "orden": 0, "esPortada": true, "preguntas": [] },
                 { "id": 32, "clave": "conociendote", "titulo": "Conociéndote", "descripcion": null, "orden": 1, "esPortada": false,
                   "preguntas": [ { /* PreguntaAdmin */ } ] } ],
  "retiradas": [ { /* PreguntaAdmin con estado RETIRADA */ } ],
  "totales": { "secciones": 6, "preguntas": 29 } }
```
`PreguntaAdmin` = `PreguntaRequest` + `{ id, clave, orden, seccionId, estado: "ACTIVA"|"RETIRADA", protegida: bool, respuestas: n }`.
`PreguntaRequest`: `{ seccionId, texto, ayuda?, tipo, requerida, maximoOpciones?, opciones:[{valor?, etiqueta}], escala?:{min,max,etiquetaMin?,etiquetaMax?}, seMuestraSi?:{preguntaId, valor}, alimenta?: null|"OBJETIVO_CUERPO"|"OBJETIVO_NEGOCIO"|"OBJETIVO_RELACIONES"|"PRIORIDAD_PRINCIPAL"|"PRIMERA_ACCION"|"TRATO_DE_LA_PERSONA", formaciones?: ["DIPLOMADO"]|null, precarga?: "NOMBRE_COMPLETO"|null }`.
- `valor` de una opción se omite (= etiqueta), salvo en `PRIORIDAD_PRINCIPAL` (cada opción lleva `CUERPO|TRABAJO|RELACIONES`) y `TRATO_DE_LA_PERSONA` (`MASCULINO|FEMENINO|NEUTRO`).
- Reglas: `400` con el motivo si el tipo pide opciones y no hay, si el máximo supera las opciones, si la condición nombra una pregunta posterior o una opción inexistente, si el destino no cuadra con el tipo (objetivos: `AREA_TEXTO` o `TEXTO`; prioridad: `SELECCION_UNICA`; trato: `SELECCION_UNICA`), `409` si otro ya tiene ese destino («Ya alimenta la pregunta …»), si se cambia el tipo con respuestas o de una protegida, o si se quita una protegida.
- `ResultadoDeQuitar`: `{ eliminadas: n, retiradas: n, conRespuestas: n }`. Quitar una pregunta de la que dependen otras responde `409 { codigo: "TIENE_DEPENDIENTES", dependientes: [ids] }` hasta que se repita con `quitarDependientes: true`.
- `POST /publicar`: `409 { codigo: "OTRA_PERSONA_PUBLICO", versionVigente }` si alguien publicó después de la `versionBase` que la app cargó; `400 { codigo: "NO_SE_PUEDE_PUBLICAR", motivos }` (sin preguntas, consentimiento sin texto de declaración o con el texto de relleno «[PENDIENTE DEL DUEÑO]», condiciones rotas).
- `VistaPreviaDeImportacion`: `{ secciones: n, preguntas: n, filas: [{numero, seccion, clave, texto, tipo}], errores: [{fila, columna?, mensaje}], advertencias: [{fila, mensaje}], preguntasQueSeRetirarian: n, conRespuestas: n }`. Los errores salen por número de fila del archivo (la cabecera es la fila 1). `ResultadoDeImportacion`: `{ secciones, preguntas, eliminadas, retiradas }`.
- Plantilla: columnas `Sección, Clave, Pregunta, Ayuda, Tipo, Obligatoria, Opciones o filas (separadas por |), Máximo de opciones, Se muestra solo si, Alimenta, Se muestra en`. Tipos: `Texto corto, Párrafo, Número, Opción única, Casillas, Escala, Cuadrícula de escala, Fecha, Consentimiento`. `Se muestra solo si` = `clave=Opción` (ej. `p13=Sí.`). `Alimenta` = `Objetivo de Cuerpo`, `Objetivo de Negocio`, `Objetivo de Relaciones`, `Prioridad principal`, `Primera acción` o `Trato de la persona`. En las opciones de la prioridad y del trato se escribe `Etiqueta = CÓDIGO` (`CUERPO`, `TRABAJO`, `RELACIONES`; `MASCULINO`, `FEMENINO`, `NEUTRO`). En `Escala` la columna de opciones lleva `1-5` (por defecto 1-5). `Se muestra en` = `Diplomado | Certificación` (vacío = todas). Una celda de «Obligatoria» vale `Sí`/`No`.

## 6. Mentor, guía y líder
Ver §4. Son endpoints de solo lectura que delegan en `onboarding.api.DiagnosticoDeLaPersonaFinder`.

## 7. SER
SER recibe el diagnóstico completo de la persona en su contexto (026-8 B): una línea fija en la situación del turno y la herramienta `consultar_mi_diagnostico` con todas las respuestas. Reemplaza al Mapa.
