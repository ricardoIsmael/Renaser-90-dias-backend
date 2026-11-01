-- V134 — Cada formación tiene sus categorías de hábito y su catálogo de hábitos (spec 023; D-300)
--
-- Por qué se toca la base
-- -----------------------
-- Hasta hoy hay UN catálogo de hábitos y CUATRO categorías fijas (`categorias_habito`: CUERPO, MENTE, CONSCIENCIA, ESPIRITU). El dueño (2026-10-31)
-- pidió que cada formación (Escuela, Diplomado, Certificación; V125) configure SUS categorías y SUS hábitos, y que cada persona vea solo los de la suya.
-- Para eso la base necesita saber de qué formación es cada categoría y cada hábito del catálogo, y dejar de exigir que el título y la clave funcional
-- sean únicos en todo el sistema (un hábito copiado a otra formación lleva el mismo título y, si es de flujo propio, la misma `clave_sistema`).
--
-- Qué se reutiliza y qué se agrega (nada de tablas nuevas)
-- --------------------------------------------------------
-- · `categorias_habito` ya es el catálogo de categorías (clave, nombre, orden, activa): gana la formación y el ícono. La clave sigue siendo la llave primaria
--   y las cuatro de siempre conservan la suya (CUERPO…), que la app y otros módulos ya leen; una categoría nueva toma una clave `<FORMACION>_<NOMBRE>`.
-- · `habitos` ya guarda el catálogo (`ambito = 'SISTEMA'`) y los hábitos propios (`PERSONAL`): gana la formación SOLO para los del catálogo. Un hábito propio
--   es de la persona y no tiene formación (sigue a la persona: si su categoría no existe en la formación vigente, la app lo agrupa en «Mis hábitos»).
-- · La formación del hábito se podría derivar de la de su categoría, pero hacen falta DOS unicidades que dependen de ella (título y clave funcional por
--   formación) y un índice único no cruza tablas. Para que la copia no pueda contradecirse, la llave foránea compuesta
--   `(formacion, categoria_clave) → categorias_habito (formacion, clave)` obliga a que un hábito esté en una categoría de SU formación (FR-002).
--
-- Qué pasa con lo que ya existe (FR-004, SC-003)
-- ----------------------------------------------
-- Las cuatro categorías y todo el catálogo son de la Escuela: el DEFAULT 'ESCUELA' de las categorías y el UPDATE de los hábitos del catálogo los dejan donde
-- estaban, con el mismo nombre, orden, regla, horario y guía. Los registros, las preferencias y los hábitos propios no se tocan. Diplomado y Certificación
-- arrancan sin categorías ni hábitos (decisión del dueño): Administración los arma antes de que entre el primer participante.
--
-- Íconos de las categorías
-- ------------------------
-- `icono` es el nombre de uno de los íconos de línea que la app ya dibuja (lista cerrada en el dominio, `IconoDeCategoria`). Las cuatro de hoy ya tenían su
-- dibujo escrito en la app (activity, brain, heart, sparkles); aquí pasa a ser un dato para que el servidor se lo diga a la app sin publicar otra versión.

SET search_path TO renaser, public;

-- 1) Categorías: formación e ícono ----------------------------------------------------------------------------------------------------------------------
ALTER TABLE categorias_habito
    ADD COLUMN formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo),
    ADD COLUMN icono     text NULL CHECK (icono IS NULL OR length(icono) BETWEEN 2 AND 40);

UPDATE categorias_habito SET icono = CASE clave
    WHEN 'CUERPO'      THEN 'activity'
    WHEN 'MENTE'       THEN 'brain'
    WHEN 'CONSCIENCIA' THEN 'heart'
    WHEN 'ESPIRITU'    THEN 'sparkles'
    ELSE icono END;

-- Hace falta para la llave foránea compuesta de `habitos`; `clave` ya es única, así que no agrega ninguna restricción real.
ALTER TABLE categorias_habito ADD CONSTRAINT categorias_habito_formacion_clave_uk UNIQUE (formacion, clave);

-- El nombre es único DENTRO de la formación sin distinguir mayúsculas ni tildes (FR-003); el mismo nombre en otra formación es válido.
-- Misma normalización que `habitos_titulo_sistema_uk` (V19).
CREATE UNIQUE INDEX categorias_habito_nombre_por_formacion_uk
    ON categorias_habito (formacion, lower(translate(nombre, 'ÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑÇáàäâéèëêíìïîóòöôúùüûñç',
                                                              'AAAAEEEEIIIIOOOOUUUUNCaaaaeeeeiiiioooouuuunc')));

-- La app lista las categorías activas de una formación en su orden.
CREATE INDEX categorias_habito_formacion_orden_idx ON categorias_habito (formacion, activa, orden);

-- 2) Hábitos del catálogo: formación -------------------------------------------------------------------------------------------------------------------
-- El DEFAULT 'ESCUELA' se conserva a propósito (igual que en V125): la app publicada y los caminos que todavía no mandan formación siguen creando
-- hábitos de la Escuela. El código escribe NULL en un hábito propio, que no es de ninguna formación; la base no lo exige para no romper a quien
-- inserta a mano, pero ninguna consulta de un hábito propio lee la columna.
ALTER TABLE habitos
    ADD COLUMN formacion text NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo);

UPDATE habitos SET formacion = 'ESCUELA' WHERE ambito = 'SISTEMA';

-- Todo hábito del catálogo es de UNA formación (FR-001).
ALTER TABLE habitos
    ADD CONSTRAINT habito_del_catalogo_con_formacion CHECK (ambito <> 'SISTEMA' OR formacion IS NOT NULL);

-- Un hábito del catálogo está en una categoría de su misma formación (FR-002). Con `formacion` nula (un hábito propio) la llave no se evalúa y vale solo
-- la llave simple de siempre (`categoria_clave → categorias_habito.clave`).
ALTER TABLE habitos
    ADD CONSTRAINT habitos_categoria_de_su_formacion_fk
        FOREIGN KEY (formacion, categoria_clave) REFERENCES categorias_habito (formacion, clave);

-- 3) Unicidad por formación ----------------------------------------------------------------------------------------------------------------------------
-- El título del catálogo era único en todo el sistema (V19); ahora lo es dentro de su formación (FR-006). Misma normalización.
DROP INDEX IF EXISTS habitos_titulo_sistema_uk;
CREATE UNIQUE INDEX habitos_titulo_sistema_uk
    ON habitos (formacion, lower(translate(titulo, 'ÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑÇáàäâéèëêíìïîóòöôúùüûñç',
                                                    'AAAAEEEEIIIIOOOOUUUUNCaaaaeeeeiiiioooouuuunc')))
 WHERE ambito = 'SISTEMA';

-- La identidad funcional (`WAKE_UP`, `PASTILLA_RENACER`, `DAILY_KM`…) era única en todo el sistema. Un hábito de flujo propio copiado a otra formación
-- conserva su clave para que su flujo funcione igual (FR-005), así que pasa a ser única por formación. Los hábitos propios no tienen clave.
ALTER TABLE habitos DROP CONSTRAINT habitos_clave_sistema_key;
CREATE UNIQUE INDEX habitos_clave_sistema_por_formacion_uk ON habitos (formacion, clave_sistema) WHERE clave_sistema IS NOT NULL;

-- El catálogo de una formación, en su orden (lo que lee la generación diaria y «Hábitos del programa»).
CREATE INDEX habitos_formacion_catalogo_idx ON habitos (formacion, activo, orden) WHERE ambito = 'SISTEMA';

COMMENT ON COLUMN categorias_habito.formacion IS 'Formación dueña de la categoría (V134, spec 023). Las cuatro de siempre son de la ESCUELA.';
COMMENT ON COLUMN categorias_habito.icono IS 'Nombre de un ícono de línea de la app (lista cerrada en IconoDeCategoria). Nulo = ícono genérico.';
COMMENT ON COLUMN habitos.formacion IS 'Formación dueña del hábito del catálogo (V134, spec 023); el código la escribe nula en un hábito propio, que sigue a su dueño.';
