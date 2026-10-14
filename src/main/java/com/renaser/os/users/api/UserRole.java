package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Permission;

import java.util.EnumSet;
import java.util.Set;

/**
 * Vocabulario de roles del sistema. Forma parte de la interfaz publica de `users`
 * porque `UserSummary` lo expone: cualquier modulo que pregunte "quien es y que puede"
 * necesita nombrar el rol.
 */
public enum UserRole {

    /**
     * Soporte TI (spec 002, D-267; {@code SOPORTE_TI} en la base, V100): la cuenta del dueño-programador que opera la
     * plataforma —interruptores, auditoría, circuito de la IA— desde su consola. <b>Se asigna solo desde la base</b>
     * ({@code scripts/soporte-ti/asignar.sql}): ningún endpoint lo da, lo quita, lo invita ni aprueba con él, y
     * Administración no suspende ni elimina una cuenta con este rol. No acompaña grupos ({@link #actuaComoMentor()}
     * es falso) y no entra a rankings ni padrones salvo que active su programa, como el resto del personal.
     */
    IT_SUPPORT,
    ALCHEMIST,
    ADMIN,
    MENTOR_LEAD,
    MENTOR,
    /**
     * Guía de aprendiz (D-265, pedido del dueño del 2026-10-13): hace todo lo que hace un {@link #MENTOR} —grupos,
     * aprendices, tickets, evidencias, cursos y eventos— y solo se distingue por cómo lo clasifica quien administra
     * («Equipo y roles», hábitos por rol). Por eso ningún chequeo pregunta {@code == MENTOR}: preguntan
     * {@link #actuaComoMentor()}, y un rol que acompañe grupos en el futuro se suma ahí.
     */
    TRAINEE_GUIDE,
    TRAINEE;

    /**
     * Los roles que acompañan grupos como mentor (D-265). Es el conjunto que reemplaza a {@code Set.of(MENTOR)} en las
     * consultas por rol (padrón de mentores, rotación, candidatos a liderar un grupo).
     */
    public static final Set<UserRole> ROLES_QUE_ACTUAN_COMO_MENTOR = Set.copyOf(EnumSet.of(MENTOR, TRAINEE_GUIDE));

    /**
     * La matriz rol -> permiso que CLAUDE.MD §5.3.2 pide en un solo archivo, no en {@code if}
     * desparramados.
     *
     * <blockquote><b>Corregido 2026-09-09.</b> Este javadoc decia <i>"Hoy solo esta cargada
     * para TRAINEE"</i> y que los otros 4 roles no tenian matriz. Desde el SDD 002 (decision
     * DL-08 del dueño del proyecto, 2026-09-09) <b>MENTOR_LEAD tambien la tiene</b>: se le
     * definio un alcance de gestion del cuerpo de mentores y ese alcance necesitaba permisos
     * propios. MENTOR, ADMIN y ALCHEMIST siguen sin matriz — sigue siendo la deuda A-1.
     * </blockquote>
     *
     * <p>Los permisos de TRAINEE salen de los guards ya existentes en cada servicio, no de una
     * lista deseada. Los de MENTOR_LEAD, de la matriz de roles de la especificacion del cliente
     * ({@code docs/spec/Especificacion_Requisitos_Renaser_OS.docx} §2.3 y HU-05) contrastada
     * endpoint por endpoint contra los guards de hoy.
     */
    private static final Set<Permission> PERMISOS_TRAINEE = EnumSet.of(
            Permission.USE_APP,
            Permission.FOLLOW_OWN_PROGRAM,
            Permission.PUBLISH_ON_WALL,
            Permission.OPEN_SUPPORT_TICKET,
            Permission.USE_MENTOR_TICKETS,
            Permission.OPEN_MENTOR_TICKET,
            Permission.VIEW_OWN_PHASE_CONTRACTS,
            Permission.SIGN_PHASE_CONTRACT);

    /**
     * Matriz de MENTOR_LEAD (SDD 002, tarea TL-05). <b>Derivada del inventario de endpoints, no
     * de memoria</b>: cada permiso de esta lista corresponde a algo que el lider de mentores ya
     * hacia, o a una capacidad que la especificacion del cliente §2.3 le concede.
     *
     * <p><b>Lo que se le concede y por que:</b>
     * <ul>
     *   <li>{@link Permission#USE_APP} — cuenta activa; es la base de 107 endpoints.</li>
     *   <li>{@link Permission#TRACK_PROGRAM_AS_STAFF} — ya lo tenia
     *       ({@code ParticipacionProgramaService}: <i>"El seguimiento personal opcional es solo
     *       para MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST"</i>).</li>
     *   <li>{@link Permission#FOLLOW_OWN_PROGRAM} — porque el dueño del proyecto decidio que el
     *       lider recorre el programa como un aprendiz (SDD 002, DL-01). <b>Ojo:</b> el permiso
     *       no alcanza por si solo — ver {@code docs/BITACORA_ERRORES.md} <b>E-169</b>: diez
     *       guards de {@code rocks}, {@code habits} y {@code academy} comparan el rol contra
     *       TRAINEE literal y siguen devolviendo 403. Se concede igual para que la capa de
     *       permisos no sea <i>tambien</i> un bloqueo cuando E-169 se resuelva.</li>
     *   <li>{@link Permission#PUBLISH_ON_WALL} — ya lo tenia
     *       ({@code PublicacionMuroService.requireActorPuedePublicar} lo enumera).</li>
     *   <li>{@link Permission#OPEN_SUPPORT_TICKET} — lo tiene cualquier cuenta, activa o
     *       suspendida.</li>
     *   <li>{@link Permission#VIEW_ALL_MENTOR_TICKETS} — ya lo tenia, y es la materia prima del
     *       seguimiento ({@code TicketMentorService}: <i>"Solo MENTOR_LEAD/ADMIN/ALCHEMIST ven
     *       todos los tickets"</i>).</li>
     *   <li>Los cuatro permisos nuevos del SDD 002: ver sus javadoc en {@link Permission}.</li>
     * </ul>
     *
     * <p><b>Lo que NO se le concede, a proposito:</b> ningun {@code MANAGE_*}
     * ({@code MANAGE_STAFF}, {@code MANAGE_CELLS}, {@code MANAGE_ROLES},
     * {@code MANAGE_TRAINEES}, {@code MANAGE_MENTOR_PROFILE}, {@code MANAGE_CALENDAR}...),
     * {@code APPROVE_ACCOUNT_REQUEST}, {@code ASSIGN_MENTOR}, {@code ADJUST_POINTS} ni
     * {@code MODERATE_WALL}. Gestionar el cuerpo de mentores no es administrar la plataforma
     * (SDD 002, CL-04).
     *
     * <p><b>Tampoco {@code USE_MENTOR_TICKETS}, {@code OPEN_MENTOR_TICKET},
     * {@code ANSWER_MENTOR_TICKET}, {@code VIEW_OWN_PHASE_CONTRACTS} ni
     * {@code SIGN_PHASE_CONTRACT}</b>: hoy sus guards excluyen a MENTOR_LEAD
     * (<i>"Solo un aprendiz o un mentor pueden listar estos tickets"</i>,
     * {@code requireProgreso(actor, {TRAINEE, MENTOR})}). Dejarlos fuera <b>mantiene el
     * comportamiento actual exactamente igual</b>: el 403 lo da el interceptor en vez del
     * servicio. Los dos ultimos pertenecen a la misma familia que E-169 y se revisan cuando esa
     * decision se tome, no antes.
     */
    private static final Set<Permission> PERMISOS_MENTOR_LEAD = EnumSet.of(
            Permission.USE_APP,
            Permission.TRACK_PROGRAM_AS_STAFF,
            Permission.FOLLOW_OWN_PROGRAM,
            Permission.PUBLISH_ON_WALL,
            Permission.OPEN_SUPPORT_TICKET,
            Permission.VIEW_ALL_MENTOR_TICKETS,
            Permission.VIEW_MENTOR_CORPS,
            Permission.VIEW_MENTOR_REPORT,
            Permission.FOLLOW_UP_MENTOR,
            Permission.SET_MENTOR_OPERATIONAL_STATUS);

    /**
     * Nombre para mostrar en español (spec 001 de hábitos por rol, D-264). Vive junto al enum para que un rol nuevo
     * traiga su nombre en el mismo cambio, y una app ya publicada lo muestre sin otra versión.
     */
    public String nombreVisible() {
        return switch (this) {
            case ALCHEMIST -> "Alquimista";
            case ADMIN -> "Administrador";
            case MENTOR_LEAD -> "Líder de mentores";
            case MENTOR -> "Mentor";
            case TRAINEE_GUIDE -> "Guía de aprendiz";
            case TRAINEE -> "Aprendiz";
            case IT_SUPPORT -> "Soporte TI";
        };
    }

    /** Si acompaña grupos con las mismas capacidades que un mentor: MENTOR y TRAINEE_GUIDE (D-265). */
    public boolean actuaComoMentor() {
        return ROLES_QUE_ACTUAN_COMO_MENTOR.contains(this);
    }

    public boolean canManageRoles() {
        return this == ADMIN || this == ALCHEMIST;
    }

    /**
     * <b>HUECO DE SEGURIDAD DELIBERADO Y TEMPORAL (A-1), ya no completo.</b> TRAINEE y
     * MENTOR_LEAD consultan su matriz real. Para MENTOR, TRAINEE_GUIDE (D-265: lo mismo que MENTOR), ADMIN y
     * ALCHEMIST <b>sigue devolviendo
     * {@code true}</b> — falla-abierto, no falla-cerrado — porque CLAUDE.MD §0.6 prohibe
     * inventar que puede hacer cada uno de esos roles sin que el dueño del proyecto lo
     * confirme. No se esconde detras de un {@code default} silencioso: el {@code switch} nombra
     * uno por uno los que siguen sin matriz, para que el hueco quede a la vista.
     *
     * <blockquote><b>Corregido 2026-09-09.</b> Esta rama decia
     * {@code case MENTOR, MENTOR_LEAD, ADMIN, ALCHEMIST -> true}. MENTOR_LEAD salio de ahi por
     * la decision DL-08 del SDD 002.</blockquote>
     *
     * <p><b>Devolver {@code false} aca no significa 403 todavia para MENTOR_LEAD:</b> quien
     * decide si se deniega es {@code PermissionEnforcementInterceptor}, que para este rol
     * arranca en <i>modo sombra</i> (registra lo que denegaria, sin denegar) hasta que el
     * dueño active el cumplimiento. Esa es la secuencia de tres pasos de DL-08.
     */
    public boolean can(Permission permission) {
        // Un permiso exclusivo de Soporte TI (OPERATE_PLATFORM) no pasa por el falla-abierto de abajo (D-267).
        if (permission.exclusivoDeSoporteTi()) {
            return this == IT_SUPPORT;
        }
        return switch (this) {
            case TRAINEE -> PERMISOS_TRAINEE.contains(permission);
            case MENTOR_LEAD -> PERMISOS_MENTOR_LEAD.contains(permission);
            case MENTOR, TRAINEE_GUIDE, ADMIN, ALCHEMIST -> true;
            // Soporte TI tiene todos los permisos de todos los roles más la consola (FR-023). Ojo: los guards de
            // Administración de los servicios todavía preguntan ADMIN/ALCHEMIST literal (D-267, pendiente).
            case IT_SUPPORT -> true;
        };
    }

    public static UserRole defaultForSelfRegistration() {
        return TRAINEE;
    }
}
