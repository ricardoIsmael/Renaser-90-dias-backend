# Tareas: Ficha con diagnóstico (spec 026)

Estado al 2026-10-31. `[x]` = hecho y con prueba; `[ ]` = pendiente. Los números de Flyway son V143–V147 y la decisión es D-303.

## Fase 1 — Qué ficha completa cada persona (base compartida con la 025)
- [x] T001 V143: columnas `ficha*` en `estado_onboarding`.
- [x] T002 `TipoDeFicha`, `EstadoOnboarding.conDiagnostico/asignarDiagnostico/quitarDiagnostico`, `FichaDeLaPersonaFinder/UseCase` y `FichaDeLaPersonaService`.
- [x] T003 `FichaAlAceptar` (025) implementada en `onboarding` (`FichaAlAceptarAdapter`).

## Fase 2 — Modelo de preguntas y versiones
- [x] T010 V144 (campos de pregunta, `CUADRICULA`, índice único de destinos), V145 (`fichas_publicadas`), V146 (`origen` de los objetivos), V147 (borrador del dueño).
- [x] T011 Dominio puro: `PreguntaDelDiagnostico`, `SeccionDelDiagnostico`, `FichaDelDiagnostico`, `ReglasDeRespuesta`, `ObjetivosDelDiagnostico`, `TextoParaLaPersona`.
- [x] T012 Puertos y adaptadores JDBC del borrador y de las versiones.

## Fase 3 — La persona (US1–US4)
- [x] T020 `DiagnosticoDeLaPersonaService` (abrir, guardar por pasos, terminar, resumen) y `LecturaDelDiagnosticoService`.
- [x] T021 `/api/v1/diagnostico/mio/**`.
- [x] T022 Trato de la persona desde el diagnóstico (`TratoDeLaPersonaService`).

## Fase 4 — Administración (US5–US7, US9)
- [x] T030 `EditarDiagnosticoService` y `QuitarPreguntasDelDiagnostico` (eliminar vs retirar, protegidas, dependientes).
- [x] T031 `PublicarDiagnosticoService` (vista previa, publicar con 409, versiones, plantilla, exportar).
- [x] T032 `ImportarDiagnosticoService` y `PlantillaDeImportacion` (todo o nada, añadir o reemplazar).
- [x] T033 `AsignarFichaService` (026-9 B).
- [x] T034 `/api/v1/admin/diagnostico/**` con el guardián del servicio además de `@RequiresPermission`.

## Fase 5 — Retirar el Mapa (US8)
- [x] T040 `MapaRenacimientoService` solo lectura; `MapaRetiradoException` y su 409; `RespuestaService` bloquea los flujos reservados.
- [x] T041 `ROCKS_EXIGIR_MAPA_ACTIVO` y `MapaActivoFinder` fuera; `RocaMaestraService` materializa los objetivos del diagnóstico «por confirmar».
- [x] T042 SER: `consultar_mi_diagnostico`, línea del prompt, compuerta de propuestas sin Mapa.

## Fase 6 — Quién lo lee (026-11)
- [x] T050 Mentor/guía: `/mentor/groups/{g}/learners/{u}/diagnostico`.
- [x] T051 Líder, Admin y Alquimista: `/leadership/groups/{g}/people/{p}/diagnostico` y `/leadership/people/{p}/diagnostico`.

## Fase 6b — Duración de la formación en las rocas (V156)
- [x] T055 `ProgresoParticipanteRocks.duracionDias`; `SemanaPrograma`/`MesPrograma`/`CurvaDelPlan`/`CalculadoraObjetivoMensual` con la duración de la persona (90 o 365).
- [x] T056 V156: CHECK de `rocas_semanales.numero_semana` a 53 y de `rocas_mensuales.numero_mes` a 12; `DuracionDelPlanTest` fija el máximo real.
- [x] T057 `RocasDelAprendizFinder.ultimaSemanaDelPrograma` para la herramienta de SER de «la semana siguiente».

## Fase 7 — Pruebas y cierre
- [x] T060 Pruebas unitarias de dominio, servicios, SER, mentor y líder; autorización negativa (403) y aislamiento.
- [ ] T061 `TZ=UTC ./mvnw -B clean verify` completo (migraciones V143–V147 contra Postgres, `DiagnosticoDeIngresoIT`, `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`).
- [ ] T062 Declaración de responsabilidad y consentimiento: la escribe el dueño; Administración la pega en el editor y publica.
- [x] T063 Decidido por el dueño (2026-11-01): el diagnóstico pregunta la meta medible (T070–T073) y la lectura del diagnóstico deja registro de consulta (T074–T076).

## Fase 8 — Dos decisiones del dueño (2026-11-01, V157)
- [x] T070 Ocho destinos nuevos de «alimenta» (`META_`/`UNIDAD_`/`PARTIDA_` de Cuerpo y Negocio, `META_`/`PARTIDA_RELACIONES`) con sus reglas de tipo y de número (Relaciones: entero del 1 al 10).
- [x] T071 V157: el CHECK de `alimenta` y ocho preguntas opcionales en el borrador sembrado, debajo del objetivo de cada eje.
- [x] T072 `ObjetivosDelDiagnostico.MetaDeclarada` → `RocaMaestra.delDiagnostico(…, meta, …)` (la meta no se borra al confirmar solo con el texto) → `ObjetivoDelMesService` (`DatosDelEje.delObjetivo`, `Magnitud.deLaUnidad`).
- [x] T073 App: los destinos en los esquemas y el editor, y la validación de la persona (Relaciones 1 a 10, Cuerpo/Negocio de cero o más).
- [x] T074 V157: `consultas_de_ficha.tipo` (`SEMANA` | `DIAGNOSTICO`), `semana_desde` NULL solo en el diagnóstico y los roles mentor, guía y Soporte TI solo ahí; `ConsultaDeFicha` con `TipoDeConsulta`.
- [x] T075 `DiagnosticoDeLaPersonaFinder.lecturaDe(persona, lector)` → `DiagnosticoConsultadoEvent` → `RegistroDeLecturasDelDiagnosticoService` (idempotente por id); los tres lectores (mentor, líder, Administración) lo usan; la propia persona y SER, no.
- [x] T076 El resumen de las 20:00 al mentor sigue leyendo solo `tipo = SEMANA`.
