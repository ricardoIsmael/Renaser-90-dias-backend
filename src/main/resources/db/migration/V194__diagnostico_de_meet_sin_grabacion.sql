-- V194 — Por qué Meet no grabó ni transcribió: el diagnóstico de cada sesión que termina sin transcripción (issue #151, spec 047 FR-022)
--
-- Por qué se toca la base
-- -----------------------
-- «Sesión II - Miguel» (13/11/2026) tuvo cuatro conferencias en una sala con grabación y transcripción automáticas confirmadas por Google y no
-- dejó ni una ni otra (`SIN_TRANSCRIPCION`); «Sesión II - Carlos», con la misma configuración, sí. No hay forma de saber el porqué: de la sesión
-- no se guarda quién entró ni cuánto duró cada conexión, y Google borra esos datos a los 30 días.
--
-- `diagnostico` guarda, SOLO cuando la fila termina en `SIN_TRANSCRIPCION` o `SIN_REUNION`, un resumen que no identifica a nadie: por conferencia,
-- inicio, fin y minutos; cuántos participantes hubo de cada tipo (con cuenta de Google, anónimos, por teléfono); y los estados de las grabaciones
-- y transcripciones que Google devolvió. Ni nombres, ni correos, ni ids de usuario. Es jsonb porque es una foto de lectura que cambia de forma
-- con lo que Google exponga y nunca se filtra por dentro. Va en esta tabla y no en otra: nace y se borra con la fila de la sala (CASCADE).
-- Es NULL en las demás filas y en las anteriores a esta migración.

SET search_path TO renaser, public;

ALTER TABLE eventos_meet_transcripciones ADD COLUMN diagnostico jsonb;

COMMENT ON COLUMN eventos_meet_transcripciones.diagnostico IS
    'Issue #151: por qué no hubo transcripción (solo SIN_TRANSCRIPCION y SIN_REUNION). Conferencias, cantidades de participantes por tipo y estados de grabación/transcripción. Sin nombres, correos ni ids.';
