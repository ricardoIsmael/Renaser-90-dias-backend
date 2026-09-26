-- ============================================================================
-- RITUAL TIERRA - AGUA - FUEGO (manana, mediodia, noche): se les da `clave_sistema`
-- ============================================================================
--
-- Que problema resuelve
-- ---------------------
-- El dueno del producto decidio (2026-09-26, D-172) que al registrar con foto un habito que exige
-- evidencia, la pregunta "¿Que sentiste?" salga SOLO en los tres rituales; el agua con limon, el
-- jugo verde y las comidas se registran con la foto y nada mas. La app y el acompanante tienen que
-- reconocer los rituales por identidad funcional, y ninguno de los tres tenia `clave_sistema`
-- (NULL en V4). Mismo problema y misma solucion que V24 y V26.
--
-- Por que no se reusa otra columna
-- --------------------------------
-- `titulo` lo puede cambiar un admin. `icono_clave` es presentacion: un habito personal puede
-- elegir el mismo icono (`CreatePersonalHabitRequest` acepta `iconKey`). `grupo` existe en la base
-- pero no se expone por la API (`MiHabitoResponse`), y es una etiqueta de agrupacion, no una
-- identidad: sacarlo a la respuesta para esto seria filtrar un dato de catalogo mas para una sola
-- regla.
--
-- Por que estos nombres
-- ---------------------
-- SCREAMING_SNAKE en ingles como las claves existentes, y los mismos valores que ya tienen como
-- `icono_clave` (RITUAL_MORNING / RITUAL_MIDDAY / RITUAL_NIGHT), asi no hay un segundo nombre
-- para la misma cosa. La columna es UNIQUE: verificado que ningun habito usa ninguna de las tres
-- como `clave_sistema` (en V4/V9/V24/V26 esos valores solo aparecen como `icono_clave`).
--
-- Que NO cambia
-- -------------
-- `RegistroPoliticasHabito.para()` cae en la politica generica cuando no hay politica para la
-- clave, asi que darles clave no altera como se completan ni cuantos puntos pagan. El efecto
-- observable esta en la app (que decide por `systemKey`) y en la tarjeta de la camara del
-- acompanante (campo `conPregunta`).
-- ============================================================================

BEGIN;

SET search_path TO renaser, public;

UPDATE habitos
   SET clave_sistema = 'RITUAL_MORNING',
       actualizado_en = now()
 WHERE id = '4dee0fa3-e285-4b7d-b062-ad0001dde314' -- RITUAL TIERRA - AGUA - FUEGO (manana)
   AND clave_sistema IS NULL;

UPDATE habitos
   SET clave_sistema = 'RITUAL_MIDDAY',
       actualizado_en = now()
 WHERE id = '63acbd12-9792-495c-be22-6280ecba53b3' -- RITUAL TIERRA - AGUA - FUEGO (mediodia)
   AND clave_sistema IS NULL;

UPDATE habitos
   SET clave_sistema = 'RITUAL_NIGHT',
       actualizado_en = now()
 WHERE id = '679188b9-7c1d-48ec-ae09-8a76b87badbf' -- RITUAL TIERRA - AGUA - FUEGO (noche)
   AND clave_sistema IS NULL;

COMMIT;
