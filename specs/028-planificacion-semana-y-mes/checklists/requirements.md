# Specification Quality Checklist: Planificar la semana y el mes — repetir lo planificado

**Purpose**: Validar que la spec está completa y bien escrita antes de planificar
**Created**: 2026-10-31
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] Sin detalles de implementación (lenguajes, frameworks, APIs, tablas): solo pantallas, reglas y specs ya existentes.
- [x] Centrada en el valor para la persona (no repetir hábito por hábito) y en el pedido textual del dueño.
- [x] Escrita para quien no programa: el riesgo técnico va en Supuestos, en palabras simples.
- [x] Todas las secciones obligatorias completas, más «Restricciones del dueño» y «Preguntas abiertas».

## Requirement Completeness

- [ ] Sin marcadores [NEEDS CLARIFICATION]: **quedan 3 a propósito** (FR-001, FR-014, FR-023). El dueño quiere responderlas él antes de implementar.
- [x] Los requisitos son comprobables y sin ambigüedad (cada FR tiene su criterio en las historias o en el éxito).
- [x] Los criterios de éxito son medibles (toques, segundos, 0 diferencias, 0 cambios en otra formación).
- [x] Los criterios de éxito no dependen de la tecnología.
- [x] Escenarios de aceptación definidos para las cuatro historias.
- [x] Casos borde identificados (hoy y pasado, pausas, otra formación, red, doble toque, dos teléfonos, APK anterior).
- [x] Alcance acotado: solo hábitos (a confirmar con la pregunta 3); no se tocan Plan ni el recordatorio de los domingos.
- [x] Dependencias y supuestos identificados (022, 023; hueco entre lo que se guarda por fecha y por día de la semana).

## Feature Readiness

- [x] Todos los requisitos funcionales tienen criterio de aceptación.
- [x] Las historias cubren los flujos principales (repetir, planificar una semana, planificar el mes, deshacer).
- [x] La feature cumple los resultados medibles de «Success Criteria».
- [x] Sin detalles de implementación en la spec.

## Notes

- Quedan 3 marcadores pendientes: la spec no pasa a `/speckit-plan` hasta que el dueño responda (FR-001: cómo se planifica una semana concreta; FR-014: mes de calendario o del programa; FR-023: solo hábitos o también acciones).
- Depende de las specs 022 (formaciones) y 023 (hábitos por formación), que escribe otra persona.
