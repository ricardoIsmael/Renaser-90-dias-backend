# Propuesta: ajustar el día del programa de un aprendiz

**Fecha:** 2026-09-03
**Estado:** **Fase 0 y Fase 1 implementadas** (V20, V21, V22 — D-81 y D-82). La Fase 2 **se
descartó al descubrir que su premisa era falsa** (ver §3.2). La Fase 3 sigue sin construirse, por
recomendación.
**Decisión de alcance (dueño del proyecto, 2026-09-03):** solo **ADMIN/ALCHEMIST**. Sin self-service.

---

## 1. El problema real, en las palabras del cliente

> "Tuve que viajar y ahora, para no perder mis puntajes, me gustaría que me regresen a tal día
> — el 34, digamos, que es donde estaba bien."

Tres cosas importan de esa frase, y ninguna es lo que a primera vista parece:

1. **Es retroactivo, no programado.** El aprendiz **no avisa antes** de viajar. Vuelve, ya perdió
   días, y recién ahí pide el ajuste. Un botón de "pausar desde hoy hasta tal fecha" **no resuelve
   este caso** — llega tarde por definición.
2. **El objetivo es el puntaje, no el calendario.** "Para no perder mis puntajes" — no le duele la
   fecha de graduación, le duele haber acumulado días fallidos. **Ojo:** al verificar esto encontré
   que hoy esos días fallidos *no le cuestan nada* — la coherencia no se calcula (§3.2). El miedo
   del cliente es legítimo para el futuro, no para el estado actual del sistema.
3. **El aprendiz elige el día, no la cantidad.** Dice "regrésenme al 34", no "descontame 6 días".
   El día es un lugar concreto donde se sentía bien; la aritmética es problema nuestro.

Por eso esto **no es una feature de pausa**. Es una feature de **reposicionar el reloj**, y el
endpoint que la sirve ya existe.

---

## 2. Qué ya existe y qué se corrigió hoy

### Ya existía

`PUT /api/v1/admin/trainees/{id}/program-day` con body `{"programDay": 34}`, permiso
`MANAGE_TRAINEES`, en [`TraineeAdminController.java:63`](../src/main/java/com/renaser/os/users/infrastructure/adapter/in/rest/admin/TraineeAdminController.java).
Cadena completa: `SetTraineeProgramDayUseCase` → `ParticipacionProgramaService.fijarDia` →
`ParticipacionPrograma.fijarDia`.

### El problema que tenía

Escribía `dia_programa = 34` **a mano** y nada más. Consecuencias, todas reales:

| Síntoma | Causa |
|---|---|
| Al día siguiente el aprendiz volvía de un salto al día real (41, no 35) | El cron incrementaba desde el valor escrito, o lo pisaba |
| La graduación no se movía | `fecha_graduacion_esperada` = `fecha_inicio + 90`, y `fecha_inicio` no se tocaba |
| El ajuste no quedaba registrado en ningún lado | Se disolvía dentro de un contador; nadie podía auditar por qué alguien estaba en el día 34 |
| Día y fecha de inicio contaban historias distintas para siempre | Nada los reconciliaba |

### Lo que se corrigió (Fase 0, ya implementado — migración `V20`)

El reloj pasó de **incremental** a **derivado**:

```
dia_programa = acotar([0, 90], (hoy_en_su_zona − fecha_inicio) + 1 − dias_ajuste_programa)
```

`dias_ajuste_programa` (columna nueva, `smallint` **con signo**) es ahora el único knob del reloj.
`fijarDia(34)` ya no escribe el día: calcula el corrimiento que hace que **hoy** caiga en el 34, lo
guarda, y materializa el día. Los cuatro síntomas de arriba desaparecen de una: el ajuste persiste,
la graduación se corre sola (`fecha_inicio + 90 + dias_ajuste`), el corrimiento queda como dato
auditable, y el día siempre es coherente con las fechas.

**Nada de la historia se toca.** Hábitos, evidencias y puntajes ya ganados viven en otras tablas con
su propia fecha. Retroceder el reloj **retoma el conteo, no reescribe el pasado** — que es
exactamente lo que el aprendiz pide cuando dice "no quiero perder mis puntajes".

---

## 3. Las tres fases y en qué quedó cada una

### Fase 1 — IMPLEMENTADA: el ajuste tiene motivo y autor (V21, D-82)

Antes, un admin podía mover a alguien del día 40 al 34 y **no quedaba registro de quién ni por qué**.
Con un solo dato de contexto eso dejó de ser un agujero.

**Cambio de contrato (compatible hacia atrás):**

```json
PUT /api/v1/admin/trainees/{id}/program-day
{ "programDay": 34, "motivo": "Viaje 03/09–09/09, avisó al volver" }
```

- `motivo`: máx. 280 caracteres, **opcional en el comando**. El panel admin actual todavía manda
  solo `programDay` y no puede romperse; el dominio normaliza el vacío a
  `AjusteDiaPrograma.MOTIVO_NO_REGISTRADO` en vez de guardar un NULL que obligue a ramificar en cada
  lectura. Un motivo más largo que el tope se recorta, no explota: perder la bitácora entera por un
  texto largo sería el peor de los tres resultados.
- Tabla `ajustes_dia_programa` (append-only, V21): `id`, `participante_id`, `dia_anterior`,
  `dia_nuevo`, `dias_ajuste_anterior`, `dias_ajuste_nuevo`, `motivo`, `ajustado_por`, `ajustado_en`.
  Se escribe **en la misma transacción** que el ajuste: si fuera aparte o por evento async, un fallo
  dejaría el día movido sin rastro — justo el agujero que la tabla viene a cerrar.
- `GET /api/v1/admin/trainees/{id}` devuelve `lastDayAdjustment` (null si nunca le movieron el día),
  para que el panel muestre *"día 34 — ajustado por Ana el 03/09: viaje"* en vez de un número sin
  explicación.
- **Tests:** `AjusteDiaProgramaTest` (6, dominio), `AjusteDiaProgramaPersistenceAdapterTest` (4,
  Postgres real) y `TraineeAdminControllerTest` (10, contrato REST) — **este último no existía**: el
  endpoint que mueve el día de un aprendiz no tenía ni una prueba de capa web, ni siquiera de
  autorización negativa (regla 0.3).

**Por qué una tabla y no una columna:** un aprendiz puede pedir esto más de una vez en 90 días, y la
pregunta que va a hacer el cliente es "¿cuántas veces le movimos el día a este chico?". Una columna
solo guarda la última.

**Por qué esta fase es la primera:** es la única que cambia el contrato HTTP. Cuanto antes entre,
menos versiones del panel admin hay que soportar.

### Fase 2 — DESCARTADA: la premisa era falsa

**Esta sección decía originalmente** que un aprendiz que viaja una semana arrastra siete días de
hábitos fallidos que le bajan la coherencia, y proponía neutralizarlos con un estado `ANULADO`.
**Eso es falso hoy**, y lo verifiqué antes de escribir una línea de esa fase:

| Pieza | ¿Funciona hoy? | Evidencia |
|---|---|---|
| Ganar puntos al completar hábito / evidencia / roca | **Sí** | `RegistroService`, `RachaService` y `EvidenciaService` llaman a `points.api.AjustarPuntosPort` de forma síncrona |
| **Perder** puntos al no completar | **No** | `RegistroService.expirarUnoEnTransaccionPropia` solo cambia el estado a `EXPIRADO`; no toca `AjustarPuntosPort` |
| **Coherencia** | **Nunca se calcula** | `RegistrarCoherenciaDiariaUseCase` no tiene un solo llamador en `src/main`: ni scheduler, ni listener, ni controller. `points` no tiene **ningún** `@ApplicationModuleListener` |
| Racha diaria (`rachaActual`/`rachaMaxima`) | **Nunca avanza** | `actualizarRachaTrasDia` solo se invoca dentro de ese mismo método muerto |
| `historial_coherencia` | Siempre vacía | Su único escritor es ese método |
| Ranking por coherencia (`TipoRanking.CELL`) | Ordena por una constante | `RankingService:134` ordena por `candidato.coherencia()`, que es 100 para todos |

**Conclusión:** hoy un aprendiz que viaja **no pierde nada** — ni puntos ni coherencia. Construir un
estado `ANULADO` para excluirlo de un cálculo que no existe sería código contra un fantasma: sin
forma de probarlo de verdad, y con una regla que quedaría desactualizada respecto de cómo se
termine implementando la coherencia cuando se implemente. Es exactamente el tipo de trabajo
especulativo que produce errores a futuro en vez de prevenirlos.

**Lo que sí quedó resuelto sin escribir esa fase:** la bitácora de la Fase 1 guarda `dia_anterior`,
`dia_nuevo` y ambos offsets de cada ajuste. Cuando se cablee la coherencia, ahí está el dato exacto
del tramo a excluir, sin haber tenido que inventar antes un estado que nadie leía.

**El hallazgo real, que es más grande que esta propuesta:** *el motor de coherencia y racha diaria
está desconectado.* Un programa de 90 días cuyo indicador central no se calcula. Eso merece su
propia tarea y su propia decisión de producto (¿cómo se define la coherencia: % de hábitos
completados sobre esperados del día? ¿acumulada o ventana móvil?), y hasta que exista, el ranking
por célula no ordena nada.

### Fase 3 — NO construida (opcional, solo si el cliente lo pide)

Recién acá entra una pausa hacia adelante: `POST /api/v1/admin/trainees/{id}/pausa` con
`{ "desde": "2026-09-10", "hasta": "2026-09-17" }`, que suspende la generación de tracks en esa
ventana y suma los días a `dias_ajuste_programa` sobre la marcha.

**Deliberadamente última**, y quizá nunca: el cliente describió el caso del que **no avisa**. Una
pausa programada sirve al aprendiz previsor, que no es el que trae el problema. Construirla primero
sería resolver el caso fácil y dejar el difícil sin cubrir.

---

## 4. Riesgos y bordes

| Riesgo | Mitigación |
|---|---|
| Un admin mueve el día por error y no hay vuelta atrás | Fase 1: la bitácora guarda `dias_ajuste_anterior`, así que revertir es fijar el día que registra la fila anterior |
| Retroceder cruza hacia atrás un umbral de fase (día 35 → 34 sale de fase 3) | Ya resuelto: `fijarDia` recalcula la fase desde el día (D-67). `phasecontracts` nunca lee la columna `fase`, la deriva |
| Un contrato de fase ya firmado en un día que ahora "no llegó" | **Sin resolver.** Los días de firma son 17/35/65. Retroceder de 40 a 34 deja un contrato de día 35 firmado por alguien que está en el 34. Hay que decidir si se invalida o se respeta — **recomiendo respetar**: ya lo firmó, y desfirmar algo es peor que tenerlo adelantado |
| `fecha_graduacion_esperada` (columna generada) queda desactualizada | Documentado en `V20`. Ninguna query la lee; la verdad la da `ParticipacionPrograma.fechaGraduacionEsperada()`. **Resuelto (V22)**: la columna generada se borró; la verdad la da el método de dominio |
| Ajuste que empuja la graduación más allá de una cohorte | No hay concepto de cohorte con fecha de cierre hoy (`celulas.cohorte_id` existe pero no tiene fechas). No aplica todavía |

---

## 5. Estado de las decisiones

| # | Decisión | Estado |
|---|---|---|
| 1 | Fase 2 (neutralizar hábitos del tramo) | **Descartada** — premisa falsa, ver §3.2. No se escribió código |
| 2 | ¿Anulación automática o por ajuste? | **Sin objeto** — depende de la Fase 2 |
| 3 | ¿Los contratos de fase firmados sobreviven a un retroceso? | **Sí, sobreviven.** No requirió código: `phasecontracts` nunca lee `participantes_programa.fase`, deriva la fase del día, y un contrato ya firmado es una fila propia que nada toca al mover el reloj. Verificado, no asumido |
| 4 | ¿Se construye la Fase 3 (pausa programada)? | **No**, por recomendación: el cliente describió el caso del que *no* avisa |

## 6. Lo que queda abierto, por prioridad

1. **Cablear el cálculo de coherencia y racha diaria** (§3.2). Es el hueco más grande del producto
   hoy y no tiene nada que ver con esta propuesta — simplemente apareció al verificarla.
2. **Decidir qué pasa con `dias_ajuste_programa` si alguna vez hay cohortes con fecha de cierre.**
   Hoy `celulas.cohorte_id` existe pero no tiene fechas, así que no aplica.
3. **Fase 3**, si el cliente la pide.
