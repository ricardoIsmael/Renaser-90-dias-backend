# Specification Quality Checklist: Ficha inicial con diagnóstico, administrable; el Mapa de Renacimiento se retira

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-31
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [ ] No [NEEDS CLARIFICATION] markers remain — **3 abiertos (Q1, Q2, Q3)**, esperan respuesta del dueño
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Q1 (qué ficha se administra), Q2 (objetivos que nacen del diagnóstico: fijos o revisables) y Q3 (fichas compartidas o por formación) cambian el alcance y no tienen
  una respuesta razonable por defecto. Q4 a Q15 tienen supuesto y se listan porque el dueño pidió hacer él mismo todas las preguntas antes de implementar.
- El nombre del interruptor `ROCKS_EXIGIR_MAPA_ACTIVO` y los formatos «CSV o Excel» aparecen porque el pedido del dueño los nombra o los exige para decir qué pasa con
  producción y qué archivo sube Administración; no describen la construcción.
- Los anexos (`diagnostico-inicial.md` y `diagnostico-inicial.csv`) son insumo verificable: el CSV se importó con un lector de CSV estándar y da 6 secciones y 27 preguntas.
- Se leyó el formulario de Google del dueño sin credenciales: Google Forms no exporta preguntas; la página pública lleva su estructura completa. La spec **no** propone leerla automáticamente
  (formato interno de Google); propone una plantilla de hoja de cálculo.
- Dependencia fuerte con la spec de planificación de la semana y el mes de la misma tanda: el Mapa alimentaba hitos, cifra del mes y acciones sugeridas (Q14).
