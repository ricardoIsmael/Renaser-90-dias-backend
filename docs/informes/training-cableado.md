# Cableado de `TrainingScreen` al backend real

**Fecha:** 2026-09-02
**Repo del cambio:** `C:\Diseño Opusplan tab 01\renaser-rn\renaser` (frontend)
**Archivo tocado:** `src/screens/TrainingScreen.tsx` (único archivo modificado)

## Qué cableé

`TrainingScreen.tsx` tenía cero llamadas al backend: las cinco dimensiones y sus hábitos venían
de `INITIAL_HABITS`, un array escrito a mano. El grueso del trabajo ya estaba hecho en
`src/features/training/` (`types/training.types.ts`, `api/trainingApi.ts`,
`api/trainingSchemas.ts`, `hooks/useTraining.ts`) — mi tarea fue cablear la pantalla a
`useTraining()`.

Cambios en `TrainingScreen.tsx`:

1. **Import y llamada al hook.** `const { habits: habitsDelBackend, loading: cargandoBackend, error: errorBackend } = useTraining();`
2. **Swap de datos con distinción loading/error vs. vacío real** (ver más abajo, es la parte
   delicada de la tarea).
3. **Contadores de evidencia corregidos** para que salgan de `hasEvidence` real y no de `done`
   (ver siguiente sección).

No toqué `PlanScreen.tsx`, `ComunidadScreen.tsx`, `features/habits/`, `features/community/`,
`features/auth/` ni `navigation/`. `git diff -- src/screens/TrainingScreen.tsx` muestra solo
imports, comentarios y lógica de datos — ninguna línea de `StyleSheet`, JSX estructural, color,
margen o layout cambió.

## De dónde sale cada dimensión (recordatorio, ya resuelto por `useTraining`)

- `CUERPO` / `MENTE` / `EMOCIONES` / `ESPÍRITU` ← `GET /api/v1/habit-tracks/today`, agrupados por
  la categoría del hábito en `GET /api/v1/habits` (`BODY→CUERPO`, `MIND→MENTE`,
  `CONSCIENCE→EMOCIONES`, `SPIRIT→ESPÍRITU`).
- `VIDA Y NEGOCIO` ← `GET /api/v1/rocks/today` (la roca del día, eje `TRABAJO`), no un hábito.
- `hasEvidence` sale de `GET /api/v1/evidence`, cruzando `registroHabitoId` (para hábitos) o
  `rocaDiariaId` (para la roca) contra el `id` del track/roca de hoy.

No unifiqué los dos vocabularios (categoría de hábito vs. eje de roca) ni reescribí `useTraining`:
ya resuelve esto correctamente.

## Cómo calculé los contadores de evidencia

El código original usaba el mismo campo (`done`) para las tres cosas que el diseño llama distinto:
"X/Y EVIDENCIAS" (catálogo), "X/Y CUMPLIDOS" (detalle) y "Evidencias selladas hoy" (barra de
progreso del detalle). Con datos reales, `done` (estado del track) y `hasEvidence` (si tiene una
fila en `evidence`) pueden divergir — un hábito puede estar `COMPLETADO` sin evidencia subida, o
viceversa según el flujo de negocio. Separé las dos métricas, sin tocar ningún texto ni JSX:

| Texto del diseño | Antes | Ahora | Justificación |
|---|---|---|---|
| "X/Y EVIDENCIAS" (catálogo, Vista 1) | `dimHabits.filter(h => h.done).length` | `dimHabits.filter(h => h.hasEvidence).length` | El texto dice "evidencias", no "completados" |
| "X/Y CUMPLIDOS" (detalle) | `currentDimensionHabits.filter(h => h.done).length` | sin cambio | El texto dice "cumplidos" = estado `done`, correcto tal cual estaba |
| "Evidencias selladas hoy" (barra de progreso, detalle) | basada en `done` (`completedEvidencesCount`) | basada en `hasEvidence` (`sealedEvidencesCount`, nueva variable) | Misma razón que la primera fila |

Las dos son 100% calculables con lo que devuelve el backend hoy — no inventé ningún número.

## Estados vacíos

Un aprendiz sin hábitos generados o sin roca del día es legítimo (día 0, o plan todavía no
generado), no un error. Lo verifiqué en vivo contra el actor de día 0
(`256090d6-3be1-4326-b8d0-4b6a11190175`): `habit-tracks/today`, `rocks/today` y `evidence`
devuelven `[]` / lista vacía, sin error HTTP.

La trampa real acá era que el patrón de degradación de `PlanScreen` (mostrar `INITIAL_HABITS`
mientras `habitsDelBackend.length === 0`) **no distingue "todavía no cargó" de "cargó y el
aprendiz no tiene nada"** — con ese criterio literal, un aprendiz de día 0 vería para siempre los
hábitos de relleno (`Entrenamiento Somático`, `Bloque de Poder Deep Work`, etc.), que es
exactamente el bug que el dueño pidió evitar.

Por eso el `useEffect` de cableado usa `loading` y `error` (ambos ya expuestos por `useTraining`),
no el largo del array:

```ts
useEffect(() => {
  if (!cargandoBackend && !errorBackend) {
    setHabits(habitsDelBackend);
  }
}, [cargandoBackend, errorBackend, habitsDelBackend]);
```

Con esto:
- **Mientras carga:** `habits` sigue en `INITIAL_HABITS` (relleno) — la pantalla no parpadea vacía.
- **Cargó bien, vacío real:** `habits` pasa a `[]`. Cada dimensión cae sola en su vacío ya
  contemplado por el JSX existente: `dimHabits.length === 0` → "0/0 EVIDENCIAS" en el catálogo;
  en el detalle, `currentDimensionHabits.map(...)` no renderiza ninguna card y
  "PRÁCTICAS ACTIVAS (0)" queda en cero. No hay ningún mensaje de fallo porque no hay ninguna rama
  de error en el JSX que se dispare — el vacío se ve como "todavía no hay nada", que es el vacío
  correcto.
- **Backend no responde:** `errorBackend` queda seteado, el efecto no pisa el estado, y
  `habits` se queda en `INITIAL_HABITS` — la pantalla no se rompe ni queda en blanco.

No agregué ningún componente de "estado vacío" nuevo porque el diseño no tiene uno explícito para
Training (a diferencia de otras pantallas): el vacío natural de las listas ya cumple "nunca un
mensaje de fallo".

## Qué NO se pudo calcular con datos reales (y quedó como estaba)

- **`streak` (🔥 X DÍAS) por hábito.** No hay endpoint de racha por hábito/roca expuesto a esta
  pantalla; `useTraining` ya lo deja en `0` explícitamente con ese criterio. Con datos reales, cada
  card va a mostrar "🔥 0 DÍAS" hasta que exista un endpoint de racha.
- **Banner "Consistencia de la Dimensión" (37 días · 94%).** Es texto fijo en el JSX, no depende de
  ningún hábito ni de ninguna variable — no hay dato real detrás en ningún endpoint disponible. Lo
  dejé sin tocar porque no es parte de la capa de datos que se pidió cablear (no itera sobre
  `habits` ni sobre ningún estado), y tocarlo habría significado inventar un número.
- **Modal de evidencia: "Sello automático: Día 37 · 07:45 AM".** Mismo caso: texto fijo, no
  proviene de ningún campo de `HabitItem` ni de la respuesta de `evidence`.
- **Pestaña "Guías y Audios" (clase recomendada, ruta de clases fase 2).** Fuera de alcance de esta
  tarea — no hay ningún endpoint de `training` para eso, y la tarea pedía cablear hábitos/roca, no
  el catálogo de audios.
- **Acciones de escritura** (marcar hábito como hecho, sellar evidencia con foto, crear hábito
  personalizado desde Training): `useTraining` solo expone `recargar` (lectura). No hay endpoint de
  escritura en `trainingApi.ts` para estas acciones desde esta pantalla, así que
  `toggleHabitState`, `handleSealEvidence` y `handleCreateHabit` siguen operando en el estado local
  de React tal como estaban — no persisten al backend. Esto es consistente con el alcance pedido
  ("reemplazar los datos estáticos por los reales" en la capa de lectura), pero lo dejo explícito:
  si el aprendiz marca algo como hecho en Training hoy, ese cambio no sobrevive un refresh ni se ve
  reflejado en Plan/Rocas.

## Verificación contra el backend real

```
curl -s "http://localhost:8080/api/v1/habit-tracks/today" -H "X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab"
```
→ 16 tracks de hoy, todos `"estado":"PENDIENTE"`, categorías BODY/MIND/SPIRIT presentes en el
catálogo cruzado (`GET /api/v1/habits`).

```
curl -s "http://localhost:8080/api/v1/rocks/today" -H "X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab"
```
→ `[]` — este aprendiz tampoco tiene roca planificada hoy; "VIDA Y NEGOCIO" cae en vacío real
igual que si fuera el aprendiz de día 0. Confirma que el vacío de roca es un caso más común de lo
que el enunciado sugería, y que la corrección loading/error-vs-vacío del punto anterior aplica
también a un aprendiz con hábitos.

```
curl -s "http://localhost:8080/api/v1/evidence" -H "X-Actor-Id: af4984b2-bf75-4b56-8f1a-f8d6b957d2ab"
```
→ `{"evidencias":[],"nextCursor":null}` — sin evidencia subida hoy, todas las cards mostrarían
`hasEvidence: false`.

```
curl -s "http://localhost:8080/api/v1/habit-tracks/today" -H "X-Actor-Id: 256090d6-3be1-4326-b8d0-4b6a11190175"
curl -s "http://localhost:8080/api/v1/rocks/today" -H "X-Actor-Id: 256090d6-3be1-4326-b8d0-4b6a11190175"
curl -s "http://localhost:8080/api/v1/evidence" -H "X-Actor-Id: 256090d6-3be1-4326-b8d0-4b6a11190175"
```
→ Los tres, vacíos (`[]` / `{"evidencias":[],"nextCursor":null}`), sin error HTTP — confirma que
el aprendiz de día 0 es un 200 con listas vacías, no un 4xx/5xx, y que el criterio
loading/error-vs-vacío del cableado es el correcto para este caso.

## `npx tsc --noEmit`

```
$ npx tsc --noEmit
exit code: 0
```

Cero errores, sin salida.

## Riesgos que le dejo a quien verifique

1. **`useTraining` no memoiza `habitosConEvidencia`/`rocasConEvidencia` entre renders** ni
   deduplica llamadas — cada `recargar()` vuelve a pedir las 4 fuentes. No es un problema nuevo que
   yo haya introducido (así estaba el hook), pero al cablearlo a la pantalla pasa a ejecutarse cada
   vez que se monta `TrainingScreen`; si la navegación remonta la pantalla seguido, vale la pena
   revisar si conviene cachear.
2. **`GET /api/v1/habit-tracks/today` puede escribir en el servidor** (genera los tracks del día si
   no existen, según el comentario de `habitsApi.ts`). Cablear Training para que lo llame en cada
   montaje significa que abrir esta pantalla puede generar tracks aunque el aprendiz nunca haya
   abierto Plan — no debería ser un problema funcional (es el mismo endpoint, idempotente por
   diseño), pero es un efecto colateral que antes no ocurría porque Training no llamaba a nada.
3. **El vacío de "VIDA Y NEGOCIO"** no tiene, en este backend de prueba, ningún aprendiz con roca
   planificada para hoy — no pude verificar en vivo cómo se ve la pantalla con datos reales de roca
   presentes, solo con roca ausente. La lógica de mapeo (`useTraining.ts`, líneas 85-95) se ve
   correcta por inspección contra `RocaDiariaApi`, pero el camino "con roca" quedó sin probar contra
   una respuesta real no vacía.
4. **Los contadores "EVIDENCIAS" vs. "CUMPLIDOS" vs. "Evidencias selladas hoy"** ahora usan dos
   métricas reales distintas (`hasEvidence` y `done`) según lo que dice cada texto — es una lectura
   razonable del diseño, pero es una decisión mía, no algo confirmado por el dueño. Si la intención
   original era que las tres mostraran lo mismo, es un cambio de un archivo (`TrainingScreen.tsx`,
   las tres líneas marcadas en el diff) revertir a usar `done` en las tres.
5. **`toggleHabitState`, `handleSealEvidence` y `handleCreateHabit` no persisten al backend** (ver
   sección anterior) — cualquiera que pruebe la pantalla tocando un checkbox va a ver el cambio
   local, pero no sobrevive un refresh. No es un bug de este cableado: es explícitamente el límite
   del alcance pedido (solo lectura), pero es fácil de confundir con "no funciona" al probar a mano.
