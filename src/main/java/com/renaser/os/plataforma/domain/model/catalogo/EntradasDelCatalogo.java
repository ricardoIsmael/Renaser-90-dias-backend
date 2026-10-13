package com.renaser.os.plataforma.domain.model.catalogo;

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
 * <p><b>Agregar una funcionalidad</b> es una línea acá y un {@code @Funcionalidad} en sus endpoints. Una clave que
 * ya se usa no se renombra: la app y la base la guardan.
 */
final class EntradasDelCatalogo {

    private static final Set<UserRole> TODOS = EnumSet.allOf(UserRole.class);
    private static final Set<UserRole> NADIE = EnumSet.noneOf(UserRole.class);
    private static final Set<UserRole> ADMINISTRACION = EnumSet.of(ADMIN, ALCHEMIST, IT_SUPPORT);

    private EntradasDelCatalogo() {
    }

    static List<DefinicionDeFuncionalidad> iniciales() {
        return List.of(
                critica("registro", "Acceso", "Solicitar acceso", "El alta pública: «Solicitar acceso» y la verificación del correo.", NADIE, NO_APLICA),
                normal("onboarding", "Programa", "Onboarding", "Ficha inicial, términos, pacto y cuestionario.", TODOS, NO_APLICA),
                normal("dia_uno", "Programa", "Elegir mi Día 1", "Elegir el Día 1 y activar el programa propio.", TODOS, NO_APLICA),
                normal("radar", "Programa", "Código Renaser", "El Código Renaser diario.", TODOS, NO_APLICA),
                normal("mapa", "Programa", "Mapa de Renacimiento", "El Mapa de Renacimiento.", TODOS, NO_APLICA),
                normal("plan", "Programa", "Plan", "Rocas y objetivos: maestra, del mes, de la semana y del día; Verdugo.", TODOS, SALTA),
                normal("clase_diaria", "Programa", "Clase diaria", "La clase diaria.", TODOS, NO_APLICA),
                critica("habitos", "Hábitos", "Hábitos", "Los hábitos de hoy y completarlos.", TODOS, SALTA),
                normal("habitos.foto", "Hábitos", "Evidencia con foto", "Subir la foto de un hábito.", TODOS, NO_APLICA),
                normal("habitos.km", "Hábitos", "Kilómetros diarios", "Registrar los kilómetros del día.", TODOS, SALTA),
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
                normal("chat.soporte", "Chat", "Chat de soporte", "Las conversaciones de soporte.", EnumSet.of(TRAINEE, ADMIN, ALCHEMIST, IT_SUPPORT), SALTA),
                normal("chat.directos", "Chat", "Chats 1 a 1", "Las conversaciones directas.", TODOS, NO_APLICA),
                normal("chat.audio", "Chat", "Notas de voz", "Mandar notas de voz.", TODOS, NO_APLICA),
                normal("chat.fotos", "Chat", "Fotos y stickers", "Mandar fotos y stickers.", TODOS, NO_APLICA),
                normal("eventos", "Eventos", "Eventos", "Ver los eventos y confirmar asistencia.", TODOS, SALTA),
                normal("eventos.gestion", "Eventos", "Crear y editar eventos", "Crear, editar y cancelar eventos.", ADMINISTRACION, NO_APLICA),
                normal("eventos.asistencia", "Eventos", "Asistencia", "Quién respondió y pasar lista.", EnumSet.of(MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT), NO_APLICA),
                normal("cursos", "Cursos", "Cursos", "Los cursos y sus lecciones.", TODOS, NO_APLICA),
                normal("ranking", "Ranking", "Ranking", "Las tablas del ranking.", TODOS, NO_APLICA),
                normal("ranking.podio_semanal", "Ranking", "Podio de los lunes", "El podio semanal en el chat global.", TODOS, POSPONE),
                normal("semaforo", "Semáforo", "Semáforo", "El semáforo propio, del mentor, por grupos y de Administración.", EnumSet.of(TRAINEE, MENTOR, TRAINEE_GUIDE, MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT), POSPONE),
                critica("ia", "SER (IA)", "SER", "SER entera, con su memoria y sus mensajes automáticos.", TODOS, SALTA),
                normal("ia.chat", "SER (IA)", "Chat de SER", "El chat de SER y el del curso.", TODOS, NO_APLICA),
                normal("ia.voz", "SER (IA)", "Voz de SER", "La voz del orbe y la voz en vivo.", TODOS, NO_APLICA),
                normal("ia.propuestas", "SER (IA)", "Propuestas de SER", "Las propuestas y acciones de SER.", TODOS, NO_APLICA),
                normal("evidencias", "Evidencias", "Mis evidencias", "Ver las evidencias y su revisión.", TODOS, POSPONE),
                normal("caja", "Caja Renaser", "Caja Renaser", "La Caja Renaser del aprendiz y su gestión.", EnumSet.of(TRAINEE, ADMIN, IT_SUPPORT), SALTA),
                normal("emergencia", "Cuenta", "Botón de emergencia", "Pedir ayuda por una emergencia y atenderla.", EnumSet.of(TRAINEE, ADMIN, ALCHEMIST, IT_SUPPORT), NO_APLICA),
                raiz("cuenta.eliminar", "Cuenta", "Eliminar mi cuenta", "Eliminar la cuenta propia.", TODOS, NO_APLICA),
                normal("perfil", "Cuenta", "Perfil", "Editar el perfil y la foto.", TODOS, NO_APLICA),
                critica("notificaciones", "Notificaciones", "Notificaciones", "Los avisos push y la bandeja.", TODOS, POSPONE),
                raiz("mentor.mi_grupo", "Mentoría", "Mi grupo", "«Mi grupo» y las fichas de los aprendices.", EnumSet.of(MENTOR, TRAINEE_GUIDE), SALTA),
                normal("lider", "Mentoría", "Mis mentores", "La gestión del Líder de mentores.", EnumSet.of(MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT), NO_APLICA),
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

    private static String prefijo(String clave) {
        int punto = clave.lastIndexOf('.');
        return punto < 0 ? null : clave.substring(0, punto);
    }
}
