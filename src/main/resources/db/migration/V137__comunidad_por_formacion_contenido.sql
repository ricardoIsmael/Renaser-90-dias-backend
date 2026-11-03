-- V137 — El contenido de la Comunidad es de una formación: Muro, testimonios, eventos y cursos (spec 024; D-301)
--
-- Por qué se toca la base
-- -----------------------
-- El dueño (2026-10-31) pidió que cada formación (Escuela, Diplomado, Certificación; V125) tenga SU comunidad y que lo de una sea invisible para las otras. Hasta hoy el
-- Muro, los testimonios, los eventos y los cursos son un único espacio sin dueño: cualquier lectura devuelve todo. Para aislarlos hace falta saber de qué formación es
-- cada pieza.
--
-- Qué se reutiliza y por qué NO hay tablas nuevas
-- -----------------------------------------------
-- · La formación es UNA COLUMNA en la tabla que ya guarda cada cosa (`formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo)`), la misma forma que
--   V125 usó en `participantes_programa` y `celulas`. La clave de la formación es su código (3 filas fijas): se lee en cada fila de cada consulta y evita un JOIN.
-- · Lo que cuelga de una de estas cuatro NO lleva columna propia: los comentarios, las reacciones y las fotos son de la publicación (`publicacion_id`); las secciones,
--   lecciones, recursos y el progreso son del curso; las respuestas «Voy», los recordatorios y la asistencia son del evento. Repetir la formación ahí sería duplicar un
--   dato derivable (regla 04): se desincronizaría en cuanto alguien corrija una.
-- · `categorias_muro` NO cambia: las categorías son las mismas en las tres formaciones y no llevan datos de personas (decisión del dueño). `niveles_membresia` tampoco.
--
-- Qué pasa con lo que ya existe (FR-002, SC-009)
-- ----------------------------------------------
-- Todo lo existente es de la Escuela, sin cambiar de valor, autor, fecha ni orden: el DEFAULT 'ESCUELA' lo deja ahí. El DEFAULT se conserva a propósito: la app
-- publicada (1.6.0) y los caminos que todavía no mandan formación siguen creando contenido de la Escuela.
--
-- Testimonios
-- -----------
-- `testimonios.formacion` es NOT NULL. La spec preveía un testimonio «sin formación» (el formulario sin cuenta), pero ese camino se cerró el 2026-09-18 (hoy solo ADMIN
-- y ALCHEMIST crean o promueven): todo testimonio tiene formación desde que nace — la que elige Administración, o la de la publicación que promueve.
--
-- Cursos con más de 90 días
-- -------------------------
-- `cursos.dia_desbloqueo` y `secciones_curso.dia_desbloqueo` tenían el 90 escrito en un CHECK. El Diplomado de 1 año llega al día 365, y un curso suyo puede abrirse en
-- el día 200. El tope pasa a 365; el de cada persona lo impone su propia duración (`participantes_programa.duracion_dias`).

SET search_path TO renaser, public;

ALTER TABLE publicaciones_muro
    ADD COLUMN formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo);

COMMENT ON COLUMN publicaciones_muro.formacion IS
    'La formación en cuyo Muro se publicó (V137, spec 024). No se mueve: una publicación no cambia de formación, aunque su autor sí.';

-- El feed lista por fecha dentro de UNA formación: el índice del feed (creado_en DESC) sigue sirviendo a la cola de ocultas; este sirve a la lectura normal.
CREATE INDEX muro_formacion_feed_idx ON publicaciones_muro (formacion, creado_en DESC) WHERE NOT oculta;

ALTER TABLE testimonios
    ADD COLUMN formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo);

COMMENT ON COLUMN testimonios.formacion IS
    'La formación de la persona que lo da (V137, spec 024): se ve solo ahí. Un testimonio promovido desde el Muro queda en la formación de su publicación.';

CREATE INDEX testimonios_formacion_idx ON testimonios (formacion, destacado, creado_en DESC);

ALTER TABLE eventos
    ADD COLUMN formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo);

COMMENT ON COLUMN eventos.formacion IS
    'La formación del evento (V137, spec 024): su audiencia (todos, nivel, curso, roles, grupo) se resuelve dentro de ella. No se mueve: para repetirlo en otra se copia.';

CREATE INDEX eventos_formacion_idx ON eventos (formacion, estado, inicia_en);

ALTER TABLE cursos
    ADD COLUMN formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo);

COMMENT ON COLUMN cursos.formacion IS
    'La formación a la que pertenece el curso (V137, spec 024). Quien es de otra formación no lo ve ni como «bloqueado». Para repetirlo en otra se copia.';

CREATE INDEX cursos_formacion_idx ON cursos (formacion, orden);

ALTER TABLE cursos
    DROP CONSTRAINT cursos_dia_desbloqueo_check,
    ADD CONSTRAINT cursos_dia_desbloqueo_check CHECK (dia_desbloqueo BETWEEN 0 AND 365);

ALTER TABLE secciones_curso
    DROP CONSTRAINT secciones_curso_dia_desbloqueo_check,
    ADD CONSTRAINT secciones_curso_dia_desbloqueo_check CHECK (dia_desbloqueo BETWEEN 0 AND 365);
