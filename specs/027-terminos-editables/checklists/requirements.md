# Specification Quality Checklist: Términos y condiciones editables desde la app

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-31
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [ ] No [NEEDS CLARIFICATION] markers remain — **2 abiertos (Q1, Q2)**, esperan respuesta del dueño
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

- Q1 (¿términos por formación o comunes?) y Q2 (¿se vuelve a aceptar cuando cambian?) cambian el alcance y no tienen una respuesta razonable por defecto.
  Q3 a Q12 tienen supuesto y se listan porque el dueño pidió hacer él mismo todas las preguntas antes de implementar.
- Hoy el servidor guarda «aceptó» y no «qué texto aceptó»: por eso la spec exige versionar. Las aceptaciones existentes se atribuyen a la versión 1 (Q5 pregunta si el texto cambió antes).
- Los límites de 2.000 caracteres por cláusula y 100 cláusulas son un supuesto razonable, no una regla del dueño.
- Esta spec se apoya en la 022 (formaciones) y se cruza con la 025 (impedir aceptar en una formación sin términos) y la 026 (los términos siguen a la ficha).
