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
