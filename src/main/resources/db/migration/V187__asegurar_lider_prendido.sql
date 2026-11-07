-- V187 — Asegurar `lider` («Mis mentores») y `lider.cumplimiento` prendidas para todos (pedido del dueño, 2026-11-07)
--
-- Por qué se toca la base:
--   El dueño no ve en producción todo lo del líder de mentores (el padrón de mentores con su ficha cuelga de `lider`; las
--   personas, de `lider.cumplimiento`) y pidió prender el interruptor sin pasar por la consola de Soporte TI. Las dos
--   nacen PRENDIDAS en el catálogo (`normal`): si no hay fila, ya rigen prendidas y esta migración no hace nada. Si
--   alguien las apagó desde la consola (fila con `prendida_global = false`, roles apagados o despliegue progresivo), las
--   deja como la consola al prender «para todos»: igual que V185 (ver su cabecera), con versión nueva, sin roles
--   apagados, sin despliegue y una fila MANUAL de auditoría por cambio.
--
-- Diferencia con V185/V186: nunca crea una fila (sin fila ya están prendidas por el catálogo).
-- Idempotente.

DO
$$
    DECLARE
        actor        CONSTANT uuid   := '00000000-0000-0000-0000-000000000000';
        motivo       CONSTANT text   := 'Prendida por pedido del dueño (migración V187)';
        dispositivo  CONSTANT text   := 'Flyway V187';
        roles_api    CONSTANT text[] := ARRAY ['IT_SUPPORT', 'ALCHEMIST', 'ADMIN', 'MENTOR_LEAD', 'MENTOR',
                                              'TRAINEE_GUIDE', 'TRAINEE'];
        todos_true   jsonb;
        c            text;
        hay_fila     boolean;
        prendida     boolean;
        aviso_actual text;
        apagados     text[];
        porcentaje   smallint;
        probadores   integer;
        antes        jsonb;
        despues      jsonb;
    BEGIN
        SELECT jsonb_object_agg(r, true) INTO todos_true FROM unnest(roles_api) AS r;

        FOREACH c IN ARRAY ARRAY ['lider', 'lider.cumplimiento']
            LOOP
                SELECT true, e.prendida_global, e.aviso
                  INTO hay_fila, prendida, aviso_actual
                  FROM renaser.funcionalidades_estado e
                 WHERE e.clave = c
                   FOR UPDATE;
                hay_fila := coalesce(hay_fila, false);

                -- Los roles apagados, con el nombre de la API (RolEnLaBase), ordenados como los escribe la consola.
                SELECT coalesce(array_agg(api ORDER BY api), '{}')
                  INTO apagados
                  FROM (SELECT CASE r.rol::text
                                   WHEN 'SOPORTE_TI' THEN 'IT_SUPPORT'
                                   WHEN 'ALQUIMISTA' THEN 'ALCHEMIST'
                                   WHEN 'ADMIN' THEN 'ADMIN'
                                   WHEN 'LIDER_MENTORES' THEN 'MENTOR_LEAD'
                                   WHEN 'MENTOR' THEN 'MENTOR'
                                   WHEN 'GUIA_APRENDIZ' THEN 'TRAINEE_GUIDE'
                                   WHEN 'APRENDIZ' THEN 'TRAINEE'
                                   ELSE r.rol::text
                                   END AS api
                          FROM renaser.funcionalidades_estado_rol r
                         WHERE r.clave = c
                           AND NOT r.prendida) x;

                SELECT d.porcentaje,
                       (SELECT count(*) FROM renaser.funcionalidades_probadores p WHERE p.clave = d.clave)
                  INTO porcentaje, probadores
                  FROM renaser.funcionalidades_despliegue d
                 WHERE d.clave = c;

                -- 1. El interruptor: prendido para todos y sin roles apagados.
                IF hay_fila AND NOT (prendida AND cardinality(apagados) = 0) THEN
                    IF hay_fila THEN
                        antes := jsonb_build_object(
                                'prendidaGlobal', prendida,
                                'porRol', (SELECT jsonb_object_agg(r, NOT (r = ANY (apagados))) FROM unnest(roles_api) AS r),
                                'apagadaPara', to_jsonb(apagados),
                                'aviso', aviso_actual);
                        UPDATE renaser.funcionalidades_estado
                           SET prendida_global = true,
                               version         = nextval('renaser.funcionalidades_version_seq'),
                               actualizado_por = actor,
                               actualizado_en  = now()
                         WHERE clave = c;
                        DELETE FROM renaser.funcionalidades_estado_rol WHERE clave = c;
                    END IF;
                    despues := jsonb_build_object('prendidaGlobal', true, 'porRol', todos_true,
                                                  'apagadaPara', '[]'::jsonb, 'aviso', aviso_actual);
                    INSERT INTO renaser.cambios_auditados
                           (id, tipo, actor_id, clave, antes, despues, motivo, dispositivo, creado_en)
                    VALUES (gen_random_uuid(), 'MANUAL', actor, c, antes, despues, motivo, dispositivo, now());
                END IF;

                -- 2. El despliegue progresivo: «para todos» es no tener fila.
                IF porcentaje IS NOT NULL THEN
                    DELETE FROM renaser.funcionalidades_despliegue WHERE clave = c;
                    -- La versión del estado tiene que subir aunque el interruptor ya estuviera prendido: es el ETag.
                    UPDATE renaser.funcionalidades_estado
                       SET version         = nextval('renaser.funcionalidades_version_seq'),
                           actualizado_por = actor,
                           actualizado_en  = now()
                     WHERE clave = c;
                    INSERT INTO renaser.cambios_auditados
                           (id, tipo, actor_id, clave, antes, despues, motivo, dispositivo, creado_en)
                    VALUES (gen_random_uuid(), 'MANUAL', actor, c,
                            jsonb_build_object('despliegue',
                                               jsonb_build_object('porcentaje', porcentaje, 'probadores', probadores)),
                            jsonb_build_object('despliegue', NULL), motivo, dispositivo, now());
                END IF;

                hay_fila := NULL;
                prendida := NULL;
                aviso_actual := NULL;
                porcentaje := NULL;
                probadores := NULL;
            END LOOP;
    END
$$;
