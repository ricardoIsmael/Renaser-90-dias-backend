package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.google.genai.errors.ClientException;
import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort.Consulta;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
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
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dentro del stream no hay status HTTP que cambiar (ya salio el 200), pero el error del SDK no
 * puede subir crudo: el servicio necesita distinguir "el proveedor no puede ahora" para decirle
 * a la persona que espere unos minutos en vez de reintentar enseguida.
 *
 * <p>Y, desde el 2026-09-14, que la situacion de la persona LLEGUE al prompt de sistema. Es la
 * unica prueba que recorre el camino entero de ese dato: el resto verifica que se calcula bien,
 * esta verifica que sale. Capturar el {@link Prompt} que recibe el modelo es la unica forma de
 * comprobarlo sin hablar con Gemini.
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
                "hola", List.of(), null, List.of(), List.of(), null);

        assertThatThrownBy(() -> adaptador.responder(consulta).blockLast())
                .isInstanceOf(ProveedorIaNoDisponibleException.class);
    }

    @Test
    @DisplayName("El dia y la fase de la persona llegan al prompt de sistema")
    void laSituacionLlegaAlPrompt() {
        String sistema = promptDeSistemaCon(new SituacionDelAprendiz(17, 2));

        assertThat(sistema).contains("dia 17 de 90").contains("fase 2 de 4");
    }

    /**
     * Sin situacion el marcador NO puede quedar vacio: un hueco en esa seccion afirma
     * implicitamente que hay un dia, y el modelo lo buscaria. Se dice en una frase que puede leer.
     */
    @Test
    @DisplayName("Sin participacion, el prompt dice que no hay dia en vez de callarse")
    void sinSituacionElPromptLoDice() {
        String sistema = promptDeSistemaCon(null);

        assertThat(sistema).contains("no esta cursando el programa")
                .doesNotContain("dia 0 de 90");
    }

    /** Arma el adaptador, lo hace responder y devuelve el mensaje de sistema que recibio el modelo. */
    private String promptDeSistemaCon(SituacionDelAprendiz situacion) {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());
        GoogleGenAiRenasiaChatAdapter adaptador = new GoogleGenAiRenasiaChatAdapter(chatModel, herramientas);

        adaptador.responder(new Consulta(AgenteConversacional.COMPANION, UserId.of(UUID.randomUUID()),
                "hola", List.of(), null, List.of(), List.of(), situacion)).blockLast();

        ArgumentCaptor<Prompt> capturado = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(capturado.capture());
        return capturado.getValue().getInstructions().stream()
                .map(mensaje -> mensaje.getText() == null ? "" : mensaje.getText())
                .reduce("", (a, b) -> a + "\n" + b);
    }
}
