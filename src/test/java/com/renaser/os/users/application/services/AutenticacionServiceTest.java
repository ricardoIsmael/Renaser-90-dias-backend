package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CredencialesInvalidasException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase.IniciarSesionCommand;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {

    private static final PasswordEncoder ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private static final String CONTRASENA_REAL = "una-contrasena-larga-de-verdad";

    @Mock
    private LoadCredencialPort loadCredencialPort;
    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private LimitarSolicitudesResetPort limitarIntentosPort;

    /** Limitador permisivo: cada prueba que quiera probar el tope lo dice explicitamente. */
    private AutenticacionService service() {
        lenient().when(limitarIntentosPort.registrarIntento(anyString(), any(), anyInt())).thenReturn(true);
        return new AutenticacionService(loadCredencialPort, loadUserPort, ENCODER, limitarIntentosPort);
    }

    private static User usuario(UserId id) {
        return User.rehydrate(id, new Email("actor@renaser.dev"), UserRole.TRAINEE,
                com.renaser.os.users.api.UserStatus.ACTIVE, "Actor de Prueba", null, null, null, null);
    }

    @Test
    void loginConEmailYContrasenaCorrectosDevuelveElUsuario() {
        UserId id = UserId.of(UUID.randomUUID());
        String hash = "{bcrypt}" + ENCODER.encode(CONTRASENA_REAL).substring("{bcrypt}".length());
        when(loadCredencialPort.porEmail("actor@renaser.dev"))
                .thenReturn(Optional.of(new CredencialParaLogin(id, hash, true)));
        when(loadUserPort.byId(id)).thenReturn(Optional.of(usuario(id)));

        User resultado = service().iniciarSesion(new IniciarSesionCommand("actor@renaser.dev", CONTRASENA_REAL, "203.0.113.10"));

        assertThat(resultado.id()).isEqualTo(id);
    }

    @Test
    void loginConContrasenaIncorrectaRechazado() {
        UserId id = UserId.of(UUID.randomUUID());
        String hash = ENCODER.encode(CONTRASENA_REAL);
        when(loadCredencialPort.porEmail("actor@renaser.dev"))
                .thenReturn(Optional.of(new CredencialParaLogin(id, hash, true)));

        assertThatThrownBy(() -> service().iniciarSesion(
                new IniciarSesionCommand("actor@renaser.dev", "otra-contrasena-distinta", "203.0.113.10")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void loginConEmailInexistenteRechazadoConElMismoMensajeQueUnaContrasenaIncorrecta() {
        when(loadCredencialPort.porEmail("fantasma@renaser.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().iniciarSesion(
                new IniciarSesionCommand("fantasma@renaser.dev", CONTRASENA_REAL, "203.0.113.10")))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Email o contrasena incorrectos");
    }

    @Test
    void loginConCuentaSuspendidaRechazadoAunqueLaContrasenaSeaCorrecta() {
        UserId id = UserId.of(UUID.randomUUID());
        String hash = ENCODER.encode(CONTRASENA_REAL);
        when(loadCredencialPort.porEmail("actor@renaser.dev"))
                .thenReturn(Optional.of(new CredencialParaLogin(id, hash, false)));

        assertThatThrownBy(() -> service().iniciarSesion(
                new IniciarSesionCommand("actor@renaser.dev", CONTRASENA_REAL, "203.0.113.10")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void loginDeCuentaSoloDeProveedorSocialRechazado() {
        UserId id = UserId.of(UUID.randomUUID());
        when(loadCredencialPort.porEmail("solo-google@renaser.dev"))
                .thenReturn(Optional.of(new CredencialParaLogin(id, null, true)));

        assertThatThrownBy(() -> service().iniciarSesion(
                new IniciarSesionCommand("solo-google@renaser.dev", CONTRASENA_REAL, "203.0.113.10")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    /**
     * No es un chequeo de tiempo real (seria un test fragil) — confirma que la comparacion
     * BCrypt se ejecuta igual aunque el email no exista, verificando que loadUserPort nunca se
     * llama en ese camino (si se hubiera saltado la comparacion, el resto de la logica seria
     * distinta de la del camino "existe pero no coincide").
     */
    @Test
    void emailInexistenteNuncaConsultaLoadUserPort() {
        when(loadCredencialPort.porEmail("fantasma@renaser.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().iniciarSesion(
                new IniciarSesionCommand("fantasma@renaser.dev", CONTRASENA_REAL, "203.0.113.10")))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(loadUserPort, org.mockito.Mockito.never()).byId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void alSuperarElLimitePorCorreoSeRechazaAntesDeMirarLaContrasena() {
        AutenticacionService servicio = new AutenticacionService(
                loadCredencialPort, loadUserPort, ENCODER, limitarIntentosPort);
        when(limitarIntentosPort.registrarIntento(eq("login:email:actor@renaser.dev"), any(), anyInt()))
                .thenReturn(false);

        assertThatThrownBy(() -> servicio.iniciarSesion(
                new IniciarSesionCommand("actor@renaser.dev", CONTRASENA_REAL, "203.0.113.10")))
                .isInstanceOf(RateLimitExceededException.class);

        // Lo que de verdad prueba que corta ANTES: nunca se pregunto por la credencial. Si el
        // limite se aplicara despues de comparar, un atacante ya habria averiguado si el correo
        // existe por el tiempo de respuesta, que es justo lo que HASH_SENUELO evita.
        verifyNoInteractions(loadCredencialPort, loadUserPort);
    }

    @Test
    void alSuperarElLimitePorIpTambienSeRechaza() {
        AutenticacionService servicio = new AutenticacionService(
                loadCredencialPort, loadUserPort, ENCODER, limitarIntentosPort);
        when(limitarIntentosPort.registrarIntento(eq("login:email:actor@renaser.dev"), any(), anyInt()))
                .thenReturn(true);
        when(limitarIntentosPort.registrarIntento(eq("login:ip:203.0.113.10"), any(), anyInt()))
                .thenReturn(false);

        assertThatThrownBy(() -> servicio.iniciarSesion(
                new IniciarSesionCommand("actor@renaser.dev", CONTRASENA_REAL, "203.0.113.10")))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void sinIpSoloSeAplicaElLimitePorCorreo() {
        UserId id = UserId.of(UUID.randomUUID());
        String hash = "{bcrypt}" + ENCODER.encode(CONTRASENA_REAL).substring("{bcrypt}".length());
        AutenticacionService servicio = new AutenticacionService(
                loadCredencialPort, loadUserPort, ENCODER, limitarIntentosPort);
        when(limitarIntentosPort.registrarIntento(eq("login:email:actor@renaser.dev"), any(), anyInt()))
                .thenReturn(true);
        when(loadCredencialPort.porEmail("actor@renaser.dev"))
                .thenReturn(Optional.of(new CredencialParaLogin(id, hash, true)));
        when(loadUserPort.byId(id)).thenReturn(Optional.of(usuario(id)));

        User resultado = servicio.iniciarSesion(
                new IniciarSesionCommand("actor@renaser.dev", CONTRASENA_REAL, null));

        assertThat(resultado.id()).isEqualTo(id);
        verify(limitarIntentosPort, never()).registrarIntento(startsWith("login:ip:"), any(), anyInt());
    }
}
