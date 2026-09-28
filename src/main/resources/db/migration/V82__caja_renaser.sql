-- ============================================================================
-- Caja Renaser (D-219, 2026-09-28): el Admin lleva la caja entera dentro de la app —aprobar,
-- armar, enviar, problema, reenvío— y el aprendiz ve en qué va y confirma que la recibió.
-- Spec: docs/specs/CAJA_RENASER.md.
--
-- QUE PROBLEMA RESUELVE
-- ---------------------
-- Hoy la caja se lleva a mano: se revisa quién terminó la Fase 1, se pide la dirección por
-- WhatsApp, se arma, se envía y se pregunta si llegó. No queda registro de quién la armó, con qué
-- código se envió ni si hubo que reenviarla.
--
-- POR QUE NO SE CREA NINGUNA TABLA
-- --------------------------------
-- Regla del dueño para esta función: «no se crean tablas; se reutiliza lo que existe». El módulo
-- `onboarding` ya es el motor genérico de formularios (V10, V41), y la caja entra en él como un
-- flujo más, `caja_renaser`:
--   * Los datos de envío YA están en la Ficha Inicial (`respuestas_onboarding` de `ficha_inicial`:
--     full_name, whatsapp, country, city, district, address_reference, identity_document). No se
--     copian: se leen de ahí, así un cambio en la ficha vale en el acto (la lección de V22).
--   * Lo que la ficha no pregunta (otra dirección, otro número, quién recibe, referencias, provincia)
--     son cinco preguntas opcionales nuevas del flujo, sección `destino`.
--   * El contenido de la caja es UNA pregunta `SELECCION_MULTIPLE` (`caja_contenido`): cada elemento
--     es una fila de `opciones_pregunta`. Editar la lista = reescribir sus opciones; el checklist de
--     cada caja es la respuesta del aprendiz a esa pregunta (`valor_json`, array de valores).
--   * Cada paso con fecha es una fila de `etapas_onboarding_completadas` (V41, la marca por flujo),
--     con `flujo = 'caja:<n>:<PASO>'` (n = número de envío; un reenvío es n + 1). La PK
--     (usuario_id, flujo) hace que el doble toque de «Enviar» choque en vez de duplicar el paso.
--   * El fondo de la carta con el nombre es una pieza más de la bitácora de la bienvenida
--     (`cambios_bienvenida`, V73): `CARTA_CAJA`, con su imagen bajo `caja/cartas/`.
--
-- POR QUE DOS COLUMNAS NUEVAS EN `etapas_onboarding_completadas`
-- -------------------------------------------------------------
-- `marcada_por`: sin ella no queda qué Admin armó o envió la caja (el mismo dato que
--   `cambios_bienvenida.cambiado_por`). ON DELETE SET NULL por lo mismo que allá: borrar la cuenta
--   de quien marcó no puede fallar por esta tabla ni borrar el paso; se pierde solo el quién.
-- `detalle`: los datos del paso (medio, courier, código y costo del envío; el id de la foto; el
--   motivo de un problema). No se reusa `respuestas_onboarding` porque tiene UNA fila por pregunta:
--   el código del segundo envío pisaría el del primero y el historial de un reenvío se perdería.
--   jsonb y no columnas: cada paso lleva datos distintos, y ninguna consulta filtra por ellos.
--   Solo se exige que sea un objeto (se evalúa con la fila); qué claves lleva cada paso lo impone
--   el dominio (`PasoDeCaja`).
-- Las filas que ya existen (el Mapa del Día 7, `mapa_dia7`) quedan con las dos en NULL, y quien las
-- lee (`MapaRenacimientoService`) pregunta por su flujo exacto: las filas `caja:%` no lo tocan.
-- ============================================================================

BEGIN;

SET search_path TO renaser, public;

-- ----------------------------------------------------------------------------
-- 1. Quién marcó cada paso y con qué datos
-- ----------------------------------------------------------------------------
ALTER TABLE etapas_onboarding_completadas
    ADD COLUMN marcada_por uuid  REFERENCES usuarios (id) ON DELETE SET NULL,
    ADD COLUMN detalle     jsonb CHECK (detalle IS NULL OR jsonb_typeof(detalle) = 'object');

-- La lista del Admin lee todos los pasos de la caja de una vez; el Mapa sigue leyendo por usuario
-- con la PK. Índice parcial: solo las filas de la caja.
CREATE INDEX etapas_onboarding_caja_idx ON etapas_onboarding_completadas (usuario_id)
    WHERE flujo LIKE 'caja:%';

-- ----------------------------------------------------------------------------
-- 2. El flujo `caja_renaser`: dos secciones
-- ----------------------------------------------------------------------------
INSERT INTO secciones_onboarding (flujo, clave_seccion, titulo, descripcion, orden) VALUES
    ('caja_renaser', 'destino',   'A dónde va tu caja', 'Solo si es distinto de lo que pusiste en tu ficha', 0),
    ('caja_renaser', 'contenido', 'Contenido de la caja', 'Lo marca el Admin al armarla', 1);

-- ----------------------------------------------------------------------------
-- 3. Las preguntas. Todas opcionales. `clave_pregunta` es UNIQUE global: prefijo `caja_`.
--    El aprendiz no las responde por /onboarding/answers (RespuestaService las rechaza): las de
--    `destino` las escribe su propio caso de uso y `caja_contenido` solo el Admin.
-- ----------------------------------------------------------------------------
INSERT INTO preguntas_onboarding (clave_pregunta, texto, tipo, requerida, orden, seccion_id)
SELECT v.clave_pregunta, v.texto, v.tipo, false, v.orden, s.id
FROM (VALUES
    ('caja_otra_direccion'::text, '¿Te la enviamos a otra dirección?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, 0::smallint, 'destino'::text),
    ('caja_otro_celular',   'Otro número de contacto',        'TEXTO',              1, 'destino'),
    ('caja_quien_recibe',   '¿Quién la recibe?',              'TEXTO',              2, 'destino'),
    ('caja_referencias',    'Referencias para encontrarte',   'AREA_TEXTO',         3, 'destino'),
    ('caja_provincia',      'Provincia',                      'TEXTO',              4, 'destino'),
    ('caja_contenido',      'Contenido de la caja',           'SELECCION_MULTIPLE', 0, 'contenido')
) AS v (clave_pregunta, texto, tipo, orden, clave_seccion)
JOIN secciones_onboarding s ON s.flujo = 'caja_renaser' AND s.clave_seccion = v.clave_seccion;

-- ----------------------------------------------------------------------------
-- 4. Los 8 elementos del procedimiento de Operaciones «Caja Renaser (02)». El Admin los edita
--    desde la app (reescribe estas opciones); estos son solo el punto de partida.
-- ----------------------------------------------------------------------------
INSERT INTO opciones_pregunta (pregunta_id, orden, valor, etiqueta)
SELECT p.id, v.orden, v.valor, v.etiqueta
FROM (VALUES
    (0::smallint, 'caja_verde'::text,     'Caja verde'::text),
    (1,           'vela',                 'Vela'),
    (2,           'esencias',             '2 esencias'),
    (3,           'cuadernos',            '3 cuadernos'),
    (4,           'guias',                '4 guías'),
    (5,           'totem',                'Tótem'),
    (6,           'hoja_de_contenido',    'Hoja de contenido'),
    (7,           'carta_con_el_nombre',  'Carta con el nombre')
) AS v (orden, valor, etiqueta)
JOIN preguntas_onboarding p ON p.clave_pregunta = 'caja_contenido';

-- ----------------------------------------------------------------------------
-- 5. La carta con el nombre: una pieza más de la bitácora de la bienvenida (V73). Sumar una pieza
--    es cambiar el CHECK (por eso V73 no usó un ENUM). Su imagen va bajo `caja/cartas/`, y cada
--    pieza de imagen solo acepta rutas de su propio prefijo.
-- ----------------------------------------------------------------------------
ALTER TABLE cambios_bienvenida DROP CONSTRAINT cambios_bienvenida_pieza_check;
ALTER TABLE cambios_bienvenida DROP CONSTRAINT cambios_bienvenida_portada_ruta_check;
ALTER TABLE cambios_bienvenida DROP CONSTRAINT cambio_de_texto_sin_portada;
ALTER TABLE cambios_bienvenida DROP CONSTRAINT cambio_de_portada_sin_texto;

ALTER TABLE cambios_bienvenida
    ADD CONSTRAINT cambios_bienvenida_pieza_check
        CHECK (pieza IN ('SOPORTE_CON_LA_TARJETA', 'SOPORTE_FORMAL', 'GRUPO', 'PORTADA', 'CARTA_CAJA')),
    ADD CONSTRAINT cambios_bienvenida_portada_ruta_check
        CHECK (portada_ruta IS NULL
               OR (char_length(portada_ruta) <= 300
                   AND ((pieza = 'PORTADA' AND portada_ruta LIKE 'bienvenida/portadas/%')
                        OR (pieza = 'CARTA_CAJA' AND portada_ruta LIKE 'caja/cartas/%')))),
    ADD CONSTRAINT cambio_de_texto_sin_portada CHECK (pieza IN ('PORTADA', 'CARTA_CAJA') OR portada_ruta IS NULL),
    ADD CONSTRAINT cambio_de_portada_sin_texto CHECK (pieza NOT IN ('PORTADA', 'CARTA_CAJA') OR texto IS NULL);

COMMENT ON COLUMN etapas_onboarding_completadas.marcada_por IS
    'Quien marco el paso (el Admin que armo o envio la caja; el aprendiz que confirmo). NULL en las etapas del Mapa y si se borro la cuenta (D-219).';
COMMENT ON COLUMN etapas_onboarding_completadas.detalle IS
    'Datos del paso de la Caja Renaser (flujo caja:<n>:<PASO>): medio, courier, codigo, costo, ids de fotos, motivo. Sus claves las impone el dominio (D-219).';

COMMIT;
