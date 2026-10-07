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

> **Actualización 2026-09-06 — la infraestructura de AWS ya existe, y el despliegue también.**
> El párrafo de arriba quedó viejo el mismo día en varios puntos, y se corrige acá en vez de
> borrarlo, para que se vea qué cambió:
>
> - **Sí existen** el proveedor OIDC, el rol `renaser-github-actions`, el repositorio de ECR
>   `renaser-backend`, los parámetros de `/renaser/prod/` y una instancia EC2 sirviendo el backend
>   detrás de CloudFront. Lo que **no** existe todavía es SonarCloud.
> - **El destino de despliegue ya está decidido: la instancia EC2, por SSM** (§5.3, reescrita).
>   El job `desplegar` del `cd.yml` dejó de ser un aviso y ahora despliega de verdad.
> - **Los workflows siguen sin ejecutarse nunca.** No por falta de infraestructura, sino porque
>   **el repositorio de GitHub no tiene ni una sola variable de Actions cargada**
>   (`gh api .../actions/variables` devuelve `total_count: 0`). Sin `AWS_ROLE_ARN`, `AWS_REGION`,
>   `ECR_REPOSITORY` y `EC2_INSTANCE_ID`, el `cd.yml` se saltea entero y termina en verde en unos
>   15 segundos — que es exactamente lo que vienen haciendo las últimas corridas. **Las imágenes
>   que hay en ECR se subieron a mano, no las publicó el workflow.** Crear esas cuatro variables
>   (§5.1 e) es el único paso que falta para que la cadena completa funcione sola.
> - **Corregido 2026-09-27 (E-357).** El punto anterior ya no vale: las cuatro variables existen
>   (`gh variable list`: `AWS_REGION`, `AWS_ROLE_ARN`, `EC2_INSTANCE_ID`, `ECR_REPOSITORY`) y el `cd.yml`
>   despliega solo con cada push a `master` (corridas `36258213585` del 26/09 y `36334605150` del 27/09, verdes).
> - **Qué se verificó del despliegue, el 2026-09-06:** la secuencia entera se corrió a mano con la
>   CLI, paso por paso, extrayendo los `run:` del propio `cd.yml` para no probar una copia. Bajó la
>   imagen, reemplazó el contenedor, y `/actuator/health` respondió `UP` **a los 43 s**. También se
>   probaron los dos caminos de fallo (la aplicación no levanta, y el contenedor se muere durante
>   el arranque) y el rol de IAM con `iam simulate-principal-policy`. **Lo que sigue sin
>   verificarse es el workflow en sí y el intercambio OIDC**, por el mismo motivo de siempre: no se
>   puede correr un workflow sin empujar el repositorio, y el rol solo se puede asumir desde
>   GitHub. El detalle está en `BITACORA_ERRORES.md` **E-139** (el workflow verde que no hacía
>   nada) y **E-140** (las tres trampas de `ssm send-command`).

---

## 1. Build local

```bash
docker compose up -d          # Postgres 16 + pgvector (:5433) y Redis 7 (:6379)
./mvnw clean test             # solo las pruebas unitarias
./mvnw clean verify           # unitarias + integración + reporte de cobertura   ← el gate real
./mvnw spring-boot:run        # levanta en :8080
```

El `redis` del compose admite autenticación sin otra configuración: si `REDIS_PASSWORD` está en
`.env`, el contenedor arranca con `requirepass` y Spring usa el mismo valor. Si queda vacío, se
mantiene el modo local sin contraseña. En producción no se debe publicar el puerto de Redis; el
servidor y la aplicación tienen que compartir la misma credencial y, cuando el proveedor lo
requiera, TLS (`REDIS_SSL_ENABLED=true`).

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
| `test` | `maven-surefire-plugin` | `**/*Test.java` | 603 archivos (4889 pruebas, 2026-09-27) | No |
| `integration-test` | `maven-failsafe-plugin` | `**/*IT.java` | 35 archivos (130 pruebas, 2026-09-27) | **Sí** |

> **Corregido 2026-09-27.** La tabla decía «331 archivos» y «10 archivos»: eran los del día en que se sumó failsafe.
> Las menciones a «los 10» que siguen en esta sección cuentan ese momento.

La convención ya existía en el repo; **no se renombró ningún archivo**. Lo que se agregó es
failsafe, y con él **los 10 archivos `*IT.java` empezaron a ejecutarse en el build**: antes no los
corría nadie. Surefire nunca los incluyó (no encajan en sus patrones por defecto: `Test*`,
`*Test`, `*Tests`, `*TestCase`) y failsafe no estaba declarado. Se ejecutaban solo a mano desde el
IDE.

Los 10 son `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` contra Postgres
(`pgvector/pgvector:pg16`) y Redis (`redis:7-alpine`) reales.

> **Consecuencia práctica:** `./mvnw clean test` ya **no** es el gate completo. Deja fuera las clases
> `*IT` (35 al 2026-09-27; decía «esas 10», corregido 2026-09-27) y no genera el reporte de cobertura. El gate es
> `./mvnw clean verify`.

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

- Runner **`ubuntu-latest`**: es el único que trae un demonio de Docker listo, y sin Docker las
  pruebas de integración no pueden levantar Testcontainers. Un runner de Windows o macOS no sirve.
  *Corregido 2026-09-27: decía «las 10 pruebas de integración»; hoy son 130 en 35 clases.*
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

> **Corregido 2026-09-05.** Esta sección decía que `ControlCuotaRedisAdapterTest` fallaba todas las
> noches y que E-126 seguía **abierto**. **Ya no**: E-126 está cerrado — el helper del test dejó de
> recalcular la fecha en UTC y ahora la deriva por el puerto `Clock` en `America/Lima`, igual que el
> adaptador. Verificado dentro de la franja que rompía (19:39–19:45 de Lima): el test viejo daba
> `Tests run: 5, Failures: 3` y el nuevo `Failures: 0`. Se deja el aviso reescrito abajo porque la
> *familia* de fallos sigue siendo real aunque este caso concreto esté resuelto.

**Los runners de GitHub corren en UTC.** Eso significa que entre las **00:00 y las 05:00 UTC** un
build ve una fecha de calendario distinta a la que ve el padrón, que vive en `America/Lima` (UTC−5):
para el runner ya es mañana mientras para el aprendiz todavía es hoy.

Si un build nocturno se pone rojo y los fallos son todos de una misma clase que compara fechas,
la primera hipótesis **no es el cambio del PR**: es que ese test reconstruye a mano una fecha que
producción deriva en la zona del padrón. Es la familia de **E-91, E-105, E-106 y E-126**. La señal
más rápida de reconocerla: el mismo commit pasa de día y falla de noche, sin ningún cambio de código
entre las dos corridas.

El arreglo es siempre el mismo — que el test derive el valor por el **mismo camino que producción**
(`clock.now().atZone(zona).toLocalDate()`), en vez de recalcularlo con `LocalDate.now(...)`. Barrido
del 2026-09-05: `ControlCuotaRedisAdapterTest` era el único test del repo que caía en esto contra el
reloj real; el resto usa `FixedClock` (determinista) o ya replica la zona de producción.

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

Corre en push a `master`. **Construye la imagen, la publica en ECR y la despliega en la instancia
EC2**, esperando a que la aplicación responda `UP` antes de dar el despliegue por bueno.

> **Corregido 2026-09-06.** Acá decía *"Construye y publica la imagen; no despliega"*. Era cierto
> mientras el destino no estaba decidido. Ya lo está (§5.3).

Como el resto, se saltea con un aviso mientras falten las variables de repositorio
`AWS_ROLE_ARN`, `AWS_REGION` y `ECR_REPOSITORY`; el job de despliegue se saltea, además, si falta
`EC2_INSTANCE_ID`.

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

**c.bis) La segunda política del rol, `desplegar-por-ssm`** — creada el 2026-09-06, es lo que le
permite al workflow desplegar. Está aplicada en la cuenta real:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EjecutarElDespliegueSoloEnLaInstanciaDelBackend",
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:us-east-1:302277511407:instance/i-0ea00f555c5fe8028",
        "arn:aws:ssm:us-east-1::document/AWS-RunShellScript"
      ]
    },
    {
      "Sid": "LeerElResultadoDelComando",
      "Effect": "Allow",
      "Action": "ssm:GetCommandInvocation",
      "Resource": "*"
    }
  ]
}
```

Tres cosas que no son obvias y conviene no "arreglar" después:

- **`ssm:SendCommand` necesita los DOS recursos.** AWS evalúa la llamada contra la instancia *y*
  contra el documento. Con solo el ARN de la instancia, la llamada se rechaza igual. Y el ARN de
  un documento propiedad de AWS **no lleva número de cuenta**: `arn:aws:ssm:us-east-1::document/...`,
  con los dos puntos seguidos. Un ARN con la cuenta adentro apunta a un documento propio que no
  existe, y el permiso no aplica.
- **`ssm:GetCommandInvocation` tiene que ir sobre `*`.** No es pereza: esa acción **no soporta
  permisos a nivel de recurso**, así que un ARN concreto la deja sin efecto. Es el único comodín de
  la política, y lo que habilita es leer la salida de comandos de SSM — no ejecutarlos.
- **Lo que deliberadamente NO se dio:** `ssm:StartSession` (una sesión interactiva en la instancia
  es otra cosa que un despliegue), `ssm:GetParameter`/`PutParameter` (el workflow no necesita ver
  ni tocar las credenciales de producción; las lee la instancia con su propio rol, §6.3), nada de
  `ec2:*`, nada de `iam:*` y nada de S3. Verificado con `iam simulate-principal-policy`: las cuatro
  acciones que hacen falta dan `allowed`, y `SendCommand` contra otra instancia, contra
  `AWS-RunPowerShellScript`, `StartSession`, los parámetros, `ec2:TerminateInstances`,
  `iam:PutRolePolicy` y `s3:GetObject` dan todas `implicitDeny`.

**d) El repositorio de ECR** (`aws ecr create-repository --repository-name renaser-backend`).

**e) Las variables en GitHub** — Settings → Secrets and variables → Actions → pestaña
**Variables** (no Secrets: ninguna de estas es una credencial, ese es justamente el punto de OIDC):

| Variable | Valor real de este proyecto | Obligatoria |
|---|---|---|
| `AWS_ROLE_ARN` | `arn:aws:iam::302277511407:role/renaser-github-actions` | Sí — sin ella no se publica ni se despliega |
| `AWS_REGION` | `us-east-1` | Sí |
| `ECR_REPOSITORY` | `renaser-backend` | Sí |
| `EC2_INSTANCE_ID` | `i-0ea00f555c5fe8028` | Sí para desplegar. Sin ella se publica la imagen y el job de despliegue se saltea con un aviso |
| `DESPLIEGUE_ESPERA_SEGUNDOS` | — | No (default `240`) |
| `IMAGEN_PLATAFORMAS` | `linux/arm64` | No (default `linux/amd64`) |

> **Ninguna de estas cinco está creada todavía** (verificado el 2026-09-06:
> `gh api repos/ricardoIsmael/Renaser-90-dias-backend/actions/variables` → `total_count: 0`).
> Mientras sigan sin existir, el `cd.yml` corre, se saltea entero y **termina en verde sin haber
> hecho nada** — que es justo lo que muestran sus últimas corridas, de 14 a 20 segundos cada una.
> Crearlas es un paso manual de la consola de GitHub (o `gh variable set`), y es lo único que
> separa a este repositorio de tener entrega continua real.

### 5.2 Etiquetas de la imagen

Cada publicación deja dos: `:<sha-del-commit>` y `:latest`. La del SHA es la que sirve para saber
qué está corriendo y para volver a una versión anterior sin reconstruir nada; `latest` es solo
"la última", y nunca alcanza para responder qué versión está en producción.

**Lo que se despliega es la del SHA** (§5.3). `latest` queda como comodidad para un `docker pull`
a mano, no como la referencia de producción: un contenedor corriendo `:latest` no permite saber de
qué commit salió. Es exactamente lo que pasaba hasta el 2026-09-06, cuando el contenedor de
producción corría `:latest` y para saber qué había adentro había que comparar digests contra ECR.

### 5.3 El despliegue: una sola EC2, por SSM

> **Reescrita 2026-09-06.** Esta sección se llamaba *"El despliegue está pendiente y por qué no se
> inventó"* y explicaba que el destino no estaba decidido, comparando ECS Fargate / App Runner /
> EC2. **Ya está decidido y construido: EC2 + Docker**, así que la comparación se resume abajo en
> vez de presentarse como una elección abierta. El razonamiento de por qué no se inventó un
> destino sigue siendo correcto y por eso el job estuvo vacío hasta hoy.

**La infraestructura que existe de verdad** (verificada contra la cuenta `302277511407`,
`us-east-1`, el 2026-09-06):

| Pieza | Valor |
|---|---|
| Instancia | `i-0ea00f555c5fe8028` — t3.small, Amazon Linux 2023, IP fija `52.0.210.237` |
| Contenedores | `redis` (`redis:7-alpine`, sin puertos publicados) y `backend` (la imagen de ECR, `-p 8080:8080`), los dos en la red de Docker `renaser`. Con el modo sin corte (E-465) se suma `proxy` (nginx) en el 8080 y `backend` deja de publicar puerto |
| Rol de la instancia | `renaser-backend-ec2` — lee `/renaser/prod/*`, firma URLs de su bucket, baja de ECR, y trae `AmazonSSMManagedInstanceCore` |
| Delante | CloudFront `E3O4M4W7JW3TJQ` (`djbooeq09skac.cloudfront.net`), hablando **HTTP** al origen |
| Lo que **no** hay | ECS, CodeDeploy, balanceador, autoscaling |

**Por qué SSM `send-command` y no otra cosa.** Con una sola instancia y sin orquestador, las
alternativas eran SSH desde el runner o instalar un agente de despliegue. SSM gana por tres
motivos concretos: no hay que abrir el puerto 22 a los rangos de GitHub, no hay que guardar una
clave privada como secret (que es exactamente la clase de credencial permanente que §5.1 evita al
usar OIDC), y el permiso queda acotado por IAM a *esa* instancia y *ese* documento (§5.1 c.bis).
Además es el mismo mecanismo que ya se venía usando a mano — por ejemplo
`scripts/crear-primer-admin.sh`.

**Qué hace el job `desplegar`, en orden:**

1. Se autentica por OIDC (el mismo rol que publica en ECR, con la política nueva).
2. Arma el script que va a correr en la instancia y lo manda con
   `ssm send-command --document-name AWS-RunShellScript`.
3. Dentro de la instancia corre `scripts/despliegue/desplegar-backend.sh` (desde E-465; antes era un
   heredoc dentro de `cd.yml`): `docker login` contra ECR → `docker pull` de **la etiqueta del commit**
   → elige el modo mirando la memoria (ver «Las tres cosas», punto 1) → levanta el backend con la red
   `renaser`, `--memory 1400m`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60.0`,
   `SPRING_PROFILES_ACTIVE=prod` y `AWS_REGION=us-east-1` (los dos de memoria desde V-8, ver §7).
4. Consulta `/actuator/health` del contenedor nuevo (por su IP en la red `renaser`) cada 3 s hasta que
   diga `"status":"UP"`, con un tope de 240 s (`DESPLIEGUE_ESPERA_SEGUNDOS`). **Medido el 2026-09-30:
   la aplicación tarda 50,7 s en arrancar** (`Started RenaserOsApplication in 50.729 seconds`; antes se
   había medido 43 s), así que el tope tiene más de 4× de margen para una migración larga o una RDS fría.
5. El runner espera el `Status` de la invocación y **falla el workflow si no es `Success`**.

**Se despliega la etiqueta del SHA, nunca `latest`.** `latest` no permite saber qué versión está
corriendo ni a cuál volver. Con la etiqueta del commit, `docker ps` responde las dos preguntas.

#### Las tres cosas que hay que tener presentes

**1. Sin corte solo si la instancia tiene memoria para dos backends; hoy no la tiene (E-465, D-235).**
El script tiene dos modos y elige solo:

- **Sin corte** si `MemAvailable` ≥ tope del contenedor + 512 MB (1.912 MB). Un nginx (`proxy`) queda en
  el 8080 del host, que es lo que mira CloudFront. El backend nuevo arranca como `backend-nuevo` **sin
  puerto publicado** mientras el viejo atiende; con UP se intercambian los nombres (el nuevo pasa a
  llamarse `backend`), nginx lo resuelve por el DNS de Docker en ≤5 s, se esperan 20 s y el viejo se apaga
  ordenado (`docker stop -t 45`). Si el nuevo no arranca se borra, y el viejo nunca dejó de atender. La
  primera vez además pone el proxy delante: lo ensaya en `127.0.0.1:8081` y el único corte es ~1 s.
- **Reemplazo**, lo de antes: `docker rm -f backend` + `docker run`, **~52 s sin servicio** (medido el
  2026-09-30, E-465), durante los que CloudFront devuelve `504 Gateway Timeout`. Se usa si la memoria no
  alcanza, o si la variable de repositorio `DESPLIEGUE_MODO` vale `reemplazo`.

**En la t3.small actual (1.909 MB, 531 MB disponibles con el backend andando) el script siempre elige
reemplazo**: dos JVM no entran, y forzarlo es repetir E-155 (siete horas caído). El corte desaparece al
pasar la instancia a `t3.medium` (4 GB, +~US$15/mes, D-235), sin tocar código. Mientras tanto, conviene
desplegar en horario de poco uso.

> **Actualizado 2026-10-02 (E-498).** La instancia ya es `t3.medium` (3.835 MB) desde el 30-09, y el
> párrafo de arriba quedó como historia. Con el backend, redis, nginx y Alloy andando quedan
> **~2.060–2.280 MB disponibles**, apenas por encima de los 1.912 que pide la condición: el 02-10 a las
> 12:23 faltaron 20 MB (1.892) y ese despliegue tuvo ~55 s de corte. Desde E-498, si la primera medición
> no alcanza, el script hace `sync`, vuelve a medir y, **si lo que falta cabe en el tope de Alloy
> (192 MB)**, para el contenedor `alloy` mientras conviven las dos JVM y lo relanza al final (también si
> el despliegue falla, con un `trap`). La condición (tope 1.400 + margen 512) **no se bajó**: el pico
> medido del backend en producción es 985–1.013 MB a los 2–4 min de arrancar (`memory.peak` del cgroup),
> pero el tope de 1.400 es lo único que lo acota de verdad y E-155 costó siete horas. La salida del CD deja
> una línea con los números: `Memoria: disponible … MB (primera medicion …), necesario 1912 MB (tope 1400 +
> margen 512), Alloy parado: si|no. Decision: sin-corte|reemplazo.`

Con el modo sin corte hay que tener presente:

- **Migraciones compatibles hacia atrás.** La versión vieja sigue atendiendo ~50 s contra el esquema que
  el nuevo ya migró. Una migración que borre o renombre algo que la versión anterior usa va con
  `DESPLIEGUE_MODO=reemplazo` (y después se vuelve a `auto`), o se parte en expandir y contraer.
- **Lo conectado al viejo se corta al apagarlo** (voz en vivo en curso, streaming largo del chat). El chat
  por WebSocket reconecta contra el nuevo; el broker STOMP es en memoria, uno por instancia.
- nginx es transparente: no toca `X-Forwarded-For` ni las cabeceras de CloudFront y conserva `Host`
  (la IP del cliente sigue saliendo igual que antes, ver `DireccionIpDelCliente`). Soporta WebSocket y no
  hace buffer. Su configuración la reescribe el script en `/opt/renaser/proxy/renaser.conf`.
- Los schedulers aguantan dos instancias unos segundos: 20 de 22 llevan ShedLock sobre JDBC y los otros
  dos pueden correr en todas (`SchedulerLockConfig`).

> **Corregido 2026-09-30 (E-465).** Este punto decía «Hay unos segundos de caída en cada despliegue, y
> es inevitable hoy … ~45 s … No se disimula porque no se puede arreglar sin cambiar la topología». La
> topología ahora cambia sola (proxy + dos contenedores) cuando hay memoria; lo que sigue siendo cierto es
> que en la t3.small no la hay, así que el corte sigue ahí hasta agrandarla. Y son ~52 s, no ~45.

**2. No se vuelve solo a la versión anterior, y es a propósito.** Si la aplicación no levanta, el
workflow falla y deja escrito en la salida el comando exacto para restaurar la imagen anterior —
pero no lo ejecuta. El motivo es Flyway: las migraciones corren al arrancar y no se deshacen, así
que si el arranque falló *después* de migrar, devolver el binario viejo lo deja contra un esquema
más nuevo, que es peor que el problema original. Automatizar el retroceso exige antes decidir qué
hacer con el esquema, y eso no está decidido.

**3. El disco son 8 GB y cada versión de la imagen ocupa ~450 MB. En el servidor quedan la versión
del backend en uso y las dos anteriores, nada más (decisión del dueño, 2026-10-02, E-498).** El script
lo aplica solo en cada despliegue, dos veces: antes del `docker pull` (libera disco para la imagen nueva)
y en el paso 5 (cuando la nueva ya es la que está en uso). Conserva las imágenes de `renaser-backend` que
use **cualquier** contenedor (en marcha o parado), la que se va a desplegar y las dos más nuevas del
resto por fecha de creación; borra las demás etiquetas con `docker rmi` sin `-f` y después solo las capas
colgadas (`docker image prune -f`, sin `-a`). No toca otros repositorios (redis, nginx, Alloy, ni
`postgres:16-alpine` / `httpd:alpine`, que están en la instancia sin uso pero no son del backend),
volúmenes ni contenedores. Deja en la salida `Limpieza de imagenes de …: N etiquetas borradas …
Disco libre en /: antes … MB, despues … MB.` Con tres versiones quedan ~3,8 GB libres; el aviso de
menos de 3 GB ahora quiere decir que el disco lo ocupa otra cosa (`docker system df`).

> **Corregido 2026-10-02 (E-498).** Este punto decía que el script **no** borraba las imágenes
> etiquetadas y que había que limpiarlas a mano (con un `tail -n +3` sobre `docker images`). Nadie lo
> hizo: el 02-10 había 85 etiquetas de `renaser-backend` acumuladas y solo 1,8 GB libres. Las borró a mano
> el coordinador por SSM (quedaron `d55f84d8`, `cde92c44` y `153820e9`; 3,8 GB libres) y desde entonces
> lo hace el script.

#### El health check depende de que `/actuator/health` siga siendo público

Sigue vigente lo que ya decía esta sección, y ahora con más peso, porque el despliegue **depende**
de esa URL: `/actuator/health` responde sin autenticación **por omisión**, no por decisión.
`SecurityConfig` todavía no tiene `anyRequest().authenticated()` —está anotado como pendiente en el
propio archivo— y lo que no coincide con ningún `requestMatchers` queda permitido. El día que se
cierre esa regla, el health check empieza a recibir 401, **y todos los despliegues van a fallar
aunque la aplicación esté perfecta**. Al agregar `anyRequest().authenticated()` hay que dejar
`/actuator/health` explícitamente permitido en el mismo cambio.

> **Ampliado 2026-10-01 (D-237).** Desde que todo actuator vive en el puerto de administración
> (§5.4), `/actuator/health` del 8080 ya no es el endpoint de actuator: es un *forward* interno
> (`SaludEnElPuertoPublicoConfig`) a `/salud`, el grupo de health `publico` que Boot publica en el
> puerto de la aplicación. Al agregar `anyRequest().authenticated()` hay que permitir **las dos**
> rutas (`/actuator/health` y `/salud`), y también el despacho `FORWARD`. `PuertoDeAdministracionIT`
> falla si `/actuator/health` deja de responder `{"status":"UP"}` en el 8080.

#### Por qué EC2 y no ECS Fargate o App Runner

La comparación que estaba acá sigue siendo válida como registro de la decisión:

| Opción | Qué hay que crear | A favor | En contra |
|---|---|---|---|
| **EC2 + Docker** *(elegida)* | Instancia, Docker, Elastic IP | Lo más barato y lo más simple de entender | Despliegue y ciclo de vida a mano; una sola instancia = caída en cada despliegue, salvo con memoria para dos backends (E-465) |
| **ECS Fargate** | Cluster, task definition, service, ALB, target group, security groups, rol de tarea | Control fino, escalado horizontal, despliegue sin caída, es lo que espera §5.2.1 de `CLAUDE.md` (varias instancias) | La más infraestructura para levantar |
| **App Runner** | Un servicio apuntando a la imagen de ECR | Lo más rápido de poner en pie; HTTPS y escalado incluidos | Menos control de red; el escalado a cero castiga el arranque de una JVM |

**El día que haya más de una instancia**, este job deja de alcanzar: hay que desplegar de a una y
sacarla del balanceador antes. Y ahí entran también las dos piezas que `CLAUDE.md` §5.2.1 ya
anticipa (Redis Pub/Sub para el chat y para invalidar la caché de rol entre instancias). Migrar a
ECS es el camino natural, y la imagen ya está lista para eso: es multi-arquitectura, corre como
usuario sin privilegios, y toma su configuración de Parameter Store (§6) en vez de variables
cableadas.

### 5.4 Observabilidad: Prometheus y Grafana (D-237, 2026-10-01)

**Qué hay.** El backend publica sus métricas en formato Prometheus (`micrometer-registry-prometheus`)
en `/actuator/prometheus`, **solo en el puerto de administración 8091** (`management.server.port`,
configurable con `MANAGEMENT_SERVER_PORT`). Todas las series llevan `application="renaser-backend"`
y `entorno` (`local`, o `prod` desde `application-prod.yaml`). Además de lo que trae Spring Boot
(HTTP con cubetas para el p95, JVM, Hikari, CPU, GC), hay métricas propias **sin datos personales**
(ninguna etiqueta lleva una persona; solo enums y nombres de herramienta):

| Serie | Etiquetas | Qué mide |
|---|---|---|
| `renaser_acompanante_mensajes_total` | `agente`, `resultado` (`ok`, `error`, `proveedor_no_disponible`, `cancelado`, `limite_diario`) | Mensajes al chat de SER / tutor |
| `renaser_acompanante_turnos_seconds_*` | `agente`, `resultado` | Latencia del turno con la IA, de la pregunta al fin del stream |
| `renaser_acompanante_propuestas_creadas_total` | `herramienta` | Propuestas con botón ofrecidas |
| `renaser_acompanante_propuestas_resueltas_total` | `herramienta`, `resolucion` (`confirmada`, `fallida`, `cancelada`) | Cómo terminaron |
| `renaser_voz_en_vivo_aperturas_total` | `resultado` (`abierta`, `cuota_agotada`, `no_disponible`, `no_autorizada`) | Intentos de abrir la voz en vivo |
| `renaser_voz_en_vivo_hablado_seconds_total` | — | Segundos cobrados de la cuota de voz |
| `renaser_chat_media_subidas_solicitadas_total` / `renaser_chat_media_enviada_total` | `tipo` (`imagen`, `audio`, `video`) | URLs firmadas / mensajes con archivo subido |

Se registran por puertos `out` (`RegistrarMetricaDelAcompanantePort` en `rag`,
`RegistrarMetricaDelChatPort` en `chat`) con adaptadores Micrometer; nada de Micrometer en
`application/` ni en `domain/`. Un contador nace con el primer evento: hasta entonces su panel
dice «No data» (los de totales usan `or vector(0)`).

**Por qué un puerto aparte, y por qué no se publica.** `SecurityConfig` solo cubre `/api/v1/**`
(auditoría OUT-2): nada protege a actuator salvo su exposición. Si `/actuator/prometheus` estuviera
en el 8080, CloudFront → nginx lo dejaría leer desde internet (nombres de endpoints, tráfico, uso).
En el 8091 no llega nadie de afuera: `desplegar-backend.sh` publica en el host **solo** el 8080 del
proxy (el backend no publica ningún puerto en el modo sin corte), y el nginx hace `proxy_pass` al
8080 del contenedor. El 8091 lo alcanza únicamente otro contenedor de la red Docker `renaser`.
Ni siquiera en el 8091 están `env`, `heapdump`, `loggers` ni ningún otro: `access.default: none` y
exposición `health,prometheus` (`src/main/resources/actuator.yaml`, que importan main y test).
`PuertoDeAdministracionIT` lo prueba contra Tomcat real: en el puerto público `/actuator/prometheus`,
`/actuator/env`, `/actuator/heapdump`, `/actuator/loggers` dan 404, y `/actuator/health` sigue en 200.

**Cómo se comporta Boot 4.1 con el puerto aparte** (verificado): *todos* los endpoints de actuator,
health incluido, se mudan al 8091, y en el 8080 `/actuator/**` deja de existir. Como el CD, el
despliegue sin corte y los scripts locales miran `:8080/actuator/health`, se publica el grupo de
health `publico` en el 8080 con `additional-path: server:/salud` (Boot solo acepta **un** segmento
ahí; `/actuator/health` lo rechaza) y `SaludEnElPuertoPublicoConfig` reenvía `/actuator/health` →
`/salud`. Mismo cuerpo `{"status":"UP"}`, 503 si está DOWN. Por eso **no hubo que tocar** el
health check del script ni del CD.

**Dos backends locales a la vez** (p. ej. el de siempre en 8080 y uno de e2e en 8090) chocan en el
8091: al segundo hay que darle `MANAGEMENT_SERVER_PORT=8092`. Las pruebas con `RANDOM_PORT` no
tienen el problema (Boot sortea también el de administración).

#### Verlo en local

```bash
docker compose --profile observabilidad up -d      # agrega prometheus y grafana a db y redis
# el backend corre en el host como siempre (IDE o java -jar): app en 8080, métricas en 8091
```

- **Grafana:** http://localhost:3000, usuario `admin`, contraseña `admin` (solo local). Carpeta
  *Renaser*: «Backend general» (req/min, p95, 5xx por endpoint, heap, Hikari, CPU, GC) y
  «Acompañante, voz y chat» (SER, voz en vivo, propuestas, media). El datasource y los paneles se
  cargan solos (`infra/observabilidad/grafana/provisioning`); los paneles son JSON versionados en
  `infra/observabilidad/grafana/dashboards` — un cambio hecho en la interfaz no vuelve al repo
  hasta exportarlo y pegarlo en el archivo.
- **Prometheus:** http://localhost:9090 (Status → Targets: `renaser-backend` tiene que estar UP).
  Scrapea `host.docker.internal:8091`; en Linux ese nombre existe solo por
  `extra_hosts: host-gateway` del compose.
- `docker compose up` **sin** perfil sigue levantando solo `db` y `redis`.

#### Producción: Grafana Cloud con Grafana Alloy (preparado, NO activo)

No hay Prometheus ni Grafana en la EC2 (no caben con dos JVM en 4 GB, y habría que respaldarlos).
Va **Grafana Alloy** —un agente— que scrapea `backend:8091` cada 30 s por la red Docker y manda
las series a Grafana Cloud por `remote_write`. Config: `infra/observabilidad/alloy/config.alloy`
(descarta las familias que ningún panel usa para no gastar series del plan gratis: 10.000 activas;
hoy el backend local expone ~700).

Cómo se activa (pasos del dueño, cuando exista la cuenta):

1. Grafana Cloud → el stack → *Prometheus* → *Details*: copiar la **URL de remote write**
   (`https://prometheus-prod-…grafana.net/api/prom/push`) y el **Instance ID** (número, es el
   usuario). Crear un *Access Policy token* con alcance `metrics:write`.
2. Crearlos en Parameter Store (región `us-east-1`), como los demás secretos:
   `/renaser/prod/grafana-cloud-prom-url` (String), `/renaser/prod/grafana-cloud-prom-user`
   (String), `/renaser/prod/grafana-cloud-token` (**SecureString**). El rol de la instancia ya lee
   `/renaser/prod/*` (§6.3). La aplicación también los va a importar como propiedades sueltas: no
   los usa y no molestan.
3. El próximo despliegue los encuentra: el CD manda la config de Alloy dentro del script
   (`CONFIG_ALLOY_B64`), y `desplegar-backend.sh` → `lanzar_alloy` escribe
   `/opt/renaser/alloy/{config.alloy, alloy.env}` (dir 700, env 600: el token no queda en la
   línea de comandos ni en el log de SSM) y levanta el contenedor `alloy`
   (`docker.io/grafana/alloy:v1.20.1`, `--memory 192m`, red `renaser`, `unless-stopped`, **sin
   puertos publicados**). Cada despliegue lo relanza con la config del commit.
4. En Grafana Cloud: *Dashboards → Import* de los dos JSON de `infra/observabilidad/grafana/dashboards`
   y elegir el datasource `grafanacloud-…-prom` en la variable *Datos*.

Mientras falte cualquiera de las tres credenciales, `lanzar_alloy` imprime «faltan credenciales
de Grafana Cloud… no se lanza Alloy» y no hace nada. Un fallo al lanzarlo **nunca** hace fallar el
despliegue. Para apagarlo: `docker rm -f alloy` en la instancia y borrar uno de los parámetros (si
no, el próximo despliegue lo vuelve a levantar).

**Memoria.** Probado en local con la config de producción: Alloy usa **~45 MB** con ~680 series
(tope 192 MB). El modo sin corte necesita `MemAvailable` ≥ 1.400 + 512 MB; con ~2,4 GB disponibles
en la t3.medium y Alloy andando quedan ~2,2 GB: sigue alcanzando, pero el margen baja. El segundo
Tomcat del puerto de administración suma unos pocos MB por JVM.

---

## 6. Configuración remota: AWS Systems Manager Parameter Store

### 6.1 Cómo está cableado

| Perfil | De dónde sale la configuración |
|---|---|
| local / test | `application.yaml` con sus defaults, más `optional:file:.env` si existe, **más las variables de entorno del IDE**. Sin hablar con AWS. |
| `prod` | `application-prod.yaml` agrega `spring.config.import: aws-parameterstore:/renaser/prod/` |

> **En esta máquina, las variables de entorno para correr en local están cargadas en el IDE**
> (la *run configuration* de IntelliJ), **no en el `.env`**. El `.env` del checkout tiene solo las
> tres de Web Push, así que a simple vista parece que el backend está sin configurar — y no lo está.
>
> **Por qué importa:** ya pasó que se diagnosticara "falta la credencial" mirando el `.env`, cuando
> el valor estaba puesto y la app arrancaba bien desde el IDE. Y al revés: **arrancar desde la
> terminal con `./mvnw spring-boot:run` no hereda esas variables**, porque viven en la
> configuración de ejecución de IntelliJ y no en el shell. Si algo funciona en el IDE y falla en la
> terminal, ése es el primer lugar donde mirar, antes que el código.
>
> Spring resuelve `${VARIABLE:default}` desde el entorno del proceso, así que las tres fuentes
> conviven: `.env` → entorno del IDE → defaults de `application.yaml`.

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

**Cambiar un parámetro no tiene efecto hasta reiniciar el contenedor.** Parameter Store se lee una
sola vez, al arrancar; no hay recarga en caliente. Después de un `put-parameter --overwrite` va
`docker restart backend` (o un despliegue). Olvidarlo dejó a producción una noche sin correos con
una `SMTP_PASSWORD` vieja (**E-244**).

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
| `REDIS_USERNAME` | String | Usuario ACL de Redis, si el servidor lo exige |
| `REDIS_PASSWORD` | **SecureString** | Contraseña de Redis; no dejarla en texto plano |
| `REDIS_SSL_ENABLED` | String | `true` cuando el proveedor de Redis exige TLS; `false` para el contenedor privado local |
| `REDIS_SESSION_NAMESPACE` | String | Namespace de Spring Session; por defecto `renaser:session:v2` |
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
| `AWS_S3_BUCKET` | String | Default `s3-renaser90dias`, **que es el bucket de producción** |
| `AWS_REGION` | String | Default `us-east-1` |

Las credenciales de S3 **no van acá**: `AlmacenamientoS3Config` usa `DefaultCredentialsProvider`,
que las toma del rol de la tarea o de la instancia. No hay que crear un `AWS_SECRET_ACCESS_KEY`.

> **Ojo en local (G-5, 2026-09-26).** El default de `AWS_S3_BUCKET` es el bucket de **producción**.
> Producción depende de ese default (no carga el parámetro), así que no se cambia; quien prenda
> `STORAGE_PROVEEDOR=s3` en su máquina **tiene que** poner su propio `AWS_S3_BUCKET` en la configuración
> de ejecución, o lo que suba el backend local (la tarjeta de bienvenida, por ejemplo) cae en el bucket
> real. Con `STORAGE_PROVEEDOR=noop` (el default local) no se sube nada, y desde G-5 la bienvenida
> tampoco manda una foto que no existe: sale solo el texto.

**Para el login social** (los adaptadores fallan al *invocarse* sin configurar, no al arrancar):

| Parámetro | Tipo |
|---|---|
| `GOOGLE_OAUTH_CLIENT_ID` | String |
| `GOOGLE_OAUTH_CLIENT_SECRET` | **SecureString** |
| `APPLE_CLIENT_ID`, `APPLE_TEAM_ID`, `APPLE_KEY_ID` | String |
| `APPLE_AUTH_KEY_PATH` | String (ruta al `.p8` dentro del contenedor) |
| `FACEBOOK_APP_ID` | String |
| `FACEBOOK_APP_SECRET` | **SecureString** |

**Para encender la IA:**

> **Corregido 2026-09-25.** Esta línea decía "(hoy todos los adaptadores son `NoOp` y nunca se llamó
> a un modelo)". Ya no es así: los adaptadores de Gemini existen y se usan (chat, herramientas, voz,
> memoria). Si producción los usa lo decide `IA_PROVEEDOR` en Parameter Store.

| Parámetro | Tipo | Ojo |
|---|---|---|
| `GOOGLE_GENAI_API_KEY` | **SecureString** | Activar facturación antes de usarla con datos reales: el nivel gratuito pide no enviar información personal, y el Espejo de la Sombra manda diarios íntimos |
| `IA_PROVEEDOR` | String | `noop` o `google`. Ponerlo en `google` antes de que existan los adaptadores reales **deja puertos sin implementación y el arranque falla** |
| `IA_BUSQUEDA_WEB` | String | Necesita además un `RENASIA_CHAT_MODEL` que soporte búsqueda (sin `-lite`): ver D-100 |
| `RENASIA_CHAT_MODEL`, `RENASIA_EMBEDDING_MODEL`, `RENASIA_EMBEDDING_DIMENSIONS`, `RENASIA_EMBEDDING_TASK_TYPE` | String | Los vectores de dos modelos distintos no son comparables: cambiar el de embeddings obliga a reindexar todo |

**Para el acompañante: voz, voz en vivo, propuestas y memoria.** Son las decisiones del dueño del
2026-09-25 para el primer paso a producción. Todos los interruptores valen `false` por defecto: si
no se cargan acá, la función queda apagada aunque el código esté desplegado.

| Parámetro | Valor en producción | Qué prende | Ojo |
|---|---|---|---|
| `IA_VOZ_PROVEEDOR` | `google` | La voz del orbe (Kore, D-159) | Usa el cupo del chat (`RENASIA_LIMITE_DIARIO`) |
| `IA_VOZ_EN_VIVO` | `true` | La conversación por voz en tiempo real (Gemini Live, D-162) | Cobra por minuto. La cuota por defecto ya es 20 min por persona y por día (`IA_VOZ_EN_VIVO_MINUTOS_POR_DIA`): no hace falta cargarla |
| `IA_ACOMPANANTE_CONFIRMACION_CON_BOTONES` | `true` | Las propuestas con Confirmar y Cancelar (D-153) | Solo con la app nueva instalada. Con la vieja, la persona ve la propuesta como texto y no puede confirmarla |
| `IA_ACOMPANANTE_MEMORIA` | `true` | La memoria del acompañante (D-167, tablas de V67) | Con la app nueva. La vieja no tiene la pantalla para ver y borrar lo que se recuerda |
| `IA_ACOMPANANTE_AVISOS_EN_CHAT`, `IA_ACOMPANANTE_LOGROS_EN_CHAT` | `true` cuando el dueño apruebe los textos | Avisos de hábitos y logros en el chat | Los textos de `application.yaml` están marcados como provisorios |
| `RENASIA_CHAT_MODEL` | No cargarlo: queda el default `gemini-3.5-flash-lite` | El modelo del chat | Decisión del dueño del 2026-09-25: el más estable de los medidos (E-233) |

**Cómo se cargan: `scripts/despliegue/parametros-acompanante.sh`.** Lo corre quien tenga acceso a AWS
y no sobrescribe nada sin preguntar. Antes de escribir, confirma que las credenciales sean de la
cuenta de producción, la `302277511407`, y si no lo son se detiene (E-277: la primera vez escribió en
otra cuenta).
- **`preparar`, antes del push a master:**
  - pide la API key de Gemini sin mostrarla;
  - revisa `IA_PROVEEDOR` y `RENASIA_CHAT_MODEL`;
  - crea los interruptores de arriba **apagados**;
  - pone en `-` los dos crons del semáforo (`renaser.scheduling.semaforo.cron` y
    `renaser.scheduling.resumen-semaforo.cron`), que así no se programan.

  Así el despliegue no le cambia nada a quien tiene la app de hoy. El dueño decidió el 2026-09-25
  subir el backend primero y prender todo junto después. Con la app vieja, un aviso del semáforo
  llegaría sin pantalla donde verlo.
- **`prender`, cuando la gente tenga la app nueva:** enciende los interruptores y borra los dos
  crons, para que el semáforo recalcule solo, y después `docker restart backend`.

**Cómo comprobar, después del despliegue, que el semáforo quedó apagado:**
- **En el log:** `[points.CerrarSemaforoScheduler] evaluados=…` sale en INFO en cada corrida. Si a los
  :25 de la hora no aparece, la tarea no corrió.
- **En la base de producción, pasadas las :25 y las :40:**
  - `SELECT count(*) FROM renaser.semaforo_dias;` da **0**. La tabla la crea V68 y solo la llena el
    barrido.
  - `SELECT name, locked_at FROM renaser.shedlock WHERE name IN ('points-cerrar-semaforo','mentoring-resumir-semana-semaforo');`
    no devuelve **ninguna fila**: ninguna de las dos tareas tomó su lock.

Si alguna da otra cosa, el parámetro no se tomó: revisar el nombre exacto y que el contenedor haya
arrancado **después** de cargarlo (E-244).

**Para el chat** (se leen al arrancar: se cambia el parámetro y `docker restart backend`):

| Parámetro | Valor por defecto | Qué decide | Ojo |
|---|---|---|---|
| `BIENVENIDA_ACTIVA` | `false` | Las bienvenidas automáticas del soporte y del grupo, firmadas por el programa (D-199, D-204) | Se prende cuando el dueño apruebe los textos de `bienvenida/mensajes.yaml` |
| `CHAT_FOTO_DE_INTEGRANTES` | `TARJETA` | Qué foto muestra la info de un grupo de cada integrante (D-206): `TARJETA`, siempre su tarjeta con nombre; `FOTO_SUBIDA`, su foto si la subió y si no la tarjeta | No hace falta cargarlo para el modo por defecto. Cambiarlo no pide APK. El log de arranque dice el modo que tomó: `[chat.fotos] foto de los integrantes…` |
| `SEMAFORO_TARJETA_DIARIA_ACTIVA` | `true` | La tarjeta diaria del semáforo en el chat de soporte, a las 23:50 de cada aprendiz (D-223) | No hace falta cargarlo para tenerla prendida. Para apagarla: `false` y reiniciar. Con `STORAGE_PROVEEDOR=noop` sale solo el texto. El APK publicado antes de D-199 muestra la imagen de un mensaje del programa como «Mensaje del sistema»; el texto sí se lee. En el log, cada noche: `[chat.semaforo] enSuHora=… enviadas=… fallidas=…` (solo cuando hubo alguien en su hora) y, la primera vez de cada color, `[chat.semaforo] tarjeta … subida al almacenamiento` |
| `RANKING_SEMANAL_ACTIVO` | `false` | El podio semanal del ranking general en el grupo «Formación Renaser Global», los lunes a las 09:00 de Lima (D-262) | Prenderlo no publica semanas atrasadas. Para probarlo antes: `GET /api/v1/admin/ranking-semanal/vista-previa` (no publica) y `POST …/publicar` (publica aunque esté apagado). Exige la migración V95 |

#### Checklist para encender el semáforo (S-7, 2026-09-26)

Se enciende **de lunes a jueves**, nunca viernes, sábado ni domingo. El barrido de las :25 cierra la
semana (sábado→viernes) en la primera corrida después de la medianoche del viernes en la zona de cada
persona, y los avisos del cierre salen solo si ese cierre cae sábado o domingo local. Encendido un
viernes, la primera semana se cierra a las pocas horas con uno o dos días medidos; encendido el
sábado, se cierran de golpe todas las semanas atrasadas desde el día 1 (sin avisos, pero quedan como
historial). Con lunes a jueves, cuando llega el primer sábado ya hay de 2 a 5 días medidos y nadie
recibe un color por un solo día (además, desde S-7 el chat no cuenta la semana con color si tuvo
menos de 3 días con datos: `SemaforoEnChat.DIAS_MINIMOS_PARA_EL_COLOR`).

1. `parametros-acompanante.sh prender` y `docker restart backend` (ver arriba).
2. **Pasadas las :25 de la primera hora**, en la base de producción:
   ```sql
   -- el barrido corrió y tomó su lock
   SELECT name, locked_at, lock_until FROM renaser.shedlock WHERE name = 'points-cerrar-semaforo';
   -- guardó días de hoy para la gente con programa activado (debe ser > 0 si hay aprendices en el programa)
   SELECT count(*) AS dias, count(DISTINCT participante_id) AS personas, max(calculado_en)
     FROM renaser.semaforo_dias;
   -- ninguna semana cerrada todavía si se encendió de lunes a jueves
   SELECT count(*) FROM renaser.semaforo_semanas;
   ```
   Y en el log, `[points.CerrarSemaforoScheduler] evaluados=… fallidos=0`.
3. **El sábado siguiente, pasadas las 00:25 de Lima (05:25 UTC) y las :40:**
   ```sql
   -- una foto por persona medida para el viernes que terminó
   SELECT semana_hasta, count(*), count(porcentaje) AS con_datos, min(dias_con_datos), max(dias_con_datos)
     FROM renaser.semaforo_semanas GROUP BY semana_hasta ORDER BY semana_hasta DESC LIMIT 2;
   -- avisos del sábado: persona, mentor y resumen general, sin duplicados
   SELECT tipo, count(*), count(DISTINCT origen_evento_id)
     FROM renaser.notificaciones WHERE tipo = 'RESUMEN_SEMANAL' AND creado_en > now() - interval '6 hours'
     GROUP BY tipo;
   -- nada trabado en el outbox
   SELECT event_type, count(*) FROM event_publication WHERE completion_date IS NULL GROUP BY event_type;
   ```
4. **Apagado de urgencia:** `renaser.scheduling.semaforo.cron=-` y `renaser.scheduling.resumen-semaforo.cron=-`
   en Parameter Store y `docker restart backend`; solo el mensaje del chat:
   `IA_ACOMPANANTE_SEMAFORO_EN_CHAT=false`. Apagar no borra nada: al volver, el barrido se pone al día
   solo (es derivado, regla 02 §2).

#### Reprocesar la semana de una persona (S-7, 2026-09-26)

Cuándo: un error ya corregido dejó mal la foto de una semana (`semaforo_semanas`), por ejemplo días que
`habits` contó mal. La foto es **append-only** para el código: ningún camino la reescribe. Reprocesar es
una operación manual y excepcional, que queda anotada en la bitácora con la foto anterior.

1. **Guardar la foto de antes** y pegarla en la entrada de la bitácora:
   ```sql
   SELECT * FROM renaser.semaforo_semanas
    WHERE participante_id = :persona AND semana_hasta >= :viernes ORDER BY semana_hasta;
   ```
2. **Borrar desde esa semana en adelante** (no una sola del medio: el barrido recalcula a partir de la
   **última** semana cerrada que queda, así que un hueco en el medio no se vuelve a llenar nunca):
   ```sql
   DELETE FROM renaser.semaforo_semanas WHERE participante_id = :persona AND semana_hasta >= :viernes;
   ```
   Los días (`semaforo_dias`) no se borran: el barrido los vuelve a calcular y el upsert solo reescribe
   los que cambiaron.
3. **Esperar a la próxima corrida de las :25.** La persona tiene que tener la cuenta ACTIVA y el
   programa activado; si no, el barrido no la toca.
4. **Verificar** con la consulta del paso 1: las semanas vuelven con `cerrada_en` de hoy. No hay avisos
   repetidos: el cierre tardío (fuera del sábado o domingo de esa semana) no avisa, y dentro del fin de
   semana la clave del aviso es la misma (`SemanaDelSemaforoCerradaEvent.claveDe`), así que la bandeja
   y el chat la descartan.

#### Anotar a mano una suspensión anterior a V72 (D-209, 2026-09-27)

Desde V72 los días con la cuenta suspendida no se miden: `points` anota cada suspensión y cada
reactivación al ocurrir. De las anteriores al despliegue no hay registro (no existe un historial de estados
de cuenta), así que siguen midiéndose como antes. **Solo** si alguien conoce las fechas por fuera (quien la
suspendió, un mensaje con fecha) y hay que corregir una semana que quedó en rojo por eso:

1. **Anotar la suspensión** con sus días locales: `:desde` es el día de la suspensión y `:hasta` el de la
   reactivación, los dos quedan sin medir (`reanudada_el` es el día siguiente). Si la persona seguía suspendida al
   desplegar, V72 ya le abrió una desde ese día: en vez de insertar, se corre su `desde` hacia atrás.
   ```sql
   -- ya reactivada antes del despliegue
   INSERT INTO renaser.semaforo_pausas (id, usuario_id, motivo, desde, hasta, reanudada_el, creada_en, reanudada_en)
   VALUES (gen_random_uuid(), :persona, 'CUENTA_SUSPENDIDA', :desde, NULL, :hasta + 1, :suspendida_en, :reactivada_en);
   -- suspendida todavía al desplegar (la abrió el relleno de V72)
   UPDATE renaser.semaforo_pausas SET desde = :desde
    WHERE usuario_id = :persona AND motivo = 'CUENTA_SUSPENDIDA' AND desde > :desde;
   ```
2. **Reprocesar** desde la semana (viernes) que contiene `:desde`, con el procedimiento de arriba. Las filas
   viejas de `semaforo_dias` de esos días quedan, pero ya no se muestran ni cuentan: un día que no se mide
   nunca muestra porcentaje.
3. **Anotarlo en la bitácora** con las fechas, de dónde salieron y las fotos de antes.

Nunca con fechas estimadas: un día anotado de más deja de medirse para siempre.

#### Volver a una imagen anterior a V72 (D-209)

El código anterior a V72 no sabe leer una suspensión (`hasta` NULL): falla al armar la ventana de esa
persona, y con ella la tarjeta del semáforo de Hoy y la tabla de su grupo. **Antes** de desplegar una imagen
anterior:

```sql
-- anotar el resultado en la bitácora: sin estas filas no hay forma de reconstruirlas
SELECT id, usuario_id, desde, reanudada_el, creada_en, reanudada_en
  FROM renaser.semaforo_pausas WHERE motivo = 'CUENTA_SUSPENDIDA';
DELETE FROM renaser.semaforo_pausas WHERE motivo = 'CUENTA_SUSPENDIDA';
```

Esas personas vuelven al comportamiento de antes (los días de la suspensión se miden al reactivarlas). La
columna `motivo` puede quedar: el código anterior no la lee y su DEFAULT cubre la pausa del staff. Al volver
a la imagen nueva las filas no vuelven solas: se reinsertan con lo anotado.

**Opcionales — solo si hay que apartarse del default:** `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`,
`DB_POOL_CONNECTION_TIMEOUT_MS`, `ASYNC_IA_CONCURRENCY_LIMIT`, `EVENTOS_CONCURRENCIA` (E-360: cuántos
listeners de eventos corren a la vez, default 4; cada uno puede tener dos conexiones, así que el doble
tiene que dejar margen dentro de `DB_POOL_MAX_SIZE`), `RENASIA_LIMITE_DIARIO`,
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

`JAVA_TOOL_OPTIONS` lleva `-XX:MaxRAMPercentage=60.0` porque una JVM en contenedor toma por defecto
~25% de la memoria del cgroup. Se pasa por variable de entorno y no por el `ENTRYPOINT` para que
`java` siga siendo el PID 1 en forma *exec*: así recibe el `SIGTERM` del orquestador y Spring apaga
ordenado, en vez de que un `sh -c` se coma la señal.

> **Corregido 2026-09-26 (V-8, D-180).** Decía `-XX:MaxRAMPercentage=75.0` y el `docker run` del CD
> no ponía tope de memoria. Sin tope, el cgroup del contenedor es la máquina entera: en la t3.small
> (1.909 MB, **sin swap**) eso daba **1,43 GB de heap** posible, con Redis, Docker y el agente de SSM
> repartiéndose el resto. Si el heap llegaba a crecer hasta ahí, el que moría por falta de memoria
> podía ser cualquier proceso de la máquina — incluido el agente de SSM, que es el único canal para
> mandar un arreglo (así se perdieron siete horas en E-155).
>
> Ahora el CD corre el contenedor con **`--memory 1400m`** y **`-e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60.0`**:
> heap máximo ≈ **840 MB**, y ~560 MB para metaspace, code cache, hilos y buffers directos (Lettuce,
> cliente de Gemini). Quedan ~500 MB de la máquina para Redis, Docker, SSM y el sistema. Si el
> backend se pasara del tope, el kernel mata **ese contenedor** y no otro proceso, y
> `--restart unless-stopped` lo vuelve a levantar solo (~45 s de caída, lo mismo que un despliegue).
> El `ENV` del `Dockerfile` también pasó a 60, pero el que manda en producción es el del `docker run`:
> se repite ahí a propósito, y también en la línea de vuelta atrás (`VOLVER`), porque la imagen
> anterior trae horneado 75 % y con el tope nuevo eso serían ~1 GB de heap sin aire para lo demás.
>
> **Qué mirar después de desplegar:** `docker stats backend` (uso contra el tope de 1,367 GiB) y
> `docker inspect -f '{{.State.OOMKilled}}' backend`. Si el heap se queda corto (GC muy seguido,
> `OutOfMemoryError` en el log), subir `MEMORIA` en `cd.yml` antes que el porcentaje, y nunca por
> encima de ~1600m en esta máquina.

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

> **Actualizada 2026-09-06.** Los puntos 2, 3 y 4 estaban abiertos y ya no lo están; se dejan
> tachados a la vista, con lo que quedó de cada uno, en vez de borrarlos.

| # | Qué | Quién |
|---|---|---|
| 1 | Organización y proyecto en SonarCloud + secret `SONAR_TOKEN`, y reemplazar los dos `TODO-` del `pom.xml` | Dueño |
| 2 | ~~Proveedor OIDC, rol de IAM, repositorio de ECR~~ **creados**. Lo que falta son **las cuatro variables de repositorio en GitHub** (§5.1 e): hoy no hay ninguna y por eso el `cd.yml` se saltea entero en cada push | **Dueño — es el único paso que falta para que la entrega continua funcione sola** |
| 3 | ~~**Destino de despliegue**: ECS Fargate / App Runner / EC2~~ **decidido: EC2 + Docker, desplegado por SSM** (§5.3). Queda abierto, para cuando el uso lo pida, pasar a dos instancias detrás de un balanceador para eliminar la caída de ~45 s por despliegue | Decisión de producto e infraestructura |
| 4 | ~~Dónde se hostea el Postgres propio~~ **resuelto: RDS** (`renaser-prod...rds.amazonaws.com`) | Ídem |
| 5 | Dominio real del frontend, para `RESET_PASSWORD_URL` / `ACTIVATE_ACCOUNT_URL` / `EMAIL_REMITENTE` | Dueño |
| 6 | Los ~42 parámetros de `/renaser/prod/` (§6.4) | Dueño, al desplegar |
| 7 | Logs estructurados en JSON para `prod` (`CLAUDE.md` §5.4.9). `application-prod.yaml` se creó solo con lo de Parameter Store; esa regla sigue sin implementarse | Pendiente, fuera del alcance de este cambio |
