# Implementation Plan: Acuerdo de transformación

> **Corregido 2026-11-02 (D-315, decisión del dueño): el cronograma de pagos NO va en los términos.** Textual: «lo de los pagos no debe de salir en términos y condiciones
> ya que será asíncrono bro, eso saldrá luego que el administrador lo llene». Lo que dice este plan de **firmar el cronograma en el mismo acto** (la historia 2, FR-006,
> FR-008, FR-009, SC-002, el supuesto 6, la sección «Mi cronograma de pagos» y la casilla de la cláusula 19, `agreement.schedule`, `scheduleFingerprint`, `scheduleSigned`,
> `cronograma_huella` y el 409 «Tu cronograma cambió mientras leías») **ya no rige**. Ahora: los términos y la firma no muestran ni exigen el cronograma, ni en el primer ingreso
> ni al volver a aceptar; «Mi aceptación» (nombre, programa y fechas) **se queda igual**; la persona lo ve y lo confirma en Yo → «Mi ingreso» (spec 032) cuando Administración
> lo registre, y al aceptar «Con cronograma» las cuotas son opcionales. Las 23 cláusulas no cambian (la 19 sigue hablando del acuerdo de pagos). El servidor ya no manda
> `schedule` ni lee `scheduleFingerprint` (un cliente que lo mande no falla: se ignora); la columna `aceptaciones_de_terminos.cronograma_huella` se deja sin borrar y sin
> usar. Ver D-315 en `docs/MODULOS_A_AVANZAR.md`.


**Branch**: `039-acuerdo-de-transformacion` (backend y app) | **Date**: 2026-11-02 | **Spec**: [spec.md](./spec.md)

## Summary

Cuatro piezas sobre lo que ya existe: (1) **V162** publica el texto nuevo en las tres formaciones con las tablas de la spec 027; (2) el servidor arma **«Mi aceptación»** (nombre, programa, inicio y fin) con la regla de la spec 036 y se lo dice a la app en `GET /onboarding/terms`; (3) al aceptar guarda **lo que se mostró** junto a la constancia; (4) si la persona entra con cronograma, la misma firma **confirma el cronograma** con la confirmación de la spec 032. La app agrega dos bloques a la pantalla de Términos y los pasa en el pedido.

## Technical Context

**Language/Version**: Java 25 / Spring Boot (según `pom.xml`); app Expo SDK 57 / TypeScript estricto.
**Storage**: ninguna tabla nueva. V162 agrega cuatro columnas nulas a `aceptaciones_de_terminos` y tres filas a `terminos_versiones`.
**Testing**: JUnit 5 + AssertJ (dominio y servicios sin Spring, `FixedClock` en la madrugada UTC), IT con Testcontainers y el `RelojDePrueba` compartido (regla 03: ninguna IT declara su propio `Clock`; limpian lo que siembran y no suponen una base vacía), `ArchitectureTest`; app: `tsc --noEmit` y Jest.
**Constraints**: reglas de `.claude/rules/` (02 tiempo y zonas: el día de alguien sale de `clock.now().atZone(zona)`, nunca de `clock.today()`).

## Constitution Check

- **Hexagonal / Modulith**: `onboarding` lee las fechas del programa por un finder nuevo de `users.api` (`FechasDelProgramaFinder`), igual que ya lee `ParticipacionProgramaFinder`; ninguna consulta cruzada a tablas ajenas. El cronograma lo da un caso de uso interno de `onboarding.pagos` (`CronogramaParaFirmarUseCase`); la regla de la huella sigue siendo de `CronogramaDePagos`.
- **No inventar reglas**: «mañana» es `ParticipacionPrograma.inicioAlActivar` (spec 036) y el último día es `ultimaFechaDelPrograma` (D-168); no se reescriben.
- **Transacción**: la constancia y la confirmación del cronograma van en la transacción del hito de Términos (`EstadoOnboardingService.aceptar`), como la constancia de la spec 027.

## Decisiones técnicas

1. **Texto literal por formación** (no un marcador `{programa}`): el editor de Administración muestra exactamente lo que verá la persona, y copiar el texto de una formación a otra no deja marcadores sueltos. El nombre del programa sale de una sola tabla (`ProgramaDelAcuerdo`) que la prueba de V162 compara con el texto.
2. **Dos párrafos en una cláusula** = una cláusula con una línea en blanco entre párrafos (`\n\n`): la numeración sigue siendo la posición y el editor/validaciones de la spec 027 no cambian.
3. **El servidor arma «Mi aceptación»**: la app no calcula fechas (regla 02) ni nombres de programa. El pedido de aceptar lleva el inicio y la huella que la persona vio; el servidor rechaza (409) si ya no coinciden, por la misma razón que rechaza una versión vencida (spec 027).
4. **Nombre del cronograma = nombre completo del perfil** (el «Participante»): la firma ya es la declaración; no se le pide escribir su nombre otra vez.
5. **Cuándo se ofrece el cronograma**: `FIRST_TIME` + cronograma + sin confirmación vigente. `NEW_VERSION` y `FIRST_IN_FORMATION` nunca (FR-006).
6. **V162 descarta el borrador copia** del Diplomado y la Certificación solo si sigue idéntico a la copia de la versión 1 de la Escuela.

## Cambios por archivo (backend)

| Archivo | Cambio |
|---|---|
| `db/migration/V162__acuerdo_de_transformacion.sql` | columnas de la constancia, tres versiones, descarte de borradores copia; idempotente |
| `users/domain/.../ParticipacionPrograma` | `inicioQueSeMuestra`, `finQueSeMuestra` |
| `users/api/FechasDelProgramaFinder` + `users/application/services/FechasDelProgramaService` | fechas del programa de una persona |
| `onboarding/domain/model/terminos/ProgramaDelAcuerdo` | nombre del programa y su etiqueta |
| `onboarding/application/ports/in/pagos/CronogramaParaFirmarUseCase` + servicio | el cronograma a firmar y su huella |
| `onboarding/application/services/terminos/ArmadorDelAcuerdo` | arma «Mi aceptación» (GET y POST) |
| `TerminosDeLaPersonaService`, `ObtenerTerminosUseCase`, `TerminosController` | `agreement` en la lectura y en «releer» |
| `AceptacionDeTerminosService`, `RegistrarAceptacionDeTerminosUseCase`, `EstadoOnboardingService`, `AceptarHitoRequest`, `AceptarHitoOnboardingUseCase` | pedido con inicio y huella; confirma el cronograma |
| `AceptacionesDeTerminosPort` + `AceptacionesDeTerminosJdbcAdapter` | guarda y lee lo mostrado |
| `ConsultaDeTerminosService`, `TerminosAdminController` | la constancia lleva programa y fechas |
| `docs/MODULOS_A_AVANZAR.md` | D-312 |

## Pruebas (backend)

- `ProgramaDelAcuerdoTest`: nombres y etiquetas (90 días, 3 meses, 1 año).
- `ParticipacionProgramaTest`: inicio y fin para 90 días, 3 meses (90) y 1 año (365), con el reloj en la madrugada UTC y con ajuste.
- `FechasDelProgramaServiceTest`: sin fila, activado, sin activar (aprendiz) y sin activar (personal).
- `ArmadorDelAcuerdoTest` y `AceptacionDeTerminosServiceTest`: nombre, programa, fechas; guarda lo mostrado; inicio vencido → 409; con huella confirma; huella vencida → 409; sin huella no confirma.
- `AcuerdoDeTransformacionIT` (HTTP real): V162 ejecutada dos veces (idempotente), el texto por formación, `agreement` sin y con cronograma, aceptar guarda lo mostrado, con cronograma «Mi ingreso» queda `CONFIRMED` con la misma huella, sin cronograma no hay confirmación, re-aceptar no ofrece cronograma.
- `TerminosDePrueba` ahora restablece el estado de V149 (la base de las pruebas de edición de la spec 027); `EstadoOnboardingServiceTest` y `ArchitectureTest` ajustados.

## App

`TerminosScreen` y `TerminosActualizadosScreen` suman `MiAceptacion` y `MiCronogramaDePagos`; `useSellarTerminos` lleva el inicio y la huella; el texto que la app lleva dentro (servidor anterior) pasa al nuevo; `TerminosAceptadosVista` y la constancia de Administración muestran lo guardado; Maestro `acuerdo-de-transformacion/`; `AGENTS.md`.
