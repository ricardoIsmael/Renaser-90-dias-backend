# Plan — backend del Mapa de Renacimiento (Día 7)

**Fecha:** 2026-09-08 · **Estado:** plan, sin una línea de código escrita.

> **Corregido el mismo día, tras la pregunta del dueño** (*"¿y el onboarding que existe cómo es? ¿no
> podemos modificarlo sin tener que crear algo nuevo?"*). La primera versión de este plan proponía un
> **módulo `mapa` con cinco tablas nuevas**. Estaba mal encarado: el módulo `onboarding` ya es un
> **motor de cuestionarios multi-flujo**, y el Mapa es, en sus tres cuartas partes, otro flujo. Ver
> §2.5 — el trabajo baja a menos de la mitad. Se deja a la vista qué decía antes para que nadie
> "corrija" esto de vuelta.

---

## 0. La respuesta corta a la pregunta

**No. El Mapa de Renacimiento no tiene backend, y hoy no guarda nada en ningún servidor.**

Verificado leyendo el módulo entero (`src/features/mapa-renacimiento/`, repo del frontend):

- Todo vive en `AsyncStorage`, bajo la clave `renaser.mapa-renacimiento.v1.<usuario>`.
- Lo dice el propio `almacen.ts`: *"el backend todavía no tiene las entidades del mapa"*.
- Lo único que llega al servidor es al **activar**: un `POST /api/v1/habits` por cada acción motora,
  que crea hábitos personales.

**Consecuencia práctica:** si el aprendiz borra la app o cambia de teléfono, **pierde el mapa
entero** — los tres objetivos, las acciones, los reemplazos, los nueve hitos y el protocolo de
retorno. Solo sobreviven los hábitos que ya se hayan creado al activar. Tampoco existe el panel del
mentor (§6 del manual): nadie más que el propio aprendiz puede ver su mapa.

El contrato que falta ya está propuesto en `docs/MAPA_RENACIMIENTO_DIA7.md` §4 (repo del frontend).
Este plan lo convierte en trabajo repartible.

---

## 1. Lo que ya está resuelto y no hay que rehacer

Es más de lo que parece, y conviene tenerlo claro antes de estimar:

| Pieza | Dónde |
|---|---|
| Las 11 vistas V01–V11 | `src/features/mapa-renacimiento/screens/` |
| El modelo de datos completo | `tipos.ts` — es §5.1 del manual, ya traducido a tipos |
| **Las ocho reglas de calidad de §4.3** | `reglas.ts` — con sus mensajes textuales |
| La redacción SMART determinista | `reglas.ts` |
| Los hitos sugeridos (40 % / 75 % / 100 %) | `reglas.ts` |
| Autoguardado local y reanudación | `almacen.ts` |
| Activación idempotente por `accionId → habitId` | `hooks/useMapaRenacimiento.ts` |

`reglas.ts` es la pieza clave: **el servidor tiene que repetir esas reglas, no inventarlas.** Son las
mismas, escritas dos veces en dos lenguajes — y eso hay que decirlo en el código de los dos lados,
porque es exactamente la forma de E-156 y E-159 (*dos caminos para el mismo dato y solo uno
actualizado*).

---

## 2. Decisiones que hay que tomar ANTES de escribir código

Ninguna es técnica; las cuatro son del dueño. **No las rellene un agente por su cuenta.**

| # | Decisión | Por qué bloquea |
|---|---|---|
| 1 | ~~**¿Módulo propio `mapa`, o dentro de `onboarding`?**~~ **RESUELTO el 2026-09-08: dentro de `onboarding`, como un flujo más.** La recomendación original era módulo propio; era incorrecta, y la corrige §2.5 — `onboarding` ya es un motor multi-flujo con cinco flujos vivos, y el mapa es otro | — |
| 2 | **¿El mapa se puede editar después de activado?** El manual habla de `map_version` y de snapshot al activar, pero no dice qué pasa si al día 40 el aprendiz quiere cambiar una meta | Cambia el modelo: versionado real vs. una sola fila mutable |
| 3 | **¿Quién ve el mapa además del aprendiz?** §6 dice "panel del mentor". ¿Solo su mentor, o también MENTOR_LEAD / ADMIN / ALCHEMIST? | Define `@RequiresPermission` y los tests de autorización negativa |
| 4 | ~~**¿El vocabulario del contrato HTTP va en inglés o en español?**~~ **RESUELTO por la decisión #1.** Al entrar como flujo de `onboarding`, el mapa hereda la convención ya establecida del módulo: *wire en inglés, dominio en español*, el mismo criterio que `phasecontracts` (`docs/MODULO_ONBOARDING.md` §1). No hay nada que elegir | — |

---

## 2.5. Reusar el motor de `onboarding` — sí, y cambia el plan entero

**El módulo `onboarding` no es "el cuestionario del Día 0": es un motor de formularios con `flujo`
como columna.** `V10` ya siembra **cinco flujos** en la misma maquinaria: `terminos`, `pacto`,
`ficha_inicial`, `cuestionario_profundo` y `diseno_destino`. Agregar `mapa_dia7` como sexto es
exactamente el uso para el que se diseñó el esquema.

### Lo que sale gratis

| Necesidad del mapa | Ya existe |
|---|---|
| Guardar respuestas por usuario | `respuestas_onboarding` (EAV tipado, `UNIQUE (usuario_id, pregunta_id)`) |
| Reanudar donde quedó | `estado_onboarding.flujo_actual` / `seccion_actual` / `paso_actual` / `progreso_flujo` |
| Endpoints de leer y guardar | `ObtenerCuestionarioUseCase`, `GuardarRespuestaUseCase`, `ObtenerRespuestasUseCase`, `AvanzarEstadoUseCase` |
| Audio, firma y archivos a S3 | `medias_onboarding` + `MediaService` (sirve para el audio de bautizo de V11) |
| Los 11 tipos de pregunta | `tipo_pregunta_onboarding`: TEXTO, AREA_TEXTO, NUMERO, ESCALA, SELECCION_UNICA, SELECCION_MULTIPLE, AUDIO, FIRMA, CASILLA, FECHA, ARCHIVO |

**Los tres objetivos del mapa entran enteros ahí**: tipo de resultado (`SELECCION_UNICA`), línea base
y meta (`TEXTO`/`NUMERO`), unidad, evidencia, motivo (`AREA_TEXTO`), la escala 1–10 de relaciones
(`ESCALA`), el compromiso de seguimiento (`CASILLA`). Y los nueve hitos son 9 preguntas fijas
(3 áreas × 3 días). **Todo eso es una migración con INSERTs, no código Java.**

### Lo que NO entra, y hay que decirlo antes de empezar

1. **Las listas que se repiten.** `respuestas_onboarding` tiene `UNIQUE (usuario_id, pregunta_id)`:
   una respuesta por pregunta. Las **1–6 acciones motoras** y los **1–3 protocolos de reemplazo** son
   listas de objetos, así que van en un `valor_json`. Se puede — la columna existe y está probada
   contra Postgres real — pero el panel del mentor (§6) deja de poder filtrar por área o por carga en
   SQL y tiene que parsear JSON.
2. **El enlace acción ↔ hábito.** Al activar hay que recordar `accionId → habitId` para que tocar
   "Activar" dos veces no duplique (AC-07). Eso es una **FK real a `habitos`**, y una FK no cabe
   dentro de un `valor_json`. **Es la única tabla nueva que hace falta de verdad.**
3. **Las ocho reglas de §4.3.** `preguntas_onboarding.reglas_validacion` es `jsonb` y **no tiene
   intérprete en Java** — lo dice el javadoc de `Pregunta`: *"NO hay un intérprete de
   reglasValidacion"*. Se pasa crudo al cliente. Así que `THIRD_PARTY_CONTROL`, "meta ≠ línea base",
   "máximo 2 acciones por objetivo" y compañía **hay que escribirlas en Java igual**. Reusar el motor
   ahorra la persistencia, no las reglas.
4. **`estado_onboarding` es UNA fila por usuario con UN `flujo_actual` y UN `completado`.** Hoy los
   cinco flujos comparten esa fila. Meter el mapa como sexto sin más mezcla "terminó el Día 0" con
   "terminó el mapa". Falta una **marca de completado POR ETAPA** — que es exactamente el pendiente
   ya anotado en `PENDIENTES_2026-09-05.md` §3.5: *"necesita una marca por etapa en el backend. No se
   agregó a propósito: las etapas 3, 4 y 5 van a necesitar el mismo mecanismo — conviene hacerlo una
   vez."* **Esta es esa vez.** Y es lo que dibuja la pantalla "Tu proceso completo · 1 de 2 etapas".

### Veredicto

**Reusar, con una tabla nueva y una columna nueva, en vez de cinco tablas y un módulo nuevo.** El
mapa queda como `flujo = 'mapa_dia7'` del motor existente; lo único propio es lo que el motor no
puede representar: el enlace a los hábitos y la marca de etapa completada.

**Lo que NO cambia con esta decisión:** las ocho reglas siguen siendo el trabajo grande, y siguen
teniendo que correr en el servidor. Hoy solo viven en el teléfono, o sea que un `curl` se las salta.

---

## 3. Dependencia dura: hoy un hábito no puede correr solo algunos días

**Esto no es un detalle, es un bloqueante del punto central del mapa.**

La activación tiene que crear las acciones motoras como hábitos **con su frecuencia y sus días**
(V06 deja elegir 1–7 días por acción). Hoy no se puede:

- `CreatePersonalHabitRequest` no lleva días ni frecuencia — solo `triggerTime` y `limitTime`.
- `MisHabitosService.crear` fija `TipoDia.TODOS` siempre.

Está anotado como *"reportado y NO arreglado"* en `BITACORA_ERRORES.md` **E-137**, y el propio
`MAPA_RENACIMIENTO_DIA7.md` §2.6 lo llama *"la limitación más visible para el aprendiz"*: alguien
que eligió correr 3 días por semana termina con un hábito diario.

Además `TipoDia` **no es un conjunto libre de días** — soportarlo pide una decisión de producto
sobre qué combinaciones se permiten. **Es trabajo previo, y es del módulo `habits`, no del mapa.**

Sin esto, la activación se puede construir igual, pero creando hábitos diarios como hoy. Hay que
elegir explícitamente y decirlo, no descubrirlo después.

---

## 4. El trabajo, en fases

Las fases 1 y 2 son secuenciales entre sí; de ahí en adelante casi todo va en paralelo.

### Fase 1 — `V41`: el flujo nuevo y lo poco que el motor no cubre (un agente)

**No son cinco tablas. Es una migración con datos y una tabla chica.**

1. **Seed del flujo** `mapa_dia7`: `INSERT` en `secciones_onboarding` (11 secciones, una por vista) y
   en `preguntas_onboarding` (los tres objetivos, los nueve hitos, el retorno, la prioridad, el
   compromiso). Mismo formato que `V10`.
2. **Una tabla nueva**, lo único que el EAV no puede representar:

```
acciones_mapa   id, usuario_id FK, accion_id (el id que ya genera el cliente), habito_id FK -> habitos,
                creado_en,  UNIQUE (usuario_id, accion_id)
```

   El `UNIQUE` es lo que hace idempotente la activación (AC-07): el segundo toque choca en vez de
   crear un hábito duplicado. Con la FK a `habitos`, borrar un hábito deja de dejar el mapa apuntando
   al vacío — eso un `valor_json` no lo puede garantizar.

3. **La marca de etapa completada** (§2.5 punto 4), que sirve al mapa y a las etapas 3, 4 y 5 que
   vengan después. Tabla chica `etapas_onboarding_completadas (usuario_id, flujo, completado_en)`,
   con `UNIQUE (usuario_id, flujo)` — y no una columna más en `estado_onboarding`, porque el número
   de etapas va a crecer y una columna por etapa es la forma de tener que migrar cada vez.

La cabecera de la migración justifica por qué se toca la base y **por qué NO se reusa** el
`completado` de `estado_onboarding`, como `V18` y `V20`.

### Fase 2 — Dominio y reglas (un agente, el trabajo más grande)

El agregado `MapaRenacimiento` con los estados de §5.2 y **las ocho reglas de `reglas.ts` portadas
a Java**, con sus tests unitarios sin Spring:

- definición de terminado (§1.2),
- `THIRD_PARTY_CONTROL` — la regla que rechaza *"que mi pareja me valore más"* (AC-04),
- máximo 6 acciones y 2 por objetivo, 1–3 patrones de reemplazo,
- `UNREALISTIC_LOAD` (aviso sobre 28 acciones/semana),
- redacción SMART determinista, para poder devolverla ya escrita.

**Nunca confiar en el cliente:** hoy estas reglas solo existen en el teléfono, o sea que un `curl`
las salta todas.

### Fase 3 — Borrador servidor (un agente, depende de 1) — **ahora es casi nada**

Los endpoints **ya existen**: `ObtenerCuestionarioUseCase` devuelve el flujo con sus secciones y
preguntas, `GuardarRespuestaUseCase` guarda respuesta por respuesta y `AvanzarEstadoUseCase` mueve
el paso. Con el seed de la Fase 1, el mapa se guarda en el servidor **sin escribir un caso de uso
nuevo**.

Lo único a agregar: un endpoint de conveniencia que devuelva las respuestas del flujo `mapa_dia7`
ya armadas con la forma de `MapaRenacimiento`, para que la app no tenga que recomponerla pregunta
por pregunta.

**Con esto solo, el mapa deja de perderse al cambiar de teléfono** — el 80 % del valor de todo este
plan, y ahora cuesta una fracción de lo que costaba en la versión anterior de este documento.

### Fase 4 — Activación (un agente, depende de 2 y 3, y de la decisión de §3)

```
POST /api/v1/mapa-renacimiento/activar
```

Idempotente por `mapa_id + version`. Valida la definición de terminado del lado del servidor, hace
el snapshot, crea los hábitos y marca `activado_en`.

Crear hábitos desde otro módulo **necesita un puerto nuevo en `habits/api/`** — hoy no existe
ninguno (`habits/api/` solo expone finders y eventos), y un módulo no puede tocar
`habits.application`. Ojo con **D-122**: la hora de disparo de los hábitos que genere no puede pasar
de las 23:40.

### Fase 5 — Panel del mentor (un agente, depende de 3 y de la decisión #3)

```
GET /api/v1/admin/mapas/{usuarioId}
```

Con **tests de autorización negativa**, que en este repo son obligatorios para todo endpoint nuevo:
un rol sin permiso recibe 403, y un usuario `SUSPENDED` recibe 403 aunque su token sea válido.

### Fase 6 — Sincronización en la app (un agente, depende de 3)

La sincronización se agrega **encima** de `almacen.ts`, sin tocar las pantallas: escribir local
primero, sincronizar al salir del campo (§2.1). El borrador local sigue siendo la primera capa —
eso es lo que hace que el mapa funcione sin conexión (§5.4).

### Fase 7 — Redacción asistida por IA (§4.2) — **al final, y opcional**

```
POST /api/v1/mapa-renacimiento/redaccion
```

El manual la pone como *release criterion* que **el mapa se pueda completar sin IA**, y hoy la
redacción determinista ya cumple. Entra como refinamiento, no como requisito.

Cuando se haga: **ninguna llamada a un puerto de IA dentro de un `@Transactional`** (C-1) — una IA
real puede tardar 45 s reteniendo una conexión de Hikari y agota el pool de toda la API.

---

## 5. Orden sugerido

```
Decisiones del dueño (§2) ──┐
                            ├─> Fase 1 (V41) ──> Fase 2 (dominio) ──> Fase 3 (borrador)  ← acá ya no se pierde el mapa
Decisión de §3 (días) ──────┘                                          │
                                                                       ├─> Fase 4 (activar)
                                                                       ├─> Fase 5 (panel mentor)
                                                                       └─> Fase 6 (sync en la app)
                                                                            Fase 7 (IA) al final
```

**Si hay que cortar por algún lado, la fase 3 sola ya justifica el trabajo:** es la diferencia entre
que un aprendiz pierda su mapa al cambiar de teléfono y que no lo pierda.

---

## 6. Lo que este plan NO cubre

- **El audio de bautizo de V11.** No existe el asset ni el flujo; hoy dice "próximamente", que es
  la verdad.
- **La analítica de §7 del manual.**
- **Sacar del onboarding del Día 0 las piezas que el manual pone en el Día 7.**
  `MAPA_RENACIMIENTO_DIA7.md` §3 lo dejó explícitamente afuera: es una decisión del dueño sobre el
  Día 0, no una consecuencia de construir este backend.
- **La bandera de encendido.** El mapa hoy está apagado en producción salvo que
  `EXPO_PUBLIC_MAPA_DIA7=on` esté definida al compilar. Encenderlo es poner la variable en Vercel y
  redesplegar — una decisión, no una tarea.
