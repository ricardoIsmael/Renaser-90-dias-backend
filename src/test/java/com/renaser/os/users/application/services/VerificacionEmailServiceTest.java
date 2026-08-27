package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CodigoVerificacionInvalidoException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarCodigoVerificacionEmailUseCase.ConfirmarCodigoVerificacionEmailCommand;
import com.renaser.os.users.application.ports.in.autenticacion.EnviarCodigoVerificacionEmailUseCase.EnviarCodigoVerificacionEmailCommand;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificacionEmailServiceTest {

    @Mock
    private CodigoVerificacionEmailPort codigoVerificacionEmailPort;
    @Mock
    private TokenVerificacionEmailPort tokenVerificacionEmailPort;
    @Mock
    private LimitarSolicitudesResetPort limitarSolicitudesResetPort;
    @Mock
    private EnviarEmailPort enviarEmailPort;

    private VerificacionEmailService service;

    @BeforeEach
    void setUp() {
        service = new VerificacionEmailService(codigoVerificacionEmailPort, tokenVerificacionEmailPort,
                limitarSolicitudesResetPort, enviarEmailPort);
        lenient().when(limitarSolicitudesResetPort.registrarIntento(any(), any(), anyInt())).thenReturn(true);
    }

    @Test
    void enviarGeneraElCodigoYLoMandaPorEmail() {
        when(codigoVerificacionEmailPort.generarCodigo("alguien@renaser.dev", VerificacionEmailService.VIGENCIA_CODIGO))
                .thenReturn("123456");

        service.enviar(new EnviarCodigoVerificacionEmailCommand("alguien@renaser.dev", "127.0.0.1"));

        verify(enviarEmailPort).enviarCodigoVerificacionEmail("alguien@renaser.dev", "123456");
    }

    @Test
    @DisplayName("supera el limite por email: no llega a generar ningun codigo")
    void enviarRechazaSiSuperaElLimitePorEmail() {
        when(limitarSolicitudesResetPort.registrarIntento(eq("email-verification:email:alguien@renaser.dev"), any(),
                anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service.enviar(new EnviarCodigoVerificacionEmailCommand("alguien@renaser.dev", null)))
                .isInstanceOf(RateLimitExceededException.class);

        verify(codigoVerificacionEmailPort, never()).generarCodigo(any(), any());
        verify(enviarEmailPort, never()).enviarCodigoVerificacionEmail(any(), any());
    }

    @Test
    @DisplayName("supera el limite por IP: no llega a generar ningun codigo")
    void enviarRechazaSiSuperaElLimitePorIp() {
        when(limitarSolicitudesResetPort.registrarIntento(eq("email-verification:ip:1.2.3.4"), any(), anyInt()))
                .thenReturn(false);

        assertThatThrownBy(
                () -> service.enviar(new EnviarCodigoVerificacionEmailCommand("alguien@renaser.dev", "1.2.3.4")))
                .isInstanceOf(RateLimitExceededException.class);

        verify(codigoVerificacionEmailPort, never()).generarCodigo(any(), any());
    }

    @Test
    void enviarSinIpNoRevisaElLimitePorIp() {
        service.enviar(new EnviarCodigoVerificacionEmailCommand("alguien@renaser.dev", null));

        verify(limitarSolicitudesResetPort, never()).registrarIntento(
                org.mockito.ArgumentMatchers.startsWith("email-verification:ip:"), any(), anyInt());
    }

    @Test
    void confirmarConElCodigoCorrectoDevuelveUnTokenDeVerificacion() {
        when(codigoVerificacionEmailPort.verificarCodigo("alguien@renaser.dev", "123456",
                VerificacionEmailService.MAX_INTENTOS)).thenReturn(true);
        when(tokenVerificacionEmailPort.generar("alguien@renaser.dev",
                VerificacionEmailService.VIGENCIA_TOKEN_VERIFICACION)).thenReturn("token-opaco");

        var resultado = service.confirmar(new ConfirmarCodigoVerificacionEmailCommand("alguien@renaser.dev", "123456"));

        assertThat(resultado.verificationToken()).isEqualTo("token-opaco");
    }

    @Test
    void confirmarConElCodigoIncorrectoLanzaExcepcionYNoEmiteToken() {
        when(codigoVerificacionEmailPort.verificarCodigo("alguien@renaser.dev", "000000",
                VerificacionEmailService.MAX_INTENTOS)).thenReturn(false);

        assertThatThrownBy(
                () -> service.confirmar(new ConfirmarCodigoVerificacionEmailCommand("alguien@renaser.dev", "000000")))
                .isInstanceOf(CodigoVerificacionInvalidoException.class);

        verify(tokenVerificacionEmailPort, never()).generar(any(), any());
    }
}
