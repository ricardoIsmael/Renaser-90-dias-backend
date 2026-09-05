package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.ChatIAPort;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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
 * <p><b>D-102 — un adaptador, dos prompts.</b> El acompanante de los 90 dias habla con
 * {@code prompts/renasia-sistema.st}; Sparkie, el tutor de cursos, con
 * {@code prompts/sparkie-cursos.st}. El {@code switch} sobre {@code AgenteConversacional} es
 * exhaustivo: agregar un tercer agente sin prompt no compila. El mismo {@link ChatModel} (y por
 * lo tanto el mismo modelo configurado y la misma opcion de busqueda web) sirve a los dos — lo que
 * cambia es la identidad y el terreno, no la infraestructura.
 *
 * <p><b>Sin {@code @Transactional} — nunca lo va a tener.</b> Este adaptador no toca
 * persistencia: es el propio {@code ConversacionRenasiaService} el que ya saca a
 * {@code buscarSimilares}/la llamada a IA fuera de cualquier transacción (C-1/C-4, ver su
 * javadoc). Ninguna llamada de este adaptador a Gemini debe quedar envuelta en una
 * transacción abierta por quien lo invoque.
 *
 * <p><b>Los prompts de sistema viven fuera de esta clase</b>, en
 * {@code src/main/resources/prompts/}. Cuando Producto entregue la voz de marca definitiva, se
 * reemplaza el contenido de esos archivos y esta clase no cambia.
 *
 * <p><b>2026-09-04 — la regla de abstención dejó de ser "callarse".</b> La versión anterior
 * obligaba a responder SOLO con el contexto recuperado, y en la práctica el asistente quedaba
 * mudo cada vez que la base de conocimiento no cubría la pregunta. Ahora los prompts definen un
 * orden de fuentes (contenido del programa → búsqueda web → conocimiento general) con
 * atribución obligatoria: lo que se relajó es de dónde puede salir la respuesta, NO el derecho
 * a inventar una cita del programa, que sigue prohibido.
 *
 * <p>La búsqueda web real la habilita {@code renaser.ia.busqueda-web} sobre el
 * {@code GoogleGenAiChatOptions} que arma {@link GoogleGenAiClientesConfig} — este adaptador
 * no la conoce ni la configura; solo se beneficia de que el modelo la traiga encendida.
 *
 * <p>Lo que NO se tocó y no debe tocarse acá: el filtro de lecciones visibles que aplica
 * {@code ConversacionRenasiaService} antes de buscar contexto. Ese no es un límite de tono
 * sino un gate de contenido — evita citarle a un aprendiz del día 3 una lección del día 60.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "google")
class GoogleGenAiRenasiaChatAdapter implements ChatIAPort {

    static final String RECURSO_PROMPT_ACOMPANANTE = "prompts/renasia-sistema.st";
    static final String RECURSO_PROMPT_TUTOR_CURSOS = "prompts/sparkie-cursos.st";

    private final ChatClient chatClient;
    private final PromptTemplate promptAcompanante;
    private final PromptTemplate promptTutorCursos;

    GoogleGenAiRenasiaChatAdapter(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
        this.promptAcompanante = new PromptTemplate(new ClassPathResource(RECURSO_PROMPT_ACOMPANANTE));
        this.promptTutorCursos = new PromptTemplate(new ClassPathResource(RECURSO_PROMPT_TUTOR_CURSOS));
    }

    /**
     * Solo emite {@link EventoRenasia.Texto} (uno por fragmento) y, al completar el stream de
     * Gemini, exactamente un {@link EventoRenasia.Fin}. Nunca emite {@code Fuentes}: este
     * adaptador solo ve texto de contexto, no qué lección lo originó — eso lo agrega
     * {@code ConversacionRenasiaService} (ver el javadoc de {@link ChatIAPort}).
     *
     * <p>Si el streaming de Gemini falla a mitad de camino, el {@code Flux} resultante
     * termina en error (nunca llega a emitir su propio {@code Fin}) — traducirlo a un
     * {@code error} + {@code fin} aptos para el cliente es responsabilidad del caso de uso (D-100).
     *
     * <p>D-100: el historial viaja como turnos previos
     * ({@code ChatClientRequestSpec.messages(List<Message>)}, verificado contra el bytecode de
     * {@code spring-ai-client-chat:2.0.0}). La pregunta viaja limpia, tal como la escribio la
     * persona: es lo que el caso de uso guarda y lo que despues se relee como historial.
     */
    @Override
    public Flux<EventoRenasia> responder(Consulta consulta) {
        return chatClient.prompt()
                .system(promptSistema(consulta))
                .messages(comoTurnos(consulta.historial()))
                .user(consulta.pregunta())
                .stream()
                .content()
                .map(GoogleGenAiRenasiaChatAdapter::comoTexto)
                .concatWithValues(new EventoRenasia.Fin());
    }

    /** D-102: cada agente tiene su prompt; solo el tutor de cursos tiene seccion de ambito. */
    private String promptSistema(Consulta consulta) {
        String contexto = formatearContexto(consulta.contexto());
        return switch (consulta.agente()) {
            case COMPANION -> promptAcompanante.render(Map.of("contexto", contexto));
            case COURSE_TUTOR -> promptTutorCursos.render(Map.of(
                    "contexto", contexto,
                    "ambito", formatearAmbito(consulta.ambito())));
        };
    }

    private static EventoRenasia comoTexto(String fragmento) {
        return new EventoRenasia.Texto(fragmento);
    }

    private static String formatearAmbito(String ambito) {
        if (ambito == null || ambito.isBlank()) {
            return "El cliente no dijo en que curso esta la persona: responde sobre los cursos del programa "
                    + "en general y, si hace falta, preguntale en cual esta.";
        }
        return "La persona esta viendo " + ambito.trim() + ".";
    }

    private static List<Message> comoTurnos(List<MensajeRenasia> historial) {
        return historial.stream()
                .<Message>map(m -> m.rol() == RolMensaje.ASISTENTE
                        ? new AssistantMessage(m.contenido())
                        : new UserMessage(m.contenido()))
                .toList();
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
