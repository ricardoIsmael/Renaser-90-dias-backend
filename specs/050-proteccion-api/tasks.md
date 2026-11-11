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

- [ ] T025 `GrupoDeLimite` y `ClasificadorDeGrupos` (`shared/domain`, sin Spring) + pruebas unitarias: cada familia de rutas del plan §5, el orden de decisión, las lecturas con POST, y que lo anónimo no se clasifica por cuenta (FR-013).
- [ ] T026 Puerto `ContadorDeVentanas` + `ContadorDeVentanasRedis` (script Lua multi-ventana) + **IT con Redis real**: el tope exacto, el TTL que no se renueva, el rechazo que no consume las otras ventanas, dos hilos a la vez (la familia «dos pedidos a la vez»), y el `Retry-After` que vuelve (FR-012, FR-015).
- [ ] T027 `PoliticaDeLimites` + pruebas: los tres modos, `REGISTRAR` nunca rechaza, y un fallo de Redis deja pasar y lo cuenta (FR-016, FR-017).
- [ ] T028 `FiltroDeLimitePorCuenta` + `…Config` (instancia, orden −85, E-307) + pruebas con MockMvc: 429 con el cuerpo y `Retry-After` correctos y las cabeceras de CORS, «ver como» se rechaza **antes** de contarse, el WebSocket y la salud quedan fuera, y una cuenta suspendida sigue recibiendo su 403 (FR-015, FR-021).
- [ ] T029 `LimitesDeProteccion` + `application.yaml` con los valores de FR-013 y todo en `REGISTRAR`; el modo en Redis con su caché de 30 s (Q6) y `scripts/despliegue/limites-modo.sh <grupo> <modo>` (simulacro por omisión).
- [ ] T030 `TodaRutaCaeEnUnGrupoTest`: recorre los controllers y falla si una ruta de `/api/v1` queda sin grupo ni exclusión escrita (FR-014).
- [ ] T031 Métricas (`renaser_limite_pedidos_total`, `renaser_limite_uso`), el registro durable de «quién habría bloqueado» (`HINCRBY proteccion:habria:…`, TTL 30 días), `scripts/proteccion/cuentas-que-habrian-bloqueado.sh`, el panel `renaser-proteccion.json` y la comprobación de que `config.alloy` deja pasar `renaser_limite_*` (FR-019).
- [ ] T032 Cuota diaria compartida (Q3): método nuevo en `rag.api` (`CuotaDiariaDeIa`), usado por Análisis 80/20, Espejo y Mapa **antes** del proveedor y **fuera** de `@Transactional`; pruebas: 1 por generación, la que pasa el tope recibe el mismo rechazo que SER hoy, y los 25 de SER siguen iguales (FR-018, FR-020).
- [ ] T033 `[APP]` `050c-app-429`: `apiFetch` lee `Retry-After`; la cola sin red (`colaSinRed.ts`) espera ese tiempo; una pantalla con error 429 muestra el `message`. Jest de la cola (429 con y sin `Retry-After`, nada se pierde) y E2E en el emulador contra el backend local con un grupo en `APLICAR` y `por-minuto=2` (FR-020). Los cinco tabs no cambian.
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

- [ ] T048 `TopeDeSesion` (puro) + pruebas: justo antes y justo después de los 90 días, valor vacío o `0` = sin tope, y un caso con el reloj en 00:00–05:00 UTC (FR-028, FR-031).
- [ ] T049 `FiltroDeTopeDeSesion` + `…Config` (instancia, orden −92) + IT con Redis y Spring Session: sesión creada hace 91 días y usada ayer → 401 `SESION_VENCIDA` e invalidada; la de 20 días sigue; las rutas públicas pasan aunque la sesión venza (FR-028, FR-029).
- [ ] T050 Propiedad `renaser.sesion.tope-absoluto` (90 días) en `application.yaml` y su vuelta atrás en `docs/DESPLIEGUE_Y_CI.md`.
- [ ] T051 `scripts/proteccion/sesiones-por-antiguedad.sh` (solo lectura, por SSM): cuántas sesiones llegarán al tope y cuándo, para avisar a la gente antes.
- [ ] T052 Nota **«Corregido»** en la D1 de la spec 048 («hoy, 30 días sin uso» → 30 días sin uso y 90 absolutos) y D-nn (FR-030).
- [ ] T053 E2E: con el tope en 1 minuto en el backend local, la app lleva al login conservando una cola de envíos pendientes y, al volver a entrar con la misma cuenta, la cola se envía (FR-030).

## Tramo F — Exportaciones y listas acotadas

- [ ] T054 **Primero** pruebas de caracterización: el CSV de Caja y el de Diagnóstico de hoy, byte a byte, para un conjunto de datos fijo (SC-008).
- [ ] T055 Caso de uso `exportar(actor, EscritorDeFilas)` por lotes y controllers que devuelven `StreamingResponseBody` (el guard de ADMIN **antes** de escribir); el CSV lo arma el adaptador de entrada (FR-032).
- [ ] T056 IT con 20.000 filas: el heap no crece más que un lote, el contenido es idéntico al de T054 y la sexta exportación en un minuto recibe 429 (grupo `exportaciones`).
- [ ] T057 Caja (Q4): el listado deja de rearmar el padrón por cada página (cache corto o cálculo por lotes), el techo documentado y la medición del costo en el PR (FR-033). Si el dueño elige B, es otro tramo con su migración.
- [ ] T058 `PaginaAcotada` (`shared/domain`) usado en `PersonasDelPrograma`, `ConsolaSoporteTiController.auditoria` y en todo controller con `size`/`limit`/`tamano`, incluidos los feeds que el inventario no comprobó (Muro, notificaciones, ranking, tickets) (FR-034).
- [ ] T059 Regla de código en `scripts/reglas-de-codigo.test.mjs`: un parámetro de tamaño de página sin `PaginaAcotada` falla, con el mensaje de qué está mal y cómo se arregla, y con su caso malo de prueba.
- [ ] T060 Docs (`MAPA_ENDPOINTS.md`: topes de cada lista), D-nn.

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
