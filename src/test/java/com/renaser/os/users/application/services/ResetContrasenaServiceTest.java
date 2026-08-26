package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.TokenResetInvalidoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.autenticacion.CerrarTodasLasSesionesUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarResetContrasenaUseCase.ConfirmarResetContrasenaCommand;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarResetContrasenaUseCase.SolicitarResetContrasenaCommand;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenResetContrasenaPort;
import com.renaser.os.users.domain.model.user.Credencial;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetContrasenaServiceTest {

    private static final String EMAIL = "actor@renaser.dev";
    private static final String IP = "203.0.113.7";

    @Mock
    private LoadCredencialPort loadCredencialPort;
    @Mock
    private SaveCredencialPort saveCredencialPort;
    @Mock
    private TokenResetContrasenaPort tokenResetContrasenaPort;
    @Mock
    private LimitarSolicitudesResetPort limitarSolicitudesResetPort;
    @Mock
    private EnviarEmailPort enviarEmailPort;
    @Mock
    private CerrarTodasLasSesionesUseCase cerrarTodasLasSesionesUseCase;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private Clock clock;

    private ResetContrasenaService service() {
        return new ResetContrasenaService(loadCredencialPort, saveCredencialPort, tokenResetContrasenaPort,
                limitarSolicitudesResetPort, enviarEmailPort, cerrarTodasLasSesionesUseCase, passwordEncoder, clock);
    }

    private void permitirRateLimit() {
        when(limitarSolicitudesResetPort.registrarIntento(anyString(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(true);
    }

    @Test
    void solicitarConCuentaExistenteYConContrasenaGeneraTokenYEnviaCorreo() {
        permitirRateLimit();
        UserId id = UserId.of(UUID.randomUUID());
        when(loadCredencialPort.porEmail(EMAIL)).thenReturn(Optional.of(new CredencialParaLogin(id, "hash", true)));
        when(tokenResetContrasenaPort.generar(eq(id), any(Duration.class))).thenReturn("token-opaco");

        service().solicitar(new SolicitarResetContrasenaCommand(EMAIL, IP));

        verify(tokenResetContrasenaPort).generar(id, ResetContrasenaService.VIGENCIA_TOKEN);
        verify(enviarEmailPort).enviarResetContrasena(EMAIL, "token-opaco");
    }

    @Test
    void solicitarConEmailInexistenteNoGeneraTokenNiEnviaCorreoPeroNoLanzaExcepcion() {
        permitirRateLimit();
        when(loadCredencialPort.porEmail(EMAIL)).thenReturn(Optional.empty());

        service().solicitar(new SolicitarResetContrasenaCommand(EMAIL, IP));

        verify(tokenResetContrasenaPort, never()).generar(any(), any());
        verify(enviarEmailPort, never()).enviarResetContrasena(any(), any());
    }

    @Test
    void solicitarConCuentaSoloDeProveedorSocialNoGeneraTokenNiEnviaCorreo() {
        permitirRateLimit();
        UserId id = UserId.of(UUID.randomUUID());
        when(loadCredencialPort.porEmail(EMAIL)).thenReturn(Optional.of(new CredencialParaLogin(id, null, true)));

        service().solicitar(new SolicitarResetContrasenaCommand(EMAIL, IP));

        verify(tokenResetContrasenaPort, never()).generar(any(), any());
        verify(enviarEmailPort, never()).enviarResetContrasena(any(), any());
    }

    /**
     * Comportamiento observable identico exista o no la cuenta: en ningun caso se distingue con
     * una excepcion distinta. Ya cubierto arriba por separado; este test deja explicito que
     * ambos caminos terminan en el mismo tipo de resultado (sin excepcion).
     */
    @Test
    void solicitarConEmailExistenteSinContrasenaYConEmailInexistenteNoSonDistinguiblesPorExcepcion() {
        permitirRateLimit();
        when(loadCredencialPort.porEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatCode(() -> service().solicitar(new SolicitarResetContrasenaCommand(EMAIL, IP)))
                .doesNotThrowAnyException();
    }

    @Test
    void solicitarConLimiteDeTasaPorEmailExcedidoLanzaRateLimitExceeded() {
        when(limitarSolicitudesResetPort.registrarIntento(eq("email:" + EMAIL), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(false);

        assertThatThrownBy(() -> service().solicitar(new SolicitarResetContrasenaCommand(EMAIL, IP)))
                .isInstanceOf(RateLimitExceededException.class);

        verify(loadCredencialPort, never()).porEmail(any());
    }

    @Test
    void solicitarConLimiteDeTasaPorIpExcedidoLanzaRateLimitExceeded() {
        when(limitarSolicitudesResetPort.registrarIntento(eq("email:" + EMAIL), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(true);
        when(limitarSolicitudesResetPort.registrarIntento(eq("ip:" + IP), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(false);

        assertThatThrownBy(() -> service().solicitar(new SolicitarResetContrasenaCommand(EMAIL, IP)))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void solicitarSinIpOmiteElLimitePorIp() {
        when(limitarSolicitudesResetPort.registrarIntento(eq("email:" + EMAIL), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(true);
        when(loadCredencialPort.porEmail(EMAIL)).thenReturn(Optional.empty());

        service().solicitar(new SolicitarResetContrasenaCommand(EMAIL, null));

        // Solo el chequeo por email se ejecuta: sin IP no hay clave "ip:..." posible que registrar.
        verify(limitarSolicitudesResetPort, org.mockito.Mockito.times(1))
                .registrarIntento(anyString(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void confirmarConTokenValidoCambiaLaContrasenaYCierraTodasLasSesiones() {
        UserId id = UserId.of(UUID.randomUUID());
        Instant ahora = Instant.parse("2026-08-26T10:00:00Z");
        when(tokenResetContrasenaPort.consumir("token-valido")).thenReturn(Optional.of(id));
        when(passwordEncoder.encode("una-contrasena-nueva-larga")).thenReturn("{bcrypt}hasheada");
        when(clock.now()).thenReturn(ahora);

        service().confirmar(new ConfirmarResetContrasenaCommand("token-valido", "una-contrasena-nueva-larga"));

        verify(saveCredencialPort).guardar(id, new Credencial("{bcrypt}hasheada", ahora));
        verify(cerrarTodasLasSesionesUseCase).cerrarTodas(id);
    }

    @Test
    void confirmarConTokenVencidoRechazado() {
        when(tokenResetContrasenaPort.consumir("token-vencido")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().confirmar(
                new ConfirmarResetContrasenaCommand("token-vencido", "una-contrasena-nueva-larga")))
                .isInstanceOf(TokenResetInvalidoException.class);

        verify(saveCredencialPort, never()).guardar(any(), any());
        verify(cerrarTodasLasSesionesUseCase, never()).cerrarTodas(any());
    }

    /**
     * El puerto colapsa "ya vencio" y "ya se uso" en el mismo {@code Optional.empty()} (GETDEL
     * atomico): este test representa el segundo intento de confirmar con un token que la primera
     * llamada ya consumio — mismo resultado observable que el vencimiento.
     */
    @Test
    void confirmarConTokenYaUsadoRechazado() {
        when(tokenResetContrasenaPort.consumir("token-reusado")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().confirmar(
                new ConfirmarResetContrasenaCommand("token-reusado", "una-contrasena-nueva-larga")))
                .isInstanceOf(TokenResetInvalidoException.class);
    }

    @Test
    void confirmarConContrasenaNuevaMenorA12CaracteresRechazadaPorSelfValidating() {
        assertThatThrownBy(() -> new ConfirmarResetContrasenaCommand("token-cualquiera", "corta"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void confirmarConContrasenaNuevaMenorA12CaracteresNuncaLlegaAConsumirElToken() {
        assertThatThrownBy(() -> service().confirmar(new ConfirmarResetContrasenaCommand("token", "corta")))
                .isInstanceOf(ConstraintViolationException.class);

        verify(tokenResetContrasenaPort, never()).consumir(any());
    }
}
