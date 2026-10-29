# Feature Specification: Training por hora

**Feature Branch**: `014-training-por-hora` (app y una lectura de solo lectura en el backend)
**Created**: 2026-10-29
**Status**: Aprobada por el dueño (vista previa v2)
**Input**: Pedido del dueño (2026-10-29), sobre la maqueta de gerencia y la vista previa
https://claude.ai/artifact/RcbkFpksPNkeEPZNf4Yg48 (copia en `vista-previa.html`): «vamos con ese diseño en Training, me
gusta», con la retroalimentación de abajo. Lo pidió con urgencia.

## Clarifications

### Session 2026-10-29 (decisiones del dueño)

- **La pantalla de la maqueta de gerencia, más el engranaje de la propuesta.**
  - Arriba: TRAINING, la fase y el día, el día elegido («Jueves 29») y la semana de lunes a domingo.
  - Debajo, una sola lista con los hábitos, las acciones del día y los eventos, ordenada por la hora que la persona
    planificó.
- **Lo que haces desaparece y sube lo que sigue.** «Despierto a las 3:20, ya lo hice: desaparece y sigue el de las
  6.» El primero de la lista dice «Te toca ahora» (su hora ya llegó) o «Lo que sigue».
- **Lo que se te pasa va al final.** «Me toca 5:30, sigue hasta las 6; si no lo hice, sube el de las 6 y se va al
  último el que no se cumplió, para igual subir evidencia.»
  - Un pendiente queda atrasado cuando llega la hora del siguiente.
  - Baja a «Se te pasó la hora» y todavía se registra hoy, con menos puntos (D-259).
  - Los eventos que ya terminaron salen de la lista.
- **Un solo toque.** Tocar la fila abre UNA hoja: la guía del hábito, plegada, y debajo el registro específico de su
  tipo, el mismo de hoy. «No 2 pestañas.»
  - Foto: la cámara y «¿Qué sentiste?».
  - Pastilla y Audioterapia: el audio y sus preguntas.
  - Clase diaria.
  - Post diario.
  - Despertar y Dormir: «Marcar como hecho», que registra la hora.
  - El ✓ de la fila marca sin abrir nada.
- **Días anteriores: el resultado de tu planificación.** Lo que hiciste lleva ✓; lo que no subiste, candado. «Yo no
  puedo subir un hábito del día anterior.» No se registra nada.
- **Días que vienen**: lo planificado, sin botones.
- **El engranaje es solo para planificar.** «Ya no subir contenido.» Adentro va lo que hoy se ve afuera:
  - «Arma tu semana los domingos»;
  - las cinco dimensiones con su avance;
  - cada dimensión, con «Planificar» igual que hoy (specs 008 a 011);
  - tocar un hábito abre su editor;
  - ninguna fila registra.
- **No se pierde «pausar»**: «creo que le quitaste lo de no hacer o suspender un día». Queda como hoy: al apagar un
  hábito propio se elige «Solo hoy» (vuelve mañana) o «Hasta que yo lo reactive». Los del programa no se pausan. Un
  hábito pausado no aparece hoy en la lista.
- **Lo hecho, plegado al final** en «N hechos hoy». Lo agregó el equipo para no perder lo hecho de vista; el dueño lo
  puede quitar.
- **Queda fuera** (necesita servidor, va en otras specs): «Cerrar y evaluar mi día», «Cierre y plan de mañana» y el
  bloque de concentración.

### Lo que hay hoy (verificado el 2026-10-29)

- `GET /api/v1/habit-tracks/today` trae cada registro con su catálogo: título, tipo, **guía** y horario. Las guías
  las carga el equipo en el panel (`/admin/habits/{id}/guides`, tabla `guias_habito`).
- **Las horas de cada día** salen de `GET /habit-preferences`, con `weekdays[i].triggerTime` (spec 008).
- **Las acciones de otra fecha** salen de los endpoints de `/rocks` que ya usa «Agendar otro día».
- **Los eventos** salen de la sección Eventos, igual que «Mi agenda».
- **Se reutilizan sin tocarlos**:
  - `PlanificarDimensionModal`, con su «Pausar»;
  - `RegistroConFotoModal` y `useRegistroConFoto`, con los PR #44 y #46 en curso;
  - `PastillaRenacerModal`, la Clase diaria, el cierre de Despertar y Dormir y el aviso `habitoCumplido`, del que
    dependen el fénix y las celebraciones.
- **No hay lectura de días anteriores de la propia persona** hábito por hábito: el servidor solo entrega «hoy».

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mi día por hora (Priority: P1)

**Independent Test**: con Despertar 05:30 hecho, Agua 06:00 pendiente y son las 06:10, la lista empieza con Agua («Te
toca ahora»). Despertar aparece solo en «1 hecho hoy».

1. **Given** el día de hoy, **When** abro Training, **Then** veo mis hábitos, acciones y eventos ordenados por la hora
   planificada, sin las cinco dimensiones.
2. **Given** que registro un hábito, **When** termina, **Then** sale de la lista y el siguiente queda primero.
3. **Given** un pendiente de las 05:30 y otro de las 06:00, **When** son las 06:00 y no hice el de las 05:30, **Then**
   el de las 06:00 sube y el de las 05:30 baja a «Se te pasó la hora», donde todavía lo registro hoy.
4. **Given** lo sin hora, **When** miro la lista, **Then** va en «Sin hora», antes de «Se te pasó la hora».

### User Story 2 - Un toque: guía y registro (Priority: P1)

1. **Given** un hábito con foto, **When** toco su fila, **Then** se abre una sola hoja con la guía plegada y el
   registro de siempre (cámara, «¿Qué sentiste?» en los rituales). Al entregar se marca y sale de la lista.
2. **Given** la Pastilla, la Clase o el post, **When** toco la fila, **Then** veo la guía, si la hay, y su registro de
   siempre en la misma hoja.
3. **Given** un hábito sin guía cargada, **When** lo abro, **Then** no aparece la sección de guía.

### User Story 3 - La semana (Priority: P2)

1. **Given** un día anterior, **When** lo elijo, **Then** veo lo que hice con ✓ y lo que no con candado, sin botones.
   Tocar uno con candado dice «Ese día ya cerró. No se puede registrar».
2. **Given** un día que viene, **When** lo elijo, **Then** veo lo planificado, sin botones.

### User Story 4 - El engranaje, solo para planificar (Priority: P1)

1. **Given** el engranaje, **When** lo toco, **Then** veo «Arma tu semana» y las cinco dimensiones. Cada una abre su
   detalle con «Planificar» y sus hábitos, que abren su editor. No hay ningún botón de registrar.
2. **Given** un hábito propio en Planificar, **When** lo apago, **Then** elijo «Solo hoy» o «Hasta que yo lo
   reactive», como hoy.

### Edge Cases

- **Cambio de día con la app abierta**: la lista pasa al día nuevo, como hoy (`useActualizacionDelDia`, spec 002).
- **Sin red al registrar**: la foto queda pendiente como hoy (#46). La fila sigue en la lista hasta que el servidor
  confirma.
- **Un aviso del teléfono que abre un hábito** (`aperturaDesdeAviso`): abre su hoja de registro.
- **Web**: sin cámara nativa, igual que hoy.
- **Backend sin la lectura de días anteriores**: el día anterior muestra lo planificado con «Lo que hiciste ese día
  está en Yo → Mis evidencias», sin ✓ ni candado inventados.

## Requirements *(mandatory)*

- **FR-001**: Training MUST mostrar la lista por hora del día elegido y la semana. Las dimensiones MUST NOT verse
  afuera.
- **FR-002**: En el día de hoy, lo hecho MUST salir de la lista, al plegado «N hechos hoy». Lo atrasado MUST ir al
  final, en «Se te pasó la hora», y seguir registrándose hoy. Los eventos terminados MUST salir.
- **FR-003**: Tocar una fila de hoy MUST abrir una sola hoja con la guía (si existe) y el registro de su tipo, el de
  hoy. El ✓ de los hábitos sin evidencia MUST marcar sin abrir nada.
- **FR-004**: Los días anteriores MUST NOT permitir registrar. Con la lectura del servidor, MUST mostrar ✓ o candado.
  Sin ella, MUST NOT inventarlos.
- **FR-004a** (aprobado por el dueño el 2026-10-29, «cuidado con el endpoint que se puede ejecutar 1000 veces»):
  `GET /api/v1/habit-tracks/day?date=` tiene la misma forma que `/today` y es de solo lectura: no crea tracks y hace
  una sola consulta.
  - Solo acepta la propia persona y días ya cerrados de la semana en curso, en su zona, nunca antes de su Día 1. Otra
    fecha da 400.
  - Responde con `Cache-Control: private, max-age=300`.
  - La app la pide solo al tocar un día anterior, una vez por fecha y por cuenta en la sesión, comparte el pedido en
    vuelo y no reintenta sola.
- **FR-005**: Los días que vienen MUST mostrar lo planificado, sin registrar.
- **FR-006**: El engranaje MUST tener «Arma tu semana», las dimensiones y «Planificar», con la pausa de hoy, y MUST
  NOT registrar.
- **FR-007**: MUST NOT cambiar la forma de registrar ni los pedidos al servidor de cada tipo, ni cómo se emite
  `habitoCumplido`. Se mantiene lo de hoy: `completarRegistro` y `useRegistroConFoto` lo emiten; la Pastilla y la Clase
  diaria, que se cierran por su propio endpoint, no.
- **FR-008**: Las reglas de §2–§6 de `AGENTS.md`: un solo scroll, 48 px de área de toque, Jost, tipo oración,
  claro y oscuro, retroceso del sistema y tuteo.

## Success Criteria *(mandatory)*

- **SC-001**: Registrar un hábito desde la lista toma 1 toque más su registro, sin pasar por otra hoja.
- **SC-002**: En el E2E, lo hecho sale de la lista el 100 % de las veces y lo atrasado baja al llegar la hora del
  siguiente.
- **SC-003**: Ningún día anterior permite registrar.
- **SC-004**: Planificar, la pausa y las alarmas dan los mismos pedidos al servidor que hoy.

## Assumptions

- **Autorización del tab** (constitución VI): el pedido del dueño del 2026-10-29 autoriza este cambio en Training.
  Se anota en `AGENTS.md` en su propio bloque, junto a las entradas de Training y no al final de la lista (ahí
  escriben los PR #44 a #46), y se mueve a `docs/producto/` cuando entre el #48.
- Hace falta un APK nuevo.
- Todo se prueba en local, con base clonada y build debug; nunca contra producción.
