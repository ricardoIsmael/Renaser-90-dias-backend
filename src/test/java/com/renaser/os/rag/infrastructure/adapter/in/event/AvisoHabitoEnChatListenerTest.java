package com.renaser.os.rag.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.AvisoHabitoDebidoEvent;
import com.renaser.os.rag.application.ports.in.aviso.DejarAvisoHabitoEnChatUseCase;
import com.renaser.os.rag.application.ports.in.aviso.DejarAvisoHabitoEnChatUseCase.AvisoHabitoEnChatCommand;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Unit puro (sin Spring), mismo estilo que los listeners de {@code notifications}: confirma que
 * el evento de {@code habits} llega al puerto de entrada con todos sus datos, sin perder ninguno. */
@ExtendWith(MockitoExtension.class)
class AvisoHabitoEnChatListenerTest {

    @Mock
    private DejarAvisoHabitoEnChatUseCase useCase;

    @Test
    void traduceElEventoAlComandoSinPerderDatos() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        UUID clave = UUID.randomUUID();
        Instant calculadoEn = Instant.parse("2026-09-23T01:59:30Z");
        var event = new AvisoHabitoDebidoEvent(UUID.randomUUID(), aprendiz, "Meditar", "POR_VENCER", 30, 10, clave,
                calculadoEn);

        new AvisoHabitoEnChatListener(useCase).on(event);

        ArgumentCaptor<AvisoHabitoEnChatCommand> captor = ArgumentCaptor.forClass(AvisoHabitoEnChatCommand.class);
        verify(useCase).dejarEnElChat(captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new AvisoHabitoEnChatCommand(aprendiz, "Meditar", "POR_VENCER", 30, 10, clave, calculadoEn));
    }
}
