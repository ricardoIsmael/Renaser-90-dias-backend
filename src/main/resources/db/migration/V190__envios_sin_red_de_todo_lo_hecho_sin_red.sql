-- V190 — Todo lo hecho sin internet se registra una sola vez y deja su rastro (spec 048, fase 3, FR-019).
--
-- QUÉ RESUELVE. La V188 nació para un solo tipo de envío: completar un hábito (`registro_id NOT NULL`, solo «sin puntos»).
-- La fase 3 suma Cierra tu día, el Código Renaser, Mi Radar, el Plan (acciones, semana, mes, 90 días) y planificar hábitos
-- y alarmas, todos con la misma regla: cada envío trae su `id_cliente` (X-Id-Cliente) y un reintento devuelve lo mismo sin
-- tocar nada. Además hay un resultado nuevo que antes no existía: el envío que NO se aplica porque en el servidor cambió algo
-- después de la hora en que lo hiciste (D13). Ese resultado se guarda con su código y su mensaje para que el reintento
-- responda exactamente lo mismo, y para que Soporte TI vea qué no se aplicó (FR-016).
--
-- POR QUÉ NO UNA TABLA POR ÁREA. Es la misma pregunta («¿este envío ya llegó y cómo quedó?») y la misma clave; una tabla por
-- área repetiría el candado, el índice y la limpieza con la cuenta. Se generaliza esta.
--
-- QUÉ CAMBIA. Ninguna columna se renombra ni se borra: el despliegue deja un rato corriendo el código de antes, que sigue
-- escribiendo `(id_cliente, participante_id, registro_id, motivo_sin_puntos)` y lee solo esas columnas.
--   * `registro_id` admite NULL: queda solo para los hábitos.
--   * `tipo`      qué se hizo (COMPLETAR_HABITO, CIERRE_DEL_DIA, CODIGO_RENASER, MI_RADAR, PLAN_AGENDAR, PLAN_MARCAR_ACCION,
--                 PLAN_SEMANAL_CREAR, PLAN_SEMANAL_EDITAR, PLAN_SEMANAL_CERRAR, PLAN_OBJETIVO_90, PLAN_OBJETIVO_MES,
--                 HABITO_PREFERENCIA, HABITO_PLAN_SEMANA, HABITO_DESBLOQUEO, HABITO_PERSONAL). Los de antes valen
--                 COMPLETAR_HABITO. Sin CHECK: la lista crece con cada área y un valor nuevo no debe pedir otra migración.
--   * `recurso`   la clave de lo que tocó (`roca-diaria:<uuid>`, `plan-diario:<fecha>`, `semana:<n>`, `cierre:<fecha>`,
--                 `radar:<uuid>`, `mi-radar`, `preferencia:<habito>`, `habito:<uuid>`…). Nunca un texto de la persona.
--   * `resultado` APLICADO o NO_APLICADO; `codigo` y `mensaje`, solo en NO_APLICADO (CHECK de los dos y de los cinco códigos
--                 cerrados que la app reconoce).
--   * `hecho_en` y `origen_de_la_hora`: lo que dijo la app (FR-013), para el rastro.
-- El índice parcial es para la lista de Soporte TI de lo que no se aplicó.
SET search_path TO renaser, public;

ALTER TABLE envios_sin_red ALTER COLUMN registro_id DROP NOT NULL;

ALTER TABLE envios_sin_red
    ADD COLUMN IF NOT EXISTS tipo              TEXT        NOT NULL DEFAULT 'COMPLETAR_HABITO',
    ADD COLUMN IF NOT EXISTS recurso           TEXT,
    ADD COLUMN IF NOT EXISTS resultado         TEXT        NOT NULL DEFAULT 'APLICADO',
    ADD COLUMN IF NOT EXISTS codigo            TEXT,
    ADD COLUMN IF NOT EXISTS mensaje           TEXT,
    ADD COLUMN IF NOT EXISTS hecho_en          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS origen_de_la_hora TEXT;

ALTER TABLE envios_sin_red
    ADD CONSTRAINT envios_sin_red_resultado_chk CHECK (resultado IN ('APLICADO', 'NO_APLICADO')),
    ADD CONSTRAINT envios_sin_red_codigo_si_no_aplicado_chk CHECK ((resultado = 'NO_APLICADO') = (codigo IS NOT NULL)),
    ADD CONSTRAINT envios_sin_red_codigo_chk CHECK (codigo IS NULL OR codigo IN (
        'CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR', 'YA_ESTABA_HECHO', 'RECURSO_YA_NO_EXISTE', 'FUERA_DE_SU_DIA', 'MI_RADAR_NO_APLICA'));

CREATE INDEX IF NOT EXISTS envios_sin_red_no_aplicados_idx
    ON envios_sin_red (participante_id, creado_en) WHERE resultado = 'NO_APLICADO';
