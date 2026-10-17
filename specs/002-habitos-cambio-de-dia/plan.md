# Implementation Plan: Hábitos vigentes al cambiar de día

**Branch**: app `004-habitos-cambio-de-dia`, backend `002-habitos-cambio-de-dia` | **Date**: 2026-10-17 | **Spec**: [spec.md](spec.md)

## Summary

Publicar una lectura aditiva `/api/v1/habit-tracks/today/snapshot` con el contexto y los tracks de la misma proyección. Mantener `/today` como array. Training invalida al cierre y recarga con foco y actividad. Los envíos genéricos validan pertenencia a `/today` y emiten un aviso local de invalidación si el ID ya no está o el servidor rechaza el día cerrado.

## Technical Context

- TypeScript 6, React 19.2, Expo 57 y React Native 0.86; Java 25, Spring Boot del pom existente.
- Sin dependencias, migraciones, schedulers ni almacenamiento nuevo.
- Pruebas: Jest con reloj controlado, tsc; JUnit/MockMvc y `./mvnw clean verify` con integración PostgreSQL real.
- Android, iOS y web; conservar el gesto de cámara web (validar después de capturar y antes de subir).
- Un snapshot coherente por carga; temporizador local del cierre y relectura periódica de compatibilidad cada 60 s solamente en actividad.
- La vigencia de la lista depende del instante del servidor transportado por JSON, no del encabezado Date.

## Constitution Check

Antes del diseño y después: PASS en I–VII. El dominio y los puertos conservan sus dependencias; no cambia ninguna regla de negocio; pruebas obligatorias; sin historia modificada; fecha/zona provienen del mismo progreso; autorización explícita del dueño anotada en AGENTS; documentación en este cambio. No se alteran sesiones de Santuario ni racha sin celular. Los cambios previos en package.json y package-lock.json se conservan.

## Project Structure

Spec canónica en backend `specs/002-habitos-cambio-de-dia/`; frontend `specs/README.md` la referencia.

```text
backend/src/main/java/com/renaser/os/habits/
  application/ports/in/registro/ConsultarTracksDelDiaConCatalogoUseCase.java
  application/services/TracksDelDiaProyeccionService.java
  infrastructure/adapter/in/rest/registro/{HabitTrackController,DiaDeHabitosResponse}.java
frontend/src/features/habits/
  api/{diaDeHabitosApi,registroVigente,evidenciaHabitoApi}.ts
  eventos/diaDeHabitosCambiado.ts
  hooks/useRegistroConFoto.ts
frontend/src/features/training/
  hooks/{useTraining,useActualizacionDelDia}.ts
  api/cargarEntrenamiento.ts
frontend/src/screens/TrainingScreen.tsx
```

## Validation strategy

US1: temporizador, actividad, foco, lista vacía, carga vencida y respuestas desordenadas. US2: cierres/evidencias sin ID vigente, 409 en carrera, cámara web y otros 409. US3: red caída, fallback 404 y recuperación. Backend: contexto coherente con lista vacía y con generación, madrugada Lima/Madrid y DST; autorización negativa y suspendido; array anterior intacto. Ver [quickstart.md](quickstart.md).

## Complexity Tracking

La proyección existente excede tamaños objetivo y tiene un constructor extenso previo: solo se conserva la zona junto a la fecha en su resultado y se añade una operación con nombre explícito; no se reestructura el módulo en este arreglo.
