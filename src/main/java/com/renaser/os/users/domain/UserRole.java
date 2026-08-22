package com.renaser.os.users.domain;

/**
 * Los 4 roles del sistema (CLAUDE.MD §5.3.1). Cada uno tiene un perfil 1-a-1.
 * No se agregan roles sin agregar tambien su perfil.
 */
public enum UserRole {

    ALCHEMIST,
    ADMIN,
    MENTOR,
    TRAINEE;

    /** Solo ADMIN y ALCHEMIST pueden cambiar roles ajenos (§5.3.2). */
    public boolean canManageRoles() {
        return this == ADMIN || this == ALCHEMIST;
    }

    /** Rol por defecto de todo alta publica. El cliente nunca elige su rol (§5.3.3). */
    public static UserRole defaultForSelfRegistration() {
        return TRAINEE;
    }
}
