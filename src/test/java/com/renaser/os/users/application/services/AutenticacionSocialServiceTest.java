package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.IniciarSesionConProveedorCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.ResultadoLoginSocial;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.OrigenSocial;
import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.eq;
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
    private LoadAccountRequestPort loadAccountRequestPort;
    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private SubmitAccountRequestUseCase submitAccountRequestUseCase;
    @Mock
    private TokenVerificacionEmailPort tokenVerificacionEmailPort;

    private AutenticacionSocialService service() {
        when(verificadorGoogle.proveedor()).thenReturn(ProveedorIdentidad.GOOGLE);
        return new AutenticacionSocialService(List.of(verificadorGoogle), loadIdentidadExternaPort,
                loadAccountRequestPort, loadUserPort, submitAccountRequestUseCase, tokenVerificacionEmailPort);
    }

    private static IniciarSesionConProveedorCommand command(String phone) {
        return new IniciarSesionConProveedorCommand(ProveedorIdentidad.GOOGLE, "un-code", "un-verifier",
                "https://app.renaser.dev/callback", phone, "Rosario", "127.0.0.1");
    }

    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-08-31T10:00:00Z"));

    private static User usuario(UserId id) {
        return User.rehydrate(id, new Email("actor@renaser.dev"), UserRole.TRAINEE, UserStatus.ACTIVE,
                "Actor de Prueba", null, null, null, null);
    }

    private static User usuarioAdmin() {
        return User.rehydrate(UserId.of(UUID.randomUUID()), new Email("admin@renaser.dev"), UserRole.ADMIN,
                UserStatus.ACTIVE, "Admin", null, null, null, null);
    }

    /** Una solicitud abierta por Google, tal cual la deja el alta social. */
    private static AccountRequest solicitudSocialPendiente(String sujeto, String email) {
        return AccountRequest.submit(UserId.of(UUID.randomUUID()), new Email(email), "Alguien",
                "+54 341 1234567", "Rosario", "127.0.0.1",
                new OrigenSocial(ProveedorIdentidad.GOOGLE, sujeto), RELOJ);
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
        when(tokenVerificacionEmailPort.generar(eq("nuevo@renaser.dev"), any()))
                .thenReturn("token-verificacion-social");

        ResultadoLoginSocial resultado = service().iniciarSesion(command("+54 341 1234567"));

        assertThat(resultado).isInstanceOf(ResultadoLoginSocial.SolicitudCreada.class);
        assertThat(((ResultadoLoginSocial.SolicitudCreada) resultado).solicitudId()).isEqualTo(solicitudId);

        ArgumentCaptor<SubmitAccountRequestCommand> captor = ArgumentCaptor.forClass(SubmitAccountRequestCommand.class);
        verify(submitAccountRequestUseCase).submit(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("nuevo@renaser.dev");
        assertThat(captor.getValue().fullName()).isEqualTo("Persona Nueva");
        assertThat(captor.getValue().phone()).isEqualTo("+54 341 1234567");
        assertThat(captor.getValue().city()).isEqualTo("Rosario");
        assertThat(captor.getValue().verificationToken()).isEqualTo("token-verificacion-social");
        // 2026-08-27: sin contrasena — esta cuenta entra por el proveedor, no por clave propia.
        assertThat(captor.getValue().contrasena()).isNull();
    }

    @Test
    @DisplayName("2026-08-27: la identidad social ya viene pre-verificada por el proveedor, asi "
            + "que se salta el codigo de 6 digitos y genera el token de verificacion directo")
    void identidadNuevaGeneraElTokenDeVerificacionSinPedirCodigo() {
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-directo", "directo@renaser.dev", true, "Directo"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadUserPort.byEmail(any())).thenReturn(Optional.empty());
        when(submitAccountRequestUseCase.submit(any())).thenReturn(AccountRequestId.newId());
        when(tokenVerificacionEmailPort.generar(eq("directo@renaser.dev"), any())).thenReturn("otro-token");

        service().iniciarSesion(command("+54 341 1234567"));

        verify(tokenVerificacionEmailPort).generar(eq("directo@renaser.dev"),
                eq(VerificacionEmailService.VIGENCIA_TOKEN_VERIFICACION));
    }

    @Test
    void identidadNuevaSinNombreUsaElEmailComoFullName() {
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-sin-nombre", "sinnombre@renaser.dev", true, null));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadUserPort.byEmail(any())).thenReturn(Optional.empty());
        when(submitAccountRequestUseCase.submit(any())).thenReturn(AccountRequestId.newId());
        when(tokenVerificacionEmailPort.generar(any(), any())).thenReturn("token-verificacion-sin-nombre");

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
     * email que ya tiene cuenta — devuelve {@code CuentaExistenteSinVinculo} en vez de generar
     * una AccountRequest confusa. Sigue sin abrir sesion: eso es lo unico que importa para la
     * seguridad. La diferencia con antes (2026-08-31, A-7) es que ahora es un resultado
     * nombrado y no un {@code IllegalStateException} generico.
     */
    @Test
    void identidadNuevaConEmailDeUnUsuarioExistenteNoVinculaNiAbreSesion() {
        UserId existente = UserId.of(UUID.randomUUID());
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-otro", "yaexiste@renaser.dev", true, "Alguien"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadUserPort.byEmail(new Email("yaexiste@renaser.dev"))).thenReturn(Optional.of(usuario(existente)));

        ResultadoLoginSocial resultado = service().iniciarSesion(command("+54 341 1234567"));

        assertThat(resultado).isInstanceOf(ResultadoLoginSocial.CuentaExistenteSinVinculo.class);
        assertThat(((ResultadoLoginSocial.CuentaExistenteSinVinculo) resultado).proveedor())
                .isEqualTo(ProveedorIdentidad.GOOGLE);
        verify(submitAccountRequestUseCase, never()).submit(any());
    }

    /**
     * A-7: volver a tocar "Continuar con Google" mientras un admin no decide NO crea una segunda
     * solicitud ni devuelve un error — devuelve la que ya existe, encontrada por
     * {@code (proveedor, sujeto)}.
     */
    @Test
    void identidadConSolicitudPendienteDevuelveSolicitudEnRevisionSinCrearOtra() {
        AccountRequest previa = solicitudSocialPendiente("google-sub-espera", "espera@renaser.dev");
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-espera", "espera@renaser.dev", true, "Espera"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadAccountRequestPort.porOrigenSocial(
                new OrigenSocial(ProveedorIdentidad.GOOGLE, "google-sub-espera")))
                .thenReturn(Optional.of(previa));

        ResultadoLoginSocial resultado = service().iniciarSesion(command("+54 341 1234567"));

        assertThat(resultado).isInstanceOf(ResultadoLoginSocial.SolicitudEnRevision.class);
        assertThat(((ResultadoLoginSocial.SolicitudEnRevision) resultado).solicitudId()).isEqualTo(previa.id());
        verify(submitAccountRequestUseCase, never()).submit(any());
        // El correo no se consulta siquiera: la identidad ya quedo resuelta por (proveedor, sujeto).
        verify(loadUserPort, never()).byEmail(any());
    }

    /**
     * Una solicitud social YA RECHAZADA no bloquea para siempre: la persona puede volver a
     * intentarlo y se le abre una solicitud nueva. Solo las PENDIENTES cortan el camino.
     */
    @Test
    void identidadConSolicitudRechazadaPuedeVolverAIntentarlo() {
        AccountRequest rechazada = solicitudSocialPendiente("google-sub-rechazado", "rechazado@renaser.dev");
        rechazada.reject(usuarioAdmin(), "Datos incompletos", RELOJ);
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-rechazado", "rechazado@renaser.dev", true, "Otra Vez"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadAccountRequestPort.porOrigenSocial(any())).thenReturn(Optional.of(rechazada));
        when(loadUserPort.byEmail(any())).thenReturn(Optional.empty());
        when(submitAccountRequestUseCase.submit(any())).thenReturn(AccountRequestId.newId());
        when(tokenVerificacionEmailPort.generar(any(), any())).thenReturn("token-reintento");

        ResultadoLoginSocial resultado = service().iniciarSesion(command("+54 341 1234567"));

        assertThat(resultado).isInstanceOf(ResultadoLoginSocial.SolicitudCreada.class);
    }

    /**
     * El corazon de A-7: el {@code (proveedor, sujeto)} que se acaba de verificar tiene que
     * viajar DENTRO del comando de alta. Sin esto, {@code approve()} no tiene con que crear la
     * {@code IdentidadExterna} y la persona nunca puede volver a entrar por el mismo proveedor.
     */
    @Test
    void elComandoDeAltaLlevaLaIdentidadVerificadaParaQueApproveLaPuedaVincular() {
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-vincula", "vincula@renaser.dev", true, "Vincula"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadUserPort.byEmail(any())).thenReturn(Optional.empty());
        when(submitAccountRequestUseCase.submit(any())).thenReturn(AccountRequestId.newId());
        when(tokenVerificacionEmailPort.generar(any(), any())).thenReturn("token-vincula");

        service().iniciarSesion(command("+54 341 1234567"));

        ArgumentCaptor<SubmitAccountRequestCommand> captor = ArgumentCaptor.forClass(SubmitAccountRequestCommand.class);
        verify(submitAccountRequestUseCase).submit(captor.capture());
        assertThat(captor.getValue().proveedor()).isEqualTo(ProveedorIdentidad.GOOGLE);
        assertThat(captor.getValue().sujetoProveedor()).isEqualTo("google-sub-vincula");
    }

    @Test
    void elToStringDelComandoNoFiltraElSujetoDelProveedor() {
        SubmitAccountRequestCommand comando = SubmitAccountRequestCommand.porProveedorSocial(
                "vincula@renaser.dev", "Vincula", "+54 341 1234567", "Rosario", "token",
                "127.0.0.1", ProveedorIdentidad.GOOGLE, "google-sub-secreto");

        assertThat(comando.toString()).doesNotContain("google-sub-secreto");
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
