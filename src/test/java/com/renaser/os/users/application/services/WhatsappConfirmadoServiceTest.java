package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.domain.WhatsappDeLaCuentaException;
import com.renaser.os.shared.domain.WhatsappDeLaCuentaException.Motivo;
import com.renaser.os.users.api.CodigoPorWhatsApp;
import com.renaser.os.users.api.CodigoPorWhatsApp.NumeroParaCodigo;
import com.renaser.os.users.application.ports.in.whatsapp.WhatsappConfirmadoUseCase.ConfirmarCommand;
import com.renaser.os.users.application.ports.in.whatsapp.WhatsappConfirmadoUseCase.PedirCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.whatsapp.CodigoDeWhatsappDeLaCuentaPort;
import com.renaser.os.users.application.ports.out.whatsapp.WhatsappConfirmadoPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** «WhatsApp confirmado en Yo» (spec 051): las reglas del servicio, con los puertos simulados. */
class WhatsappConfirmadoServiceTest {

    private static final NumeroParaCodigo NUMERO = new NumeroParaCodigo("+51987654321", "+51 9•• ••• •21");
    private final UserId cuenta = UserId.of(UUID.randomUUID());
    private RequireActiveUserGuard activa;
    private CodigoPorWhatsApp whatsapp;
    private CodigoDeWhatsappDeLaCuentaPort codigos;
    private WhatsappConfirmadoPort confirmados;
    private LimitarSolicitudesResetPort limites;
    private WhatsappConfirmadoService servicio;

    @BeforeEach
    void armar() {
        activa = mock(RequireActiveUserGuard.class);
        whatsapp = mock(CodigoPorWhatsApp.class);
        codigos = mock(CodigoDeWhatsappDeLaCuentaPort.class);
        confirmados = mock(WhatsappConfirmadoPort.class);
        limites = mock(LimitarSolicitudesResetPort.class);
        when(whatsapp.configurado()).thenReturn(true);
        when(whatsapp.numero("+51 987654321")).thenReturn(Optional.of(NUMERO));
        when(whatsapp.numero("+51987654321")).thenReturn(Optional.of(NUMERO));
        when(whatsapp.enviar(any(), any())).thenReturn(true);
        when(limites.registrarIntento(any(), any(), anyInt())).thenReturn(true);
        when(confirmados.de(cuenta)).thenReturn(Optional.empty());
        when(codigos.generarCodigo(eq(cuenta), eq("+51987654321"), any())).thenReturn("123456");
        servicio = new WhatsappConfirmadoService(activa, whatsapp, codigos, confirmados, limites);
    }

    // spec 051 FR-002, SC-002
    @Test
    @DisplayName("pedir el codigo lo manda al numero normalizado, responde enmascarado y NO guarda el numero")
    void pedirNoGuarda() {
        String enmascarado = servicio.pedirCodigo(new PedirCodigoCommand(cuenta, "+51 987654321", "1.2.3.4"));

        assertThat(enmascarado).isEqualTo("+51 9•• ••• •21");
        verify(whatsapp).enviar(NUMERO, "123456");
        verify(confirmados, never()).guardar(any(), any());
    }

    // spec 051 FR-002
    @Test
    @DisplayName("un numero sin forma es NUMERO_INVALIDO y sin canal es CANAL_NO_DISPONIBLE; no sale nada")
    void rechazosDelPedido() {
        assertThatThrownBy(() -> servicio.pedirCodigo(new PedirCodigoCommand(cuenta, "123", null)))
                .isInstanceOfSatisfying(WhatsappDeLaCuentaException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.NUMERO_INVALIDO));
        when(whatsapp.configurado()).thenReturn(false);
        assertThatThrownBy(() -> servicio.pedirCodigo(new PedirCodigoCommand(cuenta, "+51 987654321", null)))
                .isInstanceOfSatisfying(WhatsappDeLaCuentaException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.CANAL_NO_DISPONIBLE));
        verify(whatsapp, never()).enviar(any(), any());
    }

    // spec 051 FR-002
    @Test
    @DisplayName("el numero que la cuenta ya tiene confirmado es YA_CONFIRMADO y no gasta un envio")
    void yaConfirmado() {
        when(confirmados.de(cuenta)).thenReturn(Optional.of("+51987654321"));

        assertThatThrownBy(() -> servicio.pedirCodigo(new PedirCodigoCommand(cuenta, "+51 987654321", null)))
                .isInstanceOfSatisfying(WhatsappDeLaCuentaException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.YA_CONFIRMADO));
        verifyNoInteractions(codigos);
    }

    // spec 051 FR-002
    @Test
    @DisplayName("si el proveedor rechaza el envio es CODIGO_NO_SALIO")
    void noSalio() {
        when(whatsapp.enviar(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> servicio.pedirCodigo(new PedirCodigoCommand(cuenta, "+51 987654321", null)))
                .isInstanceOfSatisfying(WhatsappDeLaCuentaException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.CODIGO_NO_SALIO));
    }

    // spec 051 FR-006
    @Test
    @DisplayName("sin cupo (espera, por cuenta, por numero, por origen) es 429 y no se manda; cada contador usa su clave")
    void limites() {
        when(limites.registrarIntento(eq("codigo-whatsapp:numero:+51987654321"), any(Duration.class), eq(3))).thenReturn(false);

        assertThatThrownBy(() -> servicio.pedirCodigo(new PedirCodigoCommand(cuenta, "+51 987654321", "9.9.9.9")))
                .isInstanceOf(RateLimitExceededException.class);

        verify(whatsapp, never()).enviar(any(), any());
        verify(limites).registrarIntento(eq("whatsapp-cuenta:espera:" + cuenta), eq(Duration.ofSeconds(60)), eq(1));
        verify(limites).registrarIntento(eq("whatsapp-cuenta:codigos:" + cuenta), any(Duration.class), eq(5));
    }

    // spec 051 FR-003
    @Test
    @DisplayName("confirmar con el codigo correcto guarda el numero y responde enmascarado")
    void confirmarGuarda() {
        when(codigos.verificarCodigo(cuenta, "+51987654321", "123456", VerificacionEmailService.MAX_INTENTOS)).thenReturn(true);
        when(confirmados.guardar(cuenta, "+51987654321")).thenReturn(true);

        assertThat(servicio.confirmar(new ConfirmarCommand(cuenta, "+51 987654321", " 123456 ", null))).isEqualTo("+51 9•• ••• •21");
        verify(confirmados).guardar(cuenta, "+51987654321");
    }

    // spec 051 FR-003, SC-002
    @Test
    @DisplayName("un codigo equivocado o vacio es CODIGO_INCORRECTO y no guarda nada")
    void codigoIncorrecto() {
        when(codigos.verificarCodigo(any(), any(), any(), anyInt())).thenReturn(false);

        for (String codigo : new String[]{"000000", "", null}) {
            assertThatThrownBy(() -> servicio.confirmar(new ConfirmarCommand(cuenta, "+51 987654321", codigo, null)))
                    .isInstanceOfSatisfying(WhatsappDeLaCuentaException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.CODIGO_INCORRECTO));
        }
        verify(confirmados, never()).guardar(any(), any());
    }

    // spec 051 FR-007
    @Test
    @DisplayName("si el numero ya es de otra cuenta es NUMERO_EN_USO, aunque el codigo sea el bueno")
    void numeroEnUso() {
        when(codigos.verificarCodigo(any(), any(), any(), anyInt())).thenReturn(true);
        when(confirmados.guardar(cuenta, "+51987654321")).thenReturn(false);

        assertThatThrownBy(() -> servicio.confirmar(new ConfirmarCommand(cuenta, "+51 987654321", "123456", null)))
                .isInstanceOfSatisfying(WhatsappDeLaCuentaException.class, e -> assertThat(e.motivo()).isEqualTo(Motivo.NUMERO_EN_USO));
    }

    // spec 051 FR-001
    @Test
    @DisplayName("el estado dice si hay canal y el numero confirmado enmascarado")
    void estado() {
        when(confirmados.de(cuenta)).thenReturn(Optional.of("+51987654321"));

        var estado = servicio.estado(cuenta);

        assertThat(estado.disponible()).isTrue();
        assertThat(estado.confirmado()).contains("+51 9•• ••• •21");
    }

    // spec 051 FR-008
    @Test
    @DisplayName("los comandos no imprimen el numero ni el codigo")
    void noImprimen() {
        assertThat(new PedirCodigoCommand(cuenta, "+51 987654321", null).toString()).doesNotContain("987654321");
        assertThat(new ConfirmarCommand(cuenta, "+51 987654321", "123456", null).toString()).doesNotContain("987654321").doesNotContain("123456");
    }
}
