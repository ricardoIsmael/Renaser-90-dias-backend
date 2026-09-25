package com.renaser.os.shared.web;

import com.renaser.os.shared.domain.RateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E-248: un error que salta ANTES de abrir un endpoint {@code text/event-stream} tiene que llegar
 * como JSON con su codigo. El chat de Renasia revisa el tope diario antes de abrir el stream, y el
 * 429 no se podia escribir: Spring negociaba contra el {@code produces} del endpoint y terminaba en
 * {@code HttpMediaTypeNotAcceptableException}, con un 500 para la app en vez del aviso.
 */
class ErroresAntesDelStreamTest {

    /** Lo mismo que el chat real: el tope se revisa antes de devolver el Flux. */
    @RestController
    static class ChatDePrueba {

        @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        Flux<String> preguntar() {
            throw new RateLimitExceededException("Ya usaste todos tus mensajes de hoy. Vuelve mañana.");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ChatDePrueba())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("el 429 del tope diario sale como JSON con su mensaje, aunque el endpoint sea text/event-stream")
    void topeDiarioEnUnStream() throws Exception {
        mvc.perform(post("/chat").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Ya usaste todos tus mensajes de hoy. Vuelve mañana."));
    }
}
