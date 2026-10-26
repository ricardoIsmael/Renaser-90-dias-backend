package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.admin.ConsultarRolesAsignablesUseCase.RolAsignable;
import com.renaser.os.users.application.ports.out.rolpersonalizado.CargarRolesPersonalizadosPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado.Datos;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code GET /admin/roles-asignables} (D-272). Sin Spring. */
class RolesAsignablesServiceTest {

    private final LoadUserPort usuarios = mock(LoadUserPort.class);
    private final CargarRolesPersonalizadosPort roles = mock(CargarRolesPersonalizadosPort.class);
    private final RolesAsignablesService service = new RolesAsignablesService(new RequireAdminGuard(usuarios), roles);

    private UserId cuenta(UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        when(usuarios.byId(id)).thenReturn(Optional.of(User.rehydrate(id, new Email(id.value() + "@renaser.test"),
                rol, estado, "Persona", null, null, null, null)));
        return id;
    }

    @Test
    @DisplayName("los seis base en el orden de la app y después los personalizados activos; Soporte TI no figura")
    void seisBaseYLosActivos() {
        UserId soporte = UserId.of(UUID.randomUUID());
        Instant ahora = Instant.parse("2026-10-14T15:00:00Z");
        RolPersonalizado activo = RolPersonalizado.crear(UUID.randomUUID(), new Datos("Mentor junior", null,
                UserRole.MENTOR, Set.of()), soporte, ahora);
        RolPersonalizado inactivo = RolPersonalizado.crear(UUID.randomUUID(), new Datos("Viejo", null,
                UserRole.TRAINEE, Set.of()), soporte, ahora).editar(new Datos("Viejo", null, null, Set.of()), false, 0,
                soporte, ahora);
        when(roles.todos()).thenReturn(List.of(activo, inactivo));

        List<RolAsignable> asignables = service.rolesAsignables(cuenta(UserRole.ALCHEMIST, UserStatus.ACTIVE));

        assertThat(asignables).extracting(RolAsignable::clave).containsExactly("TRAINEE", "TRAINEE_GUIDE", "MENTOR",
                "MENTOR_LEAD", "ADMIN", "ALCHEMIST", "rp:" + activo.id());
        assertThat(asignables.getFirst()).isEqualTo(new RolAsignable("TRAINEE", "Aprendiz", UserRole.TRAINEE, null));
        assertThat(asignables.getLast()).isEqualTo(new RolAsignable("rp:" + activo.id(), "Mentor junior",
                UserRole.MENTOR, activo.id()));
    }

    @Test
    @DisplayName("solo ADMIN y ALQUIMISTA activos: un mentor, Soporte TI o un admin suspendido reciben 403")
    void soloAdministracion() {
        for (UserId actor : List.of(cuenta(UserRole.MENTOR, UserStatus.ACTIVE),
                cuenta(UserRole.IT_SUPPORT, UserStatus.ACTIVE), cuenta(UserRole.ADMIN, UserStatus.SUSPENDED))) {
            assertThatThrownBy(() -> service.rolesAsignables(actor)).isInstanceOf(NotAuthorizedException.class);
        }
    }
}
