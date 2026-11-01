# Implementation Plan: Hábitos y categorías por formación

**Branch**: `023-habitos-por-formacion` | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)

**Input**: `spec.md` con las «Decisiones del dueño (2026-10-31)` y `~/.config/renaser-e2e/formaciones/decisiones-del-dueno-2026-10-31.md`.

## Summary

Cada formación (Escuela, Diplomado, Certificación) tiene **su catálogo de hábitos y sus categorías**. Una persona ve y recibe solo los de la formación en
que hace su programa; Administración y Alquimista configuran cada formación (categorías, hábitos, copiar de una a otra). Todo lo de hoy pasa a ser de la
Escuela sin cambio visible.

Enfoque técnico, sobre la base de la spec 022 (`AlcanceDeFormacionFinder`, `FiltroDeFormacionSql`, `DuracionDelPrograma`):

- **Datos**: ninguna tabla de catálogo nueva. `categorias_habito` gana `formacion` e `icono`; `habitos` gana `formacion` (solo los del catálogo) con una
  llave compuesta `(formacion, categoria_clave)` que obliga a que un hábito esté en una categoría de SU formación; el título y la `clave_sistema` pasan a
  ser únicos por formación (V134). Una tabla de solo agregar guarda quién cambió qué (V135, FR-024). Los topes de día de los horarios y los desbloqueos
  llegan a 365 (V136).
- **Un solo punto por pregunta**: «qué hábitos le tocan a esta persona» sigue siendo `CatalogoAplicable` (spec 001), ahora también por formación; «qué hábito
  de flujo propio es el suyo» es `LoadHabitoPort.porClaveSistema(clave, formacion)`; «puede pedir este hábito por id» es `Habito.exigirQueSeaVisibleEn`.
- **Admin**: `GestionarCategoriasUseCase` / `ConsultarCategoriasAdminUseCase` (nuevos), `HabitoAdminService` con la formación en el alta y el filtro de la
  lista, y `CopiarHabitoService` («Copiar a otra formación»).
- **Persona**: `GET /habit-categories` entrega las dimensiones de Training de su formación; `GET /habits` ya viene filtrado.
- **Duración**: los topes de 90 que seguían escritos en hábitos (desbloqueo, `dia_inicio`, `HabitosDeUnDia`) leen la duración de la persona; el día se lleva a la
  escala base de 90 con `DuracionDelPrograma.enEscalaBase` donde indexa contenido (Audioterapia, Pastilla).

## Technical Context

**Language/Version**: Java 25 / Spring Boot (pom), Spring Modulith; app Expo SDK 57 / React Native / TypeScript estricto

**Storage**: PostgreSQL (schema `renaser`), migraciones `V134` a `V136`

**Testing**: JUnit 5 + Mockito (dominio y servicios sin Spring), `HabitosPorFormacionIT` (Testcontainers), `ArchitectureTest`,
`EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`; Jest + `tsc` en la app; flujos de Maestro escritos para el E2E posterior

**Performance Goals**: la generación diaria sigue leyendo el catálogo activo UNA vez por persona (3 formaciones × ~30 hábitos); ninguna lista nueva es N+1

**Constraints**: compatibilidad hacia atrás (campos aditivos; el cliente anterior sin `formation` cae en la Escuela; la app nueva contra un backend anterior
usa las cuatro categorías de siempre); cero cambios visibles para la Escuela

## Constitution Check

| Principio | Cumple | Cómo |
|---|---|---|
| I. Dependencias al dominio | Sí | `CategoriaHabito`, `IconoDeCategoria`, `CodigoDeCategoria` y las reglas de `Habito` viven en `habits.domain` sin Spring; los controladores solo invocan un caso de uso; `habits` consume `users.api.AlcanceDeFormacionFinder` (API pública). |
| II. No se inventan reglas | Sí | Las tres preguntas las contestó el dueño (A). Lo que decidió quien implementa está en `research.md` y se avisa en el reporte. |
| III. Pruebas en verde | Sí | Unitarias de dominio y servicios, `HabitosPorFormacionIT`, 403 a todo rol sin permiso y a la cuenta suspendida, reloj en la madrugada UTC donde hay día. |
| IV. La base guarda historia | Sí | Migraciones con cabecera; `auditoria_catalogo_habitos` es de solo agregar; los registros pasados no se tocan; la formación del hábito no se puede cambiar (se copia). |
| V. El tiempo es del participante | Sí | La generación y los topes usan el día de la persona en SU zona y SU duración. |
| VI. Los cinco tabs | Sí, con autorización | El pedido del 31/10 autoriza «de dónde salen las categorías y los hábitos» y el aviso de «Mi programa»; se anota en `AGENTS.md` con «lo que cambia y nada más». |
| VII. Documentar en el mismo cambio | Sí | D-300 en `docs/MODULOS_A_AVANZAR.md`, `docs/MAPA_ENDPOINTS.md`, `docs/MODULO_HABITS.md`. |

### Complexity Tracking

| Desviación | Por qué hace falta | Alternativa más simple descartada |
|---|---|---|
| Una tabla nueva de solo agregar (`auditoria_catalogo_habitos`) | FR-024 pide quién, cuándo y qué cambió; ninguna tabla existente lo guarda (`categorias_habito` solo conserva el último estado; `cambios_auditados` es de Soporte TI con un CHECK cerrado). | Columnas `actualizado_por` en la categoría: guardan el último cambio, no el «qué cambió». |
| `habitos.formacion` repite la de su categoría | Hacen falta dos unicidades por formación (título y clave funcional) y un índice único no cruza tablas. | Derivarla del JOIN con la categoría: no permite los índices únicos. La llave compuesta impide que se contradigan. |
| Un hábito propio no tiene formación | «Los hábitos propios se conservan al cambiar»: siguen a su dueño. | Darles la formación de cuando se crearon: los esconde al cambiar. |

## Project Structure

```text
src/main/resources/db/migration/
├── V134__categorias_y_habitos_por_formacion.sql   # formacion/icono en categorias_habito; formacion en habitos; llave compuesta; unicidad por formación
├── V135__auditoria_del_catalogo_de_habitos.sql    # quién cambió qué (solo agregar)
└── V136__dia_de_los_habitos_hasta_365.sql         # horarios_habito.dia_inicio y desbloqueos_habito.dia_desbloqueo hasta 365

src/main/java/com/renaser/os/habits/
├── domain/model/categoria/      CategoriaHabito, IconoDeCategoria, CodigoDeCategoria
├── domain/model/habito/Habito   + formacion, copiarA, exigirQueSeaVisibleEn
├── application/ports/in/categoria/   Consultar/Gestionar categorías, ConsultarCategoriasDeMiFormacion
├── application/ports/in/habitoadmin/ + CopiarHabitoAOtraFormacionUseCase; formación en Crear/Consultar
├── application/ports/out/categoria/  Load/Save categorías, RegistrarCambioDelCatalogo
├── application/services/        CategoriasDeHabitoAdminService, CategoriasDeLaFormacionService, CopiarHabitoService; CatalogoAplicable, DesbloqueoHabitoService, …
└── infrastructure/              adaptadores JDBC y controladores (/habit-categories, /admin/habit-categories, /admin/habits/{id}/copy)

# App (Renaser-90-dias-frontend-)
src/features/habits/categorias/  api + hook + utilidades · Training/Hoy · Administración (Hábitos del programa, Categorías)
```
