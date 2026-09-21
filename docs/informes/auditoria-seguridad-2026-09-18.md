# Auditoría de seguridad — 2026-09-18

Pasada con el método de Cloudflare (`security-audit`): reconocimiento, cacería por unidades de
cobertura, y verificación adversarial de cada candidato antes de aceptarlo. **Source-first**: nada
se ejecutó contra el despliegue, nada se sondeó, ninguna credencial se tocó.

La regla que se aplicó para aceptar un hallazgo, y que descartó la mayoría de los candidatos:
hace falta **principal de menor confianza + entrada aceptada + control previsto + frontera cruzada
+ persona o recurso afectado + resultado concreto**. Una buena práctica ausente no es un hallazgo.
Algo que solo te afecta a ti mismo, tampoco.

Rama: trabajo mergeado a `master`. Pruebas al cierre: **3075 unitarias + 66 de integración, 0 fallos**.

---

## 1. Lo que se cerró

Cada uno con prueba de regresión que **falla contra el código viejo** (regla 03: si pasa con y sin
el arreglo, no es regresión, es decoración).

| | Qué era | Gravedad |
|---|---|---|
| **E-196** | El `token` de `/auth/password/reset-confirm` solo validaba `@NotBlank` y la clave Redis se armaba `"reset-password:" + token`. Bajo ese prefijo viven el OTP, su contador de intentos y los límites de tasa; `consumir()` usa GETDEL. Mandar `token: "intentos:<correo>"` **borraba el contador de intentos ajeno** → fuerza bruta ilimitada del código de 6 dígitos → contraseña nueva. Sin autenticar. | **Crítica** |
| **E-197** | `scope` (texto libre del cliente) caía en la primera sección del prompt de SISTEMA de Sparkie, por encima del bloque de crisis y de los límites clínicos. | Baja hoy, alta al activar el proveedor |
| **E-198** | `cerrarTodas()` no alcanzaba al WebSocket: el handshake leía la sesión una sola vez. Suspender a alguien conectado no le quitaba lo ya suscrito, y con token robado el acceso era indefinido y ampliable. | Alta |
| **E-199** | El SUBSCRIBE autorizaba un grupo contra la proyección `participantes_conversacion`, que "concede de más". Las otras tres guardas del módulo ya la rechazaban; esta quedó atrás y su javadoc afirmaba lo contrario. | Media |
| **E-200** | El `fullName` autoeditable del aprendiz se renderizaba sin cota ni saneo al principio del push de su mentor, como texto del sistema. | Baja |

Más, del mismo trabajo: rutas de almacenamiento atadas a su dueño en once endpoints, `@SchedulerLock`
en seis barridos, tope de paginación, SSRF de push cerrado, tres rutas sin cubrir por el filtro,
`management.*` fijado explícito, y dos javadoc que mentían corregidos.

---

## 2. Lo que falta, y de quién depende

### 2.1 Bloqueado en un hecho que solo tú puedes observar

**Estas tres no se pueden cerrar leyendo el repo.** La configuración del borde no está versionada:
cero archivos de nginx, Terraform o CloudFront rastreados por git. Todo lo que el código dice sobre
CloudFront son *afirmaciones en comentarios*, no artefactos verificables.

#### P-1 · ¿Los límites por IP son falsificables? — **lo más importante que queda**

`server.forward-headers-strategy: framework` hace que Spring tome el valor **más a la izquierda** de
`X-Forwarded-For` (verificado en las fuentes de spring-web 7.0.9, `ForwardedHeaderUtils`). El
comentario de `application.yaml` asume que ese primero lo pone CloudFront. Eso solo es cierto si
CloudFront **reemplaza** la cabecera; si la **añade** —que es su comportamiento documentado— y el
cliente manda la suya, el valor del atacante queda primero.

Si se confirma, los **ocho** puntos que limitan por IP (login, alta de cuenta, reset) no cuentan
nada: cualquier anónimo rota una cabecera y tiene intentos ilimitados. Además escribe una IP
arbitraria en `solicitudes_cuenta.ip_solicitud`.

> **La prueba, una sola petición:**
> ```
> curl -H 'X-Forwarded-For: 203.0.113.9' https://<tu-dominio>/api/v1/auth/login \
>      -H 'Content-Type: application/json' \
>      -d '{"email":"no-existe@ejemplo.invalid","contrasena":"x"}'
> ```
> Después mira qué IP registró el backend (`docker logs backend --tail 50`, o el contador por IP en
> Redis). **Si vio `203.0.113.9`, está confirmado.**
>
> **El arreglo, si se confirma:** tomar la IP contando desde la **derecha**. Con un solo salto de
> confianza delante, el último valor es el que agrega CloudFront y el cliente no puede escribir
> después de él. Va en `DireccionIpDelCliente`, que existe justamente para tener un único lugar
> donde tocarlo (E-151).

#### P-2 · ¿El security group realmente limita el 8080?

`application.yaml` lo afirma como fundamento de por qué es seguro confiar en `X-Forwarded-For`, pero
el `docker run` del CD publica `-p 8080:8080` **en todas las interfaces**, y las reglas de Docker se
insertan por delante de las del firewall del host. El security group es el único control que separa
el origen de internet.

```
aws ec2 describe-security-groups --group-ids <sg-...> \
    --query 'SecurityGroups[].IpPermissions' --region us-east-1
```
**Cómo leer la salida** — y acá hay que tener cuidado, porque la versión anterior de este
informe decía lo contrario y te habría hecho cerrar el hallazgo mal. Mira las reglas cuyo
`FromPort`/`ToPort` es 8080:

- Una regla `0.0.0.0/0` es exposición directa a internet. Obvio.
- Una regla con la prefix list `pl-3b927c52` (`com.amazonaws.global.cloudfront.origin-facing`)
  **también es exposición**, y es justo la que `auditoria-nfr-2026-09-06.md` §2.3 dejó marcada como
  el hallazgo: esa lista es de **todas** las distribuciones de CloudFront del mundo, no solo la
  nuestra. Cualquiera con una cuenta de AWS levanta su propia distribución apuntando a
  `52.0.210.237:8080` y se saltea la nuestra, con todo lo que le colguemos.
  Acotar el origen a esa lista **no** es el arreglo: es el punto de partida del problema.
- Lo correcto es que el 8080 solo lo alcance **nuestra** distribución. Como CloudFront no publica
  las IP de una distribución concreta, eso se consigue con la receta de D-117, en este orden
  (al revés rompe producción): primero la cabecera secreta de origen en CloudFront con el secreto
  en Parameter Store, después un filtro en el backend que la exija —hoy **no existe ninguno**, hay
  que escribirlo— exceptuando `/actuator/health`, que el CD consulta por `localhost`, y recién
  entonces desplegar.

Mientras estés ahí, mira también `CustomHeaders.Quantity` de la distribución:

```
aws cloudfront get-distribution-config --id E3O4M4W7JW3TJQ --region us-east-1
```

Si da `0`, no hay cabecera secreta y la primera mitad de la receta sigue sin hacerse.

#### P-3 · ¿Qué hay realmente en Parameter Store?

`application-prod.yaml` importa `/renaser/prod/` con binding relajado y **sin** `optional:`. Un
parámetro suelto puede ensanchar configuración sin tocar el repo. Ahora que `management.*` está
fijado explícito el riesgo bajó mucho, pero conviene mirar una vez:

```
aws ssm get-parameters-by-path --path /renaser/prod/ --recursive \
    --query 'Parameters[].Name' --output text --region us-east-1
```

### 2.2 Decisión tuya, no mía

- **`GlobalExceptionHandler` reenvía `getMessage()` crudo** de `IllegalArgumentException` (→400) y
  `IllegalStateException` (→409). Es el mecanismo por el que un valor de Redis terminaba impreso en
  una respuesta (ya cerrado en su origen). Cambiarlo a un texto fijo es lo correcto en seguridad,
  **pero hoy hay mensajes de validación de dominio que el usuario ve y entiende**: cambiarlo degrada
  la experiencia en producción. El camino limpio es migrar esos rechazos a las excepciones propias
  del dominio (ya hay trece en `shared/domain`) y recién ahí callar las dos genéricas.
- **Rotar las credenciales de `.idea/workspace.xml`** (AWS `AKIA…` de larga vida, clave de Google
  GenAI, secreto de cliente OAuth, contraseña SMTP). No las toqué ni las voy a tocar.
- **El token de sesión viaja en el query string de `/ws`** (`?token=...`) porque la API `WebSocket`
  del navegador no permite cabeceras propias. Es la credencial completa de la cuenta, con
  `spring.session.timeout: 30d`, y las URLs se registran en logs de acceso. Hoy ningún cliente abre
  `/ws`, así que no corre prisa; el arreglo estándar es un ticket de un solo uso y vida corta (30-60 s)
  canjeado en el handshake.

### 2.3 Superficie que quedó sin cazar

**La auditoría no está completa, y esto es lo que falta.** De las diez unidades de cobertura
planificadas, nueve recibieron una pasada. La que quedó sin cubrir en serio:

- **Lecturas por id en servicios de dominio** — el alcance por dueño en los ~90 controllers, guard
  por guard. Es la superficie más grande del repo y la que más se parece al hallazgo clásico de
  acceso a objeto ajeno. Hubo pasadas parciales (se encontró y cerró la ruta del Muro, la de chat,
  la de soporte), pero **no** un barrido sistemático de los noventa.

Y fuera del alcance que se había fijado:

- La **ingesta** de la base de conocimiento (`POST /admin/conocimiento`): se estableció quién puede
  escribir (`MANAGE_KNOWLEDGE_BASE`), no se auditó qué pasa con el material que se indexa.
- `EspejoSombraService` → `GenerarInsightSemanalPort`: las entradas de diario del aprendiz llegan a
  un modelo por un camino distinto al que sí se revisó.
- La opción `renaser.ia.busqueda-web` (default `false`): segunda fuente de contenido no confiable.
- Los dos schedulers que **mueven gente entre células** (`RotarMentores`, `TrasladarAprendices`)
  están apagados por propiedad y no se revisaron a fondo. Si algún entorno los enciende, hay que
  volver sobre ellos.

---

## 3. Dos bugs que no son de seguridad y aparecieron de paso

Verificados en fuente, los dos. No los arreglé —están fuera del alcance de una auditoría de
seguridad— pero cualquiera de los dos significa que algo del producto no está llegando.

### B-1 · Los recordatorios de calendario no le llegan a nadie

`DespacharRecordatoriosScheduler` corre **cada minuto**, marca los recordatorios como enviados
(`marcarEnviados`) y publica `RecordatorioEventoDebidoEvent`. **No existe ningún listener de ese
evento**, ni en `notifications` ni en ningún otro módulo — verificado: fuera de `calendar` solo
aparece citado en dos comentarios. Los recordatorios se consumen, se marcan como despachados, y
mueren ahí.

### B-2 · El `REQUIRES_NEW` de los avisos al mentor es inerte

`AvisosService.detectar()` llama `this.revisarGrupo(...)` directo (línea 80), y es `revisarGrupo`
quien lleva `@Transactional(propagation = REQUIRES_NEW)` (línea 90). Con proxies —no hay
`AdviceMode.ASPECTJ` en el repo— **la anotación no se aplica**: el javadoc "una transacción por
grupo, no una por barrido" no describe lo que pasa.

La consecuencia a comprobar es peor que la pérdida de aislamiento: los `AvisoDeAcompanamientoEvent`
quedarían publicados **sin transacción activa**, y `@ApplicationModuleListener` es un
`@TransactionalEventListener` en fase `AFTER_COMMIT` sin `fallbackExecution` — o sea que los
descarta en silencio. El contador de "publicados" subiría igual mientras el mentor no recibe nada.
Es el mismo síntoma que E-109 documenta para otro barrido. **Se confirma con una prueba de
integración**, no leyendo.

---

## 4. Endurecimiento anotado, no hecho

Ninguno es un hallazgo; los dejo escritos para que no se re-descubran.

- **WebSocket**: sin tope de suscripciones por sesión STOMP ni de conexiones por usuario; el
  handshake no comprueba `UserStatus.ACTIVE` (un suspendido con sesión viva abre el socket y figura
  como presente, aunque no pueda suscribirse a nada).
- **Schedulers**: `RecordatorioService.generar` es un `@Transactional` único sin `try/catch` por
  evento — un solo evento que lance deja sin recordatorios a todo el padrón, cada cinco minutos.
  Es la desviación más clara de la regla 02 §4.
- **Reloj**: cuatro schedulers (`ExpirarRegistros`, `PromoverCambiosHorario`, `VerdugoIgnorado`,
  `SnapshotRanking`) le pasan al dominio la fecha del **servidor**. Hoy es inofensivo porque todo el
  padrón es `America/Lima` y `participantes_programa.timezone` **no tiene ningún escritor** — pero la
  corrección depende de una coincidencia de husos, no de una invariante. El día que exista un
  participante al oeste de Lima, `ExpirarRegistros` le expira registros del día que está viviendo:
  exactamente E-91.
- **Push**: `ON CONFLICT (token) DO UPDATE SET usuario_id = EXCLUDED.usuario_id` reasigna el dueño
  por token sin prueba de posesión. Es la semántica querida para "mismo teléfono, otro usuario",
  pero quien conozca el token de otro lo reclama. Un log de la reasignación lo volvería detectable.
- **Prompts**: el techo del daño de E-197 es bajo **solo** porque `disponibles(COURSE_TUTOR)`
  devuelve `List.of()`. El día que alguien le dé una herramienta a Sparkie, aunque sea de lectura,
  la inyección en rol system pasa a poder dirigir invocaciones. Conviene una regla ejecutable que
  ate "agente con ámbito del cliente" a "cero herramientas".
- **Cuota de IA**: `ControlCuotaRedisAdapter.intentarConsumir` falla abierto cuando el script Lua
  devuelve `null`. Matiz importante: con Redis **caído** la petición falla cerrada (la excepción
  sube); el fail-open cubre solo el caso "Redis responde nil".

---

## 5. Lo que la auditoría confirmó que está bien

Vale decirlo, porque una auditoría que solo lista problemas miente por omisión:

- **No hay secretos literales en el repo.** Todo es `${VAR:default}`, `.env` está ignorado, los
  workflows usan OIDC sin claves. Los hits de patrón son la clave de ejemplo de la documentación de
  AWS y delimitadores PEM en código de parseo.
- **Actuator no expone nada más que `health`**, verificado contra el metadata del propio jar.
- **Por STOMP no se puede escribir**: no existe ningún `@MessageMapping` en el repo, y un SEND a
  `/topic/` se rechaza antes del broker.
- **El agente de IA no puede actuar sobre datos ajenos**: el `actorId` entra por constructor desde
  la identidad de la conversación, no hay ninguna propiedad de usuario en el esquema que el modelo
  ve, y el guard vive al fondo (`RegistroService.requireSelf`). El fallo de herramienta tampoco es
  un oráculo: todos los errores colapsan al mismo texto.
- **La purga de cuentas no es disparable por un tercero**, el reloj del programa no es falsificable,
  ningún scheduler arma SQL nativo con valor de usuario, y ningún listener toma el destinatario de
  un payload escrito por el usuario. Las cinco hipótesis más graves quedaron refutadas con el guard
  concreto que las desmiente.
