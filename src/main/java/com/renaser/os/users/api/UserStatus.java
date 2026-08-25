package com.renaser.os.users.api;

/**
 * Estado de la cuenta. Publico por la misma razon que UserRole: `UserSummary` lo expone
 * y todo modulo debe poder rechazar a un usuario suspendido.
 */
public enum UserStatus {

    ACTIVE,
    SUSPENDED;

    public boolean allowsAccess() {
        return this == ACTIVE;
    }
}
