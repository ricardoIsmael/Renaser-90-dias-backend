package com.renaser.os.users.domain.model.user;

import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
                UserStatus.ACTIVE, "Admin", null, null, null, null);
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

    @Test
    @DisplayName("bio y department son opcionales y se actualizan por metodo de intencion (sin tabla propia: D-25)")
    void bioAndDepartmentAreUpdatable() {
        User user = trainee();
        assertThat(user.bio()).isNull();
        assertThat(user.department()).isNull();

        user.updateBio("Alquimista fundador");
        user.updateDepartment("Operaciones");

        assertThat(user.bio()).isEqualTo("Alquimista fundador");
        assertThat(user.department()).isEqualTo("Operaciones");
    }

    // ─── baja de cuenta autogestionada (gap #5) ────────────────────────────

    @Test
    @DisplayName("un usuario nuevo no tiene baja pendiente")
    void newUserHasNoBajaPendiente() {
        User user = trainee();

        assertThat(user.bajaPendiente()).isFalse();
        assertThat(user.bajaSolicitadaEn()).isNull();
    }

    @Test
    @DisplayName("solicitarBaja marca el instante y bajaPendiente pasa a true")
    void solicitarBajaMarcaElInstante() {
        User user = trainee();
        FixedClock clock = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));

        user.solicitarBaja(clock);

        assertThat(user.bajaPendiente()).isTrue();
        assertThat(user.bajaSolicitadaEn()).isEqualTo(clock.now());
    }

    @Test
    @DisplayName("solicitarBaja es idempotente: repetirla NO reinicia el contador")
    void solicitarBajaEsIdempotente() {
        User user = trainee();
        FixedClock primero = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));
        FixedClock segundo = FixedClock.at(Instant.parse("2026-08-27T10:00:00Z"));

        user.solicitarBaja(primero);
        user.solicitarBaja(segundo);

        assertThat(user.bajaSolicitadaEn()).isEqualTo(primero.now());
    }

    @Test
    @DisplayName("cancelarBaja deshace la solicitud sin dejar rastro")
    void cancelarBajaDeshaceLaSolicitud() {
        User user = trainee();
        user.solicitarBaja(FixedClock.at(Instant.parse("2026-08-26T10:00:00Z")));

        user.cancelarBaja();

        assertThat(user.bajaPendiente()).isFalse();
        assertThat(user.bajaSolicitadaEn()).isNull();
    }

    @Test
    @DisplayName("bajaSolicitadaEn NO corta hasAccess(): la gracia deja arrepentirse")
    void bajaSolicitadaNoCortaElAcceso() {
        User user = trainee();

        user.solicitarBaja(FixedClock.at(Instant.parse("2026-08-26T10:00:00Z")));

        assertThat(user.hasAccess()).isTrue();
    }

    // ─── E-57: el avatar guarda una URL PERMANENTE, jamas una prefirmada ─────

    private static final String URL_PUBLICA =
            "https://s3-renaser90dias.s3.us-east-1.amazonaws.com/avatares/perfil.png";

    @Test
    @DisplayName("changeAvatar acepta la URL permanente del objeto publico")
    void changeAvatarAceptaUnaUrlPermanente() {
        User user = trainee();

        user.changeAvatar(URL_PUBLICA);

        assertThat(user.avatarUrl()).isEqualTo(URL_PUBLICA);
    }

    /**
     * El defecto original: se guardaba la URL de lectura PREFIRMADA (7 dias) y a la semana
     * vencia sin que nada la volviera a firmar. El chequeo del dominio es lo que hace que no
     * pueda repetirse en silencio, venga de donde venga la escritura.
     */
    @Test
    @DisplayName("E-57: changeAvatar rechaza una URL prefirmada — vence y deja la foto rota")
    void changeAvatarRechazaUnaUrlPrefirmada() {
        User user = trainee();
        String prefirmada = URL_PUBLICA + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=604800"
                + "&X-Amz-Signature=deadbeef";

        assertThatThrownBy(() -> user.changeAvatar(prefirmada))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.avatarUrl()).isNull();
    }

    @Test
    @DisplayName("changeAvatar(null) quita el avatar; vacio es lo mismo que null")
    void changeAvatarNullQuitaElAvatar() {
        User user = trainee();
        user.changeAvatar(URL_PUBLICA);

        user.changeAvatar(null);
        assertThat(user.avatarUrl()).isNull();

        user.changeAvatar(URL_PUBLICA);
        user.changeAvatar("   ");
        assertThat(user.avatarUrl()).isNull();
    }
}
