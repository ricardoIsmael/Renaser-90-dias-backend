# Tareas — spec 050 (Protección de la API)

Una rama y un PR por tramo (regla 00). **Nada de esto está implementado**: la casilla de cada tarea se marca cuando su tramo se fusiona. `[DUEÑO]` = lo corre el dueño (D12: los recursos de producción los crea el dueño; los scripts traen simulacro y vuelta atrás). `[APP]` = repositorio de la app, rama aparte. Tallas: XS < ½ día · S ≈ 1 día · M 2–3 días · L 4–5 días · XL ≥ 6 días (sin contar la espera de la medición ni de la aprobación del dueño).

## Resumen de tramos

| Tramo | Rama | Talla | Depende de | Qué entrega | FR |
|---|---|---|---|---|---|
| **G0** (primero, solo lectura) | `050g0-memoria-de-la-instancia` | XS | — | Informe de memoria de la instancia y la sonda de salud | FR-036, FR-035 |
| **A1** | `050a1-redis-con-contrasena` | S | — | Redis con contraseña, sin perder sesiones | FR-010, FR-011 |
| **A2** | `050a2-cabecera-de-origen` | M | — | Cabecera secreta CloudFront → nginx | FR-006 a FR-008, FR-011 |
| **A3** | `050a3-limite-en-nginx` | S | **D1 en producción** | `limit_req` por IP del cliente (en simulacro y luego aplicado) | FR-009 |
| **B** | `050b-waf-en-cloudfront` | M | Q8 | Web ACL con reglas por tasa y grupos administrados, en `Count` | FR-001 a FR-005 |
| **C** | `050c-limites-por-cuenta` + `[APP]` `050c-app-429` | L + S | Q3, Q6 | Filtro por cuenta, **solo registrar**, y la app entiende el 429 | FR-012 a FR-021 |
| **D** | `050d-cuenta-crm-solo-lectura` | M | C, Q1, Q2 | Rol `CRM_READER` y su alcance cerrado | FR-022 a FR-027 |
| **E** | `050e-tope-absoluto-de-sesion` | S | — | 90 días absolutos | FR-028 a FR-031 |
| **F** | `050f-exportaciones-y-listas-acotadas` | M | Q4 | CSV en streaming y topes de página | FR-032 a FR-034 |
| **G** (G1–G4) | `050g-despliegue-sin-corte` | M | G0, Q5 | Despliegue sin 502 | FR-035 a FR-039 |
| **H** (H1–H4) | `050h1-…`, `050h2-…`, `050h3-…`, `050h4-…` | XL | D, Q7 | Matriz estricta de MENTOR y TRAINEE_GUIDE | FR-040 a FR-044 |
| **Z** | `050z-cierre` | S | todos | Trazabilidad y cierre | FR-045 |

Orden recomendado: **G0** → A1 → A2 → B → C → D → E → F → G1–G4 → H1 → H2 → H3/H4, con **A3** apenas D1 esté en producción (se mide junto con C). El orden del dueño (a–h) se respeta; lo único que se adelanta es G0, porque es de solo lectura y cada despliegue de los tramos siguientes hoy cuesta 1–2 minutos de 502.

## Definición de terminado (vale para cada tramo)

- [ ] `scripts/verificar.sh` en 0 (con su resumen: cuántas pruebas corrieron, 0 fallos) y el CI del PR en verde.
- [ ] Cada requisito del tramo tiene su prueba con la marca `// spec 050 FR-NNN` (solo en `src/test/`; una marca en un script o en un `.md` no cuenta). Lo que es de AWS y no se puede probar con código va con `[sin prueba automática: motivo]` en la spec.
- [ ] Todo endpoint o ruta nueva prueba el 403 de un rol sin permiso y el de una cuenta suspendida, y declara `@RequiresPermission`/`@PublicEndpoint` y `@Funcionalidad`/`@SinInterruptor`.
- [ ] Si se ve en la app: E2E en el emulador contra el backend local y los logs revisados (backend y `adb logcat`), pegados en el PR. **Infra (A, B, G)**: el ensayo local con Docker y la salida de los simulacros; lo que no se pudo probar sin AWS se dice con todas sus letras.
- [ ] D-nn en `docs/MODULOS_A_AVANZAR.md` §8, `docs/DESPLIEGUE_Y_CI.md`, `docs/MAPA_ENDPOINTS.md` y la bitácora si hubo error (E-nn), con «Corregido» donde una frase quede vieja; `.arnes/PROGRESO.md`; `graphify update .`.
- [ ] Commits en castellano, imperativo, sin `Co-Authored-By`, nunca `--no-verify`. Un bug encontrado de paso se reporta como issue, no se mezcla.

## Tramo G0 — Medir antes de decidir (solo lectura)

- [ ] T001 `scripts/despliegue/memoria-de-la-instancia.sh`: por SSM, `MemAvailable`, `docker stats --no-stream` de cada contenedor, tope y margen del script actual y su cuenta («necesario» vs «medido»). Solo lectura; perfil `prod` explícito.
- [ ] T002 `scripts/despliegue/sonda-de-salud.sh <url>`: pide `/salud` cada segundo y cuenta lo que no sea 200; sale con 1 si hay alguno.
- [ ] T003 Crear `ScriptsDeInfraTest` (en `src/test/`: todo script nuevo bajo `infra/` y `scripts/despliegue/` tiene `set -euo pipefail`, trae `--simular` si cambia algo, declara el perfil de AWS, no usa `redis-cli -a` y no contiene patrones de secretos: FR-011; se amplía en cada tramo de infraestructura) y el ensayo de la sonda en local contra un servidor de mentira.
- [ ] T004 [DUEÑO] Correr el informe antes y durante tres despliegues; los números van al PR de G.

## Tramo A1 — Redis con contraseña

- [x] T005 **Ensayo local** (compose de desarrollo, que ya admite `REDIS_PASSWORD`): backend con `REDIS_USERNAME=default` y `REDIS_PASSWORD` contra un Redis **sin** contraseña → conecta; luego `CONFIG SET requirepass` → sigue; luego recrear el contenedor con `redis.conf` y los datos → reconecta y la sesión vale. El resultado (confirma o descarta el paso 2 del plan) va al PR. **Hecho en el PR #130** (rama `050-a1-redis-clave`, a la espera de fusión). `infra/redis/ensayo-local.sh` (Docker) + `RedisConContrasenaIT`. Confirma el paso 2 del plan (`AUTH default <clave>` contra un Redis abierto) y lo corrige en el paso 4: con `appendonly yes` y sin carpeta de AOF Redis arranca **vacío**, el RDB se convierte antes en un contenedor temporal.
- [x] T006 `infra/redis/redis.conf.plantilla` (`requirepass` por sustitución, `appendonly yes`, datos en el host) y `infra/redis/contrasena.sh` (`--simular` por omisión, `--aplicar`, `--revertir`; `REDISCLI_AUTH`, nunca `-a`; conf de permiso 600). **Hecho en el PR #130** (rama `050-a1-redis-clave`, a la espera de fusión). Además: el `redis.conf` queda con dueño `999:1000` (el `redis` de la imagen no lee un conf de root con permiso 600) y lleva `notify-keyspace-events Egx` (Spring Session lo pide una sola vez al arrancar el backend). Todo el procedimiento va en `contrasena.sh` (`--paso parametros|estado|exigir`, `--revertir`), con el Redis viejo parado como `redis-previo`.
- [x] T007 Parameter Store: `REDIS_PASSWORD` (`SecureString`) y `REDIS_USERNAME=default` en `/renaser/prod/`; documentar en `docs/DESPLIEGUE_Y_CI.md` §5.3 (el contenedor `redis` pasa a tener su definición en el repositorio; «Corregido» en lo que decía «sin autenticación»). **Hecho en el PR #130** (rama `050-a1-redis-clave`, a la espera de fusión). Documentado en `docs/DESPLIEGUE_Y_CI.md` §5.3 y §6.4 (con «Corregido») y D-343; el parámetro lo crea `contrasena.sh --paso parametros --aplicar` (lo corre el dueño).
- [ ] T008 `[DUEÑO]` Simulacro en producción (`--simular`) y revisión de la salida.
- [ ] T009 `[DUEÑO]` `--aplicar` en una hora tranquila: despliegue del backend con las variables → `CONFIG SET requirepass` → recrear con la conf → comprobar `PING` sin clave desde otro contenedor (`NOAUTH`), `/salud` UP y una sesión vieja que sigue valiendo (SC-004).
- [x] T010 Ampliar `ScriptsDeInfraTest` con `infra/redis/contrasena.sh` y la plantilla `redis.conf` (`requirepass` presente, `appendonly yes`, permiso 600 en el script que la escribe) (FR-010, FR-011). **Hecho en el PR #130** (rama `050-a1-redis-clave`, a la espera de fusión). Va como `ScriptDeRedisConContrasenaTest`: `ScriptsDeInfraTest` (T003, tramo G0) no está en `master` y cada tramo trae su prueba propia para poder fusionarse en cualquier orden.

## Tramo A2 — Cabecera de origen

- [x] T011 `desplegar-backend.sh`: `map`/`geo` de nginx, `origen.conf` (permiso 600) armado con `parametro` desde Parameter Store, modos `registrar` y `exigir` (`ORIGEN_MODO`), cuerpo 403 del plan §2, loopback e IP de operación exentos. **Sin ningún secreto en el repositorio ni en la salida del despliegue.** **Hecho en el PR #131** (rama `050-a2-cabecera-origen`, a la espera de fusión). `origen-modo`, `origen-secreto`, `origen-secreto-anterior` y `origen-ips-directas` salen de Parameter Store (el CD y `cd.yml` no cambian; sin parámetros, nginx queda en `registrar`). Los secretos van en el mismo `renaser.conf` (permiso 600) y no en un `origen.conf` aparte: el proxy monta un solo archivo y agregar un segundo montaje obligaría a recrearlo. Se agregan `SOLO_PROXY=si` (reescribe y recarga nginx sin tocar el backend) y la prueba con `nginx -t` antes de reemplazar la configuración.
- [x] T012 Prueba estática (`ConfiguracionDeNginxTest`, lee la plantilla dentro del script): hay `map` del origen, el loopback queda exento, `exigir` devuelve 403 con `ORIGEN_NO_PERMITIDO`, y el health del despliegue pasa (FR-006, FR-008). **Hecho en el PR #131** (rama `050-a2-cabecera-origen`, a la espera de fusión). `ConfiguracionDeNginxTest` y `ScriptDeCabeceraDeOrigenTest`.
- [x] T013 Ensayo local con Docker: nginx + un backend de mentira; con y sin cabecera, en los dos modos, y la comprobación de salud por `127.0.0.1:8080` intacta. **Hecho en el PR #131** (rama `050-a2-cabecera-origen`, a la espera de fusión). `infra/cloudfront/ensayo-local.sh`: nginx 1.28 de verdad, el mismo código de producción y un `aws`/`curl` de mentira; más de 50 comprobaciones. Cazó que un secreto de 64 caracteres no cabe en el `map_hash_bucket_size` por omisión, y mostró que el «loopback exento» del plan no alcanza: Docker entrega lo del host con la IP de la pasarela de la red (se exime la subred de `renaser`).
- [x] T014 `infra/cloudfront/cabecera-de-origen.sh` (`--simular`, `--aplicar`, `--rotar`, `--revertir`; respaldo de la configuración de la distribución con su `ETag`; `aws cloudfront wait distribution-deployed`) y `infra/cloudfront/README.md`; extender `ScriptsDeInfraTest`. **Hecho en el PR #131** (rama `050-a2-cabecera-origen`, a la espera de fusión). Los pasos son `--paso agregar|estado|exigir|rotar` y `--revertir` (la vuelta atrás quita solo la cabecera, no restaura la distribución entera).
- [ ] T015 `[DUEÑO]` Orden de salida: despliegue en `registrar` → `--aplicar` → 24 h de `docker logs proxy` sin pedidos sin cabecera (salvo loopback y operación) → `ORIGEN_MODO=exigir`. Comprobar un despliegue completo por SSM en `exigir`.

## Tramo A3 — Tope por IP en nginx (espera a D1 en producción)

- [ ] T016 `limit_req_zone $ip_del_cliente …` (20 r/s), `limit_req … burst=40 nodelay`, `limit_req_status 429`, `limit_req_dry_run on`, `error_page 429` con el cuerpo del plan §2 y loopback exento, en la plantilla de nginx. **`$ip_del_cliente` lo define la dependencia D1**; esta rama solo la usa y no empieza hasta que D1 esté fusionado.
- [ ] T017 Prueba estática y ensayo local (ráfaga de 40 pasa, la 41 en `dry_run` pasa y queda anotada; sin `dry_run` recibe 429).
- [ ] T018 `[DUEÑO]` Dos semanas en `dry_run` (junto con C); después se quita `limit_req_dry_run`.

## Tramo B — WAF en CloudFront

- [x] T019 `infra/waf/web-acl.plantilla.json`: tres grupos administrados y dos reglas por tasa (2.000 / 5 min global; 300 / 5 min con la condición de ruta `/api/v1/auth/`), todo en `Count`, respuesta personalizada 429 con su cuerpo y `Retry-After`, `RedactedFields` de `x-auth-token`, `authorization`, `cookie` (FR-001 a FR-004). **Hecho en el PR #132** (rama `050-b-waf`, a la espera de fusión). Usa `RegexMatchStatement` para la ruta (un `ByteMatchStatement` pide un blob codificado) y una sección `Renaser` (excepciones, respuesta 429 de bloqueo, registro) que los scripts quitan antes de hablar con AWS.
- [x] T020 `infra/waf/crear-web-acl.sh`, `asociar-web-acl.sh`, `pasar-regla-a-bloqueo.sh` (`--simular` por omisión, `--aplicar`, respaldo y `--revertir`; `aws wafv2 check-capacity` en el simulacro) y `infra/waf/README.md`. **Hecho en el PR #132** (rama `050-b-waf`, a la espera de fusión). Además de los tres scripts, `infra/waf/comun.sh` (lo que comparten). El paso a bloqueo tiene su propia bandera, `--aplicar-bloqueo`; `--aplicar` no existe en `pasar-regla-a-bloqueo.sh`.
- [x] T021 `ConfiguracionDelWafTest` (en `src/test/`, lee la plantilla): los valores son 2.000 y 300, la ventana de 5 minutos, los tres grupos están, todas las reglas nacen en `Count` y el registro censura las tres cabeceras (FR-001 a FR-004). **Hecho en el PR #132** (rama `050-b-waf`, a la espera de fusión). `ConfiguracionDelWafTest` y `ScriptsDelWafTest`; además `infra/waf/ensayo-local.sh` contra un AWS de mentira.
- [ ] T022 (opcional) **Ensayo**: la misma ACL contra una distribución de CloudFront de ensayo con un origen de mentira, para ver el 429, su cuerpo y su `Retry-After` y la censura del registro, sin tocar la distribución real.
- [ ] T023 `[DUEÑO]` Responder Q8; crear la ACL (sin asociar); asociarla en `Count`; 7 días de métricas y muestras.
- [ ] T024 `[DUEÑO]` Pasar a `Block` primero las dos reglas por tasa y la reputación de IP; los otros dos grupos solo sin falsos positivos (excluyendo por nombre en la plantilla lo que acierte mal). Documentar en `DESPLIEGUE_Y_CI.md` y la decisión D-nn.

## Tramo C — Filtro de límite por cuenta (solo registrar)

- [x] T025 `GrupoDeLimite` y `ClasificadorDeGrupos` (`shared/domain`, sin Spring) + pruebas unitarias: cada familia de rutas del plan §5, el orden de decisión, las lecturas con POST, y que lo anónimo no se clasifica por cuenta (FR-013). **Hecho en el PR #134** (rama `050-c-limites-por-cuenta`, a la espera de fusión). `ClasificadorDeGrupos` y `GrupoDeLimite` (sin el grupo `crm`: D15), más `PatronDeRuta` (la ruta sin ids para el registro) y `SujetoDeLimite`. Las lecturas con POST son hoy `POST /users/me`.
- [x] T026 Puerto `ContadorDeVentanas` + `ContadorDeVentanasRedis` (script Lua multi-ventana) + **IT con Redis real**: el tope exacto, el TTL que no se renueva, el rechazo que no consume las otras ventanas, dos hilos a la vez (la familia «dos pedidos a la vez»), y el `Retry-After` que vuelve (FR-012, FR-015). **Hecho en el PR #134** (rama `050-c-limites-por-cuenta`, a la espera de fusión). `ContadorDeVentanas` (en `shared/domain`) y `ContadorDeVentanasRedis`; `ContadorDeVentanasRedisIT` con Redis real (tope exacto, TTL que no se renueva, el rechazo que no consume las otras ventanas, 16 hilos contra un tope de 25, `Retry-After`). La ventana que rechaza sigue contando (para medir cuánto se pasó); las demás no.
- [x] T027 `PoliticaDeLimites` + pruebas: los tres modos, `REGISTRAR` nunca rechaza, y un fallo de Redis deja pasar y lo cuenta (FR-016, FR-017). **Hecho en el PR #134** (rama `050-c-limites-por-cuenta`, a la espera de fusión). `PoliticaDeLimites` en `shared/application` con `DecisionDeLimite`; `PoliticaDeLimitesTest`.
- [x] T028 `FiltroDeLimitePorCuenta` + `…Config` (instancia, orden −85, E-307) + pruebas con MockMvc: 429 con el cuerpo y `Retry-After` correctos y las cabeceras de CORS, «ver como» se rechaza **antes** de contarse, el WebSocket y la salud quedan fuera, y una cuenta suspendida sigue recibiendo su 403 (FR-015, FR-021). **Hecho en el PR #134** (rama `050-c-limites-por-cuenta`, a la espera de fusión). `FiltroDeLimitePorCuenta` y su `Config` (instancia, orden −85, solo `/api/v1/*`); `FiltroDeLimitePorCuentaTest` y `LimitesPorCuentaIT` (cadena real). Se agregó `Retry-After` a las cabeceras expuestas por CORS (`SecurityConfig`) para que el build web lo lea.
- [x] T029 `LimitesDeProteccion` + `application.yaml` con los valores de FR-013 y todo en `REGISTRAR`; el modo en Redis con su caché de 30 s (Q6) y `scripts/despliegue/limites-modo.sh <grupo> <modo>` (simulacro por omisión). **Hecho en el PR #134** (rama `050-c-limites-por-cuenta`, a la espera de fusión). `LimitesDeProteccion` y el bloque de `application.yaml` (también espejado en el de las pruebas, que lo reemplaza: `LimitesDeProteccionTest` comprueba que digan lo mismo). El modo en Redis es la clave `proteccion:modo:<grupo>` (`DESACTIVADO` | `REGISTRAR` | `APLICAR`), con caché de 30 s; `scripts/despliegue/limites-modo.sh <grupo> <modo>` (más `estado`, `--revertir` y el modo `CONFIGURACION`, que borra la clave); `ScriptsDeLimitesPorCuentaTest`.
- [x] T030 `TodaRutaCaeEnUnGrupoTest`: recorre los controllers y falla si una ruta de `/api/v1` queda sin grupo ni exclusión escrita (FR-014). **Hecho en el PR #134** (rama `050-c-limites-por-cuenta`, a la espera de fusión). `TodaRutaCaeEnUnGrupoTest`: lo exento es exactamente lo `@PublicEndpoint` (más `POST /auth/logout`, que tiene sesión y se cuenta, decidido a propósito) y cada grupo tiene rutas reales. Como el clasificador termina en «escritura» o «lectura», ninguna ruta queda sin grupo; lo que la prueba frena es un endpoint público nuevo sin decidir y una ruta renombrada que se sale de su grupo.
- [x] T031 Métricas (`renaser_limite_pedidos_total`, `renaser_limite_uso`), el registro durable de «quién habría bloqueado» (`HINCRBY proteccion:habria:…`, TTL 30 días), `scripts/proteccion/cuentas-que-habrian-bloqueado.sh`, el panel `renaser-proteccion.json` y la comprobación de que `config.alloy` deja pasar `renaser_limite_*` (FR-019). **Hecho en el PR #134** (rama `050-c-limites-por-cuenta`, a la espera de fusión). Métricas (`MicrometerLimitesAdapter`), registro durable (`proteccion:habria:<grupo>:<AAAA-MM-DD>`, 30 días), `scripts/proteccion/cuentas-que-habrian-bloqueado.sh`, panel `renaser-proteccion.json` y `MetricasDelLimitePorCuentaTest` (los nombres que salen en Prometheus son los del panel y `config.alloy` no descarta `renaser_limite_*`).
- [ ] T032 Cuota diaria compartida (Q3): método nuevo en `rag.api` (`CuotaDiariaDeIa`), usado por Análisis 80/20, Espejo y Mapa **antes** del proveedor y **fuera** de `@Transactional`; pruebas: 1 por generación, la que pasa el tope recibe el mismo rechazo que SER hoy, y los 25 de SER siguen iguales (FR-018, FR-020). **NO se implementa en este tramo, a propósito (ver el registro de abajo):** hoy ningún endpoint genera con IA a pedido fuera de SER.
- [x] T033 `[APP]` `050c-app-429`: `apiFetch` lee `Retry-After`; la cola sin red (`colaSinRed.ts`) espera ese tiempo; una pantalla con error 429 muestra el `message`. Jest de la cola (429 con y sin `Retry-After`, nada se pierde) y E2E en el emulador contra el backend local con un grupo en `APLICAR` y `por-minuto=2` (FR-020). Los cinco tabs no cambian. **Hecho en el PR #188 del repositorio de la app** (rama `050-c-limites-app`, a la espera de fusión), **salvo el E2E, que está pendiente** (lo corre la sesión principal). `apiFetch` lee `Retry-After` (`ApiError.reintentarEnMs`), el mensaje sale por `mensajeDeError`, y `colaSinRed.ts` espera el mayor entre `Retry-After` y su espera creciente sin adelantarlo al volver la señal. Jest: `demasiadosPedidos.test.ts` y `colaSinRed429.test.ts`.
- [ ] T034 E2E del backend con la app: uso normal de un día (Hoy, hábitos, Muro, chat, subida de foto) sin un solo 429 y con el registro de «habría bloqueado» vacío; revisar los logs.
- [ ] T035 `[DUEÑO]` Desplegar con **todos los grupos en `REGISTRAR`**: día 0 de las dos semanas.
- [ ] T036 Día 14: el informe (percentiles de uso por grupo, `habria_bloqueado`, la lista de cuentas revisadas una por una, los picos tras reconexión de la cola) y la **tabla de valores propuestos** (el mayor entre el valor de partida y 2 × p99) para que el dueño la apruebe (SC-002, SC-003).
- [ ] T037 `[DUEÑO]` Pasar a `APLICAR` grupo por grupo con `limites-modo.sh` (primero `exportaciones`, `admin`, `ia-fuera-de-ser`, `subidas`; al final `muro`, `chat`, `escritura`, `lectura`), con la métrica de `bloqueado` abierta.

## Tramo D — DESCARTADO por el dueño (D15, 2026-11-11): no se implementa — Cuenta de servicio del CRM

- [ ] T038 `[DUEÑO]` Responder Q1 y Q2 (cómo entra RenaserOs y qué otros `GET` usa).
- [ ] T039 `V192__rol_integracion_crm.sql` (**sola** en su archivo, con la cabecera de la regla 04; verificar con `git fetch` el último número) + IT de la migración.
- [ ] T040 `UserRole.CRM_READER`, `RolUsuarioJpa.INTEGRACION_CRM`, `UserPersistenceMapper`, `nombreVisible()`, `can(...)` con el conjunto cerrado, y `UserRole.deLasPersonas()` + una prueba por cada lugar que recorre `UserRole.values()` o `EnumSet.allOf(UserRole.class)` (FR-026).
- [ ] T041 `AlcanceDeCuentaDeIntegracion` (lista de permitidos) invocado por `PermissionEnforcementInterceptor` tras la comprobación de cuenta suspendida, y el tope de 60 / min con `ContadorDeVentanas` (grupo `crm`).
- [ ] T042 Los servicios `CuotasDeTodosService` y el listado de `TraineeAdminService` aceptan el rol **solo en la lectura**; pruebas: `DELETE /admin/pagos/cuotas/{id}` y `PUT /admin/trainees/{id}/program-day` responden 403 para el rol nuevo (FR-024).
- [ ] T043 Prueba que recorre **todos** los handlers: solo los de la lista responden distinto de 403 para `CRM_READER`; además, la cuenta suspendida recibe 403, y el rol no se puede inyectar por el body de un alta (SC-006, FR-023).
- [ ] T044 Consultas de personas: una IT que crea la cuenta del CRM y comprueba que **no** aparece en el padrón, el ranking, «Equipo y roles», los destinatarios de avisos, los candidatos a un grupo ni los chats (FR-026).
- [ ] T045 `scripts/crm/asignar.sql` y `asignar-en-produccion.sh` (patrón de `scripts/soporte-ti/`: una cuenta existente y activa, el correo por variable de `psql`, simulacro por omisión, verifica antes de cambiar nada) y el registro INFO por pedido de la cuenta, sin datos personales (FR-022, FR-027).
- [ ] T046 Nota en `specs/032-cronograma-de-pagos/contracts/pagos.md` y en `docs/MAPA_ENDPOINTS.md`: la lectura de las cuotas la hace ahora una cuenta de solo lectura; D-nn.
- [ ] T047 `[DUEÑO]` Asignar el rol a la cuenta del CRM, fijar su contraseña con «restablecer», cambiar RenaserOs a esa cuenta, comprobar que lee y que no puede escribir, y retirar el uso de la cuenta de Alquimista.

## Tramo E — Tope absoluto de sesión

- [x] T048 `TopeDeSesion` (puro) + pruebas: justo antes y justo después de los 90 días, valor vacío o `0` = sin tope, y un caso con el reloj en 00:00–05:00 UTC (FR-028, FR-031). **Hecho en el PR #135** (rama `050-e-sesion-tope`, a la espera de fusión). `TopeDeSesion` es una clase final con `de(Duration)`/`sinTope()`; un valor positivo menor a un segundo (el `90` sin unidad, que Spring lee como 90 ms) impide arrancar.
- [x] T049 `FiltroDeTopeDeSesion` + `…Config` (instancia, orden −92) + IT con Redis y Spring Session: sesión creada hace 91 días y usada ayer → 401 `SESION_VENCIDA` e invalidada; la de 20 días sigue; las rutas públicas pasan aunque la sesión venza (FR-028, FR-029). **Hecho en el PR #135** (rama `050-e-sesion-tope`, a la espera de fusión). `FiltroDeTopeDeSesion`, `…Config` (instancia, orden −92), `RutasSinSesion` (copia de los `permitAll()` de `SecurityConfig`, con una prueba que la compara con el archivo) e `IT` con `creationTime` escrito en Redis. **Se suma el handshake del WebSocket** (`ActorHandshakeInterceptor`, 401): el token también viaja por `?token=`. El tope **no es un bean** (el proxy de Modulith no puede con una clase `final`, E-307).
- [x] T050 Propiedad `renaser.sesion.tope-absoluto` (90 días) en `application.yaml` y su vuelta atrás en `docs/DESPLIEGUE_Y_CI.md`. **Hecho en el PR #135** (rama `050-e-sesion-tope`, a la espera de fusión). Valor `0` o vacío = sin tope; en producción es `0` (Parameter Store no admite valores vacíos). `docs/DESPLIEGUE_Y_CI.md` y D-347.
- [x] T051 `scripts/proteccion/sesiones-por-antiguedad.sh` (solo lectura, por SSM): cuántas sesiones llegarán al tope y cuándo, para avisar a la gente antes. **Hecho en el PR #135** (rama `050-e-sesion-tope`, a la espera de fusión). Con `--local` para ensayar y `--tope-dias N` para comparar; cuenta sesiones, no personas.
- [x] T052 Nota **«Corregido»** en la D1 de la spec 048 («hoy, 30 días sin uso» → 30 días sin uso y 90 absolutos) y D-nn (FR-030). **Hecho en el PR #135** (rama `050-e-sesion-tope`, a la espera de fusión). También `docs/MODULO_AUTH.md` §4.1 y los dos lugares de la spec 048 que repetían «30 días».
- [ ] T053 E2E: con el tope en 1 minuto en el backend local, la app lleva al login conservando una cola de envíos pendientes y, al volver a entrar con la misma cuenta, la cola se envía (FR-030). **Sin hacer en el PR #135**: no se levantó backend ni emulador; lo que hace la app con un 401 está leído en `apiClient.ts`, `AuthContext.tsx` y `colaSinRed.ts`, no ejecutado.

## Tramo F — Exportaciones y listas acotadas

- [x] T054 **Primero** pruebas de caracterización: el CSV de Caja y el de Diagnóstico de hoy, byte a byte, para un conjunto de datos fijo (SC-008). **Hecho en el PR #136** (rama `050-f-exportaciones`, a la espera de fusión). Dos archivos dorados (Caja: 8 filas de todo tipo y 120 filas; diagnóstico: una ficha con comas, comillas, escala, condición y destino) capturados con el código de antes, en un commit aparte que va primero.
- [x] T055 Caso de uso `exportar(actor, EscritorDeFilas)` por lotes y controllers que devuelven `StreamingResponseBody` (el guard de ADMIN **antes** de escribir); el CSV lo arma el adaptador de entrada (FR-032). **Hecho en el PR #136** (rama `050-f-exportaciones`, a la espera de fusión). `exportar(actor)` devuelve una `ExportacionDeCajas` / `ExportacionDelDiagnostico` y el guard pasa **al pedirla** (con `StreamingResponseBody` el cuerpo corre después de volver el controller); las filas salen por lotes. El diagnóstico exporta el cuestionario, no respuestas: se escribe fila a fila, acotado por la cantidad de preguntas.
- [~] T056 IT con 20.000 filas: el heap no crece más que un lote, el contenido es idéntico al de T054 y la sexta exportación en un minuto recibe 429 (grupo `exportaciones`). **Hecho a medias en el PR #136**: la IT con 20.000 aprendices comprueba contenido completo, orden igual al de la lista, sin `Content-Length` y la memoria viva (+7 MB). **Falta** «la sexta exportación en un minuto recibe 429»: es del grupo `exportaciones` del filtro del tramo C y se prueba cuando C esté fusionado.
- [x] T057 Caja (Q4): el listado deja de rearmar el padrón por cada página (cache corto o cálculo por lotes), el techo documentado y la medición del costo en el PR (FR-033). Si el dueño elige B, es otro tramo con su migración. **Hecho en el PR #136** (rama `050-f-exportaciones`, a la espera de fusión). Se deja el cálculo (Q4 = A): cada pedido recorre el padrón **por lotes** y guarda solo un índice mínimo (id, nombre sin tildes, estado); las cajas completas se arman para la página o el lote. Medición antes/después en D-348 y en el PR; aviso en el registro desde 5.000 aprendices. No hay cache (cambia el estado y la lista debe verlo).
- [x] T058 `PaginaAcotada` (`shared/domain`) usado en `PersonasDelPrograma`, `ConsolaSoporteTiController.auditoria` y en todo controller con `size`/`limit`/`tamano`, incluidos los feeds que el inventario no comprobó (Muro, notificaciones, ranking, tickets) (FR-034). **Hecho en el PR #136** (rama `050-f-exportaciones`, a la espera de fusión). `PaginaAcotada` (`estricta` = 400, `recortada` = en silencio) usada en Personas del programa, la auditoría de la consola de Soporte TI y Caja. Las otras 13 listas ya estaban acotadas en su caso de uso o servicio (inventario del 11/11) y quedan en `tamanoSinPaginaAcotada`, lista que solo se achica.
- [x] T059 Regla de código en `scripts/reglas-de-codigo.test.mjs`: un parámetro de tamaño de página sin `PaginaAcotada` falla, con el mensaje de qué está mal y cómo se arregla, y con su caso malo de prueba. **Hecho en el PR #136** (rama `050-f-exportaciones`, a la espera de fusión). Regla en `scripts/reglas-de-codigo.test.mjs` con su caso malo y su caso bueno, y la lista de excepciones que solo se achica.
- [x] T060 Docs (`MAPA_ENDPOINTS.md`: topes de cada lista), D-nn. **Hecho en el PR #136** (rama `050-f-exportaciones`, a la espera de fusión). `docs/MAPA_ENDPOINTS.md` (tabla de topes y descargas), contrato de Soporte TI con «Corregido» (la auditoría pasa de recortar a 400) y D-348.

## Tramo G — Despliegue sin corte (G1–G4; G0 ya salió)

- [ ] T061 **G1** `[DUEÑO]` Con los números de T004, decidir Q5: bajar el margen (solo si lo medido lo permite, con la medición escrita en la bitácora) o subir a `t3.large`. La condición de memoria no se baja a ojo (E-155).
- [ ] T062 **G2** `error_page 502 503 504` de nginx con el cuerpo 503 y `Retry-After` del plan §2 para el modo con corte; prueba estática y ensayo local parando el backend (FR-038).
- [ ] T063 **G3** `[DUEÑO]` Tres despliegues seguidos con `sonda-de-salud.sh` corriendo por el borde: ninguna respuesta distinta de 200 (SC-009).
- [ ] T064 **G4** Probar en local con Docker el comando de vuelta atrás que imprime el script (FR-039) y la rama de «el nuevo no llega a UP → el viejo sigue» (FR-037).
- [ ] T065 Docs: `DESPLIEGUE_Y_CI.md` §5.3 (la tabla de memoria medida, con «Corregido» en lo que ya no sea cierto), bitácora si el 502 tenía otra causa, D-nn.

## Tramo H — Matriz estricta de MENTOR y TRAINEE_GUIDE

**H1 — inventario y propuesta (solo documentos)**
- [ ] T066 Inventario: los permisos que declara cada handler (`@RequiresPermission`, con y sin `value =`) y, por cada uno, el uso real de Mentor y Guía en la app (pantallas de Mentoría y flujos) y el registro de sombra de MENTOR_LEAD. Propuesta de la tabla de permisos de MENTOR/GUÍA **sin darla por buena**.
- [ ] T067 `[DUEÑO]` Aprobar (o corregir) la tabla; la aprobación queda en `docs/MODULOS_A_AVANZAR.md` §8 (FR-040). Responder Q7.

**H2 — sombra**
- [ ] T068 `UserRole.can` con el conjunto explícito y negación por defecto para MENTOR y TRAINEE_GUIDE; `PermissionEnforcementInterceptor` aplica la matriz con `renaser.security.mentor-enforcement=false` (registra permiso, método y ruta, **no** la cuenta); `EXPORT_RANKING` y la regla de cuenta suspendida (D-214) intactos (FR-041, FR-044).
- [ ] T069 Pruebas unitarias de `UserRoleTest` (cada permiso, por rol) y del interceptor en sombra y en aplicación.
- [ ] T070 `[DUEÑO]` Dejarlo en producción hasta que el registro de sombra esté limpio en uso real.

**H3/H4 — aplicar y probar**
- [ ] T071 La prueba que recorre todos los handlers por cada rol: lo no aprobado responde 403 y lo aprobado no (FR-043, SC-010).
- [ ] T072 **E2E por rol**: Mentor y Guía contra el backend local con mobile-mcp y Maestro (Mentoría, grupos, aprendices, tickets, evidencias, cursos, eventos, su propio programa). Un 403 nuevo en un flujo legítimo es bloqueante; revisar los logs del backend y `adb logcat`.
- [ ] T073 `mentor-enforcement=true` (y `mentor-lead-enforcement=true` si el dueño lo quiere, Q7), con la vuelta atrás a un comando. Los guards de servicio **no se borran** (FR-042).

## Tramo Z — Cierre

- [ ] T074 Revisar la tabla de trazabilidad a mano, requisito por requisito (`node scripts/trazabilidad-specs.mjs ../Renaser-90-dias-frontend- --detalle`), y escribir `specs/050-proteccion-api/validation.md` con lo que quedó sin prueba y por qué.
- [ ] T075 Agregar `"050"` a `specs/trazabilidad.json` **en este mismo cambio** (la spec entra a la lista cuando todos sus FR tienen marca; antes sería rojo por los tramos que faltan).
- [ ] T076 `.arnes/PROGRESO.md`, `docs/MODULOS_A_AVANZAR.md` (registro de D-nn) y `.arnes/scripts/grafo-al-dia.sh`; cerrar las ramas con `.arnes/scripts/worktree-cerrar.sh`.

## De cada requisito a su tramo y su prueba

| FR | Tramo | Prueba |
|---|---|---|
| FR-001 a FR-004 | B | `ConfiguracionDelWafTest` (plantilla) |
| FR-005 | B | `[sin prueba automática]`: salida de los scripts y ensayo T022 |
| FR-006 a FR-008 | A2 | `ConfiguracionDeNginxTest` + ensayo local con Docker |
| FR-009 | A3 | `ConfiguracionDeNginxTest` + ensayo local |
| FR-010 | A1 | Ensayo local T005; `ScriptsDeInfraTest`; el valor real en producción lo comprueba el dueño (T009) |
| FR-011 | todos los de infra | `ScriptsDeInfraTest` |
| FR-012, FR-015 | C | IT de `ContadorDeVentanasRedis`; MockMvc del filtro |
| FR-013, FR-014 | C | `ClasificadorDeGruposTest`; `TodaRutaCaeEnUnGrupoTest` |
| FR-016, FR-017 | C | `PoliticaDeLimitesTest`; filtro |
| FR-018 | C | pruebas de `CuotaDiariaDeIa` |
| FR-019 | C | prueba de métricas y del registro durable |
| FR-020 | C `[APP]` | Jest de la cola y de `apiFetch` (marca `// spec 050 FR-020` en el repositorio de la app) |
| FR-021 | C | MockMvc del filtro con sesión de solo lectura |
| FR-022 a FR-027 | D | IT de la migración; `UserRoleTest`; el recorrido de handlers; la IT de listados |
| FR-028 a FR-031 | E | `TopeDeSesionTest`; IT del filtro |
| FR-032, FR-033 | F | caracterización del CSV; IT de 20.000 filas |
| FR-034 | F | regla de código y `PaginaAcotadaTest` |
| FR-035 a FR-039 | G | sonda y ensayos locales; `[sin prueba automática]` para el despliegue real (SC-009 lo mide el dueño) |
| FR-040 a FR-044 | H | `UserRoleTest`; el recorrido por rol; E2E por rol |
| FR-045 | Z | `[sin prueba automática]`: documentación; se revisa a mano en T074 |


> Tramos G1–G4 (despliegue sin corte): fuera del alcance por decisión del dueño (D17, 2026-11-11). G0 hecho: medición en spec.md D17.

## Registro de implementación de A1, A2 y B (2026-11-11)

Los tres tramos están en PR (#130, #131, #132) y **ninguno se ha corrido en producción**: lo que queda de ellos es `[DUEÑO]` (T008, T009, T015, T023, T024) y T022, opcional y sin hacer. Lo que se probó y lo que no, tramo por tramo, está en el cuerpo de cada PR. Cambios respecto al plan:

- **Pruebas de infraestructura (T003, T010, T012, T014, T021).** `ScriptsDeInfraTest` pertenece al tramo G0, que no está en `master`: cada tramo trae sus pruebas (`ScriptDeRedisConContrasenaTest`, `ConfiguracionDeNginxTest`, `ScriptDeCabeceraDeOrigenTest`, `ConfiguracionDelWafTest`, `ScriptsDelWafTest`) y se pueden fusionar en cualquier orden. Si G0 sale después, puede juntar lo común.
- **A1.** El ensayo local (T005) confirmó el paso 2 y corrigió el paso 4: el RDB hay que convertirlo a AOF en un contenedor temporal, el `redis.conf` va con dueño `redis` y lleva los eventos de teclas de Spring Session.
- **A2.** «Loopback exento» (FR-006, plan §3 A2) no alcanza: Docker entrega lo que viene del propio host al contenedor de nginx con la IP de la pasarela de la red (`172.x.0.1`), no con `127.0.0.1`. Se exime también la subred de la red `renaser`. Los secretos y el modo salen de Parameter Store, no de una variable `ORIGEN_MODO` del CD, y la rotación (FR-007) se hace con `SOLO_PROXY=si`, sin un despliegue.
- **B.** La condición de ruta usa `RegexMatchStatement`; el paso Count → Block tiene bandera propia (`--aplicar-bloqueo`).
- **Antes de `exigir` (A2)**: hay que decidir qué hace la IP fija del security group (si llama a la API sin pasar por CloudFront, va en `origen-ips-directas`).

## Registro de implementación de C (2026-11-11)

El tramo C está en PR (backend #134, app #188) y **ninguno se ha corrido en producción ni con el E2E de la sesión principal**: lo que queda es T034 (E2E de un día de uso normal y el E2E de la app con un grupo en `APLICAR`), T035 `[DUEÑO]`, T036 y T037 `[DUEÑO]`. Cambios respecto al plan:

- **Nombre del modo.** La spec dice `APLICAR`; se implementó `APLICAR` (no `BLOQUEAR`). Con `DESACTIVADO` y `REGISTRAR` son los tres modos de FR-016. Se agregó una pseudo-opción del script, `CONFIGURACION`, que borra la clave de Redis y devuelve el grupo al modo de `application.yaml`.
- **Sin grupo `crm` (D15).** El consumo del CRM cae en `admin`. La tabla de FR-013 queda con diez grupos.
- **Sin sesión.** El plan decía que lo anónimo no se cuenta salvo el webhook; se cuenta por el origen (`DireccionIpDelCliente` + `UnidadDeConteoPorIp`) todo pedido sin sesión que no sea una ruta pública exenta (el webhook y lo poco que `SecurityConfig` aún deja en `permitAll`). Las rutas públicas de autenticación y alta, incluidos los códigos por WhatsApp (`/auth/whatsapp-code/**`), siguen exentas (D14).
- **FR-018 y T032 quedan sin hacer.** Se comprobó en el código: Análisis 80/20 y Espejo se generan en barridos programados y sus endpoints son `GET`; el Mapa de Renacimiento no llama a la IA; no hay ningún `POST` que genere con IA a pedido fuera de SER. Descontar de la cuota de SER en un barrido le quitaría preguntas a la persona sin que las hiciera. El grupo `ia-fuera-de-ser` queda con su tope de 5/min sobre los `POST` de esas rutas (hoy solo `POST /mapa-renacimiento/completar`, que no llama a la IA). Cuando exista un endpoint que genere con IA a pedido, descontará de `ControlCuotaRenasiaPort` por `rag.api` antes del proveedor y fuera de `@Transactional`; FR-018 necesita entonces su prueba o su corchete `[sin prueba automática: …]` antes del cierre (T074).
- **Pruebas con el `application.yaml` de test.** Reemplaza al de main en el classpath: el bloque `renaser.proteccion.limites` se espeja ahí.
- **Reloj que retrocede.** La política relee el modo de Redis si el reloj va hacia atrás (si no, un instante futuro dejaba el modo congelado); lo necesitan las pruebas que fijan el reloj.
- **La cola de carreras con GPS y la pantalla de SER no se tocaron.** La primera ya reintenta un 429 con su espera creciente; en la segunda, un 429 sigue diciendo «se acabaron las preguntas del día» (el del minuto, `DEMASIADOS_PEDIDOS`, se distingue en un cambio aparte).

## Registro de implementación de E y F (2026-11-11)

E en el PR #135 y F en el PR #136; **ninguno se ha corrido en producción ni en el emulador**. Cambios respecto al plan:

- **E.** El tope **no es un bean**: el proxy de Modulith no puede con una clase `final` (E-307), así que el filtro y el handshake leen la propiedad (`FiltroDeTopeDeSesion.PROPIEDAD`) y arman su `TopeDeSesion`. Se agrega el handshake del WebSocket. `RutasSinSesion` copia los `permitAll()` de `SecurityConfig` y una prueba la compara con el archivo. El valor para volver atrás en producción es `0` (Parameter Store no admite vacío). **En la suite el tope va apagado** (`renaser.sesion.tope-absoluto: 0` en el `application.yaml` de test): muchas ITs mueven el reloj de prueba meses adelante con sesiones reales, que nacerían «de hace más de 90 días»; `TopeAbsolutoDeSesionIT` lo prende en su propio contexto. Las ramas que se rebasen sobre E heredan ese cambio. T053 (E2E con la app) queda sin hacer.
- **F.** «Caja paginada en la base» no es posible sin una columna (Q4 = A: el estado se deriva en Java); lo que se hizo es recorrer el padrón por lotes con un índice mínimo y armar las cajas completas solo para la página o el lote. El diagnóstico exporta el cuestionario (no respuestas): lo acota la cantidad de preguntas. `PaginaAcotada` se usa en los tres controllers tocados y las otras 13 listas (ya acotadas en su servicio) quedan en una lista de excepciones que solo se achica. La auditoría de la consola pasa de recortar a **400** (lo pidió el dueño; la app pide 20). El 429 de la sexta exportación espera al tramo C. **Una descarga en streaming vive bajo `spring.mvc.async.request-timeout`** (120 s en producción): el `application.yaml` de test no lo espejaba (regía el de Tomcat, 30 s) y ahora sí; una descarga de más de 120 s (más de ~100.000 aprendices) se corta a mitad.
