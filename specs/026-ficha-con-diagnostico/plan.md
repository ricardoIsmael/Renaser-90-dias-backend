# Implementation Plan: Ficha con diagnóstico (spec 026)

**Branch**: `026-ficha-con-diagnostico` | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md) (con «Decisiones del dueño (31/10)») | **Base**: `022-formaciones`

## Resumen
El diagnóstico (el formulario del dueño) reemplaza al Mapa de Renacimiento. Se monta sobre el motor de onboarding (un flujo más), con borrador/publicación por foto (`fichas_publicadas`), administración (CRUD, importación, quitar con retiro), objetivos que nacen de las respuestas y SER que lee todo.
El Mapa se retira de la app y del servidor sin borrar datos; `ROCKS_EXIGIR_MAPA_ACTIVO` deja de tener efecto y la app anterior no queda bloqueada.

## Constitution Check
| Principio | Cumple | Cómo |
|---|---|---|
| I Dependencias al dominio | Sí | Reglas en `onboarding.domain.model.diagnostico` (sin Spring). `rocks` implementa `onboarding.api.PreparadorDeObjetivos` (la dependencia sigue yendo de `rocks` a `onboarding`, sin ciclo); `users` llama a `onboarding` solo por `users.api.FichaAlAceptar` que `onboarding` implementa; `rag` lee por `onboarding.api.DiagnosticoDeLaPersonaFinder`. Mentor y líder solo delegan en ese finder. |
| II No se inventan reglas | Sí | Q1–Q15 respondidas; lo que no, opción A. Los supuestos van escritos en la spec («Cómo quedó»). |
| III Pruebas | Sí | Unitarias de dominio y servicios, IT de migración y de aislamiento, `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`. |
| IV Historia | Sí | `fichas_publicadas` append-only; las respuestas nunca se borran por retirar una pregunta; el Mapa no se borra. |
| VI Cinco tabs | Sí, con autorización | El pedido del dueño autoriza retirar el Mapa de Hoy, Plan y Yo y mostrar el diagnóstico; se anota en `AGENTS.md`. |

### Complexity Tracking
| Desviación | Por qué | Alternativa descartada |
|---|---|---|
| Una tabla nueva (`fichas_publicadas`) | La foto inmutable que exige FR-031/FR-044 | Versionar cada pregunta (más tablas, joins por versión en cada lectura) |
| `formaciones text[]` en la pregunta | Mismo criterio de `formaciones.duraciones_dias`: se lee y escribe junto con la pregunta, 0–3 valores | Tabla `pregunta_formaciones` |
| `precarga` y `alimenta` como columnas de texto con CHECK | Son atributos de la pregunta, no entidades | Tablas de destinos |

## Estructura
```text
Backend (src/main/java/com/renaser/os/)
├── onboarding/api/                 FichaDeLaPersonaFinder/UseCase (hecho), DiagnosticoDeLaPersonaFinder, PreparadorDeObjetivos
├── onboarding/domain/model/diagnostico/   reglas puras (ver data-model.md)
├── onboarding/application/services/       DiagnosticoDeLaPersonaService, AdministrarDiagnosticoService, ImportarDiagnosticoService, LecturaDelDiagnosticoService
├── onboarding/application/ports/{in,out}/diagnostico/
├── onboarding/infrastructure/adapter/in/rest/diagnostico/  persona, admin, importación, lectura
├── onboarding/infrastructure/adapter/out/persistence/diagnostico/  JDBC (borrador, fotos, respuestas)
├── rocks/…  RocaMaestraService sin el Mapa; origen/confirmada; adaptador de PreparadorDeObjetivos
├── rag/…    ConsultarDiagnosticoPort + línea del prompt + herramienta consultar_mi_diagnostico
├── mentoring/ leadership/   GET …/diagnostico de solo lectura
└── db/migration  V143 (hecha) V144 V145 V146 V147

App (src/features/diagnostico/ y retoques mínimos en Hoy, Plan, Yo, Administración, onboarding)
```
