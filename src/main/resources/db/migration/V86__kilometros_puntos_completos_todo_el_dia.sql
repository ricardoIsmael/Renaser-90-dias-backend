-- ============================================================================
-- «Kilómetros diarios» paga el puntaje completo en cualquier momento del día local
-- (D-226, confirmación del dueño del 2026-09-29).
--
-- QUE PROBLEMA RESUELVE
-- ---------------------
-- El horario del hábito `ea87fdec…` (DAILY_KM) es el de V9: dispara 07:00 y NO tiene hora límite.
-- Sin hora límite, el ancla de la ventana es la hora de disparo (`VentanaEntrega.calcular`), así que
-- el puntaje completo dura de 07:00 a 10:00 (extensión de 3 h, D-97), cae de 10 a 5 hasta las 10:10
-- y después paga 0 (`ResultadoOtorgamiento`, fase EXPIRADO → LATE_HABIT). Quien sale a correr por la
-- tarde y lo registra a las 22:30 lo cumple, pero cobra 0 «por tarde». V84 lo dejó así a propósito
-- («se cambia desde el panel»); el dueño decidió que no: los km se registran cuando se terminan de
-- recorrer, y eso puede ser a cualquier hora. Todo el día local del aprendiz cuenta como a tiempo.
--
-- QUE HACE
-- --------
-- Pone `hora_limite = 23:59` en las filas de `horarios_habito` de ESE hábito, y solo de ese. Con el
-- ancla a las 23:59 locales:
--   - hasta las 23:59 la entrega es A_TIEMPO → 10 puntos;
--   - la extensión se recorta contra la medianoche siguiente y queda en 0, y los 10 minutos de
--     gracia arrancan a las 23:59: en el minuto que queda hasta las 00:00 todavía no se descuenta
--     nada (se descuenta 1 punto cada 2 minutos enteros). O sea: 10 puntos todo el día local.
--   - El día siguiente es otro registro: el de ayer lo expira el barrido nocturno, como a todos.
-- La hora de disparo (07:00) NO se toca: es la que usan el aviso de inicio y la alarma del teléfono.
--
-- POR QUE CON DATOS Y NO CON CÓDIGO
-- ---------------------------------
-- La ventana ya se lee de `horarios_habito` en todos los lugares que la usan: el cobro
-- (`RegistroService.completar`), los «puntos en juego» de la agenda (`TracksDelDiaProyeccionService`)
-- y los avisos «por vencer» (`AvisoHabitoService`). Una excepción en el código para DAILY_KM habría
-- que repetirla en los tres —o dejarlos diciendo números distintos— y ocultaría la regla del panel,
-- que es donde el admin ve el horario de cada hábito. Con el dato, los tres dicen lo mismo y el
-- panel muestra «07:00 – 23:59», que es la verdad.
--
-- POR QUE 23:59 Y NO 23:50 (el tope de `VentanaDelDia.ULTIMA_HORA_LIMITE`, D-122)
-- ------------------------------------------------------------------------------
-- Con 23:50 el plazo termina a las 00:00, pero los últimos 10 minutos serían gracia decreciente:
-- registrar a las 23:59 pagaría 6, no 10, y el dueño pidió «completos hasta las 23:59». El tope de
-- D-122 existe para que una ventana no cruce la medianoche al ELEGIR horas en una rueda; acá el
-- plazo (23:59 + 10 min de gracia) sí pisa 9 minutos del día siguiente, pero eso ya lo contempla
-- `VentanaEntrega` (su extensión se recorta contra la medianoche) y el registro sigue siendo del
-- día en que se generó. No es el primer horario así: DÍA SIN CELULAR (`d2d58e66…`) tiene
-- 06:30 – 23:59 desde V4. Advertencia: si un admin edita este horario desde el panel, el dominio
-- volverá a acotar la hora límite a 23:50 (`VentanaDelDia.horaLimiteAjustada`).
--
-- LO QUE NO HACE
-- --------------
-- No cambia la regla de puntos ni la ventana de ningún otro hábito (el WHERE es por id del hábito).
-- No crea filas: si el hábito no tuviera horario, no pasaría nada (sin horario ya paga completo).
-- Una preferencia de horario del aprendiz con hora límite propia sigue ganándole al catálogo
-- (`RegistroService.resolverVentana`), como en cualquier hábito.
-- Idempotente: correrla dos veces da lo mismo.
-- ============================================================================

SET search_path TO renaser, public;

UPDATE horarios_habito
SET hora_limite    = '23:59',
    actualizado_en = now()
WHERE habito_id = 'ea87fdec-d4c1-4c4f-9e61-1557bc7255d1';
