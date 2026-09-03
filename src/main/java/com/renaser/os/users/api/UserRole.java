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
     * desparramados. <b>Hoy solo esta cargada para TRAINEE</b> (A-1, decision del dueño del
     * proyecto 2026-09-01, {@code docs/ENDPOINTS_FALTANTES.md} fila A-1): es el unico rol para
     * el que existe evidencia suficiente en el codigo (los guards ya existentes en cada
     * servicio) para afirmar, permiso por permiso, que corresponde. Los otros 4 roles
     * (MENTOR, MENTOR_LEAD, ADMIN, ALCHEMIST) no tienen matriz porque definirla es una regla
     * de negocio que el dueño del proyecto todavia no dicto — CLAUDE.MD §0.6 prohibe
     * inventarla. Se deja explicita la lista vacia en vez de omitir la entrada del mapa,
     * para que quede a la vista en este archivo que la deuda es real, no un olvido.
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

    public boolean canManageRoles() {
        return this == ADMIN || this == ALCHEMIST;
    }

    /**
     * <b>HUECO DE SEGURIDAD DELIBERADO Y TEMPORAL (A-1).</b> Para TRAINEE, consulta la matriz
     * real de arriba. Para MENTOR, MENTOR_LEAD, ADMIN y ALCHEMIST, <b>siempre devuelve
     * {@code true}</b> — falla-abierto, no falla-cerrado — porque CLAUDE.MD §0.6 prohibe
     * inventar reglas de negocio (que puede hacer cada uno de esos 4 roles) sin que el dueño
     * del proyecto las confirme. No se esconde detras de un {@code default} silencioso: este
     * {@code switch} nombra los 4 roles sin matriz uno por uno para que quien lea el codigo
     * vea el hueco, y {@code docs/ENDPOINTS_FALTANTES.md} fila A-1 lo deja escrito como
     * pendiente, no como terminado.
     */
    public boolean can(Permission permission) {
        return switch (this) {
            case TRAINEE -> PERMISOS_TRAINEE.contains(permission);
            case MENTOR, MENTOR_LEAD, ADMIN, ALCHEMIST -> true;
        };
    }

    public static UserRole defaultForSelfRegistration() {
        return TRAINEE;
    }
}
