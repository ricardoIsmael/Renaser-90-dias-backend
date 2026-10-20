# Contrato: aviso de hábito (evento interno y push)

## Evento `habits.api.AvisoHabitoDebidoEvent`

Se agregan dos campos **al final** del record; los existentes no cambian de orden ni de tipo.

```text
registroId, participanteId, tituloHabito, tipoAviso ("INICIO"|"POR_VENCER"),
minutosQueFaltan, puntosEnJuego, claveEvento, recordatorioActivo, minutosRecordatorio,
occurredAt, habitoId, categoriaHabito,
momentoAvisado: Instant | null      // NUEVO
zonaHoraria:    String  | null      // NUEVO, p. ej. "America/Lima"
```

- `claveEvento` = clave de idempotencia con el momento (data-model).
- Un evento serializado antes del cambio (outbox) llega con los dos campos nulos y se procesa con el texto de minutos.

## Push Expo (`ExpoPushTransporte`)

Para `RECORDATORIO_HABITO`:

```json
{
  "to": "<token>",
  "title": "Tu hábito está por empezar",
  "body": "Agua tibia con limón: te toca a las 06:00. En juego: 10 puntos.",
  "sound": "default",
  "priority": "high",
  "tag": "habito-66507383-7219-43ab-aa42-2fbc76152b82-inicio",
  "threadId": "habito-66507383-7219-43ab-aa42-2fbc76152b82-inicio",
  "data": { "route": "/habitos/66507383-…?dimension=CUERPO" }
}
```

- `tag` y `threadId` solo se mandan si hay etiqueta (ya es así para el chat, D-221).
- `POR_VENCER`: `title` «Se te vence un hábito», `body` «{hábito} vence a las HH:mm. En juego: N puntos.», `tag` `habito-{habitoId}-por-vencer`.
- Entrega por token sin cambios: el `INICIO` con recordatorio no va a teléfonos que confirmaron alarmas en las últimas 26 h; el navegador siempre lo recibe.
- No se envía si `!ahora.isBefore(momento)` (vencido).

## Web push (`WebPushAdapter`)

Mismo `title`, `body` y `tag`; `data.url` = ruta.

## Alarma local de la app (Android)

- La antelación más temprana de cada hábito se programa con `identifier: "habito-{habitoId}-inicio"`.
- `expo-notifications` la muestra con ese `tag`; un push con el mismo `data.tag` la reemplaza en la bandeja, y viceversa.
- Las otras antelaciones conservan identificadores UUID.
