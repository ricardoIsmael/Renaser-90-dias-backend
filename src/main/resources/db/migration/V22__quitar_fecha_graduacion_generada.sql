-- Saca `participantes_programa.fecha_graduacion_esperada` (D-82).
--
-- Era `GENERATED ALWAYS AS (fecha_inicio + 90) STORED` desde el baseline. Desde V20 el
-- reloj descuenta `dias_ajuste_programa`, asi que esa formula quedo INCOMPLETA: a un
-- aprendiz que viajo una semana y fue devuelto al dia 34, la columna le sigue diciendo que
-- se gradua en la fecha original, siete dias antes de lo que realmente le corresponde.
--
-- Por que borrarla y no arreglarla: una columna generada de Postgres solo puede depender
-- de columnas de su propia fila, lo cual alcanzaria (`fecha_inicio + 90 +
-- dias_ajuste_programa`), pero el problema de fondo es otro -- tener DOS fuentes para el
-- mismo dato es la forma en que estas cosas se vuelven a desincronizar. La verdad la da
-- `ParticipacionPrograma.fechaGraduacionEsperada()`, un metodo del dominio que ya
-- contempla el ajuste y esta cubierto por tests.
--
-- Es seguro: NINGUNA query del backend la lee. Verificado sobre todo el repo -- Hibernate
-- ni siquiera la mapea (`ParticipacionProgramaJpaEntity` la excluye a proposito, porque
-- Postgres rechaza cualquier INSERT/UPDATE que mencione una columna generada), y el unico
-- consumidor del concepto es el metodo de dominio de arriba, via
-- `ActivarProgramaResponse`. No hay vista, indice ni constraint que dependa de ella.
--
-- Es DESTRUCTIVA e irreversible en el sentido de que borra una columna. No pierde
-- informacion: al ser generada, su contenido es 100% derivable de `fecha_inicio`, que
-- sigue estando. Recrearla es un ALTER de una linea.
--
-- Cada migracion corre en su propia conexion (mismo preambulo que V13, V18, V20 y V21).
SET search_path TO renaser, public;

ALTER TABLE participantes_programa
    DROP COLUMN fecha_graduacion_esperada;
