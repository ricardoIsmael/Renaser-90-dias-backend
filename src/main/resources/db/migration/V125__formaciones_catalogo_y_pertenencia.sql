-- V125 — Las tres formaciones (Escuela, Diplomado y Certificación) y a cuál pertenece cada aprendiz y cada grupo (spec 022; D-295)
--
-- Por qué se toca la base
-- -----------------------
-- Hasta hoy Renaser es un solo programa de 90 días. El dueño (2026-10-31) pidió tres formaciones fijas —Escuela Renaser (90 días), Diplomado Renaser
-- (3 meses = 90 días o 1 año = 365 días) y Certificación Renaser (90 días)— con los mismos roles pero aisladas entre sí, y que la duración la configure
-- Administración. Para eso la base necesita: (1) saber cuáles son las formaciones y cuánto pueden durar; (2) a cuál pertenece cada aprendiz y con qué
-- duración; (3) a cuál pertenece cada grupo.
--
-- Por qué no alcanza el modelo actual / qué se reutiliza
-- ------------------------------------------------------
-- · `formaciones` es la única tabla nueva de esta migración: un catálogo de 3 filas fijas (no se crean ni se borran desde la app) con su nombre,
--   descripción y las opciones de duración. Las opciones de duración son una COLUMNA (`duraciones_dias smallint[]`), no otra tabla: son de 1 a 4
--   enteros que se leen y escriben siempre juntos y nadie los une con nada más.
-- · La formación del aprendiz y su duración son dos COLUMNAS de `participantes_programa` (la fila 1:1 con la persona que ya guarda su programa), no una tabla
--   de pertenencia. La duración se guarda en la persona (no se lee del catálogo) porque cambiarla en el catálogo NO debe mover a quien ya empezó (FR-004).
-- · La formación del grupo es una COLUMNA de `celulas`: el grupo es la unidad de aislamiento (chat, semáforo, alcance del mentor) y se filtra en muchas
--   consultas; es la única fuente (no se repite en `cohortes`).
-- · La clave de la formación es el `codigo` ('ESCUELA', …) y no un uuid: son tres filas fijas, el código se lee en cada fila de cada consulta y evita un
--   JOIN para mostrarlo o filtrarlo (misma idea que `audioterapias.semana`: clave natural pura).
--
-- Qué pasa con lo que ya existe (FR-005, SC-002)
-- ----------------------------------------------
-- Todo lo existente es de la Escuela, sin que cambie nada visible: el DEFAULT 'ESCUELA' / 90 deja a cada participante y a cada grupo donde estaban. El
-- DEFAULT se conserva a propósito: la app publicada (1.6.0) y los caminos que todavía no mandan formación siguen creando filas de la Escuela.
--
-- El tope del día
-- ---------------
-- `dia_programa BETWEEN 0 AND 90` era la duración escrita en la base. Pasa a `0 AND duracion_dias`: el día de cada persona se acota con SU duración. Las
-- filas de hoy tienen duración 90, así que el nuevo CHECK se cumple con todas.

SET search_path TO renaser, public;

CREATE TABLE formaciones (
    codigo          text        PRIMARY KEY CHECK (codigo = upper(codigo) AND length(codigo) BETWEEN 3 AND 20),
    nombre          text        NOT NULL CHECK (length(btrim(nombre)) BETWEEN 3 AND 80),
    descripcion     text        NOT NULL DEFAULT '' CHECK (length(descripcion) <= 600),
    activa          boolean     NOT NULL DEFAULT true,
    orden           smallint    NOT NULL,
    duraciones_dias smallint[]  NOT NULL
        CHECK (cardinality(duraciones_dias) BETWEEN 1 AND 4 AND 1 <= ALL (duraciones_dias) AND 365 >= ALL (duraciones_dias)),
    version         integer     NOT NULL DEFAULT 1 CHECK (version > 0),
    actualizado_en  timestamptz NOT NULL DEFAULT now(),
    actualizado_por uuid        REFERENCES usuarios (id) ON DELETE SET NULL
);

COMMENT ON TABLE formaciones IS
    'Las tres formaciones fijas (V125, spec 022): Escuela, Diplomado y Certificación. Se editan nombre, descripción, estado y duraciones; no se crean ni se borran desde la app.';
COMMENT ON COLUMN formaciones.duraciones_dias IS
    'Opciones de duración en días (3 meses = 90, 1 año = 365). Cambiarlas rige solo para quien se acepte después: la duración de cada persona vive en participantes_programa.duracion_dias.';

INSERT INTO formaciones (codigo, nombre, descripcion, orden, duraciones_dias) VALUES
    ('ESCUELA',       'Escuela Renaser',       'El programa de 90 días de Renaser.',                                  1, ARRAY[90]::smallint[]),
    ('DIPLOMADO',     'Diplomado Renaser',     'Formación de 3 meses o de 1 año.',                                    2, ARRAY[90, 365]::smallint[]),
    ('CERTIFICACION', 'Certificación Renaser', 'Certificación de 90 días.',                                           3, ARRAY[90]::smallint[]);

ALTER TABLE participantes_programa
    ADD COLUMN formacion     text     NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo),
    ADD COLUMN duracion_dias smallint NOT NULL DEFAULT 90 CHECK (duracion_dias BETWEEN 1 AND 365),
    DROP CONSTRAINT participantes_programa_dia_programa_check,
    ADD CONSTRAINT participantes_programa_dia_dentro_de_la_duracion CHECK (dia_programa BETWEEN 0 AND duracion_dias);

COMMENT ON COLUMN participantes_programa.formacion IS
    'La formación en la que la persona hace su programa (V125, spec 022). Un aprendiz pertenece a una sola a la vez.';
COMMENT ON COLUMN participantes_programa.duracion_dias IS
    'Duración de SU programa en días (90 o 365), fijada al aceptarla. Es el tope del día y la fecha de graduación; cambiar el catálogo no la mueve.';

CREATE INDEX participantes_programa_formacion_idx ON participantes_programa (formacion);

ALTER TABLE celulas
    ADD COLUMN formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo);

COMMENT ON COLUMN celulas.formacion IS
    'La formación del grupo (V125, spec 022). Su mentor y todos sus integrantes son de esta formación.';

CREATE INDEX celulas_formacion_idx ON celulas (formacion);
