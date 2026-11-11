# Plan técnico — spec 048, fase 1

## Servidor (este repo)

### `POST /api/v1/habit-tracks/{id}/complete` con lo hecho sin red (FR-008, FR-013–FR-016)

El cuerpo suma tres campos opcionales; sin ellos todo sigue como hoy:

| Campo | Tipo | Qué es |
|---|---|---|
| `hechoEn` | instante ISO-8601 | La hora real en que la persona lo marcó sin red. |
| `idCliente` | UUID | El identificador del envío; un reintento con el mismo vuelve el mismo resultado. |
| `origenDeLaHora` | `RELOJ_DEL_SISTEMA` \| `TELEFONO_CORREGIDO` | Cómo se calculó `hechoEn` (FR-013). |

`RegistroService.completar`, cuando trae `hechoEn`:

1. **Idempotencia** — tabla nueva `envios_sin_red (id_cliente uuid PK, participante_id, registro_id, creado_en)`
   (V188, con `SET search_path TO renaser, public;`). Si el `idCliente` ya está, se devuelve el registro tal como quedó,
   sin tocar nada.
2. **¿Está en regla?** `hechoEn` no está en el futuro (tolerancia de 2 min), cae dentro del día del registro (zona de la
   persona) y la llegada es a lo sumo 48 h después de `hechoEn`.
3. **En regla** — los puntos se calculan con `hechoEn` en vez de `clock.now()` (`ResultadoOtorgamiento.calcular`), y
   `completadoEn = hechoEn`. Se acepta aunque el día ya haya cerrado y el registro figure `EXPIRADO` por el barrido:
   el gesto nuevo `RegistroHabito.completarHechoSinRed` admite `PENDIENTE`, `EN_CURSO` y `EXPIRADO` (la excepción
   a D-259 la justifica `hechoEn`).
4. **No está en regla** — se registra igual con 0 puntos, y `motivoSinPuntos` lo dice
   (`LLEGO_DESPUES_DE_48_H`, `HORA_FUTURA`, `FUERA_DE_SU_DIA`).
5. **Ya estaba hecho** (por otro teléfono o con red) — no rebota: se devuelve el registro con
   `motivoSinPuntos = YA_ESTABA_HECHO`.
6. **No se puede aplicar** (hábito quitado, cuenta suspendida): se anota en el log con `[offline]` para Soporte TI y
   se responde con su código (lo muestra la app; FR-016).

La respuesta suma `motivoSinPuntos` (nulo cuando sumó sus puntos).

La evidencia con foto no cambia de contrato: la app sube la foto (URL firmada → S3 → confirmar) y recién después
completa con `hechoEn`. Confirmar la evidencia de un registro `EXPIRADO` también se acepta cuando el pedido trae
`hechoEn` en regla (mismo criterio).

## App (`Renaser-90-dias-frontend-`, rama `048-modo-offline`)

| Pieza | Dónde | FR |
|---|---|---|
| La sesión no se borra por falta de red; perfil guardado por cuenta | `features/auth/context/AuthContext.tsx`, `features/sinRed/perfilGuardado.ts` | FR-001–FR-003 |
| Estado de la conexión, deducido de las respuestas de `apiFetch` (sin dependencias nativas nuevas) | `features/sinRed/estadoDeConexion.ts` | FR-006 |
| Ícono discreto y globo | `features/sinRed/components/IconoSinConexion.tsx` | FR-006 |
| Lecturas guardadas por cuenta (Hoy, hábitos de hoy, Plan) | `features/sinRed/lecturasGuardadas.ts` + `apiFetch` | FR-004 |
| Una cola por cuenta, generalizando `colaDeEnvio` de la carrera | `features/sinRed/colaSinRed.ts` | FR-007, FR-009 |
| Hora real: ancla del servidor + reloj desde el encendido | `modules/reloj-del-sistema` (módulo nativo chico) + `features/sinRed/horaReal.ts` | FR-013 |
| Marcar hábito sin red: mismas reglas, check al instante, alarmas apagadas | `features/habits/api/evidenciaHabitoApi.ts` y su hook | FR-010–FR-012 |
| «Necesitas conexión para usar esto» | `components/NecesitasConexion.tsx` en SER, chat, Pagos, Eventos, Código Renaser y videos | FR-005 |
| Audios de hoy y mañana | `features/sinRed/audiosDelDia.ts` | FR-017 |
| Aviso al cerrar sesión con pendientes | `AuthContext.logout` | FR-009 |

## Cómo se prueba

- Servidor: pruebas de `RegistroService` (en regla, tarde, futura, fuera de su día, ya hecho, idempotencia) y una IT
  contra Postgres de la tabla nueva.
- App: Jest de la cola, de la hora real, de la sesión sin red y de marcar sin red; E2E en el emulador con el modo avión
  (Maestro + mobile-mcp) y el log del backend.

---

# Plan técnico — fase 2 (el Muro sin red, FR-018, FR-020, FR-021)

## Servidor

- **V189 `publicaciones_sin_red`** `(id_cliente uuid, autor_id uuid, publicacion_id uuid → publicaciones_muro ON DELETE
  CASCADE, hecho_en timestamptz, origen_de_la_hora text, creado_en)`, PK `(autor_id, id_cliente)`. Aparte de
  `publicaciones_muro` para no tocar el agregado `Publicacion` ni su mapeo.
- `POST /api/v1/wall` suma `hechoEn`, `idCliente` y `origenDeLaHora` opcionales (los mismos de la fase 1). Con `idCliente`
  ya registrado devuelve la publicación que quedó (201, la misma), sin crear otra.
- La publicación se guarda con `clock.now()` (D18: arriba, como nueva). `PublicacionCreadaEvent` suma `hechoSinRedEn`
  (nulo cuando hubo señal).
- `PostDiarioComunidadHabitoService`: con `hechoSinRedEn`, busca el registro del DÍA DE LA HORA REAL y lo cierra con
  `CompletarHechoSinRedUseCase` (misma revisión que la fase 1: 48 h, hora futura, fuera de su día; admite `EXPIRADO`;
  idempotente por `envios_sin_red` con un id derivado de la publicación).
- `existeDeAutorEntre` (lo que mira `PoliticaPostDiarioComunidad`) cuenta también la hora real de
  `publicaciones_sin_red`: una publicación escrita ayer sin señal y llegada hoy cuenta para ayer.

## App

| Pieza | Dónde | FR |
|---|---|---|
| Envío `publicarMuro` en la cola: copia las fotos al almacenamiento de la app, las sube (URL firmada → S3) y publica con la hora real | `features/community/api/publicarSinRed.ts` | FR-018 |
| La publicación en espera arriba en el Muro, «Se publicará al volver la señal», con «Borrar» (saca el envío de la cola) | `useWallFeed`, `ComunidadScreen` | FR-018, D19 |
| El compositor abre sin red; las categorías del Muro entran en las lecturas guardadas | `NuevaPublicacion`, `lecturasGuardadas` | FR-021 |
| El «Post diario» de Entrenar sale hecho al instante (ajuste de lectura con el registro guardado en el envío) | `evidenciaHabitoApi` / `publicarSinRed` | FR-011, D17 |

## Cómo se prueba

- Servidor: `PublicarEnElMuroSinRedIT` (llega al día siguiente → el «Post diario» de ayer cumplido con puntos y la
  publicación arriba; reintento → una sola; borrada antes → no hay nada) y la prueba del servicio.
- App: Jest del envío de la cola y del borrado; E2E en el emulador cortando el túnel (como la fase 1).

---

# Plan técnico — fase 3 (FR-019): Plan, Cierra tu día, Código Renaser y planificar hábitos y alarmas sin red

Fase 3 de D16. Mismas reglas que con red, por la MISMA cola (`colaSinRed.ts`), con la hora real (D14) y la regla de choques (D13):
si en el servidor cambió algo después de la hora en que lo hiciste, gana lo del servidor y se avisa qué no se aplicó; si no, se
aplica lo tuyo. Redactado el 2026-11-10 sobre `master` de los dos repos (app hasta #179, backend hasta #125). **No se agrega ningún
endpoint**: se suman tres cabeceras opcionales a los que ya existen.

## Lo que hay hoy (recon 2026-11-10)

Las cuatro áreas escriben con `apiFetch` y, sin red, **no guardan nada**: la persona ve el error de siempre («No pudimos…») y pierde lo
que escribió (el Cierre conserva su borrador local, `borradorDelCierre.ts`). Las lecturas de `/api/v1/rocks/*` ya se ven sin red
(`lecturasGuardadas.ts`, `PREFIJOS`); las de Radar y las de preferencias de hábitos no.

| Área | Lo que escribe la app hoy (archivo:línea) | Lo que lo atiende en el servidor | Reloj del servidor que pesa | Pantalla sin red hoy |
|---|---|---|---|---|
| **Cierra tu día** | `PUT /rocks/day-closure` (`cierre/api/cierreApi.ts:35`, desde `useHojaDelCierre.guardar`) | `CierreDelDiaController:54` → `CierreDelDiaService.cerrar:67` | «hoy» de la persona (`exigirQueSeaHoy`: otro día es 409 «ese día ya cerró»); `MomentoDelCierre.de(clock.now())` decide por cuáles acciones se pregunta (hora de inicio ya llegada); `entradas_diario.actualizado_en` y `cierre_en` salen de `clock.now()` | Sin `SoloConConexion`; falla con el aviso de conexión (`MENSAJE_SIN_CONEXION`) |
| **Código Renaser / Mi Radar** | `POST /radar` (`radar/api/radarApi.ts:25`, desde `useRadarDelDia.enviar`) y `PUT /radar/mi-radar` (`radarApi.ts:66`) | `RadarController:70` → `RadarService.registrar:74`; `MiRadarService.configurar` | `registrar`: hora y día de la semana locales, día del programa (desde el 8: Mi Radar encendido, día con Radar, 03:00–23:59), `horaPrevista`, «un registro por hora» (`mismaHora`, índice V52 por hora UTC) y `creado_en = clock.now()`. `configurar`: «hoy y lo pasado no se editan» | `CodigoRenaserModal` envuelve el cuerpo en `SoloConConexion` (`:304`–`:365`); desde #179 el innegociable (días 1–7) no se abre solo sin señal. Mi Radar: sin guardado |
| **Plan: acciones y objetivos** | `POST /rocks/plan` (`objetivos/api/objetivosApi.ts:160`); `POST /rocks/{id}/evidence/upload-url` y `/evidence` (`:180`, `:208`, desde `utils/sellarRocaDiaria.ts`); `POST/PATCH /rocks/weekly…` (`:85`, `:97`, `:109`); `PUT /rocks/master/{eje}` (`:46`); `PUT /rocks/monthly/{eje}/{mes}` (`planMensualApi.ts:96`) | `RocaDiariaController:67`, `:90`; `RocaSemanalController:61`, `:74`, `:84`; `RocaMaestraController:67`; `RocaMensualController:76` | Agendar: fechas válidas desde «hoy», ventana de las 18:00, `ALREADY_PLANNED`; marcar: `requireDeHoy`, EXIF ±15 min contra el instante de subida, `EscalaPuntosRoca.calcular(horaFin, ahora)` y `RocaCompletadaEvent(ahora)`; semanal: `numeroSemanaAPlanificar` y `VentanaPlanificacionSemanal` (domingo 12:00 → lunes 09:00, o 2 h desde crear) | Sin `SoloConConexion`; lecturas guardadas sí; toda escritura falla con error genérico |
| **Planificar hábitos y alarmas** | `PATCH /habit-preferences/{id}` (`habits/api/habitsApi.ts:130`), `PUT/DELETE …/weekdays/{d}`, `…/reminder`, `…/alarm` (`:167`–`:244`), `PUT/PATCH/DELETE /habit-unlocks/{id}` (`:279`–`:292`), `PUT/DELETE …/week` (`:316`, `:321`), `POST /habit-preferences/week-repeat` y `DELETE …/weeks/{w}` (`:342`, `:351`), `POST /habits` (`:43`) | `HabitPreferenceController`, `PlanDeLaSemanaController`, `RepetirSemanaController`, `HabitUnlockController`, `MisHabitosController` → `PreferenciaHorarioService`, `PlanDeLaSemanaService`, `RepeticionDeSemanaService`, `DesbloqueoHabitoService`, `HabitosPersonalesService` | `clock.now()` para la ventana ya arrancada (el cambio rige mañana, D-91), la cuota semanal de reacomodos y la semana libre (día del programa); «hoy» en `PlanDeLaSemanaService:176` y `RepeticionDeSemanaService:172` | `PlanificarDimensionModal` envuelve todo en `SoloConConexion` (`:1858`–`:2418`); `SeccionAlarmas` y `cambioDeHora.ts` fallan con error genérico |

Hallazgos que condicionan el diseño:

1. **«Bitácora» no existe como pantalla aparte**: la nota del día vive en `entradas_diario` (tipo `EVALUACION_DIA`) y se escribe desde Cierra tu
   día. Se trata como una sola cosa (pregunta P1).
2. **Cerrar el día nunca paga puntos**; el Código Renaser tampoco. Solo **marcar una acción** (`POST /rocks/{id}/evidence`) da puntos: es la
   única pieza de la fase con las reglas de 48 h / hora futura de la fase 1.
3. `envios_sin_red` (V188) solo sabe de registros de hábito (`registro_id NOT NULL`) y de «sin puntos». Se generaliza (V190) en vez de crear
   una tabla por área.
4. **Un 409 hoy deja el envío «bloqueado» para siempre y en silencio**: `colaSinRed.desenlaceDe` marca `bloqueado` y nadie lo muestra
   (tampoco el `motivoSinPuntos` de la fase 1). D13 y D14 («nada se pierde en silencio») piden un aviso: la fase 3 lo construye y, de paso, lo
   usan las fases 1 y 2 (ver riesgos: conviene un issue de bug aparte).
5. Una acción agendada sin señal no tiene todavía su `id` (lo pone el servidor): no se puede marcar hecha hasta que llegue (P6).

## Contrato común (vale para todos los envíos de la fase)

### Cabeceras de la petición

A diferencia de las fases 1 y 2 (campos del cuerpo), la fase 3 usa **cabeceras**: hay `DELETE` y `PUT` sin cuerpo que aceptar, no se toca
ningún DTO ni su validación y un cuerpo que es una lista (`POST /rocks/plan`) no admite campos de más. Un servidor o una app anteriores las
ignoran. `apiFetch` ya las manda con `cabeceras`.

| Cabecera | Valor | Ejemplo |
|---|---|---|
| `X-Id-Cliente` | UUID del envío (el `idCliente` de la cola) | `5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10` |
| `X-Hecho-En` | instante ISO-8601 en UTC con `Z` (el `hechoEn` de la cola) | `2026-11-10T02:40:13.250Z` |
| `X-Origen-De-La-Hora` | `RELOJ_DEL_SISTEMA` o `TELEFONO_CORREGIDO` (otro valor se lee como `TELEFONO_CORREGIDO`) | `RELOJ_DEL_SISTEMA` |

**Hecho sin red** = vienen `X-Id-Cliente` y `X-Hecho-En`. Si falta uno, el pedido es uno normal (como en la fase 1). Si `X-Id-Cliente` no es
un UUID o `X-Hecho-En` no es un instante, 400 de siempre. Sin cabeceras, cada endpoint responde **exactamente lo de hoy**.

En el servidor las lee un `HandlerMethodArgumentResolver` de `shared/web` que arma `HechoSinRed(hechoEn, idCliente, origenDeLaHora)` (nulo si
no es un hecho sin red) y el controller se lo pasa al caso de uso. `HechoSinRed` y `OrigenDeLaHora` pasan de
`habits…CompletarHechoSinRedUseCase` a `shared.domain` (los dos módulos los usan; la fase 1 sigue igual en lo que hace).

### La hora real (cómo se revisa, D14)

Una sola clase, `RevisionDeLaHora` (`shared.domain`, sin reloj propio: recibe `ahora`):

- `ahora = clock.now()`. `referencia = min(hechoEn, ahora)`: **todas las reglas de calendario del caso de uso corren con `referencia`, nunca con
  `clock.now()`** (los casos de uso pasan el `Instant` a los gestos de dominio, que ya lo reciben; no se inventa un `Clock` falso).
- **Hora futura**: `hechoEn > ahora + 2 min`. Lo que da puntos (marcar una acción) queda sin puntos con `motivoSinPuntos = HORA_FUTURA`; lo
  demás se aplica con `referencia = ahora`. Igual que en la fase 1, la tolerancia es de 2 minutos.
- **48 h**: solo quita puntos. Marcar una acción que llega a más de 48 h de `hechoEn` se registra sin puntos (`LLEGO_DESPUES_DE_48_H`).
  Lo que no da puntos no tiene tope de llegada (P3); lo cuidan las reglas de su día y el choque.
- «Hoy» = `referencia.atZone(zona de la persona).toLocalDate()` (regla 02). Día del programa a la hora real = día del programa de hoy
  menos los días entre ese «hoy» y el de la llegada, con piso 0 (no reconstruye ajustes del reloj de esa semana: riesgo R4).

### Idempotencia (FR-008) y choque (D13)

- Tabla: `envios_sin_red` generalizada (V190, abajo). Clave `id_cliente`; se busca por `(id_cliente, participante_id)`.
- Cada caso de uso, dentro de **su misma transacción**: (1) `pg_advisory_xact_lock(hashtextextended(id_cliente::text, 0))` para que dos pedidos
  iguales a la vez se turnen; (2) busca el envío: si existe, responde lo mismo (abajo); (3) revisa la hora y el choque; (4) aplica o no; (5)
  escribe la fila del envío (`APLICADO` o `NO_APLICADO`). Un resultado «no aplicado» **no es una excepción** (haría rollback de la fila): el caso
  de uso devuelve `NoAplicado(codigo, mensaje)` y el controller lo traduce a 409. El puerto vive en `habits.api.EnviosSinRedPort` (público;
  `rocks` ya depende de `habits.api`); si `ArchitectureTest` lo rechaza, pasa a `shared.domain`.
- **La regla de choque es una sola: lo más nuevo por hora lógica.** Cada escritura de estas filas deja `actualizado_en = referencia` (no la
  hora de llegada: si no, dos envíos seguidos del mismo teléfono chocarían entre sí). Un envío se aplica si **ninguna fila que toca tiene
  `actualizado_en > hechoEn`** (holgura de 2 s por la resolución del header `Date`; en empate gana el servidor). Si alguna la tiene: no se aplica,
  `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR`. Las altas que chocan con algo que ya existe y es anterior o igual a `hechoEn` dicen `YA_ESTABA_HECHO`.
- **Repetición**: mismo `id_cliente` → mismo resultado, sin tocar nada. Si fue `APLICADO` responde el éxito habitual **releyendo lo que quedó**
  (mismo código HTTP y misma forma que la primera vez); si fue `NO_APLICADO`, el mismo 409 con el mismo `codigo` y `message` guardados.

### Respuesta cuando no se aplicó

`409`, `application/json`, cuerpo `ApiErrorConCodigo` (el que ya usa el servidor): `{ "message", "timestamp", "codigo", "funcionalidad": null }`.
`message` va en tuteo y con tildes, listo para mostrar. Los `codigo` son un conjunto cerrado (CHECK en V190):

| `codigo` | Cuándo | `message` |
|---|---|---|
| `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR` | alguna fila que tocabas cambió en el servidor después de `hechoEn` (otro dispositivo, tu mentora, el cierre del día…) | «Esto cambió en tu cuenta después de que lo hiciste sin conexión. Se quedó lo más nuevo.» |
| `YA_ESTABA_HECHO` | el alta ya existía (semana ya planificada, hora del Código Renaser ya ocupada, día en curso ya armado) | «Esto ya estaba hecho en tu cuenta. Se quedó lo que ya había.» |
| `RECURSO_YA_NO_EXISTE` | lo que tocabas ya no está (acción reemplazada al replanificar el día, hábito quitado del plan, semana borrada). Con `X-Id-Cliente`, un 404 por «no existe lo que tocabas» se responde así, no como 404 | «Lo que tocaste ya no existe en tu cuenta. Se quedó lo del servidor.» |
| `FUERA_DE_SU_DIA` | el día al que apunta ya terminó o no es el de `hechoEn` (agendar un día pasado, cerrar un día distinto del de la hora real) | «Esto era de un día que ya terminó y no se pudo aplicar.» |
| `MI_RADAR_NO_APLICA` | Código Renaser desde el día 8 sin Mi Radar encendido, sin horas ese día o fuera de 03:00–23:59 a la hora real | «Mi Radar no tenía esa hora para ti, así que no se guardó ese Código Renaser.» |

Lo demás sigue como hoy: 400 de datos mal hechos, 403 (con su `codigo` si lo tiene: cuenta suspendida, `ROCKS_LOCKED`…), los 409 de negocio de
siempre (`ALREADY_PLANNED`, `ROCA_MAESTRA_FIJA`, «Esta semana ya reacomodaste N hábitos»), 503 `FUNCIONALIDAD_APAGADA` (la cola reintenta).
Cada resultado, aplicado o no, deja una línea `[offline] <tipo> <recurso> hecho <hechoEn> (hora <origen>), llegó <ahora>: <aplicado|no aplicado
CODIGO>` en el log y su fila en `envios_sin_red` (FR-016, Soporte TI). **Nunca se loguea un texto de la persona** (notas, respuestas del Código
Renaser).

## Servidor

### Migraciones

- **V190 `envios_sin_red_de_todo_lo_hecho_sin_red`**: `ALTER TABLE envios_sin_red` — `registro_id` pasa a admitir NULL (queda solo para
  hábitos); agrega `tipo text NOT NULL DEFAULT 'COMPLETAR_HABITO'`, `recurso text` (la clave de lo tocado: `roca-diaria:<uuid>`,
  `plan-diario:<fecha>`, `semana:<n>`, `cierre:<fecha>`, `radar:<uuid>`, `mi-radar`, `preferencia:<habito>`, `habito:<uuid>`…),
  `resultado text NOT NULL DEFAULT 'APLICADO'`, `codigo text`, `mensaje text`, `hecho_en timestamptz`, `origen_de_la_hora text`. `CHECK` de
  `resultado IN ('APLICADO','NO_APLICADO')`, de `(resultado = 'NO_APLICADO') = (codigo IS NOT NULL)` y de `codigo` en los cinco de arriba;
  índice parcial `(participante_id, creado_en) WHERE resultado = 'NO_APLICADO'` para Soporte TI. Valores de `tipo`: `COMPLETAR_HABITO`,
  `CIERRE_DEL_DIA`, `CODIGO_RENASER`, `MI_RADAR`, `PLAN_AGENDAR`, `PLAN_MARCAR_ACCION`, `PLAN_SEMANAL_CREAR`, `PLAN_SEMANAL_EDITAR`,
  `PLAN_SEMANAL_CERRAR`, `PLAN_OBJETIVO_90`, `PLAN_OBJETIVO_MES`, `HABITO_PREFERENCIA`, `HABITO_PLAN_SEMANA`, `HABITO_DESBLOQUEO`,
  `HABITO_PERSONAL`. No se renombra ninguna columna (el despliegue deja un rato corriendo código de antes). Cabecera con el porqué, como V188.
- **V191 `mi_radar_actualizado_en`**: `participantes_programa.mi_radar_actualizado_en timestamptz NULL` (NULL = nunca tocado; se lee como
  «muy viejo»). Lo mueve `MiRadarService.configurar` (con `referencia`). Es la hora lógica de Mi Radar para el choque, porque
  `participantes_programa` se actualiza por muchos otros motivos y no sirve de señal.
- Sin más columnas: `rocas_maestras`, `rocas_semanales`, `rocas_mensuales`, `rocas_diarias`, `entradas_diario`, `preferencias_horario`,
  `horario_semanal_habito`, `horarios_habito_por_fecha` y `desbloqueos_habito` ya tienen `actualizado_en`. **Hay que comprobar, una por una, que
  cada escritura lo mueva** (por ejemplo `guardarSoloElCierre` de `rocas_diarias`); la que no lo haga lo suma en su caso de uso (R1).

### Endpoints (todos con las tres cabeceras opcionales)

**A. Cierra tu día** — `PUT /api/v1/rocks/day-closure`, cuerpo sin cambios (`fecha`, `acciones[]`, `nota`).

- Hora: `hoy = fecha local de referencia`; `exigirQueSeaHoy(fecha, hoy)`; si `fecha` no es ese día → 409 `FUERA_DE_SU_DIA` (hoy sería 409 «ese día ya
  cerró»; con hora real, cerrar el día de ayer a las 21:40 y llegar hoy **sí** se aplica). `MomentoDelCierre.de(referencia, zona)`: la acción
  se exige si sigue pendiente, su hora ya había llegado a `referencia` **y fue creada antes o igual a `hechoEn`**. `cierre_en`,
  `entradas_diario.actualizado_en` y, si es nueva, `creado_en` = `referencia`. Sigue sin completar acciones, pagar puntos ni publicar eventos.
- Choque: `entradas_diario(EVALUACION_DIA, fecha).actualizado_en > hechoEn` o algún `rocas_diarias.cierre_en > hechoEn` de ese día →
  `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR`. Un `id` de acción que ya no es de ese día → `RECURSO_YA_NO_EXISTE` (todo o nada, como el dominio; P7). Una
  pendiente exigida sin respuesta que el teléfono no conocía → `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR`.
- Respuesta 200 `CierreDelDiaResponse` (misma forma) de esa `fecha`, con `puedeCerrar` según hoy. Repetición: la misma, releída.

**B. Código Renaser** — `POST /api/v1/radar`, cuerpo sin cambios (`whatAmIDoing`, `whatAmIThinking`, `whatAmIFeeling`, `energyLevel`,
`whatAmIAvoiding`, `dictado`). `RadarService.registrar` con `referencia`:

- Hora: `local = referencia` en la zona; `diaPrograma` a la hora real; `horasDeHoy` = horas de ese día con el Despertar y el Dormir **de esa fecha**
  (`AgendaDelRadar` suma `delDia(participante, zona, miRadar, fecha)`; hoy solo sabe «hoy»); `HorarioDelRadar.puedeRegistrar(...)` con `local`. Si no
  puede → 409 `MI_RADAR_NO_APLICA`, salvo que `mi_radar_actualizado_en > hechoEn`: entonces `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR`. Los días 0–7 siguen
  sin mirar horario.
- Un registro por hora: se busca **cualquier** registro de esa hora UTC de `hechoEn` (`LoadRegistroRadarPort.deParticipanteEnLaHora(instante)`, nuevo;
  hoy solo mira el último). Si existe → 409 `YA_ESTABA_HECHO` (V52 conserva el más antiguo, P4). El índice único sigue de garantía.
- Se guarda con `creado_en = referencia` y `horaPrevista` de `local`: en el historial aparece a la hora en que se hizo (a diferencia del Muro, D18,
  aquí no hay «arriba como nuevo»). Sin puntos.
- Respuesta 200 `RegistroRadarResponse` (`createdAt = referencia`). Repetición: el mismo registro.

**B2. Mi Radar** — `PUT /api/v1/radar/mi-radar`, cuerpo sin cambios (`encendido`, `cambios[]`). Hora: la regla «hoy y lo pasado no se editan» corre
con `hoy` de `referencia`. Choque: `mi_radar_actualizado_en > hechoEn` → `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR` (toda la configuración es una sola cosa,
sin mezclar días). Aplica: `mi_radar_actualizado_en = referencia`. Respuesta 200 `MiRadarResponse`.

**C. Plan: acciones y objetivos**

| Endpoint | Hora (D14) | Choque (D13) | Aplicado | Respuesta |
|---|---|---|---|---|
| `POST /rocks/plan` (`CrearPlanDiarioRequest` igual) | `hoy`, `plazoAlCrear` (`VentanaPlanificacionDiaria.abierta`), `FechasPlanificables` y `semanasParaPlanificarUnDia` con `referencia`. Si `fecha` es anterior al «hoy» de la llegada → 409 `FUERA_DE_SU_DIA` (P2). Las rocas nacen con `creado_en = actualizado_en = referencia` | Un día futuro se reemplaza como hoy (E-208), **salvo** que alguna de sus rocas tenga `actualizado_en > hechoEn` → `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR`. El día en curso ya armado → `YA_ESTABA_HECHO` (hoy `ALREADY_PLANNED`) | `ROCKS_LOCKED` (403) y `NO_WEEKLY_ROCK` (400) como hoy | 201, lista `RocaDiariaResponse`. Repetición: 201 con las rocas de esa fecha |
| `POST /rocks/{id}/evidence` (`CompletarRocaDiariaRequest` igual; `/evidence/upload-url` no cambia) | `requireDeHoy` contra `hoy` de `referencia`; EXIF ±15 min contra `referencia` (la foto se tomó cuando se hizo, no cuando se sube); `completada_en = referencia`; `EscalaPuntosRoca.calcular(horaFin, referencia)`; `RocaCompletadaEvent(referencia)`. Puntos: 0 con `motivoSinPuntos` = `LLEGO_DESPUES_DE_48_H`, `HORA_FUTURA` o `FUERA_DE_SU_DIA` (la fecha de la roca no es el día de `hechoEn`); nunca rebota (D14) | Roca que ya no existe → 409 `RECURSO_YA_NO_EXISTE`. Ya completada → **200** con `motivoSinPuntos = YA_ESTABA_HECHO` (igual que los hábitos, fase 1). La Ley IV (VERDE primero) y `ALREADY_COMPLETED` siguen como hoy contra el estado del servidor | 200 | `RocaDiariaResponse` + campo nuevo **`motivoSinPuntos`** (string o `null`; aditivo, `null` en todo lo demás) |
| `POST /rocks/weekly` | `numeroSemanaAPlanificar` y la ventana con `referencia` (un domingo planifica la siguiente) | Esa semana ya existe: si alguna roca semanal tiene `creado_en > hechoEn` → `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR`; si no, `YA_ESTABA_HECHO` (hoy `ALREADY_PLANNED`) | `ROCKS_LOCKED` como hoy | 201, `RocaSemanalResponse[]`; repetición: las de esa semana |
| `PATCH /rocks/weekly/{id}` | `VentanaPlanificacionSemanal.puedeEditar(plazoAlCrear, creadoEn, referencia, zona)` (cerrada → 403 de hoy) | `actualizado_en > hechoEn` → `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR`; no existe → `RECURSO_YA_NO_EXISTE` | `actualizado_en = referencia` | 200 `RocaSemanalResponse` |
| `PATCH /rocks/weekly/{id}/review` | sin ventana | igual al anterior | igual | 200 `RocaSemanalResponse` |
| `PUT /rocks/master/{eje}` | `recibirDefinicion(…, referencia)` | La definición distinta de una ya confirmada sigue siendo el 409 `ROCA_MAESTRA_FIJA` de hoy. Un avance con `actualizado_en > hechoEn` → `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR` | `actualizado_en = referencia` | 200 `RocaMaestraResponse` |
| `PUT /rocks/monthly/{eje}/{numeroMes}` | `redefinir(…, referencia)` | `actualizado_en > hechoEn` → `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR`; falta la maestra → `RECURSO_YA_NO_EXISTE` | `actualizado_en = referencia` | 200 `RocaMensualResponse` |

**D. Planificar hábitos y alarmas.** Los casos de uso reciben `referencia` donde hoy llaman a `clock.now()` (`PreferenciaHorarioService` líneas
114, 360, 376, 395, 416, 459, 552; `PlanDeLaSemanaService:176`; `RepeticionDeSemanaService:172`; `DesbloqueoHabitoService:102`, `:126`):
la ventana «ya arrancó» (el cambio rige mañana, D-91), la cuota semanal y la semana libre se evalúan **a la hora real**. Un pedido sin
cabeceras no cambia. Choque: alguna fila que toca con `actualizado_en > hechoEn` → `CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR`; hábito inexistente o fuera del
plan → `RECURSO_YA_NO_EXISTE`. Un `DELETE` sobre algo que ya no está se aplica sin más (es idempotente). Al aplicar: `actualizado_en = referencia`.

| Endpoint | Fila(s) que toca (la del choque) | Respuesta si se aplica |
|---|---|---|
| `PATCH /habit-preferences/{habitId}` | `preferencias_horario(p, h)` | 200 `{ deferred, deferredEffectiveDate }` calculado a la hora real |
| `PATCH /habit-preferences/{habitId}/days/{date}` | `horarios_habito_por_fecha(p, h, fecha)` | la de hoy |
| `PUT /…/{habitId}/weekdays/{weekday}`, `…/reminder`, `…/alarm` (día), `DELETE …/weekdays/{weekday}`, `DELETE …/active` | `horario_semanal_habito(p, h, día)` | la de hoy (204/200) |
| `PUT /habit-preferences/{habitId}/alarm` (general) | `preferencias_horario(p, h)` | la de hoy |
| `PUT` y `DELETE /habit-preferences/{habitId}/week` | `horarios_habito_por_fecha(p, h, fechas de esa semana)` | la de hoy |
| `POST /habit-preferences/week-repeat`, `DELETE /habit-preferences/weeks/{weekStart}` | `horarios_habito_por_fecha(p, *, fechas de esa semana)` | la de hoy |
| `PUT`, `PATCH`, `DELETE /habit-unlocks/{habitId}` | `desbloqueos_habito(p, h)` | la de hoy |
| `POST /habits` (hábito personal) | ninguna (alta): idempotente por `id_cliente`, `recurso = habito:<uuid>` | 201 el mismo hábito, sin duplicar |

### Pruebas que exige la regla 03 en cada endpoint tocado

El 403 de un rol sin permiso y el de una cuenta suspendida **con** cabeceras (ya existen sin ellas); un caso con el reloj en una hora UTC que cae en
el día local anterior (regla 02); `ArchitectureTest` verde (el puerto nuevo, el resolver, ningún `clock.now()` en `domain/`).

## App (`Renaser-90-dias-frontend-`, rama `048-fase-3`)

Solo JavaScript: no hace falta módulo nativo nuevo (el del reloj ya está). Lo que cambia respecto de las fases 1 y 2: un **envío genérico de
escritura** (casi todo lo de esta fase es una llamada REST sin pasos intermedios) y **los avisos de lo que no se aplicó**.

| Pieza | Dónde | FR |
|---|---|---|
| Las tres cabeceras de un envío | `features/sinRed/cabecerasSinRed.ts` (`cabecerasDe(envio)`) | FR-007, FR-014 |
| Envío genérico `escritura` (`{ area, etiqueta, metodo, ruta, cuerpo, resumen }`) y su manejador; helper `escribirOEncolar({...}, ajuste)` que prueba con red, y solo ante `esDeRed` guarda en la cola con la hora real y devuelve la **respuesta optimista** | `features/sinRed/escrituraSinRed.ts`, `colaSinRed.ts` (`TipoDeEnvio` suma `'escritura'` y `'completarAccion'`) | FR-019 |
| `desenlaceDe`: un 409 con `codigo` de la lista cierra el envío (sale de la cola, **no** queda «bloqueado»), anota el aviso y sigue con el siguiente; un 4xx de otro tipo también termina en aviso en vez de quedar mudo | `features/sinRed/colaSinRed.ts` | FR-019, D13, D14 |
| Avisos de lo que no se aplicó: almacén por cuenta (se borra al cerrar sesión), hoja corta con el nombre de lo que no se aplicó, la hora en que lo hiciste, el motivo y **el texto que escribiste** para poder volver a hacerlo; el globo suma «1 cosa no se aplicó» hasta que se ve | `features/sinRed/avisosDeLoNoAplicado.ts`, `components/AvisoDeLoNoAplicado.tsx`, `AnfitrionSinRed.tsx`, `utils/textoDelGlobo.ts` | FR-019, D13 |
| Lecturas guardadas nuevas: `/api/v1/radar/mi-radar`, `/api/v1/radar/latest`, `/api/v1/radar/history` (primera página), `/api/v1/habit-preferences/`, `/api/v1/habit-unlocks`, `/api/v1/habit-preferences/week-repeat` | `features/sinRed/lecturasGuardadas.ts` | FR-004 |
| Cierra tu día: `guardarCierreDelDia` encola si no hay red (día = el de la hora real; el borrador se conserva hasta que sale); el día queda «cerrado» al instante con un ajuste de lectura sobre `/rocks/day-closure` | `features/cierre/api/cierreApi.ts`, `features/cierre/api/cierreSinRed.ts`, `useHojaDelCierre.ts` | FR-019 |
| Código Renaser: `registrarCheckIn` encola; el formulario deja de ir en `SoloConConexion`; el innegociable vuelve a abrirse solo sin señal (P8) y la hora queda respondida al instante (ajuste sobre `/radar/latest` y la historia) | `features/radar/api/radarApi.ts`, `radar/api/radarSinRed.ts`, `useRadarDelDia.ts`, `CodigoRenaserModal.tsx`, `RadarContext.tsx` | FR-019 |
| Mi Radar: `guardarMiRadar` encola con respuesta optimista (la semana con lo pedido) | `radarApi.ts`, `useConfigDeMiRadar.ts` | FR-019 |
| Plan, acciones: `crearPlanDiario` encola (las acciones se ven «Se agendará al volver la señal» y no se marcan, P6); marcar una acción (texto o foto) como `completarAccion`: copia la foto a la carpeta sin red (`archivosSinRed.ts`), sube a S3 y manda el `POST /rocks/{id}/evidence` con las cabeceras; sale hecha al instante, sus alarmas se apagan (`atenderLaAccion`, `cancelarElAvisoDeLaAccion`) y los puntos y la celebración llegan con la respuesta | `objetivos/api/objetivosApi.ts`, `objetivos/api/planSinRed.ts`, `utils/sellarRocaDiaria.ts`, `hooks/useRocasDiarias.ts` | FR-019, FR-011 |
| Plan, objetivos: `crearPlanSemanal`, `editarRocaSemanal`, `cerrarSemana`, `definirRocaMaestra`, `guardarObjetivoDelMes` encolan con respuesta optimista y ajuste de lectura de `/rocks/…` | `objetivosApi.ts`, `planMensualApi.ts`, `useRocasSemanales.ts`, `useRocasMaestras.ts`, `usePlanMensual.ts` | FR-019 |
| Planificar hábitos y alarmas: las escrituras de `habitsApi.ts` pasan por `escribirOEncolar`; `PlanificarDimensionModal` y `SeccionAlarmas` dejan de ir en `SoloConConexion`; las alarmas **locales** se reprograman al instante con lo elegido (el aviso de «rige mañana» lo recalcula la app con la misma regla que el servidor y lo corrige al releer) | `habits/api/habitsApi.ts`, `habits/api/planificarSinRed.ts`, `training/components/PlanificarDimensionModal.tsx`, `alarmas/components/SeccionAlarmas.tsx`, `habits/utils/cambioDeHora.ts` | FR-019 |
| Reglas de la app antes de encolar (mismas que con red: ventanas, día, campos obligatorios) y orden de la cola por `hechoEn` | cada `*SinRed.ts` | FR-010 |
| Decisión del dueño y Maestro | `docs/decisiones/transversal.md` (entrada «fase 3»), `.maestro/` | regla 9 |

## Cómo se prueba

- **Servidor.** Unitarias: `CierreDelDiaSinRedServiceTest` (cierra ayer a las 21:40 y llega hoy; exigidas a la hora real; acción creada después no
  se exige; choque con un cierre posterior; acción que ya no existe; repetición), `CodigoRenaserSinRedServiceTest` (día 8+ con horas de esa fecha, hora ya
  ocupada, `MI_RADAR_NO_APLICA`, `horaPrevista` real, día del programa a la hora real), `AccionesSinRedServiceTest` (marcar: en regla, tarde, futura, fuera de su
  día, ya hecha, roca reemplazada), `ObjetivosSinRedServiceTest` (semanal, mensual, maestra: choque y sin choque), `PlanificarHabitosSinRedServiceTest`
  (ventana «ya arrancó» a la hora real, cuota, choque, `DELETE` idempotente), `RevisionDeLaHoraTest` y `HechoSinRedResolverTest`. Controller
  (`@WebMvcTest`): cabeceras → comando; 409 con `codigo` y `message`; sin cabeceras, igual que hoy; 403 por rol y por cuenta suspendida.
  **ITs con Postgres real**: `EnviosSinRedGeneralIT` (V190/V191 y dos pedidos concurrentes con el mismo `X-Id-Cliente` → un solo efecto),
  `CierreDelDiaSinRedIT`, `CodigoRenaserSinRedIT`, `PlanDeRocasSinRedIT`, `PlanificarHabitosSinRedIT`. Marca `// spec 048 FR-019` en cada una.
- **App.** Jest: `cierreSinRed.test.ts`, `radarSinRed.test.ts` (y se actualiza `radarSinSenal.test.ts` de #179), `planSinRed.test.ts`
  (agendar, marcar con texto y con foto, objetivos), `planificarSinRed.test.ts`, `avisosDeLoNoAplicado.test.ts`, `colaSinRed.test.ts` (409 con `codigo`
  = final), `lecturasSinRed.test.ts` (rutas nuevas), `cabecerasSinRed.test.ts`, y el chequeo de que `SoloConConexion` ya no envuelve lo de la fase.
- **E2E en el emulador** (mobile-mcp, túnel cortado **y app reiniciada**, como la fase 1; backend de la rama sobre `renaser_e2e_048`): (1) Cierra tu día sin
  señal → «cerrado» al instante → con señal llega con la hora real; (2) choque: cerrar el día desde otra sesión (curl) **después** de lo hecho en el
  teléfono → llega y sale el aviso, el servidor se queda con lo suyo; lo contrario (el cambio ajeno **antes**) → se aplica lo del teléfono; (3) Código
  Renaser (días 1–7 innegociable y desde el 8 con Mi Radar) sin señal, hora ya ocupada desde otra sesión → aviso; (4) marcar una acción con foto
  y otra con texto sin señal → hechas al instante, puntos al volver; (5) agendar las acciones de mañana y editar el objetivo del mes sin señal;
  (6) cambiar la hora de un hábito y su alarma sin señal → la alarma local suena a la hora nueva; (7) cerrar sesión con cosas sin enviar. Revisar el log
  del backend (`[offline] … aplicado / no aplicado`) y `adb logcat`, sin errores nuevos. El cruce de medianoche (cerrar a las 23:50 y llegar al
  día siguiente) lo cubre `CierreDelDiaSinRedIT` con el reloj fijo; en el emulador se hace si da el tiempo.

## Preguntas para el dueño (no se inventa nada: cada una tiene su opción recomendada)

- **P1 — ¿«Bitácora» es Cierra tu día?** Solo existe Cierra tu día (la nota del día se guarda ahí). *Recomendada:* sí, una sola cosa. Si hay otra
  bitácora, que lo diga y se agrega.
- **P2 — Agendar un día que ya pasó al llegar** (lo agendaste ayer para ayer, llega hoy). *Recomendada:* no se aplica y se avisa
  (`FUERA_DE_SU_DIA`): crear acciones en un día vivido distorsiona el avance de la semana. Alternativa: crearlas igual.
- **P3 — ¿Tope de llegada de 48 h para lo que no da puntos?** (Cierra tu día, Código Renaser, Plan, planificar). *Recomendada:* no; las 48 h solo
  quitan puntos al marcar una acción. Lo demás lo decide el día y el choque (D13). Alternativa: 48 h para todo.
- **P4 — Código Renaser de una hora que el servidor ya tiene respondida.** *Recomendada:* se queda el del servidor (V52: «el más antiguo es el que la
  persona escribió») y se avisa. Nunca dos por hora.
- **P5 — Código Renaser que ya no corresponde al llegar** (Mi Radar apagado o sin esa hora desde el día 8). *Recomendada:* no se aplica y se avisa, con el texto
  a la vista para que la persona lo copie. Alternativa: guardarlo como registro libre (hoy el servidor lo rechaza con 409).
- **P6 — Acciones agendadas sin señal y marcadas sin señal antes de llegar.** *Recomendada:* no se pueden marcar hasta que lleguen («Se agendará al
  volver la señal»); el `id` lo pone el servidor. Alternativa: que la app genere los ids (cambia el contrato de `POST /rocks/plan`).
- **P7 — Cierra tu día con una acción que ya no existe al llegar.** *Recomendada:* todo o nada (no se aplica y se avisa; el aviso guarda lo escrito).
  Alternativa: aplicar las demás y avisar de la que falta.
- **P8 — Código Renaser innegociable (días 1–7) sin señal.** Con la fase 3 se responde y se encola. *Recomendada:* que vuelva a abrirse solo sin señal
  (revierte lo de #179, que solo existía porque no se podía responder).
- **P9 — Cómo se avisa lo que no se aplicó.** *Recomendada:* una hoja corta la próxima vez que se abre Hoy y el globo del ícono dice «1 cosa no se
  aplicó» hasta que se vea; no hay bandeja histórica.

## Riesgos y límites

- **R1 — `actualizado_en` tiene que moverse en cada escritura** de las nueve tablas; si alguna no lo hace, el choque no ve el cambio. Es la primera tarea
  de cada área, con su prueba.
- **R2 — No hay lápidas**: un borrado hecho en el servidor después de tu cambio no se ve como choque (la fila no está). Se aplica o responde
  `RECURSO_YA_NO_EXISTE` según el caso.
- **R3 — La hora real manda.** En iPhone (sin módulo del reloj) es `TELEFONO_CORREGIDO` y un error de reloj puede decidir un choque; en empate gana el
  servidor, con 2 s de holgura.
- **R4 — Día del programa a la hora real aproximado** (no reconstruye ajustes del reloj de esa semana): solo afecta el Código Renaser (día 8) y la
  semana libre de reacomodos.
- **R5 — Alcance grande** (unos 25 endpoints en cuatro áreas). Se parte en tramos que se pueden fusionar solos: núcleo común + Cierra tu día +
  Código Renaser; Plan; planificar hábitos. D16 pide un PR y un APK para la fase; si el dueño prefiere tres PR, el contrato de arriba no cambia.
- **R6 — Lo mudo de las fases 1 y 2**: hoy un envío `bloqueado` y un `motivoSinPuntos` no se muestran en ninguna pantalla (FR-015 y FR-016 lo exigen).
  La fase 3 construye el aviso y lo engancha; conviene **abrir un issue de bug** (regla 3) para dejar constancia y que el PR lo cierre.
- **R7 — `PlanificarDimensionModal` tiene unas 2 400 líneas**: se envuelven las llamadas, no se reescribe la hoja (regla 11).
- **R8 — Tabla de otro módulo**: `rocks` usa `envios_sin_red` por `habits.api`. Si Spring Modulith no lo admite, el puerto pasa a `shared`.
- **R9 — La Pastilla** sigue sin `hechoEn` en su endpoint (`/spirit-audio/submit`, límite de #179): fuera de esta fase, trabajo del backend aparte.
- **R10 — `id_cliente` es clave global** (V188): un UUID ya usado por otra cuenta no se ve (se filtra por participante) y su `INSERT` no hace nada; es
  un caso de probabilidad nula con UUID aleatorios, anotado para que nadie lo «arregle» con otra clave sin saber por qué.
