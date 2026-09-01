package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.IniciarSesionConProveedorCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.ResultadoLoginSocial;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.RegistroPendienteSocial;
import com.renaser.os.users.application.ports.out.autenticacion.TokenRegistroPendienteSocialPort;
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
    private TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort;

    private AutenticacionSocialService service() {
        when(verificadorGoogle.proveedor()).thenReturn(ProveedorIdentidad.GOOGLE);
        return new AutenticacionSocialService(List.of(verificadorGoogle), loadIdentidadExternaPort,
                loadAccountRequestPort, loadUserPort, tokenRegistroPendienteSocialPort);
    }

    private static IniciarSesionConProveedorCommand command() {
        return new IniciarSesionConProveedorCommand(ProveedorIdentidad.GOOGLE, "un-code", "un-verifier",
                "https://app.renaser.dev/callback", "127.0.0.1");
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

    /** Una solicitud abierta por Google, tal cual la deja el segundo paso del alta social. */
    private static AccountRequest solicitudSocialPendiente(String sujeto, String email) {
        return AccountRequest.submit(AccountRequestId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                new Email(email), "Alguien",
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

        ResultadoLoginSocial resultado = service().iniciarSesion(command());

        assertThat(resultado).isInstanceOf(ResultadoLoginSocial.SesionIniciada.class);
        assertThat(((ResultadoLoginSocial.SesionIniciada) resultado).usuario().id()).isEqualTo(id);
        verify(tokenRegistroPendienteSocialPort, never()).generar(any(), any());
    }

    /**
     * El corazon de D-65 (2026-09-01, docs/MODULO_AUTH.md §6.10): identidad nueva NO abre una
     * AccountRequest en esta misma llamada, retiene la identidad verificada en Redis y devuelve
     * el token de continuacion + los datos para prellenar el formulario.
     */
    @Test
    void identidadNuevaRetieneElRegistroPendienteSinAbrirAccountRequest() {
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-nuevo", "nuevo@renaser.dev", true, "Persona Nueva"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, "google-sub-nuevo"))
                .thenReturn(Optional.empty());
        when(loadUserPort.byEmail(new Email("nuevo@renaser.dev"))).thenReturn(Optional.empty());
        when(tokenRegistroPendienteSocialPort.generar(any(), any())).thenReturn("token-registro-pendiente");

        ResultadoLoginSocial resultado = service().iniciarSesion(command());

        assertThat(resultado).isInstanceOf(ResultadoLoginSocial.RegistroPendiente.class);
        ResultadoLoginSocial.RegistroPendiente pendiente = (ResultadoLoginSocial.RegistroPendiente) resultado;
        assertThat(pendiente.token()).isEqualTo("token-registro-pendiente");
        assertThat(pendiente.email()).isEqualTo("nuevo@renaser.dev");
        assertThat(pendiente.fullName()).isEqualTo("Persona Nueva");

        ArgumentCaptor<RegistroPendienteSocial> captor = ArgumentCaptor.forClass(RegistroPendienteSocial.class);
        verify(tokenRegistroPendienteSocialPort).generar(captor.capture(),
                eq(AutenticacionSocialService.VIGENCIA_REGISTRO_PENDIENTE));
        assertThat(captor.getValue().email()).isEqualTo("nuevo@renaser.dev");
        assertThat(captor.getValue().fullName()).isEqualTo("Persona Nueva");
        assertThat(captor.getValue().proveedor()).isEqualTo(ProveedorIdentidad.GOOGLE);
        assertThat(captor.getValue().sujetoProveedor()).isEqualTo("google-sub-nuevo");
    }

    @Test
    void identidadNuevaSinNombreUsaElEmailComoFullNameEnElPrellenado() {
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-sin-nombre", "sinnombre@renaser.dev", true, null));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadUserPort.byEmail(any())).thenReturn(Optional.empty());
        when(tokenRegistroPendienteSocialPort.generar(any(), any())).thenReturn("token-sin-nombre");

        ResultadoLoginSocial resultado = service().iniciarSesion(command());

        assertThat(((ResultadoLoginSocial.RegistroPendiente) resultado).fullName())
                .isEqualTo("sinnombre@renaser.dev");
    }

    /**
     * §6.4: no vincula por email, ni siquiera silenciosamente reteniendo un registro pendiente
     * para un email que ya tiene cuenta — devuelve {@code CuentaExistenteSinVinculo} en vez de
     * eso. Sigue sin abrir sesion: eso es lo unico que importa para la seguridad.
     */
    @Test
    void identidadNuevaConEmailDeUnUsuarioExistenteNoVinculaNiAbreSesion() {
        UserId existente = UserId.of(UUID.randomUUID());
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-otro", "yaexiste@renaser.dev", true, "Alguien"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadUserPort.byEmail(new Email("yaexiste@renaser.dev"))).thenReturn(Optional.of(usuario(existente)));

        ResultadoLoginSocial resultado = service().iniciarSesion(command());

        assertThat(resultado).isInstanceOf(ResultadoLoginSocial.CuentaExistenteSinVinculo.class);
        assertThat(((ResultadoLoginSocial.CuentaExistenteSinVinculo) resultado).proveedor())
                .isEqualTo(ProveedorIdentidad.GOOGLE);
        verify(tokenRegistroPendienteSocialPort, never()).generar(any(), any());
    }

    /**
     * A-7: volver a tocar "Continuar con Google" mientras un admin no decide NO crea una segunda
     * solicitud ni devuelve un error — devuelve la que ya existe, encontrada por
     * {@code (proveedor, sujeto)}.
     */
    @Test
    void identidadConSolicitudPendienteDevuelveSolicitudEnRevisionSinRetenerNada() {
        AccountRequest previa = solicitudSocialPendiente("google-sub-espera", "espera@renaser.dev");
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-espera", "espera@renaser.dev", true, "Espera"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(any(), any())).thenReturn(Optional.empty());
        when(loadAccountRequestPort.porOrigenSocial(
                new OrigenSocial(ProveedorIdentidad.GOOGLE, "google-sub-espera")))
                .thenReturn(Optional.of(previa));

        ResultadoLoginSocial resultado = service().iniciarSesion(command());

        assertThat(resultado).isInstanceOf(ResultadoLoginSocial.SolicitudEnRevision.class);
        assertThat(((ResultadoLoginSocial.SolicitudEnRevision) resultado).solicitudId()).isEqualTo(previa.id());
        verify(tokenRegistroPendienteSocialPort, never()).generar(any(), any());
        // El correo no se consulta siquiera: la identidad ya quedo resuelta por (proveedor, sujeto).
        verify(loadUserPort, never()).byEmail(any());
    }

    /**
     * Una solicitud social YA RECHAZADA no bloquea para siempre: la persona puede volver a
     * intentarlo y se le retiene un registro pendiente nuevo. Solo las PENDIENTES cortan el
     * camino.
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
        when(tokenRegistroPendienteSocialPort.generar(any(), any())).thenReturn("token-reintento");

        ResultadoLoginSocial resultado = service().iniciarSesion(command());

        assertThat(resultado).isInstanceOf(ResultadoLoginSocial.RegistroPendiente.class);
    }

    @Test
    void emailNoVerificadoPorElProveedorEsRechazado() {
        when(verificadorGoogle.verificar(any()))
                .thenReturn(new IdentidadVerificada("google-sub-3", "sinverificar@renaser.dev", false, "Alguien"));

        assertThatThrownBy(() -> service().iniciarSesion(command()))
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

        assertThatThrownBy(() -> service().iniciarSesion(command()))
                .isInstanceOf(IllegalStateException.class);
    }
}
