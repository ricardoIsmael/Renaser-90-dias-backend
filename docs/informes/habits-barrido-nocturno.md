# Barrido nocturno de generación de tracks — `habits.GenerarTracksDelDiaScheduler`

**Fecha:** 2026-09-02
**Alcance:** completar la segunda mitad del arreglo de "los tracks del día nunca se
generaban" — la generación al consultar ya estaba resuelta; faltaba el barrido nocturno
que cubre al aprendiz que nunca abre la app.

**No corrí `./mvnw` ni ningún build.** El backend está corriendo con devtools y el dueño
lo está usando para una demo — está prohibido explícitamente para este encargo. Todo lo de
abajo sale de leer el código de producción (dominio, casos de uso, adaptadores, migraciones
Flyway) y los informes previos (`C-5`), no de ejecutar nada. Las pruebas que agregué
quedan **sin correr y sin verificar** — ver la sección de pruebas.

---

## 1. Qué construí

### 1.1 Un tercer método en `GenerarTracksDelDiaUseCase`

Antes de tocar el scheduler encontré un problema de diseño que había que resolver primero:
el barrido necesita generar la jornada completa (sin filtro de hora, porque el día todavía
no empezó) para la fecha de **hoy en la zona de cada participante** — no la del servidor.
Pero el puerto de listado en lote, `ConsultarProgresoParticipanteHabitsPort.participantesInscritosActivos()`,
devuelve **solo `List<UserId>`, sin zona horaria** — a propósito, dice su propio javadoc, para
no tentar a nadie a hacer un N+1 llamando a `deParticipante` en un bucle.

Si el scheduler tuviera que resolver la zona de cada participante por su cuenta antes de
llamar al caso de uso, haría exactamente ese N+1 que el puerto evita. La solución: agregar
`generarDiaCompletoEnSuZona(UserId participanteId)` a `GenerarTracksDelDiaUseCase`
(`habits/application/ports/in/registro/GenerarTracksDelDiaUseCase.java`), que resuelve la
zona **adentro** de la implementación — reutilizando el mismo lookup de
`ConsultarProgresoParticipanteHabitsPort.deParticipante` que `generarInterno` ya hace para
validar pertenencia/suspensión (exactamente el mismo patrón que ya usa
`generarDisponiblesAhora`, que también hace ese doble lookup). El scheduler no sabe nada de
zonas: solo itera IDs y llama al caso de uso una vez por participante.

Implementado en `RegistroService`
(`habits/application/services/RegistroService.java`):

```java
@Override
@Transactional
public List<RegistroHabito> generarDiaCompletoEnSuZona(UserId participanteId) {
    ProgresoParticipanteHabits progreso = requireProgreso(participanteId);
    ZoneId zona = ZoneId.of(progreso.timezone());
    LocalDate hoyEnSuZona = clock.now().atZone(zona).toLocalDate();
    return generarInterno(participanteId, hoyEnSuZona, null);
}
```

`horaDeCorte = null` es lo que le dice a `generarInterno` que genere la jornada completa sin
descartar nada por hora — mismo contrato que ya usa `generar(participanteId, fecha)` cuando
se llama con fecha explícita.

### 1.2 `GenerarTracksDelDiaScheduler`

Nuevo archivo:
`src/main/java/com/renaser/os/habits/infrastructure/adapter/in/scheduler/GenerarTracksDelDiaScheduler.java`.

- Inyecta `GenerarTracksDelDiaUseCase` (puerto `in`) y `ConsultarProgresoParticipanteHabitsPort`
  (puerto `out`, inyectado directo en el adaptador de entrada — mismo patrón que ya usa
  `ExpirarRegistrosScheduler` con `ListarParticipantesActivosPort`, no es una novedad de estilo).
- Recorre `progresoPort.participantesInscritosActivos()` y llama a
  `generarTracksUseCase.generarDiaCompletoEnSuZona(participanteId)` dentro de un
  `try/catch (RuntimeException)` por participante — un fallo aislado no tumba el resto del
  barrido.
- Cuenta `procesados`/`fallidos`; loguea `WARN` por cada fallo individual (con el id del
  participante y el mensaje de la excepción) y un único `INFO` de resumen al final, nunca
  `INFO` dentro del bucle (CLAUDE.MD §5.4.9).
- `@SchedulerLock(name = "habits-generar-tracks-del-dia", ...)`, con las mismas propiedades
  configurables (`lock-at-most-for` / `lock-at-least-for`) que
  `evidence-procesar-cola` y `users-avanzar-dia-programa`, mismo formato de placeholder con
  default embebido.
- Método `ejecutar()` público (necesario para que el proxy CGLIB de ShedLock lo intercepte —
  mismo requisito documentado en `C-5.md` para los otros tres schedulers con lock).

### 1.3 Configuración en `application.yaml`

Agregué la entrada `habits-generar-tracks-del-dia` bajo `renaser.scheduling.shedlock`,
**sin tocar las entradas existentes** (`evidence-procesar-cola`,
`habits-promover-cambios-horario`, `rag-generar-informes-semanales`,
`users-avanzar-dia-programa` siguen intactas). También actualicé el comentario de cabecera
de esa sección, que decía literalmente "los 3 schedulers que NO son seguros" — con la
entrada nueva ya no son 3, y dejarlo así habría sido una contradicción del propio archivo
(CLAUDE.MD §0.4: los documentos no pueden contradecirse). Valores: `lock-at-most-for: PT30M`,
`lock-at-least-for: PT30S` — mismos que `users-avanzar-dia-programa`, que es el barrido más
parecido en forma (recorre todo el padrón activo, una operación por participante).

---

## 2. Horario elegido: 05:02 UTC, y por qué

Los schedulers existentes del "tramo nocturno":

| Hora UTC | Scheduler | Qué hace |
|---|---|---|
| 04:15 | `users.PurgarCuentasBajaScheduler` | purga cuentas de baja |
| 04:30 | `notifications.PurgaNotificacionesScheduler` | purga notificaciones viejas |
| 04:40 | `habits.PromoverCambiosHorarioScheduler` | promueve cambios de horario pendientes |
| 04:50 | `users.AvanzarDiaProgramaScheduler` | avanza `dia_programa` de cada participante |
| **05:02** | **`habits.GenerarTracksDelDiaScheduler` (nuevo)** | **genera los tracks del día** |
| 05:00 | `habits.ExpirarRegistrosScheduler` | expira `PENDIENTE` de días anteriores |
| 05:05 | `points.SnapshotRankingScheduler` | snapshot de ranking |

Dos restricciones, no una:

1. **Tiene que correr después de que `dia_programa` ya esté avanzado.** El catálogo del día
   se resuelve con `HorarioHabito.aplicaEnDia(diaPrograma, tipoDia)` — generar con el
   `dia_programa` de ayer generaría el catálogo equivocado (un hábito que aplica desde el
   día 38 no aparecería si el participante todavía figura en el día 37). Por eso va después
   de `AvanzarDiaProgramaScheduler` (04:50), con el mismo margen de ~10-12 minutos que el
   propio repo ya acepta como suficiente entre `AvanzarDiaProgramaScheduler` (04:50) y
   `ExpirarRegistrosScheduler` (05:00) — no es una garantía dura (`lockAtMostFor` permite
   hasta 30 min en el peor caso), pero es la misma convención que ya está en producción, no
   una nueva.

2. **Tiene que correr después de la medianoche en la zona de los participantes** — ver §3,
   es la razón real por la que elegí 05:02 y no, por ejemplo, 04:55.

No importa si corre antes o después de `ExpirarRegistrosScheduler` (05:00): ese barrido opera
sobre `PENDIENTE` con `fecha_ejecucion` **anterior** a hoy (los registros de ayer que quedaron
sin completar), y el mío genera registros para **hoy**. No hay superposición de filas entre
los dos, así que el orden relativo entre ambos no cambia el resultado. Lo dejé después de
`ExpirarRegistros` (05:00 → 05:02) solo porque necesitaba la hora ≥ 05:00 UTC por la razón de
zona horaria de abajo, no por una dependencia real con ese scheduler.

---

## 3. El problema de zonas horarias — análisis pedido explícitamente

**Sí, un cron a hora UTC fija puede generar el día equivocado si no se diseña con cuidado —
y la mitad del arreglo está en el propio horario elegido, la otra mitad en cómo se calcula
la fecha.**

### 3.1 Por qué 04:50 UTC (o cualquier hora antes de las 05:00 UTC) sería el día equivocado para Lima

Lima es UTC-5 fijo (sin horario de verano). Hora local = hora UTC − 5.

- A las **04:50 UTC**, la hora en Lima es **23:50 del día anterior** — todavía no pasó la
  medianoche local.
- Recién a partir de las **05:00 UTC** la hora en Lima es **00:00** — ahí es cuando el
  calendario local de Lima avanza al nuevo día.

Si el barrido calculara la fecha a generar con `LocalDate.now()` del **servidor** (UTC) en
vez de la zona del participante, generaría siempre con la fecha UTC del momento — que
durante esa ventana de 5 horas (00:00–05:00 UTC) va **un día adelantada** respecto a la
fecha real en Lima. Eso generaría tracks para "mañana" en vez de "hoy" desde la perspectiva
del aprendiz, y el aprendiz vería su catálogo vacío o con la fecha corrida hasta que la
fecha del servidor alcance a la suya.

**Peor todavía si el cron corriera antes de las 05:00 UTC (p. ej. a las 04:50) Y la fecha se
calculara en la zona del participante** (que es lo que hice, ver abajo): a las 04:50 UTC son
las 23:50 en Lima — la medianoche local **todavía no pasó**. `LocalDate.now(zonaLima)` en ese
instante devuelve **el día que está por terminar**, no el que está por empezar. El barrido
regeneraría (sin duplicar nada, por la idempotencia del `UNIQUE`) el catálogo del día que ya
tiene tracks, y el día que realmente hace falta pre-generar (el que arranca a las 00:00)
quedaría sin generar hasta que el aprendiz abra la app — exactamente el problema original que
este barrido existe para cerrar.

### 3.2 La solución que apliqué: dos partes, no una

1. **La fecha se calcula en la zona de CADA participante**, no en UTC del servidor:
   `generarDiaCompletoEnSuZona` hace `clock.now().atZone(zona).toLocalDate()` con la zona que
   viene de `ProgresoParticipanteHabits.timezone()` (columna real de `participantes_programa`,
   no una constante). Esto ya corrige el caso "servidor en UTC, participante en otra zona"
   para cualquier participante, sea cual sea su zona.
2. **El horario del cron se eligió para que, en el momento en que corre, ya haya pasado la
   medianoche en la zona de la población real de este producto (Lima, UTC-5).** A las 05:02
   UTC son las 00:02 en Lima: la medianoche ya pasó, así que
   `LocalDate.now(zonaLima)` devuelve la fecha correcta — el día que **acaba de empezar**, no
   el que acaba de terminar.

Con las dos partes juntas, para el 100% de la población actual (Perú, confirmado en
`docs/MODULOS_A_AVANZAR.md` y en la memoria del proyecto), el barrido genera la fecha
correcta.

### 3.3 Lo que NO resolví, y por qué se lo dejo explícito a quien verifique

**Un cron a una única hora UTC fija no puede pre-generar correctamente "el día que está por
empezar" para participantes en TODAS las zonas horarias simultáneamente — esto es
estructural, no un bug de mi implementación.**

Con el cron a las 05:02 UTC:

- Para cualquier zona con offset **≥ UTC-5** (Lima o más al este: UTC-5, UTC-3, UTC+0, etc.),
  la medianoche local ya pasó cuando corre el cron → `LocalDate.now(esaZona)` da la fecha
  correcta, igual que Lima.
- Para una zona con offset **< UTC-5** (más al oeste — p. ej. México central UTC-6, Pacífico
  de EE.UU. UTC-8, Hawái UTC-10), la medianoche local **todavía no pasó** a las 05:02 UTC →
  `LocalDate.now(esaZona)` devuelve el día que está por **terminar**, no el que está por
  **empezar**. El barrido de esa noche regeneraría (sin duplicar nada, gracias al `UNIQUE`)
  el día que ya tiene tracks, y el día nuevo de ese participante quedaría sin pre-generar
  hasta que abra la app — es decir, para esos participantes el problema original (aprendiz
  que nunca abre la app se queda sin tracks, sin expiración, con coherencia intacta) **no
  queda cerrado por el barrido**, solo por el camino on-demand
  (`generarDisponiblesAhora` vía `TracksDelDiaProyeccionService.consultar`).

**Hoy esto no rompe nada porque la población es 100% Perú (UTC-5)** — es el único caso real
y cae del lado correcto (offset = UTC-5, justo en el borde, y elegí 05:02 en vez de 05:00
exacto para tener 2 minutos de margen sobre el borde, no para cubrir otras zonas). Pero si el
producto suma participantes en zonas más al oeste que Lima, este barrido dejaría de cubrirlos
correctamente, en silencio (no hay error, solo el efecto de "regenera el día viejo, no
genera el nuevo").

**Qué haría si aparece esa necesidad real** (no lo construí ahora porque no hay señal de que
haga falta y CLAUDE.MD §0.6 pide no inventar alcance):

- Opción simple: correr el barrido más de una vez por día (p. ej. cada hora, o en 2-3
  horarios fijos) y que `generarDiaCompletoEnSuZona` sea, como ya es, naturalmente idempotente
  — solo produciría trabajo extra para las zonas donde ya generó, sin dañar nada.
- Opción más precisa: filtrar el padrón por rango de zona horaria y correr un cron por franja
  (p. ej. un cron a las 05:02 UTC solo para zonas UTC-5 a UTC+X, otro más tarde para zonas más
  al oeste) — más trabajo de mantenimiento, pero evita las corridas redundantes de la opción
  simple.
- En cualquier caso, el camino on-demand (`generarDisponiblesAhora`) ya es la red de
  seguridad real para cualquier participante fuera de la ventana horaria que el barrido cubre
  bien: mientras abra la app en algún momento del día, sus tracks se generan igual. El
  barrido nocturno solo cierra el caso "nunca abre la app", y ese caso queda cerrado bien
  para Lima y mal para zonas muy al oeste — hoy inexistentes en el producto.

---

## 4. Pruebas agregadas — **sin correr, sin verificar**

`src/test/java/com/renaser/os/habits/infrastructure/adapter/in/scheduler/GenerarTracksDelDiaSchedulerTest.java`
(Mockito puro, sin `@SpringBootTest`, sin Testcontainers — coherente con que es una prueba
unitaria del scheduler, no de integración con ShedLock):

- `llamaAlCasoDeUsoUnaVezPorParticipante`: con 3 participantes en el padrón, verifica que
  `generarDiaCompletoEnSuZona` se llama exactamente una vez por cada uno (ni cero, ni dos).
- `unParticipanteQueFallaNoDetieneElBarrido`: el participante del medio de la lista lanza
  `IllegalStateException`; verifica que los otros dos igual se procesan (aislamiento de
  fallo).
- `padronVacioNoLlamaAlCasoDeUso`: caso borde, padrón vacío no debe invocar el caso de uso.
- `todosFallanIgualSeIntentanTodos`: si TODOS los participantes fallan, igual se intentan
  todos en orden (no corta en el primer fallo).

**No corrí `./mvnw clean test`** — prohibido explícitamente para este encargo. No sé si
compilan sin errores de tipeo, si los imports están completos, ni si pasan. Quien las corra
primero debe reportar el resultado antes de dar esto por cerrado (CLAUDE.MD §0.2: "no se
reporta algo como terminado sin haber corrido las pruebas" — yo no puedo cerrarlo, lo dejo
explícito para que alguien con permiso de build lo haga).

No agregué un test de integración de lock (tipo `ProcesarColaValidacionSchedulerLockTest`,
con Testcontainers + dos hilos compitiendo por el mismo lock) porque el encargo pedía
explícitamente "pruebas unitarias", no de integración — lo marco como pendiente en
"Riesgos", no lo inventé por mi cuenta.

No agregué un test unitario para `RegistroService.generarDiaCompletoEnSuZona` en
`RegistroServiceTest` — ese archivo hoy no tiene ningún test de `generar`/
`generarDisponiblesAhora` tampoco (deuda preexistente, no la until ahora), así que no había
un patrón establecido para seguir sin inventar convenciones nuevas a mitad de una tarea de
alcance acotado. Lo dejo como riesgo abierto abajo.

---

## Para la bitácora

- **Síntoma:** ninguno todavía en producción — este cambio cierra un hueco encontrado por
  inspección de código (`RegistroService.generarDisponiblesAhora` existía y se usaba al
  consultar, pero nada llamaba a la generación completa del día por lote; un aprendiz que
  nunca abre la app nunca tendría tracks, nunca expiraría nada, y su coherencia quedaría en
  100 indefinidamente).
- **Causa real:** el caso de uso de generación por lote (`GenerarTracksDelDiaUseCase.generar`)
  siempre existió y compilaba, pero ningún `@Scheduled` lo invocaba — el barrido nocturno
  nunca se construyó en la primera pasada del módulo (documentado como deuda explícita en el
  javadoc viejo de `ExpirarRegistrosScheduler`, que decía literalmente "NI la generación
  masiva de tracks del día siguiente... queda para un caso de uso separado").
- **Solución aplicada:** `GenerarTracksDelDiaScheduler` nuevo, con `@SchedulerLock`, corriendo
  a las 05:02 UTC, aislando fallos por participante.
- **Cómo evitar que vuelva a pasar:** cuando se agregue un caso de uso `in` nuevo que
  represente un efecto de negocio recurrente (no solo on-demand), verificar explícitamente
  si necesita también un disparador por lote (`@Scheduled`) — el patrón "existe el caso de
  uso pero nadie lo llama" no lo detecta ningún test si no hay un test que verifique que el
  endpoint HTTP O el scheduler lo invocan.

## Para el registro de decisiones

- **D-nuevo (pendiente de numerar en `docs/MODULOS_A_AVANZAR.md` §8, fuera de mi alcance
  tocar ese archivo):** el barrido nocturno de generación de tracks corre a las **05:02 UTC**,
  después de `users.AvanzarDiaProgramaScheduler` (necesita `dia_programa` fresco) y después de
  la medianoche en Lima (UTC-5) — la zona horaria de la población actual del producto. La
  fecha a generar se calcula **por participante, en su propia zona** (columna real de
  `participantes_programa`), no con la fecha UTC del servidor ni con una zona hardcodeada.
- **Decisión abierta que dejo señalada, no tomada por mí:** un cron a hora fija no cubre
  correctamente zonas horarias más al oeste que Lima (UTC-6 o más negativo) para el caso
  "aprendiz que nunca abre la app". Hoy es un no-problema (0 participantes fuera de Perú). Si
  el producto se expande a otras zonas, alguien con visión de producto tiene que decidir entre
  correr el barrido más de una vez al día o segmentarlo por franja horaria — no lo decidí yo
  porque es una decisión de negocio (a qué zonas se expande el producto y cuándo), no técnica.

## Riesgos que le dejo a quien verifique

1. **Las 4 pruebas nuevas no se corrieron.** Puede haber errores de compilación (imports,
   firmas) que solo aparecen al compilar. Correr `./mvnw clean test
   -Dtest=GenerarTracksDelDiaSchedulerTest` primero, antes del resto de la suite.
2. **`generarDiaCompletoEnSuZona` no tiene test unitario propio en `RegistroServiceTest`** —
   solo lo ejercitan (sin verificar) los tests del scheduler, que mockean el caso de uso
   entero. La lógica real de resolución de zona (`clock.now().atZone(zona).toLocalDate()`)
   queda sin un test unitario dedicado que la aisle de `generarInterno`. Recomiendo agregar
   uno cuando se toque `RegistroServiceTest` la próxima vez.
3. **El margen de ~12 minutos entre `AvanzarDiaProgramaScheduler` (04:50) y este barrido
   (05:02) no es una garantía dura.** `AvanzarDiaProgramaScheduler` tiene `lockAtMostFor:
   PT30M` — en el peor caso (proceso lento o caído a mitad de barrido) podría seguir
   corriendo cuando el mío arranca. Si eso pasa, algunos participantes se generarían con
   `dia_programa` todavía no avanzado esa noche puntual — se corregiría solo, la noche
   siguiente. Es el mismo riesgo residual que ya acepta `ExpirarRegistrosScheduler` (05:00)
   respecto del mismo scheduler; no es nuevo, pero vale que quede escrito acá también.
4. **No corrí ningún build**, así que no puedo confirmar que `RegistroService.java` y
   `GenerarTracksDelDiaUseCase.java` compilen sin errores de sintaxis — los revisé con
   lectura cuidadosa, pero un typo puntual no se descarta sin compilar.
5. **El análisis de zonas horarias (§3) es análisis estático**, no verificado contra el
   comportamiento real de Postgres/JVM en este entorno (offsets de zona, `ZoneId.of(...)`
   con el string que realmente guarda `participantes_programa.timezone`). Si esa columna
   guarda algo distinto de un ID de zona IANA válido (p. ej. un offset crudo), `ZoneId.of`
   fallaría en runtime — pero eso ya era una precondición existente de `generarDisponiblesAhora`,
   no algo que yo introduzco.
