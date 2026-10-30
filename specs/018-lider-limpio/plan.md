# Implementation Plan: Vista del líder, limpia

**Branch**: `018-lider-limpio` (backend y app) | **Date**: 2026-10-29 | **Spec**: `spec.md`
**Input**: spec aprobada por el dueño (vista previa `vista-previa.html`).

## Summary

Tres cosas: (1) un control de papel en Hoy para quien lidera mentores; (2) dos respuestas arriba de la ficha de la persona,
una de las cuales necesita un dato que el servidor no daba («¿Programó?»); (3) quitar los párrafos de ayuda de las pantallas
del líder. El único trabajo de backend es un campo **aditivo y opcional** en una respuesta que ya existe. Sin migración, sin
endpoint nuevo, sin tabla.

## Technical Context

- **Backend**: Java 25 / Spring Boot 4.1, hexagonal con Spring Modulith. Módulo `leadership` (lee `habits.api` y `rocks.api`,
  que ya existen; ninguno de los dos depende de `leadership`: el ciclo no se cierra).
- **App**: Expo SDK 57 / React Native / TypeScript. `ControlSegmentado`, `Presionable`, `AvatarPersona` y las lecturas del
  semáforo ya existen.
- **Pruebas**: JUnit sin Spring para el dominio y los servicios (con los dobles de `BancoDeCumplimiento`), `@WebMvcTest` para
  el contrato y la autorización negativa; Jest con `react-test-renderer` en la app.
- **Restricciones**: reloj por `Clock` y «hoy» en la zona de la persona (regla 02); ninguna llamada a un puerto de IA; la
  app tolera el backend anterior (zod con `.catch(null)`).

## Constitution Check

| Principio | Cumple | Cómo |
|---|---|---|
| I. Las dependencias apuntan al dominio | Sí | La regla del «Sí» vive en `ProgramacionDelDia` (domain, sin Spring). `ProgramacionDeLaPersona` solo junta lecturas por `api/`. El controller no cambia: mapea la salida. |
| II. No se inventan reglas | Sí, con un supuesto | El umbral («la mitad de los hábitos y todas las acciones») se propuso en la spec y queda escrito como supuesto a confirmar; vive en un solo método. |
| III. Pruebas en verde | Parcial, dicho | Unitarias y de capa web corridas; `clean verify` e integración con Testcontainers **no** (prohibido en esta corrida). Ver el informe. |
| IV. La base guarda historia | Sí | Sin migración; solo lecturas. |
| V. El tiempo es del participante | Sí | «Hoy» sale de `clock.now()` en la zona de la persona; prueba con el reloj en la madrugada UTC. |
| VI. Los cinco tabs | Sí | Hoy cambia por pedido del dueño; se anota en `AGENTS.md` con «lo que cambia y nada más». |
| VII. Documentar | Sí | D-291 en `MODULOS_A_AVANZAR.md`, E-654 en la bitácora, contrato de la spec 005 ampliado, `AGENTS.md`. |

## Design

### Backend

1. `rocks.api.AccionesDelDiaFinder.AccionDelDia` suma `LocalTime horaInicio` (con el constructor de 5 argumentos para los
   llamadores de antes) y `agendada()`. `AccionesDelDiaFinderService` la llena con la `RocaDiaria`.
2. `leadership.domain.model.cumplimiento.ProgramacionDelDia` (record, sin Spring): las cuatro cuentas y `programo()`.
   `de(...)` devuelve vacío si no hay hábitos que le toquen hoy.
3. `leadership.application.services.ProgramacionDeLaPersona`: lee `HorarioDelDiaFinder.deFecha(persona, hoy)` (descarta
   apagados y en pausa; cuenta `personalizado`) y `AccionesDelDiaFinder.accionesEntre(persona, hoy, hoy)`. No lee nada si el
   programa no está activado.
4. `LecturaDeLaFicha` suma el campo a su `Lectura`; `ArmadoDeLaFicha` lo pasa a `SemanaDeLaPersona.programacion`;
   `SemanaDeLaPersonaResponse` lo expone como `programacion` (nulo si no hay).

**Por qué no un endpoint ni una tabla aparte**: la ficha ya autoriza (líder, Admin, Alquimista, cuenta activa, persona del
grupo hoy) y registra la consulta; un endpoint nuevo duplicaría ese guard. **Por qué `HorarioDelDiaFinder` y no una consulta
SQL**: la precedencia fecha > día de la semana > cambio general > preferencia > catálogo ya está resuelta ahí (regla 01).
**Por qué no se atrapan las excepciones del otro módulo**: pasan por un `@Transactional` que ya está dentro de la transacción
de la ficha; una excepción atrapada la dejaría marcada «solo rollback». Los guards de `PersonasDelGrupo` (activa, del
grupo, programa activado) son los que evitan que lancen.

### App

1. `features/hoy-por-papel/`: `papelesDeHoy` (puro), `papelRecordado` (AsyncStorage por cuenta), `usePapelDeHoy`,
   `ControlDePapelDeHoy`, `MiGrupoEnHoy`, `MisMentoresEnHoy`, `FilaDeLista`.
2. `HoyScreen`: calcula los papeles, pone el control bajo la línea de fase y día, oculta (sin desmontar) lo del programa
   propio con otro papel, y quita las tres tarjetas del líder (se borran sus componentes).
3. `CumplimientoDelLiderScreen` suma `grupoInicial` (arranca en el grupo de la fila y volver sale a Hoy).
4. `ControlSegmentado` suma `sinAnimacion` (apagado por defecto: los demás controles no cambian).
5. Ficha: `RespuestasDeLaPersona` + `utils/respuestasDeLaPersona` (cuentas puras), el esquema lee `programacion` con
   `.catch(null)`.
6. Menos texto: las ediciones de la tabla de la US3.

### Qué no se toca

Otros tabs, Administración (`SemaforoAdminScreen` y su línea de ayuda), la sección de huecos de la ficha (la lleva otra
rama), las rutas de cierres (otra rama), permisos, tablas, migraciones.

## Complexity Tracking

| Desvío | Por qué | Alternativa descartada |
|---|---|---|
| Lo del programa propio se oculta con `display: none` en vez de desmontarse | Cambiar de papel es instantáneo y no se pierde la conversación del orbe ni se relee nada | Desmontar: cada cambio volvería a pedir y a pintar |
| `ControlSegmentado` gana una prop | El dueño pidió que el cambio no se anime; el control compartido animaba la píldora | Un control propio: duplicaría el componente |
| Se borran tres componentes de tarjeta | Quedaban sin uso: el líder siempre tiene control | Dejarlos muertos |
