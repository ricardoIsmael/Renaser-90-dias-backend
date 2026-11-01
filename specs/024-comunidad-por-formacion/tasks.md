# Tasks: Comunidad por formación — Muro, cursos, Tribu, ranking, testimonios y eventos

**Input**: `specs/024-comunidad-por-formacion/` (spec, plan, research, data-model, contracts/comunidad-api.md, quickstart) sobre `specs/022-formaciones/`.
**Tests**: obligatorios (constitución III): unitarias de dominio y servicios, `ComunidadPorFormacionIT` de aislamiento (pares de formaciones) y de migración, `ArchitectureTest`,
`EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`.
**Rutas**: `B:` = `src/main/java/com/renaser/os/`, `BT:` = `src/test/java/com/renaser/os/`, `BR:` = `src/main/resources/db/migration/`, `A:` = app `src/`.

## Phase 1: Datos

- [x] T001 `BR:V137__comunidad_por_formacion_contenido.sql`: `formacion` en `publicaciones_muro`, `testimonios`, `eventos`, `cursos` (+ índices) y `dia_desbloqueo` hasta 365 en `cursos`/`secciones_curso`. Cabecera con el porqué.
- [x] T002 `BR:V138__comunidad_por_formacion_tribu.sql`: `conversaciones.formacion`, una GLOBAL por formación (índice único parcial), `formaciones.tipos_de_comunidad text[]`.
- [x] T003 `BR:V139__ranking_por_formacion.sql`: `ranking_aprendices.formacion` (el corte diario es historia) + índice.

## Phase 2: Fundamentos (dominio y puntos únicos)

- [x] T004 [P] `B:shared/domain/FormacionDelContenido` (`pedida`, `paraLeer`, `paraCrear`) y `TipoDeComunidad`. Pruebas `BT:shared/domain/FormacionDelContenidoTest`.
- [x] T005 `B:users` — `ComunidadDeLaFormacionFinder` (api), `TiposDeComunidadPort` + adaptador JDBC, `ConfigurarComunidadDeLaFormacionUseCase` y servicio (ADMIN/ALCHEMIST activos).
- [x] T006 [P] `B:chat/application/services/VisibilidadPorFormacion` (la regla única de quién ve una conversación). Pruebas `BT:chat/.../VisibilidadPorFormacionTest`.
- [x] T007 Dominio con `formacion`: `Publicacion`, `Testimonio`, `Evento`, `Curso`, `Conversacion` (claves de soporte/directo con sufijo fuera de la Escuela), `PosicionRanking`, `SemanaDelRanking` (ids del podio por formación, los de la Escuela intactos). Pruebas `ConversacionPorFormacionTest`, `SemanaDelRankingPorFormacionTest`.

## Phase 3: US1 — Muro, comentarios, reacciones y testimonios (P1)

- [x] T008 `B:community` — `PublicacionMuroService`/`ComentarioMuroService`: cargar por alcance (lo de otra formación es inexistente), feed/ocultas/`mine`/`latest-author` por alcance, crear con `paraCrear`; puerto, adaptador y repositorio con `IN :formaciones`; `WallController` con `formation`.
- [x] T009 `B:community` — testimonios por formación (`crear` exige formación a Administración, `promover` hereda la de la publicación, `listarDestacados(actor, formacion)`).
- [x] T010 `BT:community/ComunidadPorFormacionIT` — Muro, comentarios/reacciones (404 igual que un inexistente), testimonios, último autor.

## Phase 4: US2 — Eventos y cursos (P1)

- [x] T011 `B:calendar` — eventos por formación: acceso (`requireEnSuFormacion`), lista/detalle/«Voy»/asistencia/«Próximo evento», audiencia y recordatorios dentro de la formación del evento, `CrearEventoCommand` con formación.
- [x] T012 `B:academy` — catálogo, bloqueados, curso/sección/lección/progreso, Clase diaria y porcentaje de cursos por formación; `CursoController ?formation`.
- [x] T013 `BT` — eventos y cursos en `ComunidadPorFormacionIT`.

## Phase 5: US3 — Tribu: comunidad por formación y tipos prendibles (P1)

- [x] T014 `B:chat` — una GLOBAL por formación, unión de la persona a la de su(s) formación(es), listar/crear directo/soporte/grupo por formación, nombres derivados por formación.
- [x] T015 `B:chat` — `VisibilidadPorFormacion` aplicada en mensajes, presencia (lectura), autorización, fotos, integrantes, compartir publicación, soporte paginado y caja/tarjeta/emergencia en soporte.
- [x] T016 `B:chat` — reacción al traslado (`AprendizTrasladadoDeFormacionChatListener` → `CambioDeComunidadAlTrasladarService`).
- [x] T017 `B:users`+`chat` — `PUT /admin/formaciones/{codigo}/comunidad/{tipo}` y `GET …/comunidad` (con cuántas conversaciones esconde cada tipo), `@RequiresPermission`/`@Funcionalidad` declarados.
- [x] T018 `B:chat` — ruta del aviso de un mensaje con `?formacion=` fuera de la Escuela.
- [x] T019 `BT` — Tribu, directos, compartir, tipos y traslado en `ComunidadPorFormacionIT`.

## Phase 6: US4 — Ranking y podio por formación (P2)

- [x] T020 `B:points` — candidatos y cortes con formación; `RankingService` ordena y numera por formación; caché con el alcance en la clave; `RankingGeneralFinder.alCorte(hasta, formacion)`; `RankingController ?formation`.
- [x] T021 `B:chat` — `PodioDeLaSemanaService`: un podio por formación en su comunidad general; vista previa por formación.
- [x] T022 `BT` — `RankingServiceTest` (numeración por formación, lectura acotada), `RankingGeneralAlCorteServiceTest`, `PodioDeLaSemanaServiceTest` (un podio por formación), caché por alcance, `ComunidadPorFormacionIT` (ranking).

## Phase 7: Pulido

- [x] T023 Migración: todo lo existente es de la Escuela (SC-009) — aserción en `ComunidadPorFormacionIT.loExistenteEsDeLaEscuela`.
- [x] T024 `docs/MODULOS_A_AVANZAR.md` D-301, `specs/024…/contracts/comunidad-api.md`, `AGENTS.md` de la app.
- [x] T025 App (rama `024-comunidad-por-formacion` del frontend): selector para Administración, `formation` en lecturas/creaciones, etiquetas con «Todas», Administración → Formaciones → Comunidad, ruta del aviso; Jest y flujos de Maestro escritos.
- [ ] T026 E2E en el emulador de los flujos de `.maestro/comunidad-por-formacion/` (los corre el agente del E2E, no esta rama).

## Dependencias y límites aceptados

- **Spec 023**: ocultar las pestañas Kilómetros y Gimnasio del ranking según los hábitos de la formación.
- El aviso push de un evento no cambia de ruta (`/eventos/{id}`); solo el chat añade `?formacion=` (FR-032).
- La presencia en vivo se filtra al leer quién está conectado, no al repartir el aviso.
- `PersonasConvocadasService` (quién responde a un evento) puede seguir listando a quien respondió y luego cambió de formación; no ve nada de otra formación porque el detalle pide la formación.

## Pruebas corridas (2026-11-01, con `pesado.sh`)

- Unitarias y de arquitectura (4037, 0 fallos): `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`, `RutasCubiertasPorElFiltroTest` y todo `*Test` de `chat`,
  `community`, `calendar`, `academy`, `points`, `shared`, `users`, `plataforma`, `notifications` y `leadership`.
- Integración, todas verdes: `ComunidadPorFormacionIT`, `FormacionesIT`, `GruposPorFormacionIT`, `CentroDeFormacionesIT` y todos los `*IT` de `chat`, `community`, `calendar` y `points`
  (con `PodioDeLaSemanaIT`, `SoportesPaginadosIT`, `RankingConAprendizSinProgramaIT`), más los de `notifications`, `plataforma` y `cuenta`.
- App: `npx tsc --noEmit` en 0 y Jest de `formaciones`, `ranking`, `community`, `eventos`, `academy`, `chat`, `mentor`, `admin`, `alarmas`, `navigation` y `screens` (220 suites, 1862 pruebas).
- Falta el `clean verify` completo, que corre el coordinador con todas las ramas juntas, y el E2E (T026).
