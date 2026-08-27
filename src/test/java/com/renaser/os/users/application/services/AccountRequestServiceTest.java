package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase.ApproveAccountRequestCommand;
import com.renaser.os.users.application.ports.in.accountrequest.DeleteAccountRequestUseCase.DeleteAccountRequestCommand;
import com.renaser.os.users.application.ports.in.accountrequest.ListAccountRequestsUseCase.ListAccountRequestsCommand;
import com.renaser.os.users.application.ports.in.accountrequest.RejectAccountRequestUseCase.RejectAccountRequestCommand;
import com.renaser.os.users.application.ports.out.accountrequest.DeleteAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.SaveAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.SupabaseAdminAuthPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.DeleteUserPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private DeleteAccountRequestPort deleteAccountRequestPort;
    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private SaveUserPort saveUserPort;
    @Mock
    private DeleteUserPort deleteUserPort;
    @Mock
    private SaveCredencialPort saveCredencialPort;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SupabaseAdminAuthPort supabaseAdminAuthPort;
    @Mock
    private SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    @Mock
    private com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort tokenVerificacionEmailPort;
    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    private AccountRequestService service;

    @BeforeEach
    void setUp() {
        service = new AccountRequestService(loadAccountRequestPort, saveAccountRequestPort, deleteAccountRequestPort,
                loadUserPort, saveUserPort, deleteUserPort, saveCredencialPort, passwordEncoder,
                supabaseAdminAuthPort, saveParticipacionProgramaPort, tokenVerificacionEmailPort,
                new RequireActiveUserGuard(loadUserPort), new RequireAdminGuard(loadUserPort), events, CLOCK);
        lenient().when(saveAccountRequestPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveUserPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(passwordEncoder.encode(any())).thenReturn("hash-de-prueba");
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

    /** El usuario tal como queda tras {@code submit()}: ya existe, INACTIVE, esperando aprobar. */
    private static User pendienteDeAprobacion(AccountRequest request) {
        return User.registrarPendienteAprobacion(request.supabaseUserId(), request.email(), request.fullName());
    }

    /** Stub comun a los tests de {@code approve()}: el usuario que la solicitud ya creo. */
    private void stubUsuarioPendiente(AccountRequest request) {
        lenient().when(loadUserPort.byId(request.supabaseUserId()))
                .thenReturn(Optional.of(pendienteDeAprobacion(request)));
    }

    private static com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand
    comandoDeAlta(String verificationToken) {
        return new com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand(
                "solicitante@renaser.dev", "Solicitante", "555-0000", "Lima", verificationToken,
                "una-contrasena-de-12-o-mas", "127.0.0.1");
    }

    @Test
    @DisplayName("2026-08-27: submit consume el token de verificacion y arma la solicitud")
    void submitConTokenValidoArmaLaSolicitud() {
        when(tokenVerificacionEmailPort.consumir("token-valido")).thenReturn(Optional.of("solicitante@renaser.dev"));

        service.submit(comandoDeAlta("token-valido"));

        verify(saveAccountRequestPort).save(any());
        verify(tokenVerificacionEmailPort).consumir("token-valido");
    }

    @Test
    @DisplayName("2026-08-27: submit crea el usuario YA en el alta (INACTIVE) con su contrasena hasheada, "
            + "no recien al aprobar")
    void submitCreaElUsuarioInactivoConSuCredencial() {
        when(tokenVerificacionEmailPort.consumir("token-valido")).thenReturn(Optional.of("solicitante@renaser.dev"));

        service.submit(comandoDeAlta("token-valido"));

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(userCaptor.capture());
        assertThat(userCaptor.getValue().status()).isEqualTo(UserStatus.INACTIVE);

        verify(passwordEncoder).encode("una-contrasena-de-12-o-mas");
        var credencialCaptor = org.mockito.ArgumentCaptor.forClass(com.renaser.os.users.domain.model.user.Credencial.class);
        verify(saveCredencialPort).guardar(eq(userCaptor.getValue().id()), credencialCaptor.capture());
        assertThat(credencialCaptor.getValue().hash()).isEqualTo("hash-de-prueba");
    }

    @Test
    @DisplayName("2026-08-27: submit rechaza un token que nunca se emitio, ya vencio, o ya se uso — "
            + "y no llega a crear ni el usuario ni la solicitud")
    void submitRechazaUnTokenInvalido() {
        when(tokenVerificacionEmailPort.consumir("token-invalido")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(comandoDeAlta("token-invalido")))
                .isInstanceOf(com.renaser.os.shared.domain.TokenVerificacionEmailInvalidoException.class);

        verify(saveAccountRequestPort, never()).save(any());
        verify(saveUserPort, never()).save(any());
        verify(saveCredencialPort, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("2026-08-27: submit rechaza un token que verifica un email DISTINTO al del comando — "
            + "alguien verifico un correo y trato de usar el token para dar de alta otro")
    void submitRechazaUnTokenQueVerificaOtroEmail() {
        when(tokenVerificacionEmailPort.consumir("token-de-otro-email"))
                .thenReturn(Optional.of("otro-email-verificado@renaser.dev"));

        assertThatThrownBy(() -> service.submit(comandoDeAlta("token-de-otro-email")))
                .isInstanceOf(com.renaser.os.shared.domain.TokenVerificacionEmailInvalidoException.class);

        verify(saveAccountRequestPort, never()).save(any());
        verify(saveUserPort, never()).save(any());
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
        stubUsuarioPendiente(request);

        service.approve(new ApproveAccountRequestCommand(request.id(), actorId));

        verify(saveUserPort).save(any());
        verify(saveAccountRequestPort).save(any());
    }

    @Test
    @DisplayName("2026-08-27: aprobar SOLO activa al usuario que el alta ya creo — no genera token "
            + "ni manda correo, porque la credencial ya la eligio la persona al registrarse")
    void approveActivaSinTocarLaCredencial() {
        AccountRequest request = pendiente();
        UserId actorId = id();
        when(loadAccountRequestPort.byId(request.id())).thenReturn(Optional.of(request));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));
        stubUsuarioPendiente(request);

        service.approve(new ApproveAccountRequestCommand(request.id(), actorId));

        var saveUserCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(saveUserCaptor.capture());
        assertThat(saveUserCaptor.getValue().id()).isEqualTo(request.supabaseUserId());
        assertThat(saveUserCaptor.getValue().status()).isEqualTo(UserStatus.ACTIVE);
        verify(saveCredencialPort, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("aprobar una solicitud cuyo usuario ya no esta INACTIVE (doble aprobacion, por ejemplo) "
            + "falla en el dominio en vez de reactivar algo que no corresponde")
    void approveRechazaUnUsuarioQueYaNoEstaPendiente() {
        AccountRequest request = pendiente();
        UserId actorId = id();
        when(loadAccountRequestPort.byId(request.id())).thenReturn(Optional.of(request));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));
        when(loadUserPort.byId(request.supabaseUserId()))
                .thenReturn(Optional.of(activo(request.supabaseUserId(), UserRole.TRAINEE)));

        assertThatThrownBy(() -> service.approve(new ApproveAccountRequestCommand(request.id(), actorId)))
                .isInstanceOf(IllegalStateException.class);

        verify(saveAccountRequestPort, never()).save(any());
    }

    @Test
    @DisplayName("R-7: aprobar la cuenta de un TRAINEE crea su fila de participantes_programa "
            + "en la MISMA transaccion, con el reloj del programa pausado (dia 0, sin activar)")
    void approveCreaLaParticipacionDelTraineeConElRelojPausado() {
        AccountRequest request = pendiente();
        UserId actorId = id();
        when(loadAccountRequestPort.byId(request.id())).thenReturn(Optional.of(request));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));
        stubUsuarioPendiente(request);

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
        verify(deleteUserPort, never()).deleteById(any());
    }

    @Test
    @DisplayName("2026-08-27: rechazar borra al usuario que el alta ya habia creado — si no, el "
            + "correo quedaria ocupado para siempre por una cuenta que nunca se va a activar")
    void rejectBorraElUsuarioParaLiberarElCorreo() {
        AccountRequest request = pendiente();
        UserId actorId = id();
        when(loadAccountRequestPort.byId(request.id())).thenReturn(Optional.of(request));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));

        service.reject(new RejectAccountRequestCommand(request.id(), actorId, "motivo"));

        verify(deleteUserPort).deleteById(request.supabaseUserId());
    }

    // ─── panel admin de solicitudes de cuenta (gap #9) ─────────────────────

    @Test
    @DisplayName("listar: un actor no-ADMIN es rechazado con 403")
    void listarRechazaActorSinPermiso() {
        UserId actorId = id();
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.MENTOR)));

        assertThatThrownBy(() -> service.listar(new ListAccountRequestsCommand(actorId, null, 0, 20)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("listar: un ADMIN activo si puede listar solicitudes")
    void listarAceptaAdminActivo() {
        UserId actorId = id();
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));
        when(loadAccountRequestPort.pagina(null, 0, 20)).thenReturn(java.util.List.of());
        when(loadAccountRequestPort.contar(null)).thenReturn(0L);

        var pagina = service.listar(new ListAccountRequestsCommand(actorId, null, 0, 20));

        assertThat(pagina.total()).isZero();
    }

    @Test
    @DisplayName("eliminar: una solicitud inexistente da 404 sin importar si el actor es valido")
    void eliminarRechazaSolicitudInexistenteAntesDeChequearElActor() {
        UserId actorId = id();
        AccountRequestId requestId = AccountRequestId.newId();
        when(loadAccountRequestPort.byId(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(new DeleteAccountRequestCommand(actorId, requestId)))
                .isInstanceOf(java.util.NoSuchElementException.class);

        verify(deleteAccountRequestPort, never()).deleteById(any());
    }

    @Test
    @DisplayName("eliminar: un actor SUSPENDIDO no puede borrar una solicitud (403, no 404)")
    void eliminarRechazaActorSuspendido() {
        AccountRequest request = pendiente();
        UserId actorId = id();
        when(loadAccountRequestPort.byId(request.id())).thenReturn(Optional.of(request));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(suspendido(actorId, UserRole.ADMIN)));

        assertThatThrownBy(() -> service.eliminar(new DeleteAccountRequestCommand(actorId, request.id())))
                .isInstanceOf(NotAuthorizedException.class);

        verify(deleteAccountRequestPort, never()).deleteById(any());
    }

    @Test
    @DisplayName("eliminar: un ADMIN activo si puede borrar una solicitud")
    void eliminarAceptaAdminActivo() {
        AccountRequest request = pendiente();
        UserId actorId = id();
        when(loadAccountRequestPort.byId(request.id())).thenReturn(Optional.of(request));
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId, UserRole.ADMIN)));

        service.eliminar(new DeleteAccountRequestCommand(actorId, request.id()));

        verify(deleteAccountRequestPort).deleteById(request.id());
    }

    @Test
    @DisplayName("consultar: PUBLIC_ENDPOINT, sin actor — una solicitud inexistente da 404")
    void consultarRechazaSolicitudInexistente() {
        AccountRequestId requestId = AccountRequestId.newId();
        when(loadAccountRequestPort.byId(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(requestId)).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    @DisplayName("consultar: devuelve el estado y el motivo de rechazo si aplica, sin exponer mas datos")
    void consultarDevuelveEstadoYMotivo() {
        AccountRequest request = pendiente();
        when(loadAccountRequestPort.byId(request.id())).thenReturn(Optional.of(request));

        var vista = service.consultar(request.id());

        assertThat(vista.status()).isEqualTo(com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus.PENDING);
        assertThat(vista.rejectionReason()).isNull();
    }
}
