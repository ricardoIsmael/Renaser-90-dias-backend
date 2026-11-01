-- =====================================================================================
-- Codigo Renaser con la voz: el registro dice si se dicto (spec 030, D-297).
--
-- QUE PROBLEMA RESUELVE
--
-- El dueno (2026-10-31, decision 030-2 = B) quiere que el registro del Codigo Renaser diga «dictado» y que lo
-- vean el mentor (o guia), el lider y Administracion: quien acompana lee texto que una persona dicto con el
-- reconocimiento de voz de su telefono y que puede traer errores de transcripcion. El audio NO se guarda ni se
-- envia (decision 030-1 = A); solo cambia el registro.
--
-- QUE SE GUARDA
--
-- Una sola columna en la tabla que ya existe: `registros_radar.dictado`, verdadera si AL MENOS UNA de las cuatro
-- respuestas se dicto y esa parte sigue en el texto registrado. Lo declara la app al registrar (el servidor no
-- oye audio). Un dato por registro porque el uso es uno: avisar que puede haber errores de transcripcion.
--
-- POR QUE NO ALCANZA EL MODELO ACTUAL
--
-- Nada en `registros_radar` distingue un texto dictado de uno escrito, y no se puede deducir del texto. Una tabla
-- aparte seria una relacion uno a uno con el registro, que el dueno pidio evitar («no crear tablas innecesarias»).
--
-- POR QUE ESE NOMBRE Y ESE TIPO
--
-- `dictado` por la palabra del dueno. `boolean NOT NULL DEFAULT false`: los registros anteriores y los que hace
-- SER por la persona (nunca hablados en este formulario) valen false, sin migrar datos ni dejar nulos que
-- obliguen a un tercer estado. La tabla sigue siendo append-only: el dato nace con el registro y no se edita.
-- El indice unico `radar_un_registro_por_hora` no cambia.
-- =====================================================================================

ALTER TABLE renaser.registros_radar
    ADD COLUMN dictado boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN renaser.registros_radar.dictado IS
    'Spec 030: true si al menos una respuesta se dicto con el reconocimiento de voz del telefono. Lo declara la app; el audio no se guarda.';
