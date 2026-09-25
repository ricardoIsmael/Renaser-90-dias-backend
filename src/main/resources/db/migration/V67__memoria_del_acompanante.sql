-- =====================================================================================
-- Memoria del acompanante: lo que Renasia aprendio de cada persona, y el resumen de lo que venian
-- conversando (D-167).
--
-- QUE PROBLEMA RESUELVE
--
-- El acompanante lee solo los ultimos 10 mensajes (TURNOS_DE_MEMORIA): despues de cinco idas y
-- vueltas olvida quien es la persona, que la complica y como le gusta que la acompanen. En la bateria
-- de 102 preguntas (D-166) respondio de memoria lo que ya no tenia. El dueno pidio que Renasia sea
-- distinta para cada persona, con las mismas reglas para todos (2026-09-25).
--
-- QUE SE GUARDA Y QUE NO
--
-- `recuerdos_renasia`: hechos estables, en tres categorias que eligio el dueno: contexto de vida
-- ("trabaja de noche"), metas y lo que le funciona, y como prefiere el trato. NO se guarda lo
-- emocional ni nada de salud (dato sensible, Ley 29733): lo excluye quien compacta y lo vuelve a
-- filtrar el dominio. Tampoco datos del dia (horas, puntos, pausas): esos cambian y salen siempre de
-- las herramientas.
--
-- `memorias_renasia`: una fila por persona con el resumen corto de lo conversado antes de los
-- ultimos 10 mensajes y `compactado_hasta`, hasta donde ya se leyo la conversacion. El resumen puede
-- faltar (NULL): se borra cuando la persona borra un recuerdo, porque podia nombrarlo, y cuando borra
-- todo. `compactado_hasta` no se borra nunca: si volviera atras, la proxima compactacion releeria los
-- mensajes viejos y devolveria justo lo que la persona acaba de borrar. Al "borrar todo" avanza hasta
-- ese momento: lo conversado antes no vuelve a la memoria.
--
-- Los dos se escriben SOLO desde la compactacion automatica; la persona ve y borra lo suyo desde su
-- perfil (GET/DELETE /api/v1/renasia/memoria). No se le pregunta en cada conversacion: decision del
-- dueno, a cambio de que sea visible y borrable.
--
-- POR QUE NO SE REUSA OTRA TABLA
--
-- `mensajes_renasia` es el texto de la conversacion, que crece sin limite y se pagina: no se le puede
-- pedir al modelo que lo relea entero en cada turno. Esto es lo derivado de ese texto, corto y
-- corregible. `agenda_ocupada` (V64) son tramos horarios que calcula el codigo, otra cosa.
--
-- POR QUE DOS TABLAS
--
-- Los recuerdos se borran de a uno desde el perfil; el resumen y hasta donde se leyo son uno solo
-- por persona y se reemplazan enteros.
--
-- LOS LIMITES
--
-- 300 caracteres por recuerdo y 2000 por resumen, impuestos aca porque se evaluan con la fila
-- (regla 04). Cuantos recuerdos por categoria es regla del dominio (depende de otras filas).
--
-- ON DELETE CASCADE: si se borra la cuenta, su memoria se va con ella.
-- =====================================================================================

CREATE TABLE renaser.recuerdos_renasia (
    id               uuid        PRIMARY KEY,
    participante_id  uuid        NOT NULL REFERENCES renaser.usuarios (id) ON DELETE CASCADE,
    categoria        text        NOT NULL,
    texto            text        NOT NULL,
    creado_en        timestamptz NOT NULL,
    CONSTRAINT recuerdos_renasia_categoria_valida
        CHECK (categoria IN ('CONTEXTO_DE_VIDA', 'METAS_Y_LO_QUE_FUNCIONA', 'PREFERENCIAS_DE_TRATO')),
    CONSTRAINT recuerdos_renasia_texto_corto CHECK (char_length(texto) BETWEEN 1 AND 300)
);

COMMENT ON TABLE renaser.recuerdos_renasia IS
    'Lo que el acompanante aprendio de la persona, por categoria (D-167). Solo lo escribe la compactacion; la persona lo ve y lo borra.';

CREATE INDEX recuerdos_renasia_participante_idx ON renaser.recuerdos_renasia (participante_id);

CREATE TABLE renaser.memorias_renasia (
    participante_id  uuid        PRIMARY KEY REFERENCES renaser.usuarios (id) ON DELETE CASCADE,
    resumen          text,
    compactado_hasta timestamptz NOT NULL,
    actualizado_en   timestamptz NOT NULL,
    CONSTRAINT memorias_renasia_resumen_corto
        CHECK (resumen IS NULL OR char_length(resumen) BETWEEN 1 AND 2000)
);

COMMENT ON TABLE renaser.memorias_renasia IS
    'Resumen de lo conversado con el acompanante antes de los ultimos mensajes, y hasta donde se leyo (D-167). compactado_hasta no retrocede nunca: si no, lo borrado volveria.';
