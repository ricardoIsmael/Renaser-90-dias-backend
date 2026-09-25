package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.rag.application.ports.out.memoria.CompactarConversacionPort;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
import com.renaser.os.rag.domain.model.memoria.CategoriaDeRecuerdo;
import com.renaser.os.rag.domain.model.memoria.Compactacion;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * La compactacion de la memoria sobre Gemini (D-167): una sola llamada, sin herramientas, que
 * devuelve JSON. Lo que devuelve no se guarda tal cual: lo sanea el dominio.
 *
 * <p>El prompt es texto plano ({@code prompts/compactar-memoria.txt}) y no una plantilla: lleva un
 * ejemplo de JSON, y las llaves rompen StringTemplate.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "google")
class CompactarConversacionGeminiAdapter implements CompactarConversacionPort {

    static final String RECURSO_PROMPT = "prompts/compactar-memoria.txt";
    /** Un mensaje larguisimo no puede comerse la llamada entera. */
    private static final int LARGO_MAXIMO_POR_MENSAJE = 1500;
    private static final Map<String, CategoriaDeRecuerdo> CLAVES = Map.of(
            "contexto_de_vida", CategoriaDeRecuerdo.CONTEXTO_DE_VIDA,
            "metas_y_lo_que_funciona", CategoriaDeRecuerdo.METAS_Y_LO_QUE_FUNCIONA,
            "preferencias_de_trato", CategoriaDeRecuerdo.PREFERENCIAS_DE_TRATO);

    /** Propio y no inyectado (E-33: Boot 4 autoconfigura Jackson 3). */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChatClient chatClient;
    private final String instrucciones;

    CompactarConversacionGeminiAdapter(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
        this.instrucciones = leer(RECURSO_PROMPT);
    }

    @Override
    public Compactacion compactar(Entrada entrada) {
        String respuesta = chatClient.prompt().system(instrucciones).user(conversacion(entrada)).call().content();
        return leerCompactacion(respuesta);
    }

    /** Lo que ya sabia y el tramo nuevo, en el orden en que se dijo. */
    static String conversacion(Entrada entrada) {
        StringBuilder texto = new StringBuilder("LO QUE YA SE SABIA:\n").append(entrada.actual().paraElModelo())
                .append("\n\nTRAMO NUEVO DE LA CONVERSACION (del mas viejo al mas nuevo):\n");
        for (MensajeRenasia mensaje : entrada.mensajes()) {
            String contenido = mensaje.contenido();
            texto.append(mensaje.rol() == RolMensaje.USUARIO ? "Persona: " : "Acompanante: ")
                    .append(contenido.length() <= LARGO_MAXIMO_POR_MENSAJE ? contenido
                            : contenido.substring(0, LARGO_MAXIMO_POR_MENSAJE))
                    .append('\n');
        }
        return texto.toString();
    }

    /**
     * Tolera el JSON envuelto en un bloque de codigo. Una categoria que no viene como lista no se
     * toca (se conserva lo que habia); un JSON sin ninguna de las claves esperadas es un fallo, igual
     * que uno ilegible: se reintenta en otro turno en vez de avanzar sin haber leido nada.
     */
    static Compactacion leerCompactacion(String respuesta) {
        String json = respuesta == null ? "" : respuesta.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        JsonNode raiz;
        try {
            raiz = JSON.readTree(json);
        } catch (IOException ilegible) {
            throw new IllegalStateException("La compactacion no devolvio un JSON legible", ilegible);
        }
        Map<CategoriaDeRecuerdo, List<String>> recuerdos = new EnumMap<>(CategoriaDeRecuerdo.class);
        CLAVES.forEach((clave, categoria) -> {
            if (raiz != null && raiz.path(clave).isArray()) {
                recuerdos.put(categoria, textos(raiz.path(clave)));
            }
        });
        boolean conResumen = raiz != null && raiz.path("resumen").isTextual();
        if (recuerdos.isEmpty() && !conResumen) {
            throw new IllegalStateException("La compactacion no devolvio ninguna de las claves esperadas");
        }
        return new Compactacion(conResumen ? raiz.path("resumen").asText() : "", recuerdos);
    }

    private static List<String> textos(JsonNode lista) {
        List<String> textos = new ArrayList<>();
        lista.forEach(elemento -> textos.add(elemento.asText("")));
        return textos;
    }

    private static String leer(String recurso) {
        try {
            return new ClassPathResource(recurso).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + recurso, e);
        }
    }
}
