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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    /**
     * E-153. Sin espera entre envios, los 5 codigos de la hora se podian pedir en cinco segundos
     * apretando "reenviar", y la persona quedaba bloqueada una hora sin entender por que. Paso de
     * verdad: tres aprendices llegaron a 16, 15 y 10 pedidos la noche del 6 de septiembre.
     */
    @Test
    @DisplayName("E-153: dentro de los 30 segundos del envio anterior, rebota y no manda otro codigo")
    void enviarRechazaSiNoPasaronLos30Segundos() {
        when(limitarSolicitudesResetPort.registrarIntento(eq("email-verification:espera:alguien@renaser.dev"),
                eq(VerificacionEmailService.ESPERA_ENTRE_ENVIOS), eq(1))).thenReturn(false);

        assertThatThrownBy(() -> service.enviar(new EnviarCodigoVerificacionEmailCommand("alguien@renaser.dev", null)))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("30");

        verify(codigoVerificacionEmailPort, never()).generarCodigo(any(), any());
        verify(enviarEmailPort, never()).enviarCodigoVerificacionEmail(any(), any());
    }

    /**
     * El orden importa: si la espera se revisara despues del limite por hora, cada clic impaciente
     * gastaria uno de los 5 envios antes de rebotar, y el remedio provocaria el bloqueo que viene
     * a evitar.
     */
    @Test
    @DisplayName("E-153: rebotar por la espera NO gasta uno de los 5 envios de la hora")
    void laEsperaSeRevisaAntesQueElLimitePorHora() {
        when(limitarSolicitudesResetPort.registrarIntento(eq("email-verification:espera:alguien@renaser.dev"),
                any(), eq(1))).thenReturn(false);

        assertThatThrownBy(() -> service.enviar(new EnviarCodigoVerificacionEmailCommand("alguien@renaser.dev",
                "1.2.3.4"))).isInstanceOf(RateLimitExceededException.class);

        verify(limitarSolicitudesResetPort, never()).registrarIntento(
                eq("email-verification:email:alguien@renaser.dev"), any(), anyInt());
        verify(limitarSolicitudesResetPort, never()).registrarIntento(
                eq("email-verification:ip:1.2.3.4"), any(), anyInt());
    }

    /**
     * Regresion del hallazgo del prefijo IPv6: aca cada intento cuesta un correo real, y con la
     * direccion entera como clave un abonado con su /64 propio gastaba esa cuota sobre tantos
     * correos de terceros como quisiera.
     */
    @Test
    @DisplayName("dos direcciones del mismo /64 gastan el MISMO cupo de envios por IP")
    void elLimitePorIpCuentaPorPrefijoEnIpv6() {
        when(codigoVerificacionEmailPort.generarCodigo(eq("alguien@renaser.dev"), any())).thenReturn("123456");

        service.enviar(new EnviarCodigoVerificacionEmailCommand("alguien@renaser.dev",
                "2803:9810:6075:9310::1"));
        service.enviar(new EnviarCodigoVerificacionEmailCommand("alguien@renaser.dev",
                "2803:9810:6075:9310:1:2:3:4"));

        verify(limitarSolicitudesResetPort, org.mockito.Mockito.times(2)).registrarIntento(
                eq("email-verification:ip:2803:9810:6075:9310::/64"), any(),
                eq(VerificacionEmailService.LIMITE_POR_IP));
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

    /**
     * Auditoria 2026-09-21 (renaser-users-clave-redis-correo-sin-normalizar). Los dos frenos por
     * buzon —la espera de 30 s y los 5 por hora— se contaban sobre el String crudo del cuerpo
     * HTTP. La parte de dominio de una direccion es insensible a mayusculas para la ENTREGA (los
     * MX se resuelven por DNS, que no distingue caja), asi que {@code alguien@renaser.dev} y
     * {@code Alguien@RENASER.dev} eran dos contadores en Redis y un solo buzon en el servidor de
     * correo: cada variacion estrenaba su cupo entero. Con 'gmail.com' son 2^8 = 256 spellings.
     *
     * <p>El contador de este test es el de verdad —una clave, un numero, un maximo—, para que lo
     * que quede clavado no sea un literal sino el invariante: las dos escrituras tienen que caer
     * en la MISMA cuenta, y el correo tiene que salir a la direccion canonica.
     */
    @Test
    @DisplayName("variar las mayusculas del dominio NO estrena contador propio: la espera se aplica igual")
    void variarLasMayusculasNoEstrenaContadorPropio() {
        Map<String, Integer> contadores = new HashMap<>();
        when(limitarSolicitudesResetPort.registrarIntento(any(), any(), anyInt())).thenAnswer(invocacion -> {
            String clave = invocacion.getArgument(0);
            int maximo = invocacion.getArgument(2);
            return contadores.merge(clave, 1, Integer::sum) <= maximo;
        });
        when(codigoVerificacionEmailPort.generarCodigo(eq("alguien@renaser.dev"), any())).thenReturn("123456");

        service.enviar(new EnviarCodigoVerificacionEmailCommand("alguien@renaser.dev", null));

        assertThatThrownBy(() -> service.enviar(
                new EnviarCodigoVerificacionEmailCommand("Alguien@RENASER.dev", null)))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("30");

        assertThat(contadores).containsOnlyKeys("email-verification:espera:alguien@renaser.dev",
                "email-verification:email:alguien@renaser.dev");
        verify(enviarEmailPort, times(1)).enviarCodigoVerificacionEmail("alguien@renaser.dev", "123456");
        verify(enviarEmailPort, never()).enviarCodigoVerificacionEmail(eq("Alguien@RENASER.dev"), any());
    }

    /**
     * La otra mitad del mismo arreglo, y la razon por la que enviar() y confirmar() se tocan
     * juntos: el codigo se guarda bajo la clave canonica, asi que si confirmar() buscara bajo el
     * texto crudo, a quien escribiera su correo con otra caja no le serviria nunca su codigo.
     */
    @Test
    @DisplayName("confirmar encuentra el codigo aunque el correo vuelva con otras mayusculas")
    void confirmarUsaLaMismaClaveCanonicaQueEnviar() {
        when(codigoVerificacionEmailPort.verificarCodigo("alguien@renaser.dev", "123456",
                VerificacionEmailService.MAX_INTENTOS)).thenReturn(true);
        when(tokenVerificacionEmailPort.generar("alguien@renaser.dev",
                VerificacionEmailService.VIGENCIA_TOKEN_VERIFICACION)).thenReturn("token-opaco");

        var resultado = service.confirmar(
                new ConfirmarCodigoVerificacionEmailCommand("Alguien@RENASER.dev", "123456"));

        assertThat(resultado.verificationToken()).isEqualTo("token-opaco");
    }

    /**
     * Efecto de borde del arreglo, escrito a proposito: canonizar con el tipo del dominio tambien
     * valida la forma. Una direccion que {@code @Email} deja pasar pero que el dominio no acepta
     * —un dominio sin punto— ya no termina en el adaptador SMTP con 202; rebota como 400
     * ({@code IllegalArgumentException} -> GlobalExceptionHandler) sin tocar Redis ni el correo.
     */
    @Test
    @DisplayName("una direccion que el dominio no acepta rebota antes de tocar Redis o el correo")
    void enviarRechazaUnaDireccionQueElDominioNoAcepta() {
        assertThatThrownBy(() -> service.enviar(
                new EnviarCodigoVerificacionEmailCommand("alguien@localhost", null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(limitarSolicitudesResetPort, never()).registrarIntento(any(), any(), anyInt());
        verify(enviarEmailPort, never()).enviarCodigoVerificacionEmail(any(), any());
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
