-- Grupos que arma el ADMINISTRADOR con un periodo, y mentores con especialidad.
--
-- QUE CAMBIO
--
-- El cliente cambio el modelo de agrupacion (2026-09-11). Ya no hay rotacion ni traslado
-- automaticos: el administrador crea el grupo, le pone nombre y periodo ("septiembre, del 1 al 30,
-- se llama Fenix"), elige el mentor y mete a los alumnos. Al terminar el periodo el grupo se
-- cierra y deja de verse desde la app del alumno; solo queda para el administrador.
--
-- Los dos schedulers que rotaban y trasladaban quedan APAGADOS por configuracion, no borrados
-- (`renaser.scheduling.*.enabled`). El codigo y sus pruebas siguen ahi: ya cambiaron de modelo una
-- vez y volver a pedirlo es plausible.
--
-- POR QUE LAS FECHAS SON NULABLES
--
-- Las celulas que ya existen no tienen periodo y no deben empezar a vencerse por esta migracion.
-- `periodo_fin` nulo = el grupo no caduca, que es el comportamiento de hoy. Un DEFAULT inventado
-- (fin de mes, 30 dias) le pondria a cada grupo existente una fecha de muerte que nadie decidio.

SET search_path TO renaser, public;

-- ── Especialidad del mentor ─────────────────────────────────────────────────
-- Las tres que nombro el cliente. Un mentor tiene UNA, y el enum lo deja explicito: con un texto
-- libre, "Negocios" y "NEGOCIO" conviven y el filtro al armar el grupo empieza a fallar solo.
CREATE TYPE especialidad_mentor AS ENUM ('NEGOCIO', 'MENTE', 'RELACIONES');

ALTER TABLE perfiles_mentor
    ADD COLUMN especialidad especialidad_mentor;

COMMENT ON COLUMN perfiles_mentor.especialidad IS
    'En que se especializa. NULL = sin declarar todavia; los perfiles que ya existian quedan asi
     hasta que el administrador la complete.';

-- ── Periodo del grupo ───────────────────────────────────────────────────────
ALTER TABLE celulas
    ADD COLUMN periodo_inicio date,
    ADD COLUMN periodo_fin    date,
    -- El periodo es un rango cerrado por los dos extremos y se compara por DIA, no por instante:
    -- lo que el administrador escribe es "del 1 al 30", y el ultimo dia cuenta entero.
    ADD CONSTRAINT celulas_periodo_coherente
        CHECK (periodo_fin IS NULL OR periodo_inicio IS NULL OR periodo_fin >= periodo_inicio),
    -- Un periodo a medias es un error de quien lo creo, no un estado valido: sin inicio no se sabe
    -- desde cuando cuenta, y guardarlo asi deja al grupo en un limbo que ninguna consulta resuelve.
    ADD CONSTRAINT celulas_periodo_completo_o_ausente
        CHECK ((periodo_inicio IS NULL) = (periodo_fin IS NULL));

COMMENT ON COLUMN celulas.periodo_inicio IS
    'Primer dia del grupo, inclusive. NULL junto con periodo_fin = grupo sin periodo (no caduca).';
COMMENT ON COLUMN celulas.periodo_fin IS
    'ULTIMO dia del grupo, inclusive. Pasado ese dia el grupo se cierra y deja de verse desde la
     app del alumno.';

-- Buscar "que grupos vencen pronto" (aviso al administrador) y "cual es el grupo de recepcion
-- vigente hoy" (alta automatica al registrarse) son las dos consultas nuevas, y las dos filtran
-- por fecha. El indice parcial deja fuera las celulas sin periodo, que son la mayoria hoy.
CREATE INDEX celulas_periodo_idx ON celulas (periodo_fin, periodo_inicio) WHERE periodo_fin IS NOT NULL;
