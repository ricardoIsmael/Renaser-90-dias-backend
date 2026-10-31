# Specification Quality Checklist: Tu 80/20 con IA, cada semana y cada mes

**Purpose**: Validar que la spec está completa y es de buena calidad antes de pasar a `/speckit-clarify` o `/speckit-plan`
**Created**: 2026-10-31
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [ ] No [NEEDS CLARIFICATION] markers remain — quedan 3, a propósito: el dueño las responde antes de planificar (FR-001, FR-010 y FR-020, ver «Puntos abiertos»)
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

- Las tres marcas abiertas cambian el alcance: qué es el 20 % (Pregunta 1), quién más lo ve (Pregunta 2) y cuánto de lo emocional
  entra (Pregunta 3). Con las recomendadas (A, A, A) la spec está lista para `/speckit-plan`; hasta que se responda la 2, se construye
  para «solo la persona».
- Los valores propuestos (mínimo de datos, reintentos) están en «Supuestos» y marcados «a confirmar»; no son reglas del negocio hasta
  que el dueño las confirme (constitución, principio II).
- Mencionados por nombre de producto, no por diseño: SER, Plan «Tu 80/20», Mi Radar, Espejo de la Sombra, Soporte TI, Política de
  Privacidad.
- Depende de la spec 022 (formaciones) solo en cómo se define la pertenencia; esta spec no la redefine.
