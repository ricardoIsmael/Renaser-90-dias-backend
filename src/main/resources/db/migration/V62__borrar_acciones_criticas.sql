-- =============================================================================
-- V62 — Borrar `acciones_criticas`, la tabla de acciones que colgaba de la semana
-- =============================================================================
--
-- QUE PROBLEMA RESUELVE
--
-- La V61 movio las acciones del objetivo semanal al diario (`acciones_diarias`) y dejo
-- `acciones_criticas` en pie, marcada como historica, porque desde el entorno local no se podia
-- verificar que estuviera vacia en produccion. El dueno lo confirmo el 2026-09-22. Esta es la
-- migracion que la borra.
--
-- POR QUE ESTA MIGRACION NO PUEDE FALLAR NUNCA  (leer esto antes de "endurecerla")
--
-- La primera version de este archivo hacia `RAISE EXCEPTION` si encontraba filas, para no borrar
-- datos por error. La intencion era correcta y el efecto fue un incidente: Flyway corre al
-- arrancar, una migracion que falla impide que la aplicacion levante, y el contenedor de
-- produccion quedo reiniciandose en loop. CloudFront devolvia 504 a todo el padron (E-210).
--
-- La leccion es que eran DOS reglas distintas atadas en una:
--
--   1. "no borres datos que no esperabas encontrar"  -> se cumple, y se sigue cumpliendo
--   2. "no dejes arrancar la aplicacion"             -> NUNCA fue lo que se quiso
--
-- Un DROP de limpieza es opcional por naturaleza: si no se puede hacer hoy, la tabla queda un dia
-- mas sin molestar a nadie. No hay ni una linea de codigo que la lea. Que eso tire abajo el
-- servicio es desproporcionado en cualquier escenario.
--
-- Entonces esta migracion **reporta y sigue**. Los tres caminos que no borran dejan un WARNING en
-- el log de arranque, que es donde alguien lo va a ver, y devuelven el control sin romper nada:
--
--   * la tabla ya no existe          -> NOTICE, no hay nada que hacer (idempotente)
--   * la tabla tiene filas           -> WARNING con cuantas, y NO se borra
--   * el DROP no se puede ejecutar   -> WARNING con el error exacto de Postgres, y NO se borra
--
-- El tercer caso es el que no se podia ver en pruebas: en local se corre como `postgres`
-- superusuario y en produccion con el usuario de la aplicacion, que puede no ser dueno de la
-- tabla. `DROP TABLE` exige ser dueno, no alcanza con permisos de escritura.
--
-- QUE PASA SI HAY QUE VOLVER ATRAS
--
-- La definicion original esta en la V1 (lineas 702-708) y se puede recrear tal cual. Si la tabla
-- se borro es porque estaba vacia, asi que no hay datos que recuperar.
-- =============================================================================

DO $$
DECLARE
    filas bigint;
BEGIN
    IF to_regclass('renaser.acciones_criticas') IS NULL THEN
        RAISE NOTICE 'V62: renaser.acciones_criticas ya no existe; nada que borrar.';
        RETURN;
    END IF;

    EXECUTE 'SELECT count(*) FROM renaser.acciones_criticas' INTO filas;

    IF filas > 0 THEN
        RAISE WARNING 'V62: renaser.acciones_criticas tiene % fila(s), asi que NO se borra. '
                      'Las acciones viven en renaser.acciones_diarias desde la V61. La tabla queda '
                      'sin escritores; migra o descarta esas filas y borrala a mano cuando quieras.',
                      filas;
        RETURN;
    END IF;

    BEGIN
        EXECUTE 'DROP TABLE renaser.acciones_criticas';
        RAISE NOTICE 'V62: renaser.acciones_criticas borrada (estaba vacia).';
    EXCEPTION
        WHEN OTHERS THEN
            -- Se traga cualquier error a proposito: ver "POR QUE ESTA MIGRACION NO PUEDE FALLAR
            -- NUNCA" arriba. El caso esperado es 42501 (must be owner of table).
            RAISE WARNING 'V62: no se pudo borrar renaser.acciones_criticas (%: %). La tabla queda '
                          'como esta, sin escritores. No es bloqueante.', SQLSTATE, SQLERRM;
    END;
END
$$;
