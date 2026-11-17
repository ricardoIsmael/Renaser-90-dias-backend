-- V198 — Las notas privadas del mentor sobre un aprendiz (spec 055, FR-023 a FR-028)
--
-- Por qué se toca la base
-- -----------------------
-- El mentor que acompaña hoy a un aprendiz anota lo que habló con él. La nota es privada: la leen quien acompaña hoy al aprendiz
-- (también las de mentores anteriores), el liderazgo de su formación y Administración; el aprendiz no tiene ninguna ruta que la
-- devuelva. No existe en otra tabla: las observaciones del líder (`observaciones_mentor`, V89) son del líder sobre un MENTOR, y los
-- avisos de acompañamiento los escribe el sistema; ninguna sirve para el texto libre de un mentor sobre un aprendiz.
--
-- * `notas_del_mentor`: una fila por nota. La nota sigue al APRENDIZ, no al grupo: si el aprendiz cambia de grupo o de mentor, las
--   notas viajan con él. `dia_del_autor` es el día LOCAL del autor cuando la escribió (regla 02): decide si todavía puede corregirla
--   (solo el mismo día). Ese «mismo día» no es una columna generada porque depende de la zona del autor en ese momento.
--   El texto va recortado y entre 1 y 2000 caracteres: el CHECK mira solo esta fila, así que le toca a la base.
-- * `notas_del_mentor_versiones`: la historia de las correcciones (constitución IV). Cada corrección guarda acá el texto que reemplaza;
--   no hay borrado de notas desde el código.
--
-- `autor_id` es NULLABLE y su FK es RESTRICT (sin cascada, sin SET NULL): la baja de una cuenta (D-243) la gestionan los módulos, no la
-- base (ver `BorradoDeCuentaEnMentoringAdapter`, igual que `asignaciones_celula.actor_id`). Cuando se da de baja a un MENTOR, sus notas NO se
-- borran: el adaptador pone `autor_id` en NULL (la respuesta dice «Cuenta eliminada») y conserva también sus versiones. Cuando se da de baja a
-- un APRENDIZ, se borran las notas sobre él y sus versiones. Decisión del dueño del 17/11/2026.

SET search_path TO renaser, public;

CREATE TABLE notas_del_mentor (
    id            uuid        PRIMARY KEY,
    aprendiz_id   uuid        NOT NULL REFERENCES participantes_programa (usuario_id),
    autor_id      uuid        REFERENCES usuarios (id) ON DELETE RESTRICT,
    texto         text        NOT NULL,
    dia_del_autor date        NOT NULL,
    creado_en     timestamptz NOT NULL,
    corregido_en  timestamptz,
    CONSTRAINT notas_del_mentor_texto_chk
        CHECK (texto = btrim(texto) AND char_length(texto) BETWEEN 1 AND 2000)
);

CREATE INDEX notas_del_mentor_aprendiz_idx ON notas_del_mentor (aprendiz_id, creado_en DESC);
CREATE INDEX notas_del_mentor_autor_idx ON notas_del_mentor (autor_id);

COMMENT ON TABLE notas_del_mentor IS
    'Spec 055: nota privada de quien acompaña sobre un aprendiz. La leen el acompañante vigente, el liderazgo de su formación y Administración; nunca el aprendiz.';
COMMENT ON COLUMN notas_del_mentor.dia_del_autor IS
    'Spec 055 FR-025: el día local del autor al escribirla. Solo ese día el autor puede corregirla.';

CREATE TABLE notas_del_mentor_versiones (
    nota_id        uuid        NOT NULL REFERENCES notas_del_mentor (id),
    texto_anterior text        NOT NULL,
    reemplazado_en timestamptz NOT NULL,
    PRIMARY KEY (nota_id, reemplazado_en)
);

COMMENT ON TABLE notas_del_mentor_versiones IS
    'Spec 055 (principio IV): el texto que cada corrección reemplazó, de una nota de notas_del_mentor.';
