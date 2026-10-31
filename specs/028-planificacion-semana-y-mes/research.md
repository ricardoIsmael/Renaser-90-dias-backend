# Research: Planificación de semana y mes

Cada decisión dice qué se eligió, por qué y qué se descartó. Lo marcado **(aviso)** lo decidió quien implementa y se le dice al dueño.

## R1. «Solo esta semana» se guarda en la tabla que ya guarda «ese día»

**Decisión.** Un cambio «solo esta semana» es una fila por fecha en `horarios_habito_por_fecha` (V37/V38). Esa tabla ya tiene la clave correcta
(participante × hábito × fecha) y ya la lee todo lo que importa (registros del día, avisos del servidor, Mi Radar, tiempo libre). Se amplía con lo que el
editor guarda por día de la semana y la fecha todavía no puede decir:

- **V130** (aditiva): `antelaciones_recordatorio smallint[]` (varios avisos, igual que V81/V111) y las cuatro columnas de «Cómo suena» de V112
  (`alarma_sonido`, `alarma_vibracion`, `alarma_voz`, `alarma_repetir`), con sus CHECK.
- **V131**: `recordatorio_activo` pasa a admitir `NULL` («hereda el aviso») y el CHECK de V38 (`NOT activo OR hora_disparo IS NOT NULL`) pasa a
  «hora, aviso o alarma propios», igual que V111/V112 hicieron con `horario_semanal_habito`.

**Por qué.** Es exactamente el hueco que la spec dejaba como riesgo técnico. V39, V40, V111 y V112 ya razonaron lo mismo: una tabla aparte duplica la
clave y abre una segunda respuesta a «¿cómo va este día?». Ninguna fila existente cambia de significado: las de V37 siguen siendo «ese día a otra hora, con
su aviso» (`recordatorio_activo` no nulo = aviso propio) y las de V38 «ese día no va».

**Descartado.** Tabla `planes_de_semana` (segunda fuente de verdad); copiar la semana a `horario_semanal_habito` (lo volvería permanente y ya existe el alcance «todas
las semanas»); JSON en una columna (los CHECK necesitan mirar los campos por separado).

## R2. La lectura por capas suma la fecha con el mismo respaldo por campo

**Decisión.** `resolverPorCapas` (adaptador de preferencias) ya pone la fecha exacta por encima del día de la semana. Ahora la capa de fecha también es por campo:
una fila de **solo aviso** o de **solo alarma** (sin hora) no pisa la hora de abajo; una fila apagada sigue sin pisar nada; una fila con hora y sin aviso propio
(`recordatorio_activo IS NULL`) hereda el aviso. Las filas de antes (con hora y con `recordatorio_activo` no nulo) se leen **exactamente igual que hoy**.
Quien lee el horario resuelto de una fecha (registros del día, `AvisoHabitoService`, `HorarioDelDiaFinder`, tiempo libre) no cambia: ya pasa por esa función.

**Por qué.** Un solo lugar para la precedencia, igual que `SemanaDelHabito` para los días. La app no mezcla capas: el servidor le manda cada fecha resuelta.

## R3. La oferta de repetir

**Decisión.** La oferta es siempre **«la semana en curso → la siguiente»**, calculada por el servidor en la zona de la persona. Existe cuando:

1. la semana modelo (la en curso) tiene al menos una fecha propia de un hábito que hoy le corresponde, y
2. aplicar la repetición cambiaría algo de la siguiente (si ya es idéntica, no hay oferta).

Si la siguiente ya tiene cambios propios que la repetición reemplazaría, la oferta lo dice (`replacesOwnChanges`) y la app pide una confirmación explícita antes de
mandar `replace: true`. **(aviso)** La spec decía en FR-006 «la siguiente no tiene cambios propios» y en la historia 1 (escenario 4) «si la siguiente ya tiene cambios,
se ofrece pidiendo confirmación»; se siguió el escenario, que es más específico y más seguro para la persona.

**Qué se copia.** Para cada hábito que hoy le corresponde (catálogo aplicable por audiencia de rol + personales activos) y para cada día de la semana modelo: si la fecha
tiene fila propia, la fecha equivalente (+7 días) queda con la misma fila; si no la tiene y la equivalente sí, esa fila propia se borra (la siguiente queda igual que la
modelo). Solo se escribe en fechas **posteriores a hoy**. Las pausas no se repiten (viven en `desbloqueos_habito`, que no se toca). Un hábito de la modelo que ya no corresponde
(desactivado, fuera de la audiencia del rol) se deja afuera con su nombre y su motivo; no falla el resto.

**Idempotente y todo o nada.** El resultado es una función del estado de la modelo, no una suma: repetir dos veces deja lo mismo (la segunda vez no hay nada que escribir). Se
aplica en una transacción.

**«Ahora no» y la huella.** El servidor devuelve una `fingerprint` (SHA-256 truncado) de las filas de la **modelo**. La app guarda en el teléfono, por cuenta,
`modelo|siguiente|huella` al tocar «Ahora no»; la oferta no reaparece mientras la huella sea la misma y reaparece si la modelo cambia (FR-012). En otro teléfono puede salir una vez
más: límite aceptado en la spec. No se crea tabla ni columna.

**Deshacer.** `DELETE /habit-preferences/weeks/{lunes}` borra las filas propias de esa semana (solo fechas posteriores a hoy) de todos los hábitos de la persona. Si la
repetición había reemplazado cambios propios de la siguiente, esos no se restauran: la persona lo confirmó antes. «Deshacer» y «Volver al plan de siempre» son la misma operación.

**Descartado.** Ofrecer pares arbitrarios de semanas (no hay una pantalla del mes para hábitos: el mes es el plan de objetivos); guardar la oferta descartada en el servidor (tabla nueva);
repetir por notificación (el recordatorio de los domingos que ya existe abre Planificar, que es donde está la oferta).

## R4. Qué guarda «Solo esta semana» y qué no cobra

**Decisión.** `PUT /habit-preferences/{id}/week` recibe, por fecha, **solo lo tocado** (hora, aviso, «Cómo suena», apagar) o `reset`. El servidor une con la fila que ya hubiera, y todo
valor igual al que ya rige ese día de la semana se guarda como «hereda» (`NULL`), para que un cambio posterior del plan de siempre siga llegando a ese día. Una fila que queda sin nada propio se borra.
Solo fechas **posteriores a hoy**, dentro de la semana pedida; un hábito obligatorio del programa no se puede apagar (409, la misma regla que la pausa); la hora respeta el tope del día (D-122).
No pasa por `CuotaEdicionHorario` (no hay tope de cambios, D-170) y no deja rastro en el historial de reacomodos, igual que el aviso y la alarma por día de la semana.

## R5. Las alarmas del teléfono con fechas propias

**El problema.** Una alarma `DAILY`/`WEEKLY` no puede saltarse una fecha: cancelarla borra las de las demás semanas y dejarla puesta la deja sonar a la hora vieja. Es el mismo problema del #33.

**Decisión.** `GET /habit-preferences` suma por hábito `dateOverrides`: sus fechas con fila propia desde hoy y hasta 20 días, ya resueltas (hora, aviso, «Cómo suena», si va). La app arma el
plan de alarmas con ellas (`planDeAlarmas` recibe `fechas`):

- Si el hábito no tiene fechas propias vigentes, **todo es igual que hoy** (diario o por día; mismos identificadores; SC-007).
- Si tiene, el hábito pasa al esquema por día y los días de la semana **afectados** (los que tienen alguna fecha propia vigente) no llevan alarma semanal: llevan **alarmas de fecha**, una por aviso y por
  ocurrencia de ese día de la semana desde hoy hasta la última fecha propia más **dos semanas de cola** (con el valor propio donde hay fila y el de siempre donde no). Los demás días siguen semanales.
- **Identificadores.** El primer aviso de la primera ocurrencia de un día afectado conserva `habito-{id}-d{iso}-0` (la etiqueta del push de respaldo del servidor, spec 007); el resto, `habito-{id}-f{AAAAMMDD}-{n}` (una alarma de fecha por día no puede compartir identificador).
- **Se revierte solo.** Cuando ya no quedan fechas propias vigentes, la puesta al día que corre al abrir la app y al volver a primer plano compara lo programado con lo que corresponde (la firma del plan) y deja las `DAILY`/`WEEKLY` de siempre. «Volver al plan de siempre» y «Deshacer» también reprograman en el acto.
- **Composición.** El puente del #33 (hábito cumplido hoy) y las repeticiones de la spec 009 («Repetir si no la atiendo») leen la misma entrada, así que respetan la hora, el aviso y «Cómo suena» de la fecha propia sin lógica nueva.
- **Tope (aviso).** Máximo 48 alarmas de fecha por hábito por esta vía (las más próximas primero): Android admite unas 500 por app y ya hay semanales, el Radar y los eventos. Lo que quede fuera se pone en la siguiente puesta al día, a medida que pasan las anteriores.
- **Límites aceptados.** (1) Si la persona no abre la app en las dos semanas siguientes a la última fecha propia, el día afectado se queda sin alarma hasta que la abra (como el límite de 7 días del puente y de las repeticiones). (2) Un APK anterior no conoce las fechas propias: sus alarmas locales siguen como el plan de siempre (el push del servidor sí las respeta). (3) Hace falta un APK nuevo.

**Descartado.** Un puente de fechas para TODOS los días del hábito (más alarmas y una zona sin cobertura para todos los días al terminar); programar solo la fecha propia y dejar la semanal (sonaría dos veces); mover la
responsabilidad al servidor (la alarma es local).

## R6. «Planificar el mes» es del plan de objetivos y no necesita backend

**Decisión.** El mes es el del programa (Mes 1 = semanas 1 a 4; el mes 3 se lleva la 13), como lo cuenta Plan (`periodoDelPrograma`) y el servidor. «Planificar mi mes» abre una hoja en Plan con los tres meses, el
objetivo del mes del eje abierto y las semanas del mes. Reutiliza `GET /rocks/monthly/plan`, `PUT /rocks/monthly/{eje}/{mes}` (que ya acepta cualquier mes, no solo el en curso), `GET /rocks/weekly?semana=` y
`EditarObjetivoDelMesModal`. No se arman objetivos semanales de semanas que no llegaron: la ventana del domingo es una regla del servidor (D-203) y no se mueve. **(aviso)** Esto hace que «planificar el mes» sea sobre
todo **ver y ajustar** el objetivo de cada mes; el dueño lo definió como «el plan de objetivos que ya tiene», así que no se inventó un flujo nuevo de semanas por adelantado.

## R7. La cuota semanal después del día 90 **(aviso)**

`CuotaEdicionHorario.HABITOS_POR_SEMANA` (3) solo rige después del día 90 (`DIAS_DE_ACOMODO_LIBRE = 90`, D-170). `habitosConHorarioEntre` cuenta los hábitos con fila por fecha en la semana, así que
después del día 90 las filas de «solo esta semana» cuentan como «hábito reacomodado» para el PATCH general y para fijar la hora de un día de la semana. No se cambió esa cuenta para no tocar la lógica actual; queda a la vista en
D-296 por si el dueño decide qué pasa después del día 90.

## R8. Aislamiento

Hoy hay una sola formación. El aislamiento exigido por la spec (FR-018) se cumple con la persona (todas las lecturas y escrituras van por `participante_id` del actor) y con el conjunto de hábitos que le
corresponden (catálogo por audiencia de rol + personales propios). Un hábito que no le corresponde se rechaza (404) sin escribir nada. Cuando existan las formaciones (022/023), el filtro de «hábitos aplicables» es el punto único donde sumarlas.

## R9. Dónde vive la oferta en la app

En «Tus prácticas» (engranaje de Training → Planificar): es la pantalla que ya existe solo para planificar y sirve a todas las dimensiones; una oferta por dimensión repetiría lo mismo cinco veces. La hoja de cada dimensión
(el editor) gana el alcance «Solo esta semana». El recordatorio de los domingos no cambia ni se suma ninguna notificación.
