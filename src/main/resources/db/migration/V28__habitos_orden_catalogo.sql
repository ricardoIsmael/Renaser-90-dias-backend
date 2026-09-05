-- Fija el ORDEN de presentacion del catalogo de habitos del sistema.
--
-- QUE PROBLEMA RESUELVE (pedido del dueno del producto, 2026-09-04: "que esten en ese orden"):
--
-- 1) EMPATE. `Pastilla Renacer` y `POST DIARIO EN COMUNIDAD` tenian ambos `orden = 12`. El unico
--    consumidor de la columna (`HabitosDeAprendizJdbcAdapter`, `ORDER BY h.orden, h.titulo`)
--    desempataba por titulo, que depende del collation de la base: el mismo catalogo podia salir
--    en distinto orden en dos instalaciones. Se separan en 12 y 13.
--    El desempate NO es arbitrario: se usa la hora de disparo de su horario (`horarios_habito`),
--    07:00 la Pastilla y 22:00 el Post. Es el unico criterio objetivo disponible; el `orden`
--    general NO es cronologico (la Clase diaria es 5 y dispara 14:59, el Jugo Verde es 6 y
--    dispara 09:00), asi que la secuencia curada NO se reordena por hora — solo el empate.
--
-- 2) HUECOS. La numeracion saltaba 13 -> 19 -> 29 -> 30 -> 31. No rompia nada, pero deja la
--    columna sin lugar obvio donde insertar y hace que cualquier lectura parezca incompleta.
--    Queda 1..18 corrida, respetando la secuencia relativa que ya existia.
--
-- POR QUE NO SE REUSA OTRA COLUMNA: `orden` ya existe y es exactamente esto. No se agrega ni se
-- renombra nada — esta migracion solo REPARA VALORES, igual que V13. La estructura sigue
-- congelada (D-40).
--
-- ALCANCE: solo `ambito = 'SISTEMA'`. Los habitos PERSONAL de cada aprendiz conservan su `orden`
-- (hoy 0 para todos) y se siguen ordenando por titulo detras del catalogo.
--
-- OJO, LO QUE ESTA MIGRACION NO HACE: por si sola NO cambia lo que ve la app movil. El endpoint
-- del catalogo (`findByAmbitoAndActivoTrue`) no tenia `ORDER BY`, asi que Postgres devolvia las
-- filas en orden indefinido y `orden` se ignoraba fuera del panel admin. El `ORDER BY` se agrega
-- en el mismo cambio, en `SpringDataHabitoRepository`.

BEGIN;

SET search_path TO renaser, public;

UPDATE habitos SET orden = v.orden
FROM (VALUES
    ( 1, 'DESPERTAR'),
    ( 2, 'AGUA TIBIA CON LIMÓN'),
    ( 3, 'RITUAL TIERRA - AGUA - FUEGO (mañana)'),
    ( 4, 'DÍA SIN CELULAR'),
    ( 5, 'Clase diaria'),
    ( 6, 'JUGO VERDE'),
    ( 7, 'PRIMERA COMIDA (ROMPO EL AYUNO)'),
    ( 8, 'RITUAL TIERRA - AGUA - FUEGO (mediodía)'),
    ( 9, 'ÚLTIMA COMIDA DEL DÍA'),
    (10, 'RITUAL TIERRA - AGUA - FUEGO (noche)'),
    (11, 'ESCRITURA LIBRE NOCTURNA'),
    (12, 'Pastilla Renacer'),
    (13, 'POST DIARIO EN COMUNIDAD'),
    (14, 'DORMIR'),
    (15, 'AUDIOTERAPIA SEMANAL'),
    (16, 'DESCANSO PROFUNDO'),
    (17, 'RITUAL DE MAÑANA (domingo)'),
    (18, 'AGUA E HIDRATACIÓN (domingo)')
) AS v(orden, titulo)
WHERE habitos.ambito = 'SISTEMA' AND habitos.titulo = v.titulo;

-- Red de seguridad: si manana alguien agrega un habito de sistema y lo deja en el `orden = 0` por
-- defecto, o repite un numero, el empate vuelve en silencio. Este indice lo convierte en un error
-- de escritura. Es UNIQUE parcial: solo sobre el catalogo ACTIVO del sistema, que es el unico
-- conjunto que se presenta ordenado; los habitos PERSONAL (todos en 0) y los desactivados quedan
-- fuera a proposito.
CREATE UNIQUE INDEX habitos_catalogo_orden_unico_idx
    ON habitos (orden)
    WHERE ambito = 'SISTEMA' AND activo;

COMMENT ON COLUMN habitos.orden IS
    'Orden de presentacion del catalogo SISTEMA (1..N, sin huecos ni empates — indice unico '
    'parcial habitos_catalogo_orden_unico_idx). No es cronologico: es la secuencia curada del '
    'producto. Los habitos PERSONAL lo dejan en 0 y se ordenan por titulo detras del catalogo.';

COMMIT;
