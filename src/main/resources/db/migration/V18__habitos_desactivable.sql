-- Decision del dueno del proyecto (2026-09-02): cuatro habitos de catalogo NO se pueden sacar
-- del plan del aprendiz -- AUDIOTERAPIA SEMANAL, Pastilla Renacer, Clase diaria y POST DIARIO EN
-- COMUNIDAD -- y el resto (18 de los 22 activos) SI puede desactivarse libremente.
--
-- Por que no se reusa `es_opcional` (registrado en docs/informes/habits-campo-desactivable.md):
-- de los 22 habitos activos, 21 tienen `es_opcional=false` -- entre ellos los cuatro que el
-- dueno nombro y otros catorce que NO deberian bloquearse. `es_opcional` ademas ya tiene un
-- significado propio: se copia a cada `registro_habito` (ver HabitoJpaEntity linea 61,
-- RegistroService:168) y gobierna si no completarlo penaliza el puntaje -- es una regla de
-- puntaje, no una regla de "puede sacarse del plan". Pisar su significado le cambiaria el
-- puntaje a todos los aprendices sin que nadie lo pidiera. Se agrega una columna nueva,
-- independiente.
--
-- Nombre `desactivable` (adjetivo llano, mismo estilo que `activo`, no `es_x`/`puede_x`):
-- responde literalmente la pregunta que hace el interruptor de Plan ("se puede desactivar este
-- habito?"). DEFAULT true: deja a los 18 habitos no nombrados (y a cualquier habito PERSONAL o
-- de catalogo futuro que no se toque explicitamente) con el comportamiento que el dueno quiere
-- (toggle libre); solo se pisa a false en los cuatro nombrados, dentro de la misma migracion,
-- para que el catalogo nunca pase por un estado intermedio donde los cuatro esten desbloqueados.
--
-- Identificacion de los cuatro, verificada 1 a 1 contra V4__catalogo_habitos_default.sql (fuente
-- de origen real, no un titulo tipeado de memoria) ANTES de escribir este UPDATE:
--   - AUDIOTERAPIA SEMANAL   -> clave_sistema = 'AUDIO_THERAPY_WEEKLY'
--   - Pastilla Renacer       -> clave_sistema = 'PASTILLA_RENACER'
--   - Clase diaria           -> clave_sistema = 'DAILY_CLASS'
--   - POST DIARIO EN COMUNIDAD -> clave_sistema es NULL en el dato de origen (no lo trae, y no
--     se le inventa uno en esta migracion: agregarle una clave_sistema es una decision de
--     catalogo aparte, fuera de alcance de este cambio). El criterio mas estable disponible es
--     su `id` uuid: se preserva tal cual desde produccion (politica ya fijada en
--     docs/db/AUDITORIA_REDISENO_BD.md #14.8, "los ids uuid existentes se conservan") y es,
--     ademas, la propia primary key de la tabla -- mas estable todavia que una clave_sistema.
--     Un titulo NO se usa como criterio (se renombra; ver HabitRenameController) para ninguno de
--     los cuatro, ni siquiera para los tres que ya tienen clave_sistema.

BEGIN;

-- Explicito a proposito, mismo motivo que V3/V11/V12/V13/V17: si esta migracion corre sola en un
-- despliegue posterior, el search_path que fija V1 no esta puesto en la sesion.
SET search_path TO renaser, public;

ALTER TABLE habitos
    ADD COLUMN desactivable boolean NOT NULL DEFAULT true;

UPDATE habitos
   SET desactivable = false
 WHERE clave_sistema IN ('AUDIO_THERAPY_WEEKLY', 'PASTILLA_RENACER', 'DAILY_CLASS')
    OR id = '830c3d76-888a-4aef-bb30-fb0f0cc7ca73'; -- POST DIARIO EN COMUNIDAD (sin clave_sistema)

COMMIT;
