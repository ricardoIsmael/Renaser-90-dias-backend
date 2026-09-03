package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.ChatIAPort;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Implementación real de {@link ChatIAPort} sobre Gemini, vía el {@code ChatClient} de
 * Spring AI en streaming (verificado contra el bytecode de {@code spring-ai-client-chat:2.0.0}:
 * {@code ChatClient.create(ChatModel)}, {@code .prompt().system(...).user(...).stream().content()}
 * devuelve {@code Flux<String>} de forma nativa). El {@link ChatModel} inyectado es el bean
 * {@code GoogleGenAiChatModel} que arma {@link GoogleGenAiClientesConfig} — no hay otro
 * {@code ChatModel} en el contexto mientras las autoconfiguraciones de Spring AI sigan
 * excluidas, así que la inyección no es ambigua.
 *
 * <p><b>Sin {@code @Transactional} — nunca lo va a tener.</b> Este adaptador no toca
 * persistencia: es el propio {@code ConversacionRenasiaService} el que ya saca a
 * {@code buscarSimilares}/la llamada a IA fuera de cualquier transacción (C-1/C-4, ver su
 * javadoc). Ninguna llamada de este adaptador a Gemini debe quedar envuelta en una
 * transacción abierta por quien lo invoque.
 *
 * <p><b>El prompt de sistema es PROVISIONAL.</b> Vive en
 * {@code src/main/resources/prompts/renasia-sistema.st}, marcado como tal en el propio
 * archivo. Producto todavía no definió el tono, la voz de marca ni las reglas de
 * contenido de Renasia — lo único que este prompt garantiza hoy es la regla de abstención:
 * si el contexto recuperado no cubre la pregunta, el modelo tiene que decirlo en vez de
 * inventar. Cuando Producto entregue el prompt definitivo, reemplaza el contenido de ese
 * archivo; esta clase no necesita cambiar.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "google")
class GoogleGenAiRenasiaChatAdapter implements ChatIAPort {

    private static final String RECURSO_PROMPT_SISTEMA = "prompts/renasia-sistema.st";

    private final ChatClient chatClient;
    private final PromptTemplate promptSistema;

    GoogleGenAiRenasiaChatAdapter(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
        this.promptSistema = new PromptTemplate(new ClassPathResource(RECURSO_PROMPT_SISTEMA));
    }

    /**
     * Solo emite {@link EventoRenasia.Texto} (uno por fragmento) y, al completar el stream de
     * Gemini, exactamente un {@link EventoRenasia.Fin}. Nunca emite {@code Fuentes}: este
     * adaptador solo ve texto de contexto, no qué lección lo originó — eso lo agrega
     * {@code ConversacionRenasiaService} (ver el javadoc de {@link ChatIAPort}).
     *
     * <p>Si el streaming de Gemini falla a mitad de camino, el {@code Flux} resultante
     * termina en error (nunca llega a emitir su propio {@code Fin}) — sostener la garantía
     * SSE de "fin siempre al final, incluso con error" es responsabilidad del adaptador HTTP
     * ({@code RenasiaController}), no de este puerto.
     */
    @Override
    public Flux<EventoRenasia> responder(String prompt, List<String> contexto) {
        String sistema = promptSistema.render(Map.of("contexto", formatearContexto(contexto)));
        return chatClient.prompt()
                .system(sistema)
                .user(prompt)
                .stream()
                .content()
                .map(GoogleGenAiRenasiaChatAdapter::comoTexto)
                .concatWithValues(new EventoRenasia.Fin());
    }

    private static EventoRenasia comoTexto(String fragmento) {
        return new EventoRenasia.Texto(fragmento);
    }

    private static String formatearContexto(List<String> contexto) {
        if (contexto.isEmpty()) {
            return "(no se recupero contexto de la base de conocimiento para esta pregunta)";
        }
        StringBuilder resultado = new StringBuilder();
        for (String fragmento : contexto) {
            resultado.append("- ").append(fragmento).append('\n');
        }
        return resultado.toString();
    }
}
