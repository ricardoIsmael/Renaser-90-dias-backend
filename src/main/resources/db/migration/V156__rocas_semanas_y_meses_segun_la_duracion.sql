-- V156 — Las semanas y los meses del plan de objetivos siguen la duración del programa de cada persona (spec 026; D-303)
--
-- Por qué se toca la base
-- -----------------------
-- `rocas_semanales.numero_semana` valía de 1 a 13 y `rocas_mensuales.numero_mes` de 1 a 3: las semanas y los meses de un programa de 90 días. Con el
-- Diplomado de 1 año (365 días, spec 022) una persona llega a la semana 53 y al mes 12, y el INSERT fallaría. Los objetivos ahora nacen del diagnóstico y
-- el plan lee la duración de la formación (`participantes_programa.duracion_dias`, V125): el tope de CADA persona lo pone el dominio
-- (`SemanaPrograma.ultimaSemana()`, `MesPrograma.mesesDe(duracion)`); en estas dos columnas el CHECK pasa al máximo posible, porque la invariante depende de
-- otra fila y la base no debe repetirla (regla 04).
--
-- Cuál es el máximo real
-- ----------------------
-- Semanas de lunes a domingo (D-203): la semana 1 es la que contiene el día 1 y la última llega hasta el último día del programa. Con 365 días la semana del
-- último día es la 53 empiece la persona el día que empiece (el resto de 365 = 52·7 + 1 suma a lo sumo una semana más que las 52 completas, nunca dos):
-- por eso 53 y no 54. Lo fija `SemanaProgramaTest` recorriendo los siete días de arranque. Los meses son bloques de 30 días y el último absorbe el resto:
-- 365 / 30 = 12.
--
-- Qué NO se toca
-- --------------
-- Ninguna fila: los valores de hoy (1..13 y 1..3) siguen siendo válidos. Los demás topes de 90 de las rocas viven en el dominio, no en la base; las
-- columnas de CONTENIDO por semana (`audioterapias.semana`) son el catálogo de la Escuela (specs 023/024).

SET search_path TO renaser, public;

ALTER TABLE rocas_semanales
    DROP CONSTRAINT rocas_semanales_numero_semana_check,
    ADD CONSTRAINT rocas_semanales_numero_semana_check CHECK (numero_semana BETWEEN 1 AND 53);

ALTER TABLE rocas_mensuales
    DROP CONSTRAINT rocas_mensuales_numero_mes_check,
    ADD CONSTRAINT rocas_mensuales_numero_mes_check CHECK (numero_mes BETWEEN 1 AND 12);
