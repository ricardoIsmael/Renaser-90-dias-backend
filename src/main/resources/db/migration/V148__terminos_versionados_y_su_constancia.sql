-- V148 — Los términos y condiciones, editables y con versiones, y la constancia de qué versión aceptó cada persona (spec 027; D-304)
--
-- Por qué se toca la base
-- -----------------------
-- Hoy las 23 cláusulas están escritas dentro de la app: cambiar una palabra exige tocar código, publicar una app nueva y esperar a que la gente la instale. Y el
-- servidor solo guarda «aceptó» (`estado_onboarding.terminos_aceptados_en`), no QUÉ texto aceptó. El dueño (31/10) pidió editarlos desde la app, por formación,
-- con versiones, y poder decir qué versión aceptó cada persona. Hacen falta dos cosas que hoy no existen: el texto vigente con su historia, y la constancia.
--
-- Por qué dos tablas y no columnas
-- --------------------------------
-- · `terminos_versiones`: cada versión es una foto del texto (varias por formación, de las que una es el borrador que se edita). No cabe en
--   `estado_onboarding` (una fila por persona) ni en `formaciones` (que es configuración de la formación, no su texto legal). Las cláusulas son un
--   `text[]` ordenado (como `formaciones.duraciones_dias`): se leen y escriben siempre juntas, la numeración sale de la posición y nadie las une con nada.
-- · `aceptaciones_de_terminos`: una fila por aceptación (persona, versión, cuándo, con qué firma). Es append-only a propósito: quien aceptó la versión 1 y luego
--   la 2 figura en las dos; con una sola columna en `estado_onboarding` se perdería qué aceptó antes. `estado_onboarding.terminos_aceptados_en` se sigue
--   escribiendo (la app lo lee para saber si ya pasó por los términos). La firma es la que ya se guarda (`medias_onboarding`): no se duplica.
--
-- Por qué estos nombres: `numero` es el de la versión DENTRO de su formación (1, 2, 3…); el borrador no lo tiene hasta publicarse. Una versión publicada no se
-- edita nunca (el trigger lo impide): se corrige publicando otra.

SET search_path TO renaser, public;

CREATE TABLE terminos_versiones (
    id                    uuid        PRIMARY KEY,
    formacion             text        NOT NULL REFERENCES formaciones (codigo),
    estado                text        NOT NULL CHECK (estado IN ('BORRADOR', 'PUBLICADA')),
    numero                integer     CHECK (numero > 0),
    titulo                text        NOT NULL CHECK (length(btrim(titulo)) BETWEEN 1 AND 120),
    introduccion          text        NOT NULL DEFAULT '' CHECK (length(introduccion) <= 2000),
    clausulas             text[]      NOT NULL CHECK (cardinality(clausulas) BETWEEN 1 AND 100),
    texto_casilla         text        NOT NULL CHECK (length(btrim(texto_casilla)) BETWEEN 1 AND 200),
    nota                  text        CHECK (nota IS NULL OR length(nota) <= 500),
    pide_nueva_aceptacion boolean     NOT NULL DEFAULT false,
    base_numero           integer,
    publicada_en          timestamptz,
    publicada_por         uuid        REFERENCES usuarios (id) ON DELETE SET NULL,
    creada_en             timestamptz NOT NULL DEFAULT now(),
    actualizada_en        timestamptz NOT NULL DEFAULT now(),
    actualizada_por       uuid        REFERENCES usuarios (id) ON DELETE SET NULL,
    CONSTRAINT terminos_publicada_con_numero CHECK ((estado = 'PUBLICADA') = (numero IS NOT NULL AND publicada_en IS NOT NULL))
);

CREATE UNIQUE INDEX terminos_versiones_numero_uk ON terminos_versiones (formacion, numero) WHERE estado = 'PUBLICADA';
CREATE UNIQUE INDEX terminos_versiones_un_borrador_uk ON terminos_versiones (formacion) WHERE estado = 'BORRADOR';

CREATE FUNCTION terminos_versiones_publicada_no_se_edita() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    -- Una versión publicada es una constancia: no se edita ni se borra. La única edición que se deja pasar es la que no toca el texto: el `ON DELETE SET NULL` de quien la
    -- publicó o la editó cuando se borra su cuenta. (Las aceptaciones la referencian: borrarla dejaría constancias sin texto.)
    IF OLD.estado = 'PUBLICADA' THEN
        IF TG_OP = 'UPDATE'
           AND ROW(NEW.id, NEW.formacion, NEW.estado, NEW.numero, NEW.titulo, NEW.introduccion, NEW.clausulas, NEW.texto_casilla, NEW.nota,
                   NEW.pide_nueva_aceptacion, NEW.publicada_en)
              IS NOT DISTINCT FROM
              ROW(OLD.id, OLD.formacion, OLD.estado, OLD.numero, OLD.titulo, OLD.introduccion, OLD.clausulas, OLD.texto_casilla, OLD.nota,
                  OLD.pide_nueva_aceptacion, OLD.publicada_en) THEN
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'terminos_versiones: una versión publicada no se edita ni se borra (spec 027)' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER terminos_versiones_constancia BEFORE UPDATE OR DELETE ON terminos_versiones
    FOR EACH ROW EXECUTE FUNCTION terminos_versiones_publicada_no_se_edita();

COMMENT ON TABLE terminos_versiones IS
    'Versiones de los términos y condiciones de cada formación (V148, spec 027). Una BORRADOR por formación como máximo; las PUBLICADAS son inmutables. La vigente es la de mayor número.';

CREATE TABLE aceptaciones_de_terminos (
    id           uuid        PRIMARY KEY,
    usuario_id   uuid        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    version_id   uuid        NOT NULL REFERENCES terminos_versiones (id),
    aceptada_en  timestamptz NOT NULL DEFAULT now(),
    firma_media_id bigint    REFERENCES medias_onboarding (id) ON DELETE SET NULL
);

CREATE INDEX aceptaciones_de_terminos_usuario_idx ON aceptaciones_de_terminos (usuario_id, aceptada_en DESC);
CREATE INDEX aceptaciones_de_terminos_version_idx ON aceptaciones_de_terminos (version_id);

COMMENT ON TABLE aceptaciones_de_terminos IS
    'Constancia de qué versión de los términos aceptó cada persona, cuándo y con qué firma (V148, spec 027). Solo se agregan filas; se borran con la cuenta.';
