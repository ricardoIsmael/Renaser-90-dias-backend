# Comunidad — mentor asignado y tribu privada

**Fecha:** 2026-09-02
**Alcance:** corregir el 404 de `GET /api/v1/me/cell` cuando el aprendiz no tiene célula, e
integrar la sección MENTOR / TRIBU PRIVADA de la pantalla principal de Comunidad
(`ComunidadScreen.tsx`) contra el backend real. Testimonios no se tocó (instrucción explícita).

---

## 1. Diagnóstico del 404 y qué se cambió

**Dónde salía:** `MiCelulaController.miCelula` (backend), no `GlobalExceptionHandler` — no es una
excepción de dominio traducida, es un `ResponseEntity.status(HttpStatus.NOT_FOUND)` explícito,
escrito a mano en el controller:

```java
// ANTES
return consultarUseCase.miCelula(traineeId)
        .<ResponseEntity<?>>map(mc -> ResponseEntity.ok(MiCelulaResponse.from(mc)))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("assigned", false)));
```

El propio javadoc del controller y del caso de uso (`ConsultarMiCelulaUseCase`) ya documentaban
esto como intencional, heredado del Next.js de origen (`app/api/v1/me/cell/route.ts:32-34`): "sin
célula no es un error". El problema es que el vehículo elegido para expresar "no es un error" fue
un código HTTP que, por convención universal (REST, `fetch`, cualquier cliente HTTP), **sí**
significa error — un cliente que solo mira el status code (o que usa un wrapper que lanza en
cualquier `!response.ok`, como `apiFetch` de la app) no puede distinguir este 404 semántico de un
404 real (ruta mal escrita, endpoint caído, proxy roto).

**El argumento decisivo:** el endpoint hermano, `GET /api/v1/me/cell/members`, resuelve exactamente
el mismo caso (sin célula asignada) con `200 {"members": []}` — ya usaba el criterio correcto. La
inconsistencia entre los dos endpoints del mismo controller era la prueba de que el 404 era un
defecto, no una decisión de diseño.

**Cambio aplicado** (`MiCelulaController.java`):

```java
// DESPUÉS
return consultarUseCase.miCelula(traineeId)
        .<ResponseEntity<?>>map(mc -> ResponseEntity.ok(MiCelulaResponse.from(mc)))
        .orElseGet(() -> ResponseEntity.ok(Map.of("assigned", false)));
```

Se retiró el import ahora no usado (`org.springframework.http.HttpStatus`) y se actualizó el
javadoc del controller y de `ConsultarMiCelulaUseCase` para que digan 200, con una nota fechada
explicando la corrección — mismo criterio que usa `CLAUDE.md` para las correcciones registradas
en el documento.

**Verificación de que nada más dependía del 404 antes de tocarlo:**
- `grep -rn "MiCelulaController\|me/cell\|assigned"` sobre `src/test` → el único test relacionado
  es `CelulaServiceTest`, y opera contra `CelulaService` (capa de aplicación), no contra el
  controller — asserta `Optional.empty()`, no un status HTTP. No se ve afectado por este cambio.
- `grep -rn "me/cell"` sobre el frontend RN (`C:\Diseño Opusplan tab 01\renaser-rn\renaser\src`)
  → cero resultados antes de esta tarea. Ningún consumidor existente dependía del 404; la
  integración que se agrega en esta misma tarea (§3) ya se escribió contra el comportamiento
  correcto (200), con una rama de compatibilidad hacia atrás para el 404 mientras el backend en
  ejecución no se recompile (ver §3).
- Documentación actualizada en el mismo cambio (regla 0.4 de `CLAUDE.md`): `docs/api/
  CONTRATO_COMUNIDAD.md` §6.1 y `docs/MODULO_COMMUNITY.md` (nota CM-01) ya no describen 404 como
  el comportamiento vigente del backend Java; queda documentado como el comportamiento original
  del Next.js de origen, con la corrección fechada.

**Archivos tocados en el backend:**
- `src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/celula/MiCelulaController.java`
- `src/main/java/com/renaser/os/community/application/ports/in/celula/ConsultarMiCelulaUseCase.java`
  (solo javadoc)
- `docs/api/CONTRATO_COMUNIDAD.md`
- `docs/MODULO_COMMUNITY.md`

**No se tocó** ningún test — no había ninguno que asertara el status code del controller.

---

## 2. Forma real de las dos respuestas (verificado con `curl` contra el backend en `localhost:8080`)

El servidor sigue corriendo el código de **antes** del fix (recompilación pendiente, a cargo de
quien encargó la tarea), así que las llamadas de abajo muestran el 404 viejo — es el
comportamiento esperado hasta que se recompile.

### Aprendiz sin célula (`af4984b2-bf75-4b56-8f1a-f8d6b957d2ab`)

```
GET /api/v1/me/cell            → HTTP 404   {"assigned":false}
GET /api/v1/me/cell/members    → HTTP 200   {"members":[]}
```

Mismo resultado con el admin de prueba (`00000000-0000-0000-0000-000000000001`, tampoco tiene
célula).

### Aprendiz CON célula y mentor asignados

Se ubicó un caso real navegando `GET /api/v1/admin/cohorts` → `GET /api/v1/admin/cells?cohortId=…`
→ `GET /api/v1/admin/cells/{id}` (cohorte "Cohorte Humo", célula "Celula Humo", mentor "Mentor De
Prueba", 1 integrante: `11111111-1111-1111-1111-111111111111`):

```
GET /api/v1/me/cell         (actor: 11111111-1111-1111-1111-111111111111)
→ HTTP 200
{
  "cellId": "bbbbbbbb-0000-0000-0000-0000000ce101",
  "cellName": "Celula Humo",
  "cohortName": "Cohorte Humo",
  "cohortStatus": "ACTIVE",
  "mentorName": "Mentor De Prueba",
  "mentorAvatarUrl": null,
  "memberCount": 1,
  "totalCellsInCohort": 2,
  "videoCallUrl": null,
  "nextSessionAt": null
}

GET /api/v1/me/cell/members  (mismo actor)
→ HTTP 200
{
  "members": [
    { "traineeId": "11111111-1111-1111-1111-111111111111", "fullName": "Nuevo Nombre Aprendiz",
      "avatarUrl": null, "isSelf": true }
  ]
}
```

Esta es la forma real que se usó para escribir los esquemas `zod` del frontend — no se adivinó por
el nombre de los DTOs.

---

## 3. Integración en el frontend

Pantalla: `src/screens/ComunidadScreen.tsx` (sección MENTOR / TRIBU PRIVADA de la vista principal,
la que no está detrás de "Eventos & Experiencias"). Se siguió el patrón ya establecido por
`features/community/{api,hooks,types}` (Muro) y `features/academy`: capa `api/` con `apiFetch` +
validación `zod`, `types/` con las formas del wire, `hooks/` con `loading`/`error`/`recargar`.

**Archivos creados:**

- `src/features/community/api/celulaSchemas.ts` — esquemas `zod` para las dos formas de
  `GET /api/v1/me/cell` (`{assigned:false}` vs. la célula completa, distinguidas por el campo
  `assigned` literal) y para `GET /api/v1/me/cell/members`. `passthrough()` en todos, mismo
  criterio que `wallSchemas.ts`.
- `src/features/community/api/celulaApi.ts` — `obtenerMiCelula()` y `obtenerMisCompaneros()`.
  `obtenerMiCelula()` contempla **las dos formas** del "sin célula" (200 nuevo y 404 viejo): si
  `apiFetch` lanza `ApiError` con `status === 404` y cuerpo `{assigned:false}`, se trata igual que
  el 200 — así la integración funciona ya mismo contra el backend en ejecución (con el defecto
  todavía activo) y sigue funcionando sin cambios el día que se recompile con el fix de §1.
- `src/features/community/hooks/useMiCelula.ts` — pide las dos llamadas en paralelo
  (`Promise.all`), expone `{ miCelula, miembros, loading, error, recargar }`.

**Archivo editado (solo capa de datos):**

- `src/features/community/types/community.types.ts` — se agregaron `CellMember` y `MiCelulaInfo`
  (unión discriminada por `assigned`), al final del archivo, sin tocar los tipos del Muro ya
  existentes.
- `src/screens/ComunidadScreen.tsx`:
  - Import de `useMiCelula`.
  - Llamada al hook y variables derivadas (`mentorTitulo`, `mentorSubtitulo`, `mentorNota`,
    `tribuVisibles`, `tribuRestantes`), agregadas junto a los otros derivados de la pantalla, sin
    tocar nada del bloque de estados de navegación.
  - En el JSX de la sección **MENTOR**: los tres `Text` que antes tenían el mock fijo
    ("Sebastián Arango" / "Mentor de Alto Rendimiento" / la cita fija) ahora muestran el dato
    real; el nombre del mentor si hay uno asignado, o **"Todavía no tienes un mentor asignado"**
    (la frase pedida, literal) si no. Los `style` de esos `Text` no cambiaron ni un valor; los dos
    secundarios pasaron a ser condicionales (`{mentorSubtitulo && <Text ...>}`) para no mostrar un
    subtítulo/nota fabricados cuando no hay mentor.
  - En el JSX de **TRIBU PRIVADA**: los 4 `Placeholder` fijos + el "+12" fijo se reemplazaron por
    un `.map` sobre los integrantes reales (hasta 4 visibles) y un "+N" calculado con el resto,
    usando exactamente los mismos componentes (`Placeholder`, la `View` de `styles.more`) y las
    mismas medidas (`avatarSize`) que ya existían. Se agregaron tres líneas de texto de
    carga/vacío con los mismos tokens (`t.micro`, `c.textSoft`) que ya usa el resto del archivo
    para los mismos estados (ver el bloque de `muroCargando`/`muroError` un poco más abajo en el
    mismo archivo) — no se inventó un token ni un color nuevo.
  - No se tocó ningún otro `StyleSheet`, color, tamaño, margen ni el orden de ningún otro bloque
    del archivo.

`git diff -- src/screens src/components` confirma que el único archivo de pantalla que cambió por
esta tarea es `ComunidadScreen.tsx`, y que el diff son solo strings/condicionales sobre datos —
cero líneas de `style`, `StyleSheet.create`, color o layout tocadas. (El diff también muestra
cambios en `PlanScreen.tsx`: son de otro agente trabajando en `habits`, no de esta tarea — no se
tocó ese archivo acá.)

---

## 4. `npx tsc --noEmit`

```
[exited with code 0]
```

Sin salida — cero errores de tipos.

---

## Para la bitácora

- **Síntoma:** `GET /api/v1/me/cell` respondía `404 Not Found` con cuerpo `{"assigned":false}`
  cuando el aprendiz no tenía célula asignada. El cuerpo era correcto; el status code no.
- **Causa real:** decisión de diseño heredada literal del Next.js de origen
  (`app/api/v1/me/cell/route.ts:32-34`), portada tal cual sin notar que en Java, con un cliente
  que usa un wrapper `fetch` que lanza en cualquier `!response.ok` (como `apiFetch` de la app RN),
  ese 404 es indistinguible de un error real.
- **Solución:** cambiar `ResponseEntity.status(HttpStatus.NOT_FOUND)` por `ResponseEntity.ok(...)`
  en `MiCelulaController.miCelula`, alineando con el endpoint hermano `/members`, que ya usaba
  200 para el mismo caso.
- **Cómo evitar que vuelva a pasar:** cuando "esto no es un error" se traduce del Next.js de
  origen, el status code se decide por lo que espera el **cliente real** (la app RN, no el
  Next.js viejo), no por copiar el código HTTP tal cual estaba. Si dos endpoints del mismo
  controller resuelven el mismo caso de negocio ("sin dato asociado") con status codes distintos,
  eso es señal de un defecto, no de una decisión — revisar el hermano antes de asumir que un 404
  "raro" es intencional.

## Para el registro de decisiones

- **D (comunidad-mentor-y-tribu, 2026-09-02):** `GET /api/v1/me/cell` responde `200
  {"assigned":false}` en vez de `404` cuando el aprendiz no tiene célula asignada. Motivo: un
  cliente HTTP no puede distinguir un 404 semántico de un error real de red/ruta; el endpoint
  hermano `/members` ya usaba 200 para el mismo caso. Sin impacto conocido: no había tests ni
  consumidores del frontend que dependieran del 404 (verificado por grep en ambos repos antes del
  cambio).

## Riesgos que le dejo a quien verifique

1. **El backend en ejecución todavía no tiene el fix** (regla del entorno: no lo recompilo yo).
   Mientras tanto, `celulaApi.obtenerMiCelula()` en el frontend contempla las dos formas (200 y
   404) para que la integración funcione ya mismo — pero conviene, después de recompilar,
   volver a correr el `curl` de §2 contra el aprendiz sin célula y confirmar que efectivamente
   pasa a `200`. Si por algún motivo sigue en 404 después de recompilar, revisar que el JAR/clase
   compilada sea la nueva (`MiCelulaController.class`), no una cacheada.
2. **No hay ningún test automatizado (Java) que cubra el status code de este endpoint** — ni
   antes ni después de este cambio. Si se quiere blindar la regresión, falta un test de
   integración tipo `MiCelulaControllerIT` (o similar) que verifique `200` explícitamente para el
   caso sin célula. Quedó fuera de alcance de esta tarea (no se pidió, y tocar tests de un
   controller sin infraestructura de test HTTP ya armada para este módulo hubiera sido más
   trabajo del pedido).
3. **`mentorSubtitulo`/`mentorNota` en el frontend son copy genérico, no datos del backend.** El
   DTO `MiCelulaResponse` solo trae `mentorName`/`mentorAvatarUrl` — no hay un campo de "rol" ni
   un mensaje real del mentor. El texto "Mentor de tu célula" y "Escribile para coordinar tu
   próxima sesión." son etiquetas de UI fijas (no fabrican un dato de negocio, no se presentan
   como una cita real como hacía el mock original), pero si más adelante el dueño quiere texto
   distinto ahí, es edición de copy en `ComunidadScreen.tsx`, no de la capa de datos.
   `mentorAvatarUrl` **no se usa** — el diseño pinta el mentor con `Placeholder` (una caja, no
   `<Image>`), igual que ya se documentó para el Muro en `wallMappers.ts`; conectar la foto real
   requeriría agregar un `<Image>` al JSX, que está fuera de alcance (regla de diseño).
4. **"Tribu privada" muestra TODOS los integrantes de la célula, incluido el propio aprendiz**
   (`isSelf`), igual que el diseño original mostraba "vos" implícitamente entre los 4 avatares +
   "+12". No se filtró `isSelf` porque el pedido fue "los integrantes de la célula", sin excluir
   a quien mira la pantalla; si el dueño prefiere excluirse a sí mismo del conteo, es un cambio de
   una línea en `useMiCelula`/`ComunidadScreen.tsx` (`companerosCelula.filter(m => !m.isSelf)`).
5. **No se pudo probar en vivo el caso 200 real desde la app** (ni con Postman contra el fix, ya
   que no se recompila el backend en esta tarea) — el `curl` de §2 contra el aprendiz con célula
   real usó el endpoint de admin para encontrar un caso con datos, pero la respuesta de
   `/me/cell` para ese aprendiz se probó contra el código viejo (sigue en 200 igual, porque ese
   caso nunca pasó por la rama del 404). Lo que sigue sin probarse end-to-end es el camino
   "aprendiz sin célula → pantalla real → 200 nuevo" hasta que el backend se recompile.
