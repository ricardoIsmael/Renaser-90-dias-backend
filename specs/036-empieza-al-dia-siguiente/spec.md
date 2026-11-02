# Feature Specification: El programa empieza al día siguiente, sin elegir el Día 1

**Feature Branch**: `036-empieza-al-dia-siguiente` (backend y app)
**Created**: 2026-11-02
**Status**: Implementada (2026-11-02): backend en esta rama y app en `036-empieza-al-dia-siguiente` del frontend (sobre `botones-tipo-oracion`). Falta el E2E en el emulador. Sin preguntas abiertas: los supuestos de abajo se le avisan al dueño.
**Input**: Cambio de gerencia (2026-11-02), textual: «Después de la ficha inicial se preguntaba qué día quiere empezar. Ahora iniciará al día siguiente: el usuario no podrá elegir, lo hará al día siguiente. No es un gran cambio, pero sí cambiaría el mensaje: que comienza al día siguiente.»
Decisión: **D-309** (`docs/MODULOS_A_AVANZAR.md`). Cambia el texto de D-66, D-84, D-260, D-261 (cómo se elige el Día 1); no cambia nada más del reloj del programa.

## Clarifications

### Session 2026-11-02 (pedido del dueño)

- **Qué cambia**: la persona **ya no elige** su Día 1. Su programa empieza **al día siguiente** de confirmarlo. La pantalla deja de ofrecer fechas y dice que **empieza mañana**.
- **A quién**: a todos. El aprendiz al terminar la ficha y los Términos; el personal (mentor, guía, líder, administrador, alquimista) que se suma a su programa propio; y las cuentas antiguas que
  nunca eligieron su Día 1 (la recuperación de E-594/E-596/E-597). **Una sola regla**.
- **Qué no cambia**: la duración (90 días, 3 meses o 1 año según la formación, spec 022), el reloj derivado de fechas (D-67), el estado «Tu programa empieza el martes 3 de noviembre» del día 0,
  el onboarding (Ficha o diagnóstico → Términos → este paso → Hoy), ni lo que ya eligieron quienes activaron antes (sin reasignar nada).
- **El dueño pidió poco texto.** La pantalla nueva dice: «Tu programa empieza mañana», la fecha («Martes 3 de noviembre»), una línea con el plazo («Son tus 90 días.») y «Entendido».

### Lo que hay hoy (verificado en el código el 2026-11-02)

| Pieza | Hoy |
|---|---|
| Regla | `ParticipacionPrograma.activarPrograma(fecha, clock)` acepta **mañana, +2 o +3** en la zona de la persona (`MAX_DIAS_ESPERA_ACTIVACION = 3`); nunca hoy (D-66/D-84). |
| Lectura | `GET /api/v1/onboarding/activate-program` → `{ activated, validStartDates: [mañana, +2, +3], startDate }`. |
| Escritura | `POST /api/v1/onboarding/activate-program { startDate }` (obligatoria). 400 fuera de la ventana, 409 si ya estaba activado con otra fecha, 200 idempotente con la misma. Sin guard de rol (D-260); crea la fila del personal sin fila (D-261). Serializada por usuario (E-596). |
| App | `ActivarProgramaScreen` («Elige tu Día 1», tres opciones, «Confirmar mi Día 1») en el onboarding y, a pantalla completa, desde «Elegir mi Día 1» (Training, Hoy, Plan, Yo, Mi Radar). |
| Otro camino | `POST /api/v1/mentor/activate-tracking`: el personal arranca **hoy** (D-07). Lo llaman solo los APK 1.5.0 y anteriores; la app nueva no lo usa desde D-261. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El aprendiz termina la ficha y su programa empieza mañana (Priority: P1)

Como aprendiz, al terminar la ficha y aceptar los Términos quiero ver que mi programa empieza mañana, sin tener que elegir nada.

**Why this priority**: es el pedido.

**Independent Test**: con un aprendiz recién aprobado, terminar ficha y Términos: la pantalla dice «Tu programa empieza mañana», la fecha de mañana y «Son tus 90 días.»; tocar «Entendido» activa el programa con `fecha_inicio` = mañana en su zona y entra a Hoy.

**Acceptance Scenarios**:

1. **Given** un aprendiz sin Día 1 en cualquier hora del día (también 23:58), **When** llega al último paso del onboarding, **Then** ve «Tu programa empieza mañana», la fecha de mañana en su zona («Martes 3 de noviembre»), «Son tus 90 días.» (o «Son tus 3 meses.» / «Es tu año.» según su formación) y un solo botón, «Entendido». No hay lista de fechas.
2. **Given** esa pantalla, **When** toca «Entendido», **Then** el programa queda activado con inicio mañana, suena el logro y entra a Hoy, donde ve «Tu programa empieza el martes 3 de noviembre».
3. **Given** que se pierde la red al tocar «Entendido», **Then** se dice «No pudimos activar tu programa. Revisa tu conexión e inténtalo de nuevo.» y el mismo botón reintenta. Si no se pudo ni leer la fecha, «Reintentar».
4. **Given** que la persona dejó la pantalla abierta pasada la medianoche, **When** toca «Entendido», **Then** el servidor rechaza (400) porque la fecha que se mostraba ya no es «mañana»; la pantalla se refresca con la fecha nueva y dice «Tu fecha cambió. Ahora tu programa empieza mañana, el …»; nunca se activa con una fecha distinta a la que se vio.
5. **Given** que el programa ya estaba activado (cerró la app a mitad del paso), **Then** sigue directo a Hoy sin mostrar nada.

---

### User Story 2 - El personal se suma a su programa propio: empieza mañana (Priority: P1)

Como mentor, guía, líder, administrador o alquimista, quiero hacer mi programa y que empiece mañana, igual que el aprendiz.

**Acceptance Scenarios**:

1. **Given** personal sin fila en el programa, **When** ve en Hoy o en Plan «Haz tu programa de 90 días · Empieza mañana ›» y la toca, **Then** se abre la misma pantalla corta y «Entendido» le crea la fila ya activada con inicio mañana (D-261).
2. **Given** personal con la fila sin Día 1, **Then** Hoy y Plan dicen «Todavía no empezaste tu programa · Empieza mañana ›» y abren la misma pantalla.
3. **Given** Training, Plan (estado vacío), Yo, y Mi Radar («Mi Radar empieza en tu día 8. Primero empieza tu programa.»), **Then** el botón que abría el selector se llama «Empezar mañana» y abre la misma pantalla. En ningún lugar queda «Elegir mi Día 1», «Elige tu Día 1» ni «Confirmar mi Día 1».
4. **Given** «Ahora no» de la fila de Hoy, **Then** sigue como hoy (posponer la invitación al personal).

---

### User Story 3 - Cuentas antiguas sin Día 1 (Priority: P2)

Como persona que nunca eligió su Día 1 (cuenta anterior a este cambio), quiero poder empezar con la misma regla.

**Acceptance Scenarios**:

1. **Given** una participación pendiente de una cuenta antigua, **Then** Hoy, Plan y Training le ofrecen empezar (mismo texto) y «Entendido» la activa con inicio mañana. El servidor no le asigna fecha por su cuenta (E-594: nada se asigna solo).
2. **Given** quien ya había elegido +2 o +3 días, **Then** nada se toca: conserva su fecha y su estado «Tu programa empieza el …».

---

### User Story 4 - Un APK anterior sigue funcionando (Priority: P1)

**Acceptance Scenarios**:

1. **Given** el APK anterior (selector de fechas) contra el servidor nuevo, **When** consulta las fechas, **Then** recibe una sola (mañana), la pantalla muestra esa única opción preseleccionada y «Confirmar mi Día 1» activa con esa fecha. No se rompe.
2. **Given** que ese APK mandó una fecha que ya no es mañana (la guardó antes de medianoche), **Then** el servidor responde 400 con un mensaje claro y el APK vuelve a leer las fechas, como ya hacía con su ventana (E-594).
3. **Given** el APK nuevo contra el servidor anterior (no publicado todavía), **Then** funciona: manda la primera de las fechas que el servidor ofrece (mañana).

## Requirements *(mandatory)*

- **FR-001**: El Día 1 de quien activa su programa es **siempre mañana**: el día calendario siguiente a hoy en la **zona de la persona** (`participantes_programa.timezone`, `America/Lima` por defecto). Nunca hoy, nunca +2 o +3.
- **FR-002**: `GET /api/v1/onboarding/activate-program` devuelve en `validStartDates` **una sola fecha: mañana**; vacía si ya está activado (igual que hoy). Los demás campos no cambian.
- **FR-003**: `POST /api/v1/onboarding/activate-program` **calcula la fecha en el servidor**. `startDate` pasa a ser **opcional**: ausente = mañana. Si viene y **no es mañana** → **400** con el mensaje «El programa empieza siempre mañana (…), recibida: …» (no se ignora en silencio: el APK anterior y la app nueva vuelven a leer y nunca activan con una fecha distinta a la que mostraron).
- **FR-004**: Activar dos veces es idempotente: ya activado + sin fecha, o con la misma `fecha_inicio` → 200 sin cambios; con otra fecha → 409 (como hoy).
- **FR-005**: La duración del programa (`duracion_dias`: 90 / 90 del Diplomado de 3 meses / 365) **no cambia**; solo cambia desde cuándo se cuenta.
- **FR-006**: La activación sigue serializada por usuario (E-596), sin guard de rol (D-260) y crea la fila del personal sin fila (D-261).
- **FR-007**: `POST /api/v1/mentor/activate-tracking` (inicio hoy) **no cambia**: lo llaman solo los APK ≤ 1.5.0.
- **FR-008** *(app)*: el paso «Elige tu Día 1» se reemplaza por una pantalla corta: título «Tu programa empieza mañana», la fecha, una línea con el plazo («Son tus 90 días.», «Son tus 3 meses.», «Es tu año.») y «Entendido». Sin selector de fechas en ningún lugar.
- **FR-009** *(app)*: la manda el servidor: la fecha que se muestra es la que ofrece `validStartDates[0]`, y es la que se envía al activar.
- **FR-010** *(app)*: todas las entradas pasan a la regla nueva con los mismos textos cortos: fila de Hoy/Plan «Haz tu programa de {plazo} · Empieza mañana ›» y «Todavía no empezaste tu programa · Empieza mañana ›»; botón «Empezar mañana»; Mi Radar «Primero empieza tu programa».

### Edge cases

- **Medianoche de Lima**: a las 23:59 de Lima (04:59 UTC del día siguiente) «mañana» es el día después del día de Lima, no el día siguiente al de UTC; a las 00:01 de Lima (05:01 UTC) «mañana» ya es el siguiente. Probado con el reloj entre 00:00 y 05:00 UTC (regla 02, §3).
- **Viernes → sábado, fin de mes y fin de año**: «mañana» es aritmética de calendario (`plusDays(1)`); 31 de octubre → 1 de noviembre; 31 de diciembre → 1 de enero.
- **Doble toque / dos teléfonos**: el bloqueo por usuario ya serializa; el segundo toque encuentra la fila activada con la misma fecha y devuelve 200.
- **Zona horaria distinta a Lima** (fila con otra `timezone`): «mañana» sale de SU zona, no de la del servidor.

## Assumptions (se le avisan al dueño)

1. «Al día siguiente» es el **día calendario siguiente** en la zona de la persona, no «24 horas después». Quien termina a las 23:58 empieza dos minutos después: es lo que ya pasaba con la primera opción de antes («mañana»).
2. `POST /mentor/activate-tracking` (inicio **hoy**) queda como está: solo lo llaman APK ≤ 1.5.0 y cambiarlo cambiaría la fecha de una cuenta ya en uso. Si el dueño quiere alinearlo, es un cambio aparte.
3. Con otra fecha distinta de mañana el servidor **rechaza (400)** en vez de ignorarla: lo más seguro para el APK anterior (nunca activa con una fecha que la persona no vio) y para la app nueva (se refresca).
4. Quien ya eligió +2 o +3 conserva su fecha; nadie se reprograma.
5. El plazo que se nombra sale de la formación de la persona (`plazoDelObjetivo`): «90 días», «3 meses», «1 año».
6. No hay aviso del teléfono nuevo («empieza mañana»): no existía uno para el Día 1 y no se pidió.

## Success Criteria

- **SC-001**: ningún texto de la app, flujo de Maestro ni prueba de Jest dice «Elegir mi Día 1», «Elige tu Día 1», «Primero elige tu Día 1» ni «Confirmar mi Día 1».
- **SC-002**: para cualquier hora UTC, `validStartDates` es exactamente `[hoy_en_su_zona + 1]` y el `POST` sin fecha activa con ese día.
- **SC-003**: un APK anterior activa sin error contra el servidor nuevo.
