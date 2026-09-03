package com.renaser.os.shared.domain;

/**
 * Vocabulario de permisos del sistema: <b>que hace falta poder hacer</b>, nunca
 * <b>quien puede hacerlo</b> (CLAUDE.MD §5.3.4). Con la lista de roles esparcida por los
 * endpoints, agregar un rol nuevo obliga a cazarlo a mano en los ~218 handlers — paso de
 * verdad con {@code MENTOR_LEAD}. Nombrando el permiso, el rol nuevo se resuelve en un
 * solo lugar: la matriz rol -> permiso.
 *
 * <p><b>Esta enum solo declara. Todavia no ejecuta nada.</b> La matriz rol -> permiso y el
 * filtro que la aplica son la fase 4 de {@code docs/MODULO_AUTH.md} §9; hoy la autorizacion
 * real la siguen haciendo los guards dentro de los servicios. Lo que se gana ahora es que
 * cada endpoint declare, de forma verificable por el build
 * ({@code EndpointAuthorizationDeclarationTest}), que permiso va a exigir.
 *
 * <p><b>De donde salio cada valor:</b> de los guards que ya existen en
 * {@code <modulo>/application/services}, no de una lista deseada. El javadoc de cada valor
 * cita el mensaje literal del {@link NotAuthorizedException} que hoy lo hace cumplir y los
 * roles que hoy lo satisfacen — ese es el insumo con el que la fase 4 arma la matriz.
 *
 * <p><b>Lo que este vocabulario NO cubre, a proposito:</b> las preguntas de <i>relacion</i>
 * — dueño del recurso, mentor asignado, lider de la celula, participante de la conversacion.
 * No son preguntas de rol y siguen viviendo en el caso de uso (§5.3.4: {@code requireSelf} y
 * {@code requireMentorScope} no cambian). Cada endpoint que ademas depende de una relacion lo
 * dice en {@code @RequiresPermission(scope = "...")}.
 */
public enum Permission {

    // ---------------------------------------------------------------------------------
    // Linea de base: la cuenta existe y da acceso. Todos los roles activos la tienen.
    // ---------------------------------------------------------------------------------

    /**
     * Cuenta activa, cualquier rol. Es el permiso de la mayoria de los endpoints de
     * autoservicio: el guard solo verifica {@code hasAccess()} — "Cuenta suspendida" /
     * "La cuenta esta suspendida" / "Cuenta inexistente o suspendida" / "Se requiere una
     * cuenta activa para consultar el ranking".
     *
     * <p>Roles que hoy lo satisfacen: TRAINEE, MENTOR, MENTOR_LEAD, ADMIN, ALCHEMIST.
     */
    USE_APP,

    /**
     * Participar del programa como aprendiz: rocas, Espiritu, Codigo Renaser, audioterapia
     * semanal, clase diaria, recomendaciones de Academia Adaptativa, Verdugo.
     *
     * <p>Guards: "Solo un aprendiz opera sus propias rocas", "Espiritu es exclusivo de
     * aprendices", "El Codigo Renaser es exclusivo de aprendices", "Audioterapia semanal es
     * exclusiva de aprendices", "Solo un aprendiz tiene clase diaria", "Solo un aprendiz
     * recibe recomendaciones de Academia Adaptativa", "Solo un aprendiz registra sus propios
     * eventos Verdugo".
     *
     * <p>Roles que hoy lo satisfacen: TRAINEE.
     */
    FOLLOW_OWN_PROGRAM,

    // ---------------------------------------------------------------------------------
    // community
    // ---------------------------------------------------------------------------------

    /**
     * Publicar en el Muro y subir media para una publicacion. Guard:
     * {@code PublicacionMuroService.requireActorPuedePublicar} -> "Rol sin permiso para
     * publicar en el Muro".
     *
     * <p><b>Hoy ese guard no puede fallar:</b> enumera en negativo los 5 roles
     * (TRAINEE, MENTOR, MENTOR_LEAD, ADMIN, ALCHEMIST), asi que cualquier cuenta activa
     * pasa. Recien mordera cuando exista un rol nuevo — y ahi lo hara en silencio, sin que
     * nadie lo haya decidido. Es el antipatron exacto que este vocabulario existe para
     * corregir; se le da nombre propio en vez de colapsarlo en {@link #USE_APP} para que la
     * decision ("quien puede publicar") sea explicita en la matriz de la fase 4 y no un
     * {@code if} enterrado en un servicio.
     */
    PUBLISH_ON_WALL,

    /**
     * Moderar el Muro: ocultar ajeno, restaurar, borrado permanente, ver ocultos.
     * Guard: {@code requireModerador} -> "Solo ADMIN/ALCHEMIST moderan el Muro".
     */
    MODERATE_WALL,

    /** Guard: {@code CategoriaMuroService.requireAdmin} -> "Solo ADMIN/ALCHEMIST administran categorias del Muro". */
    MANAGE_WALL_CATEGORIES,

    /** Guard: {@code CelulaService.requireAdmin} -> "Solo ADMIN/ALCHEMIST administran celulas". */
    MANAGE_CELLS,

    /** Guard: {@code CohorteService.requireAdmin} -> "Solo ADMIN/ALCHEMIST administran cohortes". */
    MANAGE_COHORTS,

    /** Guard: {@code TestimonioService.requireAdmin} -> "Solo administradores pueden promover publicaciones". */
    PROMOTE_TESTIMONIAL,

    // ---------------------------------------------------------------------------------
    // chat
    // ---------------------------------------------------------------------------------

    /** Guard: {@code ConversacionService.requireActivoAdmin} -> "Solo ADMIN/ALCHEMIST puede renombrar el chat global". */
    RENAME_GLOBAL_CHAT,

    // ---------------------------------------------------------------------------------
    // calendar
    // ---------------------------------------------------------------------------------

    /**
     * Crear, editar, borrar o cancelar eventos del calendario. Guard:
     * {@code EventoService.requireRolCreador} -> "No tienes permiso para administrar el
     * calendario".
     *
     * <p>Roles que hoy lo satisfacen: ADMIN, ALCHEMIST, MENTOR. <b>MENTOR_LEAD no</b> — es
     * exactamente el tipo de omision que este vocabulario existe para hacer visible.
     *
     * <p>Las dos restricciones extra son de relacion y se quedan en el servicio:
     * "Todavia no lideras una celula — no puedes administrar sesiones" y "Solo puedes
     * editar/eliminar/cancelar los eventos que creaste".
     */
    MANAGE_CALENDAR,

    // ---------------------------------------------------------------------------------
    // habits
    // ---------------------------------------------------------------------------------

    /**
     * Catalogo de habitos, guias, adjuntos de guia, horarios y duracion de audioterapia.
     * Guard: {@code HabitoAdminGuard.requireAdmin} -> "Solo ADMIN/ALCHEMIST administran el
     * catalogo de habitos".
     */
    MANAGE_HABIT_CATALOG,

    // ---------------------------------------------------------------------------------
    // evidence
    // ---------------------------------------------------------------------------------

    /** Guard: {@code EvidenciaService.requireAdmin} -> "Solo ADMIN/ALCHEMIST administran evidencia ajena". */
    MANAGE_EVIDENCE,

    // ---------------------------------------------------------------------------------
    // onboarding
    // ---------------------------------------------------------------------------------

    /** Guard: {@code OnboardingDashboardService.requireAdminActivo} -> "Solo ADMIN/ALCHEMIST consultan el dashboard de onboarding". */
    VIEW_ONBOARDING_DASHBOARD,

    // ---------------------------------------------------------------------------------
    // points
    // ---------------------------------------------------------------------------------

    /** Guard: {@code PuntajeService.ajustarManualmente} -> "Solo ADMIN/ALCHEMIST activos hacen ajustes manuales de puntos". */
    ADJUST_POINTS,

    // ---------------------------------------------------------------------------------
    // rag
    // ---------------------------------------------------------------------------------

    /** Guard: {@code ConocimientoService.requireAdmin} -> "Solo ADMIN/ALCHEMIST indexan conocimiento". */
    MANAGE_KNOWLEDGE_BASE,

    // ---------------------------------------------------------------------------------
    // support
    // ---------------------------------------------------------------------------------

    /** Guard: {@code TicketSoporteService.requireAdmin} -> "Solo ADMIN/ALCHEMIST administran tickets de soporte". */
    MANAGE_SUPPORT_TICKETS,

    /**
     * Abrir un ticket de soporte, listar los propios y adjuntar archivos.
     * {@code TicketSoporteService.requireActorExiste} <b>no verifica que la cuenta este
     * activa</b>: solo que el actor exista (si no, 404 "Actor no encontrado"). Es
     * deliberado — alguien suspendido tiene que poder escribirle a soporte justamente para
     * reclamar su suspension. Se le da nombre propio para que la fase 4 no lo colapse con
     * {@link #USE_APP} y le cierre la puerta sin querer.
     *
     * <p>Roles que hoy lo satisfacen: todos, activos o suspendidos.
     */
    OPEN_SUPPORT_TICKET,

    /**
     * Listar los tickets de mentoria propios y buscar en la biblioteca de respuestas.
     * Guards: "Solo un aprendiz o un mentor pueden listar estos tickets" / "...pueden buscar
     * en la biblioteca".
     *
     * <p>Roles que hoy lo satisfacen: TRAINEE, MENTOR.
     */
    USE_MENTOR_TICKETS,

    /** Guard: {@code TicketMentorService.requireRol(TRAINEE)} -> "Solo un aprendiz puede abrir un ticket". */
    OPEN_MENTOR_TICKET,

    /**
     * Responder un ticket de mentoria y guardar la respuesta en la biblioteca.
     * Guard de rol: {@code requireRol(MENTOR)}. La restriccion de relacion — "Solo el mentor
     * asignado a ese aprendiz puede operar su ticket" — es {@code requireMentorScope} y se
     * queda en el servicio.
     */
    ANSWER_MENTOR_TICKET,

    /**
     * Ver la bandeja completa de tickets de mentoria, no solo los propios.
     * Guard: "Solo MENTOR_LEAD/ADMIN/ALCHEMIST ven todos los tickets" — el unico permiso del
     * sistema cuyo conjunto de roles es mas amplio que {@code canManageRoles()} sin llegar a
     * ser todos.
     */
    VIEW_ALL_MENTOR_TICKETS,

    // ---------------------------------------------------------------------------------
    // phasecontracts
    // ---------------------------------------------------------------------------------

    /**
     * Consultar los contratos de fase propios y el pendiente.
     * Guard: {@code ContratoService.requireProgreso(actor, {TRAINEE, MENTOR})} ->
     * "Rol sin permiso para esta operacion: &lt;rol&gt;".
     *
     * <p>Roles que hoy lo satisfacen: TRAINEE, MENTOR (el mentor tambien recorre el programa).
     */
    VIEW_OWN_PHASE_CONTRACTS,

    /**
     * Firmar un contrato de fase y pedir la URL de subida de la firma.
     * Guard: {@code requireProgreso(actor, {TRAINEE})} -> "Rol sin permiso para esta
     * operacion: &lt;rol&gt;". Mas estricto que {@link #VIEW_OWN_PHASE_CONTRACTS} a proposito.
     */
    SIGN_PHASE_CONTRACT,

    // ---------------------------------------------------------------------------------
    // users
    // ---------------------------------------------------------------------------------

    /**
     * Activar/desactivar el seguimiento personal opcional del programa siendo staff.
     * Guard: {@code ParticipacionProgramaService.requireStaffRole} -> "El seguimiento personal
     * opcional es solo para MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST".
     *
     * <p>Roles que hoy lo satisfacen: MENTOR, MENTOR_LEAD, ADMIN, ALCHEMIST (todos menos TRAINEE).
     */
    TRACK_PROGRAM_AS_STAFF,

    /** Panel de staff. Guard: {@code RequireAdminGuard} -> "Solo ADMIN/ALCHEMIST administran este panel". */
    MANAGE_STAFF,

    /** Panel de aprendices (listado, detalle, dia de programa). Guard: {@code RequireAdminGuard}. */
    MANAGE_TRAINEES,

    /**
     * Bandeja de solicitudes de cuenta: listar, aprobar, rechazar, eliminar.
     * Guards: {@code RequireAdminGuard} -> "Solo ADMIN/ALCHEMIST administran este panel" y
     * {@code AccountRequest.requireManager} -> "Solo ADMIN/ALCHEMIST deciden solicitudes de alta".
     */
    APPROVE_ACCOUNT_REQUEST,

    /**
     * Cambiar nivel o estado operativo de un mentor.
     * Guard: {@code MentorProfileService} -> "Solo ADMIN/ALCHEMIST cambian nivel o estado
     * operativo de un mentor". La edicion de la bio del propio mentor es una relacion
     * ("Solo el propio mentor o ADMIN/ALCHEMIST editan esta bio") y se queda en el servicio.
     */
    MANAGE_MENTOR_PROFILE,

    /** Guard: {@code ParticipacionProgramaService} -> "Solo ADMIN/ALCHEMIST asignan mentor a un participante". */
    ASSIGN_MENTOR,

    /**
     * Cambiar el rol de un usuario e invitar a alguien con un rol dado. Guard:
     * {@code User.requireRoleManager} -> "Solo ADMIN/ALCHEMIST cambian roles"
     * ({@code UserRole.canManageRoles()}). Es el unico permiso que CLAUDE.MD §5.3.2 ya
     * nombraba antes de esta enum.
     */
    MANAGE_ROLES;

    /**
     * Si una cuenta SUSPENDIDA sigue teniendo este permiso. Por defecto, no — una cuenta
     * suspendida corta antes de llegar a cualquier regla de negocio (defensa en profundidad,
     * CLAUDE.MD §5.3.4/D-11). La unica excepcion hoy es {@link #OPEN_SUPPORT_TICKET}: su
     * propio javadoc documenta que {@code TicketSoporteService.requireActorExiste} NO exige
     * cuenta activa a proposito — alguien suspendido tiene que poder reclamar su suspension.
     *
     * <p>Usado por el interceptor que ejecuta {@code @RequiresPermission} (A-1,
     * {@code docs/ENDPOINTS_FALTANTES.md}): sin este metodo, activar la verificacion
     * bloquearia a un aprendiz suspendido justo en el unico canal que le queda para escribirle
     * a soporte.
     */
    public boolean toleraCuentaSuspendida() {
        return this == OPEN_SUPPORT_TICKET;
    }
}
