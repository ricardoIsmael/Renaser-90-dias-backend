# Plan de desarrollo por módulo — RenaserBack (Java) sobre la BD nueva (90 tablas)

## Contexto

La fase de BD terminó: baseline operativo `V1__baseline_renaser.sql` (**90 tablas**, esquema `renaser`, español), validado contra Postgres real, con cobertura total verificada contra los dos repos viejos (tablas + datos + funciones + buckets — Anexo A de la auditoría). El módulo `users` ya está construido y **verificado en esta sesión**: estructura de carpetas correcta y `./mvnw clean test` en verde (exit 0). El usuario pide ahora el **plan de desarrollo de TODOS los módulos restantes**, teniendo en cuenta la BD, respetando la estructura hexagonal con subcarpetas por agregado, e incorporando dos decisiones nuevas: **subida de archivos a AWS S3 real** y **RAG/IA al final**.

## Verificaciones hechas (esta sesión, antes de planificar)

1. **Ruta del proyecto** ✓ `C:\Users\Usuario\Documents\renaser-backend\renaser-backend` (es el cwd de la sesión).
2. **Estructura de `users`** ✓ cumple exactamente la convención pedida, con subcarpetas por agregado:
   `domain/model/{user,accountrequest,mentorprofile}/` · `application/ports/{in,out}/{agregado}/` · `application/services/` · `infrastructure/adapter/in/rest/{agregado}/` · `infrastructure/adapter/out/persistence/{agregado}/` (+ `api/` para lo público entre módulos). Diferencias vs el template del usuario, ya resueltas por decisiones registradas: `GlobalExceptionHandler` vive en `shared/web` (uno solo para todos los módulos, no por módulo) y las excepciones de negocio en `shared/domain` o `domain/exception` del módulo según alcance.
3. **Tests** ✓ `./mvnw clean test` exit 0 (suite completa en verde).
4. **Ponytail** (repo evaluado): NO es de RAG/IA — es un plugin de Claude Code que fuerza código mínimo (bench: −54% LOC). **Veredicto: útil como refuerzo de estilo** en las olas de implementación (alineado con los límites de tamaño de CLAUDE.MD §5.4.8); no aporta a la parte de IA. Se recomienda instalarlo como plugin, opcional.

## Decisiones nuevas a registrar (van a `MODULOS_A_AVANZAR.md` §8)

- **D-34 — Storage: AWS S3 real** (elegido por el usuario sobre Supabase-S3 y MinIO). Consecuencias:
  - Puerto transversal `AlmacenamientoPort` en `shared` (`firmarSubida`, `firmarLectura`, `borrar`) + `S3StorageAdapter` (AWS SDK v2). Un bucket con prefijos por dominio (`evidencias/`, `muro/`, `chat/`, `onboarding/`, `firmas/`, `cursos/`, `soporte/`).
  - **La app deja de subir directo**: no puede tener credenciales AWS. Flujo nuevo: app → API (pide URL prefirmada PUT) → sube a S3 → confirma. Cambio de app coordinado módulo a módulo.
  - Las columnas `bucket`+`ruta_storage` del modelo ya son agnósticas — cero cambio de BD.
  - **Migración de objetos** Supabase Storage → S3 por dominio, junto con la migración de datos de cada módulo. Los audios de Espíritu siguen en Drive detrás de su puerto (decisión previa, no cambia).
  - Requiere del usuario: cuenta AWS, bucket, credenciales IAM mínimas (put/get/delete sobre el bucket), región.
- **D-35 — Plan por módulo**: el detalle vive en `docs/PLAN_DE_MODULOS.md` (nuevo, contenido = este plan); al arrancar cada módulo se crea su `docs/MODULO_<NOMBRE>.md` copiando su sección como semilla.

## Estructura estándar de cada módulo (obligatoria — la de `users`)

```
com.renaser.os.<modulo>/
├── package-info.java                      (@ApplicationModule)
├── api/                                   (lo ÚNICO importable por otros módulos + eventos publicados)
├── domain/
│   ├── model/<agregado>/                  (una subcarpeta por agregado; clases puras, sin Spring/JPA)
│   └── exception/                         (excepciones de negocio del módulo, si tiene propias)
├── application/
│   ├── ports/in/<agregado>/               (interfaces de casos de uso)
│   ├── ports/out/<agregado>/              (Load/Save*Port + puertos a infraestructura externa)
│   └── services/                          (una clase por agregado, implementa los casos de uso)
└── infrastructure/adapter/
    ├── in/rest/<agregado>/                (controller tonto + DTOs record)
    ├── in/scheduler/                      (solo módulos con cron)
    ├── in/event/                          (solo módulos que consumen eventos)
    └── out/{persistence/<agregado>, s3, push, gemini, drive}/
```

Reglas que aplican a todos (DoD de `MODULOS_A_AVANZAR.md` §1): paso 0 de análisis del código viejo (D-33), comando self-validating, controller tonto, proyecciones de salida, migración Flyway propia si agrega algo, Testcontainers, ArchitectureTest, `./mvnw clean test` verde, tests de seguridad (403/rol no inyectable), contrato de la app RN intacto, avance documentado.

---

## PLAN POR MÓDULO (orden de ejecución)

### 0. Cierre de `users` (Ola 0 — en curso, lo que falta)

- **Falta:** filtro JWT real + caché Caffeine (bloqueado por **B-2**: confirmar RS256 en dashboard Supabase — acción del usuario, 5 min); enum `Permission` + `@RequiresPermission` + test de reflexión (bloqueado por **B-5/R-2**: matriz de MENTOR y LIDER_MENTORES — respuestas pendientes del usuario); agregado `participante/` (recomendación: dentro de `users` — `ParticipantePrograma` como 4º agregado, con `AssignMentorToTraineeUseCase` y creación automática al aprobar rol APRENDIZ); adaptador real de `SupabaseAdminAuthPort` (necesita service-role key); auditar B-4 (RLS INSERT en `public.users` de producción).
- **Tablas:** `usuarios`, `solicitudes_cuenta`, `auditoria_cambios_rol`, `perfiles_mentor`, `participantes_programa` (+3 RBAC [SUPERADO]).

### 1. `points` (Ola 1)

- **Paso 0:** analizar `src/features/habits/points.ts`, `repository.ts` (adjustLeaguePoints, guarda awarded=0), cron `coherence-score/route.ts`, `general_ranking_scores_function.sql` del repo viejo → extraer reglas EXACTAS (−1 por cada 2 min de retraso tras 10 de gracia, piso 0, +5 cada 3er día 100%, criterios del ranking general).
- **Agregados:** `puntaje/` (PuntajeParticipante: coherencia, puntosLiga, rachas — invariante saldo=100+Σledger), `ajuste/` (AjustePuntos + MotivoPuntos), `ranking/` (SnapshotRanking).
- **Puertos in:** `AjustarPuntosUseCase`, `ConsultarPuntajeUseCase`, `ConsultarRankingUseCase`, `GenerarSnapshotRankingUseCase`, `RegistrarCoherenciaDiariaUseCase`. **Out:** Load/Save por agregado.
- **Adapters:** in/rest (`/api/v1/ranking`, puntaje propio), in/event (escucha `HabitoCompletadoEvent`, `RocaCompletadaEvent`, `SantuarioRotoEvent`…), in/scheduler (snapshot nocturno — reemplaza `general_ranking_scores()`), out/persistence.
- **Regla dura:** asiento + saldo en la MISMA transacción (fix de P-06). Ranking público filtra rol APRENDIZ.
- **Tablas:** `puntajes_participante`, `ajustes_puntos_liga`, `historial_coherencia`, `ranking_aprendices`, `ranking_celulas`.

### 2. `phasecontracts` (Ola 1 — desbloqueado, B-3 resuelto)

- **Paso 0:** analizar `src/features/phase-contracts/*` + `src/lib/phase.ts` (CONTRACT_UNLOCK_DAY: días de firma por fase 23/46/68 — confirmar del código).
- **Agregados:** `contrato/` (ContratoFase; regla "en qué fase está" vs "cuándo le toca" = dominio puro).
- **Puertos in:** `FirmarContratoUseCase`, `ConsultarContratosPendientesUseCase`. **Out:** Load/Save + `AlmacenamientoPort` (firma → S3 `firmas/`).
- **Adapters:** in/rest (`/phase-contracts`, `/phase-contracts/pending` — contrato API intacto), out/persistence.
- **Tablas:** `contratos_fase`. **Nota:** primer consumidor del puerto S3 — estrena D-34.

### 3. `habits` (Ola 2 — el más grande, 21 tablas)

- **Paso 0:** analizar `service.ts` (~3000 líneas: getTodayTracks, completeTrackWithOptions, ventanas/expiración, effectiveExtensionMs), `staggerService.ts`, `weeklyChoice.ts`, `phoneFree.ts`, cron `daily-reset`, `habitStaggering.ts`.
- **Agregados:** `habito/` (catálogo+personales por ámbito), `horario/`, `guia/`, `registro/` (RegistroHabito + EstadoRegistro — máquina de estados, máximo valor de tests unitarios), `preferencia/` (con CambioHorarioPendiente), `santuario/` (SesionBloqueo, RachaSinCelular — cruza medianoche), `espiritu/` (RegistroEspiritu), `radar/` (RegistroRadar), `diario/` (EntradaDiario).
- **Puertos in:** ~15 (GenerarTracksDelDia, CompletarRegistro, SubirEvidencia→delega a evidence, EditarHorario, ElegirDiaSemanal, Renombrar, Desbloquear, IniciarRacha/Cerrar/Romper, RegistrarRadar, EntregarEspiritu…). **Out:** persistencia por agregado + `AlmacenamientoPort` + `AudioCatalogPort` (Drive).
- **Adapters:** in/rest (contratos `/habit-tracks/today`, `PATCH /:id`, etc. — INTACTOS), in/scheduler (`ExpirarRegistrosScheduler` + generación nocturna — reemplaza daily-reset), out/persistence, out/drive.
- **Eventos publica:** `HabitoCompletadoEvent`, `SantuarioRotoEvent`, `RachaCompletadaEvent`.
- **Seeds:** catálogo completo (hábitos+horarios+guías+adjuntos+categorías+iconos+90 audios+13 audioterapias) — migración de datos desde producción vieja.
- **Riesgo:** el mayor del proyecto. Mitigación: la máquina de estados y el cálculo de ventanas 100% unit-testeados contra casos extraídos del código viejo; catálogo cacheado (Caffeine) para matar las 14-16 queries/request del hot path.

### 4. `rocks` (Ola 2)

- **Paso 0:** analizar `src/features/rocks/*` (plan semanal W-02/W-03 ventana 48h, review W-04, EXIF ±15min, Verdugo `enforcer/*` + cron 23:55).
- **Agregados:** `rocaMaestra/`, `rocaSemanal/` (con AccionCritica 1..3), `rocaDiaria/`, `verdugo/` (EventoVerdugo).
- **Puertos in:** CrearPlanSemanal, EditarDentroDe48h, CerrarSemana, CompletarRocaDiaria, RegistrarEventoVerdugo… **Out:** persistencia + evidence vía su api.
- **Eventos publica:** `RocaCompletadaEvent`. **Adapters:** in/rest (contrato `/rocks/*` intacto), in/scheduler (Verdugo→IGNORADO 23:55).
- **Tablas:** `rocas_maestras`, `rocas_semanales`, `acciones_criticas`, `rocas_diarias`, `eventos_verdugo`.

### 5. `notifications` (Ola 3 — primero de la ola: valida el outbox)

- **Paso 0:** analizar `notifications/service.ts` (emit con preferencia), `chat/repository.ts:385` (Expo push), `reconcile.ts` de la app (qué espera).
- **Agregados:** `notificacion/`, `preferencia/`, `tokenPush/`.
- **Puertos in:** EmitirNotificacion, MarcarLeida(s), GestionarPreferencias, RegistrarToken. **Out:** persistencia + `PushPort` (adapter Expo).
- **Adapters:** in/event (consume eventos de TODOS los módulos — es la prueba de punta a punta del outbox de Modulith), in/rest (bandeja `/notifications` — ahora sí persistida en servidor, arregla la bandeja-local-por-dispositivo), out/push.
- **Tablas:** `notificaciones`, `preferencias_notificacion`, `tokens_push`. **Retención:** cron de purga >90 días.

### 6. `academy` (Ola 3)

- **Paso 0:** analizar `cursos/repository.ts` (gates por día/rol/sección, RPC `progreso_cursos` y `catalogo_cursos_bloqueados` — su lógica pasa a Java), `clase-diaria/repository.ts`, `academia/recomendacion` (IA — la llamada a Gemini queda con interfaz stub hasta Ola 5).
- **Agregados:** `curso/` (con Seccion, Leccion, Recurso), `asignacion/` (usuario⊕grupo), `progreso/`, `recomendacion/`.
- **Puertos in:** ListarCatalogo (con gates), VerLeccion, MarcarProgreso, AsignarCurso, RecomendarClase. **Out:** persistencia + `AlmacenamientoPort` (portadas/media → S3 `cursos/`).
- **Acá se cierra el destino de las tablas RBAC [SUPERADO]:** `roles_permitidos_curso` pasa a columna enum `rol` (migración V2 de este módulo) o se conserva la junction — decidir al construir, consistente con D-21.
- **Tablas:** las 10 de academia. **Datos:** todo el contenido Skool + progreso. **Nota:** la app deja de pegar directo (`leccion_progreso` es hoy escritura directa — coordinar release).

### 7. `community` (Ola 3; feed en vivo en Ola 4)

- **Paso 0:** analizar `wall/*` (keyset, reacciones agregadas, moderación, contadores COUNT-per-request → decidir contadores denormalizados), `community/repository.ts` (células/cohortes), testimonios.
- **Agregados:** `publicacion/` (con MediaPublicacion, Reaccion, Comentario), `categoria/`, `celula/`, `cohorte/`, `testimonio/`.
- **Puertos in:** PublicarEnMuro, Reaccionar, Comentar, Moderar, GestionarCelulas/Cohortes, MiCelula. **Out:** persistencia + `AlmacenamientoPort` (S3 `muro/`).
- **Eventos publica:** `PublicacionCreadaEvent` (para hito automático y notificaciones).
- **Tablas:** 8 (cohortes, células, muro×5, testimonios). **Seeds:** categorías del muro.

### 8. `calendar` (Ola 3)

- **Paso 0:** analizar `calendar/*` (`recurrence.ts` expansión de ocurrencias, `reminderService.ts` cron 5 min N+1, `reminders.ts` reglas — ya extraído en la fase BD).
- **Agregados:** `evento/` (con Recurrencia, Excepcion, ReglaRecordatorio), `confirmacion/`, `recordatorio/` (cola), `nivelMembresia/`.
- **Puertos in:** CrearEvento, ListarAgenda (expansión en memoria se conserva), Confirmar, ProgramarRecordatorios. **Out:** persistencia + notifications vía evento.
- **Adapters:** in/scheduler (cola con `FOR UPDATE SKIP LOCKED` — mata el N+1 del cron de 5 min), in/rest. **Tablas:** las 9 de calendario. **Seeds:** niveles de membresía. **RBAC [SUPERADO]:** ídem academy para `roles_destino_evento`.

### 9. `support` (Ola 3 — el más chico, ideal para paralelizar)

- **Paso 0:** analizar `tickets/*` (biblioteca con búsqueda) y `support.ts` de la app (adjuntos).
- **Agregados:** `ticketMentor/`, `ticketSoporte/`.
- **Puertos in:** AbrirTicket, Responder, GuardarEnBiblioteca, BuscarBiblioteca (full-text español — el índice ya existe). **Out:** persistencia + `AlmacenamientoPort` (S3 `soporte/`).
- **Eventos publica:** `TicketAbiertoEvent`, `TicketRespondidoEvent`. **Tablas:** 2.

### 10. `chat` (Ola 4 — necesita Redis)

- **Paso 0:** analizar `chat/repository.ts` (directKey, lecturas, N+1 de no-leídos — se reemplaza por query por lote sobre `ultimo_leido_en`), canales realtime de la app.
- **Agregados:** `conversacion/` (con Participante), `mensaje/`.
- **Puertos in:** EnviarMensaje, ListarConversaciones (no-leídos en 1 query), MarcarLeido, CrearDirecta. **Out:** persistencia + `AlmacenamientoPort` (S3 `chat/`) + `FanoutPort` (Redis Pub/Sub).
- **Adapters:** in/websocket (STOMP/SSE — reemplaza el polling), out/redis. **Regla:** mensaje SIEMPRE primero en Postgres; pub/sub solo empuja. Auto-join de todo usuario a la GLOBAL. Retención 12 meses (cron).
- **Infra nueva:** Redis (docker-compose + prod). También migra acá la invalidación de caché rol/estado entre instancias.
- **Tablas:** 4.

### 11. `evidence` (Ola 5 — IA)

- **Paso 0:** analizar `evidence-ai/*` (validator, prompt, lote de 25, penalización) y el flujo de subida de la app.
- **Agregados:** `evidencia/` (arco exclusivo; EstadoValidacion; regla de reintentos 0..3 → REVISION_MANUAL es DOMINIO, no adapter).
- **Puertos in:** RegistrarEvidencia (usada por habits/rocks/espíritu vía api), ValidarPendientes, RevisarManualmente, AnularVeredicto. **Out:** persistencia + `AlmacenamientoPort` (S3 `evidencias/` + URLs prefirmadas de lectura) + `ValidacionIAPort` (ChatClient Gemini, Spring AI: retry, timeout 45s).
- **Adapters:** in/rest (202+polling — contrato intacto), in/scheduler (cola `SKIP LOCKED`, lote 25), out/gemini.
- **Tablas:** `evidencias`. **Nota S3:** primer módulo donde la app cambia el flujo de subida (prefirmada) — coordinar release.

### 12. `onboarding` (Ola 5 — IA)

- **Paso 0:** analizar `onboarding/*` (V90→9 audios post-0019, validate 6Ps, smart/validate síncrono, gates, wipe/reset) + seeds 0001-0020.
- **Agregados:** `estado/`, `cuestionario/` (Seccion, Pregunta, Opcion — catálogo), `respuesta/`, `media/`, `grabacionV90/`.
- **Puertos in:** GuardarRespuesta (upsert — la app deja de escribir directo), AvanzarEstado, ValidarV90 (async+polling intacto), ValidarMetaSmart, ActivarPrograma. **Out:** persistencia + `AlmacenamientoPort` + `ValidacionIAPort` (comparte el de evidence vía su api o puerto propio — decidir al construir).
- **Migración V2 propia:** las 2 FKs por `clave_pregunta` (Anexo A.4). **Datos:** catálogo de preguntas completo con su historia (0001→0020).
- **Tablas:** 7.

### 13. `rag` / `renasia` (Ola 5 — último, decisión del usuario)

- **Paso 0:** analizar `rag/repository.ts` (match_knowledge, chunks, metadata), `renasia/*` (streaming text/plain, flags, retención semanal).
- **Agregados:** `conocimiento/` (ChunkConocimiento), `conversacion/` (ConversacionRenasia, MensajeRenasia con fuentes).
- **Puertos in:** Preguntar (streaming), Calibrar, MarcarMensaje. **Out:** `VectorStorePort` (Spring AI PgVectorStore sobre `base_conocimiento` — reemplaza `match_knowledge()`), `ChatIAPort`, persistencia.
- **Adapters:** in/rest (streaming), in/scheduler (retención semanal — ya existe la regla), out/vectorstore, out/gemini.
- **Tablas:** 4 + `informes_espejo_sombra`/`preguntas_confrontacion` (Espejo Sombra: generador semanal IA, consume `entradas_diario` vía api de habits).

---

## Transversales (no son módulos, van en paralelo)

- **Storage S3 (D-34):** puerto+adapter en `shared` ANTES de `phasecontracts` (su primer consumidor). Bucket AWS + IAM del usuario. Migración de objetos por dominio al migrar cada módulo.
- **Migración de datos vieja→nueva:** por módulo, con el mapeo §11 de la auditoría (ids uuid conservados) + seeds del Anexo A.2. Verificación de conteos por tabla.
- **Strangler fig:** proxy por prefijo `/api/v1/<recurso>` → Spring según módulo migrado; la app RN no ve rupturas (tests de contrato).
- **Bloqueantes vivos:** B-2 (RS256 — usuario), B-4 (auditar RLS — usuario), B-5/R-2 (matriz permisos MENTOR + LIDER_MENTORES — usuario). Ninguno frena Ola 1.

## Estrategia de ejecución (definida por Luis/Ricardo, 2026-08-24)

**Modo: implementación con agentes simultáneos supervisados, en LOTES DE 3 módulos** ("3×3", para minimizar errores). Agentes con **Sonnet en effort alto**; yo (Fable) superviso, integro y verifico. Cada agente respeta CLAUDE.MD (programación funcional en dominio §5.4.7, clean code, límites de tamaño §5.4.8, controller tonto, comandos self-validating) y la estructura de carpetas de `users`.

**Por cada lote de 3 módulos:**
1. **3 agentes constructores** (1 por módulo, en paralelo, aislados): cada uno arranca con su **paso 0** (analizar el código viejo del feature en el clon de RenaserBack), luego dominio → puertos → services → adapters + su migración V2 si aplica + tests unitarios de dominio.
2. **Agentes de pruebas** (1 por módulo): tests de integración Testcontainers + tests de seguridad (403, rol no inyectable) contra lo construido.
3. **1 agente de endpoints del lote**: levanta la app real (Postgres docker) y prueba E2E los endpoints de los 3 módulos contra el contrato de `docs/API_CONTRACT.md`.
4. Los agentes se comunican entre sí (hallazgos cruzados: p.ej. el de pruebas devuelve fallos al constructor) — yo coordino los mensajes y resuelvo conflictos de integración.
5. **Gate del lote (lo verifico yo):** `./mvnw clean test` completo en verde + `ArchitectureTest` + revisión del diff + docs actualizados (`docs/MODULO_<X>.md` nuevo, decisiones en §8, errores en bitácora). No se abre el lote siguiente sin gate en verde.

**Lotes (respetan dependencias del §5 de MODULOS_A_AVANZAR):**
- **Lote 1:** `points` + `phasecontracts` + `support` (solo dependen de users; calibran la fábrica) — incluye crear antes `AlmacenamientoPort`/S3 en `shared` (lo hago yo, es transversal).
- **Lote 2:** `habits` + `rocks` + `notifications` (núcleo + primer consumidor de eventos Modulith).
- **Lote 3:** `academy` + `community` + `calendar` (acá se cierra el destino de las tablas RBAC [SUPERADO]).
- **Lote 4:** `chat` (+Redis) + `evidence` + `onboarding`.
- **Lote 5:** `rag`/`renasia` (último — decisión del usuario) + prueba FINAL de la app completa: suite total + arranque real + smoke E2E de todos los endpoints.
- **Cierre de `users`** (auth B-2, matriz B-5/R-2): en cuanto el usuario entregue las respuestas/verificaciones — no frena los lotes (el header temporal X-Actor-Id sigue hasta entonces).

**Antes del Lote 1 (secuencial, lo hago yo):** crear `docs/PLAN_DE_MODULOS.md`, registrar D-34/D-35 en §8, actualizar memoria.
**Después del Lote 5:** informe final de verificación → siguiente fase: **migración de datos** (mapeo §11 + seeds Anexo A.2), que el usuario ya anticipó.

**Nota de costo/honestidad:** implementar 13 módulos con este nivel de calidad es un trabajo grande incluso con agentes; los lotes son secuenciales a pedido del usuario, así que el resultado completo llega por etapas — reporto el avance al cierre de cada lote y NO declaro terminado nada sin sus pruebas en verde (CLAUDE.MD §0.2).

## Estructura de carpetas FINAL del proyecto (cuando las 5 olas terminen)

Es TU proyecto Spring (`renaser-backend`), no uno nuevo. `users` ya está así; cada módulo nuevo replica el patrón:

```
src/main/java/com/renaser/os/
├── RenaserOsApplication.java
├── shared/                                  ✅ (+ se agrega el puerto S3)
│   ├── domain/  (UserId, Clock, NotAuthorizedException)
│   ├── event/   (DomainEvent)
│   ├── application/ports/out/ (AlmacenamientoPort ← D-34)
│   ├── infrastructure/s3/ (S3StorageAdapter ← D-34)
│   └── web/     (GlobalExceptionHandler, ApiErrorResponse)
│
├── users/                                   ✅ construido (falta cierre)
│   ├── api/  domain/model/{user, accountrequest, mentorprofile, participante*}/
│   ├── application/ports/{in,out}/{agregado}/ · application/services/
│   └── infrastructure/adapter/{in/rest/{agregado}, out/{persistence/{agregado}, auth}}/
│
├── points/          domain/model/{puntaje, ajuste, ranking}/
├── phasecontracts/  domain/model/{contrato}/
├── habits/          domain/model/{habito, horario, guia, registro, preferencia, santuario, espiritu, radar, diario}/
├── rocks/           domain/model/{rocamaestra, rocasemanal, rocadiaria, verdugo}/
├── notifications/   domain/model/{notificacion, preferencia, tokenpush}/
├── academy/         domain/model/{curso, asignacion, progreso, recomendacion}/
├── community/       domain/model/{publicacion, categoria, celula, cohorte, testimonio}/
├── calendar/        domain/model/{evento, confirmacion, recordatorio, nivelmembresia}/
├── support/         domain/model/{ticketmentor, ticketsoporte}/
├── chat/            domain/model/{conversacion, mensaje}/         (+ adapter/in/websocket, out/redis)
├── evidence/        domain/model/{evidencia}/                     (+ adapter/out/gemini)
├── onboarding/      domain/model/{estado, cuestionario, respuesta, media, grabacionv90}/
└── rag/             domain/model/{conocimiento, conversacion}/    (+ adapter/out/vectorstore)

(cada módulo lleva además: package-info.java, api/, application/ports/{in,out}/{agregado}/,
 application/services/, infrastructure/adapter/{in/rest/{agregado}[, in/scheduler, in/event],
 out/persistence/{agregado}[, out/s3, out/push, out/drive]} — idéntico a users)

src/main/resources/
├── application.yaml
└── db/migration/  V1__baseline_renaser.sql (90 tablas) + V2+ por módulo cuando haga falta

src/test/java/com/renaser/os/
├── ArchitectureTest.java  ·  TestcontainersConfiguration.java
└── <modulo>/ {domain (unit puros), application (unit con mocks), adapter (IT Testcontainers)}
```

*`participante/` en users = recomendación pendiente de tu OK (podría ser módulo aparte).

## Verificación

- `docs/PLAN_DE_MODULOS.md` existe, cubre los 13 módulos restantes + cierre de users + transversales, y no contradice `MODULOS_A_AVANZAR.md` (mismas olas y dependencias) ni la BD (90 tablas todas asignadas).
- Decisiones D-34/D-35 visibles en §8.
- `./mvnw clean test` sigue en verde (no se toca código en esta fase — solo docs).
