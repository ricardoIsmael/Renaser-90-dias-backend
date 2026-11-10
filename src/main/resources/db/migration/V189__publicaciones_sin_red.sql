-- V189 — La publicación del Muro hecha sin internet (spec 048, fase 2, FR-020).
--
-- La app guarda la publicación en el teléfono y la manda sola al volver la señal. Esta tabla recuerda, por cada una:
--   * `id_cliente`: el id del envío, para que un corte a mitad de camino no la publique dos veces (una sola por autor);
--   * `hecho_en`: la hora real en que se escribió. La publicación se guarda con la hora de LLEGADA (aparece arriba, D18),
--     pero el hábito «Post diario en comunidad» cuenta el día de la hora real (D17).
--
-- Aparte de `publicaciones_muro` para no tocar el agregado del Muro. Se borra con la publicación y con la cuenta.
SET search_path TO renaser, public;

CREATE TABLE IF NOT EXISTS publicaciones_sin_red (
    autor_id          UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    id_cliente        UUID        NOT NULL,
    publicacion_id    UUID        NOT NULL REFERENCES publicaciones_muro (id) ON DELETE CASCADE,
    hecho_en          TIMESTAMPTZ NOT NULL,
    origen_de_la_hora TEXT        NOT NULL,
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (autor_id, id_cliente)
);

CREATE UNIQUE INDEX IF NOT EXISTS publicaciones_sin_red_publicacion_idx ON publicaciones_sin_red (publicacion_id);
-- Para «¿publicó en el Muro ese día?» por la hora real (PoliticaPostDiarioComunidad).
CREATE INDEX IF NOT EXISTS publicaciones_sin_red_autor_hecho_idx ON publicaciones_sin_red (autor_id, hecho_en);
