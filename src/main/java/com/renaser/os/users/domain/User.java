package com.renaser.os.users.domain;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Raiz del agregado Usuario (CLAUDE.MD §5.3.2).
 *
 * Reglas que esta clase hace IMPOSIBLES de violar, no solo desaconsejadas:
 *   - la identidad viene de Supabase Auth, nunca se genera aca;
 *   - nadie puede cambiar su propio rol: hace falta un actor con permiso;
 *   - no hay setters publicos, solo metodos con nombre de intencion.
 *
 * Sin anotaciones de Spring ni de JPA: se testea con un new User(...) plano.
 */
public final class User {

    private final UserId id;
    private final Email email;
    private UserRole role;
    private UserStatus status;
    private String fullName;
    private String avatarUrl;
    private Instant lastActiveAt;

    private User(UserId id, Email email, UserRole role, UserStatus status,
                 String fullName, String avatarUrl, Instant lastActiveAt) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.email = Objects.requireNonNull(email, "email es obligatorio");
        this.role = Objects.requireNonNull(role, "role es obligatorio");
        this.status = Objects.requireNonNull(status, "status es obligatorio");
        this.fullName = requireName(fullName);
        this.avatarUrl = avatarUrl;
        this.lastActiveAt = lastActiveAt;
    }

    /**
     * Alta por autoregistro. Fuerza TRAINEE: el rol no es parametro a proposito,
     * es el blindaje de §5.3.3 llevado al compilador.
     */
    public static User registerTrainee(UserId id, Email email, String fullName) {
        return new User(id, email, UserRole.defaultForSelfRegistration(), UserStatus.ACTIVE,
                fullName, null, null);
    }

    /** Alta por invitacion de un admin, con rol explicito (§5.3.3, InviteAndCreateUser). */
    public static User invite(UserId id, Email email, String fullName, UserRole role, User actor) {
        requireRoleManager(actor);
        return new User(id, email, role, UserStatus.ACTIVE, fullName, null, null);
    }

    /** Solo para el adaptador de persistencia: reconstruye un usuario ya existente. */
    public static User rehydrate(UserId id, Email email, UserRole role, UserStatus status,
                                 String fullName, String avatarUrl, Instant lastActiveAt) {
        return new User(id, email, role, status, fullName, avatarUrl, lastActiveAt);
    }

    public void changeRole(UserRole newRole, User actor) {
        requireRoleManager(actor);
        Objects.requireNonNull(newRole, "El nuevo rol es obligatorio");
        this.role = newRole;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void reactivate() {
        this.status = UserStatus.ACTIVE;
    }

    public void rename(String newFullName) {
        this.fullName = requireName(newFullName);
    }

    public void changeAvatar(String newAvatarUrl) {
        this.avatarUrl = newAvatarUrl;
    }

    public void touchLastActive(Clock clock) {
        this.lastActiveAt = clock.now();
    }

    public boolean canManageRoles() {
        return role.canManageRoles();
    }

    public boolean hasAccess() {
        return status.allowsAccess();
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public UserRole role() {
        return role;
    }

    public UserStatus status() {
        return status;
    }

    public String fullName() {
        return fullName;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public Instant lastActiveAt() {
        return lastActiveAt;
    }

    private static void requireRoleManager(User actor) {
        Objects.requireNonNull(actor, "Se requiere un actor para esta operacion");
        if (!actor.canManageRoles()) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST cambian roles");
        }
    }

    private static String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede ser vacio");
        }
        return fullName.trim();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof User user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "User[" + id + ", " + role + ", " + status + "]";
    }
}
