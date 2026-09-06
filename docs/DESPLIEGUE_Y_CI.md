# Build, CI/CD y configuración remota

Cómo se compila, se prueba, se empaqueta y se configura el backend de Renaser OS. Documento
hermano de [`CLAUDE.md`](../CLAUDE.MD) (por qué cada decisión de arquitectura) y de
[`BITACORA_ERRORES.md`](BITACORA_ERRORES.md) (los errores reales y cómo evitarlos).

> **Estado, 2026-09-05.** Toda la infraestructura de este documento se creó en un mismo cambio y
> **nada de la parte remota está encendida todavía**: no existe la organización de SonarCloud, no
> existe el rol de IAM, no existe el repositorio de ECR y no existe ningún parámetro en Parameter
> Store. Los workflows están escritos completos y se saltean solos, con un aviso, mientras falte
> lo que necesitan. Las secciones §4, §5 y §6 son la lista de lo que hay que crear.
>
> **Qué se verificó de verdad y qué no** (2026-09-05):
>
> | Pieza | Estado |
> |---|---|
> | Reparto surefire/failsafe | **Verificado.** Failsafe corre las 10 clases `*IT.java`: 21 pruebas, 0 fallos |
> | JaCoCo | **Verificado.** Genera `target/site/jacoco/jacoco.xml` (3,5 MB) en `verify` |
> | Empaquetado | **Verificado.** El jar de Boot se construye y `-Djarmode=tools ... extract --layers` produce las 4 capas |
> | Arranque desde las capas extraídas | **Verificado.** Se armó a mano el layout exacto de la imagen y la app arranca desde `application.jar` |
> | Parameter Store en `prod` | **Verificado contra AWS real.** El log dice `Loading property from AWS Parameter Store with name: /renaser/prod/, optional: false` |
> | `docker build` completo | **NO verificado.** Solo se pasó el *linter* de BuildKit (`docker build --check`, sin avisos). La imagen nunca se construyó ni se corrió como contenedor |
> | Los tres workflows | **NO verificados.** Un workflow de GitHub Actions no se puede ejecutar sin empujar el repositorio. Sí se probaron por separado, a mano, el script que cuenta `Tests run:` y la guarda de placeholders de Sonar |
> | Publicación en ECR y OIDC | **NO verificados.** No existe el rol, ni el repositorio de ECR |

---

## 1. Build local

```bash
docker compose up -d          # Postgres 16 + pgvector (:5433) y Redis 7 (:6379)
./mvnw clean test             # solo las pruebas unitarias
./mvnw clean verify           # unitarias + integración + reporte de cobertura   ← el gate real
./mvnw spring-boot:run        # levanta en :8080
```

**`JAVA_HOME` tiene que apuntar al JDK 25:**

```bash
export JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
```

Si está mal, `mvnw` **termina en `exit 0` sin ejecutar una sola prueba** — no falla, no avisa. Es
E-111 de la bitácora, y es el motivo por el que tanto el CI como cualquier revisión manual tienen
que mirar la línea `Tests run:` de la salida y no el código de retorno.

### 1.1 Unitarias vs integración: qué corre en cada fase

| Fase | Plugin | Qué archivos | Cuántos | Necesita Docker |
|---|---|---|---|---|
| `test` | `maven-surefire-plugin` | `**/*Test.java` | 331 archivos | No |
| `integration-test` | `maven-failsafe-plugin` | `**/*IT.java` | 10 archivos | **Sí** |

La convención ya existía en el repo; **no se renombró ningún archivo**. Lo que se agregó es
failsafe, y con él **los 10 archivos `*IT.java` empezaron a ejecutarse en el build**: antes no los
corría nadie. Surefire nunca los incluyó (no encajan en sus patrones por defecto: `Test*`,
`*Test`, `*Tests`, `*TestCase`) y failsafe no estaba declarado. Se ejecutaban solo a mano desde el
IDE.

Los 10 son `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` contra Postgres
(`pgvector/pgvector:pg16`) y Redis (`redis:7-alpine`) reales.

> **Consecuencia práctica:** `./mvnw clean test` ya **no** es el gate completo. Deja fuera esas 10
> clases y no genera el reporte de cobertura. El gate es `./mvnw clean verify`.

### 1.2 No correr dos builds a la vez sobre el mismo checkout

`target/` es un recurso compartido de todo el repositorio. Dos builds simultáneos se pisan y el
síntoma es engañoso (`Truncated class file`, `No classes found in packages [com.renaser.os]`,
`NoClassDefFoundError` nombrando una clase que sí existe). Está documentado en E-104, con las
cuatro formas en que se manifiesta. Si hay que repartir trabajo entre varias sesiones, cada una en
su propio `git worktree`.

---

## 2. Cobertura (JaCoCo)

El agente de JaCoCo se engancha en `initialize` y escribe la opción `-javaagent` en la propiedad
`argLine`, que es la misma que leen **surefire y failsafe**. Por eso las dos fases suman al mismo
`target/jacoco.exec` y un único reporte cubre todo.

| Artefacto | Ruta | Para qué |
|---|---|---|
| Datos crudos | `target/jacoco.exec` | Regenerar el reporte sin volver a correr la suite |
| Reporte HTML | `target/site/jacoco/index.html` | Mirarlo a mano |
| Reporte XML | `target/site/jacoco/jacoco.xml` | **Lo que consume SonarCloud** |

Dos cosas que rompen la cobertura en silencio, sin ningún error visible:

1. **Configurar `<argLine>` a mano en surefire sin incluir `@{argLine}`.** Pisa el del agente y la
   cobertura queda en 0%.
2. **Correr `./mvnw clean test`** en vez de `verify`. El goal `report` está atado a `verify`, así
   que con `test` el XML directamente no se genera y Sonar reporta 0% sin quejarse.

La versión de JaCoCo (`0.8.15`) **no es negociable hacia abajo**: la 0.8.14 fue la primera con
soporte *oficial* de Java 25 (la 0.8.13 lo tenía como experimental). Con una anterior, el agente no
entiende los class files de versión 69 y rompe la instrumentación.

---

## 3. Integración continua (`.github/workflows/ci.yml`)

Corre en cada push a cualquier rama y en cada PR.

- Runner **`ubuntu-latest`**: es el único que trae un demonio de Docker listo, y sin Docker las 10
  pruebas de integración no pueden levantar Testcontainers. Un runner de Windows o macOS no sirve.
- JDK 25 Temurin, con caché de `~/.m2` por hash del `pom.xml`.
- `./mvnw -B -ntp clean verify`.
- Publica `target/site/jacoco/` y `target/jacoco.exec` como artefacto (14 días), y los informes de
  surefire/failsafe **solo si algo falló**.

### 3.1 El paso que verifica que las pruebas realmente corrieron

Existe por **E-111**: `mvnw` puede terminar en `exit 0` sin ejecutar nada. El paso lee las líneas
de *resumen* de surefire y failsafe —las que dicen `Tests run: N, Failures: ...` **sin** el sufijo
`- in <clase>`, que son las de cada clase—, las suma y falla si:

- no aparece **ninguna** línea `Tests run:` (el caso de E-111);
- el total queda por debajo de `MINIMO_PRUEBAS` (variable del propio workflow);
- hay algún fallo o error.

`MINIMO_PRUEBAS` **no es una meta de cobertura ni un candado contra borrar pruebas**: es un canario
contra el cero. Está fijado por debajo del total real a propósito, para que agrupar o eliminar
pruebas legítimamente no rompa el CI, pero muy por encima de cero para que "no corrió nada" no pase
desapercibido. Si la suite crece mucho, subirlo es opcional; bajarlo hasta cerca de cero anula el
punto del paso.

### 3.2 Lo primero a mirar si el CI se pone rojo de noche

**Hay un test que falla todos los días entre las 19:00 y la medianoche de Lima, y no tiene nada que
ver con el PR:** `ControlCuotaRedisAdapterTest` arma la clave de Redis con `ZoneOffset.UTC` mientras
el adaptador de producción la arma con `America/Lima`. En esa franja las dos fechas ya no coinciden
y 3 de sus 5 pruebas fallan. Está documentado con la evidencia completa en **E-125**, sigue
**abierto**, y el arreglo es una línea en el test.

Importa especialmente acá porque **los runners de GitHub corren en UTC**, así que el CI va a ver esa
ventana igual. Si un build nocturno falla y el único error está en esa clase, no es el cambio: es
E-125.

---

## 4. SonarCloud (`.github/workflows/sonarcloud.yml`)

**Nada de esto existe todavía.** Mientras falte el secret `SONAR_TOKEN`, el workflow imprime un
aviso y termina en verde: una pieza que nadie configuró no puede bloquear los PR de todo el equipo.

### 4.1 Qué tiene que crear el dueño

1. Entrar a <https://sonarcloud.io> e iniciar sesión con la cuenta de GitHub.
2. **Crear la organización** y vincularla a la organización de GitHub donde vive este repositorio.
3. **Importar el repositorio** como proyecto nuevo. SonarCloud asigna ahí una *Project Key*.
4. En el proyecto, elegir **"With GitHub Actions"** como método de análisis. La pantalla muestra un
   token; ese es el valor de `SONAR_TOKEN`.
5. En GitHub: **Settings → Secrets and variables → Actions → New repository secret**, nombre
   `SONAR_TOKEN`, valor el del paso anterior.
6. En **New Code** del proyecto, elegir *Previous version* o *Number of days* según prefiera.

### 4.2 Qué hay que cambiar en el repositorio

En `pom.xml`, dos propiedades tienen **placeholders a propósito**:

```xml
<sonar.organization>TODO-organizacion-sonarcloud-no-creada</sonar.organization>
<sonar.projectKey>TODO-project-key-no-asignada</sonar.projectKey>
```

No se pueden adivinar: la *Organization Key* y la *Project Key* las asigna SonarCloud al importar
el repositorio, y escribir un valor inventado subiría métricas a un proyecto ajeno o fallaría con
un error de API difícil de leer. Por eso llevan el prefijo `TODO-`: el workflow **comprueba ese
prefijo** y, si hay token pero los placeholders siguen puestos, falla diciendo exactamente qué
falta en vez de dejar que Sonar responda "project not found".

La cobertura la toma de `sonar.coverage.jacoco.xmlReportPaths`, ya apuntada a
`target/site/jacoco/jacoco.xml`. El workflow corre `verify sonar:sonar` en un solo comando porque
Sonar no ejecuta pruebas: solo lee ese XML.

---

## 5. Entrega continua (`.github/workflows/cd.yml`)

Corre en push a `master`. **Construye y publica la imagen; no despliega.**

Como el resto, se saltea con un aviso mientras falten las variables de repositorio
`AWS_ROLE_ARN`, `AWS_REGION` y `ECR_REPOSITORY`.

### 5.1 Autenticación: OIDC, no claves de acceso

No hay `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY` en ningún secret. GitHub le entrega al job un
token de identidad de corta duración y AWS lo cambia por credenciales temporales. Una clave de
acceso guardada como secret, en cambio, es permanente: si se filtra sirve hasta que alguien la
rote, y nadie se entera de que se filtró.

**Lo que hay que crear en AWS:**

**a) El proveedor de identidad OIDC** (una sola vez por cuenta). IAM → Identity providers → Add
provider → OpenID Connect:

- Provider URL: `https://token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`

**b) El rol que asume GitHub Actions**, con esta política de confianza. Reemplazar
`<ID_DE_CUENTA>`, `<ORG>` y `<REPO>`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<ID_DE_CUENTA>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:<ORG>/<REPO>:ref:refs/heads/master"
        }
      }
    }
  ]
}
```

> **La condición `sub` es la parte que importa.** Sin ella, *cualquier* workflow de *cualquier*
> repositorio de GitHub puede asumir el rol. Acotarla a `ref:refs/heads/master` limita el rol a
> este repositorio y a esa rama. Si más adelante hace falta desplegar desde tags, se agrega otra
> línea `repo:<ORG>/<REPO>:ref:refs/tags/*`, no se afloja el patrón a `repo:<ORG>/<REPO>:*`.

**c) La política de permisos del rol** — lo mínimo para publicar en ECR:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "TokenDeAutenticacionDeEcr",
      "Effect": "Allow",
      "Action": "ecr:GetAuthorizationToken",
      "Resource": "*"
    },
    {
      "Sid": "PublicarEnElRepositorioDelBackend",
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:PutImage",
        "ecr:BatchGetImage"
      ],
      "Resource": "arn:aws:ecr:<REGION>:<ID_DE_CUENTA>:repository/<NOMBRE_DEL_REPO_ECR>"
    }
  ]
}
```

`ecr:GetAuthorizationToken` va sobre `*` porque es una acción de cuenta, no de recurso: no acepta
un ARN de repositorio.

**d) El repositorio de ECR** (`aws ecr create-repository --repository-name renaser-backend`).

**e) Las variables en GitHub** — Settings → Secrets and variables → Actions → pestaña
**Variables** (no Secrets: ninguna de estas es una credencial, ese es justamente el punto de OIDC):

| Variable | Ejemplo | Obligatoria |
|---|---|---|
| `AWS_ROLE_ARN` | `arn:aws:iam::123456789012:role/gha-renaser-backend` | Sí |
| `AWS_REGION` | `us-east-1` | Sí |
| `ECR_REPOSITORY` | `renaser-backend` | Sí |
| `IMAGEN_PLATAFORMAS` | `linux/arm64` | No (default `linux/amd64`) |

### 5.2 Etiquetas de la imagen

Cada publicación deja dos: `:<sha-del-commit>` y `:latest`. La del SHA es la que sirve para saber
qué está corriendo y para volver a una versión anterior sin reconstruir nada; `latest` es solo
"la última", y nunca alcanza para responder qué versión está en producción.

### 5.3 El despliegue está pendiente y por qué no se inventó

**El destino no está decidido.** El workflow tiene un job final que solo imprime un aviso. No se
agregó un `aws ecs update-service` contra un cluster que nadie creó: fallaría en cada push a master
y no ayudaría a tomar la decisión.

Las tres opciones y lo que cada una obliga a construir:

| Opción | Qué hay que crear | A favor | En contra |
|---|---|---|---|
| **ECS Fargate** | Cluster, task definition, service, ALB, target group, security groups, rol de tarea | Control fino, escalado horizontal, es lo que espera §5.2.1 de `CLAUDE.md` (varias instancias) | La más infraestructura para levantar |
| **App Runner** | Un servicio apuntando a la imagen de ECR | Lo más rápido de poner en pie; HTTPS y escalado incluidos | Menos control de red; el escalado a cero castiga el arranque de una JVM |
| **EC2 + Docker** | Instancia, `user-data` con `docker pull`, Elastic IP o ALB | Lo más barato y lo más simple de entender | Los despliegues y el ciclo de vida quedan a mano |

Lo que ya está resuelto y no cambia con la elección: la imagen es multi-arquitectura, corre como
usuario sin privilegios, y toma su configuración de Parameter Store (§6). El rol **de ejecución**
(el que usa la aplicación, distinto del rol de GitHub Actions) necesita los permisos de §6.3 y los
de S3 que documenta `AlmacenamientoS3Config`.

**Un detalle a mirar al cablear el health check, cualquiera sea el destino:** las tres opciones
necesitan una URL que responda 200 para saber si la instancia está sana, y la candidata natural es
`/actuator/health` (`spring-boot-starter-actuator` ya está en el `pom.xml`). Hoy responde sin
autenticación **por omisión**, no por decisión: `SecurityConfig` todavía no tiene
`anyRequest().authenticated()` —está anotado como pendiente en el propio archivo— y lo que no
coincide con ningún `requestMatchers` queda permitido. El día que se cierre esa regla, el health
check empieza a recibir 401 y la plataforma va a dar de baja instancias sanas. Al agregar
`anyRequest().authenticated()` hay que dejar `/actuator/health` explícitamente permitido en el
mismo cambio.

---

## 6. Configuración remota: AWS Systems Manager Parameter Store

### 6.1 Cómo está cableado

| Perfil | De dónde sale la configuración |
|---|---|
| local / test | `application.yaml` con sus defaults, más `optional:file:.env` si existe. **Sin hablar con AWS.** |
| `prod` | `application-prod.yaml` agrega `spring.config.import: aws-parameterstore:/renaser/prod/` |

Que el arranque local **no necesite credenciales de AWS** es una propiedad valiosa del repositorio
(todos los adaptadores externos tienen `NoOp`) y se conservó. Para lograrlo hizo falta una cosa que
no es obvia:

> **`spring.cloud.aws.parameterstore.enabled` tiene que estar *presente* y en `false`; no alcanza
> con no ponerla.** `ParameterStoreAutoConfiguration` es
> `@ConditionalOnProperty(..., matchIfMissing = true)` y declara un bean `SsmClient`, cuya
> construcción resuelve la región con `DefaultAwsRegionProviderChain`. Sin región configurada eso
> lanza `SdkClientException: Unable to load region from any of the providers in the chain` durante
> el arranque — o sea, con la propiedad ausente se cae el contexto en local **y en toda la suite de
> pruebas**.

Por eso el bloque está **espejado en dos archivos**: `src/main/resources/application.yaml` y
`src/test/resources/application.yaml`. El segundo *reemplaza* al primero en el classpath de test
(mismo nombre, gana el de test) en vez de complementarlo, así que una propiedad que solo esté en
`main` no llega a las pruebas. Es el mismo motivo por el que ya estaban espejados los hilos
virtuales (C-14) y `open-in-view` (C-18).

El `import` de producción **no lleva `optional:`, a propósito**. Si Parameter Store no responde
—rol sin permisos, prefijo mal escrito, red cortada— el arranque tiene que fallar ahí. Con
`optional:` la aplicación levantaría con los defaults de `application.yaml`: apuntando a
`localhost:5433`, con `EMAIL_PROVEEDOR=noop` y `STORAGE_PROVEEDOR=noop`. Un backend "sano" para el
health check que no manda un solo correo y devuelve `about:blank#pendiente-s3/...` en cada archivo
es mucho peor que uno que no arranca.

### 6.2 Cómo se nombran los parámetros

El prefijo `/renaser/prod/` se lee entero con `GetParametersByPath` y se le quita al nombre de cada
parámetro. Así, `/renaser/prod/DB_URL` termina siendo la propiedad `DB_URL`, que es exactamente lo
que busca el `${DB_URL:...}` de `application.yaml`. **Por eso los parámetros se llaman igual que
las variables del `.env`** y no `spring.datasource.url`: es la misma convención que ya usa el
equipo, sin una traducción extra que recordar.

Crear uno, desde la consola o así:

```bash
aws ssm put-parameter --name "/renaser/prod/DB_PASSWORD" --type SecureString --value "..."
aws ssm put-parameter --name "/renaser/prod/REDIS_HOST"  --type String       --value "..."
```

### 6.3 Permisos que necesita el rol de ejecución de la aplicación

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["ssm:GetParametersByPath", "ssm:GetParameters", "ssm:GetParameter"],
      "Resource": [
        "arn:aws:ssm:<REGION>:<ID_DE_CUENTA>:parameter/renaser/prod",
        "arn:aws:ssm:<REGION>:<ID_DE_CUENTA>:parameter/renaser/prod/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": "kms:Decrypt",
      "Resource": "arn:aws:kms:<REGION>:<ID_DE_CUENTA>:key/<ID_DE_LA_CLAVE>"
    }
  ]
}
```

El permiso de KMS hace falta **solo para los parámetros `SecureString`**. Con la clave por defecto
(`alias/aws/ssm`) hay que poner el ARN de esa clave; si se crea una propia, el de la propia.

### 6.4 Los parámetros a crear

Sacados uno por uno de `src/main/resources/application.yaml`. `SecureString` = es una credencial y
va cifrado.

**Obligatorios en producción — sin estos el sistema no hace lo que tiene que hacer:**

| Parámetro | Tipo | Qué es |
|---|---|---|
| `DB_URL` | String | JDBC de Postgres. El default apunta a `localhost:5433` |
| `DB_USERNAME` | String | Usuario de Postgres |
| `DB_PASSWORD` | **SecureString** | Contraseña de Postgres |
| `REDIS_HOST` | String | Host de Redis (sesiones, cuotas, pub/sub de chat) |
| `REDIS_PORT` | String | Puerto de Redis |
| `CORS_ORIGENES` | String | Orígenes permitidos, separados por coma. El default son tres `localhost` |
| `RESET_PASSWORD_URL` | String | **Hoy el default es `https://TODO-frontend-no-definido.renaser.dev/...`** — el dominio del frontend no está decidido |
| `ACTIVATE_ACCOUNT_URL` | String | Ídem: default con `TODO-` adentro |
| `EMAIL_REMITENTE` | String | Ídem: default `no-reply@TODO-dominio-no-definido.renaser.dev` |

**Para que funcione el correo** (hoy `EMAIL_PROVEEDOR=noop`, no se manda nada):

| Parámetro | Tipo | Qué es |
|---|---|---|
| `EMAIL_PROVEEDOR` | String | `noop` o `smtp` |
| `SMTP_HOST` | String | Sin valor, Spring no crea el `JavaMailSender` |
| `SMTP_PORT` | String | |
| `SMTP_USERNAME` | String | |
| `SMTP_PASSWORD` | **SecureString** | |

**Para que funcionen los archivos** (hoy `STORAGE_PROVEEDOR=noop`, toda URL sale como
`about:blank#pendiente-s3/...`):

| Parámetro | Tipo | Qué es |
|---|---|---|
| `STORAGE_PROVEEDOR` | String | `noop` o `s3` |
| `AWS_S3_BUCKET` | String | Default `s3-renaser90dias` |
| `AWS_REGION` | String | Default `us-east-1` |

Las credenciales de S3 **no van acá**: `AlmacenamientoS3Config` usa `DefaultCredentialsProvider`,
que las toma del rol de la tarea o de la instancia. No hay que crear un `AWS_SECRET_ACCESS_KEY`.

**Para el login social** (los adaptadores fallan al *invocarse* sin configurar, no al arrancar):

| Parámetro | Tipo |
|---|---|
| `GOOGLE_OAUTH_CLIENT_ID` | String |
| `GOOGLE_OAUTH_CLIENT_SECRET` | **SecureString** |
| `APPLE_CLIENT_ID`, `APPLE_TEAM_ID`, `APPLE_KEY_ID` | String |
| `APPLE_AUTH_KEY_PATH` | String (ruta al `.p8` dentro del contenedor) |
| `FACEBOOK_APP_ID` | String |
| `FACEBOOK_APP_SECRET` | **SecureString** |

**Para encender la IA** (hoy todos los adaptadores son `NoOp` y nunca se llamó a un modelo):

| Parámetro | Tipo | Ojo |
|---|---|---|
| `GOOGLE_GENAI_API_KEY` | **SecureString** | Activar facturación antes de usarla con datos reales: el nivel gratuito pide no enviar información personal, y el Espejo de la Sombra manda diarios íntimos |
| `IA_PROVEEDOR` | String | `noop` o `google`. Ponerlo en `google` antes de que existan los adaptadores reales **deja puertos sin implementación y el arranque falla** |
| `IA_BUSQUEDA_WEB` | String | Necesita además un `RENASIA_CHAT_MODEL` que soporte búsqueda (sin `-lite`): ver D-100 |
| `RENASIA_CHAT_MODEL`, `RENASIA_EMBEDDING_MODEL`, `RENASIA_EMBEDDING_DIMENSIONS`, `RENASIA_EMBEDDING_TASK_TYPE` | String | Los vectores de dos modelos distintos no son comparables: cambiar el de embeddings obliga a reindexar todo |

**Opcionales — solo si hay que apartarse del default:** `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`,
`DB_POOL_CONNECTION_TIMEOUT_MS`, `ASYNC_IA_CONCURRENCY_LIMIT`, `RENASIA_LIMITE_DIARIO`,
`ACCOUNT_DELETION_GRACE_DAYS`, `ONBOARDING_V90_HABILITADO`, `HABITS_AVISO_ANTELACION_INICIO`,
`HABITS_AVISO_ANTELACION_VENCIMIENTO` (estos dos últimos son **provisorios**, a la espera de que el
dueño confirme los números), `AWS_PARAMETER_STORE_ENABLED`.

> Los valores de negocio de esta tabla **no se inventan**: los que hoy tienen `TODO-` en el default
> están esperando una decisión del dueño (dominio del frontend, remitente de correo), no un valor
> razonable puesto por quien despliegue.

---

## 7. La imagen de Docker

Tres etapas: compilar con JDK 25, extraer el jar en capas, y armar la imagen final sobre un JRE.

- **Base final `eclipse-temurin:25-jre-noble`.** Publica `linux/arm64/v8` además de `amd64`, así que
  sirve para Graviton. Se descartó `alpine` —que también tiene arm64— porque desde el entorno de
  desarrollo no se puede levantar el contenedor para probarlo: Lettuce arrastra Netty y las
  diferencias de resolución DNS entre glibc y musl son una fuente conocida de fallos que solo
  aparecen en ejecución. Medido contra el manifiesto de Docker Hub (capas comprimidas, arm64):
  `25-jre-noble` son **98 MB** y `25-jre-alpine` **71 MB** — 27 MB no pagan una apuesta a ciegas.
  Cambiar a alpine es una línea, y si alguien lo prueba y anda, conviene dejarlo registrado.
- **Corre como usuario sin privilegios** (`renaser`, uid 1001), no como root.
- **Capas.** Un cambio de código solo invalida la última: el push sube unos pocos MB en vez de los
  ~90 del jar entero.

> **El comando de extracción es `-Djarmode=tools ... extract --layers`, no `layertools`.**
> `-Djarmode=layertools` es lo que aparece en casi todos los tutoriales y en cualquier guía escrita
> para Spring Boot 3.x, pero **fue eliminado en Spring Boot 4.1** (deprecado en 3.3, con aviso en
> 4.0, ya no existe en la versión de este repo). Si alguien copia un Dockerfile de internet, este
> es el punto donde va a romperse. Comprobado contra el jar real de este proyecto:
>
> ```
> $ java -Djarmode=layertools -jar renaser-backend-0.0.1-SNAPSHOT.jar list
> Error: Unsupported jarmode 'layertools'
>
> $ java -Djarmode=tools -jar renaser-backend-0.0.1-SNAPSHOT.jar list-layers
> dependencies
> spring-boot-loader
> snapshot-dependencies
> application
> ```

**Un detalle del `Dockerfile` que parece cosmético y no lo es:** la etapa de extracción hace
`COPY --from=build /build/target/*.jar application.jar` **antes** de extraer. El nombre importa: la
extracción conserva el nombre del jar de entrada, así que si se extrajera con el nombre original el
resultado sería `extracted/application/renaser-backend-0.0.1-SNAPSHOT.jar` y el
`ENTRYPOINT ["java", "-jar", "application.jar"]` no encontraría nada. Renombrar primero es lo que
hace que el `ENTRYPOINT` sea estable entre versiones.

`JAVA_TOOL_OPTIONS` lleva `-XX:MaxRAMPercentage=75.0` porque una JVM en contenedor toma por defecto
~25% de la memoria del cgroup. Se pasa por variable de entorno y no por el `ENTRYPOINT` para que
`java` siga siendo el PID 1 en forma *exec*: así recibe el `SIGTERM` del orquestador y Spring apaga
ordenado, en vez de que un `sh -c` se coma la señal.

El `.dockerignore` deja fuera `.env` y `.run/`, que hoy contienen credenciales reales en texto
plano. No es prolijidad: cualquier `COPY . .` futuro las metería en una capa de la imagen, de donde
se leen con `docker history` aunque el archivo se borre después.

---

## 8. Versiones elegidas, y por qué

| Pieza | Versión | Por qué esa |
|---|---|---|
| **Spring Cloud AWS** | **4.1.1** (GA, 2026-08-28) | Es la línea de Spring Boot 4.1. Verificado contra el POM publicado, no contra la tabla del README de awspring: `spring-cloud-aws-dependencies:4.1.1` hereda de `spring-cloud-dependencies-parent:5.0.3` (tren 2025.1.x) y alinea Spring Modulith 2.1.1, la misma línea de este repo. La tabla del README todavía resume la línea 4.x como "Spring Boot 4.0.x" sin desglosar parches |
| **AWS SDK v2** | **2.54.3** (subido desde 2.42.41) | Es la versión a la que alinea Spring Cloud AWS 4.1.1. Nuestro BOM se importa primero y en Maven gana la primera declaración, así que el número de acá manda sobre *todos* los `software.amazon.awssdk:*`, incluido el `ssm` de Parameter Store. Dejarlo en 2.42.41 haría correr a spring-cloud-aws (compilado contra 2.54.3) sobre un `auth`/`regions` 12 versiones menores más viejo, y un `NoSuchMethodError` así solo aparece en producción. Con 2.54.3 el riesgo se traslada a nuestro adaptador de S3, que sí lo cubre la suite completa |
| **JaCoCo** | **0.8.15** | La 0.8.14 fue la primera con soporte oficial de Java 25 |
| **sonar-maven-plugin** | **5.7.0.6970** | Última publicada. Se fija en `pluginManagement` para que `sonar:sonar` no resuelva "la que haya" y cambie entre máquinas |
| **Acciones de GitHub** | `checkout@v7`, `setup-java@v6`, `upload-artifact@v7`, `cache@v6`, `configure-aws-credentials@v6`, `amazon-ecr-login@v2`, `build-push-action@v7`, `setup-buildx-action@v4`, `setup-qemu-action@v4` | Majors vigentes al 2026-09-05, consultados contra la API de releases de cada repositorio |

**Lo que no se tocó:** el bloque `annotationProcessorPaths` del `maven-compiler-plugin`, con el
orden `lombok → lombok-mapstruct-binding → mapstruct-processor` repetido en `default-compile` y
`default-testCompile`, y `maven.compiler.proc=full`. Si ese orden se rompe, MapStruct genera
mappers vacíos **sin fallar el build** y el síntoma aparece en ejecución como campos en `null`
(`CLAUDE.md` §5.4.5).

---

## 9. Resumen de lo que falta decidir o crear

| # | Qué | Quién |
|---|---|---|
| 1 | Organización y proyecto en SonarCloud + secret `SONAR_TOKEN`, y reemplazar los dos `TODO-` del `pom.xml` | Dueño |
| 2 | Proveedor OIDC, rol de IAM, repositorio de ECR, y las tres variables de repositorio en GitHub | Dueño |
| 3 | **Destino de despliegue**: ECS Fargate / App Runner / EC2 (§5.3) | Decisión de producto e infraestructura |
| 4 | Dónde se hostea el Postgres propio (RDS / Cloud SQL / VPS) — abierto desde `CLAUDE.md` §11 | Ídem |
| 5 | Dominio real del frontend, para `RESET_PASSWORD_URL` / `ACTIVATE_ACCOUNT_URL` / `EMAIL_REMITENTE` | Dueño |
| 6 | Los ~42 parámetros de `/renaser/prod/` (§6.4) | Dueño, al desplegar |
| 7 | Logs estructurados en JSON para `prod` (`CLAUDE.md` §5.4.9). `application-prod.yaml` se creó solo con lo de Parameter Store; esa regla sigue sin implementarse | Pendiente, fuera del alcance de este cambio |
