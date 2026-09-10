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

**Reincidencia (módulo `rag`, construido por agente):** `PgVectorNativoAdapter` (adapter de persistencia vectorial, necesita serializar `metadatos` a `jsonb` a mano porque usa SQL nativo, no un `@RestController`) volvió a pedir `com.fasterxml.jackson.databind.ObjectMapper` por constructor — exactamente el mismo error, exactamente la misma causa, un módulo entero después. Se corrigió con el mismo remedio: `ObjectMapper` propio como campo, no inyectado. **Confirma el patrón de E-29/E-32: cualquier clase nueva que serialice JSON a mano (fuera del ciclo normal de un `@RestController`) es candidata segura a este error** — al encargar a un agente una tarea que involucre serializar JSON manualmente (payloads de Redis, columnas `jsonb` por SQL nativo, eventos con cuerpo serializado), hay que advertírselo explícitamente en el encargo, no asumir que "ya está documentado en la bitácora" alcanza — el agente no la lee sin que se le diga.

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

## E-38 — Pasada exhaustiva de endpoints contra la app real: 3 bugs que ninguna auditoría de código había encontrado

Tras el gate en verde, se probaron TODOS los endpoints REST del sistema uno por uno con `curl` contra la app corriendo (Postgres y Redis reales), con 5 agentes en paralelo, cada uno cubriendo un grupo de módulos, probando por endpoint: happy path, sin `X-Actor-Id`, actor inexistente, actor suspendido, actor sin permiso, y recurso inexistente. ~300 pruebas en total. Los 3 hallazgos, todos corregidos:

**Bug 1 (ALTO) — `GET /calendar/events` con fecha mal formada devolvía 500 con el stacktrace COMPLETO en el cuerpo de la respuesta.**

```bash
curl -H "X-Actor-Id: <uuid>" "http://localhost:8080/api/v1/calendar/events?from=notadate&to=2027-01-01T00:00:00Z"
# 500 + ~130 lineas de stacktrace Java: rutas de clases internas, cadena de filtros de Spring Security
```

**Causa real:** `EventoController` llama `Instant.parse(from)` sin try/catch. El `GlobalExceptionHandler` ya traducía `IllegalArgumentException` → 400, y era razonable asumir que cubría esto — pero **`DateTimeParseException` extiende `DateTimeException`, NO `IllegalArgumentException`**, así que caía al handler genérico de 500 filtrando información interna. El mismo patrón sin proteger existía en 6 lugares (`calendar` ×5 entre controller y request DTO, `chat` ×1 en el cursor de paginación); curiosamente `support` sí lo capturaba a mano en sus dos controllers, lo que muestra que el hueco era inconsistente, no sistemático.

**Solución aplicada:** un `@ExceptionHandler(DateTimeParseException.class)` → 400 en el `GlobalExceptionHandler`, en vez de repetir try/catch en cada controller. Es el único lugar del sistema que conoce HTTP (CLAUDE.MD §5.4.4), así que cubre los 6 sitios de una y cualquier parseo futuro.

**Bug 2 (MEDIO, seguridad) — `GET /me/cell` y `GET /me/cell/members` no verificaban cuenta suspendida.**

Un usuario `SUSPENDIDO` recibía `404 {"assigned":false}` y `200 {"members":[]}` respectivamente, en vez de 403. Viola directamente CLAUDE.MD §0.3 ("un usuario SUSPENDED recibe 403 aunque su token sea válido"). **Causa real:** `CelulaService.miCelula()`/`misCompaneros()` no llamaban a `requireActorActivo(...)` — un helper que **ya existía en esa misma clase** y que los otros métodos del servicio sí usaban. No faltaba escribir la verificación, faltaba invocarla en dos métodos. **Solución:** agregado el llamado en ambos.

**Bug 3 (BAJO, consistencia de autenticación) — `GET /mentor/activate-tracking` aceptaba un `X-Actor-Id` inventado y respondía 200.**

Un UUID que no corresponde a ningún usuario devolvía `200 {"active":false}` en vez de 404, mientras que el `POST` y el `DELETE` de **la misma ruta** sí rechazaban al actor inexistente. **Causa real:** el `GET` del controller llamaba directo a `ParticipacionProgramaFinder.deParticipante(...)` como atajo. Ese finder es la **API pública para otros módulos** — que ya validaron su propio actor antes de llamar — y por eso, correctamente, no verifica nada. Usarlo desde un controller salteaba la única capa que sí debía verificar. **Solución:** se creó el caso de uso que faltaba (`ConsultarSelfTrackingUseCase`, implementado en `ParticipacionProgramaService` con el mismo `RequireActiveUserGuard` que sus hermanos) y el controller ahora lo usa. Nota de diseño: a diferencia de `activate`/`deactivate`, **no** exige rol de staff — un TRAINEE puede consultar su propia participación, lo que no puede es activarla/desactivarla por esa vía.

**Cómo evitar que vuelva a pasar:**
1. **Un `Finder` del paquete `api/` NUNCA debe inyectarse en un controller.** Es el contrato entre módulos, diseñado para consumidores que ya autenticaron; un controller es una frontera externa y necesita un caso de uso (`port/in`) que aplique los guards. Si un controller inyecta algo de `<modulo>.api`, es señal de que falta un caso de uso.
2. **Cuando varios métodos comparten ruta (GET/POST/DELETE sobre el mismo recurso), verificar que TODOS apliquen la misma autenticación.** El bug 3 existía justamente porque dos de tres la aplicaban — la asimetría entre verbos hermanos es un lugar donde mirar específicamente.
3. **No asumir qué jerarquía tiene una excepción de la librería estándar.** `DateTimeParseException` parece un "argumento ilegal" conceptualmente, pero no lo es en la jerarquía de Java. Ante un handler genérico, verificar la cadena real de herencia (`extends`) antes de darla por cubierta.
4. **Probar endpoints contra la app real encuentra cosas que ninguna auditoría de código encontró.** Los 4 agentes de auditoría adversarial (E-37) leyeron este mismo código y no reportaron ninguno de estos 3 — porque los tres solo se manifiestan al ejercitar el borde exacto (una fecha basura, un UUID inventado, una cuenta suspendida en un endpoint puntual). Leer código y ejercitar código encuentran clases distintas de bugs; hacen falta los dos.

**Bug 4 (MEDIO, IDOR) — `POST /enforcer-events` no verificaba que el destino fuera del propio actor.**

Cualquier aprendiz podía registrar un evento Verdugo apuntando a la roca diaria o al registro de hábito de OTRO participante: la fila quedaba con su `participante_id` referenciando algo ajeno, rompiendo el invariante implícito de `eventos_verdugo` y ensuciando el historial del tercero. `VerdugoService.registrar` solo llamaba `requireProgreso(actorId)` — verificaba QUIÉN registra, nunca SOBRE QUÉ.

**Solución:** `requireDestinoPropio(command)` antes de construir el evento. Para `ROCA_DIARIA` usa el `LoadRocaDiariaPort` que ya existía (tabla propia de `rocks`); para `REGISTRO_HABITO` — tabla de `habits` — se agregó `VerificarDestinoVerdugoPort`, una consulta acotada a "¿pertenece a este participante?", mismo criterio con el que este módulo ya lee `participantes_programa`. Destino inexistente da 404, destino ajeno 403 (distinguirlos importa: "no existe" y "no es tuyo" son respuestas distintas).

**Bug 5 (ALTO, autorización) — cualquier MENTOR podía responder o archivar el ticket de un aprendiz ajeno.**

`TicketMentorService.responder()`/`guardar()` llamaban `requireRol(actorId, UserRole.MENTOR, "Solo el mentor asignado puede...")` — el mensaje decía "el mentor asignado" pero el código **solo miraba el rol**, nunca comparaba contra el mentor realmente asignado a ese aprendiz. Un mentor cualquiera podía contestar tickets de aprendices que no son suyos.

**Solución:** `requireMentorAsignado(actorId, ticket)`, que resuelve el mentor real vía `users.api.ParticipacionProgramaFinder` (el contrato público entre módulos, que ya exponía `mentorId`) y lo compara con el actor.

**Bug 6 (ALTO, seguridad) — el módulo `notifications` ENTERO no validaba al actor en ningún endpoint.**

Los 5 endpoints (`GET/PUT /notifications`, `GET/PATCH /notification-preferences`, `POST /push-tokens`) aceptaban cuentas SUSPENDIDAS y hasta `X-Actor-Id` inventados (devolvían `200 {"items":[]}` en vez de rechazar). Ninguno de los tres servicios del módulo inyectaba siquiera `UserSummaryFinder` — no era un chequeo mal hecho, era la ausencia total del chequeo.

**Solución:** `ActorNotificacionesGuard`, una sola clase compartida por los 3 servicios en vez de tres copias del mismo método privado.

**FALSO POSITIVO en el mismo hallazgo — `TicketSoporteService` NO era un bug, y "arreglarlo" rompió una regla de negocio deliberada.**

El agente reportó, con la misma forma que el caso anterior, que las rutas de autoservicio de tickets de soporte (`abrir`/`misTickets`/`solicitar`) aceptaban cuentas suspendidas. Se aplicó el mismo guard que a `notifications`... y el gate falló con **dos tests que afirmaban exactamente lo contrario**:

```
suspendidoSiPuedeAbrirTicketDeSoporte
    "seguridad INVERSA: un actor SUSPENDED SI puede abrir un ticket de soporte
     (regla deliberada, docs/FEATURE_SUPPORT.md)"
suspendidoSiPuedeVerSuHistorial
```

El cuerpo del propio test explica el porqué mejor que cualquier comentario: el mensaje de prueba es *"No puedo acceder a mi cuenta suspendida, necesito hablar con alguien"*. **Soporte es el único canal que le queda a una cuenta suspendida para reclamar su propia suspensión.** Bloquearlo la deja sin forma de pedir ayuda — es una excepción consciente a §0.3, no un olvido.

**Solución real:** se revirtió el guard de suspensión en las 3 rutas de autoservicio y se dejó `requireActorExiste` (solo verifica existencia, no estado). Eso conserva la regla de negocio Y arregla la parte que sí era real: un `X-Actor-Id` inexistente ahora falla como 404 en el servicio, en vez de llegar hasta la violación de FK en Postgres y salir como un 409 engañoso. `requireActorActivo` (con chequeo de suspensión) queda solo para las rutas admin.

**Lecciones — las más importantes de esta entrada:**

8. **Un agente que audita contra una regla general va a reportar toda excepción legítima como violación.** El agente aplicó §0.3 correctamente; lo que no podía saber es que existía una excepción documentada. **La responsabilidad de distinguir "viola la regla" de "es la excepción a la regla" es de quien integra el hallazgo, no del que lo reporta.**
9. **Los tests que fijan una regla contraintuitiva valen más que los que fijan la obvia.** Estos dos tests existían precisamente porque alguien anticipó que un futuro lector "corregiría" la asimetría por simetría con el resto del sistema. Atajaron ese intento exacto. Al escribir una excepción deliberada a una regla del proyecto, **el test que la fija no es opcional** — y su `@DisplayName` debe decir *por qué*, no solo *qué*.
10. **Antes de aplicar un hallazgo de seguridad, correr los tests del módulo afectado.** No para ver si compila: para ver si algún test ya documentaba la intención contraria. Es más barato que el gate completo y agarra justo este caso.

**Excepción deliberada, documentada en el código:** `NotificacionService.emitir()` **no** lleva guard. No lo invoca un usuario — lo invocan los listeners de eventos de otros módulos sobre un destinatario. Un suspendido debe seguir acumulando su bandeja (lo que no puede es leerla ni operarla), y bloquear ahí rompería el outbox de Modulith. Hay un test que fija ese comportamiento para que nadie lo "corrija" por simetría más adelante.

**Lecciones adicionales de estos tres:**
5. **Un mensaje de error que promete más de lo que el código verifica es una pista de bug, no solo un problema de redacción.** El texto "solo el mentor asignado" describía la intención del autor; el código implementaba la mitad. Al revisar autorización, vale leer el mensaje y preguntarse si el código realmente hace eso.
6. **Verificar QUIÉN actúa no es verificar SOBRE QUÉ actúa.** Los bugs 4 y 5 comparten forma: el actor estaba correctamente autenticado y tenía el rol correcto, pero nadie comprobó que el recurso destino le correspondiera. Todo caso de uso que reciba un id de recurso en el comando necesita las dos verificaciones.
7. **Cuando un módulo entero carece de una verificación, no aparece como "bug en el endpoint X" sino como ausencia total** — y por eso es fácil que pase inadvertido leyendo código módulo por módulo (no hay nada anómalo que ver; simplemente no está). Un chequeo barato: `grep -L "UserSummaryFinder\|requireActor" <servicios>` por módulo para listar los que NO lo mencionan.

## E-39 — Dos procesos Maven a la vez sobre el mismo `target/` corrompen el build y simulan cientos de bugs que no existen

**Síntoma exacto** (visto DOS veces el mismo día, con la misma firma):

```
[ERROR] Tests run: 1011, Failures: 4, Errors: 150
...
Caused by: java.lang.IllegalArgumentException: Not a managed type:
    class com.renaser.os.academy.infrastructure.adapter.out.persistence.asignacion.AsignacionCursoJpaEntity
```

Cientos de errores en cascada, concentrados en módulos **que no se habían tocado**, quejándose de que una `@Entity` "no es un tipo gestionado". Al abrir el archivo, la anotación `@Entity` **está perfectamente ahí**.

**Causa real:** algo más estaba escribiendo o borrando `target/classes` mientras el gate corría.

- **Primera vez:** la app había quedado corriendo (`spring-boot:run`, PID 13708) desde la sesión de pruebas de endpoints. El `mvnw clean` del gate borró `target/classes` bajo los pies de la JVM viva, que tenía las clases cargadas y DevTools vigilando el directorio.
- **Segunda vez:** error propio del supervisor — se lanzó `./mvnw -q compile` y `./mvnw test -Dtest=X` para verificaciones rápidas **mientras un `./mvnw clean test` seguía en vuelo**. Dos procesos Maven escribiendo el mismo `target/` se pisan.

En ambos casos el código fuente estaba intacto: el build era el corrupto, no el programa.

**Cómo reconocerlo en 10 segundos (antes de perder media hora diagnosticando):**
1. Cientos de errores, no unos pocos.
2. Concentrados en clases/módulos **que no tocaste en este cambio**.
3. Firma tipo "Not a managed type", "NoClassDefFoundError", o un `ApplicationContext` que no levanta por beans que siempre funcionaron.
4. Abrís el archivo señalado y **está bien**.

Si se cumplen los cuatro: **es el entorno, no el código.** No empieces a "arreglar" nada.

**Reglas para que no vuelva a pasar:**
1. **Un solo proceso Maven a la vez, siempre.** Antes de lanzar un gate: `Get-CimInstance Win32_Process -Filter "Name='java.exe'"` y confirmar que no hay ni app corriendo ni otro Maven. Mientras un gate está en vuelo, **no correr NADA de Maven** — ni un `compile` "rapidito", ni un test puntual. Si hace falta verificar algo urgente, se espera o se mata el gate primero.
2. **Cerrar la app antes del gate.** Un `spring-boot:run` vivo más un `clean` es la receta exacta de la primera ocurrencia. Especialmente peligroso porque la app puede haber quedado de una fase anterior de la misma sesión, sin que uno la recuerde.
3. **Ante la duda, repetir el gate en un entorno limpio antes de diagnosticar.** Cuesta unos minutos; perseguir 150 errores fantasma cuesta mucho más.

## E-40 — `src/test/resources/application.yaml` REEMPLAZA al de `main` (no lo complementa): partir el bloque `spring:` por accidente tumba el contexto entero

**Síntoma exacto:**

```
Caused by: java.lang.IllegalStateException: Google GenAI project-id must be set!
```
… en un módulo (`rag`) que ni siquiera llama a Gemini todavía (usa adaptadores NoOp), en un gate que antes pasaba.

**Causa real:** Maven/Spring resuelve `application.yaml` por **nombre de archivo en el classpath**, y el de `src/test/resources` **gana por completo** sobre el de `src/main/resources` cuando corren los tests — no se fusionan propiedad por propiedad, uno tapa al otro entero. Al agregar `renaser.web.cors.origenes` y `renaser.renasia.limite-diario` (propiedades nuevas que un `@Value` de `rag`/`shared` necesita para arrancar) se insertó el bloque `renaser:` **en medio** del bloque `spring:` del yaml de test, dejando la lista `spring.autoconfigure.exclude` (que apaga la autoconfig de Google GenAI/PgVectorStore, ver E-15) **huérfana fuera de `spring:`** — YAML no marca error de sintaxis por esto, simplemente cambia de qué es hijo de qué. Sin esa exclusión activa, Spring intentó levantar el cliente de Google GenAI de verdad y explotó por falta de credenciales.

**Solución aplicada:** reescribir el archivo completo con `spring:` intacto de punta a punta y el bloque `renaser:` como una clave de **primer nivel, al final del archivo, fuera de `spring:`** — nunca intercalado.

**Cómo evitar que vuelva a pasar:**
1. **Cualquier propiedad nueva que un bean resuelva con `@Value("${renaser...}")` en `main` tiene que agregarse TAMBIÉN en `src/test/resources/application.yaml`**, o el contexto de test no levanta — no son dos archivos que se combinan, es un reemplazo total.
2. **Al editar ese archivo, agregar bloques de primer nivel (`renaser:`, o cualquier otro hermano de `spring:`) siempre al final, nunca en medio de un bloque existente.** Un editor con resaltado de indentación lo hace obvio; a simple vista en un diff chico, no.
3. **Antes de asumir que un fallo de arranque es un bug de código, mirar el YAML completo del archivo que realmente se está usando** (`src/test/resources` para tests) — la sangría es la única pista, no hay error de parseo que avise.

## E-41 — Auditoría adversarial del módulo `rag`: 4 agentes en paralelo, 2 hallazgos reales, 2 confirmados sin bug

Cuatro agentes Sonnet en paralelo, cada uno con un ángulo distinto sobre el mismo código (`rag`/Renasia/Espejo Sombra), sin verse entre sí: seguridad/permisos (D-47), concurrencia/transacciones (streaming + cuota Redis), invariantes de dominio, límites de Modulith. Mismo patrón que la auditoría del Lote 4 (E-37): la diversidad de ángulos encuentra cosas que un solo pase no encuentra, y el hecho de que dos de los cuatro no reportaran nada es información real, no un agente "que no hizo nada" — confirma con evidencia (no con silencio) que esas dos superficies están bien.

**Hallazgo 1 (real, corregido) — la cuota diaria de Renasia se consume aunque la IA nunca responda.**

`ConversacionRenasiaService.preguntar()` llamaba `requireCuotaDisponible` (que hace `INCR` en Redis, D-48) ANTES de `vectorStorePort.buscarSimilares` y de `chatIAPort.responder`. Si cualquiera de los dos fallaba (excepción síncrona, o el `Flux` terminaba en `doOnError` sin haber emitido nada), el aprendiz perdía uno de sus 25 mensajes diarios sin recibir respuesta. Invisible hoy porque el adaptador de IA es `NoOp` (nunca falla) — se iba a activar solo, en silencio, el día que se conecte Gemini real (D-39), y para entonces nadie iba a asociar "se me acaba la cuota más rápido de lo normal" con este código.

**Solución:** se agregó `ControlCuotaRenasiaPort.liberar(UserId)` (implementado en `ControlCuotaRedisAdapter` con un `DECR` sobre la misma clave del día) y se envuelve la parte síncrona de `preguntar()` en un `try/catch` que libera y relanza, y el `doOnError` del `Flux` también libera antes de loguear. Nota de diseño: `liberar` sobre una clave que ya cruzó a un día distinto (medianoche de por medio) es un no-op sobre una clave vieja — aceptable, no hace falta que sea perfecto retroactivamente.

**Hallazgo 2 (real, corregido) — `MensajeRenasia` no protegía en el dominio que solo un mensaje del ASISTENTE puede llevar `fuentes`.**

El invariante ("un mensaje de USUARIO nunca cita la base de conocimiento") solo se cumplía por convención de los dos factory methods públicos (`escribirDeUsuario` pasaba `List.of()` a mano). Ni el método privado `crear` ni `rehydrate` (el que usa el adaptador de persistencia para reconstruir desde BD) lo validaban — exactamente el tipo de invariante que CLAUDE.MD §5.1.1 pide proteger en el dominio, no confiar en la disciplina del caller. El adaptador de persistencia ya trae las fuentes de TODOS los mensajes de una página sin filtrar por rol antes de pasarlas a `rehydrate`, así que una fila huérfana en `fuentes_mensaje_renasia` (futuro bug de adapter, migración manual, un tercer factory method que no respete la regla) se reconstruiría en un `MensajeRenasia` con `rol=USUARIO` y fuentes no vacías, silenciosamente.

**Solución:** `requireFuentesSoloDeAsistente(rol, fuentes)` en `crear` y en `rehydrate` — rechaza con `IllegalArgumentException` si `rol == USUARIO && !fuentes.isEmpty()`.

**Confirmado sin bug (no se tocó código):**
- Seguridad/permisos: los 3 servicios (`ConocimientoService`, `EspejoSombraService`, `ConversacionRenasiaService`) aplican el patrón correcto de actor-activo + rol/relación en todos sus métodos públicos, incluida la lección de `TicketMentorService` (E-38: "mentor asignado" tiene que comparar contra el mentor real, no solo mirar el rol) — ya estaba bien implementado desde la construcción del módulo, no como parche posterior.
- Límites de Modulith: imports solo vía `.api.`, `domain/` sin framework, controllers tontos, sin fuga de `*JpaEntity` a través de los puertos, sin colisión de nombres de clase con los otros 13 módulos.

**Hallazgos de severidad baja, evaluados y DEJADOS SIN CORREGIR a propósito (no todo hallazgo real amerita una corrección):**
- Orden del historial de Renasia bajo dos preguntas concurrentes muy rápidas del mismo actor (doble-tap): posible que la respuesta más rápida quede ordenada antes que una pregunta anterior más lenta. Solo estética de UI, ninguna pérdida de datos; arreglarlo bien requeriría serializar escrituras por conversación (lock o secuencia), un costo que no se justifica para un caso límite de baja probabilidad.
- Ventana no atómica entre `INCR` y `EXPIRE` en `ControlCuotaRedisAdapter`: si el proceso muere en el instante exacto entre ambas llamadas, la clave de cuota queda sin TTL — pero como la clave incluye la fecha (`renasia:cuota:{usuario}:{fecha}`), degrada a una clave huérfana que ocupa memoria en Redis, NO a que el usuario quede bloqueado (al día siguiente la clave es otra). Corregirlo necesitaría un script Lua o `EXPIRE ... NX` (soporte condicional que depende de la versión de Spring Data Redis) para una probabilidad de ocurrencia mínima.

**Lecciones:**
1. **4 agentes con ángulos distintos sobre el mismo módulo, en paralelo, sin verse entre sí, es más barato que 1 agente "que audite todo"** — cada uno profundizó en su ángulo en vez de repartir superficialmente la atención entre seguridad, concurrencia, dominio y arquitectura a la vez.
2. **No todo hallazgo real se corrige.** Evaluar severidad × probabilidad × costo de la corrección antes de tocar código — los dos hallazgos de baja severidad de esta entrada se documentan como decisión consciente, no como deuda técnica olvidada, para que nadie los "redescubra" y gaste tiempo en ellos sin saber que ya se evaluaron.
3. **Antes de aplicar una corrección de dominio que toca `rehydrate`, correr el test de persistencia con Testcontainers real** (no solo los tests unitarios de dominio) — es el único que ejercita el camino completo BD→mapper→`rehydrate` y hubiera agarrado una regresión si la nueva validación fuera incompatible con datos ya persistidos.

## E-42 — E2E contra la app real (4 agentes, un flujo completo cada uno): un actor SUSPENDIDO podía reaccionar y comentar en el Muro

Después del gate en verde de los 14 módulos, se corrieron 4 flujos E2E completos con `curl` contra la app real (Postgres/Redis en Docker, sin mocks), cada uno cruzando varios módulos de punta a punta (alta→onboarding→firma de contrato; hábito completado→puntos→notificación; muro→calendario→chat; academy→support→Renasia). Confirmaron de punta a punta, contra el sistema real y no solo en tests unitarios, varios fixes de sesiones anteriores (Verdugo destino ajeno de E-38, mentor-asignado en tickets de E-38, cuota diaria de Renasia de D-48) — y encontraron un bug nuevo real.

**Bug — `PublicacionMuroService.reaccionar()` y `ComentarioMuroService.escribir()` no chequeaban el estado del actor en absoluto.**

Un actor `SUSPENDIDO` recibía `200`/`201` al reaccionar o comentar en el Muro, mientras que `publicar()`/`feed()` (mismo módulo) correctamente daban 403 — igual que `chat`, `calendar` y `notifications`, que bloquean sistemáticamente al actor suspendido en todas sus operaciones. Causa real: `reaccionar()` solo llamaba `requireVisible(...)` (existe la publicación), nunca `requireActorActivo(...)` (que sí existe en la misma clase y se usa en otros métodos); `ComentarioMuroService.escribir()`/`editar()`/`ocultar()` ni siquiera importaban ese guard — no faltaba invocarlo, faltaba por completo en la clase.

**Solución aplicada, en dos intentos (el primero introdujo una regresión de seguridad distinta):**

El primer intento agregó `requireActorActivo(actorId)` al PRINCIPIO de `reaccionar()`/`editar()`/`ocultar()`, ANTES del chequeo de visibilidad del recurso. El gate lo rechazó: rompió `ocultarConActorInexistenteEsRechazadoComo403NoComo404` (test ya existente en ambos servicios). Motivo: `requireActorActivo` (el método pre-existente, usado por `feed()`/`publicar()`) lanza `NoSuchElementException("Actor no encontrado")` para un actor inexistente — correcto ahí porque no hay ningún recurso previo cuya existencia se pueda filtrar. Pero puesto ANTES de confirmar que el recurso existe en `ocultar()`, ese mismo `NoSuchElementException` se comporta como un 404 con un mensaje distinto al de "recurso no encontrado" — exactamente el patrón que `docs/MODULO_COMMUNITY.md` sec. 5 y `esModerador` (fail-closed a `false`, nunca una excepción de tipo distinto) existen para evitar (mismo principio que **E-30**: un chequeo que lanza en vez de fallar-cerrado convierte un 403 en un 404 que filtra información).

**Solución final:** un guard nuevo, fail-closed como `esModerador` (`actorActivo(actorId)` devuelve `boolean`, `.orElse(false)` para inexistente o suspendido, nunca lanza `NoSuchElementException`), invocado SIEMPRE DESPUÉS de confirmar la visibilidad del recurso — mismo orden que ya usaban `esModerador`/`requireModerador`. Se agregó a `reaccionar()`, `editar()` y `ocultar()` en `PublicacionMuroService` (los dos últimos no tenían NINGÚN chequeo de actor, ni siquiera se habían reportado como bug — se corrigieron igual por ser la misma clase de falla en la misma clase), y a `escribir()`, `editar()` y `ocultar()` de `ComentarioMuroService` (que reemplazó ahí su primer intento, basado en el `requireActorActivo` que lanzaba, por el mismo guard fail-closed).

**Hallazgo relacionado, NO corregido — `points.PuntajeService.consultar()` no le da acceso al mentor asignado.**

Mismo E2E: un mentor real y asignado a un aprendiz recibe 403 al consultar el puntaje de ESE aprendiz — solo el propio participante o un ADMIN/ALCHEMIST pueden verlo. Esto contradice el patrón de `requireMentorScope` que CLAUDE.MD §5.3.4 establece como una de las 3 funciones de autorización del sistema, y que sí está implementado en otros módulos (`EspejoSombraService`, D-47; `TicketMentorService`, tras el fix de E-38). A diferencia de `rocks` (que sí tiene una decisión explícita, RK-7: "sin vista de mentor, mismo criterio que el repo viejo"), en `points` no hay ninguna decisión registrada — puede ser el mismo criterio deliberado o un vacío real. **No se agregó el guard sin confirmar** (CLAUDE.MD §0.6: no inventar reglas de negocio) — se documentó como pregunta abierta (`docs/MODULO_POINTS.md` Q-6) para que el dueño del producto decida.

**Lecciones:**
1. **Un flujo E2E completo encuentra bugs que ni la auditoría de código ni las pruebas endpoint-por-endpoint sueltas encuentran** — este bug sobrevivió a la construcción del módulo `community`, a su propio testing, y no fue lo que se estaba buscando en este E2E (el flujo pedía probar aislamiento entre aprendices, no reacciones de un suspendido); apareció porque el guion de prueba incluía sistemáticamente "repetir con SUSPENDIDO" en cada flujo, no porque alguien sospechara de `reaccionar`/`comentar` en particular.
2. **Cuando se encuentra que un método de una clase le falta un guard que sus hermanos sí tienen, revisar TODOS los métodos de esa clase, no solo el reportado.** El E2E solo probó `reaccionar` y `comentar`; `editar`/`ocultar` de `PublicacionMuroService` tenían el mismo hueco y no habían sido ni siquiera mencionados — se encontraron al leer el archivo completo para aplicar el fix reportado.
3. **Un "gap de funcionalidad" encontrado en E2E no es automáticamente un bug para corregir.** El caso de `points`/mentor y de `rocks` (sin vista de mentor en absoluto, ya documentado a propósito) se ven idénticos desde afuera (mentor recibe 403), pero uno es una decisión ya tomada y documentada, el otro es un vacío sin decisión — la diferencia solo se ve leyendo la documentación del módulo, no el código ni el resultado del curl.
4. **Al agregar un chequeo de actor a un método que ya tiene un chequeo de visibilidad de recurso, el ORDEN importa tanto como el chequeo mismo.** Ponerlo primero (antes de confirmar que el recurso existe) parece más prolijo ("fallar rápido"), pero si el chequeo de actor puede lanzar un tipo de excepción que un chequeo de recurso también usa (acá, `NoSuchElementException` → 404 en ambos), un actor inexistente termina dando una pista sobre si el recurso existe o no. La regla general: en cualquier método que primero confirma visibilidad y después verifica autorización, el chequeo de actor va SIEMPRE después del de visibilidad, y SIEMPRE fail-closed a la misma excepción/status que los demás chequeos de autorización de ese método (acá, 403 vía `boolean.orElse(false)`, nunca un `orElseThrow` con tipo distinto) — correr los tests existentes del archivo (no solo los nuevos) antes de dar el fix por bueno es lo que lo agarró acá.

## E-43 — Reincidencia de E-38: capturar la subclase en vez de la familia dejó abierta la hermana (`ZoneRulesException` → 500 con stacktrace)

**Síntoma:** `POST /api/v1/calendar/events` con `"timezone":"America/Nolandia"` (una zona horaria inexistente) devuelve **500 con el stacktrace completo** en el cuerpo, filtrando rutas de clases internas y la cadena de filtros de Spring Security. Idéntico al síntoma original de **E-38**, que se había dado por corregido.

**Causa real:** el fix de E-38 agregó al `GlobalExceptionHandler` un `@ExceptionHandler(DateTimeParseException.class)` — la subclase **de parseo**. Pero `ZoneId.of("basura")` no falla parseando: lanza `ZoneRulesException`, que cuelga de `DateTimeException` por **otra rama** del árbol. Nunca fue un `DateTimeParseException`, así que se escapaba por el mismo agujero que E-38 supuestamente había tapado. `EventoController:150` hace `ZoneId.of(r.timezone())` directo sobre texto que manda el cliente, sin validación previa.

**Solución:** se agregó `@ExceptionHandler(java.time.DateTimeException.class)` — **la clase padre**, que cierra la familia entera de una vez. Se conservó el handler específico de `DateTimeParseException` porque Spring elige siempre el más específico y así el mensaje de error sigue siendo preciso ("se espera ISO-8601") para el caso de formato, mientras el padre atrapa todo lo demás con un mensaje genérico. Test de regresión en `EventoControllerValidationTest`.

**Cómo evitar que vuelva a pasar:** cuando un handler se agrega para tapar un 500, **preguntarse cuál es la familia completa de esa excepción, no solo la que apareció en el stacktrace de ese día.** El árbol de `java.time` es el ejemplo canónico: `DateTimeException` tiene al menos `DateTimeParseException` (formato) y `ZoneRulesException` (zona inexistente), y una request puede disparar cualquiera de las dos por el mismo campo mal cargado.

**Lecciones:**
1. **Un fix que captura una subclase concreta cierra un caso, no una clase de bugs.** E-38 se cerró con la excepción que se había visto en el navegador ese día; la hermana quedó viva cuatro días hasta que otro sondeo la encontró. Cuando la jerarquía es cerrada y conocida (como `java.time`), capturar el padre es más barato que ir agregando una subclase por reporte.
2. **El mismo síntoma exacto reapareciendo es señal de fix incompleto, no de bug nuevo.** Buscar en esta bitácora por el síntoma (no por la excepción) antes de diagnosticar de cero: acá la entrada de E-38 ya tenía el diagnóstico correcto a medias, incluida la observación de que `DateTimeException` NO es un `IllegalArgumentException` — le faltaba dar el paso de subir un nivel más.

## E-44 — `JpaRepository.deleteById` por default NO es idempotente: un cron de purga que reintenta puede tirar `EmptyResultDataAccessException`

**Síntoma:** al escribir el cron de purga de bajas de cuenta (gap #5, `AccountDeletionService.purgeExpired`), la primera versión de `UserPersistenceAdapter.deleteById` delegaba directo en `SpringDataUserRepository.deleteById(id)`. En un escenario realista — dos pasadas del cron solapadas, o un reintento manual sobre una fila que ya se purgó en la pasada anterior — Spring Data lanza `EmptyResultDataAccessException` en vez de simplemente no hacer nada.

**Causa real:** `SimpleJpaRepository.deleteById` (la implementación por default de Spring Data JPA) hace `findById(id).orElseThrow(...)` antes de borrar — está pensado para el caso "yo sé que el id existe", no para un borrado tipo "si está, sacalo". El contrato de `CrudRepository.deleteById` en la documentación no promete idempotencia; asumir que sí la tiene (como haría, por ejemplo, un `DELETE FROM tabla WHERE id = ?` en SQL plano, que no falla si no matchea ninguna fila) es el error.

**Solución:** `UserPersistenceAdapter.deleteById` ahora hace `existsById` antes de `deleteById` — la misma idempotencia que ya tenía `ParticipacionProgramaPersistenceAdapter.deleteByParticipanteId` (ese sí, desde el principio, con `existsById` + devolver `boolean`). Documentado en el javadoc del método y del puerto (`DeleteUserPort`): "idempotente: borrar un id que ya no existe no falla" es parte del contrato, no un detalle de implementación.

**Cómo evitar que vuelva a pasar:** cualquier puerto de borrado que un cron o un flujo con reintentos vaya a llamar debe documentar explícitamente si es idempotente, y si usa Spring Data `deleteById` directo, agregar el `existsById` antes — no asumir que el ORM se comporta como el `DELETE` de SQL plano.

**Lecciones:**
1. **Spring Data JPA y SQL plano no tienen la misma semántica de "borrar lo que no existe".** Un `DELETE ... WHERE id = ?` en SQL nunca falla por 0 filas afectadas; `deleteById` de Spring Data sí, porque internamente carga la entidad primero. Quien viene de pensar en SQL directo puede asumir mal.
2. **Un patrón ya resuelto en el mismo módulo (`ParticipacionProgramaPersistenceAdapter`) es la primera referencia a mirar antes de escribir un puerto de borrado nuevo** — el `existsById` ya estaba ahí, con el mismo razonamiento, un día antes.

---

## E-45 — Tres endpoints del contrato de la app nunca se migraron y devolvían **405**, no 404: el frontend los tomaba como "no se pudo comprobar" y seguía

**Síntoma:** con el backend Java levantado, la pantalla de registro de la app quedaba fija en *"Comprobando…"* bajo el campo de correo, y el botón de pedir código nunca aparecía. Al probar a mano:

```
POST /api/v1/account-requests/check-email   -> 405
POST /api/v1/account-requests/verify-email  -> 405
POST /api/v1/account-requests/exists        -> 405
```

**Causa real — dos cosas distintas superpuestas, y esa fue la parte que costó:**

1. **El cuelgue NO era el 405.** Era red: un dispositivo Android **físico** no alcanza el `localhost` de la PC (su `localhost` es el propio teléfono), así que el `fetch` no devolvía nada — ni error ni respuesta — y `estadoLocal` se quedaba en `comprobando` para siempre. Se resuelve con `adb reverse tcp:8080 tcp:8080`, que mapea el `localhost:8080` del teléfono al de la PC.
2. **Los tres endpoints existían en el contrato que la app ya consumía (AR-04/05/06 del repo viejo) pero nunca se portaron a Java.** Devolvían 405 y no 404 porque Spring resuelve primero la ruta contra `@RequestMapping("/api/v1/account-requests")` y recién después el método.

Lo insidioso: el frontend **degrada bien** ante un no-2xx (`if (!res.ok) return null` → estado `sin_comprobar` → *"No pudimos comprobarlo ahora. Puedes continuar"*). O sea que una vez arreglada la red, la app **funcionaba igual** con los tres endpoints faltando — el hueco quedaba invisible salvo por un mensaje degradado que se lee como un problema de conexión pasajero.

**Solución:** portar los tres (D-46) con sus reglas literales del repo viejo, y `adb reverse` para probar contra un teléfono físico.

**Cómo evitar que vuelva a pasar:**
- Al migrar un módulo, el paso 0 (D-33) tiene que incluir **listar las rutas que el cliente ya consume** (`grep -rhoE '/api/v1/[a-z0-9/_{}$.-]+' src/services src/lib`) y contrastarlas contra los `@*Mapping` del backend. La diferencia es la lista de lo que falta — es una comprobación de dos comandos que acá habría ahorrado el diagnóstico entero.
- **Un endpoint faltante que el cliente tolera es peor que uno que rompe**, porque no aparece en ninguna pantalla de error. La comprobación de arriba es la única que lo encuentra.

**Lecciones:**
1. **405 en vez de 404 no significa "método equivocado": con un `@RequestMapping` de clase, significa "la ruta base existe, ese sub-path no".** Leerlo como error del cliente hace perder tiempo.
2. **Separar "no responde" de "responde mal" antes de tocar código.** Un `curl` desde la PC contra el mismo endpoint (que sí llegaba, y devolvía 405) distinguió en un comando dos fallos que desde la app se veían como uno solo.
3. **Un teléfono físico no comparte `localhost` con la PC.** `adb reverse tcp:PUERTO tcp:PUERTO` lo resuelve sin depender de la IP de la LAN, que cambia de red en red.

---

## E-46 — Un factory estático no puede llamarse igual que el accessor del `record`: `invalid accessor method`

**Síntoma:** al compilar, sobre un `record` con un método estático de fábrica del mismo nombre que uno de sus componentes:

```
invalid accessor method in record ...ResultadoVerificacionDominio
  (return type of accessor method entregable() must match the type of record component entregable)
```

**Causa real:** el `record` tenía el componente `Boolean entregable` (por lo tanto un accessor `entregable()`) y además un factory `public static ResultadoVerificacionDominio entregable()`. Para el compilador ese estático es un intento de **redefinir el accessor** con otro tipo de retorno, no un método nuevo — de ahí el mensaje, que habla de accessor cuando uno cree estar escribiendo una fábrica.

**Solución:** nombrar los factories por **intención** en vez de por el campo que setean: `puedeRecibir()`, `noPuedeRecibir(motivo)`, `noSeSabe()`. Quedó mejor que el original — `ResultadoVerificacionDominio.puedeRecibir()` se lee como una frase y no repite el nombre del campo.

**Cómo evitar que vuelva a pasar:** en un `record`, ningún método (ni de instancia ni estático, con o sin parámetros) puede llamarse igual que un componente. Con la convención de CLAUDE.MD §5.4.8 de nombrar por intención de negocio esto casi no aparece; surge justo cuando uno nombra el factory por el campo.

**Lección:** el mensaje del compilador nombra el síntoma (`accessor`) y no la causa (colisión con un componente del record). Ante un `invalid accessor method`, buscar el **choque de nombres**, no un problema de tipos.

---

## E-47 — Flyway: `ERROR: VALUES lists must all be the same length` al escribir un INSERT multi-fila a mano

- **Fecha:** 2026-08-28
- **Dónde:** `src/main/resources/db/migration/V5__guias_audios_habitos_default.sql`, migración de datos del catálogo de hábitos (`docs/db/migracion/`)
- **Síntoma:** `./mvnw test` falla en cascada (262 tests con error, todos por el mismo `ApplicationContext failure threshold (1) exceeded`) porque Flyway no aplica la migración al levantar el contexto de Spring:
  ```
  Caused by: org.postgresql.util.PSQLException: ERROR: VALUES lists must all be the same length
    Position: 2228
  Location   : db/migration/V5__guias_audios_habitos_default.sql
  Line       : 61
  Statement  : Run Flyway with -X option to see the actual statement causing the problem
  ```
  El `Line` que reporta Flyway es la línea del `INSERT INTO ... VALUES` completo, **no** la fila real con el problema — con un INSERT multi-fila de cientos de líneas, ese número no sirve para ubicar el error.
- **Causa real:** una de las 17 filas de `guias_habito` tenía 15 valores en vez de 16 (faltaba un `NULL` entre dos columnas nullable consecutivas) — se perdió al transcribir a mano el SQL generado, no al extraer el dato de origen (el dato fuente, verificado aparte, tenía los 16 campos correctos).
- **Solución:** en vez de leer el archivo línea por línea a ojo, se escribió un parser chico en Node (respeta comillas simples y `''` escapado) que cuenta las columnas de cada tupla del `VALUES` y compara contra el número esperado de la lista de columnas del `INSERT`. Encontró la fila exacta (`38d56b8e-...`) en segundos.
- **Cómo evitarlo:** para cualquier `INSERT` de más de ~5 filas escrito a partir de datos migrados, generar el SQL programáticamente (script que arma cada tupla desde una lista de campos fija) en vez de transcribirlo a mano, y validar el conteo de columnas por fila **antes** de correr `mvnw test` — es más rápido que esperar el ciclo completo de Testcontainers para descubrir un error de transcripción.

## E-48 — "No hay uso real" no se puede concluir revisando una sola tabla: `onboarding_answers` vacía no significaba que `las_90_variables` no se usara

- **Fecha:** 2026-08-28
- **Dónde:** análisis previo a `V10__catalogo_onboarding_default.sql` (migración del catálogo de onboarding, D-52)
- **Síntoma:** ninguno técnico — fue una conclusión de análisis, no un fallo de build. Reporté que el flujo `las_90_variables` (90 de las 192 preguntas de onboarding) estaba "muerto"/sin lanzar, porque crucé sus `question_key` contra `onboarding_answers` del dump de producción y salieron cero respuestas para cualquiera de las 90 claves.
- **Causa real:** ese flujo no guarda sus respuestas en `onboarding_answers` — tiene su **propia tabla dedicada**, `variables_90_recordings`, que no revisé antes de concluir. El dueño del proyecto lo señaló directamente ("busca todo completo estás seguro que no hay registros de los usuarios de estos audios? nd registro"). Al revisarla: 221 grabaciones reales de 17 usuarios distintos, cubriendo las 90 de 90 claves, con su propio pipeline de revisión por IA (`ia_status`). El flujo sí se usa — mucho — solo que en una tabla distinta a la que yo asumí.
- **Solución:** se retractó la conclusión explícitamente y se corrigió la recomendación (de "migrar las 192 preguntas" a "excluir igual las_90_variables, pero por el motivo correcto": el catálogo de esas 90 preguntas no es lo que el cliente móvil lee — lee las grabaciones directo — no porque el flujo esté sin usar).
- **Cómo evitarlo:** cuando la pregunta es "¿esto se usa?", revisar **todas las tablas donde la evidencia de uso podría vivir** antes de afirmar que no se usa — en un dominio con tablas específicas por tipo de dato (`onboarding_answers` genérica vs. `variables_90_recordings` específica de audio), una tabla vacía prueba que *esa* tabla no se usó, no que la *feature* no se usó. Un solo chequeo negativo nunca es prueba suficiente de no-uso; hace falta descartar cada ubicación plausible antes de concluir.

## E-49 — `500 Internal Server Error` real en `POST /api/v1/admin/habits/schedules/{scheduleId}`: `@RequestBody JsonNode` del paquete viejo de Jackson, pero el conversor activo en runtime es Jackson 3

- **Fecha:** 2026-08-28
- **Dónde:** `HorarioHabitoAdminController.actualizar` (`src/main/java/com/renaser/os/habits/infrastructure/adapter/in/rest/horarioadmin/HorarioHabitoAdminController.java:75`) y `PartialUpdateScheduleRequest.from` (mismo paquete) — encontrado al probar el endpoint EN VIVO con `curl` real, no en `mvnw test` (los tests existentes no cubrían este endpoint con un `MockMvc`/JSON real que pasara por el `HttpMessageConverter` de Spring).
- **Síntoma:** cualquier `POST /api/v1/admin/habits/schedules/{scheduleId}` con body JSON devuelve:
  ```
  500 Internal Server Error
  "message": "Type definition error: [simple type, class com.fasterxml.jackson.databind.JsonNode]"
  ...InvalidDefinitionException: Cannot construct instance of com.fasterxml.jackson.databind.JsonNode (no Creators, like default constructor, exist)...
  ```
- **Causa real:** el controller y `PartialUpdateScheduleRequest` importan `com.fasterxml.jackson.databind.JsonNode` (paquete de **Jackson 2**, usado ahí a propósito para distinguir "clave ausente" de "clave presente en `null`" — ver el javadoc del método). El `pom.xml` de este proyecto es Spring Boot 4.1, que trae **Jackson 3** (`tools.jackson.*`) como el Jackson real que arma el `HttpMessageConverter` de Spring MVC. Jackson 2 sigue presente en el `.m2` local (`com.fasterxml.jackson.core:jackson-databind:2.21.x`) porque alguna otra dependencia transitiva lo trae, así que **el código compila sin error** — pero en runtime, cuando Spring intenta deserializar el body a ese tipo, usa su `ObjectMapper` de Jackson 3, que no sabe instanciar una clase de la API de Jackson 2. Es el único lugar de todo el repo que usa `JsonNode` crudo como `@RequestBody` (documentado como "la única excepción" en el propio javadoc del controller) — por eso ningún otro endpoint tiene este problema.
- **Solución:** cambiar el import en ambos archivos de `com.fasterxml.jackson.databind.JsonNode` a `tools.jackson.databind.JsonNode` (Jackson 3, ya en el classpath vía Spring Boot 4.1). La API de los métodos usados (`hasNonNull`, `get`, `has`, `isNull`, `asInt`, `asText`) es idéntica entre ambas versiones para este caso de uso, así que el cambio es solo de import.
- **Cómo evitarlo:** en un proyecto que migró a Jackson 3 (Spring Boot 4.1+), **nunca usar `com.fasterxml.jackson.databind.*` a mano** en código nuevo, ni siquiera cuando compila — el IDE/autocompletado puede ofrecer la clase vieja porque ambas conviven en el `.m2`. Verificar el import cuando se declara un tipo de Jackson explícito (`JsonNode`, `ObjectMapper`, `ObjectNode`) es exactamente el tipo de detalle que un test unitario con mocks no agarra pero un `curl` real contra el endpoint sí — refuerza por qué probar endpoints en vivo, no solo con `MockMvc`/mocks, tiene valor real.
- **Corregido el mismo día (2026-08-28):** cambiado el import a `tools.jackson.databind.JsonNode` en `HorarioHabitoAdminController.java` y `PartialUpdateScheduleRequest.java` — misma API (`get`/`has`/`hasNonNull`/`isNull`/`asInt`/`asText`, verificado con `javap` contra el jar 3.1.5 real antes de aplicar el cambio, no asumido). `./mvnw clean test`: 1665/1665 en verde. Reprobado en vivo contra el servidor corriendo: `POST /api/v1/admin/habits/schedules/{id}` con `{"endDay":96}` → `200` (antes 500), y con `{"endDay":null}` → `200` con `endDay:null` en la respuesta (el caso de "null explícito limpia el campo" que motivó usar `JsonNode` en primer lugar sigue funcionando igual).

## E-50 — El ER de la BD nueva se desfasó en silencio: `V2`, `V3` y `V8` cambiaron el esquema y nadie tocó el `.drawio`

- **Fecha:** 2026-08-31
- **Dónde:** `docs/db/ER_BD_NUEVA.drawio` contra `src/main/resources/db/migration/`
- **Síntoma:** ninguno. **Ese es el problema**: no hay mensaje de error, no falla ningún test, `ArchitectureTest` pasa, el build está verde. El diagrama simplemente describe una base que ya no es la que está corriendo. Se detectó recién al compararlo a mano contra las migraciones.
- **Causa real:** el `.drawio` se dibujó el 2026-08-24, cuando el esquema eran las 90 tablas de `V1`. Después entraron tres migraciones que lo cambiaron y ninguna actualizó el dibujo:
  - `V2__spring_modulith_event_publication.sql` → tabla `event_publication` (outbox de Modulith)
  - `V3__auth_credenciales_e_identidades.sql` → tabla `identidades_externas` + columnas `usuarios.hash_contrasena` y `usuarios.contrasena_actualizada_en`
  - `V8__audioterapias_duracion_configurable.sql` → columna `audioterapias.duracion_dias`

  Cuatro divergencias sobre 92 tablas y 125 FK; el resto del ER era exacto. El daño no es el porcentaje: es que quien lea el ER para programar auth va a creer que el login social no tiene dónde guardarse.
- **Solución:** se agregaron al `.drawio` las dos tablas, las tres columnas y la FK `identidades_externas → usuarios`, y se escribió `docs/db/verificar-er-vs-sql.mjs`, que compara tabla por tabla y columna por columna el diagrama contra las migraciones y sale con código 1 si divergen:
  ```
  node docs/db/verificar-er-vs-sql.mjs
  ```
- **Cómo evitarlo:** **correr ese script al agregar una migración**, en el mismo cambio que la agrega. Un diagrama sin chequeo automático se desfasa siempre; la pregunta no es si pasa sino cuándo se nota.
- **Dos trampas del script, por si hay que tocarlo:**
  1. El cierre de un `CREATE TABLE` **no siempre es `);`** — `V1` usa `) WITH (fillfactor = 70);` en las tablas calientes. Un regex que exija `\n\);` fusiona esa tabla con la siguiente y reporta divergencias falsas en cascada (pasó: 88 tablas "con diferencias" que en realidad estaban bien).
  2. El ER marca las PK compuestas como `PK,FK  columna: tipo`, no como `PK  columna`. Un regex que solo saque el prefijo `PK` deja `,FK` pegado y reporta como faltante toda columna de toda tabla asociativa (pasó: 32 falsos positivos).

## E-51 — `cannot find symbol` tras renombrar un campo con Lombok: el getter generado no aparece en un `grep` del nombre del campo

- **Fecha:** 2026-08-31
- **Dónde:** `AccountRequestPersistenceMapper.java`, durante el renombre `supabaseUserId` → `usuarioId` (D-53)
- **Síntoma:**
  ```
  [ERROR] .../AccountRequestPersistenceMapper.java:[16,28] cannot find symbol
  [INFO] BUILD FAILURE
  ```
  El mensaje **no dice qué símbolo** falta. Antes de esto, un `grep -rn "supabaseUserId" src/` daba cero resultados en código — el renombre parecía completo.
- **Causa real:** el campo estaba en una entidad con `@Data` de Lombok, así que el accesor generado es **`getSupabaseUserId()`, con `S` mayúscula**. `grep "supabaseUserId"` no lo encuentra: Lombok capitaliza la primera letra al armar el getter, y ese nombre no aparece escrito en ningún lado del código fuente — solo en el bytecode generado y en las llamadas que lo usan.
- **Solución:** `grep -rn "SupabaseUserId" src/` (con mayúscula) encontró la única llamada, `e.getSupabaseUserId()` en el mapper. Cambiada a `e.getUsuarioId()`. `./mvnw clean test`: 1672/1672.
- **Cómo evitarlo:** al renombrar un campo de una clase con Lombok, buscar **las dos formas**: el nombre del campo y el nombre capitalizado que usan `get`/`set`/`with`. En una sola pasada:
  ```bash
  grep -rniE "supabaseUserId" src/        # -i cubre campo, getter y setter de una vez
  ```
  El `-i` es la diferencia entre creer que el renombre está completo y que lo esté. Aplica igual a `@Getter`, `@Data` y `@Builder`.

## E-52 — Un cambio de 1 línea aparece como 623 en `git diff`: Python reescribió el archivo con CRLF

- **Fecha:** 2026-08-31
- **Dónde:** `docs/MODULOS_A_AVANZAR.md`, al insertar la decisión D-53 con un script de Python
- **Síntoma:** no hay mensaje de error. `git diff --stat` reporta:
  ```
  docs/MODULOS_A_AVANZAR.md | 623 +++++++++++----------
  1 file changed, 312 insertions(+), 311 deletions(-)
  ```
  cuando el cambio real era **una sola línea agregada**. La pista para confirmarlo:
  ```bash
  git diff --stat -w --ignore-cr-at-eol docs/MODULOS_A_AVANZAR.md   # -> 1 insertion(+)
  ```
- **Causa real:** dos cosas que se combinan y por separado no molestan:
  1. `io.open(p, 'w', encoding='utf-8')` en Windows usa `newline=None`, que traduce cada `\n` a `\r\n`. Un script que lee, modifica y reescribe **convierte todo el archivo a CRLF sin avisar**.
  2. Este repo tiene `core.autocrlf=true`, que normalmente absorbe eso — pero **git clasifica este archivo como binario** (`git ls-files --eol` devuelve `w/-text`), y a un binario no le aplica la conversión. Resultado: git compara byte a byte y ve las 311 líneas distintas. Por eso el resto de los archivos editados el mismo día salieron con diffs proporcionales y solo este explotó. No hay bytes NUL: es la heurística de git, y da igual el motivo — lo que importa es que a un archivo `-text` la red de seguridad de `autocrlf` **no lo cubre**.
- **Solución:** reescribir el archivo con los finales de línea que ya tenía:
  ```python
  s = io.open(p, encoding='utf-8', newline='').read()   # newline='' = no traducir al leer
  io.open(p, 'w', encoding='utf-8', newline='').write(s)  # ni al escribir
  ```
- **Cómo evitarlo:** **usar siempre `newline=''` en las dos puntas** cuando un script de Python edita un archivo existente del repo. Y ante un `--stat` desproporcionado, antes de investigar el contenido, comparar:
  ```bash
  git diff --stat <archivo>
  git diff --stat -w --ignore-cr-at-eol <archivo>
  ```
  Si el segundo es mucho menor, el problema son los finales de línea, no el contenido.

## E-53 — Un cambio de horario "programado para mañana" que no se aplicaba nunca: se escribía la fila y nadie la leía jamás

- **Fecha:** 2026-08-31
- **Dónde:** `habits` — `PreferenciaHorarioService.aplicarEdicion` (`src/main/java/com/renaser/os/habits/application/services/PreferenciaHorarioService.java`), tabla `cambios_horario_pendientes`
- **Síntoma:** ningún error, ninguna excepción, ningún log — **ese es el problema**. `PATCH /api/v1/habit-preferences/{habitId}` con la ventana del día ya arrancada responde `200` con:
  ```json
  { "habitId": "...", "triggerTime": "07:00", "limitTime": "09:00",
    "deferred": true, "deferredEffectiveDate": "2026-09-01", "scheduleEdits": {...} }
  ```
  y al llegar el 2026-09-01 el horario del aprendiz sigue siendo el viejo. La fila de
  `cambios_horario_pendientes` queda ahí para siempre, sin que nada la mire.
- **Causa real:** la rama diferida solo hacía `saveCambioPendientePort.save(pendiente)`. **No había ningún lector del otro lado**, y eso se puede verificar de tres formas independientes, todas negativas:
  1. `LoadCambioHorarioPendientePort` no lo inyectaba **ningún** servicio — solo lo implementaba su propio adaptador.
  2. `CambioHorarioPendiente.rigeEn(LocalDate)` existía en el dominio y no lo llamaba nadie.
  3. `TracksDelDiaProyeccionService`, que arma el día del aprendiz, inyecta `LoadHorarioHabitoPort` y `LoadPreferenciaHorarioPort` — no los pendientes.
  Un puerto de salida escrito y nunca leído es exactamente una feature a medio cablear que pasa todos los tests: los del servicio verificaban `verify(saveCambioPendientePort).save(any())`, que es cierto y no dice nada sobre si alguien lo consume después.
- **Solución:** caso de uso `PromoverCambiosHorarioProgramadosUseCase` + `PromocionCambioHorarioService` + `PromoverCambiosHorarioScheduler` (`@Scheduled(cron = "0 40 4 * * *", zone = "UTC")`, antes del barrido de las 05:00). El puerto suma `queYaRigenEn(fecha)` (`fecha_efectiva <= fecha`). Por cada vencido: escribe `preferencias_horario`, registra en `historial_cambios_horario` y borra el pendiente — borrarlo en la misma transacción es lo que hace la operación idempotente. Ver `docs/MODULO_HABITS.md` §20.1/§20.2 (incluida la decisión de que el diferido cobra cupo el día que rige, no al pedirlo).
- **Cómo evitarlo:** **un puerto de salida sin ningún inyector es un bug, no una pieza "lista para cuando se use".** Es una comprobación de un comando, barata y mecánica, que hay que hacer al cerrar cualquier feature con estado diferido:
  ```bash
  grep -rl "LoadXxxPort" src/main/java | grep -v "ports/out\|adapter/out"   # vacío = nadie lo consume
  ```
  Lo mismo para un método de dominio que nadie llama (`rigeEn`). Y a nivel de test: un `verify(save...)` prueba que se guardó, nunca que se aplicará — para un flujo diferido hace falta un test del **consumidor**, que en este caso simplemente no existía porque el consumidor tampoco.
- **Verificado:** `./mvnw clean test` → 1697/1697 en verde tras el arreglo (2026-08-31).

## E-54 — `violates foreign key constraint "cambios_horario_pendientes_participante_id_habito_id_fkey"`: la rama diferida no creaba la fila padre

- **Fecha:** 2026-08-31
- **Dónde:** `habits` — misma rama diferida de E-53; FK declarada en `src/main/resources/db/migration/V1__baseline_renaser.sql:501`
- **Síntoma:** el primer cambio diferido de un hábito que el aprendiz nunca editó explota en el INSERT (SQLState **23503**, Spring lo traduce a `DataIntegrityViolationException` → `409`):
  ```
  ERROR: insert or update on table "cambios_horario_pendientes" violates foreign key constraint
  "cambios_horario_pendientes_participante_id_habito_id_fkey"
    Detail: Key (participante_id, habito_id)=(dd2e2af5-..., 5c4dfec6-...) is not present in table "preferencias_horario".
  ```
- **Causa real:** `cambios_horario_pendientes` tiene
  `FOREIGN KEY (participante_id, habito_id) REFERENCES preferencias_horario (participante_id, habito_id) ON DELETE CASCADE`.
  La rama **inmediata** siempre crea/actualiza `preferencias_horario` primero, así que nunca choca; la **diferida** iba directo al pendiente. O sea: el bug solo aparece en la combinación "hábito nunca editado" + "ventana de hoy ya arrancada" — el camino menos frecuente, y el único sin prueba de integración.
- **Solución:** `PreferenciaHorarioService.asegurarPreferenciaVigente` crea la fila padre antes de guardar el pendiente, **con los valores vigentes hoy** (preferencia propia si existe — entonces no hay nada que crear —; si no, las horas del `horarios_habito` que aplica hoy; si el catálogo no tiene ninguno aplicable, `NULL`, que en esa tabla significa "sin override"). Nunca con las horas pedidas: el día en curso no se toca. Test que lo fija contra Postgres real: `CambioHorarioPendientePersistenceAdapterTest.sinFilaEnPreferenciasHorarioLaFkRechazaElPendiente`.
- **Cómo evitarlo:** **una FK compuesta hacia otra tabla de negocio (no un simple `id`) es una precondición del caso de uso, no un detalle del esquema** — quien inserta el hijo tiene que garantizar el padre, en el mismo caso de uso. Y la comprobación que lo habría encontrado el primer día es la que `CLAUDE.MD` §0.2 ya exige y acá faltaba: **prueba de integración con Testcontainers para todo adaptador de persistencia**. La única prueba del camino diferido usaba mocks, y un mock de `SaveCambioHorarioPendientePort` acepta cualquier cosa: por construcción no puede ver una FK. Regla práctica: al escribir un `@Entity` nuevo, `grep` de su tabla en el baseline SQL y leer sus `FOREIGN KEY` antes de escribir el caso de uso.

## E-55 — Un recurso con PATCH y sin GET: el cliente podía escribir su configuración pero no leerla

- **Fecha:** 2026-08-31
- **Dónde:** `habits` — `HabitPreferenceController` (`/api/v1/habit-preferences`)
- **Síntoma:** no es un error de runtime — es un agujero funcional que ninguna prueba puede fallar porque no hay nada que probar. El recurso `habit-preferences` exponía **solo** `PATCH /{habitId}`. Un aprendiz no tenía forma de consultar qué horario rige hoy en cada hábito, si le quedó algún cambio programado, ni cuánto cupo semanal le queda: solo podía mandar un cambio a ciegas y leer la respuesta de ese cambio puntual.
- **Causa real:** el hueco #12 se portó guiado por la lista de rutas que el frontend **ya llamaba** (D-36), y el frontend viejo tampoco tenía esa pantalla. Portar por "lo que el cliente ya consume" es la estrategia correcta para no inventar contrato (CLAUDE.MD §8), pero deja ciegos los huecos que el cliente viejo también tenía. El síntoma agravante fue E-53: el único dato que el aprendiz recibía sobre un cambio programado (`deferredEffectiveDate`) venía de la respuesta del propio PATCH, y esa respuesta era mentira — sin GET, nada permitía notarlo desde la app.
- **Solución:** `GET /api/v1/habit-preferences` (aditivo, no toca el PATCH) — `ConsultarPreferenciasHorarioUseCase`/`ConsultaPreferenciasHorarioService`. Devuelve por hábito activo el horario vigente, el cambio programado con su fecha efectiva y la cuota, reutilizando el mismo DTO de cuota del PATCH. Ver `docs/MODULO_HABITS.md` §20.4 y `docs/api/CONTRATO_DIA_A_DIA.md` §1.7.
- **Cómo evitarlo:** al cerrar un recurso REST, chequear la simetría: **si hay un verbo de escritura sobre un recurso, tiene que haber forma de leer ese mismo estado.** Un `PATCH` sin `GET` deja al cliente sin manera de mostrar el estado actual ni de verificar que su escritura tuvo efecto — que es justamente lo que hizo invisible a E-53 durante toda su vida. Chequeo de un comando sobre el módulo terminado:
  ```bash
  grep -rhoE "@(Get|Post|Put|Patch|Delete)Mapping" src/main/java/com/renaser/os/<modulo> | sort | uniq -c
  ```
  Un recurso que aparece solo con verbos de escritura es la señal.

## E-56 — Quien se registraba con Google no podía volver a entrar nunca: "Ya existe una cuenta, iniciá sesión con tu método actual"

- **Fecha:** 2026-08-31
- **Dónde:** `users` — `AutenticacionSocialService`, `AccountRequestService.approve()`, tabla `solicitudes_cuenta`. Registrado como A-7 en `docs/MODULO_AUTH.md` §6.7/§6.8
- **Síntoma:** el primer "Continuar con Google" funcionaba (abría la solicitud), un ADMIN la aprobaba, y **el segundo** "Continuar con Google" de la misma persona devolvía `409`:
  ```json
  { "error": "Ya existe una cuenta con ese correo. Iniciá sesión con tu método actual." }
  ```
  El mensaje es una trampa perfecta: esa persona **no tiene** un "método actual". El alta social deja `usuarios.hash_contrasena` en NULL a propósito, así que no hay contraseña que usar y "olvidé mi contraseña" tampoco lleva a ningún lado. La cuenta quedaba aprobada, activa y completamente inaccesible.
- **Causa real:** el `sub` del proveedor se verificaba al iniciar el alta y **se perdía ahí mismo**, porque no había dónde guardarlo. El vínculo real vive en `identidades_externas`, y la FK de esa tabla exige que la fila de `usuarios` esté creada — cosa que solo pasa al aprobar, un día después. O sea: el dato existía en el único momento en que no se podía escribir, y ya no existía en el momento en que sí. Al no haber vínculo, el segundo login no encontraba `(proveedor, sujeto)`, caía al camino de alta, chocaba con el `User` ya existente y respondía el 409 de arriba.
  **El agravante que lo hizo invisible:** los cuatro desenlaces posibles del login social colapsaban en el mismo 409 genérico, así que "todavía no te aprobaron", "ya existe una cuenta con ese correo" y "este bug" le llegaban a la app indistinguibles. No había forma de notar desde el cliente que uno de los tres era un defecto.
- **Solución:** tres piezas, ninguna opcional (ver `docs/MODULO_AUTH.md` §6.8):
  1. Migración `V12`: `solicitudes_cuenta` gana `proveedor`/`sujeto_proveedor` (nullable, con `CHECK` de que viajan juntos y `UNIQUE` parcial). La solicitud es el único registro que existe durante la espera entre el alta y la aprobación — es el lugar donde el `sub` puede sobrevivir.
  2. `AccountRequestService.approve()` escribe la `IdentidadExterna` en la **misma transacción** que activa al usuario: si el vínculo falla, la aprobación se deshace entera.
  3. `ResultadoLoginSocial` pasó de dos variantes a cuatro (`SesionIniciada`, `SolicitudCreada`, `SolicitudEnRevision`, `CuentaExistenteSinVinculo`), para que los estados normales del flujo dejen de disfrazarse de error.
- **Cómo evitarlo:** dos reglas concretas, las dos verificables.
  1. **Un dato que se verifica en el paso A y se usa en el paso B tiene que estar persistido en algún lado entre A y B.** Acá A y B estaban separados por la aprobación manual de un admin — potencialmente días. Cuando un flujo tiene una espera humana en el medio, todo lo que el paso posterior necesite hay que preguntarse dónde vive mientras tanto; si la respuesta es "en la request que ya terminó", falta una columna.
  2. **Una prueba que arranca del estado que el bug impedía alcanzar no prueba nada.** La que existía (`identidadYaVinculadaDevuelveSesionIniciadaConElUsuarioCorrespondiente`) partía de un `LoadIdentidadExternaPort` mockeado que ya devolvía el vínculo — o sea daba por cierto exactamente lo que fallaba, y pasaba en verde con el defecto vivo. El reemplazo es `LoginSocialCicloCompletoIntegrationTest`, que recorre el ciclo entero (alta → aprobación → segundo login) contra Postgres real. Regla: **para un flujo con estado que cruza varias operaciones, la prueba tiene que recorrerlo entero desde cero**; mockear el estado intermedio es asumir la conclusión.
  3. Corolario de mocks, el mismo de E-54: un mock no tiene FK, no tiene `UNIQUE` y no pierde columnas. Todo defecto cuya causa sea "ese dato no está en la base" es invisible para una prueba unitaria, por construcción.
- **Verificado:** `./mvnw clean test` en verde con `LoginSocialCicloCompletoIntegrationTest` incluido (2026-08-31).

## E-57 — El avatar se rompía solo a los 7 días: se persistía una URL prefirmada, que vence

- **Fecha:** 2026-08-31
- **Dónde:** `users` — `AvatarService.confirmar()`, columna `usuarios.avatar_url`. Propagado a `testimonios.avatar_url` por `TestimonioService.promover`
- **Síntoma:** no hay mensaje de error. La foto de perfil simplemente deja de cargar —a los 7 días exactos del último cambio de avatar— y no vuelve nunca. No solo en el perfil: el mismo string sale en el muro, los comentarios, el chat, los miembros de célula, los testimonios y el panel admin, porque todos lo reciben dentro de `users.api.UserSummary`. Con el adaptador por defecto (`NoOpAlmacenamientoAdapter`) tampoco se nota, porque devuelve `about:blank#pendiente-s3/...` para todo. O sea: **estaba escrito para romperse el día que se activara S3, una semana después de que alguien subiera una foto, sin ningún error en el log.**
- **Causa real:** la confirmación firmaba una URL de LECTURA y la guardaba como texto:
  ```java
  private static final Duration VALIDEZ_URL_LECTURA = Duration.ofDays(7);
  ...
  URI url = almacenamientoPort.firmarLectura(command.ruta(), VALIDEZ_URL_LECTURA);
  actor.changeAvatar(url.toString());   // se persiste la URL PREFIRMADA
  ```
  Una URL prefirmada de S3 **es una credencial con fecha de vencimiento**: lleva `X-Amz-Expires` y `X-Amz-Signature` en la query string y deja de servir cuando caduca. Persistirla convierte un dato con vida útil en un dato permanente, y no hay nadie del otro lado que la renueve — el único punto que firmaba era la confirmación, que solo corre cuando el usuario cambia la foto. Los 7 días eran, además, el máximo que SigV4 permite: el código ya había estirado la validez todo lo posible, que es la señal de que el diseño estaba peleando contra la herramienta.
  **El esquema ya declaraba la regla que este caso violaba.** En `V1__baseline_renaser.sql` el resto de las tablas dicen textualmente `-- P-03: la URL se firma al LEER, jamás se persiste`, `-- JAMÁS una URL (regla de oro heredada)`, `-- P-03: ruta, no URL`. `usuarios.avatar_url` era la única excepción, y estaba documentada como "limitación conocida" en vez de tratada como defecto (D-53 original).
- **Solución (D-55, decidida por el dueño del proyecto):** el objeto del avatar pasa a ser de **lectura pública** y la columna guarda su **URL permanente** — ahora el nombre `avatar_url` dice la verdad.
  1. `AlmacenamientoPort` gana `urlPublica(ruta)`: URL del objeto sin firmar. En `S3AlmacenamientoAdapter` la compone `S3Utilities` a partir del bucket y la región; en el `NoOp`, el mismo marcador que sus otros métodos.
  2. `AvatarService.confirmar()` guarda `urlPublica(...)`. `VALIDEZ_URL_LECTURA` y los 7 días desaparecen. La **subida** no cambia: sigue prefirmada a 10 minutos — escribir en el bucket nunca es público.
  3. `User.changeAvatar` **rechaza** un valor que lleve marcas de SigV4 (`X-Amz-Signature`/`X-Amz-Credential`/`X-Amz-Expires`), y `V13` agrega el `CHECK` equivalente en `usuarios` y en `testimonios`.
  4. `V13` repara los datos: corta la query string de las filas prefirmadas (`split_part(avatar_url, '?', 1)` — exacto, no heurístico: en SigV4 todo lo que caduca vive después del `?`) y pone `NULL` en las que quedaron con el marcador `about:blank` del NoOp.
- **La alternativa que se descartó, y por qué:** firmar al leer (una URL nueva en cada respuesta) también arregla el vencimiento, y es lo correcto para evidencia, contratos, adjuntos y audios. Para el avatar no: la URL cambiaría en cada respuesta y eso **invalida el caché de imagen del cliente** — un muro con 20 avatares volvería a descargar las 20 fotos en cada pantallazo. El avatar es el activo de menor sensibilidad y el que más se repite por respuesta; es el patrón de GitHub/Slack. El dueño del proyecto aceptó explícitamente que la ruta sea adivinable.
- **Requisito de infraestructura, que NO vive en el código:** el bucket tiene que permitir `s3:GetObject` anónimo sobre el prefijo `avatares/*`. S3 bloquea el acceso público por defecto (*Block Public Access*), así que **sin ese cambio de política la URL es correcta y devuelve 403**. Está escrito junto a los permisos IAM mínimos en `docs/MODULOS_A_AVANZAR.md` D-55 y en `docs/MODULO_USERS.md` §10.
- **Cómo evitarlo:** tres reglas, todas verificables.
  1. **Una URL prefirmada es una credencial, no un dato. Nunca se persiste.** Si aparece en un `INSERT`/`UPDATE`, es un bug. Lo que se guarda es la ruta (y se firma al leer) o una URL permanente (y el objeto es público) — no hay tercera opción. Chequeo de un comando sobre cualquier módulo:
     ```bash
     grep -rn "firmarLectura" src/main/java | grep -iE "change|set|save|persist|crear|actualizar"
     ```
  2. **Estirar una validez hasta el máximo que permite la herramienta es una señal de diseño equivocado, no una solución.** Los 7 días eran el techo de SigV4; el código estaba pidiendo a gritos que el problema no era la duración.
  3. **Un defecto que tarda N días en manifestarse no lo encuentra ninguna prueba que corra en un segundo.** La prueba vieja (`confirmarPersisteLaUrlResuelta`) verificaba que se guardaba lo que devolvía `firmarLectura` — o sea, afirmaba el bug y pasaba en verde. La prueba correcta no mira el valor, mira la **propiedad**: que lo guardado no tenga query string de firma, y que dos lecturas del mismo avatar den exactamente la misma URL. Regla general: **cuando un valor tiene vida útil, la prueba tiene que ser sobre su permanencia, no sobre su contenido.**
- **Efecto colateral que también se limpió:** `testimonios.avatar_url` copia el avatar del autor al promover una publicación. El snapshot es intencional (un testimonio es una foto de un momento), pero mientras `usuarios.avatar_url` guardó una prefirmada, esa copia heredaba el vencimiento. `V13` la repara con la misma regla.
- **Verificado:** `./mvnw clean test` → **1747/1747 en verde** (2026-08-31), con `V13` aplicada por Flyway contra el Postgres real de Testcontainers — los `CHECK` nuevos y los `UPDATE` de reparación corren de verdad en cada build, no solo en el despliegue. Pruebas que fijan el arreglo: `AvatarServiceTest.confirmarPersisteUnaUrlPermanente` (lo guardado no tiene query string de firma y nunca se llama a `firmarLectura`), `AvatarServiceTest.dosLecturasDelMismoAvatarDevuelvenLaMismaUrl` (la URL es estable — es la que mata el defecto), `UserTest.changeAvatarRechazaUnaUrlPrefirmada` y `S3AlmacenamientoAdapterTest.laUrlPublicaEsPermanenteYNoLlevaFirma`.
- **Barrido del resto del sistema:** se revisaron los **9 servicios** que llaman a `firmarLectura` (`academy`, `calendar`, `community` ×2, `habits`, `phasecontracts`, `support`, `users`). Todos los demás firman dentro de un método de proyección (`aVista`/`conUrlLectura`/mapeo a DTO) y devuelven la URL en la respuesta sin guardarla: `users` era el único que persistía. `testimonios.avatar_url` no es un segundo sitio de código con el mismo error — copia lo que hubiera en `usuarios.avatar_url`, así que heredaba el defecto por datos y se repara en la misma migración. Comando del barrido:
  ```bash
  grep -rn "firmarLectura" src/main/java | grep -v "ports/out\|infrastructure/storage"
  ```

## E-58 — Un parámetro de controller que se recibe y no se usa: `latest-author` filtraba el nombre completo del último autor del Muro a cualquiera

- **Fecha:** 2026-08-31
- **Dónde:** `WallController.latestAuthor` (`src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/publicacion/WallController.java:131`) y `PublicacionMuroService.ultimoAutor()` (`src/main/java/com/renaser/os/community/application/services/PublicacionMuroService.java`). Encontrado leyendo el archivo completo para medir la cobertura de autorización negativa del módulo, no por un test en rojo ni por un `curl`.
- **Síntoma:** `GET /api/v1/wall/latest-author` responde `200 {"authorName":"Nombre Apellido"}` para **cualquier** actor, incluido uno `SUSPENDED` — mientras que `GET /api/v1/wall` (el feed, mismo controller, mismo servicio) devuelve 403 al mismo actor. No hay excepción, no hay log, no hay nada raro: el endpoint simplemente contesta.
- **Causa real:** el handler declaraba `@ActorAutenticado UserId actorId` **y no lo pasaba a ningún lado**; el caso de uso, `ConsultarFeedUseCase.ultimoAutor()`, ni siquiera tenía un parámetro donde recibirlo. El parámetro del controller daba la apariencia de un endpoint autenticado (y por eso ninguna revisión lo marcó: firma idéntica a la de sus hermanos `feed`/`hidden`/`mine`), pero el guard vive en el servicio, y ahí no había nada. El método de al lado, `solicitarUrl()`, sí llama a `requireActorPuedePublicar` — la asimetría estaba a diez líneas de distancia.
- **Solución:** `ultimoAutor()` → `ultimoAutor(UserId actorId)`, con `requireActorActivo(actorId)` como primera línea (el guard de `feed()`, porque es una lectura del Muro, no una publicación). El controller pasa el actor que ya tenía. Se agregó la prueba negativa dentro del servicio (`ultimoAutorConActorSuspendidoFalla`, que además verifica con `verify(loadPublicacionPort, never()).ultimaVisible()` que ni siquiera se consulta la base). En la misma pasada se encontró y corrigió el mismo hueco en `contarMisPublicaciones()` (`GET /api/v1/wall/mine`), aplicando la lección 2 de **E-42**: cuando a un método le falta un guard que sus hermanos sí tienen, se revisan **todos** los métodos de la clase, no solo el reportado.
- **Cómo evitarlo:** **un parámetro de handler que se recibe y no se usa es un hallazgo de seguridad, no un warning de estilo.** Es el único síntoma visible cuando el guard vive una capa más adentro: la firma del controller miente sobre la protección real del endpoint. Dos formas concretas de agarrarlo antes: (1) activar/leer el aviso de "parámetro no usado" del IDE sobre los handlers REST — en un controller tonto (CLAUDE.MD §5.4.6) **todo** parámetro tiene que terminar dentro del comando del caso de uso; (2) al medir cobertura de autorización, listar los métodos del **servicio** y no los endpoints del controller — la firma del controller no dice nada sobre si hay guard, y este endpoint aparecía como "protegido" en cualquier conteo hecho desde el controller. Relacionado con **E-42** (mismo módulo, misma clase de falla: métodos hermanos sin el guard que sus vecinos sí tienen) y con **E-30** (fallar-cerrado es lo que evita que un chequeo ausente pase por chequeo presente).

## E-59 — 535 `NoClassDefFoundError` en tests que estaban bien: dos `mvnw` corriendo a la vez sobre el mismo `target/`

- **Fecha:** 2026-08-31
- **Dónde:** `./mvnw clean test` en `renaser-backend`, con otra sesión compilando el mismo directorio
- **Síntoma:** el build falla con cientos de errores en tests que no se tocaron, todos sobre **clases anónimas**:
  ```
  [ERROR] RegistroPoliticasHabitoTest.resuelvePorClaveSistema:61->politica:35
      NoClassDefFound com/renaser/os/habits/domain/model/politica/RegistroPoliticasHabitoTest$1
  [ERROR] Tests run: 1834, Failures: 23, Errors: 535, Skipped: 0
  [INFO] BUILD FAILURE
  ```
  El detalle que delata el caso: **el nombre de la clase que falta termina en `$1`, `$2`…** — son clases
  anónimas, que se cargan **tarde**, recién cuando el test las ejecuta. Las clases normales ya estaban
  cargadas en memoria y no fallan.
- **Causa real:** dos procesos de Maven sobre el **mismo `target/`**. El segundo `clean` borra
  `target/test-classes` mientras el surefire del primero todavía corre. Lo ya cargado en la JVM sigue
  funcionando; lo que se carga de forma diferida (clases anónimas, lambdas) ya no encuentra su `.class` en
  disco. **No hay ninguna regresión de código:** el mismo commit, corrido solo, dio **1886/1886** en verde.
- **Solución:** esperar a que la otra compilación termine y repetir:
  ```bash
  tasklist | grep -ci java.exe      # 0 = no hay build corriendo
  ./mvnw clean test
  ```
- **Cómo evitarlo:** **antes de correr `./mvnw clean test`, verificar que no haya otro build vivo** — es un
  reflejo barato y evita media hora persiguiendo un fantasma. Para saber qué es cada `java.exe`:
  ```powershell
  Get-CimInstance Win32_Process -Filter "name='java.exe'" | Select ProcessId,CommandLine
  ```
  Un `surefirebooter-*.jar` en la línea de comandos = hay tests corriendo ahora mismo.
  **Regla de lectura:** ante una avalancha de errores en tests que no se tocaron, y sobre todo si los nombres
  llevan `$N`, la primera hipótesis es el entorno (build pisado, `target/` a medias), **no** el código. Un
  cambio real rompe pocos tests y relacionados entre sí; un `target/` corrupto rompe cientos sin patrón.


## E-61 — Un 409 sin salida: "inicia sesion con tu contrasena para vincular Google", y despues no existia ninguna forma de vincular Google

- **Fecha:** 2026-09-01
- **Donde:** `AutenticacionController#loginSocial` (`POST /api/v1/auth/social`), variante `ResultadoLoginSocial.CuentaExistenteSinVinculo`.
- **Sintoma:** el correo del proveedor ya tenia cuenta pero esa identidad social no estaba vinculada, y el backend respondia:
  ```
  409 {"message":"Ya existe una cuenta con este correo y no esta vinculada a GOOGLE. Inicia sesion con tu contrasena para entrar."}
  ```
  El mensaje es correcto y la respuesta tambien. **El problema era lo que venia despues: nada.** La persona iniciaba sesion con su contrasena, entraba... y no habia ningun endpoint para conectar su Google. El 409 era un callejon sin salida permanente.
- **Causa real:** no fue un descuido. §6.4 de `docs/MODULO_AUTH.md` prohibe —con razon— vincular por coincidencia de correo, y §6.7 (decision 2) dejo anotado, textual, que la confirmacion autenticada *"todavia no existe como funcionalidad, asi que hoy el camino correcto es rechazar"*. El rechazo se construyo; **la funcionalidad que le daba salida quedo pendiente y nadie la cerro**. Es la misma familia que **E-56** (quien se registraba con Google no podia volver a entrar): una regla de seguridad correcta que, sin su contrapartida, deja a la persona sin ninguna via.
- **Solucion:** `POST /api/v1/auth/social/link` — vinculo **explicito** desde una sesion ya establecida (204 / 409 si la identidad ya es de otro usuario / 401 sin sesion). Ver `docs/MODULO_AUTH.md` §6.9 y la decision D-60. El mensaje del 409 ahora ademas dice a donde ir: *"Una vez adentro, podes vincular GOOGLE a tu cuenta desde tu perfil."*
- **Como evitar que vuelva a pasar:** **cuando una regla de seguridad rechaza algo, la pregunta obligatoria de la revision es "¿y que hace la persona ahora?".** Si la respuesta es "todavia nada, queda pendiente", eso no es una nota al pie: es un **callejon sin salida en produccion** y va a la lista de bloqueantes, no al final de una seccion de diseño. Los dos casos de esta familia (E-56 y este) se detectaron leyendo el mensaje de error desde el lugar del usuario, no leyendo el codigo.

## E-62 — Un CR suelto adentro de una linea: reescribir un .md con Python en modo texto parte la linea en dos

- **Fecha:** 2026-09-01
- **Donde:** `docs/MODULOS_A_AVANZAR.md`, filas D-53 y D-56 del registro de decisiones, al insertar la fila D-60 con un script de Python.
- **Sintoma:** `git diff --stat` mostraba **7 lineas cambiadas** para una insercion de **1**. En el diff, dos filas de la tabla aparecian cortadas al medio:
  ```
  -| D-53 | ... la ruta `C:[CR]enaserPlayStore\src\lib\supabase.ts` sigue existiendo ...
  +| D-53 | ... la ruta `C:
  +enaserPlayStore\src\lib\supabase.ts` sigue existiendo ...
  ```
- **Causa real:** esas dos filas tenian un **CR suelto** (`0x0D`, sin `0x0A` detras) en el medio de la linea — un artefacto viejo de haber pegado una ruta de Windows. Al leer el archivo en modo texto (`io.open(p, encoding='utf-8')`), Python usa *universal newlines*: **traduce a salto de linea los tres finales posibles, incluido el CR solo**. Ese CR interno se volvio un salto real y partio la fila en dos; el `.split()` posterior ni se entera, para el ya eran dos lineas.
- **Segundo sintoma, el mismo dia y el mismo archivo de bitacora:** reescribir `docs/BITACORA_ERRORES.md` en modo texto lo paso entero de LF a CRLF — `1298 insertions(+), 1231 deletions(-)` para agregar 20 lineas. Es **E-52** otra vez, en la misma sesion.
- **Solucion:** `git checkout -- <archivo>` y rehacer la edicion **en modo binario**, sin decodificar ni tocar los finales de linea:
  ```python
  datos = open(p, 'rb').read()
  i = datos.index(b'| D-59 |')
  fin = datos.index(b'
', i)                                  # primer LF real despues de la marca
  salto = b'
' if datos[fin-1:fin] == b'
' else b'
'
  open(p, 'wb').write(datos[:fin+1] + fila_nueva + salto + datos[fin+1:])
  ```
  Resultado: `1 file changed, 1 insertion(+)`, que es lo que la tarea pedia.
- **Reincidencia el mismo dia (2026-09-01), en la tarea D-61:** volvio a pasar exactamente igual, sobre los mismos `docs/MODULOS_A_AVANZAR.md` (las mismas filas D-53 y D-56 partidas en dos), `docs/MODULO_AUTH.md` y `docs/api/CONTRATO_IDENTIDAD.md`, los tres pasados enteros de LF a CRLF. **Lo que lo detecto fue el `git diff --stat` de esta misma entrada** (`70 insertions` para tres lineas cambiadas), y la reparacion no pudo ser `git checkout --` porque los archivos tenian cambios previos sin commitear: hubo que rehacerla en binario -- convertir CRLF a LF en todo el archivo y restaurar a mano los 2 CR sueltos (`C:` + CR + `enaserPlayStore`). **Moraleja reforzada: la regla no es "acordarse", es correr `git diff --numstat` despues de CADA script que toque un `.md`** -- si las deletions no son 0 cuando solo se inserto, esta pasando esto.
- **Como evitarlo:** **para editar un archivo existente con un script, modo binario (`'rb'`/`'wb'`) siempre** — el modo texto de Python reescribe los finales de linea de TODO el archivo aunque se toque una sola linea, y ademas convierte los CR sueltos que hubiera adentro. Hermano directo de **E-52**. **La senal de alarma es la misma y cuesta un comando: `git diff --stat` despues de cada script.** Si el numero de lineas cambiadas no coincide con lo que se quiso cambiar, revertir y rehacer en binario — nunca seguir adelante ni "arreglar" el diff a mano. Para cambios chicos, la herramienta `Edit` no tiene este problema.

## E-63 - El registro devolvia 400 para todo el mundo: el backend seguia exigiendo un telefono que el frontend ya no manda

- **Fecha:** 2026-09-01
- **Donde:** `POST /api/v1/account-requests` (alta por formulario) y `POST /api/v1/auth/social` (alta por Google), modulo `users`.
- **Sintoma:** el alta publica respondia
  ```
  400 {"message":"phone: must not be blank"}
  ```
  para **cualquier** registro, porque el cliente ya habia dejado de enviar el campo (`phone: null`). El alta por Google fallaba antes incluso de eso, con:
  ```
  400 {"message":"Se requiere un telefono para completar el registro con este proveedor"}
  ```
- **Causa real:** el requisito estaba escrito en **cinco capas distintas**, y bajarlo en una sola no cambiaba nada: (1) `solicitudes_cuenta.telefono NOT NULL` en Postgres desde el baseline V1; (2) `@NotBlank` en `SubmitAccountRequestRequest`; (3) `@NotBlank` en `SubmitAccountRequestCommand`; (4) `requireNotBlank(phone, ...)` dentro del agregado `AccountRequest`; (5) `AutenticacionSocialService.requirePhoneParaAlta`. Es lo que la arquitectura busca a proposito -- validacion sintactica en el borde, semantica en el dominio, restriccion en la base -- pero implica que **un cambio de obligatoriedad se toca en cinco lugares o no se toca en ninguno**.
- **El agravante que casi pasa desapercibido:** el punto (5) hacia que **ninguna cuenta nueva por login social pudiera registrarse**, porque Google/Apple/Facebook no devuelven telefono. Y como el `code` de OAuth es de un solo uso, el intento fallido lo consumia igual: reintentar exigia reiniciar el flujo del navegador. Estaba documentado en `docs/MODULO_AUTH.md` §6.7 punto 3 como "limitacion de diseño", no como defecto -- y por eso nadie lo trataba como urgente.
- **Solucion:** decision del dueño del proyecto (D-61): el telefono se pide en la **Ficha Inicial del onboarding**, no en el alta. Se bajo la exigencia en las cinco capas, con la migracion `V14__solicitudes_cuenta_telefono_opcional.sql` para la base. El telefono se sigue guardando si viene; un valor en blanco se normaliza a NULL en el agregado.
- **Como evitar que vuelva a pasar:** dos cosas concretas. **(a) Cuando un campo cambia de obligatorio a opcional (o al reves), la busqueda es por el nombre del campo en las cinco capas** -- migraciones, DTO web, comando de aplicacion, agregado y servicios que lo exijan a mano -- y no solo en la anotacion que salto en el error. La constraint de la base es la que no avisa hasta que el INSERT llega. **(b) Una limitacion de diseño que deja un flujo entero sin poder completarse no es una limitacion: es un defecto.** Misma familia que E-56 y E-61 -- una regla correcta que, sin su contrapartida, deja a la persona sin ninguna via. La pregunta de revision sigue siendo la misma: *"¿y que hace la persona ahora?"*.

## E-64 — `@Autowired` de la clase concreta del adaptador empieza a fallar apenas se agrega el primer `@Cacheable` del proyecto

- **Fecha:** 2026-09-01
- **Donde:** `RankingPersistenceAdapterTest` (test de integracion existente), modulo `points`, al agregar cache Caffeine a `RankingPersistenceAdapter` (D-63).
- **Sintoma:**
  ```
  UnsatisfiedDependencyException: ... Unsatisfied dependency expressed through field 'adapter':
  Bean named 'rankingPersistenceAdapter' is expected to be of type
  'com.renaser.os.points.infrastructure.adapter.out.persistence.ranking.RankingPersistenceAdapter'
  but was actually of type 'jdk.proxy2.$Proxy118'
  ```
  en un test que hasta ese commit pasaba sin problema, sin haber tocado el test.
- **Causa real:** `@EnableCaching` con `proxy-target-class` en su valor por defecto (`false`) envuelve cualquier bean que tenga **algun** metodo `@Cacheable`/`@CacheEvict` en un **proxy JDK dinamico**, que solo implementa las interfaces publicas del bean (`LoadRankingPort`, `SaveRankingSnapshotPort`, `LoadRankingCandidatosPort`), no la clase concreta. Antes de este cambio el proyecto no tenia ningun `@Cacheable` en todo el codebase, asi que **ningun bean estaba proxiado** y `@Autowired` de la clase concreta funcionaba por pura casualidad — el primer `@Cacheable` del repo fue el primero en exponer el problema.
- **Solucion:** en los tests, autowirear por **interfaz** (el puerto), nunca por la clase del adaptador — que es ademas la forma correcta segun `CLAUDE.MD` §5.1.1 (los consumidores dependen del puerto, no de la implementacion). `RankingPersistenceAdapterTest` y `RankingPersistenceAdapterCacheTest` quedaron con `@Autowired LoadRankingPort`/`SaveRankingSnapshotPort`/`LoadRankingCandidatosPort` en vez de `@Autowired RankingPersistenceAdapter`.
- **Como evitar que vuelva a pasar:** si un adaptador nuevo va a llevar `@Cacheable`/`@CacheEvict` (o cualquier otra anotacion que dispare un proxy AOP: `@Transactional` en un bean sin interfaz tiene el problema inverso), sus tests de integracion deben autowirear el puerto, no la clase — es ademas una señal de que el test estaba haciendo trampa contra la regla de hexagonal. Un test que SI necesita la clase concreta (para verificar algo que no esta en el puerto) es una señal de diseño a revisar, no un caso a resolver con `proxy-target-class=true`.

## E-65 — `@JsonNaming` importado de Jackson 2 lo ignora Jackson 3 en silencio: 10 DTOs declaraban snake_case y mandaban camelCase

- **Fecha:** 2026-09-01
- **Dónde:** los 10 DTOs de `academy/infrastructure/adapter/in/rest/` (`CursoResponse`, `MiCursoResponse`, `LeccionResponse`, `ProgresoCursoResponse`, `SeccionConLeccionesResponse`, `LeccionLiteResponse`, `CursoBloqueadoResponse`, `RecursoLeccionResponse`, y dos más que solo lo mencionaban en javadoc).
- **Síntoma:** la app mostraba *"No pudimos cargar los cursos. Revisá tu conexión e intentá de nuevo."* — un mensaje de red, con el backend respondiendo **200 y datos correctos**. `curl` al mismo endpoint devolvía los 25 cursos sin problema.
- **Causa real:** los DTOs declaraban `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` con el import `com.fasterxml.jackson.databind.annotation.JsonNaming`, o sea **la anotación de Jackson 2**. **Spring Boot 4 serializa con Jackson 3**, que vive en `tools.jackson.*`:
  ```
  tools.jackson.core:jackson-databind:3.1.5            <- el que Spring Boot 4 usa de verdad
  com.fasterxml.jackson.core:jackson-databind:2.21.5   <- de donde salia la anotacion
  ```
  Jackson 3 **no reconoce esa anotación y la ignora sin error ni warning**. Los DTOs declaraban un contrato y servían otro. Como el frontend se escribió leyendo las anotaciones del Java (que es lo correcto: son la fuente de verdad), los esquemas de validación quedaron en snake_case y **rechazaron todas las respuestas**.
- **Por qué el mensaje engañaba:** el helper `mensajeDeError(error, porDefecto)` del frontend devuelve el texto por defecto para cualquier error que **no** sea un `ApiError`. Un fallo de validación de zod no es `ApiError`, así que salía el mensaje genérico de conexión — apuntando a la red cuando el problema era el contrato.
- **Solución:** se quitaron las 10 anotaciones (no se corrigió el import) y se pasó el frontend a camelCase. **El cable no cambió**: nunca hicieron nada. Se eligió camelCase porque el resto de la API (`wall`, `chat`, `auth`) ya lo es — dejar `academy` en snake_case lo volvería la única excepción, y esas anotaciones venían de imitar el contrato viejo de Supabase, que ya se decidió no preservar. Cada archivo quedó con un comentario explicando el porqué, para que nadie las "restaure". `./mvnw test -Dtest='com.renaser.os.academy.**'`: **88/88 en verde**.
- **Cómo evitarlo:** tres reglas.
  1. **En Spring Boot 4, cualquier anotación de `com.fasterxml.jackson.databind.*` es sospechosa.** Jackson 3 movió `databind` a `tools.jackson.databind`. Las **anotaciones** de `com.fasterxml.jackson.annotation.*` (`@JsonProperty`, `@JsonUnwrapped`, `@JsonIgnore`) **sí siguen funcionando** — el artefacto `jackson-annotations` no cambió de paquete. Las de `databind` (`@JsonNaming`, `@JsonSerialize`, `@JsonDeserialize`) **no**. Chequeo de un comando:
     ```bash
     grep -rn "import com.fasterxml.jackson.databind" src/main/java --include=*.java
     ```
  2. **El contrato se verifica contra el cable, no contra el código.** Una anotación es una intención; lo único que prueba qué se manda es pedirlo:
     ```bash
     curl -s "$API/api/v1/cursos" -H "X-Auth-Token: $TOK" | python -c "import sys,json;print(list(json.load(sys.stdin)[0]))"
     ```
     Hacerlo **antes** de escribir el cliente cuesta treinta segundos y habría evitado todo esto.
  3. **Un mensaje de error por defecto que dice "revisá tu conexión" oculta la causa.** Cuando el helper no reconoce el error, conviene que el texto sea neutro ("no pudimos cargar los cursos") y que el detalle real vaya al log, en vez de afirmar una causa que puede ser falsa. Relacionado con **E-60** y el error de Caffeine de hoy: los tres son fallas donde **el mensaje apuntaba a un lugar y la causa estaba en otro**.


## E-66 — Lanzar `./mvnw test` sin esperar el resultado del propio chequeo de "¿hay otro build vivo?"

- **Fecha:** 2026-09-01
- **Dónde:** implementando D-65 (registro social en dos pasos), antes de correr la suite completa.
- **Síntoma:** ninguno todavía — se evitó a tiempo, pero el riesgo era el mismo de **E-59** (dos `mvnw` sobre el mismo `target/`, `NoClassDefFoundError` masivo con clases `$1`/`$2`). Además se descubrió, por la vía difícil, que **hay otra sesión/agente trabajando en este mismo repo al mismo tiempo** (corriendo `mvn test -Dtest=com.renaser.os.academy.**` y editando este mismo archivo — su entrada quedó como **E-65**, con el mismo número que se había elegido acá independientemente).
- **Causa real, y es distinta de E-59 aunque el riesgo final sea el mismo:** el chequeo recomendado por E-59 (`Get-CimInstance Win32_Process -Filter "name='java.exe'" | Select ProcessId,CommandLine`) es el correcto, pero es **lento en este entorno** — tardó más de 120 s y el propio tooling lo mandó a segundo plano. En vez de **esperar su resultado antes de seguir**, se lanzó `./mvnw -o test` igual, confiando en un chequeo previo más barato (`tasklist | grep -ci java.exe`, que solo cuenta procesos sin decir qué son). Cuando el chequeo lento por fin devolvió resultado, ya había **dos** `mvn test` corriendo a la vez sobre el mismo `target/`: uno preexistente y ajeno (arrancado antes de cualquiera de los chequeos propios) y el propio, recién lanzado.
- **La lección concreta:** el conteo simple de `java.exe` **no sustituye** al chequeo por línea de comandos — puede devolver el mismo número "2 = normal" tanto si esos dos procesos son de verdad solo el IDE y la app, como si uno de ellos es en realidad un Maven ajeno que arrancó hace un minuto y todavía no generó su `surefirebooter`. Un chequeo que se manda a segundo plano por lento **hay que esperarlo y leer su resultado antes de lanzar el build** — lanzar "mientras tanto" el mismo tipo de comando que el chequeo está tratando de descartar anula el propósito del chequeo. **En un repo donde puede haber más de una sesión de trabajo activa, "no hay otro build vivo" nunca es un supuesto seguro — hay que verificarlo cada vez, no asumirlo de una corrida a la otra.**
- **Qué se hizo al notarlo:** se intentó parar la tarea en segundo plano (para el wrapper de shell) y luego matar los procesos Java huérfanos que quedaron corriendo solos (`Stop-Process`/`taskkill`) — **ambos bloqueados por el clasificador de modo automático del harness** (no deja terminar procesos por su cuenta). Sin forma de matarlos, la única salida segura fue **esperar a que los dos builds terminaran solos** (monitoreando `tasklist` cada 15 s) y recién ahí correr la suite una sola vez, limpia, para tener un resultado confiable.
- **Cómo evitarlo la próxima vez:** si el chequeo de "¿hay otro build vivo?" se manda a segundo plano por tardar más de lo esperado, **no lanzar nada que toque `target/` hasta leer su resultado** — ni siquiera algo aparentemente inocuo como `test-compile`. Si de todas formas se termina con dos builds superpuestos y no se puede matar el proceso ajeno, no hay atajo: esperar a que ambos terminen y volver a correr una vez sola. Y al editar un documento compartido como este (`docs/BITACORA_ERRORES.md`), asumir que puede haber otro escritor concurrente: releer antes de cada edición en vez de confiar en una lectura vieja.

## E-67 — Llamar a la IA dentro de `@Transactional` con `@Async` sin tope: el pool de Postgres se agota apenas conecta un proveedor real

- **Fecha:** 2026-09-01
- **Dónde:** `ProcesarValidacionV90Service.procesar` (`onboarding`), `EspejoSombraService.generar` (`rag`), `ConocimientoService.indexar` (`rag`), `RecomendacionService.recomendacion` (`academy`) — hallazgo **C-1** (crítico) de `docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html`.
- **Síntoma:** ninguno todavía en este entorno — **latente**, porque los 4 puertos de IA que estos servicios llaman son `NoOp` (responden en microsegundos). El síntoma real, el día que se conecte Gemini/otro proveedor: diez aprendices pidiendo validación V90 en el mismo minuto, con una IA de hasta 45s, dejan las diez conexiones de Hikari ocupadas — el pool completo — y **toda la API** (login, hábitos, chat, cualquier endpoint que necesite Postgres) empieza a devolver 500 después de esperar el `connection-timeout`. Los hilos virtuales no evitan nada de esto: el recurso que se agota es la conexión de base, no el hilo.
- **Causa real:** los cuatro métodos tenían la misma forma — `@Transactional` envolviendo lectura + llamada a la IA + guardado en una sola transacción. Mientras la IA no responde, la transacción sigue abierta y la conexión de Postgres que la sostiene queda retenida, sin usarse para nada, todo ese tiempo. A esto se sumaba que `spring.threads.virtual.enabled=true` hace que Spring Boot arme el executor de `@Async` (usado por el despacho de validación V90) como `SimpleAsyncTaskExecutor` **sin ningún tope** salvo que se fije `spring.task.execution.simple.concurrency-limit` — que no estaba fijado —, así que no había ningún límite superior a cuántas de estas transacciones largas podían solaparse. Y el pool de Hikari nunca tuvo un tamaño propio: corría con el default sin documentar (10 conexiones).
- **Por qué no dolía hasta ahora:** con los adaptadores `NoOp`, la "llamada a la IA" tarda microsegundos — la transacción se abre y cierra tan rápido que jamás compite por una conexión con nada más. El bug es invisible mientras nadie conecta un proveedor real, y por eso una auditoría de código (no de producción, sin incidente que lo disparara) fue lo que lo encontró.
- **Solución:** en los cuatro servicios se sacó `@Transactional` del método que envolvía todo. La lectura y la escritura ya corren cada una en su propia transacción corta porque **Spring Data JPA anota `@Transactional` en sus propios repositorios** (`SimpleJpaRepository`) — llamar a un puerto respaldado por un repositorio Spring Data, desde un método sin `@Transactional` propio, ya alcanza para que esa llamada puntual tenga su propia transacción de milisegundos. No hizo falta declarar una transacción nueva en ningún lado: alcanzó con dejar de envolver de más. La llamada a la IA quedó en el medio, sin ninguna transacción abierta durante toda su duración. Además se fijó `spring.task.execution.simple.concurrency-limit=20` (application.yaml) para acotar cuántas validaciones V90 corren a la vez, y se dimensionó Hikari explícitamente (`maximum-pool-size=20`, `minimum-idle=5`, `connection-timeout=5000`) en vez de dejarlo en el default implícito.
- **El riesgo aparte que esto reveló, y que quedó sin resolver a propósito:** en `ProcesarValidacionV90Service`, la grabación V90 ya queda marcada `PROCESANDO` (persistido) ANTES de llamar a la IA (lo hace `GrabacionV90Service.solicitarValidacion`, en su propia transacción, antes del despacho `@Async`). Si la IA real lanza una excepción (timeout, error de red — el `NoOp` nunca lo hace), sin capturarla el método viejo se cortaba antes de llegar a `saveGrabacionPort.guardar`, y la grabación quedaba en `PROCESANDO` **para siempre** — el aprendiz no podía reintentar. Se agregó un `try/catch` alrededor de la llamada a la IA en ese único servicio (los otros tres no tienen un estado intermedio persistido que pueda quedar atrapado: si la IA falla ahí, simplemente no se escribe nada, y el llamador puede reintentar limpio) que trata cualquier excepción igual que `NO_DISPONIBLE` — la máquina de estados de `GrabacionV90` ya sabe reintentar o caer a `REVISION_MANUAL`. **Lo que NO se tocó, y sigue abierto:** el resto de C-3 del mismo informe (double-dispatch sin `PESSIMISTIC_WRITE`, barrido de `PROCESANDO` huérfanos tras un reinicio del proceso, `spring.task.execution.shutdown.await-termination`) y C-4 (`EvidenciaService.procesarLote`, que procesa 25 evidencias en una sola transacción — mismo bug de fondo, en un archivo fuera del alcance de esta tarea) y `PgVectorNativoAdapter.buscarSimilares` (otra instancia de IA-dentro-de-`@Transactional`, en `rag`, tampoco tocada porque no estaba en la lista de archivos del encargo).
- **Cómo evitar que vuelva a pasar:** el chequeo es mecánico y vale la pena correrlo cada vez que se agrega un `@Service` que llama a un puerto de IA/HTTP externo:
  ```bash
  grep -rn "@Transactional" src/main/java --include=*.java -A 15 | grep -B 15 "IAPort\|ChatClient\|embeddingPort\|generarInsightPort\|recomendarClasePort"
  ```
  Si un método `@Transactional` contiene una llamada a un puerto cuyo adaptador real hace I/O de red de duración variable (una IA, un proveedor OAuth, SMTP — ver también **C-11**, SMTP dentro de `@Transactional` en la invitación de staff, mismo informe, todavía sin corregir), separar: leer y guardar apoyándose en las transacciones cortas que Spring Data JPA ya da por método de repositorio, y dejar la llamada externa completamente afuera de cualquier `@Transactional` propio. Y si esa llamada puede fallar dejando un estado intermedio ya persistido (un flag tipo `PROCESANDO`), capturar el fallo ahí mismo y resolverlo con la misma máquina de estados que ya maneja "la IA no está disponible" — no dejar que la excepción se lleve puesto el guardado del veredicto.


## E-68 — Lote de IA todo-o-nada y anulación con doble reversión de puntos (C-4/C-13)

- **Fecha:** 2026-09-01
- **Dónde:** `evidence/application/services/EvidenciaService.java`
- **Síntoma:** no hay un mensaje de error único — son dos defectos de diseño encontrados
  en auditoría, no una excepción en runtime observada todavía (los adaptadores de IA son
  `NoOp`, así que en producción hoy no se manifiestan). Si se manifestaran: (C-4) con IA
  real, una evidencia que falla en el medio del lote de 25 revierte las demás ya validadas
  y la cola de validación no avanza nunca (siempre las mismas 25, por `subida_en ASC`).
  (C-13) dos admins anulando la misma evidencia case-a-caso devolverían la penalización de
  puntos dos veces.
- **Causa real:** (C-4) `procesarLote()` envolvía en una sola `@Transactional` tanto la
  lectura con `FOR UPDATE SKIP LOCKED` como hasta 25 llamadas a IA, sin `try/catch` por
  ítem — mismo defecto que C-1 (ya corregido en `onboarding`/`rag`/`academy`), no detectado
  acá porque el `NoOpValidacionIAAdapter` nunca lanza ni tarda. (C-13) `anular()` leía la
  evidencia con un `byId` sin bloqueo (`requireEvidencia`) antes de decidir si revertir la
  penalización — check-then-act, mismo patrón que C-2 en `rocks`.
- **Solución:** (C-4) sacar la IA de la transacción (transacción corta y propia solo para
  el `SELECT` del lote, cada evidencia procesada y guardada por separado, con
  `try/catch` que aísla el fallo de una evidencia del resto). (C-13) `byIdParaEscritura`
  con `PESSIMISTIC_WRITE`, mismo patrón que `LoadRocaDiariaPort.byIdParaEscritura` (C-2).
- **Cómo evitarlo:** cuando un caso de uso hace un `for` sobre varias entidades y alguna
  de las operaciones dentro del loop puede tardar o fallar por una causa externa (IA, red,
  I/O), nunca envolver el loop completo en una única transacción ni dejar el loop sin
  `try/catch` por ítem — es el mismo defecto de C-1/C-4, y va a repetirse en cualquier
  scheduler de lote nuevo si no se revisa a propósito. Cuando un caso de uso lee una
  entidad para decidir si aplicar un efecto en OTRO módulo (puntos, notificaciones) basado
  en un flag de esa entidad, la lectura tiene que ser con `PESSIMISTIC_WRITE` si dos
  llamadas concurrentes pueden ver el mismo flag antes de que ninguna escriba — patrón ya
  repetido 3 veces (C-2, C-3, C-13), buscar `byIdParaEscritura` en el repo antes de asumir
  que un `byId` simple alcanza.

## E-69 — Expirar-y-lanzar revierte su propio guardado; barridos nocturnos todo-o-nada (C-6/C-9)

- **Fecha:** 2026-09-01
- **Dónde:** `habits/application/services/RegistroService.java`,
  `habits/application/services/RachaService.java`,
  `habits/application/services/PromocionCambioHorarioService.java`
- **Síntoma:** no hay un mensaje de error único observado en producción (es un hallazgo de
  auditoría, no un incidente reportado). Si se manifestara: un aprendiz que intenta
  completar un hábito o cerrar una racha "Día sin celular" después de que venció su
  ventana recibe 409 una y otra vez en cada reintento, porque el registro/racha nunca
  queda de verdad `EXPIRADO`/`EXPIRADA` en la base — el `throw` revertía el `save` que lo
  precedía, dentro de la misma transacción. Además, para la racha, `rachas_viva_uk` (a lo
  sumo una racha `ACTIVA` por aprendiz) le impedía iniciar una nueva mientras la vieja
  "seguía activa" por el mismo motivo. Por separado, el barrido nocturno que debería
  limpiar esto (`ExpirarRegistrosScheduler`, 05:00 UTC) procesaba todas las filas
  candidatas en una única transacción sin `try/catch`: una fila corrupta revertía TODAS
  las expiraciones de esa noche, no solo la suya, y el barrido de la noche siguiente
  volvía a fallar en el mismo punto.
- **Causa real:** (C-9) `registro.expirar(ahora); saveRegistroPort.save(registro); throw
  new IllegalStateException(...)` — las tres líneas corren en la misma
  `@Transactional` del método (declarada directamente ahí, no heredada de la interfaz);
  lanzar una `RuntimeException` marca la transacción para rollback por defecto, deshaciendo
  el `save` junto con el `throw`. (C-6) el `for` de los tres barridos nocturnos no tenía
  `try/catch` por fila y corría dentro de una única `@Transactional` de método —
  cualquier excepción en cualquier fila abortaba el lote completo.
- **Solución:** (C-9) un tipo de excepción propio y puntual por cada sitio
  (`RegistroExpiradoException`, `RachaVencidaException`, ambas `extends
  IllegalStateException` para no tocar el contrato HTTP) con
  `@Transactional(noRollbackFor = <ese tipo>)` — deliberadamente NO sobre
  `IllegalStateException` en general, porque eso habría enmascarado otros guard clauses de
  dominio que sí deben revertir su escritura si fallan (ver el informe completo,
  `docs/informes/auditoria-fixes/C-6-C-9.md`, para el análisis fila por fila). Se descartó
  `REQUIRES_NEW` para este punto puntual porque el registro/racha ya viene bajo bloqueo
  pesimista de la misma transacción — abrir una segunda transacción sobre la fila
  bloqueada por la primera, sin liberarla, es un auto-interbloqueo entre dos conexiones del
  mismo pool. (C-6) cada fila del barrido se procesa en su propia transacción
  `REQUIRES_NEW` (segura acá porque cada fila toma y libera su lock antes de pasar a la
  siguiente, sin ninguna transacción externa sosteniéndolo), envuelta en `try/catch` que
  cuenta y loguea (`WARN` por fila fallida, `INFO` de resumen al final, nunca `INFO` dentro
  del loop) sin abortar el resto.
- **Cómo evitarlo:** cuando un caso de uso hace "mutar y guardar, y si cierta condición se
  cumple lanzar de todos modos" dentro de la MISMA transacción, el `throw` revierte el
  guardado salvo que se marque `noRollbackFor` — y ese `noRollbackFor` tiene que apuntar a
  un tipo de excepción tan específico como el punto de lanzamiento, nunca a la superclase
  genérica (`IllegalStateException`, `RuntimeException`) si el método tiene más de un lugar
  donde puede lanzar ese mismo tipo. Cuando un barrido nocturno hace un `for` sobre muchas
  filas con un `@Transactional` de método envolviendo todo el loop, sin `try/catch` por
  fila, es el mismo patrón de C-1/C-4 (ya corregidos en otros módulos) — buscar ese patrón
  (`@Transactional` + `for` sin `try/catch` en el cuerpo) antes de dar por buena la
  implementación de cualquier `@Scheduled` nuevo.

## E-70 — Doble `POST /validation` sobre la misma grabación V90 dispara dos llamadas a la IA, y un fallo al guardar la deja en `PROCESANDO` para siempre (C-3)

- **Fecha:** 2026-09-01
- **Dónde:** `onboarding/application/services/GrabacionV90Service.java`, `ProcesarValidacionV90Service.java`
- **Síntoma:** dos requests concurrentes a `POST /api/v1/onboarding/v90-recordings/{id}/validation` sobre la MISMA grabación devuelven ambas `202 {"status":"processing"}` pero disparan **dos** llamadas independientes a `ValidacionIAPort.validar` para el mismo `grabacionId` — doble costo de IA. Por separado: si el guardado del veredicto falla (corte de conexión a Postgres), la grabación queda en `estado_ia = 'PROCESANDO'` para siempre; ningún reintento del cliente la saca de ahí, porque `GrabacionV90.procesarIntentoDeValidacion` rechaza la reentrada mientras siga `PROCESANDO`, y no existe barrido de fondo para V90.
- **Causa real:** `solicitarValidacion` leía con `loadGrabacionPort.porId` (sin bloqueo) antes de transicionar a `PROCESANDO`. **El guard de dominio agregado para E-37 no alcanza**: protege el objeto ya leído en memoria, no impide que dos transacciones lean la misma fila en `PENDIENTE` antes de que cualquiera escriba. Y `procesar()` no tenía manejo de fallo para el guardado final — entre el fallo y una grabación atrapada solo quedaba el `catch` genérico del adaptador `@Async`, que únicamente loguea.
- **Solución:** `LoadGrabacionV90Port.porIdParaEscritura` con `@Lock(PESSIMISTIC_WRITE)` (mismo patrón que C-2 en `rocks`); si al leer con el lock ya está `PROCESANDO`, se retorna el mismo 202 idempotente sin relanzar la validación. Y `procesar()` envuelve switch+guardado en un `try/catch` que relee el estado real y, si sigue `PROCESANDO`, fuerza `registrarSinResultado()` — la misma máquina de estados de "IA no disponible", sin inventar un estado nuevo.
- **Cómo evitarlo:** todo caso de uso que **lea, transicione y guarde** un agregado compartido entre requests concurrentes (un "arrancar algo" que pasa a "en curso") necesita bloqueo pesimista en la lectura, o un `UPDATE ... WHERE estado = 'X'` que devuelva filas afectadas. Un guard en memoria dentro del objeto de dominio **nunca alcanza solo**, por más que ya exista: no existe hasta que alguien ya leyó la fila. Y todo método que persiste el resultado de un paso async largo necesita su propio manejo de fallo en el guardado final — un `catch` genérico río abajo que solo loguea no es una red de recuperación, es donde el bug se vuelve invisible.

## E-71 — `afterCommit()` NO libera la conexión: diferir un envío SMTP ahí no lo saca de la transacción (C-11)

- **Fecha:** 2026-09-01
- **Dónde:** `users/application/services/UserAccountService.inviteStaff`
- **Síntoma original:** un admin invitando staff recibía 503 ("No pudimos enviar el correo") y el usuario invitado **no quedaba creado** — rollback completo — aunque el alta en sí no tenía nada malo. Con un SMTP lento, además, una conexión de Hikari quedaba retenida hasta 15s por intento (3 reintentos del cliente de mail), con riesgo de agotar el pool para toda la API.
- **Causa real:** `inviteStaff` llamaba a `EnviarEmailPort.enviarInvitacionStaff` —una llamada de red a un servidor SMTP— dentro del método `@Transactional`.
- **El primer arreglo NO funcionó, y esta es la parte que hay que recordar:** se difirió el envío a un `TransactionSynchronization.afterCommit()`, copiando el patrón de `MensajeService.publicarDespuesDelCommit` (`chat`). **Spring ejecuta los callbacks de `afterCommit` y `afterCompletion` ANTES de `cleanupAfterCompletion`**, que es donde se desliga el `EntityManager` y la conexión vuelve al pool — así que el envío seguía corriendo con la conexión tomada. El patrón sirve en `chat` porque ahí lo diferido es publicar en memoria (instantáneo); con un servidor SMTP que puede no responder, no.
- **Solución definitiva:** `inviteStaff` deja de ser `@Transactional`. Lo que necesita atomicidad —usuario, perfil de mentor, credencial temporal y evento— corre dentro de un `TransactionTemplate`, y el envío ocurre después, con la transacción cerrada. Si el correo falla, se loguea en ERROR y no se propaga: el invitado ya tiene credencial real y puede entrar por "olvidé mi contraseña".
- **Cómo evitarlo:** para sacar una llamada externa lenta de una transacción, **`afterCommit` no es equivalente a "fuera de la transacción"** — solo garantiza "después del commit", que no es lo mismo que "después de soltar la conexión". Si lo que se quiere es liberar la conexión, hay que cerrar la transacción de verdad (método no transaccional + `TransactionTemplate` para la parte atómica). La forma de comprobarlo, y la única que sirve, es medir `TransactionSynchronizationManager.isActualTransactionActive()` **desde adentro** de la llamada diferida, en un test de integración contra Postgres real: las tres pruebas unitarias de este mismo arreglo pasaban con el arreglo roto.

## E-72 — Rate limit de alta por `COUNT` (check-then-act) y token de verificación consumido antes del INSERT (C-16)

- **Fecha:** 2026-09-01
- **Dónde:** `users/application/services/AccountRequestService.java`
- **Síntoma:** ráfagas concurrentes desde la misma IP podían superar el límite documentado de 60/hora. Y quien reintentaba un alta con un correo que ya tenía cuenta se quedaba sin poder reintentar con **otro** correo sin volver a verificar su casilla de cero: el token de verificación, de un solo uso, ya se había consumido en el intento fallido.
- **Causa real:** `rejectIfRateLimitExceeded` hacía `SELECT COUNT` y **después** insertaba — dos sentencias sin atomicidad entre ellas. Y `submit()` consumía el `verificationToken` (GETDEL en Redis) **antes** de intentar el INSERT, así que cualquier fallo posterior (típicamente el `UNIQUE` de `usuarios.email`) perdía el token sin haber servido para nada.
- **Solución:** el límite por IP pasó a `LimitarSolicitudesResetPort.registrarIntento` (Redis, `INCR` atómico), el mismo puerto que ya usan `VerificacionEmailService` y `ConsultaEmailService`. Y se agregó un chequeo explícito de "¿el correo ya existe?" **antes** de consumir el token.
- **Dependencia que esto creó, y que hay que mirar junto:** el límite de altas ahora se apoya en el mismo adaptador Redis que el hallazgo **C-8** denuncia (`INCR` y `EXPIRE` no atómicos: una clave que queda sin TTL bloquea para siempre). Antes ese defecto solo podía trabar el reseteo de contraseña; ahora puede **bloquear permanentemente las altas de cuentas nuevas**. C-16 y C-8 no deben separarse.
- **Cómo evitarlo:** todo límite de tasa nuevo en `users` se apoya en `LimitarSolicitudesResetPort` desde el principio, no en un `COUNT` de Postgres — ya hay tres usos del mismo puerto como referencia. Para recursos de un solo uso (tokens, códigos): validar lo más posible **antes** de consumir, y consumir lo más tarde posible del flujo.

## E-73 — Doble confirmación de alta social recibía un 409 genérico en vez de tratarse como éxito idempotente (C-17)

- **Fecha:** 2026-09-01
- **Dónde:** `users/application/services/CompletarRegistroSocialService.java`
- **Síntoma:** dos llamadas casi simultáneas a `POST /auth/social/complete` para la MISMA identidad de Google/Apple/Facebook (dos pestañas, un reintento de red, un doble tap en "Confirmar") hacían que la segunda recibiera un 409 genérico ("La operacion entra en conflicto con datos que ya existen") en vez de la misma respuesta de éxito que recibe la primera.
- **Causa real:** desde D-65 cada llamada a `POST /auth/social` para una identidad nueva genera un token de continuación **independiente** — no hay memoria de tokens anteriores para la misma identidad. Si dos se confirman casi a la vez, ambos intentan crear la misma `AccountRequest`/`User`; el segundo choca contra el `UNIQUE` de `usuarios.email` y esa `DataIntegrityViolationException` no estaba capturada.
- **Solución:** se captura la `DataIntegrityViolationException` alrededor de `submit()` y, si existe una `AccountRequest` pendiente para la MISMA identidad social, se devuelve su id en vez de dejar escapar el error — mismo criterio que ya aplica `vincularIdentidadSocial` ("el doble tap del cliente móvil no es un error"). Si el conflicto no es de la misma identidad, se relanza el original.
- **Impacto en el cliente, a tener presente:** en esa ventana de carrera la app React Native pasa a recibir **202 en vez de 409**. Es el comportamiento correcto, pero es un cambio observable del contrato y el frontend debe contemplarlo.
- **Cómo evitarlo:** todo caso de uso que pueda recibirse dos veces por la misma "cosa lógica" desde flujos separados por un paso intermedio en Redis (token de continuación, OTP) tiene que decidir explícitamente qué hacer con el segundo, no asumir que "nunca va a pasar". Criterio del módulo: si hay una forma barata de detectar "esto ya se hizo", tratar el segundo intento como éxito idempotente.

## E-74 — Tres pruebas de integración que fallaban en la semilla, no en el código que decían probar

- **Fecha:** 2026-09-01
- **Dónde:** `CompletarRegistroExpiracionTransaccionIT`, `CerrarRachaExpiracionTransaccionIT`, `AccountRequestRateLimitConcurrenciaTest` — pruebas nuevas escritas al aplicar la auditoría.
- **Síntoma:** tres mensajes distintos, ninguno relacionado con el hallazgo que la prueba venía a verificar:
  1. `jakarta.persistence.TransactionRequiredException: No active transaction for update or delete query` — en `seedFixtures`, no en el caso de uso.
  2. `ERROR: invalid input syntax for type inet: "rl-test-fa0ff0f8-..."`
  3. `duplicate key value violates unique constraint "habitos_clave_sistema_key" — Key (clave_sistema)=(PHONE_FREE_DAY) already exists`
- **Causa real, una por una:** (1) el `EntityManager` compartido exige transacción activa para `executeUpdate`, y `@BeforeEach` no la trae. (2) `solicitudes_cuenta.ip_solicitud` es de tipo **`inet`** en Postgres: un identificador inventado como IP única por test revienta el INSERT antes de que el limitador entre en juego. (3) el hábito de sistema "día sin celular" **ya viene sembrado** por `V4__catalogo_habitos_default.sql` y su `clave_sistema` es UNIQUE — la prueba insertaba uno propio.
- **Solución:** (1) la semilla se envuelve en un `TransactionTemplate`, que además es lo correcto: los datos deben estar **commiteados** antes de que corra el caso de uso. (2) IP del rango de documentación `2001:db8::/32` (RFC 3849), que es `inet` válido y deja espacio de sobra para una por test. (3) se toma el hábito del catálogo con un `SELECT ... WHERE clave_sistema = ?` y **no se borra** en el `@AfterEach` — borrarlo habría eliminado una fila de catálogo compartida con el resto de la suite.
- **Cómo evitarlo:** antes de escribir la semilla de una prueba de integración, mirar **el esquema y las migraciones**, no solo el código de producción: el tipo real de la columna (`inet`, `citext`, enums) y qué filas ya siembra Flyway. Y cuando una prueba de integración falla, leer **dónde** falla antes de sospechar del arreglo: `seedFixtures` en el stack trace significa que el caso de uso ni siquiera llegó a ejecutarse. Relacionado con la lección repetida de E-60/E-65/E-66: **el mensaje apuntaba a un lugar y la causa estaba en otro.**

## E-75 — La primera fila de puntaje de un participante no está protegida: 409 en el primer hábito (C-12)

- **Fecha:** 2026-09-01
- **Dónde:** `points/application/services/PuntajeService.java` (`cargarOInicializar`,
  antes líneas 136-142)
- **Síntoma:** un aprendiz recién inscrito que completa dos hábitos (o una roca y un
  hábito) casi al mismo tiempo el día 1 del programa puede recibir un 409 en uno de los
  dos, con ese punto perdido en vez de solo demorado.
- **Causa real:** `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`) no puede bloquear una fila
  que todavía no existe. Cuando `puntajes_participante` no tiene fila para el
  participante, dos ajustes concurrentes reciben `Optional.empty()` los dos, construyen su
  propio `PuntajeParticipante.inicial(...)` en memoria y los dos terminan en un `INSERT`
  (vía `merge()` de Spring Data JPA sobre una entidad con `@Id` asignado a mano). El
  segundo `INSERT` viola la PK, su transacción entera hace rollback (incluido su asiento
  en el ledger), y el ajuste se pierde.
- **Solución:** `INSERT ... ON CONFLICT (participante_id) DO NOTHING` antes de la
  relectura con `PESSIMISTIC_WRITE` de siempre. Postgres serializa el `INSERT` concurrente
  contra la restricción UNIQUE (el segundo espera a que el primero resuelva, nunca hay dos
  inserts exitosos ni uno que viole la PK); quien pierde la carrera de creación
  simplemente relee la fila ya creada, la bloquea y aplica su ajuste arriba — ningún punto
  se pierde.
- **Cómo evitarlo:** `PESSIMISTIC_WRITE`/`FOR UPDATE` protege una fila que YA existe; no
  protege su creación. Cualquier `cargarOInicializar`/`findOrCreate` sobre una tabla con PK
  propia (no autogenerada) que pueda ejecutarse concurrentemente para la MISMA clave por
  primera vez necesita `INSERT ... ON CONFLICT DO NOTHING` (o crear la fila en el momento
  del alta, si el dominio lo permite) antes de cualquier lock — el lock solo entra en
  juego después de que la existencia de la fila esté garantizada. Antes de asumir que un
  `byIdParaEscritura` alcanza, preguntar: "¿puede esta ser la primera vez que se toca esta
  fila?" — si la respuesta es sí, el lock solo no basta (patrón ya visto, en su variante
  "check-then-act sobre fila existente", en C-2/C-3/C-13; C-12 es la misma familia mirando
  la fila que directamente no existe todavía).

## E-76 — 409 en operaciones de "creá si no existe" bajo concurrencia, y UnexpectedRollbackException oculto en confirmar asistencia (C-10/C-15)

- **Fecha:** 2026-09-01
- **Dónde:** `habits/application/services/EspirituService.java`,
  `chat/application/services/ConversacionService.java`,
  `notifications/infrastructure/adapter/out/persistence/tokenpush/TokenPushPersistenceAdapter.java`,
  `calendar/application/services/ConfirmacionService.java`.
- **Síntoma:** (C-10) un `GET`/`POST` idempotente ("traeme X, y si no existe creálo") le
  devuelve 409 al segundo de dos llamadores casi simultáneos del mismo recurso, aunque
  ambos deberían terminar viendo lo mismo. (C-15) un efecto secundario best-effort que
  falla dentro de una `@Transactional` puede hacer explotar el COMMIT con
  `UnexpectedRollbackException` en vez de dejar ver la causa real, aunque el código tenga
  un `try/catch` alrededor del fallo.
- **Causa real:** (C-10) check-then-act clásico contra una columna `UNIQUE`: "leer si
  existe" y "crear si no" no son atómicos, y dos lecturas casi simultáneas pueden pasar
  las dos por el camino de creación. (C-15) cualquier método con `@Transactional` propio
  (incluidos los `@Modifying` de Spring Data JPA — Spring los envuelve con
  `@Transactional` automáticamente) que PARTICIPA de una transacción ya abierta, si lanza,
  marca esa transacción COMPARTIDA como rollback-only ANTES de que el `catch` del llamador
  la atrape — atraparla no revierte esa marca.
- **Solución:** (C-10) según el contexto transaccional: si la creación y la relectura
  posterior viven en la MISMA `@Transactional` (no se puede "atrapar y releer" ahí mismo:
  Postgres deja la transacción abortada apenas el INSERT falla), aislar la creación en su
  propia transacción con `TransactionTemplate`/`Propagation.REQUIRES_NEW` (mismo patrón ya
  usado en `RegistroService`/`RachaService`/`PromocionCambioHorarioService`). Si es un
  UPSERT real sin lectura posterior en la misma llamada, preferir
  `INSERT ... ON CONFLICT ... DO UPDATE/DO NOTHING` atómico en la base (mismo patrón que
  `ReaccionMuroPersistenceAdapter`/`RecordatorioPersistenceAdapter`) — nunca lanza por una
  carrera, así que no hace falta ni `catch` ni transacción aislada. (C-15) aislar en
  `REQUIRES_NEW` cualquier efecto secundario best-effort (avisos, notificaciones) que no
  deba poder tumbar la operación principal si falla.
- **Cómo evitarlo:** ante un "buscá X, y si no existe creálo" dentro de un método
  `@Transactional`, preguntarse primero si el llamador necesita releer algo DESPUÉS de que
  la creación pueda fallar por una carrera — si sí, la creación tiene que vivir en su
  propia transacción (REQUIRES_NEW), nunca "catch y seguir" en la misma. Ante cualquier
  `try/catch` alrededor de una llamada a un puerto/repositorio dentro de un método
  `@Transactional` cuya intención es "si esto falla, no me importa, seguí igual", verificar
  que esa llamada esté aislada en su propia transacción — si no lo está, el catch es
  cosmético: la transacción de afuera ya puede estar condenada al momento en que se
  ejecuta el catch, y el síntoma (`UnexpectedRollbackException`) aparece lejos, en el
  commit, no en el punto real del problema.

## E-77 — INCR y EXPIRE en comandos Redis separados dejaban claves sin TTL — bloqueo permanente en los tres limitadores de tasa (C-8)

**Síntoma:** en teoría (nadie lo reportó todavía en producción), si el proceso moría justo entre
un INCR y su EXPIRE siguiente en cualquiera de los tres adaptadores Redis de limitación
(`LimitarSolicitudesResetRedisAdapter`, `ControlCuotaRedisAdapter`,
`CodigoVerificacionEmailRedisAdapter`), la clave del contador quedaba sin vencimiento. A partir de
ahí, esa IP/email/actor quedaba bloqueado para siempre (o, en `ControlCuotaRedisAdapter.liberar`,
un DECR sobre una clave ya vencida creaba una clave nueva en -1 sin TTL, huérfana pero inofensiva).
Desde C-16/E-72, el mismo defecto en `LimitarSolicitudesResetRedisAdapter` puede bloquear
permanentemente el ALTA de cuentas nuevas de una IP, no solo el reseteo de contraseña.

**Causa real:** los tres adaptadores hacían `INCR` (o `INCR` + lectura de otro TTL, en el caso del
código de verificación) y DESPUÉS `EXPIRE`/`PEXPIRE` como comandos Redis separados, sin nada que
los uniera atómicamente. El chequeo de "¿es la primera vez?" (`intentos == 1`) además no
autorreparaba una clave ya envenenada: solo intenta fijar TTL una vez en la vida de la clave.

**Solución:** los tres adaptadores ahora envuelven incremento + TTL en un único script Lua
(`DefaultRedisScript` + `RedisTemplate.execute`), que Redis ejecuta de punta a punta sin permitir
que otro comando se intercale. El chequeo pasó de "¿es la primera vez?" a "¿esta clave tiene TTL
AHORA MISMO?" (`TTL == -1`): eso hace que cualquier clave envenenada (por el código viejo, o por
cualquier causa futura) se autorepare en la SIGUIENTE llamada que la toque, sin limpieza manual.
`ControlCuotaRedisAdapter.liberar` además pasó a chequear `EXISTS` antes de `DECR`, dentro del
mismo script, para no crear una clave huérfana sobre una que ya venció.

**Cómo evitar que vuelva a pasar:** cualquier contador en Redis que combine "incrementar" +
"fijar TTL si hace falta" tiene que hacerlo en un único script Lua (`DefaultRedisScript`), nunca en
dos llamadas separadas al `RedisTemplate` — ni siquiera si la primera parece "atómica por sí sola"
(`INCR` lo es, pero la SECUENCIA de INCR-y-después-EXPIRE no). El chequeo de "hace falta fijar TTL"
debe ser sobre el ESTADO ACTUAL de la clave (`TTL == -1`), no sobre el valor que acaba de devolver
el incremento (`== 1`) — lo segundo no autorrepara nada si la clave ya estaba mal por otro motivo.

## E-78 — Una prueba de integración que pasaba o fallaba según la hora del día en que se corriera

- **Fecha:** 2026-09-02
- **Dónde:** `EspirituConcurrenciaTest` (prueba nueva escrita al aplicar C-10).
- **Síntoma:** `AssertionFailedError: [una sola fila desbloqueada pese a 6 lecturas concurrentes] expected: 1L but was: 0L`. Ninguna de las seis llamadas concurrentes lanzó excepción — simplemente **no se creó ninguna fila**. El código de producción estaba correcto.
- **Causa real:** `EspirituService.asegurarAvance` retorna sin hacer nada antes de `HORA_DESBLOQUEO` (07:00 en la zona del participante). La prueba sembraba `timezone = 'UTC'` y usaba **el reloj del sistema**; se corrió a las 00:35 de Perú, o sea **05:35 UTC**, antes de las siete. La misma prueba, sin tocar una línea, habría pasado a media mañana. Es una prueba intermitente cuyo resultado depende de a qué hora se corra el build — y de las peores, porque en horario de oficina se ve verde siempre y solo falla de madrugada o en un CI en otro huso horario.
- **Un segundo detalle del mismo caso, que también costó tiempo:** el `AudioCatalogPort` real es `NoOpAudioCatalogAdapter` y siempre devuelve vacío (Google Drive nunca se integró — CLAUDE.MD §11), así que sin un doble el camino de escritura tampoco se alcanzaría nunca. Acá el agente sí lo había previsto con un `@Primary` en una `@TestConfiguration`; se menciona porque es la otra mitad de la misma trampa: **en este repo hay adaptadores `NoOp` en producción, y una prueba de integración que dependa de uno de ellos no prueba nada.**
- **Solución:** el reloj entra por el puerto `Clock` — que existe exactamente para esto (CLAUDE.MD §5) — con un `@Bean @Primary` que devuelve `FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"))`. Se dejó el motivo escrito en el javadoc de la clase para que nadie lo "simplifique" de vuelta al reloj del sistema.
- **Cómo evitarlo:** ninguna prueba puede leer la hora real. Si el código bajo prueba consulta el reloj —aunque sea indirectamente, tres llamadas más abajo— la prueba fija el `Clock` por el puerto. La señal de alarma es cualquier prueba que dependa de una ventana horaria, un vencimiento, un "día de hoy" o un `LocalDate.now()`. Y antes de dar por buena una prueba de integración nueva, verificar que **todos** los puertos que su camino atraviesa tengan un adaptador real o un doble explícito: un `NoOp` en el medio hace que la prueba pase por el camino equivocado sin fallar. Junto con **E-74**, las dos entradas cubren el mismo aprendizaje: las pruebas de integración de esta auditoría fallaron más veces por su andamiaje (semilla, esquema, reloj, dobles) que por el código que venían a verificar.

## E-79 — La foto del Muro se subía bien y se veía rota: se guardaba la URL absoluta donde va la clave de S3

- **Fecha:** 2026-09-02
- **Dónde:** `MediaItemRequest.aArchivoEntrada()` (`community`, adaptador REST) + `wallApi.urlPermanenteDesdeSubida` (app RN).
- **Síntoma:** una publicación con foto aparecía en el Muro con el recuadro gris y el texto `📷 Foto 1` en vez de la imagen. Sin error en pantalla, sin nada en los logs del backend: la publicación se creaba con `201`, la foto llegaba a S3, y el feed devolvía un `media[0].url` con pinta de URL firmada válida. Pedirla daba **404**.
- **Causa real:** la app subía los bytes con la URL prefirmada y después mandaba en `POST /api/v1/wall` la **URL absoluta** del objeto (`https://s3-renaser90dias.s3.amazonaws.com/muro/fotos/<autorId>/<uuid>`), descartando la `ruta` que el propio backend le había devuelto. `MediaItemRequest.aArchivoEntrada()` metía esa URL entera en el campo `ruta` — su javadoc **prometía** traducirla a bucket+ruta, pero la traducción no estaba escrita. Al leer el feed, `PublicacionMuroService.aVista()` pasa esa `ruta` a `AlmacenamientoPort.firmarLectura`, que la trata como **clave de objeto**, así que el presigner firmaba una URL anidada sobre sí misma:

  ```
  https://s3-renaser90dias.s3.us-east-1.amazonaws.com/https%3A//s3-renaser90dias.s3.amazonaws.com/muro/fotos/...
  ```

  Esa clave no existe → 404 → `FotoMuro.onError` esconde el `<Image>` y queda a la vista el recuadro con el texto de siempre. **Por eso no parecía un error:** la app estaba diseñada para degradar en silencio, y el defecto se veía igual que "todavía no hay foto".
- **Solución:** `aClaveDeObjeto()` en `MediaItemRequest` normaliza cualquier forma que mande el cliente (clave limpia, URL virtual-hosted, URL path-style, URL prefirmada) a la clave de S3, anclándose en el prefijo `muro/` — mismo truco que `AbrirTicketSoporteRequest.rutaDesdeUrl` en `support`, que sí lo hacía bien. `V17__medias_publicacion_ruta_no_url.sql` repara las filas ya guardadas con la misma regla en SQL y agrega un `CHECK` que prohíbe volver a guardar algo que empiece con `http`. Del lado de la app se eliminó `urlPermanenteDesdeSubida` (la función que fabricaba la URL) y ahora se manda `urlSubida.ruta` tal cual.
- **Por qué el barrido de E-57 no lo encontró:** E-57 era el mismo defecto **al revés** — persistía una URL *firmada* donde iba una clave — y su barrido buscó exactamente eso: "¿alguien guarda lo que devuelve `firmarLectura`?". `community` no lo hacía, así que pasó limpio. Lo que nadie chequeó fue la pregunta complementaria: **"¿lo que sí se guarda es una clave válida?"**.
- **Cómo evitar que vuelva a pasar:** cuando un valor persistido alimenta a `firmarLectura`, no alcanza con verificar que *no* sea una URL firmada — hay que verificar que **sea una clave**, y el `CHECK` en la columna es la forma barata de que la base lo sostenga sola. Y la regla más general, que es la que de verdad falló acá: **un javadoc que describe una traducción no es la traducción.** Si un comentario dice "esto se traduce acá", tiene que haber una prueba que lo demuestre; `MediaItemRequestTest` es esa prueba. La segunda señal ignorada fue el `onError` que esconde la imagen: **una degradación silenciosa en el cliente convierte un bug del servidor en algo invisible** — cuando exista, tiene que quedar registrado en algún lado, aunque no se le muestre a la persona.

## E-80 — El feed del Muro hacía 4 consultas por publicación: ~84 por carga, cada una con su propia conexión

- **Fecha:** 2026-09-02
- **Dónde:** `PublicacionMuroService.aVista()` / `aPagina()` (`community`).
- **Síntoma:** ninguno visible en local — y ese es el punto. Con el Postgres en Docker en la misma máquina, las 81 consultas de una página de 20 publicaciones se resuelven en ~82 ms medidos, así que en desarrollo el Muro se siente instantáneo y no hay nada que investigar.
- **Causa real:** `aPagina` llamaba a `aVista` en un bucle, y `aVista` hacía **cuatro consultas por publicación** (perfil del autor, conteo de reacciones, mi reacción, conteo de comentarios). Con `TAMANO_PAGINA = 20` eso son ~84 consultas por carga del Muro. Agravado por dos cosas: `feed()` no tenía `@Transactional`, así que cada consulta pedía y devolvía **su propia conexión** del pool de Hikari (tamaño 20) — ~84 tomas por request en vez de 1; y el método en lote que evitaba todo esto **ya existía y nadie lo usaba** (`ConsultarPerfilUsuarioPort.porIds`, con el comentario "Evita N+1" escrito encima).
- **Por qué importa igual:** contra una base administrada en otra zona, cada viaje cuesta 0,5–2 ms, así que el mismo patrón pasa a 40–170 ms de pura espera **por usuario y por carga**. Y hasta este cambio el Muro se recargaba entero después de cada publicación, así que publicar pagaba ese costo dos veces.
- **Solución:** `aVistas(List<Publicacion>, viewer)` enriquece la página entera con **cuatro consultas fijas**, sin importar el tamaño de la página, usando `porIds` + los nuevos `contarPorTipoDeVarias`, `deUsuarioEnVarias` y `contarDeVarias`. `feed()`/`feedOculto()` pasaron a `@Transactional(readOnly = true)` para que todo comparta una conexión. Firmar las URLs adentro de la transacción es seguro y no contradice CLAUDE.MD §7: el presigner de S3 calcula la firma **localmente**, sin llamar a AWS.
- **Cómo evitar que vuelva a pasar:** la prueba que lo fija (`laProyeccionNoConsultaUnaVezPorPublicacion`) verifica que los métodos de a una **nunca** se llamen, no que se llamen N veces. Un `verify(..., times(20))` sería la prueba equivocada: se pondría verde justo cuando el defecto vuelve. Y la lección de fondo: **el Postgres local miente sobre la latencia.** Un N+1 es invisible sobre un socket local y caro sobre la red; la señal a buscar en revisión es "¿cuántas consultas hace esto si la lista tiene 20 elementos?", nunca el tiempo del reloj en desarrollo. Cuando un puerto ya expone un método en lote, usarlo no es optimización prematura — es el uso previsto.


## E-81 — C-4 corrigió un problema de concurrencia y sin querer ensanchó otro (C-5)

- **Fecha:** 2026-09-02
- **Dónde:** `evidence/infrastructure/adapter/in/scheduler/ProcesarColaValidacionScheduler.java`,
  `EvidenciaService.procesarLote` (el método que C-4 ya había tocado).
- **Síntoma:** ninguno reportado todavía en producción — encontrado por auditoría estática al
  reclasificar C-5. Con N instancias desplegadas, el mismo lote de 25 evidencias "PENDIENTE"
  puede procesarse dos veces en paralelo.
- **Causa real:** C-4 acortó a propósito la transacción que sostiene el
  `FOR UPDATE SKIP LOCKED` de `pendientesLote` (para no retener una conexión de Hikari 19
  minutos con IA real) — correcto para el problema que resolvía. Efecto no buscado: el lock de
  fila ahora se libera casi al instante (apenas termina el SELECT), mucho antes de que termine
  el procesamiento real. Antes de C-4 el lock viejo duraba todo el procesamiento y tapaba, sin
  querer, la falta de coordinación entre instancias que describe C-5.
- **Solución:** `@SchedulerLock` (ShedLock, tabla `renaser.shedlock`) sobre el scheduler que
  llama a `procesarLote()` — coordina a nivel de "quién puede correr el barrido completo", no a
  nivel de fila, así que sigue siendo válido aunque el lock de fila dure microsegundos.
- **Cómo evitar que vuelva a pasar:** cuando se acorta o se elimina una transacción larga por
  un motivo (agotamiento de pool, timeout), preguntarse explícitamente si esa transacción larga
  estaba, de rebote, sirviendo de mecanismo de coordinación entre instancias para algo más. Un
  lock de fila (`FOR UPDATE`) solo coordina mientras la transacción que lo sostiene sigue
  abierta — si se acorta esa transacción sin agregar otra forma de coordinación, cualquier
  barrido que dependía de esa duración larga queda expuesto.

## E-82 — El outbox de Modulith no tenía ninguna clave `spring.modulith.*`: sin republicación al reiniciar, sin límite de crecimiento, y 4 listeners que duplicaban su efecto ante una redelivery (C-7)

- **Fecha:** 2026-09-02
- **Dónde:** `application.yaml` (sin ninguna clave `spring.modulith.*`),
  `notifications/application/services/NotificacionService.java`,
  `notifications/infrastructure/adapter/in/event/*NotificationListener.java` (los 4),
  `notifications/domain/model/notificacion/Notificacion.java`.
- **Síntoma:** ninguno visible todavía en producción (el outbox nunca se probó bajo una caída
  real ni bajo un reintento). El riesgo era latente: si el proceso muriera entre el commit de
  un evento y que su listener terminara, esa publicación quedaba incompleta para siempre (nadie
  la reentregaba); y el día que se activara la reentrega, cada redelivery de
  `HabitoCompletado`/`RachaCompletada`/`SantuarioRoto`/`RocaCompletada` iba a crear una fila
  nueva en `notificaciones` (bandeja duplicada) y reenviar un push duplicado, porque
  `EmitirNotificacionUseCase.emitir` no tenía ninguna clave de deduplicación.
- **Causa real:** at-least-once (la garantía real de cualquier outbox transaccional, incluido
  el de Modulith) significa que un listener PUEDE recibir el mismo evento más de una vez. Los
  4 listeners de `notifications` traducían el evento a un `INSERT` incondicional; nada los
  hacía tolerantes a una segunda entrega.
- **Solución:** `republish-outstanding-events-on-restart=true` + `completion-mode=DELETE`
  (config) + un scheduler que además reintenta publicaciones incompletas sin esperar a un
  restart (`shared/infrastructure/event/EventPublicationMaintenanceScheduler`); del lado de
  `notifications`, cada evento de dominio ya trae un id propio
  (`registroId`/`rachaId`/`rocaId`) que ahora viaja como `Notificacion.origenEventoId`, con un
  índice único parcial (`notificaciones_origen_evento_uk`, V16) que hace que una segunda
  entrega choque contra la restricción en vez de crear una fila nueva —
  `NotificacionService.emitir` la atrapa en su propia transacción (`REQUIRES_NEW`, mismo
  patrón que C-10) y la trata como éxito idempotente.
- **Cómo evitarlo:** cualquier listener que consuma eventos de Modulith (o de cualquier outbox
  transaccional) tiene que asumir at-least-once desde el diseño, no agregarlo después. La
  pregunta a hacerse al escribir un `@ApplicationModuleListener` nuevo: "¿qué pasa si esto se
  ejecuta dos veces con el mismo evento?" — si la respuesta es "se duplica un efecto visible"
  (una fila, un mensaje enviado, un contador que sube), hace falta una clave de deduplicación
  desde el primer commit, no como parche posterior. Los eventos de este repo ya traen esa clave
  natural (`registroId`/`rachaId`/`rocaId`/etc.) porque `habits.api`/`rocks.api` los diseñaron
  con un id de dominio propio — aprovechar esa clave existente es más simple que inventar una
  nueva.

## E-83 — C-18: revisados los 113 `@Transactional` sin `readOnly` fuera de `habits`, ninguno era seguro de marcar; open-in-view apagado

- **Fecha:** 2026-09-02
- **Dónde:** `docs/informes/auditoria-fixes/C-18.md` (análisis completo),
  `src/main/resources/application.yaml`, `src/test/resources/application.yaml`
  (`spring.jpa.open-in-view: false`).
- **Síntoma:** ninguno — es el cierre de un hallazgo de auditoría (C-18, baja), no un bug
  reportado.
- **Causa real:** no era un bug, era una pregunta abierta ("¿cuáles de los 146 `@Transactional`
  son en realidad de solo lectura?"). Respuesta, tras revisar los 113 que quedaban fuera de
  `habits` método por método: ninguno. El patrón de este repo es que los casos de uso de
  lectura pura (`listar`/`buscar`/`misX`) no llevan `@Transactional` en absoluto (se apoyan en
  la transacción por-método que ya aplica Spring Data JPA), así que todo `@Transactional`
  "pelado" que sobrevivió a esa convención es, sin excepción encontrada, un caso de uso que
  escribe — directo, delegado a otro puerto, o via un lock `PESSIMISTIC_WRITE` tomado para
  escribir después.
- **Solución:** no se marcó ningún método nuevo como `readOnly=true` (habría sido un cambio sin
  ningún método al que aplicarlo). Se apagó `open-in-view` (antes activo por default, sin
  ninguna clave que lo declarara) porque se pudo demostrar que es seguro en este repo
  específico: cero relaciones JPA reales (`@OneToMany`/`@ManyToOne`/`@OneToOne`/`@ManyToMany`)
  en las 74 entidades del sistema, cero `@Basic(fetch=LAZY)`/`@Lob`, los dos únicos
  `@ElementCollection` son `EAGER`, y ninguna entidad JPA cruza la frontera hexagonal hacia
  fuera de su adaptador de persistencia.
- **Cómo evitarlo (en realidad: cómo no perder este análisis):** si en el futuro alguien agrega
  una relación `@OneToMany`/`@ManyToOne` perezosa a una entidad, tiene que revisar si algún DTO
  de salida se arma fuera del método de servicio que la carga — con `open-in-view=false` ya
  apagado (en main y en test), cualquier violación de eso va a fallar con
  `LazyInitializationException`, en test antes que en producción. No hace falta volver a este
  documento para eso: el propio fallo del test es la señal.

## E-84 — `GET /api/v1/me/cell` devolvia 404 para decir "todavia no tenes celula", y la app no podia distinguirlo de un error real

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

## E-85 — Sin barrido nocturno, quien nunca abre la app no genera tracks: sin tracks no hay fallos, y su coherencia queda intacta

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

## E-86 — Flyway se niega a arrancar: se edito una migracion que ya estaba aplicada

- **Fecha:** 2026-09-02
- **Dónde:** arranque del backend, tras un día con varios agentes escribiendo migraciones.
- **Síntoma exacto:**
  ```
  Validate failed: Migrations have failed validation
  Migration checksum mismatch for migration version 17
  -> Applied to database : -440407064
  -> Resolved locally    : -817047180
  ```
  El contexto de Spring no levanta: `flywayInitializer` falla y arrastra a `entityManagerFactory`.
- **Causa real:** alguien **editó `V17__medias_publicacion_ruta_no_url.sql` después de que ya se hubiera aplicado** a la base (a las 15:21 del mismo día). Flyway guarda una huella de cada migración aplicada justamente para detectar esto: es su protección para que nadie reescriba la historia del esquema. **No hubo daño en la base** — el efecto de la migración estaba aplicado; lo que no coincidía era el archivo.
- **Solución:** `repair` — actualizar la huella guardada para que coincida con el archivo actual, sin volver a ejecutar nada. Como el proyecto no tiene el plugin de Flyway, se hizo con un `UPDATE` sobre `public.flyway_schema_history` fijando el checksum resuelto localmente, que es exactamente lo que hace `flyway repair`.
- **Lo que hay que verificar ANTES de reparar, y es la parte importante de esta entrada:** `repair` marca la migración como buena **sin ejecutarla**. Si la edición hubiera **agregado sentencias nuevas**, repararlo las habría salteado en silencio y la base quedaría incompleta sin que nadie se entere. Antes de reparar hay que confirmar que el efecto completo del archivo actual ya está en la base. En este caso se comprobó que la restricción `medias_publicacion_ruta_no_es_url` **existía** (y no podría haberse creado si los `UPDATE` previos no hubieran corrido) y que **cero filas** violaban la condición.
- **Cómo evitar que vuelva a pasar:** **una migración aplicada no se toca nunca más** — ni para corregir un comentario. Si hay algo que cambiar, se crea una migración nueva. Y cuando hay varios agentes trabajando en paralelo, hay que **asignarles números de migración distintos por adelantado** y prohibirles tocar los archivos ajenos: en esta misma jornada dos agentes eligieron `V15` por su cuenta y otros dos eligieron el mismo número de entrada de bitácora (`E-75`) y de decisión (`D-66`).

## E-87 — El backend rechazaba el cambio de horario del 86% de los habitos por exigir un campo que la mayoria no tiene

- **Fecha:** 2026-09-02
- **Dónde:** `UpdateHabitPreferenceRequest.java` y `PreferenciaHorarioService.requireOrdenHorario`.
- **Síntoma:** el dueño del proyecto reportó *"la parte de editar el horario no funciona"*. Desde la app, cambiar la hora de casi cualquier hábito devolvía **400** y el cambio se revertía.
- **Causa real:** el DTO declaraba `@NotNull LocalTime limitTime`, pero **19 de los 22 hábitos del catálogo real no tienen hora de cierre** (`limitTime: null`) porque no vencen dentro del día. El frontend hacía lo correcto —reenviar el `limitTime` existente para no borrarlo, ver la corrección de ese bug en el mismo cambio— pero cuando ese valor es `null`, la validación del DTO cortaba la petición antes de llegar al servicio. Y aunque hubiera pasado, `requireOrdenHorario` hacía `horaDisparo.isBefore(horaLimite)` sin proteger el nulo.
- **Por qué estaba así:** el DTO llevaba escrito *"contrato HTTP viejo literal (D-36)"*. Se copió del contrato anterior sin verificar que el catálogo real lo cumpliera. **El contrato viejo describía un cliente que siempre mandaba ambos campos; el catálogo de hoy no.**
- **Solución:** `limitTime` pasa a ser opcional, y la validación de orden solo se aplica cuando hay hora de cierre. `triggerTime` sigue siendo obligatoria: sin hora de disparo el hábito no se puede ubicar en la jornada.
- **Cómo evitar que vuelva a pasar:** cuando un DTO se copia de un contrato anterior, **hay que contrastar cada campo obligatorio contra los datos reales** antes de darlo por bueno. Un `@NotNull` heredado sin verificar convierte un endpoint en inútil para la mayoría de los casos, y el síntoma —"no funciona"— aparece lejos de la causa. Es la misma familia que **E-65** (una anotación de Jackson 2 ignorada en silencio por Jackson 3) y **E-60**: *el mensaje apuntaba a un lugar y la causa estaba en otro*.

## E-88 — No existe forma de crear el primer ADMIN: `POST /admin/staff/invite` exige un actor que ya sea ADMIN/ALQUIMISTA

- **Fecha:** 2026-09-02
- **Dónde:** `StaffAdminController.invite`, `UserAccountService` (invitación de staff).
- **Síntoma:** con la base de datos local en 0 usuarios, no hay ningún endpoint público que permita crear la primera cuenta ADMIN. `POST /api/v1/account-requests` (el alta pública) fuerza `rol = APRENDIZ` siempre (CLAUDE.MD §5.3.3, a propósito, contra mass-assignment). `POST /api/v1/admin/staff/invite` sí puede crear un ADMIN, pero exige `@ActorAutenticado` con permiso `MANAGE_ROLES`, verificado dentro del propio caso de uso — y sin usuarios en la base, ningún actor pasa esa verificación.
- **Causa real:** es una consecuencia deliberada del diseño de seguridad (nadie se auto-asigna ADMIN), pero no existe ningún `CommandLineRunner`/seed/Flyway que bootstree la primera cuenta ADMIN en un entorno nuevo. Se buscó explícitamente y no hay nada: ni en `db/migration`, ni un runner de arranque, ni una credencial de dev documentada.
- **Solución aplicada (solo para entorno local de desarrollo):** `INSERT` directo en `renaser.usuarios` con `rol='ADMIN'`, generando el hash de contraseña con el mismo `PasswordEncoderFactories.createDelegatingPasswordEncoder()` que usa `SecurityConfig` (vía `jshell` cargando `spring-security-crypto` + `spring-jcl` del `.m2` local, para que el hash `{bcrypt}$2a$...` sea idéntico al que generaría la app). Con ese ADMIN ya en la base, las operaciones siguientes (aprobar solicitudes, invitar más staff) se hicieron por el endpoint real usando el header temporal `X-Actor-Id` (`ActorAutenticadoArgumentResolver`, el mismo respaldo que usan hoy los 54 controllers migrados), no por SQL — para no saltarse la lógica de negocio real (`ApproveAccountRequestUseCase` crea el `User` + perfil + marca la solicitud, todo en una transacción).
- **Cómo evitar que vuelva a pasar:** documentar (o construir) un camino de bootstrap explícito para el primer ADMIN de un entorno nuevo — por ejemplo un `CommandLineRunner` que solo corre en el perfil `local` y solo si `renaser.usuarios` está vacía, o un script versionado en el repo (no en la bitácora) con los pasos de este `INSERT`. Mientras no exista, cualquiera que levante el proyecto desde cero se topa con este mismo bloqueo.

## E-89 — Onboarding: `NUMERO requiere exactamente el valor NUMERO` en preguntas 16 y 36; el frontend tenía TODOS los IDs de `preguntas_onboarding` corridos en +1

- **Fecha:** 2026-09-03
- **Dónde:** `Renaser-90-dias-frontend-/src/features/onboarding/data/mapaPreguntas.ts`.
- **Síntoma exacto (console warning de React Native):**
  ```
  No se pudo guardar la respuesta de onboarding (questionId=16), se reintentará más tarde: ApiError: Una respuesta de tipo NUMERO requiere exactamente el valor NUMERO y ningun otro slot
  No se pudo guardar la respuesta de onboarding (questionId=36), se reintentará más tarde: ApiError: Una respuesta de tipo NUMERO requiere exactamente el valor NUMERO y ningun otro slot
  ```
  Además, sin ningún error visible: probablemente otras respuestas del onboarding se estaban guardando bajo la pregunta EQUIVOCADA sin que nada lo detectara, cuando el tipo de la pregunta real coincidía por casualidad con el tipo esperado por el frontend (ver más abajo).
- **Causa real:** `mapaPreguntas.ts` hardcodea los `id` numéricos de `renaser.preguntas_onboarding`, consultados en vivo el 2026-09-01. En algún momento entre esa fecha y el 2026-09-03 la tabla se reseedeó (`GENERATED ALWAYS AS IDENTITY`, sin `id` estable garantizado entre reseeds) y **todos los ids se corrieron exactamente -1** respecto de lo que el frontend tenía hardcodeado (ejemplo: `sleep_hours` pasó de id 37 a id 36; `identity_document` de 16 a 15; `terms_signature` de 2 a 1). El caso de `questionId=16` fallaba fuerte porque el id 16 real es ahora `age` (NUMERO) y el frontend mandaba ahí el valor de `identity_document` (TEXTO) — la invariante de `Respuesta.java` lo rechazaba. El caso más peligroso NO tira error: `id=37` real es `height_cm` (NUMERO) y el frontend mandaba ahí `sleep_hours` (también NUMERO) — incluso con el offset corregido, si dos preguntas consecutivas comparten tipo, un desfasaje de ids se guarda "bien" en la pregunta equivocada, sin ningún síntoma.
- **Solución:** se re-consultó `SELECT id, clave_pregunta, tipo FROM renaser.preguntas_onboarding ORDER BY id` contra la base local y se reescribieron los 24 ids de `mapaPreguntas.ts` (y sus menciones en los comentarios de `CAMPOS_SIN_MAPEAR`) contra el catálogo actual.
- **Cómo evitar que vuelva a pasar:** hardcodear ids autoincrementales de una tabla seedeada por Flyway en el cliente es inherentemente frágil — sobrevive solo mientras nadie reseedee esa tabla. Dos mitigaciones a evaluar (no aplicadas en este cambio, quedan para discutir con el dueño): (1) que el backend jamás trunque/reseedee `preguntas_onboarding` en un ambiente con respuestas ya guardadas — solo `INSERT`/`UPDATE` incrementales vía migraciones nuevas, igual que la regla ya vigente para datos de tablas normales; (2) que el frontend resuelva el `questionId` por `clave_pregunta` contra un catálogo que el backend expone en vivo (`GET`), en vez de hardcodearlo — más robusto pero cambia el patrón actual de los otros 13 módulos que si hardcodean ids de catálogo. Mientras tanto, **cualquier reseed de `preguntas_onboarding` tiene que ir acompañado de volver a correr la query del comentario de `mapaPreguntas.ts` y revisar el diff completo**, no solo las preguntas que tiraron error.

## E-90 — Editar horario de un hábito seguía fallando después de E-87: el DTO se corrigió pero el comando de aplicación no

- **Fecha:** 2026-09-03
- **Dónde:** `EditarPreferenciaHorarioUseCase.EditarPreferenciaHorarioCommand` (backend); `habitsApi.cambiarHorario` (frontend).
- **Síntoma:** reportado en vivo por el dueño ("revisé el edita horario no funciona correctamente"). `PATCH /api/v1/habit-preferences/{habitId}` devolvía **400** para cualquier hábito, incluso después de que E-87 hiciera `limitTime` opcional en `UpdateHabitPreferenceRequest`.
- **Causa real — dos bugs distintos apilados en el mismo endpoint:**
  1. **El comando, no el DTO.** E-87 solo tocó el DTO HTTP (`UpdateHabitPreferenceRequest.limitTime`, ya no `@NotNull`). Pero `HabitPreferenceController.editar` pasa ese valor (ahora nullable) directo a `new EditarPreferenciaHorarioCommand(...)`, cuyo campo `horaLimite` **seguía siendo `@NotNull`** — el propio constructor (`SelfValidating.validateConstructorArgs`) rechazaba la llamada antes de que `PreferenciaHorarioService.editar` llegara a ejecutarse. Verificado con `curl`: `{"triggerTime":"09:30:00","limitTime":null,...}` → `400 "EditarPreferenciaHorarioCommand.horaLimite: no debe ser nulo"`. Todo el resto de la cadena (`PreferenciaHorario.crear/aplicarAhora`, `CambioHorarioPendiente.programar`, `PreferenciaHorarioService.requireOrdenHorario`) YA toleraba `horaLimite == null` correctamente — el único bloqueante era esta anotación, un nivel más adentro de donde miró E-87.
  2. **`reminderEnabled` es un `boolean` primitivo, y el frontend nunca lo manda.** `habitsApi.cambiarHorario` solo envía `{ triggerTime, limitTime }`. Como Jackson resuelve los `record` por el constructor canónico, un campo primitivo ausente en el JSON no puede recibir `null` — la deserialización entera del DTO fallaba con un genérico `400 "El cuerpo de la solicitud es invalido o esta mal formado"` (sin mensaje de campo, porque es un fallo de parseo, no de `@Valid`). Esto pasaba SIEMPRE, para cualquier hábito, con o sin `limitTime` — es el bug que de verdad bloqueaba todo, y el que primero apareció al probar con curl.
- **Por qué no se detectó en tests:** los tests de `PreferenciaHorarioServiceTest`/`PreferenciaHorarioService` llaman al comando construido a mano en Java, con todos los campos presentes — nunca pasan por deserialización JSON real ni por un `limitTime` nulo desde el controller. El gap estaba en la frontera HTTP↔comando, que ningún test de unidad cruza.
- **Solución:**
  1. Backend: se sacó `@NotNull` de `EditarPreferenciaHorarioCommand.horaLimite` (`EditarPreferenciaHorarioUseCase.java`) — el resto de la cadena ya estaba lista para recibirlo nulo.
  2. Frontend: `habitsApi.cambiarHorario` ahora manda siempre `reminderEnabled: false, reminderMinutesBefore: null` explícitos. No hay ninguna pantalla de recordatorios en la app todavía, y `GET /habit-preferences` tampoco devuelve el estado actual del recordatorio — no hay forma de preservarlo aunque se quisiera, así que no regresiona nada real.
  3. Verificado con `curl` end-to-end contra el backend corriendo: hábito sin `limitTime` (200), hábito con `limitTime` existente preservado (200), y el payload exacto que manda hoy el frontend (200). `PreferenciaHorarioServiceTest`: 7/7 en verde. `./mvnw clean test` completo: 2152 tests, 0 failures, 3 errors — los 3 en módulos no tocados por este cambio (`GenerarTracksDelDiaSchedulerTest`, `NotificacionServiceTest`, `NotificacionesNoLeidasServiceTest`; el segundo y tercero probablemente son el mismo problema de fondo — un `NullPointerException` en el primero deja el mock de Mockito en mal estado para el test que corre después en el mismo fork). Confirmado con `git diff --stat` que esos tres archivos no forman parte de este cambio.
- **Cómo evitar que vuelva a pasar:** cuando una corrección toca un DTO HTTP (`@NotNull` → opcional), **hay que seguir el dato hasta el final de la cadena, no solo hasta que el primer 400 desaparezca** — acá E-87 arregló el primer bloqueante que encontró y dio por cerrado el hueco, pero había un segundo `@NotNull` idéntico un nivel más adentro (el comando de aplicación) que nadie volvió a mirar. Antes de cerrar un bug de "campo obligatorio que no debería serlo", buscar `@NotNull`/`Objects.requireNonNull` de ese mismo campo en TODA la cadena (DTO → comando → dominio), no solo en el primer lugar donde se ve el error. Y para bugs de contrato HTTP, un `curl` directo contra el endpoint real con el payload EXACTO que manda el cliente encuentra en segundos lo que un test unitario con el comando armado a mano nunca va a ver.

## E-91 — El día del programa no avanzaba: el cron corría 10 minutos antes de que empezara el día del aprendiz

- **Fecha:** 2026-09-03
- **Dónde:** `users.AvanzarDiaProgramaScheduler` (cron `0 50 4 * * *` UTC) + `ParticipacionPrograma.avanzarDiaDelPrograma`.
- **Síntoma:** reportado en vivo por el dueño ("ingresé en la cuenta y pude visualizar que sigo en el día 0, no avanzó, y eso que la cuenta fue creada ayer"). Cuenta `ricardoismael777@gmail.com`, fila real: `creado_en 2026-09-03 02:00 UTC`, `programa_activado_en 2026-09-03 04:07 UTC`, `fecha_inicio 2026-09-03`, `timezone America/Lima`, `dia_programa 0`, `dia_programa_avanzado_el NULL`. El 2026-09-03 en Lima **era su Día 1** y la app mostraba 0.
- **Causa real — dos problemas apilados:**
  1. **Desfase de zona horaria (el que se veía).** El cron corría a las **04:50 UTC**, elegida para caer ordenada entre los otros crons nocturnos (`ExpirarRegistros` 05:00, `GenerarTracksDelDia` 05:02). Para `America/Lima` (UTC−5 — el default de `participantes_programa.timezone` y la zona de todo el padrón) las 04:50 UTC son las **23:50 del día ANTERIOR**. La guarda del dominio `if (fechaInicio.isAfter(hoyEnZonaParticipante)) return false` era verdadera, porque `hoyEnZonaParticipante` todavía era el día previo. Resultado: el avance a Día 1 recién ocurría en la corrida siguiente, a las 23:50 de su propio Día 1 — **diez minutos antes de que ese día terminara**. Y se repetía: en la jornada N el aprendiz veía N−1 durante 23 h 50 min. El reloj quedaba corrido un día entero para todo el programa, para toda América. Efecto colateral: `GenerarTracksDelDiaScheduler` (05:02 UTC = 00:02 Lima) armaba la jornada nueva con el `dia_programa` viejo, justo el orden que su javadoc decía estar garantizando.
  2. **El modelo incremental no se recupera (el de fondo).** `avanzarDiaDelPrograma` sumaba +1 y estampaba `dia_programa_avanzado_el = hoy`. Una noche sin el backend arriba se perdía **para siempre**: al día siguiente volvía a sumar 1 y el aprendiz quedaba un día atrasado el resto del programa. En este entorno no es hipotético — `renaser.shedlock` solo tenía filas de los jobs de minuto (`evidence-procesar-cola-validacion`, `shared-reintentar-publicaciones-incompletas`); **nunca hubo fila de `users-avanzar-dia-programa`**, o sea que el cron diario jamás tomó el lock en la vida de esta base. No hay entorno desplegado: el backend es un proceso de laptop y las 04:50 UTC son las 23:50 de Lima.
- **Por qué no se detectó en tests:** todos los tests del reloj fijan `FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"))` — las 05:00 de Lima, **mismo día calendario**. El instante real del cron (04:50 UTC, donde Lima ya está en el día anterior) no estaba cubierto por ninguna prueba. Los tests de dominio además llamaban a `avanzarDiaDelPrograma(unLocalDate, clock)` pasando la fecha a mano, así que la aritmética de zona ni siquiera participaba. El código estaba mal y el fixture lo tapaba.
- **Solución (migración `V20`, D-81):** el reloj pasó de **incremental** a **derivado**. `dia_programa` deja de ser la fuente de verdad y pasa a ser una copia materializada de `acotar([0,90], (hoy_en_su_zona − fecha_inicio) + 1 − dias_ajuste_programa)`. `avanzarDiaDelPrograma` → `sincronizarDiaDelPrograma` (idempotente, se pone al día de una sola corrida por más noches que se hayan perdido). El cron pasó de `0 50 4 * * *` a `0 5 * * * *` (**cada hora**): es la única forma de alcanzar la medianoche local de cualquier zona sin mantener una tabla de offsets, y es barato porque el dominio devuelve `false` cuando no hay nada que cambiar. Se agregó `dias_ajuste_programa` (`smallint` con signo) y `fijarDia` ahora escribe ese ajuste en vez del día — lo que además arregla que un ajuste manual de admin no persistía.
- **Verificado end-to-end contra el backend real (2026-09-03 21:20 Lima), no solo con tests.** La cuenta `ricardoismael777@gmail.com` (fecha_inicio 2026-09-03, America/Lima) quedó en **`dia_programa = 1` durante su Día 1**, que es lo que antes no pasaba: el cron horario corrió a las 02:05 UTC (`renaser.shedlock` lo registra) y sincronizó. Se probó además el flujo completo del pedido del cliente por HTTP: `PUT /api/v1/admin/trainees/{id}/program-day` con `{"programDay":34,"motivo":"..."}` → 204, día 34, fase recalculada a `FASE_2_DESARROLLO`, `dias_ajuste_programa` persistido, y `lastDayAdjustment` visible en el `GET` del detalle. **La propiedad clave, comprobada con aritmética sobre la fila real:** con el día en 34, el derivado de HOY da 34 (la sincronización no lo pisa) y el de MAÑANA da 35 — no vuelve de un salto al día real, que era el bug de `fijarDia`. Autorización negativa por HTTP: un APRENDIZ moviéndose el día a sí mismo recibe **403**, un día fuera de rango **400**, y tras ambos rechazos ni la fila ni la bitácora cambiaron. La cuenta se restauró a su estado exacto (día 1, ajuste 0, graduación 2026-12-02) y la bitácora conservó los dos movimientos, que es el comportamiento append-only esperado.
- **Lo más incómodo del incidente: el equipo ya lo sabía, en otro módulo.** `D-72` (2026-09-02, un día antes) documenta textualmente, para el barrido de tracks de `habits`, que *"a las 04:50 UTC en Lima todavía es el día anterior"* — y por eso ese cron se puso a las 05:02. Incluso deja anotado el **límite conocido**: *"un cron a hora UTC fija no cubre bien zonas más al oeste que Perú"*. El conocimiento existía y estaba escrito; nadie lo aplicó al cron de `users`, fijado el día anterior (D-67) razonando solo sobre el orden entre crons. La lección no es "faltaba saberlo" sino que **un hallazgo de zona horaria en un módulo es un hallazgo del sistema**: cuando aparezca uno, revisar TODOS los `@Scheduled` del repo en el mismo cambio, no solo el que se estaba tocando.
- **Cómo evitar que vuelva a pasar:**
  - **Nunca elegir la hora de un cron razonando solo sobre el orden entre crons.** La pregunta correcta es *¿en qué fecha local cae este instante para un participante de Lima?*. Un `@Scheduled` diario que depende del día local del usuario está mal por construcción: va cada hora, y el dominio decide.
  - **Derivar, no acumular.** Todo valor que sea función del calendario se calcula de fechas. Un contador incrementado desde un cron pierde para siempre cualquier corrida que no ocurra.
  - **Todo test de comportamiento diario debe incluir un caso con el reloj en una hora UTC que caiga en el día local anterior** (entre 00:00 y 05:00 UTC). Agregado: `RelojProgramaServiceTest.unParticipanteDeLimaEstaEnElDiaUnoDuranteTodoSuPrimerDia`.
  - Reglas ejecutables en [`.claude/rules/02-tiempo-zonas-y-schedulers.md`](../.claude/rules/02-tiempo-zonas-y-schedulers.md) y [`.claude/rules/03-pruebas.md`](../.claude/rules/03-pruebas.md), creadas en este mismo cambio.

## E-92 — Un agente trunco `TrainingScreen.tsx` a 0 bytes y se perdio el cableado de otros dos (2026-09-04)

**Sintoma exacto:** `src/screens/TrainingScreen.tsx` del movil quedo en **0 bytes**. No hubo mensaje de error visible: el archivo simplemente aparecio vacio mientras siete agentes trabajaban en paralelo sobre los dos repos.

**Causa real:** un script de uno de los agentes (el de evidencia generica) abrio el archivo en modo escritura — que trunca antes de escribir — y fallo antes de volcar el contenido. Cuatro agentes tenian instruccion de tocar ese mismo archivo; el truncado borro los cambios sin commitear de los otros dos que ya habian escrito ahi (Clase Diaria y Post en Comunidad). Los componentes nuevos de esos flujos, en archivos propios, sobrevivieron intactos.

**Solucion aplicada:** restaurar desde `HEAD`, reaplicar encima el trabajo de cada agente en el orden en que aterrizaron, y volver a cablear a mano los dos flujos perdidos (imports, estado, ramas en `toggleHabitState`/`openEvidenceModal`, back handler y montaje del modal). `npx tsc --noEmit` en cero con los siete trabajos juntos.

**Como evitar que vuelva a pasar:**
- **No lanzar en paralelo agentes que deban editar el mismo archivo.** Secuenciarlos, o repartir por archivo. Esta fue la causa de fondo, no el script.
- Al escribir un archivo desde un script, escribir a un temporal y renombrar (`os.replace`), nunca `open(path, 'w')` directo sobre el original.
- Antes de un trabajo en paralelo grande, `git stash`/commit del estado previo: lo que esta solo en el working tree no tiene copia en ningun lado (se busco en git, worktrees, Local History del IDE, cache de Metro y `dist/` — no estaba).

## E-93 — "Una respuesta de tipo FIRMA requiere mediaId" al firmar el Pacto: los ids del onboarding estaban corridos en uno (2026-09-04)

**Sintoma exacto (consola de Metro):** `WARN No se pudo guardar la respuesta de onboarding (questionId=5), se reintentara mas tarde: [ApiError: Una respuesta de tipo FIRMA requiere mediaId]`, y en pantalla "No pudimos registrar tu aceptacion del Pacto". Terminos y Condiciones "funcionaba" y el Pacto no.

**Causa real:** el commit `9ab4f6f` del movil (2026-09-02, "elegir Dia 1 tras Terminos... y arregla firma/horario") le resto 1 a los 24 ids hardcodeados en `src/features/onboarding/data/mapaPreguntas.ts`. Con eso el nombre del participante viajaba con el id 5, que en la base es `signature` (FIRMA), y el backend lo rechazaba con razon. Terminos "funcionaba" porque la cuenta soporte lo hizo el 2026-09-01 22:15, un dia ANTES del commit, con los ids correctos (sus respuestas estan bajo 3/4/6 = accepted_terms/accepted_pacto/participant_name). La cuenta del dueno lo intento despues y no tiene ninguna respuesta guardada: todas rebotaron. Se verifico ademas que los 24 ids de la version anterior al commit coinciden uno por uno con `renaser.preguntas_onboarding` de hoy.

**Pista falsa que costo tiempo:** hay una fila `test_question_1` (flujo `TEST_FLOW`) en el id 1, creada el 2026-08-25, que no viene de ninguna migracion ni de la suite. Parecia la causa del corrimiento, pero no lo es: existia antes de que se escribiera el mapa y antes del commit malo. Es contaminacion de pruebas y conviene borrarla, pero borrarla no cambia ningun id ya asignado.

**Solucion aplicada:** restaurar los 24 numeros de `9ab4f6f^` en `mapaPreguntas.ts` — solo esas lineas, sin tocar el resto de ese commit ni el mecanismo de firma. Elegida por el dueno como la opcion mas segura ("como funcionaba Terminos").

**Como evitar que vuelva a pasar:** los ids son detalle de una base concreta, lo estable es `clave_pregunta`. El backend ya expone el catalogo (`GET /api/v1/onboarding/questionnaire?flow=...`, con `id`, `questionKey` y `type`); el movil deberia resolver los ids por clave en tiempo de ejecucion y verificar el tipo, en vez de llevar numeros escritos a mano.

> **Actualizado 2026-09-04 (ver E-95).** Esto decia *"Queda propuesto, no hecho (el dueno pidio no tocar esa zona mas alla de la restauracion)"*. La restauracion de los 24 numeros se vencio en menos de un dia y el error volvio, asi que la resolucion por clave **se implemento**. Ademas, el diagnostico de arriba quedo incompleto: la causa de fondo no era el commit `9ab4f6f` sino que los ids del catalogo **no son reproducibles entre bases** — E-95 lo explica.

## E-94 — Pruebas de contexto completo que fallan con `NoClassDefFoundError` de una clase que nadie toco (2026-09-04)

**Sintoma exacto:** `ApplicationContext failure threshold (1) exceeded` en cuatro pruebas `@SpringBootTest` del modulo `rag` (`PgVectorNativoAdapterTest`, `ControlCuotaRedisAdapterTest`, `RenasiaConversacionPersistenceAdapterTest`, `InformeEspejoSombraPersistenceAdapterTest`), 19 errores. En el primer intento, la causa raiz enterrada en la traza: `java.lang.NoClassDefFoundError: com/renaser/os/users/infrastructure/adapter/out/persistence/identidadexterna/SpringDataIdentidadExternaRepository$IdentidadExternaRow`. Antes, en la misma sesion, compilando con incremental habian aparecido ademas `cannot find symbol` en `academy` (`AsignacionCursoPersistenceMapper`, `CursoPersistenceAdapter`) — archivos que tampoco se habian tocado.

**Causa real:** `target/classes` a medias. El IDE (con la app levantada y devtools) y Maven compilan sobre la MISMA carpeta; cuando se cruzan, un `.class` externo queda mas nuevo que su fuente pero sin sus clases internas (`$IdentidadExternaRow`), y ni el incremental de Maven ni devtools lo vuelven a generar porque "ya esta compilado". Las pruebas unitarias no lo notan; las de contexto completo, que instancian TODOS los beans, si.

**Solucion aplicada:** forzar la recompilacion de todo sin `clean` (que con la app levantada tumba el contexto, ver reglas de trabajo): `find src/main/java -name "*.java" -exec touch {} +` y despues `./mvnw test -Dmaven.compiler.useIncrementalCompilation=false ...`. Las cuatro clases pasaron a 19/19 sin cambiar una linea de codigo.

**Como evitar que vuelva a pasar:** cuando una prueba de contexto completo falle con `NoClassDefFoundError` o `cannot find symbol` en un archivo que no se toco, NO buscar el bug en ese archivo: es el `target` cruzado. Recompilar todo con el `touch` de arriba. La solucion de fondo seria que el IDE compile a otra carpeta (`out/`) y no a `target/classes`.

## E-95 — El mismo "FIRMA requiere mediaId" del Pacto, un dia despues: los ids del catalogo de onboarding NO son reproducibles (2026-09-04)

**Sintoma exacto (consola de Metro):** identico al de E-93, pero con otro numero — `WARN No se pudo guardar la respuesta de onboarding (questionId=4), se reintentara mas tarde: [ApiError: Una respuesta de tipo FIRMA requiere mediaId]`, disparado desde `usePersistenciaOnboarding.ts:35`.

**Causa real — la que E-93 no vio.** E-93 trato esto como "los ids estan corridos en uno" y lo arreglo corriendo los 24 numeros de vuelta. El arreglo se vencio en menos de 24 horas porque el problema nunca fueron los numeros:

`preguntas_onboarding.id` es una columna `GENERATED ALWAYS AS IDENTITY`, y el seed que la llena (`V10__catalogo_onboarding_default.sql`, linea 172) es un `INSERT ... SELECT ... FROM (VALUES ...) v JOIN secciones_onboarding s ON ...` **sin `ORDER BY`**. El orden en que ese JOIN emite las filas lo decide el planner de Postgres, no el orden del `VALUES`. Prueba directa: en el `VALUES` la primera fila es `accepted_terms` y `terms_signature` esta en la linea 152, pero en la base quedaron con id 2 y 1 respectivamente — dados vuelta.

Es decir: **los ids del catalogo cambian entre bases de datos y entre resembrados.** Van a ser otros en produccion y otros en la maquina de cualquiera que levante el Docker de cero. La prueba de que eso ya paso: E-93 documento una fila contaminante `test_question_1` ocupando el id 1; hoy esa fila **ya no existe** y la tabla tiene 102 preguntas con ids 1..102 limpios. El catalogo se resembro despues de escribir E-93, todos los ids se corrieron en −1, y los 24 numeros "restaurados" quedaron mal otra vez — los 24, no solo el del Pacto (verificado uno por uno contra la base).

**Por que fallaba de la peor forma posible:** un id corrido casi siempre guarda la respuesta **bajo la pregunta equivocada sin ningun error** (parece que funciono). Solo revienta ruidosamente cuando el corrimiento cae justo sobre una pregunta `FIRMA`/`AUDIO`/`ARCHIVO`, que son las unicas que exigen `mediaId` en vez de un valor tipado (`Respuesta.slotEsperado`, `SlotValor.SOLO_MEDIA`). El error visible era la punta: por debajo, el nombre, el pais, la profesion y todo lo demas se estaban guardando en la pregunta de al lado.

**Solucion aplicada (movil, definitiva):** los ids desaparecieron del codigo. Se resuelven en runtime por `clave_pregunta`, que si tiene `UNIQUE` en la tabla y es la misma en toda base. No hizo falta tocar el backend: `GET /api/v1/onboarding/questionnaire?flow=...` ya devuelve `{ id, questionKey, type }`.

- Nuevo `src/features/onboarding/data/catalogoPreguntas.ts`: pide los 3 flujos que responde la app (`terminos`, `pacto`, `ficha_inicial`), arma el mapa `clave -> { id, tipo }` y lo cachea (guarda la **promesa**, para que varias pantallas compartan una sola tanda de requests; si falla se limpia para poder reintentar).
- `mapaPreguntas.ts` pasa a declarar `{ clave, tipo }` y **cero numeros**. Los builders devuelven `RespuestaPorClaveInput`.
- `usePersistenciaOnboarding.enviarRespuestas` resuelve clave -> id justo antes de enviar. **Nunca manda un id adivinado**: si el catalogo no carga (fallo de red) deja todo pendiente de reintento; si una clave no existe o su tipo no coincide con el que la app asume, no la manda y la deja pendiente, que es lo que hace que la pantalla avise en vez de dar por guardado algo que no lo esta.
- El chequeo de tipo (`catalogo.idDe(clave, tipoEsperado)`) es la red de seguridad: convierte un "se guardo bajo la pregunta equivocada en silencio" en un error explicito que nombra la clave.

**Verificado (2026-09-04, contra el backend y la base reales, no solo con tests):**
- `npx tsc --noEmit` en cero.
- Las 24 claves del mapa resuelven contra el catalogo en vivo y **el tipo coincide en las 24**. Las 24 daban un id distinto del que estaba hardcodeado (−1 en todas), confirmando que el mapa entero estaba mal, no solo el Pacto.
- Reproducido el fallo y su arreglo por HTTP: `POST /api/v1/onboarding/answers` con `{"questionId":4,"booleanValue":true}` (lo que mandaba el codigo viejo para `accepted_pacto`) devuelve **400 "Una respuesta de tipo FIRMA requiere mediaId"**; con el id que ahora resuelve la clave (`accepted_pacto` -> 3) devuelve **200**.
- **Sin cubrir:** el movil no tiene runner de tests configurado (no hay `jest`/`vitest` en su `package.json`), asi que esto no quedo como prueba automatica. La verificacion de las 24 claves se hizo con un script suelto contra el backend en vivo.

**Como evitar que vuelva a pasar:**
- **Nunca hardcodear un id de fila generado por la base.** Si una tabla tiene una clave natural con `UNIQUE` (`clave_pregunta`, `clave_seccion`, `codigo`...), esa es la que viaja en el codigo; el id sustituto se resuelve contra la API. Vale para todo el repo, no solo para onboarding.
- **Sospechar de un seed con `INSERT ... SELECT` sin `ORDER BY`** si alguien depende del orden de los ids que genera. `V10` ya corrio y no se edita (D-40); lo que se corrige es la dependencia, no la migracion.
- **Cuando el mismo sintoma vuelve con otro numero, la causa no era el numero.** E-93 arreglo el sintoma y el bug volvio al dia siguiente; la senal de que faltaba mirar mas abajo fue justamente que el "arreglo" tuviera que ser un ±1.

## E-96 — El slider de "Calidad de tu sueño" se iba al minimo al arrastrarlo (2026-09-04)

**Sintoma exacto:** en el Capitulo 2 de la Ficha Inicial ("DESCANSO Y SALUD"), tocar o arrastrar el control de *Calidad de tu sueño* lo tiraba al extremo izquierdo. En la captura del dueno: thumb pegado a la izquierda y valor **2**, cuando el valor por defecto del formulario es 7. Se percibia como "la calidad se reinicia sola".

**Causa real:** `SleepQualitySlider` (`src/features/onboarding/components/ChapterSalud.tsx`) calcula el valor con `evt.nativeEvent.locationX`, y en React Native **`locationX` se mide respecto del elemento que recibio el toque, no del elemento que tiene el `PanResponder`**. El riel tiene tres hijos decorativos (linea base, linea de progreso y el thumb), todos con `pointerEvents` por defecto. Al agarrar el thumb — que es exactamente lo que hace el usuario — el target pasaba a ser el thumb, un cuadrado de 20x20: `locationX` salia medido desde SU borde (~10 en el centro) en vez de desde el inicio del riel.

Aritmetica del fallo, con el valor en 7 y un riel de ~290 px: `round(1 + (10/290)*9) = 1`. Y como el origen quedaba corrido, arrastrar 120 px a la derecha daba 5 en vez de 10. El defecto NO estaba en la formula: estaba en el dato que le llegaba.

**Solucion aplicada:** `pointerEvents="none"` en los tres hijos decorativos, para que el target sea siempre `sliderTrackTouchArea` y `locationX` quede medido contra el riel — que es lo que `calculateValueFromX` siempre asumio. No se toco la aritmetica ni el `PanResponder`.

**Relacion con el bug anterior del mismo control:** el `PanResponder` ya tenia documentado un primer arreglo (el scroll vertical secuestraba el gesto y reiniciaba el valor). Aquel se arreglo bien y sigue vigente; este es un segundo defecto independiente, con la misma consecuencia visible, y por eso parecia que el primero no se habia arreglado.

**Verificado:** `npx tsc --noEmit` en cero y la aritmetica reproducida en una simulacion con el origen equivocado vs. el correcto. **Sin cubrir:** no se probo el arrastre real en el emulador (lo prueba el dueno); el movil no tiene runner de tests configurado.

**Revisado de paso, sin defecto:** `SliderRating` (`ChapterCuerpo`) no usa `PanResponder`, son numeros tocables. `SignatureCanvas` si usa `locationX`, pero su unico hijo es un `<Svg style={StyleSheet.absoluteFill}>` — mismo origen y tamano que el contenedor, asi que las coordenadas coinciden.

**Como evitar que vuelva a pasar:** cuando un `PanResponder` calcula posiciones con `locationX`/`locationY`, **todos los hijos del area tactil deben llevar `pointerEvents="none"`**, salvo que se quiera que sean target. Si un hijo puede recibir el toque y no comparte origen y tamano con el contenedor, las coordenadas van a salir corridas — y el sintoma es un valor que "salta" sin causa aparente, no un error.

## E-97 — La firma del onboarding subia a S3 el texto "File not found" en vez del PNG (2026-09-04)

**Sintoma exacto:** ninguno visible. La app decia "FIRMADO", la fila de `medias_onboarding` se creaba, la respuesta `terms_signature` quedaba apuntando a esa media y el flujo seguia normal. El fallo solo aparece mirando el bucket: los objetos de firma pesaban **14 bytes** y su contenido, en texto plano, era `File not found`. Un objeto anterior (2026-09-03 02:30) era un PNG valido pero de **67 bytes** — un lienzo de tamano cero.

**Como se detecto:** verificando a mano en `s3-renaser90dias` con las credenciales de la configuracion de ejecucion del IDE (`.run/RenaserOsApplication.run.xml`), despues de que el dueno preguntara si la firma se habia guardado. La fila en Postgres existia y todo "parecia" bien.

**Causa real:** `SignatureCanvas.capturarComoPng` usaba `captureRef(ref, { format: 'png', quality: 1 })`, cuyo `result` por defecto es `tmpfile`: devuelve una RUTA de archivo temporal. Esa ruta despues se leia con `fetch(uri).arrayBuffer()` en `subirArchivoOnboardingAS3`. En Android la ruta viene **sin el esquema `file://`**, asi que el `fetch` no la resolvia y devolvia —con status OK, no con error— un cuerpo de 14 bytes con el texto `File not found`. Como `respuesta.ok` era `true` y `arrayBuffer()` no lanzaba, no habia nada que fallara: esos 14 bytes se subian a S3 y despues se registraba la media como si todo hubiera salido bien.

**Por que ninguna de las validaciones existentes lo atrapo:** el codigo ya distinguia "no se pudo leer el archivo local" de "S3 rechazo la subida", pero ambas ramas dependen de que algo LANCE. Acá no lanzaba nada: se leyeron bytes (14) y S3 acepto la subida (200). **Nadie miraba QUE se estaba subiendo.**

**Solucion aplicada (movil):**
1. `capturarComoPng` -> `capturarComoPngBase64`, con `result: 'base64'`. Los bytes vienen directo del modulo de captura, sin pasar por el sistema de archivos ni por `fetch` — se elimina la clase entera de fallo, no solo el sintoma en Android.
2. `subirArchivoOnboardingAS3` recibe base64, lo decodifica y **valida antes de subir** que los bytes empiecen con la cabecera PNG (`PNG


`) y midan al menos 100 bytes (el piso descarta la captura degenerada de 67 bytes). Si no, lanza con el tamano en el mensaje y **no sube nada**. Es lo que convierte "se guardo basura en silencio" en un error visible, que en evidencia con valor probatorio es lo minimo.
3. El decodificador base64 se escribio a mano (ni `atob` ni `Buffer` estan garantizados en React Native).

**Bug encontrado DENTRO del arreglo, al probarlo:** la primera version del decodificador fallaba con relleno. Cuando el largo no es multiplo de 3, el base64 termina en `=`, la limpieza lo descarta y el ultimo grupo queda con 2 o 3 caracteres; `abc.indexOf(undefined)` devuelve **-1** y contamina los bits del ultimo byte. Se detecto porque el round-trip de un PNG real de 67 bytes daba el largo correcto pero **bytes distintos** — con un archivo de largo multiplo de 3 habria pasado la prueba sin problema. Corregido con un `?? 0`. **Leccion:** probar un round-trip de codificacion con UN solo tamano no prueba nada; hay que barrer largos que ejerciten los tres casos de relleno.

**Verificado:** round-trip identico en 11 largos distintos (1, 2, 3, 4, 5, 66, 67, 68, 100, 1023, 35862 bytes), contra el PNG real de 67 bytes recuperado del bucket, y `"File not found"` rechazado por la validacion. `npx tsc --noEmit` en cero. **Sin cubrir:** no se volvio a firmar desde el emulador — lo prueba el dueno; el movil no tiene runner de tests configurado.

**Impacto en datos ya guardados:** las 2 firmas de la cuenta de prueba (`terms_signature`, 2026-09-03 y 2026-09-05) NO tienen PNG utilizable en S3. El trazo vectorial SVG si esta intacto en `medias_onboarding.metadatos` (1169 y 1469 caracteres), asi que la firma es reconstruible; lo que no existe es la imagen. Hay que volver a firmar.

**Como evitar que vuelva a pasar:**
- **Cuando se sube un archivo a un almacenamiento externo, validar el CONTENIDO antes de subir**, no solo que la lectura y el `PUT` no hayan lanzado. Un `fetch` puede devolver 200 con un cuerpo que no es lo que se pidio.
- **Evitar el rodeo por el sistema de archivos cuando existe la opcion de obtener los bytes directo.** `result: 'base64'` no tiene el problema de esquemas de URI entre plataformas que si tiene `tmpfile`.
- **Un `respuesta.ok === true` no significa que se subio lo correcto.** Para evidencia con valor legal, verificar tamano y magic number.

## E-98 — La barra de pestanas se veia BLANCA en modo oscuro (2026-09-04)

**Sintoma exacto:** con la app en modo oscuro, toda la barra inferior (HOY / PLAN / TRAINING / COMUNIDAD / YO) aparecia blanca, y el aro alrededor del boton central dorado tambien. El resto de la pantalla si respetaba el tema. Confirmado con captura del emulador via `adb exec-out screencap`: el pixel del centro de la barra daba **RGB(255,255,255)** exacto.

**Causa real:** `TabBar` pintaba su fondo con `c.cardBg`, que en la paleta oscura es **translucido**: `rgba(255,255,255,0.04)` (`src/theme/tokens.ts`). Ese token esta pensado para apoyarse sobre `c.bg` y dar una tarjeta apenas mas clara que la pagina. Pero la TabBar la dibuja el navegador **fuera del `SafeAreaView` de la pantalla**, asi que detras no habia ningun fondo del tema: estaba la vista raiz de React Native, que es **blanca por defecto**. Un 4% de blanco sobre blanco da blanco puro. El mismo token en `borderColor` explicaba el aro blanco del boton central.

Lo que despisto al principio: se busco en la barra de navegacion del sistema Android (`app.json` no declara `androidNavigationBar` y `userInterfaceStyle` esta en `"light"`). No era eso — `dumpsys window` mostro que esa barra es `fmt=TRANSLUCENT`, o sea que lo blanco lo pintaba la app.

**Solucion aplicada:** en `barOuter`, fondo `c.bg` **opaco** de base y `c.cardBg` como capa encima (`StyleSheet.absoluteFill` con `pointerEvents="none"`), reproduciendo exactamente la composicion "tarjeta sobre pagina" que el token asume. El aro del boton central pasa a `c.bg`, que ademas lo separa del contenido de la pantalla contra el que se recorta por su `marginTop` negativo. **En modo claro no cambia nada visible**: ahi `bg` (#FCFBF9) y `cardBg` (#FDFCFA) son opacos y difieren en 1/255.

**Como evitar que vuelva a pasar:** **un token de color translucido solo es valido sobre un fondo que el tema controle.** Todo componente que se dibuje fuera del arbol de una pantalla —barras del navegador, overlays, modales, portales— tiene que pintar primero un fondo opaco del tema; si no, hereda el blanco de la vista raiz y el modo oscuro se rompe solo en ese componente. Al revisar un color raro en modo oscuro, mirar antes que nada si el token es `rgba(...)` y que hay detras.

## E-99 — Las respuestas de la IA mostraban el markdown crudo (`**negrita**`) en pantalla (2026-09-04)

**Sintoma exacto:** en la burbuja del asistente se leia literalmente `Entra en la seccion **"Plan"**` y `**Sparkie**`, con los asteriscos a la vista. Las listas numeradas quedaban como texto corrido, sin sangria ni alineacion.

**Causa real:** `MensajeBurbuja` renderizaba `{mensaje.texto}` dentro de un `<Text>` plano. Los modelos devuelven markdown basico siempre (negritas, listas numeradas, algun titulo) y nadie lo estaba interpretando.

**Solucion aplicada:** nuevo `features/renasia/components/TextoAsistente.tsx`, que interpreta el subconjunto que los modelos usan de verdad: `**negrita**`/`__negrita__`, `*cursiva*`/`_cursiva_`, `` `codigo` ``, listas ordenadas (`1.`, `1)`), vinetas (`-`, `*`, `•`) y titulos (`#`..`######`). Se aplica **solo a los mensajes del asistente**: lo que escribe la persona se sigue mostrando literal, porque si tecleo asteriscos quiso asteriscos.

**Por que no una libreria de markdown:** las respuestas de este producto son texto corto de chat, no documentos. Un parser completo traeria tablas, HTML embebido, imagenes y enlaces —superficie que no se quiere en una burbuja— y sus estilos por defecto pelearian con la tipografia Jost y la paleta dorada.

**Detalle que se hubiera pasado por alto:** para la negrita no alcanza `fontWeight: '700'`. Con una familia cargada por archivo (Jost via `@expo-google-fonts`), Android ignora el peso si no se nombra la variante: hay que poner `fontFamily: 'Jost_700Bold'`, que ya esta cargada en `App.tsx`.

**Verificado:** el parser se extrajo del archivo real y se ejecuto contra el mensaje exacto de la captura mas casos borde (`10.` para la alineacion de marcadores de dos digitos, vineta con cursiva, titulo `###`, y un `**` sin cerrar). Todo lo que no se interpreta se muestra tal cual — ante un markdown raro se ve el texto original, nunca una burbuja vacia. `npx tsc --noEmit` en cero. **Sin cubrir:** no se vio renderizado en el emulador; el movil no tiene runner de tests configurado.

**Como evitar que vuelva a pasar:** toda superficie que muestre texto generado por un modelo tiene que decidir explicitamente si interpreta markdown o no. El default (`<Text>` plano) no es neutro: muestra los marcadores.

## E-100 — El orden del catalogo de habitos no llegaba a la app, y los habitos de domingo salian todos los dias (2026-09-04)

**Sintoma:** el dueno del producto pidio que los habitos activos salieran en el orden del panel de staging, y que los tres de DOMINGO aparecieran solo los domingos. En la app el orden era arbitrario y los de domingo se veian de lunes a sabado.

**Tres causas distintas, no una:**

1. **`habitos.orden` no lo usaba nadie fuera del panel admin.** El catalogo del movil sale de `findByAmbitoAndActivoTrue(...)`, **sin `ORDER BY`**: Postgres devolvia las filas en orden indefinido. Renumerar la columna sola no habria cambiado nada. Se agrego `...OrderByOrdenAscTituloAsc` en `SpringDataHabitoRepository`.
2. **Empate real en la data.** `Pastilla Renacer` y `POST DIARIO EN COMUNIDAD` compartian `orden = 12`, y el unico consumidor desempataba por `titulo`, que depende del collation. Ademas la numeracion tenia huecos (13 -> 19 -> 29 -> 31). `V28` renumera 1..18.
3. **Los dias de la semana no se exponian.** La base y el backend YA filtraban bien por `horarios_habito.tipo_dia` (`HorarioHabito.aplicaEnDia`), pero el planificador semanal del movil hacia `days: { ...TODOS_LOS_DIAS }` — y con razon: `MiHabitoResponse` no mandaba el dato. Se agrego `TipoDia.diasDeLaSemana()` en el **dominio** y `activeWeekdays` en la respuesta; el movil solo traduce el nombre del dia. Deducirlo del titulo no era opcion: `DESCANSO PROFUNDO` es de domingo y no lo dice, y el titulo es renombrable (lo mismo que descarto V18).

**Dos errores propios que atraparon las pruebas, y valen mas que el arreglo:**

- **`No property 'orden' found for type 'HabitoJpaEntity'` — 385 pruebas caidas.** La consulta derivada nueva no se podia construir porque la entidad JPA no mapeaba la columna. El detalle importante es COMO se mapeo: `@Column(insertable = false, updatable = false)`. El agregado `Habito` no lleva `orden`, asi que `HabitoPersistenceMapper.toEntity` tendria que inventar un valor en cada guardado y —con `@AllArgsConstructor`— ese valor pisaria el de la base en el primer UPDATE, **borrando el orden del catalogo entero**. Es el riesgo que el javadoc de esa clase ya advertia para las columnas no modeladas.
- **Un indice unico agregado y retirado el mismo dia (V28 -> V29).** Se creo `habitos_catalogo_orden_unico_idx` como red contra futuros empates. La suite lo rechazo: la base de pruebas trae el catalogo real con 1..18 **ya ocupados**, asi que cualquier fixture que sembrara un habito de SISTEMA chocaba — 10 clases lo hacen, casi ninguna para probar el orden. **Leccion:** una restriccion global sobre una tabla sembrada por migraciones le cobra peaje a cada fixture presente y futuro; si el valor lo fijan las migraciones y no hay caso de uso que lo escriba, el lugar de esa validacion es el caso de uso (cuando exista el panel admin), no un indice.

**El desempate lo decidi mal la primera vez.** `V28` separo 12/13 usando la hora de disparo (07:00 la Pastilla, 22:00 el Post) porque no habia criterio registrado. Al entrar despues al panel de staging —el catalogo curado a mano, que es la fuente de verdad— el orden coincidia en los 18 items **salvo en esas dos posiciones**: el Post va antes. `V30` lo corrige. Era una inferencia razonable, pero era mia y no la decision del producto: **cuando existe una fuente de verdad, se consulta antes de inferir.**

**Verificado:** `./mvnw clean test` -> **2328 pruebas, 0 fallos**. `npx tsc --noEmit` en cero. Los 18 activos de la base de desarrollo coinciden uno por uno con el panel de staging.

## E-101 — El orden del catalogo no llegaba al Plan, y la pausa no sobrevivia a recargar (2026-09-04)

**Sintoma 1:** con `habitos.orden` ya corregido (E-100) y el backend devolviendo bien los 18 en orden, el Plan del movil los seguia mostrando en otro orden.

**Causa:** el orden se descartaba DOS veces en el movil. `usePlanHabitos.ts` hacia `.sort((a,b) => a.time.localeCompare(b.time))` al cargar, y `PlanScreen.tsx` volvia a ordenar por hora dentro de cada franja. Lo segundo era **D-86**, una decision deliberada para que la seccion NOCHE no se leyera 22:30, 18:00, 21:00.

**Solucion:** se respeta el orden del catalogo (`ordenCatalogo`, tomado del indice del array que ya viene ordenado del backend). **Revierte D-86 a pedido del dueno del producto**, y queda documentado en el codigo. En la practica casi no se pierde la lectura cronologica —NOCHE queda 18:00, 21:00, 21:30, 22:00, 22:30— y arregla un caso que el orden por hora empeoraba: `DESPERTAR` no tiene horario y caia al FINAL de la mañana.

**Sintoma 2, encontrado al revisar el pedido de "elegir a que dias aplica":** el interruptor ACTIVO/PAUSADO no sobrevivia a recargar la app. D-87/V23 habian agregado la persistencia (`desbloqueos_habito.pausado_en`), pero **ninguna respuesta de lectura exponia el estado**: `GET /habits` no lo trae y `GET /habit-unlocks` devolvia solo `habitId/unlockDay/chosenAt`. Se persistia y no se leia de vuelta. Se agregaron `paused` y `pausedUntil` a `HabitUnlockItemResponse`.

**Funcionalidad nueva (V31): pausa CON FECHA DE FIN.** `desbloqueos_habito.pausado_hasta date`. Semantica: `pausado_en` NULL = activo; con valor y `pausado_hasta` NULL = pausa indefinida (V23, sin cambios); con fecha = pausado hasta ese dia INCLUSIVE.

**Por que rango de fechas y NO "dias de la semana"** (se evaluaron las dos, decision del dueno sobre recomendacion): un patron semanal ("nunca los sabados") crea un agujero PERMANENTE y silencioso en un programa de 90 dias, y duplicaria una regla que ya existe — que dias aplica un habito lo decide el catalogo via `horarios_habito.tipo_dia`, expuesto como `activeWeekdays`. Dos fuentes respondiendo "¿va hoy?" es la duplicacion que CLAUDE.MD prohibe. El rango se cura solo.

**La propiedad que sostiene todo esto: la reanudacion se DERIVA, no se ejecuta.** `DesbloqueoHabito.estaPausadoEl(fecha)` compara contra el calendario del aprendiz; no hay cron que "despause". Una pausa hasta el domingo termina el domingo aunque el backend haya estado caido toda la semana — misma regla que `.claude/rules/02` (derivar, no acumular). La fecha se evalua SIEMPRE en la zona del participante, nunca con la del servidor (E-91), y por eso la columna es `date` y no `timestamptz`.

**El choque que el dueno anticipo, y como se evito:** el dialogo aparece **solo al pausar**; reactivar sigue siendo un toque. El interruptor conserva un unico significado (encendido/apagado) y la fecha solo agrega "hasta cuando" al apagarlo.

**Bug encontrado probando por HTTP, que ninguna prueba habria atrapado:** al agregar el 4to componente al record `CambiarEstadoHabitoCommand`, la llamada a `SelfValidating.validateConstructorArgs(...)` seguia pasando 3 argumentos. Hibernate Validator responde **400** con `HV000181: Wrong number of parameters ... expects 4 parameters, but got 3`. Compila perfecto: la firma es varargs. **Al agregar un componente a un record que use `validateConstructorArgs`, hay que agregarlo tambien a esa llamada.**

**Verificado:** `./mvnw clean test` -> **2335 pruebas, 0 fallos** (7 nuevas de `DesbloqueoHabitoPausaTest`, incluida la que fija que el habito vuelve solo al dia siguiente). `npx tsc --noEmit` en cero. Flujo completo por HTTP contra el backend real: PUT al plan -> PATCH `{"active":false,"pausedUntil":"2026-09-06"}` -> 200 con `paused:true, pausedUntil:"2026-09-06"` -> la fila en `desbloqueos_habito` lo confirma -> reactivar limpia las dos columnas.

**AISLAMIENTO (pregunta explicita del dueno): la pausa es SOLO del aprendiz que la hace.** `desbloqueos_habito` tiene PK `(participante_id, habito_id)` y el endpoint es self por construccion — `@ActorAutenticado UserId actor` mas el id del HABITO, sin id de aprendiz en la ruta: no hay forma de pausarle un habito a otro. Lo que SI es global es `habitos.orden` (el catalogo compartido, que es lo que se pidio: "orden fijo para todos") y `activeWeekdays`, que se deriva del catalogo y nadie escribe desde la app.

---

## E-102 — El chat mostraba fotos y audios que nunca se enviaban (2026-09-05)

**Sintoma:** en Comunidad → un chat abierto, tocar el boton de la camara o el del microfono agregaba
una burbuja a la conversacion que se veia enviada (con su doble check dorado). No llegaba a nadie, y
desaparecia al recargar la app. El boton "GIF" hacia lo mismo con un emoji grande.

**Sintoma 2, el que reporto el dueno:** el boton flotante del acompanante IA se montaba justo encima
de la barra de escribir y tapaba el boton de enviar.

**Causa real — y no era del cliente.** `chat` era el **unico modulo del backend sin endpoint de
subida**. Todos los demas lo tienen (`community`, `rocks`, `habits`, `onboarding`, `calendar`,
`phasecontracts`, `support`, `users`), y `EnviarMensajeRequest` aceptaba `mediaBucket`/`mediaPath`
desde el primer dia — pero no habia ningun `upload-url` de donde sacar esas dos referencias. El
cliente lo tenia documentado como hueco en `chatApi.ts` y, para que los botones del diseno no
quedaran muertos, fabricaba una burbuja local con contenido inventado: `'Evidencia_1.jpg'`,
`audioDuration: '0:28'` y el emisor fijo `'Kelin Arango'`. Compilaba, se veia bien y no era real.

**Segunda mitad de la causa, que no se ve hasta arreglar la primera:** aunque se hubiera podido
subir, `MensajeResponse` devolvia `mediaPath` — la clave del objeto en S3 —, no una URL abrible. Una
`<Image>` con esa clave no muestra nada. Faltaban las dos piezas, no una.

**Solucion:**
1. `POST /api/v1/chat/conversations/{id}/media/upload-url` (`ChatMediaController` +
   `SolicitarUrlSubidaMediaChatUseCase`), con el patron de 3 pasos ya establecido en el repo:
   firmar → `PUT` directo a S3 → crear el mensaje. Ruta `chat/{conversacionId}/{fotos|audios|
   videos}/{uuid}`. Rechaza cualquier MIME que no sea `image/`, `audio/` o `video/` **antes** de
   firmar, para no dejar un objeto huerfano que ningun mensaje podria referenciar.
2. `MensajeResponse.mediaUrl`, firmada en cada lectura con `AlmacenamientoPort.firmarLectura` y
   **nunca persistida**: una URL firmada vence, y guardarla deja la foto en 403 para siempre. Es
   exactamente el defecto E-79, ya cometido en el Muro.
3. La autorizacion del `upload-url` se repite entera (activo + conversacion existente +
   participante) en vez de delegarse a `enviar`: la URL se firma **antes** de que exista el mensaje,
   asi que sin ese chequeo cualquiera con sesion podria firmar subidas contra el prefijo de una
   conversacion ajena.
4. GIF retirado por completo (tipo, opciones, modal, burbuja y estilos): no existia del lado del
   backend, `tipo_mensaje` no tiene ese valor y nunca lo iba a tener.
5. El flotante se esconde con la sala de chat abierta, reusando la senal que ya existia para el chat
   de curso — se generalizo `chatDeCursoVisible.ts` a `chatEnPantalla.ts` en vez de duplicar el
   mecanismo (el contador ya soportaba varios emisores).

**Como evitar que vuelva a pasar.** El patron a reconocer es *"la UI tiene un control para algo que
el backend no puede recibir"*. Cuando un boton del diseno no tiene endpoint detras, el reflejo no es
simular la respuesta en el cliente: es **dejarlo deshabilitado o no dibujarlo**, y anotar el hueco.
Una burbuja falsa se ve igual que una real en una demo, sobrevive meses y se descubre cuando alguien
pregunta por que su foto no le llego a nadie. El comentario en `chatApi.ts` estaba bien escrito y
decia la verdad — lo que fallo fue que la pantalla, al lado, hacia como si nada.

**Verificado:** 75 pruebas del modulo `chat` en verde, 6 nuevas sobre el `upload-url` (prefijo por
conversacion, ruta distinta en cada llamada, rechazo de MIME no soportado, y las dos de autorizacion
negativa que exige `.claude/rules/03`). `npx tsc --noEmit` en cero.

**SIN VERIFICAR, y es lo que falta:** `STORAGE_PROVEEDOR=s3` no esta configurado en este entorno, asi
que **el camino real de subida nunca se ejercito contra S3**. Con el adaptador NoOp el `uploadUrl`
sale como `about:blank#pendiente-s3/...`; el cliente lo detecta antes de intentar el `PUT` y avisa
("El almacenamiento del servidor no esta configurado") en vez de reventar con un error de red
criptico, pero eso es el camino degradado, no el bueno. Mandar una foto de punta a punta queda
pendiente de esa variable (D-34).

---

## E-103 — `JAVA_HOME` de las reglas apunta a un JDK que no existe (2026-09-05)

**Sintoma:** `.claude/rules/03-pruebas.md` dice que `JAVA_HOME` debe apuntar a
`C:\Program Files\Java\jdk-25.0.2`. Ese directorio **no existe**: `Get-ChildItem "C:\Program
Files\Java"` responde

```
Cannot find path 'C:\Program Files\Java' because it does not exist.
```

**Causa:** el JDK instalado es Eclipse Temurin y vive en
`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`. La regla quedo con la ruta de una
instalacion anterior. `CLAUDE.md` §12 ya nombra bien la version ("Eclipse Temurin JDK 25.0.4.1 LTS")
pero no la ruta, asi que la unica ruta escrita en el repo era la incorrecta.

**Solucion:** corregida la ruta en `.claude/rules/03-pruebas.md`.

**Como evitar que vuelva a pasar:** es una tonteria de entorno y por eso mismo se repite — quien la
lea de nuevo va a perder los mismos minutos. Si el JDK se reinstala en otro lado, se corrige la regla
en el mismo momento, no "despues".

---

## E-104 — `Truncated class file`: dos builds de Maven sobre el mismo `target/` (2026-09-05)

**Sintoma:** `./mvnw clean test` termina en **BUILD FAILURE con 0 pruebas ejecutadas**. El error, literal:

```
[ERROR] Truncated class file
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.6:test
        (default-test) on project renaser-backend:
[ERROR] There was an error in the forked process
[ERROR] org.apache.maven.surefire.booter.SurefireBooterForkException: There was an error in the
        forked process
```

No hay ni un nombre de prueba en la salida, ni un fallo de assertion: el proceso hijo de surefire
muere antes de arrancar.

**Segundo sintoma de la MISMA causa**, que aparece si el otro build ya tiene el directorio tomado:

```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-clean-plugin:3.5.0:clean
        (default-clean) on project renaser-backend: Failed to clean project:
        Failed to delete C:\Users\Usuario\Documents\renaser-backend\renaser-backend\target
```

En Windows esto es literal: el otro JVM tiene archivos abiertos dentro de `target/` y el sistema no
deja borrar el directorio. Es la misma colision en un instante distinto — el primer sintoma es "te
borre las clases mientras las leias", el segundo es "no puedo borrar porque vos las tenes abiertas".

**Causa: no es el codigo.** Habia **dos builds corriendo a la vez sobre el mismo `target/`** — una
sesion trabajando en `chat` y un agente en paralelo trabajando en `onboarding`, los dos sobre el
mismo checkout. El `clean` de uno borra los `.class` que el otro esta leyendo, y surefire levanta un
JVM con un classpath a medio escribir. De ahi el "Truncated class file": el archivo existe pero esta
cortado.

**Solucion:** esperar a que el otro build termine y volver a correr. No hay nada que arreglar en el
codigo — el mismo comando pasa solo, sin cambios, en cuanto no hay concurrencia.

**Tercer sintoma, y este NO necesita dos agentes — te pasa con la app corriendo desde el IDE.**
Levantada la app y corriendo `./mvnw clean test` en paralelo, el arranque muere con:

```
org.springframework.beans.factory.BeanCreationException: Error creating bean with name
'meterRegistryPostProcessor' ...
Caused by: java.lang.IllegalArgumentException: No classes found in packages [com.renaser.os]!
    at org.springframework.modulith.core.ApplicationModules.<init>(ApplicationModules.java:134)
```

Parece un problema de configuracion de Modulith y no lo es: el `clean` borro `target/classes`, y
Spring Modulith escanea el classpath al arrancar. Encontro la carpeta vacia y concluyo que no hay
modulos. Con devtools, cada restart lo reintenta y vuelve a fallar hasta que alguien recompile.

**La pista que lo delata:** el mensaje dice "no hay NINGUNA clase en `com.renaser.os`". Un problema
real de Modulith se queja de un modulo concreto o de una dependencia entre dos; que no encuentre
*nada* solo pasa si el directorio esta vacio.

Ocurre en las dos direcciones: la app del IDE compilando contra `target/` rompe el build de Maven
(sintomas 1 y 2), y el `clean` de Maven rompe la app del IDE (este). Es el mismo recurso compartido.

**Cuarto sintoma, y es el mas enganioso de los cuatro.** Si el `clean` cae justo mientras Maven
esta recompilando, la app arranca con `target/classes` a medio llenar y falla nombrando UNA clase:

```
Caused by: java.lang.NoClassDefFoundError:
    com/renaser/os/academy/domain/model/curso/TipoVideoLeccion
Caused by: java.lang.ClassNotFoundException: ...TipoVideoLeccion
    at RestartClassLoader.loadClass(RestartClassLoader.java:123)
```

Parece un modulo mal armado o una dependencia rota — el nombre concreto invita a ir a buscar ESA
clase. No es eso: la clase existe en el fuente y aparece en `target/` un segundo despues.

**La verificacion que distingue los cuatro sintomas de un problema real, en un comando:**

```bash
ls src/main/java/com/renaser/os/.../LaClaseQueFalta.java   # si existe en el fuente...
ls target/classes/com/renaser/os/.../LaClaseQueFalta.class  # ...y no en target, es esto
```

Si el fuente la tiene, no hay nada roto: hay un build corriendo. Antes de arrancar la app:

```bash
ls target/classes/com/renaser/os/RenaserOsApplication.class >/dev/null 2>&1 \
  && echo "LIBRE" || echo "OCUPADO - hay un build corriendo"
```

**Como evitar que vuelva a pasar.** `target/` es un recurso compartido de todo el checkout, no del
modulo en el que uno esta trabajando. Dos agentes sobre el mismo repo **no pueden compilar a la
vez**, aunque toquen modulos que no se cruzan.

- Al repartir trabajo entre varios agentes en este backend, o se coordina quien corre `mvnw`, o cada
  uno trabaja en su propio **git worktree** (checkout separado = `target/` separado). El worktree es
  la solucion real; la coordinacion se olvida.
- El sintoma es facil de confundir con un problema del codigo, porque dice ERROR en rojo y falla el
  build entero. La senal que lo distingue es **"0 pruebas ejecutadas"**: un problema real de codigo
  falla DESPUES de correr pruebas, y nombra cual.

**Quinto sintoma, la version masiva, vista el 2026-09-05 al montar el CI (D-114).** El mismo
choque, pero a escala, y por eso es el mas alarmante de todos: `./mvnw clean verify` termina con

```
[ERROR] Tests run: 2086, Failures: 36, Errors: 349, Skipped: 0
```

**349 errores repartidos por TODOS los modulos** — `habits`, `rocks`, `rag`, `onboarding`, `users`,
`points`, `support`, `notifications`, `phasecontracts` —, incluidas pruebas de dominio puro que no
tocan infraestructura. Parece que alguien rompio el proyecto entero. Contando por tipo:

```
305 java.lang.NoClassDefFoundError
223 java.lang.ClassNotFoundException
164 org.mockito.exceptions.base.MockitoException
```

y las clases "que faltan" son cosas tan basicas como `RegistroRadarId`, `SemanaPrograma`,
`PorcentajeHabitos` o `CatalogoHerramientasAgente`.

**La verificacion que lo cierra en dos comandos**, y que distingue esto de una rotura real: al
terminar el build, la clase esta **en el fuente Y en `target/`**.

```bash
ls src/main/java/com/renaser/os/habits/domain/model/radar/RegistroRadarId.java   # existe
ls target/classes/com/renaser/os/habits/domain/model/radar/RegistroRadarId.class # existe tambien
```

Si las dos existen despues de la corrida, no falta ninguna clase: **faltaba mientras surefire la
buscaba**, porque otro build estaba reescribiendo `target/` en ese momento. En esa maquina habia,
ademas de la app levantada desde IntelliJ, otros dos `mvnw` que arrancaron mientras este corria.

**Contraprueba, para no dejarlo en hipotesis:** la misma revision del codigo, compilada sobre una
copia aislada del checkout (`target/` propio, sin nadie mas escribiendo), dio **2421 pruebas con 3
fallos** — y esos 3 son E-126, un bug real de zona horaria. De 349 errores a 0 sin tocar una linea
de codigo.

**La leccion practica:** ante un numero de errores absurdamente grande y repartido por modulos que
no tienen nada que ver entre si, la primera hipotesis no es el codigo, es el `target/` compartido.
Un error real se concentra; una colision de builds se esparce.

## E-105 — La pantalla de habitos se apagaba todas las noches: `GET /habit-tracks/today` pedia el dia del servidor (2026-09-05)

**Sintoma:** a partir de las 19:00 hora de Lima, `GET /api/v1/habit-tracks/today` devuelve **`[]`**
para un aprendiz que si tiene habitos generados. En la app, la pantalla de Training entra en su
estado vacio ("No pudimos cargar" no: *vacio*, que es peor porque parece correcto). A la manana
siguiente vuelve solo. No hay ningun error en el log, ningun 4xx, ninguna excepcion.

**Causa real.** `HabitTrackController.hoy` resolvia el dia asi:

```java
return consultarTracksDelDiaUseCase.consultar(actor, actor, LocalDate.now())
```

`LocalDate.now()` es la fecha del **servidor**. Con el proceso en UTC y el padron en
`America/Lima` (UTC-5, el default de `participantes_programa.timezone`), a partir de las 19:00
locales el servidor ya esta en el dia siguiente. La consulta salia con la fecha de MANANA,
`registros_habito` no tiene ninguna fila para ese dia todavia, y la red de seguridad de
`TracksDelDiaProyeccionService` no ayudaba: genera los tracks para el dia del participante (hoy) y
vuelve a consultar por el del servidor (manana), asi que devolvia vacio igual.

Es exactamente la misma familia que **E-91** — "el reloj del servidor no es el reloj del aprendiz"
— en otro lugar del codigo. E-91 se arreglo en el scheduler y quedo la regla escrita
(`.claude/rules/02-tiempo-zonas-y-schedulers.md`), pero nadie audito los **controllers** buscando el
mismo patron.

**Solucion.** La decision "que dia es hoy para esta persona" se movio del adaptador de transporte al
caso de uso: `ConsultarTracksDelDiaConCatalogoUseCase.consultarHoyDe(participanteId)`, que resuelve
la fecha con `clock.now().atZone(zona del participante).toLocalDate()`. El controller quedo tonto de
nuevo, que es lo que la regla 01 pide.

**Como evitar que vuelva a pasar.**

- Test de regresion: `TracksDelDiaPuntosEnJuegoTest.consultaElDiaDelAprendizYNoElDelServidor`, con
  el reloj fijado a las **01:50 UTC** (20:50 del dia anterior en Lima). Verifica que se consulte el
  dia del aprendiz y **nunca** el del servidor. Contra el codigo viejo falla.
- La senal general, ya escrita en la regla 02 y que ahora tiene un segundo caso real:
  **`LocalDate.now()` y `clock.today()` no sirven para responder "que dia es hoy para un usuario"**.
  Si el dato depende de una persona, la fecha sale de su zona. Buscar `LocalDate.now()` en
  `adapter/in/` es una auditoria de diez minutos que conviene repetir.
- El sintoma no grita: **devolver lista vacia se ve igual que "no tiene nada"**. Un bug de zona casi
  nunca falla ruidosamente; se manifiesta como datos que faltan en una franja horaria y aparecen
  solos al otro dia. Si alguien reporta "de noche no me aparece", la primera hipotesis es la zona.

---

## E-106 — La misma familia de E-105 en otros tres lugares, y el candado que la cierra (2026-09-05)

**Contexto:** al cerrar E-105 el dueno pidio buscar el mismo defecto en el resto del backend. Su
memoria era que ya lo habia arreglado — lo que habia arreglado era **E-91**, el reloj del dia de
programa (commit `b3f3b10`). Es la misma causa en sitios distintos, y quedaban tres vivos.

**Sintoma comun:** codigo que pregunta "que dia es hoy" al proceso, que corre en UTC, cuando el
padron vive en `America/Lima` (UTC-5). Entre las **19:00 y la medianoche hora local** —cinco horas
todas las noches— "hoy" del servidor ya es manana.

**1. `ParticipacionPrograma.activarSeguimientoPersonal` y `inscribirTraineeAprobado`.** La
`fechaInicio` salia de `clock.today()`. Un aprendiz aprobado de noche arrancaba con la fecha
corrida un dia. **Lo grave es que ya no se disimula:** desde que `diaPrograma` se DERIVA de esa
fecha (D-98), el corrimiento se arrastra los 90 dias. La zona ya estaba ahi mismo
(`ZONA_POR_DEFECTO`), asi que el arreglo es un helper de una linea.

**2. `RankingController`.** Sin `fecha` explicita usaba `clock.today()`, asi que de noche pedia el
ranking de MANANA — cuyo snapshot no existe, porque `SnapshotRankingScheduler` corre a las 05:05
UTC. El ranking se veia vacio todas las noches. Se resuelve en la zona del PADRON y no en la del
actor: el ranking es una tabla comun, y si cada uno lo pidiera en su huso, dos personas de la misma
celula verian rankings de dias distintos.

**3. `ControlCuotaRedisAdapter`.** La clave diaria y el vencimiento se armaban contra la medianoche
UTC, asi que la cuota de Renasia se renovaba a las 19:00 hora local: quien la agotaba a la tarde la
recuperaba entera esa misma noche.

**Los schedulers NO se tocaron, y esta bien:** `ExpirarRegistros` (05:00 UTC), `SnapshotRanking`
(05:05) y `PromoverCambiosHorario` (04:40) estan alineados a proposito con la medianoche de Lima y
lo documentan en su propio codigo. Ahi `clock.today()` es correcto.

**Como evitar que vuelva a pasar — y esta vez es ejecutable.** Regla nueva en `ArchitectureTest`:
`adaptersDeEntradaNoUsanLaFechaDelServidor` prohibe `LocalDate.now()` y `LocalDateTime.now()` en
cualquier clase de `..adapter.in..`. **Verificada reintroduciendo el defecto a proposito**: con
`LocalDate.now()` de vuelta en `RankingController` el build falla nombrando esa clase, y sin el
pasa. Es un test de regresion real, no decoracion.

**El patron a reconocer, para la proxima:** el `@Scheduled` lo piensa todo el mundo — al escribir un
cron uno se pregunta "¿a que hora corre esto?". El `@GetMapping` no lo piensa nadie, porque "hoy"
parece obvio. Los cuatro defectos de esta familia estaban en codigo que responde a una peticion, no
en los crons.

**Verificado:** `./mvnw clean test` -> **2395 pruebas, 0 fallos**.

---

## E-107 — La app no arrancaba con 2403 pruebas en verde: `ObjectMapper` inyectado (2026-09-05)

**Sintoma:** la suite completa paso (2403 pruebas, 0 fallos) y la aplicacion murio al arrancar:

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 2 of constructor in com.renaser.os.rag.infrastructure.adapter.out.ia
.GoogleGenAiRenasiaChatAdapter required a bean of type
'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
```

**Causa:** al cablear el tool calling se agrego `ObjectMapper` como parametro de constructor del
adaptador. Ese bean **no existe en este contexto**, y ya estaba documentado como **E-33**: Spring
Boot 4.1 autoconfigura el `ObjectMapper` de **Jackson 3** (`tools.jackson.databind.ObjectMapper`),
no el clasico `com.fasterxml` que usa este codigo. Las otras seis clases del repo que serializan
JSON —`RedisChatPublisher`, `PgVectorNativoAdapter`, `EventoRenasiaSseMapper` y companiia— se
construyen el suyo y lo dicen en su javadoc. Esta se salio del patron.

**Solucion:** `this.json = new ObjectMapper()` dentro del adaptador, como el resto.

**Por que 2403 pruebas no lo atraparon — esto es lo importante.** El adaptador real es
`@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "google")`, y **las pruebas
corren con `noop`**: el bean nunca se instanciaba, asi que cualquier error de inyeccion en el
adaptador real era invisible hasta levantar la app a mano. La suite verde no era falsa, era ciega
en ese camino.

**Como evitar que vuelva a pasar.** Se agrego `GoogleGenAiRenasiaChatAdapterContextTest`: un
`ApplicationContextRunner` que enciende `renaser.ia.proveedor=google`, ofrece solo lo que el
contexto real ofrece (`ChatModel` y `EjecutarHerramientaAgenteUseCase`) y **deliberadamente no
registra ningun `ObjectMapper`**. Resuelve el constructor de verdad. Verificado reintroduciendo el
defecto: falla con el mismo mensaje que mostro la app.

**La leccion que se generaliza:** todo bean detras de un `@ConditionalOnProperty` que las pruebas
dejan apagado es un hueco de cobertura con forma de suite en verde. Si un componente solo existe en
produccion, necesita al menos una prueba que lo construya con la propiedad encendida — si no, lo
unico que verifica que arranque es levantar la app.

---

## E-108 — El tool calling ejecutaba bien y moria al devolver el resultado (2026-09-05)

**Sintoma:** el acompanante contestaba `"No pude responder en este momento"` a cualquier pregunta.
Sparkie (el tutor de cursos) funcionaba perfecto con la misma clave y el mismo modelo. En el log:

```
java.lang.RuntimeException: Failed to parse JSON: id=93ef82a1-... | ULTIMA COMIDA DEL DIA |
estado=PENDIENTE | puntos_en_juego=10 de 10 | vence=2026-09-06T02:10:00Z ...
    at GoogleGenAiChatModel.parseJsonToMap(GoogleGenAiChatModel.java:368)
    at GoogleGenAiChatModel.messageToGeminiParts(GoogleGenAiChatModel.java:311)
Caused by: StreamReadException: Unrecognized token 'id': was expecting (JSON String, Number, ...)
```

**Como se aislo, y vale la pena recordarlo:** COMPANION lleva herramientas, COURSE_TUTOR no (D-102).
Probar los dos agentes con la misma infraestructura dejo la causa en un solo lugar sin leer una
linea de codigo. Cuando dos caminos comparten todo menos una variable, esa variable es el
experimento.

**Causa:** el texto del error ERA la respuesta correcta de la herramienta — los habitos reales del
aprendiz, con sus puntos y sus vencimientos. O sea que el modelo pidio la herramienta, el actor se
resolvio, la consulta salio bien. Lo que fallaba era el ULTIMO paso: Gemini modela la respuesta de
una funcion como un `Struct`, y el adaptador de Spring AI hace `parseJsonToMap(...)` sobre lo que
devuelve `ToolCallback.call(...)`. Nuestro callback devolvia texto plano.

**Solucion:** `HerramientaToolCallback.call` devuelve `{"ok": <bool>, "resultado": "<texto>"}`,
serializado con Jackson (el contenido lleva saltos de linea, tildes y comillas). El `ok` deja que
el modelo distinga "esto es lo que pediste" de "no se pudo, explicaselo".

**Como evitar que vuelva a pasar:** la prueba `elResultadoVuelveComoObjetoJsonPorqueGeminiLoParsea`
**parsea la salida como JSON** en vez de compararla como texto. Devolver texto plano vuelve a
romper el build, no la conversacion.

**La leccion que se generaliza:** el contrato de una herramienta con el modelo no termina en
ejecutarla. El formato de la RESPUESTA es parte del contrato, y es la mitad que ninguna prueba
unitaria del dominio ve — porque del lado del dominio devolver un `String` es perfectamente valido.

---

## E-109 — Los avisos de habitos publicaban CERO, cada cinco minutos, en silencio (2026-09-05)

**Sintoma:** en el log, cada corrida del barrido:

```
ERROR: value too long for type character varying(255)
[habits.DespacharAvisosHabitoScheduler] no se pudieron despachar los avisos de 1111...:
  DataIntegrityViolationException: could not execute statement
  [insert into event_publication (... serialized_event ...) values (...)]
[habits.DespacharAvisosHabitoScheduler] 0 aviso(s) publicado(s), 8 participante(s) fallido(s) de 18
```

**La funcion entera de avisos no publico nunca nada.** Desde la app no se veia absolutamente nada:
el barrido captura por participante y sigue, como manda `.claude/rules/02`. Eso esta BIEN —un
aprendiz que falla no puede detener a los otros 17— pero significa que un fallo total se ve igual
que un dia sin avisos. Solo aparecia en el log.

**Causa, y es vieja:** `V2` creo el outbox de Spring Modulith con `VARCHAR(255)` en
`serialized_event`, `listener_id` y `event_type`. El esquema OFICIAL de Modulith usa `TEXT` en las
tres. Durante meses no molesto porque los eventos existentes serializan a ~108 caracteres (medido).
`AvisoHabitoDebidoEvent` tiene ocho campos —dos UUID, el id del participante, el titulo del habito,
el tipo de aviso, los minutos, los puntos y el instante— y su JSON pasa comodo los 255.

**Solucion:** `V32` lleva las tres columnas a `TEXT`. No a un VARCHAR mas grande: elegir 512 solo
mueve la fecha del proximo desbordamiento, y en Postgres `TEXT` y `VARCHAR(n)` tienen el mismo
rendimiento y el mismo almacenamiento — el limite no compra nada, solo agrega una forma de fallar.

**Como evitar que vuelva a pasar.** Dos cosas, y la segunda es la importante:

1. Al agregar un evento de dominio nuevo, recordar que viaja SERIALIZADO por el outbox. Un evento
   con muchos campos o con texto libre (un titulo, un mensaje) es el que rompe el limite.
2. **Un barrido que falla en el 100% de los casos deberia gritar distinto que uno que falla en
   uno.** Hoy `0 publicado(s), 8 fallido(s) de 18` sale en INFO igual que `8 publicado(s), 0
   fallido(s)`. Un WARN cuando no se publico NADA habiendo trabajo que hacer habria puesto esto a
   la vista el primer dia. Queda anotado como mejora pendiente del scheduler.

**Verificado:** `./mvnw clean test` -> **2405 pruebas, 0 fallos**. La migracion no se ejercito
todavia contra la base local — corre al proximo arranque de la app.

---

## E-110 — El acompanante desaparece de TODA la app al salir del chat por la barra de pestanas (2026-09-05) — **ABIERTO**

**Sintoma:** el boton flotante de Renasia deja de aparecer en Hoy, Plan, Training y Yo. No vuelve
solo. Reproducido en vivo el 2026-09-05.

**Como reproducirlo:** Comunidad -> Entorno Renaser -> Chats Comunidad -> Global -> y salir tocando
otra pestana de abajo (NO la flecha de volver). El flotante desaparece de todas las pantallas.

**Como recuperarlo mientras tanto:** volver a Comunidad y salir del chat con la flecha `←`.

**Causa — es una regresion introducida hoy con D-106.** La senal `chatEnPantalla` se generalizo para
que el flotante se esconda cuando hay una sala de chat abierta. En `ComunidadScreen` se enciende asi:

```java
useEffect(() => {
  if (!inAtencionPersonalizada || activeChat === null || groupInfoVisible) return;
  return marcarChatMontado();
}, [inAtencionPersonalizada, activeChat, groupInfoVisible]);
```

El efecto es correcto; el problema es la premisa. **`ComunidadScreen` NO se desmonta al cambiar de
pestana** — el navegador de tabs la mantiene viva. Al irse por la barra de abajo, `activeChat` sigue
apuntando a la conversacion, la limpieza nunca corre, y el contador queda en 1 para siempre.

**Por que no paso antes:** la senal la usaba solo `ChatDelCurso`, un componente que SI se desmonta
al salir del curso. Ahi el ciclo de vida del componente y "hay un chat en pantalla" coincidian. Al
reusarla para Comunidad esa equivalencia se rompio, porque la condicion pasa a depender de ESTADO
en una pantalla que nunca muere.

**Y es el camino natural:** nadie sale de un chat con la flecha; se toca otra pestana.

**Arreglo propuesto (no aplicado):** la condicion tiene que ser "hay un chat abierto **Y** esta
pantalla esta enfocada". React Navigation expone `useIsFocused()` justamente para esto. Es agregar
esa condicion al efecto.

**La leccion, que vale mas que el arreglo:** al reusar una senal atada al ciclo de vida de un
componente, hay que verificar que el componente nuevo tenga el MISMO ciclo de vida. Un
`useEffect` con limpieza solo se apaga si el componente se desmonta o si cambian sus dependencias —
y en un navegador de pestanas, salir de una pantalla no es ninguna de las dos cosas.


---

## E-111 — `./mvnw clean test` "pasa" sin ejecutar una sola prueba, y devuelve exit 0 (2026-09-05) — **RESUELTO**

**Sintoma exacto**, al final de la salida de Maven:

```
/c/Users/panc1/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn: line 93: cd: /c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot: No such file or directory
The JAVA_HOME environment variable is not defined correctly,
this environment variable is needed to run this program.
```

**Lo grave no es el mensaje: es que el proceso termina con `exit code 0`.** Un script de CI, un
hook, o un agente que solo mire el codigo de salida da la tarea por probada. Ninguna prueba corrio.

**Causa real:** la ruta de `JAVA_HOME` que documentaba `.claude/rules/03-pruebas.md` no existe en
esta maquina. El JDK 25 real esta en `C:\Program Files\Java\jdk-25.0.2`. La regla decia
`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`, y ademas afirmaba que E-103 habia
corregido la ruta *desde* `C:\Program Files\Java\jdk-25.0.2` — es decir, **E-103 cambio la ruta que
funcionaba por una que no existe**. No hay carpeta `Eclipse Adoptium` bajo `C:\Program Files\`.

**Solucion aplicada:** `.claude/rules/03-pruebas.md` vuelve a `C:\Program Files\Java\jdk-25.0.2`,
verificado con `ls` y con `"$JAVA_HOME/bin/java" -version` (responde `25.0.2+10-LTS-69`). Con la
ruta correcta: **2407 pruebas, 0 fallos, BUILD SUCCESS, 4:15 min.**

**Como evitar que vuelva a pasar:** no alcanza con mirar el exit code de `mvnw`. La verificacion
tiene que ser sobre la **linea `Tests run:`** de la salida — si no aparece, no se corrio nada,
diga lo que diga el codigo de salida. Antes de confiar en una ruta de JDK documentada, `ls`.

**Leccion general:** una correccion documentada tambien puede estar equivocada. E-103 se escribio
con la forma de una correccion ("aca decia X, era Y") y por eso nadie la volvio a mirar durante
dos dias, mientras la orden obligatoria del repo —correr las pruebas— se cumplia en falso.

---

## E-112 — La Clase Diaria se vuelve imposible de completar apenas el aprendiz se saltea un dia (2026-09-05) — **RESUELTO**

**Sintoma:** el aprendiz toca el habito "Clase Diaria" en Training y, en vez de abrirse la leccion
del dia, aparece:

```
Leccion no disponible 🔒
Para acceder a esta leccion primero debes completar la leccion anterior:
"<titulo de la leccion anterior>"
```

El habito queda sin poder cerrarse. **Y no se destraba solo: empeora cada dia.**

**Causa real — dos reglas que se contradicen:**

1. El **backend** elige la Clase Diaria a partir del `dia_programa` del aprendiz
   (`ClaseDiariaService.claseDeHoy`). Ese dia **avanza con el calendario**, haya o no completado la
   clase de ayer (es derivado de fechas, D-81).
2. El **frontend** abria esa leccion por `handleAbrirLeccion` (`ComunidadScreen`), que aplica una
   regla de **progresion secuencial**: no se puede abrir una leccion si la anterior no esta
   completada.

Basta faltar **un** dia para que las dos se peleen: el dia avanza, la leccion de hoy pasa a ser otra,
la anterior sigue incompleta, y el gate secuencial bloquea la de hoy. Al dia siguiente el hueco es
mayor. El habito diario queda inalcanzable para siempre.

**Por que estaba asi:** fue deliberado y esta escrito en el propio codigo — *"Deliberadamente NO se
saltea `handleAbrirLeccion` (...) Entrar por un atajo que ignore esas reglas seria inventarle una
excepcion a la Clase Diaria que nadie pidio."* El razonamiento es sensato **salvo por un detalle
que no se verifico**: que la leccion de la Clase Diaria no la elige la persona navegando, la elige
el reloj del programa. Una regla pensada para "no te saltees lecciones a mano" se aplico a un
camino donde nadie eligio saltarse nada.

**Solucion aplicada:** `handleAbrirLeccion` acepta `omitirProgresionSecuencial` (default `false`,
asi la navegacion a mano no cambia en nada), y el deep-link de la Clase Diaria lo pasa en `true`.
**El bloqueo por dia de programa (`lesson.locked`) SI se mantiene** — ese lo decide el backend y es
el que de verdad ordena el curso; el secuencial era una regla que solo vivia en el cliente.

**Como evitar que vuelva a pasar:** antes de reusar una regla de navegacion en un camino nuevo,
preguntarse **quien eligio el destino**. Si lo eligio la persona, las reglas de navegacion manual
aplican. Si lo eligio el servidor (un reloj, un scheduler, un deep-link), aplicarle una regla de
"no te saltees pasos" convierte un atraso en un bloqueo permanente.

---

## E-113 — Un script de edicion que falla al escribir deja el archivo en 0 bytes (2026-09-05) — **RESUELTO**

**Sintoma exacto:**

```
UnicodeEncodeError: 'utf-8' codec can't encode characters in position 39541-39542: surrogates not allowed
```

...y acto seguido `wc -c ComunidadScreen.tsx` devuelve **0**. El archivo entero desaparecio, no solo
el cambio.

**Causa real, y son dos cosas distintas:**

1. En Python, un literal como `"\ud83d\udd12"` (el candado 🔒 escrito como **par sustituto**) no es
   texto UTF-8 valido: `str.encode('utf-8')` lo rechaza. Un emoji fuera del plano basico se escribe
   `"\U0001F512"` o con `chr(0x1F512)`, nunca como dos mitades sueltas.
2. Lo que destruyo el archivo no fue el error, fue el orden: **`open(p, 'w')` trunca el archivo en
   el momento de abrirlo**, antes de que `.write(s)` intente codificar. Cuando la codificacion
   falla, ya no queda nada.

**Solucion aplicada:** el archivo se recupero con `git checkout --` (estaba versionado y limpio) y
los cambios se rehicieron con un script que **codifica primero y escribe despues**, sobre un
temporal:

```python
datos = s.encode('utf-8')      # si algo no es UTF-8 valido, falla ACA y el original no se toca
with open(p + '.tmp', 'wb') as f:
    f.write(datos)
os.replace(p + '.tmp', p)       # reemplazo atomico
```

**Como evitar que vuelva a pasar:** ningun script de edicion masiva abre el archivo destino en `'w'`.
Se codifica a bytes primero, se escribe a un temporal, y se reemplaza con `os.replace`. Y antes de
correr un script asi sobre un archivo, verificar que este limpio en git — es la unica red que
convirtio esto en dos minutos de perdida en vez de una tarde.

---

## E-114 — "No pudimos enviar tu resumen" sobre un POST que SI funciono: el backend publicaba `habitTrackId` y el contrato dice `registroHabitoId` (2026-09-05) — **RESUELTO**

**Sintoma exacto:** en el modal de Clase Diaria (Training), la persona escribe el resumen, toca
"ENVIAR Y COMPLETAR" y aparece en rojo:

```
No pudimos enviar tu resumen. Intentá de nuevo.
```

Reintentar da exactamente lo mismo, siempre. El habito, sin embargo, **queda completado en el
servidor**: los puntos se otorgan, la leccion se marca vista y el resumen se guarda. Solo la app
cree que fallo.

**Causa real — un nombre de campo, no un error de negocio.** `POST /api/v1/classroom/clase-diaria`
respondia **200 OK** con:

```json
{"leccionId":"...","habitTrackId":"...","puntosOtorgados":5}
```

y el cliente movil valida toda respuesta contra el contrato publicado
(`docs/api/CONTRATO_CONTENIDO_IA.md` §1.11-bis), que dice `registroHabitoId`:

```ts
const completarClaseDiariaSchema = z.object({
  leccionId: z.string(), registroHabitoId: z.string(), puntosOtorgados: z.number(),
}).passthrough();
```

`validarRespuesta` tira `Error("El backend respondió algo inesperado ... registroHabitoId: Required")`.
Ese `Error` **no es un `ApiError`**, y `mensajeDeError` devuelve el texto por defecto para todo lo que
no lo sea — de ahi que la persona vea el mensaje generico de red y no el detalle del campo. El 200
se convierte en "fallo" del lado del cliente.

De donde salio el nombre viejo: el DTO `CompletarClaseDiariaResponse` espejaba a mano el shape del
backend anterior (RenaserBack `clase-diaria/service.ts:64,85`, `{ leccionId, habitTrackId }`), y su
javadoc lo justificaba explicitamente. El contrato de ESTE backend, el use case
(`ClaseDiariaCompletada.registroHabitoId`) y el resto del sistema (`EvidenciaResponse`,
`CONTRATO_DIA_A_DIA.md`) ya usaban `registroHabitoId`. **El unico archivo de los cinco que decia
otra cosa era el DTO web.**

**Por que ninguna prueba lo vio, que es la parte importante:** `ClaseDiariaServiceTest` si
verificaba `resultado.registroHabitoId()` — pero sobre el record del caso de uso, no sobre el JSON.
Entre el caso de uso y el cable habia un mapeo a mano (`CompletarClaseDiariaResponse.from`) que
nadie probaba. **Un contrato HTTP se verifica sobre el JSON serializado; asertar los accessors del
record prueba el record, no el contrato.**

**Falsa pista que costo tiempo:** en el log del mismo dia aparecia
`409 -> Conflict: El registro ya esta en un estado terminal: COMPLETADO`, y era tentador cerrar el
caso ahi. No era eso: `ClaseDiariaHabitoService.completarDeHoy` **si** es idempotente (retorna
temprano si el registro ya esta `COMPLETADO`, sin volver a completarlo), asi que reenviar el resumen
devuelve 200, nunca 409. Ese mensaje solo lo puede tirar `RegistroHabito.requireNoTerminal()`, cuyos
tres llamadores son `RegistroService.completar` (la ruta generica
`POST /habit-tracks/{id}/complete`), `RachaService` y `SantuarioService` — ninguno es la Clase
Diaria. El javadoc de `ClaseDiariaService.completar` que promete idempotencia **no mentia**;
verificarlo contra el codigo antes de creerle al log fue lo que destrabo el diagnostico.

**Solucion aplicada:**

1. `CompletarClaseDiariaResponse.habitTrackId` -> `registroHabitoId`, con el javadoc corregido para
   que la proxima persona no lo "arregle" de vuelta al nombre del repo viejo.
2. `CompletarClaseDiariaResponseTest` (nuevo): serializa el DTO con Jackson y asserta las **claves
   del JSON**, incluida la ausencia de claves de mas. Falla contra el codigo viejo (verificado:
   1 failure + 1 error).

**Como evitar que vuelva a pasar:**

- Cuando un DTO web se escribe espejando un backend anterior, **el contrato publicado
  (`docs/api/*.md`) le gana al repo viejo**, porque es contra el contrato que se escribio el cliente.
  Si los dos difieren, es un bug del DTO, no del contrato.
- Todo DTO de salida con mapeo a mano lleva una prueba que mira el **JSON**, no los accessors. Es
  barata (no necesita Spring) y es la unica que ve el nombre real del campo.
- Si el cliente reporta "no se pudo enviar" pero el dato aparece guardado en el servidor, la
  hipotesis numero uno es **validacion de la respuesta en el cliente**, no un fallo de escritura.

---

## E-116 — El Muro y el catalogo de Cursos se pintan uno encima del otro (2026-09-05) — **RESUELTO**

**Sintoma exacto**, como lo reporto el dueno del proyecto:

```
bug encontrado con la solucion del post en comunidad. luego de publicar sale asi debe de ser
normal el flujo me lleva a muro con publicar abierto y debo de poder subir algo nd mas luego
que salga un mensaje su habito de post en comunidad se completo con exito no procede
```

La captura que adjunto muestra **dos secciones de `ComunidadScreen` apiladas en el mismo scroll**:
arriba "VOLVER A COMUNIDAD" + "EVENTOS & EXPERIENCIAS" + las pestanas MURO/TESTIMONIOS/RANKING con
una publicacion ya hecha, y pegado abajo "VOLVER A CURSOS" + la tarjeta del curso "FORMACION
RENASER - FASE I". El Muro y Cursos, visibles a la vez.

**Causa real:** `ComunidadScreen` decidia que seccion mostrar con **tres booleanos independientes**
(`inEventosExperiencias`, `inExclusiveResources`, `inAtencionPersonalizada`), y cada bloque del
render preguntaba solo por el suyo:

```tsx
{inEventosExperiencias && ( <ScrollView> ... )}          // VISTA 2
{inExclusiveResources && selectedCourse !== null && ( <ScrollView> ... )}   // VISTA 3.1
```

Son `<ScrollView>` hermanos dentro del mismo `SafeAreaView`: con los dos booleanos en `true` se
pintan los dos, uno debajo del otro. **Nada garantizaba que fueran excluyentes** — solo la
casualidad de que los caminos de entrada a mano pasaran de a uno.

Los dos atajos que entran desde la pestana Training rompen esa casualidad, porque prenden su flag
sin apagar el resto:

- `abrirCursoId`/`abrirLeccionId` (habito Clase Diaria) -> `setInExclusiveResources(true)`
- `abrirComposerMuro` (habito de post en comunidad) -> `setInEventosExperiencias(true)`

Basta tocar **un habito y despues el otro** — que es exactamente lo que hace un aprendiz cualquiera
en su dia — para que queden las dos secciones prendidas. Cambiar de pestana no desmonta la
pantalla, asi que el flag del primero sigue en `true` cuando llega el segundo.

**Solucion aplicada:** un solo valor en vez de tres booleanos, para que el estado invalido **no se
pueda ni escribir** (`ComunidadScreen.tsx`):

```tsx
type SeccionComunidad = 'inicio' | 'eventos' | 'recursos' | 'atencion';
const [seccionActiva, setSeccionActiva] = useState<SeccionComunidad>('inicio');
const inExclusiveResources = seccionActiva === 'recursos';   // derivados, ya no estados
const inEventosExperiencias = seccionActiva === 'eventos';
const inAtencionPersonalizada = seccionActiva === 'atencion';
```

Los tres nombres viejos se conservan como **derivados** a proposito: asi las ~15 condiciones de
render y el manejador del boton "atras" no se tocaron, y el diff quedo en los ~10 lugares que
escribian. Todo cambio de seccion pasa ahora por `irASeccion(seccion)`, que ademas limpia el
sub-estado de la seccion que se deja (`fullScreenLesson`/`selectedCourseId` al salir de Recursos,
`activeChat`/`groupInfoVisible` al salir de Atencion) — sin eso, quien dejaba una leccion abierta a
pantalla completa volvia a caer dentro de ESA leccion la proxima vez que entraba a Recursos.

**Como evitar que vuelva a pasar:** cuando dos estados son excluyentes **por diseno**, no se
modelan con dos booleanos y disciplina — se modelan con **una sola variable**. Con N booleanos hay
2^N combinaciones posibles y solo N+1 validas; las otras no fallan al escribirlas, fallan a la
vista del usuario semanas despues. La senal de alarma concreta: si al agregar un camino de entrada
hay que acordarse de apagar los flags de los otros, el modelo esta mal — cada camino nuevo es una
oportunidad mas de olvidarselo, y aca hubo dos y se olvidaron los dos.

---

## E-117 — El habito "post en comunidad" no se cerraba NUNCA al publicar (2026-09-05) — **RESUELTO en el movil**

**Sintoma exacto**, del mismo reporte de arriba: el aprendiz toca el habito, publica en el Muro, y
el mensaje que deberia cerrar el flujo — *"su habito de post en comunidad se completo con exito"* —
**no procede**. El habito queda pendiente hasta que el cron nocturno lo expira.

**Causa real: de la regla de negocio se implemento solo la mitad.** La regla la dio el dueno del
producto el 2026-09-04 y esta transcrita textual en el javadoc de `PoliticaPostDiarioComunidad`:

> "Cuando publique algo, y recien ahi, se marca como completado. Ojo: debe publicar algo para
> poder comprobar el estado."

Lo construido fue la mitad **guardiana** ("no lo cierres si no publico"):
`PoliticaPostDiarioComunidad.puedeCompletarseDirecto` consulta
`PublicacionMuroFinder.publicoEntre(...)` y hace que `POST /habit-tracks/{id}/complete` responda
400 si no hay publicacion de esa persona ese dia. Es un **guard**, no un **closer**: solo corre
DENTRO de ese endpoint.

La mitad que **dispara** el cierre no existia en ningun lado:

- **Backend:** `PublicacionMuroService.publicar` emite `PublicacionCreadaEvent`... y ese evento
  **no tiene ni un oyente**. El javadoc del propio evento dice que lo escucharia `notifications`
  "en la Ola 3"; ese listener tampoco existe. De hecho **`habits` no tiene ni un solo listener de
  eventos** en todo el modulo: los dos habitos que se cierran como efecto secundario (`DAILY_CLASS`
  via `ClaseDiariaHabitoService`, `PASTILLA_RENACER` via `PastillaRenacerHabitoService`) lo hacen
  con **llamada directa a un puerto de `habits.api`**, nunca por evento.
- **Frontend:** `abrirMuroParaPublicar` (TrainingScreen) solo navega — dos lineas. Y al publicar,
  `handlePublishPost` llamaba a `POST /api/v1/wall` y a `avisarPostPublicado()`, que es del
  **arranque guiado** (pasar al Pacto), no de habitos. Nadie llamaba a `/complete`.

Lo peor es que estaba **documentado al reves**, y por eso nadie lo noto: `habits.types.ts` decia
*"el movil no puede marcarlo y listo — lo unico que puede hacer es llevar a publicar"*, y
`TrainingScreen` decia *"mandar al muro es el unico camino que cierra"*. Navegar al muro no cerraba
nada. Las dos frases describian la mitad guardiana como si fuera el flujo completo.

**Solucion aplicada (en el movil):** el disparador que faltaba, con la publicacion ya confirmada.

- `features/habits/api/postDiarioComunidad.ts` — `cerrarHabitoPostDiarioComunidad()`: cruza
  `GET /habits` (de ahi sale `systemKey`, que `TrackDelDiaApi` no trae) con
  `GET /habit-tracks/today`, y si el track de `COMMUNITY_POST` esta pendiente llama a
  `POST /habit-tracks/{id}/complete`. Devuelve `completado | ya-estaba | no-aplica | fallo` y
  **nunca lanza**: la publicacion ya esta guardada y un fallo cerrando el habito no puede terminar
  mostrando "No se pudo publicar".
- `ComunidadScreen.handlePublishPost` lo llama despues del `await` de publicar, y solo si devuelve
  `completado` muestra el aviso que el dueno pidio.
- `features/habits/events/avisoPostDiarioCerrado.ts` + un oyente en `TrainingScreen`: la tarjeta
  del habito vive en OTRA pestana y `useTraining` carga una sola vez al montarse, asi que sin esto
  la persona leia "completado" y volvia a encontrar la tarjeta sin tildar.

**La verificacion sigue siendo del servidor.** El movil no "marca" nada por decision propia: pide
el cierre, y el backend lo concede solo porque encuentra la publicacion en `publicaciones_muro`. Un
cliente que llame sin haber publicado sigue comiendo el 400 de siempre.

**Lo que quedaba ABIERTO — CERRADO el 2026-09-05, ver E-121:** `rocks` publica al Muro al completar
la roca diaria (`PublicarEnMuroPort` -> `PublicacionMuroService.publicarDesdeEvidencia`), con la
publicacion **a nombre del aprendiz**. O sea que hoy, con el guard ya en produccion, completar una
roca **habilita** cerrar a mano el habito de post diario. Faltaba decidir si esa publicacion
automatica ademas deberia **cerrarlo sola y pagar sus puntos**. Preguntado el 2026-09-05, el dueno
del producto respondio **"si"**, y se implemento exactamente donde esta entrada decia que iba: un
`@ApplicationModuleListener` de `PublicacionCreadaEvent` en `habits` — el primer listener del
modulo. **Consecuencia para el movil:** `cerrarHabitoPostDiarioComunidad()` quedo redundante; ver
E-121.

**Como evitar que vuelva a pasar:** cuando una regla de negocio tiene forma de *"cuando pase X,
entonces Y"*, se implementan **las dos mitades o ninguna**. Validar X sin disparar Y deja un
sistema que rechaza correctamente lo invalido y no hace nunca lo valido — y que se ve "terminado"
en revision, porque la parte dificil (la comprobacion contra la base) esta escrita y probada. La
senal concreta que hubo que aprender a leer aca: **un evento de dominio con cero oyentes**
(`PublicacionCreadaEvent`) es codigo que no hace nada; vale la pena un test de arquitectura que
liste los eventos publicados sin ningun `@ApplicationModuleListener` que los escuche.

---

## E-118 — El contador "0/5 EVIDENCIAS" de Training no se movia nunca, aunque el aprendiz cerrara habitos (2026-09-05) — **RESUELTO en el movil**

**Sintoma exacto**, palabras del dueno del producto:

> "acabo de subir o registrar habitos y sale este mensaje me refiero la evidencias 0/5 no se
> contabiliza"

En la pantalla TRAINING, las cinco dimensiones con el numerador clavado en 0:

```
CUERPO            0/5 EVIDENCIAS
MENTE             0/3 EVIDENCIAS
EMOCIONES         0/1 EVIDENCIAS
ESPIRITU          0/2 EVIDENCIAS
VIDA Y NEGOCIO    0/0 EVIDENCIAS
```

El denominador SI variaba por dimension (5/3/1/2), asi que el reparto por categoria funcionaba.

**Lo primero que se descarto, con datos y no con lectura de codigo.** El backend NO pierde el dato:

```
$ docker exec -e PGPASSWORD=postgres renaser-db psql -U postgres -d renaser \
  -c "SELECT rh.fecha_ejecucion, h.categoria_clave, count(*) n, count(e.id) con_evidencia
      FROM renaser.registros_habito rh
      JOIN renaser.habitos h ON h.id = rh.habito_id
      LEFT JOIN renaser.evidencias e ON e.registro_habito_id = rh.id
      GROUP BY 1,2 ORDER BY 1,2;"

 2026-09-05 | CONSCIENCIA | 1 | 0
 2026-09-05 | CUERPO      | 5 | 0
 2026-09-05 | ESPIRITU    | 2 | 0
 2026-09-05 | MENTE       | 3 | 0
```

`GET /api/v1/evidence` devuelve 200 y trae bien la unica fila que existe, con
`registroHabitoId` poblado; `GET /api/v1/habit-tracks/today` devuelve los 11 tracks del dia con su
`estado`. El mapeo del movil tambien es correcto (`hasEvidence: habitosConEvidencia.has(track.id)`,
y `track.id` ES el id del registro). **El numerador era 0 porque de verdad no habia ninguna fila en
`renaser.evidencias` para los registros de hoy** — y sin embargo el aprendiz habia cerrado dos
habitos (DESPERTAR a las 17:06 de Lima, Clase Diaria a las 17:26).

**Causa real: el badge medía una cosa que, para la mitad del catalogo, no puede existir nunca.**
En todo el backend hay **exactamente tres** lugares que crean una fila en `evidencias`
(`grep "new DestinoEvidencia"`): la subida generica de archivo/texto de un habito
(`EvidenciaRegistroService`), el Santuario (`RachaService`) y las rocas (`RocaDiariaService`). Los
habitos cuya prueba **ES la accion** cierran el registro en `COMPLETADO` sin crear ninguna fila:

| Habito | Como cierra | Fila en `evidencias` |
|---|---|---|
| DESPERTAR / DORMIR (`WAKE_UP`/`SLEEP`) | `POST /habit-tracks/{id}/complete`, la evidencia es `completado_en` (D-97) | no |
| Clase Diaria (`DAILY_CLASS`) | `ClaseDiariaHabitoService`, llamada directa a `habits.api` | no |
| Post Diario en Comunidad (`COMMUNITY_POST`) | idem, tras publicar en el Muro (E-117) | no |
| Pastilla Renacer (`PASTILLA_RENACER`) | `PastillaRenacerHabitoService` | no |

El caso que lo deja a la vista: **EMOCIONES tiene un solo habito, el Post Diario**, asi que
"0/1 EVIDENCIAS" era **permanente** — ningun aprendiz podia verlo en 1/1 jamas. Un contador de
progreso cuyo numerador no puede alcanzar su propio denominador esta roto por construccion, sin
importar que la fuente de datos sea correcta.

**De donde salio.** El badge contaba `done` y el commit `6cab4c9` (frontend) lo cambio a
`hasEvidence`:

```diff
-                const doneCount = dimHabits.filter(h => h.done).length;
+                const evidenceCount = dimHabits.filter(h => h.hasEvidence).length;
```

El comentario que acompanaba el cambio ("pueden divergir con datos reales") era cierto, pero la
divergencia no es simetrica: hay habitos que **nunca** van a tener fila de evidencia.

**Solucion aplicada (solo movil, dos lineas en `TrainingScreen.tsx`):**

```diff
-  const sealedEvidencesCount = currentDimensionHabits.filter(h => h.hasEvidence).length;
+  const sealedEvidencesCount = currentDimensionHabits.filter(h => h.done || h.hasEvidence).length;
...
-                const evidenceCount = dimHabits.filter(h => h.hasEvidence).length;
+                const evidenceCount = dimHabits.filter(h => h.done || h.hasEvidence).length;
```

`||` y no solo `done`: en los datos reales ya hay un caso de evidencia subida sobre un registro que
despues vencio (`5eb085d0`, PRIMERA COMIDA del 2026-09-04, `estado = EXPIRADO` con su foto
cargada) — esa evidencia se entrego y tiene que contar. Se toco tambien
`sealedEvidencesCount` porque si no la lista decia "1/1 EVIDENCIAS" y el detalle de la misma
dimension "Evidencias selladas hoy 0%".

**El backend no se toco:** el dato que sirve ya lo publica (`estado` de cada track). No se agrego
ninguna fila de evidencia sintetica para los habitos de flujo propio — eso cambiaria el significado
de `evidencias` (la tabla que alimenta la cola de validacion por IA y la revision manual) y ademas
rompería el chip "VER / SUBIR" del detalle, que si tiene que preguntar por un archivo real.

**Como evitar que vuelva a pasar:**

- Antes de cambiar **que** cuenta un contador de progreso, verificar que la metrica nueva pueda
  **llegar al denominador que ya esta en pantalla**. Si existe una fila del catalogo para la que el
  numerador es 0 por definicion, la metrica no sirve para ese widget.
- Cuando dos widgets de la misma pantalla usan la palabra "evidencias" para dos numeros distintos
  (el badge de la lista y la barra del detalle), tienen que calcularse con **la misma** expresion.
- `exigencia_evidencia` del catalogo es el chequeo barato: hoy 5 de los 11 habitos del dia son
  `OPCIONAL`, o sea que "sin fila en `evidencias`" es su estado esperado para siempre, no una
  anomalia.

**Lo que queda ABIERTO (decision del dueno, no se invento):** el badge sigue diciendo "EVIDENCIAS"
mientras el detalle llama "CUMPLIDOS" al mismo numero. Si se prefiere separar de verdad "cumplido"
de "evidencia con archivo", el badge de la lista necesita otro texto — es copy, no logica.

---

## E-119 — Cuatro maquetas presentadas al aprendiz como contenido real (2026-09-05) — **RESUELTO**

**Sintoma:** el dueno del proyecto recorrio la app y fue encontrando pantallas con contenido que
parecia real y no lo era. No es un bug de logica: la app le **afirmaba cosas falsas al aprendiz**.

Los cuatro lugares, y lo que decia cada uno:

1. **Training -> dimension -> "GUIAS Y AUDIOS"**: una "AUDIO GUIA RECOMENDADA" distinta por
   dimension (`DIMENSIONES_CONFIG`), un boton "ESCUCHAR SESION GUIADA" que solo abria un
   `Alert.alert('Reproductor de Audio', ...)`, y una "RUTA DE CLASES (FASE 2)" de cinco clases
   inventadas, **tres marcadas "✓ Completada"** sin que el aprendiz hubiera hecho ninguna.
   El peor de todos era el tip de MACACO de MENTE:

       "Hoy registraste frustracion dos veces. Escucha pensando: ¿que hecho ocurrio y que
        historia anadiste?"

   Una constante, igual para todos, todos los dias, **presentada como una observacion sobre el dia
   de esa persona**.

2. **Comunidad -> "TESTIMONIOS"**: dos testimonios inventados con nombre y apellido
   ("Carlos Mendez, CEO & Fundador Tecnologico"; "Dra. Valeria Ruiz, Directora Medica & Cirujana"),
   insignia de generacion, cifras de resultado ("+140% USD", "-50% Horas", "100% Sin Ansiedad") y
   un boton "VER VIDEO TESTIMONIO" que no reproducia nada.

3. **Login -> "ACCESO DIRECTO (MODO DEMO)"**: `demoLogin` entraba a la app **sin ninguna llamada al
   servidor** — fijaba `USUARIO_DEMO_EXISTENTE` en estado local y marcaba el onboarding completo.

4. **Compartir publicacion -> "WhatsApp y Otras Apps"**: retirado por pedido, por ahora. No era
   falso, pero saca la publicacion de un aprendiz del circulo cerrado de la tribu y eso todavia no
   esta decidido.

**Causa real:** las pantallas se construyeron primero como maqueta visual con datos de relleno, y
la integracion con el backend fue reemplazando esos datos **pantalla por pantalla**. Las que
todavia no llegaron se quedaron con el relleno puesto y **con la misma apariencia que las ya
integradas** — nada en la interfaz distingue "esto es real" de "esto es un placeholder". El codigo
incluso lo admitia sin que nadie lo leyera como un problema:

    // El Muro y "Recursos Exclusivos" ya no usan datos fijos (...). Testimonios y Ranking siguen
    // con datos de mock — quedan fuera del alcance de esta integracion.

**Ya habia pasado antes, exactamente igual.** `INITIAL_HABITS` eran 17 habitos inventados, 11 de
ellos con `done: true`, `hasEvidence: true` y `streak: 37`. Se corrigio en su momento, pero se
corrigio **ese array**, no la clase de problema.

**Solucion aplicada:** las tres primeras pasan a un estado honesto y explicito ("EN DESARROLLO",
"PROXIMAMENTE") y la cuarta se retira. En los tres casos **se borraron los datos inventados, no
solo el render**: mientras las cadenas siguen en el archivo, alguien las vuelve a colgar de una
pantalla.

**Como evitar que vuelva a pasar:** la regla util no es "no dejar mocks" — es mas estrecha y se
puede revisar en un PR:

> **Ningun dato de relleno puede afirmar un hecho sobre el aprendiz ni sobre una persona con
> nombre.** Un titulo inventado es un placeholder; `"✓ Completada"`, `"streak: 37"`,
> `"Hoy registraste frustracion dos veces"` o un testimonio firmado por "Dra. Valeria Ruiz" son
> **afirmaciones**. Si la pantalla todavia no tiene backend, va un estado vacio que lo diga.

**Pendiente, y no es de codigo:** `GROUP_MEMBERS` (ComunidadScreen) sigue siendo mock y tambien
lista personas con nombre y racha; hoy se renderiza en la info del grupo de chat. Queda anotado
aca porque cae bajo la misma regla, pero no se toco en este cambio.

---

## E-120 — La Clase Diaria se podia cerrar sin resumen, y el resumen que llegaba despues se descartaba en silencio (2026-09-05) — **RESUELTO**

**Sintoma exacto.** Dos sintomas encadenados, y el segundo es el que pierde datos:

1. El aprendiz toca "Clase diaria" en la lista de habitos de Training (o le pide al acompanante
   *"marca mi clase diaria como hecha"*) y el habito **se marca completado y paga sus 10 puntos**,
   sin haber visto la clase ni escrito una linea de resumen.
2. Mas tarde entra al flujo bueno, mira la clase, escribe su resumen y lo envia. El servidor
   responde **`200 OK`** con los puntos ya otorgados... y el texto **no se guarda en ningun lado**.
   `registros_habito.respuesta_texto` sigue en `NULL`.

**Causa real: no existia ninguna `PoliticaHabito` para `DAILY_CLASS`.** De los ~40 habitos del
catalogo solo dos tenian regla propia (`PoliticaSantuario` por tipo `BLOQUEO`,
`PoliticaPostDiarioComunidad` por clave `COMMUNITY_POST`). `DAILY_CLASS` es un `CHECKBOX` normal,
asi que caia en `RegistroPoliticasHabito.GENERICA` — la que dice "se completa con el gesto
generico, sin condiciones". Eso dejaba **dos** puertas abiertas que no piden resumen:

- `POST /api/v1/habit-tracks/{id}/complete` -> `RegistroService.completar`
- la herramienta `marcar_habito_completado` del agente Renasia -> `HerramientasAgenteService` ->
  `AgendaDelDiaFinder.completar`, que va al **mismo** caso de uso

Y el contrato publicado afirmaba lo contrario, palabra por palabra
(`docs/api/CONTRATO_CONTENIDO_IA.md` seccion 1.11-bis): *"Sin este POST el habito NO queda
completado: no hay estado intermedio 'completado sin resumen'"*.

**La perdida de datos es la segunda mitad.** `ClaseDiariaHabitoService.completarDeHoy` tenia un
early-return idempotente que devolvia 200 **sin mirar `command.resumen()`**. Con el registro ya
`COMPLETADO` por cualquiera de esas dos puertas, el resumen que la persona escribia entraba al
servidor, no se guardaba, y se contestaba OK. Silenciosa: ni un log, ni un 4xx, ni una diferencia
visible en la respuesta.

**La complicacion, y por que el arreglo obvio no servia.** Poner una `PoliticaClaseDiaria` que
devuelva `noProcede` rompe tambien el camino legitimo: `ClaseDiariaHabitoService` pasa **a
proposito** por `CompletarRegistroUseCase` para no duplicar el calculo de puntos ni el de la
ventana de entrega, asi que atraviesa la misma politica. Habia que distinguir *"me llamo la Clase
Diaria con su resumen"* de *"me llamo la ruta generica"*.

**Solucion aplicada — el gesto viaja en el comando, no en el contexto.**

- `domain/model/politica/GestoCompletar` (`GENERICO` | `PROPIO_DEL_HABITO`) como componente nuevo
  de `CompletarRegistroCommand`. `RegistroService.completar` consulta la politica **solo** cuando el
  gesto es el generico — que es literalmente la pregunta que
  `PoliticaHabito.puedeCompletarseDirecto` dice contestar en su javadoc ("si el habito puede darse
  por cumplido con el gesto generico").
- `PoliticaClaseDiaria` (por clave `DAILY_CLASS`) devuelve `noProcede` con un motivo que la persona
  entiende. Las dos puertas viejas ahora responden **400**.
- `ClaseDiariaHabitoService` manda `PROPIO_DEL_HABITO` y su camino sigue funcionando igual.
- El constructor de 4 argumentos de `CompletarRegistroCommand` sigue existiendo y significa
  `GENERICO`: el valor por defecto es el **seguro** (gobernado por la politica), y saltearla exige
  escribirlo a proposito. El campo no existe en ningun DTO de entrada, asi que no viaja desde el
  telefono — mismo blindaje que `puntos`.

**Por que el gesto NO fue al `ContextoCompletar`,** que era la otra opcion sobre la mesa: ese objeto
son "hechos EXTERNOS al catalogo" que la politica **consulta para decidir** (si publico en el Muro).
El gesto no es un hecho del mundo: es **quien esta preguntando**. Metido ahi, cada politica futura
tendria que acordarse de ramificar por el gesto para no cerrarle a su propio habito el unico camino
valido. Con el dato en el comando, el unico que ramifica es quien orquesta, una sola vez.

**Tampoco se llevo el resumen en el contexto** ("procede si viene texto"): el endpoint generico
acepta un `respuestaTexto` libre, asi que la ruta generica quedaria abierta con solo mandar 15
caracteres — y esa ruta no marca la leccion como vista ni comprueba que sea la clase de HOY, que
son la otra mitad del gesto.

**Y el resumen tardio: se rechaza, no se guarda.** Si el registro de hoy esta `COMPLETADO` **con**
resumen, sigue siendo el 200 idempotente que el contrato promete (es un reintento genuino: doble
toque, reenvio tras un corte). Si esta `COMPLETADO` **sin** resumen, se responde **409** con un
mensaje claro en vez de tirar el texto en silencio. **Por que rechazar y no guardarlo:** guardarlo
obliga a escribir sobre un agregado en estado terminal, y el dominio lo prohibe a proposito
(`EstadoRegistro` declara COMPLETADO/FALLIDO/EXPIRADO terminales; `RegistroHabito` lo hace cumplir
con `requireNoTerminal()` en cada mutador). Abrir un mutador de excepcion deja instalada una puerta
de reparacion de datos que la proxima persona reusa para "un campo mas". Y el precio de no abrirla
es acotado: despues de este arreglo el estado es **inalcanzable**, y las unicas filas que pueden
caer ahi son de bases locales — no hay entorno desplegado. Cambiar una invariante del dominio de
forma permanente para reparar datos de desarrollo es mal negocio. Si el dueno decide que el resumen
tardio debe guardarse, es una regla de negocio nueva y se decide como tal.

**Como evitar que vuelva a pasar.** La senal que hubo que aprender a leer: **un contrato que afirma
"el unico camino es X" y un habito de catalogo sin politica propia son afirmaciones
contradictorias**, y nada las cruzaba. Cada frase del contrato de la forma *"solo se puede hacer por
aca"* necesita, del otro lado, o una `PoliticaHabito` o un test que pruebe que el resto de las
puertas responde 4xx. Los dos tests de regresion
(`RegistroServiceTest.claseDiariaNoSeCierraConElGestoGenerico` y
`ClaseDiariaHabitoServiceTest.completarDeHoyRechazaResumenSobreRegistroCerradoSinResumen`) se
verificaron en rojo contra el codigo viejo antes de darlo por arreglado.

**Segundo bug encontrado y NO arreglado (fuera de alcance, se reporta):**
`PastillaRenacerHabitoService.completarDeHoy` tiene **exactamente** el mismo early-return ciego —
si el track de `PASTILLA_RENACER` ya esta `COMPLETADO`, descarta el `resumen` y devuelve 200. Y
`PASTILLA_RENACER` tampoco tiene politica propia, asi que la ruta generica tambien lo puede cerrar
sin resumen. Mismo bug, otro habito. No se toco porque el pedido era la Clase Diaria.

---

## E-121 — Publicar en el Muro no cerraba el habito de post diario: el evento no tenia oyente (2026-09-05) — **RESUELTO**

**Sintoma exacto:** el mismo de E-117 desde el lado del servidor. El aprendiz publica en el Muro y
el habito "POST DIARIO EN COMUNIDAD" **sigue PENDIENTE** hasta que el barrido nocturno lo expira,
con sus puntos perdidos. Y cuando `rocks` publica sola al Muro al completar la roca diaria (a
nombre del aprendiz), tampoco pasaba nada.

**Causa real: `PublicacionCreadaEvent` no tenia ni un oyente.** De la regla del dueno (2026-09-04,
*"cuando publique algo, y recien ahi, se marca como completado"*) estaba construida solo la mitad
**guardiana** — `PoliticaPostDiarioComunidad`, que rechaza el cierre manual de quien no publico. La
mitad que **dispara** el cierre no existia en el backend: `PublicacionMuroService` emitia el evento
y nadie lo escuchaba (`habits` no tenia un solo listener en todo el modulo). E-117 lo tapo desde el
movil, llamando a `/complete` despues de publicar; eso no cubria la publicacion automatica de
`rocks`, que no pasa por el compositor del Muro.

**Decision del dueno (2026-09-05), textual, ante la pregunta de si la publicacion automatica de
`rocks` tambien deberia cerrar el habito y pagar sus puntos: "si".**

**Solucion aplicada:**

- `habits/infrastructure/adapter/in/event/PublicacionCreadaHabitoListener` —
  `@ApplicationModuleListener` de `PublicacionCreadaEvent`. Es el **primer oyente de eventos de
  `habits`**. Colgado del evento y no del endpoint de publicar a proposito: las dos vias (la manual
  del Muro y `publicarDesdeEvidencia` de `rocks`) emiten el mismo evento.
- `CerrarPostDiarioComunidadUseCase` / `PostDiarioComunidadHabitoService` — misma forma que
  `ClaseDiariaHabitoService`: busca por `claveSistema`, localiza el registro y delega el cierre en
  `CompletarRegistroUseCase`, donde ya viven los puntos, la ventana, el bloqueo pesimista y el
  evento de dominio.

**Los dos cuidados que costaron pensar, y que un test tapa mal:**

1. **El dia es el de la PUBLICACION, en la zona del PARTICIPANTE — no "hoy", no UTC.** El outbox de
   Modulith corre con `republish-outstanding-events-on-restart: true`: un evento que quedo sin
   procesar se reentrega **al arrancar**, que puede ser al dia siguiente. Anclando en "hoy" se le
   pagaria el habito de hoy con el post de ayer. Se ancla en `evento.occurredAt()` y se convierte
   con `publicadoEn.atZone(zona del participante)`, no con `clock.today()` (regla 02, E-91). El
   test que lo cubre usa las **02:00 UTC**, que en Lima son las 21:00 del dia anterior: con un
   reloj fijado a las 10:00 UTC, como estaban todos los fixtures viejos, este bug no se ve.
2. **Idempotencia en tres capas.** (a) el registro se busca por el dia de la publicacion, asi que
   una reentrega apunta al mismo; (b) si ese registro ya esta en estado terminal se vuelve sin
   tocarlo — es la guarda del caso comun (dos publicaciones el mismo dia, o el reinicio); (c) si
   aun asi dos caminos llegan a la vez, el bloqueo pesimista de `RegistroService.requireRegistro`
   serializa y el segundo choca contra `COMPLETADO`, que es terminal. **Nunca se paga dos veces.**

**Se cierra con el gesto GENERICO a proposito** (no `PROPIO_DEL_HABITO`, ver E-120): asi
`PoliticaPostDiarioComunidad` vuelve a comprobar contra `publicaciones_muro` que la publicacion
existe dentro del dia de ese registro. Venir de un evento no da ningun atajo.

**El oyente atrapa todo fallo y no propaga ninguno.** Publicar es lo que la persona vino a hacer;
cerrar un habito es un efecto secundario, y un efecto secundario no puede dejar el evento dando
vueltas en el outbox para siempre. El doble pago lo impiden el estado terminal y el bloqueo
pesimista, no ese `catch`.

**Consecuencia para el movil, NO tocada en este cambio:**
`features/habits/api/postDiarioComunidad.ts` (`cerrarHabitoPostDiarioComunidad`, agregado en E-117)
quedo **redundante**. El oyente corre async apenas commitea la publicacion, asi que casi siempre
gana la carrera y la llamada del movil encuentra el registro ya `COMPLETADO`: recibe un **409** y
devuelve `'fallo'`. **No es visible para la persona** — esa funcion atrapa el error, hace
`console.warn` y no interrumpe el flujo de publicar — pero deja dos efectos: el aviso *"su habito
de post en comunidad se completo con exito"* deja de mostrarse (solo se muestra con `'completado'`),
y el log del movil se llena de warnings. Lo correcto es retirar esa llamada del movil y refrescar la
tarjeta de Training por el evento local que ya existe. No se hizo aca porque el archivo es del
movil y esta sin commitear por otra sesion.

**Como evitar que vuelva a pasar:** vale la de E-117, ahora con un caso real detras — **un evento
de dominio con cero oyentes es codigo que no hace nada**. Un test de arquitectura que liste los
eventos publicados sin ningun `@ApplicationModuleListener` que los escuche habria marcado esto el
dia que se escribio `PublicacionCreadaEvent`. Sigue sin existir.

---

## E-122 — `-Djarmode=layertools` no existe en Spring Boot 4.1: el `Dockerfile` de cualquier tutorial rompe (2026-09-05) — **EVITADO**

**Sintoma exacto** (lo que habria pasado si se copiaba el `Dockerfile` estandar de cualquier guia
de Spring Boot 3.x, que es lo que devuelve casi toda busqueda):

```
Unsupported jarmode: 'layertools'
```

La etapa de extraccion del `Dockerfile` muere y no se genera ninguna imagen. El mensaje no dice
cual es el reemplazo ni desde que version cambio.

**Causa real:** el modo `layertools` quedo **deprecado en Spring Boot 3.3**, siguio funcionando con
aviso en 4.0, y **fue eliminado en 4.1** — la version exacta que usa este repo (4.1.1). El
reemplazo es otro comando, con otros flags:

```dockerfile
# ROTO en 4.1
RUN java -Djarmode=layertools -jar application.jar extract

# CORRECTO
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted
```

El `--layers` no es opcional: sin el, `extract` saca el jar entero en vez de partirlo en capas, y
se pierde todo el beneficio de cache de Docker sin ningun error.

**Solucion aplicada:** el `Dockerfile` usa `-Djarmode=tools ... extract --layers`, con el motivo
escrito en un comentario en el propio archivo para que nadie lo "arregle" de vuelta al comando
viejo copiando de internet.

**Como evitar que vuelva a pasar:** cualquier receta de infraestructura para Spring Boot que se
encuentre buscando esta escrita para 3.x, porque 4.x es reciente. Antes de copiarla, comprobar
contra las notas de version de 4.0 y 4.1 que la pieza que usa siga existiendo. Los tres candidatos
del mismo tipo, ya verificados en este cambio: `jarmode` (cambio), `annotationProcessorPaths`
(sigue igual, pero necesita `maven.compiler.proc=full` desde JDK 23) y `spring-boot:build-image`
(sigue existiendo; no se usa aca porque el `Dockerfile` propio da control sobre el usuario no-root
y la arquitectura).

---

## E-123 — Agregar Spring Cloud AWS tumba las 2400 pruebas si la propiedad se omite en vez de ponerse en `false` (2026-09-05) — **EVITADO**

**Sintoma exacto** que produce agregar `spring-cloud-aws-starter-parameter-store` sin nada mas:
**todo** test `@SpringBootTest` falla al levantar el contexto, y `./mvnw spring-boot:run` tampoco
arranca en local:

```
software.amazon.awssdk.core.exception.SdkClientException: Unable to load region from any of the
providers in the chain AwsRegionProviderChain(...)
```

No nombra Parameter Store ni AWS Systems Manager por ningun lado, asi que parece un problema de
credenciales de S3 (que es lo unico de AWS que este repo ya usaba) y se busca en el lugar
equivocado.

**Causa real, y es contraintuitiva:** `ParameterStoreAutoConfiguration` esta anotada

```java
@ConditionalOnProperty(name = "spring.cloud.aws.parameterstore.enabled",
                       havingValue = "true", matchIfMissing = true)
```

**`matchIfMissing = true`** significa que **con la propiedad ausente la autoconfiguracion se
ACTIVA**. Declara un bean `SsmClient` cuya construccion resuelve la region con
`DefaultAwsRegionProviderChain`, y sin region configurada eso explota durante el arranque. Es
decir: *no poner nada* no es lo mismo que *ponerlo en false*. Lo primero enciende la integracion;
solo lo segundo la apaga.

Verificado leyendo las anotaciones del bytecode de `spring-cloud-aws-autoconfigure-4.1.1.jar`
(`javap -v`), no suponiendo.

**Segundo lugar donde muerde, y es el que se olvida:** poner la propiedad solo en
`src/main/resources/application.yaml` **no alcanza**.
`src/test/resources/application.yaml` **reemplaza** a ese archivo en el classpath de test (mismo
nombre, gana el de test) en vez de complementarlo — cosa que el propio archivo de test ya
advertia en un comentario por E-40. Sin espejar el bloque, main arranca bien y la suite entera se
cae igual.

**Solucion aplicada:** el bloque

```yaml
spring:
  cloud:
    aws:
      parameterstore:
        enabled: false
      region:
        static: ${AWS_REGION:us-east-1}
```

esta en los **dos** `application.yaml` (main y test), y solo `application-prod.yaml` lo pone en
`true`. Es el mismo patron de espejado que ya tenian los hilos virtuales (C-14) y `open-in-view`
(C-18).

**El otro choque que se reviso y NO ocurre:** `S3AutoConfiguration` de Spring Cloud AWS declara
beans `S3Client` y `S3Presigner`, los mismos que crea a mano `AlmacenamientoS3Config`. No compiten
porque esa autoconfiguracion es `@ConditionalOnClass({S3Client, S3OutputStreamProvider})` y la
segunda clase vive en `spring-cloud-aws-s3`, que el starter de Parameter Store **no** arrastra
(confirmado con `dependency:tree`: solo entran `-parameter-store`, `-core`, `-autoconfigure` y
`spring-boot-starter`). **El dia que alguien agregue otro modulo de Spring Cloud AWS, esto hay que
revisarlo de nuevo:** si `spring-cloud-aws-s3` entra al classpath, esa autoconfiguracion se activa
y, con `renaser.storage.proveedor=noop` (el default, donde `AlmacenamientoS3Config` no crea nada),
seria ella la que cree los beans — con la misma falla de region de arriba.

**Como evitar que vuelva a pasar:** antes de sumar un starter que hable con un servicio externo,
mirar sus `AutoConfiguration.imports` y las condiciones de cada clase. La pregunta concreta no es
"traigo lo que necesito" sino **"que se enciende solo por estar en el classpath, y que pasa si no
hay credenciales"** — que es exactamente la propiedad que este repo cuida con sus adaptadores
`NoOp`, y la que un starter mal apagado rompe sin avisar.

---

## E-124 — Un guion doble dentro de un comentario rompe el `pom.xml` entero (2026-09-05) — **RESUELTO**

**Sintoma exacto:** cualquier comando de Maven muere antes de empezar, y el parser de XML de
Python da la misma queja:

```
xml.parsers.expat.ExpatError: not well-formed (invalid token): line 239, column 5
```

Maven, por su lado, dice `Non-parseable POM ... expected START_TAG or END_TAG not TEXT`. Ninguno
de los dos mensajes nombra la causa: los dos apuntan a una linea que, mirada de cerca, es prosa
dentro de un `<!-- ... -->` y parece intachable.

**Causa real:** **`--` no puede aparecer dentro de un comentario XML.** No es una rareza de Maven,
es la especificacion de XML. Se escribio, dentro de un comentario del `pom.xml`, una frase con un
guion doble usado como raya de puntuacion:

```xml
<!-- ... construye un cliente async
     -- un fallo que solo aparece en produccion ... -->
```

Es facil de meter sin darse cuenta precisamente en **este** repo, donde la convencion es escribir
comentarios largos y explicativos, y donde `--` se usa como separador de frase en los comentarios
de Java y en el YAML sin ningun problema. En Java y en YAML es legal; en XML no.

**Solucion aplicada:** se cambio el `--` por dos puntos. Cualquier otro signo sirve: `:`, `;`, una
raya larga, o un guion simple.

**Como evitar que vuelva a pasar:** despues de tocar el `pom.xml` (o cualquier XML), validarlo
antes de correr Maven, que tarda menos y da un mensaje mas claro:

```bash
python -c "import xml.dom.minidom; xml.dom.minidom.parse('pom.xml'); print('OK')"
```

Y para cazar el caso concreto sin depender de que rompa:

```bash
python -c "
import re
src = open('pom.xml', encoding='utf-8').read()
malos = [c for c in re.findall(r'<!--.*?-->', src, re.S) if '--' in c[4:-3]]
print('comentarios con doble guion:', len(malos))
"
```

---

## E-125 — `BUILD FAILURE` sin una sola prueba en rojo, con otra sesion compilando el mismo `target/` (2026-09-05) — **RESUELTO**

**Sintoma exacto:** en una tanda de tres `./mvnw clean test` seguidos sobre el mismo arbol (la unica
diferencia entre corridas eran comentarios), la del medio termino en `BUILD FAILURE` **sin ningun
fallo de assertion y sin ninguna clase de prueba en rojo**. Las corridas anterior y posterior dieron
las dos, identicas:

```
[INFO] Tests run: 2421, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Causa real: OTRA SESION estaba editando el mismo arbol mientras corria la suite.** Al terminar,
`git status` mostro modificados `pom.xml`, `SecurityConfig.java`, `application.yaml` (main y test) y
nuevos `Dockerfile`, `.github/` y `application-prod.yaml` — nada de eso lo habia tocado esta sesion.
Era el trabajo de despliegue que quedo registrado en E-122/E-123/E-124. El mecanismo exacto no se
puede fijar a posteriori, pero solo hay dos candidatos y los dos ya estan documentados: **E-123**
(agregar `spring-cloud-aws-starter-parameter-store` sin la propiedad en `false` tumba TODOS los
`@SpringBootTest` al levantar contexto) y **E-104** (dos builds de Maven sobre el mismo `target/`).
En cualquiera de los dos, el fallo es del entorno y no del codigo: la corrida siguiente, ya con el
arbol quieto, volvio a dar 2421 en verde.

**Como reconocerlo en un minuto, que es el motivo de esta entrada.** Antes de salir a buscar el bug
en el codigo, dos chequeos:

1. **¿La salida trae una linea `Tests run: N, Failures: F, Errors: E`?** Si no la trae, el build ni
   llego a correr pruebas: es del entorno. Si la trae con `Failures: 0, Errors: 0`, el codigo esta
   bien y fallo el build alrededor.
2. **`git status`** — si aparecen archivos modificados que uno no toco, hay otra sesion escribiendo
   sobre el mismo arbol. Esperar a que termine y volver a correr.

**Ruido que confunde y NO es la causa:** en el log de la corrida buena, el unico `[ERROR]` de todo
el build es `Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after
System.exit(0).` Aparece tambien cuando el build termina en verde.

**Trampa de grep, aprendida aca:** filtrar el log con
`grep -E "^\[INFO\] Tests run: ... Skipped: [0-9]+$"` **no matchea nunca** en esta maquina — los
saltos de linea son CRLF y el `\r` queda antes del `$`. Un filtro que no matchea se ve identico a
un build que no imprimio nada, y de ahi salio la mitad de la confusion. Filtrar sin anclar el final:
`grep -E "Tests run: [0-9]+, Failures"`.

---

## E-126 — `ControlCuotaRedisAdapterTest` falla TODAS las noches a partir de las 19:00 de Lima: el test arma la clave en UTC (2026-09-05) — **RESUELTO**

**Sintoma exacto:** `./mvnw clean test` en verde a las 18:50 y en rojo a las 19:20, **sin un solo
cambio de codigo entre las dos corridas**. Tres fallos, los tres en la misma clase:

```
ControlCuotaRedisAdapterTest.elTtlSeFijaUnaSolaVezYNoSeRenuevaEnConsumosPosteriores
  Expecting actual:  -2L  to be greater than:  0L

ControlCuotaRedisAdapterTest.liberarSobreUnaClaveExistenteLaDecrementaSinTocarElTtl
  expected: "1"  but was: null

ControlCuotaRedisAdapterTest.unaClaveEnvenenadaSinTtlSeAutoreparaEnElSiguienteConsumo
```

El `-2L` es la pista: en Redis, `TTL` devuelve **-2 cuando la clave no existe**. No es que el TTL
este mal — **el test esta mirando otra clave**.

**Causa real: el arreglo de zona horaria se aplico al adaptador y NO a su test.** El propio javadoc
de `ControlCuotaRedisAdapter` cuenta que la clave se armaba con la fecha UTC y se corrigio a la del
padron ("misma familia que E-105"):

```java
private static final ZoneId ZONA_PADRON = ZoneId.of("America/Lima");
private LocalDate hoyDelPadron() { return clock.now().atZone(ZONA_PADRON).toLocalDate(); }
```

Pero el helper del test se quedo en la version vieja:

```java
// ControlCuotaRedisAdapterTest:146
private static String claveDeHoy(UserId actorId) {
    return CLAVE_PREFIJO + actorId.value() + ":" + LocalDate.now(ZoneOffset.UTC);   // <-- UTC
}
```

Entre las **19:00 y la medianoche de Lima** (UTC-5) la fecha UTC ya es la del dia siguiente, asi que
la clave que arma el test (`...:2026-09-06`) no es la que escribe el adaptador (`...:2026-09-05`).
Verificado en vivo en el momento del fallo: `date` daba `Sat Sep 5 19:21 HPS` y `date -u` daba
`Sun Sep 6 00:21 UTC`.

**No es flakiness: es determinista dentro de esa franja de cinco horas.** Falla siempre despues de
las 19:00 y pasa siempre antes. Por eso puede convivir mucho tiempo con un CI "en verde" — depende
de a que hora se corra.

**Por que no se arreglo en su momento:** aparecio mientras se cerraban E-120/E-121, en otro modulo
(`rag`) y sin relacion con ese trabajo. Regla 00: un segundo bug encontrado de paso se **reporta**,
no se arregla en el mismo cambio. Se tomo despues, como tarea propia.

**Solucion aplicada (2026-09-05, rama `claude/exciting-franklin-643f14`):** el helper del test dejo
de recalcular la fecha por su cuenta y ahora la deriva **por el mismo camino que el adaptador** —
el puerto `Clock` y la zona del padron:

```java
private static final ZoneId ZONA_PADRON = ZoneId.of("America/Lima");   // espejo del adaptador
@Autowired private Clock clock;

private String claveDeHoy(UserId actorId) {
    LocalDate hoyDelPadron = clock.now().atZone(ZONA_PADRON).toLocalDate();
    return CLAVE_PREFIJO + actorId.value() + ":" + hoyDelPadron;
}
```

Se eligio pasar por `Clock` y no el minimo `LocalDate.now(ZONA_PADRON)` a proposito: con el `Clock`
inyectado, test y adaptador comparten **una sola** expresion, asi que tampoco divergen si algun dia
este contexto monta un `FixedClock`. El metodo dejo de ser `static` por eso. La alternativa que
proponia esta entrada (exponer la clave package-private desde el adaptador) se descarto: obliga a
abrir la API del adaptador de produccion para comodidad del test.

**Como se verifico** — a proposito **dentro de la franja que rompe**, 19:39-19:45 hora de Lima
(`date` = `Sat Sep 5 19:39 HPS`, `date -u` = `Sun Sep 6 00:39 UTC`: las dos fechas distintas):

| Corrida | Codigo | Resultado |
|---|---|---|
| 1 — reproduccion | el test **viejo**, sin tocar | `Tests run: 5, Failures: 3` — los tres fallos exactos de arriba: TTL `-2`, TTL `-1`, `expected "1" but was null` |
| 2 — arreglo | el test **nuevo** | `Tests run: 5, Failures: 0` |
| 3 — suite completa | `./mvnw clean verify` | `Tests run: 2407, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS` |

La corrida 1 es la que le da valor a la 2: prueba que en ese mismo instante el codigo viejo fallaba,
asi que el verde de la 2 es del arreglo y no de la hora. **Fuera de la franja no se volvio a correr**
(habria que esperar a pasada la medianoche de Lima), pero ahi el test ya pasaba antes — la franja
cubierta es justamente la unica en la que fallaba.

**En el mismo build fallo tambien** `CodigoVerificacionEmailRedisAdapterTest.unCodigoVencidoYaNoSePuedeVerificar`
("Expecting value to be false but was true"), pero **eso si fue flakiness**: paso al reejecutar la
clase sola. Son dos cosas distintas y conviene no confundirlas.

**Confirmado desde una segunda sesion (la de infraestructura de CI, D-114), con dos datos que la
primera no tenia:**

1. **No lo causa el `pom.xml` nuevo.** Como el bug aparecio el mismo dia en que se agregaron JaCoCo,
   failsafe y Spring Cloud AWS, lo primero fue descartarlos: se corrio esa sola clase sobre una copia
   aislada del checkout con el **`pom.xml` de `HEAD`**, sin ninguno de esos cambios. **Mismos 3
   fallos, mismas lineas.** Es preexistente y depende solo de la hora.

   ```bash
   git show HEAD:pom.xml > pom.xml    # sobre una copia aislada
   ./mvnw -B -ntp test -Dtest=ControlCuotaRedisAdapterTest
   # -> Tests run: 5, Failures: 3   (identico)
   ```

2. **Los runners de GitHub Actions corren en UTC.** El workflow `.github/workflows/ci.yml` que se
   agrego el mismo dia corre en cada push y cada PR, asi que va a ver esta ventana igual que una
   maquina de Lima: **el CI se va a poner rojo todas las noches por este motivo, no por el codigo del
   PR**. Queda anotado en `docs/DESPLIEGUE_Y_CI.md` §3.2 como lo primero a mirar ante un fallo
   nocturno inexplicable.

**Como evitar que vuelva a pasar** — es la regla 03 ("escribir el test que hubiera atrapado el bug")
leida al reves: cuando se corrige un bug de zona horaria en produccion, **hay que revisar si el test
duplicaba el mismo calculo**. Si lo duplica, quedan dos fuentes de verdad y solo se arreglo una; el
test sigue verde en la franja comoda del dia y miente sobre lo que verifica. La franja peligrosa en
este proyecto es **00:00-05:00 UTC**, que en Lima es la tarde-noche del dia anterior.


---

## E-127 — Flyway no puede aplicar `V1` en RDS: "permission denied to change default privileges" (2026-09-05) — **RESUELTO**

**Sintoma exacto**, corriendo Flyway contra la instancia RDS recien creada:

```
Migrating schema "public" to version "1 - baseline renaser"
ERROR: Migration of schema "public" to version "1 - baseline renaser" failed! Changes successfully rolled back.
SQL State  : 42501
Message    : ERROR: permission denied to change default privileges
Line       : 1494
```

Contra el Postgres local en Docker la misma migracion pasa sin problema. Solo falla en RDS.

**Causa real:** en Amazon RDS **el usuario maestro NO es superusuario**. Es una diferencia
deliberada del servicio administrado, no un permiso mal puesto. `V1` crea tres roles de carril
(`renaser_migraciones`, `renaser_escritura`, `renaser_lectura`) y despues hace:

```sql
ALTER DEFAULT PRIVILEGES FOR ROLE renaser_migraciones IN SCHEMA renaser
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO renaser_escritura;
```

`ALTER DEFAULT PRIVILEGES FOR ROLE <r>` exige ser superusuario **o miembro de `<r>`**. En el
Postgres local se conecta como `postgres`, que es superusuario, y por eso nunca se noto. El
usuario maestro de RDS no es ninguna de las dos cosas.

**Solucion aplicada — sin tocar la migracion.** El baseline esta congelado (D-40) y ademas ya
estaba aplicado en local: editarlo habria roto la suma de verificacion de Flyway ahi. Como el
bloque que crea los roles en `V1` es idempotente (`IF NOT EXISTS`), alcanza con **pre-crear los
roles y darle membresia al usuario maestro** antes de correr Flyway:

```sql
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='renaser_migraciones') THEN CREATE ROLE renaser_migraciones NOLOGIN; END IF;
    -- idem renaser_escritura, renaser_lectura
END $$;
GRANT renaser_migraciones TO renaser;
GRANT renaser_escritura   TO renaser;
GRANT renaser_lectura     TO renaser;
```

Con eso `V1` encuentra los roles creados, no hace nada en su `DO`, y el `ALTER DEFAULT PRIVILEGES`
procede. Resultado: **32 migraciones aplicadas, v33, 94 tablas, `vector` y `pgcrypto` instaladas.**

**Como evitar que vuelva a pasar:** este paso de arranque hay que correrlo en **cada base RDS
nueva** (produccion, pruebas, la que sea) antes del primer Flyway. Esta escrito en
`docs/INFRA_S3_BUCKET.md` y conviene moverlo al runbook de despliegue cuando exista.

**La leccion general:** "pasa en local" y "pasa en el servicio administrado" no son lo mismo
cuando la migracion hace DDL de privilegios. Postgres local corre como superusuario; RDS, Cloud
SQL y Aurora no te dan superusuario nunca. Toda migracion que use `ALTER DEFAULT PRIVILEGES`,
`CREATE EXTENSION` de extensiones no permitidas, o `ALTER SYSTEM`, es candidata a fallar recien
en el primer despliegue real.

---

## E-128 — El chip de evidencia dice "SUBIR" sobre un archivo ya guardado, pasadas las 20 evidencias (2026-09-05) — **RESUELTO en habitos**

**Sintoma:** el aprendiz sube una evidencia, la ve guardada, y al volver a Training el chip de ese
habito sigue diciendo **SUBIR** en vez de **VER**. No pasa desde el principio: empieza a pasar
cuando la persona acumula mas de 20 evidencias.

**Causa real:** el movil cruzaba los registros del dia contra `GET /api/v1/evidence`, que devuelve
**una pagina de 20**. A partir de la fila 21 el cruce ya no encuentra la evidencia y concluye que
no existe. Con el uso normal del programa —varias evidencias por dia durante 90 dias— el umbral se
cruza en la primera semana.

**Por que no se resolvio filtrando en el cliente**, que era el arreglo obvio: para pedir "solo las
de hoy" el movil tendria que saber **cuando empieza el dia de esa persona**, y no conoce
`participantes_programa.timezone` — usaria la zona del dispositivo. Es la familia de E-91 y E-105
otra vez, y habria reproducido el mismo sintoma por una causa nueva y mas dificil de ver: un
aprendiz viajando, o con el telefono en otra zona, perderia evidencia legitima del filtro. Ademas
el filtro **no elimina la paginacion**: un habito admite varias evidencias, asi que el cliente
igual necesitaria recorrer `nextCursor`.

**Solucion aplicada:** el servidor responde el dato ya resuelto. `RegistroHabitoConCatalogoResponse`
gana `tieneEvidencia`, alimentado por una API publica nueva de `evidence`
(`RegistrosConEvidenciaFinder`) que responde en lote "de estos registros, cuales ya tienen
evidencia". Sin acoplamiento nuevo —`habits` ya dependia de `evidence.api`— y sin migracion: el
indice parcial `evidencias_registro_idx` ya estaba en el baseline.

**Como evitar que vuelva a pasar:** cuando el cliente necesita cruzar dos listas del servidor para
saber algo, y una de las dos esta paginada, el cruce esta mal por construccion — funciona en las
pruebas y se rompe en cuanto los datos crecen. El dato derivado lo calcula quien tiene las dos
listas completas, que es el servidor.

**Queda pendiente:** las rocas. `GET /api/v1/rocks/today` no tiene el campo equivalente, asi que
VIDA Y NEGOCIO sigue cruzando contra el listado. Con una roca por dia las 20 filas cubren varias
semanas: es holgura, no garantia.

> **Nota de numeracion.** Esta entrada se escribio como E-120 en su rama de origen, pero ese numero
> ya estaba tomado en master por otra sesion que trabajaba en paralelo. Es la colision que advierte
> la regla 05: verificar el ultimo numero **usado en master**, no en la copia propia.

---

## E-129 — `ERR_OUT_OF_RANGE ... Received -2119958528` al armar el zip de la Lambda (2026-09-05) — **RESUELTO**

**Sintoma exacto**, al empaquetar el codigo del panel de solicitudes (`admin-panel/scripts/empaquetar.mjs`):

```
RangeError [ERR_OUT_OF_RANGE]: The value of "value" is out of range. It must be >= 0 and <= 4294967295. Received -2119958528
    at checkInt (node:internal/buffer:74:11)
    at Buffer.writeUInt32LE (node:internal/buffer:707:10)
```

**Causa real:** la linea que escribe los permisos unix del archivo dentro del zip:

```js
cabeceraCentral.writeUInt32LE(0o100644 << 16, 38);
```

En JavaScript **los operadores de bits trabajan sobre enteros con signo de 32 bits**. `0o100644`
es 33188; `33188 << 16` son 2 175 008 768, que pasa `2^31`, asi que el resultado vuelve como
**negativo**: -2 119 958 528. `writeUInt32LE` solo acepta valores sin signo, y explota.

No tiene nada que ver con el zip, con Node 24 ni con la Lambda: es aritmetica de JS.

**Solucion aplicada:** reinterpretar el resultado como sin signo con `>>> 0`, que es el unico
operador de JS que devuelve un entero sin signo de 32 bits:

```js
cabeceraCentral.writeUInt32LE((0o100644 << 16) >>> 0, 38);
```

**Como evitar que vuelva a pasar:** **todo `<<` que pueda tocar el bit 31 lleva `>>> 0` pegado.**
Aparece siempre en el mismo tipo de codigo: formatos binarios (zip, PNG, protocolos), permisos
unix, mascaras de bits, colores RGBA. La senal de alarma es ver un `<< 16`, `<< 24` o `<< 31`
seguido de una escritura sin signo.

Verificado despues del arreglo: el zip generado se abre con `Expand-Archive` y el archivo sale
con el tamano exacto original.

---

## E-130 — La Function URL responde `403 AccessDeniedException` aunque la politica de recurso sea la correcta (2026-09-05) — **RESUELTO**

**Sintoma exacto.** Function URL recien creada con `--auth-type NONE` y su permiso puesto con
`add-permission`. Cualquier `GET` a la URL devuelve:

```
HTTP/1.1 403 Forbidden
x-amzn-ErrorType: AccessDeniedException

{"Message":"Forbidden. For troubleshooting Function URL authorization issues, see: https://docs.aws.amazon.com/lambda/latest/dg/urls-auth.html"}
```

Y la politica de recurso es **exactamente** la que documenta AWS:

```json
{"Sid":"PermitirFunctionUrl","Effect":"Allow","Principal":"*","Action":"lambda:InvokeFunctionUrl",
 "Resource":"arn:aws:lambda:us-east-1:302277511407:function:renaser-admin-panel",
 "Condition":{"StringEquals":{"lambda:FunctionUrlAuthType":"NONE"}}}
```

**Lo que se descarto antes de encontrar la causa** (y por eso queda escrito, para no repetirlo):
no era propagacion (403 sostenido durante mas de 10 minutos), no era una SCP (la cuenta no
pertenece a ninguna organizacion), no era el `*` comido por el shell (la politica lo muestra
literal), no era la VPC, y no era `add-permission` puesto antes de `create-function-url-config`
(se borro y rehizo la URL en el otro orden, con el mismo 403).

**Como se aislo:** se creo una funcion **temporal, minima, fuera de la VPC**, con su URL publica
y su permiso, y devolvio el mismo 403. Con eso quedo claro que no era nada de la funcion del
panel sino algo de la cuenta. (La funcion temporal se borro.)

**Causa real: Lambda Block Public Access.** AWS agrego dos ajustes (`BlockPublicPolicy` y
`RestrictPublicResource`) que **en las funciones nuevas vienen en denegar por defecto** y
bloquean el acceso publico *sin importar lo que diga la politica de recurso*. Las funciones que
ya existian de antes quedaron en permitir; las nuevas, no.

**Agravante de diagnostico:** la AWS CLI instalada (2.34.47) y el boto3 instalado (1.43.9)
**todavia no traen esas operaciones**, asi que ni siquiera se puede consultar el ajuste desde la
linea de comandos. Verificado leyendo el modelo del servicio que trae la propia CLI
(`awscli/botocore/data/lambda/2015-03-31/service-2.json`): filtrar las operaciones por `/Public/i`
devuelve la lista vacia.

**Solucion aplicada: NO se desactivo la proteccion.** La Function URL se dejo en **`AWS_IAM`**,
que Block Public Access no toca porque no es acceso publico, y la firma SigV4 que un navegador
no sabe hacer la resuelve `admin-panel/scripts/abrir-panel.py`, un ayudante local que escucha en
`127.0.0.1`, firma con botocore y reenvia. Desactivar el bloqueo era la otra salida, pero es un
ajuste de seguridad de la cuenta y esa decision es del dueno del proyecto, no de quien despliega.

**De paso, otra trampa del mismo rato:** `delete-function-url-config` + `create-function-url-config`
**cambia el id de la URL**. La direccion vieja deja de existir. Si hay algo apuntando a la URL
(un marcador, un README), hay que actualizarlo — paso aca: se estuvo probando contra la URL
vieja creyendo que el 403 seguia.

**Como evitar que vuelva a pasar:** ante un `AccessDeniedException` en una Function URL con la
politica correcta, la primera hipotesis ya no es la politica: es **Block Public Access**. Y si
lo que se necesita es una URL que abra un navegador, `AWS_IAM` + ayudante local que firme es la
salida que no pide desactivar ninguna proteccion.

---

## E-131 — `JAVA_HOME` de `.claude/rules/03-pruebas.md` apunta a una carpeta que no existe (2026-09-05) — **ABIERTO, reportado**

**Sintoma exacto**, siguiendo al pie de la letra lo que dice la regla de pruebas:

```
/c/Users/panc1/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn: line 93: cd: /c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot: No such file or directory
The JAVA_HOME environment variable is not defined correctly,
this environment variable is needed to run this program.
```

**Causa real:** en esta maquina **no existe** `C:\Program Files\Eclipse Adoptium\` — ni la carpeta
padre. El JDK 25 que si esta instalado es:

```
C:\Program Files\Java\jdk-25.0.2      ->  java version "25.0.2" 2026-01-20 LTS
```

Que es, palabra por palabra, la ruta que `.claude/rules/03-pruebas.md` dice haber **corregido el
2026-09-05 (E-103)** por ser "una ruta que no existe en esta maquina". Quedo al reves: se cambio
una ruta correcta por una que no existe. El sintoma es identico al de E-103, asi que quien lo
busque va a encontrar la entrada vieja y la solucion contraria.

**Lo que se hizo:** correr las pruebas con `JAVA_HOME="C:/Program Files/Java/jdk-25.0.2"`, que
funciona.

**Lo que NO se hizo, a proposito:** editar `.claude/rules/03-pruebas.md`. Ese archivo es
configuracion del entorno de trabajo y la correccion la tiene que aprobar el dueno del proyecto,
sobre todo porque **ya se corrigio una vez en la direccion equivocada** — conviene confirmar cual
de las dos rutas es la buena en cada maquina antes de volver a tocarlo. Puede que en otra maquina
del equipo si exista Adoptium, y entonces lo correcto no sea reemplazar la ruta sino listar las dos.

---

## E-132 — `MSYS_NO_PATHCONV=1` y `fileb://` no conviven: la CLI recibe `/c/Users/...` y no la sabe abrir (2026-09-05) — **EVITADO**

Es la contracara de la regla que ya se usa en todo el proyecto. En Git Bash sobre Windows hay que
exportar `MSYS_NO_PATHCONV=1` para que un nombre como `/renaser/prod/DB_URL` no se convierta a
ruta de Windows (si no, la CLI responde *"Parameter name must be a fully qualified name"*).

**Pero esa misma variable apaga la conversion para TODOS los argumentos**, incluidos los que si
son rutas de verdad. Con ella activa, `--zip-file "fileb://$PWD/panel.zip"` le pasa a `aws.exe`
un `fileb:///c/Users/...`, que en Windows no existe. Lo mismo con `node /c/Users/...script.mjs`.

**Como se evito:** convertir la raiz una sola vez con `cygpath -m`, que devuelve `C:/Users/...`
—entendible por bash *y* por los binarios de Windows— y usar esa forma en todos los argumentos
que son rutas:

```bash
RAIZ="$(cygpath -m "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)")"
```

**Regla practica:** en un script que exporta `MSYS_NO_PATHCONV=1`, toda ruta que vaya a un `.exe`
pasa antes por `cygpath -m`. Los nombres que NO son rutas (parametros de SSM, ARNs) van tal cual,
que es justo para lo que se puso la variable.

---

## E-133 — `TokenResetContrasenaRedisAdapterTest` falla 1 de cada tantas corridas: el test corre una carrera contra el reloj (2026-09-05) — **ABIERTO, reportado**

**Sintoma exacto**, en una corrida completa de `./mvnw clean test` (2429 pruebas, 1 en rojo):

```
Expecting an empty Optional but was containing value: c716dba0-0df6-415a-b8b6-1ce45072ba38
	at ...TokenResetContrasenaRedisAdapterTest.unTokenVencidoYaNoSePuedeConsumir(TokenResetContrasenaRedisAdapterTest.java:69)
```

**La misma clase, corrida sola, pasa 5 de 5.** Esa asimetria es la firma del problema.

**Causa real:** el test no verifica una regla, verifica una carrera:

```java
String token = tokenResetContrasenaPort.generar(usuarioId, Duration.ofMillis(500));
Thread.sleep(900);
assertThat(tokenResetContrasenaPort.consumir(token)).isEmpty();
```

El margen es de **400 ms** entre que el TTL vence y que el test mira. En una corrida completa
—2429 pruebas, Testcontainers levantando contenedores, la maquina compilando— ese margen se
consume solo con una pausa de GC o con el planificador del sistema operativo dandole el CPU a
otra cosa. Ademas la expiracion de Redis **no es instantanea al milisegundo**: Redis borra las
claves vencidas de forma perezosa (al tocarlas) y por muestreo periodico, asi que "vencida" y
"ya no esta" no son el mismo instante.

**No es un error del codigo de produccion.** `TokenResetContrasenaRedisAdapter` esta bien: el
token vencido no se puede consumir. Lo que esta mal calibrado es la prueba.

**Como se detecto que era eso y no una regresion:** la corrida completa fallo en una sesion que
**no toco una sola linea de Java** (se estaba construyendo el panel de solicitudes, en
`admin-panel/`, fuera del build de Maven). Con cero cambios en el codigo bajo prueba, una
prueba que falla y despues pasa solo puede ser inestable.

**Lo que NO se hizo, a proposito:** arreglar el test. Estaba fuera del alcance de la tarea en
curso (regla 00: encontrar un segundo problema mientras se resuelve el primero es motivo para
**reportarlo**, no para arreglarlo en el mismo cambio).

**Como arreglarlo cuando se tome:** no subir el `sleep` —eso solo alarga el build y deja la
carrera igual, mas lenta—. Las dos salidas buenas son (a) borrar la clave a mano en vez de
esperar a que Redis la venza, si lo que se quiere probar es "no existe → vacio", o (b) usar el
puerto `Clock` inyectable que este repo ya tiene, y adelantar el reloj en vez de dormir. La
segunda es la que va con la regla 02: **el tiempo entra por el puerto `Clock`, no por
`Thread.sleep`**.

**La leccion general:** un test que duerme es un test que apuesta. Si la apuesta es a 400 ms,
la va a perder el dia que la maquina este ocupada — y va a perderla en CI, no en la maquina de
quien lo escribio.

---

## E-134 — La indexacion de la base de conocimiento se frena a los ~1.000 chunks: la API key de Google esta en el tramo gratuito, que da 1.000 embeddings por dia (2026-09-06) — **ABIERTO, bloqueado por cuota externa**

**Sintoma exacto**, en los logs del contenedor de produccion:

```
com.google.genai.errors.ClientException: 429 . You exceeded your current quota, please check
your plan and billing details. For more information on this error, head to:
https://ai.google.dev/gemini-api/docs/rate-limits.
{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{
  "quotaMetric":"generativelanguage.googleapis.com/embed_content_free_tier_requests",
  "quotaId":"EmbedContentRequestsPerDayPerProjectPerModel-FreeTier",
  "quotaDimensions":{"location":"global","model":"gemini-embedding-1.0"},
  "quotaValue":"1000"}]}
{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"38s"}
```

Del lado del que indexa el sintoma es otro y **enganya**: el script no ve un 429, ve un **HTTP
500**, y ademas ve que *algunos* chunks entran. Parece un backend inestable, no una cuota.

**Causa real:** `quotaValue: 1000`. La clave de Google esta en el **tramo gratuito**, que
permite **1.000 `embedContent` por dia, por proyecto y por modelo**. Cada chunk que se indexa
es exactamente una llamada a `embedContent`. Las 124 transcripciones de los cursos, troceadas a
450 palabras con 60 de solape, son **2.526 chunks**: dos veces y media la cuota diaria. El
trabajo no entra en un dia por construccion, y no hay nada que arreglar en el codigo.

**Por que se veia como "el backend se reinicio":** la cuota no degrada, corta en seco. Los
chunks entraban a **0,4–1,6 s cada uno** (62 chunks de una leccion en 101 s) hasta la llamada
1.000, y a partir de ahi cada uno tarda **mas de 80 s** — Spring AI reintenta con la espera de
38 s que pide Google, y de vez en cuando alguno pasa. Una leccion que quedo en `ok=39 err=4` no
perdio 4 pedazos por un reinicio: perdio los 4 que cayeron del otro lado del corte.

**El agravante que multiplica el consumo:** el backend deja escapar la `ClientException` de
Google, asi que el endpoint responde **500**, no 429. Un cliente razonable interpreta 500 como
"error transitorio del servidor" y **reintenta** — y cada reintento es otra llamada a
`embedContent` contra la misma cuota agotada, con los reintentos internos de Spring AI encima.
La cuota se gasta mas rapido justamente cuando ya no queda.

**Solucion aplicada:** ninguna en el codigo — es una cuota de un tercero. Se detuvo la
indexacion al detectarla, en vez de seguir generando huecos.

**Como evitar que vuelva a pasar:**

1. **Antes de un trabajo masivo de embeddings, contar los chunks y compararlos con la cuota
   del dia.** 2.526 contra 1.000 se sabia antes de empezar.
2. **Traducir el 429 de Google a un 429 nuestro**, no a un 500. Es la diferencia entre un
   cliente que espera y uno que reintenta y quema lo que queda. Es un `@ExceptionHandler` en
   `GlobalExceptionHandler` sobre `ClientException` mirando el codigo.
3. **No reponer una leccion a medias borrandola entera.** Cada chunk guarda
   `metadatos->>'parte' = 'N/M'`, asi que se sabe *exactamente* que pedazos faltan:
   `SELECT leccion_id, string_agg(split_part(metadatos->>'parte','/',1), ',') FROM
   renaser.base_conocimiento GROUP BY leccion_id`. Reponer 4 chunks en vez de rehacer 43
   ahorra 39 llamadas de una cuota que es el recurso escaso.
4. Las cuotas **RPD de Google se reinician a medianoche del Pacifico**, no a medianoche UTC ni
   local. Planificar los lotes con esa hora.

**La leccion general:** cuando un trabajo masivo depende de una API de terceros, el limite que
importa no es la latencia ni el tamanio del lote — es la cuota diaria, y conviene mirarla
**antes** de arrancar. Y un 429 ajeno que se convierte en un 500 propio hace que todo el
sistema empuje justo en la direccion equivocada.

---

## E-135 — El compositor del Muro ofrecia tres categorias que no existen en el catalogo, y la elegida no se mandaba nunca (2026-09-06) — **RESUELTO en el movil; el catalogo de produccion sigue vacio**

**Sintoma exacto**, palabras del duenio del proyecto:

> "en el muro que esten esas categorias, no las que tenemos"

El compositor del Muro (`ComunidadScreen.tsx`, modal "NUEVA PUBLICACION") mostraba tres
pastillas:

```
🔥 VICTORIA SOMATICA    ⚡ ALTO RENDIMIENTO    🧠 REFLEXION
```

Ninguna de las tres existe en `renaser.categorias_muro`. El catalogo real que administra
ADMIN/ALCHEMIST son otras cinco: `REVELACIONES`, `AGRADECIMIENTO`, `AYUDA`, `PRESENTACION`
(de sistema) y `LOGROS`.

**Causa real — son dos defectos apilados, y el segundo escondia al primero:**

1. **Las pastillas eran literales compilados en el bundle.** Un array escrito a mano en
   `ComunidadScreen.tsx` y la union de tipos `PostTag` en `src/types/schema.types.ts`
   (`'🔥 VICTORIA SOMATICA' | '⚡ ALTO RENDIMIENTO' | '🧠 REFLEXION' | '👑 OFICIAL'`). El
   catalogo del servidor no se consultaba en ningun lado.

2. **La categoria elegida no llegaba nunca al backend.** `handlePublishPost` llamaba a
   `publicarOptimista(texto, fotos, nombreUsuario)` y ese hook llamaba a
   `wallApi.publicarEnMuro(texto, media)` — **sin el tercer argumento**, que el propio
   `wallApi` ya aceptaba. El estado `newPostTag` se escribia al tocar una pastilla y no lo
   leia nadie mas que el resaltado visual. Todas las publicaciones del Muro salieron con
   `category: null`.

**El punto que importa para la proxima vez:** el defecto 2 hizo invisible al defecto 1. Si la
etiqueta se hubiera mandado de verdad, el backend habria respondido **400 `"Categoria
desconocida: 🔥 VICTORIA SOMATICA"`** (`PublicacionMuroService.publicar`, que valida contra
`clavesExistentes()`) y el problema se habria visto el primer dia. Un campo muerto no falla:
se ve bien y no hace nada.

**Habia una pista escrita, y decia lo contrario de lo que pasaba.** `wallApi.ts` ya tenia
`obtenerCategoriasMuro()` y su esquema Zod desde antes, con este comentario encima:

> *"Sin UI que la use hoy: la pestania 'muro' no tiene selector de categorias (no hay pills en
> el disenio actual...)"*

Era falso: el selector existia desde siempre, con las categorias inventadas. La funcion
correcta estaba escrita y sin enchufar, a diez lineas de la UI que la necesitaba.

**Solucion aplicada** (solo movil, `Renaser-90-dias-frontend-`):

- `src/features/community/hooks/useCategoriasMuro.ts` (nuevo) — pide
  `GET /api/v1/wall/categories`, mismo patron que `useMiCelula`/`useWallFeed`. No reordena ni
  filtra: el backend ya devuelve solo las activas y ordenadas por `orden`.
- `ComunidadScreen.tsx` — las pastillas se pintan desde el catalogo (`emoji` + `label`), el
  estado pasa a guardar la **clave** (`REVELACIONES`) y no el texto visible, ninguna viene
  preseleccionada (elegir es opcional: `category` es opcional en `POST /api/v1/wall`) y volver
  a tocar la elegida la desmarca. Tres estados de red cubiertos —cargando, fallo con
  "Reintentar", catalogo vacio— y en ninguno el compositor queda inutilizable: sin catalogo se
  publica igual, sin categoria.
- `useWallFeed.publicarOptimista` recibe la categoria y la pasa a `publicarEnMuro`.
- `PostTag` pasa a ser `string`: una union de literales no puede describir un catalogo que el
  administrador edita en caliente.

**Lo que NO se toco, y por que — el catalogo de produccion esta vacio:**

```
SELECT clave, etiqueta, emoji, orden, activa, es_sistema FROM renaser.categorias_muro;
(0 rows)
```

`docs/MODULO_COMMUNITY.md` §1.2 documenta las cinco filas del catalogo real, y en la misma
linea aclara que **eso no se convirtio en SQL ejecutable a proposito** (instruccion del
supervisor, CM-15: queda para la fase de migracion de datos). No hay ninguna migracion Flyway
que las siembre. Consecuencia: hasta que existan esas filas, el compositor muestra el estado
vacio — correcto, pero sin pastillas. Sembrarlas no se hizo por cuenta propia porque las dos
fuentes disponibles **no coinciden**: el documento dice `PRESENTACION` con 👋 en orden 5 y
`LOGROS` en 3; el panel que ve el duenio muestra 👏 en orden 4 y `LOGROS` en 5. Elegir una de
las dos seria inventar el dato.

**Como evitar que vuelva a pasar:**

1. **Un catalogo que se administra en caliente no se duplica nunca como literales en el
   cliente.** Si el panel promete "los cambios llegan a la app sin publicar una version
   nueva", cualquier lista compilada en el bundle rompe esa promesa por construccion.
2. **Un comentario que dice "sin UI que la use hoy" sobre una funcion de API es una alarma, no
   una nota.** O sobra la funcion, o la UI existe y esta resolviendo lo mismo por su cuenta —
   que es lo que pasaba. Antes de escribir esa frase, buscar la UI.
3. **Un `useState` cuyo valor no lo lee nadie fuera del render es un campo muerto.** Al agregar
   un control nuevo al compositor, seguir el valor hasta la llamada de red; si no llega, el
   control es decorativo.
4. **Al portar una pantalla del disenio viejo, los valores de negocio del mock no son datos.**
   Las tres categorias vienen del disenio original, no de ningun backend.

**La leccion general:** cuando el servidor ya expone un catalogo y el cliente igual lleva el
suyo escrito a mano, el sintoma que se ve (nombres equivocados) casi nunca es el peor problema.
El peor es el que no se ve: el dato elegido no llegaba a ningun lado, y por eso nadie se entero
en meses.

---

## E-136 — El recuadro de firma se corta a 300 px en web: un `<Svg>` sin `width`/`height` cae al tamanio de objeto por defecto de CSS (2026-09-06) — **RESUELTO**

**Sintoma exacto**, reportado por el dueno del proyecto sobre el build web desplegado en Vercel:

> "la firma no me deja poner de lado derecho mas como si estuviera bloqueado, es en terminos y condiciones"

En el recuadro **FIRMA DE ACEPTACION LEGAL (CON TU DEDO)** (`TerminosScreen`) el trazo dorado se
dibuja en los dos tercios izquierdos de la caja y el tercio derecho queda muerto: el dedo/mouse se
mueve, la etiqueta pasa a "✓ TRAZADO" (o sea, el componente **si** registra que hay firma), pero no
aparece linea. El borde del recuadro si llega hasta el final. En la app nativa no pasa.

**Causa real** — nada que ver con el `PanResponder` ni con las coordenadas del toque, que estaban
bien. `src/components/SignatureCanvas.tsx` (repo del frontend) dibujaba los trazos sobre:

```tsx
<Svg style={StyleSheet.absoluteFill}>
```

En **web**, `react-native-svg` no renderiza una vista nativa: emite un `<svg>` del DOM
(`lib/module/web/WebShape.js` → `unstable_createElement` de `react-native-web`). Y
`StyleSheet.absoluteFill` de `react-native-web` es exactamente
`{position:'absolute', left:0, right:0, top:0, bottom:0}` — **sin ancho ni alto**.

Un `<svg>` es un **elemento reemplazado**. Para un elemento reemplazado posicionado en absoluto con
`width:auto`, CSS **ignora** `right` (y `bottom`) y usa su tamanio intrinseco; como un `<svg>` sin
`width`/`height` no tiene ninguno, cae al *default object size* de CSS: **300 × 150 px**. O sea que
el lienzo real era una franja fija de 300 px pegada al borde izquierdo, midiera lo que midiera el
recuadro visible. Todo lo que se dibujaba mas alla de x = 300 caia fuera del viewport del SVG, que
recorta su propio contenido por definicion.

Medido en Chrome, sobre el markup exacto que emite la libreria:

| Caso | Recuadro | `<svg>` resultante |
|---|---|---|
| Como estaba (`absoluteFill`, sin `width`/`height`) | 462 × 145 | **300 × 150** |
| Con `width="100%" height="100%"` | 462 × 145 | 460 × 143 |
| Con `width="100%" height="100%"`, caja angosta | 288 × 145 | 286 × 143 |

**Por que en nativo nunca se vio:** Yoga si estira un hijo absoluto que tiene los cuatro lados en 0,
asi que el `Svg` ocupaba la caja entera. Es mas: `react-native-svg` **omite a proposito** su default
de `width = height = '100%'` cuando la posicion es `absolute` (`lib/module/elements/Svg.js`,
`if (width === undefined && height === undefined && position !== 'absolute')`), contando con ese
estirado de Yoga. En web ese default es justamente el que faltaba, y la libreria no lo repone.

**Consecuencia que no era visible y si importa:** la firma se sube a S3 como **evidencia legal**
(`capturarComoPngBase64` → `guardarFirma`). En web esa captura la hace `html2canvas` sobre el
recuadro (`react-native-view-shot/lib/RNViewShot.web.js`), o sea que **el PNG guardado tenia el
mismo trazo cortado que se veia en pantalla**. No es solo estetico: las firmas de Terminos y del
Pacto hechas desde el build web quedaron truncadas en el bucket.

**Solucion aplicada** (`src/components/SignatureCanvas.tsx`, una linea):

```tsx
<Svg width="100%" height="100%" style={StyleSheet.absoluteFill}>
```

Porcentaje y no pixeles medidos con `onLayout`, a proposito: asi el lienzo sigue al recuadro en
cualquier ancho —movil angosto, tablet, web, y al redimensionar la ventana— sin numeros magicos y
sin el frame inicial en blanco que tendria una medicion. **Sin `viewBox`**, tambien a proposito:
1 unidad de usuario = 1 px, que es la escala en la que `locationX`/`locationY` graban los trazos;
un `viewBox` escalaria el dibujo. En nativo el cambio es inocuo: `Svg.render()` lee `width`/`height`
de `stylesAndProps` y termina aplicando `width:'100%', height:'100%'` sobre una caja sin padding —
el mismo tamanio que ya tenia.

**Sobre "el recuadro del Pacto si funciona": no.** Es el **mismo** componente — `SignatureCanvas` se
usa en `TerminosScreen`, `PactoScreen` y `ChapterCompromiso`, y los tres tenian el `<svg>` de 300 px.
Peor todavia: el recuadro del Pacto es **mas ancho** que el de Terminos en todos los anchos, porque
Terminos mete el lienzo dentro de una tarjeta con `padding: 16` + borde y el Pacto no:

| Ancho de ventana | Lienzo en Terminos | Lienzo en el Pacto |
|---|---|---|
| movil < 360 | W − 62 | W − 32 |
| movil / normal | W − 70 | W − 40 |
| tablet / web (`maxWidth: 560`) | 462 | 496 |

El margen muerto a la derecha del Pacto era siempre **mayor o igual** al de Terminos, asi que **no
existe ningun ancho de ventana donde el Pacto se vea bien y Terminos no**. La comparacion del
reporte solo cierra si el Pacto se firmo en el celular (nativo, donde no falla) o con un trazo
corto. El arreglo cubre los tres usos por igual.

**Como evitar que vuelva a pasar:**

- **Un `<Svg>` que se estira por estilo necesita `width`/`height` explicitos.** Al 2026-09-06 este
  era el **unico** `<Svg>` del frontend sin `width` (`grep -rn "<Svg" src | grep -v "width="` devuelve
  una sola linea): los ~40 de `Icon.tsx` y el de `FondoAnillos.tsx` ya lo pasan. Si aparece otro, la
  regla es la misma.
- **Apenas algo "se corta a media caja" en web, sospechar del 300 × 150.** Es el tamanio de objeto
  por defecto de CSS y aparece siempre que un elemento reemplazado se queda sin dimensiones.
- **Lo que se ve bien en nativo no prueba nada sobre web.** `react-native-web` y `react-native-svg`
  traducen el mismo JSX a mecanismos de layout distintos (Yoga vs CSS), y este bug vive exactamente
  en esa costura. Toda pantalla que se despliegue en Vercel hay que mirarla en el navegador, no solo
  en Expo Go.

**Lo que quedo sin verificar:** el arreglo **no se probo en la app corriendo** — no se levanto Expo
ni se hizo `expo export`. Lo verificado es el mecanismo, medido en Chrome sobre el markup exacto que
emite `react-native-svg` en web, mas `npx tsc --noEmit` en 0. **El frontend no tiene suite de
pruebas** (no hay script `test` en `package.json` ni un solo `.test.tsx`), asi que hoy no hay forma
de dejar un test de regresion, que es lo que la regla de pruebas pediria.

**Reportado y NO arreglado (regla 00, fuera de alcance):** los trazos se guardan como coordenadas
absolutas en pixeles (`M 132.4 70.1 L ...`, serializadas a JSON en `usuario`/S3). Si una firma
guardada se vuelve a mostrar en un recuadro mas angosto que aquel donde se dibujo —otro dispositivo,
o la misma persona en el celular despues de firmar en web— se va a ver cortada por la derecha otra
vez, ahora por una razon distinta. La salida seria guardar tambien el ancho del lienzo y reescalar
al reponer, o guardar los trazos normalizados a [0,1].


---

## E-137 — Un aprendiz recien registrado no podia usar ni crear habitos: tres defectos apilados sobre el mismo Dia 0 (2026-09-06) — **RESUELTO**

**Sintoma exacto**, palabras del duenio del proyecto:

> "me registre domingo pero yo quiero ordenar mis habitos para maniana no me deja xq no tengo la
> opcion de ver? (...) no puedo crear un habito tampoco durante el dia que voy"

En pantalla (Plan / "01. HABITOS (7 DIAS)"), cuenta `ricardoismael777@gmail.com`, domingo
2026-09-06:

- Selector de semana `LUN 31 · MAR 01 · MIE 02 · JUE 03 · VIE 04 · SAB 05 · DOM 06`, con **los
  siete dias en gris** y DOM 06 seleccionado.
- Los once habitos de MANIANA con **candado** y `FALTA 1 DIA` (`Pastilla Renacer`, `FALTAN 8 DIAS`).
- Un habito propio recien creado, `adas`, con el interruptor en **PAUSADO**.
- En los logs del backend, a las 12:55 UTC:
  `400 -> Bad Request: El valor de 'habitId' no tiene el formato esperado`.

**Los datos reales de produccion** (RDS `renaser-prod`, consultado por SSM el 2026-09-06 13:00 UTC):

```
usuarios:               id 96f7c5bf-00a5-4c76-93a3-69821ed5a20b, APRENDIZ/ACTIVO, creado 12:47 UTC
participantes_programa: dia_programa = 0, fecha_inicio = 2026-09-07, timezone = America/Lima,
                        programa_activado_en = 2026-09-06 12:51 UTC, dias_ajuste_programa = 0,
                        dia_programa_avanzado_el = NULL
habitos (PERSONAL):     0 filas      <- el habito `adas` de la captura NO EXISTE
desbloqueos_habito:     0 filas
registros_habito:       0 filas
```

**Lo primero que hay que descartar, y que aca NO era: no es un desfase de zona horaria.**
`fecha_inicio = 2026-09-07` es *maniana*, y es lo correcto — `ParticipacionPrograma.activarPrograma`
solo acepta `[hoy+1, hoy+3]` (D-66: "el reloj avanza a medianoche, firmar de tarde y elegir hoy
dejaria un Dia 1 de pocas horas"). Con `fechaInicio` posterior a hoy, `diaProgramaDerivado` devuelve
0 por definicion. El barrido horario (`AvanzarDiaProgramaScheduler`, `0 5 * * * *`) lo pasa a 1 a las
05:05 UTC = 00:05 en Lima, o sea puntual. **Nada que ver con E-91 ni E-105.** El `dia_programa = 0`
era correcto; lo que estaba mal era todo lo que el sistema hacia con ese 0.

**Causa real — son tres defectos independientes, y el tercero fabricaba la evidencia falsa del
cuarto sintoma:**

1. **Backend: el alta de habito personal explotaba en Dia 0.** `MisHabitosService.crear` pasaba
   `progreso.diaPrograma()` crudo a `HorarioHabito.crear`, cuyo invariante es `1..90`:

   ```
   java.lang.IllegalArgumentException: diaInicio fuera de rango 1..90: 0
       at ...horario.HorarioHabito.crear(HorarioHabito.java:48)
       at ...services.MisHabitosService.crear(MisHabitosService.java:140)
   ```

   Como el Dia 1 nunca puede ser hoy, **todo aprendiz pasa su primera jornada en dia 0**, asi que
   esto no era un caso de borde: era el 100% de las altas recien aprobadas. Ya estaba anotado como
   hueco abierto en `docs/informes/habits-personal-con-horario.md` ("bloquear antes con un mensaje
   claro? usar `diaInicio = 1`?", decision de negocio sin confirmar) — y **D-103 la confirmo el
   2026-09-04** para la operacion hermana `DesbloqueoHabitoService.resolverDiaDesbloqueo`
   (`Math.max(1, diaActual)`), solo que nadie la trajo hasta aca.

2. **Backend: el mismo 0 ponia candado sobre TODO el catalogo.**
   `MisHabitosService.consultar` calculaba `diasParaDesbloqueo = max(0, diaDesbloqueo - 0)`, asi que
   los 13 habitos del catalogo que arrancan el dia 1 (de 18 activos: `Pastilla Renacer` arranca el 8,
   `AUDIOTERAPIA SEMANAL` el 11 y los tres de domingo el 35) viajaban con `daysUntilUnlock = 1` y
   `locked = true`. En el
   movil `habit.locked` apaga el interruptor ACTIVO/PAUSADO **y** el selector de hora: el aprendiz
   veia su plan entero bajo llave justo la vispera de empezar — exactamente lo contrario de lo que
   D-103 decidio ("quien eligio empezar maniana tiene que poder armar su plan hoy").

3. **Movil: "Crear Habito" nunca llamo al backend.** `PlanScreen.handleSaveNewHabit` construia un
   `PlanHabit` en memoria con un id inventado (`habit_` + timestamp), lo empujaba al estado de
   React y anunciaba "Habito Creado". **No habia ni un `fetch`.** De ahi salen dos cosas:
   - El habito `adas` de la captura no existia en la base (0 filas) y desaparecia al recargar.
   - En cuanto el aprendiz tocaba su interruptor, el id falso viajaba a
     `PUT /api/v1/habit-unlocks/habit_1757...` — **el `400 "El valor de 'habitId' no tiene el
     formato esperado"` de los logs.**

4. **Movil: el `PAUSADO` del habito recien creado era consecuencia del 3.** Ese objeto falso nacia
   con `newHabitDays = {..., SAB: false, DOM: false}`, y la etiqueta se decide con
   `isDayActive = habit.days[selectedDay]`. Creado un **domingo**, con DOM seleccionado, salia
   `PAUSADO` recien nacido. No era una regla de negocio: era un valor por defecto del formulario.

5. **Movil: un domingo el selector de semana no tenia ningun dia planificable.** D-98 dejo
   `esPasado = indice <= indiceDeHoy` (hoy tambien se apaga) y la pestania inicial en
   `min(indiceDeHoy + 1, 6)`. Un domingo `indiceDeHoy` vale 6: los siete dias apagados y la pestania
   inicial cayendo sobre el propio domingo. El comentario de entonces asumia que esa semana cerrada
   "era la verdad de ese momento" — **y no lo era**: maniana existe, es el lunes siguiente, y
   simplemente no se estaba dibujando. Es literalmente el *"no tengo la opcion de ver"* del reporte.

**Solucion aplicada:**

| Defecto | Cambio |
|---|---|
| 1 y 2 | `MisHabitosService.primerDiaPlanificable(dia) = Math.max(1, dia)`, usado en `crear` (el `diaInicio` del horario) y en `consultar` (el dia contra el que se mide el desbloqueo). Es el MISMO `Math.max(1, ...)` que D-103 ya aplica en `DesbloqueoHabitoService`. **Solo cambia algo en el dia 0**: para `dia >= 1` devuelve el mismo valor |
| 3 y 4 | `PlanScreen.handleSaveNewHabit` ahora llama a `POST /api/v1/habits` (`habitsApi.crearHabitoPersonal`, nuevo) y arma la tarjeta con `mapearPlanHabit` **sobre la respuesta del servidor**, no a mano — id real, categoria real, los 7 dias reales. Si el servidor rechaza, se avisa y el modal queda abierto; nunca mas un "creado" que no se creo |
| 5 | `MOSTRAR_SEMANA_SIGUIENTE` en `PlanScreen`: cuando hoy es domingo se dibuja la semana siguiente completa y `ULTIMO_INDICE_NO_PLANIFICABLE` pasa a -1, con lo que sus 7 dias quedan abiertos y la pestania inicial es el lunes. Es lo que D-98 queria decir con "la pestania inicial pasa a ser MANIANA" |

**Como evitar que vuelva a pasar:**

- **Cuatro tests nuevos en `MisHabitosServiceTest`, todos rojos contra el codigo viejo** (dos con el
  `IllegalArgumentException` literal de produccion): `enDia0ElHabitoPersonalSeCreaYSuHorarioArrancaElDia1`,
  `enDia0ElHabitoPersonalSeCreaIgualConElRelojEnLaMadrugadaUtc` (reloj a las **02:00 UTC**, que en
  Lima todavia es el dia anterior — regla 03), `enDia0LosHabitosQueArrancanElDia1NoViajanBloqueados`
  y `enDia0UnHabitoQueArrancaMasAdelanteSigueBloqueado` (lo que NO se desbloquea de mas).
- **Un test de integracion nuevo contra Postgres real**, `CrearHabitoPersonalGeneraTrackTransaccionIT.
  enDia0ElHabitoSeCreaYSuHorarioArrancaElDia1`: importa que la fila entre de verdad, porque
  `horarios_habito.dia_inicio` tiene `CHECK (dia_inicio BETWEEN 1 AND 90)` en el baseline — un 0
  tampoco habria pasado la base.
- **Cuidado al tocar un test que "documenta" un bug en vez de arreglarlo.** En ese mismo IT,
  `siElHorarioEsInvalidoNoQuedaNingunHabitoHuerfano` usaba `dia_programa = 0` solo como forma comoda
  de hacer explotar `HorarioHabito.crear` y asi demostrar el rollback. Al arreglar el bug ese
  disparador dejo de existir y el test se puso rojo por la razon correcta. **No se borro**: se
  renombro a `siElHorarioNoSePuedeGuardarNoQuedaNingunHabitoHuerfano` y el fallo se inyecta ahora en
  el puerto (`@MockitoSpyBean SaveHorarioHabitoPort`), porque despues de este cambio **ningun comando
  aceptado puede construir un `HorarioHabito` invalido** y lo unico que queda por cubrir es el fallo
  de infraestructura en el segundo guardado — que es exactamente lo que `@Transactional` protege.
- **La leccion que se repite y conviene tener a mano: el Dia 0 no es un caso de borde, es el estado
  inicial de todas las cuentas.** D-66 garantiza que nadie empieza el mismo dia en que se aprueba,
  asi que cualquier `if (dia < 1)` o rango `1..90` aplicado al dia de programa de un aprendiz es un
  fallo para el 100% de los registros nuevos, no para un raro. D-103 ya lo arreglo en un endpoint;
  esto lo arreglo en otros dos. **Al tocar cualquier cosa que lea `diaPrograma`, la primera pregunta
  es "que hace esto con un 0?".**
- **Un campo de formulario que el backend no guarda es un bug esperando.** La causa 4 no fue una
  regla mal escrita: fue un valor por defecto de un selector que nunca viajo a ningun lado. Es el
  mismo patron que E-135 ("un campo muerto no falla: se ve bien y no hace nada"). El modal de crear
  habito quedo reducido a los tres datos que el servidor guarda de verdad (nombre, categoria, hora);
  se sacaron el selector de icono (el icono lo deriva `mapearPlanHabit` de la categoria), el de
  momento del dia (lo deriva de la hora), el de duracion (no existe en el modelo) y el de dias de la
  semana (un habito personal es siempre `TipoDia.TODOS`).

**Lo que quedo sin verificar:** el arreglo del movil **no se probo en la app corriendo** — no se
levanto Expo ni se hizo `expo export`; lo verificado es `npx tsc --noEmit` en 0. **El frontend no
tiene suite de pruebas** (no hay script `test` en `package.json` ni un solo `.test.tsx`), asi que no
hay forma de dejar un test de regresion de los defectos 3, 4 y 5. Tampoco se probo el alta contra el
backend desplegado: el contenedor de produccion sigue con la imagen anterior al arreglo.

**Reportado y NO arreglado (regla 00, fuera de alcance):**

- **Un habito PERSONAL no puede aplicar solo algunos dias de la semana.** `MisHabitosService.crear`
  fija `TipoDia.TODOS` siempre, y el DTO no lleva dias. Por eso se saco el selector de dias del
  modal en vez de hacerlo funcionar: soportarlo es trabajo de backend (campo nuevo en
  `CreatePersonalHabitRequest` + mapeo a `TipoDia`) y una decision de producto sobre que
  combinaciones se permiten (`TipoDia` no es un set libre de dias).
- **`MisHabitosController.listar` dice en un comentario que "no ejecuta ningun guard (una cuenta
  SUSPENDED sigue leyendo su catalogo)", y no es cierto**: `MisHabitosService.consultar` llama a
  `requireProgreso`, que lanza `NotAuthorizedException` si el participante esta suspendido. El
  comentario justifica la ausencia de `@RequiresPermission` con un hecho falso.
- ~~**El interruptor ACTIVO/PAUSADO no va a funcionar sobre un habito PERSONAL, y ahora que el alta
  funciona esto pasa a ser alcanzable.**~~ **CERRADO el 2026-09-06 por E-138** (mismo dia). Este
  hallazgo decia ademas *"No se toco porque ese rechazo es deliberado y esta escrito"*, y esa parte
  quedo desactualizada: el rechazo era deliberado **para lo que `desbloqueos_habito` significaba
  antes de D-87**. Desde que esa tabla es tambien el unico lugar donde vive la pausa del aprendiz,
  mantenerlo dejaba a los habitos propios sin interruptor. Ver E-138 para el razonamiento completo.
- **El interruptor ACTIVO/PAUSADO se ve por dia pero se guarda por habito.** En Plan el switch vive
  dentro del dia seleccionado (`days[selectedDay]`), pero lo que persiste es
  `desbloqueos_habito.pausado_en`/`pausado_hasta`, que es un RANGO DE FECHAS del habito entero:
  pausar "solo el martes" pausa el habito hasta la fecha elegida, todos los dias incluidos. Es
  anterior a este cambio y no se toco. Cerrarlo pide decidir cual de las dos lecturas es la buena.
- **`participantes_programa.habitos_escalonados_en` sigue sin lector ni escritor** (ya anotado en la
  regla 04).

---

## E-138 — El interruptor ACTIVO/PAUSADO rechazaba con 400 cualquier habito PERSONAL (2026-09-06) — **RESUELTO**

**Sintoma exacto**, tal como lo devuelve el backend al primer toque del interruptor sobre un habito
propio:

```
PUT /api/v1/habit-unlocks/{habitId}
400 Bad Request
"Solo se eligen habitos del catalogo, no habitos personales"
```

El aprendiz ve, en la pantalla Plan: el switch se apaga (es optimista), y al instante vuelve a
encenderse con el aviso **"No pudimos guardar el cambio — Intenta de nuevo en unos segundos."**
Nunca se guarda nada.

**Por que nadie lo habia visto hasta hoy.** Hacia falta tener un habito personal, y **crear uno no
funcionaba**: el movil fabricaba un objeto en memoria con un id inventado y el backend moria con
`IllegalArgumentException: diaInicio fuera de rango 1..90: 0`. Las dos cosas se arreglaron esta
misma manana (E-137 / D-115), y en el mismo informe quedo anotado que el interruptor iba a fallar
"en cuanto alguien lo toque". Esta entrada cierra ese hallazgo.

**Causa real.** `PlanScreen.aplicarEstadoHabito` manda **dos** llamadas, en este orden (D-99):

1. `PUT /api/v1/habit-unlocks/{id}` → `DesbloqueoHabitoService.elegir` — asegura la fila de
   `desbloqueos_habito`, porque el PATCH exige que exista (404 si no).
2. `PATCH /api/v1/habit-unlocks/{id}` → `DesbloqueoHabitoService.cambiarEstado` — escribe
   `pausado_en` / `pausado_hasta`.

El paso 2 **nunca rechazo un habito personal**; el que lo rechazaba era el paso 1, con una guarda
escrita cuando `desbloqueos_habito` significaba otra cosa:

```java
if (!habito.esDeSistema()) {
    throw new IllegalArgumentException("Solo se eligen habitos del catalogo, no habitos personales");
}
```

Esa frase describia bien la operacion **"elegir"** original (agosto 2026): sacar algo de un catalogo
compartido, que es una operacion sin sentido sobre un habito que ya es tuyo. Lo que cambio despues
es **el significado de la tabla**: `V23` (D-87) y `V31` le agregaron `pausado_en` / `pausado_hasta`
y la volvieron, textualmente en su propio comentario de migracion, *"que habitos lleva este aprendiz
en su plan"*. Desde entonces `desbloqueos_habito` es el **unico** lugar donde vive el interruptor —
y la guarda vieja, que nadie volvio a mirar, le cerraba la puerta a la mitad de los habitos.

**Donde NO podia vivir la pausa de un habito personal, y por que.** Se reviso el esquema real antes
de decidir:

| Candidato | Por que no |
|---|---|
| `habitos.activo` | Es la unica bandera que tiene esa tabla, y para un habito PERSONAL significa *existe / se ve*: `LoadHabitoPort.personalesActivosDe` filtra por `activo = true`, asi que ponerlo en `false` **desaparece el habito de `GET /api/v1/habits`**. Eso es una baja logica, no una pausa — y el aprendiz se quedaria sin forma de volver a encenderlo |
| Columnas nuevas de pausa en `habitos` | Dejaria **dos tablas respondiendo la misma pregunta**, que es exactamente lo que `V23` y `V31` argumentan evitar en sus cabeceras. Ademas duplica la semantica del rango (`pausado_hasta`) y obliga a que `RegistroService` consulte dos fuentes |
| `participantes_programa.habitos_escalonados_en` | No es una pausa por habito sino un flag por participante; sigue sin lector ni escritor (regla 04) |
| `desbloqueos_habito` | Ya tiene las dos columnas, con la semantica exacta; su FK apunta a `habitos(id)` **sin distinguir ambito**; y `RegistroService.generarInterno` ya aplica el filtro de pausa sobre la lista completa `catalogoActivo() + personalesActivosDe(...)`, sin mirar si el habito es de sistema. **El mecanismo ya funcionaba de punta a punta para un habito personal: lo unico que faltaba era poder crear la fila** |

**Solucion aplicada.** Se cambio la guarda de `elegir` por la pregunta correcta — *"¿este habito
puede entrar en el plan de este aprendiz?"* — en vez de *"¿es del catalogo?"*:

```java
private static void requirePuedeEntrarEnElPlan(Habito habito, UserId actorId) {
    if (!habito.esDeSistema() && !habito.esPersonalDe(actorId)) {
        throw new NoSuchElementException("Habito no encontrado: " + habito.id());
    }
    if (!habito.activo()) { ... }
}
```

- **Lo que la guarda vieja SI protegia y aca queda explicito:** el habito personal de **otro**
  aprendiz. Antes lo bloqueaba de rebote (rechazaba todos los personales); ahora se comprueba la
  propiedad a proposito, con `Habito.esPersonalDe(UserId)` nuevo en el dominio. Se responde **404 y
  no 403**: un 403 confirmaria que ese id existe.
- **Un habito personal dado de baja** (`activo = false`) tampoco vuelve a entrar al plan.
- **No hizo falta ninguna migracion, ni ningun cambio en el movil.** La secuencia PUT+PATCH de
  `PlanScreen` es la misma; simplemente deja de recibir 400 en el primer paso.
- **La regla de pausa no se reinterpreto para los habitos personales.** Sigue siendo la de V31: el
  rango `pausado_en`/`pausado_hasta` se evalua en la zona del participante y la reanudacion se
  **deriva** de la fecha, sin cron (regla 02). Un habito personal siempre es `desactivable`, asi que
  nunca cae en el 409 de "habito obligatorio".

**Como evitar que vuelva a pasar:**

- **Ocho pruebas nuevas** — 5 unitarias (4 netas en `DesbloqueoHabitoServiceTest`, porque una
  reemplaza a la que afirmaba lo contrario, + 1 en `HabitoTest`) y 3 de integracion. **Seis de las
  ocho salen rojas contra el codigo viejo**, comprobado de verdad: se revirtio la guarda y se corrio
  la suite, con **3 fallos en `DesbloqueoHabitoServiceTest` y 3 errores en `PausaHabitoPersonalIT`**,
  los seis con el mensaje literal `"Solo se eligen habitos del catalogo, no habitos personales"`.
  Las otras dos no podian salir rojas y conviene decir por que: la de `HabitoTest` prueba un metodo
  que antes no existia (no compilaria), y `elInterruptorReactivaUnHabitoPersonalPausado` ejercita
  solo `cambiarEstado`, que **nunca** rechazo un habito personal — el que rechazaba era el PUT:
  - `DesbloqueoHabitoServiceTest.elegirElHabitoPersonalPropioLoAgregaAlPlan` — **es el metodo que
    antes se llamaba `elegirHabitoPersonalRechazado` y afirmaba lo contrario.** No se borro: se dio
    vuelta, para que quede a la vista que la regla cambio y por que.
  - `...elegirElHabitoPersonalDeOtroAprendizNoSeEncuentra` y `...elegirUnHabitoPersonalDadoDeBajaRechazado`
    — lo que sigue estando prohibido.
  - `...elInterruptorPausaUnHabitoPersonalPropioHastaUnaFecha` y `...elInterruptorReactivaUnHabitoPersonalPausado`
    — la secuencia completa PUT+PATCH, la misma que dispara el boton.
  - `PausaHabitoPersonalIT` (Testcontainers, Postgres real, 3 pruebas): que la fila entre de verdad
    con un `habito_id` de ambito PERSONAL (FK + `CHECK (dia_desbloqueo BETWEEN 1 AND 90)` +
    `desbloqueos_pausa_hasta_requiere_pausa` de V31), que el habito pausado **no** genere track ese
    dia, y que **vuelva solo** al dia siguiente del ultimo dia de la pausa sin que nadie toque nada.
  - `HabitoTest.esPersonalDeDistingueElHabitoPropioDelAjenoYDelCatalogo`.
- **La leccion general, que es la que conviene tener a mano: cuando una tabla cambia de significado,
  hay que revisar las guardas que se escribieron contra el significado viejo.** El rechazo de
  `elegir` no era un descuido cuando se escribio — era correcto. Lo que lo volvio un bug fue D-87
  ampliando `desbloqueos_habito` de *"que eligio del catalogo"* a *"que lleva en su plan, y si esta
  pausado"*, sin releer quien mas dependia de la definicion anterior. El sintoma tardo tres semanas
  en aparecer solo porque hacia falta otro bug (E-137) para poder llegar a el.

**Verificacion:** `./mvnw clean verify` con `JAVA_HOME=C:\Program Files\Java\jdk-25.0.2` →
**`Tests run: 2441, Failures: 0, Errors: 0, Skipped: 0`** (surefire) y
**`Tests run: 25, Failures: 0, Errors: 0, Skipped: 0`** (failsafe), `BUILD SUCCESS` en 06:55.

**Lo que quedo sin verificar / sin arreglar:**

- **El arreglo no se probo en la app corriendo.** No se levanto Expo. **El frontend no tiene suite de
  pruebas** (no hay script `test` en `package.json` ni un solo archivo `.test.tsx`), asi que del lado
  del movil solo se verifico `npx tsc --noEmit` en 0 — y el movil, ademas, **no se toco**.
- **El interruptor se sigue viendo por dia y guardando por habito** (hallazgo de E-137, sin
  cerrar): en Plan el switch vive dentro de `days[selectedDay]`, pero lo que persiste es un rango de
  fechas del habito entero. Este cambio **no lo empeora** —un habito personal ahora se comporta
  exactamente igual que uno de catalogo— pero tampoco lo cierra: sigue pidiendo decidir cual de las
  dos lecturas es la buena.
- **El estado de pausa nunca se lee de vuelta en el movil.** `habitsMappers` arma `days` desde
  `activeWeekdays` (que dias de la semana aplica el habito) y **no** consulta `GET /habit-unlocks`,
  que desde V31 ya expone `paused`/`pausedUntil`. Consecuencia: la pausa se guarda bien, pero al
  recargar la app el switch vuelve a pintarse encendido. Es el mismo sintoma que D-87 creyo cerrar,
  ahora del lado del cliente. Fuera del alcance de este encargo; es la continuacion natural del punto
  anterior.
- **`DELETE /api/v1/habit-unlocks/{id}` ("quitar del plan") hace lo contrario de lo que dice, y no se
  toco.** Borra la fila, y por la compatibilidad hacia atras de D-87 (*"un habito sin fila se sigue
  generando como siempre"*) el efecto real es que el habito **vuelve** a generar todos los dias. Es
  preexistente, aplica igual a los habitos de catalogo, y el movil no lo llama.

---

## E-139 — El workflow de CD termina en verde en 15 segundos sin publicar ni desplegar nada (2026-09-06) — **DIAGNOSTICADO, falta un paso manual del dueno**

**Sintoma exacto.** Las tres ultimas corridas del workflow `CD` sobre `master`, en `gh run list`:

```
completed  success  Integrar el cierre del panel de administracion...  CD  master  push  34011493241  20s
completed  success  Registrar el cruce paginado de evidencias...       CD  master  push  34004788620  14s
completed  success  Registrar por que Flyway no aplicaba el baseline   CD  master  push  34003689467  14s
```

Tres tildes verdes. Y sin embargo **ninguna de esas corridas construyo una imagen**: un `docker
build` de este proyecto (Maven + JDK 25 + tres etapas) no entra en 14 segundos. Las imagenes que
hay en ECR se subieron a mano desde la maquina de desarrollo.

**Causa real: el repositorio de GitHub no tiene NI UNA variable de Actions cargada.**

```
$ gh api repos/ricardoIsmael/Renaser-90-dias-backend/actions/variables
{"variables":[],"total_count":0}
```

El `cd.yml` tiene, a proposito, una guarda al principio: si faltan `AWS_ROLE_ARN`, `AWS_REGION` o
`ECR_REPOSITORY`, avisa con un `::notice::`, escribe "Publicacion en ECR: salteada" en el resumen
del job y **termina en verde**. La idea era buena — no pintar de rojo cada push mientras la
infraestructura no existiera. El problema es lo que pasa despues: **la infraestructura se creo**
(rol de IAM, proveedor OIDC, ECR, EC2, RDS, CloudFront) **y nadie cargo las variables**, asi que la
guarda siguio salteando el workflow entero durante dias, con el mismo tilde verde de siempre.

**La leccion, que es la misma de E-111 en otro disfraz.** E-111 era `./mvnw clean test` terminando
en `exit 0` sin correr una sola prueba. Este es un workflow terminando en `success` sin hacer una
sola cosa. En los dos casos **el codigo de salida no significa lo que uno cree**, y en los dos la
verificacion real es mirar la evidencia de que el trabajo ocurrio: alli la linea `Tests run:`,
aca la duracion del job y el paso de publicacion.

**Como evitar que vuelva a pasar:**

- Una guarda que se saltea **tiene que doler mas que un `::notice::`**. En el job de despliegue
  agregado el 2026-09-06 la guarda usa `::warning::` — que sale amarillo en la interfaz de
  Actions — en vez de `::notice::`, que pasa desapercibido.
- **Cuando se crea una pieza de infraestructura, el mismo cambio carga la variable que la apunta.**
  Un rol de IAM sin su `AWS_ROLE_ARN` en GitHub no sirve para nada, y no hay nada que avise.
- Al mirar una corrida verde de un workflow que construye algo, **mirar la duracion**. Catorce
  segundos no alcanzan para compilar este proyecto: si dice que si, no compilo.

**Que falta para cerrarlo (es del dueno, no se puede hacer desde el repositorio):** crear en
Settings -> Secrets and variables -> Actions -> **Variables** las cuatro que estan en
`docs/DESPLIEGUE_Y_CI.md` §5.1 e — `AWS_ROLE_ARN`, `AWS_REGION`, `ECR_REPOSITORY` y
`EC2_INSTANCE_ID`, con los valores reales que ya figuran en esa tabla.

---

## E-140 — Tres trampas de `ssm send-command` al automatizar un despliegue: el escapado, el codigo de salida que no llega, y el permiso que parece completo y no lo es (2026-09-06) — **RESUELTO**

Las tres aparecieron armando el paso de despliegue del `cd.yml`, y las tres tienen la misma
propiedad desagradable: **no fallan de forma ruidosa, fallan de forma equivocada**.

### 1. El escapado inline no sobrevive, y `$$` es el caso peor

**Sintoma.** Un comando armado asi, que es la forma que aparece en toda la documentacion:

```bash
aws ssm send-command --document-name AWS-RunShellScript \
  --parameters 'commands=["curl -s -w \"%{http_code}\" http://localhost:8080/actuator/health"]'
```

llega a la instancia con las comillas simples comidas y los `$` ya expandidos. Costo dos fallos en
una misma sesion. **El peor caso es `$$`**: el shell lo reemplaza por su propio PID, asi que el
comando **no falla** — corre con un valor equivocado, que es mucho mas caro de diagnosticar que un
error. La cadena atraviesa tres parsers distintos (el shell que lanza la CLI, el de la propia CLI,
y el del documento de SSM) y cada uno se come una capa de comillas.

**Solucion aplicada.** El script que corre en la instancia se escribe a un archivo y se codifica
entero como **una unica cadena JSON**:

```bash
jq -Rs '{commands: [.]}' desplegar.sh > parametros.json
aws ssm send-command ... --parameters file://parametros.json
```

`-R` lee crudo (sin interpretar JSON), `-s` junta todo el archivo en una sola cadena. Lo que corre
en la instancia queda **byte por byte** igual al archivo — verificado comparando el contenido del
JSON producido contra el archivo original. Y el archivo se genera con un *heredoc de delimitador
entrecomillado* (`<<` seguido de `FIN` entre comillas simples), que es lo que impide que el shell
local expanda nada de adentro. Los valores variables (imagen, region, tope de espera) se inyectan
como asignaciones **antes** del cuerpo, no interpolados adentro.

> Emparenta con **E-132** (`MSYS_NO_PATHCONV=1` y `fileb://`): la ruta que se le pasa a `file://`
> desde Git Bash tiene que ser la forma `C:/...` de `cygpath -m`, no `/c/...`.

### 2. `send-command` es asincrono: el codigo de salida del script remoto NO llega por ahi

**Sintoma.** `aws ssm send-command` devuelve un `CommandId` y termina en `exit 0` **siempre** —
tambien cuando el script remoto termina en `exit 1`. Un paso de despliegue que solo hace
`send-command` **queda verde aunque la aplicacion no haya arrancado nunca**. Es la misma familia
de E-111 y E-139: un exito que no significa nada.

**Solucion aplicada.** El resultado real esta en el `Status` de la invocacion, que hay que ir a
buscar aparte:

```bash
aws ssm get-command-invocation --command-id "$CID" --instance-id "$INSTANCIA" --query Status
```

Devuelve `Pending`/`InProgress` hasta que termina, y despues `Success` o `Failed`. El paso hace un
bucle propio hasta que sale de esos dos primeros estados y **falla si no es `Success`**. Probado a
mano mandando un script que hace `exit 1`: `Status=Failed`, el paso sale con 1, y la salida remota
queda impresa en el registro.

**Por que un bucle propio y no `aws ssm wait command-executed`:** ese waiter existe, pero son 20
intentos cada 5 segundos = **100 s de tope**. Esta aplicacion tarda **43 s medidos** en responder
`UP`, y una migracion de Flyway larga o una RDS fria se comen esos 100 s sin despeinarse. El
waiter fallaria por vencimiento en un despliegue perfectamente sano.

**Dos detalles del bucle que importan:** `get-command-invocation` puede responder
`InvocationDoesNotExist` durante el primer segundo (hay que tolerarlo, no tratarlo como fallo), y
**que `stderr` traiga texto no es un fallo**: `docker login` escribe siempre el aviso *"Your
password will be stored unencrypted in /root/.docker/config.json"*. Quien decide es el `Status`.

### 3. `ssm:SendCommand` necesita DOS recursos, y el ARN del documento no lleva cuenta

**Sintoma esperable** si se concede solo el ARN de la instancia:

```
An error occurred (AccessDeniedException) when calling the SendCommand operation:
User ... is not authorized to perform: ssm:SendCommand on resource: arn:aws:ssm:us-east-1::document/AWS-RunShellScript
```

AWS evalua la llamada contra la instancia **y** contra el documento. Y el ARN de un documento que
es propiedad de AWS **no lleva numero de cuenta**: `arn:aws:ssm:us-east-1::document/AWS-RunShellScript`,
con los dos puntos seguidos. Escribirlo con la cuenta adentro apunta a un documento propio que no
existe, y el permiso no aplica aunque el nombre coincida.

**La otra mitad:** `ssm:GetCommandInvocation` **no soporta permisos a nivel de recurso**. Acotarla
a un ARN la deja sin efecto; tiene que ir sobre `*`. Es el unico comodin de la politica y conviene
que quede escrito por que, para que nadie lo "corrija" despues.

**Como se verifico sin poder asumir el rol.** El rol `renaser-github-actions` solo se puede asumir
por OIDC desde GitHub, asi que desde la maquina de desarrollo **no hay forma de probarlo
ejecutando**. La herramienta correcta es `iam simulate-principal-policy`, que evalua las politicas
del rol sin asumirlo:

```bash
aws iam simulate-principal-policy --profile renaser \
  --policy-source-arn arn:aws:iam::302277511407:role/renaser-github-actions \
  --action-names ssm:SendCommand \
  --resource-arns arn:aws:ssm:us-east-1::document/AWS-RunShellScript \
  --query "EvaluationResults[0].EvalDecision"
```

**Y se simulan tambien los casos negativos**, que son los que demuestran que el permiso no quedo
de mas: `SendCommand` contra otra instancia, contra `AWS-RunPowerShellScript`, `ssm:StartSession`,
`ssm:GetParameter` sobre `/renaser/prod/*`, `ec2:TerminateInstances`, `iam:PutRolePolicy` y
`s3:GetObject` — las siete dan `implicitDeny`.

**Como evitar que vuelva a pasar:** al escribir una politica nueva, simular **las dos listas**: lo
que tiene que permitir y lo que no. Una politica que solo se probo por el lado de "funciona" es una
politica de la que no se sabe cuanto de mas concede.

---

## E-141 — Los dos chats de IA mostraban en pantalla los ids internos de las lecciones recuperadas por el RAG (2026-09-06) — **RESUELTO en el frontend**

**Sintoma exacto.** Al pie de cada respuesta del asistente, en los DOS chats del producto, se
dibujaba un bloque asi:

```
LECCIONES CITADAS
[ 3f9a1c2e-7b44-... ]  [ 08d5e611-a0c3-... ]
```

Un chip por cada id de leccion que el RAG habia recuperado para armar esa respuesta. No eran
titulos legibles para el aprendiz: era el **identificador interno de la fila** en la base de
conocimiento del backend.

**Pedido del dueno, textual (2026-09-06):** *"la ia de renasia (...) eso no debe de citar o aparecer
en su frontend para no tocar backend, no citar las referencias mejor, por seguridad. solo quita eso
en los 2 chats"*. Y sobre cuales son los dos: *"una es de cursos y este si debe de pasar por el rag
y el otro es de soporte con tool calling"* — es decir Sparkie (`COURSE_TUTOR`, entra por
`ChatDelCurso`) y el acompanante (`COMPANION`, entra por `RenasiaLauncher`).

**Donde estaba, y por que era un solo lugar.** Los dos chats son el mismo componente: `ChatDelCurso`
y `RenasiaLauncher` montan los dos el mismo `RenasiaPanel`, que dibuja cada mensaje con
`MensajeBurbuja`. El bloque de fuentes vivia unicamente ahi. Un solo borrado apago los dos chats;
no habia dos implementaciones que sincronizar.

**Que se hizo (solo frontend, ningun `.java` tocado).** Repo `Renaser-90-dias-frontend-`:

| Archivo | Cambio |
|---|---|
| `components/MensajeBurbuja.tsx` | Se borro el bloque `LECCIONES CITADAS` y sus tres estilos (`fuentesBox`, `fuentesLista`, `fuenteChip`) |
| `types/renasia.types.ts` | Se borro `RenasiaMensajeUI.lecciones` — es el modelo de PANTALLA y solo existia para alimentar esos chips |
| `hooks/useRenasiaChat.ts` | `mapearMensajeApi` deja de leer `sourceLessonIds`; se quito el callback `onFuentes` y el campo de las dos burbujas optimistas |
| `api/renasiaStream.ts` | Se quito `onFuentes` del tipo de callbacks; la rama `tipo === 'fuentes'` quedo vacia con un comentario |
| `data/agentes.ts` | La pantalla vacia del acompanante prometia *"Cada respuesta cita las lecciones exactas de las que sale."* — se quito la frase |

**La trampa, y la razon de ser de esta entrada.** El backend **no se toco**, asi que sigue mandando
`sourceLessonIds` en el historial y el evento `{"tipo":"fuentes","lecciones":[...]}` en el stream.
Eso deja tres cosas que parecen bugs y no lo son:

1. **La rama `else if (evento.tipo === 'fuentes') { }` de `renasiaStream.ts` esta vacia a proposito.**
   Se dejo escrita en vez de borrarla y dejar que el evento cayera en "lo desconocido se ignora",
   justamente para que quede constancia de que el evento **se conoce** y la decision de descartarlo
   es deliberada. Sin esa rama, el proximo que lea el archivo lo toma por un tipo sin soporte y
   "arregla" un bug que no existe.
2. **`sourceLessonIds` y `eventoFuentesSchema` siguen en el espejo del contrato** (`renasia.types.ts`,
   `renasiaSchemas.ts`). Se dejaron porque el backend los sigue emitiendo: sacarlos del tipo no deja
   de recibirlos, solo deja de documentar que llegan.
3. **`RenasiaMensajeUI.lecciones` si se borro**, y la asimetria con el punto 2 es intencional: los
   tipos del wire describen lo que el servidor manda; el modelo de pantalla describe lo que se
   dibuja. Sin chips que alimentar, ese campo era estado muerto arrastrado por el hook.

**Lo que este cambio NO resuelve, y hay que decidir aparte.** Los prompts de sistema de los dos
agentes (`prompts/sparkie-cursos.st` y `prompts/renasia-sistema.st`) tienen una seccion *"De donde
sacas lo que respondes"* que ordena, con todas las letras: *"Usa las fuentes en este orden, **y di
siempre cual usaste**"*, con ejemplos como *"en esta leccion..."* y *"esto no es parte del curso, es
informacion general que encontre"*. Esa atribucion viaja **dentro del texto** de la respuesta, no en
los chips, asi que **sigue apareciendo en pantalla**. Quitar los chips no rompe nada (el modelo no
queda hablando de fuentes invisibles: la referencia en prosa se sigue mostrando entera), pero
tampoco la elimina. Si lo que el dueno quiere es que el asistente **no mencione de donde sale nada**,
eso se cambia en esos dos `.st` del backend — que es exactamente lo que pidio no tocar. Queda
planteado, sin tocar.

**Como evitar que vuelva a pasar:** un id interno (UUID de fila, clave de la base de conocimiento,
id de documento del RAG) **no se pinta nunca en una pantalla del aprendiz**, ni siquiera "mientras
tanto". Si hace falta mostrar una fuente, se muestra su **titulo**, y el titulo lo tiene que mandar
el backend como tal. La regla general: si un dato solo sirve para depurar, no se renderiza — se
loguea.

---

## E-142 — El workflow de CD nunca desplegó: dos fallos encadenados que lo dejaban en verde (2026-09-06) — **RESUELTO**

**Sintoma exacto.** Cada push a `master` dejaba una corrida de CD en **verde**, terminada en unos
8 segundos. En el detalle de la corrida, todos los pasos que importan aparecian `skipped`:

```
JOB: Construir y publicar en ECR  success
    - Ver si la infraestructura de AWS esta configurada: success
    - Construir y publicar: skipped
JOB: Desplegar en la instancia EC2 (por SSM)  skipped
```

**Por que era invisible.** El paso guardian esta escrito a proposito para *no* pintar de rojo cuando
la infraestructura todavia no existe (mismo criterio que `sonarcloud.yml`). Esa decision es correcta
para un repo recien creado, pero tiene un costo que nadie habia pagado hasta ahora: **una vez que la
infraestructura SI existe, un fallo de configuracion se sigue viendo igual que "todavia no toca".**
El check verde de GitHub decia lo mismo en los dos casos.

Consecuencia concreta: **todos los despliegues a produccion se venian haciendo a mano** (build local
+ `docker push` a ECR + `ssm send-command`). El pipeline existia, estaba probado en el papel, y no
habia corrido ni una vez de punta a punta.

### Causa 1 — las variables estaban en un *environment*, no en el repositorio

`gh variable list` devolvia **vacio**, pero las cuatro variables existian y con los valores
correctos: estaban cargadas dentro del environment **`AWS`**
(`gh api repos/.../environments/AWS/variables`).

Un `${{ vars.X }}` resuelve variables de environment **solo si el job declara `environment:`**. Los
dos jobs de `cd.yml` no lo declaran, asi que `vars.AWS_ROLE_ARN` y compania llegaban vacias y el
guardian concluia, correctamente, que faltaba configuracion.

**Por que se resolvio moviendo las variables al repositorio y no agregando `environment: AWS` al
workflow** — que era el arreglo "obvio": porque agregar `environment:` **cambia el claim `sub` del
token OIDC** a `repo:OWNER/REPO:environment:AWS`, que no es lo que la politica de confianza del rol
autoriza. El arreglo obvio habria cambiado un salteo silencioso por un fallo de permisos, y ademas
habria requerido tocar IAM en el mismo movimiento. El environment `AWS` no tenia reglas de
proteccion ni politica de ramas, asi que no aportaba nada que se perdiera al mover las variables.

### Causa 2 — GitHub cambio el formato del `sub` de OIDC (claims inmutables)

Con las variables ya visibles, la corrida siguiente llego mas lejos y fallo asi, 12 veces seguidas:

```
Could not assume role with OIDC: Not authorized to perform sts:AssumeRoleWithWebIdentity
```

Todo lo obvio estaba bien: el proveedor OIDC existia con `ClientIDList = ["sts.amazonaws.com"]`, el
rol existia, `id-token: write` estaba declarado, el `role-to-assume` era el correcto y el nombre del
repo coincidia **con las mayusculas exactas** de la politica.

La causa aparece al consultar `repos/{owner}/{repo}/actions/oidc/customization/sub`:

```json
{"use_default":true,"use_immutable_subject":false,
 "sub_claim_prefix":"repo:ricardoIsmael@274585616/Renaser-90-dias-backend@1343032051"}
```

GitHub ahora emite el `sub` con los **IDs numericos** de la cuenta y del repositorio intercalados
(`OWNER@OWNERID/REPO@REPOID`). La politica de confianza esperaba la forma vieja, solo con nombres, y
la comparacion `StringLike` es exacta: no matchea.

**Solucion.** La politica acepta ahora las **dos** formas, cada una escrita completa y sin comodines,
las dos fijadas al mismo repo y a la misma rama:

```
repo:ricardoIsmael/Renaser-90-dias-backend:ref:refs/heads/master
repo:ricardoIsmael@274585616/Renaser-90-dias-backend@1343032051:ref:refs/heads/master
```

**Nada de resolverlo con un comodin tipo `repo:ricardoIsmael*/Renaser-90-dias-backend*:...`.** Parece
equivalente y no lo es: `ricardoIsmael*` tambien matchea a `ricardoIsmaelOtro`, un usuario que
cualquiera puede crear. La forma con IDs es ademas **mas** segura que la de nombres — un nombre de
usuario o de repo se puede transferir o renombrar, un ID numerico no.

**Verificacion.** Corrida completa en verde de punta a punta por primera vez: OIDC → login a ECR →
build → push → `ssm send-command` → `/actuator/health` **UP**. La instancia quedo corriendo la imagen
etiquetada con el SHA del commit de `master`, no `latest`.

**Como evitar que vuelva a pasar:**

1. **Un guardian que saltea trabajo no puede reportar el mismo verde que un exito.** El paso deja un
   `::notice::` y una linea en el resumen, pero el check de GitHub se ve identico. Si un workflow
   puede auto-saltearse, hay que poder distinguir "salteado" de "hecho" **sin abrir la corrida** —
   por ejemplo mirando la duracion: un CD real no termina en 8 segundos.
2. **Un pipeline de despliegue no esta terminado hasta que corrio entero una vez.** Que los pasos
   esten bien escritos no es evidencia de que funcionen; solo lo es una corrida verde que de verdad
   construyo y desplego.
3. Al cargar variables de Actions, **verificar el alcance**: `gh variable list` (repositorio) y
   `gh api repos/.../environments/<env>/variables` (environment) son dos lugares distintos y el
   workflow solo ve uno de los dos.

---

## E-143 — Los 50 archivos que faltaban del bucket viejo, y tres trampas al migrarlos (2026-09-06) — **RESUELTO**

**Sintoma exacto.** En la pantalla de cursos no cargaba ninguna portada. El backend firmaba la URL
correctamente y S3 respondia **404**: la fila de la base apuntaba a una clave que nunca se habia
subido al bucket nuevo. Lo mismo con las 13 audioterapias y con las imagenes incrustadas en el
cuerpo de 12 lecciones.

**Causa.** Al migrar a la cuenta nueva de AWS se creo `renaser90dias-prod` y se subieron los 45
audios de Pastilla Renacer, pero **nunca se copiaron** los objetos del bucket viejo
(`s3-renaser90dias`), al que esta sesion no tenia acceso. Quedaron 50 objetos sin migrar: 23
portadas, 13 audioterapias y 14 assets de leccion.

**Verificacion de correspondencia antes de subir nada** (que es el punto de esta entrada):

| Que | En la base | En disco | Coinciden |
|---|---|---|---|
| Portadas de curso (`cursos.portada_ruta`) | 23 | 23 | **23/23**, por id exacto |
| Audioterapias (`audioterapias.ruta_storage`) | 13 | 13 | **13/13**, por nombre exacto |
| Assets de leccion (referenciados en `lecciones.cuerpo_html`) | 13 | 14 | los 13 referenciados, +1 de sobra |

### Trampa 1 — la ruta de la base ES la clave de S3, sin prefijo

`S3AlmacenamientoAdapter` usa el argumento `ruta` **verbatim** como `key`; no antepone nada. Y los
llamadores (`CatalogoAcademyService.firmarPortada`, `AudioterapiaService.firmarAudio`) lo pasan tal
cual. Por eso `cursos.portada_ruta = <id>/portada.jpg` va a la **raiz** del bucket y no bajo
`contenido/`, aunque los audios de Pastilla Renacer si vivan en `contenido/pastilla-renacer/`: esa
diferencia esta en el dato, no en el codigo. Subir las portadas "ordenadas" bajo un prefijo las
habria dejado igual de rotas, y ademas mas dificiles de diagnosticar.

### Trampa 2 — cuatro archivos `.bin` que en realidad son PNG

Tres de los cuatro `assets/*.bin` empiezan con `89 50 4E 47`: son PNG con la extension perdida en
la exportacion original. **No hay que renombrarlos**: el `cuerpo_html` de la leccion referencia el
nombre con `.bin`, asi que la clave tiene que conservarlo. Lo que hace que se vean es el
**`Content-Type`**, que S3 devuelve al firmar la lectura — se subieron con `image/png` explicito,
detectado por magic bytes y no por extension. Con el `application/octet-stream` que `aws s3 cp`
habria puesto solo, la clave existiria, el 404 desapareceria, y la imagen igual no se dibujaria.

### Trampa 3 — un asset que no es una imagen

`1724d86936ba4bf6b059500e26e4a775/assets/c0077cc9a110-145086556.bin` empieza con `<!doctype ht`:
es una **pagina HTML**, no una imagen. El numero del nombre (`145086556`) es el id de ivoox del
audio que esa leccion enlaza, asi que lo que se guardo fue la pagina del reproductor en vez del
recurso. Se subio igual, como `application/octet-stream`, para no dejar un 404 — pero **esa leccion
va a seguir mostrando una imagen rota**, y el arreglo no es de infraestructura: hay que reemplazar
el archivo de origen o quitar la referencia del `cuerpo_html`. **Queda pendiente, a la vista.**

**Verificacion final.** El bucket paso de 49 a **99** objetos. Las 23 portadas, las 13 audioterapias
y los 13 assets referenciados responden a `head-object`. Prueba de lectura real con URL prefirmada:
`HTTP 200 | image/jpeg | 77184 bytes`.

**Como evitar que vuelva a pasar:** al mover un bucket entre cuentas, la lista de lo que hay que
copiar **se deriva de la base de datos**, no del listado del bucket viejo — es la base la que dice
que claves se van a pedir. Y el tipo de un archivo se determina por sus **primeros bytes**, nunca
por su extension: la extension es lo primero que se pierde en una exportacion.

---

## E-144 — `Alert` no existe en react-native-web: 99 diálogos muertos, y el interruptor de pausar hábitos que no respondía (2026-09-06) — **RESUELTO en el frontend**

**Sintoma exacto, reportado por el dueño.** En `https://renaser-90-dias-frontend-livid.vercel.app/`,
en Plan → Hábitos: *"si presiono desactivar no ocurre nada"*. El interruptor **ni siquiera se
movía**. Sin dialogo, sin mensaje de error, sin una sola linea en la consola del navegador.

**Falso positivo previo, que vale registrar.** Antes de esto se habia arreglado el CORS (el repo del
frontend despliega al proyecto `-livid` de Vercel y ese dominio no estaba permitido). Ese arreglo era
**real y necesario** —sin el ninguna llamada al backend pasaba— pero **no era la causa de este bug**,
y darlo por cerrado sin reproducirlo hizo que se le dijera al dueño que probara algo que seguia
roto. **Un arreglo verificado a nivel de infraestructura no es un sintoma verificado a nivel de
producto.** El preflight devolvia 200 y el interruptor seguia sin moverse, porque nunca llegaba a
hacerse una peticion.

**Causa real.** `Alert` de `react-native-web` **no esta implementado**. El modulo entero, textual, en
`node_modules/react-native-web/dist/exports/Alert/index.js` (0.21.2):

```js
class Alert {
  static alert() {}
}
export default Alert;
```

Un metodo vacio. No es que se vea distinto o le falten estilos: **no hace nada y nunca ejecuta los
`onPress` de los botones**. Y como no lanza ninguna excepcion, no hay rastro en ningun lado — es un
fallo perfectamente silencioso, que es lo que lo hizo tan caro de encontrar.

**Por que rompia justo el interruptor.** `PlanScreen.toggleHabitDayStatus` esta partido en dos
caminos a proposito: **reactivar** es un toque directo, **pausar** primero pregunta hasta cuando
(V31). La funcion que de verdad guarda —`aplicarEstadoHabito`— solo se llama desde el `onPress` de
esas opciones. Como el dialogo nunca se dibujaba, ese `onPress` no existia, y con el se caia tambien
la actualizacion optimista: por eso el switch no se movia ni visualmente.

### El alcance real: 99 llamadas, y solo 3 bloqueaban una accion

Clasificadas contando parentesis (no con una regex: los mensajes tienen parentesis y comillas
adentro y una regex se corta a la mitad):

| Tipo | Cuantas | Que pasaba en web |
|---|---|---|
| **Bloqueantes** (con botones y `onPress`) | **3** | La accion **nunca corria**. `PlanScreen.tsx:437` (pausar habito), `ComunidadScreen.tsx:1157`, `EvidenciaDesdeChatModal.tsx:101` |
| **Informativas** (solo texto) | **93** | El aviso **nunca se veia**: errores de subida de evidencia, validaciones del onboarding, confirmaciones del Muro y de Yo |

Las 93 informativas son la parte silenciosamente peor: **un fallo que se reportaba con
`Alert.alert('No pudimos guardar el cambio', ...)` se veia, en web, exactamente igual que un exito.**

**Solucion.** `src/components/Alerta.tsx`, con la **misma firma** que el `Alert` de React Native:

- **En movil delega en el `Alert` nativo, sin tocar nada.** Los dialogos siguen siendo los del
  sistema operativo; el riesgo de regresion en el build que va a las tiendas es nulo.
- **En web** dibuja un dialogo propio y ejecuta los `onPress`.

Se mantuvo la firma **a proposito** para que migrar cada archivo sea cambiar el `import` y nada mas:
las 99 llamadas quedan como estaban. Renombrar a algo tipo `mostrarAlerta()` habria obligado a
reescribirlas una por una, con 99 oportunidades de equivocarse.

`AnfitrionAlerta` va montado una sola vez en `App.tsx`. Si una alerta se dispara antes de que monte,
**queda en cola** en vez de perderse.

**Dos detalles que solo aparecieron al mirarlo en el navegador,** y que ninguna cantidad de lectura
del codigo habria mostrado:

1. **El dialogo salia translucido.** El fondo usaba `c.cardBgAlt`, que en modo oscuro es
   `rgba(255,255,255,0.07)`: se veia el formulario de atras a traves del cuadro, con los textos
   encimados. Una tarjeta *dentro* de una pantalla puede ser translucida; un dialogo que *tapa* la
   pantalla, no. Pasado a `c.bg`, que es opaco en los dos temas.
2. **Cerrar tocando fuera tenia que ejecutar el boton `cancel`**, que es lo que hace el dialogo
   nativo. Si no hay boton `cancel`, cierra sin ejecutar nada — nunca dispara una accion que la
   persona no eligio.

**Verificacion — hecha contra el build web real, no leyendo el codigo.** Servidor de Expo en
`localhost:8081`, con un disparador temporal en `window` (quitado despues, y confirmado quitado con
una recarga limpia):

- El dialogo **se dibuja** en el DOM.
- Pulsar una opcion imprime `PRUEBA: onPress EJECUTADO` — **ese es exactamente el callback que
  antes nunca corria**.
- Tocar el fondo cierra e imprime `PRUEBA: cancelado`, o sea ejecuta el boton `cancel`.
- Recarga limpia: la app carga, sin errores en consola, sin rastro del disparador.

**Como evitar que vuelva a pasar:**

1. **Que un modulo de React Native exista en la web no significa que haga algo.** `react-native-web`
   trae varios stubs vacios para que el bundle no se rompa. Antes de apoyar una funcionalidad en un
   modulo de `react-native` en el build web, **abrir el archivo en `node_modules`** — son diez
   lineas y se ve al instante. Otros candidatos a revisar con el mismo criterio si algun dia se usan:
   `Share`, `Vibration`, `PermissionsAndroid`, `ToastAndroid`, `BackHandler`.
2. **Un bug reportado no esta cerrado hasta reproducir el sintoma que reporto la persona.** Acá se
   arreglo el CORS (correcto y necesario), se verifico a nivel de red, y se dio por resuelto algo que
   nunca se habia reproducido. Verificar la capa que uno toco no es verificar el sintoma.
3. **Sospechar de lo que falla sin dejar rastro.** No habia error en consola, ni peticion fallida, ni
   excepcion. Cuando un boton "no hace nada" y la red esta limpia, la hipotesis no es el backend: es
   que el handler nunca se llamo.

---

## E-145 — La pausa de un hábito se escribía y nunca se leía de vuelta: al recargar, el hábito volvía a verse activo (2026-09-06) — **RESUELTO en el frontend**

**Sintoma exacto, reportado por el dueño.** Con el diálogo de pausa ya funcionando (E-144): *"no se
guarda, actualizo la página y vuelve a estar activo"*. El interruptor se apagaba, se elegía el
plazo, y al recargar el Plan el hábito aparecía encendido otra vez.

**Causa.** El PATCH **sí guardaba**. Lo que faltaba era la lectura. Al cargar la pantalla, el mapa
`days` de cada hábito se reconstruía **únicamente** desde `habito.activeWeekdays`, que es el
calendario del **catálogo compartido** — dice en qué días aplica el hábito para todo el padrón, y no
sabe nada de la pausa personal de nadie. La pausa vive en `desbloqueos_habito`, en otro endpoint,
y la app **nunca lo llamaba**: `habitsApi` tenía `PATCH`, `PUT` y `DELETE` sobre
`/api/v1/habit-unlocks/{id}`, y ningún `GET`.

**El backend ya estaba listo, y lo decía.** `HabitUnlockPlanResponse` expone `paused`/`pausedUntil`
desde V31, con este javadoc escrito de antemano:

> *"Antes NINGUNA respuesta de lectura exponia el estado de pausa, asi que el interruptor del Plan
> no podia pintarse con el valor real: se apagaba un habito, se recargaba la app y volvia a verse
> encendido. Es el mismo sintoma que D-87 creia haber cerrado — se persistia, pero no se leia de
> vuelta."*

V31 hizo su mitad; la del cliente quedó sin hacer. **Ningún `.java` se tocó en este arreglo.**

**Solución.** `GET /api/v1/habit-unlocks` entra como tercera lectura en paralelo de
`usePlanHabitos` (junto al catálogo y las preferencias), y el mapeo apaga los días sobre los que
cae la pausa.

### La decisión que hubo que tomar: una pausa es un RANGO DE FECHAS y el interruptor es POR DÍA

El switch pregunta "¿este hábito está activo el JUEVES?" y la pausa responde "hasta el domingo".
Para cruzarlos hace falta saber **qué fecha real es ese jueves**. De ahí sale lo demás:

1. **La regla de qué semana se muestra se movió a `features/habits/utils/semanaDelPlan`**, sin
   cambiarle el comportamiento. Antes vivía solo dentro de `PlanScreen`, que alcanzaba mientras
   nadie más la necesitara. Ahora la necesitan dos lugares —las pestañas y el mapeo de la pausa— y
   **dos copias que se separaran un día pintarían la pausa en la casilla equivocada, sin que
   ninguna de las dos pareciera rota.**
2. **Los días ya pasados NO se repintan.** La respuesta trae `pausedUntil` (hasta cuándo) pero no
   desde cuándo. Apagar hacia atrás inventaría un pasado que no ocurrió: si alguien pausa el jueves
   "hasta el domingo", el lunes de esa semana el hábito estuvo activo de verdad y su registro lo
   demuestra. Ante la duda, no se reescribe la historia.
3. **Una pausa vencida se apaga sola.** Sin fecha de fin (`pausedUntil = null`) la pausa es
   indefinida y apaga todo lo que viene; con fecha, apaga hasta ese día inclusive y el hábito
   vuelve solo, sin que nadie tenga que reactivarlo.

`semanaDelPlan` **no importa nada** a propósito, y `PlanScreen` reexporta desde ahí el tipo del día:
la dependencia apunta en un solo sentido, del que sabe poco al que sabe mucho.

**Verificacion.** Contra el código real en el navegador (arnés temporal, quitado y confirmado
quitado con recarga limpia). Semana lun 07 – dom 13, los siete casos:

| Caso | Días encendidos |
|---|---|
| Sin fila en el plan | los 7 |
| En el plan, sin pausar | los 7 |
| Pausado solo hoy (lun 07) | MAR…DOM |
| Pausado hasta el domingo | ninguno |
| Pausado indefinido | ninguno |
| **Pausa ya vencida (hasta ayer)** | **los 7 — vuelve solo** |
| **Hoy jueves, pausa hasta el domingo** | **LUN, MAR, MIÉ — el pasado queda intacto** |

**Un rodeo que vale registrar, porque cuesta media hora cada vez.** A mitad de la verificación la
consola mostraba `ReferenceError: INDICE_DE_HOY is not defined` y se diagnosticó como un ciclo de
importación. **No lo era:** eran mensajes **viejos del buffer de la pestaña**, de un estado
intermedio del hot-reload (la constante ya borrada de un archivo y todavía no importada en el otro).
Metro compilaba limpio y no reportaba nada. **`read_console_messages` devuelve historial, no solo lo
de la carga actual** — para saber si un error es real hay que abrir una **pestaña nueva**, cuyo
buffer arranca vacío, o contrastar contra la salida del bundler. El refactor que se hizo creyendo
que había un ciclo se conservó porque es correcto por sí mismo, pero el comentario que afirmaba
*"el bundle web reventaba"* se corrigió: describía un fallo que nunca ocurrió.

**Como evitar que vuelva a pasar:**

1. **Persistir no es guardar.** Un cambio está guardado cuando **sobrevive a recargar la pantalla**,
   y eso exige las dos mitades: el endpoint que escribe y el que lee. Este mismo síntoma ya había
   caído dos veces (D-87 y V31), siempre por la mitad que falta. Al agregar un dato del aprendiz,
   la pregunta de cierre es *"¿quién lo lee de vuelta, y con qué llamada?"*.
2. **Ojo con reconstruir un estado personal desde un dato compartido.** `activeWeekdays` es del
   catálogo y respondía casi bien, que es lo que lo hacía difícil de ver: el interruptor funcionaba
   para todo salvo justo para lo que el aprendiz había cambiado.
3. **Una regla de calendario duplicada es una bomba de tiempo silenciosa.** Si dos lugares calculan
   la misma semana por su cuenta, el día que se separen nada va a fallar — solo va a estar mal.

---

## E-146 — "Solo hoy" mandaba una fecha fuera de la semana que la pantalla dibuja, y la etiqueta pedida resultó ser mentira (2026-09-06) — **RESUELTO en el frontend**

**Sintoma exacto, reportado por el dueño** (con la pausa ya leyéndose de vuelta, E-145): *"solo
funciona con la segunda opción, no con la primera de solo hoy"*. Elegir **"Hasta que yo lo
reactive"** apagaba el hábito; elegir **"Solo hoy"** no hacía nada visible.

**Causa.** `opcionesDePausa()` calculaba la primera opción con la fecha del **dispositivo**
(`aFechaIso(new Date())`), y eso está mal por lo que la pantalla **es**: el Plan no registra el día
que uno está viviendo, **planifica hacia adelante** — hoy y todo lo anterior no son editables
(`ULTIMO_INDICE_NO_PLANIFICABLE`). "Hoy" nunca es un día que se pueda tocar ahí.

El caso en que explotó lo deja a la vista: el reporte llegó un **domingo**, y un domingo la pantalla
dibuja la semana SIGUIENTE (E-137), del lunes 7 al domingo 13. "Solo hoy" mandaba el **6**, que no
es ninguna de las siete pestañas:

```
HOY: 2026-09-06 (domingo)
SEMANA MOSTRADA: LUN=09-07 MAR=09-08 MIÉ=09-09 JUE=09-10 VIE=09-11 SÁB=09-12 DOM=09-13
```

El backend guardaba la pausa correctamente (hasta el 6) y la pantalla no apagaba ningún día, porque
ninguno caía dentro del plazo. **"Hasta que yo lo reactive" funcionaba porque no lleva fecha** — de
ahí que fallara exactamente una de las dos opciones, que fue la pista que lo destapó.

**Solución.** Las opciones pasan a ser relativas al **día que la persona está mirando**: se llaman
por esa pestaña y mandan **su** fecha real.

### El segundo hallazgo: la etiqueta que el dueño pidió no se podía cumplir

El pedido textual fue *"cambia el texto por el día: solo el lunes, solo el martes, así
sucesivamente"*. Se implementó así y **al verificarlo resultó ser falso**:

```
elijo MAR + "Solo el martes"  ->  quedan encendidos: MIÉ,JUE,VIE,SÁB,DOM
```

Apagaba **lunes y martes**. Y no era un error del cliente: es lo que el backend hace. La pausa que
`desbloqueos_habito` sabe guardar es un **rango que arranca cuando se toca el botón** —
`pausado_en` es `clock.now()`, y `estaPausadoEl` solo compara contra el extremo de arriba
(`pausadoHasta == null || !fecha.isAfter(pausadoHasta)`). **No existe forma de expresar "salteá ESE
día y ninguno más".**

Así que la etiqueta dice **"Hasta el martes"**, que es exactamente lo que ocurre. Preferir la
etiqueta pedida habría dejado un texto que le miente a la persona sobre lo que acaba de hacer.

**Lo que queda abierto, a la vista:** pausar **un solo día suelto** a mitad de semana no se puede
hoy. No es de esta pantalla: haría falta que el backend acepte un `pausadoDesde` además del
`pausadoHasta` (la columna `pausado_en` existe pero se llena con `now()`, no con una fecha elegida).
Queda planteado, sin tocar.

**Verificacion.** En el navegador, contra el código real, cruzando **cada opción de cada día** con
el mapeo que apaga los días. Cada etiqueta corresponde exactamente con lo que queda apagado:

| Pestaña elegida | Opción | Días que quedan encendidos |
|---|---|---|
| LUN | "Hasta el lunes" | MAR…DOM |
| MAR | "Hasta el martes" | MIÉ…DOM |
| SÁB | "Hasta el sábado" | DOM |
| cualquiera | "Hasta el domingo" | ninguno |
| DOM | (no se ofrece "Hasta el domingo" dos veces) | — |

**Como evitar que vuelva a pasar:**

1. **En una pantalla de planificación, "hoy" casi nunca es la respuesta correcta.** Si la pantalla
   deja elegir un día, las acciones tienen que colgar del **día elegido**, no del reloj. El bug
   estuvo latente desde que existe la opción y solo se hizo visible un domingo, que es cuando "hoy"
   y "la semana mostrada" dejan de solaparse.
2. **Una etiqueta es una afirmación sobre lo que el sistema hace, y hay que verificarla como
   tal.** "Solo el martes" compilaba, se veía bien y era falsa. Se descubrió cruzando el texto del
   botón con el efecto real, no leyendo el código.
3. **Antes de ofrecer una opción, comprobar que el modelo de datos la sabe expresar.** Acá el
   backend solo guarda el fin del rango; ofrecer "solo ese día" era prometer algo que la base no
   puede representar.

---

## E-147 — El cliente de Google reintentaba solo, sin timeout, y el retry de Spring AI nunca se activaba: un 429 de cuota tardaba ~15 s en fallar y salía como 500 (2026-09-06) — **RESUELTO**

**Sintoma exacto.** Con la cuota gratuita de Gemini agotada (ver `sparkie-indexacion/LEEME.md`), una
pregunta al asistente:

- tardaba **muchos segundos** en fallar en vez de fallar al instante,
- salia al cliente como **`500 Internal Server Error`** cuando el fallo era en la busqueda de
  contexto (embedding, que corre de forma sincrona antes del stream),
- y dentro del stream terminaba con el mensaje generico *"Intenta de nuevo en unos segundos"* —
  que era exactamente lo que NO habia que hacer contra una cuota agotada.

**Lo que se creia, y por que estaba mal.** `GoogleGenAiClientesConfig` pasaba
`RetryUtils.DEFAULT_RETRY_TEMPLATE` al modelo de chat y al de embeddings, asi que la hipotesis
inicial fue *"Spring AI reintenta 10 veces con backoff de hasta 3 minutos"*. Se verifico en el
bytecode (`javap` sobre `spring-ai-retry-2.0.0.jar`) y **no es asi para este stack**: el template
es `maxRetries(10)`, `delay 2 s`, `multiplier 5`, `maxDelay 180 s`… pero **solo sobre
`TransientAiException` y `ResourceAccessException`**, y el modulo `spring-ai-google-genai` **no
traduce** las excepciones de su SDK a esos tipos (ni una referencia a `TransientAiException` en
todo el jar). Un 429 llega como `com.google.genai.errors.ClientException`, que para ese template no
es transitoria. **Ese retry nunca se activaba.** Diagnosticar contra el codigo que uno cree que
corre, y no contra el que corre, habria producido un arreglo equivocado.

**La causa real, en dos partes:**

1. **El SDK de Google reintenta por su cuenta aunque nadie lo configure.** `ApiClient` hace
   `httpOptions.retryOptions().orElse(HttpRetryOptions.builder().build())` y con eso instala un
   `RetryInterceptor` **siempre**. Sus defaults (bytecode de `google-genai 1.58.0`): **5 intentos,
   1 s → 60 s con base 2, sobre 408/429/500/502/503/504**. Ante un 429 de cuota: cinco golpes a
   Google con 1+2+4+8 s de espera entre medio, para fallar igual — gastando mas cuota y colgando a
   la persona ~15 s.
2. **Sin timeout HTTP** en el cliente (`Client.builder().apiKey(k).build()` pelado), y sin
   `spring.mvc.async.request-timeout`: una llamada colgada retenia el hilo virtual y la conexion
   SSE hasta el default del contenedor (Tomcat, 30 s), que ademas partia respuestas largas.

Y arriba de todo eso, `GlobalExceptionHandler` no conocia ninguna excepcion de IA: lo que subia
crudo era 500.

**Solucion.**

- `GoogleGenAiClientesConfig`: `HttpOptions` con `timeout` de 60 s (`renaser.ia.google.timeout-ms`)
  y `HttpRetryOptions` explicito: **2 intentos, 0,5 s → 2 s, solo 408/5xx**. El 429 se saca de la
  lista a proposito: una cuota no vuelve en dos segundos, y cuanto esperar lo decide el cliente con
  el `Retry-After`.
- `TraduccionErroresGoogleGenAi` (adaptador): `ApiException` 429 → `ProveedorIaNoDisponibleException`
  (60 s); 408/5xx y timeouts (`GenAiIOException`, `SocketTimeoutException`…) → la misma con 10 s;
  un 400 se deja pasar tal cual, porque es un bug NUESTRO en la solicitud y tiene que sonar como tal.
  Busca la `ApiException` en toda la cadena de causas, no solo arriba.
- `ProveedorIaNoDisponibleException` en `shared/domain` (solo `java.time`) y en
  `GlobalExceptionHandler` → **503 + `Retry-After`**. Es 503 y no 429 porque el 429 de esta API ya
  significa "VOS agotaste tu cuota diaria" y el movil lo muestra asi.
- En el stream (donde ya salio el 200), `ConversacionRenasiaService` distingue esa excepcion y
  emite *"El asistente esta saturado… en unos minutos"* en vez de *"en unos segundos"*.
- `spring.mvc.async.request-timeout: 120s`.

**Verificacion.** Pruebas unitarias sin ningun modelo, con las excepciones reales del SDK
construidas a mano: traduccion (7 casos), adaptador de embeddings (3), adaptador de chat (1, con
`ChatModel` simulado emitiendo `Flux.error`), handler web (1, `@WebMvcTest` → 503 + `Retry-After: 60`),
servicio (1, mensaje propio + cuota liberada). Suite completa en verde — cifras en
`docs/informes/auditoria-nfr-2026-09-06.md`. **Sin verificar en vivo:** provocar un 429 real
implicaria agotar la cuota de produccion a proposito.

**Como evitar que vuelva a pasar:**

1. **Un retry que no se ve en el codigo puede existir igual.** Antes de razonar sobre "cuantas veces
   reintenta esto", abrir el cliente: un SDK puede traer el suyo con defaults propios, y un
   template configurado puede no aplicar a las excepciones que de verdad llegan. Las dos cosas
   pasaron a la vez aca.
2. **Todo cliente HTTP hacia afuera lleva timeout explicito.** Sin excepcion. "El default del SDK"
   no es una respuesta: en este caso era *ninguno*.
3. **Cuota agotada no se reintenta.** Reintentar un 429 de cuota es la forma mas rapida de agotarla
   mas; se devuelve `Retry-After` y se deja que el cliente espere.

---

## E-148 — El WebSocket del chat se autenticaba con un header que escribe el cliente, aceptaba cualquier origen y dejaba publicar directo en `/topic` (2026-09-06) — **RESUELTO** (S-2, S-4 y S-6 de la auditoría del 2026-09-01)

**Sintoma exacto.** No hubo síntoma visible, y por eso importa: la auditoría del 2026-09-01 lo marcó
**crítico** (S-2) y en la bitácora no había ningún rastro de cierre. Verificado en el código el
2026-09-06: `ActorHandshakeInterceptor.beforeHandshake` hacía

```java
String header = request.getHeaders().getFirst("X-Actor-Id");
attributes.put(ATRIBUTO_ACTOR_ID, UUID.fromString(header));
```

Es decir: **quien se conectaba decía quién era**, y con eso `SubscripcionAutorizadaInterceptor`
autorizaba suscribirse a `/topic/conversaciones/{id}` "como" esa persona. Los UUID no son
secretos (catorce DTOs los devuelven). Las rutas HTTP se cerraron con `authenticated()` a lo largo
del día; el canal en vivo quedó exactamente como el 1 de septiembre.

Dos más, en el mismo archivo de configuración: `setAllowedOriginPatterns("*")` (S-6: cualquier
página web podía abrir el socket desde el navegador de un aprendiz logueado) y ninguna guarda sobre
`SEND` a `/topic/**` (S-4: el broker simple reparte a los suscriptores todo lo que reciba ahí, venga
del servidor o de un cliente, saltándose el caso de uso y su persistencia).

**Por qué pasó desapercibido.** Ningún cliente del repo usa el WebSocket (verificado: cero
referencias a `stomp`, `sockjs` o `WebSocket` en `src/` del móvil; el chat va por REST). Una puerta
que nadie usa no genera bugs — solo intrusos.

**Solución.**

- `ActorHandshakeInterceptor` resuelve el actor **desde Spring Session**: lee `X-Auth-Token`
  (header) o `?token=` (los navegadores no pueden mandar cabeceras en el handshake), busca la
  sesión en Redis (`SessionRepository.findById`) y toma el usuario del `SecurityContext` que guardó
  el login. Sin sesión válida: 403, sin socket. `X-Actor-Id` ya no identifica a nadie.
- `WebSocketConfig`: `setAllowedOrigins(renaser.web.cors.origenes)` — los mismos orígenes que CORS.
- `SubscripcionAutorizadaInterceptor`: un `SEND` cuyo destino empiece por `/topic/` se rechaza; los
  clientes escriben en `/app/**`, el único que publica en `/topic` es el servidor.

**Verificación.** `ActorHandshakeInterceptorTest` (5): sesión válida por header, por `?token=`,
**solo `X-Actor-Id` → rechazado**, token desconocido → 403, sesión sin autenticación → rechazado.
`SubscripcionAutorizadaInterceptorEnvioTest` (2): `SEND /topic/...` → `MessagingException`;
`SEND /app/...` pasa. Todo sin levantar Spring: `MapSession` y `MockHttpServletRequest`.
**Sin verificar en vivo:** no hay cliente que abra el socket; se probará cuando lo haya.

**Como evitar que vuelva a pasar:** cuando un mecanismo de autenticación cambia (acá: de header a
sesión), **enumerar todos los puntos de entrada**, no solo los `@RestController`. WebSocket, SSE,
schedulers que actúan "como" alguien, y webhooks son puntos de entrada aunque no tengan `@Mapping`.

---

## E-149 — Detrás de CloudFront, todos los usuarios compartían la misma "IP" para los límites de tasa (2026-09-06) — **RESUELTO**

**Sintoma exacto.** Ninguno todavía, y era cuestión de tiempo: `AutenticacionController`,
`AccountRequestController` y `ResetContrasenaPorCodigoController` alimentan los limitadores por IP
con `request.getRemoteAddr()`, y el backend vive detrás de CloudFront **sin
`server.forward-headers-strategy`** (verificado: ausente en todos los yaml). Detrás de un proxy,
`getRemoteAddr()` es la IP del proxy. Consecuencia: el límite de **50 intentos de login por hora
"por IP"** era en realidad **un solo contador para todo el producto** (repartido entre las pocas IPs
de borde de CloudFront). Seis personas equivocando la contraseña ocho veces bloqueaban el login de
todo el mundo durante una hora; un atacante lo lograba solo, y encima el 429 le confirmaba que
funcionó. Lo mismo para el límite de altas (60/h) y el de reset.

**Solución.** `server.forward-headers-strategy: framework` (`FORWARD_HEADERS_STRATEGY`): Spring
registra el `ForwardedHeaderFilter`, que reescribe la dirección remota con el primer valor de
`X-Forwarded-For` — el que CloudFront pone: el cliente real. **Se confía en ese header porque el
security group solo deja llegar al 8080 desde los rangos de CloudFront (y la IP del dueño)**;
falsearlo exige saltarse CloudFront, que es exactamente el pendiente de la cabecera secreta de
origen (informe, §2.3). Cuando eso se cierre, la confianza en `X-Forwarded-For` queda completa.

**La trampa del arnés, que vale registrar.** La primera versión de la prueba
(`AccountRequestControllerIpRealTest`, `@WebMvcTest` + `server.forward-headers-strategy=framework`)
falló con `expected "203.0.113.9" but was "127.0.0.1"`. No era la configuración: **en Spring Boot 4
el bean del filtro se movió a `spring-boot-web-server`**, y esa auto-configuración **no forma parte
del slice `@WebMvcTest`** (la lista `AutoConfigureWebMvc.imports` trae `WebMvcAutoConfiguration`,
`ErrorMvcAutoConfiguration`, `HttpEncoding`… y ninguna de web-server). En el contexto completo de
producción sí se carga. La prueba registra el filtro igual que lo hace Boot
(`FilterRegistrationBean<ForwardedHeaderFilter>`) y lo dice en su javadoc: lo que verifica es el
efecto del filtro sobre `getRemoteAddr()`, que es lo que consumen los controllers; el cableado
propiedad → bean es de Boot y quedó verificado en el bytecode de `ServletWebServerConfiguration`.

**Como evitar que vuelva a pasar:**

1. **Todo backend detrás de un proxy declara cómo obtiene la IP real** el mismo día que se pone el
   proxy. Un rate limit por IP sin eso no protege: castiga a todos por igual.
2. **Un `@WebMvcTest` no es el contexto de producción.** Lo que se configura por propiedad en una
   auto-configuración fuera del slice hay que importarlo a mano en la prueba, o probarlo en un IT.

---

## E-150 — `unTokenVencidoYaNoSePuedeConsumir` falla solo dentro de la suite completa (2026-09-06) — **DIAGNOSTICADO, prueba sin corregir. REAPARECIÓ el 2026-09-07**

> **Al 2026-09-07 sigue abierto y ya volvió a pasar**, en la corrida completa del nivel mensual:
> `Expecting an empty Optional but was containing value: e690715c-…`. Se reintentó aislada, pasó
> 5 de 5, y la suite completa volvió a quedar verde — el protocolo del punto 2 de abajo funciona,
> pero cuesta una corrida de seis minutos cada vez que ocurre.
>
> El arreglo se escribió y se verificó (espera activa sobre un testigo aparte, porque sondear
> `consumir` haría pasar la prueba por el motivo equivocado: es GETDEL y la primera lectura borra
> el token). **Se revirtió a pedido del dueño**, junto con otros cambios, mientras se descartaba
> una sospecha que resultó infundada. Queda pendiente volver a aplicarlo.

**Sintoma exacto**, corriendo `./mvnw clean verify` entero justo antes de mergear a `master`:

```
[ERROR] TokenVerificacionEmailRedisAdapterTest.unTokenVencidoYaNoSePuedeConsumir:60
Expecting an empty Optional but was containing value: "verificado@renaser.dev"
[ERROR] Tests run: 2465, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

**Qué hace la prueba** (`TokenVerificacionEmailRedisAdapterTest:55-61`): genera un token con
`Duration.ofMillis(500)`, hace `Thread.sleep(900)` y espera que Redis ya lo haya vencido. El margen
real es de **400 ms**.

**Lo que se descartó, y por qué queda escrito.** No es una regresión de la rama de auditoría:

- **No es un TTL de menos de un segundo mal convertido.** `TokenVerificacionEmailRedisAdapter.generar`
  pasa el `Duration` tal cual a `opsForValue().set(clave, email, vigencia)`, y Spring Data Redis usa
  `PSETEX` (milisegundos) cuando la duración no es un número entero de segundos. Si fuera eso,
  fallaría **siempre**, no de a ratos.
- **No es el `spring.data.redis.timeout: 3s` que agregó la auditoría.** Ese es el tiempo máximo de
  un comando, no el TTL de una clave. Además, un timeout habría dado excepción, no un valor.
- **No es la prueba en sí.** Corrida sola, `-Dtest=TokenVerificacionEmailRedisAdapterTest`, pasó
  **tres de tres** (4 pruebas cada vez, 0 fallos).

**Causa real: el reloj del contenedor bajo carga.** El vencimiento lo decide Redis con el reloj de
**su** contenedor, mientras que el `sleep(900)` lo cuenta la JVM con el reloj del **host**. Con la
máquina saturada por las 2.465 pruebas y Docker Desktop sobre WSL2, la VM se atrasa respecto del
host lo suficiente como para que 400 ms de margen no alcancen. Sola, con la máquina libre, los dos
relojes van juntos y el margen sobra.

**Estado.** La prueba **no se tocó** en este cambio: se estaba mergeando a producción lo que ya
estaba en la rama, y modificar una prueba en ese momento es meter una variable que nadie pidió. La
suite se volvió a correr entera y quedó en verde antes de mergear.

**Cómo evitar que vuelva a pasar:**

1. **Una prueba que espera un vencimiento no se escribe con `Thread.sleep` y un margen chico.** Va
   con espera activa (`Awaitility.await().atMost(...).until(...)`), que da por buena la primera
   lectura correcta en vez de apostar a un instante. Es el arreglo pendiente para esta prueba y
   para `TokenResetContrasenaRedisAdapterTest`, que tiene la misma forma.
2. **Un fallo de una prueba de tiempo dentro de la suite completa se reintenta en aislamiento antes
   de creerle.** Si pasa sola y falla acompañada, la hipótesis es el reloj o la carga, no el código.
3. **Ojo con relojes de dos dominios en la misma aserción.** Si el que vence es Redis (o Postgres, o
   el contenedor) y el que espera es la JVM, son dos relojes distintos: el margen tiene que ser
   holgado o la espera tiene que ser activa.

---

## E-151 — Con conexión IPv6 nadie podía registrarse: `invalid input syntax for type inet` (2026-09-06) — **RESUELTO**

**Síntoma exacto**, en los registros del contenedor, 13 veces entre las 01:19 y las 01:30 UTC del 7 de septiembre:

```
org.postgresql.util.PSQLException: ERROR: invalid input syntax for type inet: "[2803:9810:6075:9310:c63b:3904:e158:3228]"
  Where: unnamed portal parameter $6 = '...'
    at org.hibernate.engine.jdbc.mutation.internal.AbstractMutationExecutor.execute
    [insert into renaser.solicitudes_cuenta (... ip_solicitud ...)]
→ DataIntegrityViolationException → 409 "violacion de integridad en la base"
```

**Lo que veía el aprendiz:** el alta fallaba. Nada más. Cuatro direcciones IPv6 distintas afectadas, o sea **al menos cuatro personas que no pudieron entrar esa noche**.

**Causa real: una regresión de E-149, del día anterior.** Al poner `server.forward-headers-strategy=framework` para que los límites por IP fueran por persona y no un contador global, `getRemoteAddr()` pasó a devolver la IP real del cliente. Con IPv4 no cambió nada. Con **IPv6 sí**: el `ForwardedHeaderFilter` de Spring reconstruye la dirección como **host de URI**, y en un URI un IPv6 va **entre corchetes**. Postgres rechaza esa forma en una columna `inet`.

**Por qué pasó desapercibido:** en IPv4 —la conexión de quien desplegó y de la mayoría de las pruebas— todo seguía funcionando. El fallo era invisible salvo que probaras desde una red IPv6, que en Perú y Bolivia es común en datos móviles.

**Solución.** `shared/web/DireccionIpDelCliente`: normaliza la dirección en el **borde**, quitando corchetes y el identificador de zona (`fe80::1%eth0`), antes de que entre al sistema. Se normaliza ahí y no en el repositorio porque la IP entra por **nueve** lugares (alta, login, reset, verificación de correo, social) y varios la usan para contar límites: si se normalizara solo al guardar, los contadores compararían `[2803:...]` contra `2803:...` y el límite por IP dejaría de acertar **sin avisar**.

**Secuela que costó más que el error:** ver E-152. Cada intento fallido quemaba el código de verificación de la persona, y de tanto pedir uno nuevo tres aprendices se pasaron del límite de 5 por hora y hubo que destrabarlas a mano borrando su contador en Redis.

**Cómo evitar que vuelva a pasar:**

1. **Toda prueba de una IP tiene que incluir un caso IPv6.** La prueba de E-149 verificaba `X-Forwarded-For` con una IPv4 y por eso no atrapó nada. Ahora `AccountRequestControllerIpRealTest` tiene los dos casos, y el de IPv6 falla contra el código anterior al arreglo.
2. **Cuando un cambio toca cómo se obtiene un dato del transporte, revisar TODAS las formas que ese dato puede tener**, no solo la común. Una dirección puede ser IPv4, IPv6, IPv6 entre corchetes, con puerto o con zona.
3. **Un `DataIntegrityViolationException` que sale como 409 esconde la causa.** El 409 decía "violación de integridad" y no "tu IP no se pudo guardar". Cuando el síntoma sea un 409 inexplicable en un alta, mirar el `Caused by` del log antes que cualquier otra cosa.

---

## E-152 — Un alta fallida quemaba el código de verificación de la persona (2026-09-06) — **RESUELTO**

**Síntoma exacto**, 14 veces la misma noche, cuatro de ellas en ráfaga entre las 01:29:57 y las 01:29:59 UTC:

```
400 -> "El codigo no es valido o ya vencio"
```

…a personas que acababan de recibir su código y no habían hecho nada mal.

**Causa real.** `AccountRequestService.submit` consumía el token de verificación **al principio**, con `consumir`, que en Redis es GETDEL: lo lee y lo borra en la misma operación. Redis **no participa de la transacción de Postgres**. Entonces, cuando el guardado fallaba —esa noche, por E-151— la base deshacía todo **pero el token ya estaba gastado**. La persona reintentaba con su código y el sistema le decía que no valía.

**Y por qué no alcanzaba con mover el consumo al final del método:** el fallo de aquella noche ocurría **durante el commit**. Hibernate vuelca los INSERT al cerrar la transacción, no al invocar `save()`. Cualquier consumo dentro del método, aunque fuera la última línea, seguiría ocurriendo antes de ese commit y quemaría el token igual.

**Solución.** Se agregó `TokenVerificacionEmailPort.emailDe(token)`, una lectura que **no borra**, para validar; y el consumo se colgó de `afterCommit` con `TransactionSynchronizationManager`. Si la transacción no comitea, el código de la persona sigue sirviendo.

**La carrera que esto abre, y por qué está contenida:** entre la validación y el consumo, dos altas simultáneas con el mismo token pasarían las dos. Lo corta la unicidad del correo (`rejectIfEmailYaRegistrado` más el UNIQUE de `usuarios.email`), así que la segunda no crea nada. Es el caso del doble clic y termina en una sola cuenta.

**Cómo evitar que vuelva a pasar:**

1. **Un efecto irreversible fuera de la transacción no va antes del commit.** Redis, S3, un correo, una llamada a un tercero: si la base puede deshacerse y eso no, va en `afterCommit`. Vale para todo el repo, no solo para este token.
2. **Cuidado con validar consumiendo.** Si la única lectura disponible destruye lo que lee, hace falta una lectura no destructiva para validar, o el error se cobra el dato del usuario.
3. **Un fallo técnico no debe costarle al usuario un recurso limitado.** Acá le costaba un código de los 5 por hora, y por eso el error de una capa terminó bloqueando gente en otra.

---

## E-153 — Sin espera entre reenvíos, los 5 códigos de la hora se gastaban en segundos (2026-09-06) — **RESUELTO**

**Síntoma exacto:** tres aprendices bloqueadas para registrarse, con estos contadores en Redis (`reset-password:rl:email-verification:email:*`), contra un límite de 5 por hora:

```
luisajandel@gmail.com      = 18
yenny01159@gmail.com       = 15
severinafortuna79@gmail.com = 16
```

Hubo que destrabarlas a mano borrando la clave, y recién entonces pudieron entrar.

**Causa real, en dos partes.** La de fondo fue E-151 + E-152: el alta fallaba y les quemaba el código, así que pedían otro. Pero lo que convirtió eso en un bloqueo fue que **no había ninguna espera entre un envío y el siguiente**: los 5 códigos de la hora se podían pedir en cinco segundos apretando "reenviar".

**Lo que se verificó antes de tocar nada.** Los límites que ya había **son el estándar de la industria** y no había que subirlos: 5 por correo por hora, 10-20 por IP, 5 intentos de tipeo, vigencia de 5-10 minutos. Lo que faltaba era la espera, que es lo que las mismas fuentes recomiendan y lo que corta entre el 60 y el 70 % de los reenvíos inútiles.

**Solución.** `VerificacionEmailService.ESPERA_ENTRE_ENVIOS` = 30 s, con el mismo contador atómico que los otros límites (máximo 1, ventana de 30 s), y el mensaje del 429 pasó a decir cuántos segundos faltan en vez de un "límite excedido" que no explica nada.

Dos decisiones dentro del arreglo:

- **La espera cuenta por correo, no por IP.** Dos personas en la misma casa o el mismo local comparten IP, y hacer esperar a una por lo que pidió la otra sería castigar a quien no hizo nada. El abuso desde una IP ya lo cubre su propio límite.
- **Se revisa ANTES que los límites por hora.** Si fuera al revés, cada clic impaciente gastaría uno de los 5 envíos antes de rebotar, y el remedio provocaría el bloqueo que viene a evitar. Hay una prueba que fija ese orden.

**Verificado en producción**: dos peticiones seguidas a `POST /auth/email-verification/send` con un dominio reservado (`example.com`, que no llega a ninguna persona) dan 202 y después `429 {"message":"Espera 30 segundos antes de pedir otro codigo"}`.

**Cómo evitar que vuelva a pasar:**

1. **Un límite por ventana sin espera entre intentos no protege a nadie: solo bloquea.** Los dos van juntos.
2. **Un 429 tiene que decir cuánto falta.** Si no, la persona reintenta, gasta más y se hunde más.
3. **Cuando haya que destrabar a alguien, el contador vive en `reset-password:rl:email-verification:email:<correo>`.** Borrar esa clave lo libera al instante; la ventana se rehace sola a la hora.
4. El contador **distingue mayúsculas**: `Nombre@gmail.com` y `nombre@gmail.com` cuentan aparte. No bloquea de más —hace el límite más flojo— pero conviene normalizar el correo antes de contar.

---

## E-154 — Una fecha escrita a mano en un fixture rompió todo `verify` al cambiar el día (2026-09-07) — **RESUELTO**

**Síntoma exacto**, en las dos pruebas de la clase:

```
[ERROR] ConfirmacionRollbackOnlyTransaccionIT.confirmarSinFalloSigueFuncionandoIgual:121
  IllegalState No puedes confirmar asistencia a una ocurrencia de dias pasados
[ERROR] ConfirmacionRollbackOnlyTransaccionIT.confirmarSobreviveAUnFalloAlCancelarAvisos:107
  Expecting code not to raise a throwable but caught "java.lang.IllegalStateException: ..."
```

**Causa real.** El fixture tenía `private static final Instant INICIA_EN = Instant.parse("2026-09-05T19:00:00Z")`. Esa prueba corre con el **reloj real** (es un IT, no usa `FixedClock`), y `ConfirmacionService.confirmar` rechaza toda ocurrencia anterior al arranque del día de hoy en UTC menos `MARGEN_OCURRENCIA_PASADA_HORAS` (12 h). Con esa fecha fija la prueba pasaba mientras "hoy" fuera el 5 o el 6 de septiembre y **empezaba a fallar sola el 7**, sin que nadie tocara una línea.

**Cuándo falló por primera vez:** a las 00:20 UTC del 7 de septiembre, o sea **apenas UTC cambió de día**. La hora local en Lima era todavía el 6, lo que hizo el diagnóstico más confuso: "ayer andaba".

**Lo que costó de más:** se sospechó de un cambio propio y se revirtió trabajo bueno para descartarlo. La fecha estaba en el repo desde el 2 de septiembre (commit `2c60f78`), no la había puesto ese cambio. **Verificarlo es un `git log -S` de diez segundos** y hubiera evitado la vuelta entera.

**Solución.** `Instant.now().truncatedTo(ChronoUnit.HOURS).plus(2, ChronoUnit.HOURS)`: siempre dentro de la ventana permitida, corra cuando corra la suite, y además es el caso realista, porque se confirma asistencia a un evento que todavía no ocurrió.

**Cómo evitar que vuelva a pasar:**

1. **Una fecha absoluta en un fixture que corre con el reloj real no es un dato: es una fecha de vencimiento.** Si la prueba no fija el reloj con `FixedClock`, su fecha se calcula relativa a `now()`.
2. **Antes de culpar al cambio propio, `git log -S "<el valor sospechoso>"`.** Dice quién lo puso y cuándo, en segundos.
3. **Se revisaron los demás ITs con fechas fijas.** Los de `habits`, `evidence` y `onboarding` usan `FixedClock` y están a salvo; `PausaHabitoPersonalIT` pasa sus fechas como entradas explícitas y compara contra ellas, no contra hoy, así que tampoco vence. El único afectado era este.

---

## E-155 — Intentar despliegue sin caida dejo produccion caida 7 horas (2026-09-07) — **RESUELTO, con leccion cara**

**Sintoma exacto.** `https://djbooeq09skac.cloudfront.net/actuator/health` devolvia:

```
HTTP/1.1 504 Gateway Timeout
<TITLE>ERROR: The request could not be satisfied</TITLE>
Generated by cloudfront (CloudFront) HTTP3 Server
```

Y, peor, la instancia dejo de aceptar comandos:

```
aws ssm describe-instance-information ... --query "...PingStatus"
ConnectionLost   2026-09-06T23:36:15-05:00
```

**Que se estaba intentando.** Cada despliegue dejaba el sitio caido ~45 s: el script borra el contenedor y levanta el nuevo, y la aplicacion tarda eso en responder. Se quiso eliminar esa ventana metiendo un nginx delante que apuntara al contenedor activo (azul/verde), levantar el nuevo mientras el viejo seguia atendiendo y recien ahi recargar nginx.

**Causa real: la instancia no tiene memoria para dos JVM.** Es una `t3.small` con **1.909 MB** de RAM total, y con un solo backend andando ya usa ~1.150 MB. El plan azul/verde exige que **los dos** contenedores corran a la vez unos 45 s. Al levantar el segundo, la memoria se agoto, el sistema empezo a paginar, y lo primero que murio fue el **agente de SSM** — con lo cual el script que estaba a mitad de camino quedo trunco (el contenedor viejo ya borrado, el proxy sin levantar) y **sin forma de mandar el comando de arreglo**.

Todo lo que se probo antes de eso habia salido bien y no era el problema: la configuracion de nginx era valida (`nginx -t` OK), proxeaba correctamente, y se verifico de punta a punta que la IP real del cliente llegaba hasta la aplicacion (contador de Redis contra `198.51.100.42`, no contra una IP interna de Docker). El diseno era correcto; **el dimensionamiento no**.

**Agravante que alargo la caida de minutos a horas.** El contenedor sobrante (`backend-azul`) se creo con `--restart unless-stopped`. En cada reinicio de la instancia **volvia a levantarse solo**, junto con `backend`, y se repetia el agotamiento de memoria: SSM conectaba unos segundos y se caia otra vez, antes de alcanzar a ejecutar la limpieza. Tres intentos de reparacion fallaron asi.

**Como se salio.** Encolar el comando **antes** de reiniciar, y que fuera **minimo** (`docker rm -f backend-azul proxy proxy-prueba`, sin esperas ni bucles). Un comando de SSM enviado con el agente desconectado queda en cola y se entrega apenas conecta: se ejecuta en el primer segundo de la ventana, antes de que la memoria se agote. Con el sobrante eliminado, `backend` quedo solo, la memoria se normalizo y el sitio volvio.

**Como evitar que vuelva a pasar:**

1. **No hay azul/verde posible en esta instancia con la memoria actual.** Dos JVM de Spring no entran en 1,9 GB. Si se quiere despliegue sin caida hay que **subir la instancia primero** (a `t3.medium`, 4 GB) o cambiar de estrategia. Intentarlo sin eso no es un riesgo: es un fallo garantizado.
2. **Nada temporal se crea con `--restart unless-stopped`.** Un contenedor de prueba con esa politica resucita en cada arranque y convierte un problema de minutos en uno que sobrevive a los reinicios.
3. **Antes de un cambio de infraestructura, tener el comando de reparacion ENCOLADO**, no escrito para mandarlo despues. Si el cambio rompe el canal por el que se manda, el comando escrito no sirve de nada.
4. **`ConnectionLost` en SSM con la instancia `running` y los status checks en `ok` es, casi siempre, presion de memoria.** No es red ni es el agente: es que no queda RAM. Mirar `free -m` apenas se recupere el acceso.
5. **Y la de fondo: un cambio de infraestructura en produccion no se hace de madrugada, con usuarios activos y con el dueno por irse a dormir.** La ventana de 45 s que se queria eliminar costaba, como mucho, un minuto por despliegue. Intentar eliminarla costo siete horas de caida.

**Lo que quedo pendiente**, si algun dia se retoma: la configuracion de nginx probada (con `X-Forwarded-For` correcto, WebSocket para `/ws`, `proxy_read_timeout` de 180 s para el chat y `proxy_buffering off` para el streaming) funcionaba. Lo unico que falta es una instancia que aguante los dos contenedores a la vez.


## E-156 — Editar la hora de un hábito para un día afecta los demás (2026-09-07)

- **Síntoma reportado:** "cuando yo edita un habito la hora para un dia exacto y luego vou a otro dia prebalece el editar del nuevo habito, no se respeta por el dia".
- **Causa:** Plan seleccionaba día pero guardaba solo hábito+hora. La preferencia general y el estado de React tenían una única hora para toda la semana.
- **Corrección:** V37 y lectura/escritura por fecha; consumidores diarios usan la fecha del registro; Plan captura y consulta la fecha elegida y descarta respuestas de otra selección. Se conserva el horario general para el resto de días.
- **Prevención:** regresión con dos fechas editadas, recarga, otro participante y mismo día de la semana siguiente; pruebas de respuesta lenta y error de carga. Backend primero: el frontend exige que GET confirme la fecha.
- **Incidencias de la implementación detectadas por las pruebas:** `Cannot invoke "java.lang.Integer.intValue()"` por ternario que desempaquetaba un recordatorio nulo en un pendiente legado; corregido preservando `Integer`. Una consulta fallida conservaba el arreglo del día anterior; ahora lo vacía. El fixture HTTP inicial omitía campos obligatorios de respuesta; se completó para validar el contrato real.
- **Verificación parcial:** 79 pruebas de backend sin contenedores (incluidas arquitectura, autorización y contrato HTTP), 5 regresiones de frontend y TypeScript en verde. La primera pasada con Postgres confirmó aislamiento entre fechas y detectó el desempaquetado nulo descrito arriba, ya corregido. **Verificación completa:** pendiente de conexión a Testcontainers Cloud; ver `docs/PRUEBAS_EN_CLOUD.md`. No se da por completado `clean verify` hasta ejecutarlo.

## E-157 — Se editó una migración ya aplicada y el backend dejó de arrancar (2026-09-07)

- **Síntoma reportado:** el backend no levanta en local. `FlywayValidateException: Migrations have failed validation` → `Migration checksum mismatch for migration version 37. Applied to database: -540300719. Resolved locally: -2109011199`.
- **Causa:** `V37__horarios_habito_por_fecha.sql` se aplicó a la base de dev el 2026-09-07 a las 12:47 y **después se editó el archivo**. Flyway guarda el checksum del contenido en `flyway_schema_history`; cualquier cambio posterior —aunque sea un comentario— lo invalida. La edición fue cosmética: comparadas una a una, la tabla en la base ya tenía las 9 columnas con sus tipos y nulabilidad, la PK, los dos CHECK, las dos FK con `ON DELETE CASCADE` y el índice que declara el archivo actual. **No faltaba DDL, solo coincidía el checksum.**
- **Corrección:** `flyway repair` — recalcula el checksum de lo aplicado contra los archivos actuales sin tocar ninguna tabla de datos. Equivalente directo: `update public.flyway_schema_history set checksum = <local> where version = '37';`. Verificar SIEMPRE antes que la estructura real coincida con el archivo; si no coincide, `repair` deja la base mintiendo y falta aplicar el DDL a mano.
- **Prevención:** **una migración aplicada no se toca nunca más, ni para arreglarle un comentario.** Si hay algo que cambiar, va una migración nueva. En dev el costo es un `repair`; en producción la misma edición es un despliegue que no arranca, y ahí `repair` es una decisión con la base en caliente. El caso peligroso —el que este síntoma NO distingue por sí solo— es la edición que sí cambia DDL: el checksum falla igual, pero reparar sin mirar deja la base sin lo que el archivo promete.
- **Verificación:** el desajuste es del estado de la base de dev, no de los archivos: `HorarioPorFechaPersistenceAdapterTest` corre las migraciones de cero contra un Postgres real (Testcontainers) y pasa, V38 incluida.

## E-158 — Pausar un hábito "hasta el jueves" apagaba también el lunes, el martes y el miércoles (2026-09-07)

- **Síntoma reportado:** al pausar un hábito con fecha de fin, los días anteriores de esa semana aparecían apagados en el Plan.
- **Causa:** `DesbloqueoHabito.estaPausadoEl` comparaba **solo contra `pausado_hasta`**: `pausadoHasta == null || !hoy.isAfter(pausadoHasta)`. O sea devolvía `true` para toda fecha anterior o igual a ese límite, incluidas las de antes de que la pausa existiera. V31 describe la pausa como un rango que arranca al tocar el botón; el código tenía implementada solo la mitad de arriba. `pausado_en` se usaba únicamente como `!= null` (el interruptor) y como dato de auditoría.
- **Corrección:** el límite de abajo **no necesitó columna nueva** — es `pausado_en`, que V23 ya guardaba justo para eso; lo único que faltaba era leerlo. Para pasarlo a día del calendario hace falta la zona del participante, así que `estaPausadoEl` la recibe por parámetro en vez de tomarla del servidor (E-91). Único llamador de producción: `RegistroService.generarInterno`, que ya la tenía.
- **Prevención:** `DesbloqueoHabitoPausaTest.unaPausaNoApagaLosDiasANTERIORESaHaberlaPuesto` fija la regresión — si se borra la comparación del límite inferior, se pone rojo. Lección general: cuando una migración describe un **rango**, el test tiene que ejercitar los dos extremos. Acá los tests cubrían el día de fin y el siguiente, y ninguno miraba hacia atrás.
- **Verificación:** 86 pruebas en verde, incluidas arquitectura, contrato HTTP y persistencia con Postgres real.

## E-159 — "Avisame 15 minutos antes" volvía del servidor sin los minutos (2026-09-07)

- **Síntoma:** al guardar un horario con recordatorio, `GET /habit-preferences` devolvía `reminderEnabled: true` con `reminderMinutesBefore: null`. Lo encontró el FLUJO 7 de `scripts/flujos-training.sh`, no una persona: el flujo guarda 15 minutos y vuelve a leer.
- **Causa:** el PATCH difiere el cambio a mañana cuando la ventana de hoy ya arrancó (D-91) y se llevaba el recordatorio con las horas, al `CambioHorarioPendiente`. Pero la lectura no lo busca ahí: `ConsultaPreferenciasHorarioService.construirVista` saca el recordatorio **solo** de `PreferenciaHorario`, y `CambioProgramado` transporta únicamente las dos horas y la fecha efectiva. Entre el guardado y la promoción nocturna los minutos no existían para ningún lector. La misma forma que E-156 y que el defecto de §4 de `VERIFICACION_TRAINING_2026-09-07.md`: **dos caminos para el mismo dato y solo uno actualizado.**
- **Por qué casi no se ve:** el conjunto de avisos vive en el teléfono (`minutos_recordatorio` es UN número; la app soporta varios), así que en el dispositivo donde guardaste no se nota. Se nota en uno **sin** ese estado local — reinstalación o segundo teléfono —: el respaldo `previa.reminderMinutesBefore ?? 0` convertía "15 minutos antes" en "a la hora exacta", en silencio.
- **Corrección:** el recordatorio se aplica **hoy**; las horas se siguen difiriendo. D-91 protege la ventana del día en curso —a qué hora te toca—, no la antelación del aviso, que cuelga de la hora que esté rigiendo, sea la vieja o la nueva. `asegurarPreferenciaVigente` pasó a llamarse `asegurarPreferenciaYRecordatorio` y carga la preferencia existente en lugar de salir temprano, así que **las horas quedan intactas en los dos caminos**. `PromocionCambioHorarioService` reescribe los mismos valores: idempotente.
- **Prevención:** el test que se puso rojo era `siLaVentanaDeHoyYaArrancoElCambioQuedaDiferidoParaManana`, con `verify(savePreferenciaPort, never()).save(any())`. Esa aserción **no describía el invariante, describía la implementación**: lo que el día en curso protege son las horas, no que no se escriba la fila. Ahora captura lo guardado y verifica las horas viejas (08:00/10:00) más el recordatorio pedido. Lección: un `never()` sobre un puerto de escritura casi siempre es un invariante mal escrito — decí *qué* no puede cambiar, no *que no se escriba*.
- **Verificación:** 2570 pruebas en verde. Falta re-correr el FLUJO 7 contra el servidor con el arreglo.

---

## E-160 — La JVM tiene permiso para usar más memoria de la que la máquina tiene (2026-09-07)

- **Síntoma:** producción caída sin ningún error en el log de la aplicación. El contenedor desaparece y CloudFront devuelve `504 Gateway Timeout`. En el journal del host:

  ```
  RenaserHikari:h invoked oom-killer: gfp_mask=0x140cca(GFP_HIGHUSER_MOVABLE|__GFP_COMP)
  oom-kill:constraint=CONSTRAINT_NONE,nodemask=(null),...,global_oom,task=java,pid=120784
  Out of memory: Killed process 120784 (java) total-vm:4199428kB, anon-rss:1065728kB
  ```

- **Causa:** el contenedor corre con `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` y **sin límite de memoria de Docker** (`HostConfig.Memory=0`). Sin límite de cgroup, la JVM ve la máquina entera y se autoriza el 75 % de ella: `MaxHeapSize = 1.503.657.984` (**1.434 MB**). La instancia es una `t3.small` de **1.909 MB y sin swap**, de la que el sistema, `dockerd`, `containerd`, `redis` y el agente de SSM ya se llevan ~250 MB fijos. Es decir: **el techo del heap está por encima de lo que la máquina puede dar.** Mientras nada empuje al proceso a crecer, aguanta; en cuanto algo lo empuja, el kernel dispara el OOM-killer y el proceso más grande es siempre la JVM.
- **La confusión que hay que evitar:** `constraint=CONSTRAINT_NONE` + `global_oom` significa que se quedó sin memoria **el host**, no el contenedor. Por eso `docker inspect` muestra `OOMKilled=false` y parece que Docker no tuvo nada que ver — es exactamente al revés: si hubiera habido un límite de contenedor, la JVM se habría dimensionado dentro de él y esto no pasaría.
- **Cómo reconocerlo rápido:** SSM en `ConnectionLost` con la instancia `running` y los status checks en `ok` es, casi siempre, presión de memoria (ver E-155). Lo primero al recuperar el acceso es `free -m` y `journalctl | grep -i oom`, no los logs de la aplicación: la aplicación no llegó a loguear nada porque la mataron desde afuera.
- **Estado:** **abierto, sin corregir.** Medido el 2026-09-07 con el servicio arriba: `java` en 1.005 MB de 1.865 GiB (54 %), 70 MB libres, 527 MB disponibles, **swap en 0**. Doce horas sin reiniciarse, pero con el mismo techo mal puesto.
- **Corrección propuesta** (no aplicada, requiere reinicio del contenedor y por lo tanto la ventana de ~45 s):
  1. Bajar `MaxRAMPercentage` de 75 a ~40 (≈760 MB de heap) y ponerle `--memory=1300m` al contenedor, para que la JVM se dimensione contra un límite real y no contra la máquina entera.
  2. Agregar **2 GB de swap**. No requiere reiniciar nada y convierte "el proceso muere" en "el proceso va más lento": el kernel pagina en vez de matar.
  3. `t3.medium` (4 GB) solo si además se quiere despliegue sin caída — ver E-155, donde el intento de azul/verde sobre esta misma instancia costó siete horas de caída justamente por esto.
- **Prevención:** ningún contenedor de la aplicación debe correr sin `--memory`. Un límite de cgroup no es una restricción: es **la información que la JVM necesita** para no pedir más de lo que hay. Sin él, `MaxRAMPercentage` se calcula contra la RAM total del host y el número que uno cree estar poniendo no es el que la JVM entiende.

---

## E-161 — "Sigo en el día 0": el día se derivaba bien y se leía mal (2026-09-08) — **RESUELTO**

- **Síntoma reportado**, palabras del dueño: *"mi cuenta con el correo ricardoismael777 comenzaba hoy
  pero sigo en el día 0 ¿qué fue bro? ¿se malogró esto si ya estaba solucionado esta parte?"*. La
  cuenta `96f7c5bf-00a5-4c76-93a3-69821ed5a20b` tenía `fecha_inicio = 2026-09-07` (ver E-137), o sea
  el 2026-09-08 tenía que verse el **día 2**, y `GET /api/v1/home` devolvía `diaPrograma = 0`.

- **Lo primero que hay que descartar, y que acá NO era: E-91 no se rompió.** El cálculo del dominio
  (`ParticipacionPrograma.diaProgramaDerivado`) estaba intacto, el cron seguía siendo horario, y el
  barrido selecciona a TODOS los activados (`programa_activado_en IS NOT NULL`) — sin filtro por día,
  así que una fila en 0 sí entraba. Lo verificado en vivo: producción arriba, el backend local
  respondiendo 200 en `/actuator/health` a las 09:27 y **conexión rehusada tres minutos después**.

- **Causa real — son dos defectos independientes:**

  1. **El camino de LECTURA nunca derivó nada.** V20 hizo el día derivado *en el dominio*, pero
     `ConsultarResumenParticipacionPersistenceAdapter` devuelve `COALESCE(pp.dia_programa, 0)`, y esa
     columna la escribe ÚNICAMENTE `AvanzarDiaProgramaScheduler`. O sea: el dominio sabía que iba por
     el día 2 y la app mostraba lo último que alguien hubiera guardado. Si el backend no estuvo
     arriba en el minuto `:05` de la hora que cruza la medianoche del participante, la pantalla dice
     **día 0 todo el día**. Afecta a `GET /api/v1/home`, al panel admin de aprendices y a los 7
     módulos que consumen `ParticipacionProgramaFinder`. **V20 arregló QUÉ se calcula; no arregló
     CUÁNDO se publica.**
  2. **Un participante que fallaba detenía el barrido entero.**
     `RelojProgramaService.avanzarParticipantesActivos` recorría el padrón sin `try/catch` por fila,
     contra lo que exige `.claude/rules/02-tiempo-zonas-y-schedulers.md` §4. Una sola `timezone`
     inválida dejaba a TODO el padrón sin avanzar, cada hora, con el único rastro de un stacktrace en
     el log del scheduler.

- **Por qué pasó CI y una verificación manual.** `VERIFICACION_TRAINING_2026-09-07.md` §1 anotó
  `diaPrograma = 0` el mismo 2026-09-07 y lo dio por correcto — se estaba verificando otra cosa (que
  el "DÍA 1 DE 90" de la app era un `?? 1` inventado por el cliente), y nadie preguntó por qué el
  servidor decía 0 el día en que el programa arrancaba. **La pregunta que faltó: ¿este 0 es el
  correcto, o es el que deja una columna que nadie escribió?**

- **Corrección:**

  | Defecto | Cambio |
  |---|---|
  | 1 | `ParticipacionPrograma.diaProgramaDerivado(fechaInicio, hoy, diasAjuste, activado)` — la MISMA cuenta, ahora también sobre datos sueltos. El adaptador la llama con las columnas y el `Clock`; **la fórmula no se copió**, que es como se desincronizó la columna generada `fecha_graduacion_esperada` (V22) |
  | 1 | La **fase** pasa a derivarse del día devuelto en vez de leerse de `pp.fase`. Es la regla que D-67 ya exige en el dominio; leer la columna dejaba viva justo la incoherencia "día nuevo, fase vieja" |
  | 2 | `sincronizarUno(participacion)` con `try/catch` + `log.error`: la fila que falla se cuenta y se sigue. Como el día es derivado, la corrida siguiente la pone al día sola |

- **Lo que NO cambió, a propósito:** mientras el reloj no arrancó (sin `programa_activado_en`, o con
  `fecha_inicio` en el futuro) sigue mandando la columna, no un 0 derivado. Es la misma distinción
  que hace `sincronizarDiaDelPrograma`: un participante pre-activación conserva el día que un ADMIN
  le haya fijado a mano.

- **Cómo evitar que vuelva a pasar:**
  - **Seis pruebas nuevas, todas rojas contra el código viejo.** Cinco en
    `ConsultarResumenParticipacionPersistenceAdapterTest` (Postgres real): el día derivado sin que el
    barrido haya corrido, la fase siguiendo al día derivado, los `dias_ajuste_programa` descontándose,
    la columna mandando mientras el programa no arrancó, y el panel admin + el barrido por rol
    derivando igual. Una en `RelojProgramaServiceTest`: un participante que explota no detiene el
    barrido. **El reloj de las de Postgres se fija a las 02:00 UTC** — que en Lima todavía es el día
    anterior (regla 03); a las 10:00 UTC el bug se esconde.
  - **Un fixture incoherente tapaba parte de esto.**
    `devuelveInscritoConTodosLosCamposDeUnAprendizConFilaDeParticipante` afirmaba `dia_programa = 20`
    **y** `fase = PHASE_1_REBIRTH` a la vez, cuando el día 20 cae en la fase 2. El INSERT no escribía
    `fase` y la columna se quedaba en su default; el test verificaba que el adaptador devolviera esa
    contradicción. Corregido a `PHASE_2_DEVELOPMENT`.
  - **La lección general, que es la de E-91 una vuelta más arriba:** *derivar en el dominio no sirve
    de nada si el que lee no deriva.* Materializar una cuenta en una columna está bien mientras la
    lectura pueda recalcularla; si la lectura depende de que un cron haya corrido, el valor que ve el
    usuario depende de que la máquina haya estado prendida — y eso no es una regla de negocio.

- **Verificación:** `./mvnw clean verify` en verde — **2576 pruebas unitarias** (eran 2570) y **25 de
  integración**, 0 fallos, 5:33 min. **Los contenedores corrieron en Docker local, NO en
  Testcontainers Cloud**: `~/.config/renaser/testcontainers-cloud.token` está vacío (0 bytes) y el
  agente no estaba levantado (`docs/PRUEBAS_EN_CLOUD.md`).

---

## E-162 — `InconsistentClassPathException` al completar un hábito: DevTools recarga las clases y Modulith se queda con las viejas (2026-09-08)

- **Síntoma:** al subir una evidencia, en el log del backend (no en la respuesta HTTP) aparece:

  ```
  ERROR 21735 --- [renaser-backend] [cTaskExecutor-1] .a.i.SimpleAsyncUncaughtExceptionHandler :
  Unexpected exception occurred invoking async method: void com.renaser.os.notifications.infrastructure.adapter.in.event.HabitoCompletadoNotificationListener.on(com.renaser.os.habits.api.HabitoCompletadoEvent)

  com.tngtech.archunit.base.ArchUnitException$InconsistentClassPathException: Can't resolve method com.renaser.os.notifications.infrastructure.adapter.in.event.HabitoCompletadoNotificationListener.on(com.renaser.os.habits.api.HabitoCompletadoEvent)
  	at com.tngtech.archunit.core.domain.JavaMethod$ReflectMethodSupplier.get(JavaMethod.java:139)
  	...
  	at org.springframework.modulith.observability.support.DefaultObservedModule.isEventListenerInvocation(DefaultObservedModule.java:223)
  	at org.springframework.modulith.observability.support.ModuleEntryInterceptor.invoke(ModuleEntryInterceptor.java:134)
  Caused by: java.lang.NoSuchMethodException: com.renaser.os.notifications.infrastructure.adapter.in.event.HabitoCompletadoNotificationListener.on(com.renaser.os.habits.api.HabitoCompletadoEvent)
  ```

- **Lo primero que hay que descartar, y que acá NO era:** no tiene nada que ver con la base de datos.
  No es una clave primaria vencida ni duplicada, no es el outbox, no es el registro de hábito y no es
  la evidencia. `getDeclaredMethod` es reflexión de Java: el "método que no se puede resolver" es un
  método Java, no una fila. **El método existe** — `javap` sobre
  `target/classes/.../HabitoCompletadoNotificationListener.class` muestra
  `void on(com.renaser.os.habits.api.HabitoCompletadoEvent)`.

- **Causa real — tres piezas que solo se juntan corriendo desde el IDE:**

  1. **DevTools está en el classpath de ejecución** (`spring-boot-devtools-4.1.1.jar`, scope `runtime`)
     y `target/classes` entra como **directorio**. Eso activa el `RestartClassLoader`: al recompilar
     desde IntelliJ, el contexto se reinicia y **cada clase `com.renaser.os.*` pasa a ser un objeto
     `Class` nuevo**. Los jars, en cambio, siguen en el classloader **base**, que no se tira.
  2. **`ApplicationModules` (spring-modulith-core) tiene un `private static final Map ... CACHE`**, y
     ese jar vive en el classloader base → **el modelo de ArchUnit sobrevive al reinicio**, apuntando a
     clases de la generación anterior.
  3. **ArchUnit resuelve con el context classloader y memoiza para siempre**
     (`Suppliers$NonSerializableMemoizingSupplier`, visible en el stack).
     `ReflectMethodSupplier.get()` hace `owner.reflect().getDeclaredMethod(nombre, params.reflect())`,
     y **`getDeclaredMethod` compara los parámetros por identidad de `Class`, no por nombre**.

  Resultado: la clase dueña queda resuelta en la generación N y el parámetro `HabitoCompletadoEvent`
  en la generación M. Los dos se llaman igual, los dos tienen el método — y el lookup falla igual.

- **Evidencia medida en vivo** (PID 21735, arrancado 09:33; `.class` recompilados 10:17; error 10:25):

  ```
  $ jcmd 21735 VM.class_hierarchy com.renaser.os.habits.api.HabitoCompletadoEvent
    com.renaser.os.habits.api.HabitoCompletadoEvent/0x00007fb9903a12b0
    com.renaser.os.habits.api.HabitoCompletadoEvent/0x00007fba0c00af60
    com.renaser.os.habits.api.HabitoCompletadoEvent/0x00007fba70000f40
  ```

  **Tres copias de la misma clase en la misma JVM.** Después de forzar `jcmd GC.run` siguen las tres:
  no son basura, están **fuertemente referenciadas** por esos cachés estáticos. Total retenido:
  **3 `RestartClassLoader` vivos, 8048 clases, 44,8 MB de metaspace** que no se liberan y crecen con
  cada recompilación (emparenta con E-160: el techo de memoria de esta app ya está justo).

- **En qué afecta — y en qué NO:**

  - **La subida de evidencia terminó bien.** `@ApplicationModuleListener` es `AFTER_COMMIT` + `@Async`:
    que el listener se haya ejecutado **es la prueba** de que la transacción de `RegistroService`
    commiteó — registro completado y puntos otorgados incluidos.
  - **La notificación se emitió.** Verificado con `javap -c -l` sobre el jar: en
    `ModuleEntryInterceptor.invoke` la línea **123 es `invocation.proceed()`** y la línea **134 es
    `observation.stop()`, dentro del `finally`**. El cuerpo del listener ya corrió; lo que reventó es
    el cálculo de los tags de la métrica, después.
  - **El outbox no queda colgado.** El stack muestra el orden de la cadena:
    `AsyncExecutionInterceptor` → `ModuleEntryInterceptor` → (`@Transactional` → `CompletionRegisteringAdvisor`
    → target). Todo lo que está por dentro ya devolvió bien, así que la fila de `event_publication` se
    completó (con `completion-mode: DELETE`, se borró). **No hay reentrega ni notificación duplicada.**
  - **Lo único que se pierde** es la métrica/traza de esa entrada de módulo, más un stack alarmante en
    el log.
  - **Es un problema exclusivo de desarrollo.** En producción se corre el jar empaquetado, sin DevTools
    y con un solo classloader: ahí no puede pasar.
  - **Le pasa a los 10 `@ApplicationModuleListener`**, no solo a este (los 5 de `notifications`, los 2
    de `chat`, los de `habits`). Cuál falla depende de qué `JavaClass` alcanzó a memoizarse en cada
    generación, por eso parece intermitente.

- **Solución inmediata:** **Stop + Run** de la aplicación (reinicio completo, no el hot restart). El
  error desaparece hasta la próxima recompilación en caliente.

- **Cómo evitarlo (ninguna aplicada todavía, hay que elegir):**

  1. Mandar los jars que cachean clases de la app al restart classloader, para que su caché muera con
     cada reinicio — mantiene el hot restart:
     `spring.devtools.restart.include.modulith=/spring-modulith-.*\.jar` y
     `spring.devtools.restart.include.archunit=/archunit.*\.jar`.
  2. Apagar la observabilidad de módulos **solo en desarrollo** — no existe propiedad de on/off, hay
     que excluir la autoconfiguración:
     `org.springframework.modulith.observability.autoconfigure.ModuleObservabilityAutoConfiguration`.
     **Ojo:** el `spring.autoconfigure.exclude` de `application.yaml` aplica también a producción, así
     que va en un perfil de desarrollo, no en el archivo base.
  3. `spring.devtools.restart.enabled=false` (se pierde el hot restart).

- **La regla general:** toda librería que guarde objetos `Class` de la aplicación en un `static`
  (el `CACHE` de `ApplicationModules`, los suppliers memoizados de ArchUnit) es **incompatible con el
  hot restart de DevTools** mientras viva en el classloader base. El síntoma siempre es el mismo:
  `NoSuchMethodException` / `ClassCastException` sobre una clase que evidentemente existe y calza.

## E-163 — `No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available` al agregar Web Push (2026-09-08) — **RESUELTO**

- **Dónde:** `notifications/infrastructure/adapter/out/push/WebPushAdapter` y arranque de Spring Boot.
- **Síntoma:** la suite de integración no podía levantar el contexto: `WebPushAdapter` pedía por
  inyección un `com.fasterxml.jackson.databind.ObjectMapper`, pero Spring Boot 4 registra el
  `ObjectMapper` de Jackson 3 (`tools.jackson.databind`) como bean global.
- **Causa real:** el adaptador nuevo dependía de Jackson 2 solo para leer y escribir la suscripción
  JSON de Web Push, y asumía que existía un bean compatible.
- **Solución:** `WebPushAdapter` crea un `ObjectMapper` Jackson 2 propio; no cambia el mapper global
  ni acopla el contexto a una versión concreta de Spring Boot.
- **Cómo evitarlo:** al agregar una librería que use Jackson 2 en Spring Boot 4, no pedir su mapper
  por constructor sin comprobar primero el tipo del bean; usar una instancia local cuando el uso es
  aislado y acotado.

---

## E-164 — Un test de ShedLock fallaba solo en CI: competía con el cron real de su propio contexto (2026-09-08) — **RESUELTO**

- **Síntoma exacto**, en el CI del PR y nunca en local:

  ```
  ProcesarColaValidacionSchedulerLockTest.dosEjecucionesConcurrentesProducenUnaSolaEjecucionEfectiva:86
  [solo UNA de las dos ejecuciones concurrentes debe correr el caso de uso real]
  expected: 1
  but was: 0
  ```

  Uno solo de 2614. `./mvnw clean verify` en la laptop pasaba en verde.

- **Causa real — hay un tercer competidor que el test no sabía que existía.** El test levanta un
  `@SpringBootTest` completo, y `@EnableScheduling` está declarado globalmente (D-P4), así que
  **el cron real del barrido también corre dentro del contexto de prueba**:
  `@Scheduled(cron = "0 * * * * *")`, cada minuto. Y el lock lleva `lockAtLeastFor: PT10S`, o sea
  que ShedLock **retiene la fila diez segundos DESPUÉS de terminar el trabajo** — protección
  legítima contra relojes desfasados entre instancias.

  Si el cron disparaba en los diez segundos previos al test, la fila seguía tomada, los dos hilos
  quedaban afuera y el contador daba **0**. La invocación del cron no se veía porque
  `invocaciones().set(0)`, la primera línea del test, la borraba: quedaba **el efecto sin la
  causa**, y de ahí que el `0` pareciera "no corrió ninguno" en vez de "ya corrió otro".

- **Por qué en CI y no en local.** La ventana es de ~10 s de cada 60. El runner arranca el contexto
  más lento, así que el test cae en otro punto del minuto. No es "CI es raro": es la misma lotería
  con un dado distinto.

- **La corrección que NO se hizo, y por qué.** La sugerencia automática del PR era `Thread.sleep` +
  Awaitility hasta que la aserción pasara. Eso empeora las cosas: pasados los diez segundos el lock
  se libera y el resultado depende de cuándo dispare el cron — el test dejaría de fallar **y
  también de probar lo que dice probar**. Ante un fallo de concurrencia, esperar más solo sirve si
  el trabajo estaba encolado; si el lock está tomado, no se suelta por esperar.

- **Solución aplicada:** borrar la fila de `renaser.shedlock` de ese barrido justo antes de soltar
  el latch, para que los dos hilos compitan sobre un lock limpio. La aserción sigue siendo
  `isEqualTo(1)`. Si el cron dispara *durante* la prueba, lo rechaza el lock que ya tiene el hilo
  ganador, así que el resultado sigue siendo 1.

- **Cómo evitar que vuelva a pasar:** **un test de concurrencia sobre ShedLock tiene que empezar
  limpiando su propia fila.** Mientras `@EnableScheduling` sea global, cualquier `@SpringBootTest`
  que toque un barrido con `@SchedulerLock` compite con el cron de verdad — y con `lockAtLeastFor`
  la interferencia sobrevive al trabajo que la causó. Es la misma familia que E-150
  (`unTokenVencidoYaNoSePuedeConsumir`, que falla solo dentro de la suite completa): **estado
  compartido entre pruebas que se ve como un fallo de lógica.**

- **Verificación:** el test corrió tres veces seguidas en verde. Con un flaky eso prueba poco por sí
  solo, y conviene decirlo: lo que sostiene el arreglo es el mecanismo —borrada la fila, no queda
  otro competidor—, no las tres corridas.


---

## E-165 — Diagnóstico equivocado: se reportó un 500 por EXIF nulo que nunca existió (2026-09-08) — **DESCARTADO**

Se anotó acá un bug que **no existe**. Queda registrado igual, porque el error fue de método y ése
sí se repite.

**Lo que se afirmó.** Que `POST /api/v1/rocks/{id}/evidence` con `{"tipo":"FOTO","timestampExif":null}`
respondía **500**, porque `RocaDiariaService.requireExifDentroDeMargen` hace
`Duration.between(timestampExif, ahora)` sin comprobar null y `Duration.between` hace
`requireNonNull`.

**Por qué es falso.** Ese método **nunca puede recibir null**. El constructor compacto de
`CompletarRocaDiariaCommand` ya lo rechaza antes, y el controller construye el comando, así que el
cliente recibe un **400** limpio:

```
timestampExif es obligatorio para evidencia de tipo FOTO (Ley VI)
```

**Cómo se descubrió.** Escribiendo el test de regresión. Falló con *error*, no con *fallo de
aserción*: la excepción saltó al construir el comando, **fuera** del `assertThatThrownBy`. Ese
detalle —error y no failure— es la señal de que la excepción llega antes de donde uno cree.

**Cómo evitar que vuelva a pasar.**

- **Leer un método aislado no alcanza para afirmar que algo revienta: hay que seguir a quién lo
  llama.** Acá se leyó `requireExifDentroDeMargen`, se vio que faltaba el null-check y se dio por
  cierto el 500 sin mirar el constructor del comando, que está en otro archivo y valida antes.
- **En este repo los `record` de comando validan en su constructor compacto.** Antes de agregar una
  guardia en un servicio, revisar si el comando ya la tiene: `CompletarRocaDiariaCommand` valida
  `contenidoTexto` para TEXTO, `bucket`+`rutaStorage` para lo no textual, `timestampExif` para FOTO
  y la coherencia del GPS. Una guardia repetida ahí abajo es código muerto.
- **El test de regresión hizo su trabajo, y por eso se escribe primero.** La regla de que el test
  tiene que fallar contra el código viejo también sirve para lo contrario: cuando falla contra el
  código *nuevo* por un motivo inesperado, lo que está mal es el diagnóstico.

---

## E-166 — Una meta que baja muestra 100 % de avance desde el primer día (2026-09-09) — **RESUELTO**

**Síntoma.** Con la roca maestra *"Al Día 90 pesaré 75 kg, partiendo de 82 kg"* (`meta = 75`,
`avance = 82`, `unidad = kg`), `GET /api/v1/rocks/master` devuelve:

```json
{"eje":"CUERPO","meta":75,"avance":82,"unidad":"kg","porcentaje":100}
```

Y el Plan muestra **"Avance cuantitativo: 100 % CUMPLIDO"** el día 1, junto a
*"Llevas: 82 kg · Meta: 75 kg"* — dos datos que se contradicen a la vista.

**Causa real.** `MetaCuantitativa.porcentaje()`:

```java
int calculado = avance.multiply(BigDecimal.valueOf(PORCENTAJE_MAXIMO))
        .divide(objetivo, 0, RoundingMode.DOWN)
        .intValue();
return Math.min(calculado, PORCENTAJE_MAXIMO);
```

82 × 100 ÷ 75 = 109 → acotado a 100. **La fórmula asume que más es mejor.** Hay objetivos donde
menos es mejor, y el propio Mapa los ofrece: `peso` y `deuda` están entre los tipos de resultado.

No es un caso de borde: quien quiere bajar de peso o reducir deuda arranca **siempre** con el avance
por encima de la meta, así que ve 100 % desde el primer día y hasta que cruza la meta.

**Detectado** probando el Mapa de punta a punta en el desplegado (`docs/PRUEBA_MAPA_2026-09-09.md`
del frontend), no por un test: no hay ninguno que cubra una meta descendente.

**Solución aplicada (autorizada por el dueño el 2026-09-09).** Columna nueva `linea_base` en
`rocas_maestras` (`V43`), y el porcentaje pasa a medir **el camino recorrido**:

```
|avance − lineaBase| / |objetivo − lineaBase|
```

Así funciona igual en las dos direcciones. "82 → 75 kg" da 0 % el primer día, 50 % a los 78,5 y
100 % al llegar. Y de paso corrige un caso que nadie había mirado: "facturar 15 000 partiendo de
5000" daba **33 % el primer día** por los 5000 que la persona ya facturaba antes de empezar; ahora
da 0 %, porque el avance del programa todavía es cero.

Alejarse de la meta (engordar, endeudarse más) da **0 %, nunca negativo**.

**Qué pasa con las filas anteriores.** `linea_base` es **nullable** y ahí el dominio conserva la
fórmula vieja. No hay forma honesta de inventarles el punto de partida: usar `avance` diría que
nadie avanzó nunca, y usar 0 que todos arrancaron de cero. Se corrigen solas la próxima vez que el
aprendiz edite su objetivo, porque el Mapa ya manda el dato. **Nada se rompe y todo lo nuevo se mide
bien.**

**Lo que NO se cambió, y hay que saberlo.** `MetaCuantitativa` exige `objetivo > 0` (y el CHECK
`roca_maestra_meta_positiva` de V35 también), así que **"reducir la deuda a 0" se sigue rechazando**.
Con línea base esa meta ya tiene sentido, pero levantar la restricción es otra decisión: hay que
cambiar el CHECK y confirmar que 0 es una meta válida para el negocio. Queda planteado.

`rocas_mensuales` **no** recibió la columna: el nivel mensual sigue con la fórmula vieja. No se
amplió el alcance sin pedirlo.

**Cómo se evita que vuelva a pasar.** `MetaCuantitativaTest` cubre ahora los seis casos, y los
cuatro primeros **fallan contra el código viejo**:

- meta descendente en el día 1 (82 → 75 arranca en 0, no en 100),
- meta descendente avanzando (78,5 → 50 %; 75 → 100 %; 70 → 100 %, no más),
- alejarse de la meta (85 con base 82 → 0 %, nunca negativo),
- meta ascendente medida desde su base (5000 → 15 000 arranca en 0, no en 33 %),
- sin línea base, la fórmula vieja intacta (las filas anteriores a V43),
- `lineaBase == meta` rechazado: sin distancia no hay avance que medir, y sería una división por cero.

La lección general: **un porcentaje calculado sobre dos números sin saber hacia dónde mejora el
indicador es una suposición, no un cálculo.** Si un dominio admite metas en las dos direcciones, la
dirección es parte del dato.

## E-167 — Spring Session guardaba autenticación con Java Serialization en Redis (2026-09-09) — **RESUELTO**

- **Dónde:** `shared/infrastructure/session`, `application.yaml`, Spring Session sobre Redis.
- **Síntoma:** *(preventivo)* no había error visible mientras Redis fuera privado, pero las sesiones se
  persistían con el serializador JDK por defecto de Spring Session. Si un atacante pudiera escribir
  bytes en Redis, la lectura de la sesión abriría la superficie conocida de deserialización Java.
- **Causa real:** Spring Session usa `JdkSerializationRedisSerializer` si no existe un bean llamado
  exactamente `springSessionDefaultRedisSerializer`.
- **Solución:** se agregó ese bean con `JacksonJsonRedisSerializer`, `JsonMapper` y
  `SecurityJacksonModules.getModules(...)`. También se configuró un namespace nuevo
  (`renaser:session:v2`) para no intentar leer las sesiones antiguas en formato JDK, y se dejaron
  usuario, contraseña y TLS de Redis parametrizables por entorno.
- **Cómo evitarlo:** toda aplicación que persista `SecurityContext` en Redis debe definir el bean
  con el nombre que Spring Session reconoce, usar los módulos de Spring Security y mantener Redis
  fuera de la red pública; las credenciales y TLS deben coordinarse con el servidor Redis del
  entorno. La prueba `RedisSessionConfigTest` comprueba la serialización JSON y el round-trip del
  `SecurityContext`.

## E-169 — El staff puede inscribirse al programa y despues no puede operarlo (2026-09-09) — **ABIERTO, sin corregir**

- **Donde:** `rocks/.../RocaMaestraService:81`, `RocaSemanalService:182`, `RocaDiariaService:365`,
  `DashboardRocasService:223`, `VerdugoService:119`; `habits/.../EspirituService:350`,
  `RadarService:106`, `AudioterapiaService:116`; `academy/.../RecomendacionService:107`,
  `ClaseDiariaService:85`.
- **Sintoma:** un usuario con rol MENTOR, MENTOR_LEAD, ADMIN o ALCHEMIST activa su seguimiento
  personal con `POST /api/v1/mentor/activate-tracking`, queda con fila en
  `participantes_programa`, y al operar recibe 403 con estos mensajes literales:
  `Solo un aprendiz opera sus propias rocas`, `Espiritu es exclusivo de aprendices`,
  `El Codigo Renaser es exclusivo de aprendices`, `Audioterapia semanal es exclusiva de aprendices`,
  `Solo un aprendiz registra sus propios eventos Verdugo`,
  `Solo un aprendiz recibe recomendaciones de Academia Adaptativa`,
  `La clase diaria no esta disponible para tu cuenta`.
- **Causa real:** los diez guards comparan el **rol** contra `RolParticipante.TRAINEE` en vez de
  preguntar si el actor **tiene una participacion activa**. La forma es siempre la misma:
  `if (progreso.rol() != RolParticipante.TRAINEE) throw new NotAuthorizedException(...)`.
- **Por que es una contradiccion y no una decision:** `Permission.TRACK_PROGRAM_AS_STAFF` existe
  justamente para que el staff curse el programa
  (`ParticipacionProgramaService:67` — *"El seguimiento personal opcional es solo para
  MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST"*), y la especificacion del cliente §2.2 describe un
  *Conmutador de Roles* entre el perfil operativo y el personal. La inscripcion esta construida;
  el uso, no.
- **Estado:** **no se corrige por cuenta propia.** Cambiar `rol != TRAINEE` por *"tiene
  participacion activa"* en diez servicios es una regla de negocio que el dueno del proyecto
  tiene que confirmar (regla 00: no inventar reglas de negocio). Detectado al ejecutar la tarea
  TL-03 del SDD 002 del rol Lider de Mentores.
- **Como evitar que vuelva a pasar:** cuando un permiso dice *"esto es para el rol X"* y un guard
  dice *"esto es solo para el rol Y"*, uno de los dos miente. Un permiso nuevo en
  `shared/domain/Permission` deberia venir siempre con la lista de guards que lo hacen cumplir
  — el javadoc de `TRACK_PROGRAM_AS_STAFF` la tenia, y aun asi nadie contrasto la otra punta.

## E-168 — Un código correcto podía validarse dos veces en paralelo (2026-09-09) — **RESUELTO**

- **Dónde:** `users/infrastructure/adapter/out/redis/AlmacenCodigoNumericoRedis`.
- **Síntoma:** *(preventivo)* dos solicitudes concurrentes que leían el mismo código correcto
  antes del `DEL` podían devolver éxito las dos, aunque el código debía ser de un solo uso.
- **Causa real:** la lectura, la comparación y el borrado eran comandos Redis separados.
- **Solución:** se reemplazaron por un script Lua que compara y consume el código, elimina el
  contador y registra los intentos incorrectos dentro de la misma operación atómica. Se agregó
  una prueba concurrente que exige exactamente un éxito.
- **Cómo evitarlo:** cualquier credencial efímera que Redis deba consumir una sola vez tiene que
  verificarse y eliminarse en una operación atómica (`GETDEL` o script Lua), no con un `GET` seguido
  de un `DEL`.
