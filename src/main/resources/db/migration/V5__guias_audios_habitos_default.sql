-- Continuacion de V4: contenido de catalogo (guias de habito, adjuntos de guia, audios de
-- Espiritu, audioterapias). Mismo origen (docs/db/migracion/datos_origen/dump_completo.sql,
-- produccion qchpxyaiipghayyfmthg) y misma politica: solo logica/catalogo, cero datos de
-- usuario, ids de produccion preservados.
--
-- ── Proceso ETL seguido (no solo "copiar filas") ──────────────────────────────────────────────
-- Se siguio el orden que recomienda la practica estandar de migracion de datos: perfilar antes
-- de transformar, limpiar en origen, validar integridad referencial antes de cargar, y solo
-- despues escribir el INSERT (fuentes: Quinnox "Data Migration Checklist 2026"
-- https://www.quinnox.com/blogs/data-migration-checklist/, digna.ai "Data Validation During
-- Migrations" https://www.digna.ai/data-validation-during-migrations-best-practices). El motivo
-- de hacerlo asi -- y no solo volcar el dump -- es evitar el error que la propia auditoria de
-- este proyecto detecto en la BD vieja (docs/db/AUDITORIA_REDISENO_BD.md P-01/P-08/P-09:
-- relaciones sin FK real que dejaban huerfanos silenciosos). Acortado:
--
-- 1) PERFILADO (extract + profile): se extrajeron las 4 tablas origen completas
--    (habit_guides: 22 filas, habit_guide_attachments: 3, spirit_audios: 43, audio_therapies:
--    13) y se cruzaron sus FKs contra los 18 habitos ya migrados en V4.
--
-- 2) HALLAZGOS Y LIMPIEZA (transform):
--    - 5 de las 22 `habit_guides` son HUERFANAS: apuntan a habit_id de los 9 habitos inactivos
--      que V4 decidio no migrar (Registro de intoxicacion consciente, Mantra matutino, Ritual
--      del agua presente, Movimiento libre/baile, Agua con limon+jugo verde noche). Se excluyen
--      -- cargarlas violaria la FK `guias_habito.habito_id REFERENCES habitos(id)` porque esos
--      habitos no existen en la BD nueva. Quedan **17 guias validas**.
--    - De las 3 `habit_guide_attachments`, 1 referencia una guia huerfana (la de "Registro de
--      intoxicacion consciente") y se excluye por la misma razon. Quedan **2 adjuntos validos**,
--      ambos enlaces de YouTube (media_type=LINK, sin archivo en storage que migrar).
--    - Sin duplicados: 0 pares (habito_id, dia_inicio) repetidos en las 17 guias validas (violaria
--      el UNIQUE de `guias_habito`); 0 dias repetidos en `spirit_audios` (violaria la PK natural
--      de `audios_espiritu`); 0 semanas repetidas en `audio_therapies`.
--    - `spirit_audios` cubre 43 de los 90 dias del programa (faltan 47) -- no es un defecto de
--      esta migracion, es contenido que el equipo todavia no grabo/subio a Drive. Se documenta
--      para que no se lea como un bug de la carga.
--    - `audio_therapies` esta completa: 13 de 13 semanas.
--    - Sin campos criticos vacios: las 17 guias validas tienen `what_to_do` (que_hacer) cargado.
--
-- 3) CARGA (load): solo las filas que pasaron el punto 2, con los ids de produccion preservados
--    para que una futura migracion de `evidencias`/adjuntos que las referencie por FK no tenga
--    que remapear nada.
--
-- Mapeo de valores aplicado (old -> new), literal:
--   habit_guide_attachments.section: HOW_TO_DO->COMO_HACERLO (unico valor presente en el dato;
--     el resto del enum seccion_guia -- QUE_HACER, CIENCIA, RENASER, ALQUIMIA, RESULTADOS,
--     COMO_VALIDAR -- no tiene filas de origen, no se inventa su mapeo sin dato real que lo pida)
--   habit_guide_attachments.media_type: LINK->ENLACE (unico valor presente)
--
-- Nota de contenido (no se corrige, se preserva verbatim): el audio de spirit_audios dia 23 trae
-- el titulo con un ¿ de apertura sin el ? de cierre ("¿Nada tiene sentido en tu vida"). Es un
-- typo del contenido original, no un problema de esta migracion -- corregirlo sin que lo pida el
-- dueno del contenido seria inventar texto (CLAUDE.MD §0.6).

BEGIN;

SET search_path TO renaser, public;

-- ============================================================================
-- GUIAS_HABITO (17 filas validas de 22 en origen -- ver exclusiones arriba)
-- ============================================================================

INSERT INTO guias_habito (
    id, habito_id, dia_inicio, dia_fin, que_hacer, como_hacerlo, ciencia, renaser, alquimia,
    resultados, mantra_titulo, mantra_intro, mantra_cuerpo, referencia_fuente, creado_en, actualizado_en
) VALUES
    ('894d6fb6-efc0-49bf-b6f0-c4ba34c14829', '3a8a82a0-787d-46bc-a69e-b29c110a37bc', 1, NULL, 'Cierra tu ventana de alimentación a las 6:00 pm. Después de esa hora, solo agua.', 'Con calma. Sin lucha. Sin castigo. Escuchando el cuerpo, bebiendo agua, respirando.', 'El descanso digestivo nocturno sostiene todo lo del ayuno: menos inflamación, mejor sueño y un despertar sin niebla mental.', 'Cerrar la cocina es cerrar el día. Lo que no se come de noche, no se carga de día.', 'El ayuno es un acto simbólico: matar al yo impulsivo para que nazca el yo consciente.', 'Te vas a sorprender de lo mucho que puedes lograr sin comer a deshoras.', NULL, NULL, NULL, 'Guía Fase I, indicaciones generales A', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('7bfe04e4-20bc-4175-85dd-195436a321a9', '60d6c870-df03-4d36-8a88-8c7411ae406b', 46, NULL, 'Descansas. Silencias. Integras. Opcionalmente, domingo sin celular.', 'Sin exigencia. El domingo no se rinde cuentas.', NULL, 'Yo confío en la vida incluso cuando no hago.', 'Descansar es parte de crear.', NULL, NULL, NULL, NULL, 'Guía Fase III, protocolo IV', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('d0e1a859-5a6e-44ed-95e1-84a457c9c339', '830c3d76-888a-4aef-bb30-fb0f0cc7ca73', 35, NULL, 'Tu publicación diaria en la comunidad.', 'Escribes para integrar, no para impresionar.', NULL, NULL, NULL, NULL, 'Mantra del post en Skool', 'Integrar a través de la palabra', 'Hoy escribo para integrar, no para impresionar.
Mi verdad expresada se convierte en claridad interna.
Al escribir, me escucho.

(Inhala profundo)
(Exhala diciendo: gracias)', 'Guía Fase III, mantra del post en Skool', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('38d56b8e-9896-41e8-a2fd-ba51e4a057e4', '830c3d76-888a-4aef-bb30-fb0f0cc7ca73', 8, 34, 'Cada día postea respondiendo seis preguntas: cómo me fue hoy, de qué me quejé, a quién culpé, qué esperé en vez de actuar, qué emoción dominó mi día y cuál fue mi verdad del día.', 'Diez minutos de seguimiento diario. En los días de desintoxicación, escribe solo lo bueno.', NULL, 'Este ciclo es un espejo: aquí no estás cambiando nada, estás viendo cómo piensas, cómo reaccionas, cómo te excusas y cómo evitas decidir.', NULL, NULL, NULL, NULL, NULL, 'Guía Fase II, actividades obligatorias', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('e2e6cc4c-5488-4d7f-bef4-a13a3179b707', '830c3d76-888a-4aef-bb30-fb0f0cc7ca73', 1, 7, 'Al terminar tu día, después de cumplir todas las actividades, publica en la comunidad respondiendo cuatro bloques: cómo me fue hoy, qué aprendí, cuál fue mi revelación del día, y sube tus evidencias.', 'Describe con honestidad: emociones, dificultades, avances, momentos de claridad, desbordes o resistencias. Las evidencias pueden ser tu jugo verde, tus notas, tu caminata, tu ritual, un libro que tocaste, una decisión tomada, un pequeño logro.', NULL, 'Compartir tus avances activa responsabilidad, memoria de largo plazo, compromiso y claridad narrativa. En Renaser nunca estás solo.', 'Postear cada día es un acto de no victimismo: es sostener tu proceso públicamente.', 'Integras tu verdad, te haces visible, rompes la vergüenza y recibes la energía del grupo.', NULL, NULL, NULL, 'Guía Fase I, sección 9', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('1f823aab-8508-41a7-9f4f-788b82cea7f5', '358caf57-519f-4b12-8cbe-c7094d0523d8', 46, NULL, 'Descansas. Silencias. Integras.', 'No te exiges, no te juzgas, no te persigues.', NULL, 'Yo confío en la vida incluso cuando no hago.', 'Descansar es parte de crear.', NULL, 'Mantra del descanso real', 'Descansar es parte de crear', 'Hoy descanso con dignidad.
No me exijo, no me juzgo, no me persigo.
Confío en que todo lo vivido se asienta suavemente en mí.
Descansar es parte de crear.

(Inhala profundo)
(Exhala diciendo: gracias)', 'Guía Fase III, protocolo IV', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('1b8e901f-2758-4563-b260-1e8c1e85cba8', '5449b2b9-a5b4-441f-8304-2c37c425a114', 1, NULL, 'Tu última comida es a las 6:00 pm. Tu primera comida del día siguiente, entre las 10:00 y las 11:00 am.', 'Con calma. Sin lucha. Sin castigo. Escuchando el cuerpo, bebiendo agua, respirando.', 'El ayuno regula el cortisol, baja la inflamación, mejora la memoria y la claridad mental, disminuye la ansiedad, aumenta la energía estable y activa la autofagia (limpieza celular). Tu cerebro entra en modo observación: la niebla mental baja y tu sistema emocional se estabiliza.', 'Cuando el cuerpo se calma, la mente revela lo que oculta. El hambre emocional, la ansiedad y la compulsión dejan de confundirse con hambre real.', 'El ayuno es un acto simbólico: matar al yo impulsivo para que nazca el yo consciente.', 'Vas a pensar con más claridad. Vas a sentir más calma. Vas a notar tus emociones sin ruido. Vas a tener más energía real: no explosiva, sino estable.', 'Mantra del ayuno del príncipe', NULL, 'Hoy decido ayunar, y lo hago con honor.
El ayuno es silencio para mi cuerpo y claridad para mi mente.
A través de este silencio, ordeno a mis células que recuerden su inteligencia original, su capacidad natural de regenerarse y su sabiduría ancestral.
Ordeno a mi mente a cultivar paciencia, templanza y confianza.
Ordeno a mi estómago, centro de mis emociones, a aprender a sostener, a tolerar y a no reaccionar desde la ansiedad.
Fortalezco mi carácter con dulzura, porque no soy un ser impulsivo gobernado por el vacío: soy un príncipe consciente que se nutre por elección.
Hoy me alimento de respiración, de presencia y de amor propio.', 'Guía Fase I, indicaciones generales A · mantra de Guía Fase III', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('05ab8801-d30a-49a5-bf62-af28428cd2b9', '4dee0fa3-e285-4b7d-b062-ad0001dde314', 35, NULL, 'Los mismos siete minutos: Tierra, Agua y Fuego. A esta altura ya no es una técnica, es tu forma de empezar el día.', 'Te detienes unos minutos para volver a ti antes de salir al mundo.', NULL, 'Intencionas el día no desde el control, sino desde la coherencia.', NULL, NULL, 'Mantra del ritual de la mañana', 'Recordar quién soy antes de salir al mundo', 'Me detengo unos minutos para volver a mí.
Aquí, en este silencio, no soy problema, no soy carencia, no soy pasado.
Soy presencia viva.
Intenciono este día no desde el control, sino desde la coherencia.
Hoy camino alineado conmigo.

(Inhala profundo)
(Exhala diciendo: gracias)', 'Guía Fase III, mantra del ritual de la mañana', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('bc1136d2-b16c-4e0e-a38b-3ba820da6365', '4dee0fa3-e285-4b7d-b062-ad0001dde314', 1, 34, 'Siete minutos. Tierra (1 minuto): observar sin querer cambiar nada, aceptación y presencia. Agua (3 minutos): respiración profunda, soltar emociones. Fuego (3 minutos): respiración fuerte y decidida, activar tu poder personal.', 'Este ejercicio está enfocado en viajar hacia tu interior, con el objetivo de despertar tu intención hacia un objetivo.', 'Baja el cortisol, regula la amígdala, activa la corteza frontal, aumenta la claridad y la valentía.', 'Es tu entrenamiento emocional base. Sin esto, no puedes entrar a Autoterapia.', 'Tierra: aceptas. Agua: sueltas. Fuego: renaces.', 'Reduce la ansiedad de inmediato y disminuye la tensión acumulada.', NULL, NULL, NULL, 'Guía Fase I, indicaciones generales E', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('9da10744-6bea-4fcc-b47a-ec953dfda453', '63acbd12-9792-495c-be22-6280ecba53b3', 1, NULL, 'Siete minutos a mitad del día para volver al cuerpo: Tierra, Agua y Fuego.', 'Con calma, donde estés. No necesitas un lugar especial, necesitas parar.', 'Baja el cortisol, regula la amígdala, activa la corteza frontal, aumenta la claridad y la valentía.', 'Es tu entrenamiento emocional base. Sin esto, no puedes entrar a Autoterapia.', 'Tierra: aceptas. Agua: sueltas. Fuego: renaces.', NULL, NULL, NULL, NULL, 'Guía Fase I, indicaciones generales E', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('c7c5db49-71c5-4f8b-8ca9-e7f8c75bd183', '679188b9-7c1d-48ec-ae09-8a76b87badbf', 1, NULL, 'Siete minutos para cerrar el día: Tierra, Agua y Fuego.', 'Bajando el ritmo. Cerrando el día con amor, no con reproche.', 'Baja el cortisol y prepara al sistema nervioso para el descanso.', 'Es tu entrenamiento emocional base. Sin esto, no puedes entrar a Autoterapia.', 'Tierra: aceptas. Agua: sueltas. Fuego: renaces.', NULL, 'Mantra del ritual nocturno', 'Cerrar el día sin violencia interior', 'Este día fue valioso.
No perfecto, pero profundamente útil para mi evolución.
Agradezco lo que comprendí y descanso en lo que aún estoy aprendiendo.
Hoy me duermo en paz conmigo.

(Inhala profundo)
(Exhala diciendo: gracias)', 'Guía Fase I, indicaciones generales E · mantra de Guía Fase III', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('834c4bd0-d62f-4ca4-97f9-7b971f83d61f', 'feeea74a-d811-47a2-a175-34f2527c1d32', 35, NULL, 'Tu clase diaria de 10 a 15 minutos, después del ritual.', 'Sin juicio y sin prisa. Escuchar es parte del trabajo, no un trámite.', NULL, NULL, NULL, NULL, 'Mantra de la clase diaria', 'Aprender sin exigencia', 'Hoy me permito aprender sobre mí sin juicio y sin prisa.
El conocimiento no me hace superior, me hace consciente.
Cada comprensión nueva me libera un poco más.

(Inhala profundo)
(Exhala diciendo: gracias)', 'Guía Fase III, mantra de la clase diaria', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('2a50aa9f-a7b6-441f-8366-72728dfe91f5', 'feeea74a-d811-47a2-a175-34f2527c1d32', 1, 34, 'Un audio-podcast oficial Renaser por día, de 10 a 15 minutos. Escúchalo después del ritual Tierra-Agua-Fuego, en un lugar en silencio, completo y sin adelantar.', 'No lo escuches haciendo otra cosa y no tomes notas durante el audio: lo importante es sentir y contemplar. Al terminar respira profundo nueve veces y recién ahí abre tu cuaderno y escribe el resumen.', 'Activa regiones cerebrales de integración emocional, memoria profunda y aprendizaje significativo; regula el sistema nervioso y reduce el ruido mental. Después de respirar, la corteza prefrontal está más abierta y la plasticidad neuronal aumenta.', 'Cada audio está diseñado para romper un patrón: miedo, culpa, ansiedad, sobrepensamiento, autosabotaje, dependencia emocional o resistencia al cambio.', 'Tú no estás escuchando un podcast: estás decodificando tu mente.', 'Mayor claridad, más capacidad de introspección, menos ansiedad y mejor comprensión de ti mismo. Se activan procesos de sanación y aparecen ideas que cambian tu forma de ver tu historia.', NULL, NULL, NULL, 'Guía Fase I, sección 8', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('0c57176a-288d-4886-8b24-5b35860cd503', '593b2c17-8acd-49d0-b8f2-199a48469715', 1, NULL, 'Diez minutos de escritura libre al final del día, en tu cuaderno personal.', 'Escribe el resumen del audio (lo que entendiste), las emociones que aparecieron, las ideas o revelaciones nuevas, qué parte tocó tu herida o tu verdad, y una decisión pequeña que vas a tomar a partir de eso.', NULL, 'Escribir hace que el aprendizaje pase de tu mente a tu identidad.', NULL, 'Vas a descubrir tus patrones automáticos, vas a ver tus horas de ansiedad y cómo se comporta tu energía. Por primera vez vas a tener un mapa de quién eres realmente.', NULL, NULL, NULL, 'Guía Fase I, secciones 4 y 8', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('b8f290ae-86bf-4b2d-b8f4-2f7a31b65a57', 'd2d58e66-db7f-4226-9c95-30b380f68b73', 1, NULL, 'Elige uno de los 7 días para vivir sin celular. La noche anterior apágalo, guárdalo en una caja, cajón o mochila, y no lo uses por 24 horas: recién lo enciendes al despertar del día siguiente. Planifica antes para no quedarte con pendientes.', 'Si de verdad necesitas una llamada o revisar algo urgente, usa el celular de alguien cercano solo para esa actividad puntual.', 'Las pantallas alteran tu dopamina y tu atención. Quitarlas por 24 horas aumenta tu productividad tres veces, restaura tu foco, regula tu sistema de recompensa, disminuye la ansiedad y mejora tu capacidad de sentir.', 'El silencio digital trae a la superficie tus emociones reales. Ahí te conoces.', 'Es un ayuno de estímulos. Es volver al vacío para escucharte.', 'Vas a sentir paz. Vas a ver cuánto tiempo realmente tienes. Vas a ser tres veces más productivo. Tu mente va a estar presente y vas a descubrir pensamientos que no escuchabas.', NULL, NULL, NULL, 'Guía Fase I, indicaciones generales D', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('ab1f6b9a-3128-4047-9a8d-595b84327e3d', 'bb0b1cd0-18df-4bcd-9a4e-9e1c63905c0d', 11, NULL, 'Tu sesión semanal de reprogramación auditiva.', 'Te entregas al viaje interno con confianza, sin necesidad de entenderlo todo ahora.', NULL, 'Tu inconsciente sabe cómo ordenar lo que tu mente no puede.', NULL, NULL, 'Mantra de la audioterapia', 'Permitirse ser guiado', 'Me entrego a este viaje interno con confianza.
No necesito entender todo ahora.
Mi inconsciente sabe cómo ordenar lo que mi mente no puede.
Me permito recibir.

(Inhala profundo)
(Exhala diciendo: gracias)', 'Guía Fase II · mantra de Guía Fase III', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00'),
    ('1278f94d-4ade-4183-8d46-7fed7e446faa', '3a922b73-6d11-48f9-89c2-abc453ca6ef7', 46, NULL, 'Hidratación del domingo, con la misma presencia del resto de la semana.', 'Cada sorbo con atención, sin prisa.', NULL, 'El agua entra y limpia lo que ya no necesitas cargar.', 'El agua como memoria de vida.', NULL, 'Mantra de agua e hidratación consciente', 'El agua como memoria de vida', 'Hoy me permito beber agua con presencia total.
No bebo por hábito, no bebo por ansiedad, bebo porque honro la vida que corre dentro de mí.
Así como el agua no lucha para avanzar, yo dejo de resistirme a la vida.
Cada sorbo es un mensaje para mis células: estamos a salvo, podemos fluir.

(Inhala profundo)
(Exhala lentamente diciendo: gracias)', 'Guía Fase III, mantra de agua e hidratación consciente', '2026-08-01 16:53:15.811513+00', '2026-08-01 18:02:54.518007+00');

-- ============================================================================
-- ADJUNTOS_GUIA (2 filas validas de 3 en origen -- 1 huerfana excluida, ver arriba)
-- ============================================================================

INSERT INTO adjuntos_guia (
    id, guia_id, seccion, tipo_medio, url, ruta_storage, mime, tamano_bytes,
    nombre_original, titulo, orden, creado_en, actualizado_en
) VALUES
    ('4e7d65c7-1c81-4074-8f22-56871b576b2e', 'bc1136d2-b16c-4e0e-a38b-3ba820da6365', 'COMO_HACERLO', 'ENLACE', 'https://www.youtube.com/watch?v=WJBVncaQKQE&t=1s', NULL, NULL, NULL, NULL, NULL, 0, '2026-08-11 21:54:27.549+00', '2026-08-11 21:54:27.549+00'),
    ('be810f29-d20a-4537-bace-90dbb0150e34', '9da10744-6bea-4fcc-b47a-ec953dfda453', 'COMO_HACERLO', 'ENLACE', 'https://www.youtube.com/watch?v=WJBVncaQKQE&t=1s', NULL, NULL, NULL, NULL, NULL, 0, '2026-08-11 22:56:02.108+00', '2026-08-11 22:56:02.108+00');

-- ============================================================================
-- AUDIOS_ESPIRITU (43 de 90 dias -- contenido incompleto en origen, no defecto de la carga)
-- ============================================================================

INSERT INTO audios_espiritu (
    dia, titulo, drive_file_id, mime, tamano_bytes, creado_en, actualizado_en
) VALUES
    (13, 'Día del amor, día de los vacíos', '1uY8UHDnLltXBya682tj4nIF1JqTpaHrq', 'audio/mpeg', 6002336, '2026-08-05 15:55:59.813', '2026-08-05 16:18:50.45'),
    (16, 'Problemas benditos son', '1-PKqTqHdGI3wmY4kki8UnM0z9PrUOjGg', 'audio/mpeg', 3374624, '2026-08-05 15:56:00.166', '2026-08-05 16:18:50.673'),
    (17, 'Morir para renaser', '1P0Of9R9FiS769JDD4zUx4yJOJ6W3MA0B', 'audio/mpeg', 6342368, '2026-08-05 15:56:00.28', '2026-08-05 16:18:50.781'),
    (20, 'Cómo superar los miedos, inseguridades y ansiedad - PARTE3', '1cYOuturrprh2H8z1I1NEyNnzPqLsIBeK', 'audio/mpeg', 7768896, '2026-08-05 15:56:00.391', '2026-08-05 16:18:50.887'),
    (19, 'Cómo superar los miedos, inseguridades y ansiedad  - PARTE2', '18-Iz0vVIuYkZMduAUK7xC9WJfU_PCN0T', 'audio/mpeg', 10174656, '2026-08-05 15:56:00.502', '2026-08-05 16:18:50.997'),
    (18, 'Cómo superar los miedos, inseguridades y ansiedad - PARTE1', '1Gz5mpg1wzDTCCfpfaoB25cIuBOsYtd2s', 'audio/mpeg', 9249024, '2026-08-05 15:56:00.612', '2026-08-05 16:18:51.106'),
    (21, 'Siendo imperfecto - Mentoría Con Darren', '1ssxAz0x53D7MKr-rXBp8-cR4Tdw67CzN', 'audio/mpeg', 11193500, '2026-08-05 15:56:00.726', '2026-08-05 16:18:51.216'),
    (23, '¿Nada tiene sentido en tu vida', '12Xncpee-xVAXfutrmvrHZhG0tiEv0CBu', 'audio/mpeg', 9997088, '2026-08-05 15:56:00.837', '2026-08-05 16:18:51.326'),
    (25, 'Sana la infidelidad de tu pareja', '1592Y3cZfWmqiqmR2hj4Q6Z8GIts0HvEZ', 'audio/mpeg', 11529056, '2026-08-05 15:56:00.947', '2026-08-05 16:18:51.436'),
    (26, '¿Por qué tienes problemas con el dinero', '1C_90PTaJN1xPVg1p-8CJHQaaGRV6fAWD', 'audio/mpeg', 6021888, '2026-08-05 15:56:01.06', '2026-08-05 16:18:51.543'),
    (27, 'Cómo convertirte en una reina', '1CTM330R06SfW7sHPMAL4wg_CUzsDn1tR', 'audio/mpeg', 6524467, '2026-08-05 15:56:01.174', '2026-08-05 16:18:51.654'),
    (33, 'La raiz de todo sufrimiento', '1HHO3nLP2OkTfAsIkUEKwgOp3Nlkmon03', 'audio/mpeg', 6358688, '2026-08-05 15:56:01.285', '2026-08-05 16:18:51.764'),
    (22, 'Esta mal estar bien', '10gaF8eVSpEJYx6vHhuwIoPYr2zuG9HjQ', 'audio/mpeg', 4545248, '2026-08-05 15:56:01.398', '2026-08-05 16:18:51.872'),
    (43, 'Rompe-el-patrón-0de-no-incomodar', '1wT5JjUgoCVtn_U5-6e6FDDmEoPIjYrf5', 'audio/mpeg', 7642989, '2026-08-05 15:56:01.511', '2026-08-05 16:18:51.984'),
    (42, 'CONFÍA-EN-TU-MAYOR-SOCIO', '1T2UeG3ve1ijULp7PNQpF3k6aE7GUZU6U', 'audio/mpeg', 8653485, '2026-08-05 15:56:01.624', '2026-08-05 16:18:52.091'),
    (41, 'La vida', '15iQfbRzF3jPJDnIcz2aUkk3_1Ar0eoji', 'audio/mpeg', 8152754, '2026-08-05 15:56:01.738', '2026-08-05 16:18:52.198'),
    (40, 'Te frustras por pesimista', '1-ZAJ4bd2q0u3VmzZ-MHfwjr0PzRNNSjZ', 'audio/mpeg', 7331762, '2026-08-05 15:56:01.849', '2026-08-05 16:18:52.31'),
    (39, 'Pastilla 2', '13HUWA7oPKb1FnbSw1ciNqh32jCOHH0bM', 'audio/mpeg', 9348914, '2026-08-05 15:56:01.961', '2026-08-05 16:18:52.419'),
    (38, 'Pastilla 1', '1-d33PFbwuN9etY5_t8k6bxiea6QTy-4C', 'audio/mpeg', 3922994, '2026-08-05 15:56:02.072', '2026-08-05 16:18:52.526'),
    (37, 'Nadie te salvara', '16ALPLD62nxqEEKTzcNQ-PTGaJQ3NcFq9', 'audio/mpeg', 6731378, '2026-08-05 15:56:02.185', '2026-08-05 16:18:52.633'),
    (36, 'No necesitas pedir perdón', '1Xutq81qBU3QNoSF1ESVjDQuAmofGtPyr', 'audio/mpeg', 9053810, '2026-08-05 15:56:02.306', '2026-08-05 16:18:52.741'),
    (35, 'Ya no puedes con tus problemas', '1bSanoQRCieVzsRt9PsemL2oaNVxhzjNg', 'audio/mpeg', 5887336, '2026-08-05 15:56:02.415', '2026-08-05 16:18:52.849'),
    (34, 'problema benditos', '1ia5-8N0Ky71av0Z8JfhztLg4FjNwe87S', 'audio/mpeg', 3374624, '2026-08-05 15:56:02.529', '2026-08-05 16:18:52.956'),
    (32, 'La-oscuridad-en-ti', '1kw5aCFcTjdcr2lSQci_PB9DHoNn75dFP', 'audio/mpeg', 6514797, '2026-08-05 15:56:02.639', '2026-08-05 16:18:53.062'),
    (31, 'Eres-un-inutil', '1wd613aUFc51G8lCm7d3e_WIpvX4hoAd2', 'audio/mpeg', 6372141, '2026-08-05 15:56:02.749', '2026-08-05 16:18:53.168'),
    (30, 'Sentir-y-conectar', '1s6J46jdfyLrKdrgAlyj9K7epFg2IlVk8', 'audio/mpeg', 10135917, '2026-08-05 15:56:02.863', '2026-08-05 16:18:53.275'),
    (29, 'Mantener-la-disciplina', '1PcKyNE6FnoCw7k_vwqCoUmoJEE9d6HqU', 'audio/mpeg', 7404909, '2026-08-05 15:56:02.973', '2026-08-05 16:18:53.383'),
    (28, 'No seas mediocre', '1aFwM9x1RlQC6qDrBRTPx-nn20es2DG1s', 'audio/mpeg', 6286308, '2026-08-05 15:56:03.082', '2026-08-05 16:18:53.489'),
    (24, 'Ama el fracaso', '15NHX_5FyE-gpvudRL3S2k3--co5ThYLt', 'audio/mpeg', 5541312, '2026-08-05 15:56:03.193', '2026-08-05 16:18:53.598'),
    (15, 'Estás frustrado por tu pesimismo', '1uHM1vYyHVTtl5yJDPwHsgFhKCyDop7MA', 'audio/mpeg', 7331168, '2026-08-05 15:56:03.307', '2026-08-05 16:18:53.711'),
    (14, 'Renaser todos los dias', '1nl3iNIZCB6JN6h_STXG4fndsTMRMM2mh', 'audio/mpeg', 4847264, '2026-08-05 15:56:03.419', '2026-08-05 16:18:53.82'),
    (10, 'Nadie te salvará, cree más en ti', '1UBWV8KCirceGcwDIqePZ8JxTuWAgW1F5', 'audio/mpeg', 6730792, '2026-08-05 15:56:03.53', '2026-08-05 16:18:53.933'),
    (9, 'Tengo miedo', '1HQDYot8LGBowwq7Ypj1h038Cv3B7ZZcK', 'audio/mpeg', 3966368, '2026-08-05 15:56:03.651', '2026-08-05 16:18:54.04'),
    (8, 'Compites olvidando tu esencia femenina', '1q5STvutIjKBsSmxbL7VvPX2mZgVdWQ_F', 'audio/mpeg', 8963203, '2026-08-05 15:56:03.777', '2026-08-05 16:18:54.147'),
    (6, 'Tu niño interior es prisionero de tus miedos?', '1-lUVpiE7MJOHeiLXhJep-oIR9v5NftQN', 'audio/mpeg', 4322162, '2026-08-05 15:56:03.892', '2026-08-05 16:18:54.261'),
    (12, '¿Sientes que tienes demasiado problemas?', '1qiPvXfxshYkk70JIgNcWNzcx5vJt1z69', 'audio/mpeg', 5887336, '2026-08-05 15:56:04.002', '2026-08-05 16:18:54.372'),
    (7, '¿Cómo abrirte a sentir?', '1rS1R3xulleJxs3_pO6j8uDu8pW88mQ8u', 'audio/mpeg', 3943912, '2026-08-05 15:56:04.115', '2026-08-05 16:18:54.491'),
    (1, 'Estas sufriendo por víctima', '1_W-Ww4Z3YBJhOLsjssbwlJoseYAz5b7Q', 'audio/mpeg', 6358688, '2026-08-05 15:56:04.227', '2026-08-05 16:18:54.6'),
    (11, '¿Tus padres se equivocaron contigo', '1tpK6Yzz20ZazLHxqNB3lSgGSGO3P6q2C', 'audio/mpeg', 4772200, '2026-08-05 15:56:04.338', '2026-08-05 16:18:54.706'),
    (5, 'Confía en tu proceso', '1xNYOPl6pEys-X-1Fu6IxWSFegZLwGBed', 'audio/mpeg', 4041440, '2026-08-05 15:56:04.452', '2026-08-05 16:18:54.816'),
    (2, '¿Qué necesitas para vivir intensamente', '1xR1xeZM1oULqkhYk0PbrkXtVdjcE1N5i', 'audio/mpeg', 4940384, '2026-08-05 15:56:04.565', '2026-08-05 16:18:54.924'),
    (3, '¿Qué te impide abrir tu alma', '1VxsBS4iA6CNcp0pmrxK2qzRoRSwaPr3_', 'audio/mpeg', 6861536, '2026-08-05 15:56:04.676', '2026-08-05 16:18:55.031'),
    (4, 'Disfrutar tu vida', '1_g_ePLIOdJaH_fOYv5IL1ykmiioCO7aV', 'audio/mpeg', 6963680, '2026-08-05 15:56:04.786', '2026-08-05 16:18:55.14');

-- ============================================================================
-- AUDIOTERAPIAS (13 de 13 semanas -- completo)
-- ============================================================================

INSERT INTO audioterapias (
    semana, titulo, ruta_storage, mime, tamano_bytes, creado_en, actualizado_en
) VALUES
    (1, 'Despierta tu potencial infinito', 'terapiarenaserconexiondarrensupa-canalrenaserdespiertatupotencialinfinito-ivoox145086556.mp3', 'audio/mpeg', 42723369, '2026-08-05 19:53:54.9', '2026-08-05 19:53:54.9'),
    (2, 'Supera tu frustración', 'superatufrustracionsesionconsciencia-canalrenaserdespiertatupotencialinfinito-ivoox145291910.mp3', 'audio/mpeg', 74772898, '2026-08-05 20:03:28.339', '2026-08-05 20:03:28.339'),
    (3, 'Despierta tu potencial infinito', 'audioterapiarenaser02sienterie-canalrenaserdespiertatupotencialinfinito-ivoox146374425.mp3', 'audio/mpeg', 28790282, '2026-08-05 20:04:12.729', '2026-08-05 20:04:12.729'),
    (4, 'Despierta tu potencial infinito', 'sesionterapiasanatulinajefemenino-canalrenaserdespiertatupotencialinfinito-ivoox146738533.mp3', 'audio/mpeg', 45219004, '2026-08-05 20:05:15.411', '2026-08-05 20:05:15.411'),
    (5, 'Viaje renaser ,la magia imperfeccion', 'sanapapaprogramarenaser90d-canalrenaserdespiertatupotencialinfinito-ivoox150018788.mp3', 'audio/mpeg', 35358929, '2026-08-05 20:06:42.824', '2026-08-05 20:06:42.824'),
    (6, 'Despierta tu potencial infinito', 'viajerenaserlamagiaimperfeccion-canalrenaserdespiertatupotencialinfinito-ivoox148063182.mp3', 'audio/mpeg', 30845805, '2026-08-05 20:07:14.947', '2026-08-05 20:07:14.947'),
    (7, 'Despierta tu potencial infinito', 'sanatudolorenfermedadsesionde-canalrenaserdespiertatupotencialinfinito-ivoox148176245.mp3', 'audio/mpeg', 20255556, '2026-08-05 20:10:53.243', '2026-08-05 20:10:53.243'),
    (8, 'Sana la infideliada de tu pareja', '028Sana-la-infidelidad-de-tu-par07.mp3', 'audio/mpeg', 10423412, '2026-08-05 20:11:20.536', '2026-08-05 20:11:20.536'),
    (9, 'Nada tiene sentido en tu vida', '023Nada-tiene-sentido-en-tu-vida08.mp3', 'audio/mpeg', 7589785, '2026-08-05 20:11:37.414', '2026-08-05 20:11:37.414'),
    (10, 'Ama el fracaso', '027Ama-el-fracaso-209.mp3', 'audio/mpeg', 4250908, '2026-08-05 20:11:51.187', '2026-08-05 20:11:58.006'),
    (11, 'Por que tines problemas', '029Por-qu-tienes-problemas-con-e-10.mp3', 'audio/mpeg', 4873335, '2026-08-05 20:12:16.309', '2026-08-05 20:12:16.309'),
    (12, 'Como convertirte en una reina', '031Cmo-convertirte-en-una-reina-11.mp3', 'audio/mpeg', 11334716, '2026-08-05 20:12:41.704', '2026-08-05 20:12:41.704'),
    (13, 'No seas mediocre', '033No-seas-mediocre-4-12.mp3', 'audio/mpeg', 5049074, '2026-08-05 20:12:58.839', '2026-08-05 20:12:58.839');

COMMIT;
