package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.google.genai.errors.ClientException;
import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort.Consulta;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Dentro del stream no hay status HTTP que cambiar (ya salio el 200), pero el error del SDK no
 * puede subir crudo: el servicio necesita distinguir "el proveedor no puede ahora" para decirle
 * a la persona que espere unos minutos en vez de reintentar enseguida.
 */
@ExtendWith(MockitoExtension.class)
class GoogleGenAiRenasiaChatAdapterTest {

    @Mock
    private ChatModel chatModel;
    @Mock
    private EjecutarHerramientaAgenteUseCase herramientas;

    @Test
    @DisplayName("un 429 de Google a mitad del stream llega al servicio como ProveedorIaNoDisponibleException")
    void cuotaAgotadaEnElStream() {
        // ChatClient arma el request con `chatModel.getOptions().mutate()`: con un mock pelado es
        // NPE antes de llegar al stream. No es lo que se prueba; se le da un ChatOptions vacio.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(
                Flux.error(new ClientException(429, "RESOURCE_EXHAUSTED", "Quota exceeded")));
        GoogleGenAiRenasiaChatAdapter adaptador = new GoogleGenAiRenasiaChatAdapter(chatModel, herramientas);
        Consulta consulta = new Consulta(AgenteConversacional.COMPANION, UserId.of(UUID.randomUUID()),
                "hola", List.of(), null, List.of(), List.of());

        assertThatThrownBy(() -> adaptador.responder(consulta).blockLast())
                .isInstanceOf(ProveedorIaNoDisponibleException.class);
    }
}
