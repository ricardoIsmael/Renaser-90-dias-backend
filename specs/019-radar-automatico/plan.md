# Implementation Plan: Mi Radar automático y personalizado

**Branch**: `019-radar-automatico` (backend y app) | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

## Summary

Mi Radar deja de guardar horas a mano. Guarda solo **encendido**, y por día de la semana el **modo**, las **veces** y «Sin
Radar». Las horas las calcula el servidor con el Despertar y el Dormir de ese día. Sin tablas nuevas y sin tocar los días
1 a 7, los hábitos, Training ni Plan.

## Technical Context

Java 21 + Spring Boot (backend); TypeScript + React Native / Expo SDK 57 (app). Sin dependencias nuevas.
Storage: PostgreSQL, **V122** (la V119 de la 012 no se edita). Testing: JUnit 5 + `@WebMvcTest` + Testcontainers; Jest;
Maestro en el emulador.

## Constitution Check

| Principio | Cómo se cumple |
|---|---|
| I. Dependencias al dominio | `MiRadar` y `HorarioDelRadar` son dominio puro; `habits` lee los horarios por su propio `HorarioDelDiaFinder`; `users` guarda las columnas (`users.api.HorasDeMiRadarPort`). |
| II. No inventar reglas | Todas salen de las decisiones del dueño; lo supuesto está en «Supuestos» de la spec. |
| III. Pruebas en verde | Dominio, servicio, controlador, adaptador (Testcontainers) y `clean verify` al final. |
| IV. Historia | `hora_prevista` se sigue guardando al registrar; la V122 no rellena nada. |
| V. Tiempo del participante | «Hoy», el día de la semana y la hora salen de la zona de la persona (`Clock` + `timezone`). |
| VI. Tabs | Pedido del dueño: Yo → Mi Radar y lo justo en Hoy y las fichas. Se anota en `AGENTS.md` de la app. |
| VII. Documentar | D-292 (`docs/MODULOS_A_AVANZAR.md`), `docs/MODULO_HABITS.md` §8 y correcciones en las specs 012 y 016. |

## Datos (V122, sin tablas)

| Tabla | Columna | Antes (V119) | Ahora (V122) |
|---|---|---|---|
| `participantes_programa` | `horas_mi_radar smallint[]` | horas de todos los días; `NULL` = apagado | **se elimina** |
| `participantes_programa` | `mi_radar_encendido boolean NOT NULL DEFAULT false` | — | encendido o apagado |
| `participantes_programa` | `dias_mi_radar jsonb` | días con horas propias | por día de la semana con algo distinto de lo normal: `[{"dia":6,"modo":"PERSONALIZADO","veces":3,"sinRadar":false}]`; `NULL` = todos Automático |

Un día que no está sigue el valor normal: `AUTOMATICO`, sin veces, con Radar. `veces` solo se guarda con `PERSONALIZADO`.
La V122 pone `mi_radar_encendido = (horas_mi_radar IS NOT NULL)` y `dias_mi_radar = NULL`: la 012 nunca llegó a
producción, no hay horas a mano que conservar. `registros_radar.hora_prevista` no cambia.

## Dominio (`habits/domain/model/radar`)

- `MiRadar(encendido, dias)`; `DiaDeMiRadar(dia 1..7, modo, veces, sinRadar)`, ordenados, sin repetir; `modo` AUTOMATICO o
  PERSONALIZADO; `veces` de 1 a 21 y solo en PERSONALIZADO (si falta se pone `VECES_POR_DEFECTO = 6`). 400 si no cumple.
- `HorarioDelRadar` (puro):
  - `horasQueCaben(despertar, dormir)`: las `h` en 03..23 con `despertar ≤ h:00 < dormir`; vacío si falta alguna, o
    `dormir ≤ despertar`.
  - `repartir(horas, n)`: `n ≥ tamaño` → todas; `n ≤ 1` → la primera; si no, la posición `round(i·(t−1)/(n−1))` (mitad hacia
    arriba, con enteros).
  - `horasDelDia(dia, despertar, dormir)`: vacío si `sinRadar` o no caben; AUTOMATICO → todas; PERSONALIZADO →
    `repartir(caben, min(veces, caben))`.
  - `puedeRegistrar(diaPrograma, horasDeHoy, local)`: días 0–7 siempre; desde el 8 si hoy tiene horas y la hora local está en
    03..23. `horaPrevista` como antes con las horas de hoy.
  - `esEditable(diaDeLaSemana, hoy)`: `dia > hoy.dow`; el domingo, de lunes a sábado.

## Servidor (`habits`)

- `AgendaDelRadar` (servicio interno): con `HorarioDelDiaFinder.deFecha` resuelve Despertar y Dormir de la próxima fecha en
  que cae cada día de la semana (hoy incluido) y arma el `PlanDelDiaDelRadar` (fecha, despertar, dormir, caben, veces, horas,
  motivo, editable). Un hábito cuenta si no está apagado ni pausado y tiene `horaDisparo`.
- `MiRadarService`: `consultar` (7 días) y `configurar` (parcial: solo los días que llegan). Compara cada día pedido con lo
  guardado: si cambia y no es editable, `IllegalStateException` (409) «Hoy y los días que pasaron no se pueden cambiar»;
  `veces` mayor que lo que cabe ese día: 400. `encendido` rige en el acto.
- `RadarService.registrar` usa las horas de hoy; `LecturaDelRadar` / `CodigoRenaserDePersonaService` devuelven `miRadar` con
  el día de hoy (una sola consulta de horario) para las fichas.
- Los motivos de rechazo: «Mi Radar está apagado», «Hoy no tienes Mi Radar», «Mi Radar funciona de 03:00 a 23:59».

## Contrato (`GET` y `PUT /api/v1/radar/mi-radar`)

```jsonc
// GET -> 200
{ "encendido": true, "hoy": "2026-10-30",
  "dias": [ { "dia": 1, "fecha": "2026-10-26", "modo": "AUTOMATICO", "veces": 17, "maximo": 17, "sinRadar": false,
              "editable": false, "despertar": "05:30", "dormir": "22:30",
              "horas": [6, 7, "…", 22], "motivo": null } /* …7 días, lunes = 1 */ ] }
// motivo: null | "SIN_DESPERTAR" | "SIN_DORMIR" | "HORARIO_INVALIDO"
// PUT { "encendido": true, "dias": [ { "dia": 6, "modo": "PERSONALIZADO", "veces": 3, "sinRadar": false } ] }  -> 200 como el GET
// 400 modo o veces fuera de regla · 409 cambia hoy o un día pasado · 403 sin permiso
```

`maximo` = las horas que caben; `veces` = las que pregunta de verdad (0 si no hay Radar ese día). `GET /radar/history` y las
tres fichas traen `miRadar` con la misma forma pero con **solo el día de hoy** en `dias` (y `hoy`), `null` antes del día 8.

## App (`features/radar`)

- `miRadar.ts`: `horasDelDia(config, diaIso)` sigue siendo la única fuente (ahora lee `dias[].horas`); `repartir` y
  `horasQueCaben` espejo del servidor para las pruebas y el ajuste de − / +; `horasDeAviso`, `textoDeMiRadar` y
  `resumenDeMiRadarHoy` leen esa función.
- `MiRadarScreen` nueva: cabecera, `InterruptorGrande`, `ControlSegmentado`, «Hora» (solo lectura), «Veces al día» con − / +,
  `FilaDeDiasDelPlan` con la cifra, «Sin Radar estos días», «Guardar · sáb, dom» fijo. «Programar mi Despertar» abre Training
  (`abrirHabitoId`).
- Alarmas: `renaser.radar.slot.d{1..7}.HH`, una por hora y día de las que tocan; se rehacen al leer Mi Radar.
- Se borran los componentes de horas a mano (`HorariosDeMiRadar`, `HorasDeLosDias`, `DiasDeMiRadar`, `diasDeMiRadar.ts`).

## Complexity Tracking

Sin desvíos que justificar.
