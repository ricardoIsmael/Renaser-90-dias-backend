# Implementation Plan: Tipos de alarma por día

**Branch**: `009-tipos-de-alarma` (backend y app; sale de `008-recordatorio-por-dia`) | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

## Summary

Debajo del recordatorio, la sección «Cómo suena» con cuatro interruptores dorados grandes: Sonido, Vibración, Voz y Repetir. Se elige por día, igual que la hora y el aviso de la 008.

- **Backend**: V112 suma la combinación a la preferencia general y a cada día de la semana. Endpoints `PUT …/alarm` sin cupo. Las lecturas suman `alarm` y `customAlarm`.
- **App (solo JavaScript, sin APK)**: componente `InterruptorGrande`. Canal de Android por combinación (sonido, vibración, voz). Repeticiones como alarmas de fecha a +5 y +10 en los próximos 7 días, que se cancelan al tocar la alarma o al completar el hábito.

Ver [research](./research.md).

## Technical Context

- **Lenguajes**: Java 25 (Spring Modulith, hexagonal), TypeScript (React Native, Expo SDK 57).
- **Librerías**: expo-notifications (canales y disparadores `DATE`, `DAILY` y `WEEKLY`), Reanimated (movimiento del pulgar).
- **Base de datos**: PostgreSQL (V112).
- **Pruebas**: JUnit y Testcontainers, Jest y `tsc`, mobile-mcp y Maestro.
- **Restricciones**:
  - Sin APK.
  - Compatible hacia atrás.
  - UX para mayores de 40: 17/15 px, filas de 56 px o más, contraste alto.
  - Orden de la pantalla: hora → días → recordatorio → cómo suena → guardar.

## Constitution Check

| Principio | Cómo se cumple | Estado |
|---|---|---|
| I | La combinación vive en el dominio (`CombinacionDeAlarma`, sin Spring). El controller no tiene lógica. En la app, la lógica pura va en `utils/`. | ✅ |
| II | Las decisiones del dueño están en Clarifications. Las dos reglas que agregué yo (sonido o vibración obligatorio; la voz depende del sonido) se le avisan al dueño. | ✅ con aviso |
| III | Pruebas primero: dominio, adaptador, controller con 403, canal por combinación, repeticiones (crear y cancelar hoy), interruptor (render y accesibilidad), editor. E2E con la base. | ✅ |
| IV | V112 solo agrega columnas nulas, con cabecera y CHECK. | ✅ |
| V | Las fechas de las repeticiones salen del día del teléfono. El servidor usa la zona de la persona. | ✅ |
| VI | El editor de Training → Planificar está autorizado por el dueño (2026-10-20). Va con nota en `AGENTS.md`. | ✅ con nota |
| VII | D-283 y E-641 en adelante, en el mismo cambio. | ✅ |

## Project Structure

```text
back: src/main/resources/db/migration/V112__combinacion_de_alarma.sql
      habits/domain/model/preferencia/CombinacionDeAlarma.java (nuevo)
      PreferenciaHorario, HorarioSemanal, adaptador, puertos, EditarHorarioSemanalUseCase + caso general
      rest: AlarmCombinationRequest (nuevo), HabitPreferenceController, respuestas
app:  src/components/InterruptorGrande.tsx (nuevo)
      src/features/alarmas/sonidoDeAlarma.ts (canales -sinvib), rearmarAlarmas.ts (ids nuevos)
      src/features/habits/notificaciones/utils/combinacionDeAlarma.ts (nuevo, puro)
      src/features/habits/notificaciones/repeticiones.ts (nuevo)
      src/features/habits/notificaciones/recordatoriosDeHabito.ts
      src/features/training/components/SeccionComoSuena.tsx (nuevo) + PlanificarDimensionModal.tsx
      cancelación: listener de respuesta de avisos + suscripción a habitoCumplido
      AGENTS.md
```

## Orden de entrega

1. La 008 integrada en la rama.
2. Backend: V112, dominio, endpoints y lecturas.
3. App: canales y repeticiones (alarmas).
4. App: `InterruptorGrande` y «Cómo suena» en el editor.
5. Validación conjunta de la 008 y la 009: verify, Jest, E2E con la base y Maestro de lo afectado.

## Complexity Tracking

| Decisión | Por qué | Alternativa descartada |
|---|---|---|
| Repeticiones de fecha en una ventana de 7 días | Es la única forma de saltar solo «hoy» sin código nativo | Repetición nativa al sonar: necesita APK |
| `InterruptorGrande` aparte de `Interruptor` | Así no cambia el tamaño de los interruptores del resto de la app | Agrandar `Interruptor`: cambiaría Yo, Plan y los Eventos sin autorización |
