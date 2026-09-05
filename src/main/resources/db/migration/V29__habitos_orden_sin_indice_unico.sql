-- Retira el indice unico parcial que V28 creo sobre `habitos.orden` (mismo dia, 2026-09-04).
--
-- QUE SE DESHACE Y POR QUE. V28 agrego `habitos_catalogo_orden_unico_idx` (UNIQUE sobre `orden`
-- donde ambito='SISTEMA' AND activo) como red de seguridad contra el empate que motivo la
-- migracion: `Pastilla Renacer` y `POST DIARIO EN COMUNIDAD` compartian `orden = 12`.
--
-- El problema aparecio al correr la suite: la base de pruebas trae el catalogo real sembrado por
-- V1 + V28, es decir los valores 1..18 YA ESTAN OCUPADOS. Cualquier fixture que inserte un habito
-- de SISTEMA —lo hacen 10 clases de prueba, casi ninguna para probar el orden— choca contra el
-- indice: o cae en el `orden = 0` por defecto junto a otro fixture, o pisa uno de los 18 reales.
-- El costo real del indice no era "prevenir un empate" sino obligar a cada prueba presente y
-- futura a coordinar un numero global que no tiene nada que ver con lo que esta probando.
--
-- QUE SE CONSERVA. La renumeracion de V28 (1..18, sin huecos, con el empate resuelto en 12 y 13)
-- queda tal cual: es lo que el pedido necesitaba. El orden con el que sale el catalogo lo garantiza
-- el `ORDER BY orden, titulo` de `SpringDataHabitoRepository`, que se agrego en el mismo cambio y
-- es lo que de verdad hacia falta — antes esa consulta no tenia ORDER BY y el `orden` se ignoraba
-- fuera del panel admin.
--
-- QUE QUEDA SIN RED. Que dos habitos de sistema vuelvan a compartir `orden` pasa a ser un asunto
-- de revision, no una restriccion de la base. El impacto de que ocurra es acotado: el desempate
-- por `titulo` mantiene la salida estable dentro de una misma instalacion. Si algun dia hay panel
-- admin para reordenar, el lugar correcto para esta validacion es ese caso de uso —, que conoce el
-- conjunto completo— y no un indice que tambien alcanza a los fixtures.
--
-- POR QUE UNA MIGRACION NUEVA Y NO EDITAR V28: V28 ya se aplico (D-40 y la regla del repo: una
-- migracion aplicada no se edita, se agrega otra).

BEGIN;

SET search_path TO renaser, public;

DROP INDEX IF EXISTS habitos_catalogo_orden_unico_idx;

COMMENT ON COLUMN habitos.orden IS
    'Orden de presentacion del catalogo SISTEMA. Tras V28 son 1..18 sin huecos ni empates, pero '
    'la base NO lo impone (V29 retiro el indice unico: chocaba con los fixtures de prueba). Se '
    'cuida en revision. No es cronologico: es la secuencia curada del producto. Los habitos '
    'PERSONAL lo dejan en 0 y se ordenan por titulo detras del catalogo.';

COMMIT;
