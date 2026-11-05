# Contrato de la API — spec 042

> **Estado: implementado en el backend (2026-11-05, D-323, migración V170).** Lo que cambió respecto del borrador está marcado con
> «Corregido 2026-11-05» y es lo que la app debe usar.

Todo es **aditivo**: la app publicada no conoce estos endpoints y los campos nuevos de lectura los ignora.

## 1. `POST /api/v1/carreras` (nuevo) — la única escritura de una carrera

Guarda una carrera terminada y, en la misma transacción, aplica sus km al hábito KILÓMETROS DIARIOS del día de la carrera (FR-020 a
FR-023). Permiso `USE_APP`, solo para la propia persona (el `participante` es quien está autenticado; no viaja en el cuerpo).
Funcionalidades: `habitos` y `habitos.km.carreras`.

**Por qué un endpoint nuevo y no `POST /habit-tracks/{id}/auto-measurement`:** la carrera existe aunque no entre al hábito (día cerrado,
captura, formación sin el hábito, velocidad imposible); puede haber varias por día; trae su propia clave de idempotencia y su ruta. El
endpoint de la 037 es por registro del día y su idempotencia es por estado. **Por dentro reusa lo mismo:** cierra por
`RegistroService.completar` con el gesto propio del hábito y valida con `PoliticaKilometros` (nueva función para la carrera, al lado de la
de Health Connect).

**Clave de idempotencia:** `carreraId` en el cuerpo (UUID v4 generado por el teléfono al empezar). No se usa cabecera
`Idempotency-Key`: una sola fuente para la clave, que además queda guardada en la carrera.

**Cuerpo**
```json
{
  "carreraId": "6f1c2c1e-3b1a-4c0a-9a55-2f0f8d1d7a10",
  "iniciadaEn": "2026-11-05T11:02:10Z",
  "terminadaEn": "2026-11-05T11:41:55Z",
  "duracionActivaSeg": 2215,
  "distanciaMetros": 6210.4,
  "subidaMetros": 38,
  "pasos": 7480,
  "caloriasAprox": 412,
  "tramos": [{ "km": 1, "segundos": 352 }, { "km": 2, "segundos": 361 }],
  "metrosSinGps": 120,
  "ruta": { "polyline": "a~l~Fjk~uOwHJy@P", "puntos": 412 },
  "appVersion": "1.8.0"
}
```
| Campo | Regla |
|---|---|
| `carreraId` | obligatorio, UUID |
| `iniciadaEn`, `terminadaEn` | obligatorios, ISO; `terminadaEn > iniciadaEn`; duración total ≤ 12 h. `terminadaEn` más de 5 min en el futuro **no es un 400**: la carrera se guarda con `cuentaParaElHabito = false` (FR-024) |
| `duracionActivaSeg` | obligatorio, `60 .. 43200`, ≤ `terminadaEn − iniciadaEn` (+1 s de redondeo) |
| `distanciaMetros` | obligatorio, `100 .. 100000` (fuera: 400). Entre 60 y 100 km se guarda y **no cuenta** (`NO_CUENTA`, FR-024) |
| `subidaMetros` | opcional, `0 .. 5000` |
| `pasos` | opcional (sin permiso de actividad física no hay), `0 .. 150000` |
| `caloriasAprox` | opcional, `0 .. 6000` |
| `tramos` | obligatorio (puede ir vacío), ≤ 61 elementos, `km` consecutivos desde 1, `segundos > 0` |
| `metrosSinGps` | opcional, ≥ 0 y ≤ `distanciaMetros` (lo rellenado con pasos, FR-035) |
| `ruta` | opcional (la persona puede no guardarla); `polyline` ≤ 100 KB, `puntos ≤ 3000`; ya viene sin los primeros y últimos 200 m |
| `appVersion` | opcional, texto ≤ 20 |

**Huella:** el servidor calcula SHA-256 del cuerpo canónico (campos en orden fijo, números normalizados) y la guarda con la carrera.

**201** (primera vez) o **200** (repetida, misma huella):
```json
{
  "carreraId": "6f1c2c1e-…",
  "repetida": false,
  "fecha": "2026-11-05",
  "kilometros": 6.21,
  "cuentaParaElHabito": true,
  "porRevisar": false,
  "motivoRevision": null,
  "habito": {
    "resultado": "CERRADO",
    "registroId": "…",
    "kilometrosDelDia": 6.21,
    "origenDelDia": "GPS",
    "puntosOtorgados": 8
  }
}
```
`habito.resultado`:
| Valor | Qué pasó |
|---|---|
| `CERRADO` | el registro estaba PENDIENTE y esta carrera lo cerró (puntos pagados ahora) |
| `ACTUALIZADO` | ya estaba cumplido por GPS o Health Connect; `kilometrosDelDia` subió por FR-022; `puntosOtorgados = 0` |
| `SIN_CAMBIO` | ya estaba cumplido por una fuente automática con más km; nada cambió |
| `YA_CON_CAPTURA` | cumplido con la captura (MANUAL); no se toca |
| `DIA_CERRADO` | el día de la carrera ya terminó (D-259); no se aplica a ningún día |
| `SIN_HABITO` | la persona no tiene el hábito de km ese día (formación, pausa, Día 0) |
| `NO_CUENTA` | `cuentaParaElHabito = false` por FR-024 (velocidad, tope) |

En una respuesta repetida, `habito` es **la misma que se devolvió la primera vez** (guardada con la carrera), no se recalcula.

> **Corregido 2026-11-05.** El borrador decía que más de 60 km y la hora en el futuro eran 400; por las decisiones del dueño («no
> cuenta») se guardan con `cuentaParaElHabito = false`, `motivoNoCuenta` y `habito.resultado = NO_CUENTA`. La respuesta trae además
> `motivoNoCuenta` (texto que lee la persona, o `null`). Una carrera que no cuenta puede igual venir `porRevisar`.

**Errores** (`{ "message": "…", "codigo": "…" }`)
| Código | Cuándo |
|---|---|
| 400 | cuerpo inválido o fuera de topes (texto que la persona lee) |
| 403 | cuenta suspendida o sin `USE_APP` |
| 409 `CARRERA_YA_REGISTRADA` | misma `carreraId` con otra huella; no cambia nada |
| 503 | `habitos` o `habitos.km.carreras` apagadas por Soporte TI (la app la deja en la cola) |

Concurrencia: dos pedidos iguales a la vez → el índice único resuelve; el que pierde relee y responde 200 `repetida: true`.

## 2. `GET /api/v1/habit-tracks/today` y `/day` (campos nuevos en `medicion`)

```json
"medicion": { "unidad": "KILOMETROS", "valorDelDia": 6.21, "total": 120.0, "meta": 5.00,
              "origenDelDia": "GPS", "fuenteDelDia": null,
              "carrerasDelDia": { "cantidad": 1, "kilometros": 6.21 } }
```
`origenDelDia` suma el valor `GPS`. `carrerasDelDia` trae cuántas carreras hay ese día y la suma de los km **de las que cuentan**.

> **Corregido 2026-11-05.** Decía que `carrerasDelDia` era `null` sin carreras: así la app no podía distinguir «sin carreras» de «backend
> anterior». Ahora va **siempre** en el hábito de km (`{ "cantidad": 0, "kilometros": 0.00 }` sin carreras) y es `null` en cualquier otro
> hábito medible. **Sin el campo (backend anterior), la app no ofrece la carrera.**

## 3. La ruta (nuevo)

Todas devuelven `{ "polyline": "…", "puntos": 412 }` (`Cache-Control: no-store`) o 404 si esa persona no tiene esa carrera, no guardó la
ruta o ya se borró (180 días). Funcionalidad `habitos.km.carreras`.

| Quién | Ruta | Guardián |
|---|---|---|
| La propia persona | `GET /api/v1/carreras/{carreraId}/ruta` | solo sus carreras: la de otra persona es 404; no deja constancia |
| Su mentor o guía vigente | `GET /api/v1/mentor/groups/{groupId}/learners/{userId}/carreras/{carreraId}/ruta` | el de las fichas del mentor: acompañante vigente del grupo, alumno de ese grupo, cuenta activa (403 si no) |
| Líder, ADMIN, ALCHEMIST | `GET /api/v1/leadership/groups/{groupId}/people/{personId}/carreras/{carreraId}/ruta` y, sin grupo, `GET /api/v1/leadership/people/{personId}/carreras/{carreraId}/ruta` | `AccesoDeLiderazgo` y el aislamiento de formaciones de siempre (403 si no) |

Cada lectura ajena deja una fila en `consultas_de_ficha` (`tipo = RUTA_DE_CARRERA`, con el rol de quien la abrió); no entra al resumen de
las 20:00 del mentor.

> **Corregido 2026-11-05.** El borrador tenía una sola ruta, `/api/v1/carreras/{carreraId}/ruta`, para todos. La `carreraId` es única por
> persona (la genera el teléfono), no en toda la base, y cada rol ya tiene su puerta a la ficha con su guardián y el aislamiento de
> formaciones: la ruta ajena entra por esas mismas puertas, como el diagnóstico (spec 026).

## 4. Fichas del acompañamiento (`ObligacionDelDia.medicion`, campos nuevos)

En `GET /mentor/groups/{g}/learners/{u}/progress` y `GET /leadership/**/people/{p}/week`, una obligación de km con carreras trae
`origen = "GPS"` y:
```json
"carreras": [{ "carreraId": "…", "kilometros": 6.21, "duracionActivaSeg": 2215, "ritmoSegPorKm": 357,
               "porRevisar": false, "cuentaParaElHabito": true, "tieneRuta": true }]
```
La ruta no viaja acá: se pide con §3 al abrir el mapa. `carreras` va siempre (vacía si no hay). Un día con carreras cuyo hábito no quedó
cumplido con un número (no contaron, el día ya había cerrado) trae `medicion` con `valor` y `origen` en `null` y solo las carreras.

## 4b. Calorías (sin endpoint)

El servidor no expone un peso. La app calcula las calorías con el peso de Plan que ya lee en `GET /api/v1/rocks/master`: el objetivo del
eje `CUERPO` si su `unidad` es de kilos, su `avance` o, si no hay, su `lineaBase` (30–300 kg); si no, 70 kg (decisión 5 del dueño, FR-015).

## 5. Ranking (`GET /api/v1/ranking`)

Sin cambios de forma. `kilometrosAutomaticos` (037) incluye también a quien tiene algún día con origen `GPS`.

## 6. Lo que la app NO pide

Nada durante la carrera. Tampoco hay un endpoint para «registrar en el hábito» aparte, ni lecturas periódicas del estado de una carrera:
para saber si llegó, la app repite el mismo `POST` (idempotente).
