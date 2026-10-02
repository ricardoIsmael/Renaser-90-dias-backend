package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EstadoDeCuentaCambiadoEvent;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.autenticacion.CerrarTodasLasSesionesUseCase;
import com.renaser.os.users.application.ports.out.eliminacion.RegistrarEliminacionPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.PlazoDeGracia;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CierreDeCuentaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-02T15:00:00Z"));

    @Mock
    private SaveUserPort saveUserPort;
    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private CerrarTodasLasSesionesUseCase cerrarSesiones;
    @Mock
    private RegistrarEliminacionPort registrarEliminacion;
    @Mock
    private ApplicationEventPublisher events;

    private CierreDeCuentaService service;

    @BeforeEach
    void setUp() {
        service = new CierreDeCuentaService(saveUserPort, loadUserPort, cerrarSesiones, registrarEliminacion, events,
                CLOCK, new PlazoDeGracia(30));
    }

    private static User cuenta(UserRole rol) {
        UserId id = UserId.of(UUID.randomUUID());
        return User.rehydrate(id, new Email("c-" + id + "@renaser.dev"), rol, UserStatus.ACTIVE, "Cuenta", null, null,
                null, null);
    }

    @Test
    @DisplayName("cierra en el acto: suspendida, sesiones cerradas, evento de suspension y auditoria; se borra en 30 dias")
    void cierraEnElActo() {
        User aprendiz = cuenta(UserRole.TRAINEE);

        var estado = service.cerrar(aprendiz, RegistroDeEliminacion.Via.APP);

        assertThat(aprendiz.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(estado.solicitadaEn()).isEqualTo(CLOCK.now());
        assertThat(estado.purgaEl()).isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
        verify(saveUserPort).save(aprendiz);
        verify(cerrarSesiones).cerrarTodas(aprendiz.id());
        ArgumentCaptor<EstadoDeCuentaCambiadoEvent> evento = ArgumentCaptor.forClass(EstadoDeCuentaCambiadoEvent.class);
        verify(events).publishEvent(evento.capture());
        assertThat(evento.getValue().estadoAnterior()).isEqualTo(UserStatus.ACTIVE);
        assertThat(evento.getValue().estadoNuevo()).isEqualTo(UserStatus.SUSPENDED);
        ArgumentCaptor<RegistroDeEliminacion> registro = ArgumentCaptor.forClass(RegistroDeEliminacion.class);
        verify(registrarEliminacion).registrar(registro.capture());
        assertThat(registro.getValue().via()).isEqualTo(RegistroDeEliminacion.Via.APP);
    }

    @Test
    @DisplayName("cerrar una cuenta ya cerrada no reinicia el plazo ni duplica la auditoria")
    void idempotente() {
        User aprendiz = cuenta(UserRole.TRAINEE);
        aprendiz.cerrarParaEliminar(FixedClock.at(Instant.parse("2026-09-30T10:00:00Z")));

        var estado = service.cerrar(aprendiz, RegistroDeEliminacion.Via.WEB);

        assertThat(estado.solicitadaEn()).isEqualTo(Instant.parse("2026-09-30T10:00:00Z"));
        verify(saveUserPort, never()).save(any());
        verify(registrarEliminacion, never()).registrar(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("la ultima cuenta ADMIN activa no se puede cerrar (409)")
    void ultimoAdmin() {
        User admin = cuenta(UserRole.ADMIN);
        when(loadUserPort.countByRoles(Set.of(UserRole.ADMIN), UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.cerrar(admin, RegistroDeEliminacion.Via.APP))
                .isInstanceOf(IllegalStateException.class);
        assertThat(admin.hasAccess()).isTrue();
        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("un ADMIN que no es el ultimo si puede cerrar su cuenta")
    void adminQueNoEsElUltimo() {
        User admin = cuenta(UserRole.ADMIN);
        when(loadUserPort.countByRoles(Set.of(UserRole.ADMIN), UserStatus.ACTIVE)).thenReturn(2L);

        service.cerrar(admin, RegistroDeEliminacion.Via.APP);

        assertThat(admin.bajaPendiente()).isTrue();
    }
}
