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

    ALCHEMIST,
    ADMIN,
    MENTOR_LEAD,
    MENTOR,
    TRAINEE;

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

    public boolean canManageRoles() {
        return this == ADMIN || this == ALCHEMIST;
    }

    /**
     * <b>HUECO DE SEGURIDAD DELIBERADO Y TEMPORAL (A-1), ya no completo.</b> TRAINEE y
     * MENTOR_LEAD consultan su matriz real. Para MENTOR, ADMIN y ALCHEMIST <b>sigue devolviendo
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
        return switch (this) {
            case TRAINEE -> PERMISOS_TRAINEE.contains(permission);
            case MENTOR_LEAD -> PERMISOS_MENTOR_LEAD.contains(permission);
            case MENTOR, ADMIN, ALCHEMIST -> true;
        };
    }

    public static UserRole defaultForSelfRegistration() {
        return TRAINEE;
    }
}
