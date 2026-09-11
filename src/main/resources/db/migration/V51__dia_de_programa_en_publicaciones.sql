-- El día de programa del AUTOR, congelado en el momento de publicar.
--
-- Por qué una columna y no un cálculo al leer: el Muro mostraba "Día 0" en todas las
-- publicaciones porque el dato no existía en ninguna capa —ni en esta tabla, ni en
-- `WallPostResponse`, ni en el móvil, donde estaba escrito a mano como `dayStreak: 0`.
--
-- Se guarda y no se deriva al vuelo a propósito. Derivarlo desde `participantes_programa`
-- haría que una publicación VIEJA cambiara de día cada vez que se ajusta el programa de su
-- autor (`dias_ajuste_programa` existe justamente para eso). Lo que la publicación cuenta es
-- dónde estaba esa persona ESE día; eso no se re-escribe después.
--
-- Nullable a propósito: una publicación de alguien sin programa activo (staff que nunca lo
-- arrancó) no tiene día, y `NULL` dice eso sin mentir. El móvil oculta la insignia cuando
-- llega nulo, en vez de pintar un cero que no significa nada.
ALTER TABLE renaser.publicaciones_muro
    ADD COLUMN dia_programa smallint;

COMMENT ON COLUMN renaser.publicaciones_muro.dia_programa IS
    'Dia de programa del autor al publicar (1..90). NULL = el autor no tenia programa activo.';

-- Relleno de lo ya publicado. Es la mejor aproximacion disponible y no puede ser exacta: se
-- reconstruye desde `fecha_inicio` y la fecha de la publicacion, sin los ajustes que el
-- programa haya tenido despues. De ahi en adelante el valor se escribe al publicar y ya no
-- se estima.
--
-- La fecha se lleva a America/Lima antes de restar: `creado_en` es timestamptz, y una
-- publicacion de las 20:00 de Lima es 01:00 UTC del dia siguiente. Restar en UTC correria un
-- dia a todo lo publicado despues de las 19:00, que es justo cuando mas se publica.
UPDATE renaser.publicaciones_muro p
SET dia_programa = d.dia
FROM (
    SELECT pm.id,
           ((pm.creado_en AT TIME ZONE 'America/Lima')::date - pp.fecha_inicio + 1) AS dia
    FROM renaser.publicaciones_muro pm
    JOIN renaser.participantes_programa pp ON pp.usuario_id = pm.autor_id
    WHERE pp.fecha_inicio IS NOT NULL
) d
WHERE p.id = d.id
  -- Fuera del rango del programa no se inventa nada: queda NULL y la insignia no se dibuja.
  AND d.dia BETWEEN 1 AND 90;
