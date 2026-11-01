-- V144 — Lo que una pregunta del diagnóstico necesita y la tabla de preguntas no tenía (spec 026; D-303)
--
-- Por qué se toca la base
-- -----------------------
-- El diagnóstico es un flujo más del motor de onboarding (`flujo = 'diagnostico'`), pero Administración tiene que poder administrarlo desde la app: ayuda bajo la
-- pregunta, casillas con un máximo, «se muestra solo si», «alimenta» (de qué pregunta nace cada objetivo), limitar una pregunta a ciertas formaciones, y retirar una
-- pregunta que ya tiene respuestas sin perderlas. Nada de eso cabía en `preguntas_onboarding`.
--
-- Por qué no tablas nuevas
-- ------------------------
-- Cada dato es un atributo de la propia pregunta y se lee y escribe junto con ella. `pregunta_padre_id` (que ya existía y nadie usaba) pasa a ser «la pregunta de la que depende
-- esta»; se le suma el valor que la activa. `formaciones` es un arreglo, como `formaciones.duraciones_dias` (V125): 0 a 3 códigos que se leen siempre juntos y no se unen con
-- nada; vacío (NULL) = todas las formaciones. `alimenta` y `precarga` son textos con CHECK, no entidades.
--
-- Por qué el índice único de `alimenta`
-- -------------------------------------
-- «Cada destino admite una sola pregunta» (FR-014): el objetivo de Cuerpo nace de UNA pregunta. Solo cuentan las activas (`retirada_en IS NULL`): una retirada deja libre su destino.
--
-- La cuadrícula de escala es un tipo nuevo del enum: sus filas son `opciones_pregunta` y su respuesta es un `valor_json`.

SET search_path TO renaser, public;

ALTER TYPE tipo_pregunta_onboarding ADD VALUE IF NOT EXISTS 'CUADRICULA';

ALTER TABLE preguntas_onboarding
    ADD COLUMN ayuda              text,
    ADD COLUMN maximo_opciones    smallint    CHECK (maximo_opciones IS NULL OR maximo_opciones >= 1),
    ADD COLUMN se_muestra_si_valor text,
    ADD COLUMN alimenta           text        CHECK (alimenta IS NULL OR alimenta IN
        ('OBJETIVO_CUERPO', 'OBJETIVO_NEGOCIO', 'OBJETIVO_RELACIONES', 'PRIORIDAD_PRINCIPAL', 'PRIMERA_ACCION', 'TRATO_DE_LA_PERSONA')),
    ADD COLUMN formaciones        text[]      CHECK (formaciones IS NULL OR cardinality(formaciones) BETWEEN 1 AND 3),
    ADD COLUMN precarga           text        CHECK (precarga IS NULL OR precarga IN ('NOMBRE_COMPLETO')),
    ADD COLUMN retirada_en        timestamptz;

CREATE UNIQUE INDEX preguntas_onb_alimenta_unica ON preguntas_onboarding (alimenta)
    WHERE alimenta IS NOT NULL AND retirada_en IS NULL;

COMMENT ON COLUMN preguntas_onboarding.pregunta_padre_id IS
    'Si no es NULL, esta pregunta solo se muestra cuando la respuesta de la padre es se_muestra_si_valor (spec 026).';
COMMENT ON COLUMN preguntas_onboarding.formaciones IS
    'NULL = la ven todas las formaciones; si no, solo las de esta lista de códigos (spec 026).';
COMMENT ON COLUMN preguntas_onboarding.retirada_en IS
    'Una pregunta con respuestas no se borra: se retira (deja de mostrarse, se conservan las respuestas) y se puede restaurar (spec 026).';
