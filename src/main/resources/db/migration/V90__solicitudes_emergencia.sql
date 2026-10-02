-- Pedidos de ayuda por emergencia del aprendiz (D-244, pedido del dueño del 2026-10-02).
--
-- Problema: un aprendiz que tuvo un accidente (o algo grave que le impidió seguir) no tiene cómo
-- pedir que lo devuelvan a un día anterior del programa. Hoy se entera el soporte solo si la persona
-- le escribe, y el pedido no queda registrado en ningún lado: no se sabe quién lo pidió, a qué día,
-- ni si alguien lo atendió.
--
-- Por qué una tabla nueva y no reusar otra:
--   · `ajustes_dia_programa` (V21) registra lo que HIZO el Admin, append-only. Esto es lo que PIDIÓ
--     el aprendiz, que puede no aplicarse nunca o aplicarse con otro día. Mezclarlos haría que la
--     bitácora del reloj dijera cosas que no pasaron.
--   · `tickets_soporte` exige asunto, categoría y un mensaje de 10 caracteres o más, y no guarda el
--     día pedido ni el día en que estaba: habría que esconder dos números dentro de un texto libre.
--   · El chat de soporte lleva el aviso (un mensaje del programa), pero un mensaje no tiene estado
--     «abierta / resuelta» y se puede perder entre otros cien.
--
-- No es append-only: la fila nace ABIERTA y se cierra UNA vez (RESUELTA), con quién la cerró y, si
-- el Admin cambió el día, a cuál. Ninguna otra columna cambia después del alta.
--
-- Nombres:
--   · `que_ocurrio`: lo que la persona escribe, 1..280 caracteres. 280 es el tope de
--     `ajustes_dia_programa.motivo`: así quien atiende puede pasarlo entero como motivo del ajuste.
--   · `dia_pedido` / `dia_al_pedir`: el día al que quiere volver y el día que vivía al pedirlo. El
--     segundo es una foto (el reloj sigue corriendo mientras soporte responde); sin él, el mensaje
--     «pide volver al día 12, hoy está en el 20» no se podría reconstruir.
--   · `dia_aplicado`: el día al que la llevó el Admin (NULL si se cerró sin cambiar el día).
--
-- CHECK: lo que se evalúa con la fila. Que `dia_pedido` no pase de `dia_al_pedir` sí entra en la
-- fila; que el aprendiz esté en curso depende de `participantes_programa` y lo impone el dominio.
--
-- Una sola ABIERTA por persona: índice único parcial. El servicio lo revisa antes (409 con un
-- mensaje claro), y el índice cierra la carrera de dos toques simultáneos.
SET search_path TO renaser, public;

CREATE TABLE solicitudes_emergencia (
    id             uuid        PRIMARY KEY,
    aprendiz_id    uuid        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    que_ocurrio    text        NOT NULL CHECK (length(btrim(que_ocurrio)) BETWEEN 1 AND 280),
    dia_pedido     smallint    NOT NULL CHECK (dia_pedido BETWEEN 1 AND 89),
    dia_al_pedir   smallint    NOT NULL CHECK (dia_al_pedir BETWEEN 1 AND 90),
    estado         text        NOT NULL CHECK (estado IN ('ABIERTA', 'RESUELTA')),
    creada_en      timestamptz NOT NULL,
    resuelta_en    timestamptz,
    resuelta_por   uuid        REFERENCES usuarios (id) ON DELETE SET NULL,
    dia_aplicado   smallint    CHECK (dia_aplicado BETWEEN 1 AND 89),
    CONSTRAINT solicitudes_emergencia_dia_coherente CHECK (dia_pedido <= dia_al_pedir),
    CONSTRAINT solicitudes_emergencia_cierre_coherente CHECK (
        (estado = 'ABIERTA' AND resuelta_en IS NULL AND dia_aplicado IS NULL)
        OR (estado = 'RESUELTA' AND resuelta_en IS NOT NULL))
);

CREATE UNIQUE INDEX solicitudes_emergencia_una_abierta_uk
    ON solicitudes_emergencia (aprendiz_id) WHERE estado = 'ABIERTA';

CREATE INDEX solicitudes_emergencia_aprendiz_idx ON solicitudes_emergencia (aprendiz_id, creada_en DESC);

COMMENT ON TABLE solicitudes_emergencia IS
    'Pedidos del aprendiz para volver a un día del programa tras una emergencia (V90, D-244). Nace ABIERTA y se cierra una vez.';
