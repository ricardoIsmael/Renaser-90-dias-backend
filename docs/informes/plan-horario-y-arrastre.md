# Plan: horario automático y arrastre de hábitos

**Fecha:** 2026-09-02
**Alcance:** `PlanScreen.tsx` y `features/habits/` en `renaser-rn/renaser` (frontend). No se tocó backend.
**Pedido del dueño:** que cambiar la hora de un hábito lo mueva solo al bloque del día correcto (sin
rangos manuales), y poder arrastrar y soltar hábitos para reordenarlos rápido. Público 40+: priorizar
lo obvio.

---

## 1. El bug: cambiar la hora no movía el hábito de bloque

**La hipótesis era correcta, pero el archivo/función exactos no eran los que decía el encargo.**

El encargo apuntaba a `cambiarHora` en `src/features/habits/hooks/usePlanHabitos.ts`. Esa función
tenía exactamente el bug descrito (`{ ...h, time: hora }` sin recalcular `moment`), **pero no es la
que usa la pantalla**: `PlanScreen.tsx` no llama a `usePlanHabitos().cambiarHora` en ningún lado — solo
consume `habits` del hook y maneja el guardado con su propia función local. El bug real, el que el
dueño ve en pantalla, estaba en:

- **`src/screens/PlanScreen.tsx`, función `updateHabitTime`** (línea ~349 antes del fix) — la que
  `guardarNuevaHora` llama al confirmar el `HoraPickerModal`. Hacía `{ ...h, time: newTime }`, sin
  tocar `moment`. Como la lista se agrupa filtrando por `h.moment === momentName`, el hábito
  quedaba en la sección vieja aunque su hora nueva fuera de otro bloque.

**Arreglado en los dos lugares**, reusando `aMomento(...)` de `habitsMappers.ts` tal cual pedía el
encargo (no se reescribió):

- `PlanScreen.tsx`: `updateHabitTime` ahora hace
  `{ ...h, time: newTime, moment: aMomento(newTime) }`. Este es el fix que de verdad cambia el
  comportamiento que ve el dueño.
- `usePlanHabitos.ts`: `cambiarHora` recibió el mismo arreglo. **Hoy sigue sin estar conectada a
  `PlanScreen`** (nadie la importa desde ahí), así que no cambia nada visible todavía — se corrigió
  para que quede bien si en algún momento se conecta, no porque estuviera causando el bug reportado.

Con el fix, al confirmar una hora en el selector el hábito salta de inmediato a la sección
correspondiente (mañana/tarde/noche), sin recargar la pantalla — es la actualización optimista que
ya existía, ahora con el bloque recalculado.

### 1.1 Lo que "no funciona" en un sentido más amplio: hallazgo más grave que el bug de UI

Verificando el camino completo con `curl` contra el backend real apareció un problema **más serio
que el de `moment`, y que mi fix no soluciona porque está en el backend**, fuera de mi alcance:

`PATCH /api/v1/habit-preferences/{habitId}` exige que **tanto `triggerTime` como `limitTime` sean
no nulos**:

```java
// src/main/java/.../habits/infrastructure/adapter/in/rest/preferencia/UpdateHabitPreferenceRequest.java:8
public record UpdateHabitPreferenceRequest(@NotNull LocalTime triggerTime, @NotNull LocalTime limitTime, ...)

// src/main/java/.../habits/application/ports/in/preferencia/EditarPreferenciaHorarioUseCase.java:29-30
record EditarPreferenciaHorarioCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId,
                                        @NotNull LocalTime horaDisparo, @NotNull LocalTime horaLimite, ...)
```

El frontend (`habitsApi.cambiarHorario`, y `guardarNuevaHora` en `PlanScreen.tsx`) hace exactamente
lo que el propio comentario del código dice que hay que hacer: reenvía el `limitTime` que el hábito
ya tenía, para no borrarlo. El problema es que **la mayoría de los hábitos del catálogo real no
tienen `limitTime`** (es `null` legítimamente — ver §1.2). Para esos, cualquier cambio de hora
manda `limitTime: null` de vuelta, y el backend lo rechaza con 400 siempre, sin excepción.

**Verificado en vivo** (aprendiz de prueba día 0, `256090d6-...`):

```
$ curl -s -X PATCH ".../habit-preferences/66507383-...-72a9..." \
    -H "X-Actor-Id: 256090d6-3be1-4326-b8d0-4b6a11190175" \
    -d '{"triggerTime":"22:00:00","limitTime":null}'
→ 400 {"message":"El cuerpo de la solicitud es invalido o esta mal formado"}

$ curl -s -X PATCH ".../habit-preferences/d2d58e66-...-30b380f68b73" \
    -H "X-Actor-Id: 256090d6-3be1-4326-b8d0-4b6a11190175" \
    -d '{"triggerTime":"07:15:00","limitTime":"23:59:00","reminderEnabled":false}'
→ 200 { "triggerTime":"07:15:00", "limitTime":"23:59:00", ... }   (el hábito SÍ tenía limitTime propio)
```

(Este segundo cambio se revirtió después con otro PATCH, a `06:30:00`, para no dejar dato de prueba
modificado.)

**Impacto real, contado sobre el catálogo del aprendiz de referencia (`af4984b2-...`, 22 hábitos):**
solo **3 de 22** (AUDIOTERAPIA SEMANAL, DÍA SIN CELULAR, Pastilla Renacer) tienen `limitTime` propio
— son los únicos a los que hoy se les puede cambiar la hora con éxito. Los otros **19 de 22 (86%)**
van a fallar con 400 siempre que se les toque la hora, sin importar el fix de `moment`.

**No lo arreglé porque está en el backend y la regla no lo tengo (rule 0.6 de `CLAUDE.md`): no sé si
la corrección correcta es (a) que `limitTime` sea opcional en el DTO/comando, o (b) que el frontend
tenga que inventar un `limitTime` por defecto cuando no hay uno — y eso último sería inventar una
regla de negocio.** Lo que sí verifiqué es que el frontend **no empeora la situación**: cuando el
backend rechaza el cambio, `apiFetch` lanza `ApiError`, `guardarNuevaHora` lo captura, revierte
`habits` al estado anterior y muestra un `Alert` — nunca queda mostrando una hora que el servidor no
guardó (requisito del encargo, ya cumplido por el código existente, verificado con el 400 real de
arriba).

**Esto es, con alta probabilidad, la causa de fondo de "no funciona" que reporta el dueño** — más
que el bug de `moment` (que sí era real, pero solo se nota si el aprendiz mira qué sección ocupa el
hábito; el 400 en el 86% de los casos hace que el cambio directamente no se guarde nunca, con o sin
el bug de UI).

### 1.2 "Sin horario": correcto, no es un síntoma

Con `curl` contra `GET /api/v1/habit-preferences` en los dos aprendices de prueba:

```
$ curl -s ".../habit-preferences" -H "X-Actor-Id: af4984b2-..."
```

De los 22 hábitos del catálogo, **8 a 10 tienen `triggerTime: null`** de forma consistente en ambos
aprendices (AGUA E HIDRATACIÓN domingo, Beber 2L de agua, DESCANSO PROFUNDO, DESPERTAR, Dia sin
celular, RITUAL DE MAÑANA domingo, Santuario nocturno A y B, y para el aprendiz día 0 también
AUDIOTERAPIA SEMANAL y Pastilla Renacer). Es dato real del catálogo/preferencias, no un efecto de
mapeo roto del lado del cliente: **"Sin horario" es correcto tal como está la base hoy**, no un
síntoma del bug. Si es deseable que todo hábito tenga una hora por defecto es una decisión de
producto/backend, fuera de este alcance.

### 1.3 Persistencia

Confirmada con `GET` después del `PATCH` exitoso (§1.1, segundo ejemplo): el cambio sobrevive, no es
solo un efecto visual hasta el próximo refresh.

---

## 2. Arrastrar y soltar: análisis y qué se construyó

### 2.1 La pregunta que pedía el encargo, respondida

Con `moment` derivado de la hora (fix de §1), arrastrar un hábito de "mañana" a "noche" solo puede
significar una de dos cosas, y **elegí no decidir por mi cuenta cuál**:

- **(a) Arrastrar cambia la hora.** Soltar en "noche" le asigna una hora de la noche. Coherente con
  el modelo nuevo, pero **¿qué hora exactamente?** ¿La hora actual del dispositivo? ¿Un valor fijo
  por bloque (ej. 20:00)? ¿La media del bloque? Es una regla de negocio que nadie confirmó, y
  `CLAUDE.md` §0.6 prohíbe inventarla. Además, choca de lleno con el hallazgo de §1.1: si el hábito
  no tiene `limitTime` propio (86% de los casos), el `PATCH` resultante del "soltar" fallaría con
  400 igual que falla hoy al usar el selector de hora — el arrastre heredaría el mismo bloqueo.

- **(b) Arrastrar solo reordena dentro del bloque, sin tocar la hora.** No pisa la regla de "la hora
  manda", pero **el orden dentro de cada bloque hoy sale de ordenar por hora**
  (`usePlanHabitos.ts`, `.sort((a, b) => a.time.localeCompare(b.time))`) — no existe un campo de
  "orden manual" en ningún lado. Un reorden así **no tiene dónde persistir**: no hay
  `PATCH .../order` ni campo equivalente en `HabitoCatalogoApi`/`PreferenciaHabitoApi`. Se perdería
  al primer refresh, y peor: el listado se re-ordena automáticamente por hora cada vez que
  `usePlanHabitos.recargar()` corre, así que el reorden manual se pisaría solo aunque no hubiera
  refresh de por medio.

**Ninguna de las dos se puede construir bien hoy** sin (a) que el dueño defina la regla de qué hora
asigna cada bloque —y probablemente primero corrija el bloqueo de §1.1—, o (b) sin que el backend
sume un endpoint/campo de orden manual. Construir cualquiera de las dos ahora sería, en el mejor
caso, un gesto que no persiste (exactamente lo que el encargo pidió evitar), y en el peor, un
`PATCH` que siempre falla por el bug de §1.1.

**Pregunta para el dueño: ¿el bloque de un hábito se define por su hora exacta (opción a — y en ese
caso, qué hora asignar por bloque), o el orden dentro de un bloque debe poder fijarse a mano,
independiente de la hora (opción b — y en ese caso hace falta un endpoint nuevo de orden)?**

### 2.2 Un hallazgo adicional: ya existe un mecanismo que contradice el modelo nuevo, y está muerto

`PlanScreen.tsx` tiene un drawer completo — estado `moveMomentModalVisible`, función
`openMoveMomentDrawer`, función `applyMomentChange`, y el `Modal` "REUBICAR HÁBITO" (líneas ~281,
380, 385, 1000 en el archivo actual) — que deja **elegir el `moment` directamente, sin tocar la
hora** (`{ ...h, moment: targetMoment }`). Es justo el patrón opuesto al que pide el dueño ahora
("no manejar rangos a mano").

**Está desconectado**: no hay ningún `onLongPress` ni `onPress` en el código que llame a
`openMoveMomentDrawer` — verificado con grep en todo `src/`, cero resultados. No lo toqué (no era
parte del pedido y no cambia nada visible hoy, al estar inalcanzable), pero lo dejo señalado: si en
algún momento se reconecta (por ejemplo al reintroducir el long-press), habría que decidir qué hace
con la hora del hábito — hoy la dejaría desincronizada del bloque, reabriendo el mismo bug que
arreglé en §1.

### 2.3 Lo que sí se construyó

Lo sancionado como respaldo seguro por el propio encargo: **que el hábito salte visiblemente al
bloque nuevo al confirmar la hora**. Ya funciona con el fix de §1 — se probó con `tsc`, no hay forma
de correr la app en este entorno (sin Metro/emulador disponible acá), así que **esto queda
pendiente de verificación visual en un dispositivo o simulador real** (ver Riesgos).

El selector táctil (`HoraPickerModal.tsx`) ya existía, ya es rápido y evidente para 40+ (grillas de
botones grandes, sin texto libre) — no lo modifiqué, cumple lo pedido tal cual está.

### 2.4 Lo que no se construyó, y por qué

- **Ningún gesto de arrastrar y soltar**, ni entre bloques ni dentro de un bloque. Motivos:
  1. Las dos interpretaciones posibles están bloqueadas (§2.1) — construir cualquiera sería
     prometer una persistencia que no existe, que el propio encargo pidió evitar explícitamente.
  2. **El encargo asumía que `react-native-gesture-handler` y `react-native-reanimated` ya estaban
     instalados ("verificalo"). Verifiqué `package.json` y NO están** — ni como dependencia directa
     ni transitiva visible. Sumar una librería de gestos nueva (con código nativo) para un gesto que
     de todas formas no tiene dónde persistir hoy es exactamente la clase de riesgo que la regla 5
     del encargo pide evitar cuando no hace falta.

---

## 3. `npx tsc --noEmit`

```
$ npx tsc --noEmit
$ echo $?
0
```

Cero errores, cero salida.

---

## 4. Resumen de cambios de archivo

- `src/screens/PlanScreen.tsx` — `updateHabitTime` recalcula `moment` con `aMomento(newTime)`
  (import agregado desde `features/habits/api/habitsMappers`). Es el único cambio de comportamiento
  de este archivo.
- `src/features/habits/hooks/usePlanHabitos.ts` — `cambiarHora` recibió el mismo arreglo, por
  completitud y porque el encargo apuntaba a esta función explícitamente; hoy no está conectada a
  `PlanScreen`, así que no cambia nada visible todavía.

No se tocó `HoraPickerModal.tsx`, ni ningún color/tipografía/tamaño/estructura de tarjeta. No se
instaló ninguna dependencia nueva. No se corrió `./mvnw` ni se tocó código de backend — los hallazgos
de §1.1 y §1.2 son solo lectura (`curl`) contra el backend ya corriendo.

---

## Riesgos que le dejo a quien verifique

1. **El bloqueo de §1.1 (backend exige `limitTime` no nulo) sigue vivo y es más grave que el bug de
   UI que arreglé.** Con los datos reales de hoy, cambiar la hora de 19 de 22 hábitos del catálogo
   del aprendiz de referencia va a seguir devolviendo 400, con o sin mi fix. El dueño va a seguir
   viendo "no funciona" en la mayoría de los hábitos hasta que esto se resuelva del lado del backend
   (fuera de mi alcance en este encargo) — decidir si `limitTime` pasa a ser opcional en el DTO/
   comando, o si hace falta otra estrategia.
2. **No pude correr la app** (Metro/emulador no disponibles en este entorno) para confirmar
   visualmente el salto de bloque al confirmar la hora. Verificado por lectura de código y por
   `tsc`, no por ejecución real — recomiendo una pasada manual en dispositivo antes de dar el fix
   por cerrado.
3. **El drawer "REUBICAR HÁBITO" (§2.2) sigue en el código, muerto pero intacto.** Si alguien lo
   reconecta sin decidir primero (a) vs (b) de §2.1, reintroduce la desincronización moment/hora que
   este cambio corrigió.
4. **`usePlanHabitos.cambiarHora` quedó arreglada pero sin cobertura real**: nadie la llama desde
   `PlanScreen` hoy, así que el fix ahí es correcto por revisión de código, no por haberlo visto
   correr contra el flujo real de la pantalla.
5. **`scheduleEdits.used` no se incrementó** en mis dos PATCH de prueba contra el aprendiz día 0
   (se mantuvo en `0/3` antes y después). No investigué por qué — puede ser que el aprendiz día 0
   esté antes del corte que activa la cuota semanal (el propio código de backend menciona "antes
   del día 7 de programa... los cambios inmediatos son ilimitados"), lo cual sería consistente y
   no un bug, pero no lo verifiqué a fondo por no ser parte del encargo. Si se prueba el flujo de
   cuota agotada, usar el aprendiz día 37 (`af4984b2-...`, período `"WEEK"` en vez de `"FREE"`) y no
   el de día 0.
6. **Usé el aprendiz `256090d6-...` (día 0) para las pruebas de `PATCH`**, revirtiendo el único
   cambio que persistió (`d2d58e66-...`, DÍA SIN CELULAR, de vuelta a `06:30:00`). Ambos aprendices
   de prueba tienen emails reales (`ricardopalomino2904@gmail.com`,
   `soporterenaser@gmail.com`) — no encontré un tercer actor claramente marcado como "de prueba" en
   la documentación existente; si hay uno más aislado, preferible usarlo en adelante.
