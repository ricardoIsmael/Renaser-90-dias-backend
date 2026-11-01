# Implementation Plan: «Cierra tu día» pasa a Plan

**Branch**: `033-cierre-del-dia-en-plan` (solo la app: el servidor no cambia; la app se implementa en `033-034-cierre-en-plan-e-interruptor`) | **Date**: 2026-10-31 | **Spec**: [spec.md](./spec.md)

## Summary

Se **mueve**, no se rehace. La tarjeta, la hoja (`HojaDelCierre`), las lecturas (`useCierreDeHoy`, `useCierresAnteriores`), el guardado y las fichas de la spec 017 se montan en **Plan** y se quitan de **Training**.
El servidor no cambia (FR-015): mismas rutas (`GET/PUT /api/v1/rocks/day-closure`), mismas reglas, mismos permisos. Con las decisiones del dueño del 31/10 (todo A): los cierres de días anteriores se ven **solo en Plan**,
en la misma tarjeta; «Ver mañana en Plan» **solo mueve** (sin atajos); y la tarjeta está **siempre**, también sin Mapa activo.

## Technical Context

**Language/Version**: TypeScript estricto, Expo SDK 57 / React Native; Jest para las pruebas. Backend: sin cambios.

**Primary Dependencies**: las de la app: `HojaDesdeAbajo`, `Presionable`, `ConfirmacionEnLinea`, `tacto`, `useFuncionalidad`, `useResumenHome`, `useAccionesDeHoy`. Ninguna nueva.

**Storage**: ninguno nuevo (el cierre ya vive en `rocas_diarias` y `entradas_diario`, V121). Memoria de la sesión para los días anteriores, como hoy.

**Testing**: `tsc --noEmit` y Jest. Las pruebas de la spec 017 de la hoja y del guardado **no se tocan** (SC-003). Maestro: `.maestro/cierre-en-plan/`.

**Target Platform**: Android (y web).

**Project Type**: app móvil (React Native / Expo).

**Performance Goals**: sin pedidos nuevos que se repitan: hoy se lee una vez cuando Plan está a la vista; los días anteriores, solo al tocarlos, una vez por fecha y cuenta.

**Constraints**: un solo scroll; 48 px; tipo oración y tuteo; claro y oscuro; solo animaciones que el cierre ya tiene; el APK anterior (cierre en Training) y el servidor actual se siguen entendiendo.

**Scale/Scope**: una tarjeta nueva en Plan; sale una fila de Training; nada de backend.

## Constitution Check

| Principio | Cumple | Cómo |
|---|---|---|
| I. Dependencias hacia el dominio | Sí | Lo que decide (cuándo hay tarjeta, qué dice, qué día se lee) vive en `features/cierre/utils` puro y probado; las pantallas solo lo dibujan. El servidor decide permisos y datos. |
| II. No se inventan reglas | Sí | Las tres preguntas las respondió el dueño (todo A). El ícono de eje de cada pendiente sale de lo que Plan ya lee, no de una regla nueva. |
| III. Pruebas | Sí | Jest de la tarjeta, de lo que ya no hace Training y de los días anteriores en Plan; las 017 intactas; `tsc` limpio. |
| IV. Base de datos | Sí | No se toca. |
| V. El tiempo es del participante | Sí | Mismo «ID original»: la hoja compara su día con el de hoy antes de enviar; se prueba con el reloj en la madrugada UTC. |
| VI. Los cinco tabs | Sí | Training y Plan se tocan **solo** para esto, autorizado por el pedido (`AGENTS.md`, «lo que cambia y nada más»). |
| VII. Documentar | Sí | D-298, `AGENTS.md` y notas «Corregido 2026-10-31» en la 017. |

Complexity Tracking: vacío.

## Project Structure

### Documentation (this feature)

```text
specs/033-cierre-del-dia-en-plan/
├── spec.md · plan.md · research.md · data-model.md · quickstart.md · tasks.md
└── contracts/ui.md          # lo que ve la persona y a dónde lleva cada toque (no hay contrato de API nuevo)
```

### Source Code (app, `033-034-cierre-en-plan-e-interruptor`)

```text
src/features/cierre/
├── components/TarjetaDelCierreEnPlan.tsx       # NUEVO: la tarjeta de Plan (hoy + semana en la misma tarjeta)
├── components/HojaDelCierre.tsx                 # «Ver mañana en Plan»; el ícono de eje de cada pendiente; sin tocar el guardado
├── components/FilaDelCierre.tsx                 # se borra (era la fila de Training)
├── components/CierresDeLaSemanaEnPlan.tsx       # NUEVO: la fila de días y el detalle de solo lectura
├── hooks/useCierreEnPlan.ts                     # NUEVO: junta useCierreDeHoy + useCierresAnteriores + la hoja
└── utils/{semanaDelCierre,ejeDeLasAcciones}.ts  # NUEVOS, puros
src/features/objetivos/components/PlanDelEje.tsx  # ranura para la tarjeta, bajo «Acciones que mueven la meta»
src/screens/PlanScreen.tsx                        # monta la tarjeta (también sin Mapa) y lleva «Ver mañana»
src/features/training/…                           # se quita «Cierra tu día» de hoy y de los días anteriores
```

**Structure Decision**: el cierre sigue en `features/cierre` (es suyo); Plan lo monta y Training deja de hacerlo.
