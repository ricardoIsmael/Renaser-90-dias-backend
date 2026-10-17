# Feature Specification: Hábitos vigentes al cambiar de día

**Feature Branch**: `004-habitos-cambio-de-dia` (app), `002-habitos-cambio-de-dia` (backend)
**Created**: 2026-10-17
**Status**: Implementado y validado automáticamente; pendiente de publicación y comprobación en dispositivo
**Input**: «continuamos con la spec para implementar y arreglar el bug», sobre el incidente del 17 de octubre.

## Clarifications

### Session 2026-10-17

- El dueño confirmó previamente: el día cierra a las 00:00 de la zona del participante en cualquier país.
- Esta instrucción autoriza corregir Training y los caminos compartidos de evidencia usados por avisos y SER.
- Un intento antiguo conserva su destino original; nunca se transforma en registro del día nuevo.
- Las sesiones especiales que legítimamente atraviesan medianoche conservan sus reglas.
- Las reglas y la aprobación ya están en la conversación: no hay decisiones de negocio pendientes.

## User Scenarios & Testing

### User Story 1 - Ver el día vigente (Priority: P1)

Como participante quiero que Training cambie de día aunque deje la app abierta.
**Why this priority**: evita ofrecer como pendientes los registros de ayer.
**Independent Test**: mantener Training abierto durante el cierre y observar la sustitución por los datos actuales.

**Acceptance Scenarios**:

1. **Given** hábitos del día vigente, **When** llega su cierre, **Then** dejan de ofrecerse como completables y se consultan los nuevos.
2. **Given** app en segundo plano o pestaña suspendida, **When** vuelve a estar activa o se vuelve a Training, **Then** se revalida la lista antes de aceptar acciones antiguas.
3. **Given** zona del teléfono distinta de la del programa, **When** cambia el día del programa, **Then** se aplica ese cierre.

### User Story 2 - No registrar el día equivocado (Priority: P1)

Como participante quiero saber si una acción pendiente pertenece a un día cerrado.
**Why this priority**: protege lo registrado y evita reintentos inútiles.
**Independent Test**: abrir un formulario o cámara antes del cierre e intentar enviarlo después.

**Acceptance Scenarios**:

1. **Given** un formulario, foto o acceso antiguo desde aviso o SER, **When** se envía después del cierre, **Then** se informa el cambio y no se completa otro registro.
2. **Given** que el cierre ocurre durante el envío, **When** el servidor lo rechaza, **Then** se actualiza la lista y no se anuncia logro ni puntos.
3. **Given** un error de otro tipo, **When** se reintenta, **Then** conserva su tratamiento actual sin interpretarlo como cambio de día.

### User Story 3 - Recuperar sin conexión y con versiones anteriores (Priority: P2)

Como participante quiero un mensaje claro si no se puede verificar el día.
**Independent Test**: cortar la red durante el cierre; restaurarla y reintentar la actualización.

**Acceptance Scenarios**:

1. **Given** datos cuyo día terminó y falta de conexión, **When** se intenta registrar, **Then** se pide actualizar y no se opera sobre datos antiguos.
2. **Given** un servidor anterior, **When** se abre Training, **Then** se conserva acceso compatible y cada envío comprueba que su registro sigue vigente.

### Edge Cases

- Lista vacía, respuesta que atraviesa medianoche y respuestas fuera de orden.
- Dos países con medianoches distintas; madrugada UTC que todavía pertenece a ayer en Lima.
- Días de 23 y 25 horas; reloj manualmente alterado y web sin encabezado Date.
- Doble toque, error durante subida, evidencia ya subida y foto recuperada tras reinicio de Android.
- Registro completado en otro dispositivo; un 409 ajeno al cierre diario.

## Requirements

### Functional Requirements

- **FR-001**: usar la fecha y el cierre del participante informados por el servidor, incluso sin hábitos.
- **FR-002**: invalidar datos vencidos al cierre y releer; revalidar al recuperar actividad/foco.
- **FR-003**: verificar vigencia del ID antes de cualquier cierre o evidencia genérica; no reasignar intentos.
- **FR-004**: recuperar la lista ante rechazo por día cerrado y avisar sin éxito ni puntos inventados.
- **FR-005**: bloquear envíos que no puedan verificarse por falta de red y permitir reintentar.
- **FR-006**: preservar sesiones especiales, las reglas del programa y los diseños de los cinco tabs.
- **FR-007**: conservar compatibilidad entre app y backend anteriores.
- **FR-008**: descartar respuestas vencidas/fuera de orden y limpiar temporizadores y suscripciones al salir.

### Key Entities

- **Día de hábitos**: fecha, zona, instante de cierre, instante del servidor y registros de esa lectura.
- **Intención de registro**: ID original y evidencia/respuesta pendiente; nunca cambia de día automáticamente.

## Success Criteria

### Measurable Outcomes

- **SC-001**: con el contrato nuevo y la app activa, los registros antiguos dejan de ser completables al cierre y la actualización comienza dentro de un segundo (salvo bloqueo del sistema).
- **SC-002**: ningún escenario de cámara, formulario o acceso antiguo completa ni atribuye puntos a un ID distinto.
- **SC-003**: al recuperar actividad se revalida inmediatamente; una respuesta antigua no restaura datos vencidos.
- **SC-004**: la matriz de dos países, cambio estacional, falta de conexión y rechazo durante envío pasa sin modificar registros históricos.

## Assumptions

- La disponibilidad de red determina cuándo llegan los hábitos nuevos; mientras tanto se explica la necesidad de actualizar.
- El servidor anterior no permite conocer el instante de cierre: se relee periódicamente (máximo 60 s con app activa) y antes de mutar, sin inferir una zona.
- No incluye despliegue en producción, compilación/distribución del APK ni investigación del timeout de Vercel.
