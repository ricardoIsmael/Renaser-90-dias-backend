package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CodigoVerificacionInvalidoException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase.CerrarMiCuentaCommand;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase.Metodo;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.eliminacion.CodigoEliminarCuentaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Yo → «Eliminar mi cuenta» (D-243): solo la propia cuenta, con su contraseña o con el codigo. */
@ExtendWith(MockitoExtension.class)
class CierrePropioServiceTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String CONTRASENA = "Secreta123!clave";

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private LoadCredencialPort loadCredencialPort;
    @Mock
    private CodigoEliminarCuentaPort codigoPort;
    @Mock
    private EnviarEmailPort enviarEmailPort;
    @Mock
    private LimitarSolicitudesResetPort limitador;
    @Mock
    private CierreDeCuentaService cierre;

    private CierrePropioService service;
    private User ana;

    @BeforeEach
    void setUp() {
        service = new CierrePropioService(new RequireActiveUserGuard(loadUserPort), loadCredencialPort, ENCODER,
                codigoPort, enviarEmailPort, limitador, cierre);
        UserId id = UserId.of(UUID.randomUUID());
        ana = User.rehydrate(id, new Email("ana-" + id + "@renaser.dev"), UserRole.TRAINEE, UserStatus.ACTIVE, "Ana",
                null, null, null, null);
        lenient().when(loadUserPort.byId(id)).thenReturn(Optional.of(ana));
        lenient().when(limitador.registrarIntento(anyString(), any(), anyInt())).thenReturn(true);
    }

    private void conContrasena() {
        when(loadCredencialPort.porEmail(ana.email().value()))
                .thenReturn(Optional.of(new CredencialParaLogin(ana.id(), ENCODER.encode(CONTRASENA), true)));
    }

    @Test
    @DisplayName("con la contraseña correcta, la cuenta se cierra (via APP)")
    void contrasenaCorrecta() {
        conContrasena();

        service.cerrar(new CerrarMiCuentaCommand(ana.id(), CONTRASENA, null));

        verify(cierre).cerrar(ana, RegistroDeEliminacion.Via.APP);
    }

    @Test
    @DisplayName("con la contraseña equivocada no se cierra (400)")
    void contrasenaIncorrecta() {
        conContrasena();

        assertThatThrownBy(() -> service.cerrar(new CerrarMiCuentaCommand(ana.id(), "otra", null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cierre, never()).cerrar(any(), any());
    }

    @Test
    @DisplayName("sin contraseña ni codigo no se cierra")
    void sinNada() {
        assertThatThrownBy(() -> service.cerrar(new CerrarMiCuentaCommand(ana.id(), " ", null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cierre, never()).cerrar(any(), any());
    }

    @Test
    @DisplayName("una cuenta sin contraseña (Google) confirma con el codigo; uno equivocado da 400")
    void conCodigo() {
        when(loadCredencialPort.porEmail(ana.email().value()))
                .thenReturn(Optional.of(new CredencialParaLogin(ana.id(), null, true)));
        when(codigoPort.verificarCodigo(eq(ana.email().value()), eq("123456"), anyInt())).thenReturn(true);

        assertThat(service.comoConfirmar(ana.id()).metodo()).isEqualTo(Metodo.CODIGO);
        service.cerrar(new CerrarMiCuentaCommand(ana.id(), null, " 123456 "));
        verify(cierre).cerrar(ana, RegistroDeEliminacion.Via.APP);

        assertThatThrownBy(() -> service.cerrar(new CerrarMiCuentaCommand(ana.id(), null, "000000")))
                .isInstanceOf(CodigoVerificacionInvalidoException.class);
    }

    @Test
    @DisplayName("una cuenta con contraseña confirma con contraseña")
    void comoConfirmarConContrasena() {
        conContrasena();
        when(cierre.diasDeGracia()).thenReturn(30);

        var como = service.comoConfirmar(ana.id());

        assertThat(como.metodo()).isEqualTo(Metodo.CONTRASENA);
        assertThat(como.diasDeGracia()).isEqualTo(30);
    }

    @Test
    @DisplayName("una cuenta SUSPENDIDA no puede usar esto (403)")
    void suspendida() {
        UserId id = UserId.of(UUID.randomUUID());
        User suspendida = User.rehydrate(id, new Email("s-" + id + "@renaser.dev"), UserRole.TRAINEE,
                UserStatus.SUSPENDED, "Susp", null, null, null, null);
        when(loadUserPort.byId(id)).thenReturn(Optional.of(suspendida));

        assertThatThrownBy(() -> service.cerrar(new CerrarMiCuentaCommand(id, CONTRASENA, null)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("al pasar el tope de intentos por hora responde 429 sin mirar la contraseña")
    void topeDeIntentos() {
        when(limitador.registrarIntento(eq("eliminar-cuenta-intentos:" + ana.id()), any(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service.cerrar(new CerrarMiCuentaCommand(ana.id(), CONTRASENA, null)))
                .isInstanceOf(RateLimitExceededException.class);
        verify(cierre, never()).cerrar(any(), any());
    }

    @Test
    @DisplayName("enviar el codigo lo manda al correo de la propia cuenta")
    void enviarCodigo() {
        when(codigoPort.generarCodigo(eq(ana.email().value()), any())).thenReturn("654321");

        service.enviarCodigo(ana.id());

        verify(enviarEmailPort).enviarCodigoEliminarCuenta(ana.email().value(), "654321");
    }
}
