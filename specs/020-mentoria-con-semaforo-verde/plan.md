# Implementation Plan: Mentoría del Alquimista solo con semáforo verde

**Branch**: `020-mentoria-semaforo-verde` (backend) | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

## Summary

El adaptador del puerto de elegibilidad de `calendar` deja de decir siempre «sí» y le pregunta el color del semáforo
vigente a `points`. Para un aprendiz, `MENTORIA_ALQUIMISTA` solo es elegible con **verde**. Todos los caminos que ya
consultaban el puerto (lista, detalle, agenda, «Próximo evento», «Voy», recordatorios, avisos y la hoja de asistencia
D-256) quedan cubiertos sin tocarlos; el despacho de la cola suma una pregunta para los recordatorios ya generados. Sin
migración, sin endpoint, sin cambio de app.

## Technical Context

Java 25 + Spring Boot (backend). Testing: JUnit 5 + Mockito + Testcontainers (Postgres real). La app (Expo SDK 57) no
cambia; Maestro y `mobile-mcp` en el emulador para el E2E.

## Constitution Check

| Principio | Cómo se cumple |
|---|---|
| I. Dependencias al dominio | `calendar` solo importa `points.api` (`SemaforoFinder`, `ColorSemaforo`); la regla vive en un adaptador de salida, el servicio de aplicación sigue hablando con el puerto. `domain/` no cambia de código. |
| II. No inventar reglas | La regla es del dueño (2026-10-30); lo decidido por quien implementa (días 1–7, pausa, recordatorio ya generado) está en «Clarifications» y las dudas, en «Dudas para el dueño». |
| III. Pruebas en verde | Adaptador por color, servicios por rol, despacho, IT con Postgres real y `clean verify` completo al final. |
| IV. Historia | Sin migración. El recordatorio cancelado conserva su fila con `motivo_cancelacion`; el «Voy» no se borra. |
| V. Tiempo del participante | El «ayer» del semáforo es el de la zona de la persona (`points`, con el `Clock`); la IT usa el reloj en la madrugada UTC. |
| VI. Tabs | La app no cambia. |
| VII. Documentar | D-293 y la nota «Corregido» de D-213 (`docs/MODULOS_A_AVANZAR.md`), `docs/MODULO_CALENDAR.md` §1.5 y §6, `docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md`, Javadoc del puerto y de `ReglasPorTipoEvento`, `AGENTS.md` de la app (una línea). |

## Diseño

| Pieza | Cambio |
|---|---|
| `ConsultarElegibilidadEventoPort` | Suma `Set<UserId> elegiblesEntre(Collection<UserId>, TipoEvento)` con un `default` que recorre `esElegible`. Solo se pregunta por aprendices (Javadoc). |
| `ElegibilidadPorSemaforoAdapter` (nuevo) | Reemplaza a `ElegibilidadSegunAudienciaAdapter`. Para `MENTORIA_ALQUIMISTA` llama `SemaforoFinder.vigenteDe(aprendices)` (una vez) y deja pasar a quien tiene `ColorSemaforo.VERDE`. Otros tipos: pasan todos, sin consultar. |
| `AccesoEventoService.puedeAcceder` | Primero la audiencia (barata) y después la elegibilidad del aprendiz, para no leer el semáforo de quien ya queda fuera. Mismos roles que antes. |
| `AudienciaDelEventoService` | `quienesPasan(personas, tipo)`: el personal pasa; los aprendices se preguntan **juntos** al puerto. `destinatarios` e `incluye` lo usan. |
| `ElegibilidadDeLosAvisos` (nuevo) | Dado un lote de recordatorios pendientes y sus eventos, devuelve los ids cuyo destinatario ya no es elegible (una pregunta por tipo de evento). |
| `DespachoDeRecordatoriosService` | Antes de clasificar, lee los eventos del lote y pregunta la elegibilidad; los avisos marcados se cancelan con `MOTIVO_NO_ELEGIBLE` y no se publican. Misma transacción del lote, mismo orden «marcar antes de publicar» (E-360). |

Sin cambios en `Evento`, `ResolverAudiencia`, la generación (`RecordatorioService`), los controladores ni la base.

## Qué pasa con cada camino

| Camino | Cómo queda cubierto |
|---|---|
| Lista y calendario (`GET /calendar/events`) | `EventoService.listar` → `puedeAcceder`. |
| «Mi agenda», huecos de horario y el acompañante | `EventosDelParticipanteFinder` → el mismo caso de uso de la lista. |
| «Próximo evento» de Hoy (`/home`) | `ProximoEventoService` → `puedeAcceder`. |
| Detalle | `EventoService.obtener` → `puedeAcceder` → 403 «No tienes acceso a este evento». |
| «Voy» / «No voy» | `ConfirmacionService.confirmar` → `puedeAcceder`; la fila guardada no se toca. |
| Recordatorios y «evento nuevo» | `RecordatorioService.generar` → `AudienciaDelEventoService.destinatarios`. |
| Recordatorio ya generado | `DespachoDeRecordatoriosService` + `ElegibilidadDeLosAvisos` (motivo `dejo_de_ser_elegible`). |
| «Quién respondió» y «Pasar lista» (D-256) | `PersonasConvocadasService` y `ListaDeAsistenciaService` → `destinatarios` / `incluye`; los que respondieron se agregan aparte y se conservan. |

## Límites aceptados

- **Alarma local de un «Voy» hecho desde el teléfono:** se quita cuando la app relee la lista (al abrirse y al entrar a
  Eventos). Si no se abre antes de la hora, suena. El servidor no envía la suya.
- El color es el del barrido horario de `points` (D-168): el cambio se ve en la siguiente consulta después del barrido,
  no en el instante en que la persona cumple un hábito.
- Un evento de mentoría en el rango cuesta una lectura del semáforo (3 consultas indexadas) por consulta de un aprendiz.

## Complexity Tracking

| Desvío | Por qué hace falta |
|---|---|
| Método por lote `elegiblesEntre` en el puerto | La audiencia de un evento «para todos» son cientos de aprendices y el generador corre cada 5 minutos: una lectura del semáforo por persona serían ~3 consultas × cientos × 12 por hora. El `default` mantiene a `esElegible` como única obligación del puerto. |
| Clase `ElegibilidadDeLosAvisos` | Mantiene `DespachoDeRecordatoriosService` por debajo del techo de 300 líneas y deja la regla del aviso ya generado en un solo lugar probable. |
