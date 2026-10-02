package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EstadoDeCuentaCambiadoEvent;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.out.eliminacion.RegistrarEliminacionPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Administracion → «Eliminar cuenta» / «Recuperar cuenta» (D-243). */
@ExtendWith(MockitoExtension.class)
class EliminacionPorAdministracionServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-02T15:00:00Z"));

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private SaveUserPort saveUserPort;
    @Mock
    private BorradoDefinitivoService borrado;
    @Mock
    private RegistrarEliminacionPort registrarEliminacion;
    @Mock
    private ApplicationEventPublisher events;

    private EliminacionPorAdministracionService service;

    @BeforeEach
    void setUp() {
        service = new EliminacionPorAdministracionService(loadUserPort, saveUserPort, new RequireAdminGuard(loadUserPort),
                borrado, registrarEliminacion, events, CLOCK);
    }

    private User guardada(UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        User u = User.rehydrate(id, new Email(rol.name().toLowerCase() + "-" + id + "@renaser.dev"), rol, estado,
                "Persona", null, null, null, null);
        when(loadUserPort.byId(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    @DisplayName("un ADMIN elimina en el acto a un aprendiz escribiendo su correo")
    void adminEliminaAprendiz() {
        User admin = guardada(UserRole.ADMIN, UserStatus.ACTIVE);
        User aprendiz = guardada(UserRole.TRAINEE, UserStatus.ACTIVE);

        service.eliminar(admin.id(), aprendiz.id(), aprendiz.email().value().toUpperCase());

        verify(borrado).borrar(aprendiz.id(), RegistroDeEliminacion.Accion.ELIMINADA_POR_ADMIN, admin.id());
    }

    @Test
    @DisplayName("un MENTOR o un LIDER no puede eliminar (403) y no se borra nada")
    void mentorYLiderNoPueden() {
        User aprendiz = guardada(UserRole.TRAINEE, UserStatus.ACTIVE);
        for (UserRole rol : new UserRole[] {UserRole.MENTOR, UserRole.MENTOR_LEAD, UserRole.TRAINEE}) {
            User actor = guardada(rol, UserStatus.ACTIVE);
            assertThatThrownBy(() -> service.eliminar(actor.id(), aprendiz.id(), aprendiz.email().value()))
                    .isInstanceOf(NotAuthorizedException.class);
        }
        verify(borrado, never()).borrar(any(), any(), any());
    }

    @Test
    @DisplayName("un ADMIN no se elimina a si mismo por esta via (403)")
    void adminNoSeEliminaASiMismo() {
        User admin = guardada(UserRole.ADMIN, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.eliminar(admin.id(), admin.id(), admin.email().value()))
                .isInstanceOf(NotAuthorizedException.class);
        verify(borrado, never()).borrar(any(), any(), any());
    }

    @Test
    @DisplayName("un ALQUIMISTA no elimina a un ADMIN (403)")
    void alquimistaNoEliminaAdmin() {
        User alquimista = guardada(UserRole.ALCHEMIST, UserStatus.ACTIVE);
        User admin = guardada(UserRole.ADMIN, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.eliminar(alquimista.id(), admin.id(), admin.email().value()))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("con el correo equivocado no se borra (400)")
    void correoEquivocado() {
        User admin = guardada(UserRole.ADMIN, UserStatus.ACTIVE);
        User aprendiz = guardada(UserRole.TRAINEE, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.eliminar(admin.id(), aprendiz.id(), "otra@renaser.dev"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(borrado, never()).borrar(any(), any(), any());
    }

    @Test
    @DisplayName("la cuenta objetivo se busca antes que el actor: 404 aunque el actor no pueda (E-42)")
    void cuentaInexistente() {
        UserId inexistente = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(UserId.of(UUID.randomUUID()), inexistente, "x@renaser.dev"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("recuperar devuelve el acceso, lo audita y avisa el cambio de estado")
    void recuperar() {
        User admin = guardada(UserRole.ADMIN, UserStatus.ACTIVE);
        User aprendiz = guardada(UserRole.TRAINEE, UserStatus.ACTIVE);
        aprendiz.cerrarParaEliminar(FixedClock.at(Instant.parse("2026-09-20T10:00:00Z")));

        service.recuperar(admin.id(), aprendiz.id());

        assertThat(aprendiz.hasAccess()).isTrue();
        assertThat(aprendiz.bajaPendiente()).isFalse();
        verify(saveUserPort).save(aprendiz);
        ArgumentCaptor<RegistroDeEliminacion> registro = ArgumentCaptor.forClass(RegistroDeEliminacion.class);
        verify(registrarEliminacion).registrar(registro.capture());
        assertThat(registro.getValue().accion()).isEqualTo(RegistroDeEliminacion.Accion.RECUPERADA);
        verify(events).publishEvent(any(EstadoDeCuentaCambiadoEvent.class));
    }

    @Test
    @DisplayName("un MENTOR no puede recuperar cuentas (403)")
    void mentorNoRecupera() {
        User mentor = guardada(UserRole.MENTOR, UserStatus.ACTIVE);
        User aprendiz = guardada(UserRole.TRAINEE, UserStatus.ACTIVE);
        aprendiz.cerrarParaEliminar(CLOCK);

        assertThatThrownBy(() -> service.recuperar(mentor.id(), aprendiz.id()))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("recuperar una cuenta que no esta cerrada es un conflicto (409)")
    void recuperarSinCierre() {
        User admin = guardada(UserRole.ADMIN, UserStatus.ACTIVE);
        User aprendiz = guardada(UserRole.TRAINEE, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.recuperar(admin.id(), aprendiz.id()))
                .isInstanceOf(IllegalStateException.class);
    }
}
