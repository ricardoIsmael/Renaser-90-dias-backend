-- V81 — Todas las antelaciones del recordatorio de un habito (D-217, 2026-09-28).
--
-- QUE RESUELVE. En la app se puede pedir mas de un aviso por habito («30 min antes y a la hora»), y
-- ese conjunto vivia solo en el telefono (AsyncStorage): en un telefono nuevo o al reinstalar, la app
-- rearmaba el recordatorio desde el servidor con un solo aviso, porque minutos_recordatorio es un solo
-- numero (E-398). El dueño pidio que se reconstruyan todos.
--
-- POR QUE NO SE REUSA minutos_recordatorio. El APK de produccion lo manda y lo lee como UN numero
-- (reminderMinutesBefore) y la app no se actualiza por aire: cambiarle el tipo o el significado lo
-- romperia. Sigue siendo la antelacion MAS TEMPRANA (el maximo del conjunto), que es lo que ese APK y el
-- aviso de inicio del servidor (CalculadoraAvisosHabito) necesitan. El conjunto va al lado.
--
-- POR QUE ESTE NOMBRE Y ESTE TIPO. «antelaciones» es la palabra del dominio (AntelacionDelRecordatorio);
-- smallint[] porque cada valor es lo mismo que minutos_recordatorio (smallint). NULL = no se conocen:
-- toda fila existente queda asi, y la app lo lee como «desconocido» y no pisa lo que el telefono tiene.
--
-- SOLO EN preferencias_horario. cambios_horario_pendientes guarda solo los minutos a proposito: desde
-- E-159 el recordatorio se aplica HOY en la preferencia y la promocion vuelve a escribir esos mismos
-- minutos (la mas temprana), con lo que el conjunto se conserva. horarios_habito_por_fecha (V37) es la
-- excepcion de un dia; la alarma del telefono es diaria y no la usa.
--
-- El CHECK mira solo la fila: ningun valor negativo, igual que minutos_recordatorio. Que el maximo del
-- conjunto coincida con minutos_recordatorio lo impone el dominio (PreferenciaHorario): un CHECK no puede
-- calcular el maximo de un arreglo sin una subconsulta.

ALTER TABLE renaser.preferencias_horario
    ADD COLUMN antelaciones_recordatorio smallint[] NULL
        CONSTRAINT preferencias_horario_antelaciones_no_negativas
            CHECK (antelaciones_recordatorio IS NULL OR 0 <= ALL (antelaciones_recordatorio));
