# Research: Tipos de alarma por día (spec 009)

Revisado en master el 2026-10-20 y sobre la rama 008.

## R1. Sonido y vibración: canales de Android

**Decision**: la combinación elige el **canal** por el que sale la alarma. Sonido y vibración son propiedades del canal, que no se pueden cambiar después de crearlo. Se suma el sufijo `-sinvib` al id del canal (`enableVibrate: false`) cuando «Vibración» está apagada:

| Sonido | Vibración | Voz | Canal |
|---|---|---|---|
| sí | sí | no | el del sonido de Yo → Alarmas (como hoy) |
| sí | no | no | ese mismo + `-sinvib` |
| no | sí | — | `recordatorios-habitos-vibrar` (ya existe, «Solo vibrar») |
| sí | sí/no | sí | el de voz del hábito (`…-voz-<clave>`, o la genérica), con o sin `-sinvib` |

**Rationale**: es el mecanismo que ya usa la app (E-9, E-10, `sonidoDeAlarma.ts`) y funciona sin código nativo, así que **no hace falta APK**. `rearmarAlarmas.archivoDelCanal` y `ARCHIVO_POR_CANAL` tienen que conocer los ids con `-sinvib`.

**Alternatives considered**: vibrar desde la notificación. En Android 8+ el canal manda, así que no sirve.

## R2. Repetir si no la atiendo

**Decision**: alarmas **de fecha** (`DATE`) a +5 y +10 min de la hora del hábito, para los **próximos 7 días** en que el día tiene «Repetir». Identificadores `habito-{id}-rep-{AAAAMMDD}-{5|10}`. Se cancelan las de **hoy** cuando:

- la persona toca cualquier alarma del hábito (listener de respuesta de notificaciones, el que ya abre la ruta `/habitos/{id}`), o
- completa el hábito (evento `habitoCumplido`).

La ventana de 7 días se renueva en cada puesta al día, al abrir la app (`ponerAlDiaLasAlarmas`).

**Rationale**: una alarma `DAILY` o `WEEKLY` no se puede «saltar solo hoy». Cancelarla borra las de los días siguientes, y reprogramarla antes de su hora la vuelve a poner hoy. Con fechas, cancelar la de hoy es exacto. Todas siguen siendo tipo despertador (007).

**Limitación aceptada**: si la persona no abre la app en 7 días, las repeticiones se acaban; la alarma principal sigue. Se anota en la spec y en `AGENTS.md`.

**Alternatives considered**: lanzar las repeticiones cuando suena la principal. Necesita código nativo en segundo plano, es decir, APK. Rechazado.

## R3. Voz

**Decision**: «Voz» encendida usa el canal de voz del hábito (Dora), que ya existe por hábito del catálogo. Si el hábito es propio o fue renombrado, usa la frase general. La regla FR-005 apaga la voz si se apaga el sonido.

## R4. Dónde se guarda la combinación

**Decision**: V112 suma cuatro columnas `boolean NULL` (`alarma_sonido`, `alarma_vibracion`, `alarma_voz`, `alarma_repetir`) a **`preferencias_horario`** (la general del hábito) y a **`horario_semanal_habito`** (la propia del día). `NULL` = hereda:

- En el día, hereda la general.
- En la general, hereda lo de hoy: sonido sí, vibración sí, voz según el sonido de Yo → Alarmas, repetir no.

**CHECK**: `NOT (alarma_sonido = false AND alarma_vibracion = false)`.

La fila de la semana que solo trae la combinación se suma a la regla de la 008: encendida ⇒ hora, aviso **o** combinación propia.

**Rationale**: las mismas capas y la misma resolución por campo que la 008. Así vale en todos los teléfonos (FR-008).

## R5. Endpoints

**Decision**:
- `PUT /api/v1/habit-preferences/{habitId}/alarm` para la combinación general.
- `PUT …/weekdays/{wd}/alarm` para la del día.

Ninguno cobra cupo (como el aviso, D-282). `GET …/weekdays` y `GET /habit-preferences` (`weekdays`) suman `alarm: {sound, vibration, voice, repeat}` resuelta y `customAlarm`. Todo aditivo.

## R6. El interruptor

**Decision**: componente nuevo `InterruptorGrande` en `app:src/components/`:
- Riel de 72 × 40 px, pulgar blanco de 34 px con sombra suave, dorado del tema encendido y gris claro apagado.
- Estado «Varía»: riel gris con un guion en el pulgar.
- La fila entera es `Presionable` de 56 px o más, con `tacto.seleccion()`.
- Movimiento del pulgar con Reanimated, 180 ms y la curva de la app. Con «reducir movimiento», cambio al instante.
- Rol `switch`, `accessibilityState.checked` y un nombre que dice la fila.

No reemplaza a `Interruptor` (51 × 31), que sigue en el resto de la app.

**Rationale**: es la referencia del dueño, con el dorado de la marca, para mayores de 40 (`emil-design-eng`, `animate-expo`, `apple-design`).

## R7. UX/IA

**Decision**: orden de arriba abajo: **Hora → Días → Recordatorio → Cómo suena → Guardar**. Cada bloque lleva su `MicroLabel`.

En «Cómo suena», cada fila tiene:
- un rótulo de 17 px: «Sonido», «Vibración», «Voz que dice el hábito», «Repetir si no la atiendo»;
- una explicación de 15 px: «Suena el tono que elegiste en Yo → Alarmas», «El teléfono vibra», «Una voz dice el nombre del hábito», «Vuelve a sonar a los 5 y a los 10 minutos».

Sin íconos decorativos de más; los mensajes de regla van en línea, debajo de la fila que los causa. La sección se atenúa con «Este día no tiene aviso» cuando el aviso es «Sin aviso».

## R8. Sin APK

Solo JavaScript, sin cambios en `app.json` (no hay sonidos nuevos) ni en módulos nativos. Se prueba con el build debug instalado y Metro.
