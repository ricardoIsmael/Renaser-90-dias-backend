package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CodigoVerificacionInvalidoException;
import com.renaser.os.shared.domain.EnvioEmailFallidoException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pagina web publica (D-243): no revela si el correo tiene cuenta. */
@ExtendWith(MockitoExtension.class)
class CierrePorCorreoServiceTest {

    private static final String IP = "203.0.113.7";

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private CodigoEliminarCuentaPort codigoPort;
    @Mock
    private EnviarEmailPort enviarEmailPort;
    @Mock
    private LimitarSolicitudesResetPort limitador;
    @Mock
    private CierreDeCuentaService cierre;

    private CierrePorCorreoService service;
    private User ana;

    @BeforeEach
    void setUp() {
        service = new CierrePorCorreoService(loadUserPort, codigoPort, enviarEmailPort, limitador, cierre);
        UserId id = UserId.of(UUID.randomUUID());
        ana = User.rehydrate(id, new Email("ana@renaser.dev"), UserRole.TRAINEE, UserStatus.ACTIVE, "Ana", null, null,
                null, null);
        lenient().when(limitador.registrarIntento(anyString(), any(), anyInt())).thenReturn(true);
    }

    @Test
    @DisplayName("con cuenta: genera el codigo y lo manda (correo normalizado)")
    void conCuentaMandaElCodigo() {
        when(loadUserPort.byEmail(new Email("ana@renaser.dev"))).thenReturn(Optional.of(ana));
        when(codigoPort.generarCodigo(eq("ana@renaser.dev"), any())).thenReturn("111222");

        service.solicitarCodigo("  Ana@Renaser.dev ", IP);

        verify(enviarEmailPort).enviarCodigoEliminarCuenta("ana@renaser.dev", "111222");
    }

    @Test
    @DisplayName("sin cuenta: no manda nada y responde igual (sin excepcion)")
    void sinCuentaNoDelata() {
        when(loadUserPort.byEmail(any())).thenReturn(Optional.empty());

        assertThatCode(() -> service.solicitarCodigo("nadie@renaser.dev", IP)).doesNotThrowAnyException();
        verifyNoInteractions(enviarEmailPort, codigoPort);
    }

    @Test
    @DisplayName("un correo mal escrito tampoco delata nada: misma respuesta")
    void correoMalFormado() {
        assertThatCode(() -> service.solicitarCodigo("no-es-un-correo", IP)).doesNotThrowAnyException();
        verifyNoInteractions(enviarEmailPort);
    }

    @Test
    @DisplayName("si el correo no sale, se anota y la respuesta sigue siendo la misma (no un 503 que delate)")
    void falloDeEnvioNoDelata() {
        when(loadUserPort.byEmail(any())).thenReturn(Optional.of(ana));
        when(codigoPort.generarCodigo(anyString(), any())).thenReturn("111222");
        doThrow(new EnvioEmailFallidoException(new RuntimeException("SMTP caido")))
                .when(enviarEmailPort).enviarCodigoEliminarCuenta(anyString(), anyString());

        assertThatCode(() -> service.solicitarCodigo("ana@renaser.dev", IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("confirmar con el codigo correcto cierra la cuenta por la WEB")
    void confirmarCierra() {
        when(codigoPort.verificarCodigo(eq("ana@renaser.dev"), eq("123456"), anyInt())).thenReturn(true);
        when(loadUserPort.byEmail(new Email("ana@renaser.dev"))).thenReturn(Optional.of(ana));

        service.confirmar("ana@renaser.dev", "123456", IP);

        verify(cierre).cerrar(ana, RegistroDeEliminacion.Via.WEB);
    }

    @Test
    @DisplayName("un codigo equivocado falla igual exista o no la cuenta, y sin buscarla")
    void codigoEquivocado() {
        when(codigoPort.verificarCodigo(anyString(), anyString(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service.confirmar("ana@renaser.dev", "000000", IP))
                .isInstanceOf(CodigoVerificacionInvalidoException.class);
        verify(loadUserPort, never()).byEmail(any());
        verify(cierre, never()).cerrar(any(), any());
    }

    @Test
    @DisplayName("pasado el tope por origen responde 429")
    void topePorOrigen() {
        when(limitador.registrarIntento(anyString(), any(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service.solicitarCodigo("ana@renaser.dev", IP))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
