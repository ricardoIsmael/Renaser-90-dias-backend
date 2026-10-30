# Research: Vista del líder, limpia

## R-1. ¿De dónde sale «con su hora»?

`HorarioDelDiaFinder.HorarioResuelto.personalizado` (habits): la hora la eligió la persona (hay una preferencia con hora) y
no vino del catálogo. Casi todo hábito del catálogo ya trae una hora de fábrica, así que `horaDisparo != null` no distingue a
quien programó de quien no. Una preferencia con solo aviso o alarma **no** es personalizada (`PreferenciaHorario`, spec 008).
**Decisión**: contar `personalizado`.

## R-2. ¿Qué hábitos cuentan en el total?

Los que el generador diario crea para ese día: `ConsultaPreferenciasHorarioService.habitosActivosDe` (catálogo aplicable a su
rol y a su día de programa, más sus hábitos personales). De ahí se descartan los apagados por fecha o por día de la semana y
los dentro de una pausa de su plan (`apagado`, `pausado`): no le tocan, así que no son «sin hora».

## R-3. ¿Qué día?

**Hoy**, en la zona de la persona. La ficha muestra 7 días ya cerrados; «programó» es una pregunta sobre el día en curso. Se
descartó medir la ventana: los hábitos de ayer ya tienen su hora y su resultado en los días de abajo.

## R-4. ¿Y las acciones?

`rocks.api.AccionesDelDiaFinder.accionesEntre(persona, hoy, hoy)` ya devolvía las acciones de la ficha. Faltaba `horaInicio`,
que `rocas_diarias.hora_inicio` ya guarda. «Agendada» = tiene hora de inicio. Se descartó `RocasDelAprendizFinder.deHoy`: es
autoservicio y lanza `NotAuthorizedException` si el programa no está andando, y esa excepción no se puede atrapar dentro de
la transacción de la ficha.

## R-5. ¿Dónde se decide el «Sí»?

En el servidor, en el dominio. La app no recalcula permisos ni datos (constitución, I). Así el umbral se cambia sin APK.

## R-6. ¿Cuántas evidencias?

De lo que la ficha ya trae (`obligaciones[]`): no hace falta backend. Se cuentan los hábitos que piden evidencia de los días
cerrados; hoy no cuenta porque todavía se puede entregar.

## R-7. «Consultas de mentores · N sin responder»

`GET /admin/tickets` pagina de a 30 y no trae un total. La app cuenta los `OPEN` de la primera página y dice «N+» si hay más.
Un endpoint de conteo habría sido más exacto pero es backend nuevo para una cifra de apoyo; queda como mejora si el dueño la
quiere exacta.

## R-8. El comentario «por el momento no debería haber grupos»

No se atiende (decisión del dueño): hoy son pocos alumnos y están decidiendo quién será el mentor.
