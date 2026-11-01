-- V140 — Lo que Administración eligió al aceptar una solicitud (spec 025; D-302)
--
-- Por qué se toca la base
-- -----------------------
-- Con las formaciones (spec 022), aceptar a alguien exige tres decisiones, en este orden: a qué formación entra (y, en el Diplomado, de cuánto tiempo),
-- qué rol tiene y si su ficha de entrada es con o sin diagnóstico. «Solicitudes» debe poder decir después qué se decidió («Aceptada por Ana el 31 oct ·
-- Diplomado 1 año · Aprendiz · con diagnóstico») y filtrar las aceptadas por formación (US4, FR-017).
--
-- Por qué no alcanza el modelo actual / qué se reutiliza
-- ------------------------------------------------------
-- · Quién aceptó y cuándo ya lo guardan `revisada_por` y `revisada_en`: no se repiten.
-- · La formación y la duración VIVAS de la persona están en `participantes_programa` (aprendiz) y en `formaciones_personal` (personal), y el rol en `usuarios`;
--   esas tablas cambian cuando la persona se traslada o la corrigen, y «lo que se decidió al aceptar» no debe cambiar con ellas. Por eso esta migración
--   guarda UNA FOTO, en la propia solicitud, de lo elegido en ese momento: es la desnormalización justificada de un dato histórico (misma idea que
--   `participaciones_anteriores`, V127), nunca la fuente de la pertenencia.
-- · La marca «con o sin diagnóstico» de la PERSONA (qué ficha le toca) NO vive aquí: es del módulo `onboarding` (spec 026, `estado_onboarding`). Esta columna solo
--   recuerda qué se pidió al aceptar, para el historial.
-- · No hay tabla nueva: son cuatro columnas de la fila que ya existe, que se leen y escriben siempre juntas con ella.
--
-- Por qué estos nombres y tipos
-- -----------------------------
-- · `formaciones_elegidas text[]`: los códigos de `formaciones` (una para el aprendiz; una o varias para el personal; vacío para Administración y
--   Alquimista, que ven las tres). Un arreglo y no una tabla N:M porque es una foto que se lee entera y nadie la une con nada.
-- · `duracion_elegida_dias`: solo el aprendiz elige duración; NULL para el resto.
-- · `rol_elegido`: el rol base con el que entró (los nombres de la API, p. ej. TRAINEE_GUIDE). NULL en las solicitudes anteriores a esta migración.
-- · `con_diagnostico`: lo pedido al aceptar. NULL mientras la solicitud no está aceptada.
--
-- Qué pasa con lo que ya existe (FR-013, SC-004)
-- ----------------------------------------------
-- Las solicitudes ya aceptadas quedan como «Escuela · sin diagnóstico» (que es lo que fueron): el UPDATE de abajo les pone esa foto. Ninguna otra fila cambia.

SET search_path TO renaser, public;

ALTER TABLE solicitudes_cuenta
    ADD COLUMN formaciones_elegidas    text[]   CHECK (cardinality(formaciones_elegidas) BETWEEN 0 AND 3),
    ADD COLUMN duracion_elegida_dias   smallint CHECK (duracion_elegida_dias BETWEEN 1 AND 365),
    ADD COLUMN rol_elegido             text,
    ADD COLUMN con_diagnostico         boolean;

COMMENT ON COLUMN solicitudes_cuenta.formaciones_elegidas IS
    'Foto de las formaciones elegidas al aceptar (V140, spec 025). NULL mientras no se acepta. Vacío = Administración o Alquimista (ven las tres). La pertenencia viva está en participantes_programa y formaciones_personal.';
COMMENT ON COLUMN solicitudes_cuenta.duracion_elegida_dias IS
    'Duración elegida al aceptar a un aprendiz (V140, spec 025). NULL para el personal y para lo anterior a las formaciones.';
COMMENT ON COLUMN solicitudes_cuenta.rol_elegido IS
    'Rol base con el que entró (V140, spec 025). NULL en lo aceptado antes de las formaciones.';
COMMENT ON COLUMN solicitudes_cuenta.con_diagnostico IS
    'Si se pidió la ficha con diagnóstico al aceptar (V140, spec 025). Solo historial: la marca de la persona es de onboarding (spec 026).';

UPDATE solicitudes_cuenta
SET formaciones_elegidas = ARRAY['ESCUELA'],
    con_diagnostico = false
WHERE estado = 'APROBADA';
