# Specification Quality Checklist: El líder de mentores ve los hábitos de todos los alumnos

**Purpose**: Validar que la spec está completa y bien escrita antes de planificar
**Created**: 2026-10-31
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] Sin detalles de implementación (lenguajes, frameworks, APIs, tablas): solo pantallas, reglas y specs ya existentes.
- [x] Centrada en el valor para el líder (contestar «¿quién no hizo este hábito?» sin abrir cientos de fichas).
- [x] Escrita para quien no programa.
- [x] Todas las secciones obligatorias completas, más «Restricciones del dueño» y «Preguntas abiertas».

## Requirement Completeness

- [ ] Sin marcadores [NEEDS CLARIFICATION]: **quedan 2 a propósito** (FR-006: qué es «ver todos los hábitos»; FR-011: período). El dueño quiere responderlas él antes de implementar.
- [x] Los requisitos son comprobables y sin ambigüedad.
- [x] Los criterios de éxito son medibles (toques, segundos, 0 discrepancias, 0 alumnos de otra formación).
- [x] Los criterios de éxito no dependen de la tecnología.
- [x] Escenarios de aceptación definidos para las cuatro historias.
- [x] Casos borde identificados (falta de datos, hoy en la madrugada UTC, cambio de grupo o formación, cientos de alumnos, APK anterior).
- [x] Alcance acotado: solo lectura, solo alumnos, sin duplicar «Todos los aprendices» ni la ficha.
- [x] Dependencias y supuestos identificados (022, 023, 021, 005; relación con la 034).

## Feature Readiness

- [x] Todos los requisitos funcionales tienen criterio de aceptación.
- [x] Las historias cubren los flujos principales (por hábito, detalle, formación, día y filtros).
- [x] La feature cumple los resultados medibles de «Success Criteria».
- [x] Sin detalles de implementación en la spec.

## Notes

- Quedan 2 marcadores pendientes: la spec no pasa a `/speckit-plan` hasta que el dueño responda.
- Depende de las specs 022 (formaciones) y 023 (hábitos por formación). Se relaciona con la 034: si Administración apaga el Código Renaser para el líder, esta vista tampoco lo muestra.
