-- El reloj del programa pasa de INCREMENTAL a DERIVADO (bug del 2026-09-03, BITACORA E-91).
--
-- Sintoma: una cuenta creada el 02-09 con fecha_inicio 03-09 seguia en `dia_programa = 0`
-- durante todo su Dia 1. Causa: el cron corria a las 04:50 UTC, que para America/Lima
-- (UTC-5, el default de la columna `timezone` y la zona de todo el padron) son las 23:50
-- del dia ANTERIOR -- 10 minutos antes de que empiece el dia que tenia que contar. El
-- modelo incremental (+1 por corrida) ademas no recupera: una noche sin backend arriba se
-- perdia para siempre, porque `dia_programa_avanzado_el` marcaba el dia como ya procesado.
--
-- Modelo nuevo: `dia_programa` deja de ser la fuente de verdad y pasa a ser una copia
-- materializada de una cuenta que se hace con fechas:
--
--     dia_programa = acotar([0, 90], (hoy_en_su_zona - fecha_inicio) + 1 - dias_ajuste_programa)
--
-- Es idempotente (correrla dos veces da lo mismo) y se auto-corrige sola: si el backend
-- estuvo caido tres dias, la primera corrida al volver deja el dia correcto, no tres dias
-- atrasado. La columna se sigue materializando porque siete modulos la leen por el puerto
-- publico `ParticipacionProgramaFinder` sin conocer la zona del participante.
--
-- Por que una columna nueva y no mover `fecha_inicio`: `fecha_inicio` es la fecha que el
-- aprendiz eligio y quedo registrada -- reescribirla borra ese hecho, y ademas arrastra a
-- `fecha_graduacion_esperada` (columna GENERADA sobre ella). El ajuste va aparte para que
-- las dos preguntas -- "cuando empezo" y "cuantos dias no le cuentan" -- se puedan
-- responder por separado y auditar.
--
-- CON SIGNO a proposito. Positivo = el aprendiz RETROCEDE (dias de calendario que no
-- cuentan para el programa: viajo, se enfermo, y se lo devuelve al dia 34 para no
-- castigarle el puntaje). Negativo = se lo ADELANTA. Sin signo, `fijarDia` -- el ajuste
-- de admin que ya existe en PUT /api/v1/admin/trainees/{id}/program-day -- perderia la
-- mitad de su rango [0, 90] y eso seria una regresion de una capacidad ya entregada.
--
-- Sin CHECK de rango: quien lo acota es el dominio (`ParticipacionPrograma.fijarDia`
-- valida [0, 90] sobre el dia RESULTANTE, que es la invariante que importa). Un CHECK
-- sobre el ajuste crudo no sabria contra que fecha_inicio compararse.
-- Cada migracion corre en su propia conexion: si el contenedor arranco en un despliegue
-- posterior, el search_path que fija V1 no esta puesto en la sesion (mismo preambulo que
-- V13 y V18).
SET search_path TO renaser, public;

ALTER TABLE participantes_programa
    ADD COLUMN dias_ajuste_programa smallint NOT NULL DEFAULT 0;

COMMENT ON COLUMN participantes_programa.dias_ajuste_programa IS
    'Dias de calendario que NO cuentan para dia_programa. Positivo = retrocede (viaje, licencia); negativo = adelanta. Ver V20.';

-- `fecha_graduacion_esperada` (GENERATED ALWAYS AS fecha_inicio + 90) queda deliberadamente
-- sin tocar: Postgres no admite una columna generada que dependa de otra columna modificable
-- por UPDATE en el mismo sentido, y ninguna query del backend la lee (verificado: el unico
-- consumidor es el dominio, que la calcula al vuelo ya sumandole el ajuste). Se deja como
-- artefacto del baseline; la fecha real de graduacion la da
-- `ParticipacionPrograma.fechaGraduacionEsperada()`.
