-- Destraba las evidencias que quedaron esperando una revision que nunca iba a llegar.
--
-- POR QUE SE TOCA LA BASE
--
-- La validacion por IA de evidencias salio del alcance (D-76): su adaptador es un NoOp que
-- siempre devuelve NO_DISPONIBLE. Con eso, cada evidencia gastaba sus tres intentos contra una
-- IA inexistente y caia en REVISION_MANUAL, estado del que solo puede sacarla un administrador
-- desde una pantalla que nunca se construyo. No era un caso raro: era el destino de TODAS.
--
-- Medido en la base el 2026-09-16, antes de esta migracion:
--   REVISION_MANUAL  9   (la mas vieja del 08/09)
--   VALIDA           1
--
-- Por decision del dueno (2026-09-16), subir la evidencia alcanza y no hace falta revisarla. El
-- agregado ya nace VALIDA desde el mismo cambio; esto arrastra a las que quedaron atrapadas
-- antes, que si no se quedarian ahi para siempre.
--
-- POR QUE SOLO REVISION_MANUAL
--
-- No se tocan RECHAZADA ni ANULADA_ADMIN: esas son decisiones que alguien tomo a proposito y
-- reescribirlas seria borrar un juicio humano. PENDIENTE tampoco: si quedara alguna en vuelo, el
-- codigo nuevo ya no las produce y el cron dejara de tener trabajo. Esta migracion arregla
-- exactamente el estado que el sistema generaba solo y nadie podia resolver.
--
-- NO ES REVERSIBLE con los datos que hay: al pasar a VALIDA se pierde cuales estaban en revision.
-- Se acepta porque ese estado no representaba una decision de nadie, solo la ausencia de una IA.
UPDATE renaser.evidencias
   SET estado_validacion = 'VALIDA',
       notas_validacion  = COALESCE(notas_validacion, 'Validada automaticamente: la revision por IA salio del alcance (D-76)')
 WHERE estado_validacion = 'REVISION_MANUAL';
