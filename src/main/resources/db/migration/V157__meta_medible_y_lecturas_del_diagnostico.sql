-- V157 — La meta medible se pregunta en el diagnóstico y las lecturas del diagnóstico dejan constancia (spec 026; D-303)
--
-- Por qué se toca la base
-- -----------------------
-- Dos decisiones del dueño sobre la ficha con diagnóstico, en una sola migración porque ambas son de la misma spec y salen juntas.
--
-- A. La meta medible se pregunta en el diagnóstico.
-- El Mapa de Renacimiento sabía QUÉ se medía en cada eje (kilos, soles, una escala del 1 al 10) y desde dónde partía la persona; con eso el plan mensual sacaba la cifra del mes y los hitos.
-- El diagnóstico lo reemplazó, pero su objetivo por eje es solo un texto («En 90 días quiero facturar S/ 15,000 mensuales»): sin número, el plan mensual dice «sin cifra». El dueño decidió que el
-- diagnóstico pregunte esa meta. Para eso:
--   1. Amplía la lista de lo que una pregunta puede «alimentar» (`alimenta`, V144) con ocho destinos nuevos: la meta, la unidad y el punto de partida de Cuerpo y de Negocio, y la meta y el punto
--      de partida de Relaciones (que siempre es la escala del 1 al 10; no tiene unidad que preguntar).
--   2. Siembra esas preguntas en el borrador del dueño (flujo `diagnostico`, V147), justo debajo del objetivo de cada eje. Todas son OPCIONALES y sin condición: quien no las responde conserva
--      su objetivo en palabras, sin cifra, como hoy. Se pueden editar, mover o quitar desde Administración → Fichas como cualquier otra.
--
-- Por qué no tablas nuevas
-- ------------------------
-- Son filas de `preguntas_onboarding` (V1, ampliada en V144) y una lista de valores permitidos. El índice único de V144 (una sola pregunta activa por destino) vale también para los nuevos.
-- La siembra es idempotente: solo corre si el borrador ya trae las preguntas de los objetivos y todavía no trae las nuevas, y recorre las `orden` de las que vienen después en la sección.
--
-- B. Quién abrió el diagnóstico de una persona queda anotado.
-- Cuando el mentor o la guía, el líder de mentores, Administración o Alquimista abren el diagnóstico de una persona, queda constancia de quién lo vio y cuándo. Se reutiliza
-- `consultas_de_ficha` (V109, V124), que ya guarda «quién abrió la ficha de quién»: no hay tabla nueva. Lo que cambia es que esa tabla hablaba solo de la ficha de una SEMANA:
--   · `tipo` dice qué se abrió: `SEMANA` (todas las filas de antes, por el valor por defecto) o `DIAGNOSTICO`.
--   · `semana_desde` acepta NULL, pero solo en un `DIAGNOSTICO` (el diagnóstico no es de una semana): una restricción amarra las dos columnas, para que ninguna fila de semana pierda
--     su fecha por error.
--   · `rol_consultor` acepta también al mentor, a la guía y a Soporte TI, pero solo en un `DIAGNOSTICO`: el resumen de las 20:00 al mentor y las otras lecturas de la ficha siguen
--     siendo de MENTOR_LEAD, ADMIN y ALCHEMIST como hasta hoy. La propia persona no deja fila: no hay ruta que la escriba.
--   · Índice parcial por persona, para el día en que haya que contestar «¿quién vio su diagnóstico?».
-- La tabla sigue siendo append-only: `ADD COLUMN` con un valor por defecto constante no reescribe filas (no dispara el trigger por fila) y `DROP NOT NULL` tampoco.

SET search_path TO renaser, public;

ALTER TABLE preguntas_onboarding DROP CONSTRAINT IF EXISTS preguntas_onboarding_alimenta_check;
ALTER TABLE preguntas_onboarding ADD CONSTRAINT preguntas_onboarding_alimenta_check CHECK (alimenta IS NULL OR alimenta IN
    ('OBJETIVO_CUERPO', 'OBJETIVO_NEGOCIO', 'OBJETIVO_RELACIONES', 'PRIORIDAD_PRINCIPAL', 'PRIMERA_ACCION', 'TRATO_DE_LA_PERSONA',
     'META_CUERPO', 'UNIDAD_CUERPO', 'PARTIDA_CUERPO', 'META_NEGOCIO', 'UNIDAD_NEGOCIO', 'PARTIDA_NEGOCIO', 'META_RELACIONES', 'PARTIDA_RELACIONES'));

DO $semilla$
DECLARE
    seccion smallint;
    despues smallint;
BEGIN
    SELECT id INTO seccion FROM secciones_onboarding WHERE flujo = 'diagnostico' AND clave_seccion = 'tu_vision';
    IF seccion IS NULL
       OR NOT EXISTS (SELECT 1 FROM preguntas_onboarding WHERE clave_pregunta = 'diag_p18b')
       OR EXISTS (SELECT 1 FROM preguntas_onboarding WHERE clave_pregunta IN ('diag_p16c', 'diag_p17c', 'diag_p18c')) THEN
        RETURN;
    END IF;

    -- Negocio (después de diag_p18b): se hace primero para no mover lo que ya se acomodó. Cada bloque abre hueco recorriendo el `orden` de las preguntas de la sección que vienen después.
    SELECT orden INTO despues FROM preguntas_onboarding WHERE seccion_id = seccion AND clave_pregunta = 'diag_p18b';
    UPDATE preguntas_onboarding SET orden = orden + 3 WHERE seccion_id = seccion AND orden > despues;
    INSERT INTO preguntas_onboarding (seccion_id, clave_pregunta, texto, tipo, config_escala, requerida, orden, pregunta_padre_id, ayuda, maximo_opciones, se_muestra_si_valor, alimenta, precarga) VALUES
        (seccion, 'diag_p18c', 'RIQUEZA Y NEGOCIOS · ¿En qué unidad mides ese resultado?', 'TEXTO', NULL, false, despues + 1, NULL,
         'Ejemplo: S/ al mes, US$ al mes, clientes, ventas. Es opcional: sin ella tu objetivo queda en palabras.', NULL, NULL, 'UNIDAD_NEGOCIO', NULL),
        (seccion, 'diag_p18d', 'RIQUEZA Y NEGOCIOS · ¿Desde qué número partes hoy?', 'NUMERO', NULL, false, despues + 2, NULL,
         'Escribe solo el número, en la unidad que elegiste. Ejemplo: 10000', NULL, NULL, 'PARTIDA_NEGOCIO', NULL),
        (seccion, 'diag_p18e', 'RIQUEZA Y NEGOCIOS · ¿A qué número quieres llegar en {duracion}?', 'NUMERO', NULL, false, despues + 3, NULL,
         'Escribe solo el número, en la misma unidad. Ejemplo: 15000', NULL, NULL, 'META_NEGOCIO', NULL);

    -- Cuerpo (después de diag_p17b)
    SELECT orden INTO despues FROM preguntas_onboarding WHERE seccion_id = seccion AND clave_pregunta = 'diag_p17b';
    UPDATE preguntas_onboarding SET orden = orden + 3 WHERE seccion_id = seccion AND orden > despues;
    INSERT INTO preguntas_onboarding (seccion_id, clave_pregunta, texto, tipo, config_escala, requerida, orden, pregunta_padre_id, ayuda, maximo_opciones, se_muestra_si_valor, alimenta, precarga) VALUES
        (seccion, 'diag_p17c', 'SALUD Y BIENESTAR · ¿En qué unidad mides ese resultado?', 'TEXTO', NULL, false, despues + 1, NULL,
         'Ejemplo: kg, días de ejercicio por semana, horas de sueño. Es opcional: sin ella tu objetivo queda en palabras.', NULL, NULL, 'UNIDAD_CUERPO', NULL),
        (seccion, 'diag_p17d', 'SALUD Y BIENESTAR · ¿Desde qué número partes hoy?', 'NUMERO', NULL, false, despues + 2, NULL,
         'Escribe solo el número, en la unidad que elegiste. Ejemplo: 92', NULL, NULL, 'PARTIDA_CUERPO', NULL),
        (seccion, 'diag_p17e', 'SALUD Y BIENESTAR · ¿A qué número quieres llegar en {duracion}?', 'NUMERO', NULL, false, despues + 3, NULL,
         'Escribe solo el número, en la misma unidad. Ejemplo: 85', NULL, NULL, 'META_CUERPO', NULL);

    -- Relaciones (después de diag_p16b): siempre en la escala del 1 al 10, así que no hay unidad que preguntar.
    SELECT orden INTO despues FROM preguntas_onboarding WHERE seccion_id = seccion AND clave_pregunta = 'diag_p16b';
    UPDATE preguntas_onboarding SET orden = orden + 2 WHERE seccion_id = seccion AND orden > despues;
    INSERT INTO preguntas_onboarding (seccion_id, clave_pregunta, texto, tipo, config_escala, requerida, orden, pregunta_padre_id, ayuda, maximo_opciones, se_muestra_si_valor, alimenta, precarga) VALUES
        (seccion, 'diag_p16c', 'RELACIONES · Del 1 al 10, ¿cómo calificas hoy esta área de tu vida?', 'NUMERO', NULL, false, despues + 1, NULL,
         'Escribe un número entero del 1 al 10. 1 es muy mal y 10 es excelente. Es opcional: sin él tu objetivo queda en palabras.', NULL, NULL, 'PARTIDA_RELACIONES', NULL),
        (seccion, 'diag_p16d', 'RELACIONES · ¿A qué número del 1 al 10 quieres llevarla en {duracion}?', 'NUMERO', NULL, false, despues + 2, NULL,
         'Escribe un número entero del 1 al 10.', NULL, NULL, 'META_RELACIONES', NULL);
END
$semilla$;

-- B. consultas_de_ficha también guarda la lectura del diagnóstico de una persona.
ALTER TABLE consultas_de_ficha
    ADD COLUMN tipo varchar(16) NOT NULL DEFAULT 'SEMANA' CHECK (tipo IN ('SEMANA', 'DIAGNOSTICO'));

ALTER TABLE consultas_de_ficha ALTER COLUMN semana_desde DROP NOT NULL;

ALTER TABLE consultas_de_ficha
    ADD CONSTRAINT consultas_de_ficha_semana_segun_tipo CHECK ((tipo = 'SEMANA') = (semana_desde IS NOT NULL));

ALTER TABLE consultas_de_ficha DROP CONSTRAINT consultas_de_ficha_rol_consultor_check;
ALTER TABLE consultas_de_ficha
    ADD CONSTRAINT consultas_de_ficha_rol_consultor_check CHECK (
        rol_consultor IN ('MENTOR_LEAD', 'ADMIN', 'ALCHEMIST')
        OR (tipo = 'DIAGNOSTICO' AND rol_consultor IN ('MENTOR', 'TRAINEE_GUIDE', 'IT_SUPPORT')));

CREATE INDEX consultas_de_ficha_diagnostico_persona_idx ON consultas_de_ficha (persona_id, consultada_en) WHERE tipo = 'DIAGNOSTICO';

COMMENT ON COLUMN consultas_de_ficha.tipo IS
    'Qué se abrió: SEMANA (la ficha de cumplimiento de una semana, V109) o DIAGNOSTICO (el diagnóstico de la persona, V157).';
COMMENT ON COLUMN consultas_de_ficha.semana_desde IS
    'Primer día de la ventana consultada; NULL solo en un DIAGNOSTICO, que no es de una semana (V157).';
