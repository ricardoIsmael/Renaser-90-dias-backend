# Análisis de la BD antigua (Prisma 6 + Supabase Postgres)

**Fecha:** 2026-08-24
**Fuentes:** `renaserlab/RenaserBack` (commit HEAD de main, `prisma/schema.prisma` de 2.945 líneas) y `renaserlab/RenaserPlayStoreCopy` (app Expo/RN + `supabase/migrations/`). Análisis en vivo de código, no de documentación.
**Diagrama:** [`ER_BD_ANTIGUA.drawio`](ER_BD_ANTIGUA.drawio) — 77 tablas + 3 externos, generado programáticamente desde el schema real (script reproducible). Abrir con draw.io (app o web).

**Propósito:** este es el insumo del rediseño. Primero se entiende lo que hay (este documento + el diagrama ER), después se normaliza, y recién después se diseña la BD nueva. No es una crítica al equipo anterior: varias decisiones "sucias" están documentadas en el propio schema como deliberadas.

---

## 1. Inventario

| Grupo | Tablas | Detalle |
|---|---|---|
| En Prisma (`schema.prisma`) | **69** | + 42 enums nativos de Postgres |
| Fuera de Prisma, vía supabase-js (PostgREST) | **8** | `cursos`, `curso_secciones`, `lecciones`, `leccion_recursos`, `grupos`, `grupo_miembros`, `curso_asignaciones`, `leccion_progreso` — creadas por `supabase/migrations/` del repo **de la app móvil** |
| Externos | 3 | `auth.users` (Supabase Auth — fuente de identidad, `users.id = auth.uid()`), Storage (≥10 buckets), Google Drive (audios Espíritu) |

**Total: 77 tablas** en el mismo Postgres. El "schema único monolítico de Prisma" que describe `CLAUDE.MD` §2 es en realidad **dos esquemas paralelos**: Prisma no ve el dominio Academia, y lo compensa denormalizando (`academia_recommendations` copia `leccion_titulo`/`curso_titulo` para no tener que volver a consultar).

### Tablas por futuro módulo (los clusters del diagrama)

| Módulo | Tablas |
|---|---|
| users | `users`, `account_requests`, `alchemist_profiles`, `admin_profiles`, `mentor_profiles`, `trainee_profiles`, `role_change_audits` |
| habits | `habits`, `habit_schedules`, `habit_guides`, `habit_guide_attachments`, `habit_tracks`, `trainee_habit_preferences`, `trainee_habit_renames`, `habit_preference_changes`, `trainee_habit_unlocks`, `trainee_weekly_habit_days` |
| habits (Santuario / Día sin celular) | `block_sessions`, `phone_free_runs`, `phone_free_weekly_reviews` |
| habits (personales) | `personal_habits`, `personal_habit_tracks`, `personal_habit_weekly_days`, `personal_habit_edit_log` |
| habits (Espíritu) | `spirit_tracks`, `spirit_audios`, `audio_therapies` |
| habits (Radar / check-in) | `radar_entries` |
| journal | `journal_entries`, `shadow_mirror_reports` |
| rocks | `master_rocks`, `weekly_rocks`, `daily_rocks`, `enforcer_events` |
| evidence | `evidence` (polimórfica: sirve a habits, rocks, spirit, personal) |
| onboarding | `onboarding_state`, `onboarding_sections`, `onboarding_questions`, `onboarding_answers`, `onboarding_media`, `variables_90_recordings` |
| community | `wall_posts`, `wall_post_media`, `wall_reactions`, `wall_comments`, `wall_categories`, `testimonios`, `cohorts`, `cells` |
| calendar | `calendar_events`, `event_occurrence_overrides`, `event_rsvps`, `event_reminders`, `membership_levels` |
| chat | `conversations`, `conversation_participants`, `messages`, `welcome_messages` |
| notifications | `notifications`, `notification_preferences`, `push_tokens` |
| rag / renasia | `knowledge_base` (pgvector), `renasia_conversations`, `renasia_messages` |
| support | `support_tickets`, `mentor_tickets` |
| points | `league_point_adjustments` (+ columnas `coherence_score`, `league_points`, `current_streak` en `trainee_profiles`) |
| phasecontracts | `phase_contracts` |
| academy | `academia_recommendations` (Prisma) + las 8 tablas Supabase-directo |

**Hub del modelo:** `trainee_profiles` — 23 columnas escalares y **22 tablas hijas**. `users` le sigue con 19. Todo lo demás cuelga de esos dos.

---

## 2. Los flujos que mandan (hot paths medidos en código)

Del análisis de las ~190 rutas del backend y de la app RN:

1. **`GET /api/v1/habit-tracks/today`** — el endpoint más golpeado del sistema: lo llama la pantalla Rutina, la Home, el reconcile de notificaciones en cada foreground, **y un monitor "Enforcer" cada 30 segundos** (`useEnforcerMonitor`). Costo actual: **14–16 queries por request** (perfil, unlocks, tracks con include, evidencias por join polimórfico manual, 8 lecturas en `Promise.all`, y updates N+1 fire-and-forget para expirar tracks). El catálogo (`habits`, `habit_schedules`, `habit_guides`) se relee entero en cada request de cada usuario — **cero caché**.
2. **`GET /api/v1/home`** — "god endpoint": 25–30 queries por render. Internamente vuelve a ejecutar `getTodayTracks` completo. Lo piden 5 consumidores distintos (3 de ellos solo quieren `programDay`), incluido un popup que lo pide **en cada cambio de ruta saltándose la caché**.
3. **`GET /api/v1/personal-habits/today`** — también cada 30 s por el mismo monitor.
4. **`GET /api/v1/ranking`** — llama `general_ranking_scores()`: una función SQL que recorre **todos** los aprendices ACTIVE con 7 días de hábitos + rocas + progreso de cursos, **en cada request, sin caché**. Nació precisamente porque la versión anterior agotaba conexiones.
5. **Chat** — sin realtime en el backend REST: la bandeja se sondea, y `GET /conversations` hace un **`COUNT` de no-leídos por conversación** (N+1) contra `messages`, que con el chat GLOBAL crece sin límite.
6. **Cron `event-reminders` cada 5 minutos** — 3 queries por candidato en bucle secuencial (N+1 documentado en el propio código).
7. **Crons nocturnos** (`daily-reset`, `coherence-score`): bucles seriales por aprendiz (~6 queries × N) y por célula; `daily-reset` regenera los `habit_tracks` del día para todo el padrón.
8. **Validación IA**: async + polling — el patrón está bien y se preserva (CLAUDE.MD §2). El polling real del móvil es V90 cada 1,5 s hasta 60 s.

**Agravante de infraestructura:** `DATABASE_URL` con `pgbouncer=true&connection_limit=1` en serverless. 25–30 queries serializadas por request de Home sobre 1 conexión por lambda: ese es el techo de latencia real hoy, no Postgres.

---

## 3. Hallazgos para la normalización (lo que el diagrama marca en rojo punteado)

### 3.1 Relaciones sin FK real (integridad no garantizada por la BD)

| Origen | Destino lógico | Mecanismo actual |
|---|---|---|
| `evidence.related_entity_id` | `daily_rocks` \| `habit_tracks` \| `spirit_tracks` \| `personal_habit_tracks` | **Polimórfico** por `related_entity_type` (enum de 6 valores). Sin FK, sin CASCADE, joins manuales con `IN (...)` |
| `enforcer_events.related_entity_id` | `daily_rocks` \| `habit_tracks` | Ídem (columna `String`, ni siquiera usa el enum) |
| `testimonios.wall_post_id` | `wall_posts` | Columna suelta con índice, sin FK |
| `account_requests.reviewed_by_id` | `users` | Deliberado (sobrevivir al borrado del revisor) — el schema lo documenta |
| `personal_habit_edit_log.personal_habit_id` / `.trainee_profile_id` | `personal_habits` / `trainee_profiles` | Log sin FKs |
| `academia_recommendations.leccion_id` / `.curso_id` | `lecciones` / `cursos` | Imposible tener FK: viven fuera de Prisma. Se copian los títulos (denormalización defensiva) |
| `calendar_events.course_id` | `cursos` | Ídem |
| `spirit_tracks.day` | `spirit_audios.day` | Join por número de día, sin FK |

### 3.2 Duplicaciones y paralelismos estructurales

- **Dos sistemas de hábitos casi idénticos:** `habits`+`habit_tracks`+`trainee_habit_preferences`+`trainee_weekly_habit_days` vs `personal_habits`+`personal_habit_tracks`+`personal_habit_weekly_days` — mismas máquinas de estado, mismas columnas `pending_*` (cambio de horario programado) repetidas en ambos.
- **Tres contadores de "puntos/score" en `trainee_profiles`** (`coherence_score`, `league_points`, `current_streak`/`longest_streak`) + un cuarto ranking calculado al vuelo (`general_ranking_scores()`) + `league_points` **también en `mentor_profiles`**. Solo `league_points` tiene bitácora (`league_point_adjustments`); `coherence_score` se recalcula por cron sin historial.
- **Cuatro variantes de evidencia/media:** `evidence` (polimórfica), `onboarding_media`, `wall_post_media`, y las columnas `media_*` embebidas en `messages`. Cuatro formas distintas de guardar lo mismo (bucket+path+mime+bytes).
- **El bug documentado de URLs:** `evidence.file_url` guarda `getPublicUrl()` de un bucket **privado** — URLs que responden 403; la app re-firma parseando el path a mano. `habit_guide_attachments` ya corrigió el patrón (guarda `storage_path`, firma al leer): la regla correcta existe pero solo en la tabla más nueva.
- `weekly_rocks.critical_action_1/2/3` — grupo repetido (violación 1FN clásica).
- `onboarding_answers` con 5 columnas de valor (`value_text`, `value_number`, `value_bool`, `value_scale`, `value_json`) — EAV híbrido.
- Horarios como `String` (`trigger_time`, `limit_time` "HH:mm") en 4 tablas, comparados como texto.
- `academia_recommendations.date` es `String` YYYY-MM-DD; `shadow_mirror_reports.week_start` también `String`.

### 3.3 Índices ausentes en el hot path

- **`habit_schedules`: CERO índices** — la tabla que el cron de medianoche y `getTodayTracks` consultan decenas de veces por `(habit_id, start_day, end_day, day_type)`.
- **`habits`: CERO índices** — se filtra por `is_active`/`category` y ordena por `display_order` en cada request.
- `trainee_profiles`: sin índice en `coherence_score` ni `league_points` — las columnas de `ORDER BY` del ranking.
- `evidence`: ningún índice cubre `(validated_by_ai, type, timestamp_upload)` — el barrido del cron de IA.
- `conversations`: sin índice en `type` (el `findFirst type='GLOBAL'` de cada arranque de chat).
- `knowledge_base`: filtros por `metadata->>'kind'` (JSONB) sin GIN; el índice vectorial vive en SQL suelto, fuera del schema.

### 3.4 Tablas que crecen sin política de retención

`messages` (chat global = todos los usuarios), `evidence`, `habit_tracks` (~30 filas/aprendiz/día generadas por cron), `personal_habit_tracks`, `league_point_adjustments`, `notifications` (se acota en lectura, nunca se purga), `event_reminders` (fila por usuario × evento × ocurrencia), `enforcer_events`, `radar_entries`, `habit_preference_changes`, `personal_habit_edit_log`. Única retención existente: `renasia_messages` (cron semanal).

### 3.5 La app móvil escribe directo a Postgres (RLS), saltándose la API

Dominios **enteros** fuera del backend: el check-in Radar (`radar_entries` INSERT/SELECT directo), todo el guardado del onboarding (`onboarding_answers`, `onboarding_state`, `onboarding_media`, `variables_90_recordings` con upserts), progreso de cursos (`leccion_progreso` siempre directo), `testimonios`, y lecturas de `users`/`trainee_profiles`. Además, subida directa a Storage en 6+ buckets y 3 canales Realtime (`messages`, `wall_posts`).

**Consecuencia para la migración:** cambiar el esquema de esas tablas rompe la app instalada sin tocar el backend. La BD nueva tiene que decidir explícitamente qué camino queda (todo por API, o RLS como contrato versionado).

### 3.6 Otros hallazgos de contorno

- ~37 scripts SQL manuales (`scripts/`, `scripts/aplicados/`) aplicados a producción fuera de `prisma migrate`; tablas como `habit_guides` no tienen migración Prisma. El estado real de producción ≠ migraciones del repo. **Verificar contra `information_schema` antes de hacer el baseline de Flyway.**
- 2 de 9 crons no están programados en `vercel.json` (`spirit-audio-unlock`, `coherence-group-score`).
- `GET /spirit-audio/status` **escribe en cada GET** (avanza el desbloqueo perezosamente).
- Ajuste de puntos: `UPDATE ... RETURNING` del saldo y el `INSERT` de la bitácora **sin transacción** entre sí.
- La bandeja de notificaciones es local al dispositivo (AsyncStorage): no hay tabla de entregas; cambiar de teléfono pierde el historial.
- `columna isBlocking` de `habits`: deprecada, nadie la lee (documentado en el schema).

---

## 4. Lo que la BD antigua hace BIEN (preservar, no "arreglar")

- **Claves únicas de negocio correctas y consistentes**: `(trainee_profile_id, habit_id, execution_date)` en tracks, `(trainee_profile_id, phase)` en contratos, `(master_rock_id, week_number)`, `(trainee_profile_id, date, type)` en journal, `(user_id, question_key)` en respuestas — la idempotencia de los crons y upserts descansa en ellas.
- **Keyset pagination** en muro y chat (`createdAt < cursor`), con índices que la cubren.
- **Idempotencia atómica de puntos**: `updateMany` con guarda `awarded_points = 0`.
- **Separación perfil/rol en 5 tablas** (no herencia con discriminador) — ya alineado con el diseño Java (§5.3.2 de CLAUDE.MD).
- Auditorías donde importan: `role_change_audits`, `league_point_adjustments`, `habit_preference_changes` (con la nota de que les faltan FKs o retención, no existencia).
- Los comentarios del schema documentan el *porqué* de cada decisión rara — ese conocimiento se traslada al diseño nuevo, no se descarta.

---

## 5. Estado del trabajo (fase BD)

- [x] **Paso 1 — ER de la BD antigua**: [`ER_BD_ANTIGUA.drawio`](ER_BD_ANTIGUA.drawio) + este análisis.
- [x] **Paso 2 — Auditoría formal + normalización 1FN–5FN**: [`AUDITORIA_REDISENO_BD.md`](AUDITORIA_REDISENO_BD.md) §1–§2 y §6 (35 problemas identificados con severidad).
- [x] **Paso 3 — Diseño de la BD nueva** (propuesta v1, según las instrucciones de Luis/Ricardo del 2026-08-24: nombres en español, RBAC, normalización hasta 5FN): [`AUDITORIA_REDISENO_BD.md`](AUDITORIA_REDISENO_BD.md) §3–§12 + DDL en [`sql/BD_NUEVA_V1.sql`](sql/BD_NUEVA_V1.sql). **Pendiente de aprobación** + 7 puntos [PENDIENTE-CONFIRMAR] (ver §15 de la auditoría).
- [ ] Paso 4 — Al aprobar: fragmentar el DDL en migraciones Flyway por módulo (F0/F1 del plan de adopción, §14 de la auditoría) y registrar las decisiones en `MODULOS_A_AVANZAR.md` §8.
