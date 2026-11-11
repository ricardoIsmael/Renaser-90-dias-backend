-- V188 — Lo marcado sin internet se registra una sola vez (spec 048, FR-008).
--
-- La app guarda en el teléfono lo que la persona hace sin señal y lo manda solo cuando vuelve la conexión. Un corte a mitad
-- de camino hace que el mismo envío llegue dos veces: cada envío trae su `id_cliente` y esta tabla recuerda los que ya
-- llegaron y cómo quedaron (`motivo_sin_puntos`), para devolver el mismo resultado sin tocar nada.
--
-- Se borra con la cuenta (FK con ON DELETE CASCADE): no guarda nada más que el vínculo con el registro.
SET search_path TO renaser, public;

CREATE TABLE IF NOT EXISTS envios_sin_red (
    id_cliente        UUID        PRIMARY KEY,
    participante_id   UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    registro_id       UUID        NOT NULL,
    motivo_sin_puntos TEXT,
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS envios_sin_red_participante_idx ON envios_sin_red (participante_id);
