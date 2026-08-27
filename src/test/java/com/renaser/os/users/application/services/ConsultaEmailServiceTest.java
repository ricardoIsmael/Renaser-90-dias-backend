package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.application.ports.in.accountrequest.VerificarDominioEmailUseCase.MotivoNoEntregable;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.ResolverMxPort;
import com.renaser.os.users.application.ports.out.accountrequest.ResolverMxPort.ResultadoMx;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Unit puro: los cuatro puertos mockeados, sin Spring, sin base, sin DNS real. */
@ExtendWith(MockitoExtension.class)
class ConsultaEmailServiceTest {

    private static final String EMAIL = "aprendiz@ejemplo.test";
    private static final String IP = "203.0.113.7";

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private LoadAccountRequestPort loadAccountRequestPort;
    @Mock
    private LimitarSolicitudesResetPort limitarSolicitudesPort;
    @Mock
    private ResolverMxPort resolverMxPort;

    private ConsultaEmailService service;

    @BeforeEach
    void setUp() {
        service = new ConsultaEmailService(loadUserPort, loadAccountRequestPort, limitarSolicitudesPort,
                resolverMxPort);
    }

    private void conMargenDeCuota() {
        given(limitarSolicitudesPort.registrarIntento(anyString(), any(), anyInt())).willReturn(true);
    }

    @Nested
    @DisplayName("estaRegistrado")
    class EstaRegistrado {

        @Test
        @DisplayName("un correo con usuario ya registrado da true sin llegar a mirar las solicitudes")
        void detectaUsuarioExistente(@Mock User usuarioExistente) {
            conMargenDeCuota();
            given(loadUserPort.byEmail(new Email(EMAIL))).willReturn(Optional.of(usuarioExistente));

            assertThat(service.estaRegistrado(EMAIL, IP)).isTrue();
            // Corto circuito: si ya hay usuario, la segunda consulta ni se hace.
            verify(loadAccountRequestPort, never()).existePorEmail(any());
        }

        @Test
        @DisplayName("una solicitud pendiente tambien ocupa el correo, aunque no haya usuario todavia")
        void detectaSolicitudPendiente() {
            conMargenDeCuota();
            given(loadUserPort.byEmail(new Email(EMAIL))).willReturn(Optional.empty());
            given(loadAccountRequestPort.existePorEmail(new Email(EMAIL))).willReturn(true);

            assertThat(service.estaRegistrado(EMAIL, IP)).isTrue();
        }

        @Test
        @DisplayName("sin usuario ni solicitud, el correo esta libre")
        void correoLibre() {
            conMargenDeCuota();
            given(loadUserPort.byEmail(new Email(EMAIL))).willReturn(Optional.empty());
            given(loadAccountRequestPort.existePorEmail(new Email(EMAIL))).willReturn(false);

            assertThat(service.estaRegistrado(EMAIL, IP)).isFalse();
        }

        @Test
        @DisplayName("el correo se normaliza antes de consultar: mayusculas y espacios no crean un caso nuevo")
        void normalizaAntesDeConsultar() {
            conMargenDeCuota();
            given(loadUserPort.byEmail(new Email(EMAIL))).willReturn(Optional.empty());
            given(loadAccountRequestPort.existePorEmail(new Email(EMAIL))).willReturn(true);

            assertThat(service.estaRegistrado("  APRENDIZ@Ejemplo.Test  ", IP)).isTrue();
        }

        @Test
        @DisplayName("un correo mal formado se rechaza SIN tocar la base: media defensa contra el sondeo")
        void formatoInvalidoNoConsultaLaBase() {
            conMargenDeCuota();

            assertThatThrownBy(() -> service.estaRegistrado("no-es-un-correo", IP))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(loadUserPort, never()).byEmail(any());
            verify(loadAccountRequestPort, never()).existePorEmail(any());
        }

        @Test
        @DisplayName("pasado el limite por IP se corta antes de consultar, no despues")
        void cortaAlSuperarElLimitePorIp() {
            given(limitarSolicitudesPort.registrarIntento("email-check:ip:" + IP,
                    ConsultaEmailService.VENTANA_RATE_LIMIT,
                    ConsultaEmailService.LIMITE_CONSULTAS_POR_IP)).willReturn(false);

            assertThatThrownBy(() -> service.estaRegistrado(EMAIL, IP))
                    .isInstanceOf(RateLimitExceededException.class);

            verify(loadUserPort, never()).byEmail(any());
        }

        @Test
        @DisplayName("sin IP conocida la consulta sigue funcionando: no se puede contar, no se bloquea")
        void sinIpNoSeLimita() {
            given(loadUserPort.byEmail(new Email(EMAIL))).willReturn(Optional.empty());
            given(loadAccountRequestPort.existePorEmail(new Email(EMAIL))).willReturn(false);

            assertThat(service.estaRegistrado(EMAIL, null)).isFalse();

            verify(limitarSolicitudesPort, never()).registrarIntento(anyString(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("verificar dominio")
    class VerificarDominio {

        @Test
        @DisplayName("con MX el dominio puede recibir correo")
        void conMx() {
            given(resolverMxPort.consultar("ejemplo.test")).willReturn(ResultadoMx.TIENE_MX);

            var resultado = service.verificar(EMAIL);

            assertThat(resultado.entregable()).isTrue();
            assertThat(resultado.motivo()).isNull();
        }

        @Test
        @DisplayName("sin MX no es entregable, y se dice por que")
        void sinMx() {
            given(resolverMxPort.consultar("ejemplo.test")).willReturn(ResultadoMx.SIN_MX);

            var resultado = service.verificar(EMAIL);

            assertThat(resultado.entregable()).isFalse();
            assertThat(resultado.motivo()).isEqualTo(MotivoNoEntregable.SIN_MX);
        }

        @Test
        @DisplayName("un dominio inexistente se distingue de uno sin MX")
        void dominioInexistente() {
            given(resolverMxPort.consultar("ejemplo.test")).willReturn(ResultadoMx.DOMINIO_INEXISTENTE);

            assertThat(service.verificar(EMAIL).motivo()).isEqualTo(MotivoNoEntregable.DOMINIO_INEXISTENTE);
        }

        @Test
        @DisplayName("si el DNS no responde, NO se convierte en un 'no': queda indeterminado")
        void dnsCaidoNoEsUnNo() {
            given(resolverMxPort.consultar("ejemplo.test")).willReturn(ResultadoMx.INDETERMINADO);

            var resultado = service.verificar(EMAIL);

            assertThat(resultado.entregable()).isNull();
            assertThat(resultado.motivo()).isNull();
        }

        @Test
        @DisplayName("un correo mal formado responde 'formato' en vez de explotar, y no consulta DNS")
        void formatoInvalido() {
            var resultado = service.verificar("no-es-un-correo");

            assertThat(resultado.entregable()).isFalse();
            assertThat(resultado.motivo()).isEqualTo(MotivoNoEntregable.FORMATO);
            verify(resolverMxPort, never()).consultar(anyString());
        }

        @Test
        @DisplayName("verificar el dominio no consume cuota: no manda correo ni toca la base")
        void noConsumeCuota() {
            given(resolverMxPort.consultar("ejemplo.test")).willReturn(ResultadoMx.TIENE_MX);

            service.verificar(EMAIL);

            verify(limitarSolicitudesPort, never()).registrarIntento(anyString(), any(), anyInt());
        }
    }
}
