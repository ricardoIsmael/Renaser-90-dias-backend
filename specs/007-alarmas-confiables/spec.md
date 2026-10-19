# Feature Specification: Alarmas confiables de hábitos y avisos sin cruces

**Feature Branch**: `007-alarmas-confiables` (backend y app)
**Created**: 2026-10-19
**Status**: Draft
**Input**: «los usuarios dicen que sus alarmas no suenan» (teléfonos Honor; en Xiaomi funciona) y «las notificaciones se cruzan, hay un bug que no salió». Tras la prueba local del 2026-10-19 el dueño pidió: «la alarma creo que quiere así como la alarma en Android… procede».

## Clarifications

### Session 2026-10-19

- **El recordatorio de un hábito se comporta como una alarma del reloj de Android** (decisión del dueño): suena a la hora exacta aunque el teléfono esté en reposo o ahorrando batería, y el sistema lo trata como alarma del usuario (aparece como «próxima alarma» y con el ícono del despertador en la barra de estado). Vale para **todos** los recordatorios de hábitos que la persona activó.
- El dueño pidió avanzar sin más preguntas; lo que no dijo se resolvió con los supuestos de abajo.

### Evidencia de la prueba local (2026-10-19)

Emulador Android 17 con build debug, backend propio y base clonada `renaser_alarmas`; nunca producción.

| Caso | Observado |
|---|---|
| Hábito a las 11:17 con avisos 1 min antes y a la hora | Las dos alarmas del teléfono quedaron exactas y sonaron 11:16:00 y 11:17:00 |
| Forzar la detención de la app | Las alarmas del teléfono pasaron de 2 a 0 |
| Volver a abrir la app | Las 2 alarmas volvieron |
| Quitar la app de recientes (Android sin capa del fabricante) | El proceso muere, la app no queda detenida y las alarmas siguen |
| Aviso de inicio del servidor | Creado 11:15 «…empieza en 2 min», mientras el teléfono avisó 11:16 «En 1 min» y 11:17 |
| Hábito apagado solo por hoy | El servidor igual creó «…empieza en 14 min» |
| Hora cambiada después de enviado el aviso de inicio | El servidor no vuelve a avisar para la hora nueva |
| Texto del servidor | «Tu habito esta por empezar» (sin tildes) |

Honor (MagicOS), como Huawei, trata «quitar de recientes» o el ahorro de batería como detención forzada si la app está en «Gestionar automáticamente» (soporte oficial de Honor). Por eso allí las alarmas desaparecen hasta que la persona vuelve a abrir Renaser.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - La alarma del hábito suena a su hora, como el despertador (Priority: P1)

Como participante quiero que el recordatorio de mi hábito suene a la hora exacta, aunque el teléfono lleve rato bloqueado o en ahorro de batería, igual que la alarma del reloj.

**Why this priority**: es la queja de los usuarios; sin alarma a tiempo el hábito se pierde y baja el puntaje.

**Independent Test**: programar un hábito con aviso en el emulador y en un teléfono real, dejarlo bloqueado y en reposo, y comprobar que suena a la hora exacta y que el sistema lo muestra como próxima alarma.

**Acceptance Scenarios**:

1. **Given** un hábito con recordatorio a las 07:00 y aviso 10 min antes, **When** el teléfono está bloqueado y en reposo, **Then** suenan los dos avisos a las 06:50 y 07:00 en punto, con el sonido o la voz elegidos.
2. **Given** dos hábitos con avisos a 2 minutos de distancia, **When** el teléfono está en reposo, **Then** los dos suenan a su hora (ninguno se corre por el límite del sistema a avisos seguidos).
3. **Given** que el teléfono tiene un recordatorio de Renaser programado, **When** la persona mira la barra de estado o la pantalla de bloqueo, **Then** ve la próxima alarma como cualquier alarma del reloj.
4. **Given** un recordatorio que suena, **When** la persona lo toca, **Then** se abre el hábito en Training, como hoy.
5. **Given** varios hábitos a la misma hora, **When** suenan, **Then** suena uno solo que los nombra a todos, como hoy (E-410).
6. **Given** que la persona no dio el permiso «Alarmas y recordatorios», **When** se programa un recordatorio, **Then** se programa como hoy y la app sigue mostrando cómo darlo.

---

### User Story 2 - Saber que el teléfono borró mis alarmas y cómo evitarlo (Priority: P1)

Como participante con un teléfono Honor (u otra marca que cierra apps a la fuerza) quiero que la app me diga, al abrirla, que el teléfono cerró Renaser y borró mis alarmas, y que me lleve al ajuste exacto de mi marca para que no vuelva a pasar.

**Why this priority**: ningún tipo de alarma sobrevive a una detención forzada; la única salida es que la persona cambie un ajuste del teléfono, y hoy la guía dice «Batería → Sin restricciones», que en Honor no existe.

**Independent Test**: forzar la detención en el emulador, abrir la app y comprobar el aviso, la guía de la marca simulada y que las alarmas vuelven.

**Acceptance Scenarios**:

1. **Given** que el sistema detuvo Renaser a la fuerza desde la última vez que se abrió, **When** la persona abre la app, **Then** las alarmas se vuelven a programar y aparece un aviso corto: el teléfono cerró Renaser y por eso no sonaron tus alarmas, con un botón para arreglarlo.
2. **Given** un teléfono Honor o Huawei, **When** toca el botón, **Then** se abre la pantalla de inicio de aplicaciones de Renaser si el teléfono la tiene; si no, los ajustes de Renaser, con el camino escrito en una línea («Inicio de aplicaciones → Renaser → apaga “Gestionar automáticamente”»).
3. **Given** Xiaomi, Samsung, Oppo, Realme, Vivo u otra marca, **When** toca el botón, **Then** ve el camino de su marca y se abre la pantalla más cercana que exista.
4. **Given** que la app se cerró normalmente (quitada de recientes en un Android sin detención forzada, o cerrada por el sistema por memoria), **When** se abre, **Then** no aparece ningún aviso.
5. **Given** que la persona ya vio el aviso, **When** el teléfono vuelve a detener la app otro día, **Then** el aviso vuelve a aparecer, como mucho una vez por día.
6. **Given** Yo → Alarmas, **When** la persona entra, **Then** la guía de batería muestra el camino de su marca, no el texto genérico.

---

### User Story 3 - Un solo aviso por hábito y a la hora correcta (Priority: P2)

Como participante quiero recibir un aviso por cada recordatorio que elegí, a la hora que elegí, y ninguno de un hábito que apagué hoy.

**Why this priority**: los avisos duplicados o a otra hora confunden («se cruzan») y hacen que la persona apague los avisos.

**Independent Test**: en local, con un teléfono que confirma sus alarmas y otro que no, programar, apagar por hoy y cambiar la hora de hábitos, y revisar qué avisos crea y envía el servidor.

**Acceptance Scenarios**:

1. **Given** un hábito apagado solo por hoy (por fecha o por día de la semana) después de que se generó su registro del día, **When** llega su hora, **Then** el servidor no crea ni envía el aviso de inicio ni el de «se te vence».
2. **Given** un teléfono que confirmó sus alarmas, **When** llega el aviso de inicio, **Then** ese teléfono no recibe el push (como hoy); el navegador sí.
3. **Given** un teléfono que no confirmó (versión vieja, más de un día sin abrir la app o sin permiso de alarmas exactas), **When** el servidor le manda el aviso de inicio de respaldo, **Then** el aviso dice la hora del hábito («Te toca a las 07:00») y no una cuenta de minutos que ya no coincide; y si el teléfono también tiene su alarma, se ve un solo aviso por hábito y momento, no dos.
4. **Given** que el aviso de inicio de hoy ya salió y la persona cambia la hora del hábito a más tarde, **When** se acerca la nueva hora, **Then** el servidor vuelve a avisar una vez para la hora nueva.
5. **Given** que la persona cambia la hora y la nueva ya pasó, o el hábito ya está completado, **When** corre el servidor, **Then** no avisa.
6. **Given** cualquier aviso de hábitos del servidor, **When** se muestra, **Then** el texto lleva tildes y tuteo: «Tu hábito está por empezar», «Se te vence un hábito».

### Edge Cases

- Teléfono reiniciado: las alarmas vuelven tras el reinicio sin abrir la app (si el sistema no detuvo la app).
- Android 11 o anterior: no hay historial de detención forzada; no se muestra el aviso de la historia 2, la guía de Yo → Alarmas sí.
- Permiso «Alarmas y recordatorios» quitado mientras había alarmas: el sistema las cancela; al abrir la app se vuelven a programar como hoy y se pide el permiso con la línea de siempre.
- Despertar y Dormir, el aviso diario de objetivos, el repaso del domingo, las acciones con hora, los eventos y el Código Renaser: siguen como hoy salvo que usen el mismo programador de alarmas; si lo usan, también pasan a alarma tipo reloj.
- Muchos recordatorios en un día: la barra muestra solo la próxima; al sonar, pasa a la siguiente.
- Prueba de sonido de Yo → Alarmas (3 s): no debe aparecer como próxima alarma.
- Cambio de cuenta: al salir se cancelan todas y no queda «próxima alarma» de la cuenta anterior (E-413).
- Zona del teléfono distinta de la del perfil: el aviso del servidor dice la hora en la zona del programa; la alarma del teléfono sigue la hora del hábito en el teléfono, como hoy.
- Backend anterior con app nueva, y app vieja con backend nuevo: ninguno se rompe; la app vieja sigue recibiendo el respaldo.
- Hábito en curso (sesión especial que sigue abierta): no recibe aviso de inicio.

## Requirements *(mandatory)*

### Functional Requirements

**Alarmas del teléfono (app, Android)**

- **FR-001**: Todo recordatorio de hábito MUST programarse como alarma del usuario del sistema (tipo despertador), cuando el teléfono tiene el permiso «Alarmas y recordatorios»; sin el permiso se conserva el comportamiento actual.
- **FR-002**: La alarma MUST conservar lo de hoy: canal, sonido o voz elegidos, texto, ruta a Training al tocarla, agrupación de avisos que coinciden y cancelación por hábito, por cuenta y al cerrar sesión.
- **FR-003**: Al tocar la «próxima alarma» desde el reloj del sistema o la pantalla de bloqueo, MUST abrirse Renaser.
- **FR-004**: Las alarmas MUST seguir volviendo a programarse al abrir la app y al volver a primer plano (D-217), ahora también como alarmas tipo despertador; las ya existentes con el tipo anterior se reemplazan sin duplicarse.
- **FR-005**: La prueba de sonido y las notificaciones inmediatas MUST NOT aparecer como próxima alarma.

**Detención forzada y guía por marca (app, Android)**

- **FR-006**: Al abrir la app, MUST detectar si el sistema o la persona detuvo Renaser a la fuerza desde la última apertura (Android 11 o más nuevo).
- **FR-007**: Si fue así, MUST mostrar una vez por día un aviso corto (dos frases como máximo) con un botón que lleve a la guía de su marca; las alarmas se reprograman antes o al mismo tiempo.
- **FR-008**: La guía MUST reconocer al menos Honor, Huawei, Xiaomi (y Redmi, POCO), Samsung, Oppo, Realme, Vivo y OnePlus; para cada una, una línea con el camino y un botón que abra la pantalla del fabricante si existe, o los ajustes de Renaser si no. Otra marca ve la guía genérica actual.
- **FR-009**: La app MUST NOT pedir la excepción directa de optimización de batería; solo puede abrir la lista del sistema o los ajustes de la app.
- **FR-010**: Yo → Alarmas MUST mostrar la guía de la marca en lugar del texto genérico, sin cambiar el resto de la sección.

**Avisos del servidor**

- **FR-011**: El servidor MUST NOT crear ni enviar avisos de inicio ni de vencimiento de un hábito apagado para ese día (por fecha o por día de la semana), aunque su registro del día ya exista.
- **FR-012**: El aviso de inicio MUST indicar la hora del hábito en la zona del programa («Te toca a las 07:00»), no minutos calculados en el momento del barrido.
- **FR-013**: Si la hora del hábito cambia antes de su inicio y el aviso de inicio ya salió para la hora anterior, el servidor MUST avisar una vez más para la hora nueva; nunca dos veces para la misma hora.
- **FR-014**: El push de inicio y el de vencimiento MUST llevar una identidad por registro y tipo de aviso para que el teléfono y el navegador reemplacen, y no apilen, avisos del mismo hábito y momento; la alarma local del teléfono MUST usar la misma identidad para el aviso de inicio.
- **FR-015**: Un aviso que llega cuando su momento ya pasó (reintento tardío) MUST NOT enviarse como push ni guardarse en la bandeja (igual que los recordatorios de eventos).
- **FR-015b**: Un hábito cuyo registro del día está en curso (sesión especial abierta) MUST NOT recibir aviso de inicio.
- **FR-016**: Los títulos y cuerpos de los avisos de hábitos MUST llevar tildes y tuteo en español de Perú.
- **FR-017**: La regla actual de respaldo se mantiene: el teléfono que confirmó sus alarmas en las últimas 26 h no recibe el push de inicio; el navegador siempre lo recibe.

**Compatibilidad**

- **FR-018**: La app nueva MUST funcionar contra el backend anterior, y la app publicada contra el backend nuevo, sin errores visibles.
- **FR-019**: Nada de esto cambia los tabs más allá de la guía de Yo → Alarmas y el aviso al abrir (excepción a anotar en `AGENTS.md`).

### Key Entities

- **Recordatorio del hábito**: aviso local del teléfono para un hábito y una antelación; hora, texto, sonido o voz, ruta, identidad compartida con el aviso del servidor.
- **Aviso de hábito del servidor**: inicio o vencimiento de un registro del día; hora del hábito para la que se creó, identidad por registro y tipo, estado de envío.
- **Detención forzada**: hecho del sistema leído al abrir la app; fecha de la última vez que se mostró el aviso.
- **Guía de marca**: fabricante del teléfono, línea con el camino y pantalla a abrir.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En la prueba en emulador con el teléfono en reposo forzado, el 100 % de los recordatorios suena dentro de los 60 s de su hora, incluidos dos avisos a 2 minutos de distancia.
- **SC-002**: En un teléfono Honor real (o equivalente) con el ajuste de la guía aplicado, las alarmas suenan al día siguiente después de quitar la app de recientes.
- **SC-003**: Tras una detención forzada, el 100 % de las aperturas siguientes reprograma las alarmas y muestra el aviso como mucho una vez por día.
- **SC-004**: En la prueba local, un hábito apagado por hoy no genera ningún aviso del servidor; un cambio de hora genera exactamente un aviso para la hora nueva.
- **SC-005**: Para el aviso de inicio que conoce el servidor (la antelación más temprana del hábito), el teléfono muestra un solo aviso en la bandeja aunque llegue también el respaldo del servidor. Las otras antelaciones son solo del teléfono.
- **SC-006**: Las quejas de «la alarma no suena» de usuarios Honor bajan a cero en las dos semanas siguientes a publicar la versión, entre quienes aplicaron la guía.

## Assumptions

- «Como la alarma de Android» se entiende como hora exacta y trato de alarma del usuario (próxima alarma e ícono del despertador). No incluye una pantalla completa que suene hasta apagarla: Google Play restringe esa pantalla a apps de reloj y llamadas. Si el dueño la quiere, es otra spec.
- Ningún tipo de alarma sobrevive a una detención forzada; la historia 2 lo mitiga, no lo elimina. Con la app detenida tampoco llega el push del servidor, así que el respaldo no cubre ese caso.
- Las pantallas de los fabricantes cambian entre versiones; si una no abre, se cae a los ajustes de Renaser sin error.
- El texto del aviso es corto (dos frases, regla del dueño del 28/09) y en tuteo.
- Hace falta un APK nuevo para todo lo de la app; el backend nuevo sirve a la app publicada sin cambios visibles más allá de los textos.
- No se toca `alarmas/api/preferenciasDeNotificacion.ts` ni `alarmas/components/InterruptoresDeAvisos.tsx` (spec 005), ni `TipoNotificacion` del backend.
- Las pruebas se hacen solo en local (emulador, backend propio, base clonada) y en un teléfono Honor real con build de prueba; nunca contra producción.
