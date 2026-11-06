-- V185 — Prender `eventos.meet`, `eventos.meet.transcripcion` y `eventos.whatsapp` para todos (pedido del dueño)
--
-- Por qué se toca la base:
--   Las tres nacen apagadas en el catálogo (specs 040, 047 y 041: `apagadaDeFabrica` / `naceApagada`) y, mientras nadie
--   guarda una fila en `funcionalidades_estado` (V101), quedan escondidas (404 «no disponible»). El dueño pidió
--   prenderlas en producción para todos y para todos los roles sin pasar por la consola de Soporte TI. Esta migración
--   hace exactamente lo que haría la consola al prenderlas «para todos»:
--     * deja la fila de `funcionalidades_estado` con `prendida_global = true` (la crea si no existe) y sin excepciones
--       por rol en `funcionalidades_estado_rol` (esa tabla guarda solo roles APAGADOS);
--     * le da una `version` nueva de `funcionalidades_version_seq` (la misma secuencia del bloqueo optimista de la
--       consola). La mayor versión es el ETag de `GET /funcionalidades/mias`: al subir, las apps vuelven a leer;
--     * si alguna estaba en despliegue progresivo (V113), borra la fila de `funcionalidades_despliegue` (sus probadores
--       se van por CASCADE): «Prender para todos» (100 %) es justamente borrarla;
--     * deja en `cambios_auditados` una fila MANUAL por cada cambio, con el «antes» y el «después» en la misma forma que
--       escribe la consola (`prendidaGlobal`, `porRol` con los siete roles de la API, `apagadaPara`, `aviso`; y
--       `{"despliegue": …}` para el despliegue) y el motivo «Prendida por pedido del dueño (migración V185)».
--
-- Qué NO cambia:
--   * El catálogo en código sigue diciendo que nacen apagadas: una base nueva (local, pruebas) nace con las tres
--     apagadas hasta que esta migración (o Soporte TI) las prende.
--   * Los roles personalizados (V115): como en la consola, prender una funcionalidad no la marca en ningún rol
--     personalizado. Quien tenga uno solo la ve si Soporte TI la marcó en su rol.
--   * El `aviso` que la fila tuviera se conserva (solo se muestra estando apagada).
--
-- Actor: una migración no tiene sesión. `actor_id` y `actualizado_por` no llevan FK a `usuarios` (V101), así que va un
--   id fijo que no es de nadie (todo ceros); la consola muestra el cambio sin nombre y con el motivo de arriba, y
--   `dispositivo` dice que vino de Flyway.
--
-- Idempotente: una funcionalidad que ya está prendida para todos y sin despliegue no se toca (ni versión nueva ni fila
--   de auditoría). Una clave sin fila se crea. No falla con ninguna combinación de filas existentes.
--
-- Caché: cada instancia guarda una copia en memoria que se relee al arrancar (después de Flyway) y cada 30 s
--   (`RecargarEstadosScheduler`, `RecargarDesplieguesScheduler`), así que una instancia vieja que siga viva durante el
--   despliegue también lo ve en menos de un minuto. No hay nada que invalidar en Redis: el canal solo avisa versiones.

DO
$$
    DECLARE
        actor        CONSTANT uuid   := '00000000-0000-0000-0000-000000000000';
        motivo       CONSTANT text   := 'Prendida por pedido del dueño (migración V185)';
        dispositivo  CONSTANT text   := 'Flyway V185';
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

        FOREACH c IN ARRAY ARRAY ['eventos.meet', 'eventos.meet.transcripcion', 'eventos.whatsapp']
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
