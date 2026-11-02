-- V162 — El Acuerdo de transformación: los términos nuevos de las tres formaciones y lo que se muestra al aceptarlos (spec 039; D-312)
--
-- Por qué se toca la base
-- -----------------------
-- El dueño (2026-11-02) entregó el texto nuevo de los términos y condiciones: el «Acuerdo de transformación», 23 cláusulas, con un bloque final «MI ACEPTACIÓN»
-- (participante, programa, fechas y firma). Los términos ya son datos editables y versionados (spec 027, V148 a V150): esta migración los publica como una versión
-- nueva en cada formación, sin código ni tablas nuevas. Y la constancia de cada aceptación pasa a guardar QUÉ se le mostró a la persona en «Mi aceptación» (el
-- programa, el inicio y el fin) y, si firmó su cronograma de pagos, la huella de lo que firmó.
--
-- Por qué no hacen falta tablas nuevas
-- ------------------------------------
-- · El texto es una versión más de `terminos_versiones`: la Escuela pasa a la versión siguiente (con `pide_nueva_aceptacion`, así quien ya estaba dentro lo acepta
--   de nuevo con su firma al abrir la app); el Diplomado y la Certificación, que no tenían términos publicados (solo un borrador copia de la Escuela, V149),
--   quedan con la versión 1. La publicación queda a nombre del «sistema» (`publicada_por` nulo) y con la fecha de la migración.
-- · Lo que se mostró son cuatro columnas nulas de `aceptaciones_de_terminos` (programa, inicio, fin y huella del cronograma): lo anterior queda como estaba. La
--   confirmación del cronograma NO se duplica: sigue viviendo en `estado_onboarding` (V158) y se escribe con la misma operación de la spec 032.
--
-- Cómo se escribe el texto
-- ------------------------
-- Cada formación lleva el texto LITERAL con el nombre de su programa en la introducción («Formación RENASER», «Diplomado RENASER», «Certificación RENASER»): el
-- editor de Administración muestra exactamente lo que verá la persona. Una cláusula de dos párrafos (18, 21, 22 y 23) es un solo elemento del arreglo con una línea
-- en blanco entre los párrafos; la numeración sigue saliendo de la posición.
--
-- Idempotente
-- -----------
-- Cada versión tiene un id fijo y se inserta solo si ese id no existe; el borrador copia solo se descarta si sigue idéntico a la versión 1 de la Escuela (si
-- Administración ya lo editó, se conserva); las columnas llevan `IF NOT EXISTS`. Correrla dos veces deja la base igual que correrla una.

SET search_path TO renaser, public;

ALTER TABLE aceptaciones_de_terminos
    ADD COLUMN IF NOT EXISTS programa_mostrado  text,
    ADD COLUMN IF NOT EXISTS inicio_mostrado    date,
    ADD COLUMN IF NOT EXISTS fin_mostrado       date,
    ADD COLUMN IF NOT EXISTS cronograma_huella  text;

COMMENT ON COLUMN aceptaciones_de_terminos.programa_mostrado IS
    'El programa que se le mostró en «Mi aceptación» al aceptar, p. ej. «Formación RENASER · 90 días» (V162, spec 039). Nulo en lo anterior.';
COMMENT ON COLUMN aceptaciones_de_terminos.inicio_mostrado IS
    'La fecha de inicio de su programa que se le mostró al aceptar (V162, spec 039). Nulo en lo anterior o si no tenía programa que mostrar.';
COMMENT ON COLUMN aceptaciones_de_terminos.fin_mostrado IS
    'La fecha de finalización de su programa que se le mostró al aceptar (V162, spec 039). Nulo en lo anterior o si no tenía programa que mostrar.';
COMMENT ON COLUMN aceptaciones_de_terminos.cronograma_huella IS
    'La huella (SHA-256 de moneda, fechas y montos, spec 032) del cronograma de pagos que firmó junto con los términos (V162, spec 039). Nulo si no lo firmó.';

-- El borrador que la spec 027 dejó en el Diplomado y la Certificación como copia de la versión 1 de la Escuela ya no sirve: se publica el texto nuevo. Solo si sigue idéntico.
DELETE FROM terminos_versiones d
USING terminos_versiones v
WHERE d.estado = 'BORRADOR' AND d.formacion <> 'ESCUELA'
  AND v.formacion = 'ESCUELA' AND v.estado = 'PUBLICADA' AND v.numero = 1
  AND d.titulo = v.titulo AND d.introduccion = v.introduccion AND d.clausulas = v.clausulas AND d.texto_casilla = v.texto_casilla;


-- ESCUELA
INSERT INTO terminos_versiones (id, formacion, estado, numero, titulo, introduccion, clausulas, texto_casilla, nota, pide_nueva_aceptacion, publicada_en)
SELECT '00000000-0000-4000-8000-000000016201', 'ESCUELA', 'PUBLICADA',
    coalesce((SELECT max(p.numero) FROM terminos_versiones p WHERE p.formacion = 'ESCUELA' AND p.estado = 'PUBLICADA'), 0) + 1,
    'Términos y condiciones',
    'Al inscribirme en Formación RENASER, declaro que lo hago de manera libre y voluntaria, aceptando íntegramente los siguientes términos:',
    ARRAY[
        'Declaro que, previo a mi inscripción, conozco el contenido del programa y acepto plenamente su estructura, duración, modalidad, metodología, dinámica y materiales que lo conforman.',
        'Reconozco que el contenido del programa ha sido desarrollado con dedicación, compromiso y altos estándares de calidad, integrando la experiencia y el conocimiento del equipo creador de Renaser Consulting SAC, con el propósito de impulsar mi desarrollo personal y empresarial.',
        'Comprendo que la metodología RENASER integra herramientas de alto rendimiento, autoconocimiento, hábitos, productividad y desarrollo estratégico, y que mi progreso requiere participación activa, práctica y aplicación de lo aprendido.',
        'Me comprometo a asistir puntualmente a las sesiones, realizar las actividades asignadas y cumplir con las evaluaciones correspondientes. Las inasistencias deberán comunicarse con al menos 24 horas de anticipación, salvo situaciones justificadas.',
        'Reconozco que las inasistencias reiteradas, el incumplimiento de actividades esenciales o la falta de participación pueden afectar mi avance y el acceso a determinados beneficios académicos, conforme a las condiciones de mi programa.',
        'Acepto que la duración, fecha de inicio, fecha de finalización, sesiones, acompañamiento y beneficios serán los establecidos para el programa contratado y confirmados durante mi ingreso a NASER.',
        'Reconozco que NASER es el aplicativo oficial de RENASER, a través del cual accederé a mis contenidos, actividades, evaluaciones, calendario y herramientas de seguimiento.',
        'Entiendo y acepto que el acceso al programa es personal e intransferible, lo que incluye la prohibición de compartir credenciales, enlaces, documentos, clases o metodologías con terceros.',
        'Reconozco que cualquier uso compartido, cesión no autorizada o utilización indebida de los accesos podrá ocasionar la suspensión o cancelación de mi participación y las acciones correspondientes, conforme a las condiciones aceptadas y la legislación aplicable.',
        'Reconozco que la totalidad del contenido del programa, incluyendo presentaciones, grabaciones, audios, metodologías, textos, gráficos y aplicaciones, constituye propiedad intelectual de RENASER o de sus respectivos titulares.',
        'Este reconocimiento se extiende a los productos físicos asociados al programa, tales como agendas, planners, cuadernos, journals, separadores y librillos. Comprendo que estos materiales se entregan para mi aprendizaje personal y no estoy autorizado(a) a reproducirlos, distribuirlos, adaptarlos o comercializarlos sin autorización.',
        'Declaro que el programa tiene como finalidad acompañar mi crecimiento personal y empresarial mediante herramientas de alto rendimiento, autoconocimiento y ejecución, y que los resultados dependen de mi compromiso y aplicación consciente.',
        'Asumo responsabilidad por la implementación de las herramientas adquiridas y comprendo que RENASER no garantiza resultados personales, económicos o empresariales específicos, sin perjuicio de sus obligaciones respecto al servicio contratado.',
        'Reconozco que el programa no ha sido diseñado para diagnosticar, tratar ni curar condiciones médicas o psicológicas, y que las herramientas de autoconocimiento y autoterapia formativa no sustituyen atención profesional especializada.',
        'Entiendo que durante el proceso pueden surgir retos emocionales y experiencias de introspección, para los cuales RENASER podrá ofrecer espacios de acompañamiento formativo, sesiones de apoyo y comunidad, según las características de mi programa.',
        'Me comprometo a mantener una conducta respetuosa con los mentores, colaboradores y demás participantes. El incumplimiento grave de las normas de convivencia podrá derivar en medidas correctivas, restricciones de acceso o terminación del servicio cuando corresponda.',
        'Asumo la responsabilidad de proporcionar información veraz en mi ficha diagnóstica inicial y participar en las evaluaciones de seguimiento necesarias para conocer mi punto de partida, medir mis avances y facilitar el acompañamiento de mi proceso.',
        E'Reconozco que RENASER documenta los procesos, experiencias y resultados de sus participantes a través de fotografías, videos, entrevistas y testimonios, con fines formativos, institucionales y de comunicación.\n\nAutorizo expresamente el uso de mi imagen, voz y testimonios en los medios y para las finalidades que se me hayan informado, incluyendo redes sociales y materiales promocionales, conforme a las condiciones de la autorización correspondiente y mis derechos legales.',
        'Acepto que la inversión, modalidad de pago y fechas de vencimiento serán las establecidas en mi acuerdo de pagos, registrado y validado en NASER. Me comprometo a cumplir con el cronograma aceptado.',
        'Reconozco que los retiros, cancelaciones, devoluciones o reprogramaciones estarán sujetos a las condiciones del programa contratado, los servicios prestados y la legislación aplicable.',
        E'Reconozco que los eventos presenciales de inauguración, encuentros o graduación pueden tener condiciones específicas informadas oportunamente. Para materiales enviados al extranjero, los costos e impuestos aduaneros no incluidos serán asumidos según las condiciones previamente comunicadas.\n\nDispongo de siete días hábiles desde la recepción del kit RENASER para comunicar inconvenientes con los materiales recibidos, sin perjuicio de mis derechos legales.',
        E'Entiendo que las herramientas de inteligencia artificial de RENASER son recursos de apoyo e información general, pueden equivocarse y no sustituyen la orientación profesional médica, psicológica, legal o financiera.\n\nComprendo que el tratamiento de mis datos personales se realizará conforme a la Política de Privacidad y las autorizaciones aplicables.',
        E'Declaro haber leído y comprendido los presentes términos y condiciones, reconociendo que mi participación requiere compromiso y responsabilidad.\n\nAcepto que cualquier controversia se procurará resolver inicialmente mediante comunicación directa, sin limitar mis derechos legales, y confirmo mi aceptación electrónica de este acuerdo.'
    ],
    'He leído y acepto los Términos y Condiciones',
    'Nuevo Acuerdo de transformación',
    EXISTS (SELECT 1 FROM terminos_versiones p WHERE p.formacion = 'ESCUELA' AND p.estado = 'PUBLICADA'),
    now()
WHERE NOT EXISTS (SELECT 1 FROM terminos_versiones WHERE id = '00000000-0000-4000-8000-000000016201');

-- DIPLOMADO
INSERT INTO terminos_versiones (id, formacion, estado, numero, titulo, introduccion, clausulas, texto_casilla, nota, pide_nueva_aceptacion, publicada_en)
SELECT '00000000-0000-4000-8000-000000016202', 'DIPLOMADO', 'PUBLICADA',
    coalesce((SELECT max(p.numero) FROM terminos_versiones p WHERE p.formacion = 'DIPLOMADO' AND p.estado = 'PUBLICADA'), 0) + 1,
    'Términos y condiciones',
    'Al inscribirme en Diplomado RENASER, declaro que lo hago de manera libre y voluntaria, aceptando íntegramente los siguientes términos:',
    ARRAY[
        'Declaro que, previo a mi inscripción, conozco el contenido del programa y acepto plenamente su estructura, duración, modalidad, metodología, dinámica y materiales que lo conforman.',
        'Reconozco que el contenido del programa ha sido desarrollado con dedicación, compromiso y altos estándares de calidad, integrando la experiencia y el conocimiento del equipo creador de Renaser Consulting SAC, con el propósito de impulsar mi desarrollo personal y empresarial.',
        'Comprendo que la metodología RENASER integra herramientas de alto rendimiento, autoconocimiento, hábitos, productividad y desarrollo estratégico, y que mi progreso requiere participación activa, práctica y aplicación de lo aprendido.',
        'Me comprometo a asistir puntualmente a las sesiones, realizar las actividades asignadas y cumplir con las evaluaciones correspondientes. Las inasistencias deberán comunicarse con al menos 24 horas de anticipación, salvo situaciones justificadas.',
        'Reconozco que las inasistencias reiteradas, el incumplimiento de actividades esenciales o la falta de participación pueden afectar mi avance y el acceso a determinados beneficios académicos, conforme a las condiciones de mi programa.',
        'Acepto que la duración, fecha de inicio, fecha de finalización, sesiones, acompañamiento y beneficios serán los establecidos para el programa contratado y confirmados durante mi ingreso a NASER.',
        'Reconozco que NASER es el aplicativo oficial de RENASER, a través del cual accederé a mis contenidos, actividades, evaluaciones, calendario y herramientas de seguimiento.',
        'Entiendo y acepto que el acceso al programa es personal e intransferible, lo que incluye la prohibición de compartir credenciales, enlaces, documentos, clases o metodologías con terceros.',
        'Reconozco que cualquier uso compartido, cesión no autorizada o utilización indebida de los accesos podrá ocasionar la suspensión o cancelación de mi participación y las acciones correspondientes, conforme a las condiciones aceptadas y la legislación aplicable.',
        'Reconozco que la totalidad del contenido del programa, incluyendo presentaciones, grabaciones, audios, metodologías, textos, gráficos y aplicaciones, constituye propiedad intelectual de RENASER o de sus respectivos titulares.',
        'Este reconocimiento se extiende a los productos físicos asociados al programa, tales como agendas, planners, cuadernos, journals, separadores y librillos. Comprendo que estos materiales se entregan para mi aprendizaje personal y no estoy autorizado(a) a reproducirlos, distribuirlos, adaptarlos o comercializarlos sin autorización.',
        'Declaro que el programa tiene como finalidad acompañar mi crecimiento personal y empresarial mediante herramientas de alto rendimiento, autoconocimiento y ejecución, y que los resultados dependen de mi compromiso y aplicación consciente.',
        'Asumo responsabilidad por la implementación de las herramientas adquiridas y comprendo que RENASER no garantiza resultados personales, económicos o empresariales específicos, sin perjuicio de sus obligaciones respecto al servicio contratado.',
        'Reconozco que el programa no ha sido diseñado para diagnosticar, tratar ni curar condiciones médicas o psicológicas, y que las herramientas de autoconocimiento y autoterapia formativa no sustituyen atención profesional especializada.',
        'Entiendo que durante el proceso pueden surgir retos emocionales y experiencias de introspección, para los cuales RENASER podrá ofrecer espacios de acompañamiento formativo, sesiones de apoyo y comunidad, según las características de mi programa.',
        'Me comprometo a mantener una conducta respetuosa con los mentores, colaboradores y demás participantes. El incumplimiento grave de las normas de convivencia podrá derivar en medidas correctivas, restricciones de acceso o terminación del servicio cuando corresponda.',
        'Asumo la responsabilidad de proporcionar información veraz en mi ficha diagnóstica inicial y participar en las evaluaciones de seguimiento necesarias para conocer mi punto de partida, medir mis avances y facilitar el acompañamiento de mi proceso.',
        E'Reconozco que RENASER documenta los procesos, experiencias y resultados de sus participantes a través de fotografías, videos, entrevistas y testimonios, con fines formativos, institucionales y de comunicación.\n\nAutorizo expresamente el uso de mi imagen, voz y testimonios en los medios y para las finalidades que se me hayan informado, incluyendo redes sociales y materiales promocionales, conforme a las condiciones de la autorización correspondiente y mis derechos legales.',
        'Acepto que la inversión, modalidad de pago y fechas de vencimiento serán las establecidas en mi acuerdo de pagos, registrado y validado en NASER. Me comprometo a cumplir con el cronograma aceptado.',
        'Reconozco que los retiros, cancelaciones, devoluciones o reprogramaciones estarán sujetos a las condiciones del programa contratado, los servicios prestados y la legislación aplicable.',
        E'Reconozco que los eventos presenciales de inauguración, encuentros o graduación pueden tener condiciones específicas informadas oportunamente. Para materiales enviados al extranjero, los costos e impuestos aduaneros no incluidos serán asumidos según las condiciones previamente comunicadas.\n\nDispongo de siete días hábiles desde la recepción del kit RENASER para comunicar inconvenientes con los materiales recibidos, sin perjuicio de mis derechos legales.',
        E'Entiendo que las herramientas de inteligencia artificial de RENASER son recursos de apoyo e información general, pueden equivocarse y no sustituyen la orientación profesional médica, psicológica, legal o financiera.\n\nComprendo que el tratamiento de mis datos personales se realizará conforme a la Política de Privacidad y las autorizaciones aplicables.',
        E'Declaro haber leído y comprendido los presentes términos y condiciones, reconociendo que mi participación requiere compromiso y responsabilidad.\n\nAcepto que cualquier controversia se procurará resolver inicialmente mediante comunicación directa, sin limitar mis derechos legales, y confirmo mi aceptación electrónica de este acuerdo.'
    ],
    'He leído y acepto los Términos y Condiciones',
    'Nuevo Acuerdo de transformación',
    EXISTS (SELECT 1 FROM terminos_versiones p WHERE p.formacion = 'DIPLOMADO' AND p.estado = 'PUBLICADA'),
    now()
WHERE NOT EXISTS (SELECT 1 FROM terminos_versiones WHERE id = '00000000-0000-4000-8000-000000016202');

-- CERTIFICACION
INSERT INTO terminos_versiones (id, formacion, estado, numero, titulo, introduccion, clausulas, texto_casilla, nota, pide_nueva_aceptacion, publicada_en)
SELECT '00000000-0000-4000-8000-000000016203', 'CERTIFICACION', 'PUBLICADA',
    coalesce((SELECT max(p.numero) FROM terminos_versiones p WHERE p.formacion = 'CERTIFICACION' AND p.estado = 'PUBLICADA'), 0) + 1,
    'Términos y condiciones',
    'Al inscribirme en Certificación RENASER, declaro que lo hago de manera libre y voluntaria, aceptando íntegramente los siguientes términos:',
    ARRAY[
        'Declaro que, previo a mi inscripción, conozco el contenido del programa y acepto plenamente su estructura, duración, modalidad, metodología, dinámica y materiales que lo conforman.',
        'Reconozco que el contenido del programa ha sido desarrollado con dedicación, compromiso y altos estándares de calidad, integrando la experiencia y el conocimiento del equipo creador de Renaser Consulting SAC, con el propósito de impulsar mi desarrollo personal y empresarial.',
        'Comprendo que la metodología RENASER integra herramientas de alto rendimiento, autoconocimiento, hábitos, productividad y desarrollo estratégico, y que mi progreso requiere participación activa, práctica y aplicación de lo aprendido.',
        'Me comprometo a asistir puntualmente a las sesiones, realizar las actividades asignadas y cumplir con las evaluaciones correspondientes. Las inasistencias deberán comunicarse con al menos 24 horas de anticipación, salvo situaciones justificadas.',
        'Reconozco que las inasistencias reiteradas, el incumplimiento de actividades esenciales o la falta de participación pueden afectar mi avance y el acceso a determinados beneficios académicos, conforme a las condiciones de mi programa.',
        'Acepto que la duración, fecha de inicio, fecha de finalización, sesiones, acompañamiento y beneficios serán los establecidos para el programa contratado y confirmados durante mi ingreso a NASER.',
        'Reconozco que NASER es el aplicativo oficial de RENASER, a través del cual accederé a mis contenidos, actividades, evaluaciones, calendario y herramientas de seguimiento.',
        'Entiendo y acepto que el acceso al programa es personal e intransferible, lo que incluye la prohibición de compartir credenciales, enlaces, documentos, clases o metodologías con terceros.',
        'Reconozco que cualquier uso compartido, cesión no autorizada o utilización indebida de los accesos podrá ocasionar la suspensión o cancelación de mi participación y las acciones correspondientes, conforme a las condiciones aceptadas y la legislación aplicable.',
        'Reconozco que la totalidad del contenido del programa, incluyendo presentaciones, grabaciones, audios, metodologías, textos, gráficos y aplicaciones, constituye propiedad intelectual de RENASER o de sus respectivos titulares.',
        'Este reconocimiento se extiende a los productos físicos asociados al programa, tales como agendas, planners, cuadernos, journals, separadores y librillos. Comprendo que estos materiales se entregan para mi aprendizaje personal y no estoy autorizado(a) a reproducirlos, distribuirlos, adaptarlos o comercializarlos sin autorización.',
        'Declaro que el programa tiene como finalidad acompañar mi crecimiento personal y empresarial mediante herramientas de alto rendimiento, autoconocimiento y ejecución, y que los resultados dependen de mi compromiso y aplicación consciente.',
        'Asumo responsabilidad por la implementación de las herramientas adquiridas y comprendo que RENASER no garantiza resultados personales, económicos o empresariales específicos, sin perjuicio de sus obligaciones respecto al servicio contratado.',
        'Reconozco que el programa no ha sido diseñado para diagnosticar, tratar ni curar condiciones médicas o psicológicas, y que las herramientas de autoconocimiento y autoterapia formativa no sustituyen atención profesional especializada.',
        'Entiendo que durante el proceso pueden surgir retos emocionales y experiencias de introspección, para los cuales RENASER podrá ofrecer espacios de acompañamiento formativo, sesiones de apoyo y comunidad, según las características de mi programa.',
        'Me comprometo a mantener una conducta respetuosa con los mentores, colaboradores y demás participantes. El incumplimiento grave de las normas de convivencia podrá derivar en medidas correctivas, restricciones de acceso o terminación del servicio cuando corresponda.',
        'Asumo la responsabilidad de proporcionar información veraz en mi ficha diagnóstica inicial y participar en las evaluaciones de seguimiento necesarias para conocer mi punto de partida, medir mis avances y facilitar el acompañamiento de mi proceso.',
        E'Reconozco que RENASER documenta los procesos, experiencias y resultados de sus participantes a través de fotografías, videos, entrevistas y testimonios, con fines formativos, institucionales y de comunicación.\n\nAutorizo expresamente el uso de mi imagen, voz y testimonios en los medios y para las finalidades que se me hayan informado, incluyendo redes sociales y materiales promocionales, conforme a las condiciones de la autorización correspondiente y mis derechos legales.',
        'Acepto que la inversión, modalidad de pago y fechas de vencimiento serán las establecidas en mi acuerdo de pagos, registrado y validado en NASER. Me comprometo a cumplir con el cronograma aceptado.',
        'Reconozco que los retiros, cancelaciones, devoluciones o reprogramaciones estarán sujetos a las condiciones del programa contratado, los servicios prestados y la legislación aplicable.',
        E'Reconozco que los eventos presenciales de inauguración, encuentros o graduación pueden tener condiciones específicas informadas oportunamente. Para materiales enviados al extranjero, los costos e impuestos aduaneros no incluidos serán asumidos según las condiciones previamente comunicadas.\n\nDispongo de siete días hábiles desde la recepción del kit RENASER para comunicar inconvenientes con los materiales recibidos, sin perjuicio de mis derechos legales.',
        E'Entiendo que las herramientas de inteligencia artificial de RENASER son recursos de apoyo e información general, pueden equivocarse y no sustituyen la orientación profesional médica, psicológica, legal o financiera.\n\nComprendo que el tratamiento de mis datos personales se realizará conforme a la Política de Privacidad y las autorizaciones aplicables.',
        E'Declaro haber leído y comprendido los presentes términos y condiciones, reconociendo que mi participación requiere compromiso y responsabilidad.\n\nAcepto que cualquier controversia se procurará resolver inicialmente mediante comunicación directa, sin limitar mis derechos legales, y confirmo mi aceptación electrónica de este acuerdo.'
    ],
    'He leído y acepto los Términos y Condiciones',
    'Nuevo Acuerdo de transformación',
    EXISTS (SELECT 1 FROM terminos_versiones p WHERE p.formacion = 'CERTIFICACION' AND p.estado = 'PUBLICADA'),
    now()
WHERE NOT EXISTS (SELECT 1 FROM terminos_versiones WHERE id = '00000000-0000-4000-8000-000000016203');
