-- V127 — Lo que quedó de una formación que la persona ya cursó (spec 022; D-295)
--
-- Por qué se toca la base
-- -----------------------
-- Cuando un aprendiz termina su formación, Administración puede pasarlo a otra. La fila de `participantes_programa` es 1:1 con la persona y se REINICIA
-- (día 0, sin grupo, sin Día 1 elegido): sin esta tabla la historia de lo que cursó se perdería. Aquí queda una foto: cuál formación, con qué duración,
-- desde y hasta cuándo, hasta qué día llegó, con cuántos puntos, y cómo terminó (terminó, o Administración la dio por terminada con un motivo escrito).
--
-- Por qué no alcanza el modelo actual
-- -----------------------------------
-- Ninguna columna de `participantes_programa` puede guardar «lo que fue» (es la participación actual), y `ajustes_dia_programa` registra otra cosa (correcciones
-- del reloj dentro de una misma participación). Los registros de hábito, evidencias, publicaciones y el ledger de puntos NO se tocan en un traslado: siguen
-- donde estaban, así que no hace falta copiarlos.
--
-- Es append-only (como `consultas_de_ficha`): una participación anterior no se edita (la única excepción es el `ON DELETE SET NULL` de quien hizo el
-- traslado cuando se borra su cuenta) y solo se borra junto con la cuenta de la persona a quien pertenece (`ON DELETE CASCADE`).

SET search_path TO renaser, public;

CREATE TABLE participaciones_anteriores (
    id             uuid         PRIMARY KEY,
    usuario_id     uuid         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    formacion      text         NOT NULL REFERENCES formaciones (codigo),
    duracion_dias  smallint     NOT NULL CHECK (duracion_dias BETWEEN 1 AND 365),
    fecha_inicio   date,
    fecha_fin      date         NOT NULL,
    dia_final      smallint     NOT NULL CHECK (dia_final BETWEEN 0 AND 365),
    resultado      text         NOT NULL CHECK (resultado IN ('TERMINADA', 'DADA_POR_TERMINADA')),
    motivo         text         CHECK (motivo IS NULL OR length(btrim(motivo)) BETWEEN 3 AND 500),
    puntos_liga    integer,
    coherencia     numeric(5,2),
    racha_maxima   smallint,
    trasladado_a   text         NOT NULL REFERENCES formaciones (codigo),
    trasladado_por uuid         REFERENCES usuarios (id) ON DELETE SET NULL,
    creado_en      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT participaciones_anteriores_motivo_si_se_da_por_terminada
        CHECK (resultado = 'TERMINADA' OR motivo IS NOT NULL)
);

CREATE INDEX participaciones_anteriores_usuario_idx ON participaciones_anteriores (usuario_id, creado_en DESC);

CREATE FUNCTION participaciones_anteriores_solo_se_agrega() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    -- Única edición que se deja pasar: el `ON DELETE SET NULL` de `trasladado_por` cuando se borra la cuenta de quien hizo el traslado. Sin esto,
    -- borrar la cuenta de una persona de Administración fallaría mientras exista algún traslado hecho por ella. Ninguna otra columna cambia.
    IF TG_OP = 'UPDATE' AND NEW.trasladado_por IS NULL
       AND ROW(NEW.id, NEW.usuario_id, NEW.formacion, NEW.duracion_dias, NEW.fecha_inicio, NEW.fecha_fin, NEW.dia_final, NEW.resultado,
               NEW.motivo, NEW.puntos_liga, NEW.coherencia, NEW.racha_maxima, NEW.trasladado_a, NEW.creado_en)
          IS NOT DISTINCT FROM
          ROW(OLD.id, OLD.usuario_id, OLD.formacion, OLD.duracion_dias, OLD.fecha_inicio, OLD.fecha_fin, OLD.dia_final, OLD.resultado,
               OLD.motivo, OLD.puntos_liga, OLD.coherencia, OLD.racha_maxima, OLD.trasladado_a, OLD.creado_en) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'participaciones_anteriores es append-only: no se edita ni se borra (spec 022)'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER participaciones_anteriores_append_only
    BEFORE UPDATE ON participaciones_anteriores
    FOR EACH ROW EXECUTE FUNCTION participaciones_anteriores_solo_se_agrega();

CREATE TRIGGER participaciones_anteriores_sin_truncate
    BEFORE TRUNCATE ON participaciones_anteriores
    FOR EACH STATEMENT EXECUTE FUNCTION participaciones_anteriores_solo_se_agrega();

COMMENT ON TABLE participaciones_anteriores IS
    'Historial de las formaciones que una persona ya cursó (V127, spec 022). Append-only: se escribe al trasladarla a otra formación. El borrado de la cuenta sí arrastra sus filas (ON DELETE CASCADE).';
