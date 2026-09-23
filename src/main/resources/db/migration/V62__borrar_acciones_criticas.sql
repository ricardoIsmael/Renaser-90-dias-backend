-- =============================================================================
-- V62 — Borrar `acciones_criticas`, la tabla de acciones que colgaba de la semana
-- =============================================================================
--
-- QUE PROBLEMA RESUELVE
--
-- La V61 movio las acciones del objetivo semanal al objetivo diario (`acciones_diarias`) y dejo
-- `acciones_criticas` en pie, marcada como historica, con este motivo escrito en su cabecera:
--
--   "Porque no se puede verificar desde aca que este vacia en produccion. En la base local tiene
--    cero filas, pero cero local no es cero en produccion (...) si el dueno confirma que quedo
--    vacia, se borra en una migracion posterior de una linea."
--
-- El dueno lo confirmo el 2026-09-22: "ya confirme que esta vacia". Esta es esa migracion.
--
-- POR QUE NO ES "UNA LINEA", COMO DECIA LA V61
--
-- Porque un `DROP TABLE` pelado borra igual de bien una tabla vacia que una con datos, y la
-- confirmacion de que esta vacia es de una persona mirando una base, no algo que la migracion
-- pueda comprobar por si misma. Si en algun entorno quedara una fila, un DROP pelado se la llevaria
-- sin decir nada y sin vuelta atras.
--
-- Entonces la migracion comprueba primero y aborta si encuentra algo. El resultado es que esta
-- migracion SOLO puede borrar una tabla vacia: en un entorno con datos no rompe nada, falla al
-- arrancar con un mensaje que dice exactamente cuantas filas encontro, y se decide que hacer con
-- ellas antes de tocarlas. Es la diferencia entre "confio en que esta vacia" y "no se borra si no
-- lo esta".
--
-- POR QUE SE BORRA Y NO SE DEJA VACIA PARA SIEMPRE
--
-- Porque una tabla sin escritores no queda quieta: queda como invitacion. El mapeo
-- `@ElementCollection` que la leia ya se saco de `RocaSemanalJpaEntity` en este mismo cambio, asi
-- que a partir de aca nadie la escribe ni la lee. Una tabla viva que nadie usa es la que, dentro de
-- seis meses, alguien "arregla" volviendo a colgarle acciones a la semana — que es exactamente el
-- diseno del que se salio.
--
-- QUE PASA SI HAY QUE VOLVER ATRAS
--
-- La definicion original esta en la V1 (lineas 702-708) y se puede recrear tal cual. Los datos no
-- se pueden recuperar, y por eso la comprobacion de arriba: solo se borra cuando no hay ninguno.
-- =============================================================================

DO $$
DECLARE
    filas bigint;
BEGIN
    IF to_regclass('renaser.acciones_criticas') IS NULL THEN
        RAISE NOTICE 'renaser.acciones_criticas ya no existe; no hay nada que borrar.';
        RETURN;
    END IF;

    EXECUTE 'SELECT count(*) FROM renaser.acciones_criticas' INTO filas;

    IF filas > 0 THEN
        RAISE EXCEPTION
            'renaser.acciones_criticas tiene % fila(s) y esta migracion solo borra la tabla vacia. '
            'Las acciones viven en renaser.acciones_diarias desde la V61: migra o descarta esas '
            'filas a mano y volve a correr.', filas;
    END IF;

    EXECUTE 'DROP TABLE renaser.acciones_criticas';
END
$$;
