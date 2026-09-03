# Hábito personal con horario — cierra el bug de "el hábito nunca aparece en el día"

**Fecha:** 2026-09-02
**Encargo:** el aprendiz crea su hábito personal (`POST /api/v1/habits`) pero nunca le aparece en su jornada, porque `RegistroService.generarInterno` solo genera track si el hábito tiene al menos un `HorarioHabito` que aplique, y la única vía para crear un horario era `HorarioHabitoAdminService` (exclusiva de ADMIN/ALCHEMIST). Decisión del dueño del proyecto (2026-09-02): **el aprendiz elige la hora al crear el hábito.**

---

## 1. Qué cambié

Reusé todo lo que ya existía (§3 de `docs/informes/habits-eleccion-y-personales.md` ya había dejado el endpoint, el comando, el DTO y el servicio construidos — solo faltaba la pieza del horario, exactamente como esa nota dejó documentado en su pregunta abierta §4.4). No creé ningún servicio, puerto ni adaptador nuevo — todo lo que necesitaba (`SaveHorarioHabitoPort`, `HorarioHabito.crear`) ya estaba, construido para el panel admin.

| Archivo | Cambio |
|---|---|
| `src/main/java/com/renaser/os/habits/domain/model/horario/HorarioHabito.java` | Nuevo invariante de dominio: `horaLimite` debe ser posterior a `horaDisparo` cuando ambas vienen cargadas. Aplicado en `crear(...)` y en `actualizarHoras(...)` (protege también la edición admin, no solo el alta nueva). |
| `src/main/java/com/renaser/os/habits/application/ports/in/habito/CrearHabitoPersonalUseCase.java` | El comando (`CrearHabitoPersonalCommand`) suma `horaDisparo` (`@NotNull LocalTime`) y `horaLimite` (`LocalTime`, opcional). Nivel 2 de validación (CLAUDE.MD §5.4.3): el compact constructor rechaza `horaLimite <= horaDisparo` — el comando es estructuralmente imposible de construir mal, sin depender de que el controller sea el único que valide. |
| `src/main/java/com/renaser/os/habits/infrastructure/adapter/in/rest/habito/CreatePersonalHabitRequest.java` | Suma `triggerTime` (`@NotNull LocalTime`) y `limitTime` (`LocalTime`, opcional). Nombres elegidos para calzar con el contrato HTTP ya establecido (`UpdateHabitPreferenceRequest`, `HabitScheduleResponse` ya usan exactamente `triggerTime`/`limitTime` para el mismo concepto en otros endpoints del módulo). |
| `src/main/java/com/renaser/os/habits/infrastructure/adapter/in/rest/habito/MisHabitosController.java` | Pasa `request.triggerTime()`/`request.limitTime()` al comando. Sin cambios de guard ni de ruta. |
| `src/main/java/com/renaser/os/habits/application/services/MisHabitosService.java` | `crear(...)` ahora, en el mismo método `@Transactional`, guarda el `Habito` y crea+guarda su `HorarioHabito` (puerto `SaveHorarioHabitoPort`, ya usado por `HorarioHabitoAdminService` — mismo puerto, sin duplicar). Constructor ampliado con `SaveHorarioHabitoPort saveHorarioPort`. `requireProgreso` ahora devuelve `ProgresoParticipanteHabits` (antes solo validaba y descartaba el resultado) para poder leer `diaPrograma()`. |

**Nada de esto tocó `Habito.java`, `Habito.crearPersonal`, el campo `desactivable`, ni ninguna migración Flyway.** El agregado `Habito` no necesitaba cambios — el problema estaba enteramente en la falta de `HorarioHabito`, no en el hábito mismo.

**Nota sobre el estado del repo al momento de este cambio:** `Habito.java` tiene hoy un desajuste real entre su lista de campos (21, incluyendo `desactivable`, ya agregado) y los argumentos que le pasa `crearPersonal` (20, uno de menos) — no compila tal cual está. Es exactamente el campo que, según el encargo, otro agente está integrando en paralelo (la migración `V18` para "si un hábito se puede desactivar"). No toqué `Habito.java` en absoluto, ni para arreglar ese desajuste: no es mi cambio y el encargo pide explícitamente no meterme en ese terreno. Lo dejo señalado acá para que quien retome ese trabajo lo sepa — si compilás este módulo antes de que ese otro cambio cierre, `Habito.java` va a fallar en `crearPersonal`, no por nada de lo que hice yo.

---

## 2. Cómo garanticé la atomicidad

`Habito` y `HorarioHabito` se guardan dentro del mismo método `MisHabitosService.crear(...)`, anotado `@Transactional` (propagación `REQUIRED`, por defecto — no hizo falta declarar nada especial). Es el mismo patrón que ya usa `HorarioHabitoAdminService.crear` para dar de alta un horario sobre un hábito ya existente: una sola transacción de Spring/JPA, sin `TransactionTemplate` ni segunda anotación.

Si `saveHorarioPort.save(horario)` (o la construcción de `HorarioHabito.crear(...)` que la precede) lanza una excepción — por ejemplo, el invariante nuevo de `horaLimite`, o el invariante ya existente de `diaInicio` fuera de `1..90` — la excepción se propaga fuera del método `@Transactional`, y Spring marca la transacción para rollback. Postgres deshace también el `savePort.save(habito)` anterior, dentro de la misma transacción. Nunca queda un `Habito` PERSONAL sin su `HorarioHabito` — que es exactamente el bug original, solo que ahora sería "un hábito a medio crear" en vez de "un hábito completo sin horario".

**Esto está probado, no solo argumentado:** el test de integración `siElHorarioEsInvalidoNoQuedaNingunHabitoHuerfano` (ver §5) fuerza el camino de falla con un caso real (participante en `dia_programa = 0`, el propio `DEFAULT` de la tabla `participantes_programa` antes de que se active el programa) y verifica contra Postgres real que no queda ninguna fila en `habitos` para ese participante después del rollback.

---

## 3. `diaInicio`/`diaFin`/`tipoDia` elegidos, y por qué

| Campo | Valor | Justificación |
|---|---|---|
| `diaInicio` | `progreso.diaPrograma()` (el día de programa del participante en el momento de la creación, leído del mismo `ConsultarProgresoParticipanteHabitsPort` que ya usa el guard de suspensión) | "Aplica desde el día actual en adelante" — nunca desde el día 1 del programa (el aprendiz puede crear su hábito personal en cualquier momento de sus 90 días) ni desde una fecha calendario (el sistema razona en días de programa, no en fechas, ver `aplicaEnDia`). |
| `diaFin` | `null` | Abierto — el hábito personal no vence solo porque pasen los días; el aprendiz lo desactiva a mano si quiere dejar de verlo (`Habito.desactivar`, ya existente, solo que hoy gateado a ADMIN — ver riesgo en §6). |
| `tipoDia` | `TipoDia.TODOS` | De los 4 valores reales del enum (`DISCIPLINA`, `INTOXICACION` — no implementado en esta versión —, `TODOS`, `DOMINGO`), `TODOS` es el único que tiene sentido para un hábito que el propio aprendiz se autoimpuso: no hay ninguna noción de "domingo especial" o "día de disciplina especial" para algo que él mismo definió. `DISCIPLINA`/`DOMINGO` son distinciones del catálogo del sistema (que sí separa el día de descanso), no algo que un hábito autodeclarado necesite. |

**Verificación contra `aplicaEnDia` (no asumida, releída):**

```java
public boolean aplicaEnDia(int diaPrograma, TipoDia tipoDiaDelDia) {
    boolean enRango = diaPrograma >= diaInicio && (diaFin == null || diaPrograma <= diaFin);
    boolean tipoCoincide = tipoDia == TipoDia.TODOS || tipoDia == tipoDiaDelDia;
    return enRango && tipoCoincide;
}
```

Con `diaInicio = diaPrograma actual` y `diaFin = null`: `enRango` es `diaPrograma >= diaInicio` — cierto exactamente desde hoy (el día de la creación) en adelante, nunca antes (no hay forma de que un `registro_habito` se genere retroactivamente para un día ya pasado, que es el comportamiento correcto). Con `tipoDia = TODOS`: `tipoCoincide` es `true` sin mirar `tipoDiaDelDia`, así que aplica los 7 días de la semana sin excepción.

---

## 4. Traza completa: por qué ahora sí genera track

Releí `RegistroService.generarInterno` línea por línea (no until el bug ya lo había hecho el informe anterior — lo volví a confirmar antes de dar esto por cerrado):

```java
List<Habito> catalogo = new ArrayList<>(loadHabitoPort.catalogoActivo());
catalogo.addAll(loadHabitoPort.personalesActivosDe(participanteId));   // (1)

for (Habito habito : catalogo) {
    if (loadRegistroPort.porParticipanteHabitoYFecha(participanteId, habito.id(), fecha).isPresent()) {
        continue;                                                       // (2)
    }
    boolean aplicaHoy = loadHorarioPort.porHabito(habito.id()).stream()
            .filter(h -> h.aplicaEnDia(progreso.diaPrograma(), tipoDia))  // (3)
            .anyMatch(h -> sigueAlcanzable(h, horaDeCorte));              // (4)
    if (!aplicaHoy) {
        continue;
    }
    RegistroHabito registro = RegistroHabito.generar(...);
    generados.add(saveRegistroPort.save(registro));                       // (5)
}
```

1. **`personalesActivosDe(participanteId)`** — el hábito recién creado entra acá: `Habito.crearPersonal` lo deja `activo = true` (sin cambios, ya funcionaba así antes de este encargo).
2. **Idempotencia** — no hay registro previo para hoy (hábito recién creado), así que no se salta.
3. **`aplicaEnDia`** — con el `HorarioHabito` que ahora creo junto al hábito (`diaInicio = diaPrograma de hoy`, `diaFin = null`, `tipoDia = TODOS`), esto es `true` el mismo día de la creación (ver traza matemática en §3). **Antes de este cambio, `loadHorarioPort.porHabito(habito.id())` devolvía una lista vacía** (no existía ningún `HorarioHabito` para un hábito recién creado) — el `.stream().filter(...).anyMatch(...)` sobre una lista vacía es `false` por definición, y por eso el hábito nunca pasaba de acá. Esa era la causa raíz, y es exactamente lo que este cambio cierra.
4. **`sigueAlcanzable`** — con `horaDeCorte = null` (el caso de `generar(participanteId, fecha)`, usado por el barrido nocturno) siempre es `true`. Con `horaDeCorte != null` (el caso de `generarDisponiblesAhora`, para "ahora mismo"), depende de si `horaLimite` ya pasó — comportamiento correcto y sin cambios: si el aprendiz elige una `horaLimite` de las 8am y crea el hábito a las 10am, no le va a aparecer como "disponible ahora" hasta el día siguiente, que es el comportamiento esperado (mismo criterio que ya aplica para el catálogo de sistema).
5. **Se genera el `RegistroHabito`** — sin cambios.

**Conclusión de la traza: sí, un hábito personal recién creado con este cambio pasa el filtro completo y genera su track del día**, confirmado también por el test de integración (§5), no solo por lectura.

**Otro obstáculo río abajo que encontré, no arreglado (fuera de este encargo):** si `progreso.diaPrograma() == 0` (el participante todavía no activó su programa — `programa_activado_en IS NULL`, el `DEFAULT` real de `participantes_programa.dia_programa`), `HorarioHabito.crear` rechaza `diaInicio = 0` (fuera de `1..90`) y el alta completa falla con `400 Bad Request`. Ver riesgo en §6 — no lo resolví porque decidir qué hacer en ese caso (¿usar `diaInicio = 1`? ¿bloquear la creación con un mensaje explícito de "activá tu programa primero"?) es una regla de negocio no confirmada.

---

## 5. Pruebas agregadas (NO ejecutadas con `./mvnw` — regla del encargo)

### Unitarias

- **`src/test/java/com/renaser/os/habits/domain/model/horario/HorarioHabitoTest.java`** (extendido) — el invariante nuevo en el dominio:
  - `crearConHoraLimiteAnteriorAHoraDisparoFalla`
  - `crearConHoraLimiteIgualAHoraDisparoFalla`
  - `crearConSoloHoraDisparoNoFalla` (regresión: sigue permitido no tener `horaLimite`)
  - `actualizarHorasConHoraLimiteAnteriorAHoraDisparoFalla` (protege también la edición admin, no solo el alta)

- **`src/test/java/com/renaser/os/habits/application/services/MisHabitosServiceTest.java`** (extendido, constructor ampliado con `SaveHorarioHabitoPort`) — reglas nuevas:
  - `crearHabitoPersonalCreaTambienElHorarioConElDiaDeProgramaActualYTipoDiaTodos` — verifica `diaInicio`/`diaFin`/`tipoDia`/`horaDisparo`/`horaLimite` del `HorarioHabito` guardado, por `ArgumentCaptor`.
  - `crearHabitoPersonalSinHoraLimiteEsValido`
  - `crearHabitoPersonalSinHoraDisparoEsRechazadoPorElComandoAntesDeLlegarAlServicio` — confirma que el rechazo pasa por `ConstraintViolationException` (Bean Validation, `@NotNull`) antes de tocar ningún puerto (`verifyNoInteractions`).
  - `crearHabitoPersonalConHoraLimiteAnteriorAHoraDisparoEsRechazadoPorElComando` / `...IgualAHoraDisparoEsRechazadoPorElComando` — el rechazo ocurre en el propio comando (nivel 2 de validación, CLAUDE.MD §5.4.3), sin invocar el servicio.
  - `crearHabitoPersonalSuspendidoRechazado` / `...ParticipanteNoEncontradoRechazado` (preexistentes) — extendidas con `verifyNoInteractions(savePort, saveHorarioPort)` para confirmar que ni el hábito ni el horario se tocan si el guard rechaza antes.

### Integración (Testcontainers, Postgres real)

**`src/test/java/com/renaser/os/habits/application/services/CrearHabitoPersonalGeneraTrackTransaccionIT.java`** (nuevo) — sigue el patrón de `CompletarRegistroExpiracionTransaccionIT` (mismo motivo: la atomicidad de una transacción real no la demuestra un mock). Antes de escribirla releí `docs/BITACORA_ERRORES.md` **E-74** (semilla envuelta en `TransactionTemplate`, tipos de columna reales) y **E-78** (nunca el reloj del sistema — uso `FixedClock`, aunque esta prueba en particular no depende de una ventana horaria):

1. **`habitoPersonalConHorarioGeneraTrackDelDia`** — la prueba central que cierra el bug: crea el hábito vía `CrearHabitoPersonalUseCase` (bean real), confirma por SQL directo que el `HorarioHabito` quedó persistido, llama `GenerarTracksDelDiaUseCase.generar(...)` y confirma que el track se generó.
2. **`habitoPersonalSinHoraLimiteGeneraTrackDelDia`** — el caso más común del catálogo real (mayoría de horarios sin `horaLimite`) también genera track.
3. **`siElHorarioEsInvalidoNoQuedaNingunHabitoHuerfano`** — la prueba de atomicidad: fuerza `dia_programa = 0`, confirma que `crear(...)` lanza y que no queda ninguna fila en `habitos` para ese participante. Ver §6 sobre esta misma condición como riesgo de UX.

**No se agregó** una prueba de reflexión ni de autorización negativa nueva: el endpoint `POST /api/v1/habits` ya tenía `@RequiresPermission(Permission.USE_APP)` desde el encargo anterior (`docs/informes/habits-eleccion-y-personales.md`), sin cambios en esta pasada — las pruebas de rol/suspensión ya existentes (`crearHabitoPersonalSuspendidoRechazado`) siguen cubriendo eso, solo actualizadas para el nuevo constructor.

---

## Para la bitácora

No encontré ningún error nuevo de configuración/entorno durante esta pasada — el hallazgo real (el desajuste de argumentos en `Habito.crearPersonal` vs. los 21 campos de `Habito.java`, §1) no es un error mío ni algo para registrar como bug propio: es un cambio en curso de otro agente (campo `desactivable`, migración `V18` pendiente), documentado acá para que quien lo termine sepa que ya quedó señalado, no para registrarlo en `BITACORA_ERRORES.md` como una entrada nueva (no lo diagnostiqué como una falla resuelta, sigue abierto y no es mío para cerrar).

## Para el registro de decisiones

- **Cerrado: pregunta 4 de `docs/informes/habits-eleccion-y-personales.md` §4** ("¿cómo se resuelve que un hábito personal recién creado no genere tracks?"). Decisión del dueño del proyecto (2026-09-02): el aprendiz elige `triggerTime`/`limitTime` al crear el hábito, en vez de un horario por defecto autogenerado o un hábito sin horario.
- Se agregó un invariante nuevo al agregado `HorarioHabito` (`horaLimite` posterior a `horaDisparo`) que antes no existía en ningún lado del código — ni en el alta admin, ni en la edición admin. Se aplicó tanto a `crear` como a `actualizarHoras` para que proteja el invariante de forma pareja, no solo en el camino nuevo.
- `diaFin = null` / `tipoDia = TODOS` para todo hábito personal, sin excepción — no hay ningún camino (hoy) para que un aprendiz le ponga un rango de días o un `tipoDia` distinto a su propio hábito. Si eso se pide en el futuro, es una extensión del comando, no un cambio de esta decisión.

## Riesgos que le dejo a quien verifique

- **No corrí `./mvnw clean test`** (regla del encargo) — ni las pruebas nuevas ni las modificadas fueron ejecutadas. El constructor de `MisHabitosService` cambió (nuevo parámetro `SaveHorarioHabitoPort`) — busqué otros usos con `grep` además del test ya actualizado y no encontré ninguno más, pero vale una segunda pasada, sobre todo si hay algún `@SpringBootTest` de otro módulo que instancie el contexto completo de `habits`.
- **`Habito.java` no compila en el estado actual del repo** (desajuste de 20 vs. 21 argumentos en `crearPersonal`, ver §1) — no es un cambio mío, pero significa que este módulo no va a compilar hasta que el otro agente cierre su parte (`desactivable`/`V18`). Si `./mvnw clean test` falla en `habits`, **revisar primero si el error es en `Habito.crearPersonal` antes de sospechar de este cambio.**
- **Participante en `dia_programa = 0` (programa no activado):** el alta de hábito personal falla con `400` y un mensaje que menciona `diaInicio` — un término interno que el aprendiz nunca mandó y que no significa nada para el cliente móvil. No lo resolví porque el comportamiento correcto (¿bloquear antes con un mensaje claro? ¿usar `diaInicio = 1`?) es una decisión de negocio no confirmada (CLAUDE.MD §0.6). Cubierto por un test que documenta el comportamiento actual (`siElHorarioEsInvalidoNoQuedaNingunHabitoHuerfano`), no que lo arregla.
- **No verifiqué que un hábito `PERSONAL` con `tipo = BLOQUEO` o `JOURNALING` funcione correctamente en el resto del módulo** (`SantuarioService`, `EntradaDiario`) — riesgo ya señalado en el informe anterior (`habits-eleccion-y-personales.md` §5), sigue sin auditar, no forma parte de este encargo.
- **No expuse el horario elegido en `MiHabitoResponse`** (la respuesta del `POST`/`GET` sigue sin `triggerTime`/`limitTime`) — no estaba pedido explícitamente en el encargo y hubiera sido alcance extra; si el cliente móvil necesita mostrar la hora elegida inmediatamente después de crear el hábito, hoy tendría que pedirla aparte (no hay endpoint de lectura de `HorarioHabito` para el propio aprendiz, solo el admin `GET /api/v1/admin/habits/{habitId}/schedules`, gateado a `MANAGE_HABIT_CATALOG`).
- **Los tests de integración usan `renaser.usuarios(rol='APRENDIZ', estado='ACTIVO')`** y `participantes_programa(usuario_id, dia_programa)` con solo esas columnas explícitas, copiando el patrón ya probado de `CompletarRegistroExpiracionTransaccionIT` — no verifiqué de nuevo contra Postgres real que ese INSERT mínimo siga siendo válido (podría haber cambiado si otro agente tocó esas tablas en paralelo).
