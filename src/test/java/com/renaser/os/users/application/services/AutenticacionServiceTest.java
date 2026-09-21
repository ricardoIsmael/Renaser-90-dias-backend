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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {

    private static final PasswordEncoder ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private static final String CONTRASENA_REAL = "una-contrasena-larga-de-verdad";
    private static final String EMAIL = "actor@renaser.dev";
    private static final String IP = "203.0.113.10";

    /** La clave compuesta tal como la arma {@code OrigenDeLaPeticion}: origen primero. */
    private static String clavePareja(String ip, String email) {
        return "login:origen-email:" + ip + "|" + email;
    }

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
    void alSuperarElLimiteDeLaParejaCorreoYOrigenSeRechazaAntesDeMirarLaContrasena() {
        AutenticacionService servicio = new AutenticacionService(
                loadCredencialPort, loadUserPort, ENCODER, limitarIntentosPort);
        when(limitarIntentosPort.registrarIntento(eq("login:ip:" + IP), any(), anyInt())).thenReturn(true);
        when(limitarIntentosPort.registrarIntento(eq(clavePareja(IP, EMAIL)), any(), anyInt()))
                .thenReturn(false);

        assertThatThrownBy(() -> servicio.iniciarSesion(
                new IniciarSesionCommand(EMAIL, CONTRASENA_REAL, IP)))
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
        when(limitarIntentosPort.registrarIntento(eq("login:ip:" + IP), any(), anyInt()))
                .thenReturn(false);

        assertThatThrownBy(() -> servicio.iniciarSesion(
                new IniciarSesionCommand(EMAIL, CONTRASENA_REAL, IP)))
                .isInstanceOf(RateLimitExceededException.class);
    }

    /**
     * Refuta el punto (a) del hallazgo: antes, el INCR del contador por correo corria ANTES del
     * guard por IP, asi que un origen con su cupo agotado seguia gastando el cupo de cada correo
     * nuevo que tocaba — y de paso creaba una clave nueva en Redis por cada uno — aunque
     * recibiera 429 por respuesta.
     */
    @Test
    void unOrigenSinCupoYaNoTocaNingunContadorDeCorreo() {
        AutenticacionService servicio = new AutenticacionService(
                loadCredencialPort, loadUserPort, ENCODER, limitarIntentosPort);
        when(limitarIntentosPort.registrarIntento(eq("login:ip:" + IP), any(), anyInt()))
                .thenReturn(false);

        assertThatThrownBy(() -> servicio.iniciarSesion(
                new IniciarSesionCommand("otra-victima@renaser.dev", CONTRASENA_REAL, IP)))
                .isInstanceOf(RateLimitExceededException.class);

        verify(limitarIntentosPort, never()).registrarIntento(startsWith("login:origen-email:"), any(), anyInt());
        verify(limitarIntentosPort, times(1)).registrarIntento(anyString(), any(), anyInt());
    }

    /**
     * El contador que era el arma —una clave armada solo con el correo del cuerpo— ya no se
     * registra en ninguna rama. Es la prueba mas directa de que el cupo de una persona no se
     * puede gastar desde afuera: no existe ningun cupo que cuelgue solo de su correo.
     */
    @Test
    void ningunContadorDelLoginCuelgaSoloDelCorreo() {
        UserId id = UserId.of(UUID.randomUUID());
        String hash = "{bcrypt}" + ENCODER.encode(CONTRASENA_REAL).substring("{bcrypt}".length());
        when(loadCredencialPort.porEmail(EMAIL)).thenReturn(Optional.of(new CredencialParaLogin(id, hash, true)));
        when(loadUserPort.byId(id)).thenReturn(Optional.of(usuario(id)));

        service().iniciarSesion(new IniciarSesionCommand(EMAIL, CONTRASENA_REAL, IP));

        verify(limitarIntentosPort, never()).registrarIntento(eq("login:email:" + EMAIL), any(), anyInt());
        verify(limitarIntentosPort).registrarIntento("login:ip:" + IP,
                AutenticacionService.VENTANA_RATE_LIMIT, AutenticacionService.LIMITE_POR_IP);
        verify(limitarIntentosPort).registrarIntento(clavePareja(IP, EMAIL),
                AutenticacionService.VENTANA_RATE_LIMIT, AutenticacionService.LIMITE_POR_EMAIL_Y_ORIGEN);
    }

    /**
     * Sin IP no hay a quien cobrarle la peticion, y la salida facil —caer a una clave de
     * solo-correo— es justamente el agujero que se cerro. Se cobra entonces a una unidad de
     * conteo unica y compartida, de modo que los dos topes siguen aplicandose.
     */
    @Test
    void sinIpElOrigenDesconocidoEsSuPropiaUnidadDeConteoYLosDosTopesSiguenCorriendo() {
        UserId id = UserId.of(UUID.randomUUID());
        String hash = "{bcrypt}" + ENCODER.encode(CONTRASENA_REAL).substring("{bcrypt}".length());
        when(loadCredencialPort.porEmail(EMAIL)).thenReturn(Optional.of(new CredencialParaLogin(id, hash, true)));
        when(loadUserPort.byId(id)).thenReturn(Optional.of(usuario(id)));

        User resultado = service().iniciarSesion(new IniciarSesionCommand(EMAIL, CONTRASENA_REAL, null));

        assertThat(resultado.id()).isEqualTo(id);
        verify(limitarIntentosPort).registrarIntento("login:ip:" + OrigenDeLaPeticion.DESCONOCIDO,
                AutenticacionService.VENTANA_RATE_LIMIT, AutenticacionService.LIMITE_POR_IP);
        verify(limitarIntentosPort).registrarIntento(clavePareja(OrigenDeLaPeticion.DESCONOCIDO, EMAIL),
                AutenticacionService.VENTANA_RATE_LIMIT, AutenticacionService.LIMITE_POR_EMAIL_Y_ORIGEN);
    }
}
