# Implementation Plan: Formaciones — Escuela, Diplomado y Certificación Renaser

**Branch**: `022-formaciones` | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)

**Input**: `specs/022-formaciones/spec.md` (con las «Decisiones del dueño (31/10)») y
`~/.config/renaser-e2e/formaciones/decisiones-del-dueno-2026-10-31.md`.

## Summary

Renaser pasa de un programa único de 90 días a **tres formaciones fijas** (Escuela, Diplomado, Certificación) con los mismos roles pero **aisladas**. Esta spec es **la base**:
el modelo de formación y de pertenencia, la duración configurable (90 / 365) con los días y las fases derivados de ella, la sesión que informa la formación, el selector del
personal con varias, el centro «Formaciones» de Administración, el traslado de un aprendiz y la migración (todo lo existente pasa a la Escuela). Además deja **un único punto**
para «la formación de la persona» y **uno** para filtrar, y aplica el aislamiento a **personas y grupos**; el aislamiento del contenido (hábitos, Muro, cursos, eventos,
testimonios, ranking, chats de comunidad) lo hacen las specs 023 y 024 sobre ese mismo punto.

Enfoque técnico: un catálogo de 3 filas (`formaciones`) + columnas en lo que ya existe (`participantes_programa`, `celulas`) + dos tablas que no se pueden evitar
(`formaciones_personal` N:M con la activa, y `participaciones_anteriores`, el historial). La regla «qué formaciones ve quien actúa» vive en
`users.api.AlcanceDeFormacionFinder`; el SQL la aplica con `shared.infrastructure.persistence.FiltroDeFormacionSql`. La app lee la formación en `/auth/me` y en
`/me/formacion`, y solo cambia la forma de unas pocas pantallas (selector en Yo, nombre de la formación en Hoy, centro «Formaciones» en Administración).

## Technical Context

**Language/Version**: backend Java 25 / Spring Boot (pom), Spring Modulith; app Expo SDK 57 / React Native / TypeScript estricto

**Primary Dependencies**: Spring Data JPA + `JdbcClient`, Flyway, ArchUnit, Testcontainers; en la app zod (esquemas tolerantes), `HojaDesdeAbajo`, `ControlSegmentado`, `Presionable`

**Storage**: PostgreSQL (schema `renaser`), migraciones `V125` a `V129`

**Testing**: JUnit 5 + Mockito (dominio y servicios sin Spring), `*IT` con Testcontainers (aislamiento), `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`,
`FuncionalidadDeclaradaTest`; en la app Jest + `tsc --noEmit`; flujos de Maestro escritos para el E2E posterior

**Target Platform**: servidor Linux (EC2) + app Android (el APK 1.6.0 publicado debe seguir funcionando)

**Project Type**: mobile-app + web-service (dos repos, una rama `022-formaciones` en cada uno)

**Performance Goals**: el alcance de formación se resuelve con **una** consulta por PK por pedido; ninguna lista nueva es N+1

**Constraints**: compatibilidad hacia atrás (campos nuevos aditivos y opcionales; un APK anterior ignora lo nuevo y la app nueva tolera un servidor sin la formación → Escuela);
cero cambios visibles para la Escuela; el día de cada persona sigue siendo el de **su** zona

**Scale/Scope**: cientos de usuarios, 3 formaciones; ~25 consultas de personas/grupos a filtrar en todo el producto (esta spec cubre las de personas y grupos), 1 catálogo y 2 tablas

## Constitution Check

*GATE: pasa antes de la investigación y se vuelve a revisar al final del diseño.*

| Principio | Cumple | Cómo |
|---|---|---|
| I. Dependencias al dominio | Sí | La regla de duración, fases y traslado vive en `users.domain` (sin Spring). `users.api` es lo único que ven otros módulos. El filtro SQL vive en `shared.infrastructure` (módulo OPEN). Los controllers solo invocan un caso de uso. El traslado avisa a `community` y `points` por **evento** (`AprendizTrasladadoDeFormacionEvent`): `users` no importa a nadie, así que no hay ciclos. |
| II. No se inventan reglas | Sí | Las dudas Q1–Q11 las contestó el dueño; lo que no listó es la opción A. Los supuestos de diseño están escritos en la spec. |
| III. Pruebas en verde | Sí | Unitarias de dominio y de servicio, de controller, IT de aislamiento, `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`. Cada endpoint: 403 sin permiso y `SUSPENDED`. Lo diario se prueba con el reloj en la madrugada UTC. |
| IV. La base guarda historia | Sí | Migraciones con cabecera; `participaciones_anteriores` es append-only; `ajustes_dia_programa` y los registros no se reescriben; el reinicio de puntos es un **ajuste** del ledger, no un borrado. Un dato derivable no se duplica: `celulas.formacion` es la única fuente de la formación de un grupo; la de una persona sale de `participantes_programa` (aprendiz) o `formaciones_personal` (personal). |
| V. El tiempo es del participante | Sí | `diaProgramaDerivado` sigue siendo función pura de (inicio, hoy en su zona, ajuste) y suma la **duración propia** como tope; el reloj entra por `Clock`. |
| VI. Los cinco tabs | Sí, con autorización | El pedido del dueño del 31/10 autoriza las excepciones mínimas (selector en Yo, nombre de la formación en Hoy, centro en Administración); se anotan en `AGENTS.md` del frontend con «lo que cambia y nada más». |
| VII. Documentar en el mismo cambio | Sí | D-295 en `docs/MODULOS_A_AVANZAR.md`, `docs/MODULO_USERS.md`, `AGENTS.md` de la app, `docs/BITACORA_ERRORES.md` si aparece algo. |

### Complexity Tracking

| Desviación | Por qué hace falta | Alternativa más simple descartada |
|---|---|---|
| Dos tablas nuevas además del catálogo (`formaciones_personal`, `participaciones_anteriores`) | El personal tiene **varias** formaciones (N:M, con una activa) y un traslado deja **historia** (la fila de `participantes_programa` es 1:1 con la persona y se reinicia). Ninguna columna existente puede guardar «varias» ni «lo que fue». | Un `text[]` en `usuarios` (sin integridad referencial ni quién asignó) y un JSON de historial (no consultable, rompe 1FN). |
| `celulas.formacion` (y no `cohortes.formacion`) | Los grupos son la unidad de aislamiento (chat, semáforo, alcance del mentor) y se filtran en muchas consultas: una columna directa evita un JOIN en cada una. Es la **única** fuente (no se repite en cohortes). | `cohortes.formacion` y un JOIN en todas las consultas de grupo. |
| Todo el dominio de formaciones dentro de `users` (no un módulo Modulith nuevo) | `participantes_programa` es de `users`; un módulo `formaciones` aparte tendría que escribir esa tabla (prohibido) o crear un ciclo `users ↔ formaciones`. Las cifras del centro de Administración (que sí juntan varios módulos) se arman en `leadership`, que ya depende de todos. | Módulo `formaciones` con eventos en ambos sentidos. |
| `duraciones_dias smallint[]` en el catálogo | Son 1–4 enteros que se leen y escriben siempre juntos y nadie los une con otra tabla; una tabla de opciones sería una tabla innecesaria. | Tabla `formacion_duraciones`. |

## Project Structure

### Documentation (this feature)

```text
specs/022-formaciones/
├── spec.md              # spec + decisiones del dueño (31/10)
├── plan.md              # este archivo
├── research.md          # decisiones de diseño y alternativas
├── data-model.md        # tablas, columnas, invariantes, transiciones
├── quickstart.md        # cómo validar de punta a punta
├── contracts/
│   └── formaciones-api.md   # endpoints nuevos o cambiados
└── tasks.md             # lo genera /speckit-tasks
```

### Source Code (repository root)

```text
# Backend (Renaser-90-dias-backend)
src/main/resources/db/migration/
├── V125__formaciones_catalogo_y_pertenencia.sql  # catálogo + participantes_programa.formacion/duracion_dias + celulas.formacion; todo → Escuela
├── V126__formaciones_del_personal.sql             # N:M del personal (con la activa); el personal existente → Escuela
├── V127__participaciones_anteriores.sql           # historial de traslados (append-only)
├── V128__dia_del_programa_hasta_365.sql           # los topes de 90 que dependen del día pasan a la duración máxima
└── V129__…                                         # reservada (no se usa si no hace falta)

src/main/java/com/renaser/os/
├── shared/domain/                 Formacion, AlcanceDeFormacion, ObjetoDeOtraFormacionException
├── shared/infrastructure/persistence/  FiltroDeFormacionSql
├── users/api/                     AlcanceDeFormacionFinder, FormacionesFinder, FormacionDeLaSesion, AprendizTrasladadoDeFormacionEvent
├── users/domain/model/formacion/  DuracionDelPrograma, FasesDelPrograma, ReglasDeTraslado, ParticipacionAnterior
├── users/application/             casos de uso (sesión, elegir activa, asignar al personal, configurar, trasladar) + puertos out
├── users/infrastructure/          controllers (/me/formacion, /admin/formaciones, …/formacion) y adaptadores JDBC
├── community/…                    listener del traslado; celulas.formacion; validación FR-010; recepción por formación; grupos por alcance
├── points/…                       listener del traslado (reinicio de puntos por ajuste)
├── leadership/…                   centro «Formaciones» (cifras) y listas del líder por alcance
└── chat/ mentoring/ …             directorio y búsquedas por alcance

# App (Renaser-90-dias-frontend-)
src/features/formaciones/
├── api/ (formacionesApi, formacionesSchemas)   hooks/ (useFormacionDeLaSesion)   context/ (FormacionContext)
├── components/ (SelectorDeFormacion, NombreDeLaFormacion, TarjetaDeFormacion)
├── screens/ (FormacionesAdminScreen, FormacionDetalleScreen)
├── utils/ (fasesDeLaDuracion, etiquetaDeDuracion)   types/   __tests__/
.maestro/formaciones/*.yaml
```

**Structure Decision**: backend hexagonal dentro de `users` (dueño de las tablas de persona y participación) con eventos hacia `community` y `points`; el centro de Administración
(cifras de varios módulos) en `leadership`; la app por feature `src/features/formaciones/` y retoques mínimos en Yo, Hoy y Administración.

## Post-Design Constitution Re-check

El diseño de `data-model.md` y `contracts/` no agrega ninguna tabla más de las justificadas arriba, no rompe ningún principio y mantiene `ArchitectureTest` (users no importa otros
módulos; community y points ya importan `users.api`). Sin violaciones nuevas.
