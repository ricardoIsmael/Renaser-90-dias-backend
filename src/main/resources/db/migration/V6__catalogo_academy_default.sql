-- Catalogo de cursos/academia migrado desde produccion (proyecto Supabase qchpxyaiipghayyfmthg,
-- tablas cursos/curso_secciones/lecciones/leccion_recursos), mismo origen y misma politica que
-- V4/V5: solo catalogo, cero datos de usuario, ids de produccion preservados.
--
-- Perfilado (extract + profile), igual disciplina que V4/V5:
--   - 23 cursos, 171 secciones, 473 lecciones, 18 recursos.
--   - 0 huerfanas (secciones/lecciones con curso_id invalido; lecciones con seccion_id invalido;
--     recursos con leccion_id invalido) -- verificado contra los propios ids migrados, no asumido.
--   - 0 duplicados de id en las 3 tablas con id natural (cursos/secciones/lecciones).
--   - 0 violaciones del CHECK `video_coherente` (video_tipo IS NULL OR video_url IS NOT NULL).
--   - `curso_asignaciones` (23 filas) y `grupos`/`grupo_miembros` (0 filas) se EXCLUYEN a
--     proposito: `curso_asignaciones` es dato de usuario (asigna un curso a un user_id real,
--     no a un rol ni a un criterio de catalogo) -- fuera del alcance de esta migracion (solo
--     logica, sin progreso/datos de aprendices). `roles_permitidos_curso` tambien se omite: la
--     columna vieja `roles_permitidos` viene NULL en las 23 filas de origen, no hay
--     dato real que migrar ahi.
--
-- Generado programaticamente (no transcrito a mano) para evitar la clase de error de E-47
-- (docs/BITACORA_ERRORES.md) dado el volumen: 473 lecciones con cuerpo_html/cuerpo_md largo no
-- son transcribibles a mano de forma confiable.
--
-- Mapeo de valores aplicado (old -> new), literal, solo los valores realmente presentes en origen:
--   cursos.acceso:        restringido->RESTRINGIDO, abierto->ABIERTO
--   lecciones.video_tipo: youtube->YOUTUBE (STORAGE no tiene filas de origen, no se inventa)
--   cursos.origen:        'skool' se preserva tal cual (no es enum, es texto libre en el esquema nuevo)

BEGIN;

SET search_path TO renaser, public;

-- ============================================================================
-- CURSOS (23 filas)
-- ============================================================================

INSERT INTO cursos (
    id, slug, titulo, descripcion, portada_ruta, orden, publicado, acceso, origen,
    dia_desbloqueo, creado_en, actualizado_en
) VALUES
    ('5ae472cf6b224d189c5dba48a22b4c09', 'aba45e19', 'RENASER 30 DIAS', 'Para desbloquear este programa, solo deberás haber completado el primero cumpliendo las condiciones y enviar un mensaje solicitando el desbloqueo de este.', '5ae472cf6b224d189c5dba48a22b4c09/portada.jpg', 6, false, 'RESTRINGIDO', 'skool', NULL, '2026-07-28 21:12:37.806489+00', '2026-07-28 21:16:15.312206+00'),
    ('1724d86936ba4bf6b059500e26e4a775', 'd54e5856', 'PROGRAMA 90D', 'Vive una transformación Real! un cambio solo para quienes se atreven a despertar su valentia!', '1724d86936ba4bf6b059500e26e4a775/portada.jpg', 7, false, 'RESTRINGIDO', 'skool', NULL, '2026-07-28 21:12:37.806489+00', '2026-07-28 21:16:15.312206+00'),
    ('6be9cefa706e40049bae44c38194be29', 'b3b0fa71', 'Sesiones de Q&A', 'El alquimista guía una sesión en vivo de preguntas y respuestas donde se abordan bloqueos reales, decisiones estratégicas y desafíos personales o empresariales. No es teoría. Es intervención directa, claridad mental y dirección concreta para avanzar con enfoque y resultados.', '6be9cefa706e40049bae44c38194be29/portada.jpg', 8, false, 'RESTRINGIDO', 'skool', NULL, '2026-07-28 21:12:37.806489+00', '2026-07-28 21:16:15.312206+00'),
    ('a9dd844e9f254cef950566885ae1f8df', '3e839059', 'MENTORES RENASER', 'Espacio exclusivo para mentores RENASER, diseñado para elevar su liderazgo, fortalecer su criterio y optimizar el acompañamiento de cada participante. Aquí encontrarás herramientas, protocolos y lineamientos claros para guiar procesos con precisión, sostener el estándar del sistema y generar resultados reales en cada intervención.', 'a9dd844e9f254cef950566885ae1f8df/portada.jpg', 9, false, 'RESTRINGIDO', 'skool', NULL, '2026-07-28 21:12:37.806489+00', '2026-07-28 21:16:15.312206+00'),
    ('01ac727ed506477883d5e015a0b792c1', 'dc24d086', 'SEMANA 7 - FORMACIÓN RENASER', 'Se construye una nueva identidad basada en responsabilidad, claridad, disciplina y poder interior. El participante define quién necesita convertirse para sostener una vida más coherente, abundante y elevada.', '01ac727ed506477883d5e015a0b792c1/portada.jpg', 17, false, 'ABIERTO', 'skool', 43, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:20:53.221523+00'),
    ('909bfb750a1543dfaa682ab3bbe00928', '028fa05d', 'FORMACIÓN RENASER - FASE II', 'La Fase II es el proceso de integración profunda del método RENASER. Aprenderás a identificar con mayor precisión los mecanismos internos que condicionan tus decisiones, a gestionar tu energía emocional y a fortalecer una identidad alineada con tus objetivos.
Aquí el enfoque ya no es solo comprender, sino entrenar tu mente y tus hábitos para sostener cambios reales, desarrollando disciplina interna, claridad estratégica y coherencia entre lo que piensas, sientes y haces.', '909bfb750a1543dfaa682ab3bbe00928/portada.jpg', 2, true, 'ABIERTO', 'skool', 8, '2026-07-28 21:12:37.806489+00', '2026-08-07 17:53:41.198385+00'),
    ('a8c5e8c2b1b24636966f2930e8d7c218', '24a25bd5', 'FORMACIÓN RENASER - FASE III', 'La Fase III representa el salto hacia un nuevo nivel de conciencia y liderazgo personal. En esta etapa aprenderás a utilizar el método RENASER no solo para transformar tu propia vida, sino también para impactar tu entorno, tus relaciones y tus decisiones estratégicas.
Profundizarás en el dominio de tus procesos internos, fortaleciendo tu capacidad de observación, análisis emocional y toma de decisiones con mayor inteligencia psicológica.', 'a8c5e8c2b1b24636966f2930e8d7c218/portada.jpg', 3, true, 'ABIERTO', 'skool', 35, '2026-07-28 21:12:37.806489+00', '2026-08-07 17:55:28.531789+00'),
    ('403c35f8bd8d4f18a657d9edadb45b30', '2775e640', 'RENASER 7 DIAS', 'Este programa esta diseñado para que cada guerrero(a), pueda tomar consciencia de su mundo interior.', '403c35f8bd8d4f18a657d9edadb45b30/portada.jpg', 5, false, 'RESTRINGIDO', 'skool', NULL, '2026-07-28 21:12:37.806489+00', '2026-07-30 20:08:07.399776+00'),
    ('0909f72cfe524f0c95d6e6fcc7ff8167', 'b93c51b5', 'BIENVENIDO A RENASER', 'Un espacio de acompañamiento donde encontrarás videos y herramientas diseñadas para guiarte, paso a paso, a lo largo de todo tu viaje RENASER, con claridad, conciencia y dirección.', '0909f72cfe524f0c95d6e6fcc7ff8167/portada.jpg', 0, true, 'ABIERTO', 'skool', NULL, '2026-07-28 21:12:37.806489+00', '2026-08-01 14:34:10.697567+00'),
    ('57955d86b173488f910cd4ecd5d8cbaf', '4cf5dc37', 'FORMACIÓN RENASER - FASE IV', 'La Fase IV es el nivel de maestría dentro del método RENASER. En esta etapa se consolida una comprensión integral del sistema, integrando todas las herramientas en un modelo de pensamiento y acción que puedes aplicar de forma natural en cualquier contexto de tu vida.
El enfoque aquí es alcanzar un estado de claridad, autonomía y consistencia, donde tu mente, emociones y acciones operan alineadas con tu propósito y visión de vida.', '57955d86b173488f910cd4ecd5d8cbaf/portada.jpg', 4, true, 'ABIERTO', 'skool', 65, '2026-07-28 21:12:37.806489+00', '2026-08-07 17:56:08.961397+00'),
    ('79ff26b6764b4a288d8db50a8be6934b', '7dddc42e', 'FORMACIÓN RENASER - FASE I', 'La Fase I es la puerta de acceso al método RENASER: Una formación estratégica diseñada para reprogramar patrones, elevar tu autoconciencia y construir un sistema de alto rendimiento aplicable a tu vida. Aquí aprenderás a usar herramientas de autoterapia profundas, precisas y replicables, capaces de desactivar sabotajes, fortalecer tu identidad y optimizar tu energía emocional.
Es el primer paso para convertirte en tu propio guía, dominar tu mente y activar tu transformación', '79ff26b6764b4a288d8db50a8be6934b/portada.jpg', 1, true, 'ABIERTO', 'skool', 1, '2026-07-28 21:12:37.806489+00', '2026-08-01 14:36:58.668975+00'),
    ('36b59c82ae5b4328a50c286cf9c5ce41', '6699581c', 'CERTIFICACIÓN RENASER', 'Programa avanzado diseñado para formar líderes de alto rendimiento capaces de guiar procesos de transformación con estructura, claridad y resultados medibles. Aquí integrarás la metodología RENASER a nivel profundo, desarrollando habilidades de acompañamiento, diagnóstico y dirección estratégica, bajo estándares de excelencia, compromiso y coherencia personal.', '36b59c82ae5b4328a50c286cf9c5ce41/portada.jpg', 10, false, 'RESTRINGIDO', 'skool', NULL, '2026-07-28 21:12:37.806489+00', '2026-07-28 21:16:15.312206+00'),
    ('8405f8f0128a46f59f155d1548ffbf8b', 'a1a7c305', 'SEMANA 2 - FORMACIÓN RENASER', 'Se trabaja la identificación de quejas, excusas, culpas y patrones de evasión. El objetivo es que el participante deje de verse como víctima de su historia y empiece a recuperar poder personal desde la conciencia y la acción.', '8405f8f0128a46f59f155d1548ffbf8b/portada.jpg', 12, false, 'ABIERTO', 'skool', 8, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:20:48.888547+00'),
    ('602f198f05c2442184c7b67071b2487d', '76b874d4', 'SEMANA 8 - FORMACIÓN RENASER', 'Esta semana convierte la transformación en sistema. Se trabajan hábitos diarios, rutinas, microacciones, cuidado del cuerpo, orden personal y energía como base para sostener resultados reales.', '602f198f05c2442184c7b67071b2487d/portada.jpg', 18, false, 'ABIERTO', 'skool', 50, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:21:18.237398+00'),
    ('1c4d035721394f9c8504883a25b88d3a', '501e435a', 'SEMANA 6 - FORMACIÓN RENASER', 'El participante aprende a dejar de ser dominado por sus emociones. Se trabaja la capacidad de sentir, ordenar y transformar emociones intensas sin reprimirlas, exagerarlas ni convertirlas en decisiones destructivas.', '1c4d035721394f9c8504883a25b88d3a/portada.jpg', 16, false, 'ABIERTO', 'skool', 36, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:20:57.808732+00'),
    ('035bf93019964b6b9b3a288a3b0f0ce4', '3a65ed5b', 'SEMANA 5 - FORMACIÓN RENASER', 'Esta semana se enfoca en detectar pensamientos limitantes, distorsiones mentales y diálogos internos que sostienen el autosabotaje. El participante aprende a reemplazar patrones mentales débiles por una estructura interna más clara, firme y consciente.', '035bf93019964b6b9b3a288a3b0f0ce4/portada.jpg', 15, false, 'ABIERTO', 'skool', 29, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:21:01.091424+00'),
    ('f71c7c7194c547bca05453d4b0c32fb0', '1909d796', 'SEMANA 12 - FORMACIÓN RENASER', 'La última semana consolida todo el camino recorrido. El participante revisa sus avances, reconoce su transformación, define compromisos futuros y cierra el proceso con una nueva estructura interna para sostener su crecimiento.', 'f71c7c7194c547bca05453d4b0c32fb0/portada.jpg', 22, false, 'ABIERTO', 'skool', 78, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:21:22.135798+00'),
    ('c2bf914741b34ea3a9d08c3ab86c5996', '3bee9d4e', 'SEMANA 11 - FORMACIÓN RENASER', 'El participante conecta su transformación personal con una visión más grande. Se trabaja propósito, dirección, impacto, liderazgo y la capacidad de convertirse en ejemplo para otros desde su propio proceso.', 'c2bf914741b34ea3a9d08c3ab86c5996/portada.jpg', 21, false, 'ABIERTO', 'skool', 71, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:21:26.182174+00'),
    ('cfc4c4334474498b9f6c9c47800fdecf', 'a1f71185', 'SEMANA 1 - FORMACIÓN RENASER', 'El participante inicia reconociendo que su vida actual no es casualidad, sino el resultado de sus pensamientos, emociones, hábitos y decisiones. Esta semana marca el inicio del cambio: dejar de vivir en automático y asumir responsabilidad sobre su proceso.', 'cfc4c4334474498b9f6c9c47800fdecf/portada.jpg', 11, false, 'ABIERTO', 'skool', 1, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:20:33.382008+00'),
    ('a11488afc79643cf87759dece4f9451d', '4203f281', 'SEMANA 4 - FORMACIÓN RENASER', 'Se profundiza en las heridas emocionales que condicionan la forma de amar, decidir, trabajar, liderar y relacionarse. El participante empieza a comprender qué parte de su historia sigue tomando decisiones por él o ella.', 'a11488afc79643cf87759dece4f9451d/portada.jpg', 14, false, 'ABIERTO', 'skool', 22, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:20:39.495189+00'),
    ('1c9e04cbb4e64afcae62ead62d334c5a', 'c2b5e80d', 'SEMANA 3 - FORMACIÓN RENASER', 'El participante aprende a observar cómo su mundo externo refleja su mundo interno. Se analizan relaciones, conflictos, emociones repetitivas y situaciones que revelan heridas, creencias y patrones inconscientes.', '1c9e04cbb4e64afcae62ead62d334c5a/portada.jpg', 13, false, 'ABIERTO', 'skool', 15, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:20:45.223652+00'),
    ('30502644ee3449ebbd4482f1ddf20a1e', '28445225', 'SEMANA 10 - FORMACIÓN RENASER', 'Se explora la relación emocional con el dinero, el éxito, la expansión y el merecimiento. El participante identifica bloqueos internos que limitan su crecimiento y empieza a construir una mentalidad más ordenada, expansiva y responsable.', '30502644ee3449ebbd4482f1ddf20a1e/portada.jpg', 20, false, 'ABIERTO', 'skool', 64, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:21:07.98585+00'),
    ('21fd1d616cd145ac876a29f402cc119a', 'df574913', 'SEMANA 9 - FORMACIÓN RENASER', 'El participante observa cómo se vincula con familia, pareja, amistades y entorno. Se trabajan límites, dependencia emocional, necesidad de aprobación, comunicación y elección consciente de relaciones que eleven su vida.', '21fd1d616cd145ac876a29f402cc119a/portada.jpg', 19, false, 'ABIERTO', 'skool', 57, '2026-07-28 21:12:37.806489+00', '2026-08-07 15:21:13.320175+00');

-- ============================================================================
-- SECCIONES_CURSO (171 filas)
-- ============================================================================

INSERT INTO secciones_curso (
    id, curso_id, titulo, orden, dia_desbloqueo
) VALUES
    ('3e116b815c57456b9a102bc783b90c55', 'cfc4c4334474498b9f6c9c47800fdecf', 'DÍA 1', 89, 1),
    ('5ab7f4a436974f96974e67fc7b750bda', 'cfc4c4334474498b9f6c9c47800fdecf', 'DÍA 2', 90, 2),
    ('d459f9b1db9248c9a9a7eb4d74ebcd51', 'cfc4c4334474498b9f6c9c47800fdecf', 'DÍA 3', 91, 3),
    ('bf4c2f2fafde453cab7c212c21198aa7', 'cfc4c4334474498b9f6c9c47800fdecf', 'DÍA 4', 92, 4),
    ('57b1ea4e15124ed4902c1459d626716c', 'cfc4c4334474498b9f6c9c47800fdecf', 'DÍA 5', 93, 5),
    ('b39edba43b1449cfbca739dfa5c97a71', 'cfc4c4334474498b9f6c9c47800fdecf', 'DÍA 6', 94, 6),
    ('4424058971c4467da1424857973e54f5', 'cfc4c4334474498b9f6c9c47800fdecf', 'DÍA 7', 95, 7),
    ('e20ac203316a4ce8b21551164bf7db34', '8405f8f0128a46f59f155d1548ffbf8b', 'DÍA 8', 96, 8),
    ('11474d55a9674740b1f31aba6df604fe', '8405f8f0128a46f59f155d1548ffbf8b', 'DÍA 9', 97, 9),
    ('62347d2a9876408ebabff48672f390c0', '8405f8f0128a46f59f155d1548ffbf8b', 'DÍA 10', 98, 10),
    ('7a47a79b40eb43728db907178162ded5', '8405f8f0128a46f59f155d1548ffbf8b', 'DÍA 11', 99, 11),
    ('bf4983a0d30b48f6bda54c65fb3e4e78', '8405f8f0128a46f59f155d1548ffbf8b', 'DÍA 12', 100, 12),
    ('bb0c2658908145c786487ca25a20936c', '8405f8f0128a46f59f155d1548ffbf8b', 'DÍA 13', 101, 13),
    ('a0978f65f5d04912911ff105df7a851f', '8405f8f0128a46f59f155d1548ffbf8b', 'DÍA 14', 102, 14),
    ('f9843e160945475b943c31aff817ef96', '1c9e04cbb4e64afcae62ead62d334c5a', 'DÍA 20', 108, 20),
    ('f5723b9e59fc4127a6ed229857f0722f', '1c9e04cbb4e64afcae62ead62d334c5a', 'DÍA 21', 109, 21),
    ('953d3f8c476441b69f0383e935515c3b', 'a11488afc79643cf87759dece4f9451d', 'DÍA 22', 110, 22),
    ('d65078bdca6f4ea9a4087ee632b86d7f', 'a11488afc79643cf87759dece4f9451d', 'DÍA 23', 111, 23),
    ('4a0668fb08d548498f3c6b23ffd730cb', 'a11488afc79643cf87759dece4f9451d', 'DÍA 24', 112, 24),
    ('5e4342a2211149c3846fbdc9848b1e5d', 'a11488afc79643cf87759dece4f9451d', 'DÍA 25', 113, 25),
    ('7c249c3b8049496a9f0d335116dce5f3', 'a11488afc79643cf87759dece4f9451d', 'DÍA 26', 114, 26),
    ('41e29aab1d3c4de1981a9e5c67c8800f', 'a11488afc79643cf87759dece4f9451d', 'DÍA 27', 115, 27),
    ('c71a6d387a4649c389d68f53ba87315f', 'a11488afc79643cf87759dece4f9451d', 'DÍA 28', 116, 28),
    ('cbd9b0012c2b46f08676c59f23e1b9cc', '035bf93019964b6b9b3a288a3b0f0ce4', 'DÍA 29', 117, 29),
    ('1b83c3eb622245d28ad14a5da6a08f9d', '035bf93019964b6b9b3a288a3b0f0ce4', 'DÍA 30', 118, 30),
    ('8ed66cf49ea44fa697f5d4695dd5170a', '035bf93019964b6b9b3a288a3b0f0ce4', 'DÍA 31', 119, 31),
    ('530a1bf889494341a6598cdb6ecb056b', '035bf93019964b6b9b3a288a3b0f0ce4', 'DÍA 32', 120, 32),
    ('ffdcdfedea0e4325ba5bd3e4164d99fe', '035bf93019964b6b9b3a288a3b0f0ce4', 'DÍA 33', 121, 33),
    ('5fb2d49b34254feab2190cee1e838c2e', '035bf93019964b6b9b3a288a3b0f0ce4', 'DÍA 34', 122, 34),
    ('54b195c19cd64ee0a65a4686c283b612', '035bf93019964b6b9b3a288a3b0f0ce4', 'DÍA 35', 123, 35),
    ('061dbb3159404ddfb9b1fa6d35885747', '1c4d035721394f9c8504883a25b88d3a', 'DÍA 36', 124, 36),
    ('3d60126bf7794db9a4a0cb21364260b5', '1c4d035721394f9c8504883a25b88d3a', 'DÍA 37', 125, 37),
    ('2e3387b37476436b85c897f3cebd9b17', '1c4d035721394f9c8504883a25b88d3a', 'DÍA 38', 126, 38),
    ('1ec7be647cf2482b941d50a3c05bffd3', '1c4d035721394f9c8504883a25b88d3a', 'DÍA 39', 127, 39),
    ('25f2edf5887b410ba6a915f53d6266bd', '1c4d035721394f9c8504883a25b88d3a', 'DÍA 40', 128, 40),
    ('c35711dd9cd646a8a53f87445cd48146', '1c4d035721394f9c8504883a25b88d3a', 'DÍA 41', 129, 41),
    ('fa31e15096394e52abbd85a0fab1b1b4', '1c4d035721394f9c8504883a25b88d3a', 'DÍA 42', 130, 42),
    ('2598bc7f4a1e4596a58b1b94440856a2', '01ac727ed506477883d5e015a0b792c1', 'DÍA 43', 131, 43),
    ('57bfc0f516024e75a06221fa1a87071c', '01ac727ed506477883d5e015a0b792c1', 'DÍA 44', 132, 44),
    ('a3169e0831c545db981c2a4959bf711e', '01ac727ed506477883d5e015a0b792c1', 'DÍA 45', 133, 45),
    ('d8be5a08307a477985ab294a9aedf3ad', '01ac727ed506477883d5e015a0b792c1', 'DÍA 46', 134, 46),
    ('4780b840603b480eaa8fb704ac4612bf', '01ac727ed506477883d5e015a0b792c1', 'DÍA 47', 135, 47),
    ('a723c60e230b42c1bcd303577c7fccb9', '01ac727ed506477883d5e015a0b792c1', 'DÍA 48', 136, 48),
    ('2c47a277dbd443bd88c916ce77b02ac4', '01ac727ed506477883d5e015a0b792c1', 'DÍA 49', 137, 49),
    ('2b33c26eba1042a9b676c16f3a098197', '602f198f05c2442184c7b67071b2487d', 'DÍA 50', 138, 50),
    ('47bd0bd9455d4eb99d3d2de9e48b8442', '602f198f05c2442184c7b67071b2487d', 'DÍA 51', 139, 51),
    ('a60b30e3acb44bd2b925f419daee821c', '602f198f05c2442184c7b67071b2487d', 'DÍA 52', 140, 52),
    ('3255f19ee2784077b4b049d4a7ec7c2b', '602f198f05c2442184c7b67071b2487d', 'DÍA 53', 141, 53),
    ('73ded8e119414dccb752d54472769a44', '602f198f05c2442184c7b67071b2487d', 'DÍA 54', 142, 54),
    ('a07e89a7ab064d21a3626e7ea41aa90e', '602f198f05c2442184c7b67071b2487d', 'DÍA 55', 143, 55),
    ('a680d24748634289ad59ca034e7e8f94', '602f198f05c2442184c7b67071b2487d', 'DÍA 56', 144, 56),
    ('e28de1e0a3ba4b0c856e8f18e495de56', '21fd1d616cd145ac876a29f402cc119a', 'DÍA 57', 145, 57),
    ('6ac008c45b4b4c9fb63d2359621f3dc9', '21fd1d616cd145ac876a29f402cc119a', 'DÍA 58', 146, 58),
    ('530e88190dc84313968336d9610d625b', '21fd1d616cd145ac876a29f402cc119a', 'DÍA 59', 147, 59),
    ('563239e3e3394dada0b37a3d29f91564', '21fd1d616cd145ac876a29f402cc119a', 'DÍA 60', 148, 60),
    ('2f0f57690f224093a72062ef65fe59e3', '21fd1d616cd145ac876a29f402cc119a', 'DÍA 61', 149, 61),
    ('dc4cfec04df64cb6881f04acd6e247e5', '21fd1d616cd145ac876a29f402cc119a', 'DÍA 62', 150, 62),
    ('7c872625ee10475e9682d211291204cc', '21fd1d616cd145ac876a29f402cc119a', 'DÍA 63', 151, 63),
    ('1d4b3bf55e1849449916605ade2541bd', '30502644ee3449ebbd4482f1ddf20a1e', 'DÍA 64', 152, 64),
    ('231b4e01b3d340de9412cf6bc6aee5ab', '30502644ee3449ebbd4482f1ddf20a1e', 'DÍA 65', 153, 65),
    ('e7b9cd683c3c4294815ac53e0b6777a7', '30502644ee3449ebbd4482f1ddf20a1e', 'DÍA 66', 154, 66),
    ('9b6c3872d8db4306bd8aa4b63611707a', '30502644ee3449ebbd4482f1ddf20a1e', 'DÍA 67', 155, 67),
    ('d1f253d440ff40a8ac37659b572297d8', '30502644ee3449ebbd4482f1ddf20a1e', 'DÍA 68', 156, 68),
    ('5c6e614b8daf40aab3e1ad6508633ef7', '30502644ee3449ebbd4482f1ddf20a1e', 'DÍA 69', 157, 69),
    ('827d12e82af6415791195dcf518cfe1f', '30502644ee3449ebbd4482f1ddf20a1e', 'DÍA 70', 158, 70),
    ('e1386d77d8624b878ff7d1e6fa5bf270', 'c2bf914741b34ea3a9d08c3ab86c5996', 'DÍA 71', 159, 71),
    ('b63f078bc5104910b789b0b8cbd5da97', 'c2bf914741b34ea3a9d08c3ab86c5996', 'DÍA 72', 160, 72),
    ('62dde6d6bc064f7b9ee2f4878658afa9', 'c2bf914741b34ea3a9d08c3ab86c5996', 'DÍA 73', 161, 73),
    ('47c2b98e39124867b12f2e2dacc350f2', 'c2bf914741b34ea3a9d08c3ab86c5996', 'DÍA 74', 162, 74),
    ('ee0ca64efd83432c9f54dadfe38fcc47', 'c2bf914741b34ea3a9d08c3ab86c5996', 'DÍA 75', 163, 75),
    ('7b476391ae2e4d9f9069dcb4198e8d5f', 'c2bf914741b34ea3a9d08c3ab86c5996', 'DÍA 76', 164, 76),
    ('a885b88815c043ec9fbd61d2ceaefa02', 'c2bf914741b34ea3a9d08c3ab86c5996', 'DÍA 77', 165, 77),
    ('bdd876c5bd214e3e8cbc3b847274c8ea', 'f71c7c7194c547bca05453d4b0c32fb0', 'DÍA 78', 166, 78),
    ('8c6aece63e4845b89ce79483301a6d35', 'f71c7c7194c547bca05453d4b0c32fb0', 'DÍA 79', 167, 79),
    ('588e980f48d44fe0b50f544782c1af20', 'f71c7c7194c547bca05453d4b0c32fb0', 'DÍA 80', 168, 80),
    ('e263339c9b8c4cf19a0c48b2e2351ee1', 'f71c7c7194c547bca05453d4b0c32fb0', 'DÍA 81', 169, 81),
    ('cbaf99846e404935bba7e4e410dbff0f', 'f71c7c7194c547bca05453d4b0c32fb0', 'DÍA 82', 170, 82),
    ('a72e73f307d043d48cfbe86a320bb0de', 'f71c7c7194c547bca05453d4b0c32fb0', 'DÍA 83', 171, 83),
    ('b7dede88c3254543a3e02b32204851a2', 'f71c7c7194c547bca05453d4b0c32fb0', 'DÍA 84', 172, 84),
    ('25dcb5cb74d843afb28801032487f9d8', '79ff26b6764b4a288d8db50a8be6934b', 'DÍA 1 | Amor propio?', 6, 1),
    ('adb24bef4f1e49c4998b666af1083c49', '79ff26b6764b4a288d8db50a8be6934b', 'DÍA 2 | La raiz de todo sufrimiento', 7, 2),
    ('a380694e7bd34b44b62809769c5e575a', '79ff26b6764b4a288d8db50a8be6934b', 'DÍA 3 | El secreto para vivir en plenitud', 8, 3),
    ('808b02ad63884da5aaa4c13598796290', '79ff26b6764b4a288d8db50a8be6934b', 'DÍA 4 | Sentir mas para vivir mas', 9, 4),
    ('3acfa0b60260497e9a7fd54f5f65bb04', '79ff26b6764b4a288d8db50a8be6934b', 'DÍA 5 | Como guiar mis emociones?', 10, 5),
    ('ed9a6bf2fb154130920e9cb5867c0e55', '79ff26b6764b4a288d8db50a8be6934b', 'DÍA 6 | El renaser de tu esencia', 11, 6),
    ('03caa50b3ceb4467bd1944ca1d5de837', '79ff26b6764b4a288d8db50a8be6934b', 'DÍA 7 | El verdadero amor propio', 12, 7),
    ('80802b2f844d45db82bde5509989991a', '909bfb750a1543dfaa682ab3bbe00928', 'DIA 8 | Sesion de inicio de fase II', 13, 8),
    ('27bf16d7772b4f52bcd3a8442aacfd9b', '909bfb750a1543dfaa682ab3bbe00928', 'Dia 9 | El mayor regalo de tu alma', 17, 9),
    ('32d5d646abc84dc894dc6103f5b2ec21', '909bfb750a1543dfaa682ab3bbe00928', 'Dia 10 | Las deudas y la culpa te condenan', 18, 10),
    ('7f66267e578c425aa569808a797083be', '909bfb750a1543dfaa682ab3bbe00928', 'Dia 11 | Reconstruye tu identidad', 19, 11),
    ('87599a55466346b78f481f1b4b32a8da', '909bfb750a1543dfaa682ab3bbe00928', 'Dia 12 | El miedo y el Ego', 20, 12),
    ('0922856f602f4fbfb32accc2166fd4aa', '909bfb750a1543dfaa682ab3bbe00928', 'Dia 13 | El pode de tus intenciones', 21, 13),
    ('a737af3f93f345bf8879744d53edf9a7', '909bfb750a1543dfaa682ab3bbe00928', 'Dia 14 | Liberar tu alma te dara plenitud', 22, 14),
    ('b2e5feeee6fe40c0bfa61b0c87f15eed', '909bfb750a1543dfaa682ab3bbe00928', 'CICLO 2 (DÍA 17 - 25)', 24, 17),
    ('4dc5346c2f974f458367da8e82e14b5f', '909bfb750a1543dfaa682ab3bbe00928', 'CICLO 3 (DÍA 26 -34 )', 25, 26),
    ('3230ec3a0c8a4693bca7a6345560ca92', '0909f72cfe524f0c95d6e6fcc7ff8167', 'VIDEOS DE APOYO', 0, NULL),
    ('575bc11a0f334605bcb0c91cbc5003e8', '0909f72cfe524f0c95d6e6fcc7ff8167', 'FORMULARIOS DE INICIO', 1, NULL),
    ('576d206ebaad4908be04ebc001c05647', '0909f72cfe524f0c95d6e6fcc7ff8167', 'VIDEOS COMPLEMENTARIOS', 2, NULL),
    ('6529c1662755491689dc496e0422aeea', '79ff26b6764b4a288d8db50a8be6934b', 'SESION 1 | Bienvenido(a)', 3, NULL),
    ('059e60f9eb0e4c25abd88cbab1e52591', '79ff26b6764b4a288d8db50a8be6934b', 'Guias y material | Imprimir', 4, NULL),
    ('481a396b37754a59b9151d3e95684968', '79ff26b6764b4a288d8db50a8be6934b', 'Respiración Renaser | Nuevo alimento de tu alma', 5, NULL),
    ('c1348ef03f304d08a216cfb58d2b42e6', '909bfb750a1543dfaa682ab3bbe00928', 'GUIAS DE LA FASE II', 14, NULL),
    ('a6bbea0be4214dbf8d2876898cd10dd7', '909bfb750a1543dfaa682ab3bbe00928', 'Clase Especial de la semana II', 15, NULL),
    ('185daefc01274f69993fe8d33cdd044f', '909bfb750a1543dfaa682ab3bbe00928', 'TERAPIA RENASER EN AUDIO | CONEXION', 16, NULL),
    ('635ec5fe41e946358a2fee41518167e4', '909bfb750a1543dfaa682ab3bbe00928', 'SEMANA 3', 23, NULL),
    ('7a4e99c8e51e421e9bf8eb360d7d8e8c', '909bfb750a1543dfaa682ab3bbe00928', 'VIDEOS DE APOYO', 26, NULL),
    ('f8a1a953c8b9499d91735ad2486af94e', '909bfb750a1543dfaa682ab3bbe00928', 'MATERIAL DE APOYO', 27, NULL),
    ('75fd11b801b849cbb8d982d8f69a41c5', 'a8c5e8c2b1b24636966f2930e8d7c218', 'BIBLIOGRAFÍA DE LA FASE III', 29, NULL),
    ('7395c4a36b0c42dba12a4f8560ec6653', '57955d86b173488f910cd4ecd5d8cbaf', 'BIBLIOGRAFÍA DE LA FASE IV', 34, NULL),
    ('0531c880f9d8429ab94fc09105c645da', '403c35f8bd8d4f18a657d9edadb45b30', 'Día 1', 43, NULL),
    ('8ee956ac61c14422b31adcecd684f36f', '403c35f8bd8d4f18a657d9edadb45b30', 'Día 2', 44, NULL),
    ('448446ec338346a58ccdb69613293044', '403c35f8bd8d4f18a657d9edadb45b30', 'Día 3', 45, NULL),
    ('5bbf0c2a89f24aa9a99d390040a6a301', '403c35f8bd8d4f18a657d9edadb45b30', 'Día 4', 46, NULL),
    ('b3eac5f69f144d21aa8c522e082e8e8e', '403c35f8bd8d4f18a657d9edadb45b30', 'Día 5', 47, NULL),
    ('21ed69c8180a4ba49ba334ef02392192', '403c35f8bd8d4f18a657d9edadb45b30', 'Día 6', 48, NULL),
    ('e58dff58897242aaba3047a9db5a9f62', '403c35f8bd8d4f18a657d9edadb45b30', 'Día 7', 49, NULL),
    ('eb93b5a309e54ff184198573d6d6efae', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 8', 50, NULL),
    ('f49b9e9fb0af41b381fcb93b0f8c12c1', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 9', 51, NULL),
    ('c43eecbdd538494fb8eb038a6158a04e', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 10', 52, NULL),
    ('78c0ac4dacfb4d98bdb480f55572520c', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 11', 53, NULL),
    ('d306a73b19ab422ab2f86e82b30be1a0', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 12', 54, NULL),
    ('a1b433c5b44f4436974b59e8bd8c663c', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 13', 55, NULL),
    ('4ab9a52beb954c5cbd3558e15d5502ba', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 14', 56, NULL),
    ('4f84a9a3013f441cae60942e56f93941', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 15', 57, NULL),
    ('65e817cefccc4a4db2c1f9832502168f', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 16', 58, NULL),
    ('faa83a73f3dd4971b6448d87151f5aab', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 17', 59, NULL),
    ('afe63d302ef4400f80e24cdd7ee3da1a', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 18', 60, NULL),
    ('d6a4afa91de642ee99b73eed48b592ee', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 19', 61, NULL),
    ('1a9720546f594346a1fbfd230c98fe0c', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 20', 62, NULL),
    ('a5622064ff06402fad15cb9d168c45f3', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 21', 63, NULL),
    ('f8cb1195a52e4cb7917aa183f11a00cd', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 22', 64, NULL),
    ('e7cd6b6c13cb403e98315468390104bb', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 23', 65, NULL),
    ('203af6edd55945e095b6d0f636aeed0a', '5ae472cf6b224d189c5dba48a22b4c09', 'Día 24', 66, NULL),
    ('95c8545c000e439684158d2c35217dcc', '1724d86936ba4bf6b059500e26e4a775', 'CÓDIGO RENASER', 67, NULL),
    ('f2c6ca1b67ae427ab32b6dee24db2dfb', '1724d86936ba4bf6b059500e26e4a775', 'SEMANA 1', 68, NULL),
    ('71c7d2a8eb7f433d8103885b939152c8', '1724d86936ba4bf6b059500e26e4a775', 'SEMANA 2', 69, NULL),
    ('bb9474712a624805a6e81954fad4c171', '1724d86936ba4bf6b059500e26e4a775', 'SEMANA 3', 70, NULL),
    ('987dd206524a41a0bb4b714429974cf3', '1724d86936ba4bf6b059500e26e4a775', 'SEMANA 4', 71, NULL),
    ('77daffb6ea054c27ba64d523acf96842', '1724d86936ba4bf6b059500e26e4a775', 'SEMANA 5', 72, NULL),
    ('7ecea71627504efea568b308a40ae94a', '1724d86936ba4bf6b059500e26e4a775', 'SEMANA 6', 73, NULL),
    ('bc43ecb0127e41f5a20d7ce9d53958ba', '1724d86936ba4bf6b059500e26e4a775', 'SEMANA 7', 74, NULL),
    ('c6a59ee82ffc4dc28d542689c467814f', '1724d86936ba4bf6b059500e26e4a775', 'SEMANA 8', 75, NULL),
    ('c4e2ddd279174e85a49097d47d275c76', '1724d86936ba4bf6b059500e26e4a775', 'SEMANA 9', 76, NULL),
    ('cbe645923a574f58be5f90c4fb956a88', '1724d86936ba4bf6b059500e26e4a775', 'BONOS', 77, NULL),
    ('9e4fa1337eac4a27b665430247b54f21', '1724d86936ba4bf6b059500e26e4a775', 'CASOS REALES', 78, NULL),
    ('30f1561529014a8eaca5645dacc5dd4e', '1724d86936ba4bf6b059500e26e4a775', 'MENTORIAS ANTES DEL 2025', 79, NULL),
    ('18812662a20a4252a1fc1609b92f9164', '6be9cefa706e40049bae44c38194be29', 'Mayo 2026', 81, NULL),
    ('577783b920cc42848dc97280e2ebda4a', '6be9cefa706e40049bae44c38194be29', 'Abril 2026', 82, NULL),
    ('3a5ae9ac50774e93984631ff8010e081', '6be9cefa706e40049bae44c38194be29', 'Marzo 2026', 83, NULL),
    ('ee772609496146a1abad12a1a16b1fcd', '6be9cefa706e40049bae44c38194be29', 'Febrero 2026', 84, NULL),
    ('92a0052c94b04daaab5c95b7160c172f', '6be9cefa706e40049bae44c38194be29', 'Enero 2026', 85, NULL),
    ('e105a2e1d6a441fa961715058639ac3e', '6be9cefa706e40049bae44c38194be29', 'Diciembre 2025', 86, NULL),
    ('80a449cdcf684848b6ad8a80371ab8de', 'a9dd844e9f254cef950566885ae1f8df', 'FORMULARIOS', 87, NULL),
    ('dc636fc5d95643e98a8b198afbbb7ef4', 'a9dd844e9f254cef950566885ae1f8df', 'DOCUMENTACIÓN', 88, NULL),
    ('0c0602a40bd54c32b7e44d47ca7043c0', 'a8c5e8c2b1b24636966f2930e8d7c218', 'SEMANA 1 (Día 35 - 42)', 30, 35),
    ('98c87b84bce74686a6e7d95c95940a31', 'a8c5e8c2b1b24636966f2930e8d7c218', 'SEMANA 2 (Día 43 - 50)', 31, 43),
    ('2cb357f3ec8d41b1945f040c9103b2b7', 'a8c5e8c2b1b24636966f2930e8d7c218', 'SEMANA 3 (Día 51 - 58)', 32, 51),
    ('46a3320fc9844e0c802e24a33279ce5f', 'a8c5e8c2b1b24636966f2930e8d7c218', 'SEMANA 4 (Día 59 - 65)', 33, 59),
    ('c5b297b4fd7941e68615a6642c271a51', '57955d86b173488f910cd4ecd5d8cbaf', 'DÍA 65 AL 67', 35, 65),
    ('3ff6a6eecaf64f0fb20164c158bdb9d8', '57955d86b173488f910cd4ecd5d8cbaf', 'DÍA 68 AL 70', 36, 68),
    ('2c6aefd1e0044558a387e53a307404ab', '57955d86b173488f910cd4ecd5d8cbaf', 'DÍA 71 AL 73', 37, 71),
    ('e2b6829a437146a09ebe4603c3814129', '57955d86b173488f910cd4ecd5d8cbaf', 'DÍA 74 AL 76', 38, 74),
    ('034b05bec38c4d20b868dcb0d468e6f4', '57955d86b173488f910cd4ecd5d8cbaf', 'DÍA 77 AL 79', 39, 77),
    ('04e6d703a66949968f8b900aeafbbd47', '57955d86b173488f910cd4ecd5d8cbaf', 'DÍA 80 AL 82', 40, 80),
    ('b583cfcc4b1d48cc9cb4b9dcee3d8442', '57955d86b173488f910cd4ecd5d8cbaf', 'DÍA 83 AL 85', 41, 83),
    ('5860cc47ea6047d1a4ee3555ecdb3d77', '57955d86b173488f910cd4ecd5d8cbaf', 'DÍA 86 AL 90', 42, 86),
    ('e8ec568a55df4b50b1e5e7c3b4571c76', '1c9e04cbb4e64afcae62ead62d334c5a', 'DÍA 15', 103, 15),
    ('8a640c8da980490fbb8045be1f773f1f', '1c9e04cbb4e64afcae62ead62d334c5a', 'DÍA 16', 104, 16),
    ('1b533a1be83845c68580c7cd3c9945fd', '1c9e04cbb4e64afcae62ead62d334c5a', 'DÍA 17', 105, 17),
    ('22dd420447114422866f56a96ef0a77d', '1c9e04cbb4e64afcae62ead62d334c5a', 'DÍA 18', 106, 18),
    ('47e50ee9787a4360aca4c110639d61b2', '1c9e04cbb4e64afcae62ead62d334c5a', 'DÍA 19', 107, 19);

-- ============================================================================
-- LECCIONES (473 filas)
-- ============================================================================

INSERT INTO lecciones (
    id, curso_id, seccion_id, titulo, orden, cuerpo_html, cuerpo_md, video_tipo, video_url,
    video_miniatura_url, video_duracion_ms, creado_en, actualizado_en
) VALUES
    ('fdb493674f554cfa9473c7c5aee331ed', '0909f72cfe524f0c95d6e6fcc7ff8167', '576d206ebaad4908be04ebc001c05647', '04. USO DE ZOOM PARA COMPUTADORA', 9, '<p>Para participar correctamente en mentorías, sesiones y espacios de trabajo, es indispensable que manejes el uso de Zoom desde tu computadora.</p>
<p>En este video aprenderás cómo ingresar a una reunión, configurar tu audio y cámara, y utilizar las funciones básicas para desenvolverte con claridad durante cada sesión.</p>
<p>¿Por qué es importante?<br />Porque estos espacios requieren presencia activa, enfoque y participación real. No es solo conectarte, es sostener el proceso.</p>
<p>Indicaciones clave:<br />• Ingresa desde computadora (evita el uso del celular)<br />• Verifica tu conexión a internet antes de iniciar<br />• Cámara encendida durante toda la sesión (requisito)<br />• Micrófono funcional y ambiente sin distracciones<br />• Conéctate 5 minutos antes para evitar retrasos</p>
<p>Este no es un espacio pasivo.<br />Es un entorno de ejecución.</p>
<p>Tu nivel de preparación define la calidad de tu avance.</p>
<p>Asegúrate de dominar esta herramienta antes de tu próxima sesión.</p>', 'Para participar correctamente en mentorías, sesiones y espacios de trabajo, es indispensable que manejes el uso de Zoom desde tu computadora.

En este video aprenderás cómo ingresar a una reunión, configurar tu audio y cámara, y utilizar las funciones básicas para desenvolverte con claridad durante cada sesión.

¿Por qué es importante?  
Porque estos espacios requieren presencia activa, enfoque y participación real. No es solo conectarte, es sostener el proceso.

Indicaciones clave:  
• Ingresa desde computadora (evita el uso del celular)  
• Verifica tu conexión a internet antes de iniciar  
• Cámara encendida durante toda la sesión (requisito)  
• Micrófono funcional y ambiente sin distracciones  
• Conéctate 5 minutos antes para evitar retrasos

Este no es un espacio pasivo.  
Es un entorno de ejecución.

Tu nivel de preparación define la calidad de tu avance.

Asegúrate de dominar esta herramienta antes de tu próxima sesión.', NULL, NULL, 'https://image.mux.com/GTz92Hr4ObmPaTzqzs8RgwtZqEREDxXP3OsciYKNMeI/thumbnail.jpg?token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJ0IiwiZXhwIjo0OTM4OTU3ODI1LCJraWQiOiJPVjIwMHZ6SWZuZFVCNHdXdTAxbDRjb0hrYTVQQUd3TlYwMEtZSkJrQkppVlFrIiwic3ViIjoiR1R6OTJIcjRPYm1QYVR6cXpzOFJnd3RacUVSRUR4WFAzT3NjaVlLTk1lSSIsIndpZHRoIjoxMjgwfQ.mRQdUyU2NnAFqfkAeVy1bcpT1PNN0ca3eAJt_iYOyghfSHYlHLsmdlpKMrMrWj27h0WOTPf5FrKE22gH92164vKlAYJl666ZCAE4VqfcIqPK5OyyACJMrDbQg5V2bAQh1XZpa2Og2sz6oGu3CLif77Xcp0Vwak-I89OvQm5lwjmEUWe4BK93DzBNv3HpJHTU0CksDWwtNLxC7IAnuImhLtNr96nb8E93ss6DHMcP9lQMoRlB6ci3FGNwpzeYHvlDCIQd97YV6VWxPhSIl4_NIRiHkG9TfRcKtWPXM2fGdKPIHvHue2KqYEbnBz4-3lnPac6b34XoAT6HM8bxGlhffA', NULL, '2026-07-28 21:12:38.840754+00', '2026-08-03 15:13:52.929652+00'),
    ('906281344ed743cbbdf7dfb0990d783f', '0909f72cfe524f0c95d6e6fcc7ff8167', '576d206ebaad4908be04ebc001c05647', '07. ENVÍO DE FOTO Y ARCHIVO EN WHATSAPP', 12, '<p>Como parte del proceso, necesitarás enviar fotos, evidencias y archivos a través de WhatsApp de forma correcta y ordenada.</p>
<p>En este video aprenderás cómo adjuntar imágenes, documentos y compartirlos adecuadamente para asegurar que tu información sea recibida sin errores.</p>
<p>¿Por qué es importante?<br />Porque el seguimiento depende de la claridad de lo que envías. Una evidencia mal enviada es información que no se puede validar.</p>
<p>Indicaciones clave:<br />• Verifica que la imagen o archivo sea claro y legible<br />• Evita enviar contenido incompleto o borroso<br />• Asegúrate de adjuntar correctamente el archivo antes de enviarlo<br />• Revisa que el envío se haya realizado (doble check)<br />• Mantén orden en lo que compartes (nombre, contexto, momento)</p>
<p>Esto no es solo enviar por enviar.<br />Es comunicar con precisión.</p>
<p>La calidad de tu evidencia define la calidad de tu seguimiento.</p>
<p>Hazlo bien desde el inicio.</p>', 'Como parte del proceso, necesitarás enviar fotos, evidencias y archivos a través de WhatsApp de forma correcta y ordenada.

En este video aprenderás cómo adjuntar imágenes, documentos y compartirlos adecuadamente para asegurar que tu información sea recibida sin errores.

¿Por qué es importante?  
Porque el seguimiento depende de la claridad de lo que envías. Una evidencia mal enviada es información que no se puede validar.

Indicaciones clave:  
• Verifica que la imagen o archivo sea claro y legible  
• Evita enviar contenido incompleto o borroso  
• Asegúrate de adjuntar correctamente el archivo antes de enviarlo  
• Revisa que el envío se haya realizado (doble check)  
• Mantén orden en lo que compartes (nombre, contexto, momento)

Esto no es solo enviar por enviar.  
Es comunicar con precisión.

La calidad de tu evidencia define la calidad de tu seguimiento.

Hazlo bien desde el inicio.', NULL, NULL, 'https://image.mux.com/3eh01esUwfkwQg007eGw3KKpPV01FQPPHKC9Qc3WIv2xrY/thumbnail.jpg?token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJ0IiwiZXhwIjo0OTM4OTU3ODI5LCJraWQiOiJPVjIwMHZ6SWZuZFVCNHdXdTAxbDRjb0hrYTVQQUd3TlYwMEtZSkJrQkppVlFrIiwic3ViIjoiM2VoMDFlc1V3Zmt3UWcwMDdlR3czS0twUFYwMUZRUFBIS0M5UWMzV0l2MnhyWSIsIndpZHRoIjoxMjgwfQ.fWPwIp5XbFExvVqiYJWiy0E05quZEG7gsHO-wtbi3ViFhkTg9d6CjIsHuL1gSU_e4XR-BdiZlpbP9d1EvgYqpuM05_pfR8Z2r7CatmMlggE1xp3rrU-B_wPBc3FBqAbKUJwxf3_IgRU3GQi9hvO77lNXk4Shiq5QNS538DkbF5449583UxASw5OIwATXjUbg_3A3bS9QzKXVhDWCTt0WSjbWIbvgg7kGZBf0mPxEc7d_ZfI28RuVipQ3uQX9JssoQT2J3UaMk5mFeXbBQbmJET-mdHRn6znKuQUgZOf6O1sFfs4Jm9StZX6Ozcvbr6a53lUMpc-cnt6NfjEFUSIxcw', NULL, '2026-07-28 21:12:38.840754+00', '2026-08-03 15:14:21.866469+00'),
    ('0ef2eae6497a4838a04a5468878c3017', '0909f72cfe524f0c95d6e6fcc7ff8167', '576d206ebaad4908be04ebc001c05647', '08. ENVÍO DE AUDIOS EN WHATSAPP', 13, '<p>Como parte del proceso, utilizarás los audios en WhatsApp para reportes, reflexiones y comunicación directa con el equipo.</p>
<p>En este video aprenderás cómo grabar y enviar audios de forma clara, ordenada y funcional.</p>
<p>¿Por qué es importante?<br />Porque tu mensaje debe ser comprendido sin esfuerzo. Un audio desordenado genera confusión y retrasa tu seguimiento.</p>
<p>Indicaciones clave:<br />• Habla con claridad, sin apuros ni interrupciones<br />• Ve directo al punto (evita rodeos innecesarios)<br />• Busca un lugar sin ruido externo<br />• Mantén audios breves y estructurados<br />• Escucha tu audio antes de enviarlo si es necesario</p>
<p>Esto no es solo hablar.<br />Es comunicar con intención.</p>
<p>La calidad de tu comunicación define la calidad de tu proceso.</p>
<p>Hazlo claro. Hazlo preciso.</p>', 'Como parte del proceso, utilizarás los audios en WhatsApp para reportes, reflexiones y comunicación directa con el equipo.

En este video aprenderás cómo grabar y enviar audios de forma clara, ordenada y funcional.

¿Por qué es importante?  
Porque tu mensaje debe ser comprendido sin esfuerzo. Un audio desordenado genera confusión y retrasa tu seguimiento.

Indicaciones clave:  
• Habla con claridad, sin apuros ni interrupciones  
• Ve directo al punto (evita rodeos innecesarios)  
• Busca un lugar sin ruido externo  
• Mantén audios breves y estructurados  
• Escucha tu audio antes de enviarlo si es necesario

Esto no es solo hablar.  
Es comunicar con intención.

La calidad de tu comunicación define la calidad de tu proceso.

Hazlo claro. Hazlo preciso.', NULL, NULL, 'https://image.mux.com/etmhk5w00FFgNH01lDkjSnB8id5BDc01Xna02LxhV02BSy02Q/thumbnail.jpg?token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJ0IiwiZXhwIjo0OTM4OTU3ODMwLCJraWQiOiJPVjIwMHZ6SWZuZFVCNHdXdTAxbDRjb0hrYTVQQUd3TlYwMEtZSkJrQkppVlFrIiwic3ViIjoiZXRtaGs1dzAwRkZnTkgwMWxEa2pTbkI4aWQ1QkRjMDFYbmEwMkx4aFYwMkJTeTAyUSIsIndpZHRoIjoxMjgwfQ.DwSgwLdesRuB3-Hr-A9IcVyIBCK4QVgaww9sf4Ofab1XqMUR-V8QA-xc7N03IJcX1Z0AHptDaGDMdRgPcb0r2R44MPu34ldPH4-Htt61PN76eLvWfJvzgOk4sxY7aPQnRplk_J9PqFZSQmSPE179kcSi4sivTwP5q7oLImBSDYMvhieNK9ynNUUEmmBdyCnKViTKZXR8Q7adSf3wVOYW48o8nl055tp_VXwkqMLifiws1cjVrYGoYLDF5dj49XvrNTyoVHDAOwZCdMojpt-PK8_oJQLX_iteivbzqpb3RIiZ2HW7Ja1yPSZ4S2UeXcRsAF9Q7FxajfsWiYyj72BrBA', NULL, '2026-07-28 21:12:38.840754+00', '2026-08-03 15:14:31.457667+00'),
    ('fcd82dda2b274dcd8149bfb1e7e60efa', '8405f8f0128a46f59f155d1548ffbf8b', 'bf4983a0d30b48f6bda54c65fb3e4e78', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('08a263454039480f9e165801e26bd602', '8405f8f0128a46f59f155d1548ffbf8b', 'bf4983a0d30b48f6bda54c65fb3e4e78', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('c674db27202443dea559ccec689e9e99', '8405f8f0128a46f59f155d1548ffbf8b', 'bb0c2658908145c786487ca25a20936c', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('d31615a034d247299dd8cd5e8ef24053', '8405f8f0128a46f59f155d1548ffbf8b', 'bb0c2658908145c786487ca25a20936c', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('117ae1d3a65d4301829b0ab64ab48d62', '8405f8f0128a46f59f155d1548ffbf8b', 'bb0c2658908145c786487ca25a20936c', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('9fc2e5fb871b42c58c3e210070d657a7', '8405f8f0128a46f59f155d1548ffbf8b', 'a0978f65f5d04912911ff105df7a851f', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('d5d0f38dc09b4d4f8f4066abc088dcaf', '8405f8f0128a46f59f155d1548ffbf8b', 'a0978f65f5d04912911ff105df7a851f', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('370970ec754b4ff8bdf903cdee4f8564', '8405f8f0128a46f59f155d1548ffbf8b', 'a0978f65f5d04912911ff105df7a851f', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('6ea05c779b4d4f7d81aaa69127bac69e', '8405f8f0128a46f59f155d1548ffbf8b', 'a0978f65f5d04912911ff105df7a851f', 'MASTERCLASS', 21, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('5c9675c9835e454ea0186896c044cf29', '1c9e04cbb4e64afcae62ead62d334c5a', 'e8ec568a55df4b50b1e5e7c3b4571c76', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('6151c6501f6341d8b87af2876e624cfd', '1c9e04cbb4e64afcae62ead62d334c5a', 'e8ec568a55df4b50b1e5e7c3b4571c76', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('65b8b75d4eca42e9aa272afaae938ba3', '1c9e04cbb4e64afcae62ead62d334c5a', 'e8ec568a55df4b50b1e5e7c3b4571c76', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('8a28fa653c2642e3a52bb8f90333c1d7', '1c9e04cbb4e64afcae62ead62d334c5a', '8a640c8da980490fbb8045be1f773f1f', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('acb874b6115d4f4a8b9365d2b02d6657', '1c9e04cbb4e64afcae62ead62d334c5a', '8a640c8da980490fbb8045be1f773f1f', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('148ab2283d404ac495c1f3c4f9fffd1c', '1c9e04cbb4e64afcae62ead62d334c5a', '8a640c8da980490fbb8045be1f773f1f', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('27c0713a6d1c4fb1811acd1023d14936', '1c9e04cbb4e64afcae62ead62d334c5a', '1b533a1be83845c68580c7cd3c9945fd', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('ee8025a7f4364ce7b98cc1aefbd74dc8', '1c9e04cbb4e64afcae62ead62d334c5a', '1b533a1be83845c68580c7cd3c9945fd', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e4814ccaa00e4de49395b78a7cce412d', '1c9e04cbb4e64afcae62ead62d334c5a', '1b533a1be83845c68580c7cd3c9945fd', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('cb4b320556d14778a1c669406e946c1e', '1c9e04cbb4e64afcae62ead62d334c5a', '22dd420447114422866f56a96ef0a77d', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('17ed5fba0f674b6da29e052f2587f836', '1c9e04cbb4e64afcae62ead62d334c5a', '22dd420447114422866f56a96ef0a77d', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('a37961bcc7fb477eabbb74752cab4d87', '1c9e04cbb4e64afcae62ead62d334c5a', '22dd420447114422866f56a96ef0a77d', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('8327c108d2f042d096ff819995f589ef', '1c9e04cbb4e64afcae62ead62d334c5a', '47e50ee9787a4360aca4c110639d61b2', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('bfe815a6718b46b8a5c1145398a0098b', '1c9e04cbb4e64afcae62ead62d334c5a', '47e50ee9787a4360aca4c110639d61b2', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('c9333ca4bbfa463e9b0ca74727d7e1a8', '1c9e04cbb4e64afcae62ead62d334c5a', '47e50ee9787a4360aca4c110639d61b2', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('a7d9db8096e1433e973fe628e1c81660', '1c9e04cbb4e64afcae62ead62d334c5a', 'f9843e160945475b943c31aff817ef96', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e5526ab8d72a4c86b2d99c4f2f8a6dc1', '1c9e04cbb4e64afcae62ead62d334c5a', 'f9843e160945475b943c31aff817ef96', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('2258c1ef80fe4819bd073f5c692c2704', '1c9e04cbb4e64afcae62ead62d334c5a', 'f9843e160945475b943c31aff817ef96', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('d3c6fdf5cfd440318f9448a9ed9669dc', '1c9e04cbb4e64afcae62ead62d334c5a', 'f5723b9e59fc4127a6ed229857f0722f', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e4cbc756b77746b8a3c0d0e79fe5daca', '1c9e04cbb4e64afcae62ead62d334c5a', 'f5723b9e59fc4127a6ed229857f0722f', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('920c4a0c08ea4d1aa00dd8b0510c6b5a', '1c9e04cbb4e64afcae62ead62d334c5a', 'f5723b9e59fc4127a6ed229857f0722f', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('fc560bf1881c43668179044f07d399b2', '1c9e04cbb4e64afcae62ead62d334c5a', 'f5723b9e59fc4127a6ed229857f0722f', 'MASTERCLASS', 21, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('0ac3ae73bb5642b6afaa127637e2e263', 'a11488afc79643cf87759dece4f9451d', '953d3f8c476441b69f0383e935515c3b', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('1be2ef4b735840d7b24d59c688f8d94f', 'a11488afc79643cf87759dece4f9451d', '953d3f8c476441b69f0383e935515c3b', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('3be1307c9eb042cbb0650f40a68f0b31', 'a11488afc79643cf87759dece4f9451d', '953d3f8c476441b69f0383e935515c3b', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('03112b8e0da84d0cb5255edcd4b8971a', 'a11488afc79643cf87759dece4f9451d', 'd65078bdca6f4ea9a4087ee632b86d7f', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('de7272be7ea54b83a9153927b6b4a518', 'a11488afc79643cf87759dece4f9451d', 'd65078bdca6f4ea9a4087ee632b86d7f', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('22b5b1f5da3440759a745c331c21057f', 'a11488afc79643cf87759dece4f9451d', 'd65078bdca6f4ea9a4087ee632b86d7f', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('fa3ef0a415cf4355bb62e06a00b1f149', 'a11488afc79643cf87759dece4f9451d', '4a0668fb08d548498f3c6b23ffd730cb', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('469586250e3445fa95036a034cb6c12d', 'a11488afc79643cf87759dece4f9451d', '4a0668fb08d548498f3c6b23ffd730cb', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e7a9c9beb8ff42728f0a017fed6ba6c4', 'a11488afc79643cf87759dece4f9451d', '4a0668fb08d548498f3c6b23ffd730cb', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('2405ca591ffb4d478904efbe490c8a98', 'a11488afc79643cf87759dece4f9451d', '5e4342a2211149c3846fbdc9848b1e5d', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('a2ac65ee3aa04ebeac73d40a9e364ac3', 'a11488afc79643cf87759dece4f9451d', '5e4342a2211149c3846fbdc9848b1e5d', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('5ced87a1fff949af805593fbda5e720d', 'a11488afc79643cf87759dece4f9451d', '5e4342a2211149c3846fbdc9848b1e5d', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('da49aa832ab643a1a756da1c26ad429f', 'a11488afc79643cf87759dece4f9451d', '7c249c3b8049496a9f0d335116dce5f3', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('90e9956f7f694eef90b6feb5b8b80201', '6be9cefa706e40049bae44c38194be29', 'e105a2e1d6a441fa961715058639ac3e', 'ROMPE TU MACACO INTERIOR', 24, '<p><em>Mentoría realizada el día 18 de diciembre del 2025</em></p>
<p>Una mentoría intensa de apertura para quienes inician un nuevo ciclo en RENASER. En este encuentro se profundiza en la confianza, la soberbia, el victimismo, el egocentrismo, la disciplina personal, el contexto que rodea tu vida y la importancia de crear hábitos que sostengan una transformación real. Un llamado directo a dejar el drama, asumir responsabilidad y empezar a renacer desde adentro.</p>', '_Mentoría realizada el día 18 de diciembre del 2025_

Una mentoría intensa de apertura para quienes inician un nuevo ciclo en RENASER. En este encuentro se profundiza en la confianza, la soberbia, el victimismo, el egocentrismo, la disciplina personal, el contexto que rodea tu vida y la importancia de crear hábitos que sostengan una transformación real. Un llamado directo a dejar el drama, asumir responsabilidad y empezar a renacer desde adentro.', 'YOUTUBE', 'https://youtu.be/LXqGmWEfU4Y', 'https://i.ytimg.com/vi/LXqGmWEfU4Y/maxresdefault.jpg', 5250000, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('cc789a9252cc4633a1529d27a10c4a1d', 'a11488afc79643cf87759dece4f9451d', '7c249c3b8049496a9f0d335116dce5f3', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('a7596bfcd50b4d8e998b900ba498d90a', 'a11488afc79643cf87759dece4f9451d', '7c249c3b8049496a9f0d335116dce5f3', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('bfdf14ca036c4adf8725fd73fadf1f6d', 'a11488afc79643cf87759dece4f9451d', '41e29aab1d3c4de1981a9e5c67c8800f', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('f72a222c2e584abe8369fed926231713', 'a11488afc79643cf87759dece4f9451d', '41e29aab1d3c4de1981a9e5c67c8800f', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('9ce19ad5db6f405a9ceb7ed417b367e9', 'a11488afc79643cf87759dece4f9451d', '41e29aab1d3c4de1981a9e5c67c8800f', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('2f37a2a484da4bc2a6613e3fb5f8be97', 'a9dd844e9f254cef950566885ae1f8df', NULL, 'MANUAL DE EMBAJADOR RENASER', 0, '<p>Este manual reúne la estructura, el estándar y la forma de ejecución que define el rol del Embajador RENASER dentro del proceso. No es un documento teórico, es una guía práctica diseñada para que sepas exactamente qué hacer, cómo hacerlo y cuándo hacerlo en tu día a día como líder.</p>
<p>A lo largo de este material encontrarás:</p>
<p>• Tu rol como Embajador y el estándar que debes sostener<br />• Los protocolos diarios que garantizan tu coherencia y liderazgo<br />• El sistema de seguimiento de aprendices (semáforo, reportes y acciones)<br />• La estructura de las reuniones semanales y su correcta ejecución<br />• Los lineamientos para acompañar a cada aprendiz según su fase<br />• Scripts y mensajes listos para mantener claridad y consistencia<br />• Herramientas prácticas (checklists, formularios y tableros de control)</p>
<p>Este manual debe ser revisado constantemente. No se trata de entenderlo una vez, sino de integrarlo en tu forma de operar.</p>
<p>Tu impacto como Embajador no depende de cuánto sabes, sino de cuánto ejecutas.</p>', 'Este manual reúne la estructura, el estándar y la forma de ejecución que define el rol del Embajador RENASER dentro del proceso. No es un documento teórico, es una guía práctica diseñada para que sepas exactamente qué hacer, cómo hacerlo y cuándo hacerlo en tu día a día como líder.

A lo largo de este material encontrarás:

• Tu rol como Embajador y el estándar que debes sostener  
• Los protocolos diarios que garantizan tu coherencia y liderazgo  
• El sistema de seguimiento de aprendices (semáforo, reportes y acciones)  
• La estructura de las reuniones semanales y su correcta ejecución  
• Los lineamientos para acompañar a cada aprendiz según su fase  
• Scripts y mensajes listos para mantener claridad y consistencia  
• Herramientas prácticas (checklists, formularios y tableros de control)

Este manual debe ser revisado constantemente. No se trata de entenderlo una vez, sino de integrarlo en tu forma de operar.

Tu impacto como Embajador no depende de cuánto sabes, sino de cuánto ejecutas.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('130c8422c91241608a43170ed8ddd934', '6be9cefa706e40049bae44c38194be29', '92a0052c94b04daaab5c95b7160c172f', 'TRANSMUTA TU SUFRIMIENTO Y CAMBIA TU IDENTIDAD', 16, '<p><em>Mentoría realizada el día 29 de enero del 2026</em></p>
<p>En esta mentoría se revela uno de los principios más profundos del proceso RENASER: <strong>la verdadera transformación no ocurre sanando emociones, sino cambiando la identidad que las sostiene</strong>. A lo largo de la sesión se explica cómo muchas personas pasan años intentando sanar heridas emocionales sin darse cuenta de que el problema real no es la herida, sino la identidad que se ha construido alrededor del sufrimiento.</p>
<p>La clase explora cómo el ser humano crea inconscientemente identidades basadas en el abandono, la traición, la carencia o el fracaso, y cómo estas identidades terminan reproduciendo los mismos patrones en relaciones, dinero, salud y decisiones de vida. En lugar de enfocarse únicamente en “curar” emociones, el enfoque propone ir al origen: <strong>transformar la estructura interna que creó esos problemas</strong>.</p>', '_Mentoría realizada el día 29 de enero del 2026_

En esta mentoría se revela uno de los principios más profundos del proceso RENASER: **la verdadera transformación no ocurre sanando emociones, sino cambiando la identidad que las sostiene**. A lo largo de la sesión se explica cómo muchas personas pasan años intentando sanar heridas emocionales sin darse cuenta de que el problema real no es la herida, sino la identidad que se ha construido alrededor del sufrimiento.

La clase explora cómo el ser humano crea inconscientemente identidades basadas en el abandono, la traición, la carencia o el fracaso, y cómo estas identidades terminan reproduciendo los mismos patrones en relaciones, dinero, salud y decisiones de vida. En lugar de enfocarse únicamente en “curar” emociones, el enfoque propone ir al origen: **transformar la estructura interna que creó esos problemas**.', 'YOUTUBE', 'https://youtu.be/zdMXJ2XnYVQ', 'https://i.ytimg.com/vi/zdMXJ2XnYVQ/maxresdefault.jpg', NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('db016e9cf1a34168b9fe712f43c69f7a', 'a11488afc79643cf87759dece4f9451d', 'c71a6d387a4649c389d68f53ba87315f', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('448a0fb24f4144d18c807bbec8421cc8', '21fd1d616cd145ac876a29f402cc119a', 'e28de1e0a3ba4b0c856e8f18e495de56', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e605cb529bd948ce80f282ba4f0eff7a', '21fd1d616cd145ac876a29f402cc119a', '6ac008c45b4b4c9fb63d2359621f3dc9', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('3162679714454fc3abe692bcc702e447', '21fd1d616cd145ac876a29f402cc119a', '6ac008c45b4b4c9fb63d2359621f3dc9', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('9471ce8dbd144e138d0188ca73295bd6', '21fd1d616cd145ac876a29f402cc119a', '6ac008c45b4b4c9fb63d2359621f3dc9', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('88bd960ed68548e2900a2f8704ef319f', '21fd1d616cd145ac876a29f402cc119a', '530e88190dc84313968336d9610d625b', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('9cc6c108fe064184b9c903e5abd9a427', '21fd1d616cd145ac876a29f402cc119a', '530e88190dc84313968336d9610d625b', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e0b0f8df494940de84df58f0f432a906', '21fd1d616cd145ac876a29f402cc119a', '530e88190dc84313968336d9610d625b', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('c40109dbeabf4094b73ff52920d355bb', '0909f72cfe524f0c95d6e6fcc7ff8167', NULL, '02. BIENVENIDO(A) A TU VIAJE RENASER', 1, '<p>Has elegido iniciar un proceso diferente: un camino hacia tu verdad, tu potencial y la construcción de una nueva versión de ti.</p>
<p>Durante los próximos 90 días, aprenderás a observarte, comprender tus patrones y convertirte en tu propio psicólogo de alto rendimiento. Este proceso no se trata de acumular información, sino de conectar contigo, soltar máscaras y dejar atrás aquello que ya no representa tu verdadera esencia.</p>
<p>El primer requisito de este viaje es la <strong>paciencia</strong>. Toda transformación profunda necesita tiempo, compromiso y la disposición de atravesar incomodidades sin abandonar el proceso.</p>
<p>Aquí no encontrarás solo palabras bonitas; encontrarás herramientas, experiencias y verdades que te ayudarán a reconocer quién eres, qué te ha detenido y qué necesitas transformar para avanzar.</p>
<p>A lo largo del programa tendrás acceso a audios, pódcast, biblioteca, acompañamiento del equipo RENASER y experiencias diseñadas para fortalecer tu evolución.</p>
<p>Este viaje puede confrontarte, emocionarte y llevarte a descubrir partes de ti que habías olvidado. Pero, sobre todo, puede ayudarte a volver a tu esencia y activar el potencial que siempre ha estado dentro de ti.</p>
<p><strong>Bienvenido(a) al Programa de Formación RENASER.</strong><br />Tu viaje comienza hoy. ✨</p>', 'Has elegido iniciar un proceso diferente: un camino hacia tu verdad, tu potencial y la construcción de una nueva versión de ti.

Durante los próximos 90 días, aprenderás a observarte, comprender tus patrones y convertirte en tu propio psicólogo de alto rendimiento. Este proceso no se trata de acumular información, sino de conectar contigo, soltar máscaras y dejar atrás aquello que ya no representa tu verdadera esencia.

El primer requisito de este viaje es la **paciencia**. Toda transformación profunda necesita tiempo, compromiso y la disposición de atravesar incomodidades sin abandonar el proceso.

Aquí no encontrarás solo palabras bonitas; encontrarás herramientas, experiencias y verdades que te ayudarán a reconocer quién eres, qué te ha detenido y qué necesitas transformar para avanzar.

A lo largo del programa tendrás acceso a audios, pódcast, biblioteca, acompañamiento del equipo RENASER y experiencias diseñadas para fortalecer tu evolución.

Este viaje puede confrontarte, emocionarte y llevarte a descubrir partes de ti que habías olvidado. Pero, sobre todo, puede ayudarte a volver a tu esencia y activar el potencial que siempre ha estado dentro de ti.

**Bienvenido(a) al Programa de Formación RENASER.**  
Tu viaje comienza hoy. ✨', 'YOUTUBE', 'https://www.youtube.com/watch?v=ffLP1xR_Obo', 'https://i.ytimg.com/vi/ffLP1xR_Obo/maxresdefault.jpg', 458000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('ac59361fbe614ac2a695d1125bb02910', '0909f72cfe524f0c95d6e6fcc7ff8167', NULL, '03. LINEAMIENTOS RENASER', 2, '<figure><img src="0909f72cfe524f0c95d6e6fcc7ff8167/assets/70fa834b66cd-80e00f908ecf4770ab29bb96835c29ef6991e09d.jpg" alt="WhatsApp Image 2026-05-29 at 12.43.08 PM.jpeg" loading="lazy" /></figure>
<figure><img src="0909f72cfe524f0c95d6e6fcc7ff8167/assets/6f280aeb29a6-807d0eda142f40b98c9cecba4f1b56a01a398385.jpg" alt="WhatsApp Image 2026-05-29 at 12.43.12 PM.jpeg" loading="lazy" /></figure>
<p><br /></p>', '![WhatsApp Image 2026-05-29 at 12.43.08 PM.jpeg](0909f72cfe524f0c95d6e6fcc7ff8167/assets/70fa834b66cd-80e00f908ecf4770ab29bb96835c29ef6991e09d.jpg)

![WhatsApp Image 2026-05-29 at 12.43.12 PM.jpeg](0909f72cfe524f0c95d6e6fcc7ff8167/assets/6f280aeb29a6-807d0eda142f40b98c9cecba4f1b56a01a398385.jpg)', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('506c59787b764eed8eb32adf05b16d34', '0909f72cfe524f0c95d6e6fcc7ff8167', '3230ec3a0c8a4693bca7a6345560ca92', '01. ENTRAR A MI ESPACIO RENASER', 3, '<p>Entrar a RENASER no es acceder a una plataforma: es asumir responsabilidad sobre tu proceso, tu disciplina y tu presencia diaria. Aquí no consumes contenido, interactúas, te observas y te expones con otros que también eligieron dejar de huir. Comunidad, estructura y seguimiento no existen para motivarte, existen para sostenerte cuando tu mente quiera sabotearte.<br />Si usas este ecosistema como fue diseñado, no vuelves a ser el mismo. <br />RENASER: eliges, te comprometes, creas.</p>', 'Entrar a RENASER no es acceder a una plataforma: es asumir responsabilidad sobre tu proceso, tu disciplina y tu presencia diaria. Aquí no consumes contenido, interactúas, te observas y te expones con otros que también eligieron dejar de huir. Comunidad, estructura y seguimiento no existen para motivarte, existen para sostenerte cuando tu mente quiera sabotearte.  
Si usas este ecosistema como fue diseñado, no vuelves a ser el mismo.   
RENASER: eliges, te comprometes, creas.', 'YOUTUBE', 'https://youtu.be/TFUwh9QPnc8', 'https://i.ytimg.com/vi/TFUwh9QPnc8/maxresdefault.jpg', NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('123503cab8ff49c0a5d405596c288f4f', '0909f72cfe524f0c95d6e6fcc7ff8167', '575bc11a0f334605bcb0c91cbc5003e8', '01. FICHA INICIAL DE PARTICIPANTES', 4, '<figure><img src="0909f72cfe524f0c95d6e6fcc7ff8167/assets/26b6eca99069-40decc88d588442f8c2f9b38b238ff391dc149fc.png" alt="Captura de pantalla 2026-04-14 130302.png" loading="lazy" /></figure>
<p><br /></p>
<p>Este es el punto de partida de tu proceso en RENASER.</p>
<p>A través de esta ficha realizamos un mapeo estratégico de tu situación actual a nivel personal, emocional y operativo. Nos permite comprender con precisión dónde estás, qué patrones estás repitiendo y qué áreas requieren intervención inmediata.</p>
<p>La información que compartas aquí no es solo un registro, es la base sobre la que se diseñará tu proceso de alto rendimiento dentro del programa.</p>
<p>Respóndela con honestidad y profundidad.<br />Tu nivel de claridad en esta etapa definirá la velocidad de tus resultados.</p>
<p><strong>Link del formulario:</strong> <a href="https://forms.gle/sjp45Ni8STBM1QN79" target="_blank" rel="noopener noreferrer">https://forms.gle/sjp45Ni8STBM1QN79</a></p>
<p>Posteriormente, en el grupo de Whatsapp personal, mándanos una captura de la encuesta finalizada con la descripción &quot;Realizado ✅&quot;</p>', '![Captura de pantalla 2026-04-14 130302.png](0909f72cfe524f0c95d6e6fcc7ff8167/assets/26b6eca99069-40decc88d588442f8c2f9b38b238ff391dc149fc.png)

Este es el punto de partida de tu proceso en RENASER.

A través de esta ficha realizamos un mapeo estratégico de tu situación actual a nivel personal, emocional y operativo. Nos permite comprender con precisión dónde estás, qué patrones estás repitiendo y qué áreas requieren intervención inmediata.

La información que compartas aquí no es solo un registro, es la base sobre la que se diseñará tu proceso de alto rendimiento dentro del programa.

Respóndela con honestidad y profundidad.  
Tu nivel de claridad en esta etapa definirá la velocidad de tus resultados.

**Link del formulario:** [https://forms.gle/sjp45Ni8STBM1QN79](https://forms.gle/sjp45Ni8STBM1QN79)

Posteriormente, en el grupo de Whatsapp personal, mándanos una captura de la encuesta finalizada con la descripción "Realizado ✅"', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('d715cc2f2d9c44a19bd9daccfb01a89a', '79ff26b6764b4a288d8db50a8be6934b', 'a380694e7bd34b44b62809769c5e575a', '3 - EL SECRETO PARA VIVIR EN PLENITUD', 11, '<h4><em><strong>La plenitud comienza cuando te abrazas por completo.</strong></em><strong> </strong></h4>
<p>Vivir en plenitud no es evitar los problemas, sino aprender a mantener la paz en medio de ellos.<br />El secreto está en tu presencia: en cómo eliges responder, agradecer y crear, incluso cuando la vida no es perfecta. </p>
<p><strong>Abrázate.</strong> Entiende tus sentimientos sin juzgarlos.<br />La vida es bonita, incluso cuando duele, porque todo lo que sientes te acerca más a ti mismo/a. </p>
<p>Solo cuando sueltas la resistencia y confías en el flujo de la vida, comienza la verdadera plenitud. </p>', '#### _**La plenitud comienza cuando te abrazas por completo.**_** **

Vivir en plenitud no es evitar los problemas, sino aprender a mantener la paz en medio de ellos.  
El secreto está en tu presencia: en cómo eliges responder, agradecer y crear, incluso cuando la vida no es perfecta. 

**Abrázate.** Entiende tus sentimientos sin juzgarlos.  
La vida es bonita, incluso cuando duele, porque todo lo que sientes te acerca más a ti mismo/a. 

Solo cuando sueltas la resistencia y confías en el flujo de la vida, comienza la verdadera plenitud.', 'YOUTUBE', 'https://youtu.be/5ikZHefqYbE?si=WjRqb4KltKCldHbQ', 'https://i.ytimg.com/vi/5ikZHefqYbE/maxresdefault.jpg', 1079000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('980b3ca627e24f37a25e7d20664d7166', '0909f72cfe524f0c95d6e6fcc7ff8167', '576d206ebaad4908be04ebc001c05647', '01. CREACIÓN DE CUENTA DE GMAIL', 5, '<p>Para avanzar de manera ordenada dentro del sistema, es fundamental que cuentes con una cuenta de Gmail activa, correctamente configurada y de uso personal.</p>
<p>En este video encontrarás el paso a paso para crear tu cuenta de forma simple, rápida y sin errores. Te recomendamos seguirlo en tiempo real, pausando si es necesario, para asegurar que todo quede correctamente implementado desde el inicio.</p>
<p>¿Por qué es importante?<br />Porque esta cuenta será tu punto de acceso a todas las herramientas del proceso: plataformas, formularios, sesiones y seguimiento. Sin esta base, el avance se vuelve desordenado.</p>
<p>Indicaciones clave:<br />• Utiliza un correo que revises con frecuencia<br />• Registra tus datos con precisión<br />• Guarda tu usuario y contraseña en un lugar seguro<br />• Evita crear múltiples cuentas innecesarias</p>
<p>Tómate este momento con enfoque.<br />No es un trámite, es parte de tu estructura.</p>
<p>Una vez completado, podrás continuar con el siguiente paso del sistema.</p>', 'Para avanzar de manera ordenada dentro del sistema, es fundamental que cuentes con una cuenta de Gmail activa, correctamente configurada y de uso personal.

En este video encontrarás el paso a paso para crear tu cuenta de forma simple, rápida y sin errores. Te recomendamos seguirlo en tiempo real, pausando si es necesario, para asegurar que todo quede correctamente implementado desde el inicio.

¿Por qué es importante?  
Porque esta cuenta será tu punto de acceso a todas las herramientas del proceso: plataformas, formularios, sesiones y seguimiento. Sin esta base, el avance se vuelve desordenado.

Indicaciones clave:  
• Utiliza un correo que revises con frecuencia  
• Registra tus datos con precisión  
• Guarda tu usuario y contraseña en un lugar seguro  
• Evita crear múltiples cuentas innecesarias

Tómate este momento con enfoque.  
No es un trámite, es parte de tu estructura.

Una vez completado, podrás continuar con el siguiente paso del sistema.', NULL, NULL, 'https://image.mux.com/e4l01dVED7dj7XHEDrGpo0046U7hFufEDt1qtW8FoHlwg/thumbnail.jpg?token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJ0IiwiZXhwIjo0OTM4OTU3ODE5LCJraWQiOiJPVjIwMHZ6SWZuZFVCNHdXdTAxbDRjb0hrYTVQQUd3TlYwMEtZSkJrQkppVlFrIiwic3ViIjoiZTRsMDFkVkVEN2RqN1hIRURyR3BvMDA0NlU3aEZ1ZkVEdDFxdFc4Rm9IbHdnIiwid2lkdGgiOjEyODB9.RKS9GDMKPBsRHBsG3BXk7LUTAR-34-39GccauSW1M6XsQveRhlUbe0dllIEkiZwYZ1zHnEYlMoYaJNdNUg_J4Ne9vExk10wUeAGz6hgiTHI7kiSk7VHSCQVychijujCLD54EBGVjCmFyzA4l4zUB8u3ZHENitdARM_T3i_B_1_hanOIRZGhB4oDu6w6zWbKA0kMlwyfKe-vEVC8uGoyxOk3oRygT5uxRgcOkcjmpXM3m01oPX46FmTjEKy34UUvJALoZiN3acj7vBE1pkF5j9ldaP8dY5WzSlNSL0URkGTifTs5iK_Hocwvf9uxhdlOKaqtl3wdtqjiuq-47oVIoOw', NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('1946c715fd8e4a71b1fb6f20df5dcafe', '909bfb750a1543dfaa682ab3bbe00928', '635ec5fe41e946358a2fee41518167e4', '10. TERAPIA - POTENCIAL INFINITO', 16, '<p>Escucha con audífonos en un lugar cómodo y tranquilo y vive este viaje </p>', 'Escucha con audífonos en un lugar cómodo y tranquilo y vive este viaje', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('3aa63573e4e24fe5b82ea5381f331d7a', '035bf93019964b6b9b3a288a3b0f0ce4', 'ffdcdfedea0e4325ba5bd3e4164d99fe', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('20afeb1cd1444ba485eb7a988df0a531', '0909f72cfe524f0c95d6e6fcc7ff8167', '576d206ebaad4908be04ebc001c05647', '02. USO DE GOOGLE MEET PARA COMPUTADORA', 7, '<p>Para participar correctamente en sesiones, mentorías y espacios de acompañamiento, es fundamental que domines el uso de Google Meet desde tu computadora.</p>
<p>En este video aprenderás cómo ingresar a una reunión, activar cámara y audio, y manejar las funciones básicas para desenvolverte con claridad durante cada sesión.</p>
<p>¿Por qué es importante?<br />Porque tu presencia activa en estos espacios es parte del proceso. No es solo conectarte, es participar con enfoque, orden y compromiso.</p>
<p>Indicaciones clave:<br />• Ingresa siempre desde una computadora (evita el celular)<br />• Verifica tu conexión a internet antes de iniciar<br />• Activa tu cámara (requisito obligatorio)<br />• Ten micrófono funcional y ambiente sin distracciones<br />• Conéctate al menos 5 minutos antes</p>
<p>Este espacio no es casual.<br />Es un entorno de trabajo y avance.</p>
<p>Tu nivel de preparación antes de entrar, define la calidad de tu participación.</p>
<p>Asegúrate de dominar esta herramienta antes de tu próxima sesión.</p>', 'Para participar correctamente en sesiones, mentorías y espacios de acompañamiento, es fundamental que domines el uso de Google Meet desde tu computadora.

En este video aprenderás cómo ingresar a una reunión, activar cámara y audio, y manejar las funciones básicas para desenvolverte con claridad durante cada sesión.

¿Por qué es importante?  
Porque tu presencia activa en estos espacios es parte del proceso. No es solo conectarte, es participar con enfoque, orden y compromiso.

Indicaciones clave:  
• Ingresa siempre desde una computadora (evita el celular)  
• Verifica tu conexión a internet antes de iniciar  
• Activa tu cámara (requisito obligatorio)  
• Ten micrófono funcional y ambiente sin distracciones  
• Conéctate al menos 5 minutos antes

Este espacio no es casual.  
Es un entorno de trabajo y avance.

Tu nivel de preparación antes de entrar, define la calidad de tu participación.

Asegúrate de dominar esta herramienta antes de tu próxima sesión.', NULL, NULL, 'https://image.mux.com/FDxhMVZrTIJDIoaczmt8XYcf6DoczZpER8w200Lk7obs/thumbnail.jpg?token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJ0IiwiZXhwIjo0OTM4OTU3ODIzLCJraWQiOiJPVjIwMHZ6SWZuZFVCNHdXdTAxbDRjb0hrYTVQQUd3TlYwMEtZSkJrQkppVlFrIiwic3ViIjoiRkR4aE1WWnJUSUpESW9hY3ptdDhYWWNmNkRvY3pacEVSOHcyMDBMazdvYnMiLCJ3aWR0aCI6MTI4MH0.dh33GBEYXhbdONPpwwuvOOJcsddjfFNMg-V7GRgdmtr_SLmjseSYvkvyaT2X3Ke3IWmlXTTc8SWelOhoQ6-0Bcy87ylglZD3qareLF7BGfd9Fkbh8I377jRFQp2pZGXxzYUOsa6LIBIig7CHMpsN9dyCqT6irTza2ZcFI9Md_G3JzbLXLwdgKLWZHpq9iuMncD9xUlVaej_9MZGSnX-0dnu7ZaTUl0Qc4ksBahp2d-SdE-QXA5zARMa4G7iBwnYRZ-u1fCa14BgWutNA0G2gujNnU9evTke2fW65hMtkq-rvlM_yfJsaClVG9VU5kg7IRfb4eIQZJSQrDYK4gafG6A', NULL, '2026-07-28 21:12:38.840754+00', '2026-08-03 15:13:35.008419+00'),
    ('d3edcba02e6b4cb1b8d98fe1041becf5', '0909f72cfe524f0c95d6e6fcc7ff8167', '576d206ebaad4908be04ebc001c05647', '03. USO DE GOOGLE MEET PARA CELULAR', 8, '<p>En caso no tengas acceso a una computadora, podrás utilizar Google Meet desde tu celular para ingresar a tus sesiones.</p>
<p>En este video aprenderás cómo descargar la aplicación, ingresar correctamente a una reunión y configurar tu audio y cámara para participar de forma adecuada.</p>
<p>¿Por qué es importante?<br />Porque, aunque el celular es una opción secundaria, tu participación sigue requiriendo presencia, enfoque y compromiso.</p>
<p>Indicaciones clave:<br />• Descarga la aplicación de Google Meet previamente<br />• Ingresa con el correo que registraste (Gmail)<br />• Asegura buena conexión a internet (WiFi recomendado)<br />• Mantén la cámara encendida durante la sesión<br />• Busca un lugar estable, sin movimiento ni distracciones</p>
<p>Recuerda: el celular es un recurso de respaldo, no la opción principal.</p>
<p>Tu nivel de enfoque no depende del dispositivo, sino de tu decisión de estar presente.</p>
<p>Prepárate correctamente antes de cada sesión.</p>', 'En caso no tengas acceso a una computadora, podrás utilizar Google Meet desde tu celular para ingresar a tus sesiones.

En este video aprenderás cómo descargar la aplicación, ingresar correctamente a una reunión y configurar tu audio y cámara para participar de forma adecuada.

¿Por qué es importante?  
Porque, aunque el celular es una opción secundaria, tu participación sigue requiriendo presencia, enfoque y compromiso.

Indicaciones clave:  
• Descarga la aplicación de Google Meet previamente  
• Ingresa con el correo que registraste (Gmail)  
• Asegura buena conexión a internet (WiFi recomendado)  
• Mantén la cámara encendida durante la sesión  
• Busca un lugar estable, sin movimiento ni distracciones

Recuerda: el celular es un recurso de respaldo, no la opción principal.

Tu nivel de enfoque no depende del dispositivo, sino de tu decisión de estar presente.

Prepárate correctamente antes de cada sesión.', NULL, NULL, 'https://image.mux.com/1bmjKs8RI3q00voSJ78ZvaqUrCS5CwFboyLHIpy1HrWg/thumbnail.jpg?token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJ0IiwiZXhwIjo0OTM4OTU3ODI0LCJraWQiOiJPVjIwMHZ6SWZuZFVCNHdXdTAxbDRjb0hrYTVQQUd3TlYwMEtZSkJrQkppVlFrIiwic3ViIjoiMWJtaktzOFJJM3EwMHZvU0o3OFp2YXFVckNTNUN3RmJveUxISXB5MUhyV2ciLCJ3aWR0aCI6MTI4MH0.nhRs5J-UtIpQk_6UwkF1YpLAIY5tzxC3jKEOOpIzztXFlQywRO8xJD9m0U-kqkMJkgAGOBtL6EILJFtx0OCff1_hXQJms-pmZbBKzZPXPmDuKZDCrp8pJ4YN0sP6t_jmlXpHFrCvkLPROkzJwv6sEjKDTkPVP5fg2V_Vm4iWIOzWF8NZoP1MF-7UpAchoWq1LADQjynVFmKpzSb5xlvFCb9SdLnvfUcTiLxZBXJ3yZqb8WwgNhCotjGns8J1vyaSAfk0t5puUnzOtI-w5mvc173R0CpTErdf7EQKC5mao7SYp_yMNZyALqo61r5iGq1TUwaHe_O9E6BJOVCfo3tqLg', NULL, '2026-07-28 21:12:38.840754+00', '2026-08-03 15:13:43.755715+00'),
    ('01588c6f6d5749f9b1991caa681c4a6e', '035bf93019964b6b9b3a288a3b0f0ce4', '5fb2d49b34254feab2190cee1e838c2e', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('8cfb740f8364484ea6bbeb1ed21e669e', '035bf93019964b6b9b3a288a3b0f0ce4', '5fb2d49b34254feab2190cee1e838c2e', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e8da1605d9f14102ad1035934fae55b1', '035bf93019964b6b9b3a288a3b0f0ce4', '5fb2d49b34254feab2190cee1e838c2e', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('2d6327a259b74cbea5b4d76f680744fc', '035bf93019964b6b9b3a288a3b0f0ce4', '54b195c19cd64ee0a65a4686c283b612', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('15954e0ab743438aaeb3b8f0d4594632', '035bf93019964b6b9b3a288a3b0f0ce4', '54b195c19cd64ee0a65a4686c283b612', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('5fbb2f12d881416b8bb555f9ab3c4a60', '035bf93019964b6b9b3a288a3b0f0ce4', '54b195c19cd64ee0a65a4686c283b612', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('7f543dc998c94653b4e3a2d26e309b3c', '0909f72cfe524f0c95d6e6fcc7ff8167', '576d206ebaad4908be04ebc001c05647', '05. USO DE ZOOM PARA CELULAR', 10, '<p>En caso no tengas acceso a una computadora, podrás utilizar Zoom desde tu celular para ingresar a tus sesiones.</p>
<p>En este video aprenderás cómo descargar la aplicación, ingresar correctamente a una reunión y configurar tu audio y cámara para participar de forma adecuada.</p>
<p>¿Por qué es importante?<br />Porque, aunque el celular es una opción secundaria, tu participación sigue requiriendo presencia, enfoque y compromiso.</p>
<p>Indicaciones clave:<br />• Descarga la aplicación de Zoom previamente<br />• Ingresa con el correo que registraste (Gmail)<br />• Asegura buena conexión a internet (WiFi recomendado)<br />• Mantén la cámara encendida durante la sesión<br />• Busca un lugar estable, sin movimiento ni distracciones</p>
<p>Recuerda: el celular es un recurso de respaldo, no la opción principal.</p>
<p>Tu nivel de enfoque no depende del dispositivo, sino de tu decisión de estar presente.</p>
<p>Prepárate correctamente antes de cada sesión.</p>', 'En caso no tengas acceso a una computadora, podrás utilizar Zoom desde tu celular para ingresar a tus sesiones.

En este video aprenderás cómo descargar la aplicación, ingresar correctamente a una reunión y configurar tu audio y cámara para participar de forma adecuada.

¿Por qué es importante?  
Porque, aunque el celular es una opción secundaria, tu participación sigue requiriendo presencia, enfoque y compromiso.

Indicaciones clave:  
• Descarga la aplicación de Zoom previamente  
• Ingresa con el correo que registraste (Gmail)  
• Asegura buena conexión a internet (WiFi recomendado)  
• Mantén la cámara encendida durante la sesión  
• Busca un lugar estable, sin movimiento ni distracciones

Recuerda: el celular es un recurso de respaldo, no la opción principal.

Tu nivel de enfoque no depende del dispositivo, sino de tu decisión de estar presente.

Prepárate correctamente antes de cada sesión.', NULL, NULL, 'https://image.mux.com/iyvXY5Rz9HM3R37mTCgwM02kUo00Wwt003ETCjo2fCbPJ8/thumbnail.jpg?token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJ0IiwiZXhwIjo0OTM4OTU3ODI3LCJraWQiOiJPVjIwMHZ6SWZuZFVCNHdXdTAxbDRjb0hrYTVQQUd3TlYwMEtZSkJrQkppVlFrIiwic3ViIjoiaXl2WFk1Uno5SE0zUjM3bVRDZ3dNMDJrVW8wMFd3dDAwM0VUQ2pvMmZDYlBKOCIsIndpZHRoIjoxMjgwfQ.EE1Mv7K9lAsS7enfYXlcLRLsfCOh17urKV9nOBOcXpdhfol19SABaBm0AxU0NS1-Sc9VnxhGkmCv6yPJlwfUJBioPmzvQDZ8gU-y53ImmkwaFth5nseX2vqN9rb-ywd-T0iRBXGlWz5N7y8o7jAOA4oogl2uKWOyq9yUwOWx1-lsRP8Oq6T9NFv9AxkW_xaK54XKqBEntJqWbP864lVZHmebyLyTultfKM1iFaZZlZsgW4HbyJOCF5hOEWiRwnsh2jnPXTcNOFMbOJbx_af_ITUwPyjDyT18SQDVM6OmFDWEy8Xnri6Ayb_n3F_KBGNUfLJLop0tWzmymhakFEiVdg', NULL, '2026-07-28 21:12:38.840754+00', '2026-08-03 15:14:04.800195+00'),
    ('f1e6512279c542829ff627e79575f163', '0909f72cfe524f0c95d6e6fcc7ff8167', '576d206ebaad4908be04ebc001c05647', '06. USO DE STRAVA', 11, '<p>Como parte del sistema, utilizaremos Strava para registrar y dar seguimiento a tu actividad física (caminatas, trote, entrenamiento, etc.).</p>
<p>En este video aprenderás cómo descargar la aplicación, crear tu cuenta y registrar correctamente tus actividades.</p>
<p>¿Por qué es importante?<br />Porque lo que no se mide, no se mejora. Este registro forma parte de tu disciplina y evidencia de avance dentro del proceso.</p>
<p>Indicaciones clave:<br />• Descarga Strava y crea tu cuenta<br />• Registra cada actividad que realices (aunque sea corta)<br />• Asegúrate de guardar correctamente cada sesión<br />• Mantén constancia, no perfección<br />• Comparte tu avance cuando sea solicitado</p>
<p>Este no es un tema deportivo.<br />Es un tema de estructura, disciplina y seguimiento.</p>
<p>Cada registro suma. Cada omisión también.</p>
<p>Hazlo parte de tu sistema.</p>', 'Como parte del sistema, utilizaremos Strava para registrar y dar seguimiento a tu actividad física (caminatas, trote, entrenamiento, etc.).

En este video aprenderás cómo descargar la aplicación, crear tu cuenta y registrar correctamente tus actividades.

¿Por qué es importante?  
Porque lo que no se mide, no se mejora. Este registro forma parte de tu disciplina y evidencia de avance dentro del proceso.

Indicaciones clave:  
• Descarga Strava y crea tu cuenta  
• Registra cada actividad que realices (aunque sea corta)  
• Asegúrate de guardar correctamente cada sesión  
• Mantén constancia, no perfección  
• Comparte tu avance cuando sea solicitado

Este no es un tema deportivo.  
Es un tema de estructura, disciplina y seguimiento.

Cada registro suma. Cada omisión también.

Hazlo parte de tu sistema.', NULL, NULL, 'https://image.mux.com/WpF311InG6DW6ShTBnGcaxHH400gJgAm3hQvxaq4EcEQ/thumbnail.jpg?token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJ0IiwiZXhwIjo0OTM4OTU3ODI4LCJraWQiOiJPVjIwMHZ6SWZuZFVCNHdXdTAxbDRjb0hrYTVQQUd3TlYwMEtZSkJrQkppVlFrIiwic3ViIjoiV3BGMzExSW5HNkRXNlNoVEJuR2NheEhINDAwZ0pnQW0zaFF2eGFxNEVjRVEiLCJ3aWR0aCI6MTI4MH0.AcpMVDr22XFcOAeTJRUFSvensaQMTdea_6CzHH2VNSyY62prgJTTnmlDJKp1WCdPVC_96GjaQMXiIcipVYIj_sjO6p8Wi792BnCN7fcF-tJ4ud3d4a5IJMroXXzNZZ_JMAm0dKWzAVrXABgEBy_wF7U1J0puuLAWf9XpN1LE-5tsctv4GVkukYQTfwlTWqzFMQPPixn-jSEX46g9KAnBWsEIkJdVvj6sCxhYj81WB8ybg6zQjGsvU2j7nWnsIdDwro4M2p1T_8mIbRBlOqXPtHZSSuyKp5XYzP0lAzKUImrspdtZzzY1hhuxoYa4PjwjCK7Yl9dWjyPGwPGZc7_MQQ', NULL, '2026-07-28 21:12:38.840754+00', '2026-08-03 15:14:13.615489+00'),
    ('e2b0b83eef2c4ff5a95c9495c28002bf', '79ff26b6764b4a288d8db50a8be6934b', '6529c1662755491689dc496e0422aeea', 'Clase 1 | Experiencia General Renaser', 0, '<blockquote><h2><strong>En la siguiente clase, tienes un recorrido general de entendimiento, de como llevaras este primer mes. Es importante meditar sobre esta experiencia, para que tengas resultados. </strong></h2><h2></h2></blockquote>', '> ## **En la siguiente clase, tienes un recorrido general de entendimiento, de como llevaras este primer mes. Es importante meditar sobre esta experiencia, para que tengas resultados. **
> ##', 'YOUTUBE', 'https://youtu.be/_2wMACWhi3I', 'https://i.ytimg.com/vi/_2wMACWhi3I/maxresdefault.jpg', 647000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('535d959e1f764ec09416ce15bddfad4d', '79ff26b6764b4a288d8db50a8be6934b', '6529c1662755491689dc496e0422aeea', 'Clase 2 | Como iniciar tus primeros 7 Dias', 1, '<h2>La Semana 1: Marca el inicio real de todo el programa. Aquí no vienes a mejorar tu vida. Vienes a cambiar la forma desde la cual la has venido viviendo. Antes de avanzar, sanar, construir o decidir, hay algo que debe quedar claro: no puedes transformar lo que sigues sosteniendo en automático.</h2>
<h2>Esta semana cumple una sola función esencial: Sacarte del modo inconsciente. Durante estos primeros días detienes la inercia con la que vienes operando, tomas conciencia de cómo piensas, sientes y reaccionas en tiempo real, y empiezas a diferenciar entre lo que haces por elección y lo que repites por hábito.</h2>
<h2>Aquí no se exige perfección, se exige presencia. No es una semana de información, es una semana de observación activa. Todo lo que verás —tus impulsos, tus excusas, tus reacciones, tus silencios— no es el problema, es el mapa. Si no observas con honestidad, el proceso no avanza. Si observas pero no te haces responsable, el proceso se estanca. Si observas y te haces responsable, el sistema comienza a responder.</h2>
<h2>Esta semana no busca resultados externos, busca activar conciencia interna sostenida. Avanza sin prisa, pero no avances distraído. Porque desde aquí en adelante, todo lo que hagas ya no será inconsciente.</h2>
<h2>Bienvenido(a) a la Semana 1, aquí comienza RENASER.</h2>', '## La Semana 1: Marca el inicio real de todo el programa. Aquí no vienes a mejorar tu vida. Vienes a cambiar la forma desde la cual la has venido viviendo. Antes de avanzar, sanar, construir o decidir, hay algo que debe quedar claro: no puedes transformar lo que sigues sosteniendo en automático.

## Esta semana cumple una sola función esencial: Sacarte del modo inconsciente. Durante estos primeros días detienes la inercia con la que vienes operando, tomas conciencia de cómo piensas, sientes y reaccionas en tiempo real, y empiezas a diferenciar entre lo que haces por elección y lo que repites por hábito.

## Aquí no se exige perfección, se exige presencia. No es una semana de información, es una semana de observación activa. Todo lo que verás —tus impulsos, tus excusas, tus reacciones, tus silencios— no es el problema, es el mapa. Si no observas con honestidad, el proceso no avanza. Si observas pero no te haces responsable, el proceso se estanca. Si observas y te haces responsable, el sistema comienza a responder.

## Esta semana no busca resultados externos, busca activar conciencia interna sostenida. Avanza sin prisa, pero no avances distraído. Porque desde aquí en adelante, todo lo que hagas ya no será inconsciente.

## Bienvenido(a) a la Semana 1, aquí comienza RENASER.', 'YOUTUBE', 'https://www.youtube.com/watch?v=dPpcY8tbQ_w', 'https://i.ytimg.com/vi/dPpcY8tbQ_w/maxresdefault.jpg', 1808000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('27b59edb038949e58f9800cfe0798a4e', '79ff26b6764b4a288d8db50a8be6934b', '059e60f9eb0e4c25abd88cbab1e52591', '01. GUÍA SEMANA 1 | imprimir', 2, '<p>Esta guía marca el inicio real de tu proceso RENASER.<br />No es información para entender, es estructura para <strong>observarte con honestidad</strong> y empezar a romper patrones que operan en automático.</p>
<p>Durante esta primera semana se establece la base del trabajo:<br />conciencia, responsabilidad y presencia. Aquí no se busca cambiarte todavía, sino <strong>verte con claridad</strong>, sin juicio y sin excusas.</p>
<p>Todo lo que aparece esta semana —pensamientos, emociones, resistencia, incomodidad— <strong>es parte del proceso</strong>, no un error.</p>
<p>Esta guía te ayudará a:</p>
<ul><li><p>Comprender cómo estás funcionando hoy, sin maquillarlo.</p></li><li><p>Identificar los patrones que repites sin darte cuenta.</p></li><li><p>Empezar a crear espacio interno para una transformación real y sostenible.</p></li></ul>
<p>Semana 1 no se trata de hacerlo perfecto.<br />Se trata de <strong>hacerte presente</strong>.</p>
<p>Lee la guía con calma.<br />Aplica lo indicado.<br />Y permite que el proceso haga su trabajo.</p>
<p>Aquí comienza RENASER.</p>', 'Esta guía marca el inicio real de tu proceso RENASER.  
No es información para entender, es estructura para **observarte con honestidad** y empezar a romper patrones que operan en automático.

Durante esta primera semana se establece la base del trabajo:  
conciencia, responsabilidad y presencia. Aquí no se busca cambiarte todavía, sino **verte con claridad**, sin juicio y sin excusas.

Todo lo que aparece esta semana —pensamientos, emociones, resistencia, incomodidad— **es parte del proceso**, no un error.

Esta guía te ayudará a:

- Comprender cómo estás funcionando hoy, sin maquillarlo.
- Identificar los patrones que repites sin darte cuenta.
- Empezar a crear espacio interno para una transformación real y sostenible.

Semana 1 no se trata de hacerlo perfecto.  
Se trata de **hacerte presente**.

Lee la guía con calma.  
Aplica lo indicado.  
Y permite que el proceso haga su trabajo.

Aquí comienza RENASER.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('eae65766c78f4668b306c40f6d299224', '79ff26b6764b4a288d8db50a8be6934b', '059e60f9eb0e4c25abd88cbab1e52591', '02. CHECKLIST SEMANA 1 | imprimir', 3, '<p>Este checklist no es una lista para cumplir por cumplir.<br />Es una herramienta para <strong>entrenar presencia</strong>, ordenar tu proceso y hacer visible cómo te estás relacionando contigo durante esta primera semana.</p>
<p>No evalúa resultados externos.<br />Evalúa <strong>nivel de conciencia y compromiso real</strong>.</p>
<p>Usa este checklist a diario como un ancla.<br />No te juzgues si algo no se cumple: obsérvalo.</p>
<p>Este checklist te ayuda a:</p>
<ul><li><p>Mantener estructura sin rigidez.</p></li><li><p>Detectar resistencia, evasión o autoexigencia.</p></li><li><p>Fortalecer disciplina consciente, no forzada.</p></li></ul>
<p>Marca cada punto con honestidad.<br />Si un día no cumpliste, no lo ocultes: <strong>regístralo</strong>.</p>
<p>Semana 1 no se trata de avanzar rápido.<br />Se trata de <strong>no escapar de ti</strong>.</p>
<p>El proceso funciona cuando tú estás presente.</p>', 'Este checklist no es una lista para cumplir por cumplir.  
Es una herramienta para **entrenar presencia**, ordenar tu proceso y hacer visible cómo te estás relacionando contigo durante esta primera semana.

No evalúa resultados externos.  
Evalúa **nivel de conciencia y compromiso real**.

Usa este checklist a diario como un ancla.  
No te juzgues si algo no se cumple: obsérvalo.

Este checklist te ayuda a:

- Mantener estructura sin rigidez.
- Detectar resistencia, evasión o autoexigencia.
- Fortalecer disciplina consciente, no forzada.

Marca cada punto con honestidad.  
Si un día no cumpliste, no lo ocultes: **regístralo**.

Semana 1 no se trata de avanzar rápido.  
Se trata de **no escapar de ti**.

El proceso funciona cuando tú estás presente.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('115fa00060f2445d8d9bf7008df0b020', '79ff26b6764b4a288d8db50a8be6934b', '059e60f9eb0e4c25abd88cbab1e52591', '03. PLANTILLA DIARIO DE AUTOCONCIENCIA', 4, '<p>Puedes hacerlo en tu celular, escribiendo o utilizándoselo la herramienta de audio (transcripción) para que solo te tome menos de 3 minutos hacerlo correctamente. </p>
<p>Cualquier duda, tienes a nuestro equipo administrativo Renaser o a tu mentor(a).</p>
<p><br /></p>
<p>Aqui te compartimos el ejemplo que también esta en tu cheklist.</p>
<figure><img src="79ff26b6764b4a288d8db50a8be6934b/assets/7f9c73aa4b7b-a52225f9563a46c192d7627fcab1972d83662632.png" alt="CHECKLIST DIARIO - SEMANA 1.png" loading="lazy" /></figure>
<p><br /></p>', 'Puedes hacerlo en tu celular, escribiendo o utilizándoselo la herramienta de audio (transcripción) para que solo te tome menos de 3 minutos hacerlo correctamente. 

Cualquier duda, tienes a nuestro equipo administrativo Renaser o a tu mentor(a).

Aqui te compartimos el ejemplo que también esta en tu cheklist.

![CHECKLIST DIARIO - SEMANA 1.png](79ff26b6764b4a288d8db50a8be6934b/assets/7f9c73aa4b7b-a52225f9563a46c192d7627fcab1972d83662632.png)', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('9384d34d5b1b4b62b7c509e3e1372d2f', '1c4d035721394f9c8504883a25b88d3a', '061dbb3159404ddfb9b1fa6d35885747', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('f6143d45dd834818ac6ed2eeced22ef3', '79ff26b6764b4a288d8db50a8be6934b', '059e60f9eb0e4c25abd88cbab1e52591', '04. MENSAJE DE EJEMPLO EN SKOOL', 5, '<p>SKOOL es tu <strong>bitácora de proceso y crecimiento</strong>. </p>
<p><strong>Esta actividad es un proceso terapéutico de introspección, no es un azar. la mayoria de personas viven en piloto automático, esto te permitirá detenerte para poder ser mas sabio a la hora de tomar desiciones. </strong></p>
<p>Debes tomar en cuenta que hay etapas o fases del sistema que te obliga a solo escribir todo en positivo, y otras fases que te obliga a escribir en negativo (las indicaciones se darán en las mentorias) </p>
<p>Para que podamos acompañarte con precisión, escribe siempre de la siguiente manera:</p>
<p>1️⃣ <strong>Empieza tu mensaje indicando el día </strong><br />Ejemplo: <em>Día 3 – Claridad y Responsabilidad</em></p>
<p>2️⃣ <strong>Redacta como si fuese un diario personal </strong><br />Evita mensajes desordenados o frases sueltas sin contexto.</p>
<p>3️⃣ <strong>Habla en primera persona</strong><br />Describe lo que pensaste, sentiste, comprendiste y decidiste.</p>
<p>4️⃣ <strong>Sé honesto y concreto</strong><br />No escribas lo que “crees que deberías decir”, escribe lo que realmente pasó dentro de ti.</p>
<p>5️⃣ <strong>Cierra con una decisión o revelación </strong><br />Ejemplo: <em>Hoy elijo hacerme responsable y actuar distinto. | hoy me di cuenta que aun seguía en mi victimismo en relación con mi pareja, ahora...</em></p>
<p>🔒 Mensajes confusos, incompletos o fuera de formato <strong>no podrán ser evaluados correctamente</strong>.</p>
<p>Este orden no es una exigencia externa.<br />Es parte de entrenar tu mente, tu enfoque y tu nivel de compromiso.</p>
<p>Escribe con presencia.<br />Escribe con intención.<br />Escribe como la persona que estás construyendo.</p>
<p><strong>Equipo RENASER</strong></p>
<figure><img src="79ff26b6764b4a288d8db50a8be6934b/assets/cb80a9e2f9f4-cb6673d8cfa14d829118ad2d094f99701f3a6e11.png" alt="GUÍA RENASER - SEMANA 1.png" loading="lazy" /></figure>
<p><br /></p>', 'SKOOL es tu **bitácora de proceso y crecimiento**. 

**Esta actividad es un proceso terapéutico de introspección, no es un azar. la mayoria de personas viven en piloto automático, esto te permitirá detenerte para poder ser mas sabio a la hora de tomar desiciones. **

Debes tomar en cuenta que hay etapas o fases del sistema que te obliga a solo escribir todo en positivo, y otras fases que te obliga a escribir en negativo (las indicaciones se darán en las mentorias) 

Para que podamos acompañarte con precisión, escribe siempre de la siguiente manera:

1️⃣ **Empieza tu mensaje indicando el día **  
Ejemplo: _Día 3 – Claridad y Responsabilidad_

2️⃣ **Redacta como si fuese un diario personal **  
Evita mensajes desordenados o frases sueltas sin contexto.

3️⃣ **Habla en primera persona**  
Describe lo que pensaste, sentiste, comprendiste y decidiste.

4️⃣ **Sé honesto y concreto**  
No escribas lo que “crees que deberías decir”, escribe lo que realmente pasó dentro de ti.

5️⃣ **Cierra con una decisión o revelación **  
Ejemplo: _Hoy elijo hacerme responsable y actuar distinto. | hoy me di cuenta que aun seguía en mi victimismo en relación con mi pareja, ahora..._

🔒 Mensajes confusos, incompletos o fuera de formato **no podrán ser evaluados correctamente**.

Este orden no es una exigencia externa.  
Es parte de entrenar tu mente, tu enfoque y tu nivel de compromiso.

Escribe con presencia.  
Escribe con intención.  
Escribe como la persona que estás construyendo.

**Equipo RENASER**

![GUÍA RENASER - SEMANA 1.png](79ff26b6764b4a288d8db50a8be6934b/assets/cb80a9e2f9f4-cb6673d8cfa14d829118ad2d094f99701f3a6e11.png)', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('2d71cbe5555e4cf1ba08cb25e65f41ae', '79ff26b6764b4a288d8db50a8be6934b', '481a396b37754a59b9151d3e95684968', '01. TIERRA, AGUA Y FUEGO: RITUAL PARA VOLVER A TI', 6, '<p>Si ya registraste tu vida por 7 días, ahora viene el punto donde la mayoría se sabotea: Seguir cargando basura. RENASER no es acumular técnicas. Es cortar.</p>
<p>En este paso vas a hacer algo radical: tomar tu lista y eliminar el 50%. Pensamientos, emociones y conductas que te drenan, que te distraen, que te roban tiempo. Porque tu problema no es falta de tiempo… es exceso de ruido. Y cuando quitas el ruido, aparece algo que casi nunca sientes: Espacio.</p>
<p>Luego viene la recodificación: Entender que tus “reacciones” no son destino, son códigos inconscientes. Piensas algo, sientes algo, haces algo… y repites el mismo ciclo, pero aquí tú vuelves a ser creador: reestructuras el recuerdo, cambias la emoción, y eliges una nueva conducta, no sufres por lo que pasó, sufres por el guión que sigues actuando.</p>
<p>Y para alimentar tu prana —tu energía vital— activas las tres respiraciones: tierra (sentir sin huir), agua (soltar sin juzgar), y fuego (creer en ti con cuerpo de guerrero). Primero sueltas, luego te empoderas. Este video no es teoría. Es práctica. Hazlo y mírate cambiar. </p>
<p>RENASER corta el ruido, reescribe el código.</p>
<p>Respira y renace.</p>', 'Si ya registraste tu vida por 7 días, ahora viene el punto donde la mayoría se sabotea: Seguir cargando basura. RENASER no es acumular técnicas. Es cortar.

En este paso vas a hacer algo radical: tomar tu lista y eliminar el 50%. Pensamientos, emociones y conductas que te drenan, que te distraen, que te roban tiempo. Porque tu problema no es falta de tiempo… es exceso de ruido. Y cuando quitas el ruido, aparece algo que casi nunca sientes: Espacio.

Luego viene la recodificación: Entender que tus “reacciones” no son destino, son códigos inconscientes. Piensas algo, sientes algo, haces algo… y repites el mismo ciclo, pero aquí tú vuelves a ser creador: reestructuras el recuerdo, cambias la emoción, y eliges una nueva conducta, no sufres por lo que pasó, sufres por el guión que sigues actuando.

Y para alimentar tu prana —tu energía vital— activas las tres respiraciones: tierra (sentir sin huir), agua (soltar sin juzgar), y fuego (creer en ti con cuerpo de guerrero). Primero sueltas, luego te empoderas. Este video no es teoría. Es práctica. Hazlo y mírate cambiar. 

RENASER corta el ruido, reescribe el código.

Respira y renace.', 'YOUTUBE', 'https://www.youtube.com/watch?v=WJBVncaQKQE&t=1s', 'https://i.ytimg.com/vi/WJBVncaQKQE/maxresdefault.jpg', 1306000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('afc0c049e3ef4d0fab15c8b96f798439', '79ff26b6764b4a288d8db50a8be6934b', '25dcb5cb74d843afb28801032487f9d8', '1 - ¿AMOR PROPIO?', 7, '<h4>¿Y si el verdadero bloqueo eres tú? ✨</h4>
<p>Este no es un curso que busca consolarte, sino confrontarte. Es una experiencia directa, intensa y profundamente transformadora, diseñada para desactivar excusas y despertar tu poder interior. Aquí no vienes a aprender teorías: vienes a liberarte de los patrones mentales, la ansiedad y el caos emocional que te atan.</p>
<p>No es para todos. Es para quienes están listos para romper con todo y comenzar de verdad.¿Estás listo/a?</p>', '#### ¿Y si el verdadero bloqueo eres tú? ✨

Este no es un curso que busca consolarte, sino confrontarte. Es una experiencia directa, intensa y profundamente transformadora, diseñada para desactivar excusas y despertar tu poder interior. Aquí no vienes a aprender teorías: vienes a liberarte de los patrones mentales, la ansiedad y el caos emocional que te atan.

No es para todos. Es para quienes están listos para romper con todo y comenzar de verdad.¿Estás listo/a?', 'YOUTUBE', 'https://youtu.be/s87xo-gcUw8?si=EUWbsH9N9y7hxVIK', 'https://i.ytimg.com/vi/s87xo-gcUw8/maxresdefault.jpg', 689000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('4be683919243479da94d6e30c37dd091', '79ff26b6764b4a288d8db50a8be6934b', '25dcb5cb74d843afb28801032487f9d8', 'EBOOK 1 - ¿AMOR PROPIO?', 8, '<p>Este ebook no fue creado para motivarte ni para decirte que todo estará bien. Fue creado para despertarte. Nada de lo que leerás aquí cambiará tu vida si tú no lo permites. Yo no voy a cambiar tu vida. TÚ LO HARÁS.</p>', 'Este ebook no fue creado para motivarte ni para decirte que todo estará bien. Fue creado para despertarte. Nada de lo que leerás aquí cambiará tu vida si tú no lo permites. Yo no voy a cambiar tu vida. TÚ LO HARÁS.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('9d7089b613ec423ba8863ec9a70d5946', '79ff26b6764b4a288d8db50a8be6934b', 'adb24bef4f1e49c4998b666af1083c49', '2 - LA RAÍZ DE TODO SUFRIMIENTO', 9, '<h4>Cuando dejas de ser víctima, empieza tu sanación.</h4>
<p>El sufrimiento no nace de lo que te sucede, sino de la forma en que lo interpretas.<br />Esta clase te guía a reconocer las raíces ocultas de tu dolor: los apegos, las expectativas y las resistencias que te mantienen atrapado/a en un ciclo de frustración y culpa.</p>
<p>También descubrirás una verdad incómoda: muchas veces, la raíz del sufrimiento es el <strong>victimismo</strong>, esa energía que te hace creer que no tienes poder sobre tu vida.</p>
<p>Aquí aprenderás a mirar dentro de ti con valentía, a soltar el control y a comprender que toda herida guarda una lección de liberación. </p>', '#### Cuando dejas de ser víctima, empieza tu sanación.

El sufrimiento no nace de lo que te sucede, sino de la forma en que lo interpretas.  
Esta clase te guía a reconocer las raíces ocultas de tu dolor: los apegos, las expectativas y las resistencias que te mantienen atrapado/a en un ciclo de frustración y culpa.

También descubrirás una verdad incómoda: muchas veces, la raíz del sufrimiento es el **victimismo**, esa energía que te hace creer que no tienes poder sobre tu vida.

Aquí aprenderás a mirar dentro de ti con valentía, a soltar el control y a comprender que toda herida guarda una lección de liberación.', 'YOUTUBE', 'https://youtu.be/QBJEp2iiZow?si=KQj1mV2fJ7-Qwh1q', 'https://i.ytimg.com/vi/QBJEp2iiZow/maxresdefault.jpg', 989000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('69192e76ba904609b47908d356035fc2', '79ff26b6764b4a288d8db50a8be6934b', 'adb24bef4f1e49c4998b666af1083c49', 'EBOOK 2 - LA RAÍZ DE TODO SUFRIMIENTO', 10, '<p>Cuando no comprendemos esto, buscamos culpables afuera, defendemos ideologías, nos dividimos y sufrimos. Este ebook no está diseñado para consolarte. Está diseñado para despertarte.</p>', 'Cuando no comprendemos esto, buscamos culpables afuera, defendemos ideologías, nos dividimos y sufrimos. Este ebook no está diseñado para consolarte. Está diseñado para despertarte.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('0f96d438e329463ca4382b7bf94b3db7', '1c4d035721394f9c8504883a25b88d3a', '061dbb3159404ddfb9b1fa6d35885747', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('63e1c559750e4ab39b1cf47a462cf423', '1c4d035721394f9c8504883a25b88d3a', '061dbb3159404ddfb9b1fa6d35885747', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('abb6dda149374e47899c0f9895bae8ae', '1c4d035721394f9c8504883a25b88d3a', '3d60126bf7794db9a4a0cb21364260b5', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('1cdd59643b774bda8023e4672c7c8956', '1c4d035721394f9c8504883a25b88d3a', '3d60126bf7794db9a4a0cb21364260b5', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('1bf7bd86461849998ad6cc1dd8681328', '79ff26b6764b4a288d8db50a8be6934b', 'a380694e7bd34b44b62809769c5e575a', 'EBOOK 3 - EL SECRETO PARA VIVIR EN PLENITUD', 12, '<p>La mayoría de las personas no vive en plenitud.<br />Sobrevive.</p>
<p>Cargamos heridas, cicatrices emocionales y promesas internas que hicimos en momentos de dolor:<br />“Ya no vuelvo a sentir”,<br />“Ya no me enamoro”,<br />“Mejor no me ilusiono”.</p>
<p>Pero hay una verdad que pocos se atreven a aceptar:</p>
<p><strong>Vivir en plenitud es aprender a sentir.</strong></p>
<p>Este ebook no busca que evites el dolor.<br />Busca que <strong>dejes de huir de la vida</strong>.</p>', 'La mayoría de las personas no vive en plenitud.  
Sobrevive.

Cargamos heridas, cicatrices emocionales y promesas internas que hicimos en momentos de dolor:  
“Ya no vuelvo a sentir”,  
“Ya no me enamoro”,  
“Mejor no me ilusiono”.

Pero hay una verdad que pocos se atreven a aceptar:

**Vivir en plenitud es aprender a sentir.**

Este ebook no busca que evites el dolor.  
Busca que **dejes de huir de la vida**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('e7ad890d3b6c44969d24d1159bab309b', '79ff26b6764b4a288d8db50a8be6934b', '808b02ad63884da5aaa4c13598796290', '4 - SENTIR MÁS PARA VIVIR MÁS', 13, '<p><em><strong>Sentir más para vivir más</strong></em><br />La vida no se expande cuando todo está perfecto, sino cuando te permites sentir lo que realmente está pasando dentro de ti.<br />Sentir no es debilidad; es volver a tu verdad.<br />Cada emoción —la suave y la incómoda— te revela algo que habías olvidado: que estás vivo(a), que sigues en camino, que aún puedes elegir.<br />Cuando dejas de anestesiarte y empiezas a escuchar tu mundo interno, tu vida se vuelve más auténtica, más intensa y más tuya.<br />Sentir es el puente hacia vivir con más presencia, más claridad y más propósito.<br />Cuanto más te permites sentir… más espacio tienes para renaser.</p>', '_**Sentir más para vivir más**_  
La vida no se expande cuando todo está perfecto, sino cuando te permites sentir lo que realmente está pasando dentro de ti.  
Sentir no es debilidad; es volver a tu verdad.  
Cada emoción —la suave y la incómoda— te revela algo que habías olvidado: que estás vivo(a), que sigues en camino, que aún puedes elegir.  
Cuando dejas de anestesiarte y empiezas a escuchar tu mundo interno, tu vida se vuelve más auténtica, más intensa y más tuya.  
Sentir es el puente hacia vivir con más presencia, más claridad y más propósito.  
Cuanto más te permites sentir… más espacio tienes para renaser.', 'YOUTUBE', 'https://youtu.be/tq5bLrMgvdc?si=7_Rq4SjxuL6Ni-tg', 'https://i.ytimg.com/vi/tq5bLrMgvdc/maxresdefault.jpg', 1024000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('386b9d2e2a174299b33612b32fe61294', '79ff26b6764b4a288d8db50a8be6934b', '808b02ad63884da5aaa4c13598796290', 'EBOOK 4 - SENTIR MÁS PARA VIVIR MÁS', 14, '<p>El mayor regalo que hemos recibido en esta vida no es el dinero, el éxito ni el reconocimiento.<br />El regalo más grande es <strong>sentir</strong>.</p>
<p>Sentir no es algo que la ciencia pueda medir por completo, porque el verdadero sentir no nace del cuerpo ni de la mente: <strong>nace del alma</strong>.</p>
<p>Solo cuando empezamos a sentir nuestro corazón y nuestra alma, podemos encontrarnos con nosotros mismos… y recién entonces, conectar con otros.</p>
<p>Este ebook es una invitación directa a dejar de huir y <strong>volver a sentir la vida</strong>.</p>', 'El mayor regalo que hemos recibido en esta vida no es el dinero, el éxito ni el reconocimiento.  
El regalo más grande es **sentir**.

Sentir no es algo que la ciencia pueda medir por completo, porque el verdadero sentir no nace del cuerpo ni de la mente: **nace del alma**.

Solo cuando empezamos a sentir nuestro corazón y nuestra alma, podemos encontrarnos con nosotros mismos… y recién entonces, conectar con otros.

Este ebook es una invitación directa a dejar de huir y **volver a sentir la vida**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('03eaccc0ff7d43ff8176129ed9d9d4ff', '79ff26b6764b4a288d8db50a8be6934b', '3acfa0b60260497e9a7fd54f5f65bb04', '5 - GUÍA TUS EMOCIONES GUIANDO TU MENTE', 15, '<p><em><strong>Guía tus emociones guiando tu mente</strong></em><br />Tus emociones no son el problema; el verdadero desafío es la historia que tu mente construye alrededor de ellas.<br />Cuando aprendes a dirigir tu pensamiento con claridad, tus emociones dejan de arrastrarte y comienzan a acompañarte.<br />No se trata de controlar lo que sientes, sino de <strong>ordenar la mente</strong> para que no convierta un instante en una tormenta.<br />Guías tus emociones cuando eliges interpretar la vida desde presencia, responsabilidad y verdad.<br />Ese es el punto donde recuperas poder: cuando tu mente deja de sabotearte y empieza a trabajar contigo.<br />La paz llega cuando aprendes a pensar distinto… y desde ahí, a sentir distinto.</p>', '_**Guía tus emociones guiando tu mente**_  
Tus emociones no son el problema; el verdadero desafío es la historia que tu mente construye alrededor de ellas.  
Cuando aprendes a dirigir tu pensamiento con claridad, tus emociones dejan de arrastrarte y comienzan a acompañarte.  
No se trata de controlar lo que sientes, sino de **ordenar la mente** para que no convierta un instante en una tormenta.  
Guías tus emociones cuando eliges interpretar la vida desde presencia, responsabilidad y verdad.  
Ese es el punto donde recuperas poder: cuando tu mente deja de sabotearte y empieza a trabajar contigo.  
La paz llega cuando aprendes a pensar distinto… y desde ahí, a sentir distinto.', 'YOUTUBE', 'https://youtu.be/jptFYPpgMgw?si=Bdmc2qJSb_cfy9rj', 'https://i.ytimg.com/vi/jptFYPpgMgw/maxresdefault.jpg', 762000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('8e096a8a6dac4393abc160c98012d977', '79ff26b6764b4a288d8db50a8be6934b', '3acfa0b60260497e9a7fd54f5f65bb04', 'EBOOK 5 - GUÍA TUS EMOCIONES GUÍA TU MENTE', 16, '<p>La frustración no es el problema.<br />El problema es <strong>quedarte atrapado en ella</strong>.</p>
<p>Las personas que nunca se frustran no están creciendo.<br />Pero cuando te frustras siempre por lo mismo, no estás creciendo más: estás repitiendo.</p>
<p>La raíz de ese estancamiento tiene un nombre claro: <strong>victimismo</strong>.<br />Y el victimismo no vive afuera. Vive en la <strong>mente</strong>.</p>
<p>Este ebook es una guía para aprender a <strong>guiar tu mente</strong>, dejar de ser controlado por ella y recuperar tu capacidad de sentir, crecer y vivir con plenitud.</p>', 'La frustración no es el problema.  
El problema es **quedarte atrapado en ella**.

Las personas que nunca se frustran no están creciendo.  
Pero cuando te frustras siempre por lo mismo, no estás creciendo más: estás repitiendo.

La raíz de ese estancamiento tiene un nombre claro: **victimismo**.  
Y el victimismo no vive afuera. Vive en la **mente**.

Este ebook es una guía para aprender a **guiar tu mente**, dejar de ser controlado por ella y recuperar tu capacidad de sentir, crecer y vivir con plenitud.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('85f652b11072493d904a4368fe2d175a', '1c4d035721394f9c8504883a25b88d3a', '3d60126bf7794db9a4a0cb21364260b5', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('5ee9e90738c742f7b36f15bd0d1feee0', '1c4d035721394f9c8504883a25b88d3a', '2e3387b37476436b85c897f3cebd9b17', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('3e6cbb7e4ea5488ab724f6211428461b', '1c4d035721394f9c8504883a25b88d3a', '2e3387b37476436b85c897f3cebd9b17', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('7cb8eb7e481e41b0a3eb041fe59ff473', '1c4d035721394f9c8504883a25b88d3a', '2e3387b37476436b85c897f3cebd9b17', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('784fef65d92b4cf696a8e10037a2bc9c', '1c4d035721394f9c8504883a25b88d3a', '1ec7be647cf2482b941d50a3c05bffd3', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('fa224b37376f414e93be2f554afadce1', '79ff26b6764b4a288d8db50a8be6934b', 'ed9a6bf2fb154130920e9cb5867c0e55', '6 - EL RENASER DE TU NIÑO(A) INTERIOR', 17, '<p><em><strong>El renaser de tu niño(a) interior</strong></em><br />Renace cuando por fin te permites volver a sentir sin miedo.<br />Ese niño(a) que fuiste no desapareció: sigue ahí, esperando que lo mires sin juicio, que lo abraces sin prisa y que le devuelvas la voz que un día tuvo que callar.<br />El renacer ocurre cuando reconoces su dolor, honras su inocencia y le recuerdas que ahora sí tiene a alguien que lo puede sostener: <strong>Tú</strong>.<br />Cada emoción que aparece hoy es una señal de esa parte de ti que pide ser tomada de la mano.<br />Y cuando lo haces, algo cambia para siempre: recuperas tu ternura, tu fuerza y tu capacidad de amar la vida con ojos nuevos.</p>
<p>Renaces… porque vuelves a casa.</p>', '_**El renaser de tu niño(a) interior**_  
Renace cuando por fin te permites volver a sentir sin miedo.  
Ese niño(a) que fuiste no desapareció: sigue ahí, esperando que lo mires sin juicio, que lo abraces sin prisa y que le devuelvas la voz que un día tuvo que callar.  
El renacer ocurre cuando reconoces su dolor, honras su inocencia y le recuerdas que ahora sí tiene a alguien que lo puede sostener: **Tú**.  
Cada emoción que aparece hoy es una señal de esa parte de ti que pide ser tomada de la mano.  
Y cuando lo haces, algo cambia para siempre: recuperas tu ternura, tu fuerza y tu capacidad de amar la vida con ojos nuevos.

Renaces… porque vuelves a casa.', 'YOUTUBE', 'https://youtu.be/Zi3chTIYJMs?si=cwJwnwjP0pJ05lAo', 'https://i.ytimg.com/vi/Zi3chTIYJMs/maxresdefault.jpg', 813000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('9e009c851bc54b49912fab0a558d3b33', '79ff26b6764b4a288d8db50a8be6934b', 'ed9a6bf2fb154130920e9cb5867c0e55', 'EBOOK 6 - EL RENASER DE TU NIÑO(A) INTERIOR', 18, '<p>Mientras sigas viéndote como víctima, tu mente seguirá controlando tu vida, sembrando miedos, bloqueando tu corazón y apagando tu capacidad de disfrutar.</p>
<p>Este ebook es una invitación profunda a volver a ti, a tu niño, a tu esencia, y a recuperar el amor propio desde la responsabilidad y la conciencia.</p>', 'Mientras sigas viéndote como víctima, tu mente seguirá controlando tu vida, sembrando miedos, bloqueando tu corazón y apagando tu capacidad de disfrutar.

Este ebook es una invitación profunda a volver a ti, a tu niño, a tu esencia, y a recuperar el amor propio desde la responsabilidad y la conciencia.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('ee4ba59eb8f7450f810312665fddd871', '79ff26b6764b4a288d8db50a8be6934b', '03caa50b3ceb4467bd1944ca1d5de837', '7 - EL VERDADERO AMOR PROPIO', 19, '<p><em><strong>El verdadero amor propio</strong></em><br />El amor propio no nace en los días fáciles, sino en esos momentos en los que eliges no abandonarte.<br />No es repetirte frases positivas, sino hacerte cargo de ti: de tus límites, tus heridas, tus decisiones y tu verdad.<br />El verdadero amor propio es valiente; te invita a mirarte sin filtros, a decirte la verdad incómoda y a sostenerte incluso cuando nadie más lo hace.<br />Empieza cuando dejas de mendigar amor afuera y comienzas a darte lo que siempre esperaste recibir.<br />Amarte es construir una vida que sea segura para ti, coherente contigo y digna de tu futuro.<br />Y cuando lo haces… renases.</p>', '_**El verdadero amor propio**_  
El amor propio no nace en los días fáciles, sino en esos momentos en los que eliges no abandonarte.  
No es repetirte frases positivas, sino hacerte cargo de ti: de tus límites, tus heridas, tus decisiones y tu verdad.  
El verdadero amor propio es valiente; te invita a mirarte sin filtros, a decirte la verdad incómoda y a sostenerte incluso cuando nadie más lo hace.  
Empieza cuando dejas de mendigar amor afuera y comienzas a darte lo que siempre esperaste recibir.  
Amarte es construir una vida que sea segura para ti, coherente contigo y digna de tu futuro.  
Y cuando lo haces… renases.', 'YOUTUBE', 'https://youtu.be/uahHuheNAWM?si=2mI6Dpmp0VfdNS9n', 'https://i.ytimg.com/vi/uahHuheNAWM/maxresdefault.jpg', 779000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('dce7541eef1e44b783dee811f7e7bb6a', '79ff26b6764b4a288d8db50a8be6934b', '03caa50b3ceb4467bd1944ca1d5de837', 'EBOOK 7 - EL VERDADERO AMOR PROPIO', 20, '<p>Hoy se habla mucho de amor propio, pero se vive poco.<br />La mayoría confunde el amor propio con sentirse bien todo el tiempo, con ser complaciente, con evitar el dolor o la incomodidad.</p>
<p>Pero el amor propio real no es cómodo.<br />Es <strong>profundo, sólido y transformador</strong>.</p>
<p>Este ebook es una invitación a volver a la esencia del amor propio:<br /><strong>el autoconocimiento</strong>, la honestidad contigo mismo y la capacidad de mirarte sin huir.</p>', 'Hoy se habla mucho de amor propio, pero se vive poco.  
La mayoría confunde el amor propio con sentirse bien todo el tiempo, con ser complaciente, con evitar el dolor o la incomodidad.

Pero el amor propio real no es cómodo.  
Es **profundo, sólido y transformador**.

Este ebook es una invitación a volver a la esencia del amor propio:  
**el autoconocimiento**, la honestidad contigo mismo y la capacidad de mirarte sin huir.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('5a53adcfb8054f42b9ab88711917a13c', '79ff26b6764b4a288d8db50a8be6934b', '03caa50b3ceb4467bd1944ca1d5de837', 'PODCAST PARA 10 KM', 21, '<p>Escucha 5 podcast durante todo el día. si puedes mas, será excelente. A continuación los enlaces. Para ello, debes de enfocarte en sentirlo, y estudiarlos. aqui encontraras lecciones que equivalen a 3 años de terapia</p>
<p><strong>Podcast 1:</strong> La clave de la libertad de tu ser - Principio de imperfeccion PODCAST</p>
<ul><li><p><a href="https://open.spotify.com/episode/7qo79D2TqRczNyGs70Awnf?si=ZKzuCYA3Qi6cp_RZTdTcMg&amp;t=1220" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/7qo79D2TqRczNyGs70Awnf?si=ZKzuCYA3Qi6cp_RZTdTcMg&amp;t=1220</a></p></li></ul>
<p><strong>Podcast 2:</strong> Consciencia detrás de los problemas</p>
<ul><li><p><a href="https://open.spotify.com/episode/0XOPc3EwepLM9jmdWM8Ynv?si=OQkD_SDTRxOBY0MpuyLncg" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/0XOPc3EwepLM9jmdWM8Ynv?si=OQkD_SDTRxOBY0MpuyLncg</a></p></li></ul>
<p><strong>Podcast 3:</strong> ¿Tus padres te condenaron?</p>
<ul><li><p><a href="https://open.spotify.com/episode/4UyQtUFkibtJDZV1v2FSXu?si=-8rs74h7Q0eiIfmIDd92tw" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/4UyQtUFkibtJDZV1v2FSXu?si=-8rs74h7Q0eiIfmIDd92tw</a></p></li></ul>
<p><strong>Podcast 4:</strong> Como superas los miedos y la ansiedad?</p>
<ul><li><p><a href="https://open.spotify.com/episode/7yYc9wbB85iOF5vxZB7dgH?si=1yn3uxBKQt2dpc8jAYLPlA" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/7yYc9wbB85iOF5vxZB7dgH?si=1yn3uxBKQt2dpc8jAYLPlA</a></p></li><li><p><a href="https://open.spotify.com/episode/5oE9rg1k7j9CwiiorvyUvG?si=nDLA6z4UTiWfwFXniYP9IA" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/5oE9rg1k7j9CwiiorvyUvG?si=nDLA6z4UTiWfwFXniYP9IA</a></p></li><li><p><a href="https://open.spotify.com/episode/6yYb4SxDGp4NQy00W3d5e7?si=V79KKJ5iRrez_nr0QC5Ihw" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/6yYb4SxDGp4NQy00W3d5e7?si=V79KKJ5iRrez_nr0QC5Ihw</a></p></li></ul>
<p><strong>Podcast 5:</strong> Olvidaste tu esencia femenina</p>
<ul><li><p><a href="https://open.spotify.com/episode/41Asl2vNqs0RnT9hY9dT3E?si=bGeBkMXVTlue9-2t5x7O9Q" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/41Asl2vNqs0RnT9hY9dT3E?si=bGeBkMXVTlue9-2t5x7O9Q</a></p></li></ul>
<p><strong>Podcast íntimos:</strong></p>
<ul><li><p><a href="https://open.spotify.com/show/2Y6aHhlDfNYuQcpeU3Dov0?si=BY6cQyDlRLqkK1KurxAHmw" target="_blank" rel="noopener noreferrer">https://open.spotify.com/show/2Y6aHhlDfNYuQcpeU3Dov0?si=BY6cQyDlRLqkK1KurxAHmw</a></p></li></ul>', 'Escucha 5 podcast durante todo el día. si puedes mas, será excelente. A continuación los enlaces. Para ello, debes de enfocarte en sentirlo, y estudiarlos. aqui encontraras lecciones que equivalen a 3 años de terapia

**Podcast 1:** La clave de la libertad de tu ser - Principio de imperfeccion PODCAST

- [https://open.spotify.com/episode/7qo79D2TqRczNyGs70Awnf?si=ZKzuCYA3Qi6cp_RZTdTcMg&t=1220](https://open.spotify.com/episode/7qo79D2TqRczNyGs70Awnf?si=ZKzuCYA3Qi6cp_RZTdTcMg&t=1220)

**Podcast 2:** Consciencia detrás de los problemas

- [https://open.spotify.com/episode/0XOPc3EwepLM9jmdWM8Ynv?si=OQkD_SDTRxOBY0MpuyLncg](https://open.spotify.com/episode/0XOPc3EwepLM9jmdWM8Ynv?si=OQkD_SDTRxOBY0MpuyLncg)

**Podcast 3:** ¿Tus padres te condenaron?

- [https://open.spotify.com/episode/4UyQtUFkibtJDZV1v2FSXu?si=-8rs74h7Q0eiIfmIDd92tw](https://open.spotify.com/episode/4UyQtUFkibtJDZV1v2FSXu?si=-8rs74h7Q0eiIfmIDd92tw)

**Podcast 4:** Como superas los miedos y la ansiedad?

- [https://open.spotify.com/episode/7yYc9wbB85iOF5vxZB7dgH?si=1yn3uxBKQt2dpc8jAYLPlA](https://open.spotify.com/episode/7yYc9wbB85iOF5vxZB7dgH?si=1yn3uxBKQt2dpc8jAYLPlA)
- [https://open.spotify.com/episode/5oE9rg1k7j9CwiiorvyUvG?si=nDLA6z4UTiWfwFXniYP9IA](https://open.spotify.com/episode/5oE9rg1k7j9CwiiorvyUvG?si=nDLA6z4UTiWfwFXniYP9IA)
- [https://open.spotify.com/episode/6yYb4SxDGp4NQy00W3d5e7?si=V79KKJ5iRrez_nr0QC5Ihw](https://open.spotify.com/episode/6yYb4SxDGp4NQy00W3d5e7?si=V79KKJ5iRrez_nr0QC5Ihw)

**Podcast 5:** Olvidaste tu esencia femenina

- [https://open.spotify.com/episode/41Asl2vNqs0RnT9hY9dT3E?si=bGeBkMXVTlue9-2t5x7O9Q](https://open.spotify.com/episode/41Asl2vNqs0RnT9hY9dT3E?si=bGeBkMXVTlue9-2t5x7O9Q)

**Podcast íntimos:**

- [https://open.spotify.com/show/2Y6aHhlDfNYuQcpeU3Dov0?si=BY6cQyDlRLqkK1KurxAHmw](https://open.spotify.com/show/2Y6aHhlDfNYuQcpeU3Dov0?si=BY6cQyDlRLqkK1KurxAHmw)', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('2cf8414bd25b42ac9720b67d08c2f57d', '909bfb750a1543dfaa682ab3bbe00928', '80802b2f844d45db82bde5509989991a', '01. HOJA DE RUTA', 0, '<p>Bienvenido(a)! </p>
<p>En el siguiente documento te contamos el material y las clases que tenemos en esta semana, de manera ordenada. </p>
<p>Disfruta de este viaje!</p>', 'Bienvenido(a)! 

En el siguiente documento te contamos el material y las clases que tenemos en esta semana, de manera ordenada. 

Disfruta de este viaje!', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('8ed23ff7ddee47408ae5683c67492aa2', '1c4d035721394f9c8504883a25b88d3a', '1ec7be647cf2482b941d50a3c05bffd3', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('4c8e0e41ae28448abd799d070cccf15b', '1c4d035721394f9c8504883a25b88d3a', '1ec7be647cf2482b941d50a3c05bffd3', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('bb5c0785d7ca48f2afa2e2f89ad1499f', '1c4d035721394f9c8504883a25b88d3a', '25f2edf5887b410ba6a915f53d6266bd', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('88a2ae22d1504c7cbc4453808baddeba', '1c4d035721394f9c8504883a25b88d3a', '25f2edf5887b410ba6a915f53d6266bd', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('6e289678f174415c945af9b4084526c1', '1c4d035721394f9c8504883a25b88d3a', '25f2edf5887b410ba6a915f53d6266bd', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('479ad42e813f4ba8a40f5499c64a6c0a', '1c4d035721394f9c8504883a25b88d3a', 'c35711dd9cd646a8a53f87445cd48146', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('5203bacab1854f61b0eaeafc6ce8758d', '909bfb750a1543dfaa682ab3bbe00928', '80802b2f844d45db82bde5509989991a', 'Clase 1: Como iniciar mi siguiente fase?', 1, '<p><br /></p>
<p>Si estás aquí, no es casualidad.<br />La primera etapa abrió conciencia. Esta segunda fase <strong>consolida identidad, dirección y acción consciente</strong>.</p>
<p>Aquí ya no observas desde afuera.<br />Aquí <strong>te haces responsable de lo que sabes</strong>, de lo que sientes y de lo que eliges sostener.</p>
<p>En esta fase:</p>
<ul><li><p>Se ordena lo interno para que lo externo responda.</p></li><li><p>Se eliminan patrones que ya no sostienen tu crecimiento.</p></li><li><p>Se instala una estructura emocional y mental más firme, clara y funcional.</p></li></ul>
<p>No es un espacio para acumular información.<br />Es un espacio para <strong>integrar, ejecutar y encarnar</strong>.</p>
<p>Avanza con presencia.<br />Participa con honestidad.<br />Y recuerda: en esta fase, <strong>tu compromiso define tu resultado</strong>.</p>
<p>Bienvenido(a) al siguiente nivel.<br />Bienvenido(a) a RENASER – Fase II.</p>', 'Si estás aquí, no es casualidad.  
La primera etapa abrió conciencia. Esta segunda fase **consolida identidad, dirección y acción consciente**.

Aquí ya no observas desde afuera.  
Aquí **te haces responsable de lo que sabes**, de lo que sientes y de lo que eliges sostener.

En esta fase:

- Se ordena lo interno para que lo externo responda.
- Se eliminan patrones que ya no sostienen tu crecimiento.
- Se instala una estructura emocional y mental más firme, clara y funcional.

No es un espacio para acumular información.  
Es un espacio para **integrar, ejecutar y encarnar**.

Avanza con presencia.  
Participa con honestidad.  
Y recuerda: en esta fase, **tu compromiso define tu resultado**.

Bienvenido(a) al siguiente nivel.  
Bienvenido(a) a RENASER – Fase II.', 'YOUTUBE', 'https://youtu.be/y-J3UIRHzJE', 'https://i.ytimg.com/vi/y-J3UIRHzJE/maxresdefault.jpg', 868000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('3d2fb66df8624c1bb0aa40a98394b03b', '909bfb750a1543dfaa682ab3bbe00928', 'c1348ef03f304d08a216cfb58d2b42e6', '01. GUIA FASE II (Imprimir)', 2, '<p>Esta guía marca <strong>la continuidad y profundización real de tu proceso RENASER</strong>.<br />Aquí ya no estás comenzando: estás <strong>sosteniendo presencia</strong> y enfrentando lo que antes evitabas mirar.</p>
<p>No es una guía para motivarte.<br />Es una estructura para <strong>mantenerte consciente cuando la incomodidad aparece</strong> y los viejos patrones intentan recuperar control.</p>
<p>En esta etapa, el trabajo se vuelve más sutil y más exigente:<br />ya no se trata solo de observar, sino de <strong>hacerte responsable de lo que descubres</strong>.</p>
<p>Durante estos días es normal que aparezcan:<br />pensamientos repetitivos, cansancio interno, dudas, resistencia o ganas de abandonar.<br />Nada de eso indica retroceso.<br /><strong>Indica que el proceso está funcionando.</strong></p>
<p>Esta guía te ayudará a:</p>
<ul><li><p>Sostener presencia sin volver al automático.</p></li><li><p>Reconocer cuándo tu mente intenta sabotear el avance.</p></li><li><p>Consolidar una nueva forma de responder, no desde la reacción, sino desde la claridad.</p></li><li><p>Fortalecer disciplina interna sin violencia ni autoexigencia.</p></li></ul>
<p>Aquí no se busca intensidad constante.<br />Se busca <strong>continuidad consciente</strong>.</p>
<p>Lee cada parte con calma.<br />Aplica lo indicado sin anticiparte.<br />No fuerces resultados.</p>
<p>Permite que el proceso haga su trabajo<br />mientras tú haces el tuyo: <strong>estar presente</strong>.</p>
<p>Esto no es un reto.<br />Es un compromiso contigo.</p>
<p>Aquí RENASER se vuelve real.</p>', 'Esta guía marca **la continuidad y profundización real de tu proceso RENASER**.  
Aquí ya no estás comenzando: estás **sosteniendo presencia** y enfrentando lo que antes evitabas mirar.

No es una guía para motivarte.  
Es una estructura para **mantenerte consciente cuando la incomodidad aparece** y los viejos patrones intentan recuperar control.

En esta etapa, el trabajo se vuelve más sutil y más exigente:  
ya no se trata solo de observar, sino de **hacerte responsable de lo que descubres**.

Durante estos días es normal que aparezcan:  
pensamientos repetitivos, cansancio interno, dudas, resistencia o ganas de abandonar.  
Nada de eso indica retroceso.  
**Indica que el proceso está funcionando.**

Esta guía te ayudará a:

- Sostener presencia sin volver al automático.
- Reconocer cuándo tu mente intenta sabotear el avance.
- Consolidar una nueva forma de responder, no desde la reacción, sino desde la claridad.
- Fortalecer disciplina interna sin violencia ni autoexigencia.

Aquí no se busca intensidad constante.  
Se busca **continuidad consciente**.

Lee cada parte con calma.  
Aplica lo indicado sin anticiparte.  
No fuerces resultados.

Permite que el proceso haga su trabajo  
mientras tú haces el tuyo: **estar presente**.

Esto no es un reto.  
Es un compromiso contigo.

Aquí RENASER se vuelve real.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('aba86de15cfb406b92545cf4c7d71b4a', '909bfb750a1543dfaa682ab3bbe00928', 'c1348ef03f304d08a216cfb58d2b42e6', '02. CHECKLIST FASE II (imprimir)', 3, '<p>Este checklist <strong>no es una lista para cumplir por obligación</strong>.<br />Es una herramienta para <strong>entrenar presencia</strong>, ordenar tu proceso y hacer visible <strong>cómo te estás relacionando contigo</strong> durante esta primera semana.</p>
<p>Aquí no se miden resultados externos.<br />No importa si “lograste” o no algo.<br />Lo que se observa es <strong>tu nivel real de conciencia y compromiso</strong>.</p>
<p>Usa este checklist <strong>a diario</strong>, como un ancla.<br />No para exigirte.<br />Sino para <strong>verte con honestidad</strong>.</p>
<p>Si algo no se cumple, no lo corrijas de inmediato.<br />No lo justifiques.<br /><strong>Obsérvalo.</strong></p>
<p>Este checklist te ayudará a:</p>
<ul><li><p>Mantener estructura sin caer en rigidez.</p></li><li><p>Detectar resistencia, evasión o autoexigencia encubierta.</p></li><li><p>Fortalecer disciplina consciente, no forzada.</p></li></ul>
<p>Marca cada punto con honestidad.<br />Si un día no cumpliste, <strong>no lo ocultes: regístralo</strong>.<br />Ahí está la información valiosa.</p>', 'Este checklist **no es una lista para cumplir por obligación**.  
Es una herramienta para **entrenar presencia**, ordenar tu proceso y hacer visible **cómo te estás relacionando contigo** durante esta primera semana.

Aquí no se miden resultados externos.  
No importa si “lograste” o no algo.  
Lo que se observa es **tu nivel real de conciencia y compromiso**.

Usa este checklist **a diario**, como un ancla.  
No para exigirte.  
Sino para **verte con honestidad**.

Si algo no se cumple, no lo corrijas de inmediato.  
No lo justifiques.  
**Obsérvalo.**

Este checklist te ayudará a:

- Mantener estructura sin caer en rigidez.
- Detectar resistencia, evasión o autoexigencia encubierta.
- Fortalecer disciplina consciente, no forzada.

Marca cada punto con honestidad.  
Si un día no cumpliste, **no lo ocultes: regístralo**.  
Ahí está la información valiosa.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('8a38c488cc4b48d9a6e4029a41051f96', '909bfb750a1543dfaa682ab3bbe00928', 'c1348ef03f304d08a216cfb58d2b42e6', '03.  INTOXICACIÓN CONSCIENTE Dia 8, 9 y 10', 4, '<figure><img src="909bfb750a1543dfaa682ab3bbe00928/assets/a68bfd5acf30-69345b4dbcf243c3971649fd1e61e20e3c6175df.png" alt="Guía Desde el día 8 hasta el día 34.png" loading="lazy" /></figure>
<p>Durante estos días no se busca disciplina ni control. Se busca <strong>verdad</strong>.<br />Al permitirte exceso, ruptura de rutinas y descarga emocional, la mente deja de sostener personajes y estrategias de supervivencia. Lo que aparece no es debilidad: es información pura.</p>
<p>Este ciclo no es comodidad, es descenso.<br />Aquí se revelan los patrones que normalmente se esconden bajo la productividad, la exigencia y el “todo está bien”.</p>
<p>No se corrige nada.<br />Se observa todo.</p>
<p>Porque antes de transformarte, necesitas ver con claridad <strong>qué estás sosteniendo</strong>.</p>', '![Guía Desde el día 8 hasta el día 34.png](909bfb750a1543dfaa682ab3bbe00928/assets/a68bfd5acf30-69345b4dbcf243c3971649fd1e61e20e3c6175df.png)

Durante estos días no se busca disciplina ni control. Se busca **verdad**.  
Al permitirte exceso, ruptura de rutinas y descarga emocional, la mente deja de sostener personajes y estrategias de supervivencia. Lo que aparece no es debilidad: es información pura.

Este ciclo no es comodidad, es descenso.  
Aquí se revelan los patrones que normalmente se esconden bajo la productividad, la exigencia y el “todo está bien”.

No se corrige nada.  
Se observa todo.

Porque antes de transformarte, necesitas ver con claridad **qué estás sosteniendo**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('8985743d436f4ecaa196785eaf1d0a29', '909bfb750a1543dfaa682ab3bbe00928', '4dc5346c2f974f458367da8e82e14b5f', '07. AUDIOTERAPIA 3.0 | SANA CON PAPÁ', 27, '<p>Esta audioterapia abre un espacio directo y profundo con una de las raíces más determinantes de tu estructura interna: la relación con la figura paterna. No se trata de juzgar, justificar ni reescribir la historia, sino de <strong>reconocer cómo ese vínculo sigue influyendo en tu forma de afirmarte, decidir y ocupar tu lugar</strong>.</p>
<p>Aquí se trabaja la huella del padre no desde la memoria, sino desde el cuerpo y la emoción. Al hacerlo, se libera la tensión que se manifiesta como exigencia excesiva, miedo a fallar o dificultad para sostener autoridad personal sin dureza.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para desactivar patrones de aprobación, rebeldía o autoexigencia ligados a la figura paterna.</p></li><li><p>Para fortalecer tu eje interno y tu capacidad de decisión.</p></li><li><p>Para reconciliarte con la autoridad sin miedo ni rigidez.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Sensación de firmeza y sostén interno.</p></li><li><p>Mayor claridad para poner límites y avanzar.</p></li><li><p>Una relación más madura contigo y con el mundo.</p></li></ul>
<p>Esta audioterapia no cambia a tu padre.<br />Cambia la forma en que su huella vive en ti.<br />Sanar con papá es recuperar tu autoridad interna.</p>', 'Esta audioterapia abre un espacio directo y profundo con una de las raíces más determinantes de tu estructura interna: la relación con la figura paterna. No se trata de juzgar, justificar ni reescribir la historia, sino de **reconocer cómo ese vínculo sigue influyendo en tu forma de afirmarte, decidir y ocupar tu lugar**.

Aquí se trabaja la huella del padre no desde la memoria, sino desde el cuerpo y la emoción. Al hacerlo, se libera la tensión que se manifiesta como exigencia excesiva, miedo a fallar o dificultad para sostener autoridad personal sin dureza.

**¿Para qué sirve?**

- Para desactivar patrones de aprobación, rebeldía o autoexigencia ligados a la figura paterna.
- Para fortalecer tu eje interno y tu capacidad de decisión.
- Para reconciliarte con la autoridad sin miedo ni rigidez.

**Qué activa en ti**

- Sensación de firmeza y sostén interno.
- Mayor claridad para poner límites y avanzar.
- Una relación más madura contigo y con el mundo.

Esta audioterapia no cambia a tu padre.  
Cambia la forma en que su huella vive en ti.  
Sanar con papá es recuperar tu autoridad interna.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('f5250b1d37ff42cdad567760999de6d6', '909bfb750a1543dfaa682ab3bbe00928', 'a6bbea0be4214dbf8d2876898cd10dd7', 'MASTERCLASS | EL ARTE DE SER TU TERAPEUTA', 5, '<p>Esta masterclass marca un punto de quiebre: dejar de depender de explicaciones externas y empezar a <strong>relacionarte contigo desde conciencia, estructura y presencia</strong>. No reemplaza procesos profundos ni promete soluciones mágicas; te enseña algo más valioso: <strong>cómo acompañarte sin sabotearte</strong>.</p>
<p>Aquí comprendes por qué muchas personas “saben mucho” pero siguen repitiendo lo mismo, y cuál es la diferencia entre analizarte y <strong>hacerte cargo de tu proceso interno</strong>. Ser tu propio terapeuta no es tratarte, es <strong>sostenerte con honestidad y criterio</strong>.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para dejar de buscar respuestas fuera cuando la claridad ya está disponible dentro.</p></li><li><p>Para interpretar tus emociones sin exagerarlas ni minimizarlas.</p></li><li><p>Para intervenir tus estados internos en tiempo real, sin huir ni castigarte.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Autonomía emocional.</p></li><li><p>Capacidad de autoobservación clara.</p></li><li><p>Una relación más adulta y funcional contigo mismo.</p></li></ul>', 'Esta masterclass marca un punto de quiebre: dejar de depender de explicaciones externas y empezar a **relacionarte contigo desde conciencia, estructura y presencia**. No reemplaza procesos profundos ni promete soluciones mágicas; te enseña algo más valioso: **cómo acompañarte sin sabotearte**.

Aquí comprendes por qué muchas personas “saben mucho” pero siguen repitiendo lo mismo, y cuál es la diferencia entre analizarte y **hacerte cargo de tu proceso interno**. Ser tu propio terapeuta no es tratarte, es **sostenerte con honestidad y criterio**.

**¿Para qué sirve?**

- Para dejar de buscar respuestas fuera cuando la claridad ya está disponible dentro.
- Para interpretar tus emociones sin exagerarlas ni minimizarlas.
- Para intervenir tus estados internos en tiempo real, sin huir ni castigarte.

**Qué activa en ti**

- Autonomía emocional.
- Capacidad de autoobservación clara.
- Una relación más adulta y funcional contigo mismo.', 'YOUTUBE', 'http://youtube.com/watch?v=2cHOL1KojPo&time_continue=9&source_ve_path=NzY3NTg&embeds_referring_euri=https%3A%2F%2Fwww.skool.com%2F&embeds_referring_origin=https%3A%2F%2Fwww.skool.com', 'https://i.ytimg.com/vi/2cHOL1KojPo/maxresdefault.jpg', 4724000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('f959157d43124105a95fd748e4476c88', '909bfb750a1543dfaa682ab3bbe00928', '185daefc01274f69993fe8d33cdd044f', '07. AUDIOTERAPIA 1.0 | CONEXIÓN CON TU ESENCIA', 6, '<p>Esta audioterapia es el primer umbral del proceso RENASER. No busca motivarte ni “arreglarte”, sino <strong>detener el ruido</strong>, bajar la exigencia y <strong>devolver tu atención a lo esencial</strong>: aquello que sigue intacto debajo de la prisa, el rol y la autoexigencia.</p>
<p>A través de una guía profunda y cuidadosamente diseñada, entras en un estado de presencia donde puedes <strong>escucharte sin juicio</strong>, reconocer cómo te has desconectado de ti y empezar a <strong>habitarte otra vez</strong>. Aquí no se fuerza el cambio: se crea el espacio interno para que ocurra.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para salir del piloto automático.</p></li><li><p>Para reconectar con tu centro interno y tu claridad natural.</p></li><li><p>Para iniciar el proceso desde la conciencia, no desde el esfuerzo.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Calma real (no evasión).</p></li><li><p>Sensación de arraigo y coherencia interna.</p></li><li><p>El primer recuerdo corporal de quién eres cuando no estás huyendo de ti.</p></li></ul>
<p>Esta audioterapia es la base.<br />Sin conexión con tu esencia, cualquier avance se vuelve frágil.<br />Aquí comienza el regreso.</p>', 'Esta audioterapia es el primer umbral del proceso RENASER. No busca motivarte ni “arreglarte”, sino **detener el ruido**, bajar la exigencia y **devolver tu atención a lo esencial**: aquello que sigue intacto debajo de la prisa, el rol y la autoexigencia.

A través de una guía profunda y cuidadosamente diseñada, entras en un estado de presencia donde puedes **escucharte sin juicio**, reconocer cómo te has desconectado de ti y empezar a **habitarte otra vez**. Aquí no se fuerza el cambio: se crea el espacio interno para que ocurra.

**¿Para qué sirve?**

- Para salir del piloto automático.
- Para reconectar con tu centro interno y tu claridad natural.
- Para iniciar el proceso desde la conciencia, no desde el esfuerzo.

**Qué activa en ti**

- Calma real (no evasión).
- Sensación de arraigo y coherencia interna.
- El primer recuerdo corporal de quién eres cuando no estás huyendo de ti.

Esta audioterapia es la base.  
Sin conexión con tu esencia, cualquier avance se vuelve frágil.  
Aquí comienza el regreso.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('c2eeb17e5ff14618a0d9f741c097ad19', '909bfb750a1543dfaa682ab3bbe00928', '27bf16d7772b4f52bcd3a8442aacfd9b', '04. DIA 8 - EL MAYOR REGALO DE TU ALMA', 7, '<p><strong>(Visualiza este video diariamente desde el día 8 al 10)</strong></p>
<p><strong>Nadie vendrá a salvarte (y esa es tu libertad).</strong><br /><br />¿Y si la persona que tanto esperas que te rescate es, en realidad, la que ves cada mañana en el espejo? Pasamos la existencia mendigando migajas de atención, prefiriendo una compañía vacía antes que enfrentar el sagrado silencio de nuestra propia presencia.</p>
<p>El conflicto estalla cuando comprendes que tu hambre de afuera es solo el reflejo de tu abandono interno. Tocamos la herida del rechazo para que dejes de ser un satélite de otros y te conviertas en tu propio centro. Mira este video completo; esto cambiará tu forma de amar y de habitar tu soledad.</p>', '**(Visualiza este video diariamente desde el día 8 al 10)**

**Nadie vendrá a salvarte (y esa es tu libertad).**  
  
¿Y si la persona que tanto esperas que te rescate es, en realidad, la que ves cada mañana en el espejo? Pasamos la existencia mendigando migajas de atención, prefiriendo una compañía vacía antes que enfrentar el sagrado silencio de nuestra propia presencia.

El conflicto estalla cuando comprendes que tu hambre de afuera es solo el reflejo de tu abandono interno. Tocamos la herida del rechazo para que dejes de ser un satélite de otros y te conviertas en tu propio centro. Mira este video completo; esto cambiará tu forma de amar y de habitar tu soledad.', 'YOUTUBE', 'https://youtu.be/yCAG9FpTu64', 'https://i.ytimg.com/vi/yCAG9FpTu64/maxresdefault.jpg', 1088000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('bd378418499d4e76977305f82808495b', '909bfb750a1543dfaa682ab3bbe00928', '32d5d646abc84dc894dc6103f5b2ec21', '05. DIA 9 - LAS DEUDAS QUE TE CONDENAN', 8, '<p><strong>(Visualiza este video diariamente desde el día 11 al 13)</strong></p>
<p><strong>El fin del sufrimiento: Deja de pelear contigo.</strong><br /><br />¿Es el dolor lo que te detiene o la historia que te repites sobre él cada mañana? Vivimos atrapados en un laberinto de espejos, identificándonos con el reflejo de una herida que ya no debería sangrar, pero que alimentamos con nuestra propia atención.</p>
<p>El conflicto nace cuando el observador se confunde con lo observado, perdiendo su esencia en el drama del pensamiento. Tocamos la herida del &quot;yo&quot; para que descubras que el silencio no es ausencia, sino la plenitud absoluta de tu ser. Mira este video completo; esto cambiará tu forma de ver tu mente y la paz que siempre estuvo ahí.</p>', '**(Visualiza este video diariamente desde el día 11 al 13)**

**El fin del sufrimiento: Deja de pelear contigo.**  
  
¿Es el dolor lo que te detiene o la historia que te repites sobre él cada mañana? Vivimos atrapados en un laberinto de espejos, identificándonos con el reflejo de una herida que ya no debería sangrar, pero que alimentamos con nuestra propia atención.

El conflicto nace cuando el observador se confunde con lo observado, perdiendo su esencia en el drama del pensamiento. Tocamos la herida del "yo" para que descubras que el silencio no es ausencia, sino la plenitud absoluta de tu ser. Mira este video completo; esto cambiará tu forma de ver tu mente y la paz que siempre estuvo ahí.', 'YOUTUBE', 'https://youtu.be/QOfAyzHuELo', 'https://i.ytimg.com/vi/QOfAyzHuELo/maxresdefault.jpg', 501000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('6170b64487cc4a4eb2b13745687b21a9', '909bfb750a1543dfaa682ab3bbe00928', '7f66267e578c425aa569808a797083be', '06. DIA 10 - RECONSTRUYE TU IDENTIDAD', 9, '<p><strong>(Visualiza este video diariamente desde el día 14 al 16)</strong></p>
<p><strong>Deja de correr: Lo que buscas está bajo tus pies.</strong><br /><br />¿Cuánto tiempo más vas a posponer tu vida en nombre de una meta que siempre se desplaza? Corremos tras un horizonte que prometía paz, pero solo encontramos un hambre que nunca se sacia y un cansancio que ya es parte del alma.</p>
<p>El conflicto es la herida de la eterna insatisfacción; ese abismo que intentas llenar con logros, objetos o personas, ignorando que el vacío es el espacio donde reside tu verdadera esencia. Debes ver este video completo; esto cambiará tu forma de desear y te devolverá el poder de habitar el ahora.</p>', '**(Visualiza este video diariamente desde el día 14 al 16)**

**Deja de correr: Lo que buscas está bajo tus pies.**  
  
¿Cuánto tiempo más vas a posponer tu vida en nombre de una meta que siempre se desplaza? Corremos tras un horizonte que prometía paz, pero solo encontramos un hambre que nunca se sacia y un cansancio que ya es parte del alma.

El conflicto es la herida de la eterna insatisfacción; ese abismo que intentas llenar con logros, objetos o personas, ignorando que el vacío es el espacio donde reside tu verdadera esencia. Debes ver este video completo; esto cambiará tu forma de desear y te devolverá el poder de habitar el ahora.', 'YOUTUBE', 'https://youtu.be/q9xwJzsBdDw', 'https://i.ytimg.com/vi/q9xwJzsBdDw/maxresdefault.jpg', 748000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('6a215a26e25d477abebf20d24169cee4', '1c4d035721394f9c8504883a25b88d3a', 'c35711dd9cd646a8a53f87445cd48146', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('aa1db8b9bf20427d86401650b287f4d0', '1c4d035721394f9c8504883a25b88d3a', 'c35711dd9cd646a8a53f87445cd48146', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('81d51f72fe1a48a48fbc10728996b13f', '909bfb750a1543dfaa682ab3bbe00928', '87599a55466346b78f481f1b4b32a8da', '04. EL EGO QUE TE LIMITA, VICTIMISMO', 10, '<p><strong>(Visualiza este video diariamente desde el día 17 al 19)</strong></p>
<p><strong>No eres quien crees: la verdad sobre tu origen.</strong><br /><br />¿Te has detenido a observar qué parte de ti permanece inmutable mientras todo a tu alrededor se desmorona? Pasamos la vida protegiendo un cuerpo y una historia, olvidando que somos el espacio infinito donde ambos suceden.</p>
<p>El conflicto central es la herida de la finitud: ese terror a la nada que nos obliga a aferrarnos a lo transitorio. Aquí exploramos el silencio que precede a tus palabras y la luz que brilla antes de tus pensamientos. Míralo completo; esto cambiará tu forma de ver la muerte, el tiempo y tu propia presencia en este universo.</p>', '**(Visualiza este video diariamente desde el día 17 al 19)**

**No eres quien crees: la verdad sobre tu origen.**  
  
¿Te has detenido a observar qué parte de ti permanece inmutable mientras todo a tu alrededor se desmorona? Pasamos la vida protegiendo un cuerpo y una historia, olvidando que somos el espacio infinito donde ambos suceden.

El conflicto central es la herida de la finitud: ese terror a la nada que nos obliga a aferrarnos a lo transitorio. Aquí exploramos el silencio que precede a tus palabras y la luz que brilla antes de tus pensamientos. Míralo completo; esto cambiará tu forma de ver la muerte, el tiempo y tu propia presencia en este universo.', 'YOUTUBE', 'https://youtu.be/YSA1_mCzyFA', 'https://i.ytimg.com/vi/YSA1_mCzyFA/maxresdefault.jpg', 372000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('c76e139ae792488db8a8cf8fa9c089a6', '909bfb750a1543dfaa682ab3bbe00928', '0922856f602f4fbfb32accc2166fd4aa', '05. EL PODER DE TUS INTENCIONES', 11, '<p><strong>(Visualiza este video diariamente desde el día 20 al 22)</strong></p>
<p><strong>La trampa de querer controlarlo todo en tu vida.</strong><br /><br />¿Qué pasaría si hoy dejaras de luchar contra la marea y permitieras que el abismo te alcanzara? Vivimos en una guerra agotadora, protegiendo tesoros de humo y persiguiendo sombras que llamamos &quot;éxito&quot;, mientras la vida real sucede en la rendición.</p>
<p>El conflicto es el pánico a la pérdida, esa herida que supura cada vez que el destino nos quita lo que creíamos poseer. Aquí descubrimos que solo aquel que no tiene nada que perder lo ha ganado todo. Mira este video completo; esto cambiará tu forma de ver tus fracasos y tu resistencia a la vida.</p>', '**(Visualiza este video diariamente desde el día 20 al 22)**

**La trampa de querer controlarlo todo en tu vida.**  
  
¿Qué pasaría si hoy dejaras de luchar contra la marea y permitieras que el abismo te alcanzara? Vivimos en una guerra agotadora, protegiendo tesoros de humo y persiguiendo sombras que llamamos "éxito", mientras la vida real sucede en la rendición.

El conflicto es el pánico a la pérdida, esa herida que supura cada vez que el destino nos quita lo que creíamos poseer. Aquí descubrimos que solo aquel que no tiene nada que perder lo ha ganado todo. Mira este video completo; esto cambiará tu forma de ver tus fracasos y tu resistencia a la vida.', 'YOUTUBE', 'https://youtu.be/T7rNU-eJl7Q', 'https://i.ytimg.com/vi/T7rNU-eJl7Q/maxresdefault.jpg', 499000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('e0efeae7338e48608ae825217a5f5530', '909bfb750a1543dfaa682ab3bbe00928', 'a737af3f93f345bf8879744d53edf9a7', '06. LIBERAR TU ALMA TE DA PLENITUD', 12, '<p><strong>(Visualiza este video diariamente desde el día 23 al 25)</strong></p>
<p><strong>La decepción es lo mejor que puede pasarte hoy.</strong><br /><br />¿Preferirías una mentira que te mantenga a salvo o una verdad que te rompa en mil pedazos? Sostenemos realidades ficticias por el pánico a descubrir que el suelo bajo nuestros pies nunca estuvo ahí.</p>
<p>El conflicto central es la agonía de la desilusión; esa herida necesaria que desmantela el teatro de tu vida para que la realidad finalmente respire. No es un proceso amable, es un incendio que consume lo falso para salvar lo eterno. Míralo completo; esto cambiará tu forma de abrazar el colapso y te enseñará a caminar entre las cenizas de tu antiguo yo.</p>', '**(Visualiza este video diariamente desde el día 23 al 25)**

**La decepción es lo mejor que puede pasarte hoy.**  
  
¿Preferirías una mentira que te mantenga a salvo o una verdad que te rompa en mil pedazos? Sostenemos realidades ficticias por el pánico a descubrir que el suelo bajo nuestros pies nunca estuvo ahí.

El conflicto central es la agonía de la desilusión; esa herida necesaria que desmantela el teatro de tu vida para que la realidad finalmente respire. No es un proceso amable, es un incendio que consume lo falso para salvar lo eterno. Míralo completo; esto cambiará tu forma de abrazar el colapso y te enseñará a caminar entre las cenizas de tu antiguo yo.', 'YOUTUBE', 'https://youtu.be/XgviJUKfcQQ', 'https://i.ytimg.com/vi/XgviJUKfcQQ/maxresdefault.jpg', 1041000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('6a1896bc3ac74de1be62962959e38b1c', '909bfb750a1543dfaa682ab3bbe00928', 'a737af3f93f345bf8879744d53edf9a7', 'Sesion exclusiva | Vulnerabilidad consciente', 13, '<p>Esta sesion es para expandir tu consciencia en relación a la pregunta... cuanto debo sentir? cual es la forma de sentir sin dejarme llevar por mi victimismo? </p>
<p><br /></p>', 'Esta sesion es para expandir tu consciencia en relación a la pregunta... cuanto debo sentir? cual es la forma de sentir sin dejarme llevar por mi victimismo?', 'YOUTUBE', 'https://youtu.be/dGHTPZEt9qU', 'https://i.ytimg.com/vi/dGHTPZEt9qU/maxresdefault.jpg', 630000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('dcb2ef1bef4141068b67dc5377f3fc77', '909bfb750a1543dfaa682ab3bbe00928', '635ec5fe41e946358a2fee41518167e4', '08. AUDIOTERAPIA 1.1 | SUPERA TU FRUSTRACIÓN', 14, '<p>Esta audioterapia actúa directamente sobre uno de los estados más silenciosos y desgastantes: la frustración acumulada. No la que explota, sino la que se guarda. La que nace cuando haces, intentas, sostienes… y aun así sientes que no avanzas como esperas.</p>
<p>Aquí no se trata de “pensar positivo” ni de resistir más. Esta guía te lleva a <strong>identificar el origen real de tu frustración</strong>, a escuchar lo que está intentando decirte y a <strong>desactivar el ciclo interno de exigencia–decepción–culpa</strong> que la mantiene viva.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para liberar la tensión interna que se genera cuando te fuerzas a rendir sin escucharte.</p></li><li><p>Para transformar la frustración en información clara, no en castigo personal.</p></li><li><p>Para recuperar dirección sin autoataque.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Descenso inmediato de la carga emocional.</p></li><li><p>Claridad sobre qué estás forzando y por qué.</p></li><li><p>Un cambio interno: de la lucha a la comprensión consciente.</p></li></ul>
<p>Esta audioterapia no elimina la frustración negándola.<br />La atraviesa, la ordena y la convierte en guía.<br />Cuando entiendes tu frustración, dejas de pelear contigo.</p>', 'Esta audioterapia actúa directamente sobre uno de los estados más silenciosos y desgastantes: la frustración acumulada. No la que explota, sino la que se guarda. La que nace cuando haces, intentas, sostienes… y aun así sientes que no avanzas como esperas.

Aquí no se trata de “pensar positivo” ni de resistir más. Esta guía te lleva a **identificar el origen real de tu frustración**, a escuchar lo que está intentando decirte y a **desactivar el ciclo interno de exigencia–decepción–culpa** que la mantiene viva.

**¿Para qué sirve?**

- Para liberar la tensión interna que se genera cuando te fuerzas a rendir sin escucharte.
- Para transformar la frustración en información clara, no en castigo personal.
- Para recuperar dirección sin autoataque.

**Qué activa en ti**

- Descenso inmediato de la carga emocional.
- Claridad sobre qué estás forzando y por qué.
- Un cambio interno: de la lucha a la comprensión consciente.

Esta audioterapia no elimina la frustración negándola.  
La atraviesa, la ordena y la convierte en guía.  
Cuando entiendes tu frustración, dejas de pelear contigo.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('f19ebe6a03494ac38b386ad5996d0d33', '909bfb750a1543dfaa682ab3bbe00928', '635ec5fe41e946358a2fee41518167e4', '09. AUDIOTERAPIA 1.2 | SIENTE Y RÍE', 15, '<p>Esta audioterapia abre un espacio poco habitual pero profundamente reparador: <strong>Permitirte sentir sin control y reír sin culpa</strong>. No como evasión, sino como señal de que el cuerpo y la emoción empiezan a aflojar después de mucho tiempo en tensión.</p>
<p>Aquí no se analiza ni se corrige nada. Se guía al sistema interno a <strong>bajar la guardia</strong>, a soltar la rigidez emocional y a recuperar la capacidad natural de sentir con ligereza. La risa aparece como consecuencia de la liberación, no como obligación.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para descargar emociones retenidas sin drama ni exigencia.</p></li><li><p>Para reconciliarte con el placer de estar presente.</p></li><li><p>Para recordar que sentir no siempre es pesado.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Relajación profunda del cuerpo y la mente.</p></li><li><p>Reconexión con la espontaneidad emocional.</p></li><li><p>Una sensación genuina de alivio, expansión y ligereza.</p></li></ul>
<p>Esta audioterapia no busca alegría forzada.<br />Permite que la emoción circule… y cuando circula, la risa surge sola.<br />Sentir y reír es una forma de volver a casa.</p>', 'Esta audioterapia abre un espacio poco habitual pero profundamente reparador: **Permitirte sentir sin control y reír sin culpa**. No como evasión, sino como señal de que el cuerpo y la emoción empiezan a aflojar después de mucho tiempo en tensión.

Aquí no se analiza ni se corrige nada. Se guía al sistema interno a **bajar la guardia**, a soltar la rigidez emocional y a recuperar la capacidad natural de sentir con ligereza. La risa aparece como consecuencia de la liberación, no como obligación.

**¿Para qué sirve?**

- Para descargar emociones retenidas sin drama ni exigencia.
- Para reconciliarte con el placer de estar presente.
- Para recordar que sentir no siempre es pesado.

**Qué activa en ti**

- Relajación profunda del cuerpo y la mente.
- Reconexión con la espontaneidad emocional.
- Una sensación genuina de alivio, expansión y ligereza.

Esta audioterapia no busca alegría forzada.  
Permite que la emoción circule… y cuando circula, la risa surge sola.  
Sentir y reír es una forma de volver a casa.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('a40d51e1f70a44c991adaddf42539fa2', '909bfb750a1543dfaa682ab3bbe00928', 'b2e5feeee6fe40c0bfa61b0c87f15eed', '01. HOJA DE RUTA', 17, '<p><strong>🧭 HOJA DE RUTA RENASER – SEMANA 2</strong><br />No basta con entender. Hay que habitar el proceso. Esta hoja de ruta es tu brújula emocional para la semana más simbólica del programa.</p>
<p>Aquí encontrarás <strong>la guía exacta para integrar la masterclass, el audio de la semana y los ejercicios del manual</strong>. Todo diseñado para que no solo avances… sino que <strong>renazcas con poder y claridad</strong>.</p>
<p>🔹 Escucha el audio “Supera tu frustración” cada mañana<br />🔹 Haz mínimo 2 ejercicios + 1 ritual del manual<br />🔹 Conecta con la tierra, tu cuerpo y tu historia sin juicio<br />🔹 Apóyate en frases activadoras que despiertan tu alma</p>
<p>📎 Aquí tienes el mapa. Lo demás… depende de ti.</p>', '**🧭 HOJA DE RUTA RENASER – SEMANA 2**  
No basta con entender. Hay que habitar el proceso. Esta hoja de ruta es tu brújula emocional para la semana más simbólica del programa.

Aquí encontrarás **la guía exacta para integrar la masterclass, el audio de la semana y los ejercicios del manual**. Todo diseñado para que no solo avances… sino que **renazcas con poder y claridad**.

🔹 Escucha el audio “Supera tu frustración” cada mañana  
🔹 Haz mínimo 2 ejercicios + 1 ritual del manual  
🔹 Conecta con la tierra, tu cuerpo y tu historia sin juicio  
🔹 Apóyate en frases activadoras que despiertan tu alma

📎 Aquí tienes el mapa. Lo demás… depende de ti.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('b5313ca10a324727a72f5eb4095e8972', '909bfb750a1543dfaa682ab3bbe00928', 'b2e5feeee6fe40c0bfa61b0c87f15eed', '02. MASTERCLASS 2 | LA ESENCIA DEL AMOR', 18, '<p>Esta masterclass desmonta una de las confusiones más profundas y normalizadas: creer que el amor es sacrificio, intensidad o necesidad. Aquí se revela el amor no como emoción pasajera, sino como <strong>estado interno de coherencia y presencia</strong>.</p>
<p>No se habla de amor romántico ni de fórmulas relacionales. Se explora el amor como la <strong>capacidad de estar sin poseer, de vincular sin perderte y de elegir sin depender</strong>. Cuando esa esencia no está clara, el vínculo se convierte en intercambio, miedo o control.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para distinguir amor de apego.</p></li><li><p>Para entender por qué repites ciertos patrones relacionales.</p></li><li><p>Para reconstruir tu forma de amar desde integridad, no desde carencia.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Claridad emocional en tus vínculos.</p></li><li><p>Mayor respeto por tus límites y los del otro.</p></li><li><p>Una sensación de calma al relacionarte, sin urgencia ni autoabandono.</p></li></ul>', 'Esta masterclass desmonta una de las confusiones más profundas y normalizadas: creer que el amor es sacrificio, intensidad o necesidad. Aquí se revela el amor no como emoción pasajera, sino como **estado interno de coherencia y presencia**.

No se habla de amor romántico ni de fórmulas relacionales. Se explora el amor como la **capacidad de estar sin poseer, de vincular sin perderte y de elegir sin depender**. Cuando esa esencia no está clara, el vínculo se convierte en intercambio, miedo o control.

**¿Para qué sirve?**

- Para distinguir amor de apego.
- Para entender por qué repites ciertos patrones relacionales.
- Para reconstruir tu forma de amar desde integridad, no desde carencia.

**Qué activa en ti**

- Claridad emocional en tus vínculos.
- Mayor respeto por tus límites y los del otro.
- Una sensación de calma al relacionarte, sin urgencia ni autoabandono.', 'YOUTUBE', 'https://www.youtube.com/watch?v=qEIN-uQKHGc', 'https://i.ytimg.com/vi/qEIN-uQKHGc/maxresdefault.jpg', 5644000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('deea012c3a544c25a04b60228d5f91bd', '909bfb750a1543dfaa682ab3bbe00928', 'b2e5feeee6fe40c0bfa61b0c87f15eed', '03. CICLO II - INTOXICACIÓN CONSCIENTE', 19, '<figure><img src="909bfb750a1543dfaa682ab3bbe00928/assets/c36f558d09b2-e871ab7b85394eb9ae2f82b677df2ca5aa8b80ea.png" alt="Guía Desde el día 8 hasta el día 34 (1).png" loading="lazy" /></figure>
<p>Durante estos días no se busca disciplina ni control. Se busca <strong>verdad</strong>.<br />Al permitirte exceso, ruptura de rutinas y descarga emocional, la mente deja de sostener personajes y estrategias de supervivencia. Lo que aparece no es debilidad: es información pura.</p>
<p>Este ciclo no es comodidad, es descenso.<br />Aquí se revelan los patrones que normalmente se esconden bajo la productividad, la exigencia y el “todo está bien”.</p>
<p>No se corrige nada.<br />Se observa todo.</p>
<p>Porque antes de transformarte, necesitas ver con claridad <strong>qué estás sosteniendo</strong>.</p>', '![Guía Desde el día 8 hasta el día 34 (1).png](909bfb750a1543dfaa682ab3bbe00928/assets/c36f558d09b2-e871ab7b85394eb9ae2f82b677df2ca5aa8b80ea.png)

Durante estos días no se busca disciplina ni control. Se busca **verdad**.  
Al permitirte exceso, ruptura de rutinas y descarga emocional, la mente deja de sostener personajes y estrategias de supervivencia. Lo que aparece no es debilidad: es información pura.

Este ciclo no es comodidad, es descenso.  
Aquí se revelan los patrones que normalmente se esconden bajo la productividad, la exigencia y el “todo está bien”.

No se corrige nada.  
Se observa todo.

Porque antes de transformarte, necesitas ver con claridad **qué estás sosteniendo**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('4a46387494b14026b3b7cf6a961610e7', '909bfb750a1543dfaa682ab3bbe00928', 'b2e5feeee6fe40c0bfa61b0c87f15eed', '07. AUDIOTERAPIA 2.0 | SANA TU LINAJE FEMENINO', 20, '<p>Esta audioterapia guía un encuentro profundo con la historia que vive en tu cuerpo. No se trata de revisar el pasado ni de buscar culpables, sino de <strong>reconocer cómo las cargas, silencios y patrones del linaje femenino siguen operando en tu forma de sentir, vincularte y sostenerte</strong>.</p>
<p>Aquí se abre un espacio de escucha interna donde puedes identificar qué no te pertenece, qué fue heredado y qué ya no necesitas cargar. Al hacerlo, el sistema emocional comienza a soltar lealtades inconscientes que condicionan tu energía, tu autoestima y tu manera de habitar el mundo.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para liberar mandatos femeninos inconscientes (sacrificio, silencio, sobrecarga).</p></li><li><p>Para cortar repeticiones emocionales que no elegiste.</p></li><li><p>Para reconciliarte con la fuerza femenina desde calma y dignidad.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Sensación de alivio y descanso interno.</p></li><li><p>Mayor conexión con tu intuición y tu valor propio.</p></li><li><p>Un cambio sutil pero profundo en la forma en que te relacionas contigo y con otros.</p></li></ul>', 'Esta audioterapia guía un encuentro profundo con la historia que vive en tu cuerpo. No se trata de revisar el pasado ni de buscar culpables, sino de **reconocer cómo las cargas, silencios y patrones del linaje femenino siguen operando en tu forma de sentir, vincularte y sostenerte**.

Aquí se abre un espacio de escucha interna donde puedes identificar qué no te pertenece, qué fue heredado y qué ya no necesitas cargar. Al hacerlo, el sistema emocional comienza a soltar lealtades inconscientes que condicionan tu energía, tu autoestima y tu manera de habitar el mundo.

**¿Para qué sirve?**

- Para liberar mandatos femeninos inconscientes (sacrificio, silencio, sobrecarga).
- Para cortar repeticiones emocionales que no elegiste.
- Para reconciliarte con la fuerza femenina desde calma y dignidad.

**Qué activa en ti**

- Sensación de alivio y descanso interno.
- Mayor conexión con tu intuición y tu valor propio.
- Un cambio sutil pero profundo en la forma en que te relacionas contigo y con otros.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('5d7f337c2f9b458280a63ed6533386e9', '909bfb750a1543dfaa682ab3bbe00928', '4dc5346c2f974f458367da8e82e14b5f', '01. HOJA DE RUTA', 21, '<p><strong>🧭 HOJA DE RUTA RENASER – SEMANA 3: Vuelve a ti, incluso con miedo</strong><br />¿Y si esta semana no hicieras más que sostenerte?<br />Esta hoja de ruta es tu brújula emocional para integrar el manual, mirar tu miedo sin ceder, y <strong>volver a ti cuando todo afuera se tambalea</strong>.</p>
<p>Incluye:<br />🔹 Actividades esenciales de introspección<br />🔹 Ritual con objeto simbólico de seguridad<br />🔹 Frases de anclaje diario para tu sistema nervioso<br />🔹 Evaluación de avance emocional</p>
<p>📎 Imprímela, úsala con intención, vuelve a ti.</p>', '**🧭 HOJA DE RUTA RENASER – SEMANA 3: Vuelve a ti, incluso con miedo**  
¿Y si esta semana no hicieras más que sostenerte?  
Esta hoja de ruta es tu brújula emocional para integrar el manual, mirar tu miedo sin ceder, y **volver a ti cuando todo afuera se tambalea**.

Incluye:  
🔹 Actividades esenciales de introspección  
🔹 Ritual con objeto simbólico de seguridad  
🔹 Frases de anclaje diario para tu sistema nervioso  
🔹 Evaluación de avance emocional

📎 Imprímela, úsala con intención, vuelve a ti.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('960e9963d4644650857d34524d3c280b', '1c4d035721394f9c8504883a25b88d3a', 'fa31e15096394e52abbd85a0fab1b1b4', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('6a42e0c8d1bd405b88fe0ace96698f3d', '909bfb750a1543dfaa682ab3bbe00928', '4dc5346c2f974f458367da8e82e14b5f', '02. MASTERCLASS 3 | SEGURIDAD INQUEBRANTABLE', 22, '<p>Esta masterclass redefine la seguridad personal. No como control, dureza o exceso de confianza, sino como <strong>estabilidad interna que no depende de la aprobación, del resultado ni del entorno</strong>.</p>
<p>Aquí se revela por qué muchas personas se muestran fuertes pero viven internamente en alerta, y cómo construir una seguridad que <strong>no se quiebra cuando algo falla, alguien se va o el plan cambia</strong>. La seguridad inquebrantable no se impone: se estructura desde adentro.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para dejar de reaccionar desde el miedo al error o al rechazo.</p></li><li><p>Para sostener decisiones sin necesidad de validación constante.</p></li><li><p>Para actuar con firmeza sin rigidez emocional.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Confianza serena y consistente.</p></li><li><p>Mayor claridad al tomar decisiones.</p></li><li><p>Capacidad de avanzar sin traicionarte.</p></li></ul>
<p>Esta masterclass no te promete invulnerabilidad.<br />Te enseña a <strong>mantenerte estable incluso cuando tiembla</strong>.<br />Eso es seguridad inquebrantable.</p>', 'Esta masterclass redefine la seguridad personal. No como control, dureza o exceso de confianza, sino como **estabilidad interna que no depende de la aprobación, del resultado ni del entorno**.

Aquí se revela por qué muchas personas se muestran fuertes pero viven internamente en alerta, y cómo construir una seguridad que **no se quiebra cuando algo falla, alguien se va o el plan cambia**. La seguridad inquebrantable no se impone: se estructura desde adentro.

**¿Para qué sirve?**

- Para dejar de reaccionar desde el miedo al error o al rechazo.
- Para sostener decisiones sin necesidad de validación constante.
- Para actuar con firmeza sin rigidez emocional.

**Qué activa en ti**

- Confianza serena y consistente.
- Mayor claridad al tomar decisiones.
- Capacidad de avanzar sin traicionarte.

Esta masterclass no te promete invulnerabilidad.  
Te enseña a **mantenerte estable incluso cuando tiembla**.  
Eso es seguridad inquebrantable.', 'YOUTUBE', 'https://www.youtube.com/watch?v=saOx0oxpZgQ&source_ve_path=NzY3NTg&embeds_referring_euri=https%3A%2F%2Fwww.skool.com%2F&embeds_referring_origin=https%3A%2F%2Fwww.skool.com', 'https://i.ytimg.com/vi/saOx0oxpZgQ/maxresdefault.jpg', 5908000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('93316bf1fe344b219fa937dd98a28388', '909bfb750a1543dfaa682ab3bbe00928', '4dc5346c2f974f458367da8e82e14b5f', '03. CICLO III - INTOXICACIÓN CONSCIENTE', 23, '<figure><img src="909bfb750a1543dfaa682ab3bbe00928/assets/015e8c61c7f4-15c59114c48d43979174d4458669d55c0b72884c.png" alt="Guía Desde el día 8 hasta el día 34 (2).png" loading="lazy" /></figure>
<p>Durante estos días no se busca disciplina ni control. Se busca <strong>verdad</strong>.<br />Al permitirte exceso, ruptura de rutinas y descarga emocional, la mente deja de sostener personajes y estrategias de supervivencia. Lo que aparece no es debilidad: es información pura.</p>
<p>Este ciclo no es comodidad, es descenso.<br />Aquí se revelan los patrones que normalmente se esconden bajo la productividad, la exigencia y el “todo está bien”.</p>
<p>No se corrige nada.<br />Se observa todo.</p>
<p>Porque antes de transformarte, necesitas ver con claridad <strong>qué estás sosteniendo</strong>.</p>', '![Guía Desde el día 8 hasta el día 34 (2).png](909bfb750a1543dfaa682ab3bbe00928/assets/015e8c61c7f4-15c59114c48d43979174d4458669d55c0b72884c.png)

Durante estos días no se busca disciplina ni control. Se busca **verdad**.  
Al permitirte exceso, ruptura de rutinas y descarga emocional, la mente deja de sostener personajes y estrategias de supervivencia. Lo que aparece no es debilidad: es información pura.

Este ciclo no es comodidad, es descenso.  
Aquí se revelan los patrones que normalmente se esconden bajo la productividad, la exigencia y el “todo está bien”.

No se corrige nada.  
Se observa todo.

Porque antes de transformarte, necesitas ver con claridad **qué estás sosteniendo**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('a445ed75e7294b37ba1577d37e7496e4', '909bfb750a1543dfaa682ab3bbe00928', '4dc5346c2f974f458367da8e82e14b5f', '04. PORQUE TE DIVIDES, ERES UNA TOTALIDAD', 24, '<p><strong>(Visualiza este video diariamente desde el día 26 al 28)</strong></p>
<p><strong>Deja de buscarte: nunca te has perdido realmente.</strong><br /><br />¿Cuánto tiempo más vas a seguir persiguiendo una versión &quot;iluminada&quot; de ti mismo que nunca llega? La búsqueda constante es la trampa perfecta; un laberinto donde el ego se disfraza de espiritualidad para evitar ser descubierto.</p>
<p>El conflicto radica en la herida de la insuficiencia, ese susurro que te dice que aún te falta algo para estar completo. Aquí revelamos que la sanación no es una meta, sino el cese de toda guerra interna. Mira este video completo; esto cambiará tu forma de ver tu proceso y te permitirá, por fin, descansar en lo que ya eres.</p>', '**(Visualiza este video diariamente desde el día 26 al 28)**

**Deja de buscarte: nunca te has perdido realmente.**  
  
¿Cuánto tiempo más vas a seguir persiguiendo una versión "iluminada" de ti mismo que nunca llega? La búsqueda constante es la trampa perfecta; un laberinto donde el ego se disfraza de espiritualidad para evitar ser descubierto.

El conflicto radica en la herida de la insuficiencia, ese susurro que te dice que aún te falta algo para estar completo. Aquí revelamos que la sanación no es una meta, sino el cese de toda guerra interna. Mira este video completo; esto cambiará tu forma de ver tu proceso y te permitirá, por fin, descansar en lo que ya eres.', 'YOUTUBE', 'https://youtu.be/FFdcrz7-CDo', 'https://i.ytimg.com/vi/FFdcrz7-CDo/maxresdefault.jpg', 579000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('31ca2d874db94085b1e53ca356ce4d1e', '909bfb750a1543dfaa682ab3bbe00928', '4dc5346c2f974f458367da8e82e14b5f', '05. CONVIERTETE EN UN ARTISTA DE DIOS', 25, '<p><strong>(Visualiza este video diariamente desde el día 29 al 31)</strong></p>
<p><strong>¿Te amas lo suficiente como para estar a solas contigo?</strong><br /><br />¿Es soledad lo que sientes, o es el terror de encontrarte con el desconocido que vive en tu espejo? Pasamos la vida huyendo del silencio, llenando vacíos con presencias que solo aumentan nuestra orfandad espiritual.</p>
<p>El conflicto estalla cuando el mundo se retira y quedas tú, despojado de títulos y aplausos. Tocamos la herida del abandono para que comprendas que solo en la soledad más cruda se forja la integridad del ser. Debes ver este video completo; esto cambiará tu forma de habitar tus espacios de vacío y silencio.</p>', '**(Visualiza este video diariamente desde el día 29 al 31)**

**¿Te amas lo suficiente como para estar a solas contigo?**  
  
¿Es soledad lo que sientes, o es el terror de encontrarte con el desconocido que vive en tu espejo? Pasamos la vida huyendo del silencio, llenando vacíos con presencias que solo aumentan nuestra orfandad espiritual.

El conflicto estalla cuando el mundo se retira y quedas tú, despojado de títulos y aplausos. Tocamos la herida del abandono para que comprendas que solo en la soledad más cruda se forja la integridad del ser. Debes ver este video completo; esto cambiará tu forma de habitar tus espacios de vacío y silencio.', 'YOUTUBE', 'https://youtu.be/T9cZbun-W-Y', 'https://i.ytimg.com/vi/T9cZbun-W-Y/maxresdefault.jpg', 1090000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('7d3b42d077bb456ab9d049a5bebbd55c', '909bfb750a1543dfaa682ab3bbe00928', '4dc5346c2f974f458367da8e82e14b5f', '06. NOS CALLARON LOS QUE MÁS NOS AMARON', 26, '<p><strong>(Visualiza este video diariamente desde el día 32 al 34)</strong></p>
<p><strong>La belleza oculta que solo aparece en tu oscuridad.</strong><br /><br />¿Por qué huyes de la tormenta si es ella quien viene a limpiar tus raíces? Pasamos la vida persiguiendo una luz artificial, negando que el brillo más puro solo se gesta en las entrañas de la oscuridad que tanto evitas.</p>
<p>El conflicto no es el dolor, sino la resistencia a sentirlo. Tocamos la herida de la fragmentación para enseñarte a abrazar tus pedazos rotos como parte de un paisaje sagrado. Mira este video completo; esto cambiará tu forma de ver tus sombras y la paz que nace de la aceptación total.</p>', '**(Visualiza este video diariamente desde el día 32 al 34)**

**La belleza oculta que solo aparece en tu oscuridad.**  
  
¿Por qué huyes de la tormenta si es ella quien viene a limpiar tus raíces? Pasamos la vida persiguiendo una luz artificial, negando que el brillo más puro solo se gesta en las entrañas de la oscuridad que tanto evitas.

El conflicto no es el dolor, sino la resistencia a sentirlo. Tocamos la herida de la fragmentación para enseñarte a abrazar tus pedazos rotos como parte de un paisaje sagrado. Mira este video completo; esto cambiará tu forma de ver tus sombras y la paz que nace de la aceptación total.', 'YOUTUBE', 'https://youtu.be/V2SdrC7TuQo', 'https://i.ytimg.com/vi/V2SdrC7TuQo/maxresdefault.jpg', 425000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('9e1c6e659b8e40e08d6c048a0ce73491', '909bfb750a1543dfaa682ab3bbe00928', '7a4e99c8e51e421e9bf8eb360d7d8e8c', '01. TIERRA AGUA Y FUEGO: RITUAL PARA VOLVER A TI', 28, '<p>Si ya registraste tu vida por 7 días, ahora viene el punto donde la mayoría se sabotea: seguir cargando basura. RENASER no es acumular técnicas. Es cortar.</p>
<p>En este paso vas a hacer algo radical: tomar tu lista y eliminar el 50%. Pensamientos, emociones y conductas que te drenan, que te distraen, que te roban tiempo. Porque tu problema no es falta de tiempo… es exceso de ruido. Y cuando quitas el ruido, aparece algo que casi nunca sientes: espacio.</p>
<p>Luego viene la recodificación: entender que tus “reacciones” no son destino, son códigos inconscientes. Piensas algo, sientes algo, haces algo… y repites el mismo ciclo. Pero aquí tú vuelves a ser creador: reestructuras el recuerdo, cambias la emoción, y eliges una nueva conducta. No sufres por lo que pasó. Sufres por el guion que sigues actuando.</p>
<p>Y para alimentar tu prana —tu energía vital— activas las tres respiraciones: tierra (sentir sin huir), agua (soltar sin juzgar), y fuego (creer en ti con cuerpo de guerrero).</p>
<p>Primero sueltas. Luego te empoderas.</p>
<p>Este video no es teoría. Es práctica.</p>
<p>Hazlo y mírate cambiar.</p>
<p>RENASER</p>
<p>Corta el ruido. Reescribe el código. Respira y renace.</p>', 'Si ya registraste tu vida por 7 días, ahora viene el punto donde la mayoría se sabotea: seguir cargando basura. RENASER no es acumular técnicas. Es cortar.

En este paso vas a hacer algo radical: tomar tu lista y eliminar el 50%. Pensamientos, emociones y conductas que te drenan, que te distraen, que te roban tiempo. Porque tu problema no es falta de tiempo… es exceso de ruido. Y cuando quitas el ruido, aparece algo que casi nunca sientes: espacio.

Luego viene la recodificación: entender que tus “reacciones” no son destino, son códigos inconscientes. Piensas algo, sientes algo, haces algo… y repites el mismo ciclo. Pero aquí tú vuelves a ser creador: reestructuras el recuerdo, cambias la emoción, y eliges una nueva conducta. No sufres por lo que pasó. Sufres por el guion que sigues actuando.

Y para alimentar tu prana —tu energía vital— activas las tres respiraciones: tierra (sentir sin huir), agua (soltar sin juzgar), y fuego (creer en ti con cuerpo de guerrero).

Primero sueltas. Luego te empoderas.

Este video no es teoría. Es práctica.

Hazlo y mírate cambiar.

RENASER

Corta el ruido. Reescribe el código. Respira y renace.', 'YOUTUBE', 'https://www.youtube.com/watch?v=WJBVncaQKQE&index=9', 'https://i.ytimg.com/vi/WJBVncaQKQE/maxresdefault.jpg', 1306000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('e23f3daf3c464492a1838923a2f7a9e7', '909bfb750a1543dfaa682ab3bbe00928', '7a4e99c8e51e421e9bf8eb360d7d8e8c', '02. MANUAL DE LOS THOTEM RENASER', 29, '<p>Un Tótem RENASER es más que un cuadro, es un puente energético y simbólico entre tu conciencia actual y tu conciencia superior. Representa una fuerza arquetípica que ya existe dentro de ti y que se activa cuando decides mirarte con honestidad. El tótem no te da poder. Te recuerda el poder que olvidaste. </p>', 'Un Tótem RENASER es más que un cuadro, es un puente energético y simbólico entre tu conciencia actual y tu conciencia superior. Representa una fuerza arquetípica que ya existe dentro de ti y que se activa cuando decides mirarte con honestidad. El tótem no te da poder. Te recuerda el poder que olvidaste.', 'YOUTUBE', 'https://youtu.be/L7R_wopgt24', 'https://i.ytimg.com/vi/L7R_wopgt24/maxresdefault.jpg', 3437000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('2263dc2c400a471a8791a411e2822c35', '909bfb750a1543dfaa682ab3bbe00928', '7a4e99c8e51e421e9bf8eb360d7d8e8c', '03. MAESTROS QUE TE ACOMPAÑAN EN EL VIAJE RENASER', 30, '<p>¿Y si nunca estuviste desconectado… sino distraído?</p>
<p>¿Y si el poder que buscas afuera siempre estuvo esperando dentro de ti?</p>
<p>Desde pequeños nos enseñaron a adorar, a pedir, a depender. Pero nunca a honrar. Nunca a activar lo que habita en nosotros. Este video no habla de religiones, dogmas ni creencias heredadas. Habla de algo más incómodo: tu responsabilidad energética.</p>
<p>Dentro de ti viven fuerzas dormidas. Arquetipos. Guardianes. Tótems que representan partes tuyas que fueron silenciadas por miedo, soberbia o ignorancia. No porque no existan, sino porque dejaste de escucharlos. El agua, el aire, los animales, la naturaleza… todo sigue ahí. Tú fuiste quien se desconectó.</p>
<p>Aquí se revela por qué ciertos elementos llegan a tu vida, por qué algunos animales te acompañan, por qué ciertas miradas te incomodan y otras te sostienen. No es casualidad. Es información que no sabes leer.</p>
<p>Este video no es cómodo. Es un recordatorio. Un llamado a activar, honrar y asumir que la luz y la oscuridad conviven en ti. Y que solo cuando las integras, despiertas.</p>
<p>Míralo completo. No para creer.</p>
<p>Para recordar.</p>
<p>RENASER</p>
<p>Abraza tu luz. Abraza tu sombra. Renace.</p>', '¿Y si nunca estuviste desconectado… sino distraído?

¿Y si el poder que buscas afuera siempre estuvo esperando dentro de ti?

Desde pequeños nos enseñaron a adorar, a pedir, a depender. Pero nunca a honrar. Nunca a activar lo que habita en nosotros. Este video no habla de religiones, dogmas ni creencias heredadas. Habla de algo más incómodo: tu responsabilidad energética.

Dentro de ti viven fuerzas dormidas. Arquetipos. Guardianes. Tótems que representan partes tuyas que fueron silenciadas por miedo, soberbia o ignorancia. No porque no existan, sino porque dejaste de escucharlos. El agua, el aire, los animales, la naturaleza… todo sigue ahí. Tú fuiste quien se desconectó.

Aquí se revela por qué ciertos elementos llegan a tu vida, por qué algunos animales te acompañan, por qué ciertas miradas te incomodan y otras te sostienen. No es casualidad. Es información que no sabes leer.

Este video no es cómodo. Es un recordatorio. Un llamado a activar, honrar y asumir que la luz y la oscuridad conviven en ti. Y que solo cuando las integras, despiertas.

Míralo completo. No para creer.

Para recordar.

RENASER

Abraza tu luz. Abraza tu sombra. Renace.', 'YOUTUBE', 'https://www.youtube.com/watch?v=QZLeEZKTlk0&index=6', 'https://i.ytimg.com/vi/QZLeEZKTlk0/maxresdefault.jpg', 669000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('a6f618c77736401d86a6a7b03bf7d549', '909bfb750a1543dfaa682ab3bbe00928', '7a4e99c8e51e421e9bf8eb360d7d8e8c', '04. ESENCIA RENASER: RESPIRA Y VUELVE A TU PODER', 31, '<p>Hay días en los que no estás triste. Estás apagado.</p>
<p>Y ese apagón no siempre viene de ti… viene de lo que permitiste cargar.</p>
<p>La Esencia RENASER no se usa como perfume. Se honra. Porque no es un olor: es un puente. Un acto de presencia. Una forma de volver a respirar como alguien vivo. Cuando no te sientes vivo con lo que haces, con lo que sientes, con lo que sueñas… algo externo te está influenciando. Y no siempre lo notas. Solo sientes que te faltas a ti.</p>
<p>En este video aprendes cómo activar la esencia desde lo más importante: tu intención. No sirve “a ver qué pasa”. Aquí eliges: fortaleza, amor, limpieza, liberación, claridad. Y si no estás dispuesto a soltar lo contrario, no funciona. Así de simple.</p>
<p>Luego viene el ritual: vela, oración, gratitud y la respiración guiada. Inhalar con fuerza, sostener, exhalar con potencia. Detenerte, sentir, viajar hacia adentro. No para escapar… sino para volver. Porque cuando tu respiración despierta, tu energía se ordena. Cuando sueltas, tu cuerpo se abre. Y cuando tu presencia regresa, la vida vuelve a mostrarse.</p>
<p>Hazlo tres veces al día. No para “sentirte bien”.</p>
<p>Para renacer.</p>
<p>RENASER</p>
<p>Intención. Respiración. Presencia. Limpieza. Renacimiento.</p>', 'Hay días en los que no estás triste. Estás apagado.

Y ese apagón no siempre viene de ti… viene de lo que permitiste cargar.

La Esencia RENASER no se usa como perfume. Se honra. Porque no es un olor: es un puente. Un acto de presencia. Una forma de volver a respirar como alguien vivo. Cuando no te sientes vivo con lo que haces, con lo que sientes, con lo que sueñas… algo externo te está influenciando. Y no siempre lo notas. Solo sientes que te faltas a ti.

En este video aprendes cómo activar la esencia desde lo más importante: tu intención. No sirve “a ver qué pasa”. Aquí eliges: fortaleza, amor, limpieza, liberación, claridad. Y si no estás dispuesto a soltar lo contrario, no funciona. Así de simple.

Luego viene el ritual: vela, oración, gratitud y la respiración guiada. Inhalar con fuerza, sostener, exhalar con potencia. Detenerte, sentir, viajar hacia adentro. No para escapar… sino para volver. Porque cuando tu respiración despierta, tu energía se ordena. Cuando sueltas, tu cuerpo se abre. Y cuando tu presencia regresa, la vida vuelve a mostrarse.

Hazlo tres veces al día. No para “sentirte bien”.

Para renacer.

RENASER

Intención. Respiración. Presencia. Limpieza. Renacimiento.', 'YOUTUBE', 'https://www.youtube.com/watch?v=hYL-ODt-GL0&index=10', 'https://i.ytimg.com/vi/hYL-ODt-GL0/maxresdefault.jpg', 1446000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('9b73e3c1a60843eea4b36e9f50a05fdb', '909bfb750a1543dfaa682ab3bbe00928', 'f8a1a953c8b9499d91735ad2486af94e', '01. CÓDIGO RENASER: 90 DÍAS PARA RENASER DE VERDAD', 32, '<p>Si tienes acceso a este video, no es casualidad: Es porque ya dejaste de vivir “a medias”. Pero hay una pregunta que lo define todo: ¿estás listo para decirte la verdad, aunque te duela? El Código RENASER no es un libro bonito. Es un sistema. Un mapa de 90 días para que dejes de “entender” tu vida y empieces a transformarla. Porque la mayoría no está estancada por falta de inteligencia, ni por heridas, ni por mala suerte. Está estancada por una sola razón: Vive en mentiras pequeñas todos los días.</p>
<p>Aquí vas a aprender el primer pilar: la verdad. Lo que niegas te somete. Lo que escondes te gobierna. Y mientras sigas maquillando tu sombra, seguirás preso. Luego viene lo que casi nadie hace: planificar tu vida por escrito. No para “organizarte”, sino para diagnosticarte. Porque una cosa es lo que crees que haces… y otra lo que tu cuaderno va a revelar. Identificarás tus distractores, aplicarás la ley del 3/97, y vas a elegir qué destruir para renacer. </p>
<p>Porque RENASER es simple: morir para crear. RENASER Verdad. Escritura. Destrucción. Renacimiento.</p>', 'Si tienes acceso a este video, no es casualidad: Es porque ya dejaste de vivir “a medias”. Pero hay una pregunta que lo define todo: ¿estás listo para decirte la verdad, aunque te duela? El Código RENASER no es un libro bonito. Es un sistema. Un mapa de 90 días para que dejes de “entender” tu vida y empieces a transformarla. Porque la mayoría no está estancada por falta de inteligencia, ni por heridas, ni por mala suerte. Está estancada por una sola razón: Vive en mentiras pequeñas todos los días.

Aquí vas a aprender el primer pilar: la verdad. Lo que niegas te somete. Lo que escondes te gobierna. Y mientras sigas maquillando tu sombra, seguirás preso. Luego viene lo que casi nadie hace: planificar tu vida por escrito. No para “organizarte”, sino para diagnosticarte. Porque una cosa es lo que crees que haces… y otra lo que tu cuaderno va a revelar. Identificarás tus distractores, aplicarás la ley del 3/97, y vas a elegir qué destruir para renacer. 

Porque RENASER es simple: morir para crear. RENASER Verdad. Escritura. Destrucción. Renacimiento.', 'YOUTUBE', 'https://www.youtube.com/watch?v=lO5RtqkF4Dg&index=8', 'https://i.ytimg.com/vi/lO5RtqkF4Dg/maxresdefault.jpg', 1669000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('ad03b18cde2648188e2b03109b8a76df', '1c4d035721394f9c8504883a25b88d3a', 'fa31e15096394e52abbd85a0fab1b1b4', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('81a5dc98b24b4c3aa4a86f761f8c7af0', '1c4d035721394f9c8504883a25b88d3a', 'fa31e15096394e52abbd85a0fab1b1b4', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('7442b937f9b64c7e9a0551a0424ee220', '01ac727ed506477883d5e015a0b792c1', '2598bc7f4a1e4596a58b1b94440856a2', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('0d58c33a25e2431784088ff3bfed87f7', '01ac727ed506477883d5e015a0b792c1', '2598bc7f4a1e4596a58b1b94440856a2', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('ac093a58840743e9b8dd719cd75154c6', '01ac727ed506477883d5e015a0b792c1', '2598bc7f4a1e4596a58b1b94440856a2', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('24bb53173bb84903b7456a31aba97d48', '01ac727ed506477883d5e015a0b792c1', '57bfc0f516024e75a06221fa1a87071c', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('ba1e4004780e4690856f362d1a4ba9d2', 'a8c5e8c2b1b24636966f2930e8d7c218', NULL, '01. BIENVENIDO A TU FASE 3', 0, '<p>¿Y si nunca has sido creador, sino solo una víctima con discursos bonitos? </p>
<p>Este proceso no empieza con manifestar, empieza con disciplina, orden y voluntad. Aquí se revela la diferencia brutal entre elegir conscientemente y vivir reaccionando como esclavo de impulsos, miedos y excusas. Crear tu realidad no es motivación: es responsabilidad interna, descanso consciente y palabra con intención. </p>
<p>Si quieres dejar de sobrevivir y empezar a crear, este video va a incomodarte… y eso es exactamente lo que necesitas. </p>
<p>RENASER: no reaccionas, eliges. No repites, creas.</p>', '¿Y si nunca has sido creador, sino solo una víctima con discursos bonitos? 

Este proceso no empieza con manifestar, empieza con disciplina, orden y voluntad. Aquí se revela la diferencia brutal entre elegir conscientemente y vivir reaccionando como esclavo de impulsos, miedos y excusas. Crear tu realidad no es motivación: es responsabilidad interna, descanso consciente y palabra con intención. 

Si quieres dejar de sobrevivir y empezar a crear, este video va a incomodarte… y eso es exactamente lo que necesitas. 

RENASER: no reaccionas, eliges. No repites, creas.', 'YOUTUBE', 'https://www.youtube.com/watch?v=GU0DC0lyv4Y', 'https://i.ytimg.com/vi/GU0DC0lyv4Y/maxresdefault.jpg', 1211000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('559d7e6f36bc4257bdcab4cedbf43c1f', 'a8c5e8c2b1b24636966f2930e8d7c218', '75fd11b801b849cbb8d982d8f69a41c5', '01. GUÍA FASE 3', 1, '<p>Esta guía marca el inicio de tu siguiente nivel en RENASER.</p>
<p>Aquí ya no estás rompiendo patrones.<br />Aquí estás eligiendo vivir con conciencia.</p>
<p>Del día 35 al 64 entrenas al Maestro Interno:<br />disciplina con gozo, orden con amor, acción con presencia.</p>
<p>Ya no haces nada por miedo ni por obligación.<br />Lo haces porque eliges tu vida.</p>
<p>Cada práctica —ayuno, descanso, silencio, respiración, movimiento—<br />no es tarea.<br />Es dignidad.</p>
<p>Aquí no te corriges.<br />Te gobiernas.</p>
<p>Aquí no termina RENASER.<br />Aquí comienza tu soberanía.</p>', 'Esta guía marca el inicio de tu siguiente nivel en RENASER.

Aquí ya no estás rompiendo patrones.  
Aquí estás eligiendo vivir con conciencia.

Del día 35 al 64 entrenas al Maestro Interno:  
disciplina con gozo, orden con amor, acción con presencia.

Ya no haces nada por miedo ni por obligación.  
Lo haces porque eliges tu vida.

Cada práctica —ayuno, descanso, silencio, respiración, movimiento—  
no es tarea.  
Es dignidad.

Aquí no te corriges.  
Te gobiernas.

Aquí no termina RENASER.  
Aquí comienza tu soberanía.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('f8a03e7ac71e4296b28c4750f9a84a5f', 'a8c5e8c2b1b24636966f2930e8d7c218', '75fd11b801b849cbb8d982d8f69a41c5', '02. CHECKLIST FASE 3', 2, '<p>Este checklist no es una lista mecánica.<br />Es un espejo diario.</p>
<p>En esta etapa del proceso ya no se trata de demostrar disciplina,<br />sino de sostener identidad.</p>
<p>Aquí cada acción —presencia, silencio, movimiento, orden, elección consciente—<br />refleja cómo te estás gobernando internamente.</p>
<p>No mide productividad.<br />Mide coherencia.</p>
<p>Este checklist te ayuda a:</p>
<p>• Consolidar dominio sin rigidez.<br />• Detectar cuándo actúas desde amor y cuándo desde presión.<br />• Sostener el orden como parte de quién eres, no como esfuerzo temporal.</p>
<p>Úsalo cada día como un punto de verificación interna.<br />Si algo no se cumple, no te castigues.<br />Obsérvalo y reajusta.</p>
<p>En esta fase ya no estás aprendiendo a empezar.<br />Estás aprendiendo a sostener.</p>
<p>No se trata de hacerlo perfecto.<br />Se trata de vivir alineado.</p>
<p>El proceso se consolida cuando tu práctica se vuelve identidad.</p>', 'Este checklist no es una lista mecánica.  
Es un espejo diario.

En esta etapa del proceso ya no se trata de demostrar disciplina,  
sino de sostener identidad.

Aquí cada acción —presencia, silencio, movimiento, orden, elección consciente—  
refleja cómo te estás gobernando internamente.

No mide productividad.  
Mide coherencia.

Este checklist te ayuda a:

• Consolidar dominio sin rigidez.  
• Detectar cuándo actúas desde amor y cuándo desde presión.  
• Sostener el orden como parte de quién eres, no como esfuerzo temporal.

Úsalo cada día como un punto de verificación interna.  
Si algo no se cumple, no te castigues.  
Obsérvalo y reajusta.

En esta fase ya no estás aprendiendo a empezar.  
Estás aprendiendo a sostener.

No se trata de hacerlo perfecto.  
Se trata de vivir alineado.

El proceso se consolida cuando tu práctica se vuelve identidad.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('d8642c38fafe4f13b2f1987555e475f4', 'a8c5e8c2b1b24636966f2930e8d7c218', '0c0602a40bd54c32b7e44d47ca7043c0', '01. HOJA DE RUTA', 3, '<p><strong>🧭 HOJA DE RUTA RENASER – SEMANA 4: El arte de elegirte incluso en tu caos</strong><br />¿Te has mirado con ternura últimamente?<br />Esta hoja de ruta es tu guía emocional para aplicar el manual, integrar la masterclass y practicar el amor propio real: <strong>ese que no necesita perfección, solo verdad</strong>.</p>
<p>Incluye:<br />🔹 Actividades semanales esenciales<br />🔹 Ritual simbólico frente al espejo<br />🔹 Frases activadoras para comenzar el día<br />🔹 Una mini evaluación para medir tu compromiso emocional</p>
<p>📎 Imprímela, márcala, úsala. Es tu brújula esta semana.</p>', '**🧭 HOJA DE RUTA RENASER – SEMANA 4: El arte de elegirte incluso en tu caos**  
¿Te has mirado con ternura últimamente?  
Esta hoja de ruta es tu guía emocional para aplicar el manual, integrar la masterclass y practicar el amor propio real: **ese que no necesita perfección, solo verdad**.

Incluye:  
🔹 Actividades semanales esenciales  
🔹 Ritual simbólico frente al espejo  
🔹 Frases activadoras para comenzar el día  
🔹 Una mini evaluación para medir tu compromiso emocional

📎 Imprímela, márcala, úsala. Es tu brújula esta semana.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('263ba568ca2f4760be981652912297f3', 'a8c5e8c2b1b24636966f2930e8d7c218', '0c0602a40bd54c32b7e44d47ca7043c0', '02. MASTERCLASS 4 | AMOR PROPIO', 4, '<p>Esta masterclass estará enfocada en desmontar la idea distorsionada del amor propio. Aquí no se trabaja desde frases motivacionales ni desde la exigencia de “sentirte bien”, sino desde el autoconocimiento real.</p>
<p>A lo largo de la sesión se aborda por qué sentir es parte de vivir, cómo muchas veces te abandonas para agradar o no incomodar, y de qué manera el amor propio inicia cuando dejas de rechazarte en tus errores, tu cansancio y tu imperfección.</p>
<p>Se profundiza en la integración de tu luz y tu sombra, entendiendo que el verdadero amor propio no divide, no exige perfección y no se basa en la validación externa.</p>
<p>Esta masterclass te invita a mirarte con honestidad y a empezar a sostenerte desde dentro.</p>', 'Esta masterclass estará enfocada en desmontar la idea distorsionada del amor propio. Aquí no se trabaja desde frases motivacionales ni desde la exigencia de “sentirte bien”, sino desde el autoconocimiento real.

A lo largo de la sesión se aborda por qué sentir es parte de vivir, cómo muchas veces te abandonas para agradar o no incomodar, y de qué manera el amor propio inicia cuando dejas de rechazarte en tus errores, tu cansancio y tu imperfección.

Se profundiza en la integración de tu luz y tu sombra, entendiendo que el verdadero amor propio no divide, no exige perfección y no se basa en la validación externa.

Esta masterclass te invita a mirarte con honestidad y a empezar a sostenerte desde dentro.', 'YOUTUBE', 'https://youtu.be/ON4_Vw-DMfM?si=Cts_OCIE-tnhx8xS', 'https://i.ytimg.com/vi/ON4_Vw-DMfM/maxresdefault.jpg', 3906000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('face7f8cdd2d4bb3933cbbf67fb37b0b', '01ac727ed506477883d5e015a0b792c1', '57bfc0f516024e75a06221fa1a87071c', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('152e606cd9534616a5632a549be8696b', '01ac727ed506477883d5e015a0b792c1', '57bfc0f516024e75a06221fa1a87071c', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('5834860508414472a536006d58e524b8', 'a8c5e8c2b1b24636966f2930e8d7c218', '0c0602a40bd54c32b7e44d47ca7043c0', '03. EL AMOR REAL DEL RENASER', 5, '<p><strong>(Visualiza este video diariamente desde el día 35 al 37)</strong></p>
<p><strong>La trampa mental que te impide ser feliz hoy.</strong><br /><br />¿Te has preguntado quién es el que escucha cuando hablas contigo mismo en la oscuridad? Vivimos esclavizados por una voz que no calla, una narrativa de miedo que nos aleja del único momento real que existe.</p>
<p>El conflicto no está en tus circunstancias, sino en la herida de creer que eres esa corriente incesante de pensamientos. Aquí desnudamos la mente para revelar el espacio sagrado que hay detrás del ruido. Míralo completo; esto cambiará tu forma de ver tu propia conciencia y la paz que te pertenece.</p>', '**(Visualiza este video diariamente desde el día 35 al 37)**

**La trampa mental que te impide ser feliz hoy.**  
  
¿Te has preguntado quién es el que escucha cuando hablas contigo mismo en la oscuridad? Vivimos esclavizados por una voz que no calla, una narrativa de miedo que nos aleja del único momento real que existe.

El conflicto no está en tus circunstancias, sino en la herida de creer que eres esa corriente incesante de pensamientos. Aquí desnudamos la mente para revelar el espacio sagrado que hay detrás del ruido. Míralo completo; esto cambiará tu forma de ver tu propia conciencia y la paz que te pertenece.', 'YOUTUBE', 'https://youtu.be/QJMR6gna7LI', 'https://i.ytimg.com/vi/QJMR6gna7LI/maxresdefault.jpg', 543000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('cddff5635af341e09606a31fe8339f35', 'a8c5e8c2b1b24636966f2930e8d7c218', '0c0602a40bd54c32b7e44d47ca7043c0', '04. CREA ABUNDANCIA DESDE TU INTEGRIDAD', 6, '<p><strong>(Visualiza este video diariamente desde el día 38 al 40)</strong></p>
<p><strong>La muerte de tu ego es tu único camino.</strong><br /><br />¿Qué queda de ti cuando el ruido del mundo finalmente se apaga y el silencio te devora? Sostener una máscara que ya no encaja es la forma más lenta de morir en vida.</p>
<p>Nos aterra el vacío, pero es en ese abismo donde la herida deja de sangrar para convertirse en portal. El conflicto central es tu resistencia a dejar morir a quien fuiste para permitir que nazca quien realmente eres. Debes ver este video completo; esto cambiará tu forma de ver tus desiertos personales y tu soledad.</p>', '**(Visualiza este video diariamente desde el día 38 al 40)**

**La muerte de tu ego es tu único camino.**  
  
¿Qué queda de ti cuando el ruido del mundo finalmente se apaga y el silencio te devora? Sostener una máscara que ya no encaja es la forma más lenta de morir en vida.

Nos aterra el vacío, pero es en ese abismo donde la herida deja de sangrar para convertirse en portal. El conflicto central es tu resistencia a dejar morir a quien fuiste para permitir que nazca quien realmente eres. Debes ver este video completo; esto cambiará tu forma de ver tus desiertos personales y tu soledad.', 'YOUTUBE', 'https://youtu.be/dhvfpZ2dUMc', 'https://i.ytimg.com/vi/dhvfpZ2dUMc/maxresdefault.jpg', 836000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('bccf545a36e547b7852268b6fbfe3f2c', 'a8c5e8c2b1b24636966f2930e8d7c218', '0c0602a40bd54c32b7e44d47ca7043c0', '05. AUDIOTERAPIA 4 | LA MAGIA DE LA IMPERFECCIÓN', 7, '<p>Esta audioterapia te guía hacia un punto poco explorado: el lugar donde dejas de exigirte completarte para poder estar en paz. No se trata de conformarte ni de justificar errores, sino de <strong>soltar la lucha constante por ser “mejor” para merecer descanso, amor o validación</strong>.</p>
<p>Aquí se trabaja la rigidez interna que nace del perfeccionismo, del miedo a fallar y de la autoobservación crítica permanente. Al permitirte habitar la imperfección sin ataque, se libera una energía creativa y vital que había quedado atrapada en el control.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para disminuir la autoexigencia que desgasta y paraliza.</p></li><li><p>Para reconciliarte con tus errores sin perder dirección.</p></li><li><p>Para recuperar espontaneidad y disfrute sin culpa.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Relajación profunda del sistema interno.</p></li><li><p>Mayor autenticidad en tu forma de expresarte.</p></li><li><p>Una sensación de libertad al dejar de sostener una imagen.</p></li></ul>
<p>Esta audioterapia no celebra el error.<br />Celebra el momento en que <strong>dejas de pelear contigo por ser humano</strong>.<br />Ahí comienza la magia de la imperfección.</p>', 'Esta audioterapia te guía hacia un punto poco explorado: el lugar donde dejas de exigirte completarte para poder estar en paz. No se trata de conformarte ni de justificar errores, sino de **soltar la lucha constante por ser “mejor” para merecer descanso, amor o validación**.

Aquí se trabaja la rigidez interna que nace del perfeccionismo, del miedo a fallar y de la autoobservación crítica permanente. Al permitirte habitar la imperfección sin ataque, se libera una energía creativa y vital que había quedado atrapada en el control.

**¿Para qué sirve?**

- Para disminuir la autoexigencia que desgasta y paraliza.
- Para reconciliarte con tus errores sin perder dirección.
- Para recuperar espontaneidad y disfrute sin culpa.

**Qué activa en ti**

- Relajación profunda del sistema interno.
- Mayor autenticidad en tu forma de expresarte.
- Una sensación de libertad al dejar de sostener una imagen.

Esta audioterapia no celebra el error.  
Celebra el momento en que **dejas de pelear contigo por ser humano**.  
Ahí comienza la magia de la imperfección.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('a142b67f0e3d4577b0031cb0bfc61d84', 'a8c5e8c2b1b24636966f2930e8d7c218', '98c87b84bce74686a6e7d95c95940a31', '01. MASTERCLASS 5 | CUERPO - MENTE', 8, '<p><strong>(Visualiza este video diariamente desde el día 41 al 42)</strong></p>
<p>Esta masterclass abre una verdad que casi nadie quiere mirar: tu cuerpo no “falla”… responde. Aquí vas a entender por qué una dolencia no es solo física, sino una consecuencia directa de tu forma de administrar tu vida: pensamientos, emociones, estrés, relaciones y hábitos.</p>
<p>Trabajamos 4 pilares prácticos que cambian tu biología desde hoy: respiración consciente (oxígeno), alimentación sin culpa, relaciones que te nutren o te drenan, y movimiento como medicina. No es teoría: es aplicación real, con ejercicios que generan un “antes y después” en minutos.</p>
<p>Si tu cuerpo te está hablando… esta masterclass te enseña a escucharlo sin miedo y a tomar control con consciencia.</p>', '**(Visualiza este video diariamente desde el día 41 al 42)**

Esta masterclass abre una verdad que casi nadie quiere mirar: tu cuerpo no “falla”… responde. Aquí vas a entender por qué una dolencia no es solo física, sino una consecuencia directa de tu forma de administrar tu vida: pensamientos, emociones, estrés, relaciones y hábitos.

Trabajamos 4 pilares prácticos que cambian tu biología desde hoy: respiración consciente (oxígeno), alimentación sin culpa, relaciones que te nutren o te drenan, y movimiento como medicina. No es teoría: es aplicación real, con ejercicios que generan un “antes y después” en minutos.

Si tu cuerpo te está hablando… esta masterclass te enseña a escucharlo sin miedo y a tomar control con consciencia.', 'YOUTUBE', 'https://youtu.be/Fz90HS0Rnnw?si=GLUSsBH50Bc6ZCAE', 'https://i.ytimg.com/vi/Fz90HS0Rnnw/maxresdefault.jpg', 6433000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('3f25c6c0d9cf47939685e0e76599ea7f', 'a8c5e8c2b1b24636966f2930e8d7c218', '98c87b84bce74686a6e7d95c95940a31', '02. SANA TU ANSIEDAD, CONFRONTA TUS MIEDOS', 9, '<p><strong>(Visualiza este video diariamente desde el día 43 al 45)</strong></p>
<p><strong>Tu dolor es el mapa hacia tu tesoro oculto.</strong></p>
<p>¿Cuánto veneno has tragado intentando parecer alguien que siempre tiene el control? La verdad es que tu fortaleza actual es solo una armadura que hoy te impide respirar.</p>
<p>Caminamos sobre las brasas de viejas traiciones, ignorando que el fuego no viene a destruirnos, sino a purificar el metal de nuestra alma. Este conflicto interno es el llamado a desmantelar la mentira de tu falsa paz para tocar la herida que te hará libre. Míralo completo; esto cambiará tu forma de ver cada cicatriz que llevas en el alma.</p>', '**(Visualiza este video diariamente desde el día 43 al 45)**

**Tu dolor es el mapa hacia tu tesoro oculto.**

¿Cuánto veneno has tragado intentando parecer alguien que siempre tiene el control? La verdad es que tu fortaleza actual es solo una armadura que hoy te impide respirar.

Caminamos sobre las brasas de viejas traiciones, ignorando que el fuego no viene a destruirnos, sino a purificar el metal de nuestra alma. Este conflicto interno es el llamado a desmantelar la mentira de tu falsa paz para tocar la herida que te hará libre. Míralo completo; esto cambiará tu forma de ver cada cicatriz que llevas en el alma.', 'YOUTUBE', 'https://youtu.be/R4jQ-o3d8WE', 'https://i.ytimg.com/vi/R4jQ-o3d8WE/maxresdefault.jpg', 1020000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('05314d97797f4aaaa684ccb229a65d03', '01ac727ed506477883d5e015a0b792c1', 'a3169e0831c545db981c2a4959bf711e', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('d65b132b0eb5475091f2e22d73597cca', 'a8c5e8c2b1b24636966f2930e8d7c218', '98c87b84bce74686a6e7d95c95940a31', '03. SANA LA DEPRESIÓN', 10, '<p><strong>(Visualiza este video diariamente desde el día 46 al 48)</strong></p>
<p><strong>¿Y si todo lo que crees ser es solo una máscara impuesta por el miedo al juicio ajeno?</strong><br /><br />Vivimos mendigando una aprobación que nos despoja de nuestra esencia, aceptando una libertad a medias que sabe a cautiverio.</p>
<p>El conflicto surge cuando el alma ya no encaja en los moldes estrechos de la lógica. Tocamos la herida de la despersonalización para que dejes de ser una sombra y reclames tu luz. Mira este video hasta el final; esto cambiará tu forma de ver tu propia realidad interna.</p>', '**(Visualiza este video diariamente desde el día 46 al 48)**

**¿Y si todo lo que crees ser es solo una máscara impuesta por el miedo al juicio ajeno?**  
  
Vivimos mendigando una aprobación que nos despoja de nuestra esencia, aceptando una libertad a medias que sabe a cautiverio.

El conflicto surge cuando el alma ya no encaja en los moldes estrechos de la lógica. Tocamos la herida de la despersonalización para que dejes de ser una sombra y reclames tu luz. Mira este video hasta el final; esto cambiará tu forma de ver tu propia realidad interna.', 'YOUTUBE', 'https://youtu.be/gk-mAo0JZNM', 'https://i.ytimg.com/vi/gk-mAo0JZNM/maxresdefault.jpg', 458000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('2d77fcf2fe254aed8fa2f61339bd1e56', 'a8c5e8c2b1b24636966f2930e8d7c218', '98c87b84bce74686a6e7d95c95940a31', '04. AUDIOTERAPIA 5 | SANA TU DOLOR Y ENFERMEDAD', 11, '<p>Esta audioterapia está diseñada para ayudarte a comprender y transformar el origen profundo de aquello que tu cuerpo hoy está manifestando.</p>
<p>Aquí no trabajamos únicamente el síntoma… trabajamos el mensaje.</p>
<p>A través de esta experiencia guiada, comenzarás a identificar la relación directa entre tus pensamientos, emociones no procesadas y las respuestas físicas que hoy experimentas. Aprenderás a observar tu cuerpo con conciencia, reconocer patrones internos y activar un proceso de reconfiguración desde el interior.</p>
<p>Este espacio no busca aliviar momentáneamente, sino generar un cambio real en la forma en la que te relacionas contigo mismo.</p>
<p>Es una invitación a asumir responsabilidad, escuchar lo que antes evitabas y reconectar con tu capacidad natural de equilibrio.</p>
<p>Si estás listo para dejar de ignorar lo que tu cuerpo intenta decirte… este es el punto de inicio.</p>', 'Esta audioterapia está diseñada para ayudarte a comprender y transformar el origen profundo de aquello que tu cuerpo hoy está manifestando.

Aquí no trabajamos únicamente el síntoma… trabajamos el mensaje.

A través de esta experiencia guiada, comenzarás a identificar la relación directa entre tus pensamientos, emociones no procesadas y las respuestas físicas que hoy experimentas. Aprenderás a observar tu cuerpo con conciencia, reconocer patrones internos y activar un proceso de reconfiguración desde el interior.

Este espacio no busca aliviar momentáneamente, sino generar un cambio real en la forma en la que te relacionas contigo mismo.

Es una invitación a asumir responsabilidad, escuchar lo que antes evitabas y reconectar con tu capacidad natural de equilibrio.

Si estás listo para dejar de ignorar lo que tu cuerpo intenta decirte… este es el punto de inicio.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('3bfb69f4f8f6494e8d7aae1f8b6cef0e', 'a8c5e8c2b1b24636966f2930e8d7c218', '2cb357f3ec8d41b1945f040c9103b2b7', '01. MASTERCLASS 6 | PODER CREADOR', 12, '<p><strong>(Visualiza este video diariamente desde el día 49 al 50)</strong></p>
<p>Este episodio no es para motivarte, es para confrontarte.</p>
<p>Aquí no hablamos de “pensar positivo”, hablamos de recuperar tu poder creador: ese que cediste al miedo, a la culpa, al qué dirán y a una vida que no elegiste conscientemente.</p>
<p>Si sientes que repites los mismos ciclos, que trabajas mucho pero no te sientes pleno, que algo dentro de ti sabe que hay más… este episodio es para ti.</p>
<p>No vas a encontrar respuestas cómodas.</p>
<p>Vas a encontrar verdad.</p>
<p>Y la verdad, cuando llega, transforma.</p>
<p>Escúchalo con presencia.</p>
<p>Porque después de este episodio, ya no podrás seguir diciendo: “no sabía”.</p>', '**(Visualiza este video diariamente desde el día 49 al 50)**

Este episodio no es para motivarte, es para confrontarte.

Aquí no hablamos de “pensar positivo”, hablamos de recuperar tu poder creador: ese que cediste al miedo, a la culpa, al qué dirán y a una vida que no elegiste conscientemente.

Si sientes que repites los mismos ciclos, que trabajas mucho pero no te sientes pleno, que algo dentro de ti sabe que hay más… este episodio es para ti.

No vas a encontrar respuestas cómodas.

Vas a encontrar verdad.

Y la verdad, cuando llega, transforma.

Escúchalo con presencia.

Porque después de este episodio, ya no podrás seguir diciendo: “no sabía”.', 'YOUTUBE', 'https://youtu.be/--Gkf0ZnPho?si=rN-ySSK9UgeRXS07', 'https://i.ytimg.com/vi/--Gkf0ZnPho/maxresdefault.jpg', 7000000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('f9bf97038a53433ba6b418b8aafb9d7b', 'a8c5e8c2b1b24636966f2930e8d7c218', '2cb357f3ec8d41b1945f040c9103b2b7', '02. SUPERA TU DUELO', 13, '<p><strong>(Visualiza este video diariamente desde el día 51 al 53)</strong></p>
<p><strong>¿Estás viviendo tu propia vida o solo interpretas el guion que el miedo escribió para ti? </strong><br /><br />Pasamos los días construyendo muros de cristal que llamamos seguridad, sin darnos cuenta de que se han convertido en nuestra propia celda.</p>
<p>En el núcleo de este conflicto arde una verdad incómoda: el dolor que evitas es precisamente la medicina que necesitas para despertar. Este video es una invitación a cruzar el umbral del temor y mirar a los ojos a tu propia sombra. Míralo completo; esto cambiará tu forma de ver tus crisis para siempre.</p>', '**(Visualiza este video diariamente desde el día 51 al 53)**

**¿Estás viviendo tu propia vida o solo interpretas el guion que el miedo escribió para ti? **  
  
Pasamos los días construyendo muros de cristal que llamamos seguridad, sin darnos cuenta de que se han convertido en nuestra propia celda.

En el núcleo de este conflicto arde una verdad incómoda: el dolor que evitas es precisamente la medicina que necesitas para despertar. Este video es una invitación a cruzar el umbral del temor y mirar a los ojos a tu propia sombra. Míralo completo; esto cambiará tu forma de ver tus crisis para siempre.', 'YOUTUBE', 'https://youtu.be/26JkyVYqiSw', 'https://i.ytimg.com/vi/26JkyVYqiSw/maxresdefault.jpg', 446000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('43f7a4236d6f4033951f9a47518964ab', 'a8c5e8c2b1b24636966f2930e8d7c218', '2cb357f3ec8d41b1945f040c9103b2b7', '03. RELACIONES DE PAREJA PLENA', 14, '<p><strong>(Visualiza este video diariamente desde el día 54 al 56)</strong></p>
<p><strong>¿Cuánto tiempo más intentarás sostener una estructura que ya se hizo pedazos en tu interior?</strong><br /><br />Caminamos como extraños en nuestra propia piel, alimentando versiones de nosotros mismos que solo existen para complacer el afuera, mientras el alma grita por un respiro.</p>
<p>Este video es un descenso necesario a la herida; ese espacio donde el dolor deja de ser ruido para convertirse en maestro. Revelamos el conflicto de habitar una identidad agotada que ya no te pertenece.</p>
<p>Míralo completo porque esto cambiará tu forma de entender tus quiebres; no son finales, sino el inicio de una arquitectura nueva.</p>', '**(Visualiza este video diariamente desde el día 54 al 56)**

**¿Cuánto tiempo más intentarás sostener una estructura que ya se hizo pedazos en tu interior?**  
  
Caminamos como extraños en nuestra propia piel, alimentando versiones de nosotros mismos que solo existen para complacer el afuera, mientras el alma grita por un respiro.

Este video es un descenso necesario a la herida; ese espacio donde el dolor deja de ser ruido para convertirse en maestro. Revelamos el conflicto de habitar una identidad agotada que ya no te pertenece.

Míralo completo porque esto cambiará tu forma de entender tus quiebres; no son finales, sino el inicio de una arquitectura nueva.', 'YOUTUBE', 'https://youtu.be/qf92w0Xb4xM', 'https://i.ytimg.com/vi/qf92w0Xb4xM/maxresdefault.jpg', 856000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('2e8fbe4e34d04e5ea015b31db97dd525', '01ac727ed506477883d5e015a0b792c1', 'a3169e0831c545db981c2a4959bf711e', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('ffed4354bf9542298ff171475e9b8a85', '01ac727ed506477883d5e015a0b792c1', 'a3169e0831c545db981c2a4959bf711e', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('da59192aa33f4a98b21185b65801bb01', '01ac727ed506477883d5e015a0b792c1', 'd8be5a08307a477985ab294a9aedf3ad', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('49566c88058042958b76cf5b198043e8', 'a8c5e8c2b1b24636966f2930e8d7c218', '2cb357f3ec8d41b1945f040c9103b2b7', '04. AUDIOTERAPIA 6 | SANA INFIDELIDAD DE TU PAREJA', 15, '<p><strong>Escucha esta audioterapia los días 20, 21 Y 22.</strong></p>
<p>Esta autoterapia no busca justificar lo que ocurrió ni minimizar el dolor.<br />Busca acompañarte a <strong>sanar la herida que dejó la infidelidad</strong>, sin quedarte atrapado en la rabia, la culpa o la obsesión por entenderlo todo.</p>
<p>Aquí se trabaja el impacto real que la traición deja en tu autoestima, en tu confianza y en tu vínculo contigo mismo. No para olvidar lo sucedido, sino para <strong>dejar de cargarlo en el cuerpo y en la mente</strong>.</p>
<p>Este espacio es para ti.<br />Para sentir lo que no se permitió sentir.<br />Para soltar lo que no fue tu responsabilidad.<br />Y para recuperar tu centro, con dignidad y claridad.</p>
<p>Haz esta autoterapia con honestidad y presencia.<br />Sanar no es volver atrás.<br />Es <strong>volver a ti</strong>.</p>', '**Escucha esta audioterapia los días 20, 21 Y 22.**

Esta autoterapia no busca justificar lo que ocurrió ni minimizar el dolor.  
Busca acompañarte a **sanar la herida que dejó la infidelidad**, sin quedarte atrapado en la rabia, la culpa o la obsesión por entenderlo todo.

Aquí se trabaja el impacto real que la traición deja en tu autoestima, en tu confianza y en tu vínculo contigo mismo. No para olvidar lo sucedido, sino para **dejar de cargarlo en el cuerpo y en la mente**.

Este espacio es para ti.  
Para sentir lo que no se permitió sentir.  
Para soltar lo que no fue tu responsabilidad.  
Y para recuperar tu centro, con dignidad y claridad.

Haz esta autoterapia con honestidad y presencia.  
Sanar no es volver atrás.  
Es **volver a ti**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('e5cc3a5abd3b41fd9c45319e6a56ce72', 'a8c5e8c2b1b24636966f2930e8d7c218', '46a3320fc9844e0c802e24a33279ce5f', '01. MASTERCLASS 7 | ESENCIA DINERO', 16, '<p><strong>(Visualiza este video diariamente desde el día 57 al 58)</strong></p>
<p>Esta masterclass estará enfocada en revelar las creencias inconscientes que están saboteando tu relación con el dinero. Aquí no se trabaja desde fórmulas rápidas para “ganar más”, sino desde la raíz: lo que piensas, sientes y crees sin darte cuenta sobre la abundancia.</p>
<p>A lo largo de la sesión se aborda cómo ideas como “no merezco tener mucho”, “si tengo dinero me volveré mala persona” o “para ganar hay que sufrir” influyen directamente en tus decisiones, tus precios, tus oportunidades y tu capacidad para sostener ingresos. Se explora cómo el miedo al rechazo, a la envidia, a la soledad o a perder tu espiritualidad puede llevarte a elegir inconscientemente la escasez.</p>
<p>Se profundiza en la comprensión de que el dinero no es el problema, sino el reflejo. Tus resultados financieros son consecuencia de tus comportamientos, y tus comportamientos nacen de tus creencias más profundas.</p>
<p>Esta masterclass te invita a cuestionar lo que aprendiste sobre el dinero, a identificar tus patrones de autosabotaje y a comenzar a construir una relación más consciente, íntegra y poderosa con la abundancia.</p>', '**(Visualiza este video diariamente desde el día 57 al 58)**

Esta masterclass estará enfocada en revelar las creencias inconscientes que están saboteando tu relación con el dinero. Aquí no se trabaja desde fórmulas rápidas para “ganar más”, sino desde la raíz: lo que piensas, sientes y crees sin darte cuenta sobre la abundancia.

A lo largo de la sesión se aborda cómo ideas como “no merezco tener mucho”, “si tengo dinero me volveré mala persona” o “para ganar hay que sufrir” influyen directamente en tus decisiones, tus precios, tus oportunidades y tu capacidad para sostener ingresos. Se explora cómo el miedo al rechazo, a la envidia, a la soledad o a perder tu espiritualidad puede llevarte a elegir inconscientemente la escasez.

Se profundiza en la comprensión de que el dinero no es el problema, sino el reflejo. Tus resultados financieros son consecuencia de tus comportamientos, y tus comportamientos nacen de tus creencias más profundas.

Esta masterclass te invita a cuestionar lo que aprendiste sobre el dinero, a identificar tus patrones de autosabotaje y a comenzar a construir una relación más consciente, íntegra y poderosa con la abundancia.', 'YOUTUBE', 'https://youtu.be/GKNG8-fIaPo', 'https://i.ytimg.com/vi/GKNG8-fIaPo/maxresdefault.jpg', 6591000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('6f92f69623fc4a3687d9b6db29893680', 'a8c5e8c2b1b24636966f2930e8d7c218', '46a3320fc9844e0c802e24a33279ce5f', '02. COMO SUPERAR LA FOBIA SOCIAL', 17, '<p><strong>(Visualiza este video diariamente desde el día 59 al 61)</strong></p>
<p>Tu ansiedad no es tu enemiga; es la alarma de una mente que aprendió a protegerte exagerando el peligro. La fobia social no nace de la debilidad, sino de una distorsión inconsciente que convirtió miradas en amenazas y opiniones en juicios definitivos.</p>
<p>Superarla no es “forzarte a socializar”, es reprogramar la narrativa interna que te hace sentir expuesto. La reprogramación mental es clave para desmontar las interpretaciones erróneas que tu mente creó como mecanismo de defensa. Pero no basta con entenderlo racionalmente; es necesario un trabajo emocional profundo que toque patrones reprimidos, heridas antiguas y memorias que aún vibran en tu cuerpo.</p>
<p>También el cuerpo habla. El exceso de cortisol, la tensión constante y el desgaste energético necesitan atención consciente: alimentación adecuada, movimiento y regulación fisiológica para devolverle al sistema nervioso la sensación de seguridad.</p>
<p>Este proceso no es genérico ni superficial. Requiere evaluación precisa y un enfoque personalizado. No es para todos, porque no todos están listos para dejar la identidad que construyeron alrededor del miedo. Pero para quien decide enfrentarlo, el cambio puede ser más rápido de lo que imagina.</p>', '**(Visualiza este video diariamente desde el día 59 al 61)**

Tu ansiedad no es tu enemiga; es la alarma de una mente que aprendió a protegerte exagerando el peligro. La fobia social no nace de la debilidad, sino de una distorsión inconsciente que convirtió miradas en amenazas y opiniones en juicios definitivos.

Superarla no es “forzarte a socializar”, es reprogramar la narrativa interna que te hace sentir expuesto. La reprogramación mental es clave para desmontar las interpretaciones erróneas que tu mente creó como mecanismo de defensa. Pero no basta con entenderlo racionalmente; es necesario un trabajo emocional profundo que toque patrones reprimidos, heridas antiguas y memorias que aún vibran en tu cuerpo.

También el cuerpo habla. El exceso de cortisol, la tensión constante y el desgaste energético necesitan atención consciente: alimentación adecuada, movimiento y regulación fisiológica para devolverle al sistema nervioso la sensación de seguridad.

Este proceso no es genérico ni superficial. Requiere evaluación precisa y un enfoque personalizado. No es para todos, porque no todos están listos para dejar la identidad que construyeron alrededor del miedo. Pero para quien decide enfrentarlo, el cambio puede ser más rápido de lo que imagina.', 'YOUTUBE', 'https://youtu.be/N_NYcS29RV0', 'https://i.ytimg.com/vi/N_NYcS29RV0/maxresdefault.jpg', 877000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('bc70f826677c451d905f8235cc1c0e17', 'a8c5e8c2b1b24636966f2930e8d7c218', '46a3320fc9844e0c802e24a33279ce5f', '03. DESPIERTA TU POTENCIAL', 18, '<p><strong>(Visualiza este video diariamente desde el día 62 al 64)</strong></p>
<p>Tus conflictos emocionales no son el origen del problema; son el síntoma visible de una estructura mental que se formó cuando aún no tenías herramientas para comprender lo que vivías. Entre los 0 y los 7 años se instalaron programas invisibles que hoy siguen dirigiendo tus reacciones, tus relaciones y tus miedos.</p>
<p>Repites patrones en pareja, en el trabajo o frente a la ansiedad, creyendo que el contexto cambia, cuando en realidad es la misma programación operando en distintos escenarios. El 95% de tu comportamiento nace del inconsciente. Y no se transforma solo con entenderlo intelectualmente, sino haciéndolo consciente a través de experiencias que impacten más profundo que cualquier explicación racional.</p>
<p>La mente cambia cuando vive algo que la sacude, cuando atraviesa un shock emocional que reescribe la narrativa interna. Una experiencia transformadora puede generar en horas lo que años de análisis no logran: integrar, liberar y reorientar tu dirección de vida.</p>
<p>Esta invitación no es a “hablar de tus problemas”, sino a vivir un proceso que te permita elegir distinto. A salir del ciclo de ansiedad, depresión o somatización y despertar el potencial que siempre estuvo detrás del conflicto. Hay una consulta personalizada para evaluar tu caso y un proceso completo para quienes están listos para comprometerse con su transformación real.</p>', '**(Visualiza este video diariamente desde el día 62 al 64)**

Tus conflictos emocionales no son el origen del problema; son el síntoma visible de una estructura mental que se formó cuando aún no tenías herramientas para comprender lo que vivías. Entre los 0 y los 7 años se instalaron programas invisibles que hoy siguen dirigiendo tus reacciones, tus relaciones y tus miedos.

Repites patrones en pareja, en el trabajo o frente a la ansiedad, creyendo que el contexto cambia, cuando en realidad es la misma programación operando en distintos escenarios. El 95% de tu comportamiento nace del inconsciente. Y no se transforma solo con entenderlo intelectualmente, sino haciéndolo consciente a través de experiencias que impacten más profundo que cualquier explicación racional.

La mente cambia cuando vive algo que la sacude, cuando atraviesa un shock emocional que reescribe la narrativa interna. Una experiencia transformadora puede generar en horas lo que años de análisis no logran: integrar, liberar y reorientar tu dirección de vida.

Esta invitación no es a “hablar de tus problemas”, sino a vivir un proceso que te permita elegir distinto. A salir del ciclo de ansiedad, depresión o somatización y despertar el potencial que siempre estuvo detrás del conflicto. Hay una consulta personalizada para evaluar tu caso y un proceso completo para quienes están listos para comprometerse con su transformación real.', 'YOUTUBE', 'https://youtu.be/AOI9KHwzGLw', 'https://i.ytimg.com/vi/AOI9KHwzGLw/maxresdefault.jpg', 567000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('4bc3ffc865a04a71beb26989ed0053cb', '57955d86b173488f910cd4ecd5d8cbaf', '2c6aefd1e0044558a387e53a307404ab', '01. EL VICTIMISMO TE ROBA EL PODER CREADOR', 8, '<p><strong>(Visualiza este video desde el día 71 al 73)</strong></p>
<p>Dices: “Ya entendí que soy un macaco… ahora dame herramientas.” Pero aquí viene la verdad que nadie te quiere decir: buscar más herramientas puede ser tu nueva forma de huir de ti.</p>
<p>En esta segunda clase se revela lo que RENASER llama los tres “cánceres” del sufrimiento: victimismo, soberbia y flojera. No como insulto, sino como diagnóstico crudo de cómo tu mente se sabotea. La víctima se queja, culpa y espera. La soberbia te enceguece: te hace creer que no necesitas ayuda… hasta que la vida te rompe. Y la flojera se camufla de “tranquilidad”, pero termina en desorden, caos y enfermedad. Aquí se desarma una ilusión peligrosa: que el conocimiento externo te salva. A veces solo te llena la cabeza y te vacía el alma.</p>
<p>Este episodio no es para sentirte bien. Es para ver la raíz. Si llegas hasta el final, te llevas tres mandamientos prácticos para recuperar poder: no te quejes, no culpes, no esperes… y un ejercicio guiado que te obliga a mirarte sin máscaras.</p>
<p>Cuando vuelves a ti, el caos deja de mandarte.</p>
<p>RENASER</p>', '**(Visualiza este video desde el día 71 al 73)**

Dices: “Ya entendí que soy un macaco… ahora dame herramientas.” Pero aquí viene la verdad que nadie te quiere decir: buscar más herramientas puede ser tu nueva forma de huir de ti.

En esta segunda clase se revela lo que RENASER llama los tres “cánceres” del sufrimiento: victimismo, soberbia y flojera. No como insulto, sino como diagnóstico crudo de cómo tu mente se sabotea. La víctima se queja, culpa y espera. La soberbia te enceguece: te hace creer que no necesitas ayuda… hasta que la vida te rompe. Y la flojera se camufla de “tranquilidad”, pero termina en desorden, caos y enfermedad. Aquí se desarma una ilusión peligrosa: que el conocimiento externo te salva. A veces solo te llena la cabeza y te vacía el alma.

Este episodio no es para sentirte bien. Es para ver la raíz. Si llegas hasta el final, te llevas tres mandamientos prácticos para recuperar poder: no te quejes, no culpes, no esperes… y un ejercicio guiado que te obliga a mirarte sin máscaras.

Cuando vuelves a ti, el caos deja de mandarte.

RENASER', 'YOUTUBE', 'https://www.youtube.com/watch?v=Wl_h0YKQmX4&index=2', 'https://i.ytimg.com/vi/Wl_h0YKQmX4/maxresdefault.jpg', 1403000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('94bfcd83cecd4eb0aa8e798a1c8be723', 'a8c5e8c2b1b24636966f2930e8d7c218', '46a3320fc9844e0c802e24a33279ce5f', '04. AUDIOTERAPIA 7 | NADA TIENE SENTIDO EN TU VIDA', 19, '<p><strong>Esucha esta audioterapia los días 11, 12 y 13</strong></p>
<p>Si últimamente sientes que nada tiene sentido, este podcast es para ti.</p>
<p>No porque estés “mal”… sino porque tu mente está pidiendo una verdad que ya no puedes seguir maquillando. A veces el vacío no es depresión: es una señal de que estás viviendo en automático, cumpliendo expectativas, sobreviviendo, pero no habitándote.</p>
<p>En este episodio vamos directo: qué significa realmente sentir que tu vida no tiene sentido, por qué aparece esa sensación, qué mentira se te está cayendo por dentro y cómo empezar a recuperar dirección sin frases bonitas ni autoengaño.</p>
<p>Escúchalo con presencia. No para entretenerte.</p>', '**Esucha esta audioterapia los días 11, 12 y 13**

Si últimamente sientes que nada tiene sentido, este podcast es para ti.

No porque estés “mal”… sino porque tu mente está pidiendo una verdad que ya no puedes seguir maquillando. A veces el vacío no es depresión: es una señal de que estás viviendo en automático, cumpliendo expectativas, sobreviviendo, pero no habitándote.

En este episodio vamos directo: qué significa realmente sentir que tu vida no tiene sentido, por qué aparece esa sensación, qué mentira se te está cayendo por dentro y cómo empezar a recuperar dirección sin frases bonitas ni autoengaño.

Escúchalo con presencia. No para entretenerte.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('a8e4f54c6f784a908651a63b69704e99', '57955d86b173488f910cd4ecd5d8cbaf', '7395c4a36b0c42dba12a4f8560ec6653', '01. GUÍA FASE 4', 0, '<p>Esta fase representa el punto donde todo deja de ser teoría… y se convierte en identidad.</p>
<p>Del día 65 al 90, el enfoque ya no está en descubrir ni en corregir, sino en <strong>sostener, integrar y ejecutar</strong> desde una versión más consciente, firme y alineada contigo mismo. Aquí se mide algo más importante que el esfuerzo: <strong>la consistencia real</strong>.</p>
<p>Durante esta etapa, vas a consolidar los hábitos, decisiones y estructuras internas que has venido construyendo. Es el momento donde se revela si el cambio fue superficial… o si realmente estás operando desde un nuevo nivel.</p>
<p>Esta guía te acompaña a:</p>
<ul><li><p>Mantener claridad incluso en escenarios desafiantes</p></li><li><p>Sostener disciplina sin depender de la motivación</p></li><li><p>Detectar micro retrocesos antes de que escalen</p></li><li><p>Operar con enfoque, presencia y dirección</p></li></ul>
<p>La Fase IV no es el final… es la validación.</p>
<p>Aquí se define si vuelves a lo anterior o si te conviertes en alguien que ya no negocia con su crecimiento.</p>
<p>Este tramo es para quienes deciden cerrar el proceso con contundencia y demostrar, con hechos, que no vinieron a intentarlo… vinieron a transformarse.</p>', 'Esta fase representa el punto donde todo deja de ser teoría… y se convierte en identidad.

Del día 65 al 90, el enfoque ya no está en descubrir ni en corregir, sino en **sostener, integrar y ejecutar** desde una versión más consciente, firme y alineada contigo mismo. Aquí se mide algo más importante que el esfuerzo: **la consistencia real**.

Durante esta etapa, vas a consolidar los hábitos, decisiones y estructuras internas que has venido construyendo. Es el momento donde se revela si el cambio fue superficial… o si realmente estás operando desde un nuevo nivel.

Esta guía te acompaña a:

- Mantener claridad incluso en escenarios desafiantes
- Sostener disciplina sin depender de la motivación
- Detectar micro retrocesos antes de que escalen
- Operar con enfoque, presencia y dirección

La Fase IV no es el final… es la validación.

Aquí se define si vuelves a lo anterior o si te conviertes en alguien que ya no negocia con su crecimiento.

Este tramo es para quienes deciden cerrar el proceso con contundencia y demostrar, con hechos, que no vinieron a intentarlo… vinieron a transformarse.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('2aafb4cad13b4cc281ab4ca5777961bb', '57955d86b173488f910cd4ecd5d8cbaf', '7395c4a36b0c42dba12a4f8560ec6653', '02. CHECKLIST FASE 4', 1, '<p>Este checklist ha sido diseñado como una herramienta de ejecución y seguimiento para la etapa final del programa. Su función no es solo organizar tus acciones, sino ayudarte a sostener con precisión el nivel de disciplina, enfoque y consciencia que has venido construyendo.</p>
<p>Durante esta fase, cada acción cuenta. Por eso, este sistema te permitirá validar diariamente tu compromiso, identificar desviaciones a tiempo y asegurar que estás operando desde tu nueva identidad, no desde patrones pasados.</p>
<p>Aquí no se trata de intentar… se trata de cumplir.</p>
<p>Este checklist acompaña tu proceso para que cierres el programa con consistencia, claridad y resultados reales, asegurando que lo trabajado no se pierda, sino que se integre como parte de tu forma de vivir y decidir.</p>', 'Este checklist ha sido diseñado como una herramienta de ejecución y seguimiento para la etapa final del programa. Su función no es solo organizar tus acciones, sino ayudarte a sostener con precisión el nivel de disciplina, enfoque y consciencia que has venido construyendo.

Durante esta fase, cada acción cuenta. Por eso, este sistema te permitirá validar diariamente tu compromiso, identificar desviaciones a tiempo y asegurar que estás operando desde tu nueva identidad, no desde patrones pasados.

Aquí no se trata de intentar… se trata de cumplir.

Este checklist acompaña tu proceso para que cierres el programa con consistencia, claridad y resultados reales, asegurando que lo trabajado no se pierda, sino que se integre como parte de tu forma de vivir y decidir.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('da8b4a3d9ca442a4b29a69f60ae08a14', '57955d86b173488f910cd4ecd5d8cbaf', 'c5b297b4fd7941e68615a6642c271a51', '01. TU PROBLEMA NO ES FLOJERA, ES ODIO A TI MISMO', 2, '<p><strong>(Visualiza este video desde el día 65 al 67)</strong></p>
<p>Este no es un descanso.<br />Es un permiso estratégico para que la mente deje de fingir.</p>
<p>Durante estos días no se busca control ni disciplina, se busca <strong>verdad</strong>.<br />Cuando bajas la exigencia, el exceso revela patrones, el cuerpo habla y el personaje se cae.</p>
<p>Aquí no corriges nada.<br />Observas todo.</p>
<p>Porque la transformación no comienza cuando mejoras,<br />comienza cuando <strong>ves con claridad lo que realmente estás sosteniendo.</strong></p>', '**(Visualiza este video desde el día 65 al 67)**

Este no es un descanso.  
Es un permiso estratégico para que la mente deje de fingir.

Durante estos días no se busca control ni disciplina, se busca **verdad**.  
Cuando bajas la exigencia, el exceso revela patrones, el cuerpo habla y el personaje se cae.

Aquí no corriges nada.  
Observas todo.

Porque la transformación no comienza cuando mejoras,  
comienza cuando **ves con claridad lo que realmente estás sosteniendo.**', 'YOUTUBE', 'https://youtu.be/yQvLRBGOMvk', 'https://i.ytimg.com/vi/yQvLRBGOMvk/maxresdefault.jpg', 925000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('4ebaea445bc6446e9ad9d9de1f733df9', '57955d86b173488f910cd4ecd5d8cbaf', 'c5b297b4fd7941e68615a6642c271a51', '02. MASTERCLASS 8 | DESPIERTA TU TIPO DE GUERRERO', 3, '<p>Esta masterclass estará enfocada en activar tu arquetipo de Guerrero y recordarte que no viniste a reaccionar ante la vida, sino a crearla. Aquí no se habla de “pedir deseos al universo”, sino de asumir tu poder creador con coherencia, intención y disciplina interna.</p>
<p>A lo largo de la sesión se profundiza en cómo la manifestación no es magia, sino alineación: lo que piensas, lo que sientes y lo que vibras deben ir en la misma dirección. Se trabaja la claridad de metas, la visualización consciente y la conexión emocional real con aquello que deseas, entendiendo que el corazón —con su potente campo electromagnético— amplifica lo que verdaderamente sostienes por dentro.</p>
<p>También se exponen los errores que debilitan tu creación: dudar de ti, vibrar en miedo, enfocarte en lo que no quieres, sentir que no mereces o abandonar el proceso antes de tiempo. El Guerrero no vive en la queja ni en la inconstancia; vive en certeza, coherencia y fe sostenida.</p>
<p>Se explora el poder de la intención como fuerza superior a las palabras, y cómo los simbolismos y la visualización consciente pueden reprogramar el inconsciente para alinear tu realidad externa con tu mundo interno.</p>
<p>Esta masterclass te invita a dejar de esperar señales y empezar a convertirte en la señal. A reclamar tu capacidad de crear, sostener y materializar desde la conciencia, la constancia y el corazón.</p>', 'Esta masterclass estará enfocada en activar tu arquetipo de Guerrero y recordarte que no viniste a reaccionar ante la vida, sino a crearla. Aquí no se habla de “pedir deseos al universo”, sino de asumir tu poder creador con coherencia, intención y disciplina interna.

A lo largo de la sesión se profundiza en cómo la manifestación no es magia, sino alineación: lo que piensas, lo que sientes y lo que vibras deben ir en la misma dirección. Se trabaja la claridad de metas, la visualización consciente y la conexión emocional real con aquello que deseas, entendiendo que el corazón —con su potente campo electromagnético— amplifica lo que verdaderamente sostienes por dentro.

También se exponen los errores que debilitan tu creación: dudar de ti, vibrar en miedo, enfocarte en lo que no quieres, sentir que no mereces o abandonar el proceso antes de tiempo. El Guerrero no vive en la queja ni en la inconstancia; vive en certeza, coherencia y fe sostenida.

Se explora el poder de la intención como fuerza superior a las palabras, y cómo los simbolismos y la visualización consciente pueden reprogramar el inconsciente para alinear tu realidad externa con tu mundo interno.

Esta masterclass te invita a dejar de esperar señales y empezar a convertirte en la señal. A reclamar tu capacidad de crear, sostener y materializar desde la conciencia, la constancia y el corazón.', 'YOUTUBE', 'https://youtu.be/q9yYMN2-8fE?si=df_QeDOfZiK_Bfwp', 'https://i.ytimg.com/vi/q9yYMN2-8fE/maxresdefault.jpg', 5377000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('2fc79eec0604456c945d12d74bd41595', '01ac727ed506477883d5e015a0b792c1', 'd8be5a08307a477985ab294a9aedf3ad', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e7130cde748641a79debbe8311b0db64', '01ac727ed506477883d5e015a0b792c1', 'd8be5a08307a477985ab294a9aedf3ad', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('a36ae227c82841cfb665a99b65b54aeb', '01ac727ed506477883d5e015a0b792c1', '4780b840603b480eaa8fb704ac4612bf', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('3dd07efb7a474941ab1978ffab9c1aaf', '57955d86b173488f910cd4ecd5d8cbaf', 'c5b297b4fd7941e68615a6642c271a51', '03. AUDIOTERAPIA 8: AMA EL FRACASO', 4, '<p>Esta autoterapia no está diseñada para motivarte ni para decirte que “todo pasa por algo”. Está diseñada para ayudarte a <strong>cambiar la relación que tienes con el fracaso</strong>.</p>
<p>Aquí se trabaja el miedo a equivocarte, a intentarlo y a exponerte. No para romantizar el error, sino para dejar de usarlo como excusa para detenerte o castigarte. El fracaso deja de ser un enemigo cuando entiendes qué vino a mostrarte y qué parte de ti necesita crecer.</p>
<p>Este espacio es para confrontar la exigencia, la vergüenza y la autoimagen que se rompe cuando las cosas no salen como esperabas.</p>
<p>Haz esta autoterapia con honestidad y apertura.<br />Cuando dejas de huir del fracaso, <strong>empiezas a avanzar de verdad</strong>.</p>', 'Esta autoterapia no está diseñada para motivarte ni para decirte que “todo pasa por algo”. Está diseñada para ayudarte a **cambiar la relación que tienes con el fracaso**.

Aquí se trabaja el miedo a equivocarte, a intentarlo y a exponerte. No para romantizar el error, sino para dejar de usarlo como excusa para detenerte o castigarte. El fracaso deja de ser un enemigo cuando entiendes qué vino a mostrarte y qué parte de ti necesita crecer.

Este espacio es para confrontar la exigencia, la vergüenza y la autoimagen que se rompe cuando las cosas no salen como esperabas.

Haz esta autoterapia con honestidad y apertura.  
Cuando dejas de huir del fracaso, **empiezas a avanzar de verdad**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('fc94e5d4e2dc4f0c98b638c2e1915ccf', '57955d86b173488f910cd4ecd5d8cbaf', '3ff6a6eecaf64f0fb20164c158bdb9d8', '01. DEJA DE REACCIONAR COMO UN MACACO', 5, '<p><strong>(Visualiza este video desde el día 68 al 70)</strong></p>
<p>¿Y si aquello que más te duele no te está pasando a pesar de ti, sino a través de ti? Esta no es una idea cómoda. Es una verdad que incomoda porque rompe el relato donde siempre hay un culpable afuera.</p>
<p>Durante años te enseñaron a sobrevivir, no a comprenderte. A reaccionar, no a elegir. En este episodio se abre una grieta: la diferencia entre vivir desde el miedo —ese cerebro antiguo que solo sabe defenderse— y despertar una conciencia que puede detener el ciclo. Aquí no se habla de culpas, sino de responsabilidad. De cómo la preocupación se disfraza de amor, de cómo el estrés se normaliza hasta volverse enfermedad, y de por qué repetir lo mismo no es mala suerte, es programación. Nada de rodeos: lo que llamas destino suele ser hábito emocional.</p>
<p>Si te atreves a mirar sin filtros, este video puede cambiar la forma en que entiendes tu ansiedad, tus relaciones y tu cuerpo. No promete alivio inmediato; promete claridad. Y la claridad, cuando llega, ya no te deja volver atrás.</p>
<p>Aceptar tu sombra no te hace débil. Te devuelve el poder.</p>
<p>RENASER</p>', '**(Visualiza este video desde el día 68 al 70)**

¿Y si aquello que más te duele no te está pasando a pesar de ti, sino a través de ti? Esta no es una idea cómoda. Es una verdad que incomoda porque rompe el relato donde siempre hay un culpable afuera.

Durante años te enseñaron a sobrevivir, no a comprenderte. A reaccionar, no a elegir. En este episodio se abre una grieta: la diferencia entre vivir desde el miedo —ese cerebro antiguo que solo sabe defenderse— y despertar una conciencia que puede detener el ciclo. Aquí no se habla de culpas, sino de responsabilidad. De cómo la preocupación se disfraza de amor, de cómo el estrés se normaliza hasta volverse enfermedad, y de por qué repetir lo mismo no es mala suerte, es programación. Nada de rodeos: lo que llamas destino suele ser hábito emocional.

Si te atreves a mirar sin filtros, este video puede cambiar la forma en que entiendes tu ansiedad, tus relaciones y tu cuerpo. No promete alivio inmediato; promete claridad. Y la claridad, cuando llega, ya no te deja volver atrás.

Aceptar tu sombra no te hace débil. Te devuelve el poder.

RENASER', 'YOUTUBE', 'https://youtu.be/RIT2G8Zt9xs', 'https://i.ytimg.com/vi/RIT2G8Zt9xs/maxresdefault.jpg', 1231000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('b3cc8947160a4651adac4934ba92eddf', '57955d86b173488f910cd4ecd5d8cbaf', '3ff6a6eecaf64f0fb20164c158bdb9d8', '02. MASTERCLASS 9 | MENTALIDAD DE CEO', 6, '<p>Esta masterclass estará enfocada en desarrollar la mentalidad de CEO desde la raíz psicológica, no desde técnicas superficiales de productividad. Aquí no se trata solo de aprender a gestionar un negocio, sino de convertirte en el tipo de persona capaz de sostener visión, presión y responsabilidad sin caer en el victimismo.</p>
<p>A lo largo de la sesión se aborda el paso decisivo de víctima a creador. Se trabaja cómo el victimismo se manifiesta en la queja, la culpa, la justificación y la espera constante de que otros cambien primero. Se profundiza en reconocer estos patrones —muchas veces inconscientes— y transformarlos en poder personal y liderazgo interno.</p>
<p>También se explora la educación del cuerpo y la mente. El éxito no es natural; requiere disciplina, entrenamiento y capacidad de incomodidad. No basta con hacer tareas como un CEO, hay que serlo: pensar con claridad, actuar con firmeza y sostener abundancia interna incluso en escenarios desafiantes.</p>
<p>Se desarrolla la importancia de una visión clara y un mapa detallado. Un verdadero CEO no improvisa su vida: diseña sistemas, estructura procesos y tiene obsesión estratégica por sus objetivos. Esa obsesión no es ansiedad, es dirección consciente.</p>
<p>Finalmente, se trabajan los arquetipos inconscientes que limitan el liderazgo: creer que no tienes control, que dependes de otros para cambiar o que no eres capaz de sostener decisiones difíciles.</p>
<p>Esta masterclass te invita a dejar de reaccionar ante la vida y empezar a dirigirla. A asumir tu poder creador con responsabilidad, claridad y determinación.</p>', 'Esta masterclass estará enfocada en desarrollar la mentalidad de CEO desde la raíz psicológica, no desde técnicas superficiales de productividad. Aquí no se trata solo de aprender a gestionar un negocio, sino de convertirte en el tipo de persona capaz de sostener visión, presión y responsabilidad sin caer en el victimismo.

A lo largo de la sesión se aborda el paso decisivo de víctima a creador. Se trabaja cómo el victimismo se manifiesta en la queja, la culpa, la justificación y la espera constante de que otros cambien primero. Se profundiza en reconocer estos patrones —muchas veces inconscientes— y transformarlos en poder personal y liderazgo interno.

También se explora la educación del cuerpo y la mente. El éxito no es natural; requiere disciplina, entrenamiento y capacidad de incomodidad. No basta con hacer tareas como un CEO, hay que serlo: pensar con claridad, actuar con firmeza y sostener abundancia interna incluso en escenarios desafiantes.

Se desarrolla la importancia de una visión clara y un mapa detallado. Un verdadero CEO no improvisa su vida: diseña sistemas, estructura procesos y tiene obsesión estratégica por sus objetivos. Esa obsesión no es ansiedad, es dirección consciente.

Finalmente, se trabajan los arquetipos inconscientes que limitan el liderazgo: creer que no tienes control, que dependes de otros para cambiar o que no eres capaz de sostener decisiones difíciles.

Esta masterclass te invita a dejar de reaccionar ante la vida y empezar a dirigirla. A asumir tu poder creador con responsabilidad, claridad y determinación.', 'YOUTUBE', 'https://youtu.be/liVI5k6WWes?si=X4wAML7FPO05INoe', 'https://i.ytimg.com/vi/liVI5k6WWes/maxresdefault.jpg', 5917000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('2900ea2d72c84d459df80dee0731138f', '57955d86b173488f910cd4ecd5d8cbaf', '3ff6a6eecaf64f0fb20164c158bdb9d8', '03. AUDIOTERAPIA 9: PROBLEMAS CON EL DINERO', 7, '<p>Esta audioterapia no habla de dinero como números, sino como <strong>síntoma</strong>. Aquí se explora por qué, aunque trabajes, te esfuerces o sepas qué hacer, el dinero no se sostiene, se va rápido o nunca es suficiente.</p>
<p>Se trabaja la raíz emocional y mental que afecta tu relación con el recibir, el merecimiento, el miedo a perder y la forma en la que administras tu energía. No para culparte, sino para <strong>dejar de repetir patrones invisibles</strong> que sabotean tu estabilidad económica.</p>
<p>Este espacio te invita a mirar con honestidad qué historia interna estás viviendo con el dinero y cómo esa historia se refleja hoy en tus decisiones.</p>
<p>Haz esta audioterapia con presencia.<br />Cuando ordenas la relación interna, <strong>el resultado externo cambia</strong>.</p>', 'Esta audioterapia no habla de dinero como números, sino como **síntoma**. Aquí se explora por qué, aunque trabajes, te esfuerces o sepas qué hacer, el dinero no se sostiene, se va rápido o nunca es suficiente.

Se trabaja la raíz emocional y mental que afecta tu relación con el recibir, el merecimiento, el miedo a perder y la forma en la que administras tu energía. No para culparte, sino para **dejar de repetir patrones invisibles** que sabotean tu estabilidad económica.

Este espacio te invita a mirar con honestidad qué historia interna estás viviendo con el dinero y cómo esa historia se refleja hoy en tus decisiones.

Haz esta audioterapia con presencia.  
Cuando ordenas la relación interna, **el resultado externo cambia**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('d8ab4cc7bfce4dd592879a35da60e26e', '57955d86b173488f910cd4ecd5d8cbaf', 'b583cfcc4b1d48cc9cb4b9dcee3d8442', '01. SER DE ALTO RENDIMIENTO ES PARA LOCOS', 14, '<p><strong>(Visualiza este video desde el día 83 al 85)</strong></p>
<p>El alto rendimiento no se trata de hacer más cosas, sino de <strong>valorar y usar el tiempo con conciencia</strong>. La verdadera productividad se mide por cómo utilizamos cada segundo, eliminando distracciones y enfocándonos en lo esencial.</p>
<p>En este video también se aborda cómo el miedo, la procrastinación y el victimismo limitan nuestro potencial. Muchas veces la procrastinación es simplemente miedo disfrazado. Cuando dejamos de culpar a las circunstancias y asumimos responsabilidad por nuestras decisiones, recuperamos el control de nuestra vida y nuestro tiempo.</p>
<p>Además, se explora el poder del inconsciente en nuestras decisiones y cómo conectar con nuestras emociones puede desbloquear nuestro verdadero potencial para vivir con mayor claridad, energía y propósito.</p>
<p>RENASER</p>', '**(Visualiza este video desde el día 83 al 85)**

El alto rendimiento no se trata de hacer más cosas, sino de **valorar y usar el tiempo con conciencia**. La verdadera productividad se mide por cómo utilizamos cada segundo, eliminando distracciones y enfocándonos en lo esencial.

En este video también se aborda cómo el miedo, la procrastinación y el victimismo limitan nuestro potencial. Muchas veces la procrastinación es simplemente miedo disfrazado. Cuando dejamos de culpar a las circunstancias y asumimos responsabilidad por nuestras decisiones, recuperamos el control de nuestra vida y nuestro tiempo.

Además, se explora el poder del inconsciente en nuestras decisiones y cómo conectar con nuestras emociones puede desbloquear nuestro verdadero potencial para vivir con mayor claridad, energía y propósito.

RENASER', 'YOUTUBE', 'https://www.youtube.com/watch?v=EBn3dDn4oQs', 'https://i.ytimg.com/vi/EBn3dDn4oQs/maxresdefault.jpg', 1162000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('256feb80e80a4c69962301c7b9f6fe4a', '57955d86b173488f910cd4ecd5d8cbaf', '2c6aefd1e0044558a387e53a307404ab', '02. AUDIOTERAPIA 10: CONVERTIRTE EN UNA REINA', 9, '<p>Esta autoterapia no trata de poder externo ni de roles idealizados. Trata de <strong>recuperar tu trono interno</strong>: límites claros, dignidad emocional y presencia consciente.</p>
<p>Aquí se trabaja la transición de la autoexigencia, la complacencia y la dependencia emocional hacia una identidad que se sostiene sola. No para dominar, sino para <strong>habitarte con respeto</strong>, elegir desde la claridad y dejar de negociar tu valor.</p>
<p>Este espacio es para soltar versiones pequeñas de ti, revisar cómo te relacionas con el amor, el cuerpo, el dinero y las decisiones, y empezar a actuar desde una identidad más firme y alineada.</p>
<p>Haz esta autoterapia con presencia y compromiso.<br />Convertirte en reina no es imponerte.<br />Es <strong>volver a ocupar tu lugar</strong>.</p>', 'Esta autoterapia no trata de poder externo ni de roles idealizados. Trata de **recuperar tu trono interno**: límites claros, dignidad emocional y presencia consciente.

Aquí se trabaja la transición de la autoexigencia, la complacencia y la dependencia emocional hacia una identidad que se sostiene sola. No para dominar, sino para **habitarte con respeto**, elegir desde la claridad y dejar de negociar tu valor.

Este espacio es para soltar versiones pequeñas de ti, revisar cómo te relacionas con el amor, el cuerpo, el dinero y las decisiones, y empezar a actuar desde una identidad más firme y alineada.

Haz esta autoterapia con presencia y compromiso.  
Convertirte en reina no es imponerte.  
Es **volver a ocupar tu lugar**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('76854a765287448ab05d47330f003abe', '57955d86b173488f910cd4ecd5d8cbaf', 'e2b6829a437146a09ebe4603c3814129', '01. SANAR NO ES ELIMINAR, ES TRANSMUTAR', 10, '<p><strong>(Visualiza este video desde el día 74 al 76)</strong></p>
<p>Te han vendido una mentira elegante: que sanar es “quitar” el problema. Como si pudieras borrar el miedo, apagar el dolor o hacer que desaparezca lo que tu vida te mostró. Pero la energía no se destruye… se transforma.</p>
<p>En la Clase 3, Darren te entrega el paso a paso del método RENASER: sanar es transmutar. No es pelearte con la enfermedad, es convertirla en recurso. Aquí aparece el mapa que casi nadie usa completo: mente, emociones, energía y cuerpo. Si intentas “arreglar” solo uno, el sistema se rebela. Cambias pensamientos, pero el cuerpo guarda memoria. Haces dieta, pero la emoción sigue mandando. Buscas motivación, pero tu mente —tu “macaco”— te protege evitando lo que teme vivir. Y entonces vuelves a lo mismo.</p>
<p>El núcleo de esta clase es brutalmente simple: sentir. No pensar más. No aprender más. Sentir sin juicio, soltar, abrazarte y agradecer. Un ejercicio guiado que, si lo haces diario, deja de ser meditación y se vuelve reprogramación.</p>
<p>Si llegas al final, entiendes por qué 90 días pueden cambiar una vida… cuando hay sistema y compromiso.</p>
<p>Lo que no sientes, te controla.</p>
<p>RENASER</p>', '**(Visualiza este video desde el día 74 al 76)**

Te han vendido una mentira elegante: que sanar es “quitar” el problema. Como si pudieras borrar el miedo, apagar el dolor o hacer que desaparezca lo que tu vida te mostró. Pero la energía no se destruye… se transforma.

En la Clase 3, Darren te entrega el paso a paso del método RENASER: sanar es transmutar. No es pelearte con la enfermedad, es convertirla en recurso. Aquí aparece el mapa que casi nadie usa completo: mente, emociones, energía y cuerpo. Si intentas “arreglar” solo uno, el sistema se rebela. Cambias pensamientos, pero el cuerpo guarda memoria. Haces dieta, pero la emoción sigue mandando. Buscas motivación, pero tu mente —tu “macaco”— te protege evitando lo que teme vivir. Y entonces vuelves a lo mismo.

El núcleo de esta clase es brutalmente simple: sentir. No pensar más. No aprender más. Sentir sin juicio, soltar, abrazarte y agradecer. Un ejercicio guiado que, si lo haces diario, deja de ser meditación y se vuelve reprogramación.

Si llegas al final, entiendes por qué 90 días pueden cambiar una vida… cuando hay sistema y compromiso.

Lo que no sientes, te controla.

RENASER', 'YOUTUBE', 'https://www.youtube.com/watch?v=4xTRSwHZfv8&index=3', 'https://i.ytimg.com/vi/4xTRSwHZfv8/maxresdefault.jpg', 1691000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('34eb292ce86449058d8b1d1811012508', '57955d86b173488f910cd4ecd5d8cbaf', 'e2b6829a437146a09ebe4603c3814129', '02. AUDIOTERAPIA 11: NO SEAS MEDIOCRE', 11, '<p>Esta autoterapia no busca atacarte ni exigirte más. Busca confrontar el lugar donde te acostumbraste a sobrevivir, a conformarte y a negociar tu potencial.</p>
<p>Aquí se trabaja la diferencia entre paz y comodidad, entre descanso real y evasión, entre aceptarte y resignarte. No para compararte con nadie, sino para <strong>dejar de traicionarte en lo pequeño</strong>, que es donde nace la mediocridad.</p>
<p>Este espacio es para mirar con honestidad dónde estás apagando tu fuego, postergando decisiones y repitiendo lo mínimo cuando sabes que puedes más.</p>
<p>Haz esta autoterapia con presencia.<br />No seas mediocre no es una orden.<br />Es un <strong>recuerdo de lo que eres capaz</strong>.</p>', 'Esta autoterapia no busca atacarte ni exigirte más. Busca confrontar el lugar donde te acostumbraste a sobrevivir, a conformarte y a negociar tu potencial.

Aquí se trabaja la diferencia entre paz y comodidad, entre descanso real y evasión, entre aceptarte y resignarte. No para compararte con nadie, sino para **dejar de traicionarte en lo pequeño**, que es donde nace la mediocridad.

Este espacio es para mirar con honestidad dónde estás apagando tu fuego, postergando decisiones y repitiendo lo mínimo cuando sabes que puedes más.

Haz esta autoterapia con presencia.  
No seas mediocre no es una orden.  
Es un **recuerdo de lo que eres capaz**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('ac4e41179d6347f8ac4f4f87799b7ff2', '57955d86b173488f910cd4ecd5d8cbaf', '034b05bec38c4d20b868dcb0d468e6f4', '01. LA CONCIENCIA QUE TE ROMPE O TE LIBERA', 12, '<p><strong>(Visualiza este video desde el día 77 al 79)</strong></p>
<p>Puedes “liberar” emociones mil veces… y volver al mismo infierno. ¿Por qué? Porque soltar no sirve si tu conciencia sigue dormida. La vida repite la lección hasta que te atreves a verla.</p>
<p>En esta clase, Darren entra a lo más incómodo del método RENASER: tres pilares de conciencia que chocan con el pensamiento ordinario. Primero: no existe lo bueno ni lo malo, existe la totalidad. El juicio te parte en dos y alimenta ansiedad, culpa y control. Segundo: “nunca nadie te lastima”. No como justificación de lo que ocurrió, sino como llave para dejar de vivir desde el trauma como identidad. Tercero: a mayor miedo, mayor ego… y mayor drama. Tus historias, tu victimismo, tu necesidad de tener razón: todo delata lo que todavía no quieres trascender.</p>
<p>Luego viene el filtro brutal: tu contexto. La gente con la que te rodeas puede sostener tu cambio… o devolverte al mismo campo energético. Y se remata con una pregunta que separa destinos: ¿buscas solo consuelo, solo sentirte bien, o estás dispuesto a renacer de verdad?</p>
<p>Si miras esto completo, vas a entender por qué tu vida se repite… y cómo cortar el patrón.</p>
<p>La conciencia no te consuela. Te despierta.</p>
<p>RENASER</p>', '**(Visualiza este video desde el día 77 al 79)**

Puedes “liberar” emociones mil veces… y volver al mismo infierno. ¿Por qué? Porque soltar no sirve si tu conciencia sigue dormida. La vida repite la lección hasta que te atreves a verla.

En esta clase, Darren entra a lo más incómodo del método RENASER: tres pilares de conciencia que chocan con el pensamiento ordinario. Primero: no existe lo bueno ni lo malo, existe la totalidad. El juicio te parte en dos y alimenta ansiedad, culpa y control. Segundo: “nunca nadie te lastima”. No como justificación de lo que ocurrió, sino como llave para dejar de vivir desde el trauma como identidad. Tercero: a mayor miedo, mayor ego… y mayor drama. Tus historias, tu victimismo, tu necesidad de tener razón: todo delata lo que todavía no quieres trascender.

Luego viene el filtro brutal: tu contexto. La gente con la que te rodeas puede sostener tu cambio… o devolverte al mismo campo energético. Y se remata con una pregunta que separa destinos: ¿buscas solo consuelo, solo sentirte bien, o estás dispuesto a renacer de verdad?

Si miras esto completo, vas a entender por qué tu vida se repite… y cómo cortar el patrón.

La conciencia no te consuela. Te despierta.

RENASER', 'YOUTUBE', 'https://www.youtube.com/watch?v=dmNJ7Fq0KeQ&index=4', 'https://i.ytimg.com/vi/dmNJ7Fq0KeQ/maxresdefault.jpg', 967000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('6eebe25659bf4aeaa5509500d6522cdc', '57955d86b173488f910cd4ecd5d8cbaf', '04e6d703a66949968f8b900aeafbbd47', '01. SI PROCRASTINAS, NO ERES FLOJO, ERES HIPÓCRITA', 13, '<p><strong>(Visualiza este video desde el día 80 al 82)</strong></p>
<p>La procrastinación suele confundirse con pereza, pero en realidad es un conflicto interno entre lo que decimos que queremos y lo que realmente estamos dispuestos a hacer. Cada vez que postergamos una decisión importante, aparece un ciclo silencioso de evitación, culpa y justificación que debilita nuestro carácter.</p>
<p>En este video exploramos cómo la culpa puede convertirse en una herramienta de transformación cuando se usa con conciencia. Superar la procrastinación no depende solo de técnicas de productividad, sino de construir un carácter fuerte, aprender a decir no a las distracciones y ser honestos con nosotros mismos sobre lo que realmente queremos.</p>
<p>Al final, todo se reduce a una decisión: permitir que el miedo y la comodidad pesen más, o fortalecer el carácter para actuar con coherencia y determinación.</p>
<p>RENASER</p>', '**(Visualiza este video desde el día 80 al 82)**

La procrastinación suele confundirse con pereza, pero en realidad es un conflicto interno entre lo que decimos que queremos y lo que realmente estamos dispuestos a hacer. Cada vez que postergamos una decisión importante, aparece un ciclo silencioso de evitación, culpa y justificación que debilita nuestro carácter.

En este video exploramos cómo la culpa puede convertirse en una herramienta de transformación cuando se usa con conciencia. Superar la procrastinación no depende solo de técnicas de productividad, sino de construir un carácter fuerte, aprender a decir no a las distracciones y ser honestos con nosotros mismos sobre lo que realmente queremos.

Al final, todo se reduce a una decisión: permitir que el miedo y la comodidad pesen más, o fortalecer el carácter para actuar con coherencia y determinación.

RENASER', 'YOUTUBE', 'https://www.youtube.com/watch?v=cVaYTyfKJ9I&t=1s', 'https://i.ytimg.com/vi/cVaYTyfKJ9I/maxresdefault.jpg', 769000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('fc87fc405bb145afbea25d758700acb5', '01ac727ed506477883d5e015a0b792c1', '4780b840603b480eaa8fb704ac4612bf', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('bbbbdf72dfd841d39b9c5cb9d3aa2003', '01ac727ed506477883d5e015a0b792c1', '4780b840603b480eaa8fb704ac4612bf', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('9acad56515624af6bc9f640cb8c345c8', '57955d86b173488f910cd4ecd5d8cbaf', '5860cc47ea6047d1a4ee3555ecdb3d77', '01. EL ENGAÑO DE CREER QUE SABES', 15, '<p><strong>(Visualiza este video desde el día 86 al 90)</strong></p>
<p>Muchas veces creemos entender nuestros problemas, pero esa creencia puede convertirse en el mayor obstáculo para resolverlos. En este video se explora cómo la mente puede distorsionar nuestra percepción, enfocándose en el miedo y la supervivencia, haciéndonos creer que el problema está afuera cuando en realidad su raíz suele estar dentro de nosotros.</p>
<p>También se reflexiona sobre la importancia de identificar la raíz real de los conflictos, cuestionar los patrones heredados y desarrollar autoconocimiento profundo. La verdadera transformación ocurre cuando existe coherencia entre lo que pensamos, sentimos y hacemos.</p>
<p>Este mensaje invita a dejar de ver los problemas como una amenaza y comenzar a utilizarlos como una puerta hacia el crecimiento, entendiendo que muchas veces nuestro mayor potencial se encuentra precisamente en aquello que más nos incomoda enfrentar.</p>
<p>RENASER</p>', '**(Visualiza este video desde el día 86 al 90)**

Muchas veces creemos entender nuestros problemas, pero esa creencia puede convertirse en el mayor obstáculo para resolverlos. En este video se explora cómo la mente puede distorsionar nuestra percepción, enfocándose en el miedo y la supervivencia, haciéndonos creer que el problema está afuera cuando en realidad su raíz suele estar dentro de nosotros.

También se reflexiona sobre la importancia de identificar la raíz real de los conflictos, cuestionar los patrones heredados y desarrollar autoconocimiento profundo. La verdadera transformación ocurre cuando existe coherencia entre lo que pensamos, sentimos y hacemos.

Este mensaje invita a dejar de ver los problemas como una amenaza y comenzar a utilizarlos como una puerta hacia el crecimiento, entendiendo que muchas veces nuestro mayor potencial se encuentra precisamente en aquello que más nos incomoda enfrentar.

RENASER', 'YOUTUBE', 'https://www.youtube.com/watch?v=gaihd9Lv62U', 'https://i.ytimg.com/vi/gaihd9Lv62U/maxresdefault.jpg', 1106000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('2bd91c969412494998a36fffffeb3497', '403c35f8bd8d4f18a657d9edadb45b30', NULL, '¡BIENVENIDO(A) A ESTE UNIVERSO! 🌌', 0, '<p>La clave es el <strong>compromiso</strong>.<br />No puedes vivir en plenitud si sigues defendiendo los mismos hábitos, conductas y creencias que te mantienen estancado(a).<br />Lo sabes… pero aquí no basta con saberlo: aquí se trata de <strong>hacerlo real</strong>.</p>
<p>Si hoy sientes caos, dolor, sufrimiento o frustración, suele nacer de una de estas raíces:</p>
<ul><li><p><strong>Arrogancia:</strong> creer que ya lo sabes todo.</p></li><li><p><strong>Victimismo:</strong> quejarte, culpar y esperar que el mundo cambie por ti.</p></li><li><p><strong>Ignorancia:</strong> vivir desconectado(a) de tu verdad.</p></li></ul>
<p>Si realmente quieres cambiar, debes abrirte a <strong>aprender de nuevo</strong>. 🌱</p>
<hr />
<h2>RECUERDA</h2>
<p>Debes <strong>documentar cada día</strong> tu viaje de RENASER con un texto y su evidencia.<br />Las indicaciones están en el primer post.</p>
<p>Si no lo haces, perderás acceso a beneficios y acompañamiento terapéutico dentro de este espacio.</p>
<figure><img src="403c35f8bd8d4f18a657d9edadb45b30/assets/630a80089d2e-32fdfdde1ce3476d914f829298795818866118c2.bin" alt="ddh.png" loading="lazy" /></figure>', 'La clave es el **compromiso**.  
No puedes vivir en plenitud si sigues defendiendo los mismos hábitos, conductas y creencias que te mantienen estancado(a).  
Lo sabes… pero aquí no basta con saberlo: aquí se trata de **hacerlo real**.

Si hoy sientes caos, dolor, sufrimiento o frustración, suele nacer de una de estas raíces:

- **Arrogancia:** creer que ya lo sabes todo.
- **Victimismo:** quejarte, culpar y esperar que el mundo cambie por ti.
- **Ignorancia:** vivir desconectado(a) de tu verdad.

Si realmente quieres cambiar, debes abrirte a **aprender de nuevo**. 🌱

---

## RECUERDA

Debes **documentar cada día** tu viaje de RENASER con un texto y su evidencia.  
Las indicaciones están en el primer post.

Si no lo haces, perderás acceso a beneficios y acompañamiento terapéutico dentro de este espacio.

![ddh.png](403c35f8bd8d4f18a657d9edadb45b30/assets/630a80089d2e-32fdfdde1ce3476d914f829298795818866118c2.bin)', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('f341db35fc08442899ee45dee33e464d', '403c35f8bd8d4f18a657d9edadb45b30', NULL, 'Tierra, agua y fuego: el ritual para volver a ti', 1, '<p><br /></p>', NULL, 'YOUTUBE', 'https://www.youtube.com/watch?v=WJBVncaQKQE&index=9', 'https://i.ytimg.com/vi/WJBVncaQKQE/maxresdefault.jpg', 1306000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('55582904aa804ed29ac9cba1d736f5f1', '403c35f8bd8d4f18a657d9edadb45b30', '0531c880f9d8429ab94fc09105c645da', 'Clase 1 - ¿Amor Propio?', 2, '<h4>¿Y si el verdadero bloqueo eres tú? ✨</h4>
<p>Este no es un curso que busca consolarte, sino confrontarte. Es una experiencia directa, intensa y profundamente transformadora, diseñada para desactivar excusas y despertar tu poder interior. Aquí no vienes a aprender teorías: vienes a liberarte de los patrones mentales, la ansiedad y el caos emocional que te atan.</p>
<p>No es para todos. Es para quienes están listos para romper con todo y comenzar de verdad.¿Estás listo/a?</p>', '#### ¿Y si el verdadero bloqueo eres tú? ✨

Este no es un curso que busca consolarte, sino confrontarte. Es una experiencia directa, intensa y profundamente transformadora, diseñada para desactivar excusas y despertar tu poder interior. Aquí no vienes a aprender teorías: vienes a liberarte de los patrones mentales, la ansiedad y el caos emocional que te atan.

No es para todos. Es para quienes están listos para romper con todo y comenzar de verdad.¿Estás listo/a?', 'YOUTUBE', 'https://youtu.be/s87xo-gcUw8', 'https://i.ytimg.com/vi/s87xo-gcUw8/hqdefault.jpg', 689000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('f6c0c0c789064689869ef0dfb4bc1d43', '403c35f8bd8d4f18a657d9edadb45b30', '0531c880f9d8429ab94fc09105c645da', 'Ebook 1 - ¿Amor propio?', 3, '<p>Este ebook no fue creado para motivarte ni para decirte que todo estará bien. Fue creado para despertarte. Nada de lo que leerás aquí cambiará tu vida si tú no lo permites. Yo no voy a cambiar tu vida. TÚ LO HARÁS.</p>', 'Este ebook no fue creado para motivarte ni para decirte que todo estará bien. Fue creado para despertarte. Nada de lo que leerás aquí cambiará tu vida si tú no lo permites. Yo no voy a cambiar tu vida. TÚ LO HARÁS.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('ab37c394aba44ec08290c7e28fb33056', '403c35f8bd8d4f18a657d9edadb45b30', '8ee956ac61c14422b31adcecd684f36f', 'Clase 2 - La raíz de todo sufrimiento', 4, '<h4>Cuando dejas de ser víctima, empieza tu sanación.</h4>
<p>El sufrimiento no nace de lo que te sucede, sino de la forma en que lo interpretas.<br />Esta clase te guía a reconocer las raíces ocultas de tu dolor: los apegos, las expectativas y las resistencias que te mantienen atrapado/a en un ciclo de frustración y culpa.</p>
<p>También descubrirás una verdad incómoda: muchas veces, la raíz del sufrimiento es el <strong>victimismo</strong>, esa energía que te hace creer que no tienes poder sobre tu vida.</p>
<p>Aquí aprenderás a mirar dentro de ti con valentía, a soltar el control y a comprender que toda herida guarda una lección de liberación. </p>', '#### Cuando dejas de ser víctima, empieza tu sanación.

El sufrimiento no nace de lo que te sucede, sino de la forma en que lo interpretas.  
Esta clase te guía a reconocer las raíces ocultas de tu dolor: los apegos, las expectativas y las resistencias que te mantienen atrapado/a en un ciclo de frustración y culpa.

También descubrirás una verdad incómoda: muchas veces, la raíz del sufrimiento es el **victimismo**, esa energía que te hace creer que no tienes poder sobre tu vida.

Aquí aprenderás a mirar dentro de ti con valentía, a soltar el control y a comprender que toda herida guarda una lección de liberación.', 'YOUTUBE', 'https://youtu.be/QBJEp2iiZow', 'https://i.ytimg.com/vi/QBJEp2iiZow/hqdefault.jpg', 989000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('6189185b68d542ec991c54b6ce2aa4bf', '403c35f8bd8d4f18a657d9edadb45b30', '8ee956ac61c14422b31adcecd684f36f', 'Ebook 2 - La raíz de todo sufrimiento', 5, '<p>Cuando no comprendemos esto, buscamos culpables afuera, defendemos ideologías, nos dividimos y sufrimos. Este ebook no está diseñado para consolarte. Está diseñado para despertarte.</p>', 'Cuando no comprendemos esto, buscamos culpables afuera, defendemos ideologías, nos dividimos y sufrimos. Este ebook no está diseñado para consolarte. Está diseñado para despertarte.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('294dcf6b4cbc4a868994bfd340bbceef', '1724d86936ba4bf6b059500e26e4a775', 'bb9474712a624805a6e81954fad4c171', 'MASTERCLASS 3 | SEGURIDAD INQUEBRANTABLE', 16, '<p>Esta masterclass redefine la seguridad personal. No como control, dureza o exceso de confianza, sino como <strong>estabilidad interna que no depende de la aprobación, del resultado ni del entorno</strong>.</p>
<p>Aquí se revela por qué muchas personas se muestran fuertes pero viven internamente en alerta, y cómo construir una seguridad que <strong>no se quiebra cuando algo falla, alguien se va o el plan cambia</strong>. La seguridad inquebrantable no se impone: se estructura desde adentro.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para dejar de reaccionar desde el miedo al error o al rechazo.</p></li><li><p>Para sostener decisiones sin necesidad de validación constante.</p></li><li><p>Para actuar con firmeza sin rigidez emocional.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Confianza serena y consistente.</p></li><li><p>Mayor claridad al tomar decisiones.</p></li><li><p>Capacidad de avanzar sin traicionarte.</p></li></ul>
<p>Esta masterclass no te promete invulnerabilidad.<br />Te enseña a <strong>mantenerte estable incluso cuando tiembla</strong>.<br />Eso es seguridad inquebrantable.</p>', 'Esta masterclass redefine la seguridad personal. No como control, dureza o exceso de confianza, sino como **estabilidad interna que no depende de la aprobación, del resultado ni del entorno**.

Aquí se revela por qué muchas personas se muestran fuertes pero viven internamente en alerta, y cómo construir una seguridad que **no se quiebra cuando algo falla, alguien se va o el plan cambia**. La seguridad inquebrantable no se impone: se estructura desde adentro.

**¿Para qué sirve?**

- Para dejar de reaccionar desde el miedo al error o al rechazo.
- Para sostener decisiones sin necesidad de validación constante.
- Para actuar con firmeza sin rigidez emocional.

**Qué activa en ti**

- Confianza serena y consistente.
- Mayor claridad al tomar decisiones.
- Capacidad de avanzar sin traicionarte.

Esta masterclass no te promete invulnerabilidad.  
Te enseña a **mantenerte estable incluso cuando tiembla**.  
Eso es seguridad inquebrantable.', 'YOUTUBE', 'https://youtu.be/saOx0oxpZgQ', 'https://i.ytimg.com/vi/saOx0oxpZgQ/hqdefault.jpg', 5908000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('3594521b262f4830b104909b60fe6e93', '01ac727ed506477883d5e015a0b792c1', 'a723c60e230b42c1bcd303577c7fccb9', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('eba75a976ee9448086b34202557dbd79', '403c35f8bd8d4f18a657d9edadb45b30', '448446ec338346a58ccdb69613293044', 'Clase 3 - El secreto para vivir en plenitud', 6, '<h4><strong>La plenitud comienza cuando te abrazas por completo. </strong></h4>
<p>Vivir en plenitud no es evitar los problemas, sino aprender a mantener la paz en medio de ellos.<br />El secreto está en tu presencia: en cómo eliges responder, agradecer y crear, incluso cuando la vida no es perfecta. </p>
<p><strong>Abrázate.</strong> Entiende tus sentimientos sin juzgarlos.<br />La vida es bonita, incluso cuando duele, porque todo lo que sientes te acerca más a ti mismo/a. </p>
<p>Solo cuando sueltas la resistencia y confías en el flujo de la vida, comienza la verdadera plenitud. </p>', '#### **La plenitud comienza cuando te abrazas por completo. **

Vivir en plenitud no es evitar los problemas, sino aprender a mantener la paz en medio de ellos.  
El secreto está en tu presencia: en cómo eliges responder, agradecer y crear, incluso cuando la vida no es perfecta. 

**Abrázate.** Entiende tus sentimientos sin juzgarlos.  
La vida es bonita, incluso cuando duele, porque todo lo que sientes te acerca más a ti mismo/a. 

Solo cuando sueltas la resistencia y confías en el flujo de la vida, comienza la verdadera plenitud.', 'YOUTUBE', 'https://youtu.be/5ikZHefqYbE', 'https://i.ytimg.com/vi/5ikZHefqYbE/hqdefault.jpg', 1079000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('0837d3e6157441e7971f64388c6f78f8', '403c35f8bd8d4f18a657d9edadb45b30', '448446ec338346a58ccdb69613293044', 'Ebook 3 - El secreto para vivir en plenitud', 7, '<p>La mayoría de las personas no vive en plenitud.<br />Sobrevive.</p>
<p>Cargamos heridas, cicatrices emocionales y promesas internas que hicimos en momentos de dolor:<br />“Ya no vuelvo a sentir”,<br />“Ya no me enamoro”,<br />“Mejor no me ilusiono”.</p>
<p>Pero hay una verdad que pocos se atreven a aceptar:</p>
<p><strong>Vivir en plenitud es aprender a sentir.</strong></p>
<p>Este ebook no busca que evites el dolor.<br />Busca que <strong>dejes de huir de la vida</strong>.</p>', 'La mayoría de las personas no vive en plenitud.  
Sobrevive.

Cargamos heridas, cicatrices emocionales y promesas internas que hicimos en momentos de dolor:  
“Ya no vuelvo a sentir”,  
“Ya no me enamoro”,  
“Mejor no me ilusiono”.

Pero hay una verdad que pocos se atreven a aceptar:

**Vivir en plenitud es aprender a sentir.**

Este ebook no busca que evites el dolor.  
Busca que **dejes de huir de la vida**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('6ee2a6074d8848a4bf2724bdf18a518e', '403c35f8bd8d4f18a657d9edadb45b30', '5bbf0c2a89f24aa9a99d390040a6a301', 'Clase 4 - Sentir más para vivir más.', 8, '<p><strong>¿Y si el verdadero miedo fuera sentir de verdad?</strong><br /><br />Nos enseñaron a llenar el vacío con distracciones: el celular, la comida, el trabajo, las personas, los viajes. Pero nada de eso calma. Solo posterga.<br />El cuerpo siente, el alma grita… y tú corres.<br />No porque seas débil, sino porque nadie te enseñó a sostener lo que duele.<br />La ansiedad, la tristeza, la frustración no aparecen para destruirte. Aparecen cuando llevas demasiado tiempo evitándote.<br />El problema no es el dolor.<br />El problema es que aprendiste a huir de él.</p>
<p>Aquí se dice algo que incomoda: <strong>preferimos seguir vacíos antes que sentir</strong>, porque sentir implica responsabilidad, presencia y coraje.<br />Y eso no encaja en un mundo que vive anestesiado.</p>', '**¿Y si el verdadero miedo fuera sentir de verdad?**  
  
Nos enseñaron a llenar el vacío con distracciones: el celular, la comida, el trabajo, las personas, los viajes. Pero nada de eso calma. Solo posterga.  
El cuerpo siente, el alma grita… y tú corres.  
No porque seas débil, sino porque nadie te enseñó a sostener lo que duele.  
La ansiedad, la tristeza, la frustración no aparecen para destruirte. Aparecen cuando llevas demasiado tiempo evitándote.  
El problema no es el dolor.  
El problema es que aprendiste a huir de él.

Aquí se dice algo que incomoda: **preferimos seguir vacíos antes que sentir**, porque sentir implica responsabilidad, presencia y coraje.  
Y eso no encaja en un mundo que vive anestesiado.', 'YOUTUBE', 'https://youtu.be/tq5bLrMgvdc', 'https://i.ytimg.com/vi/tq5bLrMgvdc/hqdefault.jpg', 1024000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('6800e8d112064590a22b6a8920912131', '403c35f8bd8d4f18a657d9edadb45b30', '5bbf0c2a89f24aa9a99d390040a6a301', 'Ebook 4 - Sentir más para vivir más', 9, '<p>El mayor regalo que hemos recibido en esta vida no es el dinero, el éxito ni el reconocimiento.<br />El regalo más grande es <strong>sentir</strong>.</p>
<p>Sentir no es algo que la ciencia pueda medir por completo, porque el verdadero sentir no nace del cuerpo ni de la mente: <strong>nace del alma</strong>.</p>
<p>Solo cuando empezamos a sentir nuestro corazón y nuestra alma, podemos encontrarnos con nosotros mismos… y recién entonces, conectar con otros.</p>
<p>Este ebook es una invitación directa a dejar de huir y <strong>volver a sentir la vida</strong>.</p>', 'El mayor regalo que hemos recibido en esta vida no es el dinero, el éxito ni el reconocimiento.  
El regalo más grande es **sentir**.

Sentir no es algo que la ciencia pueda medir por completo, porque el verdadero sentir no nace del cuerpo ni de la mente: **nace del alma**.

Solo cuando empezamos a sentir nuestro corazón y nuestra alma, podemos encontrarnos con nosotros mismos… y recién entonces, conectar con otros.

Este ebook es una invitación directa a dejar de huir y **volver a sentir la vida**.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('e8defc68a2fb4fc19d3136b47d20a855', '403c35f8bd8d4f18a657d9edadb45b30', 'b3eac5f69f144d21aa8c522e082e8e8e', 'Ebook 5 - La clave de la transformación interna', 10, '<p>La frustración no es el problema.<br />El problema es <strong>quedarte atrapado en ella</strong>.</p>
<p>Las personas que nunca se frustran no están creciendo.<br />Pero cuando te frustras siempre por lo mismo, no estás creciendo más: estás repitiendo.</p>
<p>La raíz de ese estancamiento tiene un nombre claro: <strong>victimismo</strong>.<br />Y el victimismo no vive afuera. Vive en la <strong>mente</strong>.</p>
<p>Este ebook es una guía para aprender a <strong>guiar tu mente</strong>, dejar de ser controlado por ella y recuperar tu capacidad de sentir, crecer y vivir con plenitud.</p>', 'La frustración no es el problema.  
El problema es **quedarte atrapado en ella**.

Las personas que nunca se frustran no están creciendo.  
Pero cuando te frustras siempre por lo mismo, no estás creciendo más: estás repitiendo.

La raíz de ese estancamiento tiene un nombre claro: **victimismo**.  
Y el victimismo no vive afuera. Vive en la **mente**.

Este ebook es una guía para aprender a **guiar tu mente**, dejar de ser controlado por ella y recuperar tu capacidad de sentir, crecer y vivir con plenitud.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('eefcd9eb0ed2485bb06c80c805e1f7a1', '1724d86936ba4bf6b059500e26e4a775', 'bc43ecb0127e41f5a20d7ce9d53958ba', 'MASTERCLASS 7 |ESENCIA DINERO', 32, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/GKNG8-fIaPo', 'https://i.ytimg.com/vi/GKNG8-fIaPo/hqdefault.jpg', NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('bb8482ddd2b8410cb7cdc88da3a8f0b0', '01ac727ed506477883d5e015a0b792c1', 'a723c60e230b42c1bcd303577c7fccb9', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('67d9953446874eeca92adf10ef9693aa', '01ac727ed506477883d5e015a0b792c1', 'a723c60e230b42c1bcd303577c7fccb9', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('256edac907f74bd88d659a97a1ffa624', '01ac727ed506477883d5e015a0b792c1', '2c47a277dbd443bd88c916ce77b02ac4', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('35d777c933a347c1af4b9887c64ef52d', '01ac727ed506477883d5e015a0b792c1', '2c47a277dbd443bd88c916ce77b02ac4', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('2babaf6a8fad45eead80d3cbea8a649e', '403c35f8bd8d4f18a657d9edadb45b30', 'b3eac5f69f144d21aa8c522e082e8e8e', 'Clase 5 - La clave de la transformación interna', 11, '<p><strong>Tal vez no es la vida… es tu mente la que te gobierna.</strong><br /><br />La frustración no es mala. De hecho, es señal de crecimiento.<br />El problema empieza cuando repites el mismo dolor una y otra vez.<br />Ahí ya no estás creciendo: estás atrapado en el victimismo.</p>
<p>La raíz de gran parte del sufrimiento humano no está afuera.<br />Vive en la mente.<br />En la que se queja, culpa, huye, critica y manipula.<br />Esa mente que te dice “vete”, “no sigas”, “busca algo más fácil”,<br />“no sientas”, “no incomodes”, “no te expongas”.</p>
<p>Cuando no observas tu mente, ella decide por ti.<br />Cuando no la guías, te esclaviza.<br />Por eso sonríes por fuera y estás roto por dentro.<br />Por eso rechazas lo que te incomoda: personas, emociones, momentos… y partes de ti.</p>', '**Tal vez no es la vida… es tu mente la que te gobierna.**  
  
La frustración no es mala. De hecho, es señal de crecimiento.  
El problema empieza cuando repites el mismo dolor una y otra vez.  
Ahí ya no estás creciendo: estás atrapado en el victimismo.

La raíz de gran parte del sufrimiento humano no está afuera.  
Vive en la mente.  
En la que se queja, culpa, huye, critica y manipula.  
Esa mente que te dice “vete”, “no sigas”, “busca algo más fácil”,  
“no sientas”, “no incomodes”, “no te expongas”.

Cuando no observas tu mente, ella decide por ti.  
Cuando no la guías, te esclaviza.  
Por eso sonríes por fuera y estás roto por dentro.  
Por eso rechazas lo que te incomoda: personas, emociones, momentos… y partes de ti.', 'YOUTUBE', 'https://youtu.be/jptFYPpgMgw', 'https://i.ytimg.com/vi/jptFYPpgMgw/hqdefault.jpg', 762000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('493ca8bbde104a43b6083781c3cbb48d', '403c35f8bd8d4f18a657d9edadb45b30', '21ed69c8180a4ba49ba334ef02392192', 'Clase 6 - El renaser de tu niño(a) interior', 12, '<p><strong>¿Y si tu mayor bloqueo no fuera el miedo, sino el niño que dejaste solo?</strong><br /><br />Tu mayor bloqueo no es el miedo, es la parte de ti que fue herida y aprendió a esconderse. Este contenido revela que el verdadero amor propio no es control ni dureza, sino responsabilidad emocional: sentir, mirar el dolor y transformarlo en carácter. No apela a la nostalgia ni al victimismo, sino a una guía vivencial para dejar de abandonarte, romper patrones repetidos y recuperar tu poder al reconectar con tu niño interior.</p>', '**¿Y si tu mayor bloqueo no fuera el miedo, sino el niño que dejaste solo?**  
  
Tu mayor bloqueo no es el miedo, es la parte de ti que fue herida y aprendió a esconderse. Este contenido revela que el verdadero amor propio no es control ni dureza, sino responsabilidad emocional: sentir, mirar el dolor y transformarlo en carácter. No apela a la nostalgia ni al victimismo, sino a una guía vivencial para dejar de abandonarte, romper patrones repetidos y recuperar tu poder al reconectar con tu niño interior.', 'YOUTUBE', 'https://youtu.be/Zi3chTIYJMs', 'https://i.ytimg.com/vi/Zi3chTIYJMs/hqdefault.jpg', 813000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('a3d186d463a844b59674eb917925a323', '403c35f8bd8d4f18a657d9edadb45b30', '21ed69c8180a4ba49ba334ef02392192', 'Ebook 6 - El Renaser de tu niño Interior', 13, '<p>Mientras sigas viéndote como víctima, tu mente seguirá controlando tu vida, sembrando miedos, bloqueando tu corazón y apagando tu capacidad de disfrutar.</p>
<p>Este ebook es una invitación profunda a volver a ti, a tu niño, a tu esencia, y a recuperar el amor propio desde la responsabilidad y la conciencia.</p>', 'Mientras sigas viéndote como víctima, tu mente seguirá controlando tu vida, sembrando miedos, bloqueando tu corazón y apagando tu capacidad de disfrutar.

Este ebook es una invitación profunda a volver a ti, a tu niño, a tu esencia, y a recuperar el amor propio desde la responsabilidad y la conciencia.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('e0916d09394a45088c60ae3557d9a9a7', '403c35f8bd8d4f18a657d9edadb45b30', 'e58dff58897242aaba3047a9db5a9f62', 'Clase 7 - El verdadero amor propio', 14, '<p><strong>¿Y si el amor propio no fuera paz, sino verdad?</strong><br /><br />El amor propio no es sentirte bien todo el tiempo, es atreverte a verte con verdad. Este módulo de RENASER confronta la idea superficial del bienestar constante y revela que amarte también implica incomodidad, rabia y miedo. No busca agradar, sino despertar conciencia: mirar tu oscuridad para recuperar tu fuerza, dejar de depender de la opinión ajena y romper ciclos repetidos. </p>
<p>Porque <strong>amarte no empieza siendo amable</strong>.<br />Empieza siendo honesto.</p>', '**¿Y si el amor propio no fuera paz, sino verdad?**  
  
El amor propio no es sentirte bien todo el tiempo, es atreverte a verte con verdad. Este módulo de RENASER confronta la idea superficial del bienestar constante y revela que amarte también implica incomodidad, rabia y miedo. No busca agradar, sino despertar conciencia: mirar tu oscuridad para recuperar tu fuerza, dejar de depender de la opinión ajena y romper ciclos repetidos. 

Porque **amarte no empieza siendo amable**.  
Empieza siendo honesto.', 'YOUTUBE', 'https://youtu.be/uahHuheNAWM', 'https://i.ytimg.com/vi/uahHuheNAWM/hqdefault.jpg', 779000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('4ce53de0f2404ac89b76556e7c58e02a', '403c35f8bd8d4f18a657d9edadb45b30', 'e58dff58897242aaba3047a9db5a9f62', 'Ebook 7 - El verdadero Amor Propio', 15, '<p>Hoy se habla mucho de amor propio, pero se vive poco.<br />La mayoría confunde el amor propio con sentirse bien todo el tiempo, con ser complaciente, con evitar el dolor o la incomodidad.</p>
<p>Pero el amor propio real no es cómodo.<br />Es <strong>profundo, sólido y transformador</strong>.</p>
<p>Este ebook es una invitación a volver a la esencia del amor propio:<br /><strong>el autoconocimiento</strong>, la honestidad contigo mismo y la capacidad de mirarte sin huir.</p>', 'Hoy se habla mucho de amor propio, pero se vive poco.  
La mayoría confunde el amor propio con sentirse bien todo el tiempo, con ser complaciente, con evitar el dolor o la incomodidad.

Pero el amor propio real no es cómodo.  
Es **profundo, sólido y transformador**.

Este ebook es una invitación a volver a la esencia del amor propio:  
**el autoconocimiento**, la honestidad contigo mismo y la capacidad de mirarte sin huir.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('051168f196fb4ff08371ea180a4b577b', '403c35f8bd8d4f18a657d9edadb45b30', 'e58dff58897242aaba3047a9db5a9f62', 'Clase 8 - Podcast para 10 KM', 16, '<p>En esta clase escucha 5 podcast durante todo el día. si puedes mas, será excelente. A continuación los enlaces. Para ello, debes de enfocarte en sentirlo, y estudiarlos. aqui encontraras lecciones que equivalen a 3 años de terapia</p>
<p>Podcast 1. La clave de la libertad de tu ser - Principio de imperfeccion PODCAST</p>
<p><a href="https://open.spotify.com/episode/7qo79D2TqRczNyGs70Awnf?si=ZKzuCYA3Qi6cp_RZTdTcMg&amp;t=1220" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/7qo79D2TqRczNyGs70Awnf?si=ZKzuCYA3Qi6cp_RZTdTcMg&amp;t=1220</a></p>
<p><br /></p>
<p>Podcast 2. Consciencia detrás de los problemas</p>
<p><a href="https://open.spotify.com/episode/0XOPc3EwepLM9jmdWM8Ynv?si=OQkD_SDTRxOBY0MpuyLncg" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/0XOPc3EwepLM9jmdWM8Ynv?si=OQkD_SDTRxOBY0MpuyLncg</a></p>
<p>Podcast 3. Tus padres te condenaron?</p>
<p><a href="https://open.spotify.com/episode/4UyQtUFkibtJDZV1v2FSXu?si=-8rs74h7Q0eiIfmIDd92tw" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/4UyQtUFkibtJDZV1v2FSXu?si=-8rs74h7Q0eiIfmIDd92tw</a></p>
<p>Podcast 4. Como superas los miedos y la ansiedad?</p>
<p><a href="https://open.spotify.com/episode/7yYc9wbB85iOF5vxZB7dgH?si=1yn3uxBKQt2dpc8jAYLPlA" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/7yYc9wbB85iOF5vxZB7dgH?si=1yn3uxBKQt2dpc8jAYLPlA</a></p>
<p><a href="https://open.spotify.com/episode/5oE9rg1k7j9CwiiorvyUvG?si=nDLA6z4UTiWfwFXniYP9IA" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/5oE9rg1k7j9CwiiorvyUvG?si=nDLA6z4UTiWfwFXniYP9IA</a></p>
<p><a href="https://open.spotify.com/episode/6yYb4SxDGp4NQy00W3d5e7?si=V79KKJ5iRrez_nr0QC5Ihw" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/6yYb4SxDGp4NQy00W3d5e7?si=V79KKJ5iRrez_nr0QC5Ihw</a></p>
<p>Podcast 5. Olvidaste tu esencia femenina</p>
<p><a href="https://open.spotify.com/episode/41Asl2vNqs0RnT9hY9dT3E?si=bGeBkMXVTlue9-2t5x7O9Q" target="_blank" rel="noopener noreferrer">https://open.spotify.com/episode/41Asl2vNqs0RnT9hY9dT3E?si=bGeBkMXVTlue9-2t5x7O9Q</a></p>
<p>30 Podcast íntimos:</p>
<p><a href="https://open.spotify.com/show/2Y6aHhlDfNYuQcpeU3Dov0?si=BY6cQyDlRLqkK1KurxAHmw" target="_blank" rel="noopener noreferrer">https://open.spotify.com/show/2Y6aHhlDfNYuQcpeU3Dov0?si=BY6cQyDlRLqkK1KurxAHmw</a></p>', 'En esta clase escucha 5 podcast durante todo el día. si puedes mas, será excelente. A continuación los enlaces. Para ello, debes de enfocarte en sentirlo, y estudiarlos. aqui encontraras lecciones que equivalen a 3 años de terapia

Podcast 1. La clave de la libertad de tu ser - Principio de imperfeccion PODCAST

[https://open.spotify.com/episode/7qo79D2TqRczNyGs70Awnf?si=ZKzuCYA3Qi6cp_RZTdTcMg&t=1220](https://open.spotify.com/episode/7qo79D2TqRczNyGs70Awnf?si=ZKzuCYA3Qi6cp_RZTdTcMg&t=1220)

Podcast 2. Consciencia detrás de los problemas

[https://open.spotify.com/episode/0XOPc3EwepLM9jmdWM8Ynv?si=OQkD_SDTRxOBY0MpuyLncg](https://open.spotify.com/episode/0XOPc3EwepLM9jmdWM8Ynv?si=OQkD_SDTRxOBY0MpuyLncg)

Podcast 3. Tus padres te condenaron?

[https://open.spotify.com/episode/4UyQtUFkibtJDZV1v2FSXu?si=-8rs74h7Q0eiIfmIDd92tw](https://open.spotify.com/episode/4UyQtUFkibtJDZV1v2FSXu?si=-8rs74h7Q0eiIfmIDd92tw)

Podcast 4. Como superas los miedos y la ansiedad?

[https://open.spotify.com/episode/7yYc9wbB85iOF5vxZB7dgH?si=1yn3uxBKQt2dpc8jAYLPlA](https://open.spotify.com/episode/7yYc9wbB85iOF5vxZB7dgH?si=1yn3uxBKQt2dpc8jAYLPlA)

[https://open.spotify.com/episode/5oE9rg1k7j9CwiiorvyUvG?si=nDLA6z4UTiWfwFXniYP9IA](https://open.spotify.com/episode/5oE9rg1k7j9CwiiorvyUvG?si=nDLA6z4UTiWfwFXniYP9IA)

[https://open.spotify.com/episode/6yYb4SxDGp4NQy00W3d5e7?si=V79KKJ5iRrez_nr0QC5Ihw](https://open.spotify.com/episode/6yYb4SxDGp4NQy00W3d5e7?si=V79KKJ5iRrez_nr0QC5Ihw)

Podcast 5. Olvidaste tu esencia femenina

[https://open.spotify.com/episode/41Asl2vNqs0RnT9hY9dT3E?si=bGeBkMXVTlue9-2t5x7O9Q](https://open.spotify.com/episode/41Asl2vNqs0RnT9hY9dT3E?si=bGeBkMXVTlue9-2t5x7O9Q)

30 Podcast íntimos:

[https://open.spotify.com/show/2Y6aHhlDfNYuQcpeU3Dov0?si=BY6cQyDlRLqkK1KurxAHmw](https://open.spotify.com/show/2Y6aHhlDfNYuQcpeU3Dov0?si=BY6cQyDlRLqkK1KurxAHmw)', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('b9b6799fe5634b57b659549a9f7fd551', 'cfc4c4334474498b9f6c9c47800fdecf', '5ab7f4a436974f96974e67fc7b750bda', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('a51242dde63945b1ac6a1cdaeae62726', '5ae472cf6b224d189c5dba48a22b4c09', 'eb93b5a309e54ff184198573d6d6efae', 'Clase 8 - El mayor regalo de tu alma', 0, '<p><strong>Nadie vendrá a salvarte (y esa es tu libertad).</strong><br /><br />¿Y si la persona que tanto esperas que te rescate es, en realidad, la que ves cada mañana en el espejo? Pasamos la existencia mendigando migajas de atención, prefiriendo una compañía vacía antes que enfrentar el sagrado silencio de nuestra propia presencia.</p>
<p>El conflicto estalla cuando comprendes que tu hambre de afuera es solo el reflejo de tu abandono interno. Tocamos la herida del rechazo para que dejes de ser un satélite de otros y te conviertas en tu propio centro. Mira este video completo; esto cambiará tu forma de amar y de habitar tu soledad.</p>', '**Nadie vendrá a salvarte (y esa es tu libertad).**  
  
¿Y si la persona que tanto esperas que te rescate es, en realidad, la que ves cada mañana en el espejo? Pasamos la existencia mendigando migajas de atención, prefiriendo una compañía vacía antes que enfrentar el sagrado silencio de nuestra propia presencia.

El conflicto estalla cuando comprendes que tu hambre de afuera es solo el reflejo de tu abandono interno. Tocamos la herida del rechazo para que dejes de ser un satélite de otros y te conviertas en tu propio centro. Mira este video completo; esto cambiará tu forma de amar y de habitar tu soledad.', 'YOUTUBE', 'https://youtu.be/yCAG9FpTu64', 'https://i.ytimg.com/vi/yCAG9FpTu64/hqdefault.jpg', 1088000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('56edec7687324e4982a828c45ba33174', '5ae472cf6b224d189c5dba48a22b4c09', 'f49b9e9fb0af41b381fcb93b0f8c12c1', 'Clase 9 - Las deudas que te condenan el alma', 1, '<p><strong>El fin del sufrimiento: deja de pelear contigo.</strong><br /><br />¿Es el dolor lo que te detiene o la historia que te repites sobre él cada mañana? Vivimos atrapados en un laberinto de espejos, identificándonos con el reflejo de una herida que ya no debería sangrar, pero que alimentamos con nuestra propia atención.</p>
<p>El conflicto nace cuando el observador se confunde con lo observado, perdiendo su esencia en el drama del pensamiento. Tocamos la herida del &quot;yo&quot; para que descubras que el silencio no es ausencia, sino la plenitud absoluta de tu ser. Mira este video completo; esto cambiará tu forma de ver tu mente y la paz que siempre estuvo ahí.</p>
<p><br /></p>', '**El fin del sufrimiento: deja de pelear contigo.**  
  
¿Es el dolor lo que te detiene o la historia que te repites sobre él cada mañana? Vivimos atrapados en un laberinto de espejos, identificándonos con el reflejo de una herida que ya no debería sangrar, pero que alimentamos con nuestra propia atención.

El conflicto nace cuando el observador se confunde con lo observado, perdiendo su esencia en el drama del pensamiento. Tocamos la herida del "yo" para que descubras que el silencio no es ausencia, sino la plenitud absoluta de tu ser. Mira este video completo; esto cambiará tu forma de ver tu mente y la paz que siempre estuvo ahí.', 'YOUTUBE', 'https://youtu.be/QOfAyzHuELo', 'https://i.ytimg.com/vi/QOfAyzHuELo/hqdefault.jpg', 501000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('9f80fa1f08784531a1d74723b00a8f44', '5ae472cf6b224d189c5dba48a22b4c09', 'c43eecbdd538494fb8eb038a6158a04e', 'Clase 10 - Reconstruye tu identidad', 2, '<p><strong>Deja de correr: lo que buscas está bajo tus pies.</strong><br /><br />¿Cuánto tiempo más vas a posponer tu vida en nombre de una meta que siempre se desplaza? Corremos tras un horizonte que prometía paz, pero solo encontramos un hambre que nunca se sacia y un cansancio que ya es parte del alma.</p>
<p>El conflicto es la herida de la eterna insatisfacción; ese abismo que intentas llenar con logros, objetos o personas, ignorando que el vacío es el espacio donde reside tu verdadera esencia. Debes ver este video completo; esto cambiará tu forma de desear y te devolverá el poder de habitar el ahora.</p>', '**Deja de correr: lo que buscas está bajo tus pies.**  
  
¿Cuánto tiempo más vas a posponer tu vida en nombre de una meta que siempre se desplaza? Corremos tras un horizonte que prometía paz, pero solo encontramos un hambre que nunca se sacia y un cansancio que ya es parte del alma.

El conflicto es la herida de la eterna insatisfacción; ese abismo que intentas llenar con logros, objetos o personas, ignorando que el vacío es el espacio donde reside tu verdadera esencia. Debes ver este video completo; esto cambiará tu forma de desear y te devolverá el poder de habitar el ahora.', 'YOUTUBE', 'https://youtu.be/q9xwJzsBdDw', 'https://i.ytimg.com/vi/q9xwJzsBdDw/hqdefault.jpg', 748000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('3e485361c9d84b54ba48e48a79c475f6', '5ae472cf6b224d189c5dba48a22b4c09', '78c0ac4dacfb4d98bdb480f55572520c', 'Clase 11 - El Ego que te limita, victimismo', 3, '<p><strong>No eres quien crees: la verdad sobre tu origen.</strong><br /><br />¿Te has detenido a observar qué parte de ti permanece inmutable mientras todo a tu alrededor se desmorona? Pasamos la vida protegiendo un cuerpo y una historia, olvidando que somos el espacio infinito donde ambos suceden.</p>
<p>El conflicto central es la herida de la finitud: ese terror a la nada que nos obliga a aferrarnos a lo transitorio. Aquí exploramos el silencio que precede a tus palabras y la luz que brilla antes de tus pensamientos. Míralo completo; esto cambiará tu forma de ver la muerte, el tiempo y tu propia presencia en este universo.</p>', '**No eres quien crees: la verdad sobre tu origen.**  
  
¿Te has detenido a observar qué parte de ti permanece inmutable mientras todo a tu alrededor se desmorona? Pasamos la vida protegiendo un cuerpo y una historia, olvidando que somos el espacio infinito donde ambos suceden.

El conflicto central es la herida de la finitud: ese terror a la nada que nos obliga a aferrarnos a lo transitorio. Aquí exploramos el silencio que precede a tus palabras y la luz que brilla antes de tus pensamientos. Míralo completo; esto cambiará tu forma de ver la muerte, el tiempo y tu propia presencia en este universo.', 'YOUTUBE', 'https://youtu.be/YSA1_mCzyFA', 'https://i.ytimg.com/vi/YSA1_mCzyFA/hqdefault.jpg', 372000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('3c34da74e275404e92c082c347701920', '5ae472cf6b224d189c5dba48a22b4c09', 'd306a73b19ab422ab2f86e82b30be1a0', 'Clase 12 - El poder de tus intensiones', 4, '<p><strong>La trampa de querer controlarlo todo en tu vida.</strong><br /><br />¿Qué pasaría si hoy dejaras de luchar contra la marea y permitieras que el abismo te alcanzara? Vivimos en una guerra agotadora, protegiendo tesoros de humo y persiguiendo sombras que llamamos &quot;éxito&quot;, mientras la vida real sucede en la rendición.</p>
<p>El conflicto es el pánico a la pérdida, esa herida que supura cada vez que el destino nos quita lo que creíamos poseer. Aquí descubrimos que solo aquel que no tiene nada que perder lo ha ganado todo. Mira este video completo; esto cambiará tu forma de ver tus fracasos y tu resistencia a la vida.</p>', '**La trampa de querer controlarlo todo en tu vida.**  
  
¿Qué pasaría si hoy dejaras de luchar contra la marea y permitieras que el abismo te alcanzara? Vivimos en una guerra agotadora, protegiendo tesoros de humo y persiguiendo sombras que llamamos "éxito", mientras la vida real sucede en la rendición.

El conflicto es el pánico a la pérdida, esa herida que supura cada vez que el destino nos quita lo que creíamos poseer. Aquí descubrimos que solo aquel que no tiene nada que perder lo ha ganado todo. Mira este video completo; esto cambiará tu forma de ver tus fracasos y tu resistencia a la vida.', 'YOUTUBE', 'https://youtu.be/T7rNU-eJl7Q', 'https://i.ytimg.com/vi/T7rNU-eJl7Q/hqdefault.jpg', 499000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('b3ee96291f5d4ceba01919f35722b2a6', 'cfc4c4334474498b9f6c9c47800fdecf', '5ab7f4a436974f96974e67fc7b750bda', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('409a8e1bc2954c5bb55ec37203d59e91', '5ae472cf6b224d189c5dba48a22b4c09', 'a1b433c5b44f4436974b59e8bd8c663c', 'Clase 13 - Liberar tu alma te dará plenitud', 5, '<p><strong>La decepción es lo mejor que puede pasarte hoy.</strong><br /><br />¿Preferirías una mentira que te mantenga a salvo o una verdad que te rompa en mil pedazos? Sostenemos realidades ficticias por el pánico a descubrir que el suelo bajo nuestros pies nunca estuvo ahí.</p>
<p>El conflicto central es la agonía de la desilusión; esa herida necesaria que desmantela el teatro de tu vida para que la realidad finalmente respire. No es un proceso amable, es un incendio que consume lo falso para salvar lo eterno. Míralo completo; esto cambiará tu forma de abrazar el colapso y te enseñará a caminar entre las cenizas de tu antiguo yo.</p>', '**La decepción es lo mejor que puede pasarte hoy.**  
  
¿Preferirías una mentira que te mantenga a salvo o una verdad que te rompa en mil pedazos? Sostenemos realidades ficticias por el pánico a descubrir que el suelo bajo nuestros pies nunca estuvo ahí.

El conflicto central es la agonía de la desilusión; esa herida necesaria que desmantela el teatro de tu vida para que la realidad finalmente respire. No es un proceso amable, es un incendio que consume lo falso para salvar lo eterno. Míralo completo; esto cambiará tu forma de abrazar el colapso y te enseñará a caminar entre las cenizas de tu antiguo yo.', 'YOUTUBE', 'https://youtu.be/XgviJUKfcQQ', 'https://i.ytimg.com/vi/XgviJUKfcQQ/hqdefault.jpg', 1041000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('59edf170634344d4965e4b7ca8588b07', '5ae472cf6b224d189c5dba48a22b4c09', '4ab9a52beb954c5cbd3558e15d5502ba', 'Clase 14 - Porque te divides, eres una totalidad', 6, '<p><strong>Deja de buscarte: nunca te has perdido realmente.</strong><br /><br />¿Cuánto tiempo más vas a seguir persiguiendo una versión &quot;iluminada&quot; de ti mismo que nunca llega? La búsqueda constante es la trampa perfecta; un laberinto donde el ego se disfraza de espiritualidad para evitar ser descubierto.</p>
<p>El conflicto radica en la herida de la insuficiencia, ese susurro que te dice que aún te falta algo para estar completo. Aquí revelamos que la sanación no es una meta, sino el cese de toda guerra interna. Mira este video completo; esto cambiará tu forma de ver tu proceso y te permitirá, por fin, descansar en lo que ya eres.</p>', '**Deja de buscarte: nunca te has perdido realmente.**  
  
¿Cuánto tiempo más vas a seguir persiguiendo una versión "iluminada" de ti mismo que nunca llega? La búsqueda constante es la trampa perfecta; un laberinto donde el ego se disfraza de espiritualidad para evitar ser descubierto.

El conflicto radica en la herida de la insuficiencia, ese susurro que te dice que aún te falta algo para estar completo. Aquí revelamos que la sanación no es una meta, sino el cese de toda guerra interna. Mira este video completo; esto cambiará tu forma de ver tu proceso y te permitirá, por fin, descansar en lo que ya eres.', 'YOUTUBE', 'https://youtu.be/FFdcrz7-CDo', 'https://i.ytimg.com/vi/FFdcrz7-CDo/hqdefault.jpg', 579000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('f6db665d5409415eb33e597e57dda494', '5ae472cf6b224d189c5dba48a22b4c09', '4f84a9a3013f441cae60942e56f93941', 'Clase 15 - Conviértete en un artista de Dios', 7, '<p><strong>¿Te amas lo suficiente como para estar a solas contigo?</strong><br /><br />¿Es soledad lo que sientes, o es el terror de encontrarte con el desconocido que vive en tu espejo? Pasamos la vida huyendo del silencio, llenando vacíos con presencias que solo aumentan nuestra orfandad espiritual.</p>
<p>El conflicto estalla cuando el mundo se retira y quedas tú, despojado de títulos y aplausos. Tocamos la herida del abandono para que comprendas que solo en la soledad más cruda se forja la integridad del ser. Debes ver este video completo; esto cambiará tu forma de habitar tus espacios de vacío y silencio.</p>', '**¿Te amas lo suficiente como para estar a solas contigo?**  
  
¿Es soledad lo que sientes, o es el terror de encontrarte con el desconocido que vive en tu espejo? Pasamos la vida huyendo del silencio, llenando vacíos con presencias que solo aumentan nuestra orfandad espiritual.

El conflicto estalla cuando el mundo se retira y quedas tú, despojado de títulos y aplausos. Tocamos la herida del abandono para que comprendas que solo en la soledad más cruda se forja la integridad del ser. Debes ver este video completo; esto cambiará tu forma de habitar tus espacios de vacío y silencio.', 'YOUTUBE', 'https://youtu.be/T9cZbun-W-Y', 'https://i.ytimg.com/vi/T9cZbun-W-Y/hqdefault.jpg', 1090000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('c12a7a371832475892167ee12d177cbe', '5ae472cf6b224d189c5dba48a22b4c09', '65e817cefccc4a4db2c1f9832502168f', 'Clase 16 - Propósito de expresar tu alma', 8, '<p><strong>La trampa de buscarte en un mundo que no eres.</strong><br /><br />¿Qué sucede cuando dejas de contar la historia de quién eres y te quedas solo con el silencio? Nos aterra la nada porque hemos construido un imperio sobre cimientos de arena: el nombre, el prestigio y el pasado.</p>
<p>El conflicto central es la angustia de soltar el control en un universo que no puedes dominar. Tocamos la herida de la insignificancia para que descubras que, al dejar de ser &quot;alguien&quot;, finalmente puedes serlo todo. Mira este video completo; esto cambiará tu forma de ver tu identidad y el infinito que habita en tu pecho.</p>', '**La trampa de buscarte en un mundo que no eres.**  
  
¿Qué sucede cuando dejas de contar la historia de quién eres y te quedas solo con el silencio? Nos aterra la nada porque hemos construido un imperio sobre cimientos de arena: el nombre, el prestigio y el pasado.

El conflicto central es la angustia de soltar el control en un universo que no puedes dominar. Tocamos la herida de la insignificancia para que descubras que, al dejar de ser "alguien", finalmente puedes serlo todo. Mira este video completo; esto cambiará tu forma de ver tu identidad y el infinito que habita en tu pecho.', 'YOUTUBE', 'https://youtu.be/wBMn0YKc1bk', 'https://i.ytimg.com/vi/wBMn0YKc1bk/hqdefault.jpg', 627000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('d4add3698f4c442ea339e8939469ca8f', '5ae472cf6b224d189c5dba48a22b4c09', 'faa83a73f3dd4971b6448d87151f5aab', 'Clase 17 - El silencio que te condenó', 9, '<p><strong>La muerte necesaria que nadie se atreve a vivir.</strong><br /><br />¿Es posible que el dolor que te quiebra sea en realidad el martillo que rompe tu celda? Nos aferramos a los escombros de lo que conocemos por miedo a quedar desnudos frente a la inmensidad de nuestra propia existencia.</p>
<p>El conflicto no es la caída, sino la desesperación por volver a ser el de antes. Tocamos la herida de la pérdida para recordarte que nada de lo que es real puede ser destruido. Míralo completo; esto cambiará tu forma de ver tus finales y te mostrará el camino hacia lo que nunca muere.</p>', '**La muerte necesaria que nadie se atreve a vivir.**  
  
¿Es posible que el dolor que te quiebra sea en realidad el martillo que rompe tu celda? Nos aferramos a los escombros de lo que conocemos por miedo a quedar desnudos frente a la inmensidad de nuestra propia existencia.

El conflicto no es la caída, sino la desesperación por volver a ser el de antes. Tocamos la herida de la pérdida para recordarte que nada de lo que es real puede ser destruido. Míralo completo; esto cambiará tu forma de ver tus finales y te mostrará el camino hacia lo que nunca muere.', 'YOUTUBE', 'https://youtu.be/X9pC_2X_zsg', 'https://i.ytimg.com/vi/X9pC_2X_zsg/hqdefault.jpg', 557000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('ff79abad7dda473d85974ea55d453a0a', 'cfc4c4334474498b9f6c9c47800fdecf', 'd459f9b1db9248c9a9a7eb4d74ebcd51', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('3cb1c0e7e73641fc90e9e69c00396207', 'cfc4c4334474498b9f6c9c47800fdecf', 'd459f9b1db9248c9a9a7eb4d74ebcd51', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('58e3cbd5d92f4a22accf56da700e9b15', '5ae472cf6b224d189c5dba48a22b4c09', 'afe63d302ef4400f80e24cdd7ee3da1a', 'Clase 18 - Nos callaron los que más nos amaron', 10, '<p><strong>La belleza oculta que solo aparece en tu oscuridad.</strong><br /><br />¿Por qué huyes de la tormenta si es ella quien viene a limpiar tus raíces? Pasamos la vida persiguiendo una luz artificial, negando que el brillo más puro solo se gesta en las entrañas de la oscuridad que tanto evitas.</p>
<p>El conflicto no es el dolor, sino la resistencia a sentirlo. Tocamos la herida de la fragmentación para enseñarte a abrazar tus pedazos rotos como parte de un paisaje sagrado. Mira este video completo; esto cambiará tu forma de ver tus sombras y la paz que nace de la aceptación total.</p>', '**La belleza oculta que solo aparece en tu oscuridad.**  
  
¿Por qué huyes de la tormenta si es ella quien viene a limpiar tus raíces? Pasamos la vida persiguiendo una luz artificial, negando que el brillo más puro solo se gesta en las entrañas de la oscuridad que tanto evitas.

El conflicto no es el dolor, sino la resistencia a sentirlo. Tocamos la herida de la fragmentación para enseñarte a abrazar tus pedazos rotos como parte de un paisaje sagrado. Mira este video completo; esto cambiará tu forma de ver tus sombras y la paz que nace de la aceptación total.', 'YOUTUBE', 'https://youtu.be/V2SdrC7TuQo', 'https://i.ytimg.com/vi/V2SdrC7TuQo/hqdefault.jpg', 425000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('cb4f8b8a1d764112b0079262a24bbb1a', '5ae472cf6b224d189c5dba48a22b4c09', 'd6a4afa91de642ee99b73eed48b592ee', 'Clase 19 - El amor real del Renaser', 11, '<p><strong>La trampa mental que te impide ser feliz hoy.</strong><br /><br />¿Te has preguntado quién es el que escucha cuando hablas contigo mismo en la oscuridad? Vivimos esclavizados por una voz que no calla, una narrativa de miedo que nos aleja del único momento real que existe.</p>
<p>El conflicto no está en tus circunstancias, sino en la herida de creer que eres esa corriente incesante de pensamientos. Aquí desnudamos la mente para revelar el espacio sagrado que hay detrás del ruido. Míralo completo; esto cambiará tu forma de ver tu propia conciencia y la paz que te pertenece.</p>', '**La trampa mental que te impide ser feliz hoy.**  
  
¿Te has preguntado quién es el que escucha cuando hablas contigo mismo en la oscuridad? Vivimos esclavizados por una voz que no calla, una narrativa de miedo que nos aleja del único momento real que existe.

El conflicto no está en tus circunstancias, sino en la herida de creer que eres esa corriente incesante de pensamientos. Aquí desnudamos la mente para revelar el espacio sagrado que hay detrás del ruido. Míralo completo; esto cambiará tu forma de ver tu propia conciencia y la paz que te pertenece.', 'YOUTUBE', 'https://youtu.be/QJMR6gna7LI', 'https://i.ytimg.com/vi/QJMR6gna7LI/hqdefault.jpg', 543000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('764364fb682a411ca5cfe6148e08e40b', '5ae472cf6b224d189c5dba48a22b4c09', '1a9720546f594346a1fbfd230c98fe0c', 'Clase 20 - Crea abundancia desde tu integridad', 12, '<p><strong>La muerte de tu ego es tu único camino.</strong><br /><br />¿Qué queda de ti cuando el ruido del mundo finalmente se apaga y el silencio te devora? Sostener una máscara que ya no encaja es la forma más lenta de morir en vida.</p>
<p>Nos aterra el vacío, pero es en ese abismo donde la herida deja de sangrar para convertirse en portal. El conflicto central es tu resistencia a dejar morir a quien fuiste para permitir que nazca quien realmente eres. Debes ver este video completo; esto cambiará tu forma de ver tus desiertos personales y tu soledad.</p>', '**La muerte de tu ego es tu único camino.**  
  
¿Qué queda de ti cuando el ruido del mundo finalmente se apaga y el silencio te devora? Sostener una máscara que ya no encaja es la forma más lenta de morir en vida.

Nos aterra el vacío, pero es en ese abismo donde la herida deja de sangrar para convertirse en portal. El conflicto central es tu resistencia a dejar morir a quien fuiste para permitir que nazca quien realmente eres. Debes ver este video completo; esto cambiará tu forma de ver tus desiertos personales y tu soledad.', 'YOUTUBE', 'https://youtu.be/dhvfpZ2dUMc', 'https://i.ytimg.com/vi/dhvfpZ2dUMc/hqdefault.jpg', 836000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('f787ac38282f4a46b27156f8422e1aaa', '5ae472cf6b224d189c5dba48a22b4c09', 'a5622064ff06402fad15cb9d168c45f3', 'Clase 21 - Sana tu ansiedad, confronta tus miedos', 13, '<p><strong>Tu dolor es el mapa hacia tu tesoro oculto.</strong></p>
<p>¿Cuánto veneno has tragado intentando parecer alguien que siempre tiene el control? La verdad es que tu fortaleza actual es solo una armadura que hoy te impide respirar.</p>
<p>Caminamos sobre las brasas de viejas traiciones, ignorando que el fuego no viene a destruirnos, sino a purificar el metal de nuestra alma. Este conflicto interno es el llamado a desmantelar la mentira de tu falsa paz para tocar la herida que te hará libre. Míralo completo; esto cambiará tu forma de ver cada cicatriz que llevas en el alma.</p>', '**Tu dolor es el mapa hacia tu tesoro oculto.**

¿Cuánto veneno has tragado intentando parecer alguien que siempre tiene el control? La verdad es que tu fortaleza actual es solo una armadura que hoy te impide respirar.

Caminamos sobre las brasas de viejas traiciones, ignorando que el fuego no viene a destruirnos, sino a purificar el metal de nuestra alma. Este conflicto interno es el llamado a desmantelar la mentira de tu falsa paz para tocar la herida que te hará libre. Míralo completo; esto cambiará tu forma de ver cada cicatriz que llevas en el alma.', 'YOUTUBE', 'https://youtu.be/R4jQ-o3d8WE', 'https://i.ytimg.com/vi/R4jQ-o3d8WE/hqdefault.jpg', 1020000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('2e2d8efb54f346f2b35ff5470d9a3ec6', '5ae472cf6b224d189c5dba48a22b4c09', 'f8cb1195a52e4cb7917aa183f11a00cd', 'Clase 22 - Sana la depresión', 14, '<p><strong>¿Y si todo lo que crees ser es solo una máscara impuesta por el miedo al juicio ajeno?</strong><br /><br />Vivimos mendigando una aprobación que nos despoja de nuestra esencia, aceptando una libertad a medias que sabe a cautiverio.</p>
<p>El conflicto surge cuando el alma ya no encaja en los moldes estrechos de la lógica. Tocamos la herida de la despersonalización para que dejes de ser una sombra y reclames tu luz. Mira este video hasta el final; esto cambiará tu forma de ver tu propia realidad interna.</p>', '**¿Y si todo lo que crees ser es solo una máscara impuesta por el miedo al juicio ajeno?**  
  
Vivimos mendigando una aprobación que nos despoja de nuestra esencia, aceptando una libertad a medias que sabe a cautiverio.

El conflicto surge cuando el alma ya no encaja en los moldes estrechos de la lógica. Tocamos la herida de la despersonalización para que dejes de ser una sombra y reclames tu luz. Mira este video hasta el final; esto cambiará tu forma de ver tu propia realidad interna.', 'YOUTUBE', 'https://youtu.be/gk-mAo0JZNM', 'https://i.ytimg.com/vi/gk-mAo0JZNM/hqdefault.jpg', 458000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('392a8c8b71ca472ca9f0aee82ab30437', 'cfc4c4334474498b9f6c9c47800fdecf', 'd459f9b1db9248c9a9a7eb4d74ebcd51', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('c624b505ef17472b920d4caec35bd8f5', 'cfc4c4334474498b9f6c9c47800fdecf', 'bf4c2f2fafde453cab7c212c21198aa7', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('7469d7a821b2412ead0b4ba140032415', 'cfc4c4334474498b9f6c9c47800fdecf', 'bf4c2f2fafde453cab7c212c21198aa7', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('edb8be36dbaf4faba4d1d7519042e879', 'cfc4c4334474498b9f6c9c47800fdecf', 'bf4c2f2fafde453cab7c212c21198aa7', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('2d03b26ac6c64ddeacf207224f87d707', 'cfc4c4334474498b9f6c9c47800fdecf', '57b1ea4e15124ed4902c1459d626716c', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('bd4392f4ea1f44f08fc808dd4335df65', '5ae472cf6b224d189c5dba48a22b4c09', 'e7cd6b6c13cb403e98315468390104bb', 'Clase 23 - Supera el duelo', 15, '<p><strong>¿Estás viviendo tu propia vida o solo interpretas el guion que el miedo escribió para ti? </strong><br /><br />Pasamos los días construyendo muros de cristal que llamamos seguridad, sin darnos cuenta de que se han convertido en nuestra propia celda.</p>
<p>En el núcleo de este conflicto arde una verdad incómoda: el dolor que evitas es precisamente la medicina que necesitas para despertar. Este video es una invitación a cruzar el umbral del temor y mirar a los ojos a tu propia sombra. Míralo completo; esto cambiará tu forma de ver tus crisis para siempre.</p>', '**¿Estás viviendo tu propia vida o solo interpretas el guion que el miedo escribió para ti? **  
  
Pasamos los días construyendo muros de cristal que llamamos seguridad, sin darnos cuenta de que se han convertido en nuestra propia celda.

En el núcleo de este conflicto arde una verdad incómoda: el dolor que evitas es precisamente la medicina que necesitas para despertar. Este video es una invitación a cruzar el umbral del temor y mirar a los ojos a tu propia sombra. Míralo completo; esto cambiará tu forma de ver tus crisis para siempre.', 'YOUTUBE', 'https://youtu.be/26JkyVYqiSw', 'https://i.ytimg.com/vi/26JkyVYqiSw/hqdefault.jpg', 446000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('dc46887b496243edbe9ede77331d84dd', '5ae472cf6b224d189c5dba48a22b4c09', '203af6edd55945e095b6d0f636aeed0a', 'Clase 24 - Relaciones de pareja plena', 16, '<p><strong>¿Cuánto tiempo más intentarás sostener una estructura que ya se hizo pedazos en tu interior?</strong><br /><br />Caminamos como extraños en nuestra propia piel, alimentando versiones de nosotros mismos que solo existen para complacer el afuera, mientras el alma grita por un respiro.</p>
<p>Este video es un descenso necesario a la herida; ese espacio donde el dolor deja de ser ruido para convertirse en maestro. Revelamos el conflicto de habitar una identidad agotada que ya no te pertenece.</p>
<p>Míralo completo porque esto cambiará tu forma de entender tus quiebres; no son finales, sino el inicio de una arquitectura nueva.</p>', '**¿Cuánto tiempo más intentarás sostener una estructura que ya se hizo pedazos en tu interior?**  
  
Caminamos como extraños en nuestra propia piel, alimentando versiones de nosotros mismos que solo existen para complacer el afuera, mientras el alma grita por un respiro.

Este video es un descenso necesario a la herida; ese espacio donde el dolor deja de ser ruido para convertirse en maestro. Revelamos el conflicto de habitar una identidad agotada que ya no te pertenece.

Míralo completo porque esto cambiará tu forma de entender tus quiebres; no son finales, sino el inicio de una arquitectura nueva.', 'YOUTUBE', 'https://youtu.be/qf92w0Xb4xM', 'https://i.ytimg.com/vi/qf92w0Xb4xM/hqdefault.jpg', 856000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('dfbbfa3d27d24490b257fd1eb1f04536', '1724d86936ba4bf6b059500e26e4a775', NULL, 'BIENVENIDO(A)', 0, '<blockquote><p>&quot;No estás comenzando un curso. Estás entrando en una experiencia de reconstrucción total. Este es tu laboratorio sagrado de transformación.&quot;</p></blockquote>
<p>Ya formas parte del programa <strong>RENASER 90 DÍAS</strong>, y ahora accedes a este espacio íntimo y estratégico diseñado exclusivamente para ti.</p>
<p>Aquí no vienes a probar. Vienes a <strong>avanzar con intención</strong>, a <strong>dejar atrás versiones obsoletas</strong> y a encarnar tu potencial con precisión.</p>
<p>Este espacio SKOOL será:</p>
<p>✅ Tu cuaderno de bitácora emocional y mental.<br />✅ Tu red de contención y conexión con otros líderes en transformación.<br />✅ Tu centro de seguimiento, rituales, tareas, y activaciones semanales.<br />✅ El lugar donde compartes avances, integras aprendizajes y declaras decisiones poderosas.</p>
<p>💎 Ya estás dentro del proceso.<br />💎 Ya estás siendo sostenido por una estructura de alto rendimiento.<br />💎 Ya no estás solo.</p>
<p>Ahora es momento de usar este espacio como lo que es: <strong>Una plataforma de expansión acelerada</strong>.</p>
<h3>¿Qué hacer ahora?</h3>
<ol><li><p>Dirígete al módulo actual de tu semana.</p></li><li><p>Completa tu ritual de compromiso.</p></li><li><p>Comparte tu intención poderosa para esta etapa.</p></li><li><p>Conecta con la comunidad: tu reflejo y tu impulso.</p></li></ol>
<p>Nos emociona y honra acompañarte.<br />Esto apenas comienza... y ya está cambiando todo.<br /><strong>Tu equipo RENASER </strong>💎</p>', '> "No estás comenzando un curso. Estás entrando en una experiencia de reconstrucción total. Este es tu laboratorio sagrado de transformación."

Ya formas parte del programa **RENASER 90 DÍAS**, y ahora accedes a este espacio íntimo y estratégico diseñado exclusivamente para ti.

Aquí no vienes a probar. Vienes a **avanzar con intención**, a **dejar atrás versiones obsoletas** y a encarnar tu potencial con precisión.

Este espacio SKOOL será:

✅ Tu cuaderno de bitácora emocional y mental.  
✅ Tu red de contención y conexión con otros líderes en transformación.  
✅ Tu centro de seguimiento, rituales, tareas, y activaciones semanales.  
✅ El lugar donde compartes avances, integras aprendizajes y declaras decisiones poderosas.

💎 Ya estás dentro del proceso.  
💎 Ya estás siendo sostenido por una estructura de alto rendimiento.  
💎 Ya no estás solo.

Ahora es momento de usar este espacio como lo que es: **Una plataforma de expansión acelerada**.

### ¿Qué hacer ahora?

1. Dirígete al módulo actual de tu semana.
2. Completa tu ritual de compromiso.
3. Comparte tu intención poderosa para esta etapa.
4. Conecta con la comunidad: tu reflejo y tu impulso.

Nos emociona y honra acompañarte.  
Esto apenas comienza... y ya está cambiando todo.  
**Tu equipo RENASER **💎', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('748813b99c9f45f199216fd17f764bff', '1724d86936ba4bf6b059500e26e4a775', '95c8545c000e439684158d2c35217dcc', '🔓 EL CÓDIGO RENASER', 1, '<p><strong>No es un libro. Es una llave.</strong><br />Una puerta directa a tu verdad, sin rodeos, sin adornos.<br />Aquí no encontrarás frases de autoayuda para sentirte mejor un rato.<br />Encontrarás preguntas que incomodan, ejercicios que confrontan y principios que te exigen dejar de ser espectador de tu propia vida.</p>
<p><strong>Este eBook fue diseñado para ser vivido, no solo leído.</strong><br />Por eso te invito a que <strong>lo descargues, lo imprimas y lo trabajes a conciencia.</strong><br />Anota. Tacha. Responde. Vuelve. Rompe. Sana.<br />Este código no es para acumular información, es para transformar tu historia desde la raíz.</p>
<p>Si estás listo para dejar de huir de ti,<br />si estás listo para renacer con carácter,<br /><strong>empieza aquí.</strong></p>
<p>📥 <strong>Descárgalo.</strong><br /><strong>🖨️ Imprímelo.</strong><br /><strong>🧠 Y hazlo parte de ti.</strong></p>', '**No es un libro. Es una llave.**  
Una puerta directa a tu verdad, sin rodeos, sin adornos.  
Aquí no encontrarás frases de autoayuda para sentirte mejor un rato.  
Encontrarás preguntas que incomodan, ejercicios que confrontan y principios que te exigen dejar de ser espectador de tu propia vida.

**Este eBook fue diseñado para ser vivido, no solo leído.**  
Por eso te invito a que **lo descargues, lo imprimas y lo trabajes a conciencia.**  
Anota. Tacha. Responde. Vuelve. Rompe. Sana.  
Este código no es para acumular información, es para transformar tu historia desde la raíz.

Si estás listo para dejar de huir de ti,  
si estás listo para renacer con carácter,  
**empieza aquí.**

📥 **Descárgalo.**  
**🖨️ Imprímelo.**  
**🧠 Y hazlo parte de ti.**', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('66f975d7240a4095a84eb874c8e5b72f', '1724d86936ba4bf6b059500e26e4a775', 'f2c6ca1b67ae427ab32b6dee24db2dfb', 'HOJA DE RUTA  | SEMANA 1', 2, '<p>Bienvenido(a)! </p>
<p>En el siguiente documento te contamos el material y las clases que tenemos en esta semana, de manera ordenada. </p>
<p>Disfruta de este viaje!</p>', 'Bienvenido(a)! 

En el siguiente documento te contamos el material y las clases que tenemos en esta semana, de manera ordenada. 

Disfruta de este viaje!', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('41ee7baa95f240219cb39cf97f6ec1a4', '1724d86936ba4bf6b059500e26e4a775', 'f2c6ca1b67ae427ab32b6dee24db2dfb', 'MASTERCLASS 1 | EL ARTE DE SER TU TERAPEUTA', 3, '<p>Esta masterclass marca un punto de quiebre: dejar de depender de explicaciones externas y empezar a <strong>relacionarte contigo desde conciencia, estructura y presencia</strong>. No reemplaza procesos profundos ni promete soluciones mágicas; te enseña algo más valioso: <strong>cómo acompañarte sin sabotearte</strong>.</p>
<p>Aquí comprendes por qué muchas personas “saben mucho” pero siguen repitiendo lo mismo, y cuál es la diferencia entre analizarte y <strong>hacerte cargo de tu proceso interno</strong>. Ser tu propio terapeuta no es tratarte, es <strong>sostenerte con honestidad y criterio</strong>.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para dejar de buscar respuestas fuera cuando la claridad ya está disponible dentro.</p></li><li><p>Para interpretar tus emociones sin exagerarlas ni minimizarlas.</p></li><li><p>Para intervenir tus estados internos en tiempo real, sin huir ni castigarte.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Autonomía emocional.</p></li><li><p>Capacidad de autoobservación clara.</p></li><li><p>Una relación más adulta y funcional contigo mismo.</p></li></ul>', 'Esta masterclass marca un punto de quiebre: dejar de depender de explicaciones externas y empezar a **relacionarte contigo desde conciencia, estructura y presencia**. No reemplaza procesos profundos ni promete soluciones mágicas; te enseña algo más valioso: **cómo acompañarte sin sabotearte**.

Aquí comprendes por qué muchas personas “saben mucho” pero siguen repitiendo lo mismo, y cuál es la diferencia entre analizarte y **hacerte cargo de tu proceso interno**. Ser tu propio terapeuta no es tratarte, es **sostenerte con honestidad y criterio**.

**¿Para qué sirve?**

- Para dejar de buscar respuestas fuera cuando la claridad ya está disponible dentro.
- Para interpretar tus emociones sin exagerarlas ni minimizarlas.
- Para intervenir tus estados internos en tiempo real, sin huir ni castigarte.

**Qué activa en ti**

- Autonomía emocional.
- Capacidad de autoobservación clara.
- Una relación más adulta y funcional contigo mismo.', 'YOUTUBE', 'https://youtu.be/2cHOL1KojPo', 'https://i.ytimg.com/vi/2cHOL1KojPo/hqdefault.jpg', 4724000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('4c6b7ff3704a4015b6b948703ee60bae', 'cfc4c4334474498b9f6c9c47800fdecf', '57b1ea4e15124ed4902c1459d626716c', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('838f58e18dc34c92898c9918b041f53a', '1724d86936ba4bf6b059500e26e4a775', 'f2c6ca1b67ae427ab32b6dee24db2dfb', 'VIDEO 1 | VULNERABILIDAD CONSCIENTE', 4, '<p>Este video introduce una distinción clave que cambia por completo la forma en que te relacionas contigo: <strong>no toda vulnerabilidad es debilidad</strong>. Existe una vulnerabilidad que expone sin sostén… y otra que <strong>ordena, integra y fortalece</strong>.</p>
<p>Aquí se explora la vulnerabilidad consciente como una posición interna de claridad y responsabilidad. No es desbordarte ni mostrarte para ser validado, sino <strong>permitirte ver lo que hay sin huir ni atacarte</strong>, manteniendo presencia y dirección.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para dejar de confundir apertura con descontrol.</p></li><li><p>Para relacionarte con tus emociones sin perderte en ellas.</p></li><li><p>Para construir una fortaleza real, que no nace de la dureza sino de la conciencia.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Mayor honestidad interna sin autoexigencia.</p></li><li><p>Capacidad de sostener emociones difíciles con estabilidad.</p></li><li><p>Una sensación de coherencia: sentir, ver y decidir desde el mismo lugar.</p></li></ul>', 'Este video introduce una distinción clave que cambia por completo la forma en que te relacionas contigo: **no toda vulnerabilidad es debilidad**. Existe una vulnerabilidad que expone sin sostén… y otra que **ordena, integra y fortalece**.

Aquí se explora la vulnerabilidad consciente como una posición interna de claridad y responsabilidad. No es desbordarte ni mostrarte para ser validado, sino **permitirte ver lo que hay sin huir ni atacarte**, manteniendo presencia y dirección.

**¿Para qué sirve?**

- Para dejar de confundir apertura con descontrol.
- Para relacionarte con tus emociones sin perderte en ellas.
- Para construir una fortaleza real, que no nace de la dureza sino de la conciencia.

**Qué activa en ti**

- Mayor honestidad interna sin autoexigencia.
- Capacidad de sostener emociones difíciles con estabilidad.
- Una sensación de coherencia: sentir, ver y decidir desde el mismo lugar.', 'YOUTUBE', 'https://www.youtube.com/watch?v=dGHTPZEt9qU', 'https://i.ytimg.com/vi/dGHTPZEt9qU/hqdefault.jpg', 630000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('b2051ec3b7d24cc28f4525cc30be240b', '1724d86936ba4bf6b059500e26e4a775', 'f2c6ca1b67ae427ab32b6dee24db2dfb', 'VIDEO 2 | VICTIMISMO CONSCIENTE', 5, '<p>Este video aborda un tema incómodo pero decisivo: el victimismo que no se ve como tal. No el evidente, sino el <strong>sofisticado</strong>, el que se disfraza de cansancio, de “ya intenté todo” o de aparente humildad.</p>
<p>Aquí no se juzga ni se señala. Se revela. El victimismo consciente no busca culpar, sino <strong>hacer visible el punto exacto donde entregas tu poder sin notarlo</strong>, y cómo ese patrón te mantiene detenido aunque sigas “haciendo cosas”.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para identificar las formas sutiles en las que evitas responsabilizarte.</p></li><li><p>Para dejar de confundir dolor con identidad.</p></li><li><p>Para recuperar agencia sin violencia interna.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Claridad sobre tus excusas automáticas.</p></li><li><p>Un quiebre interno entre sentir dolor y vivir desde él.</p></li><li><p>La capacidad de elegir acción sin autoengaño.</p></li></ul>', 'Este video aborda un tema incómodo pero decisivo: el victimismo que no se ve como tal. No el evidente, sino el **sofisticado**, el que se disfraza de cansancio, de “ya intenté todo” o de aparente humildad.

Aquí no se juzga ni se señala. Se revela. El victimismo consciente no busca culpar, sino **hacer visible el punto exacto donde entregas tu poder sin notarlo**, y cómo ese patrón te mantiene detenido aunque sigas “haciendo cosas”.

**¿Para qué sirve?**

- Para identificar las formas sutiles en las que evitas responsabilizarte.
- Para dejar de confundir dolor con identidad.
- Para recuperar agencia sin violencia interna.

**Qué activa en ti**

- Claridad sobre tus excusas automáticas.
- Un quiebre interno entre sentir dolor y vivir desde él.
- La capacidad de elegir acción sin autoengaño.', 'YOUTUBE', 'https://youtu.be/-Y2x6Olii_Y', 'https://i.ytimg.com/vi/-Y2x6Olii_Y/hqdefault.jpg', 974000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('d4c2204f49fe40a5830b3cea3fb10478', '1724d86936ba4bf6b059500e26e4a775', 'f2c6ca1b67ae427ab32b6dee24db2dfb', 'TERAPIA Potencial infinito', 6, '<p>Escucha con audífonos en un lugar cómodo y tranquilo y vive este viaje </p>
<p><a href="1724d86936ba4bf6b059500e26e4a775/assets/c0077cc9a110-145086556.bin" target="_blank" rel="noopener noreferrer">1724d86936ba4bf6b059500e26e4a775/assets/c0077cc9a110-145086556.bin</a></p>', 'Escucha con audífonos en un lugar cómodo y tranquilo y vive este viaje 

[1724d86936ba4bf6b059500e26e4a775/assets/c0077cc9a110-145086556.bin](1724d86936ba4bf6b059500e26e4a775/assets/c0077cc9a110-145086556.bin)', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('4d044a8d2c2b47ff864f2ed1c087427a', '1724d86936ba4bf6b059500e26e4a775', 'f2c6ca1b67ae427ab32b6dee24db2dfb', 'E-BOOK (MASTERCLASS-01)', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('8c7babbe862141958ca6041ccb3ccb9c', '1724d86936ba4bf6b059500e26e4a775', 'f2c6ca1b67ae427ab32b6dee24db2dfb', 'AUDIOTERAPIA 1.0 | CONEXIÓN CON TU ESENCIA', 8, '<p>Esta audioterapia es el primer umbral del proceso RENASER. No busca motivarte ni “arreglarte”, sino <strong>detener el ruido</strong>, bajar la exigencia y <strong>devolver tu atención a lo esencial</strong>: aquello que sigue intacto debajo de la prisa, el rol y la autoexigencia.</p>
<p>A través de una guía profunda y cuidadosamente diseñada, entras en un estado de presencia donde puedes <strong>escucharte sin juicio</strong>, reconocer cómo te has desconectado de ti y empezar a <strong>habitarte otra vez</strong>. Aquí no se fuerza el cambio: se crea el espacio interno para que ocurra.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para salir del piloto automático.</p></li><li><p>Para reconectar con tu centro interno y tu claridad natural.</p></li><li><p>Para iniciar el proceso desde la conciencia, no desde el esfuerzo.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Calma real (no evasión).</p></li><li><p>Sensación de arraigo y coherencia interna.</p></li><li><p>El primer recuerdo corporal de quién eres cuando no estás huyendo de ti.</p></li></ul>
<p>Esta audioterapia es la base.<br />Sin conexión con tu esencia, cualquier avance se vuelve frágil.<br />Aquí comienza el regreso.</p>', 'Esta audioterapia es el primer umbral del proceso RENASER. No busca motivarte ni “arreglarte”, sino **detener el ruido**, bajar la exigencia y **devolver tu atención a lo esencial**: aquello que sigue intacto debajo de la prisa, el rol y la autoexigencia.

A través de una guía profunda y cuidadosamente diseñada, entras en un estado de presencia donde puedes **escucharte sin juicio**, reconocer cómo te has desconectado de ti y empezar a **habitarte otra vez**. Aquí no se fuerza el cambio: se crea el espacio interno para que ocurra.

**¿Para qué sirve?**

- Para salir del piloto automático.
- Para reconectar con tu centro interno y tu claridad natural.
- Para iniciar el proceso desde la conciencia, no desde el esfuerzo.

**Qué activa en ti**

- Calma real (no evasión).
- Sensación de arraigo y coherencia interna.
- El primer recuerdo corporal de quién eres cuando no estás huyendo de ti.

Esta audioterapia es la base.  
Sin conexión con tu esencia, cualquier avance se vuelve frágil.  
Aquí comienza el regreso.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('a3e96426d55d44d3a43340116c5b85b8', '1724d86936ba4bf6b059500e26e4a775', 'f2c6ca1b67ae427ab32b6dee24db2dfb', 'AUDIOTERAPIA 1.1 | SUPERA TU FRUSTRACIÓN', 9, '<p>Esta audioterapia actúa directamente sobre uno de los estados más silenciosos y desgastantes: la frustración acumulada. No la que explota, sino la que se guarda. La que nace cuando haces, intentas, sostienes… y aun así sientes que no avanzas como esperas.</p>
<p>Aquí no se trata de “pensar positivo” ni de resistir más. Esta guía te lleva a <strong>identificar el origen real de tu frustración</strong>, a escuchar lo que está intentando decirte y a <strong>desactivar el ciclo interno de exigencia–decepción–culpa</strong> que la mantiene viva.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para liberar la tensión interna que se genera cuando te fuerzas a rendir sin escucharte.</p></li><li><p>Para transformar la frustración en información clara, no en castigo personal.</p></li><li><p>Para recuperar dirección sin autoataque.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Descenso inmediato de la carga emocional.</p></li><li><p>Claridad sobre qué estás forzando y por qué.</p></li><li><p>Un cambio interno: de la lucha a la comprensión consciente.</p></li></ul>
<p>Esta audioterapia no elimina la frustración negándola.<br />La atraviesa, la ordena y la convierte en guía.<br />Cuando entiendes tu frustración, dejas de pelear contigo.</p>', 'Esta audioterapia actúa directamente sobre uno de los estados más silenciosos y desgastantes: la frustración acumulada. No la que explota, sino la que se guarda. La que nace cuando haces, intentas, sostienes… y aun así sientes que no avanzas como esperas.

Aquí no se trata de “pensar positivo” ni de resistir más. Esta guía te lleva a **identificar el origen real de tu frustración**, a escuchar lo que está intentando decirte y a **desactivar el ciclo interno de exigencia–decepción–culpa** que la mantiene viva.

**¿Para qué sirve?**

- Para liberar la tensión interna que se genera cuando te fuerzas a rendir sin escucharte.
- Para transformar la frustración en información clara, no en castigo personal.
- Para recuperar dirección sin autoataque.

**Qué activa en ti**

- Descenso inmediato de la carga emocional.
- Claridad sobre qué estás forzando y por qué.
- Un cambio interno: de la lucha a la comprensión consciente.

Esta audioterapia no elimina la frustración negándola.  
La atraviesa, la ordena y la convierte en guía.  
Cuando entiendes tu frustración, dejas de pelear contigo.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('4057fa6c80a34aa5b290ff181075239c', '1724d86936ba4bf6b059500e26e4a775', 'f2c6ca1b67ae427ab32b6dee24db2dfb', 'AUDIOTERAPIA 1.2 | SIENTE Y RÍE', 10, '<p>Esta audioterapia abre un espacio poco habitual pero profundamente reparador: <strong>Permitirte sentir sin control y reír sin culpa</strong>. No como evasión, sino como señal de que el cuerpo y la emoción empiezan a aflojar después de mucho tiempo en tensión.</p>
<p>Aquí no se analiza ni se corrige nada. Se guía al sistema interno a <strong>bajar la guardia</strong>, a soltar la rigidez emocional y a recuperar la capacidad natural de sentir con ligereza. La risa aparece como consecuencia de la liberación, no como obligación.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para descargar emociones retenidas sin drama ni exigencia.</p></li><li><p>Para reconciliarte con el placer de estar presente.</p></li><li><p>Para recordar que sentir no siempre es pesado.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Relajación profunda del cuerpo y la mente.</p></li><li><p>Reconexión con la espontaneidad emocional.</p></li><li><p>Una sensación genuina de alivio, expansión y ligereza.</p></li></ul>
<p>Esta audioterapia no busca alegría forzada.<br />Permite que la emoción circule… y cuando circula, la risa surge sola.<br />Sentir y reír es una forma de volver a casa.</p>', 'Esta audioterapia abre un espacio poco habitual pero profundamente reparador: **Permitirte sentir sin control y reír sin culpa**. No como evasión, sino como señal de que el cuerpo y la emoción empiezan a aflojar después de mucho tiempo en tensión.

Aquí no se analiza ni se corrige nada. Se guía al sistema interno a **bajar la guardia**, a soltar la rigidez emocional y a recuperar la capacidad natural de sentir con ligereza. La risa aparece como consecuencia de la liberación, no como obligación.

**¿Para qué sirve?**

- Para descargar emociones retenidas sin drama ni exigencia.
- Para reconciliarte con el placer de estar presente.
- Para recordar que sentir no siempre es pesado.

**Qué activa en ti**

- Relajación profunda del cuerpo y la mente.
- Reconexión con la espontaneidad emocional.
- Una sensación genuina de alivio, expansión y ligereza.

Esta audioterapia no busca alegría forzada.  
Permite que la emoción circule… y cuando circula, la risa surge sola.  
Sentir y reír es una forma de volver a casa.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('9891043e9c2e4b098b8d3bd3de39834e', 'cfc4c4334474498b9f6c9c47800fdecf', '57b1ea4e15124ed4902c1459d626716c', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('98d3ed544bf246c2a1a78f0aaa52bcef', 'cfc4c4334474498b9f6c9c47800fdecf', 'b39edba43b1449cfbca739dfa5c97a71', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('46f21556a9884018925ebe9455e2f5ca', 'cfc4c4334474498b9f6c9c47800fdecf', 'b39edba43b1449cfbca739dfa5c97a71', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('8216ec7aaa09417eb39701170a888788', 'cfc4c4334474498b9f6c9c47800fdecf', 'b39edba43b1449cfbca739dfa5c97a71', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('cc592fc95a944421ad3a7b73f0be1b52', '1724d86936ba4bf6b059500e26e4a775', '71c7d2a8eb7f433d8103885b939152c8', 'HOJA DE RUTA I SEMANA 2', 11, '<p><strong>🧭 HOJA DE RUTA RENASER – SEMANA 2</strong><br />No basta con entender. Hay que habitar el proceso. Esta hoja de ruta es tu brújula emocional para la semana más simbólica del programa.</p>
<p>Aquí encontrarás <strong>la guía exacta para integrar la masterclass, el audio de la semana y los ejercicios del manual</strong>. Todo diseñado para que no solo avances… sino que <strong>renazcas con poder y claridad</strong>.</p>
<p>🔹 Escucha el audio “Supera tu frustración” cada mañana<br />🔹 Haz mínimo 2 ejercicios + 1 ritual del manual<br />🔹 Conecta con la tierra, tu cuerpo y tu historia sin juicio<br />🔹 Apóyate en frases activadoras que despiertan tu alma</p>
<p>📎 Aquí tienes el mapa. Lo demás… depende de ti.</p>', '**🧭 HOJA DE RUTA RENASER – SEMANA 2**  
No basta con entender. Hay que habitar el proceso. Esta hoja de ruta es tu brújula emocional para la semana más simbólica del programa.

Aquí encontrarás **la guía exacta para integrar la masterclass, el audio de la semana y los ejercicios del manual**. Todo diseñado para que no solo avances… sino que **renazcas con poder y claridad**.

🔹 Escucha el audio “Supera tu frustración” cada mañana  
🔹 Haz mínimo 2 ejercicios + 1 ritual del manual  
🔹 Conecta con la tierra, tu cuerpo y tu historia sin juicio  
🔹 Apóyate en frases activadoras que despiertan tu alma

📎 Aquí tienes el mapa. Lo demás… depende de ti.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('467d0f8fbd2941c88900e0b16df44e15', '1724d86936ba4bf6b059500e26e4a775', '71c7d2a8eb7f433d8103885b939152c8', 'MASTERCLASS 2 | LA ESENCIA DEL AMOR', 12, '<p>Esta masterclass desmonta una de las confusiones más profundas y normalizadas: creer que el amor es sacrificio, intensidad o necesidad. Aquí se revela el amor no como emoción pasajera, sino como <strong>estado interno de coherencia y presencia</strong>.</p>
<p>No se habla de amor romántico ni de fórmulas relacionales. Se explora el amor como la <strong>capacidad de estar sin poseer, de vincular sin perderte y de elegir sin depender</strong>. Cuando esa esencia no está clara, el vínculo se convierte en intercambio, miedo o control.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para distinguir amor de apego.</p></li><li><p>Para entender por qué repites ciertos patrones relacionales.</p></li><li><p>Para reconstruir tu forma de amar desde integridad, no desde carencia.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Claridad emocional en tus vínculos.</p></li><li><p>Mayor respeto por tus límites y los del otro.</p></li><li><p>Una sensación de calma al relacionarte, sin urgencia ni autoabandono.</p></li></ul>', 'Esta masterclass desmonta una de las confusiones más profundas y normalizadas: creer que el amor es sacrificio, intensidad o necesidad. Aquí se revela el amor no como emoción pasajera, sino como **estado interno de coherencia y presencia**.

No se habla de amor romántico ni de fórmulas relacionales. Se explora el amor como la **capacidad de estar sin poseer, de vincular sin perderte y de elegir sin depender**. Cuando esa esencia no está clara, el vínculo se convierte en intercambio, miedo o control.

**¿Para qué sirve?**

- Para distinguir amor de apego.
- Para entender por qué repites ciertos patrones relacionales.
- Para reconstruir tu forma de amar desde integridad, no desde carencia.

**Qué activa en ti**

- Claridad emocional en tus vínculos.
- Mayor respeto por tus límites y los del otro.
- Una sensación de calma al relacionarte, sin urgencia ni autoabandono.', 'YOUTUBE', 'https://youtu.be/qEIN-uQKHGc', 'https://i.ytimg.com/vi/qEIN-uQKHGc/hqdefault.jpg', 5644000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('ab9b0bb4a7044c69af73987a7a05a52c', '1724d86936ba4bf6b059500e26e4a775', '71c7d2a8eb7f433d8103885b939152c8', 'E-BOOK (SEMANA 2)', 13, '<p><strong>📖 MANUAL SEMANA 2: “La verdad sobre mamá: el inicio de tu relación con la vida”</strong><br />Esta semana no solo confrontamos tu historia personal… abrimos el portal hacia la raíz emocional de tu poder: la figura de mamá.</p>
<p>Este manual es una guía profunda, reveladora y terapéutica para trabajar la energía de mamá en tu vida, tus negocios, tu cuerpo y tus emociones. No es un documento… es un espejo.</p>
<p>💠 Contiene:</p>
<ul><li><p>Ejercicios de introspección profunda</p></li><li><p>Reflexiones reveladoras</p></li><li><p>Un ritual simbólico de transformación</p></li><li><p>Testimonios reales que activan tu proceso</p></li><li><p>Frases RENASER que despiertan verdad</p></li></ul>
<p>📎 Descárgalo, imprímelo o léelo con conciencia. No avances sin mirarte de verdad.</p>', '**📖 MANUAL SEMANA 2: “La verdad sobre mamá: el inicio de tu relación con la vida”**  
Esta semana no solo confrontamos tu historia personal… abrimos el portal hacia la raíz emocional de tu poder: la figura de mamá.

Este manual es una guía profunda, reveladora y terapéutica para trabajar la energía de mamá en tu vida, tus negocios, tu cuerpo y tus emociones. No es un documento… es un espejo.

💠 Contiene:

- Ejercicios de introspección profunda
- Reflexiones reveladoras
- Un ritual simbólico de transformación
- Testimonios reales que activan tu proceso
- Frases RENASER que despiertan verdad

📎 Descárgalo, imprímelo o léelo con conciencia. No avances sin mirarte de verdad.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('a4c948f5bf6243a79cdef2fe8deb7866', '1724d86936ba4bf6b059500e26e4a775', '71c7d2a8eb7f433d8103885b939152c8', 'AUDIOTERAPIA 2.0 | SANA TU LINAJE FEMENINO', 14, '<p>Esta audioterapia guía un encuentro profundo con la historia que vive en tu cuerpo. No se trata de revisar el pasado ni de buscar culpables, sino de <strong>reconocer cómo las cargas, silencios y patrones del linaje femenino siguen operando en tu forma de sentir, vincularte y sostenerte</strong>.</p>
<p>Aquí se abre un espacio de escucha interna donde puedes identificar qué no te pertenece, qué fue heredado y qué ya no necesitas cargar. Al hacerlo, el sistema emocional comienza a soltar lealtades inconscientes que condicionan tu energía, tu autoestima y tu manera de habitar el mundo.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para liberar mandatos femeninos inconscientes (sacrificio, silencio, sobrecarga).</p></li><li><p>Para cortar repeticiones emocionales que no elegiste.</p></li><li><p>Para reconciliarte con la fuerza femenina desde calma y dignidad.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Sensación de alivio y descanso interno.</p></li><li><p>Mayor conexión con tu intuición y tu valor propio.</p></li><li><p>Un cambio sutil pero profundo en la forma en que te relacionas contigo y con otros.</p></li></ul>', 'Esta audioterapia guía un encuentro profundo con la historia que vive en tu cuerpo. No se trata de revisar el pasado ni de buscar culpables, sino de **reconocer cómo las cargas, silencios y patrones del linaje femenino siguen operando en tu forma de sentir, vincularte y sostenerte**.

Aquí se abre un espacio de escucha interna donde puedes identificar qué no te pertenece, qué fue heredado y qué ya no necesitas cargar. Al hacerlo, el sistema emocional comienza a soltar lealtades inconscientes que condicionan tu energía, tu autoestima y tu manera de habitar el mundo.

**¿Para qué sirve?**

- Para liberar mandatos femeninos inconscientes (sacrificio, silencio, sobrecarga).
- Para cortar repeticiones emocionales que no elegiste.
- Para reconciliarte con la fuerza femenina desde calma y dignidad.

**Qué activa en ti**

- Sensación de alivio y descanso interno.
- Mayor conexión con tu intuición y tu valor propio.
- Un cambio sutil pero profundo en la forma en que te relacionas contigo y con otros.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('066d0051038c4d9f95c211a1fd31792a', '1724d86936ba4bf6b059500e26e4a775', 'bb9474712a624805a6e81954fad4c171', 'HOJA DE RUTA I SEMANA 3', 15, '<p><strong>🧭 HOJA DE RUTA RENASER – SEMANA 3: Vuelve a ti, incluso con miedo</strong><br />¿Y si esta semana no hicieras más que sostenerte?<br />Esta hoja de ruta es tu brújula emocional para integrar el manual, mirar tu miedo sin ceder, y <strong>volver a ti cuando todo afuera se tambalea</strong>.</p>
<p>Incluye:<br />🔹 Actividades esenciales de introspección<br />🔹 Ritual con objeto simbólico de seguridad<br />🔹 Frases de anclaje diario para tu sistema nervioso<br />🔹 Evaluación de avance emocional</p>
<p>📎 Imprímela, úsala con intención, vuelve a ti.</p>', '**🧭 HOJA DE RUTA RENASER – SEMANA 3: Vuelve a ti, incluso con miedo**  
¿Y si esta semana no hicieras más que sostenerte?  
Esta hoja de ruta es tu brújula emocional para integrar el manual, mirar tu miedo sin ceder, y **volver a ti cuando todo afuera se tambalea**.

Incluye:  
🔹 Actividades esenciales de introspección  
🔹 Ritual con objeto simbólico de seguridad  
🔹 Frases de anclaje diario para tu sistema nervioso  
🔹 Evaluación de avance emocional

📎 Imprímela, úsala con intención, vuelve a ti.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('a9bdc568fe0c42608b55eccd9c8a160d', 'cfc4c4334474498b9f6c9c47800fdecf', '4424058971c4467da1424857973e54f5', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('69d959754c3547d7a472cf0c2859a1ad', '1724d86936ba4bf6b059500e26e4a775', 'bb9474712a624805a6e81954fad4c171', 'E-BOOK (SEMANA 3)', 17, '<p><strong>📖 MANUAL SEMANA 3: “Seguridad Inquebrantable”</strong><br />Esta semana no necesitas mostrar fuerza. Necesitas <strong>recordarte que ya la tienes dentro</strong>.</p>
<p>Este manual te guía a reconocer tu centro, ese lugar donde puedes respirar contigo, aunque el mundo se esté cayendo. No es un texto, es un anclaje.</p>
<p>💠 Incluye:</p>
<ul><li><p>Ejercicios para activar tu centro interno</p></li><li><p>Frases que fortalecen desde el alma</p></li><li><p>Rituales simbólicos de anclaje emocional</p></li><li><p>Reflexiones que confrontan sin derrumbarte</p></li></ul>
<p>📎 Léelo con calma, en silencio. Es una conversación contigo.</p>', '**📖 MANUAL SEMANA 3: “Seguridad Inquebrantable”**  
Esta semana no necesitas mostrar fuerza. Necesitas **recordarte que ya la tienes dentro**.

Este manual te guía a reconocer tu centro, ese lugar donde puedes respirar contigo, aunque el mundo se esté cayendo. No es un texto, es un anclaje.

💠 Incluye:

- Ejercicios para activar tu centro interno
- Frases que fortalecen desde el alma
- Rituales simbólicos de anclaje emocional
- Reflexiones que confrontan sin derrumbarte

📎 Léelo con calma, en silencio. Es una conversación contigo.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('6844f1ecc8964a718d6b316fa9714fea', '1724d86936ba4bf6b059500e26e4a775', 'bb9474712a624805a6e81954fad4c171', 'AUDIOTERAPIA 3.0 | SANA CON PAPÁ', 18, '<p>Esta audioterapia abre un espacio directo y profundo con una de las raíces más determinantes de tu estructura interna: la relación con la figura paterna. No se trata de juzgar, justificar ni reescribir la historia, sino de <strong>reconocer cómo ese vínculo sigue influyendo en tu forma de afirmarte, decidir y ocupar tu lugar</strong>.</p>
<p>Aquí se trabaja la huella del padre no desde la memoria, sino desde el cuerpo y la emoción. Al hacerlo, se libera la tensión que se manifiesta como exigencia excesiva, miedo a fallar o dificultad para sostener autoridad personal sin dureza.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para desactivar patrones de aprobación, rebeldía o autoexigencia ligados a la figura paterna.</p></li><li><p>Para fortalecer tu eje interno y tu capacidad de decisión.</p></li><li><p>Para reconciliarte con la autoridad sin miedo ni rigidez.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Sensación de firmeza y sostén interno.</p></li><li><p>Mayor claridad para poner límites y avanzar.</p></li><li><p>Una relación más madura contigo y con el mundo.</p></li></ul>
<p>Esta audioterapia no cambia a tu padre.<br />Cambia la forma en que su huella vive en ti.<br />Sanar con papá es recuperar tu autoridad interna.</p>', 'Esta audioterapia abre un espacio directo y profundo con una de las raíces más determinantes de tu estructura interna: la relación con la figura paterna. No se trata de juzgar, justificar ni reescribir la historia, sino de **reconocer cómo ese vínculo sigue influyendo en tu forma de afirmarte, decidir y ocupar tu lugar**.

Aquí se trabaja la huella del padre no desde la memoria, sino desde el cuerpo y la emoción. Al hacerlo, se libera la tensión que se manifiesta como exigencia excesiva, miedo a fallar o dificultad para sostener autoridad personal sin dureza.

**¿Para qué sirve?**

- Para desactivar patrones de aprobación, rebeldía o autoexigencia ligados a la figura paterna.
- Para fortalecer tu eje interno y tu capacidad de decisión.
- Para reconciliarte con la autoridad sin miedo ni rigidez.

**Qué activa en ti**

- Sensación de firmeza y sostén interno.
- Mayor claridad para poner límites y avanzar.
- Una relación más madura contigo y con el mundo.

Esta audioterapia no cambia a tu padre.  
Cambia la forma en que su huella vive en ti.  
Sanar con papá es recuperar tu autoridad interna.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('b584a6e44b6549b2b33793bb4cb229ea', '1724d86936ba4bf6b059500e26e4a775', '987dd206524a41a0bb4b714429974cf3', 'HOJA DE RUTA I SEMANA 4', 19, '<p><strong>🧭 HOJA DE RUTA RENASER – SEMANA 4: El arte de elegirte incluso en tu caos</strong><br />¿Te has mirado con ternura últimamente?<br />Esta hoja de ruta es tu guía emocional para aplicar el manual, integrar la masterclass y practicar el amor propio real: <strong>ese que no necesita perfección, solo verdad</strong>.</p>
<p>Incluye:<br />🔹 Actividades semanales esenciales<br />🔹 Ritual simbólico frente al espejo<br />🔹 Frases activadoras para comenzar el día<br />🔹 Una mini evaluación para medir tu compromiso emocional</p>
<p>📎 Imprímela, márcala, úsala. Es tu brújula esta semana.</p>', '**🧭 HOJA DE RUTA RENASER – SEMANA 4: El arte de elegirte incluso en tu caos**  
¿Te has mirado con ternura últimamente?  
Esta hoja de ruta es tu guía emocional para aplicar el manual, integrar la masterclass y practicar el amor propio real: **ese que no necesita perfección, solo verdad**.

Incluye:  
🔹 Actividades semanales esenciales  
🔹 Ritual simbólico frente al espejo  
🔹 Frases activadoras para comenzar el día  
🔹 Una mini evaluación para medir tu compromiso emocional

📎 Imprímela, márcala, úsala. Es tu brújula esta semana.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('f5c2ff5523914320b6e016808f564c5b', '1724d86936ba4bf6b059500e26e4a775', '987dd206524a41a0bb4b714429974cf3', 'MASTERCLASS 4 | AMOR PROPIO', 20, '<p>Esta masterclass desmonta una de las ideas más distorsionadas del desarrollo personal: el amor propio como discurso o autoafirmación vacía. Aquí se aborda el amor propio como <strong>estructura interna</strong>, no como emoción momentánea ni como complacencia contigo.</p>
<p>Se revela por qué muchas personas “se aceptan” pero se abandonan en decisiones clave, y cómo el verdadero amor propio se expresa en <strong>límites claros, coherencia interna y respeto por tu energía, tu tiempo y tu verdad</strong>.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para dejar de confundir amor propio con permisividad o egoísmo.</p></li><li><p>Para entender por qué te traicionas incluso sabiendo qué te hace mal.</p></li><li><p>Para construir una relación contigo basada en dignidad, no en exigencia ni culpa.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Mayor coherencia entre lo que sientes, piensas y haces.</p></li><li><p>Capacidad de priorizarte sin justificarte.</p></li><li><p>Una sensación estable de valía que no depende de otros.</p></li></ul>
<p>Esta masterclass no busca que te “quieras más”.<br />Te enseña a <strong>no abandonarte</strong>.<br />Eso es amor propio real.</p>', 'Esta masterclass desmonta una de las ideas más distorsionadas del desarrollo personal: el amor propio como discurso o autoafirmación vacía. Aquí se aborda el amor propio como **estructura interna**, no como emoción momentánea ni como complacencia contigo.

Se revela por qué muchas personas “se aceptan” pero se abandonan en decisiones clave, y cómo el verdadero amor propio se expresa en **límites claros, coherencia interna y respeto por tu energía, tu tiempo y tu verdad**.

**¿Para qué sirve?**

- Para dejar de confundir amor propio con permisividad o egoísmo.
- Para entender por qué te traicionas incluso sabiendo qué te hace mal.
- Para construir una relación contigo basada en dignidad, no en exigencia ni culpa.

**Qué activa en ti**

- Mayor coherencia entre lo que sientes, piensas y haces.
- Capacidad de priorizarte sin justificarte.
- Una sensación estable de valía que no depende de otros.

Esta masterclass no busca que te “quieras más”.  
Te enseña a **no abandonarte**.  
Eso es amor propio real.', 'YOUTUBE', 'https://youtu.be/ON4_Vw-DMfM', 'https://i.ytimg.com/vi/ON4_Vw-DMfM/hqdefault.jpg', 3906000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('2ec740585f414399b9ac7df033350a55', '1724d86936ba4bf6b059500e26e4a775', '987dd206524a41a0bb4b714429974cf3', 'E-BOOK (SEMANA 4)', 21, '<p><strong>📖 MANUAL SEMANA 4: “El amor propio no es autoestima. Es integración radical”</strong><br />Esta semana no se trata de gustarte frente al espejo… sino de dejar de pelear contigo cuando caes. El verdadero amor propio no ocurre en lo bonito, sino en el caos. En la sombra.</p>
<p>Este manual es una guía para volver a ti.<br />Para abrazarte cuando más lo necesitas. Para <strong>reconciliarte con la parte de ti que aún escondes</strong>.</p>
<p>💠 Incluye:</p>
<ul><li><p>Ejercicios introspectivos reveladores</p></li><li><p>Frases activadoras</p></li><li><p>Testimonios reales</p></li><li><p>Un ritual simbólico que sana lo que rechazaste de ti</p></li></ul>
<p>📎 Léelo con presencia. No es solo lectura… es alquimia interior.</p>', '**📖 MANUAL SEMANA 4: “El amor propio no es autoestima. Es integración radical”**  
Esta semana no se trata de gustarte frente al espejo… sino de dejar de pelear contigo cuando caes. El verdadero amor propio no ocurre en lo bonito, sino en el caos. En la sombra.

Este manual es una guía para volver a ti.  
Para abrazarte cuando más lo necesitas. Para **reconciliarte con la parte de ti que aún escondes**.

💠 Incluye:

- Ejercicios introspectivos reveladores
- Frases activadoras
- Testimonios reales
- Un ritual simbólico que sana lo que rechazaste de ti

📎 Léelo con presencia. No es solo lectura… es alquimia interior.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('8d7dc7b29c834e468e7a092e75d31fd4', '1724d86936ba4bf6b059500e26e4a775', '987dd206524a41a0bb4b714429974cf3', 'AUDIOTERAPIA 4 | LA MAGIA DE LA IMPERFECCIÓN', 22, '<p>Esta audioterapia te guía hacia un punto poco explorado: el lugar donde dejas de exigirte completarte para poder estar en paz. No se trata de conformarte ni de justificar errores, sino de <strong>soltar la lucha constante por ser “mejor” para merecer descanso, amor o validación</strong>.</p>
<p>Aquí se trabaja la rigidez interna que nace del perfeccionismo, del miedo a fallar y de la autoobservación crítica permanente. Al permitirte habitar la imperfección sin ataque, se libera una energía creativa y vital que había quedado atrapada en el control.</p>
<p><strong>¿Para qué sirve?</strong></p>
<ul><li><p>Para disminuir la autoexigencia que desgasta y paraliza.</p></li><li><p>Para reconciliarte con tus errores sin perder dirección.</p></li><li><p>Para recuperar espontaneidad y disfrute sin culpa.</p></li></ul>
<p><strong>Qué activa en ti</strong></p>
<ul><li><p>Relajación profunda del sistema interno.</p></li><li><p>Mayor autenticidad en tu forma de expresarte.</p></li><li><p>Una sensación de libertad al dejar de sostener una imagen.</p></li></ul>
<p>Esta audioterapia no celebra el error.<br />Celebra el momento en que <strong>dejas de pelear contigo por ser humano</strong>.<br />Ahí comienza la magia de la imperfección.</p>', 'Esta audioterapia te guía hacia un punto poco explorado: el lugar donde dejas de exigirte completarte para poder estar en paz. No se trata de conformarte ni de justificar errores, sino de **soltar la lucha constante por ser “mejor” para merecer descanso, amor o validación**.

Aquí se trabaja la rigidez interna que nace del perfeccionismo, del miedo a fallar y de la autoobservación crítica permanente. Al permitirte habitar la imperfección sin ataque, se libera una energía creativa y vital que había quedado atrapada en el control.

**¿Para qué sirve?**

- Para disminuir la autoexigencia que desgasta y paraliza.
- Para reconciliarte con tus errores sin perder dirección.
- Para recuperar espontaneidad y disfrute sin culpa.

**Qué activa en ti**

- Relajación profunda del sistema interno.
- Mayor autenticidad en tu forma de expresarte.
- Una sensación de libertad al dejar de sostener una imagen.

Esta audioterapia no celebra el error.  
Celebra el momento en que **dejas de pelear contigo por ser humano**.  
Ahí comienza la magia de la imperfección.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('48f942d2e7dd41cfb4b12425904d7fe4', 'cfc4c4334474498b9f6c9c47800fdecf', '4424058971c4467da1424857973e54f5', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('305ae80efd3043e7bec18eddd5ee743f', '1724d86936ba4bf6b059500e26e4a775', '77daffb6ea054c27ba64d523acf96842', 'HOJA DE RUTA I SEMANA 5', 23, '<p>Esta semana no necesitas más información.<br />Necesitas <strong>recordar cómo administrar tu energía con amor.</strong></p>
<p>Te dejo aquí la <strong>Hoja de Ruta RENASER – Semana 5</strong><br />para que sepas exactamente qué hacer y cómo aplicar lo que estás integrando:</p>
<p>✅ Resumen práctico<br />🎧 Recursos disponibles (audioterapia, masterclass y ebook)<br />📌 Actividades clave para integrar cuerpo y mente<br />🔮 Ritual simbólico<br />📊 Evaluación de avance</p>
<p>✨ Es hora de dejar de pelearte con tu cuerpo y comenzar a honrarlo.</p>
<p>📎 Descarga tu hoja de ruta aquí </p>', 'Esta semana no necesitas más información.  
Necesitas **recordar cómo administrar tu energía con amor.**

Te dejo aquí la **Hoja de Ruta RENASER – Semana 5**  
para que sepas exactamente qué hacer y cómo aplicar lo que estás integrando:

✅ Resumen práctico  
🎧 Recursos disponibles (audioterapia, masterclass y ebook)  
📌 Actividades clave para integrar cuerpo y mente  
🔮 Ritual simbólico  
📊 Evaluación de avance

✨ Es hora de dejar de pelearte con tu cuerpo y comenzar a honrarlo.

📎 Descarga tu hoja de ruta aquí', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('b60690ff9c974c12b42c5b2a3762b2de', '1724d86936ba4bf6b059500e26e4a775', '77daffb6ea054c27ba64d523acf96842', 'MASTERCLASS 5 | CUERPO - MENTE', 24, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/Fz90HS0Rnnw', 'https://i.ytimg.com/vi/Fz90HS0Rnnw/hqdefault.jpg', 6433000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('61ddfe65d44e428881271187b26a450a', '1724d86936ba4bf6b059500e26e4a775', '77daffb6ea054c27ba64d523acf96842', 'E-BOOK I SEMANA 5', 25, '<p>Esta semana no venimos a corregir el cuerpo.<br />Venimos a escucharlo.</p>
<p>Tu cuerpo no te está fallando. Está recordándote lo que olvidaste: <strong>respirar, sentir, habitar.</strong></p>
<p>🌀 Ya está disponible el eBook de esta semana:<br /><strong>“Cuerpo y Mente: El Arte de Administrarte para Sanar”</strong></p>
<p>📘 Descárgalo. Léelo con pausa.<br />⚡ Aplícalo como un ritual, no como una teoría.<br />🪶 Tu cuerpo no quiere perfección, quiere presencia.</p>', 'Esta semana no venimos a corregir el cuerpo.  
Venimos a escucharlo.

Tu cuerpo no te está fallando. Está recordándote lo que olvidaste: **respirar, sentir, habitar.**

🌀 Ya está disponible el eBook de esta semana:  
**“Cuerpo y Mente: El Arte de Administrarte para Sanar”**

📘 Descárgalo. Léelo con pausa.  
⚡ Aplícalo como un ritual, no como una teoría.  
🪶 Tu cuerpo no quiere perfección, quiere presencia.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('5f23c77bcb204877b2816d055294cd98', '1724d86936ba4bf6b059500e26e4a775', '77daffb6ea054c27ba64d523acf96842', 'Audioterapia 5 | SANA TU DOLOR / ENFERMEDAD', 26, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('d35b290850474f63b23f216330e81951', '1724d86936ba4bf6b059500e26e4a775', '7ecea71627504efea568b308a40ae94a', 'HOJA DE RUTA I SEMANA 6', 27, '<figure><img src="1724d86936ba4bf6b059500e26e4a775/assets/6e382eba471e-d9b54801e2b54bf5bdeda3fbd4eaada431041722.bin" alt="image.png" loading="lazy" /></figure>
<p><br /></p>', '![image.png](1724d86936ba4bf6b059500e26e4a775/assets/6e382eba471e-d9b54801e2b54bf5bdeda3fbd4eaada431041722.bin)', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('6f0d9ec1cdd0471ca4fe754c83733254', '1724d86936ba4bf6b059500e26e4a775', '7ecea71627504efea568b308a40ae94a', 'MASTERCLASS 6 | PODER CREADOR', 28, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/--Gkf0ZnPho', 'https://i.ytimg.com/vi/--Gkf0ZnPho/hqdefault.jpg', 7000000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('ed92b2d5d6984170a422d10e29e6c78c', '1724d86936ba4bf6b059500e26e4a775', '7ecea71627504efea568b308a40ae94a', 'E-BOOK (SEMANA 6)', 29, '<p>Esta semana no es sobre cambiar tu vida.<br />Es sobre <strong>recordar que tú la estás creando.</strong></p>
<p>Si estás reaccionando ante todo, es porque <strong>cediste tu poder</strong> sin darte cuenta.<br />Hoy es el momento de recuperarlo.</p>
<p>🌀 Ya puedes descargar el eBook de esta semana:<br /><strong>“Recupera tu Poder Creador: Deja de Reaccionar y Aprende a Crear”</strong></p>
<p>📘 En él encontrarás:<br />✔️ Un test revelador para medir tu poder creador<br />✔️ Ejercicios de reconexión energética y respiración sagrada<br />✔️ Preguntas activadoras + ritual diario para volver a tu centro<br />✔️ Bitácora simbólica y checklist práctico</p>
<p>Este no es un eBook más.<br />Es un <strong>despertador de tu soberanía.</strong></p>
<p>Y empieza a <strong>crear desde el alma, no desde la herida.</strong></p>
<p>📎 Descárgalo aquí:<br /></p>', 'Esta semana no es sobre cambiar tu vida.  
Es sobre **recordar que tú la estás creando.**

Si estás reaccionando ante todo, es porque **cediste tu poder** sin darte cuenta.  
Hoy es el momento de recuperarlo.

🌀 Ya puedes descargar el eBook de esta semana:  
**“Recupera tu Poder Creador: Deja de Reaccionar y Aprende a Crear”**

📘 En él encontrarás:  
✔️ Un test revelador para medir tu poder creador  
✔️ Ejercicios de reconexión energética y respiración sagrada  
✔️ Preguntas activadoras + ritual diario para volver a tu centro  
✔️ Bitácora simbólica y checklist práctico

Este no es un eBook más.  
Es un **despertador de tu soberanía.**

Y empieza a **crear desde el alma, no desde la herida.**

📎 Descárgalo aquí:', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('b1259703815e48eea6f628a75318c962', '1724d86936ba4bf6b059500e26e4a775', '7ecea71627504efea568b308a40ae94a', 'Audioterapia 6 | CREA TU REALIDAD', 30, '<figure><img src="1724d86936ba4bf6b059500e26e4a775/assets/85e5291135be-8a3069d5c8dc483085764e07c209af6fb185a701.bin" alt="Documento A4 Portada de Proyecto Lobo Geométrico Azul (1).png" loading="lazy" /></figure>
<p><br /></p>
<p><br /></p>', '![Documento A4 Portada de Proyecto Lobo Geométrico Azul (1).png](1724d86936ba4bf6b059500e26e4a775/assets/85e5291135be-8a3069d5c8dc483085764e07c209af6fb185a701.bin)', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('b6ad919ce7f6413ea7503b317e0d0aab', '1724d86936ba4bf6b059500e26e4a775', 'bc43ecb0127e41f5a20d7ce9d53958ba', 'HOJA DE RUTA I SEMANA 7', 31, '<p>Esta semana el dinero deja de ser un problema… y se convierte en un espejo.<br />No estás aquí para atraer más. Estás aquí para <strong>dejar de rechazar lo que ya te pertenece.</strong></p>
<p>🔓 Ya puedes descargar la <strong>Hoja de Ruta de la Semana 7</strong>:</p>
<p>📘 Incluye:<br />✔️ Resumen simbólico de la semana<br />✔️ Recursos disponibles (ebook y masterclass)<br />✔️ Actividades esenciales para soltar creencias de escasez<br />✔️ Frases de anclaje para reprogramarte<br />✔️ Ritual diario de reconciliación<br />✔️ Evaluación de avance</p>
<p>✨ Esta semana no se trata de cuánto dinero tienes,<br />sino de <strong>cuánto amor puedes sostener sin sentir culpa.</strong></p>
<p>📎 Descárgala aquí y úsala como guía práctica y emocional: </p>
<p><br /></p>', 'Esta semana el dinero deja de ser un problema… y se convierte en un espejo.  
No estás aquí para atraer más. Estás aquí para **dejar de rechazar lo que ya te pertenece.**

🔓 Ya puedes descargar la **Hoja de Ruta de la Semana 7**:

📘 Incluye:  
✔️ Resumen simbólico de la semana  
✔️ Recursos disponibles (ebook y masterclass)  
✔️ Actividades esenciales para soltar creencias de escasez  
✔️ Frases de anclaje para reprogramarte  
✔️ Ritual diario de reconciliación  
✔️ Evaluación de avance

✨ Esta semana no se trata de cuánto dinero tienes,  
sino de **cuánto amor puedes sostener sin sentir culpa.**

📎 Descárgala aquí y úsala como guía práctica y emocional:', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('7c7bede2e9294993aa0e1272c57a8d79', '1724d86936ba4bf6b059500e26e4a775', 'bc43ecb0127e41f5a20d7ce9d53958ba', 'E-BOOK (SEMANA 7)', 33, '<p>Esta semana no se trata de aprender a ganar más dinero.<br />Se trata de <strong>sanar tu vínculo con él</strong>.</p>
<p>El dinero no es tu enemigo. Es tu espejo.<br />Y todo lo que rechazas en él… refleja algo que aún no has abrazado en ti.</p>
<p>🔑 En este eBook vas a:</p>
<ul><li><p>Identificar las creencias que sabotean tu abundancia</p></li><li><p>Romper lealtades invisibles que te atan al sacrificio</p></li><li><p>Hacer un ritual simbólico de reconciliación con el dinero</p></li><li><p>Activar tu energía de merecimiento desde un lugar consciente</p></li></ul>
<p>📘 Descarga, léelo con apertura, y trabaja cada ejercicio desde tu poder interior.</p>
<p><strong>RENASER no te hace millonario.</strong><br /><strong>Te recuerda que puedes sostener abundancia sin traicionarte.</strong></p>', 'Esta semana no se trata de aprender a ganar más dinero.  
Se trata de **sanar tu vínculo con él**.

El dinero no es tu enemigo. Es tu espejo.  
Y todo lo que rechazas en él… refleja algo que aún no has abrazado en ti.

🔑 En este eBook vas a:

- Identificar las creencias que sabotean tu abundancia
- Romper lealtades invisibles que te atan al sacrificio
- Hacer un ritual simbólico de reconciliación con el dinero
- Activar tu energía de merecimiento desde un lugar consciente

📘 Descarga, léelo con apertura, y trabaja cada ejercicio desde tu poder interior.

**RENASER no te hace millonario.**  
**Te recuerda que puedes sostener abundancia sin traicionarte.**', NULL, NULL, NULL, NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('ec7615cad7c34012915d3fca618b620a', '1724d86936ba4bf6b059500e26e4a775', 'c6a59ee82ffc4dc28d542689c467814f', 'MASTERCLASS 8 | DESPIERTA TU ARQUETIPO DE GUERRERO', 34, '<p><br /></p>', NULL, 'YOUTUBE', 'https://www.youtube.com/watch?v=q9yYMN2-8fE', 'https://i.ytimg.com/vi/q9yYMN2-8fE/hqdefault.jpg', 5377000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('841d69b47f7e431f9f19b37d6b213dff', '1724d86936ba4bf6b059500e26e4a775', 'c4e2ddd279174e85a49097d47d275c76', 'MASTERCLASS 9 | MENTALIDAD DE CEO', 35, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/liVI5k6WWes', 'https://i.ytimg.com/vi/liVI5k6WWes/hqdefault.jpg', 5917000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('06a67090d92d47e38a16c2360622d97e', '1724d86936ba4bf6b059500e26e4a775', 'cbe645923a574f58be5f90c4fb956a88', 'Porque tu negocio esta estancado?', 36, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/-xNVvKS5GQo', 'https://i.ytimg.com/vi/-xNVvKS5GQo/hqdefault.jpg', 280000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('03cea3fcce534eb38ec3d6669f77a35d', '1724d86936ba4bf6b059500e26e4a775', 'cbe645923a574f58be5f90c4fb956a88', 'Construye un negocio de alto rendimiento', 37, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/1O6xI6GYMPk', 'https://i.ytimg.com/vi/1O6xI6GYMPk/hqdefault.jpg', 7893000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('d1c5d784abbe472793f9a608dae385fc', '1724d86936ba4bf6b059500e26e4a775', 'cbe645923a574f58be5f90c4fb956a88', 'Despertar de consciencia', 38, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/myFPTlWN79Q', 'https://i.ytimg.com/vi/myFPTlWN79Q/hqdefault.jpg', 2384000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('4f65d7c29462456a96749df8f191376c', '1724d86936ba4bf6b059500e26e4a775', '9e4fa1337eac4a27b665430247b54f21', 'Alcoholismo | Codependencia emocional', 39, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/5PERNn_0LaI', 'https://i.ytimg.com/vi/5PERNn_0LaI/hqdefault.jpg', 2331000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('7540c6b9af3d4f4ca91dabf4da69ddfd', '1724d86936ba4bf6b059500e26e4a775', '9e4fa1337eac4a27b665430247b54f21', '002 Inseguridades | miedos', 40, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/7F4-smOobDY', 'https://i.ytimg.com/vi/7F4-smOobDY/hqdefault.jpg', 3495000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('569408bb230d492d97eeae2e713269d7', '1724d86936ba4bf6b059500e26e4a775', '9e4fa1337eac4a27b665430247b54f21', '003 | Conflicto con mi hijo', 41, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/niJXle_6b9Q', 'https://i.ytimg.com/vi/niJXle_6b9Q/hqdefault.jpg', 2716000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('4e4a0cbfa3ce472885a767226ed3e8c1', '1724d86936ba4bf6b059500e26e4a775', '30f1561529014a8eaca5645dacc5dd4e', 'Culpa y Feminidad', 42, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/U5DD283paf0', 'https://i.ytimg.com/vi/U5DD283paf0/hqdefault.jpg', 3282000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('71cd8a5f2b704aae926ee68849a9d8c1', '1724d86936ba4bf6b059500e26e4a775', '30f1561529014a8eaca5645dacc5dd4e', 'Me siento culpable', 43, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/1HzqG6-F-Ss', 'https://i.ytimg.com/vi/1HzqG6-F-Ss/hqdefault.jpg', 2498000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('fd8a7e53c224465394acaa3a56ffab7f', '1724d86936ba4bf6b059500e26e4a775', '30f1561529014a8eaca5645dacc5dd4e', 'Miedos inseguridades', 44, '<p><br /></p>', NULL, 'YOUTUBE', 'https://youtu.be/dp-TtpOMZRI', 'https://i.ytimg.com/vi/dp-TtpOMZRI/hqdefault.jpg', 1543000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('45073b1fa81c4a888d5058f9a227971e', '6be9cefa706e40049bae44c38194be29', '18812662a20a4252a1fc1609b92f9164', 'ABUNDANCIA Y CARENCIA', 0, '<p><em>Mentoría realizada el día 26 de mayo del 2026</em><br /><br />Tener dinero no significa tener abundancia.<br />Puedes tener mucho… y seguir vacío.</p>
<p>En este video vas a entender por qué la verdadera riqueza no nace desde la carencia, sino desde una nueva identidad. Para crear dinero y abundancia necesitas dejar de perseguir, dejar de mendigar y empezar a convertirte en una persona plena, estratégica y creadora.</p>
<p>Aprenderás cómo cambiar tu relación con el dinero, cómo dejar de actuar desde la escasez y por qué RENASER empieza cuando decides construir una nueva versión de ti.</p>', '_Mentoría realizada el día 26 de mayo del 2026_  
  
Tener dinero no significa tener abundancia.  
Puedes tener mucho… y seguir vacío.

En este video vas a entender por qué la verdadera riqueza no nace desde la carencia, sino desde una nueva identidad. Para crear dinero y abundancia necesitas dejar de perseguir, dejar de mendigar y empezar a convertirte en una persona plena, estratégica y creadora.

Aprenderás cómo cambiar tu relación con el dinero, cómo dejar de actuar desde la escasez y por qué RENASER empieza cuando decides construir una nueva versión de ti.', 'YOUTUBE', 'https://youtu.be/su5Y-qvYhdo', 'https://i.ytimg.com/vi/su5Y-qvYhdo/hqdefault.jpg', NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('cb97edbac4c94afe8ebdf23bc546f705', '6be9cefa706e40049bae44c38194be29', '18812662a20a4252a1fc1609b92f9164', 'ESTRUCTURA & SISTEMATIZACIÓN', 1, '<p><em>Mentoría realizada el día 19 de mayo del 2026</em></p>', '_Mentoría realizada el día 19 de mayo del 2026_', 'YOUTUBE', 'https://youtu.be/bBHvNV1b2NY', 'https://i.ytimg.com/vi/bBHvNV1b2NY/maxresdefault.jpg', 11131000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('d0693ddb0e2743218282065f37b59f85', '6be9cefa706e40049bae44c38194be29', '18812662a20a4252a1fc1609b92f9164', 'CÓMO DEJAR DE SABOTEARTE Y CONSTRUIRTE DE VERDAD', 2, '<p><em>Mentoría realizada el día 05 de mayo del 2026</em></p>', '_Mentoría realizada el día 05 de mayo del 2026_', 'YOUTUBE', 'https://youtu.be/j9dGMePKYFU', 'https://i.ytimg.com/vi/j9dGMePKYFU/maxresdefault.jpg', 10215000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('10ff73f2e3eb48f1a264801a1392e7bc', 'cfc4c4334474498b9f6c9c47800fdecf', '4424058971c4467da1424857973e54f5', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('eaf1fada3bcb4863879901da3ec6484e', 'cfc4c4334474498b9f6c9c47800fdecf', '4424058971c4467da1424857973e54f5', 'MASTERCLASS', 21, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('9e23a184818d4edbb8cc13c56952e2ca', '8405f8f0128a46f59f155d1548ffbf8b', 'e20ac203316a4ce8b21551164bf7db34', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('d52b39ca6a5a43f0bae0cfeba375d52d', '8405f8f0128a46f59f155d1548ffbf8b', 'e20ac203316a4ce8b21551164bf7db34', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('606ca36e3a574189951a0e24a34d5812', '8405f8f0128a46f59f155d1548ffbf8b', 'e20ac203316a4ce8b21551164bf7db34', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('f2c5f9283ed446ef9936a24aa92ab5e4', '6be9cefa706e40049bae44c38194be29', '577783b920cc42848dc97280e2ebda4a', 'CÓMO REPROGRAMAR TU MENTE PARA TENER RESULTADOS', 3, '<p><em>Mentoría realizada el día 28 de abril del 2026</em><br /><br />Tu mente no es neutral: o crea… o destruye.<br />Y ahora mismo está definiendo toda tu vida.</p>
<p>En este video vas a descubrir cómo tus pensamientos, tu lenguaje y tu enfoque están moldeando tus resultados, y cómo tomar control real para dejar de sabotearte.</p>
<p>Aprenderás:</p>
<ul><li><p>Cómo identificar pensamientos que te limitan</p></li><li><p>Por qué tu lenguaje define tu realidad</p></li><li><p>Cómo crear estructura, enfoque y resultados reales</p></li></ul>
<p>Si no controlas tu mente, ella te controla</p>', '_Mentoría realizada el día 28 de abril del 2026_  
  
Tu mente no es neutral: o crea… o destruye.  
Y ahora mismo está definiendo toda tu vida.

En este video vas a descubrir cómo tus pensamientos, tu lenguaje y tu enfoque están moldeando tus resultados, y cómo tomar control real para dejar de sabotearte.

Aprenderás:

- Cómo identificar pensamientos que te limitan
- Por qué tu lenguaje define tu realidad
- Cómo crear estructura, enfoque y resultados reales

Si no controlas tu mente, ella te controla', 'YOUTUBE', 'https://youtu.be/oaPeDSQE3Ec', 'https://i.ytimg.com/vi/oaPeDSQE3Ec/maxresdefault.jpg', 11739000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('9ff848693bdb43ccb7558d8a4b13eddd', '6be9cefa706e40049bae44c38194be29', '577783b920cc42848dc97280e2ebda4a', 'CONVIÉRTETE EN UNA PERSONA ALTAMENTE PRODUCTIVA', 4, '<p><em>Mentoría realizada el día 21 de abril del 2026</em><br /><br />No te falta dinero… te está faltando tiempo bien usado.<br />Y eso está destruyendo tu vida sin que lo notes.</p>
<p>En este video vas a entender por qué estás estancado, por qué sientes frustración constante y cuál es el verdadero recurso que define tu éxito o tu fracaso.</p>
<p>Aprenderás:</p>
<ul><li><p>Por qué el tiempo es más valioso que el dinero</p></li><li><p>Cómo estás desperdiciando tu vida sin darte cuenta</p></li><li><p>Cómo convertirte en una persona altamente productiva</p></li></ul>
<p>Si no aprendes a dominar tu tiempo, nada va a cambiar.</p>', '_Mentoría realizada el día 21 de abril del 2026_  
  
No te falta dinero… te está faltando tiempo bien usado.  
Y eso está destruyendo tu vida sin que lo notes.

En este video vas a entender por qué estás estancado, por qué sientes frustración constante y cuál es el verdadero recurso que define tu éxito o tu fracaso.

Aprenderás:

- Por qué el tiempo es más valioso que el dinero
- Cómo estás desperdiciando tu vida sin darte cuenta
- Cómo convertirte en una persona altamente productiva

Si no aprendes a dominar tu tiempo, nada va a cambiar.', 'YOUTUBE', 'https://youtu.be/tm2mIHI-krg', 'https://i.ytimg.com/vi/tm2mIHI-krg/maxresdefault.jpg', 13867000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('677f277a198e4370a2b052e1ef3a68ed', '6be9cefa706e40049bae44c38194be29', '577783b920cc42848dc97280e2ebda4a', 'CREA SISTEMAS ORDENA TU VIDA', 5, '<p><em>Mentoría realizada el día 14 de abril del 2026</em><br /><br />No te falta disciplina… te faltan sistemas. Y por eso tu vida sigue en caos.</p>
<p>En este video vas a entender por qué no logras sostener resultados, cómo salir del desorden y cómo crear sistemas simples que automaticen tu vida y tus resultados.</p>
<p>Aprenderás:</p>
<ul><li><p>Por qué la motivación no funciona a largo plazo</p></li><li><p>Cómo crear sistemas que sostengan tu disciplina</p></li><li><p>Cómo pasar de caos a orden y luego a crecimiento</p></li></ul>
<p>Si no estructuras tu vida, siempre vas a empezar de cero.</p>', '_Mentoría realizada el día 14 de abril del 2026_  
  
No te falta disciplina… te faltan sistemas. Y por eso tu vida sigue en caos.

En este video vas a entender por qué no logras sostener resultados, cómo salir del desorden y cómo crear sistemas simples que automaticen tu vida y tus resultados.

Aprenderás:

- Por qué la motivación no funciona a largo plazo
- Cómo crear sistemas que sostengan tu disciplina
- Cómo pasar de caos a orden y luego a crecimiento

Si no estructuras tu vida, siempre vas a empezar de cero.', 'YOUTUBE', 'https://youtu.be/RenmkjjXClo', 'https://i.ytimg.com/vi/RenmkjjXClo/maxresdefault.jpg', 10045000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('c88ae940cf8447eb8165d24018deddf1', '6be9cefa706e40049bae44c38194be29', '577783b920cc42848dc97280e2ebda4a', 'EL ERROR QUE TE MANTIENE POBRE SIN DARTE CUENTA', 6, '<p><em>Mentoría realizada el día 07 de abril del 2026</em><br /><br />No es falta de dinero… es falta de valor.<br />Y hasta que no entiendas esto, seguirás en escasez.</p>
<p>En este video vas a descubrir cómo funciona realmente el dinero, por qué algunas personas multiplican resultados y otras se quedan estancadas, y cuál es el principio clave que define tu nivel económico.</p>
<p>Aprenderás:</p>
<ul><li><p>Por qué el dinero sigue al valor (no al esfuerzo)</p></li><li><p>Cómo cambiar tu percepción para generar abundancia</p></li><li><p>Qué está bloqueando tus ingresos hoy</p></li></ul>
<p>Si quieres ganar más, primero tienes que ver diferente.</p>', '_Mentoría realizada el día 07 de abril del 2026_  
  
No es falta de dinero… es falta de valor.  
Y hasta que no entiendas esto, seguirás en escasez.

En este video vas a descubrir cómo funciona realmente el dinero, por qué algunas personas multiplican resultados y otras se quedan estancadas, y cuál es el principio clave que define tu nivel económico.

Aprenderás:

- Por qué el dinero sigue al valor (no al esfuerzo)
- Cómo cambiar tu percepción para generar abundancia
- Qué está bloqueando tus ingresos hoy

Si quieres ganar más, primero tienes que ver diferente.', 'YOUTUBE', 'https://youtu.be/i8MUmPxhxnc', 'https://i.ytimg.com/vi/i8MUmPxhxnc/maxresdefault.jpg', 12207000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('996a46ca9de44ca4a9558a165124eb37', '6be9cefa706e40049bae44c38194be29', '3a5ae9ac50774e93984631ff8010e081', 'TE ESTAS MINTIENDO Y POR ESO NO AVANZAS', 7, '<p><em>Mentoría realizada el día 31 de marzo del 2026</em><br /><br />No es que no puedas… es que te estás mintiendo.<br />Y esa mentira está destruyendo tus resultados.</p>
<p>En este video vas a descubrir por qué no eres constante, por qué abandonas lo que empiezas y cuál es la raíz real de tu falta de disciplina.</p>
<p>Aprenderás:</p>
<ul><li><p>Por qué tu mente sabotea tus objetivos</p></li><li><p>El verdadero motivo detrás de tu inconsistencia</p></li><li><p>Cómo alinear tu verdad para lograr resultados reales</p></li></ul>
<p>Si quieres cambiar tu vida, tienes que empezar por esto.</p>', '_Mentoría realizada el día 31 de marzo del 2026_  
  
No es que no puedas… es que te estás mintiendo.  
Y esa mentira está destruyendo tus resultados.

En este video vas a descubrir por qué no eres constante, por qué abandonas lo que empiezas y cuál es la raíz real de tu falta de disciplina.

Aprenderás:

- Por qué tu mente sabotea tus objetivos
- El verdadero motivo detrás de tu inconsistencia
- Cómo alinear tu verdad para lograr resultados reales

Si quieres cambiar tu vida, tienes que empezar por esto.', 'YOUTUBE', 'https://youtu.be/U2VGzYiQHX0', 'https://i.ytimg.com/vi/U2VGzYiQHX0/maxresdefault.jpg', 7972000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('e239bafb25e54a9ca8dd0f8e3f85c34b', '8405f8f0128a46f59f155d1548ffbf8b', '11474d55a9674740b1f31aba6df604fe', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('ebf05e80ca824200bd43ee3ac34e2237', '8405f8f0128a46f59f155d1548ffbf8b', '11474d55a9674740b1f31aba6df604fe', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e7dbf8d9be5e4524a2b72d3870d1510c', '8405f8f0128a46f59f155d1548ffbf8b', '11474d55a9674740b1f31aba6df604fe', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('ad92c2c1747e4fdfbf4da348406a006e', '8405f8f0128a46f59f155d1548ffbf8b', '62347d2a9876408ebabff48672f390c0', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('9af3bcdf980c4869bc3f4b2cbde44e10', '6be9cefa706e40049bae44c38194be29', '3a5ae9ac50774e93984631ff8010e081', 'NO SOY POBRE SOY INGRAT@', 8, '<p><em>Mentoría realizada el día 24 de marzo del 2026</em><br /><br />No es falta de dinero… es algo mucho más profundo.<br />Y hasta que no lo veas, nada va a cambiar.</p>
<p>En este video vas a entender por qué realmente no estás generando resultados económicos, qué está bloqueando tu crecimiento y cómo empezar a transformar tu realidad desde hoy.</p>
<p>Descubrirás:</p>
<ul><li><p>El error mental que te mantiene en escasez</p></li><li><p>Por qué estás rechazando oportunidades sin darte cuenta</p></li><li><p>Cómo empezar a generar valor real y dinero</p></li></ul>
<p>Si quieres resultados diferentes, necesitas ver esto.</p>', '_Mentoría realizada el día 24 de marzo del 2026_  
  
No es falta de dinero… es algo mucho más profundo.  
Y hasta que no lo veas, nada va a cambiar.

En este video vas a entender por qué realmente no estás generando resultados económicos, qué está bloqueando tu crecimiento y cómo empezar a transformar tu realidad desde hoy.

Descubrirás:

- El error mental que te mantiene en escasez
- Por qué estás rechazando oportunidades sin darte cuenta
- Cómo empezar a generar valor real y dinero

Si quieres resultados diferentes, necesitas ver esto.', 'YOUTUBE', 'https://youtu.be/3pmVr6a1XGg', 'https://i.ytimg.com/vi/3pmVr6a1XGg/maxresdefault.jpg', 10480000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('715ab8fc1bf1449288fdb8dcaa849112', '6be9cefa706e40049bae44c38194be29', '3a5ae9ac50774e93984631ff8010e081', 'EL PROBLEMA NO ES EL PASADO ES QUE NO LO CERRASTE', 9, '<p><em>Mentoría realizada el día 17 de marzo del 2026</em><br /><br />En esta mentoría vas a entender por qué el duelo no solo ocurre cuando pierdes a alguien, sino cuando dejas atrás una versión de ti mismo.</p>
<p>Aprenderás:</p>
<ul><li><p>Por qué te sientes vacío cuando cambias</p></li><li><p>Cómo identificar patrones que repites sin darte cuenta</p></li><li><p>La verdadera razón detrás de las relaciones tóxicas</p></li><li><p>Por qué no cerrar ciclos está destruyendo tu crecimiento</p></li><li><p>Cómo soltar sin caer en el autoengaño</p></li></ul>
<p>Este contenido no es motivación superficial. Es confrontación directa con lo que te está frenando.</p>', '_Mentoría realizada el día 17 de marzo del 2026_  
  
En esta mentoría vas a entender por qué el duelo no solo ocurre cuando pierdes a alguien, sino cuando dejas atrás una versión de ti mismo.

Aprenderás:

- Por qué te sientes vacío cuando cambias
- Cómo identificar patrones que repites sin darte cuenta
- La verdadera razón detrás de las relaciones tóxicas
- Por qué no cerrar ciclos está destruyendo tu crecimiento
- Cómo soltar sin caer en el autoengaño

Este contenido no es motivación superficial. Es confrontación directa con lo que te está frenando.', 'YOUTUBE', 'https://youtu.be/Zyoh2NP61K8', 'https://i.ytimg.com/vi/Zyoh2NP61K8/maxresdefault.jpg', 9683000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('4e5d4d1c7a81482694f742e892f63306', '6be9cefa706e40049bae44c38194be29', '3a5ae9ac50774e93984631ff8010e081', 'EL MACACO QUE ARRUINA TU VIDA', 10, '<p><em>Mentoría realizada el día 10 de marzo del 2026</em><br /><br />No te falta potencial.<br />Te está gobernando una parte de ti que evita soltar, incomodarse y ejecutar.</p>
<p>En este video vas a descubrir por qué muchas personas no avanzan, no porque les falten herramientas, sino porque siguen obedeciendo patrones inconscientes que frenan su crecimiento.</p>
<p>Aquí hablamos de la “mente macaca”: esa parte primitiva que busca placer inmediato, evita el dolor, posterga decisiones y sabotea tu evolución sin que te des cuenta.</p>
<p>Vas a entender:</p>
<ul><li><p>por qué sigues estancado aunque sabes mucho</p></li><li><p>qué tipo de autosabotaje domina tu vida hoy</p></li><li><p>cómo identificar el patrón que te está frenando</p></li><li><p>por qué soltar duele, pero no soltar te cuesta más</p></li><li><p>cómo empezar a actuar desde una identidad más fuerte</p></li></ul>
<p>Este contenido te va a confrontar, pero también te va a dar claridad. Porque a veces el problema no es que no puedas avanzar… sino que sigues escuchando la voz equivocada dentro de ti.<br /></p>', '_Mentoría realizada el día 10 de marzo del 2026_  
  
No te falta potencial.  
Te está gobernando una parte de ti que evita soltar, incomodarse y ejecutar.

En este video vas a descubrir por qué muchas personas no avanzan, no porque les falten herramientas, sino porque siguen obedeciendo patrones inconscientes que frenan su crecimiento.

Aquí hablamos de la “mente macaca”: esa parte primitiva que busca placer inmediato, evita el dolor, posterga decisiones y sabotea tu evolución sin que te des cuenta.

Vas a entender:

- por qué sigues estancado aunque sabes mucho
- qué tipo de autosabotaje domina tu vida hoy
- cómo identificar el patrón que te está frenando
- por qué soltar duele, pero no soltar te cuesta más
- cómo empezar a actuar desde una identidad más fuerte

Este contenido te va a confrontar, pero también te va a dar claridad. Porque a veces el problema no es que no puedas avanzar… sino que sigues escuchando la voz equivocada dentro de ti.', 'YOUTUBE', 'https://youtu.be/vP0HIFK9AjA', 'https://i.ytimg.com/vi/vP0HIFK9AjA/maxresdefault.jpg', 6885000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('6b6ab6d1629342eb94b9c392e42f3a2c', '6be9cefa706e40049bae44c38194be29', '3a5ae9ac50774e93984631ff8010e081', 'RESTRUCTURA TU IDENTIDAD', 11, '<p><em>Mentoría realizada el día 03 de marzo del 2026</em><br /><br />En este video vas a descubrir <strong>la raíz real del cambio personal</strong>:<br />la identidad.</p>
<p>No importa cuántos libros leas, terapias hagas o metas te pongas…<br />si tu identidad sigue siendo la misma, tu vida seguirá repitiendo los mismos patrones.</p>
<p>Aquí aprenderás:</p>
<ul><li><p>Por qué atraes siempre los mismos problemas</p></li><li><p>Qué significa realmente <strong>reestructurar tu identidad</strong></p></li><li><p>Cómo modelar el éxito de otras personas</p></li><li><p>Por qué la motivación no cambia tu vida</p></li><li><p>El método para dar <strong>saltos cuánticos en resultados</strong></p></li></ul>
<p>Si quieres crecer en dinero, relaciones, salud o liderazgo, este video puede cambiar la forma en la que entiendes el desarrollo personal.</p>', '_Mentoría realizada el día 03 de marzo del 2026_  
  
En este video vas a descubrir **la raíz real del cambio personal**:  
la identidad.

No importa cuántos libros leas, terapias hagas o metas te pongas…  
si tu identidad sigue siendo la misma, tu vida seguirá repitiendo los mismos patrones.

Aquí aprenderás:

- Por qué atraes siempre los mismos problemas
- Qué significa realmente **reestructurar tu identidad**
- Cómo modelar el éxito de otras personas
- Por qué la motivación no cambia tu vida
- El método para dar **saltos cuánticos en resultados**

Si quieres crecer en dinero, relaciones, salud o liderazgo, este video puede cambiar la forma en la que entiendes el desarrollo personal.', 'YOUTUBE', 'https://youtu.be/rzf0DSWEMbM', 'https://i.ytimg.com/vi/rzf0DSWEMbM/maxresdefault.jpg', 9635000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('47884bcbdda9434488c34b6c2f2a7f6b', '6be9cefa706e40049bae44c38194be29', 'ee772609496146a1abad12a1a16b1fcd', 'ABRAZA TU OSCURIDAD', 12, '<p><em>Mentoría realizada el día 24 de febrero del 2026</em></p>
<p>En esta mentoría se desarrolla una idea central del sistema RENASER: <strong>La grandeza personal nace cuando una persona deja de negar su oscuridad y aprende a canalizarla conscientemente</strong>. Se plantea que la seguridad, el liderazgo y la claridad no surgen de intentar ser “más positivos”, sino de reconocer los miedos, culpas, frustraciones y contradicciones internas que normalmente se esconden.</p>
<p>A través de ejemplos históricos, psicológicos y cotidianos —como la historia de Siddhartha Gautama, relaciones de pareja, liderazgo empresarial y conflictos personales— se explica que todo en el universo posee dos caras: luz y oscuridad. Cuando una persona solo quiere mostrarse “buena”, termina actuando desde la hipocresía del ego, negando su propia sombra y proyectándola en los demás.</p>
<p>La mentoría profundiza en cómo este rechazo a la oscuridad genera victimismo, culpa, conflictos en relaciones, sabotaje en negocios y falta de liderazgo. Se enseña que muchas frustraciones surgen porque las personas evitan conversaciones incómodas, temen ejercer autoridad o intentan agradar a todos, negando aspectos de su carácter que también necesitan existir.</p>
<p>Se propone un proceso de conciencia para identificar aquello que genera enojo o rechazo en otros, comprender que esos elementos reflejan partes internas no integradas y aprender a transmutarlos en fuerza personal. Desde esta perspectiva, aceptar la propia sombra permite desarrollar carácter, responsabilidad y capacidad de liderazgo.</p>
<p>El mensaje final es claro: <strong>La verdadera libertad no se alcanza negando la oscuridad, sino integrándola.</strong> Cuando una persona reconoce su sombra, deja de vivir desde el victimismo, asume responsabilidad total por su vida y comienza a actuar con claridad, poder y autenticidad.</p>', '_Mentoría realizada el día 24 de febrero del 2026_

En esta mentoría se desarrolla una idea central del sistema RENASER: **La grandeza personal nace cuando una persona deja de negar su oscuridad y aprende a canalizarla conscientemente**. Se plantea que la seguridad, el liderazgo y la claridad no surgen de intentar ser “más positivos”, sino de reconocer los miedos, culpas, frustraciones y contradicciones internas que normalmente se esconden.

A través de ejemplos históricos, psicológicos y cotidianos —como la historia de Siddhartha Gautama, relaciones de pareja, liderazgo empresarial y conflictos personales— se explica que todo en el universo posee dos caras: luz y oscuridad. Cuando una persona solo quiere mostrarse “buena”, termina actuando desde la hipocresía del ego, negando su propia sombra y proyectándola en los demás.

La mentoría profundiza en cómo este rechazo a la oscuridad genera victimismo, culpa, conflictos en relaciones, sabotaje en negocios y falta de liderazgo. Se enseña que muchas frustraciones surgen porque las personas evitan conversaciones incómodas, temen ejercer autoridad o intentan agradar a todos, negando aspectos de su carácter que también necesitan existir.

Se propone un proceso de conciencia para identificar aquello que genera enojo o rechazo en otros, comprender que esos elementos reflejan partes internas no integradas y aprender a transmutarlos en fuerza personal. Desde esta perspectiva, aceptar la propia sombra permite desarrollar carácter, responsabilidad y capacidad de liderazgo.

El mensaje final es claro: **La verdadera libertad no se alcanza negando la oscuridad, sino integrándola.** Cuando una persona reconoce su sombra, deja de vivir desde el victimismo, asume responsabilidad total por su vida y comienza a actuar con claridad, poder y autenticidad.', 'YOUTUBE', 'https://youtu.be/YpmxdGA3hJE', 'https://i.ytimg.com/vi/YpmxdGA3hJE/maxresdefault.jpg', 9833000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('3a5d86fbb1624e49a1cf272727aab26e', '8405f8f0128a46f59f155d1548ffbf8b', '62347d2a9876408ebabff48672f390c0', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('0e3046d46ed747b88f3f6f93397706f5', '6be9cefa706e40049bae44c38194be29', 'ee772609496146a1abad12a1a16b1fcd', 'LA RAREZA QUE TE HACE RICO', 13, '<p><em>Mentoría realizada el día 17 de febrero del 2026</em></p>
<p>En esta mentoría se explora una idea poderosa sobre la relación entre identidad, expresión y dinero. La sesión explica que muchas personas desean ganar más dinero, pero permanecen bloqueadas porque reprimen su esencia, se avergüenzan de sus imperfecciones y viven intentando encajar en lo “normal”. El enfoque plantea que la verdadera abundancia surge cuando una persona reconoce su poder creador y aprende a expresarse auténticamente.</p>
<p>Durante la sesión se desarrollan tres principios fundamentales para generar dinero desde la esencia personal. El primero es <strong>expresar</strong>, entendiendo que todo creador que prospera aprende a comunicar, mostrar y compartir su valor con el mundo. El segundo es <strong>honrar las imperfecciones</strong>, transformando aquello que antes generaba vergüenza —como la forma de hablar, la personalidad o las experiencias difíciles— en una ventaja única que diferencia a la persona del resto. Y el tercero es <strong>mostrar con grandeza lo que sabes, lo que haces o lo que eres</strong>, convirtiendo ese valor en oportunidades reales de ingresos.</p>
<p>La mentoría también aborda cómo el miedo al juicio, la autocrítica y el “macaco interior” suelen sabotear el crecimiento personal y financiero. A través de ejercicios de conciencia, ejemplos reales y reflexiones sobre la autenticidad, se invita a dejar de esconderse, crear una marca personal y compartir el propio valor sin miedo.</p>
<p>El mensaje central es claro: <strong>La riqueza no surge de imitar al rebaño, sino de abrazar la propia rareza y convertirla en valor para el mundo.</strong> Cuando una persona deja de luchar contra sí misma, expresa su esencia con confianza y se atreve a mostrarse, el dinero comienza a llegar como consecuencia natural de esa autenticidad.</p>', '_Mentoría realizada el día 17 de febrero del 2026_

En esta mentoría se explora una idea poderosa sobre la relación entre identidad, expresión y dinero. La sesión explica que muchas personas desean ganar más dinero, pero permanecen bloqueadas porque reprimen su esencia, se avergüenzan de sus imperfecciones y viven intentando encajar en lo “normal”. El enfoque plantea que la verdadera abundancia surge cuando una persona reconoce su poder creador y aprende a expresarse auténticamente.

Durante la sesión se desarrollan tres principios fundamentales para generar dinero desde la esencia personal. El primero es **expresar**, entendiendo que todo creador que prospera aprende a comunicar, mostrar y compartir su valor con el mundo. El segundo es **honrar las imperfecciones**, transformando aquello que antes generaba vergüenza —como la forma de hablar, la personalidad o las experiencias difíciles— en una ventaja única que diferencia a la persona del resto. Y el tercero es **mostrar con grandeza lo que sabes, lo que haces o lo que eres**, convirtiendo ese valor en oportunidades reales de ingresos.

La mentoría también aborda cómo el miedo al juicio, la autocrítica y el “macaco interior” suelen sabotear el crecimiento personal y financiero. A través de ejercicios de conciencia, ejemplos reales y reflexiones sobre la autenticidad, se invita a dejar de esconderse, crear una marca personal y compartir el propio valor sin miedo.

El mensaje central es claro: **La riqueza no surge de imitar al rebaño, sino de abrazar la propia rareza y convertirla en valor para el mundo.** Cuando una persona deja de luchar contra sí misma, expresa su esencia con confianza y se atreve a mostrarse, el dinero comienza a llegar como consecuencia natural de esa autenticidad.', 'YOUTUBE', 'https://youtu.be/ZKEiRgyUJI8', 'https://i.ytimg.com/vi/ZKEiRgyUJI8/maxresdefault.jpg', 7785000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('d14b9fbc45724f4984a8f83f9edc7b3a', '6be9cefa706e40049bae44c38194be29', 'ee772609496146a1abad12a1a16b1fcd', 'DEJA EL BASURAL Y CONVIERTETE EN CREADOR DE VIDA', 14, '<p><em>Mentoría realizada el día 10 de febrero del 2026</em></p>
<p>Una mentoría profunda que confronta directamente la mentalidad de víctima y enseña a interpretar los problemas como señales del universo para despertar el poder creador interior. A través de reflexiones, casos reales y ejercicios de consciencia, se revela cómo muchas personas permanecen atrapadas en su “basural emocional”: viejas heridas, creencias limitantes y hábitos de pensamiento que perpetúan una vida mediocre.</p>
<p>La sesión explora la diferencia entre reaccionar como un “macaco” —desde el miedo, la queja y la supervivencia— o despertar como un creador capaz de transformar cualquier experiencia en crecimiento, libertad y abundancia. También se introduce el principio de que el universo constantemente envía mensajes a través de las personas, los conflictos y las circunstancias, y que cada problema puede convertirse en una puerta hacia una vida más plena.</p>
<p>El mensaje central es claro: <strong>Renunciar al basural mental y elegir conscientemente una vida extraordinaria</strong>, desarrollando responsabilidad personal, visión abundante y la convicción de que cada persona puede crear una realidad más grande de la que ha vivido hasta ahora. Una invitación directa a dejar la mediocridad interior y empezar a vivir con grandeza.</p>', '_Mentoría realizada el día 10 de febrero del 2026_

Una mentoría profunda que confronta directamente la mentalidad de víctima y enseña a interpretar los problemas como señales del universo para despertar el poder creador interior. A través de reflexiones, casos reales y ejercicios de consciencia, se revela cómo muchas personas permanecen atrapadas en su “basural emocional”: viejas heridas, creencias limitantes y hábitos de pensamiento que perpetúan una vida mediocre.

La sesión explora la diferencia entre reaccionar como un “macaco” —desde el miedo, la queja y la supervivencia— o despertar como un creador capaz de transformar cualquier experiencia en crecimiento, libertad y abundancia. También se introduce el principio de que el universo constantemente envía mensajes a través de las personas, los conflictos y las circunstancias, y que cada problema puede convertirse en una puerta hacia una vida más plena.

El mensaje central es claro: **Renunciar al basural mental y elegir conscientemente una vida extraordinaria**, desarrollando responsabilidad personal, visión abundante y la convicción de que cada persona puede crear una realidad más grande de la que ha vivido hasta ahora. Una invitación directa a dejar la mediocridad interior y empezar a vivir con grandeza.', 'YOUTUBE', 'https://youtu.be/eTICg4CxiFI', 'https://i.ytimg.com/vi/eTICg4CxiFI/maxresdefault.jpg', 8362000, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('24afddfaf30d4027aa5671acf59e8068', '6be9cefa706e40049bae44c38194be29', 'ee772609496146a1abad12a1a16b1fcd', 'VOLUNTAD U OBLIGACIÓN: DECISIÓN QUE DEFINE TU VIDA', 15, '<p><em>Mentoría realizada el día 03 de febrero del 2026</em></p>
<p>Esta mentoría explora uno de los principios más determinantes del crecimiento personal: <strong>la diferencia entre vivir por obligación o vivir por voluntad</strong>. A través de reflexiones, ejemplos reales y ejercicios prácticos, se revela cómo muchas personas permanecen estancadas no por falta de capacidad, sino porque realizan sus acciones desde la presión, el miedo o la costumbre, en lugar de hacerlo desde una decisión consciente.</p>
<p>La sesión explica cómo cuando una persona actúa por obligación, aparecen el victimismo, el enojo, la procrastinación y los problemas constantes. En cambio, cuando las decisiones nacen desde la voluntad, surge la disciplina natural, la claridad mental, la iniciativa y la capacidad de resolver cualquier desafío.</p>', '_Mentoría realizada el día 03 de febrero del 2026_

Esta mentoría explora uno de los principios más determinantes del crecimiento personal: **la diferencia entre vivir por obligación o vivir por voluntad**. A través de reflexiones, ejemplos reales y ejercicios prácticos, se revela cómo muchas personas permanecen estancadas no por falta de capacidad, sino porque realizan sus acciones desde la presión, el miedo o la costumbre, en lugar de hacerlo desde una decisión consciente.

La sesión explica cómo cuando una persona actúa por obligación, aparecen el victimismo, el enojo, la procrastinación y los problemas constantes. En cambio, cuando las decisiones nacen desde la voluntad, surge la disciplina natural, la claridad mental, la iniciativa y la capacidad de resolver cualquier desafío.', 'YOUTUBE', 'https://youtu.be/iors3I7in4I', 'https://i.ytimg.com/vi/iors3I7in4I/maxresdefault.jpg', NULL, '2026-07-28 21:12:38.840754+00', '2026-07-28 21:16:15.865703+00'),
    ('601391789aa04d30ae83c256c3f7ed5b', '6be9cefa706e40049bae44c38194be29', '92a0052c94b04daaab5c95b7160c172f', 'NUNCA NADIE TE LASTIMA', 17, '<p><em>Mentoría realizada el día 28 de enero del 2026</em></p>
<p>En esta mentoría se presenta uno de los principios más confrontativos del proceso RENASER: <strong>el sufrimiento no es algo que otros nos hacen, sino una realidad que cada persona cocrea desde su propia conciencia</strong>. La sesión explica que el dolor emocional surge cuando entramos en una visión dual de la vida donde existen víctimas y victimarios, buenos y malos, traicionados y traidores. Al aceptar ese juego mental, entramos automáticamente en el “circo del sufrimiento”.</p>
<p>A lo largo de la clase se explora cómo la mente —representada metafóricamente como el “macaco interior”— se alimenta constantemente de tres emociones que mantienen activo el sufrimiento: <strong>miedo, culpa y vergüenza</strong>. Estos tres elementos refuerzan patrones inconscientes que se repiten en relaciones, dinero, salud y decisiones de vida.</p>
<p>La mentoría cuestiona muchas creencias comunes en el desarrollo personal y en la terapia tradicional, mostrando que comprender la historia del dolor no siempre transforma la vida. El verdadero cambio ocurre cuando una persona deja de buscar culpables externos, observa sus propios patrones inconscientes y asume su papel como <strong>cocreador de su realidad</strong>.</p>', '_Mentoría realizada el día 28 de enero del 2026_

En esta mentoría se presenta uno de los principios más confrontativos del proceso RENASER: **el sufrimiento no es algo que otros nos hacen, sino una realidad que cada persona cocrea desde su propia conciencia**. La sesión explica que el dolor emocional surge cuando entramos en una visión dual de la vida donde existen víctimas y victimarios, buenos y malos, traicionados y traidores. Al aceptar ese juego mental, entramos automáticamente en el “circo del sufrimiento”.

A lo largo de la clase se explora cómo la mente —representada metafóricamente como el “macaco interior”— se alimenta constantemente de tres emociones que mantienen activo el sufrimiento: **miedo, culpa y vergüenza**. Estos tres elementos refuerzan patrones inconscientes que se repiten en relaciones, dinero, salud y decisiones de vida.

La mentoría cuestiona muchas creencias comunes en el desarrollo personal y en la terapia tradicional, mostrando que comprender la historia del dolor no siempre transforma la vida. El verdadero cambio ocurre cuando una persona deja de buscar culpables externos, observa sus propios patrones inconscientes y asume su papel como **cocreador de su realidad**.', 'YOUTUBE', 'https://www.youtube.com/watch?v=C4ih_UmrprM&index=2', 'https://i.ytimg.com/vi/C4ih_UmrprM/maxresdefault.jpg', NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('b21443f9bd4b43a6861819195e0fd8ef', '8405f8f0128a46f59f155d1548ffbf8b', '62347d2a9876408ebabff48672f390c0', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('fd149a4cdc654b1b8e2dbaf8b359e282', '8405f8f0128a46f59f155d1548ffbf8b', '7a47a79b40eb43728db907178162ded5', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('d6556116fc9847269630dece5234d762', '8405f8f0128a46f59f155d1548ffbf8b', '7a47a79b40eb43728db907178162ded5', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('88169815c8094642af1e9b51d9dc6a8c', '8405f8f0128a46f59f155d1548ffbf8b', '7a47a79b40eb43728db907178162ded5', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('267c583e8c8a46e6a289c3a0d374fca3', '8405f8f0128a46f59f155d1548ffbf8b', 'bf4983a0d30b48f6bda54c65fb3e4e78', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('ab06f9e9c0324227974fef0585f88917', 'a11488afc79643cf87759dece4f9451d', 'c71a6d387a4649c389d68f53ba87315f', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('a2b51e6153234e05b01122e9ea39ffdb', 'a11488afc79643cf87759dece4f9451d', 'c71a6d387a4649c389d68f53ba87315f', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('1caa47beb9b14a00811c89dfd25f729d', '6be9cefa706e40049bae44c38194be29', '92a0052c94b04daaab5c95b7160c172f', 'HONRA TU VERDAD: PRINCIPIO PARA SANAR Y RENACER', 18, '<p><em>Mentoría realizada el día 27 de enero del 2026</em></p>
<p>En esta sesión se presenta uno de los pilares fundamentales del proceso de transformación personal: <strong>honrar la verdad propia</strong>. La mentoría explora cómo la mayoría de los conflictos emocionales, mentales e incluso físicos nacen de las mentiras que una persona se cuenta a sí misma para evitar el dolor, el miedo o la responsabilidad.</p>
<p>A través de ejemplos reales y conversaciones profundas con los participantes, se revela cómo la culpa, el victimismo, las creencias heredadas y los condicionamientos sociales mantienen a las personas atrapadas en sufrimiento, enfermedad o estancamiento. El enfoque propone que la verdadera sanación no ocurre cuando alguien externo intenta “arreglarte”, sino cuando cada persona despierta el sanador que existe dentro de sí misma.</p>', '_Mentoría realizada el día 27 de enero del 2026_

En esta sesión se presenta uno de los pilares fundamentales del proceso de transformación personal: **honrar la verdad propia**. La mentoría explora cómo la mayoría de los conflictos emocionales, mentales e incluso físicos nacen de las mentiras que una persona se cuenta a sí misma para evitar el dolor, el miedo o la responsabilidad.

A través de ejemplos reales y conversaciones profundas con los participantes, se revela cómo la culpa, el victimismo, las creencias heredadas y los condicionamientos sociales mantienen a las personas atrapadas en sufrimiento, enfermedad o estancamiento. El enfoque propone que la verdadera sanación no ocurre cuando alguien externo intenta “arreglarte”, sino cuando cada persona despierta el sanador que existe dentro de sí misma.', 'YOUTUBE', 'https://www.youtube.com/watch?v=KIQh7x4kuCU&index=1', 'https://i.ytimg.com/vi/KIQh7x4kuCU/maxresdefault.jpg', NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('3b1e028df2ba4b5bba401c1c5fa6f618', '6be9cefa706e40049bae44c38194be29', '92a0052c94b04daaab5c95b7160c172f', 'RITUALIZAR LA VIDA PARA DESPERTAR TU PODER', 19, '<p><em>Mentoría realizada el día 13 de enero del 2026</em></p>
<p>Una mentoría enfocada en aprender a convertirse en el propio guía del proceso personal. La sesión explora cómo romper patrones heredados, dejar de depender emocionalmente de otros y desarrollar la capacidad de sostenerse internamente. A través del poder de la intención, la identidad y los rituales cotidianos, se propone transformar acciones simples en prácticas conscientes que alineen mente, emociones y propósito. Un llamado a dejar el piloto automático, asumir responsabilidad sobre la propia vida y despertar el poder interior para dirigir el propio camino.</p>', '_Mentoría realizada el día 13 de enero del 2026_

Una mentoría enfocada en aprender a convertirse en el propio guía del proceso personal. La sesión explora cómo romper patrones heredados, dejar de depender emocionalmente de otros y desarrollar la capacidad de sostenerse internamente. A través del poder de la intención, la identidad y los rituales cotidianos, se propone transformar acciones simples en prácticas conscientes que alineen mente, emociones y propósito. Un llamado a dejar el piloto automático, asumir responsabilidad sobre la propia vida y despertar el poder interior para dirigir el propio camino.', 'YOUTUBE', 'https://youtu.be/ed__fw6Hb-k', 'https://i.ytimg.com/vi/ed__fw6Hb-k/maxresdefault.jpg', NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('9b3b37bbca2e4f668a3b60a1768b164c', '6be9cefa706e40049bae44c38194be29', '92a0052c94b04daaab5c95b7160c172f', 'NUNCA NADIE TE LASTIMA', 20, '<p><em>Mentoría realizada el día 08 de enero del 2026</em></p>
<p>En esta mentoría se aborda uno de los principios más confrontativos y liberadores del proceso RENASER: <strong>Nadie tiene el poder real de lastimarte; el sufrimiento nace de las creencias, interpretaciones y heridas que cada persona carga dentro de sí misma.</strong></p>
<p>A través de ejemplos profundos y reflexiones sobre el lenguaje, la cultura y la psicología humana, se explica cómo las personas interpretan la realidad según sus propias creencias aprendidas en la infancia. Situaciones como abandono, traición, engaño o rechazo no generan el dolor por sí mismas; el sufrimiento aparece cuando la mente interpreta esos hechos desde expectativas, miedos o códigos emocionales adquiridos.</p>', '_Mentoría realizada el día 08 de enero del 2026_

En esta mentoría se aborda uno de los principios más confrontativos y liberadores del proceso RENASER: **Nadie tiene el poder real de lastimarte; el sufrimiento nace de las creencias, interpretaciones y heridas que cada persona carga dentro de sí misma.**

A través de ejemplos profundos y reflexiones sobre el lenguaje, la cultura y la psicología humana, se explica cómo las personas interpretan la realidad según sus propias creencias aprendidas en la infancia. Situaciones como abandono, traición, engaño o rechazo no generan el dolor por sí mismas; el sufrimiento aparece cuando la mente interpreta esos hechos desde expectativas, miedos o códigos emocionales adquiridos.', 'YOUTUBE', 'https://youtu.be/f1AaEkybxKc', 'https://i.ytimg.com/vi/f1AaEkybxKc/maxresdefault.jpg', 7130000, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('ae9a68b942664f0bb5457db3ba34de15', '6be9cefa706e40049bae44c38194be29', '92a0052c94b04daaab5c95b7160c172f', 'PODER DE LA GRATITUD: HÁBITO QUE CAMBIA EL DESTINO', 21, '<p><em>Mentoría realizada el día 06 de enero del 2026</em></p>
<p>En esta mentoría se profundiza en uno de los principios más poderosos para transformar la vida: <strong>la verdadera gratitud</strong>. Más allá de decir “gracias”, se explora la gratitud como un estado de conciencia que redefine la manera en que una persona interpreta su pasado, sus dificultades y su presente.</p>
<p>La sesión inicia resolviendo dudas de los participantes sobre dinero y negocios, donde se explica que el verdadero crecimiento económico no depende solo de vender más, sino de <strong>crear marca, innovar y construir valor a largo plazo</strong>. Se muestra cómo muchos emprendedores fracasan porque compiten por precio en lugar de construir identidad, historia y propósito detrás de lo que ofrecen.</p>
<p>Luego la mentoría se enfoca en el tema central: <strong>La gratitud como la base del bienestar emocional, mental y espiritual</strong>. Se explica que muchas personas viven atrapadas en frustración, ansiedad o conflictos porque desarrollaron el hábito de la ingratitud: enfocarse constantemente en lo que falta, en lo que duele o en lo que consideran injusto.</p>', '_Mentoría realizada el día 06 de enero del 2026_

En esta mentoría se profundiza en uno de los principios más poderosos para transformar la vida: **la verdadera gratitud**. Más allá de decir “gracias”, se explora la gratitud como un estado de conciencia que redefine la manera en que una persona interpreta su pasado, sus dificultades y su presente.

La sesión inicia resolviendo dudas de los participantes sobre dinero y negocios, donde se explica que el verdadero crecimiento económico no depende solo de vender más, sino de **crear marca, innovar y construir valor a largo plazo**. Se muestra cómo muchos emprendedores fracasan porque compiten por precio en lugar de construir identidad, historia y propósito detrás de lo que ofrecen.

Luego la mentoría se enfoca en el tema central: **La gratitud como la base del bienestar emocional, mental y espiritual**. Se explica que muchas personas viven atrapadas en frustración, ansiedad o conflictos porque desarrollaron el hábito de la ingratitud: enfocarse constantemente en lo que falta, en lo que duele o en lo que consideran injusto.', 'YOUTUBE', 'https://youtu.be/lQYoOB2J0dU', 'https://i.ytimg.com/vi/lQYoOB2J0dU/maxresdefault.jpg', NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('d39c84c2a47049f1aa47baa696ee0816', '035bf93019964b6b9b3a288a3b0f0ce4', 'cbd9b0012c2b46f08676c59f23e1b9cc', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('3638ad349b944309941679bce7212348', '035bf93019964b6b9b3a288a3b0f0ce4', 'cbd9b0012c2b46f08676c59f23e1b9cc', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('6ae4d84a105b4fddb48985cfb9e84111', '035bf93019964b6b9b3a288a3b0f0ce4', 'cbd9b0012c2b46f08676c59f23e1b9cc', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('afbfcc1d92264cf99e3c420d07f6da35', '035bf93019964b6b9b3a288a3b0f0ce4', '1b83c3eb622245d28ad14a5da6a08f9d', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('eb8e9853abeb4e59bcb5becfd26d1333', '035bf93019964b6b9b3a288a3b0f0ce4', '1b83c3eb622245d28ad14a5da6a08f9d', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e28319fc973745a9b9801783fbcb5f73', '035bf93019964b6b9b3a288a3b0f0ce4', '1b83c3eb622245d28ad14a5da6a08f9d', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('a550548835a14e4ebc2c1ead1001c7a6', '035bf93019964b6b9b3a288a3b0f0ce4', '8ed66cf49ea44fa697f5d4695dd5170a', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('6a8dc052300e423db54f1a6e254d1a0a', '6be9cefa706e40049bae44c38194be29', '92a0052c94b04daaab5c95b7160c172f', 'TIPO DE PERSONAS: ESCLAVOS, SOÑADORES Y EJECUTORES', 22, '<p><em>Mentoría realizada el día 01 de enero del 2026</em></p>
<p>En esta mentoría se explica cómo la mayoría de personas vive atrapada en patrones mentales que determinan su forma de actuar frente a la vida. Se describen tres tipos principales: los <strong>esclavos u operarios</strong>, que viven en victimismo, culpa y queja constante; los <strong>jefes soñadores</strong>, que tienen grandes ideas y metas pero permanecen atrapados en la procrastinación y las excusas; y los <strong>ejecutores</strong>, personas que actúan, toman decisiones y trabajan con hechos, números y resultados concretos.</p>
<p>La sesión profundiza en cómo el lenguaje que usamos revela en qué nivel estamos viviendo. Mientras algunos utilizan un lenguaje lleno de dudas, interrogantes y justificaciones, los ejecutores utilizan un lenguaje claro, afirmativo y orientado a la acción.</p>
<p>También se aborda el papel de la <strong>voluntad</strong> como una capacidad que se desarrolla, comparable a un músculo que debe entrenarse con disciplina y hábitos. Cuando una persona vive en dudas constantes, cuestionamientos y excusas, su mente entra en ciclos de ansiedad, pensamientos repetitivos y autosabotaje que bloquean su crecimiento.</p>', '_Mentoría realizada el día 01 de enero del 2026_

En esta mentoría se explica cómo la mayoría de personas vive atrapada en patrones mentales que determinan su forma de actuar frente a la vida. Se describen tres tipos principales: los **esclavos u operarios**, que viven en victimismo, culpa y queja constante; los **jefes soñadores**, que tienen grandes ideas y metas pero permanecen atrapados en la procrastinación y las excusas; y los **ejecutores**, personas que actúan, toman decisiones y trabajan con hechos, números y resultados concretos.

La sesión profundiza en cómo el lenguaje que usamos revela en qué nivel estamos viviendo. Mientras algunos utilizan un lenguaje lleno de dudas, interrogantes y justificaciones, los ejecutores utilizan un lenguaje claro, afirmativo y orientado a la acción.

También se aborda el papel de la **voluntad** como una capacidad que se desarrolla, comparable a un músculo que debe entrenarse con disciplina y hábitos. Cuando una persona vive en dudas constantes, cuestionamientos y excusas, su mente entra en ciclos de ansiedad, pensamientos repetitivos y autosabotaje que bloquean su crecimiento.', 'YOUTUBE', 'https://youtu.be/fYwAbAVrH34', 'https://i.ytimg.com/vi/fYwAbAVrH34/maxresdefault.jpg', 4076000, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('4600cf8ddbde40b8bdc56c03d18c51ff', '6be9cefa706e40049bae44c38194be29', 'e105a2e1d6a441fa961715058639ac3e', 'RECETA PARA RESOLVER CUALQUIER PROBLEMA', 23, '<p><em>Mentoría realizada el día 30 de diciembre del 2025</em></p>
<p>En esta mentoría se revela una idea poderosa: todos los problemas de la vida —dinero, relaciones, disciplina o productividad— tienen el mismo origen interno. La clave no está en aprender más técnicas ni buscar más soluciones externas, sino en hacerse una pregunta radicalmente honesta: <strong>“¿Realmente quiero hacerlo?”</strong></p>
<p>Muchas personas dicen querer cambiar, pero en el fondo no lo desean. Mantienen hábitos, relaciones o situaciones porque obtienen beneficios ocultos: atención, lástima, seguridad o evitar el fracaso. Esa incongruencia entre lo que se dice y lo que realmente se quiere crea sabotaje, culpa y estancamiento.</p>', '_Mentoría realizada el día 30 de diciembre del 2025_

En esta mentoría se revela una idea poderosa: todos los problemas de la vida —dinero, relaciones, disciplina o productividad— tienen el mismo origen interno. La clave no está en aprender más técnicas ni buscar más soluciones externas, sino en hacerse una pregunta radicalmente honesta: **“¿Realmente quiero hacerlo?”**

Muchas personas dicen querer cambiar, pero en el fondo no lo desean. Mantienen hábitos, relaciones o situaciones porque obtienen beneficios ocultos: atención, lástima, seguridad o evitar el fracaso. Esa incongruencia entre lo que se dice y lo que realmente se quiere crea sabotaje, culpa y estancamiento.', 'YOUTUBE', 'https://youtu.be/mjs0w6yy0NI', 'https://i.ytimg.com/vi/mjs0w6yy0NI/maxresdefault.jpg', NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('ef0159e283a24d7c8892bddc86c4a091', 'a9dd844e9f254cef950566885ae1f8df', NULL, 'MANUAL DEL MENTOR', 1, '<p>Este no es un documento más.<br />Es el estándar que define quién está listo para liderar.</p>
<p>Aquí entenderás con precisión qué significa ser un Guía RENASER: no desde el rol, sino desde la ejecución.</p>
<p>No estás aquí para motivar.<br />No estás aquí para acompañar desde la comodidad.<br />Estás aquí para sostener el nivel.</p>
<p>Este manual te muestra cómo:</p>
<ul><li><p>Elevar el estándar del grupo sin desgaste</p></li><li><p>Detectar patrones y eliminar excusas</p></li><li><p>Confrontar con claridad y respeto</p></li><li><p>Formar líderes, no seguidores</p></li><li><p>Operar desde disciplina, orden y responsabilidad radical</p></li></ul>
<p>Ser Guía no es un título.<br />Es una decisión diaria visible en tu ejecución.</p>
<p>Si no lo encarnas, se pierde la autoridad.<br />Si lo sostienes, multiplicas resultados.</p>
<p>Aquí encontrarás:<br />✔ Principios irrenunciables<br />✔ Sistema de supervisión basado en evidencia<br />✔ Estructura exacta de reuniones<br />✔ Función diaria del Guía<br />✔ Sistema de evaluación semanal<br />✔ Límites claros del rol<br />✔ Ritual de aceptación e investidura</p>
<p>Este manual no busca que entiendas.<br />Busca que ejecutes.</p>
<p>Porque en RENASER hay una ley clara:<br /><strong>sin disciplina, no hay transformación.</strong></p>', 'Este no es un documento más.  
Es el estándar que define quién está listo para liderar.

Aquí entenderás con precisión qué significa ser un Guía RENASER: no desde el rol, sino desde la ejecución.

No estás aquí para motivar.  
No estás aquí para acompañar desde la comodidad.  
Estás aquí para sostener el nivel.

Este manual te muestra cómo:

- Elevar el estándar del grupo sin desgaste
- Detectar patrones y eliminar excusas
- Confrontar con claridad y respeto
- Formar líderes, no seguidores
- Operar desde disciplina, orden y responsabilidad radical

Ser Guía no es un título.  
Es una decisión diaria visible en tu ejecución.

Si no lo encarnas, se pierde la autoridad.  
Si lo sostienes, multiplicas resultados.

Aquí encontrarás:  
✔ Principios irrenunciables  
✔ Sistema de supervisión basado en evidencia  
✔ Estructura exacta de reuniones  
✔ Función diaria del Guía  
✔ Sistema de evaluación semanal  
✔ Límites claros del rol  
✔ Ritual de aceptación e investidura

Este manual no busca que entiendas.  
Busca que ejecutes.

Porque en RENASER hay una ley clara:  
**sin disciplina, no hay transformación.**', NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('cb82fe2e0be14e59bce3afd2dd769f8a', 'a9dd844e9f254cef950566885ae1f8df', NULL, 'MANUAL DEL APRENDIZ', 2, '<p>Este manual marca el inicio de una nueva etapa dentro de tu proceso.</p>
<p>Has sido integrado al Sistema de Guías RENASER, una estructura diseñada para ayudarte a avanzar con más claridad, disciplina y responsabilidad.</p>
<p>Este no es un grupo social.<br />No es un espacio para quejas.<br />No es terapia grupal.</p>
<p>Es un sistema de ejecución.</p>
<p>Aquí comprenderás:<br />✔ Qué significa ser aprendiz dentro del sistema<br />✔ Cómo trabajar con tu Guía<br />✔ Qué evidencias debes presentar<br />✔ Cómo aprovechar las reuniones semanales<br />✔ Qué actitudes sostienen tu crecimiento<br />✔ Qué patrones debes dejar de justificar</p>
<p>Tu Guía no está para juzgarte.<br />Está para ayudarte a elevar tu estándar.</p>
<p>No se espera perfección.<br />Se espera compromiso real.</p>
<p>Si tomas este proceso en serio, avanzarás más rápido, detectarás tus patrones antes y construirás una disciplina más sólida.</p>
<p>Aceptar guía no te hace débil.<br />Te hace inteligente.</p>
<p>Bienvenido(a) a este nuevo camino.</p>', 'Este manual marca el inicio de una nueva etapa dentro de tu proceso.

Has sido integrado al Sistema de Guías RENASER, una estructura diseñada para ayudarte a avanzar con más claridad, disciplina y responsabilidad.

Este no es un grupo social.  
No es un espacio para quejas.  
No es terapia grupal.

Es un sistema de ejecución.

Aquí comprenderás:  
✔ Qué significa ser aprendiz dentro del sistema  
✔ Cómo trabajar con tu Guía  
✔ Qué evidencias debes presentar  
✔ Cómo aprovechar las reuniones semanales  
✔ Qué actitudes sostienen tu crecimiento  
✔ Qué patrones debes dejar de justificar

Tu Guía no está para juzgarte.  
Está para ayudarte a elevar tu estándar.

No se espera perfección.  
Se espera compromiso real.

Si tomas este proceso en serio, avanzarás más rápido, detectarás tus patrones antes y construirás una disciplina más sólida.

Aceptar guía no te hace débil.  
Te hace inteligente.

Bienvenido(a) a este nuevo camino.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('a8ee990a2680479f9f5c13b19f3b7eef', 'a9dd844e9f254cef950566885ae1f8df', NULL, 'OBJETIVOS SMART', 3, '<figure><img src="a9dd844e9f254cef950566885ae1f8df/assets/9cc6a7a891f0-e55f3316dd2f41be9236f4a460b815d349747496.png" alt="Anuncios Mentores - Formación 2026.png" loading="lazy" /></figure>
<p><br /></p>', '![Anuncios Mentores - Formación 2026.png](a9dd844e9f254cef950566885ae1f8df/assets/9cc6a7a891f0-e55f3316dd2f41be9236f4a460b815d349747496.png)', NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('88bd322df4504d37a4a02973c31ea1e0', '035bf93019964b6b9b3a288a3b0f0ce4', '8ed66cf49ea44fa697f5d4695dd5170a', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('c5d35efc54804ebe922af42b5e402995', '035bf93019964b6b9b3a288a3b0f0ce4', '8ed66cf49ea44fa697f5d4695dd5170a', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('bd423e667cc444a78a66775689b47980', '035bf93019964b6b9b3a288a3b0f0ce4', '530a1bf889494341a6598cdb6ecb056b', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('0c655ec68d514ca38cf61cdd61b52d75', '035bf93019964b6b9b3a288a3b0f0ce4', '530a1bf889494341a6598cdb6ecb056b', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('5af38d8d3b194df1b0d3295b4f0ece64', '035bf93019964b6b9b3a288a3b0f0ce4', '530a1bf889494341a6598cdb6ecb056b', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('d60d70a2b32a49ccaa986332574f3256', '035bf93019964b6b9b3a288a3b0f0ce4', 'ffdcdfedea0e4325ba5bd3e4164d99fe', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('7523d0c9a02b49409356a53a1af17830', '035bf93019964b6b9b3a288a3b0f0ce4', 'ffdcdfedea0e4325ba5bd3e4164d99fe', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('7c74ee0c7db14a20a4e24f51e68282f4', 'a9dd844e9f254cef950566885ae1f8df', '80a449cdcf684848b6ad8a80371ab8de', 'FORMULARIO PARA MENTORES', 4, '<p>Este formulario es un espacio de autoevaluación diseñado para que, como mentor, revises con honestidad tu desempeño durante la semana.</p>
<p>Aquí no se trata de calificarte, sino de observarte con claridad: cómo lideraste, cómo acompañaste a tus aprendices y qué tan alineado estuviste con el estándar RENASER.</p>
<p>• Nivel de cumplimiento de tu protocolo<br />• Calidad de tu liderazgo y presencia<br />• Gestión de aprendices y toma de acción<br />• Identificación de errores, patrones y oportunidades de mejora<br />• Claridad en tus próximos ajustes</p>
<p>Este ejercicio es clave para sostener tu crecimiento como mentor.</p>
<p>Tu evolución impacta directamente en el proceso y resultados de tus aprendices.</p>', 'Este formulario es un espacio de autoevaluación diseñado para que, como mentor, revises con honestidad tu desempeño durante la semana.

Aquí no se trata de calificarte, sino de observarte con claridad: cómo lideraste, cómo acompañaste a tus aprendices y qué tan alineado estuviste con el estándar RENASER.

• Nivel de cumplimiento de tu protocolo  
• Calidad de tu liderazgo y presencia  
• Gestión de aprendices y toma de acción  
• Identificación de errores, patrones y oportunidades de mejora  
• Claridad en tus próximos ajustes

Este ejercicio es clave para sostener tu crecimiento como mentor.

Tu evolución impacta directamente en el proceso y resultados de tus aprendices.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('fb0d58cd803e486c8ffaf0e0972e1ab2', 'a9dd844e9f254cef950566885ae1f8df', '80a449cdcf684848b6ad8a80371ab8de', 'FORMULARIO PARA APRENDICES', 5, '<p>Este formulario está diseñado para que lo compartas con tus aprendices y puedas dar seguimiento real a su proceso durante la semana.</p>
<p>A través de sus respuestas podrás medir su nivel de compromiso, identificar bloqueos, evaluar su avance y tomar decisiones claras sobre cómo acompañarlos.</p>
<p>• Nivel de cumplimiento de actividades<br />• Avance en sus objetivos<br />• Identificación de patrones y resistencias<br />• Nivel de enfoque y responsabilidad<br />• Próximos pasos del aprendiz</p>
<p>Asegúrate de que todos tus aprendices lo completen en el tiempo indicado.</p>
<p>Este formulario no es opcional. Es una herramienta clave para sostener el estándar, el seguimiento y los resultados del proceso RENASER.</p>', 'Este formulario está diseñado para que lo compartas con tus aprendices y puedas dar seguimiento real a su proceso durante la semana.

A través de sus respuestas podrás medir su nivel de compromiso, identificar bloqueos, evaluar su avance y tomar decisiones claras sobre cómo acompañarlos.

• Nivel de cumplimiento de actividades  
• Avance en sus objetivos  
• Identificación de patrones y resistencias  
• Nivel de enfoque y responsabilidad  
• Próximos pasos del aprendiz

Asegúrate de que todos tus aprendices lo completen en el tiempo indicado.

Este formulario no es opcional. Es una herramienta clave para sostener el estándar, el seguimiento y los resultados del proceso RENASER.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('c2903bf82b9b43248552668a63956440', 'a9dd844e9f254cef950566885ae1f8df', 'dc636fc5d95643e98a8b198afbbb7ef4', 'FICHA DE APRENDIZ', 6, '<p>Como mentor, tu rol no es solo acompañar, sino asegurar avance real.</p>
<p>Esta semana, te corresponde realizar un seguimiento consciente y estratégico a tu aprendiz. Observa su participación, revisa su cumplimiento (checklist, publicaciones, asistencia) y, sobre todo, identifica dónde se está frenando.</p>
<p>No se trata solo de preguntar cómo va, sino de ayudarlo a ver lo que aún no está viendo.</p>
<ul><li><p>Conecta desde la intención, no desde la obligación</p></li><li><p>Haz preguntas que lo confronten y lo despierten</p></li><li><p>Refuerza lo que está haciendo bien</p></li><li><p>Corrige con claridad lo que está evitando</p></li></ul>
<p>Recuerda: tu presencia puede marcar la diferencia entre alguien que avanza… y alguien que se queda igual.</p>', 'Como mentor, tu rol no es solo acompañar, sino asegurar avance real.

Esta semana, te corresponde realizar un seguimiento consciente y estratégico a tu aprendiz. Observa su participación, revisa su cumplimiento (checklist, publicaciones, asistencia) y, sobre todo, identifica dónde se está frenando.

No se trata solo de preguntar cómo va, sino de ayudarlo a ver lo que aún no está viendo.

- Conecta desde la intención, no desde la obligación
- Haz preguntas que lo confronten y lo despierten
- Refuerza lo que está haciendo bien
- Corrige con claridad lo que está evitando

Recuerda: tu presencia puede marcar la diferencia entre alguien que avanza… y alguien que se queda igual.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('b75f16b08353444b8821977620aff592', '36b59c82ae5b4328a50c286cf9c5ce41', NULL, 'CERTIFICACIÓN EMBAJADOR RENASER', 0, '<p>Este documento es una guía experiencial integral diseñada para acompañar un proceso de transformación profunda de 84 días, enfocado en desarrollar Embajadores RENASER capaces de liderar procesos de cambio personal y facilitar resultados reales en otros. No se trata de un curso teórico, sino de un sistema estructurado de entrenamiento intensivo que combina autoconocimiento, acción disciplinada, construcción de identidad, desarrollo de marca personal y dominio de herramientas de intervención.</p>
<p>A lo largo del proceso, el participante trabaja sobre su propia historia, patrones y creencias, transformando su herida en una propuesta de valor auténtica. Paralelamente, adquiere una metodología clara para diagnosticar problemáticas, identificar raíces emocionales, diseñar procesos de intervención y construir programas de transformación de 8 a 12 semanas, con estructura, entregables y métricas definidas.</p>
<p>La guía incluye protocolos base, ejercicios prácticos, preguntas de reflexión diaria, estructuras de sesión profesional, diseño de reportes clínicos, seguimiento de métricas y desarrollo de sistemas de trabajo replicables. Todo esto bajo un enfoque de alto rendimiento que exige compromiso diario, vulnerabilidad, acción constante y capacidad de integración real.</p>
<p>Además, el documento establece los principios fundamentales del Embajador RENASER, basados en tres pilares: sabiduría (aprendizaje continuo), compasión (empatía con dirección) e integridad (coherencia absoluta), asegurando que el proceso no solo forme habilidades, sino también una identidad sólida y sostenible en el tiempo.</p>
<p>Al finalizar, el participante no solo cuenta con conocimiento, sino con un sistema completo: sabe cómo diagnosticar, intervenir, vender con integridad, medir resultados y sistematizar su trabajo. Este documento representa tanto una guía de ejecución como un estándar de excelencia para operar dentro del ecosistema RENASER, convirtiendo la transformación personal en una práctica profesional estructurada y escalable.</p>', 'Este documento es una guía experiencial integral diseñada para acompañar un proceso de transformación profunda de 84 días, enfocado en desarrollar Embajadores RENASER capaces de liderar procesos de cambio personal y facilitar resultados reales en otros. No se trata de un curso teórico, sino de un sistema estructurado de entrenamiento intensivo que combina autoconocimiento, acción disciplinada, construcción de identidad, desarrollo de marca personal y dominio de herramientas de intervención.

A lo largo del proceso, el participante trabaja sobre su propia historia, patrones y creencias, transformando su herida en una propuesta de valor auténtica. Paralelamente, adquiere una metodología clara para diagnosticar problemáticas, identificar raíces emocionales, diseñar procesos de intervención y construir programas de transformación de 8 a 12 semanas, con estructura, entregables y métricas definidas.

La guía incluye protocolos base, ejercicios prácticos, preguntas de reflexión diaria, estructuras de sesión profesional, diseño de reportes clínicos, seguimiento de métricas y desarrollo de sistemas de trabajo replicables. Todo esto bajo un enfoque de alto rendimiento que exige compromiso diario, vulnerabilidad, acción constante y capacidad de integración real.

Además, el documento establece los principios fundamentales del Embajador RENASER, basados en tres pilares: sabiduría (aprendizaje continuo), compasión (empatía con dirección) e integridad (coherencia absoluta), asegurando que el proceso no solo forme habilidades, sino también una identidad sólida y sostenible en el tiempo.

Al finalizar, el participante no solo cuenta con conocimiento, sino con un sistema completo: sabe cómo diagnosticar, intervenir, vender con integridad, medir resultados y sistematizar su trabajo. Este documento representa tanto una guía de ejecución como un estándar de excelencia para operar dentro del ecosistema RENASER, convirtiendo la transformación personal en una práctica profesional estructurada y escalable.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('74560df65cc943e694a376afc0f3162d', '36b59c82ae5b4328a50c286cf9c5ce41', NULL, 'Queremos conocerte', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('afc7b5ce312c41798712b5f064f57f41', '36b59c82ae5b4328a50c286cf9c5ce41', NULL, 'Propuesta única de valor', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('7927bc13f37e40e79655ad53daa065fe', '36b59c82ae5b4328a50c286cf9c5ce41', NULL, 'El arte de vender', 3, '<p>En este manual estarás inmerso. en el mundo de las ventas, no desde la necesidad, aprende estos conceptos y aplicalo en tu día a día.</p>', 'En este manual estarás inmerso. en el mundo de las ventas, no desde la necesidad, aprende estos conceptos y aplicalo en tu día a día.', NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('db9a0988a00d4aa093925c18578a5a45', 'cfc4c4334474498b9f6c9c47800fdecf', '3e116b815c57456b9a102bc783b90c55', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('211ebd8ed14e4a448f652be303df4d44', 'cfc4c4334474498b9f6c9c47800fdecf', '3e116b815c57456b9a102bc783b90c55', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('1fe74692ad6a4f8eb976d2ede2d297d5', 'cfc4c4334474498b9f6c9c47800fdecf', '3e116b815c57456b9a102bc783b90c55', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('95889b5058fd4589a5408fbb4e000868', 'cfc4c4334474498b9f6c9c47800fdecf', '5ab7f4a436974f96974e67fc7b750bda', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('fe00e661fe434d4c867d7a35bd658aaf', '01ac727ed506477883d5e015a0b792c1', '2c47a277dbd443bd88c916ce77b02ac4', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('cd7a8d99d65245b78e6fdde616ab84f0', '602f198f05c2442184c7b67071b2487d', '2b33c26eba1042a9b676c16f3a098197', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('a82a07ba8d1040efb6cd38562f63137e', '602f198f05c2442184c7b67071b2487d', '2b33c26eba1042a9b676c16f3a098197', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('81f40ec3f3ff4440866eaf5e0d4aa840', '602f198f05c2442184c7b67071b2487d', '2b33c26eba1042a9b676c16f3a098197', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('84aaf626907f49f58b9d6cf0ff4f461c', '602f198f05c2442184c7b67071b2487d', '47bd0bd9455d4eb99d3d2de9e48b8442', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('8aa51b432bf64b409c5884daf779f6ac', '602f198f05c2442184c7b67071b2487d', '47bd0bd9455d4eb99d3d2de9e48b8442', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('ea0829de17474628922f620d3308ed0d', '602f198f05c2442184c7b67071b2487d', '47bd0bd9455d4eb99d3d2de9e48b8442', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('f320473639e54bea808e26c7aa4631d5', '602f198f05c2442184c7b67071b2487d', 'a60b30e3acb44bd2b925f419daee821c', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('50260464a77f44578e92e7d6c7e1f80d', '602f198f05c2442184c7b67071b2487d', 'a60b30e3acb44bd2b925f419daee821c', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('d9a43bfcac4645329c4c82f45fa6f70f', '602f198f05c2442184c7b67071b2487d', 'a60b30e3acb44bd2b925f419daee821c', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e2722546bbd543018bc5133eb790c4d8', '602f198f05c2442184c7b67071b2487d', '3255f19ee2784077b4b049d4a7ec7c2b', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('c4e1c85ea6c2446c8b3f8f9a836bb140', '602f198f05c2442184c7b67071b2487d', '3255f19ee2784077b4b049d4a7ec7c2b', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('103ca3317a014fc4bbb91561e055a08a', '602f198f05c2442184c7b67071b2487d', '3255f19ee2784077b4b049d4a7ec7c2b', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('a723f44cb0654515b9fc85034c3ffaa7', '602f198f05c2442184c7b67071b2487d', '73ded8e119414dccb752d54472769a44', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('bebe306f62354d76aa4cd75f9c7a6f6f', '602f198f05c2442184c7b67071b2487d', '73ded8e119414dccb752d54472769a44', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('0bc844931d804d938ede3575a33613b3', '602f198f05c2442184c7b67071b2487d', '73ded8e119414dccb752d54472769a44', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('fae0166889864b99895405a3264a4a72', '602f198f05c2442184c7b67071b2487d', 'a07e89a7ab064d21a3626e7ea41aa90e', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('f707449fa84341f8b9e50657f378cd56', '602f198f05c2442184c7b67071b2487d', 'a07e89a7ab064d21a3626e7ea41aa90e', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('2a58fe9aa85c4f9280b25d505fd44b1c', '602f198f05c2442184c7b67071b2487d', 'a07e89a7ab064d21a3626e7ea41aa90e', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('d9c7a9edab094a82a1c48d18858a671e', '602f198f05c2442184c7b67071b2487d', 'a680d24748634289ad59ca034e7e8f94', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('8170524347cc4d9ab6d4d94d43f45076', '602f198f05c2442184c7b67071b2487d', 'a680d24748634289ad59ca034e7e8f94', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('1c017da402da452493d309cf9c0a38c0', '602f198f05c2442184c7b67071b2487d', 'a680d24748634289ad59ca034e7e8f94', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('18fb5751297b4a1298883029d9930b94', '21fd1d616cd145ac876a29f402cc119a', 'e28de1e0a3ba4b0c856e8f18e495de56', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('da07ae7e5eb148a6b787be7b9f9326a5', '21fd1d616cd145ac876a29f402cc119a', 'e28de1e0a3ba4b0c856e8f18e495de56', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.190781+00', '2026-07-28 21:16:16.086916+00'),
    ('e03eb04ec86649cfbb25ebdb96b54b81', '21fd1d616cd145ac876a29f402cc119a', '563239e3e3394dada0b37a3d29f91564', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('b123ef9b6ad747c8b327dd97bea19e76', '21fd1d616cd145ac876a29f402cc119a', '563239e3e3394dada0b37a3d29f91564', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('33d7d16d514e4fb3966172f6c08ab2ae', '21fd1d616cd145ac876a29f402cc119a', '563239e3e3394dada0b37a3d29f91564', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('e58baef26c8b488db5a45b9ab40fe423', '21fd1d616cd145ac876a29f402cc119a', '2f0f57690f224093a72062ef65fe59e3', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('fd43d8baad1d4333bb1b1f759316826b', '21fd1d616cd145ac876a29f402cc119a', '2f0f57690f224093a72062ef65fe59e3', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('66c2754ea269484198387425ad724d5d', '21fd1d616cd145ac876a29f402cc119a', '2f0f57690f224093a72062ef65fe59e3', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('069566aada864cb595757467b7999b4b', '21fd1d616cd145ac876a29f402cc119a', 'dc4cfec04df64cb6881f04acd6e247e5', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('66cdef4b1be24a82917384eff670aa06', '21fd1d616cd145ac876a29f402cc119a', 'dc4cfec04df64cb6881f04acd6e247e5', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('88bcdb14941649f3b7d4db6e9f546229', '21fd1d616cd145ac876a29f402cc119a', 'dc4cfec04df64cb6881f04acd6e247e5', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('ecde2d7c8ade4e7994e6cc14e842ac28', '21fd1d616cd145ac876a29f402cc119a', '7c872625ee10475e9682d211291204cc', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('d7a1838ce80a47e7bc57b78a8e6144f3', '21fd1d616cd145ac876a29f402cc119a', '7c872625ee10475e9682d211291204cc', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('1ed05426425348b1b275f2f523c37382', '21fd1d616cd145ac876a29f402cc119a', '7c872625ee10475e9682d211291204cc', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('82bf2afdd5f0400385e41329f52fc883', '30502644ee3449ebbd4482f1ddf20a1e', '1d4b3bf55e1849449916605ade2541bd', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('aef609464d24493d97ef1dd479f2bd1d', '30502644ee3449ebbd4482f1ddf20a1e', '1d4b3bf55e1849449916605ade2541bd', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('253ff9f9f559432492ba478294169996', '30502644ee3449ebbd4482f1ddf20a1e', '1d4b3bf55e1849449916605ade2541bd', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('e646da2e7b7d437bbf5b1eac04e674de', '30502644ee3449ebbd4482f1ddf20a1e', '231b4e01b3d340de9412cf6bc6aee5ab', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('f380f6f49de74f5b958467cde6cacf4c', '30502644ee3449ebbd4482f1ddf20a1e', '231b4e01b3d340de9412cf6bc6aee5ab', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('4c20226e7e5b4b5491b313f5a595d23a', '30502644ee3449ebbd4482f1ddf20a1e', '231b4e01b3d340de9412cf6bc6aee5ab', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('a5bb77cb9f4543578daa6216fd5afcef', '30502644ee3449ebbd4482f1ddf20a1e', 'e7b9cd683c3c4294815ac53e0b6777a7', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('684acbd38fc644cf9b397144dd1ba17a', '30502644ee3449ebbd4482f1ddf20a1e', 'e7b9cd683c3c4294815ac53e0b6777a7', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('c568ec4e3dcc4eee8328bbd9997e5539', '30502644ee3449ebbd4482f1ddf20a1e', 'e7b9cd683c3c4294815ac53e0b6777a7', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('536849c7b11f43baa7bda117f531f7f1', '30502644ee3449ebbd4482f1ddf20a1e', '9b6c3872d8db4306bd8aa4b63611707a', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('4e5392f393984f738e61d93802b50bc1', '30502644ee3449ebbd4482f1ddf20a1e', '9b6c3872d8db4306bd8aa4b63611707a', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('988b5cb7380a42c9b414c0ae9e3b2947', '30502644ee3449ebbd4482f1ddf20a1e', '9b6c3872d8db4306bd8aa4b63611707a', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('42f0330cb72844d5b621e32808bb826f', '30502644ee3449ebbd4482f1ddf20a1e', 'd1f253d440ff40a8ac37659b572297d8', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('4aca295cfe35447ea79cdd8da6ed274d', '30502644ee3449ebbd4482f1ddf20a1e', 'd1f253d440ff40a8ac37659b572297d8', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('1ac90e23b5134db3816b891995604d0f', '30502644ee3449ebbd4482f1ddf20a1e', 'd1f253d440ff40a8ac37659b572297d8', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('8573c9e5cb6c49de93fc5fafb29d66a9', '30502644ee3449ebbd4482f1ddf20a1e', '5c6e614b8daf40aab3e1ad6508633ef7', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('483f18ed113c4035bfdf3a309bb2e5e4', '30502644ee3449ebbd4482f1ddf20a1e', '5c6e614b8daf40aab3e1ad6508633ef7', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('abccb6993fec4910a008535d69fd109a', '30502644ee3449ebbd4482f1ddf20a1e', '5c6e614b8daf40aab3e1ad6508633ef7', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('0c16e61062344fcabdf7f534768ff881', '30502644ee3449ebbd4482f1ddf20a1e', '827d12e82af6415791195dcf518cfe1f', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('3151131d9c2740cb85c24fdd4491c235', '30502644ee3449ebbd4482f1ddf20a1e', '827d12e82af6415791195dcf518cfe1f', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('d57ca91559b94eae8b08b6dfedeed40a', '30502644ee3449ebbd4482f1ddf20a1e', '827d12e82af6415791195dcf518cfe1f', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('08a949bb6d7245aa8d82cba09298bcf8', 'c2bf914741b34ea3a9d08c3ab86c5996', 'e1386d77d8624b878ff7d1e6fa5bf270', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('e3e8a83321a144829f66e9bc95fd10ea', 'c2bf914741b34ea3a9d08c3ab86c5996', 'e1386d77d8624b878ff7d1e6fa5bf270', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('23d9791fe21d43a295a7b85d7d13a35c', 'c2bf914741b34ea3a9d08c3ab86c5996', 'e1386d77d8624b878ff7d1e6fa5bf270', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('d689812fdb024f67bd3c68836df99d39', 'c2bf914741b34ea3a9d08c3ab86c5996', 'b63f078bc5104910b789b0b8cbd5da97', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('535e341ce95445938ae61e4397f6f14b', 'c2bf914741b34ea3a9d08c3ab86c5996', 'b63f078bc5104910b789b0b8cbd5da97', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('5a25c6a010334e12a45ee16cc2b77572', 'c2bf914741b34ea3a9d08c3ab86c5996', 'b63f078bc5104910b789b0b8cbd5da97', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('93ba175a7b6c4c1f92567abc4d72e602', 'c2bf914741b34ea3a9d08c3ab86c5996', '62dde6d6bc064f7b9ee2f4878658afa9', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('cc49fa89693044bca8806e9529d3e802', 'c2bf914741b34ea3a9d08c3ab86c5996', '62dde6d6bc064f7b9ee2f4878658afa9', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('19d33a4ac6f044f5ba6e265454651d06', 'c2bf914741b34ea3a9d08c3ab86c5996', '62dde6d6bc064f7b9ee2f4878658afa9', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('70cfb75d8dae4b1790f01084d5b66ce0', 'c2bf914741b34ea3a9d08c3ab86c5996', '47c2b98e39124867b12f2e2dacc350f2', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('6a8d8b4b2b8441b2bd834dd1048eb15b', 'c2bf914741b34ea3a9d08c3ab86c5996', '47c2b98e39124867b12f2e2dacc350f2', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('1e4375ac89d94bf9a83dcc97c69a8fe7', 'c2bf914741b34ea3a9d08c3ab86c5996', '47c2b98e39124867b12f2e2dacc350f2', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('5e100029b7094eea89362e17330c9551', 'c2bf914741b34ea3a9d08c3ab86c5996', 'ee0ca64efd83432c9f54dadfe38fcc47', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('6542798cd665494fa3288bfe84718f2b', 'c2bf914741b34ea3a9d08c3ab86c5996', 'ee0ca64efd83432c9f54dadfe38fcc47', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('d1b84207eaf14386a76c2e7e4cf6afdb', 'c2bf914741b34ea3a9d08c3ab86c5996', 'ee0ca64efd83432c9f54dadfe38fcc47', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('e3f337a9c79e482ca5e425d6b5904477', 'c2bf914741b34ea3a9d08c3ab86c5996', '7b476391ae2e4d9f9069dcb4198e8d5f', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('e7dedaa9f92c4c48a31ed3d6396199aa', 'c2bf914741b34ea3a9d08c3ab86c5996', '7b476391ae2e4d9f9069dcb4198e8d5f', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('26f1512b443c4248981446d6fe7ce6da', 'c2bf914741b34ea3a9d08c3ab86c5996', '7b476391ae2e4d9f9069dcb4198e8d5f', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('2387d5f839c94a8692ff1137cda63329', 'c2bf914741b34ea3a9d08c3ab86c5996', 'a885b88815c043ec9fbd61d2ceaefa02', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('bef453d414e54866955e183389731b91', 'c2bf914741b34ea3a9d08c3ab86c5996', 'a885b88815c043ec9fbd61d2ceaefa02', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('0b67ed8f2f0a4b948076bdb07d0c9b68', 'c2bf914741b34ea3a9d08c3ab86c5996', 'a885b88815c043ec9fbd61d2ceaefa02', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('af044a3c57b347ac93cd9fb9f7daa0a1', 'f71c7c7194c547bca05453d4b0c32fb0', 'bdd876c5bd214e3e8cbc3b847274c8ea', 'CLASE', 0, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('9fc6bdf486ef4527b69e2a8ac0f16039', 'f71c7c7194c547bca05453d4b0c32fb0', 'bdd876c5bd214e3e8cbc3b847274c8ea', 'AUDIOTERAPIA', 1, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('f93b06be9770443981e91ce68233e0a6', 'f71c7c7194c547bca05453d4b0c32fb0', 'bdd876c5bd214e3e8cbc3b847274c8ea', 'EBOOK', 2, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('27813c871b9c4ba7865c73c4a0c31227', 'f71c7c7194c547bca05453d4b0c32fb0', '8c6aece63e4845b89ce79483301a6d35', 'CLASE', 3, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('c72899e8ae904de5b88a51e657b9fccb', 'f71c7c7194c547bca05453d4b0c32fb0', '8c6aece63e4845b89ce79483301a6d35', 'AUDIOTERAPIA', 4, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('d4a971b9bedd4dae9f7879c3ca9db50c', 'f71c7c7194c547bca05453d4b0c32fb0', '8c6aece63e4845b89ce79483301a6d35', 'EBOOK', 5, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('745780e3f4b84505b28557ffa2d0a0ef', 'f71c7c7194c547bca05453d4b0c32fb0', '588e980f48d44fe0b50f544782c1af20', 'CLASE', 6, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('8325097396994753a130c8fffadc3a81', 'f71c7c7194c547bca05453d4b0c32fb0', '588e980f48d44fe0b50f544782c1af20', 'AUDIOTERAPIA', 7, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('fba6f24d28ea4e22abcfb91332733af9', 'f71c7c7194c547bca05453d4b0c32fb0', '588e980f48d44fe0b50f544782c1af20', 'EBOOK', 8, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('a544fde7bf964833ac7cd298ff7ad4f9', 'f71c7c7194c547bca05453d4b0c32fb0', 'e263339c9b8c4cf19a0c48b2e2351ee1', 'CLASE', 9, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('11c26e8ffb864a01ae1942ab3a6e51c7', 'f71c7c7194c547bca05453d4b0c32fb0', 'e263339c9b8c4cf19a0c48b2e2351ee1', 'AUDIOTERAPIA', 10, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('6d620f7b36d9469dba127486c457cf43', 'f71c7c7194c547bca05453d4b0c32fb0', 'e263339c9b8c4cf19a0c48b2e2351ee1', 'EBOOK', 11, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('3005516a189647cf953c8d5ade4c3746', 'f71c7c7194c547bca05453d4b0c32fb0', 'cbaf99846e404935bba7e4e410dbff0f', 'CLASE', 12, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('d605b2340c20428d93a82dd77cbd0578', 'f71c7c7194c547bca05453d4b0c32fb0', 'cbaf99846e404935bba7e4e410dbff0f', 'AUDIOTERAPIA', 13, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('b2893f44b5074adda12662630ad1e11b', 'f71c7c7194c547bca05453d4b0c32fb0', 'cbaf99846e404935bba7e4e410dbff0f', 'EBOOK', 14, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('18ca67f287ea4c7ea3e9280043f9afbf', 'f71c7c7194c547bca05453d4b0c32fb0', 'a72e73f307d043d48cfbe86a320bb0de', 'CLASE', 15, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('5300923178c946b7907ea8946ae83e14', 'f71c7c7194c547bca05453d4b0c32fb0', 'a72e73f307d043d48cfbe86a320bb0de', 'AUDIOTERAPIA', 16, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('589ed82f1fd54a249b1b21504bb56fa8', 'f71c7c7194c547bca05453d4b0c32fb0', 'a72e73f307d043d48cfbe86a320bb0de', 'EBOOK', 17, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('66170095557f4a0ea2e4dfa28e601722', 'f71c7c7194c547bca05453d4b0c32fb0', 'b7dede88c3254543a3e02b32204851a2', 'CLASE', 18, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('5d2dfdce87cd43d39e5752123bbee4b9', 'f71c7c7194c547bca05453d4b0c32fb0', 'b7dede88c3254543a3e02b32204851a2', 'AUDIOTERAPIA', 19, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00'),
    ('5a25322c48ea4c3db06de267d5082625', 'f71c7c7194c547bca05453d4b0c32fb0', 'b7dede88c3254543a3e02b32204851a2', 'EBOOK', 20, '<p><br /></p>', NULL, NULL, NULL, NULL, NULL, '2026-07-28 21:12:39.370131+00', '2026-07-28 21:16:16.235849+00');

-- ============================================================================
-- RECURSOS_LECCION (18 filas -- id es IDENTITY, se omite y se deja autogenerar)
-- ============================================================================

INSERT INTO recursos_leccion (
    leccion_id, nombre, url, orden
) VALUES
    ('f959157d43124105a95fd748e4476c88', 'CONEXIÓN CON TU ESENCIA', 'https://www.ivoox.com/player_ej_145086556_6_1.html?c1=93634d', 0),
    ('dcb2ef1bef4141068b67dc5377f3fc77', 'SUPERA TU FRUSTRACIÓN', 'https://go.ivoox.com/rf/145291910', 0),
    ('f19ebe6a03494ac38b386ad5996d0d33', 'SIENTE Y RÍE', 'https://go.ivoox.com/rf/146374425', 0),
    ('1946c715fd8e4a71b1fb6f20df5dcafe', 'POTENCIAL INFINITO', 'https://go.ivoox.com/rf/145086556', 0),
    ('4a46387494b14026b3b7cf6a961610e7', 'SANA TU LINAJE FEMENINO', 'https://go.ivoox.com/rf/146738533', 0),
    ('8985743d436f4ecaa196785eaf1d0a29', 'SANA CON PAPÁ', 'https://go.ivoox.com/rf/150018788', 0),
    ('bccf545a36e547b7852268b6fbfe3f2c', 'LA MAGIA DE LA IMPERFECCIÓN', 'https://go.ivoox.com/rf/148063182', 0),
    ('2d77fcf2fe254aed8fa2f61339bd1e56', 'SANA TU DOLOR Y ENFERMEDAD', 'https://go.ivoox.com/rf/148176245', 0),
    ('8c7babbe862141958ca6041ccb3ccb9c', 'Escúchalo aquí', 'https://www.ivoox.com/player_ej_145086556_6_1.html?c1=93634d', 0),
    ('a3e96426d55d44d3a43340116c5b85b8', 'Escúchalo aquí', 'https://go.ivoox.com/rf/145291910?utm_source=embed_audio_new&utm_medium=share&utm_campaign=new_embeds', 0),
    ('4057fa6c80a34aa5b290ff181075239c', 'Escúchalo aquí', 'https://go.ivoox.com/rf/146374425', 0),
    ('a4c948f5bf6243a79cdef2fe8deb7866', 'Escúchalo aquí', 'https://go.ivoox.com/rf/146738533', 0),
    ('6844f1ecc8964a718d6b316fa9714fea', 'Viaje Renaser para Sanar con papá', 'https://go.ivoox.com/rf/150018788', 0),
    ('8d7dc7b29c834e468e7a092e75d31fd4', 'Escúchalo aquí', 'https://go.ivoox.com/rf/148063182', 0),
    ('5f23c77bcb204877b2816d055294cd98', 'Escúchalo aquí: Sana tu dolor', 'https://go.ivoox.com/rf/148176245', 0),
    ('b1259703815e48eea6f628a75318c962', 'Escúchalo aquí: Crea tu realidad', 'https://go.ivoox.com/rf/149115564', 0),
    ('7c74ee0c7db14a20a4e24f51e68282f4', 'FORMULARIO PARA MENTORES', 'https://forms.gle/8gtsP3iQ4GonvBq19', 0),
    ('fb0d58cd803e486c8ffaf0e0972e1ab2', 'FORMULARIO PARA APRENDICES', 'https://forms.gle/2uyCXcsjK9XJ8wSeA', 0);

COMMIT;
