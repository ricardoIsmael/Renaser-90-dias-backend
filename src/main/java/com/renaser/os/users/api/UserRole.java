package com.renaser.os.users.api;

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

    public boolean canManageRoles() {
        return this == ADMIN || this == ALCHEMIST;
    }


    public static UserRole defaultForSelfRegistration() {
        return TRAINEE;
    }
}
