package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que recibe el modelo cuando pide una herramienta que no existe (E-247).
 *
 * <p>Spring AI busca la herramienta por nombre y, si no la encuentra, LANZA
 * {@code IllegalStateException: No ToolCallback found for tool name}: el turno entero se cae y la
 * persona se queda sin respuesta. Paso dos veces en la bateria del 2026-09-25: el modelo pidio
 * {@code proponer_horario_por_dia_semana} (le comio el "de" a {@code proponer_horario_por_dia_de_semana}).
 * Con este resolver el modelo recibe un {@code ok=false} legible con el nombre que quiso decir, y
 * lo vuelve a intentar. Ninguna herramienta se ejecuta por parecido: solo se sugiere.
 *
 * <p>Los nombres se leen en cada llamada y no al construir: el modelo de chat se arma antes que
 * las herramientas, y algunas herramientas usan el modelo (un ciclo de beans si se pidieran aca).
 */
class ResolverDeHerramientasDesconocidas implements ToolCallbackResolver {

    private static final Logger log = LoggerFactory.getLogger(ResolverDeHerramientasDesconocidas.class);

    /** Mas lejos que esto ya no es un error de tipeo sino otra cosa: no se sugiere nada. */
    static final int DISTANCIA_MAXIMA_PARA_SUGERIR = 6;

    /** Propio y no inyectado, igual que en el adaptador de chat (E-33: Boot 4 autoconfigura Jackson 3). */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectProvider<EjecutarHerramientaAgenteUseCase> herramientas;

    ResolverDeHerramientasDesconocidas(ObjectProvider<EjecutarHerramientaAgenteUseCase> herramientas) {
        this.herramientas = herramientas;
    }

    @Override
    public ToolCallback resolve(String nombrePedido) {
        Optional<String> sugerencia = sugerenciaPara(nombrePedido);
        log.warn("[rag] el modelo pidio la herramienta '{}', que no existe; sugerencia: {}", nombrePedido,
                sugerencia.orElse("ninguna"));
        return new HerramientaDesconocida(nombrePedido, mensaje(nombrePedido, sugerencia));
    }

    Optional<String> sugerenciaPara(String nombrePedido) {
        EjecutarHerramientaAgenteUseCase casoDeUso = herramientas.getIfAvailable();
        if (casoDeUso == null || nombrePedido == null) {
            return Optional.empty();
        }
        List<String> nombres = casoDeUso.disponibles(AgenteConversacional.COMPANION).stream()
                .map(DefinicionHerramienta::nombre).toList();
        return nombres.stream()
                .min(Comparator.comparingInt(nombre -> distancia(nombre, nombrePedido)))
                .filter(nombre -> distancia(nombre, nombrePedido) <= DISTANCIA_MAXIMA_PARA_SUGERIR);
    }

    private static String mensaje(String nombrePedido, Optional<String> sugerencia) {
        return sugerencia.map(nombre -> "No existe una herramienta llamada '" + nombrePedido + "'. Quisiste decir '"
                        + nombre + "': vuelve a llamarla con ese nombre exacto.")
                .orElse("No existe una herramienta llamada '" + nombrePedido + "'. Usa solo los nombres exactos de "
                        + "las herramientas que tienes declaradas.");
    }

    /** Distancia de edicion (Levenshtein): cuantas letras hay que agregar, quitar o cambiar. */
    static int distancia(String a, String b) {
        int[] previa = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previa[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cambio = previa[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                actual[j] = Math.min(cambio, Math.min(previa[j] + 1, actual[j - 1] + 1));
            }
            int[] cambioDeFila = previa;
            previa = actual;
            actual = cambioDeFila;
        }
        return previa[b.length()];
    }

    /** Una herramienta que solo sabe decir que no existe, como objeto JSON (Gemini lo lee como Struct). */
    private record HerramientaDesconocida(String nombrePedido, String mensaje) implements ToolCallback {

        private static final String ESQUEMA_SIN_PARAMETROS = "{\"type\":\"object\",\"properties\":{}}";

        @Override
        public ToolDefinition getToolDefinition() {
            return DefaultToolDefinition.builder().name(nombrePedido).description(mensaje)
                    .inputSchema(ESQUEMA_SIN_PARAMETROS).build();
        }

        @Override
        public String call(String argumentosJson) {
            try {
                return JSON.writeValueAsString(Map.of("ok", false, "resultado", mensaje));
            } catch (Exception e) {
                return "{\"ok\":false,\"resultado\":\"No existe esa herramienta.\"}";
            }
        }
    }
}
