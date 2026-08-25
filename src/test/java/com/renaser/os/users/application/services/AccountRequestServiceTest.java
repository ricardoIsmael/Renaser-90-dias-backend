package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase.ApproveAccountRequestCommand;
import com.renaser.os.users.application.ports.in.accountrequest.RejectAccountRequestUseCase.RejectAccountRequestCommand;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.SaveAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.SupabaseAdminAuthPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regresion del hallazgo de la fase de pruebas de endpoints (2026-08-25): un ADMIN
 * SUSPENDIDO podia aprobar/rechazar solicitudes de alta (creando usuarios nuevos)
 * porque `requireUser(id)` cargaba el actor sin verificar `hasAccess()`. Mismo defecto
 * que en {@link UserAccountServiceTest}, mismo arreglo.
 */
@ExtendWith(MockitoExtension.class)
class AccountRequestServiceTest {

    private static final Clock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));

    @Mock
    private LoadAccountRequestPort loadAccountRequestPort;
    @Mock
    private SaveAccountRequestPort saveAccountRequestPort;
    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private SaveUserPort saveUserPort;
    @Mock
    private SupabaseAdminAuthPort supabaseAdminAuthPort;
    @Mock
    private SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    private AccountRequestService service;

    @BeforeEach
    void setUp() {
        service = new AccountRequestService(loadAccountRequestPort, saveAccountRequestPort, saveUserPort,
                supabaseAdminAuthPort, saveParticipacionProgramaPort, new RequireActiveUserGuard(loadUserPort),
                events, CLOCK);
        lenient().when(saveAccountRequestPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveUserPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static UserId id() {
        return UserId.of(UUID.randomUUID());
    }

    private static User activo(UserId id, UserRole role) {
        return User.rehydrate(id, new Email("actor" + id.value() + "@renaser.dev"), role, UserStatus.ACTIVE,
                "Actor", null, null, null, null);
    }

    private static User suspendido(UserId id, UserRole role) {
        User user = activo(id, role);
        user.suspend();
        return user;
    }

    private static AccountRequest pendiente() {
        return AccountRequest.submit(UserId.of(UUID.randomUUID()), new Email("solicitante@renaser.dev"),
                "Solicitante", "555-0000", "Lima", null, CLOCK);
    }

    @Test
    @DisplayName("un ADMIN SUSPENDIDO no puede aprobar una solicitud de alta (no crea el usuario)")
    void approveRechazaActorSuspendido() {
        AccountRequest request = pendiente();
        UserId actorId = id();
        when(loadAccountRequestPort.byId(request.id())).thenReturn(Optional.of(request));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(suspendido(actorId, UserRole.ADMIN)));

        assertThatThrownBy(() -> service.approve(new ApproveAccountRequestCommand(request.id(), actorId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveUserPort, never()).save(any());
        verify(saveAccountRequestPort, never()).save(any());
        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    @Test
    @DisplayName("un ADMIN activo si puede aprobar una solicitud de alta")
    void approveAceptaActorActivo() {
        AccountRequest request = pendiente();
        UserId actorId = id();
        when(loadAccountRequestPort.byId(request.id())).thenReturn(Optional.of(request));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));

        service.approve(new ApproveAccountRequestCommand(request.id(), actorId));

        verify(saveUserPort).save(any());
        verify(saveAccountRequestPort).save(any());
    }

    @Test
    @DisplayName("R-7: aprobar la cuenta de un TRAINEE crea su fila de participantes_programa "
            + "en la MISMA transaccion, con el reloj del programa pausado (dia 0, sin activar)")
    void approveCreaLaParticipacionDelTraineeConElRelojPausado() {
        AccountRequest request = pendiente();
        UserId actorId = id();
        when(loadAccountRequestPort.byId(request.id())).thenReturn(Optional.of(request));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));

        service.approve(new ApproveAccountRequestCommand(request.id(), actorId));

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.renaser.os.users.domain.model.participante.ParticipacionPrograma.class);
        verify(saveParticipacionProgramaPort).save(captor.capture());
        ParticipacionPrograma participacion = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(participacion.diaPrograma()).isZero();
        org.assertj.core.api.Assertions.assertThat(participacion.programaActivadoEn()).isNull();
        org.assertj.core.api.Assertions.assertThat(participacion.fechaInicio()).isEqualTo(CLOCK.today().plusDays(1));
    }

    @Test
    @DisplayName("un ADMIN SUSPENDIDO no puede rechazar una solicitud de alta")
    void rejectRechazaActorSuspendido() {
        AccountRequest request = pendiente();
        UserId actorId = id();
        when(loadAccountRequestPort.byId(request.id())).thenReturn(Optional.of(request));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(suspendido(actorId, UserRole.ADMIN)));

        assertThatThrownBy(() -> service.reject(new RejectAccountRequestCommand(request.id(), actorId, "motivo")))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveAccountRequestPort, never()).save(any());
        verify(supabaseAdminAuthPort, never()).deleteUser(any());
    }
}
