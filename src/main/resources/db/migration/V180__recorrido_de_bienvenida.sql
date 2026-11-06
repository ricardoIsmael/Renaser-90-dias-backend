-- Spec 045 (D-330): el recorrido de bienvenida que lleva a la persona hasta su primer post en el Muro.
--
-- Una fila por persona con el paso en el que va (1 a 5) y, al terminar, cuándo y cómo terminó. Sin fila = todavía no empezó.
-- Lo recuerda el servidor y no el teléfono: cambiar de celular no lo vuelve a mostrar (como el arranque guiado).
--
-- Decisión del dueño D2 (06/11): el recorrido sale SOLO a las cuentas nuevas. Por eso cada cuenta que ya existe queda marcada
-- como terminada con `CUENTA_ANTERIOR`: no se le muestra sola (la puede ver desde Yo → Ajustes, eso no pasa por aquí).
SET search_path TO renaser, public;

CREATE TABLE recorrido_de_bienvenida (
    usuario_id     uuid PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    paso           smallint    NOT NULL DEFAULT 1 CHECK (paso BETWEEN 1 AND 5),
    terminado_en   timestamptz,
    como           varchar(20) CHECK (como IN ('PUBLICO', 'LISTO', 'SALTO', 'CUENTA_ANTERIOR')),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    CHECK ((terminado_en IS NULL) = (como IS NULL))
);

COMMENT ON TABLE recorrido_de_bienvenida IS
    'Spec 045: en qué paso va cada persona del recorrido de bienvenida y cómo lo terminó (publicó, listo, saltó, cuenta anterior).';

INSERT INTO recorrido_de_bienvenida (usuario_id, paso, terminado_en, como)
SELECT id, 5, now(), 'CUENTA_ANTERIOR' FROM usuarios;

-- Spec 045, D5 y D8 (06/11): la pestaña «Training» se llama «Entrenar». Las descripciones de los hábitos que la nombraban.
UPDATE habitos SET descripcion = regexp_replace(descripcion, '\mTraining\M', 'Entrenar', 'g')
 WHERE descripcion ~ '\mTraining\M';
