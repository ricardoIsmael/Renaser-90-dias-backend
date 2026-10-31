# Tasks: Administración decide si el mentor y el líder ven el Código Renaser

**Input**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/api.md`. Estado: **[x]** hecho y probado · **[ ]** pendiente.
Reglas: sin tablas ni migración nuevas (V133 no se usa); D-299; commits por paso, en castellano y sin coautoría; sin `verify` completo ni emulador (máquina compartida).

## Phase 1: Setup

- [ ] T001 Anotar en `docs/MODULOS_A_AVANZAR.md` §8 la decisión D-299 (al final de la tabla, tras D-294) y no tocar `V*.sql`

## Phase 2: Foundational (bloquea todo)

- [ ] T002 `shared/domain/ClavesDeFuncionalidad.java`: `RADAR_EN_FICHAS = "radar.fichas"` (la nombran `habits`, `leadership` y `plataforma`)
- [ ] T003 `plataforma/domain/model/catalogo/DefinicionDeFuncionalidad.java`: sumar `apagadaPorDefectoPara` (Set<UserRole>, copia inmutable, vacío por omisión) y `seEsconde` (boolean); el constructor de 9 argumentos se conserva para no tocar las pruebas existentes
- [ ] T004 `plataforma/domain/model/estado/EvaluacionDeFuncionalidades.java`: sin fila, si `apagadaPorDefectoPara` contiene el rol del actor está apagada (el rol se lee solo cuando hace falta); con `seEsconde` el resultado es `noDisponible` (en el apagado manual también)
- [ ] T005 `EntradasDelCatalogo.java`: `radar.fichas` («Código Renaser en las fichas», área «Programa», roles MENTOR, TRAINEE_GUIDE, MENTOR_LEAD, `apagadaPorDefectoPara` los mismos, `seEsconde`) + nota en el javadoc; `CatalogoDeFuncionalidadesTest` suma la clave y la excepción de «Soporte TI figura en todas»
- [ ] T006 `plataforma/domain/model/catalogo/CodigoRenaserEnFichas.java` (puro): `ROLES`, `estadoDe(fila)` → visible / oculto / mixto, `cambioHacia(visible, anterior, motivo)` → `CambioDeFuncionalidad`; prueba unitaria `CodigoRenaserEnFichasTest`
- [ ] T007 `Permission.MANAGE_RADAR_VISIBILITY` (+ `UserRoleTest`: MENTOR_LEAD no lo tiene)

## Phase 3: User Story 2 — Apagado, el mentor y el líder no ven nada (P1) 🎯 MVP

**Prueba independiente**: con el interruptor apagado, las tres lecturas dan 404 al mentor y al líder y 200 a Administración.

- [ ] T008 [US2] `AcompanamientoDeAlumnoService.codigoRenaserDeAlumno`: `interruptores.exigirPrendidaPara(RADAR_EN_FICHAS, actor)` después de `requireAcompananteVigente` y antes de leer; `AcompanamientoDeAlumnoServiceTest` (apagado: no lee; encendido: como hoy; el 403 gana al 404)
- [ ] T009 [US2] `RadarDePersonaService.radarDe` y `radarDeUnaPersona`: lo mismo después de `acceso.requireLiderazgoActivo`; `RadarDePersonaServiceTest` y los fixtures `BancoDeCumplimiento` / `CumplimientoDePrueba`
- [ ] T010 [US2] `CodigoRenaserSoloPorLasTresLecturasTest` (ArchUnit): solo esas clases dependen de `CodigoRenaserDePersonaFinder`, `ConsultarCodigoRenaserDeAlumnoUseCase` y `LecturaDelRadar`

## Phase 4: User Story 1 — Administración lo enciende y apaga (P1)

**Prueba independiente**: `PUT {"visible": true}` con ADMIN rige en el acto; con MENTOR, 403.

- [ ] T011 [US1] `plataforma/application/services/EscrituraDeEstado.java` (extraído de `ConsolaDeFuncionalidadesService`: siguiente versión, guardar con bloqueo optimista, auditar, publicar el evento); la consola lo usa sin cambiar su constructor ni su comportamiento (`ConsolaDeFuncionalidadesServiceTest` sin tocar)
- [ ] T012 [US1] `FuncionalidadesEnConsola`: `porRol` y el «antes» de la auditoría respetan `apagadaPorDefectoPara` cuando no hay fila
- [ ] T013 [US1] Puertos `ConsultarVisibilidadDelCodigoRenaserUseCase` y `CambiarVisibilidadDelCodigoRenaserUseCase` + `VisibilidadDelCodigoRenaserService` (guardia ADMIN/ALCHEMIST/IT_SUPPORT activos, idempotente, hasta 3 intentos si otro cambio llega en el medio, motivo fijo «Cambio desde Administración (Código Renaser en las fichas)»); `VisibilidadDelCodigoRenaserServiceTest`
- [ ] T014 [US1] `CodigoRenaserEnFichasController` (`GET/PUT /api/v1/admin/codigo-renaser-en-fichas`, `@RequiresPermission(MANAGE_RADAR_VISIBILITY)`, `@Funcionalidad("admin")`, `no-store`) + request y response; `CodigoRenaserEnFichasControllerTest` (`@WebMvcTest`: 200 de ADMIN, ALQUIMISTA y Soporte TI; 403 de TRAINEE, MENTOR, guía, líder y cuenta suspendida; 400 sin `visible`; el rol no se inyecta)
- [ ] T015 [US1] `FuncionalidadCambiadaNotificationListener`: el título solo dice «Soporte TI cambió…» si lo cambió Soporte TI; su prueba

## Phase 5: User Story 3 y 4 — Encendido como hoy; queda constancia (P1/P2)

- [ ] T016 [US3] [US4] `CodigoRenaserEnFichasIT` (Testcontainers): apagado de fábrica (mentor y líder 404 en las tres rutas; Administración 200; la propia persona 200); Administración enciende (rige en el acto, `GET` con `ultimoCambio`); encendido (mentor y líder 200 con todos los registros, también los hechos mientras estuvo apagado); apagar otra vez; dos filas en `cambios_auditados` con quién, cuándo, antes y después y que no se puedan editar; sin ninguna notificación a la persona; mixto desde la consola de Soporte TI; rol personalizado que hereda de Mentor; rechazo 403 de la cuenta suspendida
- [ ] T017 `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`, `RutasCubiertasPorElFiltroTest`, `UserRoleTest`, `EvaluacionDeFuncionalidadesTest`, `MisFuncionalidadesServiceTest` en verde

## Phase 6: App

- [ ] T018 [US2] `features/plataforma`: `CLAVES.radarFichas`; `CodigoRenaserDelAlumno` no se dibuja ni pide si no está disponible/prendida; Jest (las tres fichas)
- [ ] T019 [US1] `features/admin`: API + hook + `CodigoRenaserEnFichasAdminScreen` (`Interruptor`, confirmación, `ConfirmacionEnLinea`, `tacto.logro()`, «Último cambio», «Reintentar», estado mixto) y la fila en «Más opciones» solo con `canAdminister`; el atrás sube un nivel; Jest
- [ ] T020 `AGENTS.md` (entrada 2026-10-31 y «Corregido 2026-10-31» en la spec 012), Maestro `.maestro/codigo-renaser-en-fichas/` (apagado y encendido, con el mentor), `tsc --noEmit` y Jest

## Phase 7: Polish

- [ ] T021 Notas «Corregido 2026-10-31» en `specs/012-*` si existe y en el catálogo de `specs/002-soporte-ti-interruptores/`; `graphify update .` en los dos repos

## Dependencias

T002–T007 → T008–T010 (lecturas) y T011–T015 (cambio) en paralelo → T016/T017 → app (T018–T020). MVP = Phase 2 + Phase 3 (apagado de fábrica protegido en el servidor), pero la entrega es el conjunto.
