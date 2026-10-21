# Data Model: Mensaje del semáforo y descarga del podio

## Tablas (V110__mensajes_del_semaforo.sql, schema `renaser`)

### `mensajes_semaforo` — catálogo

| Columna | Tipo | Regla |
|---|---|---|
| `id` | `varchar(8)` PK | `v001`…`v035`, `a001`…, `r001`…, `s001`… (del JSON) |
| `grupo` | `varchar(20)` not null | CHECK en VERDE, AMARILLO, ROJO, INICIO, SIN_PROGRAMA, PAUSA, CUENTA_PAUSADA, GRADUADO |
| `texto` | `varchar(300)` not null | con variables `{…}` |
| `sticker` | `varchar(40)` not null | uno de los 15 nombres |
| `activo` | `boolean` not null default true | un mensaje se desactiva, no se borra |

Se siembra con los 115 mensajes de `data/mensajes-semaforo.json`. Un mensaje SIN_COLOR toma su `caso` como grupo.

### `mensajes_semaforo_elegidos` — historial, append-only

| Columna | Tipo | Regla |
|---|---|---|
| `participante_id` | `uuid` not null | FK al participante |
| `fecha` | `date` not null | fecha local de la persona |
| `canal` | `varchar(10)` not null default `'HOY'` | por dónde llegó: `HOY` (tarjeta del semáforo) o `CHAT` (tarjeta diaria del soporte). CHECK en (`HOY`, `CHAT`). *(Agregada 2026-10-19, D-281, dentro de V110.)* |
| `grupo` | `varchar(20)` not null | grupo al momento de elegir |
| `mensaje_id` | `varchar(8)` not null | FK `mensajes_semaforo(id)` |
| `elegido_en` | `timestamptz` not null | `clock.now()` |

- PK `(participante_id, fecha, canal, grupo)`. *(Corregido 2026-10-19, D-281: decía `(participante_id, fecha, grupo)`; Hoy
  y el chat eligen cada uno el suyo el mismo día y grupo, y tienen que poder guardarlo sin pisarse. Las filas sin canal
  son de Hoy por el default.)*
- ~~Índice `(participante_id, fecha desc)` para leer los últimos 14 días.~~ *(Corregido 2026-10-19: no se creó; la PK ya
  sirve esa lectura por persona y rango de fechas. V110.)*
- No se actualiza ni se borra. Si cambia el grupo en el día, entra otra fila; cada canal tiene la suya.
- La lectura de «vistos en 14 días» incluye **los dos canales**, y trae además lo que el OTRO canal mostró hoy (de
  cualquier grupo): es lo que hace que el mensaje del chat sea distinto del de Hoy ese día (D-281).

## Dominio (`points/domain/model/semaforo/mensaje`)

- **`GrupoDelMensaje`** (enum): los 8 grupos de la tabla.
- **`MensajeDelSemaforo`** (record): `id`, `grupo`, `texto`, `sticker`, `activo`; `variables()` se **deriva** del texto
  (constitución IV: no se guarda en una columna).
- **`Variable`** (enum): NOMBRE, DIA, FASE, HABITO_FLOJO, HABITO_FUERTE, FALTAN, con su clave entre llaves.
- **`DatosDelMensaje`** (record, todos opcionales): `nombre`, `dia`, `fase`, `habitoFlojo`, `habitoFuerte`, `faltan`.
  - Método `tiene(Variable)`.
- **`EleccionDelMensaje`** (servicio de dominio puro, con el `CanalDelMensaje` por el que va a salir):
  - Entradas: `grupo`, candidatos, `DatosDelMensaje`, ids vistos (14 días, de los dos canales), id de ayer, ids que el
    otro canal mostró hoy, azar.
  - Regla:
    0. *(D-281)* Solo cuentan los que el canal admite (en el chat, ninguno con `{faltan}`) y se quitan, sin excepción,
       los que el otro canal mostró hoy.
    1. Se filtran los candidatos activos del grupo cuyas variables tiene la persona.
    2. Se quitan los vistos en 14 días; si no queda ninguno, se quita solo el de ayer; si sigue vacío, se usan todos.
    3. Se elige uno al azar.
    4. Si no hay ningún candidato con datos, se usa un mensaje del grupo sin variables. Si tampoco hay, no hay mensaje.
- **`RedaccionDelMensaje`**: reemplaza cada `{clave}` por su dato. Invariante: el resultado no contiene `{` ni `}`
  (si falta un dato, el mensaje no era candidato).
- **`GrupoDelDia`**: deriva el grupo del color y del estado (research R3). `delColor(color)` da el grupo de un día que ya tiene
  color (VERDE, AMARILLO o ROJO), que es lo que conoce la tarjeta del chat.
- **`CanalDelMensaje`** (enum, D-281): `HOY` (admite todas las variables) y `CHAT` (admite todas menos `{faltan}`).
  `admite(mensaje)` dice si un mensaje puede salir por ese canal.
- **`ClaveDeLaEleccion`** (record, D-281): persona, fecha local, grupo y canal; la clave de una fila de elegidos.
- **`HabitosQueFaltan`**: deriva `faltan` (research R5). Devuelve vacío si no aplica.

## Puertos de salida

- `CatalogoDeMensajesPort.activosDe(grupo)`.
- `MensajesElegidosPort` *(D-281: ambas operaciones reciben una `ClaveDeLaEleccion` con el canal)*:
  - `deLaPersona(clave)`: lo elegido hoy para ese canal y grupo y, solo si no hay, lo visto en los 14 días anteriores
    (cualquier canal y grupo) y lo que el otro canal mostró hoy, en una consulta (*corregido 2026-10-19, SC-004: antes
    `delDia` y `entre`, dos consultas*);
  - `registrar(clave, mensajeId, ahora)`, idempotente con ON CONFLICT DO NOTHING.
- `AzarPort.indice(n)`.
- Lecturas de otros módulos, solo por `api/`:
  - `points/api/CumplimientoPorHabitoFinder.enRango(participante, desde, hasta)` *(corregido 2026-10-19, E-630: antes
    `habits/api`; lo implementa `habits`)*, que devuelve una lista de `(nombre, programados, cumplidos)`;
  - el finder de `users` para el nombre y el programa completado;
  - ~~`SemaforoDelDiaFinder` para P y C de hoy.~~ *(corregido 2026-10-19, SC-004: P y C salen de lo que `/home` ya
    leyó, `ConsultaDelMensaje.deHoy`; no se consulta nada.)*
- `CatalogoDeMensajesPort` se guarda en memoria 10 minutos (`recargar()` lo descarta) y el nombre y los hábitos
  destacados, por persona y día local hasta la medianoche de su zona (`DatosEstablesDelDia`).

## API para el chat (D-281)

- `points.api.MensajeDelChatFinder.delDia(aprendiz, fecha, color)` → `Optional<String>`: el texto ya escrito del
  mensaje del chat. La implementa `MensajeDelChatService` con las mismas piezas que Hoy (catálogo, elegidos, azar,
  `RecolectorDeDatosDelMensaje`); lee la participación (`users.api.ParticipacionProgramaFinder`) y la ventana vigente
  (`ConsultarMiSemaforoUseCase.resumenParaHoy`). Ver `contracts/chat-tarjeta-diaria-mensaje.md`.
- `ConsultaDelMensaje.deHoy` es ahora `Optional<CumplimientoDelDia>`: el chat no conoce lo de hoy y no dice `{faltan}`.

## Permiso

- `Permission.EXPORT_RANKING`: «Descargar la imagen del podio del ranking para compartirla».
- Concedido a MENTOR_LEAD, ADMIN, ALCHEMIST e IT_SUPPORT.
- Se expone como `capabilities.canExportRanking` (contrato).

## App

- **`MensajeDelSemaforo`** (tipo): `{ id: string; texto: string; sticker: string | null }`. Va dentro de `SemaforoDeHoy`
  como `mensaje: MensajeDelSemaforo | null`.
- **`PodioParaCompartir`**: `titulo` (la tabla), `fecha` (texto) y `puestos[]` (`posicion`, `nombre` recortado,
  `puntaje` con unidad), con los primeros 10.
