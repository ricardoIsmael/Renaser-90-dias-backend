-- V173 — Las partes y la lista fija de la audiencia elegida de un evento (spec 044, pedido del dueño del 2026-11-06)
--
-- Por qué se toca la base
-- -----------------------
-- Un evento `SELECCION` (V172) tiene una audiencia hecha de partes que se suman: personas por su nombre, los alumnos de un mentor, un grupo, los
-- mentores de la formación, los aprendices de un color del semáforo o el personal (Administración y Alquimista). Hay dos clases de partes y por eso
-- dos tablas:
--
-- 1. `reglas_audiencia_evento`: las partes que se leen EN CADA MOMENTO (un grupo, los alumnos de un mentor, los mentores, todo el personal: quien
--    entra al grupo ve el evento, quien sale deja de verlo) y una fila por cada COLOR elegido con el instante de su foto (`resuelto_en`). El dueño
--    decidió (P1) que el color se mire una vez, al guardar: la foto vive en `invitados_evento` y esta fila recuerda qué color era y cuándo se tomó,
--    para «Volver a calcular» y para decirle a quien creó el evento «Según el semáforo del 6 de noviembre».
--    - `mentor_id` es de quién son «mis alumnos»: se guarda aparte de `eventos.creado_por` porque el evento lo puede editar otra persona (el líder,
--      Administración) y los alumnos tienen que seguir siendo los del mentor que lo creó.
--    - Sin clave única por el CHECK de coherencia: una regla repetida no cambia a quién le llega, y el servicio reemplaza todas las filas del evento
--      al guardar.
--
-- 2. `invitados_evento`: la lista FIJA, una fila por persona y por motivo (`PERSONA`, `PERSONAL` o `COLOR`, con el color que tenía en la foto). Una
--    persona puede estar dos veces (por nombre y por color) y recibe un solo aviso: la aplicación junta. El índice por `participante_id` es el que
--    hace barato listar los eventos de una persona.
--
-- Las dos tablas cuelgan del evento con ON DELETE CASCADE (borrar un evento borra su audiencia) y de `usuarios`/`celulas` también en cascada: una
-- cuenta borrada deja de estar invitada y un grupo borrado deja de ser parte. Nada de esto se le muestra al invitado (FR-016).

SET search_path TO renaser, public;

CREATE TABLE reglas_audiencia_evento (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evento_id    uuid        NOT NULL REFERENCES eventos (id) ON DELETE CASCADE,
    tipo_parte   text        NOT NULL CHECK (tipo_parte IN ('GRUPO', 'MIS_ALUMNOS', 'MENTORES', 'PERSONAL', 'COLOR')),
    grupo_id     uuid        REFERENCES celulas (id) ON DELETE CASCADE,
    mentor_id    uuid        REFERENCES usuarios (id) ON DELETE CASCADE,
    color        text        CHECK (color IN ('VERDE', 'AMARILLO', 'ROJO')),
    resuelto_en  timestamptz,
    creado_en    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT regla_audiencia_coherente CHECK (
        (tipo_parte = 'GRUPO')       = (grupo_id IS NOT NULL) AND
        (tipo_parte = 'MIS_ALUMNOS') = (mentor_id IS NOT NULL) AND
        (tipo_parte = 'COLOR')       = (color IS NOT NULL AND resuelto_en IS NOT NULL)
    )
);

CREATE INDEX reglas_audiencia_evento_evento_idx ON reglas_audiencia_evento (evento_id);

CREATE TABLE invitados_evento (
    evento_id        uuid        NOT NULL REFERENCES eventos (id) ON DELETE CASCADE,
    participante_id  uuid        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    origen           text        NOT NULL CHECK (origen IN ('PERSONA', 'PERSONAL', 'COLOR')),
    color            text        CHECK (color IN ('VERDE', 'AMARILLO', 'ROJO')),
    creado_en        timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (evento_id, participante_id, origen),
    CONSTRAINT invitado_color_coherente CHECK ((origen = 'COLOR') = (color IS NOT NULL))
);

CREATE INDEX invitados_evento_participante_idx ON invitados_evento (participante_id);
