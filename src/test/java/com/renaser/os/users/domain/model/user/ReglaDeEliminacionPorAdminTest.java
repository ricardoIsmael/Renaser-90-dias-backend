package com.renaser.os.users.domain.model.user;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Decision 2 del dueño (D-243): quien elimina a quien desde Administracion. Sin Spring. */
class ReglaDeEliminacionPorAdminTest {

    private static User cuenta(UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        return User.rehydrate(id, new Email(rol.name().toLowerCase() + "-" + id + "@renaser.dev"), rol, estado,
                "Persona " + rol, null, null, null, null);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "ALCHEMIST"})
    @DisplayName("ADMIN y ALQUIMISTA eliminan a un aprendiz, un mentor o un lider")
    void adminYAlquimistaEliminanCuentasQueNoSonDeGestion(UserRole rolDelActor) {
        User actor = cuenta(rolDelActor, UserStatus.ACTIVE);
        for (UserRole rol : new UserRole[] {UserRole.TRAINEE, UserRole.MENTOR, UserRole.MENTOR_LEAD}) {
            assertThatCode(() -> ReglaDeEliminacionPorAdmin.exigirPermitida(actor, cuenta(rol, UserStatus.ACTIVE)))
                    .doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"TRAINEE", "MENTOR", "MENTOR_LEAD"})
    @DisplayName("aprendiz, mentor y lider no eliminan a nadie")
    void losDemasRolesNoEliminan(UserRole rolDelActor) {
        User actor = cuenta(rolDelActor, UserStatus.ACTIVE);

        assertThatThrownBy(() -> ReglaDeEliminacionPorAdmin.exigirPermitida(actor, cuenta(UserRole.TRAINEE,
                UserStatus.ACTIVE))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un ADMIN suspendido no elimina")
    void adminSuspendidoNoElimina() {
        User actor = cuenta(UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> ReglaDeEliminacionPorAdmin.exigirPermitida(actor, cuenta(UserRole.TRAINEE,
                UserStatus.ACTIVE))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un ALQUIMISTA no elimina a un ADMIN ni a otro ALQUIMISTA; un ADMIN si")
    void soloUnAdminEliminaCuentasDeGestion() {
        User alquimista = cuenta(UserRole.ALCHEMIST, UserStatus.ACTIVE);
        User admin = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);

        assertThatThrownBy(() -> ReglaDeEliminacionPorAdmin.exigirPermitida(alquimista,
                cuenta(UserRole.ADMIN, UserStatus.ACTIVE))).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> ReglaDeEliminacionPorAdmin.exigirPermitida(alquimista,
                cuenta(UserRole.ALCHEMIST, UserStatus.ACTIVE))).isInstanceOf(NotAuthorizedException.class);
        assertThatCode(() -> ReglaDeEliminacionPorAdmin.exigirPermitida(admin,
                cuenta(UserRole.ADMIN, UserStatus.ACTIVE))).doesNotThrowAnyException();
        assertThatCode(() -> ReglaDeEliminacionPorAdmin.exigirPermitida(admin,
                cuenta(UserRole.ALCHEMIST, UserStatus.ACTIVE))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un ADMIN no se elimina a si mismo por esta via")
    void nadieSeEliminaASiMismo() {
        User admin = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);

        assertThatThrownBy(() -> ReglaDeEliminacionPorAdmin.exigirPermitida(admin, admin))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("el correo escrito tiene que ser el de la cuenta (sin importar mayusculas ni espacios)")
    void correoDeConfirmacion() {
        User aprendiz = cuenta(UserRole.TRAINEE, UserStatus.ACTIVE);
        String correo = aprendiz.email().value();

        assertThatCode(() -> ReglaDeEliminacionPorAdmin.exigirCorreoConfirmado(aprendiz, "  " + correo.toUpperCase() + " "))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ReglaDeEliminacionPorAdmin.exigirCorreoConfirmado(aprendiz, "otra@renaser.dev"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReglaDeEliminacionPorAdmin.exigirCorreoConfirmado(aprendiz, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
