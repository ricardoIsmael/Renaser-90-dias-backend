-- La bienvenida cubre los dias 1..7 del programa de cada persona; el traslado a un grupo estable
-- ocurre el dia 8. Antes eran los dias 1..3 (traslado el dia 4).
--
-- El numero que MANDA en produccion es el default de CODIGO
-- (PoliticaMentoria.DIA_TRASLADO_POR_DEFECTO), porque el traslado cae en el cuando una cohorte no
-- tiene fila de politica propia —y una instalacion nueva arranca sin ninguna—. Esta migracion NO
-- puede cambiar ese comportamiento por si sola; solo deja la BASE diciendo lo mismo que el codigo,
-- para que una fila creada sin especificar el dia nazca ya con 7 dias de bienvenida y no con 3.

ALTER TABLE renaser.politicas_mentoria ALTER COLUMN dia_traslado SET DEFAULT 8;

-- Alinea las filas que hayan quedado con el viejo default. El 4 nunca fue una eleccion del
-- administrador: era el valor por omision. Se sube el `version` para no romper el bloqueo
-- optimista de quien tuviera la fila cargada. (En una instalacion nueva no afecta a nadie: no hay
-- filas todavia.)
UPDATE renaser.politicas_mentoria
SET dia_traslado = 8, version = version + 1, actualizado_en = now()
WHERE dia_traslado = 4;
