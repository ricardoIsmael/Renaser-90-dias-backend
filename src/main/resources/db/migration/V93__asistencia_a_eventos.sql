-- Asistencia a eventos: quién respondió, su historial de respuestas y la lista que se pasa en el evento
-- (D-256, reglas del dueño del 2026-10-06).
--
-- Problema: quien crea un evento, el Admin, el Alquimista y el Líder de mentores quieren ver quién dijo
-- «Voy» / «No voy» y, el día del evento, pasar lista (A tiempo / Tarde / Ausente). Es «simplemente
-- seguimiento»: no da puntos ni toca coherencia, semáforo ni racha, así que nada de esto lo lee otro
-- módulo. Hoy la base no alcanza por tres lados:
--
-- 1. `confirmaciones_evento` (V1) guarda solo la ÚLTIMA respuesta (PK evento+ocurrencia+usuario, el
--    UPDATE pisa el estado). La hoja «Quién respondió» quiere poder decir «Antes dijo «No voy»», y eso
--    no se puede reconstruir. No se agrega una columna `estado_anterior` a esa tabla: guardaría una sola
--    vuelta atrás, y la tabla la escribe el endpoint de la app instalada (PUT /rsvp), que no se toca.
--    Tabla nueva `historial_confirmaciones_evento`, append-only (el código solo inserta), que se llena
--    en `ConfirmacionService.confirmar` cada vez que la respuesta CAMBIA. Empieza vacía: las respuestas
--    anteriores a esta migración no tienen historia (no se inventa).
--
-- 2. La asistencia real no existe en ningún lado. Tabla nueva `asistencias_evento`, una fila por
--    persona MARCADA en una ocurrencia: `estado` A_TIEMPO o TARDE, quién la marcó y cuándo. **Ausente =
--    sin fila**: así «sin marcar» y «ausente» son lo mismo por construcción (regla del dueño) y no hace
--    falta sembrar a toda la audiencia al abrir la lista. `estado` es text con CHECK y no un enum de
--    Postgres (mismo criterio que V89/V90): agregar un valor no exige ALTER TYPE.
--    No se reusa `confirmaciones_evento`: «dijo que iba» y «vino» son dos datos distintos de la misma
--    persona, y la hoja muestra los dos a la vez («Dijo «Voy» · llegó 20:02»).
--
-- 3. Cerrar la lista: tabla `listas_asistencia_evento`, una fila por ocurrencia CERRADA (`cerrada_en`,
--    `cerrada_por`). Reabrir = borrar la fila. Sin fila = abierta (o todavía no se pasó).
--
-- Borrado de cuentas (V90, D-243):
--    · `usuario_id` (la persona respondió / fue marcada): ON DELETE CASCADE, igual que
--      `confirmaciones_evento.usuario_id`. Son datos de esa persona y se van con ella; además
--      `calendar.BorradoDeCuentaEnCalendarAdapter` los borra explícitamente antes del DELETE de la cuenta.
--    · `marcado_por` y `cerrada_por` (quién pasó lista): NULLables con ON DELETE SET NULL, el trato de las
--      demás columnas de «quién lo hizo» (`eventos.creado_por`, `ajustes_dia_programa.ajustado_por`). La
--      marca es historia del aprendiz, no de quien la puso: sobrevive y pierde el autor.
--    · `evento_id`: ON DELETE CASCADE, como `confirmaciones_evento` (borrar el evento borra todo lo suyo).
--
-- Nombres: «asistencia» (la palabra que usa el dueño), «lista» por «pasar lista». `inicio_ocurrencia` con
-- el mismo significado que en `confirmaciones_evento`: el slot ORIGINAL de la serie, estable aunque la
-- ocurrencia se reprograme.
SET search_path TO renaser, public;

CREATE TABLE historial_confirmaciones_evento (
    id                bigint              GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evento_id         uuid                NOT NULL REFERENCES eventos (id) ON DELETE CASCADE,
    inicio_ocurrencia timestamptz         NOT NULL,
    usuario_id        uuid                NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    estado            estado_confirmacion NOT NULL,
    registrado_en     timestamptz         NOT NULL
);

CREATE INDEX historial_confirmaciones_ocurrencia_idx
    ON historial_confirmaciones_evento (evento_id, inicio_ocurrencia, usuario_id, registrado_en);
CREATE INDEX historial_confirmaciones_usuario_idx ON historial_confirmaciones_evento (usuario_id);

CREATE TABLE asistencias_evento (
    evento_id         uuid        NOT NULL REFERENCES eventos (id) ON DELETE CASCADE,
    inicio_ocurrencia timestamptz NOT NULL,
    usuario_id        uuid        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    estado            text        NOT NULL CHECK (estado IN ('A_TIEMPO', 'TARDE')),
    marcado_por       uuid        REFERENCES usuarios (id) ON DELETE SET NULL,
    marcado_en        timestamptz NOT NULL,
    PRIMARY KEY (evento_id, inicio_ocurrencia, usuario_id)
);

CREATE INDEX asistencias_evento_usuario_idx ON asistencias_evento (usuario_id);
CREATE INDEX asistencias_evento_marcado_por_idx ON asistencias_evento (marcado_por) WHERE marcado_por IS NOT NULL;

CREATE TABLE listas_asistencia_evento (
    evento_id         uuid        NOT NULL REFERENCES eventos (id) ON DELETE CASCADE,
    inicio_ocurrencia timestamptz NOT NULL,
    cerrada_en        timestamptz NOT NULL,
    cerrada_por       uuid        REFERENCES usuarios (id) ON DELETE SET NULL,
    PRIMARY KEY (evento_id, inicio_ocurrencia)
);

CREATE INDEX listas_asistencia_cerrada_por_idx ON listas_asistencia_evento (cerrada_por) WHERE cerrada_por IS NOT NULL;

COMMENT ON TABLE historial_confirmaciones_evento IS
    'Cada cambio de «Voy»/«No voy»/«Quizás» de una persona a una ocurrencia (V93, D-256). Append-only; empieza vacía.';
COMMENT ON TABLE asistencias_evento IS
    'Quién vino a una ocurrencia (A_TIEMPO/TARDE). Ausente = sin fila. Solo seguimiento: no da puntos (V93, D-256).';
COMMENT ON TABLE listas_asistencia_evento IS
    'Ocurrencias cuya lista se cerró. Reabrir = borrar la fila (V93, D-256).';
