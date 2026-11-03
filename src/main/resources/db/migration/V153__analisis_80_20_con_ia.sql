-- V153 — El análisis 80/20 con IA de cada semana y cada mes del programa (spec 031; D-306)
--
-- Por qué se toca la base
-- -----------------------
-- El dueño pidió (2026-10-31) que cada persona reciba, cada semana y cada mes del programa, su 80/20 de acciones diarias y un análisis con
-- IA que lo lea junto con lo que respondió en el Código Renaser y en «Cierra tu día». Las CIFRAS se calculan al pedirlas desde las tablas
-- de siempre (`rocas_diarias`, `registros_radar`, `entradas_diario`) y no se guardan. Lo que sí hay que guardar es el TEXTO que la IA
-- generó y el estado de ese intento:
--   · abrirlo muchas veces (la hoja de Plan, la ficha de quien acompaña, SER) NO puede volver a pagar la IA (FR-006, SC-003);
--   · lo que se leyó hoy tiene que ser lo que se lea mañana, así que el texto se guarda junto a las cifras que cita;
--   · el barrido horario necesita saber qué periodos ya se decidieron (listo, sin datos suficientes, agotado) para no recalcularlos
--     cada hora, y cuántos intentos lleva cada uno para acotar los reintentos (FR-016).
--
-- Por qué no alcanza el modelo actual
-- -----------------------------------
-- `informes_espejo_sombra` es el informe semanal del diario: otra fuente, otra forma (patrón dominante, distribución temporal, preguntas) y
-- una decisión del dueño explícita de no unirlos (031-5 A). `mensajes_renasia` guarda lo que se conversa con SER, no un periodo analizado.
-- Una tabla propia es la forma correcta: una fila por persona y periodo.
--
-- Qué se agrega y por qué así
-- ---------------------------
-- · Una fila por persona, tipo de periodo (SEMANA o MES) e inicio del periodo (único): el inicio identifica el periodo sin depender de que
--   sea lunes (la semana 1 empieza el Día 1 y la 14 el día siguiente al último de la 13).
-- · `formacion`: la de la persona cuando se generó (spec 022). El análisis es de una formación y nunca se mezcla con otra; cuando alguien
--   cambia de formación, lo suyo anterior queda con la anterior y no se muestra en la nueva.
-- · `estado`: PENDIENTE (cerró y falta generarlo; `intentos` cuenta los intentos fallidos), LISTO (el texto está), SIN_DATOS (el periodo no
--   alcanzó el mínimo y no se llamó a la IA) y AGOTADO (se acabaron los intentos). Solo LISTO lleva texto: lo exige un CHECK.
-- · El texto en tres columnas `text` y los pasos en `text[]` (de uno a tres textos cortos que nunca se consultan por separado: una tabla hija
--   no aportaría nada). `cifras` es `jsonb`: la FOTO de lo que el texto cita. Es la única desnormalización y está justificada arriba; nadie
--   la consulta por dentro.
-- · `ultimo_intento_en` + índice parcial: el tope diario de generaciones cuenta los intentos de las últimas 24 horas sin una tabla de
--   contadores.
-- · `participante_id` cuelga de `participantes_programa` (como `registros_radar`, `rocas_diarias` e `informes_espejo_sombra`): el análisis
--   es actividad del programa, no de la cuenta. `ON DELETE CASCADE`: se borra con la cuenta (FR-025); `BorradoDeCuentaEnRagAdapter` lo
--   borra antes y la cascada queda de red de seguridad (D-243).
--
-- Por qué estos nombres
-- ---------------------
-- `analisis_80_20` es lo que el dueño llama «80/20» en la app. `lo_que_mas_movio`, `lo_que_se_te_fue`, `como_estuviste` y `proximos_pasos`
-- son las cuatro partes del análisis con los nombres que lee la persona.

SET search_path TO renaser, public;

CREATE TABLE analisis_80_20 (
    id                uuid         PRIMARY KEY,
    participante_id   uuid         NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    formacion         text         NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo),
    periodo           varchar(8)   NOT NULL CHECK (periodo IN ('SEMANA', 'MES')),
    numero            integer      NOT NULL CHECK (numero >= 1),
    inicio            date         NOT NULL,
    fin               date         NOT NULL,
    estado            varchar(16)  NOT NULL CHECK (estado IN ('PENDIENTE', 'LISTO', 'SIN_DATOS', 'AGOTADO')),
    intentos          integer      NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    ultimo_intento_en timestamptz,
    generado_en       timestamptz,
    creado_en         timestamptz  NOT NULL,
    lo_que_mas_movio  text,
    lo_que_se_te_fue  text,
    como_estuviste    text,
    proximos_pasos    text[],
    cifras            jsonb,
    CONSTRAINT analisis_80_20_fin_despues_del_inicio CHECK (fin >= inicio),
    CONSTRAINT analisis_80_20_uno_por_periodo UNIQUE (participante_id, periodo, inicio),
    CONSTRAINT analisis_80_20_solo_listo_lleva_texto CHECK (
        (estado = 'LISTO' AND lo_que_mas_movio IS NOT NULL AND lo_que_se_te_fue IS NOT NULL AND como_estuviste IS NOT NULL
            AND proximos_pasos IS NOT NULL AND cardinality(proximos_pasos) BETWEEN 1 AND 3
            AND cifras IS NOT NULL AND generado_en IS NOT NULL)
        OR (estado <> 'LISTO' AND lo_que_mas_movio IS NULL AND lo_que_se_te_fue IS NULL AND como_estuviste IS NULL
            AND proximos_pasos IS NULL AND generado_en IS NULL))
);

CREATE INDEX analisis_80_20_intentos_idx ON analisis_80_20 (ultimo_intento_en) WHERE ultimo_intento_en IS NOT NULL;
CREATE INDEX analisis_80_20_formacion_idx ON analisis_80_20 (formacion);

COMMENT ON TABLE analisis_80_20 IS
    'Análisis 80/20 con IA de una semana o un mes del programa de una persona (spec 031, D-306): una fila por persona y periodo; el texto solo existe en LISTO.';
COMMENT ON COLUMN analisis_80_20.cifras IS
    'La foto de las cifras que el texto cita (FR-006): lo que se leyó hoy es lo que se lee mañana.';
