-- V186 — Prender `auth.codigo_whatsapp` (el código de verificación por WhatsApp, spec 043) para todos (pedido del dueño)
--
-- Por qué se toca la base:
--   Nace apagada en el catálogo (`raizQueNaceApagada`, spec 043) hasta que Meta aprobara la plantilla de autenticación
--   `renaser_codigo_v1`. El dueño confirmó el 07/11/2026 que Meta ya la aprobó y pidió prenderla en producción sin pasar
--   por la consola de Soporte TI. Mientras está apagada, `GET /api/v1/auth/whatsapp-code` responde 404 y la app no ofrece
--   el código por WhatsApp ni al solicitar acceso ni al recuperar la contraseña.
--
-- Cómo: exactamente lo mismo que V185 (ver su cabecera): fila de `funcionalidades_estado` prendida para todos con versión
--   nueva, sin roles apagados, sin despliegue progresivo y una fila MANUAL en `cambios_auditados` por cada cambio.
--   Idempotente. El catálogo en código sigue diciendo que nace apagada: una base nueva nace con ella apagada.
--
-- Además del interruptor, el canal tiene que estar listo para enviar (`WhatsAppProperties.listoParaEnviar()`: clave de
--   YCloud, número emisor y la plantilla `WHATSAPP_PLANTILLA_CODIGO`, por defecto `renaser_codigo_v1`). Sin eso,
--   `available` sale `false` y la app sigue sin ofrecerlo aunque el interruptor esté prendido.

DO
$$
    DECLARE
        actor        CONSTANT uuid   := '00000000-0000-0000-0000-000000000000';
        motivo       CONSTANT text   := 'Prendida por pedido del dueño (migración V186)';
        dispositivo  CONSTANT text   := 'Flyway V186';
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

        FOREACH c IN ARRAY ARRAY ['auth.codigo_whatsapp']
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
                IF NOT (hay_fila AND prendida AND cardinality(apagados) = 0) THEN
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
                    ELSE
                        -- Sin fila rige el valor por defecto del catálogo: apagada, sin roles por defecto.
                        antes := jsonb_build_object('prendidaGlobal', false, 'porRol', todos_true,
                                                    'apagadaPara', '[]'::jsonb, 'aviso', NULL);
                        INSERT INTO renaser.funcionalidades_estado
                               (clave, prendida_global, aviso, version, actualizado_por, actualizado_en)
                        VALUES (c, true, NULL, nextval('renaser.funcionalidades_version_seq'), actor, now());
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
