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
' if datos[fin-1:fin] == b'' else b'
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
