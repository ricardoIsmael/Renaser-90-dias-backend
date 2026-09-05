-- ============================================================================
-- DESPERTAR y DORMIR: se les da `clave_sistema` para poder darles flujo propio
-- ============================================================================
--
-- Que problema resuelve
-- ---------------------
-- El dueno del producto pidio (2026-09-04) que en estos dos habitos el boton de evidencia
-- "registre la hora y nada mas que lo presiono": no hay archivo que subir ni texto que
-- escribir, la evidencia ES el instante en que la persona toco el boton (que ya se guarda en
-- `registros_habito.completado_en`). Para que el movil pueda desviar esos dos habitos al
-- camino corto hay que poder reconocerlos por identidad funcional, y ninguno de los dos
-- tenia `clave_sistema` (NULL en V4). Mismo problema y misma solucion que V24 para
-- POST DIARIO EN COMUNIDAD — leer ahi por que no sirve `titulo` ni `icono_clave`
-- (los dos comparten `icono_clave = 'SLEEP'`, asi que el icono ni siquiera los distingue).
--
-- Por que estos nombres
-- ---------------------
-- SCREAMING_SNAKE en ingles como las siete claves existentes. 'SLEEP' para DORMIR coincide
-- con su `icono_clave`; 'WAKE_UP' para DESPERTAR porque no hay valor previo que preservar.
-- La columna es UNIQUE: verificado contra V4/V9/V24 que ningun habito usa ninguna de las dos.
--
-- Que NO cambia
-- -------------
-- Nada del backend depende de estas claves todavia: `RegistroPoliticasHabito.para()` cae en la
-- politica generica cuando no hay politica para la clave, asi que darles clave no altera como
-- se completan. El efecto observable esta en el movil, que desvia por `systemKey`.
-- Lo que SI cambia para DESPERTAR — que paga 10 puntos en vez de 0 — no viene de esta
-- migracion sino de D-97 en `RegistroService.completar` (sin horario, la hora de la accion es
-- el ancla).
-- ============================================================================

BEGIN;

SET search_path TO renaser, public;

UPDATE habitos
   SET clave_sistema = 'WAKE_UP',
       actualizado_en = now()
 WHERE id = '899a2151-e98c-4b61-a46c-b55134240d17' -- DESPERTAR
   AND clave_sistema IS NULL;

UPDATE habitos
   SET clave_sistema = 'SLEEP',
       actualizado_en = now()
 WHERE id = '48d68c12-72a9-428c-a8c6-02b9b01bc2fe' -- DORMIR
   AND clave_sistema IS NULL;

COMMIT;
