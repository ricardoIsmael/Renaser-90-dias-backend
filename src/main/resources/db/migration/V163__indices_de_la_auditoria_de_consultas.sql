-- V163 — Índices de las claves foráneas de auditoría (auditoría de consultas del 2026-11-02, hallazgo H-11)
--
-- Qué problema resuelve
-- ---------------------
-- Cuatro columnas guardan «quién hizo esto» con `REFERENCES usuarios (id) ON DELETE SET NULL` y no tienen índice. Mientras la tabla es chica no se nota, pero cada
-- vez que se borra una cuenta (`PurgarCuentasBajaScheduler`, la baja de una persona) PostgreSQL tiene que encontrar, en cada una de esas tablas, las filas que la
-- nombran para ponerlas en NULL: sin índice es un barrido completo por cuenta borrada.
--
--   · cuotas_de_pago.estado_cambiado_por           (V158)
--   · ajustes_dia_programa.ajustado_por            (V21; V90 la pasó a SET NULL)
--   · estado_onboarding.ficha_asignada_por         (V143)
--   · participaciones_anteriores.trasladado_por    (V127)
--
-- Qué cambia
-- ----------
-- Un índice PARCIAL por columna (`WHERE col IS NOT NULL`): la inmensa mayoría de las filas no tiene a nadie que las haya cambiado a mano, así que el índice queda
-- chico y solo guarda lo que el borrado de una cuenta necesita encontrar. Son índices nuevos: no cambian ningún dato ni ninguna consulta de la app.
--
-- Sin CREATE INDEX CONCURRENTLY: Flyway corre cada script en una transacción y CONCURRENTLY no se puede ahí. Estas tablas son chicas (miles de filas), el
-- índice se arma en milisegundos y el bloqueo de escritura es inapreciable. Idempotente (`IF NOT EXISTS`).
--
-- Lo que NO hace esta migración (pendiente de confirmar con `idx_scan` en producción)
-- ----------------------------------------------------------------------------------
-- La auditoría también señaló tres índices que parecen sobrar (cada uno es una escritura de más en su tabla). NO se borran acá: antes hay que mirar, en producción,
-- `SELECT indexrelname, idx_scan FROM pg_stat_user_indexes WHERE indexrelname IN (...)` y confirmar que valen 0 tras unas semanas de uso:
--   · ranking_apr_lectura_idx        (tipo, fecha, posicion)  — ninguna consulta del código lo usa desde la V139
--   · analisis_80_20_formacion_idx   (formacion)              — ninguna consulta filtra solo por formación
--   · celulas_formacion_idx          (formacion)              — 30 a 100 filas
-- Ver `docs/AUDITORIA_DE_CONSULTAS_2026-11-02.md`.

SET search_path TO renaser, public;

CREATE INDEX IF NOT EXISTS cuotas_de_pago_cambiada_por_idx
    ON cuotas_de_pago (estado_cambiado_por) WHERE estado_cambiado_por IS NOT NULL;

CREATE INDEX IF NOT EXISTS ajustes_dia_programa_ajustado_por_idx
    ON ajustes_dia_programa (ajustado_por) WHERE ajustado_por IS NOT NULL;

CREATE INDEX IF NOT EXISTS estado_onboarding_ficha_asignada_por_idx
    ON estado_onboarding (ficha_asignada_por) WHERE ficha_asignada_por IS NOT NULL;

CREATE INDEX IF NOT EXISTS participaciones_anteriores_trasladado_por_idx
    ON participaciones_anteriores (trasladado_por) WHERE trasladado_por IS NOT NULL;
