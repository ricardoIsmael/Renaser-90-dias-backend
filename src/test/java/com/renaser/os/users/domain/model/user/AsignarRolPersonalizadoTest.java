package com.renaser.os.users.domain.model.user;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.RolPersonalizadoAsignado;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado.Datos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Asignar un rol personalizado a una cuenta (spec 002, FR-030 y FR-031; D-272). Sin Spring. */
class AsignarRolPersonalizadoTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final UserId SOPORTE = UserId.of(UUID.randomUUID());

    private static User cuenta(UserRole rol) {
        return User.rehydrate(UserId.of(UUID.randomUUID()), new Email(UUID.randomUUID() + "@renaser.test"), rol,
                UserStatus.ACTIVE, "Persona", null, null, null, null);
    }

    private static RolPersonalizado rol(String nombre, UserRole base) {
        return RolPersonalizado.crear(UUID.randomUUID(), new Datos(nombre, null, base, Set.of("muro")), SOPORTE,
                AHORA);
    }

    @Test
    @DisplayName("la cuenta toma el rol base del rol personalizado y lo muestra con su nombre (FR-030)")
    void tomaLaBase() {
        User aprendiz = cuenta(UserRole.TRAINEE);
        RolPersonalizado mentorJunior = rol("Mentor junior", UserRole.MENTOR);

        aprendiz.asignarRolPersonalizado(mentorJunior, cuenta(UserRole.ALCHEMIST));

        assertThat(aprendiz.role()).isEqualTo(UserRole.MENTOR);
        assertThat(aprendiz.rolPersonalizadoId()).isEqualTo(mentorJunior.id());
        assertThat(aprendiz.rolPersonalizado()).contains(new RolPersonalizadoAsignado(mentorJunior.id(),
                "Mentor junior"));
    }

    @Test
    @DisplayName("cambiar el rol base sin rol personalizado lo quita, aunque la base sea la misma")
    void cambiarElRolLoQuita() {
        User mentor = cuenta(UserRole.MENTOR);
        mentor.asignarRolPersonalizado(rol("Mentor junior", UserRole.MENTOR), cuenta(UserRole.ADMIN));

        mentor.changeRole(UserRole.MENTOR, cuenta(UserRole.ADMIN));

        assertThat(mentor.rolPersonalizadoId()).isNull();
        assertThat(mentor.rolPersonalizado()).isEmpty();
    }

    @Test
    @DisplayName("un rol que hereda de ADMIN o de ALQUIMISTA lo asigna solo un ADMIN (FR-031): 403 al alquimista")
    void soloAdminAsignaLoQueHeredaDeAdministracion() {
        for (UserRole base : Set.of(UserRole.ADMIN, UserRole.ALCHEMIST)) {
            User aprendiz = cuenta(UserRole.TRAINEE);
            RolPersonalizado deAdministracion = rol("Admin acotado " + base, base);

            assertThatThrownBy(() -> aprendiz.asignarRolPersonalizado(deAdministracion, cuenta(UserRole.ALCHEMIST)))
                    .isInstanceOf(NotAuthorizedException.class).hasMessageContaining("Solo un ADMIN");
            assertThat(aprendiz.role()).isEqualTo(UserRole.TRAINEE);

            aprendiz.asignarRolPersonalizado(deAdministracion, cuenta(UserRole.ADMIN));
            assertThat(aprendiz.role()).isEqualTo(base);
        }
    }

    @Test
    @DisplayName("quien no administra roles no asigna (403); un rol desactivado no se asigna (400)")
    void guardias() {
        User aprendiz = cuenta(UserRole.TRAINEE);
        RolPersonalizado activo = rol("Mentor junior", UserRole.MENTOR);
        RolPersonalizado inactivo = activo.editar(new Datos("Mentor junior", null, null, Set.of()), false, 0,
                SOPORTE, AHORA);

        assertThatThrownBy(() -> aprendiz.asignarRolPersonalizado(activo, cuenta(UserRole.MENTOR)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> aprendiz.asignarRolPersonalizado(inactivo, cuenta(UserRole.ADMIN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("desactivado");
        assertThat(aprendiz.rolPersonalizadoId()).isNull();
    }

    @Test
    @DisplayName("a la cuenta Soporte TI no se le asigna un rol personalizado: 403")
    void soporteTiNo() {
        User soporte = cuenta(UserRole.IT_SUPPORT);

        assertThatThrownBy(() -> soporte.asignarRolPersonalizado(rol("Mentor junior", UserRole.MENTOR),
                cuenta(UserRole.ADMIN))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("al rehidratar, sin nombre (rol desactivado o de otra base) conserva la referencia y no lo muestra")
    void rehidratarSinNombre() {
        UUID rolId = UUID.randomUUID();
        User mentor = User.rehydrate(UserId.of(UUID.randomUUID()), new Email("m@renaser.test"), UserRole.MENTOR,
                UserStatus.ACTIVE, "Mentor", null, null, null, null, null, rolId, null);

        assertThat(mentor.rolPersonalizadoId()).isEqualTo(rolId);
        assertThat(mentor.rolPersonalizado()).isEmpty();
    }
}
