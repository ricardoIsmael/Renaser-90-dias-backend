# Implementation Plan: El líder de mentores ve los hábitos de todos los alumnos

**Branch**: `029-lider-ve-todos-los-habitos` | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)

**Input**: `spec.md` con las «Decisiones del dueño (2026-10-31)» y `~/.config/renaser-e2e/formaciones/decisiones-del-dueno-2026-10-31.md`.

## Summary

Una pantalla de solo lectura, «Hábitos de todos» (Yo → «Mis mentores»), con el día elegido (hoy y hasta 7 atrás): por cada hábito del catálogo de las formaciones del
líder, cuántos alumnos lo cumplieron, cuántos todavía pueden y a cuántos se les pasó, los de menos cumplimiento primero; al tocar un hábito, los alumnos con su estado, la
hora planificada y la de cumplimiento; al tocar un alumno, su ficha de siempre en ese día.

Enfoque técnico: **ninguna tabla ni migración nueva** (V151–V152 quedan sin usar). Las cifras salen de `registros_habito`, la misma fuente de la ficha, leídas UNA vez
para todo el padrón con un nuevo contrato público de `habits` (`CumplimientoDelDiaFinder`). El módulo `leadership` arma las listas con el padrón que ya usa «Todos los
aprendices» (spec 021) y el alcance de formación de la spec 022; el filtro por formación del contenido es el de la 023.

## Technical Context

**Language/Version**: Java 25 / Spring Boot, Spring Modulith; app Expo SDK 57 / React Native / TypeScript estricto

**Storage**: PostgreSQL; sin migraciones. La consulta se apoya en `registros_dia_idx (participante_id, fecha_ejecucion)`.

**Testing**: JUnit 5 + Mockito (dominio y servicio), `HabitosDeTodosIT` (Tomcat real, sesión, permisos, aislamiento), `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`,
`FuncionalidadDeclaradaTest`; Jest + `tsc` en la app; Maestro escrito para el E2E posterior

**Performance Goals**: la lista se arma con TRES lecturas en lote (padrón, grupos, registros del día), no persona por persona; el detalle pide el horario y la evidencia solo
de la página (30 por defecto, 100 como máximo)

## Constitution Check

| Principio | Cumple | Cómo |
|---|---|---|
| I. Dependencias al dominio | Sí | Las reglas puras (`EstadoEnElDia`, `ResumenDeUnHabito`, `VentanaDeLosHabitos`) viven en `leadership.domain` sin Spring; `leadership` lee `habits.api`, `evidence.api` y `users.api` (solo APIs públicas); el controlador solo invoca un caso de uso. |
| II. No se inventan reglas | Sí | Las dos preguntas las contestó el dueño (A). |
| III. Pruebas en verde | Sí | 403 a mentor, guía, aprendiz, Soporte TI y líder suspendido; aislamiento por formación; reloj en la madrugada UTC. |
| IV. La base guarda historia | Sí | No escribe nada. |
| V. El tiempo es del participante | Sí | El estado de cada alumno lo dice SU registro (el barrido de expiración cierra su día en su zona); el «hoy» de la ventana es el del padrón (Lima) y las horas de cumplimiento se dicen en la zona de cada persona. |
| VI. Los cinco tabs | Sí, con autorización | Yo → «Mis mentores» gana una fila; se anota en `AGENTS.md`. |
| VII. Documentar en el mismo cambio | Sí | D-305, `MAPA_ENDPOINTS`. |

Sin violaciones: no hay desviaciones que justificar.

## Project Structure

```text
src/main/java/com/renaser/os/
├── habits/api/CumplimientoDelDiaFinder           # contrato público: los registros de un día de varias personas
├── habits/application/{ports/out/registro/LeerRegistrosDelDiaPort, services/CumplimientoDelDiaFinderService}
├── habits/infrastructure/adapter/out/persistence/registro/RegistrosDelDiaJdbcAdapter    # UNA consulta, con el filtro de formación
└── leadership/
    ├── domain/model/habitos/    EstadoEnElDia, ResumenDeUnHabito, VentanaDeLosHabitos
    ├── application/ports/in/ConsultarHabitosDeTodosUseCase
    ├── application/services/    HabitosDeTodosService; PersonasDelPrograma.alumnos(alcance, ahora)
    └── infrastructure/adapter/in/rest/  HabitosDeTodosController (+ respuestas)

# App: src/features/paneles-de-rol/ (fila «Hábitos de todos» en «Mis mentores», pantalla, lista de un hábito) · .maestro/habitos-de-todos/
```
