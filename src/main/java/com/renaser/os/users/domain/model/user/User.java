package com.renaser.os.users.domain.model.user;

import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;


@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class User {

    private final UserId id;
    private final Email email;
    private UserRole role;
    private UserStatus status;
    private String fullName;
    private String avatarUrl;
    /** Solo tiene sentido si role == ALCHEMIST. Sin tabla propia: decisión 2026-08-24, ver D-25. */
    private String bio;
    /** Solo tiene sentido si role == ADMIN. Sin tabla propia: decisión 2026-08-24, ver D-25. */
    private String department;
    private Instant lastActiveAt;

    /**
     * Alta por autoregistro. Fuerza TRAINEE: el rol no es parametro a proposito,
     * es el blindaje de §5.3.3 llevado al compilador.
     */
    public static User registerTrainee(UserId id, Email email, String fullName) {
        return new User(requireId(id), requireEmail(email), UserRole.defaultForSelfRegistration(),
                UserStatus.ACTIVE, requireName(fullName), null, null, null, null);
    }

    /** Alta por invitacion de un admin, con rol explicito (§5.3.3, InviteAndCreateUser). */
    public static User invite(UserId id, Email email, String fullName, UserRole role, User actor) {
        requireRoleManager(actor);
        return new User(requireId(id), requireEmail(email), Objects.requireNonNull(role, "role es obligatorio"),
                UserStatus.ACTIVE, requireName(fullName), null, null, null, null);
    }

    public static User rehydrate(UserId id, Email email, UserRole role, UserStatus status,
                                 String fullName, String avatarUrl, String bio, String department,
                                 Instant lastActiveAt) {
        return new User(id, email, role, status, fullName, avatarUrl, bio, department, lastActiveAt);
    }

    public void changeRole(UserRole newRole, User actor) {
        requireRoleManager(actor);
        this.role = Objects.requireNonNull(newRole, "El nuevo rol es obligatorio");
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

    public void updateBio(String newBio) {
        this.bio = newBio;
    }

    public void updateDepartment(String newDepartment) {
        this.department = newDepartment;
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

    private static void requireRoleManager(User actor) {
        Objects.requireNonNull(actor, "Se requiere un actor para esta operacion");
        if (!actor.canManageRoles()) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST cambian roles");
        }
    }

    private static UserId requireId(UserId id) {
        return Objects.requireNonNull(id, "id es obligatorio");
    }

    private static Email requireEmail(Email email) {
        return Objects.requireNonNull(email, "email es obligatorio");
    }

    private static String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede ser vacio");
        }
        return fullName.trim();
    }

    @Override
    public String toString() {
        return "User[" + id + ", " + role + ", " + status + "]";
    }
}
