-- V159 — La identidad de cada formación: sus mensajes de bienvenida, su portada y su imagen (spec 035; D-308)
--
-- Por qué se toca la base
-- -----------------------
-- La bienvenida automática (tres mensajes y la portada de la tarjeta, D-210) y la imagen de los chats eran una sola para todo el programa. Con tres formaciones
-- (Escuela, Diplomado y Certificación, spec 022) una persona del Diplomado recibía «Hoy empiezas tu Formación Renaser» sobre la portada de la Escuela. El dueño pidió
-- (2026-11-02) que cada formación tenga la suya, sin cruzarse. Lo que Administración cambia desde la app tiene que guardar de qué formación es.
--
-- Por qué no una tabla nueva ni columnas en `formaciones`
-- -------------------------------------------------------
-- `cambios_bienvenida` (V73, ampliada en V82) ya es la bitácora append-only de «lo que Administración pone encima del original»: una fila por cambio, lo vigente es la
-- última, volver al original es otra fila, y guarda quién y cuándo. La formación es una COLUMNA más de esa fila, igual que V125 lo hizo con `participantes_programa` y
-- `celulas`. Una tabla aparte por formación perdería la historia y obligaría a copiar lo que ya existe; columnas de imagen en `formaciones` (módulo `users`) harían que
-- `users` guardara rutas de imágenes que `chat` abre, revisa y sirve. Los ORIGINALES de los textos no se copian a la base (la lección de V73): viven en
-- `bienvenida/mensajes.yaml`, que ahora trae una sección por formación.
--
-- La imagen de la formación
-- -------------------------
-- La comunidad general y los grupos sin foto propia de una formación muestran una imagen que Administración elige desde la app. Es la misma idea que la portada y que
-- la carta de la caja (V82): una imagen del chat que se cambia desde la app, con su historia. Es una pieza más de la bitácora (`IMAGEN_DE_LA_FORMACION`), con su propio
-- prefijo de ruta; sumar una pieza es cambiar el CHECK (por eso V73 no usó un ENUM). Sin fila, la formación usa la de la Escuela y, si tampoco, la de la app.
--
-- Qué pasa con lo que ya existe
-- -----------------------------
-- Todo lo guardado hasta hoy es de la Escuela: el DEFAULT 'ESCUELA' deja cada fila donde estaba, así que la bienvenida de la Escuela no cambia ni un byte. El DEFAULT se
-- conserva a propósito: la app anterior (que no manda formación) y los caminos que no la saben siguen escribiendo en la Escuela.
--
-- Idempotente: cada paso se puede repetir sin efecto (ADD COLUMN IF NOT EXISTS, DROP CONSTRAINT IF EXISTS antes de cada ADD, CREATE INDEX IF NOT EXISTS).
SET search_path TO renaser, public;

ALTER TABLE cambios_bienvenida
    ADD COLUMN IF NOT EXISTS formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo);

ALTER TABLE cambios_bienvenida DROP CONSTRAINT IF EXISTS cambios_bienvenida_pieza_check;
ALTER TABLE cambios_bienvenida DROP CONSTRAINT IF EXISTS cambios_bienvenida_portada_ruta_check;
ALTER TABLE cambios_bienvenida DROP CONSTRAINT IF EXISTS cambio_de_texto_sin_portada;
ALTER TABLE cambios_bienvenida DROP CONSTRAINT IF EXISTS cambio_de_portada_sin_texto;
ALTER TABLE cambios_bienvenida DROP CONSTRAINT IF EXISTS carta_de_la_caja_solo_de_la_escuela;

ALTER TABLE cambios_bienvenida
    ADD CONSTRAINT cambios_bienvenida_pieza_check
        CHECK (pieza IN ('SOPORTE_CON_LA_TARJETA', 'SOPORTE_FORMAL', 'GRUPO', 'PORTADA', 'CARTA_CAJA', 'IMAGEN_DE_LA_FORMACION')),
    ADD CONSTRAINT cambios_bienvenida_portada_ruta_check
        CHECK (portada_ruta IS NULL
               OR (char_length(portada_ruta) <= 300
                   AND ((pieza = 'PORTADA' AND portada_ruta LIKE 'bienvenida/portadas/%')
                        OR (pieza = 'CARTA_CAJA' AND portada_ruta LIKE 'caja/cartas/%')
                        OR (pieza = 'IMAGEN_DE_LA_FORMACION' AND portada_ruta LIKE 'formaciones/imagenes/%')))),
    ADD CONSTRAINT cambio_de_texto_sin_portada
        CHECK (pieza IN ('PORTADA', 'CARTA_CAJA', 'IMAGEN_DE_LA_FORMACION') OR portada_ruta IS NULL),
    ADD CONSTRAINT cambio_de_portada_sin_texto
        CHECK (pieza NOT IN ('PORTADA', 'CARTA_CAJA', 'IMAGEN_DE_LA_FORMACION') OR texto IS NULL),
    ADD CONSTRAINT carta_de_la_caja_solo_de_la_escuela
        CHECK (pieza <> 'CARTA_CAJA' OR formacion = 'ESCUELA');

-- «El último cambio de una pieza en una formación» es la única lectura (en cada bienvenida y en cada foto del chat): un salto por el índice, sin ordenar la tabla.
DROP INDEX IF EXISTS cambios_bienvenida_pieza_idx;
CREATE INDEX IF NOT EXISTS cambios_bienvenida_formacion_pieza_idx ON cambios_bienvenida (formacion, pieza, id DESC);

COMMENT ON COLUMN cambios_bienvenida.formacion IS
    'La formación a la que pertenece el cambio (V159, spec 035). Lo anterior a V159 es de la Escuela. La carta de la caja es siempre de la Escuela.';
COMMENT ON TABLE cambios_bienvenida IS
    'Bitacora append-only de los textos, la portada y la imagen de cada formacion que cambia Administracion desde la app (D-210, V159). Lo vigente de cada pieza en una formacion es su ultima fila; texto y portada_ruta en NULL = volvio al original.';
