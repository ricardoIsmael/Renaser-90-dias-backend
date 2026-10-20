-- V110 — Mensajes del semáforo (D-277 y D-281, spec 006)
--
-- Por qué se toca la base:
--   El dueño pidió que el semáforo de Hoy deje de ser solo un porcentaje y le hable a la persona: un mensaje
--   distinto cada día, personalizado, con el sticker del búho (spec 006, US1 y US2). El mismo catálogo también
--   acompaña la tarjeta diaria del chat de soporte (D-281): dos CANALES, Hoy y el chat. Las reglas (qué mensaje,
--   cuál no repetir, con qué datos) viven en el dominio de `points`; la base guarda dos cosas que el código no
--   puede derivar:
--     1. El CATÁLOGO de mensajes (115 al sembrar): texto con variables, grupo al que pertenece y sticker. Vive en
--        la base y no en el código para poder corregir un texto sin publicar el backend ni un APK.
--     2. Lo ELEGIDO: qué mensaje se le mostró a qué persona qué día y por qué canal. Hace falta para mantener el
--        mismo mensaje todo el día, para no repetirlo en 14 días y para que el del chat sea distinto del de Hoy ese
--        día; no se puede recalcular porque la elección es al azar.
--
-- Por qué así:
--   * `grupo` es el color (VERDE, AMARILLO, ROJO) o, cuando el día no tiene color, el caso (INICIO, SIN_PROGRAMA,
--     PAUSA, CUENTA_PAUSADA, GRADUADO). Son los ocho valores del enum `GrupoDelMensaje` del dominio; el CHECK los
--     repite en las dos tablas porque la invariante se evalúa con los datos de la propia fila (regla 04).
--   * Las VARIABLES (`{nombre}`, `{dia}`, `{fase}`, `{habitoFlojo}`, `{habitoFuerte}`, `{faltan}`) NO se guardan
--     en una columna: se derivan del texto en el dominio (`MensajeDelSemaforo.variables()`). Una columna aparte
--     se desincronizaría en cuanto alguien corrigiera el texto (la lección de V22).
--   * Un mensaje se DESACTIVA (`activo = false`), no se borra: `mensajes_semaforo_elegidos` lo referencia con FK y
--     es la historia de lo que vio cada persona.
--   * `mensajes_semaforo_elegidos` es APPEND-ONLY por código: una fila por persona, fecha, canal y grupo; el código
--     nunca la actualiza ni la borra. Si el color cambia durante el día entra otra fila con el grupo nuevo. La PK
--     (participante_id, fecha, canal, grupo) ya sirve como índice de la lectura de los últimos 14 días (participante y
--     rango de fechas), así que no se crea otro índice. Va con `ON DELETE CASCADE` contra `participantes_programa`,
--     como las tablas del semáforo (V68): si se borra el programa, se va con él.
--   * `canal` (HOY | CHAT, D-281) distingue por dónde llegó el mensaje. Es parte de la PK porque Hoy y el chat eligen
--     cada uno el suyo el mismo día y grupo, y tienen que poder guardarlo sin pisarse; la regla «el del chat es distinto
--     del de Hoy» y los 14 días sin repetir leen los dos canales. Es una columna y no otra tabla porque la historia es
--     una sola: «qué mensaje del catálogo vio esta persona». Default 'HOY': toda fila escrita antes del canal es de Hoy.
--     No se creó V111 porque V110 todavía no está integrada en master: ninguna base de CI ni de producción la tiene
--     aplicada. Solo las bases LOCALES de esta rama (la del E2E) la aplicaron con la versión anterior y fallarían la
--     validación del checksum: se rehacen (borrar su fila de flyway_schema_history y las dos tablas, o repair + aplicar).
--   * Sin CHECK sobre `sticker`: el set de stickers lo fija la app (15 imágenes dentro del APK) y una prueba
--     del catálogo verifica que ningún mensaje use otro nombre. Un CHECK acá obligaría a una migración para
--     cada sticker nuevo.
--   * Fuera de esta migración queda la edición del catálogo desde Administración (spec 006, fuera de alcance).
--
-- La siembra sale de `specs/006-mensaje-semaforo-y-podio/data/mensajes-semaforo.json` (115 mensajes: 35 VERDE,
-- 35 AMARILLO, 30 ROJO y 3 por cada caso sin color). Los ids son los del JSON: v001…v035, a001…a035, r001…r030 y
-- s001…s015.

CREATE TABLE renaser.mensajes_semaforo (
    id       varchar(8)   PRIMARY KEY,
    grupo    varchar(20)  NOT NULL,
    texto    varchar(300) NOT NULL,
    sticker  varchar(40)  NOT NULL,
    activo   boolean      NOT NULL DEFAULT true,
    CONSTRAINT mensajes_semaforo_grupo_valido CHECK (grupo IN
        ('VERDE', 'AMARILLO', 'ROJO', 'INICIO', 'SIN_PROGRAMA', 'PAUSA', 'CUENTA_PAUSADA', 'GRADUADO')),
    CONSTRAINT mensajes_semaforo_texto_con_contenido CHECK (btrim(texto) <> '')
);

CREATE INDEX mensajes_semaforo_grupo_idx ON renaser.mensajes_semaforo (grupo) WHERE activo;

COMMENT ON TABLE renaser.mensajes_semaforo IS
    'Catalogo de mensajes del semaforo de Hoy (D-277). Texto con variables entre llaves. Un mensaje se desactiva, no se borra.';

CREATE TABLE renaser.mensajes_semaforo_elegidos (
    participante_id  uuid         NOT NULL REFERENCES renaser.participantes_programa (usuario_id) ON DELETE CASCADE,
    fecha            date         NOT NULL,
    canal            varchar(10)  NOT NULL DEFAULT 'HOY',
    grupo            varchar(20)  NOT NULL,
    mensaje_id       varchar(8)   NOT NULL REFERENCES renaser.mensajes_semaforo (id),
    elegido_en       timestamptz  NOT NULL,
    PRIMARY KEY (participante_id, fecha, canal, grupo),
    CONSTRAINT mensajes_semaforo_elegidos_canal_valido CHECK (canal IN ('HOY', 'CHAT')),
    CONSTRAINT mensajes_semaforo_elegidos_grupo_valido CHECK (grupo IN
        ('VERDE', 'AMARILLO', 'ROJO', 'INICIO', 'SIN_PROGRAMA', 'PAUSA', 'CUENTA_PAUSADA', 'GRADUADO'))
);

COMMENT ON TABLE renaser.mensajes_semaforo_elegidos IS
    'Que mensaje del semaforo vio cada persona cada dia (fecha local), por canal HOY o CHAT (D-277, D-281). Append-only: mantiene el del dia, evita repetir en 14 dias y hace que el del chat sea distinto del de Hoy.';

INSERT INTO renaser.mensajes_semaforo (id, grupo, texto, sticker) VALUES
    ('v001', 'VERDE', '{nombre}, tu constancia está dando frutos. Mantén el ritmo y demuestra que puedes sostenerlo.', 'dorado-orgulloso'),
    ('v002', 'VERDE', 'Vas por buen camino. Lo que haces cada día está construyendo tu nueva versión.', 'dorado-sereno'),
    ('v003', 'VERDE', '{nombre}, hoy tienes motivos para sentir orgullo. Mañana tendrás otros si sigues cumpliendo.', 'dorado-celebrando'),
    ('v004', 'VERDE', 'Tu disciplina está hablando por ti. No bajes la guardia cuando las cosas empiezan a salir bien.', 'dorado-orgulloso'),
    ('v005', 'VERDE', '{nombre}, estás cumpliendo contigo. Ese compromiso vale más que cualquier aplauso.', 'dorado-trofeo'),
    ('v006', 'VERDE', 'Buen trabajo. Ahora demuestra que tu avance no depende de tener un día fácil.', 'dorado-guino'),
    ('v007', 'VERDE', '{nombre}, llegaste al día {dia} con un ritmo sólido. Sigue haciendo lo que te trajo hasta aquí.', 'dorado-celebrando'),
    ('v008', 'VERDE', 'La constancia se nota en tus resultados. Disfruta el avance sin perder el enfoque.', 'dorado-sereno'),
    ('v009', 'VERDE', '{nombre}, tu esfuerzo está dejando huella. Que el orgullo de hoy impulse tus decisiones de mañana.', 'dorado-orgulloso'),
    ('v010', 'VERDE', 'Estás demostrando de qué eres capaz. El siguiente reto es mantenerlo incluso sin motivación.', 'dorado-trofeo'),
    ('v011', 'VERDE', '{nombre}, ya sabes cómo se siente cumplir. Convierte esa sensación en tu nueva normalidad.', 'dorado-guino'),
    ('v012', 'VERDE', 'No necesitas hacerlo perfecto para avanzar. Tu compromiso sostenido está marcando la diferencia.', 'dorado-sereno'),
    ('v013', 'VERDE', '{nombre}, celebra lo conseguido, pero conserva el hambre de crecer. Todavía tienes mucho por descubrir.', 'dorado-celebrando'),
    ('v014', 'VERDE', 'Cada hábito cumplido refuerza tu palabra. Sigue convirtiendo tus decisiones en resultados.', 'dorado-trofeo'),
    ('v015', 'VERDE', '{nombre}, tu mejor argumento es lo que haces. Continúa construyendo pruebas de tu transformación.', 'dorado-orgulloso'),
    ('v016', 'VERDE', 'Qué bien se siente avanzar con intención. Conserva esa energía y úsala para cumplir hoy.', 'dorado-guino'),
    ('v017', 'VERDE', '{nombre}, estás en {fase} y tu constancia te acompaña. Sigue honrando el proceso.', 'dorado-sereno'),
    ('v018', 'VERDE', 'Tus acciones están alineadas con tus metas. Ese equilibrio merece celebrarse y protegerse.', 'dorado-celebrando'),
    ('v019', 'VERDE', '{nombre}, estás ganando confianza con hechos. Que cada logro sea combustible, no una excusa para detenerte.', 'dorado-trofeo'),
    ('v020', 'VERDE', 'El avance que ves nació de decisiones pequeñas. Sigue eligiendo bien incluso cuando nadie te mire.', 'dorado-orgulloso'),
    ('v021', 'VERDE', '{nombre}, vas firme. Tu desafío ahora es cuidar la disciplina que te está llevando tan lejos.', 'dorado-guino'),
    ('v022', 'VERDE', 'Mantenerte al día también es una victoria. Reconócela y sigue trabajando por la siguiente.', 'dorado-celebrando'),
    ('v023', 'VERDE', '{nombre}, estás aprendiendo a confiar en ti porque cumples lo que prometes. Sigue así.', 'dorado-sereno'),
    ('v024', 'VERDE', 'Hay progreso real en tu camino. No permitas que la comodidad frene lo que tanto te ha costado construir.', 'dorado-trofeo'),
    ('v025', 'VERDE', '{nombre}, hoy tu disciplina merece reconocimiento. Mantén los pies en la tierra y la mirada adelante.', 'dorado-orgulloso'),
    ('v026', 'VERDE', 'Cumplir está empezando a formar parte de quien eres. Fortalece esa identidad con tus acciones.', 'dorado-sereno'),
    ('v027', 'VERDE', '{nombre}, tienes razones para sonreír. No fue suerte: fueron tus decisiones sostenidas.', 'dorado-guino'),
    ('v028', 'VERDE', 'Tu esfuerzo merece este reconocimiento. Celebra sin desconectarte de tus próximos compromisos.', 'dorado-celebrando'),
    ('v029', 'VERDE', '{nombre}, {habitoFuerte} es una fortaleza en tu rutina. Sigue cuidando ese hábito y fortalece los demás.', 'dorado-trofeo'),
    ('v030', 'VERDE', 'Seguir cumpliendo cuando todo marcha bien también requiere carácter. Tú lo estás demostrando.', 'dorado-orgulloso'),
    ('v031', 'VERDE', '{nombre}, estás cosechando disciplina. Siembras cada día cuando decides actuar en lugar de postergar.', 'dorado-sereno'),
    ('v032', 'VERDE', 'Disfruta tu progreso. La verdadera recompensa será convertir estos hábitos en parte de tu vida.', 'dorado-guino'),
    ('v033', 'VERDE', '{nombre}, este avance tiene tu firma. Sigue haciendo que tus resultados reflejen tus valores.', 'dorado-trofeo'),
    ('v034', 'VERDE', 'Tu compromiso está más fuerte que tus excusas. Conserva esa ventaja con humildad y determinación.', 'dorado-celebrando'),
    ('v035', 'VERDE', '{nombre}, hoy puedes mirar atrás con orgullo. Pero tu mejor versión todavía se sigue construyendo.', 'dorado-guino'),
    ('a001', 'AMARILLO', '{nombre}, todavía puedes levantar el ritmo. Haz espacio hoy para {habitoFlojo} y vuelve a enfocarte.', 'azul-animando'),
    ('a002', 'AMARILLO', 'Te faltan {faltan} para subir al siguiente color. Elige una tarea pendiente y empieza ahora.', 'azul-reloj'),
    ('a003', 'AMARILLO', '{nombre}, el macaco quiere que lo dejes para mañana. Véncelo dedicando tiempo hoy a {habitoFlojo}.', 'azul-vs-macaco'),
    ('a004', 'AMARILLO', 'Hoy necesitas menos vueltas y más acción. Reserva un momento concreto para cumplir un hábito pendiente.', 'azul-firme'),
    ('a005', 'AMARILLO', '{nombre}, tu semáforo pide atención. Revisa lo pendiente y cumple una tarea antes de que termine el día.', 'azul-pensativo'),
    ('a006', 'AMARILLO', 'Aún puedes mejorar tu semana. Dedica un espacio de tu agenda a {habitoFlojo} hoy.', 'azul-reloj'),
    ('a007', 'AMARILLO', '{nombre}, no necesitas esperar el momento perfecto. Escoge un hábito pendiente y ponte en marcha.', 'azul-entrenando'),
    ('a008', 'AMARILLO', 'El macaco negocia descansos antes de empezar. No le sigas el juego: cumple una tarea hoy.', 'azul-vs-macaco'),
    ('a009', 'AMARILLO', '{nombre}, el progreso necesita una decisión tuya. Dedica hoy unos minutos a tus objetivos pendientes.', 'azul-animando'),
    ('a010', 'AMARILLO', 'Mira lo que falta sin agobiarte. Elige lo más importante y dale un espacio real en tu día.', 'azul-pensativo'),
    ('a011', 'AMARILLO', '{nombre}, faltan {faltan} para avanzar de color. Empieza por lo más sencillo y recupera movimiento.', 'azul-reloj'),
    ('a012', 'AMARILLO', 'Si el macaco insiste en postergar, respóndele con hechos. Haz tu siguiente hábito ahora.', 'azul-vs-macaco'),
    ('a013', 'AMARILLO', '{nombre}, un día distraído no tiene que convertirse en una semana perdida. Retoma un compromiso hoy.', 'azul-ofrece-ala'),
    ('a014', 'AMARILLO', 'Tienes margen para recuperar terreno. Revisa {habitoFlojo} y decide cuándo le dedicarás tiempo hoy.', 'azul-pensativo'),
    ('a015', 'AMARILLO', '{nombre}, tu esfuerzo necesita dirección. Abre tu lista y completa una tarea que vienes postergando.', 'azul-firme'),
    ('a016', 'AMARILLO', 'No dejes que un pendiente se convierta en costumbre. Resuelve hoy algo que está bajo tu control.', 'azul-despertando'),
    ('a017', 'AMARILLO', '{nombre}, estás a tiempo de corregir el rumbo. Programa hoy un espacio para {habitoFlojo}.', 'azul-reloj'),
    ('a018', 'AMARILLO', 'Ese macaco sabe inventar excusas convincentes. Demuéstrale quién decide cumpliendo una tarea hoy.', 'azul-vs-macaco'),
    ('a019', 'AMARILLO', '{nombre}, tienes más capacidad de la que estás usando. Demuéstratelo hoy con una acción pendiente.', 'azul-entrenando'),
    ('a020', 'AMARILLO', 'La diferencia empieza cuando actúas. Dedica tiempo hoy a {habitoFlojo}, aunque no tengas ganas.', 'azul-firme'),
    ('a021', 'AMARILLO', '{nombre}, estás en el día {dia}. Haz que cuente completando algo importante antes de descansar.', 'azul-animando'),
    ('a022', 'AMARILLO', 'Puedes cambiar la dirección de hoy. Deja preparada tu siguiente tarea y empieza sin distracciones.', 'azul-despertando'),
    ('a023', 'AMARILLO', '{nombre}, al macaco le encanta decir después. Tú responde ahora y completa un hábito pendiente.', 'azul-vs-macaco'),
    ('a024', 'AMARILLO', 'Un pequeño ajuste puede mejorar tu día. Revisa tus pendientes y atiende primero el más importante.', 'azul-pensativo'),
    ('a025', 'AMARILLO', '{nombre}, faltan {faltan} para alcanzar el siguiente color. Organízate y cumple lo que puedas hoy.', 'azul-reloj'),
    ('a026', 'AMARILLO', 'Recupera el control de tu agenda. Aparta un momento hoy para avanzar con {habitoFlojo}.', 'azul-entrenando'),
    ('a027', 'AMARILLO', '{nombre}, no confundas estar ocupado con avanzar. Prioriza un compromiso y complétalo hoy.', 'azul-firme'),
    ('a028', 'AMARILLO', 'Que el macaco no dirija tu rutina. Cierra una distracción y comienza una tarea pendiente.', 'azul-vs-macaco'),
    ('a029', 'AMARILLO', '{nombre}, {habitoFuerte} demuestra que puedes sostener compromisos. Usa esa confianza para cumplir otro hoy.', 'azul-animando'),
    ('a030', 'AMARILLO', 'Detén la postergación con una decisión concreta. Elige ahora cuándo realizarás tu siguiente hábito.', 'azul-despertando'),
    ('a031', 'AMARILLO', '{nombre}, tu avance no está perdido. Revisa {habitoFlojo} y da hoy un paso para fortalecerlo.', 'azul-ofrece-ala'),
    ('a032', 'AMARILLO', 'Hay compromisos esperando tu atención. Ordena tus prioridades y cumple uno antes de cerrar el día.', 'azul-pensativo'),
    ('a033', 'AMARILLO', '{nombre}, estás en {fase}. Haz que esta etapa tenga sentido recuperando hoy un hábito pendiente.', 'azul-entrenando'),
    ('a034', 'AMARILLO', 'No permitas que la comodidad decida por ti. Levántate y cumple una acción que vienes evitando.', 'azul-firme'),
    ('a035', 'AMARILLO', '{nombre}, hoy puedes recuperar impulso. Revisa tu lista, elige una tarea y termínala.', 'azul-sorprendido'),
    ('r001', 'ROJO', '{nombre}, tus compromisos están quedando pendientes. No los ignores: retoma un hábito ahora y cambia el día.', 'azul-firme'),
    ('r002', 'ROJO', 'El semáforo está en rojo y necesitas actuar. Empieza con una tarea sencilla y recupera el rumbo hoy.', 'azul-despertando'),
    ('r003', 'ROJO', '{nombre}, el macaco está ganando terreno. No le entregues otra jornada: cumple un hábito ahora.', 'azul-vs-macaco'),
    ('r004', 'ROJO', 'Tus hábitos necesitan atención urgente, no excusas. Elige uno y haz lo necesario para cumplirlo hoy.', 'azul-decepcionado'),
    ('r005', 'ROJO', '{nombre}, no estás cumpliendo como te propusiste. Reconócelo sin castigarte y vuelve a empezar hoy.', 'azul-ofrece-ala'),
    ('r006', 'ROJO', 'Seguir postergando tiene consecuencias. Recupera el control dedicando tiempo hoy a {habitoFlojo}.', 'azul-firme'),
    ('r007', 'ROJO', '{nombre}, faltan {faltan} para subir de color. Deja de aplazar lo importante y completa una tarea hoy.', 'azul-reloj'),
    ('r008', 'ROJO', 'El macaco está cómodo con tus pendientes. Rómpelo con acción: empieza un hábito ahora mismo.', 'azul-vs-macaco'),
    ('r009', 'ROJO', '{nombre}, tus resultados reflejan compromisos sin cumplir. No te quedes mirando: hoy puedes cambiar el rumbo.', 'azul-despertando'),
    ('r010', 'ROJO', 'La rutina se está alejando de tus objetivos. Detén esa distancia: completa un hábito pendiente hoy.', 'azul-pensativo'),
    ('r011', 'ROJO', '{nombre}, necesitas recuperar disciplina, no castigarte. Empieza ahora por una acción que sí puedas terminar.', 'azul-entrenando'),
    ('r012', 'ROJO', 'Tus pendientes siguen creciendo y el tiempo avanza. Haz espacio hoy para {habitoFlojo} y retoma el control.', 'azul-reloj'),
    ('r013', 'ROJO', '{nombre}, el macaco te quiere inmóvil. Demuéstrale que no manda: levántate y cumple una tarea hoy.', 'azul-vs-macaco'),
    ('r014', 'ROJO', 'No estás sosteniendo tus hábitos y eso merece atención. Hazte cargo con una acción concreta hoy.', 'azul-decepcionado'),
    ('r015', 'ROJO', '{nombre}, no basta con querer cambiar. Tu siguiente decisión importa: cumple un hábito pendiente ahora.', 'azul-firme'),
    ('r016', 'ROJO', 'Evitar tus compromisos no hará que desaparezcan. Mira tu lista y empieza por lo que puedas resolver hoy.', 'azul-pensativo'),
    ('r017', 'ROJO', '{nombre}, estás dejando pasar oportunidades de avanzar. Hoy recuperas terreno cumpliendo un hábito.', 'azul-entrenando'),
    ('r018', 'ROJO', 'El macaco celebra cada postergación. Córtale la fiesta: cumple una tarea y vuelve a tomar el mando hoy.', 'azul-vs-macaco'),
    ('r019', 'ROJO', '{nombre}, tu esfuerzo está por debajo de tus compromisos. Puedes corregirlo: empieza con algo concreto hoy.', 'azul-ofrece-ala'),
    ('r020', 'ROJO', 'Los pendientes no se resolverán solos. Aparta un momento para {habitoFlojo} y vuelve a actuar hoy.', 'azul-reloj'),
    ('r021', 'ROJO', '{nombre}, hoy necesitas una respuesta distinta a la postergación. Abre tu lista y cumple un hábito ahora.', 'azul-firme'),
    ('r022', 'ROJO', 'Reconoce lo que no has cumplido sin esconderlo ni castigarte. Elige un compromiso y retómalo hoy.', 'azul-pensativo'),
    ('r023', 'ROJO', '{nombre}, el macaco aprovecha cada excusa. No lo alimentes con otra: cumple lo pendiente que puedas hoy.', 'azul-vs-macaco'),
    ('r024', 'ROJO', 'No estás avanzando al ritmo que buscas. Eso puede cambiar: vuelve a tu rutina con una acción hoy.', 'azul-despertando'),
    ('r025', 'ROJO', '{nombre}, aunque estés en rojo, todavía tienes capacidad de respuesta. Completa un hábito y empieza a recuperarte hoy.', 'azul-ofrece-ala'),
    ('r026', 'ROJO', 'Te has alejado de tus compromisos, pero puedes regresar. Empieza hoy dedicando tiempo a {habitoFlojo}.', 'azul-animando'),
    ('r027', 'ROJO', '{nombre}, hoy el macaco no puede tener la última palabra. Haz una tarea pendiente y recupera tu dirección.', 'azul-vs-macaco'),
    ('r028', 'ROJO', 'Tu disciplina necesita reconstruirse con acciones. No esperes al día ideal: vuelve a comenzar hoy.', 'azul-entrenando'),
    ('r029', 'ROJO', '{nombre}, lo que falta por cumplir es real. También lo es tu capacidad de reaccionar: haz un hábito ahora.', 'azul-firme'),
    ('r030', 'ROJO', 'Estar en rojo es una señal para cambiar tus decisiones. Escoge un pendiente y retoma tu camino hoy.', 'azul-ofrece-ala'),
    ('s001', 'INICIO', '{nombre}, te damos la bienvenida a Renaser. Tu transformación comienza con una decisión: elige tu primer hábito y empieza hoy.', 'azul-animando'),
    ('s002', 'INICIO', 'Este es tu punto de partida. Define lo que quieres construir y da hoy tu primer paso con intención.', 'azul-despertando'),
    ('s003', 'INICIO', '{nombre}, todavía no hay resultados que medir, pero ya tienes una oportunidad. Comienza con una acción sencilla.', 'azul-ofrece-ala'),
    ('s004', 'SIN_PROGRAMA', '{nombre}, esta semana no tienes hábitos ni objetivos programados. Define una meta y organiza cómo empezar.', 'azul-pensativo'),
    ('s005', 'SIN_PROGRAMA', 'Tu semáforo necesita un plan para acompañarte. Programa tus hábitos y establece objetivos alcanzables.', 'azul-reloj'),
    ('s006', 'SIN_PROGRAMA', '{nombre}, una buena semana empieza con dirección. Elige los compromisos que quieres trabajar y anótalos.', 'azul-entrenando'),
    ('s007', 'PAUSA', '{nombre}, tu proceso está en pausa y puedes darte ese espacio. Cuídate; retomarás cuando sea posible.', 'azul-ofrece-ala'),
    ('s008', 'PAUSA', 'Descansar también puede ser necesario. Atiende lo que estás viviendo y vuelve a tu ritmo cuando puedas.', 'azul-pensativo'),
    ('s009', 'PAUSA', '{nombre}, tu avance no pierde valor por hacer una pausa. Date tiempo y regresa cuando sea tu momento.', 'azul-animando'),
    ('s010', 'CUENTA_PAUSADA', '{nombre}, qué bueno tenerte de vuelta. Tu semáforo se reanuda con lo que hagas desde hoy: empieza con un hábito.', 'azul-pensativo'),
    ('s011', 'CUENTA_PAUSADA', 'Volviste y eso ya cuenta. Los días en pausa no te restan; enfócate en lo que puedes cumplir hoy.', 'azul-reloj'),
    ('s012', 'CUENTA_PAUSADA', '{nombre}, retomas tu camino. Elige un compromiso sencillo para hoy y vuelve a encender tu semáforo.', 'azul-ofrece-ala'),
    ('s013', 'GRADUADO', '{nombre}, completaste Renaser. Celebra este logro y sigue cuidando los hábitos que te trajeron hasta aquí.', 'dorado-trofeo'),
    ('s014', 'GRADUADO', 'Terminaste el programa, pero tu crecimiento continúa. Lleva tu disciplina a cada nueva etapa de tu vida.', 'dorado-celebrando'),
    ('s015', 'GRADUADO', '{nombre}, tu graduación es un logro y un nuevo comienzo. Sigue eligiendo cada día la persona que quieres ser.', 'dorado-orgulloso');
