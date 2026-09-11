-- Un Código Renaser por hora y por persona.
--
-- Hasta acá `registros_radar` era un log libre: tantas filas como quisiera el cliente. Eso era
-- correcto mientras el Código Renaser no tenía momento; desde que son doce franjas fijas —una por
-- hora en punto, días 1 a 7— dos filas en la misma hora no son dos registros, son el mismo
-- registro duplicado por un doble toque o por dos dispositivos a la vez.
--
-- El caso de uso ya lo evita (si el último registro cae en la hora en curso, lo devuelve en vez de
-- crear otro), pero eso es una comprobación y no una garantía: entre leer y escribir hay una
-- ventana. La garantía solo puede vivir acá.
--
-- Se trunca en UTC y no en America/Lima porque un índice necesita una expresión IMMUTABLE, y
-- `AT TIME ZONE 'America/Lima'` no lo es. Da igual para el caso real: Lima es UTC-5 exacto, así
-- que el corte de hora UTC cae en el mismo instante que el corte de hora local. Solo se despegaría
-- en zonas con desfase de media hora (India, Nepal), donde el índice partiría la hora en dos
-- mitades — más permisivo, nunca más estricto, así que no bloquea a nadie de forma incorrecta.

-- Primero se limpia lo que ya está duplicado, o el índice no se puede crear. Se conserva el
-- registro MÁS ANTIGUO de cada hora: es el que la persona escribió de verdad; los que vienen
-- detrás son el reenvío.
DELETE FROM renaser.registros_radar r
USING renaser.registros_radar otro
WHERE r.participante_id = otro.participante_id
  AND date_trunc('hour', r.creado_en AT TIME ZONE 'UTC') = date_trunc('hour', otro.creado_en AT TIME ZONE 'UTC')
  AND (otro.creado_en < r.creado_en OR (otro.creado_en = r.creado_en AND otro.id < r.id));

CREATE UNIQUE INDEX radar_un_registro_por_hora
    ON renaser.registros_radar (participante_id, date_trunc('hour', creado_en AT TIME ZONE 'UTC'));
