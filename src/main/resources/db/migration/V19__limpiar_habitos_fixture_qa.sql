-- Saca del catalogo los 5 habitos de fixture de QA que quedaron vivos en la base y que nunca
-- existieron en produccion, y cierra el agujero por el que entro el duplicado de 'DÍA SIN CELULAR'.
--
-- ── Como se detectaron ───────────────────────────────────────────────────────────────────────
--
-- El dueno del proyecto reporto que la app mostraba habitos que no existen y que ademas no se
-- podian evidenciar. Se cruzo el catalogo servido por este backend contra la fuente real, dos
-- veces y por dos caminos independientes:
--
--   1) `docs/db/migracion/datos_origen/dump_completo.sql` (dump de produccion del 2026-08-28,
--      tabla `public.habits`): 27 filas, 18 con is_active=true y 9 con is_active=false.
--   2) El panel de produccion en vivo (renaser-back-staging.vercel.app/habits), leido pagina por
--      pagina el 2026-09-03: TOTAL 27 · ACTIVOS 18 · INACTIVOS 9, y por eje Cuerpo 14 / Mente 5 /
--      Emociones 2 / Espiritu 6. Identico al dump: 0 altas, 0 bajas y 0 renombres desde el dump,
--      o sea que la linea base seguia vigente.
--
-- `GET /api/v1/admin/habits` de este backend devolvia 31 (22 activos + 9 inactivos) y
-- `GET /api/v1/habits` devolvia 23 para el participante 11111111-…. La diferencia son estos 5.
--
-- ── Por que son fixtures y no catalogo ──────────────────────────────────────────────────────
--
-- Los 5 tienen uuid de fixture (digito repetido), ninguno aparece en el dump ni en el panel, y
-- ninguno esta en V4 ni en V9 -- se insertaron a mano en la base durante las pruebas y nunca se
-- sacaron. V9 (linea 11) y V18 (linea 3) ya los venian contando sin darse cuenta: hablan de "los
-- 22 habitos de sistema" y "los 22 activos" cuando el catalogo real tiene 18.
--
--   61111111-1111-1111-1111-111111111111  Beber 2L de agua        SISTEMA  CHECKBOX  CUERPO
--   62222222-2222-2222-2222-222222222221  Santuario nocturno A    SISTEMA  BLOCKING  MENTE
--   62222222-2222-2222-2222-222222222222  Santuario nocturno B    SISTEMA  BLOCKING  MENTE
--   63333333-3333-3333-3333-333333333333  Dia sin celular         SISTEMA  CHECKBOX  MENTE
--   64444444-4444-4444-4444-444444444444  Habito de prueba QA     PERSONAL CHECKBOX  CUERPO
--
-- Los dos BLOCKING son los unicos del catalogo entero: el catalogo real no tiene ninguno.
--
-- ── Por que ademas no se podian evidenciar ──────────────────────────────────────────────────
--
-- Los 4 de SISTEMA no tienen ninguna fila en `horarios_habito` (verificado:
-- `GET /api/v1/admin/habits/{id}/schedules` devuelve [] para los cuatro). Sin horario,
-- `GenerarTracksDelDia` no les crea `registro_habito`, asi que salian en el plan del aprendiz y
-- nunca aparecian en el dia ni se podian cerrar. Ese es exactamente el sintoma reportado.
--
-- ── Historial que se borra, y por que es seguro ─────────────────────────────────────────────
--
-- OJO: estos 5 SI tienen historial, 12 `registros_habito` en total, asi que un DELETE directo
-- sobre `habitos` choca contra el FK `ON DELETE RESTRICT` de `registros_habito` (V1 linea 558,
-- P-02: "el catalogo NO arrastra historial"). Por eso esta migracion borra primero los registros.
--
-- Los 12 registros son TODOS del participante `11111111-1111-1111-1111-111111111111` -- el mismo
-- uuid de fixture, no un aprendiz real (verificado 1 a 1 contra `registros_habito` antes de
-- escribir este archivo). Ningun habito real esta en la lista de ids, asi que este DELETE no
-- puede tocar el historial de nadie: el filtro es por `habito_id`, no por participante.
--
-- Lo que se lleva la cascada (contado antes, no estimado): 12 registros_habito -> 3 evidencias,
-- 4 sesiones_bloqueo, 3 rachas_sin_celular, 1 evento_verdugo (todos ON DELETE CASCADE), mas
-- 1 preferencia_horario colgando del habito. Los otros 15 registros de HOY de ese mismo
-- participante, los de los habitos reales, no se tocan.
--
-- ── El indice: por que se cambia ────────────────────────────────────────────────────────────
--
-- `habitos_titulo_sistema_uk` (V1 linea 417) es UNIQUE sobre `titulo` crudo, asi que
-- 'Dia sin celular' pudo entrar al lado del real 'DÍA SIN CELULAR': difieren en mayusculas y en
-- la tilde. Se reemplaza por un indice sobre el titulo normalizado.
--
-- Se usa `lower(translate(...))` y NO `unaccent()`: `unaccent` es STABLE, no IMMUTABLE, y
-- Postgres rechaza el indice con "functions in index expression must be marked IMMUTABLE"
-- (probado contra la base real antes de escribir esto). Envolverlo en una funcion IMMUTABLE
-- propia funciona, pero obliga a fijar en que esquema vive la extension -- y en el deploy real
-- (Supabase) las extensiones no viven donde las pone un `CREATE EXTENSION` con este search_path,
-- asi que la migracion podria pasar en local y fallar en produccion. `translate` es built-in e
-- IMMUTABLE, no necesita extension, y cubre todo el alfabeto castellano, que es lo unico que
-- este catalogo usa.

BEGIN;

-- Explicito a proposito, mismo motivo que V3/V11/V12/V13/V17/V18: si esta migracion corre sola
-- en un despliegue posterior, el search_path que fija V1 no esta puesto en la sesion.
SET search_path TO renaser, public;

-- 1) El historial de fixture primero: sin esto, el DELETE de abajo choca con el FK RESTRICT.
--    El filtro es por habito_id (los 5 fixtures), nunca por participante: ningun habito de
--    catalogo real esta en la lista, asi que no hay forma de que se lleve historial legitimo.
DELETE FROM registros_habito
 WHERE habito_id IN (
   '61111111-1111-1111-1111-111111111111',  -- Beber 2L de agua
   '62222222-2222-2222-2222-222222222221',  -- Santuario nocturno A
   '62222222-2222-2222-2222-222222222222',  -- Santuario nocturno B
   '63333333-3333-3333-3333-333333333333',  -- Dia sin celular (duplicado del real)
   '64444444-4444-4444-4444-444444444444'   -- Habito de prueba QA (PERSONAL)
 );

-- 2) Los habitos. El resto de sus dependencias (horarios, preferencias, guias, renombres,
--    desbloqueos, dias semanales, historial de cambios de horario) son ON DELETE CASCADE.
DELETE FROM habitos
 WHERE id IN (
   '61111111-1111-1111-1111-111111111111',
   '62222222-2222-2222-2222-222222222221',
   '62222222-2222-2222-2222-222222222222',
   '63333333-3333-3333-3333-333333333333',
   '64444444-4444-4444-4444-444444444444'
 );

-- 3) Que no pueda volver a entrar un duplicado que solo difiere en mayusculas o tildes.
DROP INDEX IF EXISTS habitos_titulo_sistema_uk;
CREATE UNIQUE INDEX habitos_titulo_sistema_uk
    ON habitos (lower(translate(titulo, 'ÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑÇáàäâéèëêíìïîóòöôúùüûñç',
                                        'AAAAEEEEIIIIOOOOUUUUNCaaaaeeeeiiiioooouuuunc')))
 WHERE ambito = 'SISTEMA';  -- los personales pueden repetir titulo entre aprendices (igual que V1)

-- 4) Red de seguridad: si el catalogo de sistema no queda exactamente en 27 filas (18 activas +
--    9 inactivas, lo que dice el panel de produccion), algo mas se movio y esta migracion aborta
--    en vez de dejar la base en un estado que nadie reviso.
DO $$
DECLARE
    total   integer;
    activos integer;
BEGIN
    SELECT count(*), count(*) FILTER (WHERE activo)
      INTO total, activos
      FROM habitos
     WHERE ambito = 'SISTEMA';

    IF total <> 27 OR activos <> 18 THEN
        RAISE EXCEPTION
            'Catalogo SISTEMA inesperado tras la limpieza: % filas (% activas). Esperado 27 (18 activas).',
            total, activos;
    END IF;
END $$;

COMMIT;
