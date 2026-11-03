# Contrato de la API — spec 037

Todo es **aditivo**: la app publicada ignora los campos nuevos y no usa el endpoint nuevo.

## 1. `POST /api/v1/habit-tracks/{id}/auto-measurement` (nuevo)

Cierra el hábito de kilómetros con la lectura que hizo el teléfono. Mismo permiso que `/complete`: `USE_APP`, **solo el dueño del registro**. Funcionalidades: `habitos` (la del controller) y `habitos.km` (la del hábito, como `/complete`).

**Cuerpo**
```json
{ "kilometros": 5.4, "pasos": 7120, "fuente": "Galaxy Watch", "dispositivo": "RELOJ", "leidoEn": "2026-11-04T22:10:03Z" }
```
| Campo | Regla |
|---|---|
| `kilometros` | obligatorio, `> 0`; se redondea a 2 decimales |
| `pasos` | opcional (un reloj puede dar solo distancia), `0 .. 150000` |
| `fuente` | obligatoria, 1 a 60 caracteres (etiqueta que la persona lee) |
| `dispositivo` | obligatorio: `TELEFONO` \| `RELOJ` \| `OTRO` |
| `leidoEn` | obligatorio, instante ISO; no más de 5 min en el futuro y **no anterior al inicio del día del registro** en la zona del participante |

**200** → `RegistroHabitoResponse` (como `/complete`) con el campo nuevo `medicion`:
```json
{ "id": "…", "estado": "COMPLETADO", "puntosOtorgados": 8, "valorMedido": 5.40,
  "medicion": { "valor": 5.40, "origen": "HEALTH_CONNECT", "pasos": 7120, "fuente": "Galaxy Watch",
                "dispositivo": "RELOJ", "leidoEn": "2026-11-04T22:10:03Z", "porRevisar": false } }
```
**Errores** (`{ "message": "…" }`, texto que la persona lee)
| Código | Cuándo |
|---|---|
| 400 | menos de la meta («Todavía no llegas a tu meta de 5 km.»), más del tope («Revisa los datos: más de 60 km en un día no se puede registrar solo. Sube tu captura.»), lectura del futuro o de otro día, hábito que no mide km, cuerpo inválido |
| 403 | no es el dueño del registro, o cuenta suspendida |
| 404 | el registro no existe |
| 409 | el día del registro ya terminó (D-259); o ya está cumplido con la captura («Ya registraste este hábito con tu captura.») |
| 503 | `habitos` o `habitos.km` apagadas por Soporte TI |

**Idempotencia:** si el registro ya está `COMPLETADO` con origen `HEALTH_CONNECT`, responde **200** con ese registro, sin pagar puntos otra vez ni cambiar nada (aunque el cuerpo traiga otro número).

## 2. `GET /api/v1/habit-tracks/today` y `/day` (campos nuevos en `medicion`)

```json
"medicion": { "unidad": "KILOMETROS", "valorDelDia": 5.40, "total": 117.80,
              "meta": 5.00, "origenDelDia": "HEALTH_CONNECT", "fuenteDelDia": "Galaxy Watch" }
```
`meta` solo en los hábitos medibles (hoy KILÓMETROS DIARIOS); `origenDelDia` y `fuenteDelDia` son `null` si todavía no se cumplió (o si fue por captura, `origenDelDia = "MANUAL"` y `fuenteDelDia = null`). **Sin `meta`, la app no ofrece «Conectar»** (backend anterior).

## 3. Meta (Administración)

`GET /api/v1/admin/habits/{id}/measurement-goal` y `PUT` (mismo permiso que el resto de «Hábitos del programa»: `MANAGE_HABIT_CATALOG`, ADMIN/ALQUIMISTA; el servicio vuelve a exigirlo).
```json
{ "unidad": "KILOMETROS", "meta": 5.00, "porDefecto": 5.00, "minimo": 0.50, "maximo": 50.00, "esLaPorDefecto": true }
```
`PUT` cuerpo `{ "meta": 6.5 }` (o `{ "meta": null }` = volver a la del programa). 404 si el hábito no existe **o no mide nada**; 400 si está fuera de 0,5 a 50.

## 4. Fichas del acompañamiento (`ObligacionDelDia.medicion`, campo nuevo)

En `GET /mentor/groups/{g}/learners/{u}/progress` y `GET /leadership/**/people/{p}/week`, cada obligación suma `medicion` (o `null`): `{ "valor", "origen", "fuente", "dispositivo", "pasos", "leidaEn", "porRevisar" }`. `origen = "MANUAL"` trae solo `valor` y `origen`.

## 5. Ranking (`GET /api/v1/ranking`)

`RankingAgregadoResponse` suma `kilometrosAutomaticos: ["<participanteId>", …]`, solo de quienes salen en la lista de km.
