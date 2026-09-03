# Auditoría de cumplimiento del código contra `CLAUDE.MD`

**Fecha:** 2026-08-31
**Alcance:** `src/main/java/` completo — 1.551 archivos `.java`, 3.318 métodos, 74 controllers, 15 módulos.
**Vara:** el texto literal de `CLAUDE.MD` §5.1.2, §5.4.1–§5.4.9 y el checklist de §5.4.10. No se aplicaron criterios propios; donde la regla escrita me parece discutible o se contradice a sí misma, está señalado aparte (§9).
**Método:** solo lectura. **No se corrió Maven** (había un build en curso). Se usó `grep`/`find` y un analizador estructural de Java escrito para esta auditoría (tokenizador que descarta comentarios y literales, luego balanceo de llaves para medir cuerpos de método/clase, anidamiento, parámetros y llamadas por handler). Todo hallazgo está citado con archivo y línea; nada se infirió de un nombre o un javadoc sin abrir el archivo (§10 detalla las dos excepciones).

---

## 1. Resumen ejecutivo

| # | Eje | Hallazgos | Severidad máxima |
|---|---|---|---|
| 1 | Pureza del dominio (§5.1.2, §5.4.10) | **0** violaciones de código; **1** hallazgo sobre la cobertura del `ArchitectureTest` | Media (riesgo, no defecto) |
| 2 | Controllers tontos (§5.4.6) | **4** (13 de 263 handlers) | **Alta** |
| 3 | Límites de tamaño (§5.4.8) | **5** (1 clase, 2 métodos, 115 firmas, 13 clases) | Media |
| 4 | Programación funcional (§5.4.7) | **2** | Baja |
| 5 | Nombres (§5.4.8) | **1** | Muy baja |
| 6 | Logging y PII (§5.4.9) | **2** | **Alta** |
| 7 | Estrategia de mapeo (§5.4.1, §5.4.5) | **0** violaciones; **1** observación | Informativa |

**El hallazgo más grave** es el **§6.1: fuga de PII en `Email.java:21`**. El mensaje de excepción incluye el correo completo del solicitante, `GlobalExceptionHandler` lo escribe al log **y** lo devuelve en el cuerpo de la respuesta HTTP. Es una cadena verificada de tres saltos, no una hipótesis, y viola textualmente la regla dura de §5.4.9 ("Nunca loguear: ... emails completos"). No está documentado en ningún lugar del repo.

**Segundo en gravedad:** §2.1 — 9 handlers de `community` orquestan dos casos de uso por endpoint. Importa además porque la auditoría previa del módulo (`docs/MODULO_COMMUNITY.md:266`) afirma lo contrario de forma explícita, así que el equipo hoy cree que ese punto está limpio.

**Lo que se cumple de punta a punta** (sin una sola excepción en 1.551 archivos): pureza del dominio, ausencia de `@Transactional` y de puertos `out` en controllers, anidamiento ≤3, `domain/` sin logging, nombres de puertos por intención de negocio, `Optional` como retorno en los 183 puertos de salida, y la estrategia de mapeo de §5.4.5. Detalle en §8.

---

## 2. Eje 1 — Pureza del dominio (§5.1.2, §5.4.10)

### 2.1 Resultado: limpio, sin excepciones

Verificado con `grep` sobre los 15 `domain/` del proyecto:

| Verificación | Resultado |
|---|---|
| `import org.springframework.*` en `domain/` | **0** |
| `import jakarta.persistence.*` en `domain/` | **0** |
| `import jakarta.validation.*` en `domain/` | **0** |
| `import com.fasterxml.jackson.*` en `domain/` | **0** |
| `import ...infrastructure.adapter.*` en `domain/` | **0** |
| `public void setX(...)` en `domain/` | **0** |
| `@Data` / `@Setter` / `@NoArgsConstructor` / `@Builder` / `@ToString` sin acotar en `domain/` | **0** |
| `@Entity` fuera de `adapter/out/persistence` | **0** |
| Clases con `@Entity` **y** Jackson/`@JsonProperty` a la vez | **0** |

El uso de Lombok en `domain/` es exactamente el set que §5.4.5 autoriza, en las 48 clases que lo usan:

- `@Getter` — 48
- `@AllArgsConstructor(access = AccessLevel.PRIVATE)` — 48
- `@Accessors(fluent = true)` — 48
- `@EqualsAndHashCode(of = "…")` **siempre acotado** — 48 (35 por `id`, el resto por clave natural: `{participanteId, habitoId}`, `{conversacionId, usuarioId}`, etc.)

Cero apariciones sin acotar. Y `User.toString()` (`users/domain/model/user/User.java:227-229`) está escrito a mano y devuelve `"User[" + id + ", " + role + ", " + status + "]"` — sin email, sin nombre. Es literalmente lo que §5.4.5 pide ("el `toString()` de una entidad con datos personales se escribe a mano, corto, sin PII"). Esto está bien hecho y conviene decirlo.

### 2.2 Hallazgo — qué cubre el `ArchitectureTest` y qué NO

`src/test/java/com/renaser/os/ArchitectureTest.java` tiene 6 reglas. Es un test bien construido: los patrones de paquete (`..adapter.in.rest..`, `..ports.out..`, `..domain..`) **sí coinciden** con la estructura real del proyecto (`<modulo>/infrastructure/adapter/in/rest`, `<modulo>/application/ports/out`, `<modulo>/domain/model`), así que ninguna regla está evaluando el vacío. Verificado archivo por archivo.

**Lo que el test SÍ garantiza en CI:**

| Línea | Regla |
|---|---|
| 24-26 | Modulith `verify()` — los módulos no importan paquetes internos de otros |
| 30-40 | `..domain..` no depende de Spring / `jakarta.persistence` / `jakarta.validation` / Jackson |
| 44-50 | `..domain..` no depende de `..adapter..` |
| 54-63 | `..application..` no depende de `jakarta.servlet` / `springframework.web` / `springframework.http` |
| 67-77 | `..adapter.in.rest..` no depende de `..ports.out..`, `..adapter.out..`, `springframework.data`, `jakarta.persistence` |
| 81-90 | Ninguna clase termina en `Util`, `Utils`, `Helper`, `Manager` |

**Lo que el test NO mira — y por eso es donde puede acumularse deuda sin que el build se ponga rojo:**

1. **Lombok es invisible para ArchUnit.** ArchUnit lee *bytecode*; las anotaciones de Lombok son `RetentionPolicy.SOURCE` y desaparecen en la compilación. Consecuencia concreta: **si mañana alguien pone `@Data` o `@Setter` en una clase de `domain/`, los 6 tests siguen en verde.** Hoy no pasa (§2.1 lo verifica), pero la regla más citada de §5.4.5 está sostenida solo por revisión humana. Tampoco hay una regla ArchUnit que prohíba métodos `public void set*` en `..domain..`, que sería la forma de cazar el efecto de Lombok en bytecode.
2. **`@Transactional` en un controller no está cubierto.** La regla de la línea 70-74 lista `org.springframework.data..` pero **no** `org.springframework.transaction..`. §5.4.6 lo prohíbe explícitamente. Hoy hay 0 casos (verificado), pero no está protegido.
3. **Dos de las seis familias de nombres prohibidos faltan.** §5.4.8 prohíbe `Util`, `Helper`, `Manager`, `Processor`, `Data`, `Info`. La regla de la línea 81-90 cubre solo las cuatro primeras: **`Processor`, `Data` e `Info` no se chequean** (y ahí es donde apareció el único hallazgo del eje 5, §6).
4. **Todo §5.4.8 de tamaños** (método ≤40, clase ≤300, ≤4 parámetros, ≤10 métodos públicos, ≤3 niveles) no tiene una sola regla. Es el eje con más incumplimientos reales (§4).
5. **§5.4.7 `Optional` como campo o parámetro** no está cubierto — y hay un caso real (§5.1).
6. **§5.4.9 logging y PII** no está cubierto — es el eje del hallazgo más grave (§7).
7. **§5.4.6 "un controller invoca UN caso de uso"** no es expresable en ArchUnit (es una regla sobre el cuerpo del método, no sobre dependencias). Los 9 casos de §3.1 pasan el test sin problema.
8. **§5.4.5 "MapStruct solo en persistencia"** no está cubierto. Hoy no importa porque no hay ningún `@Mapper` (§8).
9. **`domain/` no loguea** (§5.4.9) no tiene regla. Hoy hay 0 casos.
10. `GlobalExceptionHandler` vive en `shared/web`, **fuera** de `..adapter.in.rest..`, así que la regla de la línea 67-77 no lo alcanza. No es un problema hoy, pero es el archivo que §5.4.9 marca como punto de fuga clásico.

**Interpretación:** la lectura correcta de "el `ArchitectureTest` pasa" es "las dependencias entre paquetes están sanas", no "el código cumple §5.4". Las reglas de §5.4.5 (Lombok), §5.4.6 (cuerpo del controller), §5.4.7 (`Optional`), §5.4.8 (tamaños) y §5.4.9 (PII) viven íntegramente fuera del alcance del build.

---

## 3. Eje 2 — Controllers tontos (§5.4.6)

74 controllers, **263 handlers** anotados con `@*Mapping`. Se midió, por handler, qué dependencias inyectadas invoca y cuántos `if`/ternarios contiene.

### 3.1 Hallazgo A (alto) — 9 handlers orquestan dos casos de uso: escribir y volver a leer

§5.4.6 es explícito: *"Prohibido en un controller: … Orquestar varios casos de uso para 'armar' una operación. Si hacen falta dos, falta un caso de uso que los componga."*

**`community/infrastructure/adapter/in/rest/celula/CelulaAdminController.java` — 6 de 14 handlers:**

| Línea | Handler | Patrón |
|---|---|---|
| 121-128 | `crear` | `crearUseCase.crear(...)` → `consultarUseCase.obtener(...)` |
| 131-136 | `actualizar` | `actualizarUseCase.actualizar(...)` → `consultarUseCase.obtener(...)` |
| 139-144 | `asignarMentor` | `asignarMentorUseCase.asignar(...)` → `consultarUseCase.obtener(...)` |
| 147-151 | `quitarMentor` | `quitarMentorUseCase.quitar(...)` → `consultarUseCase.obtener(...)` |
| 155-160 | `asignarAprendiz` | `asignarAprendizUseCase.asignar(...)` → `consultarUseCase.obtener(...)` |
| 173-… | `programarSesion` | `programarSesionUseCase...` → `consultarUseCase.obtener(...)` |

Ejemplo textual (`CelulaAdminController.java:131-136`):

```java
@PatchMapping("/{id}")
public CelulaDetalleResponse actualizar(@ActorAutenticado UserId actorId, @PathVariable UUID id,
                                         @RequestBody ActualizarCelulaRequest request) {
    actualizarUseCase.actualizar(new ActualizarCelulaCommand(actorId, CelulaId.of(id), request.name(),
            request.videoCallUrl(), true));
    return CelulaDetalleResponse.from(consultarUseCase.obtener(actorId, CelulaId.of(id)));
}
```

**`community/infrastructure/adapter/in/rest/cohorte/CohorteAdminController.java` — 3 de 7 handlers:** `crear` (64-71), `actualizar` (74-79), `cambiarEstado` (82-87). Idéntico patrón.

**Por qué importa más de lo que parece:** el mutar y el leer ocurren en **dos transacciones distintas**, porque cada `@Transactional` vive en su propio caso de uso (§5.4.6: *"La transacción es del caso de uso, no del transporte"*). Entre el `actualizar` y el `obtener` hay una ventana donde otro request puede modificar la misma célula, y el cliente recibe un estado que no es el que él escribió. El caso de uso compuesto que la regla pide (`ActualizarCelulaUseCase` devolviendo la vista de detalle) resolvería las dos cosas a la vez.

**Este hallazgo contradice la auditoría previa.** `docs/MODULO_COMMUNITY.md:266` afirma: *"`CelulaAdminController` …, `CohorteAdminController`, `MiCelulaController`: cada endpoint deserializa, valida (`@Valid`), llama un único caso de uso y mapea a DTO de salida"*. Es incorrecto para 9 de los handlers de esos dos archivos. Lo señalo no para corregir a nadie, sino porque el equipo hoy tiene ese punto marcado como verificado.

### 3.2 Hallazgo B (alto, **ya documentado**) — `if` de negocio eligiendo entre dos casos de uso

`community/infrastructure/adapter/in/rest/testimonio/TestimonioController.java:48-61`:

```java
int estrellas = request.estrellas() != null ? request.estrellas() : 5;
if (request.wallPostId() != null && !request.wallPostId().isBlank()) {
    var vista = promoverUseCase.promover(...);
    return ResponseEntity.status(HttpStatus.CREATED).body(TestimonioResponse.from(vista));
}
var vista = crearUseCase.crear(...);
```

Viola dos prohibiciones a la vez: el `if` de negocio y la orquestación de dos casos de uso. Además, el default `estrellas = 5` es un valor de negocio decidido en el transporte.

**Ya está documentado** en `docs/MODULO_COMMUNITY.md:251` con el mismo diagnóstico y hasta la misma propuesta de arreglo (`POST /testimonios` vs `POST /testimonios/from-post`). No lo presento como descubrimiento.

### 3.3 Hallazgo C (medio) — `Clock` inyectado en el controller y usado para decidir

§5.4.6 lista entre lo prohibido: *"Cálculos, formateos de negocio, acceso a `Clock` real (usar el puerto `Clock` de `shared/`)"*. Los controllers usan el puerto `Clock` de `shared/`, que es la alternativa que la propia regla nombra — así que la letra estricta no está rota. Lo que sí ocurre es lo otro que la frase prohíbe: **decidir con el reloj dentro del controller.**

- `points/…/rest/ranking/RankingController.java:25` inyecta `Clock`; en las líneas **38** y **47** decide `LocalDate fechaConsultada = fecha != null ? fecha : clock.today();`. "Si el cliente no manda fecha, se entiende hoy" es una regla del caso de uso, no del transporte, y hoy está escrita dos veces en el mismo archivo.
- `habits/…/rest/racha/RachaController.java:36` inyecta `Clock` y lo pasa al DTO de salida en las líneas **53**, **71** y **79**: `RachaSinCelularResponse.from(racha, clock.now(), EXTENSION_DEFAULT_HORAS)`. El DTO calcula tiempo restante con ese instante, y `EXTENSION_DEFAULT_HORAS` es una constante de negocio declarada en el controller.

No encontré este punto documentado en `docs/`.

### 3.4 Hallazgo D (bajo) — `if` de negocio en el mapeo request→command

`calendar/infrastructure/adapter/in/rest/evento/EventoController.java:166` y **179** tienen `if (r.recurrenceFrequency() == null) { … }` / `if (r.reminderRules() == null) { … }`, y la línea **170** aplica un default de negocio: `r.recurrenceInterval() == null ? 1 : r.recurrenceInterval()`. Están en helpers privados de mapeo, que §5.4.5 sí ubica en la frontera web ("a mano"), pero "intervalo de recurrencia por defecto = 1" es una regla, no un formato.

### 3.5 Lo que está bien y hay que decirlo

- **0 de 74 controllers** tienen `@Transactional`. Verificado con `grep` sobre los 74 archivos.
- **0 de 74 controllers** importan un `ports.out`, un `adapter.out`, `org.springframework.data` o `jakarta.persistence`.
- **246 de 263 handlers (93,5%)** invocan exactamente una dependencia inyectada.
- Los ternarios restantes que detecté (`EvidenciaController.java:63-67`, `MensajeController.java:37`, `MiembroController.java:24`, `ConversacionController.java:80`) son parseo null-safe de query params HTTP → tipo de dominio. Es traducción de transporte, no negocio: **no los cuento como violación.**
- Los `parseCursor` privados de `WallController.java:153`, `WallCommentController.java:56`, `TicketMentorController.java:87` y `TicketMentorAdminController.java:30` son lo mismo (decodificar un cursor de paginación). Tampoco los cuento.
- `AutenticacionController` inyecta `SesionWebAdapter` además del caso de uso (líneas 69, 75, 81, 134). Es una clase de `adapter/in/web/security` que concentra cookies y `SecurityContextHolder`, y su javadoc (`SesionWebAdapter.java:18-26`) argumenta explícitamente por qué existe: para que el controller quede en la forma de §5.4.6 sin lógica de transporte inline. **No es un puerto `out` ni un caso de uso: es transporte.** Lo considero conforme.

---

## 4. Eje 3 — Límites de tamaño (§5.4.8)

Medido con el analizador estructural (cuerpo de clase = declaración → llave de cierre; se descuentan comentarios y literales para el anidamiento).

### 4.1 Clases > 300 líneas

La regla dice "clase ≤ 150, techo 300". Doy las dos métricas porque las auditorías previas del repo contaron **líneas de archivo** y yo conté **cuerpo de clase**; ambas son lecturas legítimas y dan resultados distintos.

| Archivo | Líneas de archivo | Cuerpo de clase | ¿Documentado? |
|---|---|---|---|
| `community/application/services/CelulaService.java` | **417** | **363** | Sí — `docs/MODULO_COMMUNITY.md:274` |
| `rocks/application/services/RocaDiariaService.java` | **350** | <300 | Sí — `docs/MODULO_PHASECONTRACTS.md:226` |
| `academy/application/services/CatalogoAcademyService.java` | **341** | <300 | Sí — `docs/MODULO_ACADEMY.md:527` |
| `calendar/application/services/EventoService.java` | **340** | ~293 | Sí — `docs/MODULO_CALENDAR.md:237` |
| `community/application/services/PublicacionMuroService.java` | **332** | <300 | Sí — `docs/MODULO_COMMUNITY.md:274` |
| `users/…/persistence/participante/ConsultarResumenParticipacionPersistenceAdapter.java` | **304** | <300 | Sí — `docs/MODULO_USERS.md:561` |

**Los seis casos ya están documentados en el repo.** Por cuerpo de clase, **solo `CelulaService` supera el techo duro** (363 > 300); los otros cinco lo cruzan únicamente si se cuentan imports y javadoc. Es un dato relevante para priorizar: hay un incumplimiento duro, no seis. 1.545 de 1.551 archivos están debajo de 300; 27 archivos más están entre 150 y 300 (sobre el objetivo, bajo el techo).

### 4.2 Métodos > 40 líneas — solo 2 en 3.318

| Líneas | Ubicación |
|---|---|
| **49** | `calendar/application/services/RecordatorioService.java:112-160` — `generarParaEvento()` |
| **47** | `academy/application/services/ClaseDiariaService.java:130-176` — `buscarClaseDiaria()` |

**Nota metodológica importante:** un primer barrido daba 10 métodos >40 líneas. Ocho eran falsos positivos: declaraciones de `record` con javadoc extenso, que el parser lee como constructor (`SubmitAccountRequestUseCase.java:39` "92 líneas" es el `record SubmitAccountRequestCommand`, verificado abriendo el archivo). Los descarté. **134 métodos superan el objetivo de 20 líneas**, todos por debajo del techo de 40.

### 4.3 Anidamiento — cumplimiento perfecto

**0 métodos** superan 3 niveles de anidamiento, en 3.318 métodos. Este es el resultado más limpio de toda la auditoría.

### 4.4 Parámetros > 4 — 115 métodos reales

Del total de 369 firmas con más de 4 parámetros, **234 son componentes de `record`** (comandos, DTOs, proyecciones) y **20 son constructores de inyección**. Los componentes de `record` **no los cuento como violación**: §5.4.8 nombra el `record` como la solución prescrita ("4 → si no, un `record` de comando"), y contar sus componentes sería castigar el arreglo. Quedan **115 métodos reales**. Los peores:

| Params | Ubicación | Método |
|---|---|---|
| **24** | `calendar/domain/model/evento/Evento.java:97` | `rehydrate` |
| **20** | `habits/domain/model/habito/Habito.java:84` | `rehydrate` |
| **20** | `calendar/domain/model/evento/Evento.java:65` | `crear` |
| **18** | `rocks/domain/model/rocadiaria/RocaDiaria.java:69` | `rehydrate` |
| **18** | `evidence/domain/model/evidencia/Evidencia.java:86` | `rehydrate` |
| **18** | `calendar/domain/model/evento/Evento.java:84` | `actualizar` |
| **17** | `calendar/domain/model/evento/Evento.java:132` | `aplicarCambios` |
| **16** | `onboarding/domain/model/grabacionv90/GrabacionV90.java:71` | `rehydrate` |
| **16** | `habits/domain/model/guia/GuiaHabito.java:47` | `rehydrate` |

**La gran mayoría son `rehydrate`**, el factory que el adaptador de persistencia usa para reconstruir un agregado sin re-validar (documentado como tal en `Evento.java:96`). Es un patrón deliberado y consistente. Aun así, `Evento.rehydrate` con **24 parámetros posicionales** del mismo puñado de tipos (`String`, `Integer`, `boolean`, `UUID`) es un riesgo real de invertir dos argumentos sin que el compilador diga nada — que es exactamente el daño que la regla de ≤4 parámetros existe para prevenir. `Evento.crear` (20) y `Evento.actualizar` (18) **no** son `rehydrate`: son API de negocio y ahí la regla aplica sin atenuante.

También hay **20 constructores de inyección** por encima de 4: los peores son `AccountRequestService` (15, `users/application/services/AccountRequestService.java:75`), `CelulaService` (13, `:74`) y `RecordatorioService` (13, `calendar/application/services/RecordatorioService.java:76`). `docs/MODULO_COMMUNITY.md:266` ya observa este punto para los controllers y decide no tratarlo como hallazgo grave; el mismo criterio aplica acá.

### 4.5 Clases con más de 10 métodos públicos — 13

| Públicos | Clase | ¿Documentado? |
|---|---|---|
| **22** | `users/domain/model/user/User.java` | No |
| **21** | `shared/web/GlobalExceptionHandler.java` | No |
| **19** | `users/application/services/ParticipacionProgramaService.java` | No |
| **18** | `community/application/services/CelulaService.java` | Sí — `MODULO_COMMUNITY.md:274` |
| **18** | `academy/domain/model/curso/Curso.java` | No |
| **17** | `academy/domain/model/curso/Leccion.java` | No |
| **14** | `community/…/rest/celula/CelulaAdminController.java` | Sí |
| **14** | `academy/domain/model/asignacion/AsignacionCurso.java` | No |
| **13** | `users/domain/model/participante/ParticipacionPrograma.java` | No |
| **12** | `habits/domain/model/santuario/RachaSinCelular.java` | No |
| **12** | `community/application/services/PublicacionMuroService.java` | Sí |
| **11** | `habits/domain/model/habito/Habito.java` | No |
| **11** | `community/…/rest/publicacion/WallController.java` | No |

Dos matices honestos sobre esta lista:

- **`GlobalExceptionHandler` (21) es un falso positivo de la regla.** Sus 21 métodos son 21 `@ExceptionHandler`, uno por tipo de excepción. La regla de §5.4.8 apunta a SRP ("no un `UserService` con 30 métodos"); un `@RestControllerAdvice` tiene por definición un método por excepción. Yo no lo trataría como deuda.
- **En las clases de `domain/` (`User`, `Curso`, `Leccion`, `AsignacionCurso`, `Habito`…) el conteo es de métodos escritos a mano**, no de getters de Lombok (el analizador lee fuente, no bytecode). Son métodos de negocio con nombre de intención (`suspend`, `reactivate`, `solicitarBaja`, `cancelarBaja`, `canManageRoles`…). Que un agregado rico tenga 22 comportamientos es tensión con el techo literal, pero no es el síntoma que la regla busca cazar.

---

## 5. Eje 4 — Programación funcional (§5.4.7)

### 5.1 Hallazgo (bajo) — `Optional` como componente de `record`, 1 caso

§5.4.7, en la lista de "No": *"`Optional` como **campo de clase o parámetro de método**. Es un tipo de retorno, no un modelo de datos."*

`habits/application/ports/in/audioterapia/ConsultarAudioterapiaSemanalUseCase.java:16`:

```java
record EstadoAudioterapia(Optional<Integer> semanaActual, Optional<AudioResuelto> audio) {
```

Un componente de `record` es un campo. Es el **único** caso en 1.551 archivos. Cosmético, pero es la regla textual y no cuesta nada arreglarlo (un `record` con dos campos nullable y un javadoc, o un `sealed interface` con las dos variantes, que es lo que §5.4.7 recomienda para "resultados con variantes cerradas").

Aclaración: `ClaseDiariaService.java:130`, `ProximoEventoService.java:73` y `GuiaHabitoAdminService.java:179` aparecen en un grep ingenuo de "Optional como campo", pero son **métodos privados que retornan `Optional`** — uso correcto y explícitamente autorizado. No son hallazgos.

### 5.2 Hallazgo (bajo) — 1 cadena de stream de 5 pasos

§5.4.7: *"Cadenas de `.stream().map().filter().flatMap()` de más de ~3 pasos: extraer a un método privado con nombre."*

`chat/application/services/MiembroService.java:67-74`:

```java
List<MiembroResumen> candidatos = resolverParticipantesDe(global).stream()
        .filter(u -> u.status() == UserStatus.ACTIVE)
        .filter(u -> !u.id().equals(actorId))
        .map(MiembroService::aResumen)
        .filter(m -> coincideConLaBusqueda(m, query))
        .sorted(POR_NOMBRE)
        .toList();
```

Cinco pasos intermedios. Atenuante real: cada paso tiene comentario inline explicando el porqué, y los predicados no triviales ya están extraídos (`coincideConLaBusqueda`, `POR_NOMBRE`). Es la mejor versión posible de una cadena que igualmente excede el límite escrito.

Descarto como falsos positivos las cadenas de `DashboardRocasResponse.java:29-30` y `RankingAgregadoResponse.java:21`: verificado abriendo los archivos, son varias cadenas **independientes** de 2 pasos (`.stream().map(X::from).toList()`) sobre campos distintos dentro de un mismo constructor de `record`, no una cadena de 7.

### 5.3 Lo que se cumple

- **`forEach` con efecto secundario (guardar/publicar/eliminar): 0 casos.** Barrido sobre todos los `.forEach(` del proyecto.
- **Excepciones checked dentro de lambdas (`try` dentro de `-> { }`): 0 casos.**
- **`map()` con efecto: 1 caso marginal**, `academy/…/persistence/progreso/ProgresoLeccionPersistenceAdapter.java:104`. Es `Optional.map(...).orElseGet(() -> …save(…))`, no `Stream.map`, y el efecto está en el `orElseGet` (un upsert), que es el lugar semánticamente correcto. **No lo cuento como violación**; lo dejo anotado por transparencia del método.
- **`Optional` como retorno de los puertos de lectura:** en los **183 puertos `out`** del proyecto no hay ni un solo método `find*`/`buscar*`/`obtener*` que devuelva un objeto de dominio desnudo (nullable). Todos devuelven `Optional`, `List`, `Set`, `Page` o primitivo. Cumplimiento total.
- **`record` para value objects y comandos:** 131 comandos declarados como `record`, de los cuales **126 archivos usan `SelfValidating`** — el patrón de §5.4.3 nivel 2 (comando self-validating que explota en el constructor venga de donde venga la invocación). Está implementado en serio, no de adorno.

### 5.4 Hot path (§5.3.5): `for` plano vs streams — se respeta

La regla pide evitar streams en el filtro de autenticación. El camino real de autenticación es `shared/web/security/ActorAutenticadoArgumentResolver.java:36-59` más `users/…/web/security/SesionWebAdapter.java:61-68`. **Ninguno de los dos usa streams**: son `if` planos sobre `SecurityContextHolder` y una lectura de header. La regla se respeta, aunque en rigor **se respeta trivialmente** — no hay ninguna colección que iterar en ese camino, así que la tentación del stream nunca se presentó. No es una decisión de optimización, es que el problema no existe.

Observación adyacente (no es un eje que me pidieran, la dejo por si sirve): §5.3.5 describe una caché Caffeine de `{role, status}` en el filtro. **No existe todavía**: `ActorAutenticadoArgumentResolver` resuelve el `UserId` y nada más — no carga rol ni estado, y el guard de rol vive dentro de cada servicio. No es una violación de §5.4, es una pieza de §5.3.5 aún no construida.

---

## 6. Eje 5 — Nombres (§5.4.8)

### 6.1 Hallazgo (muy bajo) — 1 nombre de la lista prohibida

`users/infrastructure/adapter/out/oauth/FacebookIdentidadAdapter.java:143`:

```java
private record FacebookUserInfo(String id, String email, String name) { }
```

`Info` está en la lista de prohibidos de §5.4.8. Atenuante fuerte: es un `record` **privado** que modela literalmente la respuesta del endpoint `/me` de la Graph API de Facebook, donde el nombre del recurso remoto *es* "user info". Cumple el espíritu de la regla (no es un basurero: 3 campos, un uso, alcance privado) e incumple su letra. `FacebookPerfil` cerraría el punto sin costo.

Nota de §2.2: este caso **no lo habría detectado el `ArchitectureTest`** ni aunque estuviera en el paquete correcto — la regla de la línea 81-90 no incluye `Info`.

### 6.2 Lo que se cumple, y es notable

- **`Util` / `Utils` / `Helper` / `Manager` / `Processor` / `Data`: 0 clases** en todo el proyecto, ni de primer nivel ni anidadas.
- **Puertos por intención de negocio: 183 de 183.** Ni un solo puerto en `application/ports/out` contiene `Jpa`, `Sql`, `Redis`, `Http`, `Rest`, `Gemini`, `Supabase`, `S3`, `Drive`, `Postgres`, `Mongo`, `Kafka` ni `Jdbc` en su nombre. Es literalmente la *"conversación con propósito"* de Cockburn que §5.1.1 cita, sostenida a lo largo de 15 módulos.
- **Adaptadores sí nombran la tecnología**, de forma consistente: `*PersistenceAdapter`, `*RedisAdapter`, `S3AlmacenamientoAdapter`, `GoogleIdentidadAdapter`, `AppleIdentidadAdapter`, `SmtpEnviarEmailAdapter`, `NoOp*Adapter`. La asimetría que §5.4.8 pide (puerto ciego a la tecnología, adaptador que la declara) está aplicada en todos lados.

---

## 7. Eje 6 — Logging y PII (§5.4.9)

### 7.1 Hallazgo (ALTO, el más grave de la auditoría) — el correo completo viaja al log y al cliente

**No está documentado en ninguna parte del repo.** Verificado con `grep` sobre `docs/` completo.

Cadena verificada, tres saltos:

**Salto 1 — el dominio mete el dato en el mensaje.** `users/domain/model/user/Email.java:21`:

```java
throw new IllegalArgumentException("Formato de email invalido: " + raw);
```

**Salto 2 — el handler global lo captura.** `shared/web/GlobalExceptionHandler.java:95-98`:

```java
@ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException ex) {
    return respond(HttpStatus.BAD_REQUEST, ex.getMessage());
}
```

**Salto 3 — `respond` lo escribe al log y lo devuelve al cliente.** `GlobalExceptionHandler.java:206-209`:

```java
private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, String message) {
    log.warn("{} -> {}: {}", status.value(), status.getReasonPhrase(), message);
    return ResponseEntity.status(status).body(ApiErrorResponse.of(message));
}
```

Resultado: cualquier alta con un correo mal tipeado deja `WARN 400 -> Bad Request: Formato de email invalido: juan.perez@gmail.con` en el log de producción. §5.4.9 lo prohíbe con esas palabras: *"**Nunca loguear**: … emails completos"*. Y es doblemente indeseable porque **el mismo string vuelve en el cuerpo HTTP**, así que el dato reflejado también sale por la respuesta.

Es además exactamente el escenario que §5.4.9 anticipa al decir *"El punto de fuga clásico es el `GlobalExceptionHandler` volcando el request entero en un 500 — auditarlo explícitamente"*. Acá no vuelca el request entero: vuelca el fragmento del request que la excepción de dominio decidió llevarse consigo. La causa raíz está en `Email.java:21`, no en el handler.

Arreglo mínimo: quitar `+ raw` del mensaje (el campo ya viaja identificado por Bean Validation en `MethodArgumentNotValidException`, que sí dice *qué* campo falló sin repetir el valor).

### 7.2 Hallazgo (medio) — superficie latente de PII en `toString()` de 6 entidades JPA

Este es **el mismo hallazgo que el equipo ya reconoció y corrigió parcialmente**: `docs/INFORME_2026-08-28.md` §3.2 detectó que `@Data` genera un `toString()` con todos los campos y aplicó `@ToString(exclude = {...})` a `GrabacionV90JpaEntity` y `RespuestaOnboardingJpaEntity`. Verifiqué que ambas correcciones están aplicadas.

Aplicando ese mismo criterio al resto del proyecto, **quedan 6 entidades con `@Data` sin acotar que exponen los tipos de dato que §5.4.9 nombra**:

| Entidad | Campos expuestos por el `toString()` generado |
|---|---|
| `users/…/persistence/user/UserJpaEntity.java:34,42,44` | `email`, `nombreCompleto` |
| `users/…/persistence/accountrequest/AccountRequestJpaEntity.java:20,30,32,34` | `email`, `nombreCompleto`, `telefono` |
| `notifications/…/persistence/tokenpush/TokenPushJpaEntity.java:21,31` | `token` (credencial de push) |
| `evidence/…/persistence/EvidenciaJpaEntity.java:27,51` | `contenidoTexto` (contenido de evidencia) |
| `habits/…/persistence/diario/EntradaDiarioJpaEntity.java:21,37,43` | `contenidoTexto`, `transcripcion` |
| `rag/…/persistence/conversacion/MensajeRenasiaJpaEntity.java:28,42` | `contenido` (conversación con Renasia) |

Mismo encuadre honesto que usó el informe del 28: **no verifiqué una fuga activa** — ningún `log.*` del proyecto imprime una de estas entidades (lo comprobé con grep sobre los 56 sitios de logging). Es superficie latente: un log futuro de la entidad completa, o un mensaje de Hibernate que la incluya, la abriría. `UserJpaEntity.java:24-25` incluso lleva un comentario justificando `@Data` "porque no tiene relaciones perezosas" — razonamiento correcto sobre el riesgo de JPA, pero que no aborda el riesgo de PII que sí es el tema de §5.4.9.

### 7.3 `GlobalExceptionHandler` — auditado específicamente, y está bien construido

Fuera del problema heredado de §7.1, este archivo hace bien lo que §5.4.9 pide:

- **No vuelca el request en ningún handler.** Ni body, ni headers, ni query params.
- **No manda stacktraces al cliente.** Los dos únicos handlers que loguean la excepción completa (`:86` login social, `:201` violación de integridad) lo hacen contra el log del servidor y devuelven un mensaje genérico. El javadoc de `:196-197` explica por qué el detalle de la restricción no se expone ("revelaría nombres de tablas e índices"). Es un razonamiento de seguridad correcto, escrito.
- **Los handlers existen precisamente para evitar fugas.** El javadoc de `:110-115` y `:149-159` documenta que sin ellos Spring devolvía el Whitelabel con stacktrace completo (paquetes internos, versión de Spring/Tomcat, cadena de filtros de seguridad), y que eso apareció en pruebas reales (E-38 de la bitácora). El handler de `DateTimeException` (`:176`) captura la clase padre en vez de ir agregando subclases — buena decisión.

**Una observación, no un hallazgo:** no existe `@ExceptionHandler(Exception.class)` ni configuración explícita de `server.error.include-stacktrace` en `application.yaml`. Cualquier excepción no mapeada cae en el manejo por defecto de Spring Boot. El default de Boot para `include-stacktrace` es `never`, así que **no afirmo que haya fuga**; señalo que la protección depende de un default del framework y no de una decisión escrita del proyecto, en un archivo cuyo propio javadoc documenta dos incidentes previos de ese tipo exacto.

### 7.4 Lo que se cumple, y muy bien

- **`domain/` no loguea: 0 apariciones** de `log.`, `Logger`, `@Slf4j` o `System.out` en los 15 `domain/`. Cumplimiento perfecto de la regla más dura de §5.4.9.
- **Distribución por capa** exactamente como §5.4.9 prescribe: 0 en `domain/`, 0 en `api/`, **16 en `application/`** (decisiones de negocio), **40 en `infrastructure/`** (latencia y fallos de lo externo).
- **Los adaptadores de correo son un ejemplo a copiar.** `users/…/out/email/NoOpEnviarEmailAdapter.java:43-78` loguea la *longitud* del link y del token en vez del valor, con un comentario que cita la regla: `log.info("… link de {} caracteres armado", link.length())`. `SmtpEnviarEmailAdapter.java:91-95` loguea el asunto y el `getClass().getSimpleName()` de la causa, **nunca el destinatario ni el cuerpo**. Alguien pensó esto a propósito.
- **0 logs con token, JWT, `id_token`, contraseña, hash, secreto o cookie.** Los diez `log.*` que un grep de palabras sensibles marca resultaron, al abrirlos, todos correctos: cuentan tokens (`tokens.size()`), reportan que un `id_token` fue inválido sin imprimirlo (`AppleIdentidadAdapter.java:139`, `GoogleIdentidadAdapter.java:103`), o loguean una ruta de storage (`S3AlmacenamientoAdapter.java:71`).
- **Logger uniforme:** 40 clases con `LoggerFactory.getLogger`, **0 con `@Slf4j`**. Consistente.

---

## 8. Eje 7 — Estrategia de mapeo (§5.4.1, §5.4.5)

**La regla se cumple. Y se cumple de una forma que conviene registrar: no hay MapStruct en ningún lado.**

- **`@Mapper` en el proyecto: 0.** Cero mappers generados, en 1.551 archivos.
- **No hay ni un `@Mapper` apuntando a un DTO de salida HTTP** — que es exactamente el riesgo que §5.4.5 quiere evitar ("MapStruct mapea por nombre, automáticamente: agregar un campo al dominio lo filtra solo a la respuesta HTTP").
- **Los 123 DTOs `*Response` se construyen con factories estáticos escritos a mano** (`static X from(...)`), proyección explícita campo por campo. Es lo que §5.4.1 pide para la frontera de salida ("Two-Way, a mano").
- **Hay 59 clases `*Mapper` escritas a mano**, y **58 viven en `adapter/out/persistence`**. La única fuera es `calendar/infrastructure/adapter/in/rest/evento/EventoWireMapper.java` — está en la frontera web, que es donde §5.4.5 manda mapear **a mano**, y lo hace a mano (no es `@Mapper`). Conforme.
- **El `pom.xml` sí tiene MapStruct 1.6.3 configurado correctamente**: la cadena `lombok → lombok-mapstruct-binding → mapstruct-processor` en `annotationProcessorPaths` (líneas 336-347) y el mismo set replicado en `default-testCompile` (359-370), tal como §5.4.5 exige. La configuración está bien; simplemente nunca se usó.

**Observación (no hallazgo):** la decisión escrita D-5 (`docs/MODULOS_A_AVANZAR.md:225`) y §5.4.5 dicen "MapStruct **sí** en `adapter/out/persistence`". El código eligió mano también ahí. Esa divergencia **ya está documentada**, con el mismo diagnóstico, en `docs/MODULO_CHAT.md:153` (*"no es una violación arquitectónica, es una elección de herramienta distinta a la documentada como default"*) y en `docs/MODULO_ONBOARDING.md:372`. Coincido: el riesgo que §5.4.5 quería evitar era el mapeo automático **hacia afuera**, y ese lado está cubierto con creces. El efecto secundario a registrar es que MapStruct queda como una dependencia y una configuración de build que nadie ejercita — y el "riesgo abierto" que §5.4.5 anota sobre 1.6.3 y JDK 25 es, en la práctica, inexistente.

---

## 9. Donde la regla escrita se contradice a sí misma

Esto no es un hallazgo contra el código; es un pedido de decisión, señalado aparte como se me indicó.

**`@Data` en entidades JPA: §6 y §5.4.5 dicen cosas opuestas.**

- **§6** (tabla de stack, fila "Boilerplate"): *"**Nunca `@Data` en entidades JPA**"*. Sin matices.
- **§5.4.5** (párrafo "Dónde SÍ van `@Entity`/`@NoArgsConstructor`"): cita `buckpal` con aprobación — *"`AccountJpaEntity` (con `@Entity @Table @Data @AllArgsConstructor @NoArgsConstructor`) vive en `adapter/out/persistence/`"* — presentándolo como el patrón a seguir.

El código eligió §5.4.5: **78 entidades JPA usan `@Data`**, y `users/…/persistence/user/UserJpaEntity.java:24-25` lleva un comentario que argumenta la decisión citando §5.4.5 nominalmente. La elección es defendible y está razonada. Pero mientras §6 diga "nunca", cualquier auditoría futura (humana o automática) va a volver a reportar esas 78 clases. Conviene cerrar la contradicción en el documento — y, si se elige §5.4.5, agregar la salvedad de PII que §7.2 hace necesaria: `@Data` es seguro en una entidad JPA **salvo que tenga campos personales, donde hace falta `@ToString(exclude = …)`**, que es la conclusión a la que el propio equipo ya llegó en `INFORME_2026-08-28` §3.2.

Segundo punto menor: **§5.4.8 no distingue entre "método" y "componentes de un `record`"**. Como el `record` es la solución que la misma regla prescribe para el exceso de parámetros, contar sus componentes como violación es autocontradictorio. Yo los excluí (§4.4); vale dejarlo escrito para que la próxima medición dé el mismo número.

---

## 10. Qué verifiqué y qué inferí

**Verificado abriendo el archivo y leyendo el código** (todo lo citado con línea): las 6 reglas del `ArchitectureTest` y su correspondencia con la estructura real de paquetes; los 9 handlers de `CelulaAdminController`/`CohorteAdminController`; `TestimonioController.crear`; `RankingController` y `RachaController`; `EventoController`; `GlobalExceptionHandler` íntegro; `Email.java`; `ActorAutenticadoArgumentResolver` y `SesionWebAdapter` íntegros; `NoOpEnviarEmailAdapter` y `SmtpEnviarEmailAdapter`; `MiembroService`; `User.java`; `EstadoAudioterapia`; `FacebookIdentidadAdapter:143`; `Evento.rehydrate`; `DashboardRocasResponse` y `RankingAgregadoResponse` (para descartarlos); `ProgresoLeccionPersistenceAdapter:104`; `pom.xml`.

**Medido con herramienta, no leído uno por uno** (y por lo tanto sujeto a error de parseo, aunque los casos límite se verificaron a mano): los conteos de líneas de método y clase, anidamiento, parámetros y métodos públicos sobre los 3.318 métodos; los conteos de handlers por controller y de dependencias invocadas por handler; los barridos de `forEach`/`map` con efecto y de `try` dentro de lambda. Cuando la herramienta dio 10 métodos >40 líneas, abrí los 10 y descarté 8 falsos positivos (§4.2) — señal de que el parser tiene ese sesgo y de que los números de esa clase hay que leerlos con esa reserva.

**Inferido, no verificado:**
1. Que el default de Spring Boot para `server.error.include-stacktrace` es `never` (§7.3). No lo comprobé ejecutando la app — no corrí Maven. Si importa, se confirma con un request a una ruta que lance una excepción no mapeada.
2. Que las anotaciones de Lombok son invisibles para ArchUnit por ser `RetentionPolicy.SOURCE` (§2.2). Es una propiedad conocida de las dos herramientas, no algo que haya verificado ejecutando el test.

**Fuera de alcance por instrucción:** no corrí `./mvnw`, así que **no sé si la suite está en verde hoy**. Todos los hallazgos son de lectura estática; ninguno se contrastó contra un test ejecutado.

**Advertencia sobre números de línea en `users`:** durante esta auditoría había otro trabajo en curso sobre el módulo `users` (feature de avatar: 12 archivos de `main` y 7 de `test` modificados sin commitear, más `V13__usuarios_avatar_ruta.sql`). De los archivos que cito, el único tocado por ese trabajo es **`AutenticacionController.java`** (§3.5, líneas 69/75/81/134) — ahí las líneas pueden haberse corrido, aunque el hallazgo (inyección de `SesionWebAdapter`, que declaro **conforme**) no cambia. `Email.java`, `User.java`, `UserJpaEntity.java`, `AccountRequestJpaEntity.java`, `AccountRequestService.java` y `GlobalExceptionHandler.java` — los archivos de los hallazgos 1 y 3 del ranking — **no** están entre los modificados; esas citas son firmes.

---

## 11. Orden sugerido de atención

| Prioridad | Hallazgo | Dónde | Costo |
|---|---|---|---|
| **1** | Correo completo al log y al cliente | `Email.java:21` | Trivial: borrar `+ raw` |
| **2** | 9 handlers orquestan dos casos de uso | `CelulaAdminController`, `CohorteAdminController` | Medio: que el caso de uso de escritura devuelva la vista |
| **3** | `toString()` con PII en 6 entidades JPA | §7.2 | Bajo: `@ToString(exclude=…)`, igual que se hizo con las otras 2 |
| **4** | `if` de negocio + doble caso de uso | `TestimonioController.java:48` — **ya documentado** | Medio: partir en dos endpoints |
| **5** | `Clock` decidiendo en el controller | `RankingController:38,47`; `RachaController:36,53,71,79` | Bajo: mover el default al caso de uso |
| **6** | `CelulaService` 363 líneas de clase | **ya documentado** | Medio |
| **7** | `Evento.rehydrate` (24 params), `Evento.crear` (20) | `Evento.java:65,97` | Medio: un `record` de reconstrucción |
| **8** | Reglas de §5.4 sin cobertura en CI | §2.2 | Medio: 3 reglas ArchUnit nuevas cubren los ítems 2, 3 y parte del 1 |
| **9** | `Optional` como componente de `record` | `ConsultarAudioterapiaSemanalUseCase.java:16` | Trivial |
| **10** | Cadena de stream de 5 pasos | `MiembroService.java:67` | Trivial |
| **11** | `FacebookUserInfo` | `FacebookIdentidadAdapter.java:143` | Trivial |
| **12** | Contradicción §6 vs §5.4.5 sobre `@Data` | `CLAUDE.MD` | Decisión, no código |
