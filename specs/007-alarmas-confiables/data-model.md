# Data Model: 007 Alarmas confiables

Sin cambios de esquema en PostgreSQL. Lo nuevo son valores en memoria, en eventos y en el almacenamiento local de la app.

## Backend

### AvisoHabito (dominio `habits`, existe)

| Campo | Nota |
|---|---|
| `tipo` | `INICIO` o `POR_VENCER` |
| `momento` | Instante de inicio o plazo de evidencia; se calcula de la hora del hábito, nunca del reloj del barrido |
| `faltan` | Duración hasta `momento` en el barrido |

### Clave de idempotencia (cambia)

`UUID.nameUUIDFromBytes(prefijo + registroId + ":" + momento.epochSecond)`.
- Mismo registro y mismo momento → misma clave, aunque corran varios barridos o instancias.
- Hora nueva → clave nueva → un aviso más (FR-013).
- Se apoya en el índice único existente `(usuario_id, tipo, origen_evento_id)`.

### AvisoHabitoDebidoEvent (`habits.api`, se amplía al final)

| Campo nuevo | Tipo | Nulo | Uso |
|---|---|---|---|
| `momentoAvisado` | `Instant` | sí (eventos viejos) | Hora del texto y vencimiento |
| `zonaHoraria` | `String` | sí | Zona del participante para `HH:mm` |

### AvisoDeHabito (nuevo, dominio `notifications`)

Valor sin Spring construido desde el evento.

| Elemento | Regla |
|---|---|
| `titulo()` | INICIO «Tu hábito está por empezar»; POR_VENCER «Se te vence un hábito» |
| `cuerpo()` | Con momento y zona: «{hábito}: te toca a las HH:mm. En juego: N puntos.» / «{hábito} vence a las HH:mm. En juego: N puntos.»; si no, la versión con minutos |
| `etiqueta()` | `habito-{habitoId}-inicio` / `habito-{habitoId}-por-vencer`; nula sin `habitoId` |
| `yaNoSirve(ahora)` | `!ahora.isBefore(momento)`; momento = `momentoAvisado` o `occurredAt + minutosQueFaltan` truncado al minuto |

### Filtro de apagados (regla nueva en `AvisoHabitoService`)

`apagados(fecha) = habitosApagadosEn(participante, fecha) ∪ habitosApagadosEnDiaSemana(participante, fecha.dayOfWeek)`. La fecha es la del participante en su zona. Ningún aviso para un registro cuyo hábito esté en ese conjunto.

## App

### Alarma programada (expo-notifications, existe)

| Elemento | Cambio |
|---|---|
| Tipo en Android | Despertador (`setAlarmClock`) si hay permiso exacto y el disparador no es de intervalo; si no, como hoy |
| `identifier` de la antelación más temprana de cada hábito | `habito-{habitoId}-inicio` (antes UUID); el resto sigue con UUID |

### Estado local por cuenta (AsyncStorage)

| Clave | Valor | Uso |
|---|---|---|
| `renaser.alarmas.detencionRevisada.<userId>` | epoch ms | Último instante revisado del historial de salidas |
| `renaser.alarmas.avisoDetencion.<userId>` | fecha `AAAA-MM-DD` | Última vez que se mostró el aviso (una por día) |

Se borran al cerrar sesión junto con lo demás de alarmas (`alarmasDeLaCuenta.ts`, prefijos).

### Guía por marca (`alarmas/data/guiasPorMarca.ts`)

| Marca (`Manufacturer`/`Brand`) | Línea |
|---|---|
| honor, huawei | Inicio de aplicaciones → Renaser → apaga «Gestionar automáticamente» y deja todo encendido. |
| xiaomi, redmi, poco | Inicio automático → activa Renaser. En Ahorro de batería elige «Sin restricciones». |
| samsung | Batería → Renaser → «Sin restricciones», y sácala de «Apps en suspensión». |
| oppo, realme, oneplus | Inicio automático → activa Renaser, y en Batería permite la actividad en segundo plano. |
| vivo, iqoo | Inicio automático / Segundo plano → permite Renaser. |
| otra | El texto actual («Batería → Sin restricciones…»). |

Máximo dos frases, tuteo.

## Transiciones del aviso de alarmas borradas

`sin revisar` → (abrir app con sesión) → `revisado`:
- Hubo detención forzada nueva y no se avisó hoy: se reprograman las alarmas, se muestra la hoja y se guarda la fecha.
- No hubo, o ya se avisó hoy: no se muestra nada.

En los dos casos se guarda el instante revisado.
