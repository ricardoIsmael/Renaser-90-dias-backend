# Plan: horario por día, interruptor por día y momento del día

Verificado contra el código real del backend y del móvil el 2026-09-07.

> Los cinco hallazgos del §0 se comprobaron dos veces: los produjo un agente leyendo el repo y
> después se releyeron a mano los archivos citados. Los números de línea son de esa lectura.

## 0. Estado real, contra el código (no contra los nombres de endpoint)

### 0.1 Nombres de tabla reales

El pedido menciona `renaser.preferencias_habito`; **esa tabla no existe**. Los reales, en `V1__baseline_renaser.sql`:

| Concepto | Tabla real | PK |
|---|---|---|
| Catálogo compartido | `renaser.habitos` | `id` |
| Días/horas del catálogo | `renaser.horarios_habito` | `id` |
| Horario personal general | `renaser.preferencias_horario` | `(participante_id, habito_id)` |
| Cambio general diferido | `renaser.cambios_horario_pendientes` | `(participante_id, habito_id)` |
| Libro de cuota | `renaser.historial_cambios_horario` | `id` |
| Plan del aprendiz + pausa | `renaser.desbloqueos_habito` | `(participante_id, habito_id)` |
| Track diario | `renaser.registros_habito` | UNIQUE `(participante_id, habito_id, fecha_ejecucion)` |
| **Excepción por fecha** | `renaser.horarios_habito_por_fecha` | **V37, ya existe** |

### 0.2 Lo que YA está hecho y el pedido no contempla

**El horario por fecha exacta ya está implementado** (V37 / D-121 / E-156):

- `PATCH /api/v1/habit-preferences/{habitId}` ya acepta `date` — `UpdateHabitPreferenceRequest:18`
  tiene `LocalDate date`, y `PreferenciaHorarioService.aplicarEdicion` bifurca: con fecha llama a
  `savePreferenciaPort.saveParaFecha(...)`, sin fecha conserva el cambio general diferido.
- `GET /api/v1/habit-preferences?date=yyyy-MM-dd` ya resuelve el horario de esa fecha
  (`HabitPreferenceController:48`).
- La precedencia vive en `PreferenciaHorarioPersistenceAdapter.porParticipanteHabitosYFecha`:
  preferencia general → pendiente ya vigente → excepción de la fecha (gana).
- Los cuatro lectores diarios ya pasan por ahí con la fecha del registro: `RegistroService.resolverVentana`,
  `TracksDelDiaProyeccionService.consultar`, `AvisoHabitoService`, `ConsultaPreferenciasHorarioService`.

**Lo que falta del punto 1**: es por FECHA, no por DÍA DE SEMANA. Editar "el jueves" hoy solo arregla
el jueves 11; el jueves 18 vuelve al horario general. Y `HorarioPorFecha.requirePlanificable` rechaza
`fecha <= hoy`, así que hoy nunca se puede corregir.

**El móvil todavía no manda `date`.** `habitsApi.cambiarHorario` envía
`{triggerTime, limitTime, reminderEnabled: false, reminderMinutesBefore: null}` y nada más;
`obtenerPreferencias` llama a `/api/v1/habit-preferences` sin query. El backend ya sabe hacerlo y la
app en producción todavía no lo usa.

### 0.3 El bug de la pausa es peor de lo reportado

`DesbloqueoHabito.estaPausadoEl` (línea 89) **ignora `pausadoEn` por completo**:

```java
public boolean estaPausadoEl(LocalDate hoyEnSuZona) {
    if (pausadoEn == null) return false;
    return pausadoHasta == null || !hoyEnSuZona.isAfter(pausadoHasta);
}
```

No es "un rango que arranca al tocar el botón": es "todo lo que sea `<= pausado_hasta`". Pausar hasta
el jueves marca como pausados el lunes, el martes, el miércoles **y todas las fechas anteriores del
programa**. `pausado_en` solo se usa como `!= null` y como dato de auditoría.

### 0.4 La línea que hay que mirar sí o sí (el generador)

`RegistroService.generarInterno`, línea 196:

```java
var preferencias = horaDeCorte == null ? Map.<HabitoId, PreferenciaHorario>of()
        : loadPreferenciaPort.porParticipanteHabitosYFecha(participanteId, ..., fecha)...
```

**El barrido nocturno (`horaDeCorte == null`) no lee ninguna preferencia.** Hoy es inocuo porque
`sigueAlcanzable` corta en seco con corte nulo. Pero cualquier esquema nuevo "por día" queda
silenciosamente ignorado por `GenerarTracksDelDiaScheduler` (05:02 UTC), que es la vía normal por la
que se crean los tracks de todo el padrón.

Y lo decisivo: **`aplicaHoy` sale solo del catálogo compartido** (`loadHorarioPort.porHabito(...)`
filtrado por `aplicaEnDia`). `activeWeekdays` sale del mismo lado: `TipoDia.diasDeLaSemana()` sobre
`horarios_habito.tipo_dia`, que escribe el panel admin. No hay ningún lugar donde un aprendiz pueda
apagar un día suelto.

### 0.5 El momento del día

- El `aMomento` viejo (`habitsMappers.ts:45`) es `<12 mañana / <18 tarde / else noche`, con la
  madrugada cayendo en mañana.
- **El móvil ya rediseñó el modelo del lado del cliente**: `utils/momentosDelDia.ts` (tres cortes en
  un círculo de 24 h, `RANGOS_POR_DEFECTO = 05:00/12:00/18:00`, `MINIMO_POR_BLOQUE = 60`,
  `MOMENTO_ESPERADO = { WAKE_UP: 'mañana', SLEEP: 'noche' }`) y `storage/rangosDelDia.ts`, que guarda
  los cortes en AsyncStorage por usuario y **dice en su propio javadoc** que el backend no tiene dónde
  ponerlos y que queda planteado moverlos al servidor.
- `PlanScreen.applyMomentChange` solo hace `setHabits(...)`: no persiste nada.
- `WAKE_UP` y `SLEEP` son `clave_sistema` reales (`V26__habitos_despertar_dormir_clave_sistema.sql`).

**Consecuencia de diseño:** el modelo ya está escrito y validado en el cliente. El backend no tiene
que inventarlo: tiene que darle un lugar donde guardarlo y derivar el bloque a partir de él.

### 0.6 VEREDICTO: cero tablas nuevas — 6 columnas sobre 3 tablas que ya existen

> **Esto reemplaza a V39 y V40 de §2.** Se conservan abajo como registro de lo que se descartó y por
> qué, no como el camino a seguir. V38 sigue en pie: es una columna, no una tabla.

| Cambio | Dónde va | Forma normal | Consultas nuevas |
|---|---|---|---|
| Apagar/prender un día, y hora de ese día | `horarios_habito_por_fecha` + `activo`, y `hora_disparo` pasa a nullable | 3FN: `activo` depende de la PK completa (participante, hábito, fecha) | **0** — el adapter ya lee esa tabla |
| Pausa que no arrastre días anteriores | `desbloqueos_habito` + `pausado_desde` | 3FN: depende de (participante, hábito) | **0** — el generador ya lee esa tabla |
| ~~Mañana / tarde / noche~~ | **DESCARTADO al implementar — ver 0.7** | — | — |

### 0.7 Los cortes del día se quedan en el teléfono (decidido al implementar, 2026-09-07)

Se escribió la migración que agregaba `inicio_manana` / `inicio_tarde` / `inicio_noche` a
`participantes_programa` y **se descartó antes de commitear**, por algo que no se ve leyendo el
esquema y sí leyendo el código:

**`habits` no lee `participantes_programa`.** Lee `users.api.ParticipacionPrograma`, un record
PÚBLICO del módulo `users` que —dice su propio javadoc— es el contrato que consumen `points`,
`phasecontracts`, `habits`, `rocks`, `calendar` y `community`, y que se construye en 10 lugares
(3 de producción y 7 de prueba). Agregarle tres campos para que los use un solo módulo significa
ensanchar un contrato de seis consumidores para servir a uno, y tocar sus diez sitios de
construcción.

O sea: la columna sale barata, pero **el camino de lectura no**. Y una columna que nadie lee es
esquema muerto, que es peor que no tenerla.

Las tres opciones reales eran: ensanchar ese contrato compartido, crear una tabla propia de
`habits`, o dejar los cortes donde ya funcionan. La tercera es la más chica y no rompe nada: el
móvil los guarda por usuario en AsyncStorage (`features/habits/storage/rangosDelDia.ts`) y el
bloque se deriva de la hora en `momentosDelDia.ts`, que es la corrección que el dueño pidió —
dormir a las 00:30 ya cae en NOCHE. Lo único que se pierde es que los cortes no viajan entre
dispositivos.

**Si más adelante tienen que viajar**, el camino es agregarlos a `ParticipacionPrograma` junto a
`zona()`, que es su vecino natural, y actualizar los diez sitios de construcción de una vez. Es
trabajo del módulo `users`, no de `habits`, y conviene hacerlo cuando haya un segundo motivo para
tocar ese contrato.

**Por qué los cortes del día van en `participantes_programa` y no en una tabla propia.** Esa tabla ya
tiene `timezone`, que es exactamente el mismo tipo de dato: *cómo funciona el día de esta persona*.
Los tres cortes dependen solo de `usuario_id`, que es la PK completa — ponerlos en cualquier tabla de
clave más ancha sería una dependencia parcial y rompería 2FN. Una tabla 1:1 aparte estaría igual de
normalizada, pero **no agrega ninguna garantía y sí agrega un JOIN**.

**El argumento de rendimiento es el que decide.** `habits` ya carga la fila del participante por el
puerto `ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits(diaPrograma, timezone, rol,
suspendido)`, tanto en el camino a demanda como en el barrido nocturno sobre **todo el padrón**.
Sumar tres campos a ese record es gratis. Una tabla nueva sería una consulta más por participante en
un cron que recorre a todos.

**Lo mismo con el día apagado**: `PreferenciaHorarioPersistenceAdapter.porParticipanteHabitosYFecha`
ya trae en lote las filas de `horarios_habito_por_fecha` para la fecha. Una columna viaja en esa misma
consulta; una tabla nueva sería otra consulta en lote, más su entidad, su PK, su repositorio y su
mapper — todo para responder una pregunta que la fila que ya se está leyendo puede responder sola.

**En Postgres las tres migraciones son instantáneas**: `ADD COLUMN` con `DEFAULT` no reescribe la
tabla desde PG 11, y `DROP NOT NULL` solo toca el catálogo. Sin bloqueos de importancia.

---

## 1. Decisiones de diseño

**D1 — Se agrega una capa POR DÍA DE SEMANA, no se reusa la de por fecha.**
`horarios_habito_por_fecha` tiene `hora_disparo NOT NULL` y su agregado rechaza fechas `<= hoy`. No
puede expresar "este día, apagado" ni "corregir hoy". Sobrecargarla rompería su invariante. Las dos
capas conviven: la de fecha exacta gana.

**D2 — Interruptor y hora por día viven en la MISMA tabla.** Mismo razonamiento de V23: pausar no es
una relación nueva, es un atributo de una relación que ya existe. Acá la relación es "qué hace este
aprendiz con este hábito ESE día de la semana".

**D3 — El plan personal solo puede RESTAR días, nunca sumar.** V31 rechazó los patrones semanales por
dos motivos: crean un agujero permanente y silencioso en un programa de 90 días, y duplican
`horarios_habito.tipo_dia`. Hay que responderle, no ignorarla:
- La duplicación se evita con precedencia **unidireccional**: el catálogo dice en qué días PUEDE
  correr; el plan personal solo puede sacar. "¿Va hoy?" sigue siendo un AND, no dos fuentes compitiendo.
- El agujero se acota dejando fuera los obligatorios (`habitos.desactivable = false`, V18): 409, igual
  que `DesbloqueoHabito.pausar`.

**D4 — El momento del día se DERIVA, no se guarda por hábito.** Guardar `momento` en cada hábito lo
desincroniza en cuanto cambia la hora — la lección de `.claude/rules/04` sobre
`fecha_graduacion_esperada`. Se guardan los tres cortes por aprendiz y el bloque se calcula.

**D5 — "Mover de bloque" es cambiar la hora, y así hay que decirlo.** Si no cambia la hora, el hábito
queda a las 06:30 rotulado "TARDE": una segunda verdad. El móvil abre el picker precargado con el
inicio del bloque y llama al PATCH que ya existe — **cero backend**.

**D6 — Se arregla el rango de la pausa aunque el interruptor por día lo tape.** Es un bug
independiente y desplegable solo.

---

## 2. Migraciones

Convención de `.claude/rules/04`: `V<n>__descripcion_en_snake_case.sql`, cabecera que justifica qué
problema resuelve y por qué no se reusa una columna existente, `SET search_path TO renaser, public;`
explícito, y `CHECK` solo donde la invariante se evalúa con datos de la propia fila.

Última aplicada: **V37**. Siguiente libre: **V38**. Nota: **V7 no existe** en la secuencia (V1–V6,
luego V8) — Flyway tolera el hueco, no se rellena.

### V38 — `desbloqueos_habito_pausa_desde.sql`

```sql
-- La pausa del aprendiz nunca fue un RANGO, aunque V31 la describa asi.
-- `DesbloqueoHabito.estaPausadoEl` no mira `pausado_en`: devuelve true para TODA fecha
-- <= `pausado_hasta`. Efecto real reportado por el dueno: apagar el martes apaga tambien el
-- lunes (y todos los dias anteriores del programa).
--
-- POR QUE UNA COLUMNA NUEVA Y NO REUSAR `pausado_en`: `pausado_en` es `timestamptz` y es el
-- hecho de auditoria "cuando toco el boton" (V23 lo eligio asi a proposito). El limite
-- inferior del rango se razona en dias del calendario DEL APRENDIZ, igual que `pausado_hasta`,
-- y por el mismo motivo que V31 argumento: comparar contra un instante reintroduce el problema
-- de zonas de E-91. Convertir `pausado_en` a `date` perderia la hora y le cambiaria el
-- significado a una columna que ya tiene lectores.
--
-- NULL = sin limite inferior: es EXACTAMENTE el comportamiento actual, asi que las filas
-- existentes no cambian de semantica al migrar.

SET search_path TO renaser, public;

ALTER TABLE desbloqueos_habito
    ADD COLUMN pausado_desde date;

ALTER TABLE desbloqueos_habito
    ADD CONSTRAINT desbloqueos_pausa_desde_requiere_pausa
    CHECK (pausado_desde IS NULL OR pausado_en IS NOT NULL);

ALTER TABLE desbloqueos_habito
    ADD CONSTRAINT desbloqueos_pausa_rango_coherente
    CHECK (pausado_desde IS NULL OR pausado_hasta IS NULL OR pausado_desde <= pausado_hasta);

COMMENT ON COLUMN desbloqueos_habito.pausado_desde IS
    'Primer dia (INCLUSIVE) de la pausa, en la zona del participante. NULL = sin limite '
    'inferior (comportamiento de V23/V31, que es el de las filas ya existentes). Ver V38.';
```

### V39 — `plan_semanal_habito.sql`

```sql
-- El aprendiz decide, POR DIA DE LA SEMANA, si un habito va y a que hora.
--
-- QUE PROBLEMA RESUELVE. Hoy no existe forma de apagar un dia suelto: `habitos.activo` es del
-- catalogo compartido (panel admin), `horarios_habito.tipo_dia` tambien, y la pausa de
-- `desbloqueos_habito` es un rango de fechas que arrastra los dias anteriores. Y la hora es
-- una sola para los siete dias (`preferencias_horario`, PK participante+habito).
--
-- POR QUE NO SE REUSA `horarios_habito_por_fecha` (V37): esa tabla tiene `hora_disparo NOT
-- NULL` y `HorarioPorFecha.requirePlanificable` solo acepta fechas futuras, asi que no puede
-- expresar ni "este dia APAGADO" ni "corregir hoy". Ademas responde otra pregunta: una
-- excepcion de UNA fecha, que no se repite la semana siguiente. Las dos capas conviven y la de
-- fecha exacta gana.
--
-- POR QUE NO SE REUSA `preferencias_horario`: su PK es (participante, habito) — es literalmente
-- "una hora para toda la semana". Agregarle `dia_semana` cambiaria su PK y romperia la FK
-- compuesta que `cambios_horario_pendientes` ya tiene contra ella (E-54).
--
-- POR QUE UNA SOLA TABLA PARA EL INTERRUPTOR Y LA HORA: mismo razonamiento que V23. La relacion
-- "que hace este aprendiz con este habito ESE dia" ya es una sola; dos tablas al lado dejarian
-- dos filas respondiendo la misma pregunta.
--
-- POR QUE ESTO NO CONTRADICE A V31, que rechazo los patrones semanales para la pausa: la
-- precedencia es UNIDIRECCIONAL. `horarios_habito.tipo_dia` (compartido) decide en que dias el
-- habito PUEDE correr; `activo = false` aca solo puede SACAR dias de ese conjunto, nunca
-- agregar. "Va hoy" sigue siendo una sola regla, un AND, no dos fuentes en competencia. El
-- agujero permanente que preocupaba a V31 se acota en el dominio: un habito con
-- `habitos.desactivable = false` (V18) no acepta `activo = false` — invariante que cruza dos
-- tablas, asi que NO puede ser un CHECK.
--
-- `dia_semana smallint 1..7`: ISO-8601, el mismo valor que `java.time.DayOfWeek.getValue()`
-- (1 = lunes). No se usa el `dow` de Postgres (0 = domingo) para que la traduccion Java<->SQL
-- sea la identidad y no haya un +1 escondido en un mapper.
--
-- Horas NULLABLES: NULL = "ese dia hereda la hora que ya regia". Sin eso, apagar el jueves
-- obligaria a repetir la hora, y cambiar el horario general dejaria el jueves congelado.

SET search_path TO renaser, public;

CREATE TABLE plan_semanal_habito (
    participante_id      uuid     NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    habito_id            uuid     NOT NULL REFERENCES habitos (id) ON DELETE CASCADE,
    dia_semana           smallint NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    activo               boolean  NOT NULL DEFAULT true,
    hora_disparo         time,
    hora_limite          time,
    recordatorio_activo  boolean,
    minutos_recordatorio smallint CHECK (minutos_recordatorio >= 0),
    creado_en            timestamptz NOT NULL DEFAULT now(),
    actualizado_en       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (participante_id, habito_id, dia_semana),
    CONSTRAINT plan_semanal_horas_coherentes
        CHECK (hora_disparo IS NULL OR hora_limite IS NULL OR hora_disparo < hora_limite)
);

-- El barrido nocturno pregunta "que dice el plan de este participante para ESTE dia de la
-- semana" una sola vez por participante. Este es el indice de esa consulta.
CREATE INDEX plan_semanal_dia_idx ON plan_semanal_habito (participante_id, dia_semana);

COMMENT ON COLUMN plan_semanal_habito.activo IS
    'false = el aprendiz apago este habito ESE dia de la semana. Solo RESTA dias del conjunto '
    'que habilita el catalogo (horarios_habito.tipo_dia); nunca agrega. Ver V39.';
COMMENT ON COLUMN plan_semanal_habito.hora_disparo IS
    'NULL = hereda de la excepcion de fecha, del cambio pendiente vigente, de preferencias_horario '
    'o del catalogo, en ese orden. Ver V39 y HorarioResuelto.';
```

### V40 — `rangos_dia_participante.sql`

```sql
-- Donde empiezan la manana, la tarde y la noche PARA ESTE APRENDIZ.
--
-- QUE PROBLEMA RESUELVE. El bloque del dia lo derivaba el movil con dos cortes clavados en el
-- cliente (`aMomento`: <12 manana, <18 tarde, si no noche). Dos defectos reales: la madrugada
-- caia en "manana" (dormir 00:30 aparecia en el bloque de levantarse), y quien se levanta 04:00
-- o trabaja de noche veia su dia partido en los bloques de otra persona. El movil ya reescribio
-- la regla (`utils/momentosDelDia.ts`) y guarda los cortes en AsyncStorage, que su propio
-- javadoc declara provisorio. Esta migracion es ese lugar.
--
-- POR QUE NO UNA COLUMNA EN `participantes_programa`: esa tabla es de `users`, y `habits` la lee
-- por un puerto. Meterle una columna que solo lee `habits` cruzaria el limite de modulo que la
-- arquitectura hexagonal del repo sostiene.
--
-- POR QUE NO SE GUARDA EL MOMENTO EN CADA HABITO: se desincronizaria en cuanto cambiara la hora.
-- Es la leccion literal de .claude/rules/04 sobre `fecha_graduacion_esperada`.
--
-- TRES COMIENZOS Y NO SEIS EXTREMOS: tres puntos en un circulo de 24 h no dejan huecos ni
-- superposiciones POR CONSTRUCCION. La noche es el bloque que envuelve la medianoche: va de
-- `inicio_noche` a `inicio_manana` del dia siguiente — que es exactamente la correccion pedida.
--
-- `time` y no `smallint` de minutos: el resto del esquema ya migro de texto "HH:mm" a `time`
-- (P-05), y comparar contra `hora_disparo` (tambien `time`) no necesita conversion.
--
-- DEFAULTS 05:00/12:00/18:00: 12 y 18 son EXACTAMENTE los cortes de `aMomento`, para que quien
-- no toque nada vea lo mismo. 05:00 es el unico valor nuevo.
--
-- El minimo de 60 minutos por bloque se impone en el DOMINIO y no aca: el bloque de la NOCHE se
-- mide dando la vuelta a la medianoche, y esa cuenta no es una comparacion llana entre columnas
-- de la misma fila.

SET search_path TO renaser, public;

CREATE TABLE rangos_dia_participante (
    participante_id uuid PRIMARY KEY REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    inicio_manana   time NOT NULL DEFAULT '05:00',
    inicio_tarde    time NOT NULL DEFAULT '12:00',
    inicio_noche    time NOT NULL DEFAULT '18:00',
    creado_en       timestamptz NOT NULL DEFAULT now(),
    actualizado_en  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rangos_dia_en_orden
        CHECK (inicio_manana < inicio_tarde AND inicio_tarde < inicio_noche),
    CONSTRAINT rangos_dia_bloques_diurnos_minimos
        CHECK (inicio_tarde - inicio_manana >= interval '1 hour'
           AND inicio_noche  - inicio_tarde  >= interval '1 hour')
);
```

---

## 3. Precedencia y zona horaria

### 3.1 Hora efectiva de (aprendiz A, hábito H, fecha F) — **campo por campo**

| # | Fuente | Tabla | Alcance |
|---|---|---|---|
| 1 | Excepción de fecha exacta | `horarios_habito_por_fecha` donde `fecha = F` | V37, existente |
| 2 | **Patrón por día de semana** | `plan_semanal_habito` donde `dia_semana = F.getDayOfWeek().getValue()` | **V39, nuevo** |
| 3 | Cambio general ya vigente | `cambios_horario_pendientes` donde `fecha_efectiva <= F` | V1 |
| 4 | Preferencia general | `preferencias_horario` | V1 |
| 5 | Catálogo | primer `horarios_habito` que cumple `aplicaEnDia(diaPrograma, tipoDia)` | V1 |

**Gana la más específica**, y el respaldo es **por campo**, no por objeto: una excepción de fecha que
solo fija `hora_disparo` conserva la `hora_limite` de la capa de abajo.

> **Bug preexistente a corregir en el camino.** Hoy
> `PreferenciaHorarioPersistenceAdapter.porParticipanteHabitosYFecha` hace
> `efectivos.put(id, PreferenciaHorario.rehydrate(...))` con la fila de fecha: **reemplaza el objeto
> entero**. Una excepción de fecha con `hora_limite = NULL` borra silenciosamente la `hora_limite` de
> la preferencia general. Si la capa 2 se agrega copiando ese `put`, hereda el defecto multiplicado
> por dos.

### 3.2 "¿Va hoy?" — AND de cuatro condiciones

1. **Catálogo**: existe `horarios_habito` con `aplicaEnDia(diaPrograma(F), TipoDia.delDia(F))`.
2. **Desbloqueo**: no hay fila con `dia_desbloqueo > diaPrograma`.
3. **Pausa por rango**: `!estaPausadoEl(F)` — con V38 pasa a ser un rango real.
4. **Interruptor por día** (nuevo): no hay fila en `plan_semanal_habito` con ese `dia_semana` y
   `activo = false`.

Nunca se agrega un día que el catálogo excluye (D3).

### 3.3 Momento del día — derivación pura

```
momento(F) = f(horaDisparoResuelta(A,H,F), rangos(A))
  [inicio_manana, inicio_tarde)                          → MORNING
  [inicio_tarde,  inicio_noche)                          → AFTERNOON
  resto  ([inicio_noche,24:00) ∪ [00:00,inicio_manana))  → NIGHT
sin hora resuelta                                        → MORNING (mismo default que hoy)
```

Idéntica a `momentoDeMinutos` en `momentosDelDia.ts`. La regla vive en el dominio
(`MomentoDelDia.de(LocalTime, RangosDelDia)`), se prueba sin Spring, y el móvil deja de ser su dueño.

### 3.4 Zona horaria

Antecedentes reales: **E-91** (cron 10 minutos antes de que empezara el día del aprendiz), **E-105**
(`LocalDate.now()` en `HabitTrackController` apagaba la pantalla desde las 19:00 Lima), **E-106**. El
candado ejecutable es `ArchitectureTest.adaptersDeEntradaNoUsanLaFechaDelServidor:112`.

1. **La fecha F sale siempre** de `clock.now().atZone(ZoneId.of(progreso.timezone())).toLocalDate()`.
   Nunca `clock.today()`, nunca `LocalDate.now()`.
2. **El día de la semana se deriva de F** (`F.getDayOfWeek()`). Ya está en la zona del aprendiz.
   **No recalcularlo en ningún otro lado** — es el punto exacto donde volvería a entrar E-91.
3. **Los cortes y las horas no se convierten de zona.** `inicio_manana` y `hora_disparo` son hora de
   pared del día del aprendiz. El momento se calcula **sin un solo `atZone`**.
4. `pausado_desde` y `pausado_hasta` son `date` para no comparar contra un instante.
5. **Riesgo preexistente**: `GenerarTracksDelDiaScheduler` corre a las 05:02 UTC (= 00:02 Lima) y
   `participantes_programa.timezone` es `text` libre. Un participante al este de UTC recibe su día
   tarde. El arreglo correcto (cron cada hora, el dominio decide) **no está en el alcance de este plan**.

---

## 4. Fases

### Fase 0 — COMMITEAR LO QUE YA EXISTE (antes que cualquier otra cosa)

> **Estado real verificado el 2026-09-07.** La rama es `codex/horarios-habitos-por-fecha` y
> **no tiene ni un commit por encima de `master`** (`git log master..codex/horarios-habitos-por-fecha`
> devuelve vacío). Todo el trabajo por fecha está en el **working tree**: 27 archivos modificados
> (+327/−108) y 12 archivos nuevos sin rastrear, entre ellos `V37__horarios_habito_por_fecha.sql`,
> `HorarioPorFecha.java`, su entidad JPA, su PK, su repositorio y cuatro clases de prueba.
>
> **Un `git checkout`, un `git stash` o un `git clean` lo borra entero.** Commitear es el paso 0
> literal: no se numera V38 ni se escribe una línea nueva hasta que esto esté guardado.

Después de commitear:
1. Confirmar si `momentosDelDia.ts` y `storage/rangosDelDia.ts` del móvil están commiteados.
2. Decidir con el dueño las tres preguntas de §6.2 — la primera **cambia si V39 hace falta o no**
   (ver §4.bis).

### Fase 1.bis — Lo que ya está construido y NO hay que volver a hacer

Inventario de lo que existe en esa rama, para reusarlo en vez de duplicarlo:

| Pieza | Archivo | Reusable para |
|---|---|---|
| Tabla por fecha | `V37__horarios_habito_por_fecha.sql` | La capa 1 de §3.1, ya hecha |
| Agregado de dominio | `domain/model/preferencia/HorarioPorFecha.java` | Invariantes de la excepción |
| Entidad + PK + repo | `HorarioPorFechaJpaEntity`, `HorarioPorFechaPk`, `SpringDataHorarioPorFechaRepository` | Persistencia, ya hecha |
| **Precedencia de 3 capas** | `PreferenciaHorarioPersistenceAdapter.porParticipanteHabitosYFecha` | El lugar donde entra la capa por día de semana |
| Puertos con fecha | `LoadPreferenciaHorarioPort`, `SavePreferenciaHorarioPort` | Firmas ya extendidas |
| Contrato HTTP | `UpdateHabitPreferenceRequest.date`, `HabitPreferenceController` | El PATCH/GET con fecha, ya hecho |
| Pruebas | `HorarioPorFechaTest`, `HorarioPorFechaPersistenceAdapterTest`, `HabitPreferenceControllerTest` | Base para las nuevas |

**Nada de esto se rehace.** El trabajo nuevo se engancha en el adapter que ya resuelve la precedencia.

### 4.bis — ¿V39 hace falta? Depende de la pregunta 1 de §6.2

**Si "el jueves" significa la fecha exacta** (jueves 11), **V39 no hace falta y no se crea ninguna
tabla nueva para esto.** Alcanza con extender la tabla que ya existe:

```sql
-- V38b (o dentro de V38): el interruptor por DIA, sobre la tabla que ya existe.
ALTER TABLE renaser.horarios_habito_por_fecha ADD COLUMN activo boolean NOT NULL DEFAULT true;
ALTER TABLE renaser.horarios_habito_por_fecha ALTER COLUMN hora_disparo DROP NOT NULL;
```

Con eso, una fila de esa tabla pasa a poder decir tres cosas: "ese día a otra hora", "ese día apagado"
y "ese día apagado sin tocarle la hora". Se reusa el agregado, la entidad, el repositorio, el adapter,
la precedencia y los cuatro lectores diarios: **cero infraestructura nueva**.

Hay que tocar dos invariantes que el autor de V37 escribió a propósito, y por eso no es gratis:
- `HorarioPorFecha` exige `horaDisparo != null` → pasa a exigirla **solo cuando `activo` es true**.
- `requirePlanificable` rechaza `fecha <= hoy` → hay que decidir si apagar HOY se permite. Para una
  pantalla del día (Training) hace falta; para "planificar mañana" (Plan) no. Recomendación: permitir
  apagar hoy, seguir prohibiendo **mover la hora** de hoy — que es lo que esa regla protegía.

**Si "el jueves" significa todos los jueves**, entonces sí hace falta V39 como está descrita, porque
una tabla con `fecha` en la PK no puede expresar un patrón que se repite.

**Recomendación:** empezar por la extensión de la tabla existente. Cubre el caso de Training —que es
la pantalla que disparó el pedido— sin esquema nuevo, y deja V39 para cuando el patrón semanal se
pida de verdad.

### Fase 1 — Arreglar el rango de la pausa (desplegable sola)

**Migración**: V38.

`DesbloqueoHabito`: campo `LocalDate pausadoDesde` + sobrecarga de `rehydrate` (el archivo ya encadena
tres por compatibilidad); `pausar(boolean desactivable, LocalDate desde, LocalDate hasta, Instant ahora)`,
con las firmas viejas delegando con `desde = null`. **`estaPausadoEl` pasa a ser un rango real**:

```java
if (pausadoEn == null) return false;
if (pausadoDesde != null && dia.isBefore(pausadoDesde)) return false;
return pausadoHasta == null || !dia.isAfter(pausadoHasta);
```

`DesbloqueoHabitoService.cambiarEstado`: al pausar, `pausadoDesde` por defecto es **hoy en la zona del
aprendiz**. Las filas viejas quedan con `null` y conservan su comportamiento.

Contrato aditivo: `CambiarEstadoHabitoRequest` gana `LocalDate pausedFrom` (opcional);
`HabitUnlockItemResponse` gana `pausedFrom`.

### Fase 2 — Plan semanal: interruptor y hora por día de semana

**Migración**: V39.

**Dominio** (`domain/model/plansemanal/`): `DiaPlanHabito` (invariantes: `horaDisparo < horaLimite`;
`apagar(boolean desactivable)` tira si `!desactivable` → 409) y `PlanSemanalHabito` (los 7 días).
**Extender `HorarioResuelto`** con una capa más, manteniendo el respaldo por campo — único lugar donde
vive la precedencia de §3.1.

**Decisión de arquitectura clave:** meter la capa 2 **dentro de
`LoadPreferenciaHorarioPort.porParticipanteHabitosYFecha`**, no en un método nuevo. Los cuatro lectores
diarios ya la llaman con la fecha: heredan la hora por día **sin tocar una línea**. Un método nuevo
obligaría a modificar los cuatro y garantizaría que alguno quedara afuera. El interruptor `activo` no
puede ir por ahí (`PreferenciaHorario` no tiene ese campo) → puerto propio.

**Generador — `RegistroService.generarInterno`, los tres cambios que hacen que el esquema se note**:

1. **Cargar las preferencias SIEMPRE**, no solo con corte horario. Costo: una consulta de lote más por
   participante en el barrido.
2. **Sumar el interruptor al conjunto de descarte**, en UNA consulta:
   `loadPlanSemanalPort.deParticipanteEnDia(participanteId, fecha.getDayOfWeek())`. `fecha` ya viene en
   la zona del participante — **no recalcular el día de la semana en ningún otro lado**.
3. **Compatibilidad explícita**: **sin fila = el hábito va**. Filtrar por "tiene fila en el plan
   semanal" dejaría al padrón entero sin hábitos, porque la tabla arranca vacía.

**Endpoints**

`GET /api/v1/habit-weekly-plan` — el plan de los 7 días ya resuelto (el cliente nunca reimplementa la
precedencia):

```json
{
  "habits": [
    {
      "habitId": "3f0c1e5a-1c2b-4d3e-8a9f-0b1c2d3e4f50",
      "catalogWeekdays": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY"],
      "days": [
        { "weekday": "MONDAY",  "active": true,  "triggerTime": "06:30", "limitTime": "07:30",
          "reminderEnabled": true, "reminderMinutesBefore": 10,
          "customized": false, "dayMoment": "MORNING" },
        { "weekday": "THURSDAY","active": false, "triggerTime": "06:30", "limitTime": "07:30",
          "reminderEnabled": true, "reminderMinutesBefore": 10,
          "customized": true,  "dayMoment": "MORNING" }
      ]
    }
  ],
  "dayRanges": { "morningStart": "05:00", "afternoonStart": "12:00", "nightStart": "18:00" }
}
```

`days` trae siempre las 7 entradas. `customized: false` = ese día hereda. `catalogWeekdays` repite el
`activeWeekdays` de `GET /api/v1/habits` para que la pantalla no cruce dos endpoints. `weekday` como
nombre de `java.time.DayOfWeek`, igual que `activeWeekdays` ya hace.

`PATCH /api/v1/habit-weekly-plan/{habitId}/{weekday}` — `{"active": false}` o
`{"active": true, "triggerTime": "07:15", "limitTime": null, "reminderEnabled": true, "reminderMinutesBefore": 15}`.

**Todos los campos opcionales, con tipos wrapper** (`Boolean`, no `boolean`). Ausente = no se toca. No
es cosmético: `UpdateHabitPreferenceRequest.reminderEnabled` es `boolean` primitivo y por eso el PATCH
entero devuelve 400 si el cliente omite el campo — bug real del 2026-09-03. No repetirlo.

Errores: `409` si `desactivable = false` y `active: false`; `409` si el `weekday` no está en
`catalogWeekdays` y se manda `active: true` (D3); `404` si el hábito no existe o es PERSONAL de otro.

`DELETE /api/v1/habit-weekly-plan/{habitId}/{weekday}` → `204`, idempotente. Ese día vuelve a heredar.

### Fase 3 — Momento del día persistido

**Migración**: V40.

**Dominio** (`domain/model/momento/`): `RangosDelDia` (record de tres `LocalTime`, `POR_DEFECTO`
05:00/12:00/18:00, validación completa **incluido el mínimo de 60 min de la noche dando la vuelta a la
medianoche** — el que no entra en un CHECK) y `MomentoDelDia` (enum `MANANA/TARDE/NOCHE` +
`de(LocalTime, RangosDelDia)`). Traducción literal de `momentosDelDia.ts`.

`GET /api/v1/habit-day-ranges` → `{"morningStart":"05:00","afternoonStart":"12:00","nightStart":"18:00","customized":false}`
`PUT /api/v1/habit-day-ranges` con los tres campos → misma forma, `customized: true`. `400` si no pasa
la validación del dominio. Sin fila = defaults; no se siembra una fila por participante.

Campos derivados aditivos:

| Endpoint | Campo nuevo | Valores |
|---|---|---|
| `GET /api/v1/habit-preferences[?date=]` → item | `dayMoment` | `MORNING`/`AFTERNOON`/`NIGHT` |
| `GET /api/v1/habit-weekly-plan` → `days[]` | `dayMoment` | idem |
| `GET /api/v1/habit-tracks/today` → item | `momentoDelDia` | idem |

> **Inconsistencia real del repo, a documentar y no a "arreglar":** `RegistroHabitoConCatalogoResponse`
> usa nombres en castellano (`habitoId`, `fechaEjecucion`, `horaDisparo`) mientras `habit-preferences`
> y `habit-unlocks` usan inglés. El campo nuevo sigue la convención **de su propio DTO** para no dejar
> un DTO mitad y mitad. Renombrar los existentes rompe la app en producción.
>
> **Los VALORES del enum van en inglés en los tres**: son campos nuevos sin restricción de
> compatibilidad, y el móvil ya traduce enums (`BODY`/`MIND`/…).

**"Mover de bloque"**: sin backend nuevo (D5). El móvil abre el picker con
`limitesDelMomento(bloque).desde` precargado y llama al PATCH que ya existe. Documentar la decisión: es
la razón por la que no aparece un endpoint `move-moment`.

### Fase 4 — Cierre

- `GET /api/v1/habits` (`MiHabitoResponse`) gana **`plannedWeekdays`**: el conjunto **efectivo**
  (`activeWeekdays` menos los días apagados). `activeWeekdays` no se toca. Requiere una consulta de
  lote más en `MisHabitosService.consultar`.
- Cuota: sumar `plan_semanal_habito` a `PreferenciaHorarioService.habitosConCupoComprometido` **solo
  si** se decide que la hora por día consume cupo (§6.2).
- Docs: `docs/MODULO_HABITS.md` §23, `docs/MAPA_ENDPOINTS.md`, entrada en `docs/BITACORA_ERRORES.md`
  para el bug de `estaPausadoEl`.

**Pruebas** (regla 03), lo que no puede faltar:
- `DesbloqueoHabitoTest`: pausa `[martes, jueves]` → **lunes NO pausado**. Es la regresión del bug.
- `RegistroServiceTest`: hábito con `activo = false` el jueves **no genera track por el camino del
  barrido nocturno** (`horaDeCorte == null`) — el camino que hoy no lee preferencias.
- `RegistroServiceTest`: hora por día de semana aplicada **en el barrido nocturno**.
- `HorarioResueltoTest`: las 5 capas, respaldo **por campo**, incluida la excepción de fecha con
  `hora_limite = NULL`.
- `MomentoDelDiaTest`: 00:30 con `inicio_manana = 05:00` → **NIGHT**. El caso del dueño.
- **`FixedClock` con una hora UTC que caiga en el día local ANTERIOR** (entre 00:00 y 05:00 UTC) en
  todo test de comportamiento diario. Lo exige la regla 02, y es donde se esconde E-91.

---

## 5. Datos existentes

| Tabla | Al migrar | Efecto en el aprendiz |
|---|---|---|
| `plan_semanal_habito` | Vacía. Sin fila = el hábito va y hereda la hora | **Ninguno** |
| `rangos_dia_participante` | Vacía. Sin fila = 05:00/12:00/18:00 | Los hábitos entre 00:00 y 04:59 pasan de MORNING a NIGHT. **Es la corrección pedida**, pero es visible: afecta a `SLEEP` |
| `desbloqueos_habito.pausado_desde` | `NULL` en todas | Ninguno inmediato. Las pausas **nuevas** dejan de arrastrar días anteriores |
| `horarios_habito_por_fecha` | Se conserva tal cual | Ninguno. Sigue ganando sobre el patrón semanal |
| `preferencias_horario` / `cambios_horario_pendientes` | **No se tocan** | Ninguno |
| `registros_habito` | **No se tocan** | Los tracks ya generados son historia (`dia_programa` es snapshot) |

**No hay backfill de ningún tipo.** En particular, **no se convierten las pausas vigentes a filas de
`plan_semanal_habito`**: un rango de fechas y un patrón semanal no significan lo mismo, y adivinar qué
quiso decir el aprendiz es exactamente lo que E-156 anotó que no se hace.

---

## 6. Riesgos y decisiones que necesitan al dueño

### 6.1 Lo que NO se puede hacer sin romper la app en producción

1. **No** hacer obligatorio `date` ni ningún campo nuevo en `PATCH /api/v1/habit-preferences/{habitId}`.
2. **No** cambiar el significado del PATCH sin fecha: sigue siendo el cambio general diferido a mañana.
3. **No** quitar ni renombrar `activeWeekdays`, `paused`, `pausedUntil`, `deferred`,
   `deferredEffectiveDate`, `scheduleEdits`, `locked`, `systemKey`. La app los lee.
4. **No** usar `boolean` primitivo en DTOs de request nuevos → 400 para todo cliente que omita el campo.
5. **Los campos de respuesta aditivos SÍ son seguros**: todos los esquemas zod del móvil usan
   `.passthrough()`, con un comentario que lo declara intencional (`habitsSchemas.ts:9`). Verificado.
6. **Punto ciego del cliente viejo:** un hábito apagado los jueves **no genera track**, pero la app
   enviada lo pinta encendido. **Obliga a un orden de despliegue: backend primero, app después**, y a
   que la pantalla nueva lea `plannedWeekdays`.
7. **El default 05:00 mueve DORMIR de bloque.** Es lo que se pidió, pero es visible el primer día.

### 6.2 Tres preguntas abiertas

1. **¿"El jueves" es una fecha o un día de semana?** El plan soporta las dos (fecha exacta gana), pero
   la UI tiene que decir cuál está guardando. Hoy Plan muestra pestañas de una semana, así que el
   aprendiz lo lee como "los jueves" y el backend guarda "el jueves 11".
2. **¿La hora por día de semana consume cupo?** `CuotaEdicionHorario`: 3 hábitos distintos por semana
   pasado el día 7. Recomendación: **el interruptor ON/OFF es gratis** (es hermano de la pausa, que
   nunca cobró cupo); **la hora sí cobra**. Si cobra, sumar `plan_semanal_habito` a
   `habitosConCupoComprometido` o el cupo se evade por esta vía.
3. **¿"Mover de bloque" cambia la hora?** Si no la cambia, el bloque y la hora se contradicen.
   Recomendación en D5.

### 6.3 Riesgos técnicos

- **El más grave**: que el nuevo esquema se escriba y el **barrido nocturno no lo lea**. Si la Fase 2
  no toca las dos cosas de §0.4, el cambio funciona probándolo a mano por HTTP (que pasa por
  `generarDisponiblesAhora`, con corte) y **falla en producción para todo el padrón**.
- **Precedencia por objeto en vez de por campo** (§3.1): defecto ya presente, se duplica si se copia.
- **Recalcular el día de la semana** en cualquier lugar que no sea `F.getDayOfWeek()` sobre la fecha ya
  resuelta en la zona del aprendiz. Es la puerta de entrada de E-91.
- **`participantes_programa.timezone` es `text` sin restricción**: `ZoneId.of` tira
  `ZoneRulesException` con un valor basura, y el scheduler lo atrapa por participante (bien) pero ese
  aprendiz queda sin tracks en silencio. Preexistente; con más lecturas por zona, más superficie.
- **Numeración**: `V7` no existe en la secuencia — no rellenarlo. Confirmar que V37 está mergeado.

### 6.4 Archivos críticos para la implementación

- `src/main/java/com/renaser/os/habits/application/services/RegistroService.java`
- `src/main/java/com/renaser/os/habits/infrastructure/adapter/out/persistence/preferencia/PreferenciaHorarioPersistenceAdapter.java`
- `src/main/java/com/renaser/os/habits/domain/model/desbloqueo/DesbloqueoHabito.java`
- `src/main/java/com/renaser/os/habits/domain/model/horario/HorarioResuelto.java`
- `src/main/resources/db/migration/V37__horarios_habito_por_fecha.sql`
