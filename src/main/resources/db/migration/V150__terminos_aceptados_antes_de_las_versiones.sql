-- V150 — Quienes ya aceptaron los términos figuran como aceptantes de la versión 1 de la Escuela (spec 027; D-304)
--
-- Por qué se toca la base
-- -----------------------
-- Nadie vuelve a firmar por esto (SC-003): todas las aceptaciones de antes de las versiones se atribuyen a la versión 1 de la Escuela, con la fecha que ya
-- tenían (`estado_onboarding.terminos_aceptados_en`) y la firma que ya habían dibujado (la respuesta a `terms_signature`, que apunta a `medias_onboarding`).
-- Si el texto cambió alguna vez antes de las versiones no hay copia: se les atribuye la versión 1 igualmente (pregunta Q5 de la spec, supuesto A: nunca cambió).

SET search_path TO renaser, public;

INSERT INTO aceptaciones_de_terminos (id, usuario_id, version_id, aceptada_en, firma_media_id)
SELECT gen_random_uuid(), e.usuario_id, '00000000-0000-4000-8000-000000000148', e.terminos_aceptados_en,
       (SELECT r.media_id
          FROM respuestas_onboarding r
          JOIN preguntas_onboarding p ON p.id = r.pregunta_id
         WHERE r.usuario_id = e.usuario_id AND p.clave_pregunta = 'terms_signature'
         LIMIT 1)
FROM estado_onboarding e
WHERE e.terminos_aceptados_en IS NOT NULL;
