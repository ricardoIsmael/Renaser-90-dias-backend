-- V145 — La foto de cada ficha publicada (spec 026; D-303)
--
-- Por qué se toca la base
-- -----------------------
-- Administración edita el diagnóstico en un borrador y lo PUBLICA; las personas ven lo publicado, y quien empezó una ficha la termina con la versión con la que empezó (FR-031).
-- Además, la ficha de una persona debe mostrar cada pregunta «tal como se le hizo» (FR-044) aunque después se edite o se retire. Las filas de `preguntas_onboarding` son el borrador
-- y cambian; hace falta la foto de lo que se publicó.
--
-- Por qué una tabla y por qué un jsonb
-- ------------------------------------
-- Es la única tabla nueva de la spec 026. El contenido es un documento inmutable que solo se lee entero (la persona abre SU versión; la lectura de una ficha usa el texto de SU versión);
-- nunca se consulta por dentro, salvo una pregunta de existencia («¿alguna versión incluyó esta pregunta?», para decidir si se elimina o se retira). Versionar cada pregunta fila por fila
-- exigiría un join por versión en cada lectura. Es append-only (como `participaciones_anteriores`): una versión publicada no se edita; la única edición permitida es el
-- `ON DELETE SET NULL` de quien la publicó cuando se borra su cuenta.

SET search_path TO renaser, public;

CREATE TABLE fichas_publicadas (
    flujo         text        NOT NULL,
    version       integer     NOT NULL CHECK (version > 0),
    contenido     jsonb       NOT NULL CHECK (jsonb_typeof(contenido) = 'object'),
    publicada_por uuid        REFERENCES usuarios (id) ON DELETE SET NULL,
    publicada_en  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (flujo, version)
);

CREATE FUNCTION fichas_publicadas_solo_se_agrega() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.publicada_por IS NULL
       AND ROW(NEW.flujo, NEW.version, NEW.contenido, NEW.publicada_en)
           IS NOT DISTINCT FROM ROW(OLD.flujo, OLD.version, OLD.contenido, OLD.publicada_en) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'fichas_publicadas es append-only: una versión publicada no se edita ni se borra (spec 026)'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER fichas_publicadas_append_only
    BEFORE UPDATE OR DELETE ON fichas_publicadas
    FOR EACH ROW EXECUTE FUNCTION fichas_publicadas_solo_se_agrega();

COMMENT ON TABLE fichas_publicadas IS
    'La foto de cada versión publicada de una ficha administrable (hoy: el flujo diagnostico). Append-only (V145, spec 026).';
