package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort.Pendiente;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort.Pendientes;
import com.renaser.os.chat.application.ports.out.bienvenida.TextosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La bienvenida del mentor en el chat del grupo estable (D-191). Sin Spring: el
 * {@code PlatformTransactionManager} sin stubbing hace que {@code TransactionTemplate} ejecute el
 * callback directo, como en {@code BienvenidaEnSoporteServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class BienvenidaEnGrupoServiceTest {

    private static final UUID GRUPO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final ConversacionId CHAT = ConversacionId.of(UUID.fromString("33333333-3333-4333-8333-333333333333"));
    private static final UserId MENTOR = UserId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId LUIS = UserId.of(UUID.randomUUID());
    private static final UUID ASIG_ANA = UUID.randomUUID();
    private static final UUID ASIG_LUIS = UUID.randomUUID();

    @Mock
    private BienvenidaEnGrupoPort bienvenidaPort;
    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private EnviarMensajeUseCase enviarMensaje;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private PlatformTransactionManager transactionManager;

    private BienvenidaEnGrupoService servicio(String texto) {
        TextosDeBienvenidaPort textos = mock(TextosDeBienvenidaPort.class);
        when(textos.grupo()).thenReturn(texto);
        return new BienvenidaEnGrupoService(bienvenidaPort, textos, loadConversacionPort, enviarMensaje,
                userSummaryFinder, transactionManager);
    }

    @Test
    @DisplayName("marca y después manda el texto del recurso en el chat del grupo, firmado por el mentor, con los dos primeros nombres")
    void mandaLaBienvenidaDelMentor() {
        preparar(List.of(new Pendiente(ASIG_ANA, ANA)), UserStatus.ACTIVE);
        when(bienvenidaPort.marcarDada(ASIG_ANA)).thenReturn(true);

        int dadas = servicio("¡{nombre}, bienvenida! Soy {mentor}.").darBienvenidas(GRUPO);

        assertThat(dadas).isEqualTo(1);
        ArgumentCaptor<EnviarMensajeCommand> enviado = ArgumentCaptor.forClass(EnviarMensajeCommand.class);
        InOrder orden = inOrder(bienvenidaPort, enviarMensaje);
        orden.verify(bienvenidaPort).marcarDada(ASIG_ANA);
        orden.verify(enviarMensaje).enviar(enviado.capture());
        assertThat(enviado.getValue().actorId()).isEqualTo(MENTOR);
        assertThat(enviado.getValue().conversacionId()).isEqualTo(CHAT);
        assertThat(enviado.getValue().tipo()).isEqualTo(TipoMensaje.TEXTO);
        assertThat(enviado.getValue().texto()).isEqualTo("¡Ana, bienvenida! Soy Carlos.");
    }

    @Test
    @DisplayName("idempotente: si la marca ya estaba (otra entrega la dio), no manda nada")
    void reentregaNoDuplica() {
        preparar(List.of(new Pendiente(ASIG_ANA, ANA)), UserStatus.ACTIVE);
        when(bienvenidaPort.marcarDada(ASIG_ANA)).thenReturn(false);

        assertThat(servicio("Hola {nombre}").darBienvenidas(GRUPO)).isZero();
        verify(enviarMensaje, never()).enviar(any());
    }

    @Test
    @DisplayName("recepción, sin mentor o grupo no operativo (el puerto no devuelve pendientes): no manda nada")
    void sinPendientesNoManda() {
        when(bienvenidaPort.pendientes(GRUPO)).thenReturn(Optional.empty());

        assertThat(servicio("Hola {nombre}").darBienvenidas(GRUPO)).isZero();
        verifyNoInteractions(enviarMensaje, loadConversacionPort, userSummaryFinder);
    }

    @Test
    @DisplayName("sin texto de grupo en el recurso no consulta ni marca: las pertenencias quedan pendientes")
    void sinTextoNoMarca() {
        assertThat(servicio("").darBienvenidas(GRUPO)).isZero();
        verifyNoInteractions(bienvenidaPort, enviarMensaje);
    }

    @Test
    @DisplayName("mentor suspendido: no marca ni manda (queda pendiente)")
    void mentorSuspendidoNoManda() {
        preparar(List.of(new Pendiente(ASIG_ANA, ANA)), UserStatus.SUSPENDED);

        assertThat(servicio("Hola {nombre}").darBienvenidas(GRUPO)).isZero();
        verify(bienvenidaPort, never()).marcarDada(any());
        verify(enviarMensaje, never()).enviar(any());
    }

    @Test
    @DisplayName("un envío que falla no frena a los demás y se lanza al final para que el outbox reintente")
    void unFalloSeLanzaAlFinal() {
        preparar(List.of(new Pendiente(ASIG_ANA, ANA), new Pendiente(ASIG_LUIS, LUIS)), UserStatus.ACTIVE);
        when(bienvenidaPort.marcarDada(any())).thenReturn(true);
        when(enviarMensaje.enviar(any())).thenThrow(new IllegalStateException("base caída")).thenReturn(null);

        assertThatThrownBy(() -> servicio("Hola {nombre}").darBienvenidas(GRUPO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("se dieron 1");
        verify(enviarMensaje, times(2)).enviar(any());
    }

    private void preparar(List<Pendiente> pendientes, UserStatus estadoMentor) {
        when(bienvenidaPort.pendientes(GRUPO)).thenReturn(Optional.of(new Pendientes(MENTOR, pendientes)));
        when(loadConversacionPort.porCelulaId(GRUPO)).thenReturn(Optional.of(
                Conversacion.crearCelula(CHAT, GRUPO, Instant.parse("2026-09-01T15:00:00Z"))));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(
                MENTOR, new UserSummary(MENTOR, "carlos ramírez", null, UserRole.MENTOR, estadoMentor),
                ANA, new UserSummary(ANA, "Ana Pérez", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                LUIS, new UserSummary(LUIS, "Luis Soto", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }
}
