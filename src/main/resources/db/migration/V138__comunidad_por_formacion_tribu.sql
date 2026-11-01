-- V138 — La Tribu de cada formación: sus conversaciones y los tipos de comunidad que ofrece (spec 024; D-301)
--
-- Por qué se toca la base
-- -----------------------
-- (1) Cada conversación es de UNA formación: la comunidad general (hoy una sola para todos), el grupo, el soporte de cada aprendiz y los directos. Sin ese dato no se
--     puede dar a cada formación su propia comunidad general ni esconder lo de otra. (2) Administración decide, por formación, qué tipos de comunidad ofrece (general,
--     grupo con mentor, soporte, directos); eso es un dato de la formación.
--
-- Qué se reutiliza y por qué NO hay tablas nuevas
-- -----------------------------------------------
-- · `conversaciones.formacion` es una COLUMNA (como `celulas.formacion`, V125). Se repite en las de grupo (cuya fuente es `celulas.formacion`) a propósito: la lista de
--   chats filtra por formación en UNA consulta sin JOIN, y un grupo no cambia de formación (V125), así que no hay nada que desincronizar. Es la desnormalización
--   mínima justificada por la lectura caliente de la Tribu.
-- · Los tipos de comunidad de cada formación son una COLUMNA de `formaciones` (`tipos_de_comunidad text[]`), la misma justificación que `duraciones_dias`: son 1 a 4
--   valores que se leen y escriben siempre juntos y nadie los une con otra tabla. Una tabla `tipos_de_comunidad` sería una tabla innecesaria.
--
-- Una comunidad general por formación
-- -----------------------------------
-- `conversacion_global_unica_uk` (V1) permitía UNA sola GLOBAL en toda la base. Pasa a UNA POR FORMACIÓN. La que existe hoy queda en la Escuela con su nombre de siempre
-- («Formación Renaser Global»): las comunidades de las otras dos nacen al primer uso, con su nombre.
--
-- Qué pasa con lo que ya existe (FR-002, SC-009)
-- ----------------------------------------------
-- Toda conversación existente es de la Escuela (DEFAULT) y la Escuela ofrece los cuatro tipos que tiene hoy: ninguna persona ve nada distinto.
--
-- El soporte de quien cambia de formación
-- ---------------------------------------
-- La clave del soporte de la Escuela no cambia (`soporte:<aprendiz>`). La de las otras formaciones lleva el código al final (`soporte:<aprendiz>:DIPLOMADO`): así un
-- aprendiz que pasa de formación tiene un soporte nuevo en la nueva y conserva el viejo (sin verse) en la anterior. No hace falta ninguna columna: `clave_directa`
-- ya era la identidad de negocio del soporte (V53/V54) y sigue siendo UNIQUE.

SET search_path TO renaser, public;

ALTER TABLE conversaciones
    ADD COLUMN formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo);

COMMENT ON COLUMN conversaciones.formacion IS
    'La formación a la que pertenece la conversación (V138, spec 024). Un directo es de la formación en que se abrió y solo se ve con las dos personas dentro de ella.';

CREATE INDEX conversaciones_formacion_idx ON conversaciones (formacion, tipo);

DROP INDEX conversacion_global_unica_uk;
CREATE UNIQUE INDEX conversacion_global_unica_uk ON conversaciones (formacion) WHERE tipo = 'GLOBAL';

ALTER TABLE formaciones
    ADD COLUMN tipos_de_comunidad text[] NOT NULL DEFAULT ARRAY['GENERAL', 'GRUPO', 'SOPORTE', 'DIRECTO']
        CHECK (tipos_de_comunidad <@ ARRAY['GENERAL', 'GRUPO', 'SOPORTE', 'DIRECTO']);

COMMENT ON COLUMN formaciones.tipos_de_comunidad IS
    'Los tipos de comunidad PRENDIDOS de la formación (V138, spec 024): GENERAL, GRUPO, SOPORTE y DIRECTO. Apagar uno lo esconde sin borrar sus mensajes; prenderlo los devuelve.';
