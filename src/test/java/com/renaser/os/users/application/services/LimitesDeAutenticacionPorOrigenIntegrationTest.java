package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CredencialesInvalidasException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.autenticacion.CerrarTodasLasSesionesUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase.IniciarSesionCommand;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarCodigoResetContrasenaUseCase.SolicitarCodigoResetContrasenaCommand;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoResetContrasenaPort;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenResetContrasenaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regresion del bloqueo dirigido por correo (2026-09-21). Hasta este arreglo, el corte duro del
 * login ({@code login:email:<correo>}, 10/h) y el de la recuperacion ({@code email:<correo>},
 * 5/h) colgaban del correo que venia en el cuerpo de la peticion. Quince peticiones anonimas por
 * hora —sin cuenta, sin sesion y sin acertar ninguna contrasena— dejaban a una persona nombrada
 * sin poder entrar y sin poder pedir su codigo, porque el rechazo salia antes de que se llegara
 * a comparar ningun hash.
 *
 * <p>Se prueban las DOS direcciones, que es lo unico que hace verificable el arreglo:
 *
 * <ul>
 *   <li><b>El atacante sigue frenado.</b> Desde un mismo origen, el intento once contra el mismo
 *       correo rebota igual que antes; y un origen que agoto su propio cupo por IP ya no alcanza
 *       a correos nuevos.</li>
 *   <li><b>La victima ya no queda bloqueada.</b> Despues de que un tercero agote todo lo que
 *       puede agotar, la duena entra con su contrasena desde su propio origen y sigue pudiendo
 *       pedir su codigo de recuperacion.</li>
 * </ul>
 *
 * <p>Corre contra <b>Redis real</b> (Testcontainers) y contra el adaptador real del limitador,
 * mismo criterio que {@code AccountRequestRateLimitConcurrenciaTest}: lo que se afirma aca es la
 * forma de la clave y el conteo, y con un mock del puerto se estaria probando el mock. Los demas
 * colaboradores si son dobles — ni la base ni el envio de correo entran en lo que se afirma.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LimitesDeAutenticacionPorOrigenIntegrationTest {

    private static final String CONTRASENA_CORRECTA = "la-contrasena-de-la-duena";
    private static final String CONTRASENA_ADIVINADA = "cualquier-cosa-que-no-es";

    /** Direcciones nuevas para cada prueba: el contenedor de Redis es uno para toda la clase y
     * los contadores viven una hora, asi que dos pruebas no pueden compartir claves. */
    private static final AtomicInteger SECUENCIA_DE_IPS = new AtomicInteger();

    /**
     * Comparacion trivial a proposito: lo que se mide aca son contadores. Con BCrypt real, los
     * casi cien intentos de esta clase costarian segundos de CPU sin agregar nada a lo que se
     * afirma — de la comparacion en si se ocupa {@code AutenticacionServiceTest}.
     */
    private static final PasswordEncoder ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return raw.toString();
        }

        @Override
        public boolean matches(CharSequence raw, String hashGuardado) {
            return hashGuardado != null && hashGuardado.contentEquals(raw);
        }
    };

    @Autowired
    private LimitarSolicitudesResetPort limitador;

    private String correoVictima;
    private String ipAtacante;
    private String ipVictima;
    private UserId idVictima;
    private EnviarEmailPort correos;

    @BeforeEach
    void datosNuevosPorPrueba() {
        correoVictima = "victima-" + UUID.randomUUID().toString().replace("-", "") + "@renaser.dev";
        ipAtacante = ipNueva();
        ipVictima = ipNueva();
        idVictima = UserId.of(UUID.randomUUID());
    }

    private static String ipNueva() {
        int n = SECUENCIA_DE_IPS.incrementAndGet();
        return "10." + (n >> 16 & 255) + "." + (n >> 8 & 255) + "." + (n & 255);
    }

    // ------------------------------------------------------------------ login

    private AutenticacionService login() {
        LoadCredencialPort credenciales = correo -> correo.equals(correoVictima)
                ? Optional.of(new CredencialParaLogin(idVictima, CONTRASENA_CORRECTA, true))
                : Optional.empty();
        LoadUserPort usuarios = mock(LoadUserPort.class);
        when(usuarios.byId(idVictima)).thenReturn(Optional.of(User.rehydrate(idVictima,
                new Email("duena@renaser.dev"), UserRole.TRAINEE, UserStatus.ACTIVE, "Duena",
                null, null, null, null)));
        return new AutenticacionService(credenciales, usuarios, ENCODER, limitador);
    }

    @Test
    @DisplayName("atacante: desde un mismo origen, el intento once contra el mismo correo sigue rebotando")
    void elIntentoOnceDesdeElMismoOrigenContraElMismoCorreoSigueRebotando() {
        AutenticacionService servicio = login();

        for (int i = 0; i < AutenticacionService.LIMITE_POR_EMAIL_Y_ORIGEN; i++) {
            assertThatThrownBy(() -> servicio.iniciarSesion(
                    new IniciarSesionCommand(correoVictima, CONTRASENA_ADIVINADA, ipAtacante)))
                    .as("los primeros %s intentos llegan a comparar y fallan como credenciales invalidas",
                            AutenticacionService.LIMITE_POR_EMAIL_Y_ORIGEN)
                    .isInstanceOf(CredencialesInvalidasException.class);
        }

        assertThatThrownBy(() -> servicio.iniciarSesion(
                new IniciarSesionCommand(correoVictima, CONTRASENA_ADIVINADA, ipAtacante)))
                .isInstanceOf(RateLimitExceededException.class);

        // Acertar la contrasena desde ese mismo origen tampoco sirve: el freno se gasta con el
        // intento y no con su resultado, que es la propiedad que habia que conservar.
        assertThatThrownBy(() -> servicio.iniciarSesion(
                new IniciarSesionCommand(correoVictima, CONTRASENA_CORRECTA, ipAtacante)))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("victima: con el cupo del atacante agotado, la duena entra desde su propio origen")
    void laDuenaEntraAunqueUnTerceroHayaAgotadoSuCupoContraEseCorreo() {
        AutenticacionService servicio = login();
        agotarCupoDeLoginDe(servicio, ipAtacante);

        User entro = servicio.iniciarSesion(
                new IniciarSesionCommand(correoVictima, CONTRASENA_CORRECTA, ipVictima));

        assertThat(entro.id()).isEqualTo(idVictima);
    }

    @Test
    @DisplayName("victima: repetir la tanda desde seis origenes distintos tampoco la deja afuera")
    void variosOrigenesAgotandoSuCupoTampocoDejanAfueraALaDuena() {
        AutenticacionService servicio = login();
        for (int origen = 0; origen < 6; origen++) {
            agotarCupoDeLoginDe(servicio, ipNueva());
        }

        User entro = servicio.iniciarSesion(
                new IniciarSesionCommand(correoVictima, CONTRASENA_CORRECTA, ipVictima));

        assertThat(entro.id()).isEqualTo(idVictima);
    }

    @Test
    @DisplayName("atacante: un origen que agoto su cupo por IP ya no alcanza a correos nuevos")
    void unOrigenConSuCupoPorIpAgotadoNoLlegaACorreosNuevos() {
        AutenticacionService servicio = login();
        // Se gasta el cupo por IP contra correos distintos, que era la forma barata de tocar
        // muchas victimas desde un solo origen: antes el INCR del contador por correo corria
        // ANTES del guard por IP, asi que seguia quemando cupo ajeno aun recibiendo 429.
        for (int i = 0; i < AutenticacionService.LIMITE_POR_IP; i++) {
            String otroCorreo = "objetivo-" + UUID.randomUUID().toString().replace("-", "") + "@renaser.dev";
            assertThatThrownBy(() -> servicio.iniciarSesion(
                    new IniciarSesionCommand(otroCorreo, CONTRASENA_ADIVINADA, ipAtacante)))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }

        assertThatThrownBy(() -> servicio.iniciarSesion(
                new IniciarSesionCommand(correoVictima, CONTRASENA_ADIVINADA, ipAtacante)))
                .isInstanceOf(RateLimitExceededException.class);

        // El correo que ese origen alcanzo a nombrar en la peticion rechazada quedo intacto: su
        // duena entra a la primera desde su propio origen.
        assertThat(servicio.iniciarSesion(
                new IniciarSesionCommand(correoVictima, CONTRASENA_CORRECTA, ipVictima)).id())
                .isEqualTo(idVictima);
    }

    private void agotarCupoDeLoginDe(AutenticacionService servicio, String ip) {
        for (int i = 0; i < AutenticacionService.LIMITE_POR_EMAIL_Y_ORIGEN; i++) {
            assertThatThrownBy(() -> servicio.iniciarSesion(
                    new IniciarSesionCommand(correoVictima, CONTRASENA_ADIVINADA, ip)))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }
        assertThatThrownBy(() -> servicio.iniciarSesion(
                new IniciarSesionCommand(correoVictima, CONTRASENA_ADIVINADA, ip)))
                .isInstanceOf(RateLimitExceededException.class);
    }

    // ----------------------------------------------------------- recuperacion

    private ResetContrasenaService reset() {
        correos = mock(EnviarEmailPort.class);
        LoadCredencialPort credenciales = correo -> correo.equals(correoVictima)
                ? Optional.of(new CredencialParaLogin(idVictima, CONTRASENA_CORRECTA, true))
                : Optional.empty();
        return new ResetContrasenaService(credenciales, mock(SaveCredencialPort.class),
                mock(TokenResetContrasenaPort.class), mock(CodigoResetContrasenaPort.class), limitador,
                correos, mock(CerrarTodasLasSesionesUseCase.class), ENCODER, mock(Clock.class));
    }

    @Test
    @DisplayName("atacante: desde un mismo origen, el sexto pedido de recuperacion sigue rebotando")
    void elSextoPedidoDeRecuperacionDesdeElMismoOrigenSigueRebotando() {
        ResetContrasenaService servicio = reset();

        for (int i = 0; i < ResetContrasenaService.LIMITE_POR_EMAIL_Y_ORIGEN; i++) {
            servicio.solicitarCodigo(new SolicitarCodigoResetContrasenaCommand(correoVictima, ipAtacante));
        }

        assertThatThrownBy(() -> servicio.solicitarCodigo(
                new SolicitarCodigoResetContrasenaCommand(correoVictima, ipAtacante)))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("victima: con el cupo del atacante agotado, la duena sigue pudiendo pedir su codigo")
    void laDuenaSiguePudiendoPedirSuCodigoAunqueUnTerceroHayaAgotadoSuCupo() {
        ResetContrasenaService servicio = reset();
        for (int i = 0; i < ResetContrasenaService.LIMITE_POR_EMAIL_Y_ORIGEN; i++) {
            servicio.solicitarCodigo(new SolicitarCodigoResetContrasenaCommand(correoVictima, ipAtacante));
        }
        assertThatThrownBy(() -> servicio.solicitarCodigo(
                new SolicitarCodigoResetContrasenaCommand(correoVictima, ipAtacante)))
                .isInstanceOf(RateLimitExceededException.class);

        servicio.solicitarCodigo(new SolicitarCodigoResetContrasenaCommand(correoVictima, ipVictima));

        // El correo que sale despues del bloque del atacante es el de la duena: la salida de
        // emergencia —el desafio de posesion del correo que el modulo ya emite— no se le puede
        // cerrar desde afuera. Es lo que sostiene que el login no necesite un tope global.
        verify(correos, times(ResetContrasenaService.LIMITE_POR_EMAIL_Y_ORIGEN + 1))
                .enviarCodigoResetContrasena(eq(correoVictima), any());
    }

    @Test
    @DisplayName("el tope de envios frena el mail, pero nunca rechaza la peticion")
    void agotadoElTopeDeEnviosNoSaleMasMailPeroLaPeticionNoSeRechaza() {
        ResetContrasenaService servicio = reset();
        int origenesNecesarios = ResetContrasenaService.LIMITE_DE_ENVIOS_POR_EMAIL
                / ResetContrasenaService.LIMITE_POR_EMAIL_Y_ORIGEN;
        for (int origen = 0; origen < origenesNecesarios; origen++) {
            String ip = ipNueva();
            for (int i = 0; i < ResetContrasenaService.LIMITE_POR_EMAIL_Y_ORIGEN; i++) {
                servicio.solicitarCodigo(new SolicitarCodigoResetContrasenaCommand(correoVictima, ip));
            }
        }
        verify(correos, times(ResetContrasenaService.LIMITE_DE_ENVIOS_POR_EMAIL))
                .enviarCodigoResetContrasena(eq(correoVictima), any());

        // Un origen fresco tiene cupo propio, asi que su peticion NO se rechaza — pero el buzon
        // ya recibio todo el correo que se tolera meterle en esta hora, y no sale uno mas.
        assertThatCode(() -> servicio.solicitarCodigo(
                new SolicitarCodigoResetContrasenaCommand(correoVictima, ipVictima)))
                .doesNotThrowAnyException();

        verify(correos, times(ResetContrasenaService.LIMITE_DE_ENVIOS_POR_EMAIL))
                .enviarCodigoResetContrasena(eq(correoVictima), any());
    }
}
