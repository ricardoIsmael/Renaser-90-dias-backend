package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.IniciarSesionConProveedorCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.ResultadoLoginSocial;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacionSocialServiceTest {

    @Mock
    private VerificadorIdentidadProveedor verificadorGoogle;
    @Mock
    private LoadIdentidadExternaPort loadIdentidadExternaPort;
    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private SubmitAccountRequestUseCase submitAccountRequestUseCase;

    private AutenticacionSocialService service() {
        when(verificadorGoogle.proveedor()).thenReturn(ProveedorIdentidad.GOOGLE);
        return new AutenticacionSocialService(List.of(verificadorGoogle), loadIdentidadExternaPort, loadUserPort,
                submitAccountRequestUseCase);
    }

    private static IniciarSesionConProveedorCommand command(String phone) {
        return new IniciarSesionConProveedorCommand(ProveedorIdentidad.GOOGLE, "un-code", "un-verifier",
                "https://app.renaser.dev/callback", phone, "Rosario", "127.0.0.1");
    }

    private static User usuario(UserId id) {
        return User.rehydrate(id, new Email("actor@renaser.dev"), UserRole.TRAINEE, UserStatus.ACTIVE,
                "Actor de Prueba", null, null, null, null);
    }

    @Test
    void identidadYaVinculadaDevuelveSesionIniciadaConElUsuarioCorrespondiente() {
        UserId id = UserId.of(UUID.randomUUID());
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-1", "actor@renaser.dev", true, "Actor"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.of(new IdentidadExterna(ProveedorIdentidad.GOOGLE, "google-sub-1", id,
                        "actor@renaser.dev", Instant.now())));
        when(loadUserPort.byId(id)).thenReturn(Optional.of(usuario(id)));

        ResultadoLoginSocial resultado = service().iniciarSesion(command(null));

        assertThat(resultado).isInstanceOf(ResultadoLoginSocial.SesionIniciada.class);
        assertThat(((ResultadoLoginSocial.SesionIniciada) resultado).usuario().id()).isEqualTo(id);
        verify(submitAccountRequestUseCase, never()).submit(any());
    }

    @Test
    void identidadNuevaConTelefonoAbreUnaAccountRequestSinCrearUsuario() {
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-nuevo", "nuevo@renaser.dev", true, "Persona Nueva"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, "google-sub-nuevo"))
                .thenReturn(Optional.empty());
        when(loadUserPort.byEmail(new Email("nuevo@renaser.dev"))).thenReturn(Optional.empty());
        AccountRequestId solicitudId = AccountRequestId.newId();
        when(submitAccountRequestUseCase.submit(any())).thenReturn(solicitudId);

        ResultadoLoginSocial resultado = service().iniciarSesion(command("+54 341 1234567"));

        assertThat(resultado).isInstanceOf(ResultadoLoginSocial.SolicitudCreada.class);
        assertThat(((ResultadoLoginSocial.SolicitudCreada) resultado).solicitudId()).isEqualTo(solicitudId);

        ArgumentCaptor<SubmitAccountRequestCommand> captor = ArgumentCaptor.forClass(SubmitAccountRequestCommand.class);
        verify(submitAccountRequestUseCase).submit(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("nuevo@renaser.dev");
        assertThat(captor.getValue().fullName()).isEqualTo("Persona Nueva");
        assertThat(captor.getValue().phone()).isEqualTo("+54 341 1234567");
        assertThat(captor.getValue().city()).isEqualTo("Rosario");
    }

    @Test
    void identidadNuevaSinNombreUsaElEmailComoFullName() {
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-sin-nombre", "sinnombre@renaser.dev", true, null));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadUserPort.byEmail(any())).thenReturn(Optional.empty());
        when(submitAccountRequestUseCase.submit(any())).thenReturn(AccountRequestId.newId());

        service().iniciarSesion(command("+54 341 1234567"));

        ArgumentCaptor<SubmitAccountRequestCommand> captor = ArgumentCaptor.forClass(SubmitAccountRequestCommand.class);
        verify(submitAccountRequestUseCase).submit(captor.capture());
        assertThat(captor.getValue().fullName()).isEqualTo("sinnombre@renaser.dev");
    }

    @Test
    void identidadNuevaSinTelefonoRechazadaSinLlegarASubmit() {
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-2", "sintelefono@renaser.dev", true, "Alguien"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadUserPort.byEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().iniciarSesion(command(null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(submitAccountRequestUseCase, never()).submit(any());
    }

    /**
     * §6.4: no vincula por email, ni siquiera silenciosamente creando otra solicitud para un
     * email que ya tiene cuenta — se rechaza en vez de generar una AccountRequest confusa.
     */
    @Test
    void identidadNuevaConEmailDeUnUsuarioExistenteEsRechazada() {
        UserId existente = UserId.of(UUID.randomUUID());
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-otro", "yaexiste@renaser.dev", true, "Alguien"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadUserPort.byEmail(new Email("yaexiste@renaser.dev"))).thenReturn(Optional.of(usuario(existente)));

        assertThatThrownBy(() -> service().iniciarSesion(command("+54 341 1234567")))
                .isInstanceOf(IllegalStateException.class);
        verify(submitAccountRequestUseCase, never()).submit(any());
    }

    @Test
    void emailNoVerificadoPorElProveedorEsRechazado() {
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-3", "sinverificar@renaser.dev", false, "Alguien"));

        assertThatThrownBy(() -> service().iniciarSesion(command(null)))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);
        verify(loadIdentidadExternaPort, never()).porProveedorYSujeto(any(), any());
    }

    @Test
    void identidadVinculadaSinUsuarioCorrespondienteEsUnEstadoInconsistente() {
        UserId id = UserId.of(UUID.randomUUID());
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-huerfano", "huerfano@renaser.dev", true, "Alguien"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any()))
                .thenReturn(Optional.of(new IdentidadExterna(ProveedorIdentidad.GOOGLE, "google-sub-huerfano", id,
                        "huerfano@renaser.dev", Instant.now())));
        when(loadUserPort.byId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().iniciarSesion(command(null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
