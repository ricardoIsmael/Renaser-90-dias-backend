-- V149 — La versión 1 de los términos de la Escuela y los borradores de las otras dos formaciones (spec 027; D-304)
--
-- Por qué se toca la base
-- -----------------------
-- Las 23 cláusulas que la app lleva escritas son, desde hoy, la VERSIÓN 1 de los términos de la Escuela, ya publicada: las personas que entran ven exactamente lo
-- mismo de siempre (SC-006) y las que ya aceptaron figurarán como aceptantes de esa versión (V150). El texto es el de `terminosData.ts` de la app, sin cambiar una coma.
--
-- El Diplomado y la Certificación nacen con una COPIA de los de la Escuela en BORRADOR, sin publicar: hay que revisarla y publicarla antes de aceptar a alguien en
-- esa formación (decisión del dueño, 31/10: «Uno por formación, copia de la Escuela en borrador»). «Por revisar» no es una columna: es una formación que todavía
-- no tiene ninguna versión publicada.

SET search_path TO renaser, public;

INSERT INTO terminos_versiones (id, formacion, estado, numero, titulo, introduccion, clausulas, texto_casilla, nota, pide_nueva_aceptacion, publicada_en)
VALUES (
    '00000000-0000-4000-8000-000000000148', 'ESCUELA', 'PUBLICADA', 1, 'Términos y condiciones',
    'Al inscribirme en el programa Renaser, declaro que lo hago de manera libre y voluntaria, y acepto íntegramente los siguientes términos:',
    ARRAY[
        'Declaro que, previo a mi inscripción, conozco el contenido del programa y acepto plenamente su estructura, duración, modalidad, metodología, dinámica y materiales que lo conforman.',
        'Reconozco que el contenido del programa ha sido desarrollado con dedicación, compromiso y altos estándares de calidad, integrando la experiencia y el conocimiento del equipo creador y de Darren Supa, con el propósito de generar bienestar y transformación en cada participante. Toda la información, dinámicas, documentos y materiales compartidos dentro del programa son de uso estrictamente personal e individual. Queda prohibida su reproducción, distribución o difusión no autorizada.',
        'Entiendo y acepto que el acceso al programa es personal e intransferible, lo que incluye la prohibición de compartir credenciales de acceso, enlaces, documentos, clases o metodologías con terceros.',
        'Cualquier indicio de uso compartido, cesión a terceros o utilización indebida facultará a Renaser a cancelar de forma inmediata el acceso, sin derecho a reembolso ni compensación alguna.',
        'Reconozco que la totalidad del contenido del programa —incluyendo presentaciones, grabaciones, audios, metodologías, textos y gráficos— constituye propiedad intelectual exclusiva de Renaser.',
        'Este reconocimiento se extiende igualmente a los productos físicos asociados al programa, tales como agendas, planners, cuadernos, journals, separadores y librillos protegidos por derechos de autor.',
        'Declaro que no estoy autorizada(o) a reproducir, comunicar públicamente, distribuir, copiar, traducir, adaptar o vender el contenido del programa por ningún medio.',
        'Comprendo que la entrega del material constituye una autorización limitada para su uso exclusivo con fines de aprendizaje durante la vigencia del programa.',
        'En caso de vulneración de los derechos de propiedad intelectual, Renaser se reserva el derecho de ejercer las acciones legales pertinentes.',
        'Declaro que el programa tiene como finalidad acompañar el crecimiento personal, emocional, mental y físico a través de herramientas de hábitos y productividad, y que los resultados dependen de la aplicación consciente de lo aprendido.',
        'Asumo plena y absoluta responsabilidad por la implementación de las herramientas y conocimientos adquiridos para el logro de mis objetivos personales.',
        'Acepto que la duración del programa con acompañamiento, acceso a comunidad y entrega de contenidos es de 90 días (tres meses).',
        'Libero de toda responsabilidad a Renaser, Darren Supa y su equipo respecto a los resultados personales obtenidos durante el programa.',
        'Reconozco que el programa no ha sido diseñado para diagnosticar, tratar ni curar condiciones médicas o psicológicas.',
        'Para envíos internacionales, acepto que los impuestos y costos aduaneros no están incluidos en el valor del programa y son asumidos por el participante.',
        'Dispongo de siete (7) días hábiles desde la recepción del kit Renaser para presentar reclamos respecto al material recibido.',
        'Entiendo que durante el proceso pueden surgir retos emocionales, para lo cual el programa ofrece espacios de acompañamiento, sesiones de apoyo y comunidad.',
        'El incumplimiento de las normas de convivencia podrá derivar en la suspensión del acceso a los canales comunitarios.',
        'Reconozco que los eventos presenciales de inauguración o graduación pueden tener condiciones específicas informadas oportunamente.',
        'Asumo la responsabilidad exclusiva de la información declarada en mi ficha inicial para el correcto seguimiento de mi proceso.',
        'Autorizo a Renaser a documentar mi participación y testimonios con fines formativos y de comunidad.',
        'Acepto que cualquier controversia derivada de este acuerdo será resuelta mediante mecanismos de mediación directa.',
        'Entiendo que las herramientas de inteligencia artificial de RENASER son de apoyo e información general, pueden equivocarse y no sustituyen la orientación profesional médica, psicológica, legal o financiera. Las decisiones y acciones que tome a partir de sus respuestas son de mi exclusiva responsabilidad.'
    ],
    'He leído y acepto los Términos y Condiciones',
    'Los términos con los que empezó el programa.', false, TIMESTAMPTZ '2026-07-30 20:59:26+00');

INSERT INTO terminos_versiones (id, formacion, estado, numero, titulo, introduccion, clausulas, texto_casilla, base_numero)
SELECT gen_random_uuid(), f.codigo, 'BORRADOR', NULL, v.titulo, v.introduccion, v.clausulas, v.texto_casilla, NULL
FROM formaciones f
CROSS JOIN terminos_versiones v
WHERE v.formacion = 'ESCUELA' AND v.numero = 1 AND f.codigo <> 'ESCUELA';
