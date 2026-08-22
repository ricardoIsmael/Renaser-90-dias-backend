package com.renaser.os.users.domain;

/**
 * Estado de la cuenta. SUSPENDED corta el acceso antes de llegar al caso de uso (§5.3.5).
 */
public enum UserStatus {

    ACTIVE,
    SUSPENDED;

    public boolean allowsAccess() {
        return this == ACTIVE;
    }
}
