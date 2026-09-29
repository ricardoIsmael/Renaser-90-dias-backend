package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.chat.api.AvisosDeMensajesFinder;
import com.renaser.os.chat.api.AvisosDeMensajesFinder.AvisoDeMensaje;
import com.renaser.os.chat.api.AvisosDeMensajesFinder.Contenido;
import com.renaser.os.chat.api.AvisosDeMensajesFinder.Destinatario;
import com.renaser.os.chat.api.MensajeDeChatGuardadoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-223: el aviso de un mensaje con {@code soloPara} (la tarjeta diaria del semáforo) llega solo a esa
 * persona; el staff del soporte, que también ve el chat, no recibe push. Sin {@code soloPara}, todos (D-221).
 */
class MensajeDeChatSoloParaTest {

    private static final UUID MENSAJE = UUID.randomUUID();
    private static final UUID SOPORTE = UUID.randomUUID();
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());

    private final AvisosDeMensajesFinder finder = mock(AvisosDeMensajesFinder.class);
    private final EmitirNotificacionUseCase emitir = mock(EmitirNotificacionUseCase.class);
    private final MensajeDeChatNotificationListener listener = new MensajeDeChatNotificationListener(finder, emitir);

    private List<UserId> avisados(MensajeDeChatGuardadoEvent evento) {
        when(finder.avisoDe(MENSAJE)).thenReturn(Optional.of(new AvisoDeMensaje(SOPORTE, "/chat/" + SOPORTE, false,
                "Formación Renaser", Contenido.TEXTO, "Hoy llevas 85 % de tus hábitos.",
                List.of(new Destinatario(ANA, "Ana – Formación Renaser", 1), new Destinatario(ADMIN, "Ana – Formación Renaser", 1)))));
        listener.on(evento);
        ArgumentCaptor<EmitirNotificacionCommand> comandos = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir, atLeastOnce()).emitir(comandos.capture());
        return comandos.getAllValues().stream().map(EmitirNotificacionCommand::usuarioId).toList();
    }

    @Test
    @DisplayName("con soloPara, solo la aprendiz")
    void soloParaLaAprendiz() {
        assertThat(avisados(new MensajeDeChatGuardadoEvent(MENSAJE, SOPORTE, ANA.value()))).containsExactly(ANA);
    }

    @Test
    @DisplayName("sin soloPara, todos los que resolvió chat")
    void sinSoloParaTodos() {
        assertThat(avisados(new MensajeDeChatGuardadoEvent(MENSAJE, SOPORTE))).containsExactly(ANA, ADMIN);
    }
}
