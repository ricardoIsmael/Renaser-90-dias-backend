# Implementation Plan: Cierra tu día

**Branch**: `017-cierre-del-dia` | **Date**: 2026-10-29 | **Spec**: `spec.md` | **Contrato**: `contracts/api.md`
**Base**: `origin/master` (`21d6cc13`). Números reservados: spec 017, migración **V121**, decisión **D-290**, error E-653 (sin usar: no
apareció ningún bug).

## Resumen

Una persona que hace su programa cierra **hoy** diciendo, por cada acción pendiente, qué pasó y qué hará mañana. El acompañamiento lo
lee por semana. No hay tabla nueva: las respuestas por acción son cuatro columnas de `rocas_diarias`; la marca de «día cerrado» y la
nota del día son una fila `EVALUACION_DIA` de `entradas_diario`. Guardar no completa nada ni paga puntos.

## Technical Context

**Lenguaje**: Java 25 / Spring Boot, hexagonal + Spring Modulith, PostgreSQL con Flyway (schema `renaser`).
**Pruebas**: JUnit 5 + Mockito + AssertJ (`@WebMvcTest` para controllers); las de integración (`*IT`) se escriben sin correrlas.
**Restricciones**: reloj por el puerto `Clock` y la zona del participante; método ≤ 20 líneas, clase ≤ 150, parámetros ≤ 3.

## Constitution Check (antes y después del diseño)

| Principio | Cumple |
|---|---|
| I. Dependencias al dominio | Reglas en `rocks.domain.model.cierre` (sin Spring). Controllers tontos. `rocks` importa `habits.api` y `leadership` importa `rocks.api` (sin ciclo: `habits` no importa `rocks`). |
| II. No se inventan reglas | Todo lo del dueño está en `spec.md`; lo que decidió el equipo está en «Decisiones técnicas» y D-290, a la vista. |
| III. Pruebas | Dominio sin Spring, servicios con dobles, controllers `@WebMvcTest` con 403 negativos, reloj en la madrugada UTC. |
| IV. La base guarda historia | V121 con cabecera; las respuestas son del día (no se reescribe historia: un día pasado no se puede cerrar). |
| V. El tiempo es del participante | «Hoy» = `clock.now()` en la zona de la persona; sin `LocalDate.now()`. |
| VI. Cinco tabs | La app solo toca Training (y las fichas) con la entrada en `AGENTS.md`. |
| VII. Documentar | D-290, `MODULO_ROCKS`, `MODULO_HABITS`, `MAPA_ENDPOINTS`, grafo de módulos. |

## Decisión de diseño: dónde vive cada cosa (SOLID)

| Pieza | Módulo / capa | Por qué |
|---|---|---|
| `MotivoDeAccionPendiente`, `PlanDeManana`, `RespuestaDeCierre`, `CierreDeAccion`, `CierreDelDia`, `EstadoDelCierre` | `rocks.domain.model.cierre` | Son reglas de la acción del día; dominio puro y probado sin Spring. |
| `RocaDiaria.cerrarConRespuesta / quitarCierre / cierre()` | `rocks.domain.model.rocadiaria` | El agregado muta por intención y nunca toca `completada`. |
| Guardar solo las columnas de cierre | `SaveRocaDiariaPort.guardarSoloElCierre` + `UPDATE` dirigido | Un `save` de la entidad entera reescribiría `completada` y los puntos: si la persona cumple una acción mientras cierra, **se perdería el cumplimiento**. El UPDATE dirigido hace imposible tocarlos (FR-003). |
| La marca del día | `habits.api.EvaluacionDelDiaPort` + `EvaluacionDelDiaService` | `entradas_diario` es de `habits` (nadie manda SQL contra una tabla ajena). |
| Casos de uso de la persona | `rocks.application.services.CierreDelDiaService` (`CerrarElDiaUseCase`, `ConsultarCierreDelDiaUseCase`) | Reusa `AccesoARocas.exigir` (suspendido, programa andando). |
| Lectura por semana | `rocks.api.CierresDelDiaFinder` ← `CierresDeLaSemanaService` | Una sola regla para el mentor y el liderazgo (como `HuecosDeHorarioFinder`). |
| Ruta del mentor | `rocks...rest.cierre.CierreDelAlumnoController` + `CierresDelAlumnoService` | Guardián copiado del Radar/huecos (acompañante vigente + alumno del grupo + cuenta activa); no se comparte a propósito (ARF-15). |
| Ruta del liderazgo | `leadership...CierresDeLaPersonaController` + `CierresDePersonaService` | `AccesoDeLiderazgo` + `PersonasDelGrupo` + constancia en `consultas_de_ficha`. |

`SemanaDeLaPersonaResponse` y `HuecosDeLaSemana` **no se tocan** (otra rama las modifica): el cierre va en rutas aparte.

## Flujo de «guardar»

`PUT` → `CierreDelDiaService.cerrar` (`@Transactional`):
1. `AccesoARocas.exigir(actor)` → zona. 2. `hoy = clock.now() en la zona`; `fecha != hoy` → `CierreDeOtroDiaException` (409);
`fecha > hoy` → 400. 3. Carga las rocas de hoy. 4. `CierreDelDia.cerrar(...)` valida y aplica en memoria (400 si falta una
respuesta o sobra un id). 5. `guardarSoloElCierre`. 6. `EvaluacionDelDiaPort.dejarCerrado(actor, hoy, nota)`. 7. Devuelve la vista releída.
Si cualquier paso falla, la transacción deshace todo (no queda la marca sin las respuestas).

## Estructura de archivos

```text
src/main/resources/db/migration/V121__cierre_del_dia.sql
habits/api/EvaluacionDelDiaPort.java                         # nuevo
habits/application/services/EvaluacionDelDiaService.java     # nuevo
habits/application/ports/out/diario/LoadEntradaDiarioPort    # + entreFechasDeTipo
habits/infrastructure/.../diario/{Adapter, Repository}       # + consulta; entradasEntre excluye EVALUACION_DIA
rocks/api/CierresDelDiaFinder.java                           # nuevo
rocks/domain/model/cierre/*                                  # nuevo
rocks/domain/model/rocadiaria/RocaDiaria.java                # + cierre
rocks/application/ports/in/cierre/*                          # nuevo
rocks/application/services/{CierreDelDiaService, CierresDeLaSemanaService, CierresDelAlumnoService, ArmadoDelCierre}
rocks/application/ports/out/rocadiaria/SaveRocaDiariaPort    # + guardarSoloElCierre
rocks/infrastructure/.../rocadiaria/{JpaEntity, Mapper, Adapter, Repository}
rocks/infrastructure/adapter/in/rest/cierre/*                # controllers + DTO
leadership/application/{ports/in, services}/Cierres*         # nuevo
leadership/infrastructure/adapter/in/rest/CierresDeLaPersonaController
```

## Complexity Tracking

| Desvío | Por qué | Alternativa descartada |
|---|---|---|
| `rocks` pasa a depender de `habits.api` | La marca del día es de `habits`; no hay otro dueño razonable de `entradas_diario` | Una tabla nueva (prohibida por el dueño); SQL contra `entradas_diario` (viola D-41); un módulo nuevo que dependa de los dos (más piezas sin ganar nada) |
| `UPDATE` dirigido en vez de `save` | Evita pisar `completada`/puntos si la persona cumple una acción mientras cierra | `byIdParaEscritura` con bloqueo por roca: más caro y deja la puerta abierta a un mapper que sobrescriba |
| La hora de la fila vive en la app | Training ya lee la hora de Dormir de hoy; el servidor no tiene `claveSistema` en master | Agregar `claveSistema` a `HorarioResuelto` (choca con la rama 015) |

## Orden de publicación

Backend (V121) antes que la app. La app nueva contra el backend anterior no muestra la fila (404). **Aviso de migraciones:** la rama 015
usa V120 y esta V121; si 017 se aplica antes que 015, Flyway no aplicará V120 después sin `outOfOrder`. Fusionar 015 primero o
renumerar al integrar.
