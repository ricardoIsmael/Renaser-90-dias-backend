-- V128 — El día del programa de una persona puede llegar a 365 (spec 022; D-295)
--
-- Por qué se toca la base
-- -----------------------
-- Las tablas que GUARDAN el día en que estaba una persona tenían el 90 escrito en un CHECK (`BETWEEN 0 AND 90`). Con el Diplomado de 1 año (365 días) un
-- aprendiz llega al día 120 y el INSERT fallaría. El tope de cada persona lo impone el dominio con SU duración (`participantes_programa.duracion_dias`, V125);
-- en estas tablas el CHECK pasa al máximo posible (365), porque la invariante depende de otra fila y la base no debe repetirla (regla 04).
--
-- Qué NO se toca
-- --------------
-- Las columnas de CONTENIDO por día (audioterapias, guías y horarios de hábito, desbloqueos) son el catálogo de la Escuela; cómo se reparte el contenido de
-- las otras formaciones lo deciden las specs 023 y 024.
-- `publicaciones_muro.dia_programa` no tiene CHECK en la base (el tope lo pone el dominio).

SET search_path TO renaser, public;

ALTER TABLE ajustes_dia_programa
    DROP CONSTRAINT ajustes_dia_programa_dia_anterior_check,
    DROP CONSTRAINT ajustes_dia_programa_dia_nuevo_check,
    ADD CONSTRAINT ajustes_dia_programa_dia_anterior_check CHECK (dia_anterior BETWEEN 0 AND 365),
    ADD CONSTRAINT ajustes_dia_programa_dia_nuevo_check CHECK (dia_nuevo BETWEEN 0 AND 365);

ALTER TABLE registros_habito
    DROP CONSTRAINT registros_habito_dia_programa_check,
    ADD CONSTRAINT registros_habito_dia_programa_check CHECK (dia_programa BETWEEN 0 AND 365);

ALTER TABLE solicitudes_emergencia
    DROP CONSTRAINT solicitudes_emergencia_dia_pedido_check,
    DROP CONSTRAINT solicitudes_emergencia_dia_al_pedir_check,
    DROP CONSTRAINT solicitudes_emergencia_dia_aplicado_check,
    ADD CONSTRAINT solicitudes_emergencia_dia_pedido_check CHECK (dia_pedido BETWEEN 1 AND 364),
    ADD CONSTRAINT solicitudes_emergencia_dia_al_pedir_check CHECK (dia_al_pedir BETWEEN 0 AND 365),
    ADD CONSTRAINT solicitudes_emergencia_dia_aplicado_check CHECK (dia_aplicado BETWEEN 1 AND 364);
