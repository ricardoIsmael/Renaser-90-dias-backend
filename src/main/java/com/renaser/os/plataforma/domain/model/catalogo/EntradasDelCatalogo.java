package com.renaser.os.plataforma.domain.model.catalogo;

import com.renaser.os.shared.domain.ClavesDeFuncionalidad;
import com.renaser.os.users.api.UserRole;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.renaser.os.plataforma.domain.model.catalogo.PoliticaDeProcesos.NO_APLICA;
import static com.renaser.os.plataforma.domain.model.catalogo.PoliticaDeProcesos.POSPONE;
import static com.renaser.os.plataforma.domain.model.catalogo.PoliticaDeProcesos.SALTA;
import static com.renaser.os.users.api.UserRole.ADMIN;
import static com.renaser.os.users.api.UserRole.ALCHEMIST;
import static com.renaser.os.users.api.UserRole.IT_SUPPORT;
import static com.renaser.os.users.api.UserRole.MENTOR;
import static com.renaser.os.users.api.UserRole.MENTOR_LEAD;
import static com.renaser.os.users.api.UserRole.TRAINEE;
import static com.renaser.os.users.api.UserRole.TRAINEE_GUIDE;

/**
 * La tabla «Catálogo inicial de funcionalidades» de la spec 002, tal cual: clave, área, nombre, qué apaga, a quién le
 * llega, si es crítica y qué hacen sus procesos. Las críticas son las siete del contrato §3: {@code chat},
 * {@code muro}, {@code ia}, {@code habitos}, {@code registro}, {@code notificaciones} y {@code admin}. Todas nacen
 * prendidas.
 *
 * <p>Soporte TI figura en todas las que no son de acompañar grupos: tiene lo de todos los roles (FR-023).
 *
 * <p>{@code radar.fichas} (spec 034, D-299) tampoco está en la tabla de la spec 002: es el interruptor que decide Administración para
 * que el mentor, la guía y el líder vean el Código Renaser en las fichas. Cuelga de {@code radar}, nace apagada para esos tres roles
 * (sin fila) y apagada se esconde ({@code noDisponible}) en vez de avisar mantenimiento.
 *
 * <p>{@code lider.cumplimiento} (spec 005, D-274) es la única que no está en la tabla de la spec 002: se sumó con la ficha
 * de cumplimiento por persona del líder. Cuelga de {@code lider}, y su resumen diario al mentor corre en una ventana que
 * pasa (20:00 a 20:59 de la zona del mentor), así que apagada se salta y no se pone al día.
 *
 * <p><b>Agregar una funcionalidad</b> es una línea acá y un {@code @Funcionalidad} en sus endpoints. Una clave que
 * ya se usa no se renombra: la app y la base la guardan.
 */
final class EntradasDelCatalogo {

    private static final Set<UserRole> TODOS = EnumSet.allOf(UserRole.class);
    private static final Set<UserRole> NADIE = EnumSet.noneOf(UserRole.class);
    private static final Set<UserRole> ADMINISTRACION = EnumSet.of(ADMIN, ALCHEMIST, IT_SUPPORT);
    /** Quienes acompañan a un aprendiz desde su ficha, sin ser Administración: el interruptor de la spec 034 los gobierna. */
    private static final Set<UserRole> ACOMPANAMIENTO = EnumSet.of(MENTOR, TRAINEE_GUIDE, MENTOR_LEAD);

    private EntradasDelCatalogo() {
    }

    static List<DefinicionDeFuncionalidad> iniciales() {
        return List.of(
                critica("registro", "Acceso", "Solicitar acceso", "El alta pública: «Solicitar acceso» y la verificación del correo.", NADIE, NO_APLICA),
                // Spec 043 (D-325): el código por WhatsApp del alta y de recuperar la contraseña. Nace APAGADO hasta que Meta apruebe la plantilla de
                // autenticación; apagado se esconde (404) y la app no lo ofrece. Pública, como el alta: no es de ningún rol.
                raizQueNaceApagada("auth.codigo_whatsapp", "Acceso", "Código por WhatsApp", "Mandar el código de verificación por WhatsApp cuando no llega al correo: al solicitar acceso y al recuperar la contraseña.", NADIE),
                normal("onboarding", "Programa", "Onboarding", "Ficha inicial, términos, pacto y cuestionario.", TODOS, NO_APLICA),
                // Issue #62: la clave `dia_uno` NO cambia (hay interruptores y despliegues guardados con ella); solo lo que lee Soporte TI. Ya no se elige el Día 1 (spec 036).
                normal("dia_uno", "Programa", "Empezar mi programa", "Activar el programa propio; empieza al día siguiente.", TODOS, NO_APLICA),
                normal("radar", "Programa", "Código Renaser", "El Código Renaser diario.", TODOS, NO_APLICA),
                // Spec 034 (D-299): nace apagada para quien acompaña y la decide Administración; apagada se esconde.
                oculta(ClavesDeFuncionalidad.RADAR_EN_FICHAS, "Programa", "Código Renaser en las fichas", "Que el mentor, la guía y el líder vean el Código Renaser de los aprendices en sus fichas.", ACOMPANAMIENTO),
                normal("mapa", "Programa", "Mapa de Renacimiento", "El Mapa de Renacimiento.", TODOS, NO_APLICA),
                normal("plan", "Programa", "Plan", "Rocas y objetivos: maestra, del mes, de la semana y del día; Verdugo.", TODOS, SALTA),
                normal("clase_diaria", "Programa", "Clase diaria", "La clase diaria.", TODOS, NO_APLICA),
                critica("habitos", "Hábitos", "Hábitos", "Los hábitos de hoy y completarlos.", TODOS, SALTA),
                normal("habitos.foto", "Hábitos", "Evidencia con foto", "Subir la foto de un hábito.", TODOS, NO_APLICA),
                normal("habitos.km", "Hábitos", "Kilómetros diarios", "Registrar los kilómetros del día.", TODOS, SALTA),
                // Spec 042 (D-323): la carrera con GPS desde Training y la lectura de su ruta. Hija de habitos.km: apagar los km la apaga.
                normal("habitos.km.carreras", "Hábitos", "Carreras con GPS", "Salir a correr o caminar con GPS desde Training, registrar la carrera y ver su ruta.", TODOS, NO_APLICA),
                normal("habitos.gimnasio", "Hábitos", "Gimnasio", "El hábito de gimnasio.", TODOS, SALTA),
                normal("habitos.espiritu", "Hábitos", "Pastilla y audioterapia", "La Pastilla Renacer y la audioterapia semanal.", TODOS, NO_APLICA),
                normal("habitos.ajustes", "Hábitos", "Ajustes de hábitos", "Horarios, pausa y renombre de hábitos.", TODOS, NO_APLICA),
                critica("muro", "Muro", "Muro", "El muro entero.", TODOS, SALTA),
                normal("muro.publicar", "Muro", "Publicar en el muro", "Publicar y subir fotos al muro.", TODOS, NO_APLICA),
                normal("muro.comentarios", "Muro", "Comentarios", "Comentar en el muro.", TODOS, NO_APLICA),
                normal("muro.reacciones", "Muro", "Reacciones", "Reaccionar en el muro.", TODOS, NO_APLICA),
                critica("chat", "Chat", "Chat", "El chat entero: Tribu, salas y mensajes.", TODOS, POSPONE),
                normal("chat.grupos", "Chat", "Chat de grupo", "Las conversaciones de grupo (célula) y la tarjeta del grupo.", TODOS, NO_APLICA),
                normal("chat.global", "Chat", "Formación Renaser Global", "La conversación global.", TODOS, POSPONE),
                normal("chat.soporte", "Chat", "Chat de soporte", "Las conversaciones de soporte.", EnumSet.of(TRAINEE, MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT), SALTA),
                normal("chat.directos", "Chat", "Chats 1 a 1", "Las conversaciones directas.", TODOS, NO_APLICA),
                normal("chat.audio", "Chat", "Notas de voz", "Mandar notas de voz.", TODOS, NO_APLICA),
                normal("chat.fotos", "Chat", "Fotos y stickers", "Mandar fotos y stickers.", TODOS, NO_APLICA),
                normal("eventos", "Eventos", "Eventos", "Ver los eventos y confirmar asistencia.", TODOS, SALTA),
                normal("eventos.gestion", "Eventos", "Crear y editar eventos", "Crear, editar y cancelar eventos.", ADMINISTRACION, NO_APLICA),
                // Spec 044: quien creó el evento (también un mentor o una guía) ve quién respondió y pasa lista de SU evento.
                normal("eventos.asistencia", "Eventos", "Asistencia", "Quién respondió y pasar lista.", EnumSet.of(MENTOR, TRAINEE_GUIDE, MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT), NO_APLICA),
                // Spec 044 (FR-028, P5 del dueño): crear eventos para personas elegidas. Nace prendida; Soporte TI la apaga si algo falla.
                normal("eventos.convocar", "Eventos", "Eventos para personas elegidas", "Crear eventos para personas, un grupo, un color del semáforo o el personal (líder, mentor y guía de aprendiz; y la audiencia elegida de Administración).", EnumSet.of(MENTOR, TRAINEE_GUIDE, MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT), NO_APLICA),
                // Spec 040 (D-321): el enlace de Meet que el servidor crea solo. Nace APAGADA y no se prende en producción hasta pasar SC-001
                // (reunión de más de 60 minutos con 3 o más personas). Apagada se esconde (404), y los procesos de fondo saltan su turno.
                apagadaDeFabrica("eventos.meet", "Eventos", "Enlace de Meet automático", "Crear solo la sala de Meet de un evento al guardarlo.", EnumSet.of(MENTOR, TRAINEE_GUIDE, MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT), SALTA),
                // Spec 041 (D-322): el WhatsApp de los recordatorios de eventos nace APAGADO (sin el numero, la clave y la plantilla aprobada no hay como enviar) y se prende a proposito.
                naceApagada("eventos.whatsapp", "Eventos", "Recordatorios por WhatsApp", "Los recordatorios de los eventos por WhatsApp (1 día, 1 hora, 30 y 10 minutos antes) y su resumen.", ADMINISTRACION, SALTA),
                normal("cursos", "Cursos", "Cursos", "Los cursos y sus lecciones.", TODOS, NO_APLICA),
                normal("ranking", "Ranking", "Ranking", "Las tablas del ranking.", TODOS, NO_APLICA),
                normal("ranking.podio_semanal", "Ranking", "Podio de los lunes", "El podio semanal en el chat global.", TODOS, POSPONE),
                normal("semaforo", "Semáforo", "Semáforo", "El semáforo propio, del mentor, por grupos y de Administración.", EnumSet.of(TRAINEE, MENTOR, TRAINEE_GUIDE, MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT), POSPONE),
                critica("ia", "SER (IA)", "SER", "SER entera, con su memoria y sus mensajes automáticos.", TODOS, SALTA),
                normal("ia.chat", "SER (IA)", "Chat de SER", "El chat de SER y el del curso.", TODOS, NO_APLICA),
                normal("ia.voz", "SER (IA)", "Voz de SER", "La voz del orbe y la voz en vivo.", TODOS, NO_APLICA),
                normal("ia.propuestas", "SER (IA)", "Propuestas de SER", "Las propuestas y acciones de SER.", TODOS, NO_APLICA),
                // Spec 031 (D-306): el analisis 80/20 con IA se apaga solo, sin tocar el chat ni la voz. Las cifras de «Tu 80/20» no dependen de esto.
                normal("ia.analisis", "SER (IA)", "Análisis 80/20 con IA", "El análisis con IA de la semana y del mes («Tu 80/20»); las cifras se siguen viendo.", TODOS, SALTA),
                normal("evidencias", "Evidencias", "Mis evidencias", "Ver las evidencias y su revisión.", TODOS, POSPONE),
                // MENTOR y TRAINEE_GUIDE: ven el chip de la caja en la ficha de su aprendiz (CajaDelMentorController, D-219).
                // Faltaban (E-620): sin ellos la consola no mostraba su interruptor y un rol personalizado de esa base no
                // la podía marcar.
                normal("caja", "Caja Renaser", "Caja Renaser", "La Caja Renaser del aprendiz y su gestión.", EnumSet.of(TRAINEE, MENTOR, TRAINEE_GUIDE, ADMIN, IT_SUPPORT), SALTA),
                // Spec 032 (D-307): «Mi ingreso» de la persona y los pagos de su ficha. Soporte TI figura como en todas (FR-023: puede apagarla y verla con «Ver como»), pero el servicio le niega operar pagos.
                normal("pagos", "Pagos", "Cronograma de pagos", "«Mi ingreso» de la persona (su cronograma y sus recordatorios) y los pagos de su ficha en Administración.", EnumSet.of(TRAINEE, MENTOR, TRAINEE_GUIDE, MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT), NO_APLICA),
                normal("emergencia", "Cuenta", "Botón de emergencia", "Pedir ayuda por una emergencia y atenderla.", EnumSet.of(TRAINEE, ADMIN, ALCHEMIST, IT_SUPPORT), NO_APLICA),
                raiz("cuenta.eliminar", "Cuenta", "Eliminar mi cuenta", "Eliminar la cuenta propia.", TODOS, NO_APLICA),
                normal("perfil", "Cuenta", "Perfil", "Editar el perfil y la foto.", TODOS, NO_APLICA),
                critica("notificaciones", "Notificaciones", "Notificaciones", "Los avisos push y la bandeja.", TODOS, POSPONE),
                raiz("mentor.mi_grupo", "Mentoría", "Mi grupo", "«Mi grupo» y las fichas de los aprendices.", EnumSet.of(MENTOR, TRAINEE_GUIDE), SALTA),
                normal("lider", "Mentoría", "Mis mentores", "La gestión del Líder de mentores.", EnumSet.of(MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT), NO_APLICA),
                normal("lider.cumplimiento", "Mentoría", "Cumplimiento por persona", "Del semáforo por grupos a cada persona: su semana, sus evidencias, avisar a su mentor y el resumen diario al mentor.", EnumSet.of(MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT), SALTA),
                normal("tickets", "Mentoría", "Tickets de mentoría", "Las consultas al mentor y la bandeja de tickets.", TODOS, NO_APLICA),
                critica("admin", "Administración", "Administración", "Administración entera.", ADMINISTRACION, NO_APLICA),
                normal("admin.solicitudes", "Administración", "Solicitudes", "Aprobar, rechazar y borrar solicitudes de alta.", ADMINISTRACION, NO_APLICA),
                normal("admin.equipo", "Administración", "Equipo y roles", "El equipo, los roles y las invitaciones.", ADMINISTRACION, NO_APLICA),
                normal("admin.grupos", "Administración", "Grupos y cohortes", "Grupos, cohortes y su armado.", ADMINISTRACION, SALTA),
                normal("admin.personas", "Administración", "Personas y fichas", "Las personas, sus fichas y su día del programa.", ADMINISTRACION, NO_APLICA),
                normal("admin.habitos", "Administración", "Hábitos del programa", "El catálogo de hábitos y sus horarios.", ADMINISTRACION, NO_APLICA),
                normal("admin.bienvenida", "Administración", "Bienvenida", "La bienvenida automática y su tarjeta.", ADMINISTRACION, SALTA),
                normal("admin.fases", "Administración", "Imágenes de las fases", "Cambiar las imágenes y nombres de las fases.", ADMINISTRACION, NO_APLICA));
    }

    private static DefinicionDeFuncionalidad normal(String clave, String area, String nombre, String descripcion,
                                                    Set<UserRole> roles, PoliticaDeProcesos procesos) {
        return new DefinicionDeFuncionalidad(clave, prefijo(clave), area, nombre, descripcion, roles, false,
                ValorPorDefecto.PRENDIDA, procesos);
    }

    /**
     * Una funcionalidad que NACE apagada para todos (spec 041): no tiene fila y Soporte TI la prende a proposito. Apagada se esconde (404 «no disponible»), porque no es un
     * servicio que se cae sino algo que todavia no se ofrece. Los procesos {@code SALTA}: lo que toca mientras esta apagada no se acumula para mandarse tarde.
     */
    private static DefinicionDeFuncionalidad naceApagada(String clave, String area, String nombre, String descripcion,
                                                        Set<UserRole> roles, PoliticaDeProcesos procesos) {
        return new DefinicionDeFuncionalidad(clave, prefijo(clave), area, nombre, descripcion, roles, false,
                ValorPorDefecto.APAGADA, procesos, Set.of(), true);
    }

    /**
     * Una funcionalidad que nace apagada para {@code roles} y, apagada, se esconde en vez de avisar mantenimiento
     * (spec 034). Prendida para todos los demás.
     */
    private static DefinicionDeFuncionalidad oculta(String clave, String area, String nombre, String descripcion,
                                                    Set<UserRole> roles) {
        return new DefinicionDeFuncionalidad(clave, prefijo(clave), area, nombre, descripcion, roles, false,
                ValorPorDefecto.PRENDIDA, NO_APLICA, roles, true);
    }

    /**
     * Una funcionalidad que nace APAGADA para todos (spec 040): Soporte TI la prende a propósito, y mientras no, se esconde (404) en vez de avisar
     * mantenimiento. Es la forma de entregar algo que depende de una prueba manual del dueño.
     */
    private static DefinicionDeFuncionalidad apagadaDeFabrica(String clave, String area, String nombre, String descripcion,
                                                              Set<UserRole> roles, PoliticaDeProcesos procesos) {
        return new DefinicionDeFuncionalidad(clave, prefijo(clave), area, nombre, descripcion, roles, false,
                ValorPorDefecto.APAGADA, procesos, Set.of(), true);
    }

    private static DefinicionDeFuncionalidad critica(String clave, String area, String nombre, String descripcion,
                                                     Set<UserRole> roles, PoliticaDeProcesos procesos) {
        return new DefinicionDeFuncionalidad(clave, prefijo(clave), area, nombre, descripcion, roles, true,
                ValorPorDefecto.PRENDIDA, procesos);
    }

    /** Una clave con punto cuyo prefijo no es una funcionalidad del catálogo ({@code cuenta.eliminar}). */
    private static DefinicionDeFuncionalidad raiz(String clave, String area, String nombre, String descripcion,
                                                  Set<UserRole> roles, PoliticaDeProcesos procesos) {
        return new DefinicionDeFuncionalidad(clave, null, area, nombre, descripcion, roles, false,
                ValorPorDefecto.PRENDIDA, procesos);
    }

    /** Una clave con punto cuyo prefijo no es una funcionalidad ({@code auth.codigo_whatsapp}) y que nace APAGADA para todos (spec 043): apagada se esconde. */
    private static DefinicionDeFuncionalidad raizQueNaceApagada(String clave, String area, String nombre, String descripcion, Set<UserRole> roles) {
        return new DefinicionDeFuncionalidad(clave, null, area, nombre, descripcion, roles, false,
                ValorPorDefecto.APAGADA, NO_APLICA, Set.of(), true);
    }

    private static String prefijo(String clave) {
        int punto = clave.lastIndexOf('.');
        return punto < 0 ? null : clave.substring(0, punto);
    }
}
