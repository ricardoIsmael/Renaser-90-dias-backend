# Tiempo, zonas horarias y schedulers

Esta regla existe por un bug real (2026-09-03, `BITACORA_ERRORES.md` E-91): un aprendiz pasó su
Día 1 entero viendo "día 0", y el reloj le quedó corrido un día para todo el programa. La causa
tenía **diez minutos** de margen.

## 1. La medianoche local no existe a una hora UTC fija

Todo el padrón vive en `America/Lima` (UTC−5, el default de `participantes_programa.timezone`).
Un cron a las `04:50 UTC` corre a las **23:50 del día anterior** en Lima.

- **Un `@Scheduled` diario que depende del día local del usuario está mal por construcción.** Si el
  trabajo tiene que ocurrir "cuando empieza el día del participante", el cron corre **cada hora** y
  el dominio decide si toca hacer algo.
- Nunca elegir una hora de cron razonando solo sobre el orden entre crons. Preguntarse siempre:
  *¿en qué fecha local cae este instante para un participante de Lima?*
- `clock.today()` es la fecha del **servidor**, no la del participante. Para el día de alguien va
  siempre `clock.now().atZone(participacion.timezone()).toLocalDate()`.

## 2. Derivar, no incrementar

Un contador que se incrementa desde un cron **pierde para siempre** cualquier corrida que no ocurra:
una noche con el backend caído deja al aprendiz un día atrasado el resto del programa.

- **Todo valor que sea función del calendario se deriva de fechas, no se acumula.**
  `dia_programa = acotar([0,90], (hoy_en_su_zona − fecha_inicio) + 1 − dias_ajuste_programa)`.
- Se puede materializar el resultado en una columna (siete módulos lo leen sin conocer la zona),
  pero la **fuente de verdad son las fechas**, y el barrido solo copia.
- Una operación derivada es idempotente gratis: correrla dos veces da lo mismo, y correrla tarde se
  pone al día sola. Eso vale más que cualquier `@SchedulerLock`.

## 3. El reloj entra por el puerto `Clock`

- `domain/` nunca llama a `Instant.now()`, `LocalDate.now()` ni `System.currentTimeMillis()`.
  Se inyecta `com.renaser.os.shared.domain.Clock`.
- En tests se usa `FixedClock`. **Cuidado con la hora que se fija**: un `FixedClock` a las 10:00 UTC
  cae el mismo día calendario en Lima y esconde exactamente el bug de arriba. Todo test de un
  comportamiento diario **debe** incluir un caso con el reloj en una hora UTC que caiga en el día
  local *anterior* (entre 00:00 y 05:00 UTC).

## 4. Schedulers

- `@EnableScheduling` ya está declarado globalmente (`points.PointsSchedulingConfig`, D-P4). No
  repetirlo.
- Todo `@Scheduled` nuevo pasa por la tabla de `docs/informes/auditoria-fixes/C-5.md` y lleva
  `@SchedulerLock` salvo justificación escrita.
- Un barrido masivo **pagina** (`TAMANO_LOTE`), no carga el padrón entero en memoria.
- Un barrido masivo **no** va dentro de un `@Transactional` único: cada `save()` en su transacción
  implícita, para que un fallo a mitad no tire lo ya hecho.
- Un participante que falla no puede detener el barrido: `try/catch` por participante.
