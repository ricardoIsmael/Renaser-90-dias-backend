# Elección de hábitos y hábitos personales — módulo `habits`

**Fecha:** 2026-09-02

---

## 0. Respuesta urgente: el interruptor ACTIVO/PAUSADO — es del catálogo GLOBAL, no personal

**Hoy, el único endpoint que escribe `habitos.activo` es del panel admin y exige ADMIN/ALCHEMIST. No existe ninguna vía por la que un aprendiz pueda tocarlo.** Si el interruptor de la pantalla Plan llama a ese endpoint, el dueño tenía razón en sospechar: sería el interruptor equivocado para "activo/inactivo a nivel personal" — pero la buena noticia es que, tal como está el backend hoy, un aprendiz **no puede** ejecutarlo (recibiría 403), así que no hay agujero de seguridad. El riesgo real es de **UX/contrato**: si el cliente espera un interruptor personal y hoy solo existe uno global-admin, ese botón en la pantalla Plan del aprendiz no tiene ningún endpoint propio al que llamar todavía.

Evidencia, archivo:línea:

1. **El campo que muta es el agregado de catálogo compartido**, no un flag por aprendiz:
   `src/main/java/com/renaser/os/habits/domain/model/habito/Habito.java:117-126`
   ```java
   public void desactivar(Instant ahora) {
       this.activo = false;
       this.actualizadoEn = ahora;
   }
   public void activar(Instant ahora) {
       this.activo = true;
       this.actualizadoEn = ahora;
   }
   ```
   `activo` es un campo de `Habito` (tabla `renaser.habitos`, fila única compartida por ambito=SISTEMA). No existe ningún flag de "activo para MÍ" por participante en el dominio — la única bandera de opt-in/opt-out por aprendiz que existe hoy es `desbloqueos_habito` (tarea 2 de este informe) y `personalesActivosDe` (para hábitos PERSONAL, que ya son propios de un único dueño).

2. **El único caso de uso que llama a esos métodos es el del panel admin**, gateado a ADMIN/ALCHEMIST:
   `src/main/java/com/renaser/os/habits/application/services/HabitoAdminService.java:66-77`
   ```java
   @Override
   @Transactional
   public Habito cambiarActivo(CambiarActivoHabitoCommand command) {
       guard.requireAdmin(command.actorId());
       Habito habito = requireHabito(command.habitoId());
       if (command.activo()) {
           habito.activar(clock.now());
       } else {
           habito.desactivar(clock.now());
       }
       return savePort.save(habito);
   }
   ```
   El javadoc de la propia interfaz ya lo dice explícito:
   `src/main/java/com/renaser/os/habits/application/ports/in/habitoadmin/CambiarActivoHabitoUseCase.java:9`
   `/** Baja/alta logica del catalogo (toggle isActive del panel). Solo ADMIN/ALCHEMIST. */`

3. **El guard rechaza a cualquiera que no sea ADMIN/ALCHEMIST, y a una cuenta SUSPENDED aunque lo sea**:
   `src/main/java/com/renaser/os/habits/application/services/HabitoAdminGuard.java:31-40`
   ```java
   void requireAdmin(UserId actorId) {
       UserSummary actor = userSummaryFinder.findById(actorId)...
       if (actor.status() != UserStatus.ACTIVE) {
           throw new NotAuthorizedException("La cuenta esta suspendida");
       }
       if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
           throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran el catalogo de habitos");
       }
   }
   ```

4. **El único endpoint HTTP que dispara ese caso de uso es del panel admin, no del aprendiz**, y exige el permiso `MANAGE_HABIT_CATALOG` (distinto del `USE_APP` genérico que usan los endpoints de autoservicio del aprendiz):
   `src/main/java/com/renaser/os/habits/infrastructure/adapter/in/rest/habitoadmin/HabitoAdminController.java:39-40,84-91`
   ```java
   @RestController
   @RequestMapping("/api/v1/admin/habits")
   public class HabitoAdminController {
       ...
       @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
       @PostMapping("/{id}/toggle")
       public AdminHabitResponse toggle(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                         @RequestBody ToggleHabitRequest request) { ... }
   ```

5. **Ningún endpoint de autoservicio del aprendiz escribe `activo`.** El único endpoint del lado aprendiz que lee el catálogo es de solo lectura:
   `src/main/java/com/renaser/os/habits/infrastructure/adapter/in/rest/habito/MisHabitosController.java:16-29` — un solo `@GetMapping`, sin ningún `@PostMapping`/`@PutMapping`/`@PatchMapping`.
   Búsqueda exhaustiva (`grep -rn "\.activar\(\|\.desactivar\("` sobre `com.renaser.os.habits`) no encuentra ningún otro llamador de `Habito.activar()`/`Habito.desactivar()` fuera de `HabitoAdminService`.

**Conclusión para el dueño:** el interruptor ACTIVO/PAUSADO del panel admin es, por diseño y por permiso, exclusivamente para ADMIN/ALCHEMIST sobre el catálogo compartido — un aprendiz no puede tocarlo aunque quisiera (403 garantizado por rol, antes de llegar al dominio). Si la pantalla Plan del aprendiz necesita un interruptor **personal** (pausar/reanudar un hábito solo para mí, sin afectar a nadie más), **ese endpoint no existe todavía** — no es un bug de seguridad, es una funcionalidad que falta. Los candidatos naturales para modelarlo, sin inventar la regla de negocio exacta:
- Filtrar el hábito de la generación diaria de tracks (`RegistroService.generarInterno`, que hoy solo mira `catalogoActivo()` + `personalesActivosDe(actor)`, sin ningún filtro de "pausado por mí").
- O bien tratarlo como una eliminación reversible de la elección en `desbloqueos_habito` (la tabla que sí es por-aprendiz) — pero esa tabla hoy es solo de catálogo escalonado, no de pausa.

Esto queda además como pregunta abierta en la sección 4 de este informe. El resto del encargo (tareas 2 y 3) sigue abajo.

---

## 2. Endpoint para que el aprendiz elija sus hábitos (`desbloqueos_habito`)

**Nuevo:** `PUT /api/v1/habit-unlocks/{habitId}` — `@RequiresPermission(Permission.USE_APP)`, mismo patrón de autoservicio que `PUT /api/v1/weekly-habit-days/{habitId}`. Idempotente: elegir el mismo hábito dos veces no falla ni duplica, simplemente devuelve el mismo resultado (200, nunca 409 por repetir la elección).

**Reusado:**
- `DesbloqueoHabitoService` (ahora implementa también `ElegirHabitoUseCase`, no un servicio nuevo) — mismo `requireProgreso` (SUSPENDED → 403) que ya usaba `consultar`.
- `LoadHabitoPort` para verificar que el hábito exista, sea de catálogo (`ambito == SISTEMA`) y esté activo.
- `HabitUnlockController`/`HabitUnlockPlanResponse` existentes — se agregó el método `PUT` y un overload de `HabitUnlockItemResponse.from(DesbloqueoHabito)` al lado del que ya recibía `ItemDesbloqueo`.
- La PK compuesta real de `desbloqueos_habito` (`PRIMARY KEY (participante_id, habito_id)`, `V1__baseline_renaser.sql:526`) — **no hizo falta ningún `UNIQUE` nuevo**, ya está en el baseline. Se verificó antes de asumir, como pedía el encargo.

**Nuevo (mínimo, siguiendo el patrón ya usado por `SavePuntajePort.crearFilaInicialSiFalta`, C-12/E-75):**
- `application/ports/in/desbloqueo/ElegirHabitoUseCase.java` — comando self-validating `(actorId, habitoId)`.
- `application/ports/out/desbloqueo/SaveDesbloqueoHabitoPort.java` — método `elegirSiFalta(...)`.
- `LoadDesbloqueoHabitoPort.deParticipanteYHabito(...)` — relectura puntual tras el insert.
- `SpringDataDesbloqueoHabitoRepository.elegirSiFalta(...)` — `INSERT ... ON CONFLICT (participante_id, habito_id) DO NOTHING`, nativo, `@Modifying(clearAutomatically = true)`.

**Idempotencia, cómo quedó resuelta:** a diferencia de `RocaDiaria.completar()` (C-2, que necesita bloqueo pesimista porque hay una fila existente que se MUTA), elegir un hábito es "agregar a un conjunto" — no hay estado previo que proteger con `FOR UPDATE`. El `INSERT ... ON CONFLICT DO NOTHING` alcanza solo: si dos requests concurrentes eligen el mismo hábito, Postgres serializa el segundo `INSERT` contra la restricción `UNIQUE` de la PK (nunca hay una violación de PK que tire un 500, y nunca hay dos filas). El caso de uso relee después con `deParticipanteYHabito` y devuelve el estado canónico sin importar quién "ganó". **A diferencia de `RocaDiaria`, acá TODOS los intentos concurrentes terminan en éxito (200)** — no hay un perdedor que reciba 409, porque no hay una operación que solo pueda pasar una vez en el sentido de "completar algo"; es una elección que converge al mismo resultado sin importar cuántas veces se repita.

**Reglas de negocio que tuve que decidir para que el endpoint funcione, NO confirmadas — ver §4:**
- `diaDesbloqueo` se fija al **día de programa actual** del aprendiz en el momento de elegir (desbloqueo inmediato). No hay escalonamiento por lotes (el algoritmo completo de `habitStaggering.ts`, D-H2, sigue sin portarse — esto no lo cierra, solo agrega el alta autoservicio simple).
- Rechazo con 403 si `diaPrograma == 0` (día 0, vista previa) — mismo criterio literal que ya usa `EleccionDiaSemanalService` para el mismo campo.
- Rechazo si el hábito es `PERSONAL` (solo tiene sentido "elegir" hábitos de catálogo compartido — un hábito personal ya es exclusivamente del dueño que lo creó).
- Rechazo si el hábito está inactivo en el catálogo.
- **Sin máximo de hábitos elegibles** — el dueño no especificó un tope, así que no se inventó uno.

**Hallazgo adicional, no resuelto (para el registro de decisiones):** el baseline de `participantes_programa` ya tiene una columna `habitos_escalonados_en timestamptz` (`V1__baseline_renaser.sql:273`, comentario: *"NULL = padrón anterior al desbloqueo escalonado"*) — es decir, la semántica que `ConsultarDesbloqueosHabitoUseCase.PlanDesbloqueo.enabled` aproxima hoy ("true si hay al menos un desbloqueo guardado") **ya tiene un campo real dedicado en la base**, pero el módulo `habits` no lo lee ni lo escribe (solo aparece mapeado, sin uso funcional, en `users/infrastructure/adapter/out/persistence/participante/ParticipacionProgramaJpaEntity.java`). Mi nuevo `ElegirHabitoUseCase` tampoco lo toca — `habits` solo tiene un puerto de **lectura** hacia `participantes_programa` (`ConsultarProgresoParticipanteHabitsPort`, deuda ya documentada en su propio javadoc), no uno de escritura, así que setear esa columna requeriría un puerto nuevo hacia `users` (o un evento) que está fuera del alcance de este encargo. Lo dejo señalado porque conecta directo con la pregunta del dueño sobre "activo/inactivo a nivel personal" (§0): ese campo podría ser la pieza correcta para modelarlo, en vez de agregar algo nuevo.

**Pruebas agregadas (NO ejecutadas con `./mvnw` — regla del encargo):**
- `src/test/java/com/renaser/os/habits/application/services/DesbloqueoHabitoServiceTest.java` — se actualizó el constructor existente (4 dependencias nuevas) y se agregaron: día 0 rechazado, hábito personal rechazado, hábito inactivo rechazado, elección normal devuelve el desbloqueo asegurado, elegir dos veces seguidas es idempotente (mismo resultado, sin excepción).
- `src/test/java/com/renaser/os/habits/application/services/DesbloqueoHabitoConcurrenciaTest.java` — integración con Testcontainers (Postgres real), siguiendo el patrón de `RocaDiariaConcurrenciaTest` (seed vía `JdbcTemplate` en auto-commit, `Clock` fijo por `@Bean @Primary`, lección E-74/E-78 de `docs/BITACORA_ERRORES.md`): elección normal, elección repetida sin duplicar, y 6 elecciones concurrentes del mismo hábito → una sola fila y **ningún** intento falla (a diferencia de C-2, acá no hay perdedor de carrera).

---

## 3. Endpoint para que el aprendiz cree un hábito propio (`ambito = PERSONAL`)

**Nuevo:** `POST /api/v1/habits` — `@RequiresPermission(Permission.USE_APP)`, 201 con el hábito creado. Convive con el `GET /api/v1/habits` ya existente (que sigue sin ningún guard, ver nota abajo).

**Reusado, sin duplicar el servicio admin:**
- El agregado `Habito` y su factoría `Habito.crearPersonal(id, participanteId, titulo, tipo, categoriaClave, plantilla, etiquetaMeta, ahora)` (`domain/model/habito/Habito.java:88-96`) — **ya existía**, no se tocó.
- `SaveHabitoPort.save(...)` — el mismo puerto que usa `HabitoAdminService`, sin ningún método nuevo.
- `MisHabitosService` (ahora implementa también `CrearHabitoPersonalUseCase`) — **no se creó un servicio nuevo**, tal como pedía el encargo ("no dupliques el servicio de admin"). `HabitoAdminService` queda intacto, exclusivo del catálogo SISTEMA.
- `HabitCategoryDto`/`HabitTypeDto` (ya existían para el panel admin) — reusados en el DTO nuevo para no reinventar el mapeo `BODY→CUERPO`/`RATING→CALIFICACION`/etc.
- `MiHabitoResponse.from(habito)` (ya existía) como respuesta — ninguna proyección nueva.

**Nuevo, mínimo:**
- `application/ports/in/habito/CrearHabitoPersonalUseCase.java` — comando self-validating.
- `infrastructure/adapter/in/rest/habito/CreatePersonalHabitRequest.java` — DTO de entrada.
- Constructor de `MisHabitosService` ampliado con `SaveHabitoPort`, `ConsultarProgresoParticipanteHabitsPort`, `Clock`, `IdGenerator` (los tres últimos ya existían como puertos en el módulo, reusados de otros servicios).

**Blindaje de mass-assignment (CLAUDE.MD §5.3.3), verificado línea por línea:**
- `CreatePersonalHabitRequest` (`infrastructure/.../habito/CreatePersonalHabitRequest.java`) **no declara** `ambito` ni `participanteId` — ni siquiera como campo opcional. Los únicos campos son `title`, `habitType`, `category`, `template`, `goalLabel`.
- `CrearHabitoPersonalCommand` (el contrato del caso de uso, no solo el DTO HTTP — nivel 2 de validación de CLAUDE.MD §5.4.3) **tampoco** los declara.
- En `MisHabitosService.crear(...)`, el `participanteId` que se pasa a `Habito.crearPersonal(...)` es literalmente `command.actorId()` — el valor que puso `@ActorAutenticado` en el controller a partir de la sesión, nunca algo leído del cuerpo del request. El `ambito` no es un parámetro en absoluto: `Habito.crearPersonal` lo fija a `AmbitoHabito.PERSONAL` adentro de la fábrica (`domain/model/habito/Habito.java:93`), no hay ningún camino para pasarle `SISTEMA`.
- Test que verifica esto explícitamente: `MisHabitosServiceTest.creaUnHabitoPersonalConIdentidadDelActorSinImportarQueNoSePidaEnElComando` — confirma `resultado.ambito() == PERSONAL` y `resultado.participanteId() == actor` sin que el comando tenga esos campos para forzarlo.

**Guard aplicado:** `requireProgreso` (mismo patrón que el resto del módulo) — SUSPENDED → 403, participante inexistente → 404 (`NoSuchElementException`). Nota de consistencia: el `GET /api/v1/habits` existente **no** tiene ningún guard (ver `EndpointAuthorizationDeclarationTest.HANDLERS_SIN_CLASIFICAR`, entrada `"MisHabitosController#listar"`, con el comentario explícito de por qué) — una cuenta SUSPENDED puede seguir leyendo su catálogo hoy. El `POST` nuevo sí lleva guard porque es una escritura; no se tocó el comportamiento del `GET` porque estaba fuera de este encargo y ya está documentado como deuda conocida, no un descuido nuevo.

**Verificación de `RegistroService.generar` (pedida explícitamente en el encargo) — con un hallazgo importante:**

Confirmado por lectura de código (`application/services/RegistroService.java:152-165`) que un hábito PERSONAL bien creado **sí** entra en la lista de candidatos del barrido diario:
```java
List<Habito> catalogo = new ArrayList<>(loadHabitoPort.catalogoActivo());
catalogo.addAll(loadHabitoPort.personalesActivosDe(participanteId));   // <- acá entra
...
boolean aplicaHoy = loadHorarioPort.porHabito(habito.id()).stream()
        .filter(h -> h.aplicaEnDia(progreso.diaPrograma(), tipoDia))
        .anyMatch(h -> sigueAlcanzable(h, horaDeCorte));
if (!aplicaHoy) {
    continue;                                                          // <- y acá se frena
}
```
**Pero la segunda mitad de ese mismo método es la que importa:** un hábito solo genera un `RegistroHabito` (track) si existe al menos un `HorarioHabito` que le `aplicaEnDia(...)`. Y la **única** vía para crear un `HorarioHabito` hoy es `HorarioHabitoAdminService`, gateado a ADMIN/ALCHEMIST (`application/services/HorarioHabitoAdminService.java:45-62`, mismo `HabitoAdminGuard` de la sección 0). **No existe ningún caso de uso de autoservicio para que el propio aprendiz le ponga un horario a su hábito personal.**

**Consecuencia concreta:** con el endpoint que acabo de construir, un aprendiz puede crear su hábito PERSONAL (aparece en `GET /api/v1/habits`, vía `personalesActivosDe`), pero **ese hábito nunca va a generar un track en el barrido diario** hasta que alguien con rol ADMIN/ALCHEMIST le cree un horario a mano desde el panel admin — lo cual no tiene sentido para un hábito que es, por definición, privado de un aprendiz. Esto **no lo resolví** porque decidir *cómo* se cubre (¿el aprendiz elige su propio horario? ¿se genera un horario "todo el día, todos los días" automáticamente al crear el hábito? ¿cuál sería el rango de días 1-90?) es una regla de negocio no confirmada — inventarla violaría CLAUDE.MD §0.6. Queda como la pregunta abierta más importante de este informe (§4).

**Sobre el tipo de hábito permitido:** el DTO acepta los 4 `TipoHabito` (CHECKBOX/JOURNALING/CALIFICACION/BLOQUEO), igual que el alta admin — no restringí a CHECKBOX por la misma razón (no inventar una regla no confirmada). Ver riesgo en §5: no verifiqué si `BLOQUEO` (Santuario) o `JOURNALING` funcionan correctamente sobre un hábito `PERSONAL` en el resto del módulo (`SantuarioService`, `EntradaDiario`), porque asumen implícitamente un hábito de catálogo en varios lugares del código que no audité a fondo en esta pasada.

**Pruebas agregadas (NO ejecutadas con `./mvnw` — regla del encargo):**
- `src/test/java/com/renaser/os/habits/application/services/MisHabitosServiceTest.java` — se actualizó el constructor existente (4 dependencias nuevas) y se agregaron: suspendido rechazado, participante no encontrado rechazado, y el test de blindaje de mass-assignment descrito arriba (verifica `ambito`/`participanteId` del agregado resultante sin que el comando los declare).
- No se agregó una prueba de integración nueva para `RegistroService.generar` + hábito PERSONAL con/sin horario: `RegistroServiceTest` no tiene hoy ningún test de `generar()` (solo de `completar()`/`expirarPendientesAnterioresA()`) y armar ese andamiaje desde cero para un caso que de todos modos no tiene forma de resolverse sin la pieza faltante (horario de autoservicio) hubiera sido alcance no pedido. La verificación de esta sección es por lectura de código, citada línea por línea arriba — declarado explícitamente como tal, no como prueba automatizada.

---

## 4. Preguntas abiertas de negocio (CLAUDE.MD §0.6 — no se inventó ninguna respuesta)

1. **Interruptor personal ACTIVO/PAUSADO (§0):** ¿existe o debería existir una forma de que el aprendiz pause un hábito SOLO para sí mismo (catálogo o personal), sin tocar el catálogo global? Hoy no hay ningún endpoint para eso. Candidatos posibles: extender `desbloqueos_habito`, usar la columna ya existente `participantes_programa.habitos_escalonados_en`, o algo nuevo — ninguno confirmado.
2. **`diaDesbloqueo` al elegir un hábito (§2):** ¿debe ser el día de programa actual (desbloqueo inmediato, lo que implementé) o debe pasar por el algoritmo de escalonamiento por lotes de D-H2 (días 1/3/5/7) cuando se porte?
3. **Máximo de hábitos elegibles (§2):** ¿hay un tope de cuántos hábitos de catálogo puede tener activos un aprendiz a la vez? Hoy no hay límite.
4. **Horario para hábitos PERSONAL (§3):** ¿cómo se resuelve que un hábito personal recién creado no genere tracks porque no tiene `HorarioHabito`? ¿Autoservicio de horario para el dueño del hábito? ¿Un horario por defecto "todo el día, todos los días del programa" al crearlo?
5. **Tipos permitidos para hábitos PERSONAL (§3):** ¿los 4 tipos (CHECKBOX/JOURNALING/CALIFICACION/BLOQUEO) tienen sentido para un hábito autodeclarado, o debería restringirse (p. ej. solo CHECKBOX)?

---

## Para la bitácora

No se encontró ningún error de configuración/entorno real durante esta pasada (a diferencia de otras entradas de `docs/BITACORA_ERRORES.md` como E-74/E-75/E-78, que sí se consultaron antes de escribir las pruebas de integración, según pedía el encargo). Si al correr `./mvnw clean test` aparece algo inesperado en `DesbloqueoHabitoConcurrenciaTest`, revisar primero — por la lección de E-74 — si el fallo está en el `seedParticipanteYHabito()` (esquema/tipo de columna) antes de sospechar de `ElegirHabitoUseCase`.

## Para el registro de decisiones

- Se agregó autoservicio de escritura sobre `desbloqueos_habito` (`ElegirHabitoUseCase`) — cierra PARCIALMENTE D-H2 (el alta simple, no el algoritmo de escalonamiento por lotes, que sigue pendiente).
- Se agregó autoservicio de creación de hábitos PERSONAL (`CrearHabitoPersonalUseCase`) — el agregado `Habito.crearPersonal` y la tabla ya estaban listos; solo faltaba el caso de uso y el endpoint.
- **No se resolvió** la pregunta original que motivó el encargo (interruptor activo/inactivo personal) — se determinó que la implementación actual del interruptor visible en el admin es correcta y segura (global, solo ADMIN/ALCHEMIST), pero que falta la pieza de "pausa personal" si eso es lo que la pantalla Plan del aprendiz necesita.
- Hallazgo: `participantes_programa.habitos_escalonados_en` existe en el baseline sin ningún lector/escritor funcional en `habits` — candidato para resolver la pregunta 1 de §4, no aprovechado en esta pasada por estar fuera del puerto de solo-lectura que `habits` tiene hacia `users`.

## Riesgos que le dejo a quien verifique

- **No corrí `./mvnw clean test`** (regla del encargo) — ni las pruebas nuevas ni las modificadas (`DesbloqueoHabitoServiceTest`, `MisHabitosServiceTest`, `DesbloqueoHabitoConcurrenciaTest`) fueron ejecutadas. Los cambios de firma de constructor en `DesbloqueoHabitoService` y `MisHabitosService` son *breaking* para cualquier otro test que los instancie directamente — busqué otros usos con `grep` y no encontré más que los dos archivos de test ya actualizados, pero vale una segunda pasada.
- La query nativa `elegirSiFalta` en `SpringDataDesbloqueoHabitoRepository` usa `CAST(:diaDesbloqueo AS smallint)` — mismo patrón que `SpringDataPuntajeParticipanteRepository.insertarInicialSiFalta`, pero no la corrí contra Postgres real.
- No verifiqué que un hábito `PERSONAL` con `tipo = BLOQUEO` o `JOURNALING` funcione correctamente en el resto del módulo (`SantuarioService`, `EntradaDiario`) — ver pregunta 5 de §4.
- El endpoint `PUT /api/v1/habit-unlocks/{habitId}` y `POST /api/v1/habits` son rutas **elegidas por mí**, sin contrato viejo verificado 1:1 (misma salvedad que el resto del módulo, `docs/MODULO_HABITS.md` §3) — si el cliente móvil espera una ruta distinta, hay que ajustarla.

