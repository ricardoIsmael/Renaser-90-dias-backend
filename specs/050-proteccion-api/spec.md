# Feature Specification: Protección de la API — límites por capa, borde y origen cerrados, sesión acotada, permisos estrictos y despliegue sin corte

**Feature Branch**: `050-proteccion-api`
**Created**: 2026-11-11
**Status**: Borrador para aprobar. Las decisiones D1–D14 son del dueño (2026-11-11); lo que sigue abierto está en «Preguntas abiertas», cada una con su opción recomendada. Esta spec **no se implementa en esta rama**: solo la describe, el plan la reparte y las tareas la parten en tramos.
**Input**: revisión de la protección de la API hecha el 2026-11-11 (página «Límites de la API Renaser»: lectura del código en `master`, la auditoría de seguridad de septiembre contrastada con el código de hoy y la configuración real de AWS) y las respuestas del dueño: opciones 1A, 2A, 3A, 4A, 5A, 6A y 7A, y «lo demás, lo recomendado».

> **Repositorio público.** Esta spec describe controles y requisitos. No detalla cómo se abusa de ningún hueco actual; los valores secretos (cabecera de origen, contraseña de Redis, cuenta del CRM) no viven en el repositorio, entran por Parameter Store.

## Decisiones del dueño (2026-11-11)

| # | Tema | Decisión |
|---|---|---|
| D1 | IP del cliente | Se resuelve **en un solo lugar**, a partir de lo que pone el borde. Es un cambio aparte (rama `ip-real-del-cliente`), no parte de esta spec. Esta spec lo da por hecho: lo que cuenta pedidos por IP en el origen (D9) no se enciende antes de que ese cambio esté en producción. |
| D2 | WAF | **AWS WAF en CloudFront** (opción 2A): regla por tasa por IP de **2.000 pedidos / 5 min** en toda la API y **300 / 5 min** en `/api/v1/auth/*`, más los grupos administrados de AWS «Common», «Known bad inputs» e «IP reputation». Costo aproximado: US$ 5–10 al mes. |
| D3 | Límite por cuenta | **Un filtro en Spring** que reusa el contador de Redis de los límites existentes, con un tope por grupo de endpoints (tabla de FR-013). Respuesta **429** con `Retry-After` y `codigo: DEMASIADOS_PEDIDOS`. **Las dos primeras semanas solo registra** (interruptor por grupo): se mide y recién después se bloquea (opción 3A). |
| D4 | CRM | **Cuenta de servicio de solo lectura** con rol y permisos propios: solo `GET /admin/pagos/cuotas`, `GET /admin/trainees` y lo que el CRM necesite de verdad; su propio token y **60 pedidos por minuto** (opción 4A). Hoy el CRM usa una cuenta de Alquimista. |
| D5 | Matriz de permisos | **Estricta para MENTOR y TRAINEE_GUIDE** (opción 5A): lo no permitido se niega por defecto en `UserRole.can`, con permisos explícitos; los guards de cada servicio se quedan como defensa en profundidad; E2E por rol. |
| D6 | Sesión | **30 días sin uso con un tope absoluto de 90 días** desde que se inició (opción 6A): al día 90 se vuelve a entrar. |
| D7 | Redis | **Contraseña en producción** y el puerto sigue sin publicarse (opción 7A). |
| D8 | Origen | Cabecera secreta **CloudFront → origen**, verificada por nginx (recomendado). |
| D9 | nginx | Tope general por IP real del cliente: **20 pedidos por segundo con ráfaga de 40** (recomendado). |
| D10 | Datos masivos | Exportaciones CSV en **streaming** y Caja sin armar todo en memoria; tope en Personas del programa y en la consola de Soporte TI (recomendado). |
| D11 | Despliegue | **Sin corte**: levantar el contenedor nuevo, comprobar que responde, cambiar el tráfico de nginx y recién ahí apagar el viejo (recomendado). |
| D12 | Quién corre qué | Todo cambio de infraestructura (WAF, cabecera de CloudFront, contraseña de Redis, despliegue) viene como **script versionado** en `infra/` o `scripts/despliegue/` que **corre el dueño**, cada uno con **simulacro (`--simular`) por omisión y vuelta atrás**. |
| D13 | Entrega | Tramos fusionables, cada uno con su rama y su PR, en este orden: (a) Redis + cabecera de origen + nginx, (b) WAF, (c) filtro por cuenta en modo «solo registrar», (d) cuenta del CRM, (e) tope de sesión, (f) exportaciones, (g) despliegue sin corte, (h) matriz estricta. |
| D14 | Lo que no cambia | Los límites que ya existen (login, redes sociales, restablecer contraseña, verificación de correo, solicitudes de cuenta, códigos por WhatsApp, eliminación de cuenta, SER 25 por día, voz en vivo 15 min) **se mantienen tal cual**. |

## Lo que hay hoy (leído el 2026-11-11, `master` `e45b0595`)

- **Capas**: CloudFront (cabeceras de seguridad administradas; sin WAF) → una instancia EC2 con `proxy` (nginx, transparente: solo reenvía) → `backend` (Spring, solo en la red interna de Docker) y `redis` (red interna del host, puerto sin publicar). El security group acepta el 8080 solo de la lista de IP de CloudFront y de una IP fija de operación.
- **Límites que existen** (D14): un contador en Redis con script Lua (`LimitarSolicitudesResetPort`, ventana fija, clave `reset-password:rl:<clave>`) que usan los doce puntos de autenticación y alta; la cuota diaria de SER; el tope de voz en vivo. **No hay un límite general** por cuenta ni por IP en la API.
- **Seguridad en la cadena**: todo `/api/v1` con controlador exige sesión salvo los pocos públicos enumerados en `SecurityConfig`. Después de Spring Security corre `FiltroDeSoloLectura` (orden −90, «ver como») y, ya en Spring MVC, `PermissionEnforcementInterceptor` (cuenta suspendida para todo rol; matriz estricta para TRAINEE; MENTOR_LEAD en **modo sombra**: registra lo que denegaría, `renaser.security.mentor-lead-enforcement=false`).
- **Matriz de permisos**: `UserRole.can` es estricta para TRAINEE y MENTOR_LEAD; para **MENTOR y TRAINEE_GUIDE** devuelve «sí» a todo salvo `EXPORT_RANKING` (deuda **A-1**, falla-abierto). La barrera real de los endpoints de Administración son los guards dentro de cada servicio.
- **Sesión**: Spring Session sobre Redis, id opaco en `X-Auth-Token`, `spring.session.timeout: 30d` de inactividad y cada pedido la renueva: **no hay tope absoluto**.
- **Despliegue**: `scripts/despliegue/desplegar-backend.sh` ya sabe desplegar **sin corte** (E-465, D-235) y elige el modo mirando la memoria disponible. El 2026-11-11 eligió «reemplazo» (~50–70 s de 502 desde el borde): había **1.890 MB disponibles y la condición pide 1.912** (tope 1.400 + margen 512), faltaron 22 MB aun parando Alloy. O sea: el mecanismo existe; lo que falta es memoria para usarlo, no código nuevo.
- **Datos masivos**: las listas de `admin` tienen tope (200); el CSV de Caja y el de Diagnóstico exportan todo de una vez y Caja arma todo el padrón en memoria para contar estados; el tope de Personas del programa y de la consola de Soporte TI no está comprobado.
- **El cliente**: la app móvil ya reintenta un 429 en su cola sin red (`colaSinRed.ts`, spec 048) pero no usa `Retry-After` ni muestra un mensaje propio de «demasiados pedidos».

## User Scenarios & Testing

### Historia 1 — Un robot o un cliente descontrolado no llega a la instancia (P1, tramos A y B)

La API recibe más pedidos de los que una persona hace. El WAF y nginx cortan el exceso **antes** de que llegue a Spring.

**Acceptance**:
1. **Dado** una IP que supera 2.000 pedidos en 5 minutos (o 300 en `/api/v1/auth/*`), **cuando** el WAF está en `Block`, **entonces** recibe 429 con `Retry-After` y `codigo: DEMASIADOS_PEDIDOS`, y el pedido no llega a la instancia.
2. **Dado** el WAF en `Count` (primera etapa), **cuando** una IP supera el umbral, **entonces** el pedido pasa y queda contado en las métricas de la regla.
3. **Dado** un pedido que no trae la cabecera de origen vigente, **cuando** llega a nginx desde fuera del host, **entonces** recibe 403 con `codigo: ORIGEN_NO_PERMITIDO`; el health del despliegue (desde el propio host) sigue respondiendo.
4. **Dado** una IP (según D1) que pasa de 20 pedidos por segundo sostenidos con ráfaga de 40, **cuando** nginx está en modo de aplicación, **entonces** recibe 429; en modo de simulacro solo queda anotado.
5. **Dado** una persona común usando la app (abre Hoy, marca hábitos, publica), **cuando** pasa todo el día, **entonces** nunca ve un 429 ni un 403 de esta capa.

### Historia 2 — Una cuenta con sesión no puede inundar el Muro, el chat ni las subidas (P1, tramo C)

Cada grupo de endpoints tiene su tope por cuenta. Durante dos semanas el filtro solo registra; después se aplica grupo por grupo.

**Acceptance**:
1. **Dado** una cuenta que hace 11 publicaciones en un minuto, **cuando** el grupo «Muro» está en `APLICAR`, **entonces** la undécima recibe 429 con `Retry-After`; en `REGISTRAR` pasa y queda anotada como «habría bloqueado».
2. **Dado** una cuenta que pide 21 URLs de subida en un minuto, **cuando** el grupo «Subidas» está en `APLICAR`, **entonces** la 21ª recibe 429.
3. **Dado** un pedido que ya tiene su límite propio (login, SER 25 por día), **cuando** pasa por el filtro, **entonces** no se cuenta dos veces y su límite no cambia.
4. **Dado** que Redis no responde, **cuando** llega un pedido, **entonces** el filtro lo deja pasar y lo anota; nunca responde 500 por esto.
5. **Dado** que la app tiene una cola de envíos pendientes (spec 048), **cuando** recibe 429, **entonces** espera lo que dice `Retry-After` y reintenta; no pierde lo que la persona hizo.

### Historia 3 — Redis y el origen no aceptan a cualquiera (P1, tramo A)

**Acceptance**:
1. **Dado** Redis con contraseña, **cuando** otro contenedor de la red pide `PING` sin autenticarse, **entonces** recibe `NOAUTH`; el backend arranca y atiende con la contraseña de Parameter Store.
2. **Dado** el cambio a la contraseña, **cuando** termina, **entonces** las sesiones vigentes siguen vivas (nadie vuelve a iniciar sesión por esto).
3. **Dado** el script de la cabecera de origen, **cuando** se corre con `--simular`, **entonces** muestra qué cambiaría en CloudFront y en nginx sin tocar nada, y deja el comando para volver atrás.

### Historia 4 — El CRM lee con una cuenta que solo lee (P2, tramo D)

**Acceptance**:
1. **Dado** la cuenta de servicio del CRM, **cuando** pide `GET /admin/pagos/cuotas` o `GET /admin/trainees`, **entonces** recibe 200 con lo de siempre.
2. **Dado** esa cuenta, **cuando** hace cualquier otro pedido (otra ruta o cualquier verbo que escriba), **entonces** recibe 403 con `codigo: FUERA_DE_ALCANCE_DE_LA_INTEGRACION`, sin llegar al caso de uso.
3. **Dado** más de 60 pedidos por minuto, **cuando** llega el siguiente, **entonces** recibe 429.
4. **Dado** que la cuenta se suspende, **cuando** el CRM vuelve a pedir, **entonces** recibe 403 de cuenta suspendida y su sesión se cierra (comportamiento de siempre).
5. **Dado** el padrón, el ranking, «Equipo y roles», los destinatarios de avisos y los chats, **cuando** se listan, **entonces** la cuenta del CRM no aparece en ninguno.

### Historia 5 — Una sesión no dura para siempre (P2, tramo E)

**Acceptance**:
1. **Dado** una sesión iniciada hace más de 90 días que se usó ayer, **cuando** hace un pedido, **entonces** recibe 401 con `codigo: SESION_VENCIDA` y la sesión se invalida.
2. **Dado** una sesión de 20 días sin uso, **cuando** pide algo, **entonces** sigue vigente (30 días de inactividad, como hoy).
3. **Dado** que la app tenía envíos pendientes cuando recibió el 401, **cuando** la persona vuelve a entrar con la misma cuenta, **entonces** la cola se envía (spec 048, D15).

### Historia 6 — Una exportación o una lista grande no tumba el servidor (P2, tramo F)

**Acceptance**:
1. **Dado** el CSV de Caja o de Diagnóstico con decenas de miles de filas, **cuando** el ADMIN lo descarga, **entonces** llega completo, con el mismo contenido y orden de siempre, y el servidor no sube su memoria en proporción a las filas.
2. **Dado** `size=100000` en cualquier lista, **cuando** se pide, **entonces** se acota al máximo del endpoint (o responde 400 con motivo, como `GET /admin/pagos/cuotas`).
3. **Dado** una sexta exportación en un minuto, **cuando** llega, **entonces** recibe 429 (grupo «Exportaciones»).

### Historia 7 — Desplegar no corta el servicio (P2, tramo G)

**Acceptance**:
1. **Dado** un despliegue normal, **cuando** una sonda pide `/salud` por el borde cada segundo durante todo el cambio, **entonces** no ve ninguna respuesta distinta de 200.
2. **Dado** que el contenedor nuevo no llega a «UP», **cuando** el despliegue falla, **entonces** el viejo sigue atendiendo y el script lo dice.
3. **Dado** que no hay memoria para dos JVM, **cuando** el script elige el modo, **entonces** imprime la tabla de memoria con su cuenta y, si va con corte, nginx responde 503 con `Retry-After` en vez de 502.

### Historia 8 — Mentor y Guía hacen solo lo que su rol permite (P3, tramo H)

**Acceptance**:
1. **Dado** una cuenta MENTOR o TRAINEE_GUIDE, **cuando** llama a un endpoint cuyo permiso no está en la matriz aprobada, **entonces** recibe 403 aunque el servicio olvidara su guard.
2. **Dado** esa misma cuenta, **cuando** usa todo lo que su rol hace hoy (grupos, aprendices, tickets, evidencias, cursos, eventos, su propio programa), **entonces** sigue funcionando: ningún 403 nuevo en un flujo legítimo.
3. **Dado** el modo sombra encendido, **cuando** un endpoint se habría negado, **entonces** queda en el registro con método, ruta y permiso (sin la identidad de la cuenta), para corregir la matriz antes de aplicarla.

## Requirements

### Borde: WAF (D2)

- **FR-001** Debe existir un Web ACL de alcance CloudFront, definido en una **plantilla versionada** (`infra/waf/`), con tres grupos administrados (`AWSManagedRulesCommonRuleSet`, `AWSManagedRulesKnownBadInputsRuleSet`, `AWSManagedRulesAmazonIpReputationList`) y dos reglas por tasa por IP: **2.000 pedidos / 5 min** en toda la API y **300 / 5 min** en las rutas que empiezan por `/api/v1/auth/`.
- **FR-002** Toda regla MUST nacer en `Count` y pasar a `Block` una por una, por decisión del dueño, después de revisar sus métricas. Las reglas por tasa, al bloquear, MUST responder **429** con `Retry-After` y el cuerpo JSON `{"message":"Espera un momento y vuelve a intentarlo.","codigo":"DEMASIADOS_PEDIDOS"}`.
- **FR-003** Un falso positivo de un grupo administrado con tráfico legítimo de la app MUST resolverse **excluyendo esa regla por nombre** (queda en la plantilla con su motivo y su fecha), nunca apagando el grupo entero.
- **FR-004** El registro del WAF MUST censurar `x-auth-token`, `authorization` y `cookie`, y no guardar el cuerpo de los pedidos.
- **FR-005** El Web ACL MUST quedar asociado a la distribución de CloudFront por un script con simulacro y vuelta atrás. [sin prueba automática: el recurso solo existe en la cuenta de AWS del dueño; la plantilla y los scripts se prueban en `src/test` (FR-001 a FR-004, FR-011) y el recurso se verifica con la salida del propio script y con la distribución de ensayo de la tarea T022]

### Origen: cabecera, nginx y Redis (D7, D8, D9)

- **FR-006** CloudFront MUST agregar a cada pedido hacia el origen una cabecera con un secreto, y nginx MUST responder **403** con `codigo: ORIGEN_NO_PERMITIDO` a todo pedido que no la traiga con el valor vigente o con el anterior, **salvo** los que vienen del propio host (loopback: es lo que usa el CD para comprobar la salud) y de las IP de operación configuradas en Parameter Store (no en el repositorio).
- **FR-007** La rotación del secreto MUST hacerse **sin corte**: nginx acepta el vigente y el anterior, y el script de rotación aplica los pasos en el orden que lo garantiza (nginx acepta los dos → CloudFront cambia → se espera a que propague → nginx deja de aceptar el viejo).
- **FR-008** La comprobación del origen MUST tener un modo previo de solo anotar (pedidos sin cabecera quedan en el log de nginx, no se rechazan) que se usa mientras la cabecera propaga y se valida.
- **FR-009** nginx MUST limitar por IP del cliente (la de D1, definida en un solo `map`) a **20 pedidos por segundo con ráfaga de 40** y responder **429** con `Retry-After` y el cuerpo de FR-002. Primero con `limit_req_dry_run on` (anota, no rechaza) durante la medición; el loopback queda exento.
- **FR-010** Redis MUST exigir contraseña en producción (valor `SecureString` en Parameter Store, `/renaser/prod/REDIS_PASSWORD`), el puerto MUST seguir sin publicarse, las sesiones vigentes MUST conservarse y el backend MUST arrancar con ella sin cambios de código (las propiedades `spring.data.redis.password` y `username` ya existen).
- **FR-011** Todo script de infraestructura de esta spec MUST: correr en **simulacro** salvo que se le pase `--aplicar`; declarar el **perfil de AWS** (`prod`) en vez de depender del que haya por defecto; ser **idempotente**; guardar lo anterior en un archivo para **volver atrás** y mostrar el comando; no imprimir ningún secreto ni dejarlo en el repositorio ni en el log del CD; fallar con `set -euo pipefail`.

### Límite por cuenta en Spring (D3, D14)

- **FR-012** Un único filtro MUST contar los pedidos **por cuenta y por grupo** en Redis (ventana fija, script Lua atómico con el mismo patrón de TTL del contador existente: el TTL se fija solo mientras la clave no tenga uno). Cuando un grupo tiene dos ventanas (minuto y hora) un pedido rechazado por una MUST NOT consumir la otra.
- **FR-013** Los grupos y sus topes de partida (se ajustan con la medición):

  | Grupo | Qué cubre | Tope por cuenta |
  |---|---|---|
  | `lectura` | `GET` de la API (hábitos, plan, Muro, perfil…) | 300 / min |
  | `escritura` | `POST`/`PUT`/`PATCH`/`DELETE` de hábitos, plan, radar, diario… | 60 / min |
  | `muro` | publicar y comentar en el Muro | 10 / min · 60 / hora |
  | `chat` | enviar mensajes del chat | 30 / min |
  | `subidas` | toda URL firmada de subida (`…/upload-url`, `…/presign`) | 20 / min · 300 / día |
  | `ia-fuera-de-ser` | generaciones con IA que no son SER: Análisis 80/20, Espejo, Mapa de Renacimiento | 5 / min, más la cuota diaria compartida con SER (FR-018) |
  | `ser` | `POST /renasia` | 10 / min, además de los 25 por día de hoy |
  | `crm` | la cuenta de servicio del CRM | 60 / min (FR-025) |
  | `exportaciones` | los `…/export.csv` y `…/exportar.csv` | 5 / min |
  | `admin` | el resto de `/api/v1/admin/**` | 120 / min |
  | `webhook-whatsapp` | `POST /webhooks/whatsapp` (firma + límite por origen, no por cuenta) | 600 / min |

  Quedan fuera del filtro, a propósito y por escrito: las rutas públicas de autenticación y alta (tienen sus límites propios, D14), el WebSocket (`/ws`: la voz en vivo ya tiene su tope) y la salud.
- **FR-014** Todo pedido de `/api/v1` MUST caer en **exactamente un grupo** o en la lista de exclusiones; una ruta nueva sin clasificar MUST romper la build (prueba que recorre los controllers, como `RutasCubiertasPorElFiltroTest`).
- **FR-015** El rechazo MUST ser **HTTP 429** con la cabecera `Retry-After` (segundos enteros, ≥ 1, lo que falta para que venza la ventana que se pasó) y el cuerpo `{"message":"Espera un momento y vuelve a intentarlo.","timestamp":"…","codigo":"DEMASIADOS_PEDIDOS"}` (el formato de error de siempre más `codigo`), con las cabeceras de CORS que puso Spring Security. No MUST revelar el nombre del grupo ni el tope.
- **FR-016** Cada grupo MUST tener un modo propio: `DESACTIVADO`, `REGISTRAR` o `APLICAR`. El modo de partida de **todos** es `REGISTRAR`: cuenta, anota «habría bloqueado» y **nunca** responde 429. El modo se cambia **sin reiniciar** (Q6).
- **FR-017** Si Redis falla o tarda más de su tope, el filtro MUST dejar pasar el pedido y anotarlo (contador y registro); MUST NOT responder 500 por esta causa. *Decisión técnica derivada: el límite protege, no es fuente de verdad de negocio, y la sesión también vive en Redis, así que con Redis caído la API ya no atiende de todos modos.*
- **FR-018** `ia-fuera-de-ser` MUST descontar de la misma cuota diaria de SER (la de `ControlCuotaRenasiaPort`, 25 por día), cada generación con el peso que decida Q3, a través de la API pública del módulo `rag`; el filtro por minuto no la reemplaza.
- **FR-019** El filtro MUST publicar métricas por grupo (`renaser_limite_pedidos_total{grupo,resultado}` con `permitido`, `habria_bloqueado`, `bloqueado`, `error_de_redis`; y la distribución de uso por ventana) y una línea de registro por «habría bloqueado» y por bloqueo **con método, patrón de ruta (sin ids) y grupo**, para decidir los topes con datos y detectar abuso.
- **FR-020** La app MUST tratar un 429 como **reintentable**: respeta `Retry-After`, muestra el mensaje del servidor si la persona está mirando la pantalla y **nunca** descarta lo que estaba en su cola de envíos (spec 048, D14). Una versión vieja de la app que no lo entiende ve el `message` como cualquier error.
- **FR-021** Una cuenta en «ver como» (solo lectura) MUST seguir recibiendo su 403 de siempre **antes** de contarse (el filtro corre después de `FiltroDeSoloLectura`).

### Cuenta de servicio del CRM (D4)

- **FR-022** Debe existir un rol nuevo **`CRM_READER`** (`INTEGRACION_CRM` en la base, valor nuevo del tipo `rol_usuario` en su propia migración, como `SOPORTE_TI` en V100) que **solo se asigna desde la base** con un script del dueño (patrón de `scripts/soporte-ti/`): ningún endpoint lo da, lo quita ni lo invita. Suspender la cuenta sí se puede y es su interruptor de emergencia: corta su sesión de inmediato, como con cualquier cuenta.
- **FR-023** El rol MUST tener un **alcance cerrado de `GET`** (lo de D4 más lo que confirme Q2); todo otro método o ruta MUST responder **403** `FUERA_DE_ALCANCE_DE_LA_INTEGRACION`, **por defecto** (lista de permitidos, no de prohibidos), antes de llegar al caso de uso. Una prueba MUST recorrer **todos** los handlers y comprobar que solo los de la lista responden distinto de 403.
- **FR-024** `UserRole.can` MUST devolver «sí» para ese rol solo a los permisos que esas lecturas necesitan, y los guards de los dos servicios (`CuotasDeTodosService`, el listado de `TraineeAdminService`) MUST aceptarlo **únicamente en la lectura**, nunca en las escrituras que comparten permiso (`DELETE /admin/pagos/cuotas/{id}`, `PUT /admin/trainees/{id}/program-day`).
- **FR-025** La cuenta MUST tener su propio tope de **60 / min** (grupo `crm`), contado por cuenta.
- **FR-026** La cuenta MUST quedar fuera de todo listado de personas (padrón, ranking, «Equipo y roles», destinatarios de avisos, candidatos a un grupo, chats) y no poder activar un programa. Toda consulta que recorre `UserRole.values()` MUST excluirla explícitamente.
- **FR-027** Cada pedido de la cuenta MUST dejar una línea de registro (ruta, estado, cantidad de filas) **sin datos personales**, y la contraseña MUST poder rotarse con el flujo de restablecer de siempre.

### Sesión (D6)

- **FR-028** Una sesión MUST invalidarse y responder **401 `SESION_VENCIDA`** cuando hayan pasado más de **90 días desde que se creó**, aunque se haya usado ayer. La propiedad (`renaser.sesion.tope-absoluto`, 90 días de partida) MUST poder ponerse en vacío para volver atrás sin código.
- **FR-029** Los 30 días de inactividad (`spring.session.timeout`) MUST seguir igual, y «ver como» (30 minutos) no cambia.
- **FR-030** El 401 MUST dejar a la app en el login **conservando su cola de envíos** (spec 048, D15). La nota de D1 de la spec 048 («hoy, 30 días sin uso») se corrige en el mismo cambio diciendo qué decía y por qué.
- **FR-031** El tope MUST medirse con el `Clock` del puerto (no `Instant.now()`), y su prueba MUST incluir un caso con el reloj entre las 00:00 y las 05:00 UTC.

### Datos masivos (D10)

- **FR-032** Los CSV de Caja y de Diagnóstico MUST escribirse **en streaming por lotes** (memoria acotada al tamaño del lote) con el **mismo contenido, columnas y orden** de hoy; el guard de ADMIN se comprueba **antes** de empezar a escribir.
- **FR-033** (según Q4) El listado de Caja MUST dejar de armar el padrón completo más de lo necesario para contar estados, o, si el estado derivado no puede expresarse en la base, MUST llevar un **tope documentado** y una medición de su costo.
- **FR-034** Todo parámetro de tamaño de página (`size`, `limit`, `tamano`) de un controller MUST acotarse en **un solo lugar** (un valor `PaginaAcotada` con mínimo y máximo) — incluidos Personas del programa y la consola de Soporte TI — y una **regla de código** MUST fallar si un controller nuevo recibe uno sin acotarlo (la corrección se repite → se vuelve prueba).

### Despliegue (D11)

- **FR-035** Un despliegue normal MUST pasar **sin ninguna respuesta distinta de 200 en `/salud` visto desde el borde** durante todo el cambio (medido con una sonda de un pedido por segundo). Un despliegue con una migración que rompe al código viejo sigue pudiendo pedir `MODO=reemplazo` a propósito. [sin prueba automática: el corte solo se mide en la instancia real; la sonda y los scripts se prueban estáticamente y en local, y el resultado de tres despliegues queda en el PR del tramo G]
- **FR-036** La elección del modo MUST seguir mirando la memoria, pero un **informe de solo lectura** (`scripts/despliegue/memoria-de-la-instancia.sh`) MUST dar la medición real (RSS de cada contenedor, disponible antes y durante el solape) para decidir con datos cuánto margen hace falta (Q5). La condición de memoria **no se baja sin esa medición** (E-155).
- **FR-037** El cambio de tráfico MUST esperar a que el contenedor nuevo responda «UP» **y** a que la ruta por nginx lo alcance; si no, el viejo sigue atendiendo (ya es así: se conserva y se prueba).
- **FR-038** En el modo con corte (cuando se use), nginx MUST responder **503 JSON con `Retry-After`** en vez del 502 desnudo, para que la app lo trate como «sin servicio, reintenta» y no como un error.
- **FR-039** El comando para volver a la versión anterior MUST seguir impreso en cada despliegue y probado en local.

### Matriz estricta de MENTOR y TRAINEE_GUIDE (D5)

- **FR-040** `UserRole.can` MUST negar por defecto a MENTOR y TRAINEE_GUIDE y listar **explícitamente** sus permisos (un solo conjunto compartido, porque D-265 los define como lo mismo: `actuaComoMentor()`), derivado del inventario de endpoints y **aprobado por el dueño** antes de aplicarse (regla 00: los permisos por rol no se inventan).
- **FR-041** `PermissionEnforcementInterceptor` MUST aplicar la matriz a estos dos roles con el mismo mecanismo que MENTOR_LEAD: **modo sombra** primero (`renaser.security.mentor-enforcement=false`: registra lo que denegaría), y a `true` cuando el registro esté limpio en uso real; volver atrás es cambiar la propiedad.
- **FR-042** Los guards de cada servicio MUST **quedarse** (segunda línea de defensa para `@Scheduled` y listeners); ninguno se borra por esta spec.
- **FR-043** Una prueba MUST recorrer todos los handlers y comprobar, **por cada rol**, que lo no aprobado responde 403 y que lo aprobado no (misma técnica que `CuentaSuspendidaEnTodoEndpointTest`); y MUST existir un E2E por rol (Mentor y Guía) contra el backend local con las pantallas de acompañamiento.
- **FR-044** `EXPORT_RANKING` y la regla de la cuenta suspendida (D-214, E-366) MUST seguir igual.

### Documentación

- **FR-045** En el mismo cambio de cada tramo: la decisión en `docs/MODULOS_A_AVANZAR.md` §8 (D-nn, el último hoy es D-341), lo que corresponda en `docs/DESPLIEGUE_Y_CI.md` (con «Corregido» donde una frase quede vieja), `docs/MAPA_ENDPOINTS.md` y la bitácora si aparece un error (E-nn, el último hoy es E-677); y `specs/trazabilidad.json` suma `"050"` **solo cuando todos sus requisitos tengan marca** (en el tramo de cierre, como pasó con las specs 035–047). [sin prueba automática: es de documentación; lo comprueba la revisión del PR y la tabla de trazabilidad leída a mano al cerrar]

### Fuera de alcance

- **La IP del cliente** (D1): su cambio es otro, privado hasta que esté en producción.
- Cabeceras de seguridad (ya las pone CloudFront), TLS entre CloudFront y el origen (la cabecera de FR-006 y el security group son la defensa de esta spec), Bot Control de pago del WAF, límites por dispositivo y límites por mensaje dentro de un WebSocket.
- Volver a correr la auditoría de seguridad sobre el código actual y cerrar sus dos incertidumbres (el listener de hábitos y la purga de solicitudes de cuenta): recomendación aparte.
- Encender el cumplimiento de MENTOR_LEAD (Q7), y el costo de una instancia más grande (Q5): decisiones del dueño, no código de esta spec.
- Cambiar los topes de las rutas públicas ya limitadas (D14) y la duración de 30 días de inactividad.

## Success Criteria

- **SC-001** Con el WAF en `Block`, una IP que pasa 2.000 pedidos en 5 minutos recibe 429 desde el borde y la instancia no ve ese exceso (medido en las métricas de la regla y en el log de nginx).
- **SC-002** Durante las dos semanas de medición, **cero** respuestas 429 salen del filtro por cuenta (todo en `REGISTRAR`); al final hay, por grupo y ventana, la distribución de uso (p50, p95, p99) que fundamenta los topes definitivos.
- **SC-003** Con los topes ajustados y en `APLICAR`, **a lo sumo el 0,5 %** de las cuentas activas habría recibido un 429 en los últimos 7 días de medición, y cada una de esas cuentas se revisa a mano (abuso o tope mal puesto).
- **SC-004** Un `PING` a Redis sin autenticarse devuelve `NOAUTH`, el backend responde con normalidad y **ninguna sesión vigente se pierde** por el cambio.
- **SC-005** Un pedido sin la cabecera de origen desde fuera del host recibe 403 en el 100 % de los casos; el despliegue por SSM sigue comprobando la salud sin cambios.
- **SC-006** Para la cuenta del CRM, el **100 %** de los handlers fuera de su lista responde 403 (prueba que los recorre todos) y los dos permitidos responden 200.
- **SC-007** Una sesión de más de 90 días recibe 401 `SESION_VENCIDA` en el 100 % de los casos, con el reloj de la prueba entre las 00:00 y las 05:00 UTC incluido.
- **SC-008** Una exportación de 20.000 filas no sube el heap del backend más que el tamaño de un lote (medido en una prueba de integración) y su contenido es idéntico al de antes.
- **SC-009** Tres despliegues consecutivos en producción pasan con la sonda de `/salud` sin una sola respuesta distinta de 200.
- **SC-010** Para MENTOR y TRAINEE_GUIDE, el **100 %** de los handlers fuera de la matriz aprobada responde 403, y los dos E2E por rol (Mentor y Guía) pasan sin un 403 nuevo en un flujo legítimo.

## Supuestos

- Los valores de la tabla de FR-013 son **de partida**: salen de la propuesta del dueño y se ajustan con la medición de dos semanas antes de bloquear.
- La ventana de los topes por cuenta es **fija y de 24 h para el diario, contada desde el primer pedido** (no el día calendario de Lima): es protección contra abuso, no una regla de negocio, así que no aplica la regla 02 de tiempo.
- El WAF cuenta por la IP de la conexión que ve CloudFront; el origen cuenta por la IP de D1.
- Los secretos (cabecera de origen, contraseña de Redis) viven en Parameter Store bajo `/renaser/prod/`, como el resto de la configuración; el rol de la instancia ya los lee.
- La app no se actualiza por aire: lo que cambie en la app (FR-020) llega con el siguiente APK, y el backend **no** pasa un grupo a `APLICAR` hasta que su mensaje sirva también para una app que no lo entiende (el `message` en tuteo basta).

## Preguntas abiertas para el dueño

Cada una con la opción que se recomienda primero. Ninguna bloquea el tramo A; Q8 se responde antes del tramo B, Q3 y Q6 antes del C, Q1 y Q2 antes del D, Q4 antes del F, Q5 antes del G y Q7 antes del H.

1. **Q1 — ¿Cómo entra hoy el CRM (RenaserOs)?** **A (recomendada):** la cuenta de servicio entra con su correo y contraseña por el login de siempre (cero código nuevo de autenticación) y RenaserOs vuelve a entrar si recibe un 401 (también ocurre por el tope de 90 días de D6). B: una llave de API aparte (más código y otra superficie que cuidar).
2. **Q2 — ¿Qué otros `GET` usa el CRM además de `/admin/pagos/cuotas` y `/admin/trainees`?** **A (recomendada):** que el dueño dé la lista (RenaserOs es suyo) y se confirme con lo que registre el filtro durante la medición; se parte solo con esos dos.
3. **Q3 — Cuota diaria compartida con SER:** ¿cada generación de 80/20, Espejo o Mapa cuenta **1** de los 25 diarios? **A (recomendada):** sí, 1 por generación. B: pesos distintos por herramienta.
4. **Q4 — Caja:** su estado se **deriva** en Java, por eso hoy arma todo el padrón. **A (recomendada):** dejar el cálculo, pero con CSV en streaming, un tope documentado y la medición de su costo (cero columnas nuevas: «derivar, no guardar»). B: materializar el estado en una columna (migración y riesgo de desfase).
5. **Q5 — Despliegue sin corte:** faltaron 22 MB. **A (recomendada):** medir tres despliegues (informe de solo lectura) y bajar el margen **solo si** lo medido lo permite; si no, subir de `t3.medium` a `t3.large` (unos US$ 30 más al mes). B: subir ya a `t3.large`. C: dejar los 1–2 minutos de 502.
6. **Q6 — ¿Cambiar el modo de un grupo sin reiniciar?** **A (recomendada):** sí: un valor en Redis que el filtro lee cada 30 s y un script del dueño (`scripts/despliegue/limites-modo.sh <grupo> <modo>`); reiniciar hoy cuesta 1–2 minutos de 502. B: solo por configuración y reinicio.
7. **Q7 — MENTOR_LEAD sigue en modo sombra:** ¿lo encendemos junto con el tramo H? **A (recomendada):** sí, si el registro de sombra está limpio; es una propiedad, no código. B: dejarlo como está.
8. **Q8 — Registro del WAF:** ¿activar el registro completo en CloudWatch Logs durante la medición (costo pequeño, con `x-auth-token`, `authorization` y `cookie` censurados)? **A (recomendada):** sí, 14 días y luego se baja a solo muestreo. B: solo métricas y muestreo.

## Cambios del dueño (2026-11-11)

- **D15 — Sin cuenta de servicio para el CRM.** La integración del CRM ya está hecha y queda como está: el tramo D (cuenta de solo lectura, V192, límite propio) sale del alcance. Su consumo cae en el grupo «administración» del filtro por cuenta (tramo C).
- **D16 — Respuestas a las preguntas abiertas:** se toma la opción recomendada en Q1, Q3–Q8 (Q1 y Q2 dejan de aplicar por D15). La tabla de permisos de Mentor y Guía (tramo H) la arma la sesión principal a partir del código y del registro en sombra, y no se aplica hasta que el dueño la apruebe.
- **D17 — Despliegue: se deja como está (2026-11-11).** G0 midió en el despliegue del 11/11: disponible 1890 MB, necesario 1912 MB (tope 1400 + margen 512) → reemplazo con 1–2 min de 502. El dueño eligió no cambiar el tipo de instancia ni bajar el margen. Los tramos G1–G4 salen del alcance; se recomienda desplegar en horario de poco uso.
