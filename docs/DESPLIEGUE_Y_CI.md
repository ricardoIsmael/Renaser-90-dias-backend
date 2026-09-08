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
| Contenedores | `redis` (`redis:7-alpine`, sin puertos publicados) y `backend` (la imagen de ECR, `-p 8080:8080`), los dos en la red de Docker `renaser` |
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
3. Dentro de la instancia: `docker login` contra ECR → `docker pull` de **la etiqueta del commit**
   → `docker rm -f backend` → `docker run` con la red `renaser`, `-p 8080:8080`,
   `--restart unless-stopped`, `SPRING_PROFILES_ACTIVE=prod` y `AWS_REGION=us-east-1`.
4. Consulta `http://localhost:8080/actuator/health` cada 3 s hasta que diga `"status":"UP"`, con un
   tope de 240 s (`DESPLIEGUE_ESPERA_SEGUNDOS`). **Medido: la aplicación tarda 43 s en responder
   `UP`**, así que el tope tiene más de 5× de margen para una migración larga o una RDS fría.
5. El runner espera el `Status` de la invocación y **falla el workflow si no es `Success`**.

**Se despliega la etiqueta del SHA, nunca `latest`.** `latest` no permite saber qué versión está
corriendo ni a cuál volver. Con la etiqueta del commit, `docker ps` responde las dos preguntas.

#### Las tres cosas que hay que tener presentes

**1. Hay unos segundos de caída en cada despliegue, y es inevitable hoy.** Entre el `docker rm -f`
y el momento en que la aplicación responde `UP` pasan ~45 s en los que la API no contesta: unos
pocos segundos de conexión rechazada, y el resto con el proceso arrancando. CloudFront no tiene a
dónde mandar el tráfico mientras tanto, así que el aprendiz ve errores. **No se disimula porque no
se puede arreglar sin cambiar la topología:** hacerlo sin caída pide dos instancias detrás de un
balanceador (o dos contenedores en puertos distintos y un proxy que cambie de destino), y eso es
una decisión de infraestructura y de costo que nadie tomó. Mientras siga habiendo una sola
instancia, conviene desplegar en horario de poco uso.

**2. No se vuelve solo a la versión anterior, y es a propósito.** Si la aplicación no levanta, el
workflow falla y deja escrito en la salida el comando exacto para restaurar la imagen anterior —
pero no lo ejecuta. El motivo es Flyway: las migraciones corren al arrancar y no se deshacen, así
que si el arranque falló *después* de migrar, devolver el binario viejo lo deja contra un esquema
más nuevo, que es peor que el problema original. Automatizar el retroceso exige antes decidir qué
hacer con el esquema, y eso no está decidido.

**3. El disco son 8 GB y cada versión de la imagen ocupa ~440 MB.** El script borra las imágenes
colgadas (`docker image prune -f`), pero **no** las etiquetadas — justamente porque la anterior es
la que sirve para volver atrás. Con ~5,2 GB libres eso da lugar para unas diez versiones antes de
que el disco sea el problema, así que el script avisa en la salida cuando quedan menos de 3 GB.
Limpiar a mano:

```bash
aws ssm send-command --profile renaser --region us-east-1 \
  --instance-ids i-0ea00f555c5fe8028 --document-name AWS-RunShellScript \
  --parameters 'commands=["docker images renaser-backend --format {{.ID}} | tail -n +3 | xargs -r docker rmi"]'
```

#### El health check depende de que `/actuator/health` siga siendo público

Sigue vigente lo que ya decía esta sección, y ahora con más peso, porque el despliegue **depende**
de esa URL: `/actuator/health` responde sin autenticación **por omisión**, no por decisión.
`SecurityConfig` todavía no tiene `anyRequest().authenticated()` —está anotado como pendiente en el
propio archivo— y lo que no coincide con ningún `requestMatchers` queda permitido. El día que se
cierre esa regla, el health check empieza a recibir 401, **y todos los despliegues van a fallar
aunque la aplicación esté perfecta**. Al agregar `anyRequest().authenticated()` hay que dejar
`/actuator/health` explícitamente permitido en el mismo cambio.

#### Por qué EC2 y no ECS Fargate o App Runner

La comparación que estaba acá sigue siendo válida como registro de la decisión:

| Opción | Qué hay que crear | A favor | En contra |
|---|---|---|---|
| **EC2 + Docker** *(elegida)* | Instancia, Docker, Elastic IP | Lo más barato y lo más simple de entender | Despliegue y ciclo de vida a mano; una sola instancia = caída en cada despliegue |
| **ECS Fargate** | Cluster, task definition, service, ALB, target group, security groups, rol de tarea | Control fino, escalado horizontal, despliegue sin caída, es lo que espera §5.2.1 de `CLAUDE.md` (varias instancias) | La más infraestructura para levantar |
| **App Runner** | Un servicio apuntando a la imagen de ECR | Lo más rápido de poner en pie; HTTPS y escalado incluidos | Menos control de red; el escalado a cero castiga el arranque de una JVM |

**El día que haya más de una instancia**, este job deja de alcanzar: hay que desplegar de a una y
sacarla del balanceador antes. Y ahí entran también las dos piezas que `CLAUDE.md` §5.2.1 ya
anticipa (Redis Pub/Sub para el chat y para invalidar la caché de rol entre instancias). Migrar a
ECS es el camino natural, y la imagen ya está lista para eso: es multi-arquitectura, corre como
usuario sin privilegios, y toma su configuración de Parameter Store (§6) en vez de variables
cableadas.

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
