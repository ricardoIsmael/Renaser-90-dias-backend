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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    /**
     * Regresion del hallazgo del prefijo IPv6. Este es el contador donde mas dolia: es el UNICO
     * control de `check-email` y `exists`, que responden con un booleano si un correo ya tiene
     * cuenta. Con la direccion entera como clave, un cliente IPv6 con su /64 propio estrenaba
     * contador en cada peticion y el tope de 120/h no disparaba nunca — enumeracion del padron
     * sin techo, sin falsificar una sola cabecera.
     */
    @Nested
    @DisplayName("el contador por IP cuenta por prefijo, no por direccion")
    class ContadorPorPrefijo {

        @Test
        @DisplayName("dos direcciones distintas del mismo /64 gastan el MISMO contador")
        void dosDireccionesDelMismoPrefijoCompartenContador() {
            conMargenDeCuota();

            service.estaRegistrado(EMAIL, "2803:9810:6075:9310:c63b:3904:e158:3228");
            service.estaRegistrado(EMAIL, "2803:9810:6075:9310:1:2:3:4");

            verify(limitarSolicitudesPort, times(2)).registrarIntento(
                    eq("email-check:ip:2803:9810:6075:9310::/64"), any(), anyInt());
        }

        @Test
        @DisplayName("dos formas de escribir la misma direccion gastan el MISMO contador")
        void dosFormasDeLaMismaDireccionCompartenContador() {
            conMargenDeCuota();

            service.estaRegistrado(EMAIL, "2803:9810:0:0::1");
            service.estaRegistrado(EMAIL, "2803:9810::1");

            verify(limitarSolicitudesPort, times(2)).registrarIntento(
                    eq("email-check:ip:2803:9810::/64"), any(), anyInt());
        }

        @Test
        @DisplayName("dos /64 distintos siguen en contadores distintos: agregar no junta a todo el mundo")
        void dosPrefijosDistintosNoCompartenContador() {
            conMargenDeCuota();

            service.estaRegistrado(EMAIL, "2001:db8:1:1::1");
            service.estaRegistrado(EMAIL, "2001:db8:1:2::1");

            verify(limitarSolicitudesPort).registrarIntento(
                    eq("email-check:ip:2001:db8:1:1::/64"), any(), anyInt());
            verify(limitarSolicitudesPort).registrarIntento(
                    eq("email-check:ip:2001:db8:1:2::/64"), any(), anyInt());
        }

        @Test
        @DisplayName("IPv4 no cambia: la clave es exactamente la misma que antes del arreglo")
        void ipv4CuentaIgualQueAntes() {
            conMargenDeCuota();

            service.estaRegistrado(EMAIL, IP);

            verify(limitarSolicitudesPort).registrarIntento(eq("email-check:ip:" + IP), any(), anyInt());
        }

        @Test
        @DisplayName("dos IPv4 distintas siguen en contadores distintos")
        void dosIpv4DistintasNoCompartenContador() {
            conMargenDeCuota();

            service.estaRegistrado(EMAIL, "203.0.113.7");
            service.estaRegistrado(EMAIL, "203.0.113.8");

            verify(limitarSolicitudesPort).registrarIntento(eq("email-check:ip:203.0.113.7"), any(), anyInt());
            verify(limitarSolicitudesPort).registrarIntento(eq("email-check:ip:203.0.113.8"), any(), anyInt());
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

        /**
         * Regresion del hallazgo del nombre JNDI. Este es el camino publico completo
         * (POST /api/v1/account-requests/verify-email, sin sesion): antes, la parte de dominio de
         * estos correos bajaba entera hasta el resolvedor y de ahi al interprete JNDI, que salia a
         * conectarse al host y al puerto que eligio quien escribio el correo. Lo que se exige aca
         * es que el puerto NO se llame: si se llamara, el destino de la conexion volveria a
         * elegirlo el cliente.
         */
        @ParameterizedTest
        @ValueSource(strings = {
                "a@ldap://baliza.ejemplo-del-atacante.tld:1389/x",
                "a@ldaps://baliza.ejemplo-del-atacante.tld:8443/x",
                "a@dns://baliza.ejemplo-del-atacante.tld:5353/x.y",
                "a@ldap://127.0.0.1:6379/aa",
                "a@ldap://169.254.169.254:80/aa"
        })
        @DisplayName("un dominio con forma de URL se responde 'formato' y no llega al resolvedor")
        void dominioConFormaDeUrlNoLlegaAlResolvedor(String conFormaDeUrl) {
            var resultado = service.verificar(conFormaDeUrl);

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
