# Plan — selector de hora, bloqueos y orden de hábitos

**Fecha:** 2026-09-02
**Repo tocado:** `C:\Diseño Opusplan tab 01\renaser-rn\renaser` (frontend React Native/Expo)
**Backend contra el que se verificó:** `http://localhost:8080` (Java, corriendo en vivo, no se tocó)
**Archivos tocados:**
- `src/screens/PlanScreen.tsx`
- `src/features/habits/api/habitsMappers.ts`
- `src/features/habits/hooks/usePlanHabitos.ts`
- `src/features/habits/components/HoraPickerModal.tsx` (nuevo)

---

## Resumen de los cinco puntos

| # | Pedido | Estado |
|---|---|---|
| 1 | Selector de hora táctil | **Hecho** — modal propio con grillas de botones, sin dependencia nueva |
| 2 | Sombrear/bloquear hábitos vencidos | **Hecho**, con una limitación documentada: no hay endpoint que exponga la zona horaria del aprendiz (ver `## Falta en el backend`) |
| 3 | Hábitos obligatorios sin poder desactivar | **Hecho** con `isOptional`, pero **no coincide** con los 4 que nombró el dueño — ver §3 |
| 4 | Respetar el día del hábito (`tipo_dia`, días de la semana) | **No implementado** — confirmado que ningún endpoint que consume Plan lo expone |
| 5 | Ordenar hábitos por hora dentro de cada bloque | **Ya estaba resuelto** en el código de hoy; se verificó y se documenta el criterio |

---

## 1. Selector de hora táctil

**Antes:** `TextInput` de texto libre (`styles.timeInputDirect`) donde el aprendiz escribía la hora a mano.

**Verificado en `package.json`** antes de tocar nada: el proyecto **no trae ningún selector de hora nativo** (`@react-native-community/datetimepicker` no está instalado, ni nada equivalente). Instalarlo hubiera significado sumar una dependencia con código nativo — rebuild de Expo, riesgo de romper algo en un momento en que no puedo tocar el backend ni pedirle al dueño que reconstruya el proyecto para probar.

**Decisión:** en vez de instalar algo nuevo, se construyó `src/features/habits/components/HoraPickerModal.tsx`, un modal con el mismo lenguaje visual que ya usan `moveMomentModalVisible` y los selectores de día/momento existentes en `PlanScreen.tsx`: grillas de botones grandes (48×48), nada de texto libre.

- Fila **HORA**: 24 botones (00–23).
- Fila **MINUTOS**: 12 botones, en pasos de 5 (00, 05, 10 … 55) — un selector de los 60 minutos posibles con un dedo grande es más difícil de acertar, no más preciso en la práctica; casi todos los horarios reales del catálogo caen en horas exactas o en :30.
- Al abrir, arranca en la hora que ya tenía el hábito (no en un valor fijo), redondeada al múltiplo de 5 más cercano.
- Botón "✓ GUARDAR HORA" al final, mismo componente `GoldButton` que usa el resto de la pantalla.

**Persistencia:** se conectó el guardado al backend real. Al confirmar una hora se llama a `PATCH /api/v1/habit-preferences/{habitId}` (vía `habitsApi.cambiarHorario`), con reversión local + `Alert` si falla.

### Bug encontrado y corregido de paso: `cambiarHora` borraba `limitTime`

`src/features/habits/hooks/usePlanHabitos.ts` ya traía una función `cambiarHora` (creada hoy mismo, según su propio comentario), pero **no estaba conectada a ningún lado todavía** — se verificó con `grep -rn "cambiarHora"` que solo aparecía definida, nunca invocada. Tenía un bug real: llamaba a `habitsApi.cambiarHorario(habitId, hora, null)`, mandando **siempre `null`** como `limitTime`. Como el PATCH reemplaza los dos campos a la vez, usarla tal cual para el selector nuevo habría borrado la hora límite de hábitos como "AUDIOTERAPIA SEMANAL" (23:55) solo por cambiar la hora de disparo.

Se corrigió la firma para que reciba el `limitTime` actual del hábito y lo reenvíe sin tocarlo (`usePlanHabitos.ts`, función `cambiarHora`). Como nada más la llamaba, el cambio de firma no rompe nada existente. Documentado también en el propio código.

En la práctica, `PlanScreen.tsx` no terminó usando esta función del hook directamente: usa su propio `guardarNuevaHora`, que llama a `habitsApi.cambiarHorario` de forma directa. Motivo: el hook expone su propia copia de `habits` y, si `PlanScreen` hubiera usado `cambiarHora` del hook, el `useEffect` que sincroniza `habitsDelBackend → habits` habría **pisado** cualquier edición local que el aprendiz hizo en pantalla sobre otros hábitos (mover de momento, pausar un día) cada vez que guardara una hora — un efecto secundario que ya existía en el diseño de la pantalla (nada de eso se persiste hoy) y que no correspondía introducir como regresión nueva. Se dejó la corrección del bug en el hook igual, para quien lo conecte más adelante.

---

## 2. Sombrear y bloquear hábitos vencidos

**Criterio implementado**, tal como lo pidió el dueño: un hábito se sombrea y se bloquea (switch deshabilitado, hora no editable) **solo si tiene `limitTime` y esa hora ya pasó hoy**. Un hábito sin `limitTime` (la mayoría del catálogo) **nunca vence dentro del día**, aunque su hora de disparo (`triggerTime`) ya haya pasado — se muestra normal.

También se acotó a "hoy": si el aprendiz está mirando el selector de otro día de la semana (`selectedDay !== hoy`), el vencimiento no aplica — mirar el jueves un martes no debería mostrar nada bloqueado, porque el jueves todavía no llegó.

Código: `habitoVencidoHoy()` en `PlanScreen.tsx`, usado en el render de cada tarjeta de hábito.

### La limitación real: no hay endpoint que exponga la zona horaria del aprendiz

Se verificó con `curl` contra los tres candidatos obvios, los tres sin `timezone`:

```
$ curl -s -i -X OPTIONS -H "X-Actor-Id: ..." http://localhost:8080/api/v1/users/me
Allow: PATCH,POST,OPTIONS        # no hay GET

$ curl -s -i -X OPTIONS -H "X-Actor-Id: ..." http://localhost:8080/api/v1/users/me/trainee-profile
Allow: PATCH,OPTIONS             # no hay GET

$ curl -s -H "X-Actor-Id: ..." http://localhost:8080/api/v1/onboarding/activate-program
{"activated":true,"validStartDates":[]}   # no trae timezone
```

Y por código: `ParticipacionPrograma.java` (backend) documenta en su Javadoc que la zona vive en `participantes_programa.timezone`, pero ningún `Controller` de `users` la serializa en una respuesta.

**Solución aplicada mientras tanto:** se usa la hora del propio teléfono (`new Date()`, zona del dispositivo) como aproximación — el aprendiz completa sus hábitos desde su celular, así que salvo que tenga mal configurada la hora del equipo, coincide con su zona real. Esto **no es lo mismo** que "la zona horaria del aprendiz que manda el backend" que pidió el encargo, es la mejor aproximación posible sin ese dato. Ver `## Falta en el backend`.

El reloj se refresca solo cada 30 segundos (`setInterval` en `PlanScreen.tsx`) para que un hábito se sombree en vivo si el aprendiz deja la pantalla abierta y cruza la hora límite, sin necesidad de recargar.

---

## 3. Hábitos obligatorios sin poder desactivar

Se agregó `isOptional` al catálogo mapeado (`PlanHabit.isOptional`) y, en el render, cuando `isOptional === false`: el switch se muestra siempre en ON, deshabilitado (`disabled` de `Switch`), con la etiqueta "OBLIGATORIO" en vez de "ACTIVO/PAUSADO" y un ícono de candado junto al tag de categoría.

### La lista real de `isOptional` NO coincide con los 4 que nombró el dueño

El dueño nombró **4**: AUDIOTERAPIA SEMANAL, Pastilla Renacer, Clase diaria, POST DIARIO EN COMUNIDAD.

`GET /api/v1/habits` con `X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab` (aprendiz día 37) devuelve **22 hábitos**, de los cuales **21 tienen `isOptional: false`** — es decir, con el campo tal cual está hoy en el catálogo, quedarían bloqueados 21 hábitos, no 4. El único con `isOptional: true` es **"DÍA SIN CELULAR"** (con mayúsculas; nótese que existe también un duplicado "Dia sin celular" en minúsculas con `isOptional: false` — parece dato de prueba/legado, no se investigó más porque no era parte del encargo).

Lista completa devuelta por el catálogo:

| Hábito | `isOptional` |
|---|---|
| AGUA E HIDRATACIÓN (domingo) | false |
| AGUA TIBIA CON LIMÓN | false |
| AUDIOTERAPIA SEMANAL | false |
| Beber 2L de agua | false |
| Clase diaria | false |
| DESCANSO PROFUNDO | false |
| DESPERTAR | false |
| Dia sin celular | false |
| **DÍA SIN CELULAR** | **true** |
| DORMIR | false |
| ESCRITURA LIBRE NOCTURNA | false |
| JUGO VERDE | false |
| Pastilla Renacer | false |
| POST DIARIO EN COMUNIDAD | false |
| PRIMERA COMIDA (ROMPO EL AYUNO) | false |
| RITUAL DE MAÑANA (domingo) | false |
| RITUAL TIERRA - AGUA - FUEGO (mañana) | false |
| RITUAL TIERRA - AGUA - FUEGO (mediodía) | false |
| RITUAL TIERRA - AGUA - FUEGO (noche) | false |
| Santuario nocturno A | false |
| Santuario nocturno B | false |
| ÚLTIMA COMIDA DEL DÍA | false |

**Siguiendo la instrucción explícita del encargo** ("si no coinciden, usá el campo igual y dejá la diferencia anotada para que el dueño decida — nunca una lista de títulos a mano"), la implementación usa `isOptional` tal cual viene del backend. **Consecuencia real y visible en la app: con los datos de hoy, 21 de los 22 hábitos del plan aparecen con el interruptor bloqueado en ON, no solo los 4 que se nombraron.** Si la intención real era bloquear solo esos 4, hace falta que el backend corrija los datos de catálogo (`isOptional` en `renaser.habitos` o como se llame la tabla) para que sea `false` únicamente en esos 4 y `true` en el resto — el frontend ya está listo para reflejarlo apenas el dato cambie, sin tocar código de nuevo.

---

## 4. Respetar el día del hábito — NO implementado, confirmado como falta de backend

Se verificó con `curl` que **ninguno de los dos endpoints que usa Plan** (`GET /api/v1/habits`, `GET /api/v1/habit-preferences`) trae `tipo_dia` ni los días de la semana. Los campos que sí devuelven ambos están completos más arriba en este informe (bloque de la sección 3 y el JSON pegado al principio del trabajo).

Como pista adicional, se probó `GET /api/v1/habit-tracks/today` (que Plan hoy **no** consume) y ese sí trae `tipoDia` — pero solo para el hábito de **hoy**, no para toda la semana:

```
$ curl -s -H "X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab" http://localhost:8080/api/v1/habit-tracks/today
```
Devolvió 15 hábitos (no 22): los 2 hábitos "(domingo)" — AGUA E HIDRATACIÓN (domingo) y RITUAL DE MAÑANA (domingo) — **no aparecen**, porque hoy es miércoles (confirma que el backend sí filtra por tipo de día al generar los tracks del día, solo que no expone ese criterio a quien arma la vista semanal). Los 15 que sí aparecen traen `"tipoDia":"DISCIPLINA"` en todos los casos.

También se revisó `PUT /api/v1/weekly-habit-days/{habitId}` (`WeeklyHabitDayController.java`) buscando pistas de la forma del dato: ese endpoint sirve para que el aprendiz **elija** en qué día de la semana hacer un hábito tipo `DISCIPLINA` (recibe solo una `date`, no hay `GET` para leer la elección ya hecha). Confirma que el concepto existe en el dominio, pero no resuelve el problema de "traer el `tipo_dia` de los 22 hábitos del catálogo para pintar el selector semanal".

**No se adivinó** ninguna regla a partir del título (ej. buscar "(domingo)" en el string), tal como pidió el encargo — esa heurística se rompe apenas alguien renombra un hábito, y de hecho ya hay un comentario en `habitsMappers.ts` (escrito antes de este encargo, en el mismo día) documentando exactamente este mismo hueco y la misma decisión de no adivinar. Se dejó ese comentario tal cual, porque ya describe correctamente el estado real. El selector de días semanales de Plan sigue mostrando los 7 días activos por defecto (`TODOS_LOS_DIAS`), sin cambios.

Ver el detalle de qué hace falta construir en `## Falta en el backend`.

---

## 5. Orden de los hábitos — ya estaba resuelto

Se revisó `usePlanHabitos.ts` antes de tocar nada: el `recargar()` del hook ya ordena el catálogo completo con `.sort((a, b) => a.time.localeCompare(b.time))` **antes** de que `PlanScreen` lo separe en mañana/tarde/noche por `filter()`. Como `filter()` preserva el orden relativo del arreglo de origen, el resultado dentro de cada bloque es:

- Los hábitos **con** hora de disparo quedan ordenados ascendente dentro de su bloque (mañana: 00:00–11:59, tarde: 12:00–17:59, noche: 18:00–23:59 — cortes ya definidos en `aMomento()`).
- Los hábitos **sin** hora de disparo (`time === ''`) quedan agrupados al **principio** del bloque "mañana" (es el único bloque donde pueden caer, por el valor por defecto de `aMomento(null)`), porque una cadena vacía ordena antes que cualquier hora en comparación de strings.

Este criterio ya estaba **documentado** en el JSDoc del hook ("Los hábitos sin hora quedan al principio, no al final: son los que el aprendiz todavía no ubicó en su jornada y conviene que los vea"), escrito el mismo día que se conectó Plan al backend. Se lo dejó tal cual porque es un criterio razonable y ya deliberado, y no había ningún defecto que corregir.

**Único ajuste hecho, relacionado con el punto 1:** antes, un hábito sin hora mostraba el `TextInput` vacío (una caja en blanco, ambigua). Con el selector nuevo, se muestra el texto **"Sin horario"** en el botón de hora en su lugar, para que la agrupación "al principio, sin hora" sea legible y no una caja vacía sin explicación.

---

## Lista de verificación con `curl` (comandos y salidas usadas)

```
$ curl -s -H "X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab" http://localhost:8080/api/v1/habits
# 22 hábitos — ver tabla completa en §3

$ curl -s -H "X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab" http://localhost:8080/api/v1/habit-preferences
{"habits":[{"habitId":"3a922b73-...","title":"AGUA E HIDRATACIÓN (domingo)","triggerTime":null,"limitTime":null,"customized":false,"pendingChange":null}, ...
 ...{"habitId":"a344681d-...","title":"Pastilla Renacer","triggerTime":"07:00:00","limitTime":"12:00:00", ...},
 ...{"habitId":"bb0b1cd0-...","title":"AUDIOTERAPIA SEMANAL","triggerTime":"07:00:00","limitTime":"23:55:00", ...}],
 "scheduleEdits":{"used":0,"remaining":3,"limit":3,"period":"WEEK"}}

$ curl -s -H "X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab" http://localhost:8080/api/v1/habit-tracks/today
# 15 de 22 hábitos (excluye los "(domingo)" porque hoy es miércoles) — todos con "tipoDia":"DISCIPLINA"

$ curl -s -i -X OPTIONS -H "X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab" http://localhost:8080/api/v1/users/me
Allow: PATCH,POST,OPTIONS

$ curl -s -i -X OPTIONS -H "X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab" http://localhost:8080/api/v1/users/me/trainee-profile
Allow: PATCH,OPTIONS

$ curl -s -H "X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab" http://localhost:8080/api/v1/onboarding/activate-program
{"activated":true,"validStartDates":[]}

$ curl -s -i -X OPTIONS -H "X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab" http://localhost:8080/api/v1/weekly-habit-days/3a922b73-6d11-48f9-89c2-abc453ca6ef7
Allow: PUT,OPTIONS

$ curl -s -H "X-Actor-Id: 256090d6-3be1-4326-b8d0-4b6a11190175" http://localhost:8080/api/v1/habit-preferences
# aprendiz día 0: misma forma, mismo catálogo — usado solo para confirmar que la forma de la
# respuesta no cambia entre aprendices
```

---

## `npx tsc --noEmit`

```
$ cd "C:\Diseño Opusplan tab 01\renaser-rn\renaser" && npx tsc --noEmit
(sin salida — 0 errores)
```

Corrió limpio en cero errores. En la primera pasada apareció un error real (`PlanHabit` en `handleSaveNewHabit` — el objeto literal del formulario "Crear Hábito" — no tenía los dos campos nuevos `limitTime`/`isOptional`); se corrigió agregándolos (`limitTime: null, isOptional: true`, coherente con que un hábito creado a mano por el aprendiz nunca es obligatorio del programa ni vence) y se volvió a correr hasta quedar en cero.

---

## Qué NO se tocó (fuera de alcance, tal como pidió el encargo)

- `src/screens/TrainingScreen.tsx`, `ComunidadScreen.tsx`, `RootNavigator.tsx`, `src/features/auth/`, `src/features/training/`, `src/features/community/` — no se abrieron.
- Colores, tipografías, layout general de Plan — no se tocaron; los cambios son estructurales (selector, bloqueos, orden), no estéticos.
- No se llamó a `./mvnw` ni se tocó código del backend Java en ningún momento — todas las verificaciones fueron `curl` de solo lectura (con una excepción deliberada: un PATCH con cuerpo vacío a `/api/v1/habit-preferences/{id}` para confirmar que el endpoint exige los dos campos y no acepta un body incompleto; devolvió 400, no modificó nada).
- `openMoveMomentDrawer` (mover hábito de momento) sigue sin tener ningún `onLongPress` que lo dispare en la tarjeta del hábito — es así desde antes de este encargo (función definida pero nunca invocada), no es parte de los 5 puntos pedidos, no se tocó.

---

## Falta en el backend

Para cerrar el hueco real que quedó (puntos 2 y 4), hace falta construir del lado de Java:

1. **Exponer la zona horaria del aprendiz en algún GET que el frontend ya pueda pedir en el arranque de la app** — hoy `participantes_programa.timezone` existe en la base (confirmado por el Javadoc de `ParticipacionPrograma.java`) pero ningún controller de `users` lo serializa. Candidato natural: agregar `timezone` a la respuesta de `GET /api/v1/onboarding/activate-program` (ya es un GET que la app pide temprano) o crear el `GET /api/v1/users/me` que hoy no existe (el método no está soportado — se confirmó con `OPTIONS`, devuelve `Allow: PATCH,POST,OPTIONS`). Mientras tanto, el frontend sigue usando la hora del dispositivo como aproximación (§2), lo que puede fallar si el aprendiz tiene mal configurada la zona horaria de su teléfono.

2. **Exponer `tipo_dia` (`horarios_habito.tipo_dia`: `TODOS`/`DISCIPLINA`/`DOMINGO`) y los días elegidos (`dias_semanales_habito`) en `GET /api/v1/habits` o en `GET /api/v1/habit-preferences`** — hoy ninguno de los dos los trae. Sin esto, el selector semanal de Plan no puede filtrar correctamente qué hábito corresponde a qué día, y sigue mostrando los 7 días activos por defecto para todos los hábitos (incluidos los que en realidad son solo de domingo, como "AGUA E HIDRATACIÓN (domingo)" y "RITUAL DE MAÑANA (domingo)"). Ya existe la pieza para *elegir* el día en hábitos `DISCIPLINA` (`PUT /api/v1/weekly-habit-days/{habitId}`), pero falta la de **leer** qué día quedó elegido, y falta el `tipo_dia` de los hábitos `DOMINGO` en el catálogo/preferencias.

3. **Revisar el dato de `isOptional` del catálogo** — ver §3. Hoy 21 de 22 hábitos son `isOptional: false`, y el dueño del producto nombró explícitamente solo 4 como "no se pueden desactivar". Si la intención es que solo esos 4 estén bloqueados, hay que corregir el dato en el catálogo (probablemente en el seed o en la tabla `habitos`), no en el frontend — el frontend ya usa el campo tal cual venga.

4. Nota menor, no bloqueante: el catálogo trae **"Dia sin celular"** (minúsculas, `isOptional: false`) y **"DÍA SIN CELULAR"** (mayúsculas, `isOptional: true`) como dos hábitos distintos con nombres casi idénticos — probablemente un duplicado de datos de prueba/legado. No se tocó porque no era parte del encargo, pero vale que alguien del lado de datos lo revise.
