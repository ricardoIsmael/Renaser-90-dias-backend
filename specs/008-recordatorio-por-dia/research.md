# Research: Recordatorio por día (spec 008)

Revisado en master el 2026-10-20: backend `c9ae3620` y app `f273242`.

## R1. Dónde vive el recordatorio de un día de la semana

**Decision**: dos columnas nuevas en `horario_semanal_habito` (V111): `recordatorio_activo boolean NULL` y `antelaciones_recordatorio smallint[] NULL`. `NULL` quiere decir que ese día hereda el recordatorio general. La regla `CHECK` de V40 se relaja: una fila encendida necesita hora propia **o** recordatorio propio.

**Rationale**: la tabla ya es la relación participante × hábito × día de la semana (V39). El dominio `HorarioSemanal` ya envuelve una `PreferenciaHorario`, que tiene `recordatorioActivo`, `minutosRecordatorio` y `antelacionesRecordatorio`: solo falta persistirlos. Un día con aviso propio y sin hora propia es una fila con `hora_disparo NULL`, así sigue la hora general si esta cambia.

**Alternatives considered**:
- Guardar la hora resuelta junto con el aviso: congelaría la hora del día y un cambio general ya no le llegaría. Rechazado.
- Usar filas por fecha (`horarios_habito_por_fecha`): no se repiten cada semana. Rechazado.
- Una tabla nueva solo de avisos: duplicaría la clave de V39 y abriría una segunda respuesta a «¿cómo va este día?». Rechazado.

## R2. Endpoint para el aviso de un día

**Decision**: `PUT /api/v1/habit-preferences/{habitId}/weekdays/{weekday}/reminder` con `{reminderEnabled, reminderMinutesList}`. No consume cupo semanal. `PUT …/weekdays/{weekday}` (la hora) **no cambia de contrato** y no toca el aviso. `DELETE …/weekdays/{weekday}` sigue borrando la fila entera, hora y aviso (FR-006). `GET …/weekdays` suma, por día, `reminderEnabled`, `reminderMinutesList` y `customReminder`. Son campos aditivos.

**Rationale**:
- **FR-013:** un APK anterior que manda la hora sin aviso no puede borrar el aviso, porque son dos escrituras separadas.
- **Respuesta del dueño, «solo lo que toqué»:** si solo se cambia el aviso, se llama solo al endpoint del aviso y la hora de cada día no se toca.
- **Cupo:** el cupo (`CuotaEdicionHorario`) existe para frenar el «reacomodo de horario». Cambiar el aviso no reacomoda nada, igual que apagar un día (`apagar` no cobra cupo).

**Alternatives considered**: sumar campos al PUT de la hora. Un APK viejo los mandaría en `null`; no se distingue «no lo mandé» de «lo quiero borrar» sin romper el contrato. Rechazado.

## R3. Precedencia del recordatorio en el barrido del servidor

**Decision**: en `PreferenciaHorarioPersistenceAdapter.porParticipanteHabitosYFecha`, la capa del día de la semana aplica **por campo**:
- Hora: la propia si hay; si no, la de abajo. Es lo de hoy.
- Recordatorio: el propio si `recordatorio_activo` no es `NULL`; si no, el de abajo.
- La fila de solo aviso (hora `NULL`, activo) ya no se salta: aplica su aviso y deja la hora de abajo.
- Las filas apagadas (`activo = false`) siguen igual.

La fecha puntual sigue por encima. El barrido (`AvisoHabitoService.calculadoraPara`) ya usa lo resuelto, así que no cambia.

**Rationale**: es el mismo patrón «por campo» del comentario de V39. FR-011.

## R4. Alarmas del teléfono por día

**Decision**: `programar` (app) decide el esquema por hábito:
- **Sin diferencias entre días** (ningún día con hora o aviso propios y ningún día apagado): queda **como hoy**, con un `DAILY` por antelación y el identificador `habito-{id}-inicio` en la más temprana. Así no se toca lo que ya funciona ni se pierde la etiqueta compartida con el push (007).
- **Con diferencias**: un `WEEKLY` por día encendido y por antelación, a `hora del día − antelación`. Si cruza la medianoche, va al día anterior. Identificadores `habito-{id}-d{iso}-{n}`. Antes se cancelan todos los del hábito (`recordatoriosPorHabito`).

`ajustarRecordatoriosAlServidor` lee los días ya resueltos de `GET /habit-preferences` (campo aditivo `weekdays`, R5), sin un pedido por hábito.

**Rationale**:
- **FR-008:** hoy un `DAILY` ignora la hora de cada día.
- **Ya soportado:** el `WEEKLY` está en expo-notifications. El parche de la 007 lo programa tipo despertador, porque no es `TimeIntervalTrigger`, así que **no hace falta APK nuevo**. `rearmarAlarmas.disparadorDeEntrada` ya rearma los `weekly`.

**Alternatives considered**:
- Siete `DATE` rodantes: obligan a reprogramar cada día y se pierden si la app no se abre. Rechazado.
- Siempre `WEEKLY`: perdería el identificador compartido en el caso común y movería todas las alarmas de todos los usuarios al actualizar. Rechazado.

## R5. De dónde lee la app los días de cada hábito

**Decision**: `GET /api/v1/habit-preferences` suma por hábito `weekdays` con 7 entradas resueltas: `weekday`, `triggerTime`, `active`, `reminderEnabled`, `reminderMinutesList`, `customTime`, `customReminder`. Se arman con **una** consulta por participante a `horario_semanal_habito`. El campo es aditivo y un APK anterior lo ignora.

**Rationale**: la puesta al día corre al abrir la app. Un `GET …/weekdays` por hábito serían ~18 pedidos.

## R6. Cruce con el push del servidor

**Decision**:
- **En el esquema por día:** la alarma más temprana de cada día lleva el identificador `habito-{id}-d{iso}-0` y ya no comparte la etiqueta `habito-{id}-inicio` con el push. El cruce lo evita la confirmación por teléfono de la 007: el servidor no le manda el push de inicio a un token que confirmó sus alarmas en las últimas 26 h (`EntregaPush.RESPALDO_DE_ALARMA_LOCAL`).
- **En el esquema sin diferencias:** se mantiene también la etiqueta.

**Rationale**: Android usa el identificador de expo como etiqueta. Siete alarmas semanales no pueden tener el mismo identificador. La confirmación ya es la protección principal de la 007; la etiqueta era la segunda capa (FR-012).

**Riesgo aceptado**: un teléfono que no confirmó (permiso de avisos denegado al confirmar, o sin red al abrir) puede ver dos avisos en el esquema por día. Es lo mismo que pasaba antes de la 007, y solo en hábitos con días distintos.

## R7. Agrupar avisos que coinciden (E-410)

**Decision**: `agruparAvisosQueCoinciden` hoy agrupa `DAILY` de la misma hora. En el esquema por día agrupa también `WEEKLY` del mismo día y la misma hora. Si no se puede agrupar con seguridad, se deja sin agrupar; suenan los dos, que es lo mismo que antes de E-410.

**Rationale**: mantener lo que ya hay sin bloquear la 008. La tarea lo verifica con pruebas.

## R8. Pantalla del editor

**Decision**: en `PlanificarDimensionModal`:
- Con días marcados, se muestra la sección Recordatorio y se quita la línea E-408.
- **Estado inicial:** el aviso común de los marcados. Si difieren, no se marca ninguno y aparece «Estos días tienen avisos distintos».
- **Guardado:** si no se movió la rueda, solo se guarda el aviso, con el botón «Guardar aviso · S, D». Si se movió, se guardan hora y aviso.
- **Chips:** `FilaDeDiasDelPlan` muestra debajo de la hora un `Icon` de campana de 16 px y «30′», «a la hora», o la campana tachada si es sin aviso. Solo cuando el día difiere del general.
- **Sin días marcados:** el recordatorio es el general, como hoy.

`FilaDeDiasDelPlan` es compartido con Plan. Solo se le suma una prop opcional; Plan no la pasa y no cambia.

**Rationale**: respuestas del dueño del 2026-10-20 y FR-001 a FR-006.

## R9. Iconos

**Decision**: se usan `bell` y `bellOff` de `Icon` si existen. Si no, se dibujan con la forma de Lucide en caja de 24, como pide `AGENTS.md`. Nada de emojis.

## R10. Sin APK

**Decision**: la app solo cambia JavaScript. No se toca `package.json`, `app.json` ni `modules/`. Se prueba con el build debug instalado y Metro del worktree.
