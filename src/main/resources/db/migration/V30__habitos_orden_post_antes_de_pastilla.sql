-- Corrige el desempate que V28 resolvio al reves entre `POST DIARIO EN COMUNIDAD` y
-- `Pastilla Renacer`.
--
-- CONTEXTO. Los dos compartian `orden = 12` en la base (ver cabecera de V28). Al no haber criterio
-- registrado, V28 los separo usando la hora de disparo de su horario —07:00 la Pastilla, 22:00 el
-- Post— y dejo Pastilla=12, Post=13.
--
-- POR QUE CAMBIA. Se consulto el panel de checklist del entorno de staging
-- (renaser-back-staging.vercel.app/habits), que es el catalogo curado a mano por el dueno del
-- producto y la fuente de verdad del orden. Ahi el orden de los 18 items ACTIVOS coincide
-- exactamente con el que dejo V28 salvo en estas dos posiciones: el Post va ANTES que la Pastilla.
-- El criterio de la hora de disparo era razonable pero era una inferencia mia, no la decision del
-- producto; se reemplaza por el dato real.
--
-- POR QUE NO SE EDITA V28: ya esta aplicada (D-40). Se corrige con esta.
--
-- El intercambio es directo, sin paso intermedio, porque V29 ya retiro el indice unico parcial
-- sobre `orden` que habria bloqueado el estado transitorio de un UPDATE fila por fila.

BEGIN;

SET search_path TO renaser, public;

UPDATE habitos
   SET orden = CASE titulo
                   WHEN 'POST DIARIO EN COMUNIDAD' THEN 12
                   WHEN 'Pastilla Renacer'         THEN 13
               END
 WHERE ambito = 'SISTEMA'
   AND titulo IN ('POST DIARIO EN COMUNIDAD', 'Pastilla Renacer');

COMMIT;
