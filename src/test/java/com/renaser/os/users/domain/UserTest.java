package com.renaser.os.users.domain;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de dominio puro: sin Spring, sin Postgres, sin Testcontainers.
 * Que esto sea posible es la prueba de que la arquitectura esta bien puesta (§5.1.1).
 */
class UserTest {

    private static User trainee() {
        return User.registerTrainee(newId(), new Email("aprendiz@renaser.com"), "Ana Aprendiz");
    }

    private static User adminActor() {
        return User.rehydrate(newId(), new Email("admin@renaser.com"), UserRole.ADMIN,
                UserStatus.ACTIVE, "Admin", null, null);
    }

    private static UserId newId() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("el autoregistro siempre crea un TRAINEE activo")
    void selfRegistrationForcesTraineeRole() {
        User user = trainee();

        assertThat(user.role()).isEqualTo(UserRole.TRAINEE);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.hasAccess()).isTrue();
    }

    @Test
    @DisplayName("un TRAINEE no puede cambiar roles")
    void traineeCannotChangeRoles() {
        User target = trainee();
        User actor = trainee();

        assertThatThrownBy(() -> target.changeRole(UserRole.ADMIN, actor))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessageContaining("ADMIN/ALCHEMIST");

        assertThat(target.role()).isEqualTo(UserRole.TRAINEE);
    }

    @Test
    @DisplayName("un ADMIN si puede cambiar roles")
    void adminCanChangeRoles() {
        User target = trainee();

        target.changeRole(UserRole.MENTOR, adminActor());

        assertThat(target.role()).isEqualTo(UserRole.MENTOR);
    }

    @Test
    @DisplayName("invitar con rol explicito requiere un actor con permiso")
    void inviteRequiresRoleManager() {
        assertThatThrownBy(() -> User.invite(newId(), new Email("mentor@renaser.com"),
                "Mentor", UserRole.MENTOR, trainee()))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un usuario suspendido pierde el acceso")
    void suspendedUserLosesAccess() {
        User user = trainee();

        user.suspend();
        assertThat(user.hasAccess()).isFalse();

        user.reactivate();
        assertThat(user.hasAccess()).isTrue();
    }

    @Test
    @DisplayName("el nombre no puede quedar vacio")
    void nameCannotBeBlank() {
        User user = trainee();

        assertThatThrownBy(() -> user.rename("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dos usuarios son el mismo si comparten id")
    void identityIsTheId() {
        UserId id = newId();
        User one = User.registerTrainee(id, new Email("a@renaser.com"), "A");
        User two = User.registerTrainee(id, new Email("b@renaser.com"), "B");

        assertThat(one).isEqualTo(two);
    }
}
