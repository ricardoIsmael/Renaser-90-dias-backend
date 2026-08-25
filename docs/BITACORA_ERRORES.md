# Bitácora de errores y bugs

**Regla:** todo error, bug o comportamiento inesperado se registra acá, **aunque se haya resuelto en dos minutos y aunque parezca una tontería**. Los errores de configuración y de entorno son justamente los que se repiten.

**Objetivo:** que la segunda vez cueste un minuto en vez de media hora.

Ver `CLAUDE.MD` §0.5.

---

## Cómo registrar una entrada

Copiar esta plantilla al final del archivo, con el siguiente número:

```markdown
## E-NN — Título corto y buscable

- **Fecha:** AAAA-MM-DD
- **Dónde:** archivo, módulo o herramienta
- **Síntoma:** el mensaje de error LITERAL, copiado tal cual. No parafrasear —
  el valor de esta bitácora es poder buscar el texto exacto que aparece en pantalla.
- **Causa real:** qué lo provocaba de verdad (no la primera hipótesis)
- **Solución:** qué se hizo, con el comando o el diff concreto
- **Cómo evitarlo:** la regla o el chequeo que impide que vuelva
```

**Buscá acá antes de investigar un error.** `Ctrl+F` con el texto del mensaje.

---

## E-01 — `release version 25 not supported`

- **Fecha:** 2026-08-22
- **Dónde:** `./mvnw compile`
- **Síntoma:**
  ```
  [ERROR] Failed to execute goal ...maven-compiler-plugin:3.15.0:compile
  Fatal error compiling: error: release version 25 not supported
  ```
- **Causa real:** el `pom.xml` pide Java 25, pero `JAVA_HOME` apuntaba al JBR de Android Studio, que es **JDK 21**. No había ningún JDK 25 instalado.
- **Solución:** `winget install --id EclipseAdoptium.Temurin.25.JDK` y `JAVA_HOME` apuntando a `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`.
- **Cómo evitarlo:** verificar `java -version` antes de culpar al código. El mensaje habla del *compilador*, no del proyecto.
- **Truco útil:** para type-checkear sin el JDK correcto, `./mvnw -Djava.version=21 test` override la propiedad sin tocar el `pom.xml`. **No valida el target real** — es diagnóstico, no verificación.

---

## E-02 — `JAVA_HOME` de usuario pisando al de máquina

- **Fecha:** 2026-08-22
- **Dónde:** entorno de Windows
- **Síntoma:** el instalador de Temurin dejó `JAVA_HOME` correcto, pero una terminal nueva seguía tomando el JDK 21 y el build seguía fallando con E-01.
- **Causa real:** en Windows, `JAVA_HOME` puede existir en **dos niveles**: usuario y máquina. **El de usuario gana.** El instalador escribió el de máquina; el de usuario seguía apuntando a Android Studio.
- **Solución:**
  ```powershell
  [Environment]::SetEnvironmentVariable('JAVA_HOME','C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot','User')
  ```
- **Cómo evitarlo:** revisar **ambos** niveles, no solo `$env:JAVA_HOME`:
  ```powershell
  [Environment]::GetEnvironmentVariable('JAVA_HOME','User')
  [Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')
  ```
- **Ojo:** un cambio de variable de entorno **no afecta a procesos ya abiertos**, incluidos terminales y el IDE. Hay que abrir una terminal nueva.

---

## E-03 — Lombok y MapStruct generando mappers vacíos sin fallar el build

- **Fecha:** 2026-08-22
- **Dónde:** `pom.xml`, `maven-compiler-plugin`
- **Síntoma:** *(preventivo — se detectó por revisión antes de que ocurriera)* el build compila en verde, pero los mappers generados quedan vacíos o incompletos y los campos llegan `null` en runtime.
- **Causa real:** desde Lombok **1.18.16**, si Lombok y MapStruct están en el mismo `annotationProcessorPaths` sin `lombok-mapstruct-binding`, MapStruct puede correr **antes** de que Lombok genere los getters/setters. No ve los métodos y genera un mapper vacío — **sin error**.
- **Solución:** agregar `org.projectlombok:lombok-mapstruct-binding` respetando el orden `lombok → binding → mapstruct-processor`.
- **Cómo evitarlo:** si un mapper devuelve objetos con campos en `null` sin motivo aparente, **mirar el código generado en `target/generated-sources/annotations/`** antes que el código propio.

---

## E-04 — Anotaciones que no corren en JDK 23+

- **Fecha:** 2026-08-22
- **Dónde:** `pom.xml`
- **Síntoma:** Lombok y MapStruct no generan nada; los métodos "no existen" al compilar.
- **Causa real:** **desde JDK 23, el annotation processing implícito está deshabilitado por seguridad.** javac ya no escanea el classpath buscando procesadores.
- **Solución:** `<maven.compiler.proc>full</maven.compiler.proc>` en `properties`.
- **Cómo evitarlo:** al subir de JDK, revisar si el proyecto depende de procesadores de anotaciones. Es un cambio de comportamiento silencioso, no un error de compilación claro.

---

## E-05 — `illegal escape character` en un regex de Java

- **Fecha:** 2026-08-22
- **Dónde:** `users/domain/Email.java`
- **Síntoma:**
  ```
  [ERROR] Email.java:[15,77] illegal escape character
  ```
- **Causa real:** el archivo se creó con un *heredoc* de shell que **colapsó `\\s` a `\s`**. En un string de Java, `\s` no es un escape válido — hay que escribir `\\s`.
- **Solución:** reescribir esa línea con un editor de archivos, no por shell.
- **Cómo evitarlo:** **no crear archivos Java con backslashes (regex, rutas) usando heredocs de shell.** Usar la herramienta de escritura de archivos. Si ya pasó: `sed -n '15p' archivo.java | cat -A` muestra el contenido literal.

---

## E-06 — `PostgreSQLContainer does not take parameters`

- **Fecha:** 2026-08-22
- **Dónde:** `TestcontainersConfiguration.java`
- **Síntoma:**
  ```
  type org.testcontainers.postgresql.PostgreSQLContainer does not take parameters
  cannot use '<>' with non-generic class org.testcontainers.postgresql.PostgreSQLContainer
  ```
- **Causa real:** en **Testcontainers 2.x**, `PostgreSQLContainer` dejó de ser genérico. El `PostgreSQLContainer<?>` de todos los tutoriales es de la 1.x.
- **Solución:** quitar los parámetros de tipo: `PostgreSQLContainer` y `new PostgreSQLContainer(...)`.
- **Cómo evitarlo:** con librerías que cambiaron de major, el código de ejemplo de internet suele ser de la versión anterior. Confirmar la versión resuelta con `./mvnw dependency:list`.

---

## E-07 — Dos beans `@ServiceConnection` de Postgres en conflicto

- **Fecha:** 2026-08-22
- **Dónde:** `TestcontainersConfiguration.java` (generado por Spring Initializr)
- **Síntoma:** el Initializr generó **dos** beans `PostgreSQLContainer`, ambos con `@ServiceConnection` — uno `postgres:latest` y otro `pgvector/pgvector:pg16`. Dos datasources compitiendo.
- **Causa real:** el Initializr agrega un contenedor por cada starter que lo pida. Con `data-jpa` **y** `vector-store-pgvector`, agregó dos.
- **Solución:** dejar **uno solo**, el de pgvector (es Postgres completo + la extensión), marcado como sustituto compatible:
  ```java
  new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")
          .asCompatibleSubstituteFor("postgres"))
  ```
- **Cómo evitarlo:** revisar siempre lo que genera el Initializr antes de construir encima. No es código verificado.

---

## E-08 — ArchUnit fallando por reglas que no matchean nada

- **Fecha:** 2026-08-22
- **Dónde:** `ArchitectureTest.java`
- **Síntoma:**
  ```
  Rule '...' failed to check any classes. This means either that no classes have been
  passed to the rule at all, or that no classes passed to the rule matched the `that()` clause.
  ```
- **Causa real:** **no era una violación de arquitectura.** ArchUnit falla por defecto si una regla no evalúa ninguna clase, para avisarte de patrones de paquete mal escritos. Las reglas sobre `application/` y `adapter/in/web` no matcheaban porque **esos paquetes todavía no existen**.
- **Solución:** `.allowEmptyShould(true)` en esas dos reglas, con un `TODO` para quitarlo al crear el primer caso de uso.
- **Cómo evitarlo:** **no** poner `allowEmptyShould(true)` en todas las reglas "por las dudas" — enmascara patrones de paquete mal escritos, que es exactamente de lo que ArchUnit te está avisando. Solo en las que legítimamente están vacías, y con `TODO`.

---

## E-09 — Un rol faltante por una contradicción entre documentos

- **Fecha:** 2026-08-22
- **Dónde:** `users/domain/UserRole.java`
- **Síntoma:** el enum se construyó con **4 roles** cuando el negocio tiene **5**: faltaba `MENTOR_LEAD`.
- **Causa real:** `CLAUDE.MD` se contradecía. La tabla de §5 listaba `Alchemist/Admin/Mentor/**MentorLead**/Trainee` (cinco), mientras §5.3.1 decía *"Los 4 roles"*. Se construyó sobre §5.3.1.
- **Solución:** corregir §5.3.1, agregar la nota de corrección, y registrar `MENTOR_LEAD` como deuda del código.
- **Cómo evitarlo:** **dos razones, y las dos son reglas ahora:**
  1. Cuando un documento menciona una lista en más de un lugar, **contrastarlas antes de codificar**. Si difieren, preguntar — no elegir la que aparece primero.
  2. Es el argumento concreto detrás de D-13: con `if (role == ADMIN || role == ALCHEMIST)` esparcido por el código, este error habría obligado a revisar 29 endpoints a mano. Con la matriz en el enum, es un archivo.

---

## E-10 — Contradicciones acumuladas entre documentos

- **Fecha:** 2026-08-22
- **Dónde:** `CLAUDE.MD` y `docs/MODULOS_A_AVANZAR.md`
- **Síntoma:** una revisión completa encontró **siete** inconsistencias: conteo de módulos (13/14/15), Gradle vs Maven, `traineeprofile` como módulo y como no-módulo, "4 perfiles" vs 5, `AccessGuard` con la firma vieja, y el propio `CLAUDE.MD` mostrando el antipatrón `role == ADMIN || role == ALCHEMIST` que otra sección prohibía.
- **Causa real:** decisiones nuevas escritas en una sección sin revisar las secciones viejas que quedaban desactualizadas.
- **Solución:** revisión completa, corrección de las siete, y creación del **registro de decisiones** (`MODULOS_A_AVANZAR.md` §8) como índice único.
- **Cómo evitarlo:** es la regla `CLAUDE.MD` §0.4 — al tomar una decisión, **buscar en el documento todas las menciones del tema** (`grep`) y actualizarlas en el mismo cambio. Un documento que se contradice es peor que uno incompleto: el incompleto se nota, la contradicción se propaga al código (ver E-09).

---

## E-11 — `git clone` en Windows: "Clone succeeded, but checkout failed"

- **Fecha:** 2026-08-24
- **Dónde:** `git clone` de `renaserlab/RenaserPlayStoreCopy` en Windows
- **Síntoma:**
  ```
  fatal: unable to checkout working tree
  warning: Clone succeeded, but checkout failed.
  You can inspect what was checked out with 'git status'
  and retry with 'git restore --source=HEAD :/'
  ```
- **Causa real:** el límite de 260 caracteres de ruta de Windows (MAX_PATH). El repo de la app tiene rutas profundas (android/, node_modules committeados en subcarpetas, assets con nombres largos) que al combinarse con un directorio destino largo superan el límite. El clone de objetos funciona; el checkout de archivos no.
- **Solución:** dentro del repo clonado a medias:
  ```
  git config core.longpaths true
  git checkout -f HEAD
  ```
- **Cómo evitarlo:** habilitarlo global una sola vez: `git config --global core.longpaths true`. Si vuelve a pasar con otra herramienta (no git), el fix a nivel OS es la clave de registro `LongPathsEnabled`.

---

## E-12 — `type "vector" does not exist` con esquema propio

- **Fecha:** 2026-08-24
- **Dónde:** `docs/db/sql/BD_NUEVA_V1.sql` (validación en Postgres 16 + pgvector)
- **Síntoma:**
  ```
  psql:/tmp/bd.sql:1366: ERROR:  type "vector" does not exist
  ```
  a pesar de tener `CREATE EXTENSION IF NOT EXISTS vector;` al comienzo del script.
- **Causa real:** las extensiones se instalan en `public`. El script hacía `SET search_path TO renaser;` (sin `public`), así que al llegar a `embedding vector(768)` el tipo no se resolvía.
- **Solución:** `SET search_path TO renaser, public;` — `public` al final, donde viven las extensiones.
- **Cómo evitarlo:** todo script que use esquema propio + extensiones debe incluir `public` en el search_path (o instalar la extensión `WITH SCHEMA`). Aplica igual a la config de Flyway/JPA (`spring.jpa.properties.hibernate.default_schema` no cubre los tipos de extensión).

---

## E-13 — Git Bash convierte `/tmp` en rutas de Windows dentro de `docker exec`

- **Fecha:** 2026-08-24
- **Dónde:** `docker exec ... psql -f /tmp/bd.sql` desde Git Bash
- **Síntoma:**
  ```
  psql: error: C:/Users/Usuario/AppData/Local/Temp/bd.sql: No such file or directory
  ```
  El archivo SÍ estaba en `/tmp` del contenedor; la ruta llegó traducida a Windows.
- **Causa real:** MSYS/Git Bash traduce automáticamente argumentos que parecen rutas POSIX (`/tmp/...`) a rutas de Windows antes de pasarlas al comando.
- **Solución:**
  ```
  export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
  ```
  antes del `docker exec` (o duplicar la barra: `//tmp/bd.sql`).
- **Cómo evitarlo:** en cualquier comando `docker exec`/`docker run` con rutas del contenedor desde Git Bash, exportar esas variables primero. En PowerShell no pasa.

---

## E-14 — `domain/User.java` importando `jakarta.validation.constraints.Email` como si fuera un tipo

- **Fecha:** 2026-08-24
- **Dónde:** `users/domain/User.java`, working tree sin commitear
- **Síntoma:** el árbol de trabajo tenía `import jakarta.validation.constraints.Email;` y el campo
  `private final Email email;` usando esa anotación de Bean Validation como si fuera la clase
  de dominio. `Email.java` (el `record` propio, con `EmailTest` en verde) estaba borrado.
  No llegó a fallar el build porque nadie corrió `./mvnw clean test` sobre ese estado — lo
  hubiera roto: `EmailTest` no habría compilado (clase `Email` inexistente) y `ArchitectureTest.
  domainIsFrameworkFree` habría fallado (`domain/` no puede depender de `jakarta.validation..`).
- **Causa real:** confusión de nombre — `jakarta.validation.constraints.Email` y el `Email`
  propio de `users.domain` comparten simple name. Al autocompletar el import se tomó el de
  Jakarta en vez de escribir el propio o importarlo explícito por FQN.
- **Solución:** restaurar `users/domain/Email.java` (el `record` con `normalize()`/regex de
  formato) desde el último commit, y sacar el import de Jakarta de `User.java`.
- **Cómo evitarlo:** `ArchitectureTest.domainIsFrameworkFree` ya cubre esto — la razón de que
  no se haya notado antes es no correr los tests después de editar. Regla de CLAUDE.MD §0.2:
  toda tarea que toque código termina con `./mvnw clean test` en verde antes de darla por
  cerrada, no después.

---

## E-15 — `column "estado" is of type renaser.estado_usuario but expression is of type character varying`

- **Fecha:** 2026-08-24
- **Dónde:** `UserJpaEntity`/`AccountRequestJpaEntity`/`MentorProfileJpaEntity`, primer test de integración con Testcontainers
- **Síntoma:**
  ```
  ERROR: column "estado" is of type renaser.estado_usuario but expression is of type character varying
    Hint: You will need to rewrite or cast the expression.
  ```
  A pesar de `@Enumerated(EnumType.STRING)` en el campo. El insert nunca corría en los
  primeros dos tests porque `findById` lee del cache de primer nivel sin flush — el bug
  quedó invisible hasta el tercer test, que usa una query derivada (`findByEmail`) y sí
  fuerza el flush.
- **Causa real:** `@Enumerated(STRING)` solo, sin más, manda el valor por JDBC como
  `varchar`. Postgres no castea implícito varchar→enum nativo en ese contexto.
- **Solución:** agregar `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` (Hibernate 6.3+) junto a
  `@Enumerated(STRING)` en los 4 campos enum de las 3 entidades JPA.
- **Cómo evitarlo:** cualquier columna Postgres de tipo enum nativo (no `varchar`/`text`)
  mapeada a un enum Java necesita las dos anotaciones juntas. Un test que solo usa
  `findById` no lo detecta — forzar al menos una query derivada en el test de integración
  de cada entidad nueva.

---

## E-16 — `relation "event_publication" does not exist` al cerrar el contexto de test

- **Fecha:** 2026-08-24
- **Dónde:** shutdown de cualquier `@SpringBootTest` (Spring Modulith `eventPublicationRegistry`)
- **Síntoma:** `WARN` (no falla el test) al destruir el contexto:
  ```
  ERROR: relation "event_publication" does not exist
  ```
- **Causa real:** `spring-modulith-starter-jpa` espera su propia tabla de outbox
  (`event_publication`) para el patrón de eventos persistidos de §4.4, y esa tabla no
  está en `V1__baseline_renaser.sql` — Modulith trae sus propias migraciones pero no se
  agregó esa ubicación a `spring.flyway.locations`.
- **Solución:** pendiente — no bloquea nada hasta que `users` (o cualquier módulo)
  publique su primer evento de dominio de verdad. Anotado para no reaparecer como sorpresa.
- **Cómo evitarlo:** cuando se implemente el primer `@ApplicationModuleListener` o
  `events().publish(...)`, agregar `classpath:org/springframework/modulith/events/jpa`
  a `spring.flyway.locations` (o el equivalente para el proveedor de outbox elegido).

---

## E-17 — Comando self-validating rechaza datos válidos: "campo: no debe estar vacío" con el campo lleno

- **Fecha:** 2026-08-24
- **Dónde:** los 8 `record ...Command` de `application/ports/in/**`, patrón `SelfValidating.validate(this)` dentro del constructor compacto
- **Síntoma:** llamando `POST /api/v1/account-requests` con un body JSON completo y válido:
  ```json
  {"message":"email: no debe estar vacío, phone: no debe estar vacío, fullName: no debe estar vacío, supabaseUserId: no debe estar vacío", ...}
  ```
  Confirmado que Jackson deserializaba bien (un email con formato inválido SÍ se leía y reportaba el valor real) — el problema era específico de los campos "vacíos".
- **Causa real:** en un **constructor compacto de un `record`**, la asignación implícita de
  los campos (`this.campo = parametro`) pasa **después** de que termina el código que escribís
  en el constructor — no antes, no durante. `SelfValidating.validate(this)` llamaba a Bean
  Validation sobre `this` en ese punto: los getters del record (`email()`, `phone()`...) leían
  los campos todavía sin asignar (null), así que **todo** salía "vacío" sin importar qué se
  mandara. Es la razón por la que ningún test de dominio lo detectó antes: `Email`/`UserId`
  (los otros records con constructor compacto) solo *reasignan el parámetro* (`value = normalize(value)`),
  nunca llaman a un método sobre `this` — por eso a ellos no les pasaba.
- **Solución:** cambiar `SelfValidating` para validar los **argumentos del constructor**
  directamente (`Validator.forExecutables().validateConstructorParameters(...)`), no una
  instancia ya construida. Nuevo uso: `SelfValidating.validateConstructorArgs(MiComando.class, a, b, c)`
  con los parámetros en el mismo orden que el constructor canónico.
- **Cómo evitarlo:** nunca llamar `this.metodoQueSea()` (ni siquiera un getter) dentro del
  cuerpo explícito de un constructor compacto de un record — en ese punto `this` existe como
  objeto pero sus campos todavía no. Válido solo reasignar los parámetros locales. Se agregó
  `SubmitAccountRequestCommandTest` como test de regresión — construye un comando con datos
  válidos y verifica que NO explote; sin este tipo de test, el bug es invisible para cualquier
  test que solo pruebe el dominio (`AccountRequest`, no records `Command`).

---

## E-18 — `deleteBy...` + `saveAll` en la misma transacción no inserta nada

- **Fecha:** 2026-08-24
- **Dónde:** `points` — `SpringDataRankingAprendizRepository` (reemplazo de snapshots de ranking)
- **Síntoma:** dos `reemplazar()` seguidos en la misma transacción: el segundo `saveAll` no insertaba filas, sin error alguno (silencioso).
- **Causa real:** un `@Query` de DELETE con `@Modifying` **no limpia el contexto de persistencia**: las entidades borradas quedan "fantasma" en la caché de primer nivel de Hibernate y el `saveAll` posterior cree que ya existen.
- **Solución:** `@Modifying(clearAutomatically = true)` en el DELETE. Test de idempotencia que cubre el doble reemplazo.
- **Cómo evitarlo:** todo `@Modifying` de DELETE/UPDATE masivo seguido de escrituras en la misma transacción lleva `clearAutomatically = true`. Detectado por el agente constructor de `points` en revisión propia.

---

## E-19 — `repository.save()` difiere el INSERT y las violaciones de FK no se traducen

- **Fecha:** 2026-08-24
- **Dónde:** `support` — `TicketMentorPersistenceAdapter.save()`
- **Síntoma:** guardar un ticket con un participante inexistente NO lanzaba error en el adapter: la violación de FK explotaba después, al flush del commit, fuera del código que podía traducirla a una excepción de negocio.
- **Causa real:** Hibernate difiere el INSERT hasta el flush; `save()` solo encola.
- **Solución:** `saveAndFlush()` en los adapters de persistencia cuyo caso de uso necesita enterarse de la violación de FK en el momento. Cubierto por test de integración.
- **Cómo evitarlo:** en adapters que traducen errores de integridad a excepciones de dominio, usar `saveAndFlush()`; `save()` solo cuando el diferimiento no cambia el contrato.

---

## E-20 — Saldo cacheado que puede divergir de su libro mayor por escritura concurrente

- **Fecha:** 2026-08-24
- **Dónde:** `points` — `PuntajeService` + `PuntajeParticipantePersistenceAdapter`
- **Síntoma:** ninguno visible de inmediato. La vista `renaser.verificacion_puntos_liga` empieza a devolver filas: el saldo de `puntajes_participante.puntos_liga` deja de coincidir con `100 + SUM(ajustes_puntos_liga.delta_aplicado)`.
- **Causa real:** el saldo se actualizaba leyendo el agregado, mutándolo en memoria y guardándolo (patrón *read-modify-write*) **sin bloqueo**. Dos ajustes concurrentes sobre el mismo participante (dos hábitos completados casi a la vez, o un ajuste manual mientras corre el bono de racha nocturno) leen el mismo saldo de partida y el segundo pisa al primero. Los dos asientos del libro mayor sí se escriben — por eso el saldo queda mal y el ledger bien. El sistema anterior no tenía el problema porque hacía un `UPDATE ... SET puntos = GREATEST(puntos + delta, 0)` atómico de un solo golpe.
- **Solución:** carga con bloqueo pesimista (`@Lock(PESSIMISTIC_WRITE)`) en el camino de escritura, separada de la lectura pura. La alternativa equivalente es `@Version` (bloqueo optimista), que necesita una columna nueva en la tabla.
- **Cómo evitarlo:** todo contador cacheado que se actualiza sumando su valor anterior necesita bloqueo (pesimista u optimista) o un UPDATE atómico. Regla práctica: si el código hace `leer → calcular → guardar` sobre una fila que otros procesos también tocan, falta un candado. Detectado por revisión adversarial, no por la suite de tests (los tests unitarios y de integración pasaban).

## E-21 — `PuntajeServiceTest` no compilaba tras cambiar la firma de `consultar()`

- **Fecha:** 2026-08-24
- **Dónde:** `src/test/java/com/renaser/os/points/application/services/PuntajeServiceTest.java`
- **Síntoma:**
  ```
  [ERROR] PuntajeServiceTest.java:[154,46] method consultar in class PuntajeService cannot be applied to given types;
    required: UserId,UserId
    found:    UserId
    reason: actual and formal argument lists differ in length
  ```
- **Causa real:** `PuntajeService.consultar(UserId actorId, UserId participanteId)` se actualizó para exigir el actor (regla de autorización: solo el propio participante o un administrativo pueden consultar) pero el test seguía llamando con un solo argumento. Bloqueaba `./mvnw clean test` (`testCompile`) para todo el proyecto, no solo para `points`.
- **Solución:** actualizar la llamada a `service.consultar(id, id)` (autoservicio) y agregar el test de seguridad que faltaba (`consultarRechazaActorAjenoSinPermiso`, CLAUDE.MD §0.3) que no existía para esa rama nueva de autorización.
- **Cómo evitarlo:** cuando se agrega un parámetro de autorización a un método de servicio ya existente, revisar en el mismo cambio todos los tests que lo llaman Y agregar el caso negativo de seguridad — no solo el positivo.

## E-22 — `NoSuchBeanDefinitionException` para un `@Component` real solo al correr la suite completa (no en aislado)

- **Fecha:** 2026-08-24
- **Dónde:** `ContratoPersistenceAdapterTest` (`phasecontracts`), pero cascadea a TODOS los tests de integración posteriores del mismo run (`UserPersistenceAdapterTest`, etc.)
- **Síntoma:**
  ```
  Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type
  'com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.contrato.ContratoPersistenceAdapter' available
  ```
  seguido de, en TODAS las clases IT que corren después en el mismo `./mvnw clean test`:
  ```
  java.lang.IllegalStateException: ApplicationContext failure threshold (1) exceeded: skipping repeated attempt to load context...
  ```
- **Causa real — NO CONFIRMADA todavía, solo acotada:** `ContratoPersistenceAdapter` SÍ tiene `@Component`, SÍ está compilado en `target/classes`, y `ContratoPersistenceAdapterTest` **pasa 5/5 en verde cuando se corre aislado** (`./mvnw test -Dtest=ContratoPersistenceAdapterTest` → BUILD SUCCESS, 74s). Solo falla dentro de `./mvnw clean test` completo, siempre en el mismo punto. Como todas las clases `@SpringBootTest` comparten la misma firma de configuración (`@Import(TestcontainersConfiguration.class)`, sin más customización), Spring cachea UN solo contexto para todas — si el primero que lo dispara falla, el resto hereda el mismo contexto roto sin re-intentar. Hipótesis más probable, sin confirmar: interacción entre `ArchitectureTest` (que corre primero en el mismo JVM y usa `ArchUnit ClassFileImporter` para leer todos los `.class` de `com.renaser.os`) y el classpath scanning de Spring inmediatamente después — posible problema de caché de recursos a nivel de JVM/Windows, no un bug de `phasecontracts`.
- **Solución / cierre (2026-08-24, mismo día):** **falso positivo.** Dos corridas completas posteriores de `./mvnw clean test` (421 tests, con `habits`/`rocks` ya integrados) no lo reprodujeron ni una vez. La causa real fue una carrera de archivos: esta corrida se lanzó mientras el agente constructor de `habits` estaba moviendo `MotivoPuntos` a `points.api` en simultáneo (edición de ~13 archivos), dejando el árbol de código en un estado transitorio inconsistente que Maven alcanzó a compilar a medias. No es un bug de Spring/ArchUnit ni de `phasecontracts`.
- **Cómo evitarlo:** **nunca correr `./mvnw` mientras un agente sigue escribiendo archivos en el mismo working tree.** Esperar la notificación de fin de cada agente constructor antes de compilar/testear. Si un resultado de `NoSuchBeanDefinitionException`/`ApplicationContext failure` para una clase que compila y tiene `@Component` no se puede explicar por el código, sospechar primero de una corrida concurrente antes de investigar Spring.

## E-23 — Comando self-validating llama `SelfValidating.validateConstructorArgs` con menos argumentos que campos tiene el record

- **Fecha:** 2026-08-24
- **Dónde:** `rocks` — `CompletarRocaDiariaUseCase.CompletarRocaDiariaCommand` (3 de 9 campos) y `EditarDentroDe48hUseCase.EditarRocaSemanalCommand` (2 de 7 campos)
- **Síntoma:**
  ```
  java.lang.IllegalArgumentException: HV000181: Wrong number of parameters. Method or constructor
  class ...CompletarRocaDiariaCommand#CompletarRocaDiariaCommand(UserId, RocaDiariaId, TipoEvidenciaRoca,
  String, String, String, Instant, Double, Double) expects 9 parameters, but got 3.
  ```
  Revienta SIEMPRE que se construye el comando (no solo en tests) — bug de producción, no solo de test.
- **Causa real:** `SelfValidating.validateConstructorArgs(Class, Object... args)` usa `recordClass.getDeclaredConstructors()[0]` (el constructor canónico completo del record) y le pasa los `args` recibidos a Hibernate Validator vía `validateConstructorParameters`. Si el compact constructor del record llama a `validateConstructorArgs(...)` con MENOS argumentos que campos tiene el record (ej. copiar-pegar de un comando más chico sin actualizar la lista), el conteo no matchea contra el constructor real y Hibernate Validator explota — nunca llega a evaluar las anotaciones `@NotNull`, así que ni siquiera cumple su propósito de validar.
- **Solución:** pasar TODOS los componentes del record, en el mismo orden, sin importar cuáles llevan `@NotNull`. Patrón correcto ya usado en `UpdateUserRoleCommand`/`UpdateMyProfileCommand` (`users`): tantos argumentos como campos.
- **Cómo evitarlo:** al escribir un compact constructor con `SelfValidating.validateConstructorArgs(...)`, contar los campos del record y contar los argumentos pasados — deben coincidir siempre. Vale la pena una regla de ArchUnit o un test de reflexión genérico que lo verifique para todos los comandos del repo (no se agregó todavía).

## E-24 — Comparar `Duration` truncado a minutos antes de decidir un límite de fase pierde precisión en el borde exacto

- **Fecha:** 2026-08-24
- **Dónde:** `habits` — `ResultadoOtorgamiento.calcular` (cálculo de puntos por ventana de entrega)
- **Síntoma:** tests de borde fallaban con resultados "un estado antes" del esperado: a los "+10min y 1seg" el resultado era `GRACIA` en vez de `EXTENDIDO`; a "+10min+3h y 1seg" era `EXTENDIDO` en vez de `EXPIRADO`.
- **Causa real:** el código convertía el retraso a minutos enteros (`Duration.toMinutes()`, que trunca) **antes** de comparar contra el límite de fase (`minutosTarde <= GRACIA_MINUTOS`). `floor(10min01s) = 10`, así que "10min01s" se consideraba `<= 10` y quedaba adentro de la gracia, aunque el contrato documentado en el propio javadoc de la clase especifica el corte sobre el instante exacto, no sobre el minuto redondeado.
- **Solución:** comparar con `Duration.compareTo(...)` (precisión de nanosegundos) para decidir en qué fase cae, y usar `toMinutes()` truncado únicamente para la fórmula de puntos dentro de la fase GRACIA (ahí sí es la regla de negocio real: "-1 punto cada 2 minutos").
- **Cómo evitarlo:** nunca truncar un `Duration`/`Instant` a una unidad más gruesa ANTES de una comparación de límite (`<=`/`<`) que después alimenta una decisión de estado — truncar solo para el cálculo que específicamente lo pide, después de decidir en qué rama se está.

## E-25 — `verify(mock).metodoSobrecargado(any())` verifica el overload equivocado con `ApplicationEventPublisher`

- **Fecha:** 2026-08-24
- **Dónde:** `RocaDiariaServiceTest.completarATiempoPagaRockCompleted` (`rocks`)
- **Síntoma:**
  ```
  Wanted but not invoked:
  events.publishEvent(<any>);
  However, there was exactly 1 interaction with this mock:
  events.publishEvent(RocaCompletadaEvent[...]);
  ```
  El mock **muestra la invocación real** en el propio mensaje de error, pero el `verify()` igual falla.
- **Causa real:** `ApplicationEventPublisher` declara DOS métodos sobrecargados: `publishEvent(ApplicationEvent)` y `publishEvent(Object)`. Como los eventos de dominio de este repo son records que implementan `DomainEvent` (no extienden `ApplicationEvent`), la llamada real en producción resuelve al overload `Object`. Pero `verify(events).publishEvent(any())` — con `any()` sin tipo — deja que el compilador elija el overload por su cuenta, y en presencia de dos candidatos elige el más específico (`ApplicationEvent`), verificando una sobrecarga distinta de la que realmente se invocó.
- **Solución:** `verify(events).publishEvent(any(RocaCompletadaEvent.class))` (o el tipo de evento concreto que corresponda) — fuerza el overload correcto (`Object`, porque `RocaCompletadaEvent` no es `ApplicationEvent`).
- **Cómo evitarlo:** con `ApplicationEventPublisher` (o cualquier mock con métodos sobrecargados), nunca usar `any()` sin el `.class` del tipo esperado en `verify()`/`when()` — la ambigüedad de overload es silenciosa hasta que el test falla de forma confusa.

## E-26 — Test de integración inserta un evento con FK a una fila que no existe

- **Fecha:** 2026-08-24
- **Dónde:** `EventoVerdugoPersistenceAdapterTest` (`rocks`)
- **Síntoma:**
  ```
  DataIntegrityViolation could not execute statement [ERROR: insert or update on table "eventos_verdugo"
  violates foreign key constraint "eventos_verdugo_roca_diaria_id_fkey"
  ```
- **Causa real:** el test construía `EventoVerdugo` con `DestinoVerdugo.ROCA_DIARIA` y un `UUID.randomUUID()` como `destinoId`, sin insertar antes la fila real en `rocas_diarias` que esa columna referencia por FK (`eventos_verdugo.roca_diaria_id REFERENCES rocas_diarias(id)`). El `@BeforeEach` sembraba `usuarios`+`participantes_programa` pero no la tabla intermedia.
- **Solución:** insertar una fila real de `rocas_diarias` (con todas sus columnas `NOT NULL`: `fecha`, `posicion`, `titulo`, `color`, `puntaje_impacto`, `eje`) en el `@BeforeEach` y usar ese id real como `destinoId` en vez de un UUID sin respaldo.
- **Cómo evitarlo:** cuando un test IT construye una entidad con una FK hacia otra tabla, sembrar SIEMPRE la fila referenciada primero — un `UUID.randomUUID()` "de relleno" en una columna con FK real solo funciona por casualidad si la constraint no está activa.

## E-27 — Un test nombrado `*IT.java` nunca se ejecuta: Surefire lo ignora en silencio y `mvn test` reporta éxito

- **Fecha:** 2026-08-24
- **Dónde:** `notifications` — el E2E del outbox de Modulith, entregado como `NotificationsEventOutboxIT.java`
- **Síntoma:** ninguno visible — `./mvnw clean test` terminaba en `BUILD SUCCESS` con 465 tests, y la prueba más importante del módulo (la que demuestra que un evento de `habits`/`rocks` realmente llega a la bandeja de `notifications` a través del outbox) nunca aparecía en el log de Surefire. Solo se detectó al auditar a mano qué clases de test corrieron contra la lista de archivos en disco.
- **Causa real:** este proyecto usa únicamente `maven-surefire-plugin` (fase `test`), sin `maven-failsafe-plugin`. El patrón de inclusión por defecto de Surefire es `**/Test*.java`, `**/*Test.java`, `**/*Tests.java`, `**/*TestCase.java` — el sufijo `*IT.java` es el default de **Failsafe**, no de Surefire, y como Failsafe no está configurado en el `pom.xml`, ese archivo simplemente nunca entra a ninguna fase del build. No es un error, no es un skip reportado: el archivo se compila (es código Java válido) pero JUnit nunca lo descubre.
- **Solución:** renombrado a `NotificationsEventOutboxTest.java` (clase y archivo), consistente con que en todo el repo no existe ni un solo archivo `*IT.java` — la convención real de este proyecto es que los tests de integración también terminan en `Test`.
- **Cómo evitarlo:** antes de dar por buena la cobertura de un módulo nuevo, no alcanza con "el build pasó en verde" — hay que verificar que el número de clases que corrieron en el log de Surefire coincide con el número de archivos `*Test.java` en disco. Si un agente entrega un archivo `*IT.java`/`*ITCase.java` en un proyecto sin Failsafe, renombrarlo de inmediato: es exactamente el tipo de falla silenciosa que un gate en verde no detecta por sí solo.

## E-28 — La tabla `event_publication` del outbox de Spring Modulith no existía en ninguna base: `spring-modulith-starter-jpa` no trae su propio script de schema

- **Fecha:** 2026-08-24
- **Dónde:** transversal — cualquier módulo que publique eventos de dominio (visible primero en `notifications`, pero el síntoma ya aparecía antes en tests de persistencia de `rocks`/`support`/`users`)
- **Síntoma:** warning silencioso al cerrar el contexto de cada test `@SpringBootTest` que usa Testcontainers:
  ```
  WARN org.hibernate.orm.jdbc.error : ERROR: relation "event_publication" does not exist
  WARN o.s.b.f.support.DisposableBeanAdapter : Invocation of destroy method failed on bean with name 'eventPublicationRegistry'
  ```
  No hacía fallar ningún test porque ocurre en `destroy()` del bean, después de que el test ya terminó y evaluó sus asserts — por eso pasó inadvertido en varios lotes.
- **Causa real:** `spring-modulith-starter-jpa` (2.1.0) registra `JpaEventPublicationRepository` sobre una entidad `JpaEventPublication` mapeada a la tabla `event_publication`, pero el jar **no incluye ningún script SQL/Flyway/Liquibase** (verificado inspeccionando el `.jar` completo: cero archivos `.sql`). Es responsabilidad del proyecto crear esa tabla — nadie lo había hecho porque el baseline (`V1__baseline_renaser.sql`) es anterior a que cualquier módulo publicara eventos de verdad.
- **Solución:** se generó el DDL exacto dejando que Hibernate lo derive del mapeo JPA real (`ddl-auto=update` contra el Postgres de Testcontainers en un test descartable, volcando columnas/PK/índices desde `information_schema` y `pg_indexes`) — no se inventó a mano. Se agregó `V2__spring_modulith_event_publication.sql` con esa estructura exacta (`id UUID PK`, `completion_attempts INTEGER NOT NULL`, `completion_date`/`last_resubmission_date TIMESTAMPTZ` nullable, `event_type`/`listener_id`/`serialized_event VARCHAR(255) NOT NULL`, `status VARCHAR(255)` nullable). Tras aplicarla, los warnings desaparecieron (0 ocurrencias en el log completo) y `NotificationsEventOutboxTest` pasó de punta a punta.
- **Cómo evitarlo:** al agregar `spring-modulith-starter-jpa` (o cualquier starter de Modulith con persistencia propia), verificar de entrada si el jar trae su schema o si hay que crearlo — no asumir que "starter" implica autoprovisión de tablas. La forma segura de obtener el DDL exacto sin adivinar es dejar que Hibernate lo genere una vez (`ddl-auto=update`) contra una base descartable y volcarlo desde `information_schema`, nunca escribirlo de memoria.

---

## E-29 — Dos módulos crean una clase con el mismo nombre simple y Spring tumba el contexto de TODA la suite

**Síntoma exacto:**

```
Caused by: org.springframework.context.annotation.ConflictingBeanDefinitionException:
Annotation-specified bean name 'consultarMiembrosCelulaPersistenceAdapter' for bean class
[com.renaser.os.community.infrastructure.adapter.out.persistence.participante.ConsultarMiembrosCelulaPersistenceAdapter]
conflicts with existing, non-compatible bean definition of same name and class
[com.renaser.os.calendar.infrastructure.adapter.out.persistence.celula.ConsultarMiembrosCelulaPersistenceAdapter]
```

Y a continuación, en cascada, en **todos** los `@SpringBootTest` del proyecto:

```
java.lang.IllegalStateException: ApplicationContext failure threshold (1) exceeded:
skipping repeated attempt to load context for [WebMergedContextConfiguration@... ]
```

**Causa real:** dos módulos distintos (`community` y `calendar`) necesitaban leer los miembros de una célula y —siguiendo correctamente la regla de "cada módulo hace su propia query nativa en vez de importar internals de otro"— cada uno creó su adaptador. Ambos lo llamaron igual. El paquete es distinto, así que **javac compila sin quejarse**; pero `@Component` sin nombre explícito deriva el nombre del bean del **nombre simple** de la clase, y ahí sí chocan.

Lo peligroso no es el fallo en sí, es el alcance: no rompe solo el módulo culpable. Un único contexto Spring roto hace fallar **todos los tests de integración del proyecto**, incluidos módulos que nadie tocó (se cayeron `habits` y `notifications`, que estaban en verde).

**Solución aplicada:** renombrar con el sufijo del módulo, siguiendo la convención que el repo ya usaba para este mismo problema (`ConsultarProgresoParticipanteRocksPersistenceAdapter`, `...HabitsPersistenceAdapter`):

- `community` → `ConsultarMiembrosCelulaCommunityPersistenceAdapter`
- `calendar` → `ConsultarMiembrosCelulaCalendarPersistenceAdapter`

**Cómo evitar que vuelva a pasar:** el patrón de "copia propia por módulo" (§ el mismo que produce `ConsultarProgresoParticipante*Port`) **garantiza** nombres repetidos si no se nombra el módulo en la clase. Regla: **todo adaptador que sea la copia propia de un módulo sobre una tabla de otro contexto lleva el nombre del módulo en el nombre de la clase.** Verificación barata antes de un build largo:

```bash
find src/main/java/com/renaser/os -name "*.java" | xargs -n1 basename | sort | uniq -d
```

Si aparece algo que sea un `@Component`/`@Service`/`@Repository`, es este error. (Interfaces, records DTO y enums pueden repetir nombre sin problema: no son beans.)

---

## E-30 — Un `esX()` que lanza excepción en vez de devolver `false` convierte un 403 en un 404 que filtra existencia

**Síntoma exacto:** un test de autorización negativa esperaba 403 y recibía 404:

```
Expecting actual throwable to be an instance of:
  com.renaser.os.shared.domain.NotAuthorizedException
but was:
  java.util.NoSuchElementException: Actor no encontrado: fa19f00e-...
  at ComentarioMuroService.lambda$esModerador$0(ComentarioMuroService.java:126)
```

**Causa real:** `esModerador(actorId)` es un predicado booleano, pero resolvía el actor con `.orElseThrow(...)`. Rompía su propio contrato: un predicado no puede explotar.

**Por qué importa más de lo que parece.** El orden de comprobaciones en `ocultar()` era: 1) ¿existe el comentario? → 404 si no. 2) ¿el actor puede moderar? Con el `orElseThrow`, un actor inexistente llegaba al paso 2 y recibía **404 "Actor no encontrado"**. Pero para cuando se llega al paso 2, el 404 de "comentario no encontrado" **ya fue descartado** — así que ese segundo 404, por eliminación, le confirma a quien pregunta que **el comentario existe**. Es una fuga de existencia por canal lateral, con un actor que ni siquiera es un usuario del sistema.

**Solución aplicada:** el predicado falla cerrado — `.map(...).orElse(false)` — así un actor inexistente termina siempre en 403. Se corrigió en `ComentarioMuroService` y en `PublicacionMuroService` (tenía el mismo bug latente, enmascarado porque su test casualmente stubbeaba al actor), con tests de regresión en ambos.

**Distinción que se conservó a propósito:** `requireActorActivo`/`requireAdmin` —donde el actor es el *sujeto* de la operación, no un tercero cuyo rol se consulta— sí siguen dando 404 si el actor no existe. Ahí no hay fuga: no hay otro recurso cuya existencia se pueda inferir.

**Cómo evitar que vuelva a pasar:** dos reglas.
1. **Un método `esX()`/`puedeX()`/`tieneX()` nunca lanza por "no encontrado".** Si no puede responder, la respuesta es `false`. Lanzar es para el sujeto de la operación, no para un tercero consultado.
2. **Al escribir el test de autorización negativa (obligatorio por `CLAUDE.MD` §0.3), stubear al actor como un usuario REAL sin permiso.** Si el test pasa con el actor sin stubear, no está probando el permiso: está probando el "no existe", y el 403 que ve es un falso positivo.

---

## E-31 — `(:cursor IS NULL OR col < :cursor)` en JPQL rompe contra Postgres real: "could not determine data type of parameter $1"

**Síntoma exacto:** sondeo manual de la app levantada contra Postgres real (no un test — los tests con mocks no lo veían). `GET /api/v1/wall`, `GET /api/v1/wall/hidden` y `GET /api/v1/wall/{id}/comments` devolvían **500**:

```
ERROR: could not determine data type of parameter $1
```

SQL generado por Hibernate para `/wall`:

```sql
select pje1_0.id, ...
from renaser.publicaciones_muro pje1_0
where pje1_0.oculta=false
  and (? is null or pje1_0.creado_en<?)
  and (? is null or pje1_0.categoria_clave=?)
order by pje1_0.creado_en desc
fetch first ? rows only
```

Y en `/wall/{id}/comments`, mismo patrón: `and (? is null or cje1_0.creado_en>?)`.

**Causa real:** el patrón JPQL de "parámetro opcional" `(:x IS NULL OR col < :x)` genera, en el SQL final, **dos placeholders `?` distintos** para la misma variable nombrada — uno por cada aparición. El primero (`? IS NULL`) no tiene ningún otro contexto en esa posición del `WHERE`, así que en el protocolo extendido de Postgres (que exige conocer el tipo de cada parámetro en el `Parse` antes de `Bind`/`Execute`) no hay forma de inferirlo. El `prepare` falla **antes de ejecutar nada**, incluso cuando el valor real que se manda es `null` (que es, además, el caso más común: pedir la primera página del feed, sin cursor).

**Alcance:** 3 consultas en `community`, las únicas con el patrón `IS NULL OR` en el módulo — `SpringDataPublicacionRepository.feed` (con 2 filtros opcionales: cursor y categoría → 4 combinaciones), `SpringDataPublicacionRepository.feedOculto` (1 filtro opcional: cursor), `SpringDataComentarioRepository.pagina` (1 filtro opcional: cursor). Se revisó el resto del módulo con `grep -rn "IS NULL OR"` y no aparecen más ocurrencias.

**Solución aplicada:** en vez de `CAST(:cursor AS ...)` en el JPQL o pasar a query nativa con `?::timestamptz`, se partió cada consulta en un método de repositorio por combinación de filtro opcional — `feedSinCursorSinCategoria`/`feedSinCursorConCategoria`/`feedConCursorSinCategoria`/`feedConCursorConCategoria`, `feedOcultoSinCursor`/`feedOcultoConCursor`, `paginaSinCursor`/`paginaConCursor` — y el adaptador de persistencia (`PublicacionPersistenceAdapter.feed`/`.feedOculto`, `ComentarioPersistenceAdapter.pagina`) elige cuál llamar según qué venga `null`. Cada método queda con SQL simple y sin ambigüedad de tipos, porque el parámetro que antes solo aparecía en `IS NULL` directamente no existe en la versión "sin ese filtro". Se agregaron tests de integración con Testcontainers contra Postgres real cubriendo los dos caminos de cada consulta (`PublicacionPersistenceAdapterTest`, `ComentarioPersistenceAdapterTest`, en `community`).

**Cómo evitar que vuelva a pasar:**
1. **Nunca usar `(:param IS NULL OR col OP :param)` en un `@Query` JPQL/HQL contra Postgres.** Es un antipatrón conocido de Hibernate+Postgres, no un caso límite raro — rompe en el caso más común (parámetro realmente `null`), no en un caso raro.
2. **Un filtro opcional se resuelve con un método de repositorio por combinación**, no con un único método "inteligente". Si son muchos filtros opcionales combinados, es señal de que hace falta Criteria API/Specification — pero para 1-2 filtros (el caso real de este módulo), la explosión combinatoria de métodos sigue siendo más simple de leer y más rápida que cualquier alternativa con cast.
3. **Los tests con mocks de los puertos `out` NUNCA van a detectar esto** — el error es del `prepare` de Postgres, no de la lógica Java. Toda query con un parámetro potencialmente `null` necesita al menos un test de integración con Testcontainers que la ejecute **con el parámetro en `null`**, no solo con un valor presente.

## E-32 — Repetición de E-29: dos agentes en paralelo (`evidence` y `onboarding`) crean cada uno una clase `NoOpValidacionIAAdapter` y tumban todo el contexto

**Síntoma exacto:** gate completo del Lote 4 (`chat`+`evidence`+`onboarding`) en rojo con cientos de fallos en cascada, todos con el mismo mensaje de fondo:

```
Caused by: org.springframework.beans.factory.BeanDefinitionStoreException: Failed to parse configuration class [com.renaser.os.RenaserOsApplication]
Caused by: org.springframework.context.annotation.ConflictingBeanDefinitionException: Annotation-specified bean name 'noOpValidacionIAAdapter' for bean class [com.renaser.os.onboarding.infrastructure.adapter.out.ia.NoOpValidacionIAAdapter] conflicts with existing, non-compatible bean definition of same name and class [com.renaser.os.evidence.infrastructure.adapter.out.ia.NoOpValidacionIAAdapter]
```

Y luego, en cada test `@SpringBootTest`: `IllegalState ApplicationContext failure threshold (1) exceeded: skipping repeated attempt to load context`.

**Causa real:** dos agentes trabajando en paralelo (uno construyendo `evidence`, otro `onboarding`) recibieron, cada uno por separado, la instrucción de crear un adapter placeholder "sin IA todavía" siguiendo el mismo patrón (`shared.infrastructure.storage.NoOpAlmacenamientoAdapter`). Ambos, sin coordinación entre sí, nombraron su clase literalmente igual: `NoOpValidacionIAAdapter`, cada uno en el paquete `infrastructure/adapter/out/ia/` de su propio módulo. Java lo permite (paquetes distintos, FQN distinto), pero `@Component` sin nombre explícito deriva el nombre del bean del **simple class name** (`noOpValidacionIAAdapter`), igual en los dos — exactamente el mismo mecanismo ya documentado en **E-29**, ahora disparado por instrucciones de agente en vez de copy-paste manual.

**Por qué no lo agarró ningún test antes del gate:** cada agente compiló y escribió tests solo dentro de su propio módulo (nunca ejecutaron Maven, por regla de esta sesión — el supervisor corre el build). El conflicto solo existe cuando `RenaserOsApplication` escanea **el classpath completo**, algo que ningún test aislado por módulo dispara.

**Solución aplicada:** renombradas ambas clases (y sus tests) con sufijo específico del dominio que resuelven, no del patrón genérico: `evidence.infrastructure.adapter.out.ia.NoOpEvidenciaValidacionIAAdapter` y `onboarding.infrastructure.adapter.out.ia.NoOpV90ValidacionIAAdapter`. Se verificó con `grep -rn "class NoOpValidacionIAAdapter"` en todo `src/` que no queda ningún duplicado.

**Cómo evitar que vuelva a pasar:**
1. **Mismo remedio que E-29, reforzado**: nunca nombrar una clase `@Component` solo por su *rol genérico* (`NoOpXAdapter`, `XValidator`, `XMapper`) sin prefijo de módulo/dominio — dos módulos independientes resolviendo el mismo patrón (acá: "placeholder sin IA") van a converger al mismo nombre genérico de forma natural, no por descuido.
2. **Cuando se despliegan agentes en paralelo sobre módulos distintos que van a compartir patrones estructurales** (adapters NoOp, mappers, DTOs de página), el encargo a cada agente debería incluir el nombre exacto de la clase a crear (no solo "seguí el patrón de X"), precisamente para evitar que dos agentes sin visibilidad entre sí lleguen al mismo nombre. Alternativa más barata: después de que todos los agentes paralelos terminan, correr `find src/main -iname "*.java" | xargs -n1 basename | sort | uniq -d` ANTES de lanzar el gate completo — detecta el choque de nombres en segundos sin gastar los ~15-20 minutos de una corrida completa de Testcontainers.
3. **La cascada de cientos de fallos es una señal, no ruido**: cuando un gate reporta "ApplicationContext failure threshold exceeded" en decenas de clases de test no relacionadas entre sí, la causa casi nunca son esos tests — es un solo error de arranque de contexto (bean duplicado, migración rota, config faltante). Buscar el primer `Caused by:` real del log, no perseguir cada test individual.

## E-33 — Spring Boot 4.1 autoconfigura Jackson 3 (`tools.jackson.*`), no Jackson 2 (`com.fasterxml.jackson.*`): inyectar `ObjectMapper` clásico por constructor falla con "No qualifying bean"

**Síntoma exacto:**

```
Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'redisChatPublisher' ...
Unsatisfied dependency expressed through constructor parameter 1: No qualifying bean of type
'com.fasterxml.jackson.databind.ObjectMapper' available: expected at least 1 bean which qualifies
as autowire candidate. Dependency annotations: {}
```

Apareció en el gate del Lote 4, después de resolver E-32, como el segundo bloqueo real (mismo patrón de cascada: un solo fallo de arranque de contexto tumbaba toda la suite).

**Causa real:** este proyecto corre **Spring Boot 4.1**, que en esta versión migró su autoconfiguración interna de Jackson a **Jackson 3** (`tools.jackson.databind.ObjectMapper`, artefacto `org.springframework.boot:spring-boot-starter-jackson` → `tools.jackson.core:jackson-databind:3.x`) — confirmado con `./mvnw dependency:tree | grep -i jackson`. El bean `ObjectMapper` que Spring registra automáticamente es de ese tipo nuevo. `RedisChatPublisher` (agente de `chat`) pidió por constructor el `ObjectMapper` **clásico** de Jackson 2 (`com.fasterxml.jackson.databind.ObjectMapper`, `import com.fasterxml.jackson.databind.ObjectMapper`) — el que prácticamente todo el ecosistema Java usó durante una década y el que cualquier LLM entrenado hasta antes de Boot 4 va a escribir por reflejo. Como no hay ningún bean de ESE tipo específico, la inyección falla — aunque Jackson 2 (`com.fasterxml.jackson.core:jackson-databind:2.21.5`) SÍ está en el classpath (llega transitivo de otras dependencias), simplemente no está registrado como bean de Spring bajo ese tipo.

**Por qué no se detectó antes en el resto del proyecto:** ningún otro módulo (Lotes 1-3) inyecta `ObjectMapper` por constructor — todos delegan la serialización JSON a Spring MVC (`@RestController` + `HttpMessageConverter`s), que internamente sabe resolver el Jackson correcto sin que el código de la app lo pida explícito. `chat` fue el primero en necesitar serializar manualmente (el payload del fanout de Redis no pasa por un `@RestController`).

**Solución aplicada:** `RedisChatPublisher` ya no recibe `ObjectMapper` por constructor — crea el suyo propio como campo (`new ObjectMapper().registerModule(new JavaTimeModule()).disable(WRITE_DATES_AS_TIMESTAMPS)`, mismos defaults sensatos que Spring Boot aplicaría). Es una necesidad interna acotada a esa clase (serializar un payload liviano de 6 campos), no justifica cablear el `ObjectMapper` de toda la app ni migrar el código a la API de Jackson 3.

**Cómo evitar que vuelva a pasar:**
1. **En este proyecto (Boot 4.1), nunca pedir `ObjectMapper`/tipos de Jackson por inyección de Spring esperando la clase clásica de `com.fasterxml.jackson.*`** — el bean autoconfigurado es Jackson 3 (`tools.jackson.*`). Si de verdad hace falta el `ObjectMapper` de Spring, hay que usar el tipo nuevo y su API (parcialmente distinta); si el código existente usa Jackson 2 (como el resto del repo, vía `com.fasterxml.jackson.databind.ObjectMapper` para DTOs con anotaciones `@JsonProperty` etc.), la solución más simple y aislada es construir un `ObjectMapper` propio de esa clase en vez de pedirlo por DI.
2. **Antes de escribir `new Constructor(SomeSpringManagedType tipo)` para un tipo que no es obviamente un bean de la app** (no es un puerto, no es un `@Repository`/`@Service` propio), verificar primero que existe un bean de ESE tipo exacto — `./mvnw dependency:tree | grep -i <libreria>` para confirmar qué versión/paquete gana en este proyecto específico, en vez de asumir la convención más común del ecosistema.

## E-34 — Dependencia circular: un mismo servicio implementa el "disparador" y el "trabajo real" de un flujo `@Async`

**Síntoma exacto:**

```
Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name
'grabacionV90Service' ... Unsatisfied dependency expressed through constructor parameter 3: Error creating
bean with name 'despacharValidacionV90Adapter' ... Unsatisfied dependency expressed through constructor
parameter 0: Error creating bean with name 'grabacionV90Service': Requested bean is currently in creation:
Is there an unresolvable circular reference or an asynchronous initialization dependency?
```

Tercer y último bloqueo del gate del Lote 4, después de E-32 y E-33.

**Causa real:** `onboarding.GrabacionV90Service` implementaba a la vez `ValidarV90UseCase` (el lado que **dispara** la validación: `solicitarValidacion()` llama a `DespacharValidacionV90Port`, cuyo único adapter es `@Async`) y `ProcesarValidacionV90UseCase` (el lado que hace el **trabajo real**: `procesar()`, invocado por ese mismo adapter desde el hilo separado — necesario para esquivar el problema clásico de auto-invocación de `@Async` en Spring, que el propio agente documentó correctamente en el javadoc del adapter). El diseño del *patrón* (puerto out separado del caso de uso in) era correcto; el error fue que **ambos lados vivían en la misma clase**: `GrabacionV90Service` necesita `DespacharValidacionV90Port` (el adapter) para construirse, y el adapter necesita `ProcesarValidacionV90UseCase` — que resuelve al mismo `GrabacionV90Service` — para construirse. Ciclo: servicio → puerto → adapter → servicio.

**Por qué no se veía en compilación ni en los tests unitarios de `GrabacionV90Service`:** Mockito no arma un `ApplicationContext` real — construye `GrabacionV90Service` a mano con mocks, así que el ciclo de beans de Spring nunca se ejerce ahí. Solo aparece cuando algo bootea el contenedor completo (`@SpringBootTest`, o la app real), que es exactamente lo que ningún agente ejecuta (regla de esta sesión: los agentes no corren Maven).

**Solución aplicada:** se extrajo `procesar()` a una clase nueva y separada, `ProcesarValidacionV90Service` (implementa *solo* `ProcesarValidacionV90UseCase`, con sus propias dependencias: `LoadGrabacionV90Port`, `SaveGrabacionV90Port`, `ValidacionIAPort`, `Clock` — ninguna de ellas es el adapter `@Async` ni depende de `GrabacionV90Service`). `GrabacionV90Service` quedó con `RegistrarGrabacionV90UseCase`/`ListarGrabacionesV90UseCase`/`ValidarV90UseCase` únicamente. Cadena de dependencias resultante, sin ciclo: `GrabacionV90Service` → `DespacharValidacionV90Port` (adapter) → `ProcesarValidacionV90UseCase` (`ProcesarValidacionV90Service`, clase nueva) → puertos de datos. Se movieron los 3 tests de `procesar()` a `ProcesarValidacionV90ServiceTest` (archivo nuevo) y se ajustó el constructor mockeado de `GrabacionV90ServiceTest` (ya no recibe `ValidacionIAPort`).

**Cómo evitar que vuelva a pasar:**
1. **Cuando un flujo necesita el patrón "puerto out `@Async` que le devuelve la pelota a un caso de uso in"** (para esquivar auto-invocación), ese caso de uso in **nunca puede vivir en la misma clase** que el servicio que depende del puerto out — aunque conceptualmente ambos "sean del mismo agregado". Es SRP aplicado a un caso muy específico de Spring: dos responsabilidades que deben ser dos beans porque uno depende del otro a través de una interfaz.
2. **Regla general, no solo para `@Async`**: si un puerto `out` de un módulo termina siendo implementado por un adapter que a su vez depende (directa o indirectamente) de un puerto `in` que implementa el MISMO servicio que declaró el puerto `out`, hay un ciclo — sin importar si Spring lo detecta en el momento de escribir el código (no lo hace: es un error de **runtime**, al armar el contexto, no de compilación).
3. **Esto no lo agarra ningún test unitario con mocks — solo un `@SpringBootTest` real o el arranque de la app.** Es la misma lección de E-32/E-33: los tests aislados por servicio no prueban que el grafo de beans de la aplicación completa sea válido. La única red de seguridad real es correr el build completo antes de dar un módulo por terminado — precisamente lo que esta fase de auditoría está haciendo.

## E-35 — Dos bugs más, revelados en el mismo gate: un mock que quedó incompleto (efecto dominó de un fix anterior) y un límite conocido del naming strategy implícito de Hibernate

**Bug A — `RegistroServiceTest` (`habits`) vuelve a fallar, pero por otra causa:**

```
java.util.NoSuchElementException: Participante no encontrado: 7bd8a5ad-...
	at RegistroService.lambda$requireProgreso$0
	at RegistroService.requireProgreso
	at RegistroService.requireSelf
	at RegistroService.completar
```

Después de arreglar el mock de `byIdParaEscritura` (fase de auditoría anterior, esta misma sesión), la ejecución de `completar()`/`consultar()` ahora avanza más lejos de lo que avanzaba antes — y llega a `requireSelf()`, que a su vez llama `requireProgreso()` (agregado en la Fase 5 de auditoría de seguridad de esta sesión, para que un aprendiz SUSPENDIDO no pueda operar). Ese `requireProgreso()` necesita `progresoPort.deParticipante(...)` estubeado, y 3 tests (`consultarDelegaAlPuertoParaElPropioParticipante`, `completarSinHorarioNoOtorgaPuntos`, `completarUnHabitoBloqueoRechazado`) no lo tenían — el gap ya existía desde la Fase 5, pero quedaba **tapado** por el error de `byIdParaEscritura`, que ocurría antes en la cadena de llamadas y nunca dejaba que la ejecución llegara a `requireProgreso()`. **Lección: arreglar un mock puede destapar un segundo mock faltante más adelante en el mismo método — cuando un test que fallaba por A empieza a fallar por B después del fix, no es necesariamente un fix incompleto, es la siguiente capa.** Solución: se agregó `when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(new ProgresoParticipanteHabits(...)))` a los 3 tests (mismo valor que ya usaba `completarATiempoOtorgaDiezPuntos`).

**Bug B — `ChatPersistenceAdapterTest` (`chat`), las 5 pruebas de integración fallan con `column mje1_0.media_duracions does not exist`:**

```
org.hibernate.exception.SQLGrammarException: ERROR: column mje1_0.media_duracions does not exist
  Hint: Perhaps you meant to reference the column "mje1_0.media_duracion_s".
```

**Causa real:** el algoritmo implícito de Hibernate para convertir camelCase → snake_case (`CamelCaseToUnderscoresNamingStrategy.addUnderscores`) tiene dos límites documentados pero poco conocidos:
1. **Nunca separa el ÚLTIMO carácter del identificador**, aunque sea una mayúscula sola — el loop interno es `for (i = 1; i < length - 1; i++)`, así que el índice del último carácter nunca se evalúa. `mediaDuracionS` → la `S` final nunca recibe un `_` antes → `media_duracions` (falta el `_` antes de la `s`), en vez de `media_duracion_s`.
2. **Nunca separa mayúsculas consecutivas** — la condición de inserción exige minúscula-MAYÚSCULA-minúscula; con dos mayúsculas seguidas, ninguna de las dos cumple la condición. `respuestaAId` (con "AId") → ni la `A` ni la `I` reciben `_` → `respuestaaid`, en vez de `respuesta_a_id`.

Los 14 módulos construidos antes de `chat` nunca chocaron con esto por pura casualidad de nomenclatura — es la primera entidad JPA de todo el proyecto con un campo que termina en una sola mayúscula (`mediaDuracionS`, reflejando literalmente la columna `media_duracion_s` del baseline) o con mayúsculas consecutivas (`respuestaAId`, reflejando `respuesta_a_id`).

**Solución aplicada:** `@Column(name = "media_duracion_s")` y `@Column(name = "respuesta_a_id")` explícitos en `MensajeJpaEntity`. Se escaneó el resto de `chat`/`evidence`/`onboarding` (los 3 módulos más nuevos, más propensos a tener el mismo patrón) buscando campos que terminen en una sola mayúscula o con mayúsculas consecutivas — no aparecieron más casos.

**Cómo evitar que vuelva a pasar:**
1. **Cualquier campo de entidad JPA cuya columna real termine en `_x` (una sola letra) o tenga una sigla de 2+ letras seguidas (`_aB`, `_ABC`) necesita `@Column(name=...)` explícito** — el naming strategy implícito de Hibernate no es simétrico con el patrón `snake_case → camelCase` que uno asume intuitivamente. No confiar en la conversión automática para columnas con ese patrón, verificarlo contra el DDL real.
2. **Chequeo rápido y barato antes de correr el gate completo**: `grep -noE "private [A-Za-z0-9<>.]+ [a-zA-Z0-9]+;" **/*JpaEntity*.java` y revisar a mano cualquier campo que termine en `[a-z][A-Z]` o contenga `[A-Z]{2,}` — es exactamente el patrón que expone este bug, y el chequeo tarda segundos contra los ~20 minutos de una corrida completa de Testcontainers.
3. **De nuevo, ningún test con mocks detecta esto** — solo un test de integración contra Postgres real que efectivamente ejecute un `SELECT`/`INSERT` sobre esa columna. Otra razón más para que todo adapter de persistencia nuevo tenga su Testcontainers IT (ya es regla del checklist de PR, CLAUDE.MD §5.4.10) — acá es donde esa regla paga.

## E-36 — Comparar una columna `jsonb` como string literal es una aserción demasiado estricta: Postgres reserializa el JSON (no preserva orden de claves ni espacios)

**Síntoma exacto (4 tests en `onboarding`):**

```
expected: "{"pantalla":"bienvenida","pasos":[1,2,3]}"
 but was: "{"pasos": [1, 2, 3], "pantalla": "bienvenida"}"
```

**Causa real:** `jsonb` en Postgres no es un tipo que preserve texto — al insertar, Postgres parsea el JSON y lo guarda en su representación binaria interna; al leerlo de vuelta, lo re-serializa en su forma canónica propia (con espacios después de `:`/`,`, y sin garantía de preservar el orden de inserción de las claves de un objeto). El contenido semántico es idéntico (mismos pares clave-valor, mismo orden de elementos en los arrays) — lo que cambia es la representación textual. Comparar `.isEqualTo(stringOriginal)` es una aserción más estricta de lo que la columna garantiza.

**Nota aparte, mismo archivo:** `GrabacionV90PersistenceAdapterTest.guardarDosVecesActualizaElMismoSlot` fallaba por una causa DISTINTA y no relacionada con jsonb — el test llamaba `procesarIntentoDeValidacion()` sobre una grabación recién creada con `crearSlot()` sin haber llamado antes `marcarGrabada()` (el dominio correctamente rechaza validar un slot sin audio grabado: "No se puede validar un slot sin audio grabado todavia"). Era simplemente una secuencia de setup incompleta en el test, no un bug de mapeo.

**Solución aplicada:** en los 4 tests de jsonb (`EstadoOnboardingPersistenceAdapterTest`, `GrabacionV90PersistenceAdapterTest.feedbackIaHaceRoundtrip`, `MediaPersistenceAdapterTest`, `RespuestaPersistenceAdapterTest`), se reemplazó `assertThat(recuperado).isEqualTo(stringOriginal)` por una comparación del árbol JSON parseado (`ObjectMapper().readTree(...)`, comparando `JsonNode` contra `JsonNode` — `equals()` de Jackson para objetos es por clave, no por orden de inserción, y para arrays sí respeta el orden posicional, que es exactamente lo que `jsonb` garantiza). Se agregó `marcarGrabada()` al setup del test de `guardarDosVecesActualizaElMismoSlot`.

**Cómo evitar que vuelva a pasar:**
1. **Nunca comparar el valor de ida y vuelta de una columna `jsonb`/`json` como string literal.** Parsear ambos lados (original y recuperado) a un árbol JSON (`JsonNode`, `Map`, o el objeto de dominio real si el mapeo ya lo deserializa) y comparar la estructura, no el texto.
2. Si el dominio expone el jsonb como `String` opaco (como en `onboarding`, decisión documentada — jsonb no se modela como tipo de dominio propio), el test de integración que verifica el roundtrip es exactamente el lugar donde hace falta ese parseo — es la única defensa contra que Postgres "pierda" contenido real (a diferencia de solo reordenar), así que vale la pena mantenerlo, solo con la aserción correcta.

## E-37 — Auditoría adversarial del Lote 4 (`chat`/`evidence`/`onboarding`): 2 hallazgos reales corregidos

Se desplegaron 4 agentes de auditoría (seguridad, concurrencia, lógica de negocio, integración entre módulos) sobre los 3 módulos nuevos y los 4 módulos que tocaron para integrarse (`rocks`, `habits`, `users`, `community`). Integración salió limpia. Dos hallazgos reales, corregidos:

**Hallazgo 1 (CRÍTICO, seguridad) — el endpoint WebSocket de `chat` (`/ws`) no verificaba nada:**

`WebSocketConfig` registraba `/ws` con `setAllowedOriginPatterns("*")` y sin ningún `ChannelInterceptor` — mientras que la capa REST del mismo módulo (`MensajeService`/`ConversacionService`) sí exige `X-Actor-Id` + `UserStatus.ACTIVE` + pertenencia a la conversación (`EsParticipantePort`) en cada operación. Cualquier cliente podía abrir una conexión STOMP y suscribirse a `/topic/conversaciones/{cualquierUUID}` sin autenticarse de ninguna forma, recibiendo en vivo los mensajes de una conversación ajena — anulando por completo el chequeo de pertenencia que la capa REST aplicaba cuidadosamente.

**Causa real:** al construir el módulo, la protección se agregó de forma consistente en cada caso de uso/servicio (capa REST), pero nadie replicó esa misma verificación en la capa de transporte WebSocket — un olvido de superficie de ataque, no un error de lógica dentro de un flujo ya protegido.

**Solución aplicada:** dos componentes nuevos en `chat/infrastructure/adapter/in/websocket/`:
- `ActorHandshakeInterceptor` (`HandshakeInterceptor`): lee `X-Actor-Id` del handshake HTTP inicial (mismo header temporal que el resto de la API) y lo guarda en los atributos de la sesión WebSocket; rechaza el handshake (403) si falta o no es un UUID válido.
- `SubscripcionAutorizadaInterceptor` (`ChannelInterceptor`, registrado en `configureClientInboundChannel`): intercepta cada frame `SUBSCRIBE`, extrae el actor de la sesión y el `conversacionId` del destino (`/topic/conversaciones/{id}`), y aplica la MISMA regla que la capa REST (`UserSummaryFinder` para `ACTIVE`, `EsParticipantePort.esParticipante` para pertenencia) — rechaza la suscripción si falla cualquiera de las dos.

**Hallazgo 2 (ALTO, lógica de negocio + concurrencia) — `GrabacionV90` (onboarding) permitía sobrescribir un veredicto IA "final" vía doble despacho async:**

`procesarIntentoDeValidacion()` bloqueaba reentrada solo si el estado ya era `APROBADA`/`RECHAZADA` o si se agotaron los 3 intentos — **no bloqueaba si el estado ya era `PROCESANDO`**. Y `registrarAprobacion()`/`registrarRechazo()`/`registrarSinResultado()` no tenían NINGÚN guard de estado de entrada (a diferencia de su análogo en `evidence`, que sí exige `requireEnPendiente()` en los tres). Sin `@Version` ni lock pesimista que lo tapara, un doble despacho del cliente (timeout + reintento del `POST .../validate`) podía arrancar dos intentos de validación async en paralelo; el que terminara después sobrescribía en silencio el veredicto que ya había registrado el primero — incluso si ese primero ya era `APROBADA`/`RECHAZADA`.

Relacionado (mismo agente de auditoría, ángulo de concurrencia): `GrabacionV90Service.solicitarValidacion` disparaba `despacharPort.despachar(...)` (el `@Async`) **dentro** del método `@Transactional`, antes del commit — el hilo async podía arrancar y leer el estado previo (todavía no comprometido) bajo READ_COMMITTED, agravando la ventana de carrera del hallazgo anterior. `chat.MensajeService` ya resuelve este mismo problema para su publish a Redis (`publicarDespuesDelCommit`, vía `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()`) — `onboarding` no había replicado ese patrón para su propio disparo async.

**Solución aplicada:**
1. `procesarIntentoDeValidacion()` ahora también rechaza reentrada si `estadoIa == PROCESANDO`.
2. `registrarAprobacion`/`registrarRechazo`/`registrarSinResultado` ahora exigen `requireEnProcesando()` antes de transicionar (mismo patrón que `Evidencia.requireEnPendiente()`).
3. `GrabacionV90Service.solicitarValidacion` ahora despacha el `@Async` vía `TransactionSynchronizationManager.afterCommit()`, igual que `MensajeService`.
4. Tests nuevos en `GrabacionV90Test`: reentrada en `PROCESANDO` rechazada, resolver sin intento en curso rechazado, un veredicto final no se pisa con un segundo despacho tardío.

**Cómo evitar que vuelva a pasar:**
1. **Toda máquina de estados con transiciones "de una vía" (PENDIENTE→PROCESANDO→final) necesita un guard de ENTRADA en cada método de transición, no solo en el primero.** Es un patrón fácil de aplicar a medias: se protege el primer paso (arrancar el intento) y se da por sentado que los pasos siguientes solo se invocan "cuando corresponde" — pero nada en el código lo garantiza si hay reintentos, dobles despachos, o llamadas fuera de orden.
2. **Cuando dos módulos implementan la MISMA forma de máquina de estados de forma independiente (por diseño, ver `evidence`/`onboarding` — "análoga en forma pero independiente en código"), auditarlas una contra la otra es barato y efectivo**: si una tiene un guard que la otra no tiene para el mismo tipo de transición, es señal de un hueco real, no de una diferencia de diseño intencional — así se encontró este hallazgo.
3. **Todo disparo de `@Async`/publish a un sistema externo que dependa de leer el estado recién guardado debe ir después del commit** (`TransactionSynchronizationManager.afterCommit()`), nunca dentro del método `@Transactional` — ya era la regla para Redis en `chat`, ahora es la regla general para cualquier disparo async post-persistencia.
4. **La auditoría adversarial con agentes en paralelo, cada uno con un ángulo distinto (seguridad/concurrencia/lógica de negocio/integración) sobre el MISMO código, encuentra cosas que un solo pase no encuentra** — el hallazgo 2 fue reportado independientemente por el agente de concurrencia (el síntoma: disparo antes del commit) y por el agente de lógica de negocio (la causa raíz: falta de guards de estado) — dos ángulos distintos sobre el mismo bug real, que se complementaron en vez de duplicarse.
