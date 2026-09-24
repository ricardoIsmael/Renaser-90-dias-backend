package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Los mensajes JSON del WebSocket de Gemini Live (D-162), en funciones puras: armar lo que se
 * manda y leer lo que llega. Separados del socket para poder probarlos sin red.
 *
 * <p>Formato verificado con la key real el 2026-09-24 (Fase 0 de
 * {@code docs/arquitectura/PROPUESTA_GEMINI_LIVE.md}): {@code setup} → {@code setupComplete},
 * {@code realtimeInput.audio}, {@code serverContent} (audio, transcripciones, {@code interrupted},
 * {@code turnComplete}), {@code toolCall} → {@code toolResponse}, {@code goAway}.
 *
 * <p>Jackson 2 propio ({@code com.fasterxml}), por lo mismo que {@code GeminiVozAdapter} (E-33).
 */
final class MensajesGeminiLive {

    private static final ObjectMapper JSON = new ObjectMapper();
    static final String TIPO_DE_AUDIO = "audio/pcm;rate=16000";

    private MensajesGeminiLive() {
    }

    /** Lo que se lee de un mensaje del servidor. Un solo mensaje puede traer varias cosas. */
    sealed interface DelServidor {
        record SetupCompleto() implements DelServidor { }
        record Audio(byte[] pcm24kHz) implements DelServidor { }
        record Oido(String texto) implements DelServidor { }
        record Dicho(String texto) implements DelServidor { }
        record Interrumpido() implements DelServidor { }
        record TurnoCompleto() implements DelServidor { }
        record PedidoDeHerramienta(String id, InvocacionHerramienta invocacion) implements DelServidor { }
        /** Google avisa que va a cortar la conexion pronto. */
        record Adios() implements DelServidor { }
    }

    /**
     * Deteccion de voz (E-239). El microfono queda abierto entre turnos, y con la sensibilidad por
     * defecto el ruido del cuarto disparaba turnos que el modelo transcribia en otro idioma. Baja
     * sensibilidad al arranque; un colchon de audio antes de la voz para no comerse la primera silaba;
     * 800 ms de silencio para dar por terminado lo que dijo la persona (el valor del servidor).
     *
     * <p>Corregido 2026-09-24 (E-243): el colchon era de 200 ms y con la sensibilidad baja el detector
     * arranca tarde: "Desactiva el habito..." llego como "Activa el habito..." y el orbe contesto lo
     * contrario de lo pedido. Con 600 ms entra la primera silaba aunque la deteccion tarde.
     */
    static final String SENSIBILIDAD_DE_ARRANQUE = "START_SENSITIVITY_LOW";
    static final int COLCHON_ANTES_DE_LA_VOZ_MS = 600;
    static final int SILENCIO_FIN_DE_TURNO_MS = 800;

    static String setup(String modelo, String voz, String prompt, List<DefinicionHerramienta> herramientas) {
        ObjectNode setup = JSON.createObjectNode();
        setup.put("model", modelo.startsWith("models/") ? modelo : "models/" + modelo);
        ObjectNode generacion = setup.putObject("generationConfig");
        generacion.putArray("responseModalities").add("AUDIO");
        generacion.putObject("speechConfig").putObject("voiceConfig").putObject("prebuiltVoiceConfig")
                .put("voiceName", voz);
        setup.putObject("systemInstruction").putArray("parts").addObject().put("text", prompt);
        ObjectNode deteccion = setup.putObject("realtimeInputConfig").putObject("automaticActivityDetection");
        deteccion.put("startOfSpeechSensitivity", SENSIBILIDAD_DE_ARRANQUE);
        deteccion.put("prefixPaddingMs", COLCHON_ANTES_DE_LA_VOZ_MS);
        deteccion.put("silenceDurationMs", SILENCIO_FIN_DE_TURNO_MS);
        setup.putObject("inputAudioTranscription");
        setup.putObject("outputAudioTranscription");
        if (!herramientas.isEmpty()) {
            ArrayNode declaraciones = setup.putArray("tools").addObject().putArray("functionDeclarations");
            herramientas.forEach(h -> declaraciones.add(declaracion(h)));
        }
        ObjectNode raiz = JSON.createObjectNode();
        raiz.set("setup", setup);
        return escribir(raiz);
    }

    static String audio(byte[] pcm16kHz) {
        ObjectNode raiz = JSON.createObjectNode();
        raiz.putObject("realtimeInput").putObject("audio")
                .put("data", Base64.getEncoder().encodeToString(pcm16kHz))
                .put("mimeType", TIPO_DE_AUDIO);
        return escribir(raiz);
    }

    /** Mismo objeto que recibe el chat ({@code HerramientaToolCallback}): {@code ok} y {@code resultado}. */
    static String respuestaDeHerramienta(String id, String nombre, ResultadoHerramienta resultado) {
        ObjectNode respuesta = JSON.createObjectNode();
        switch (resultado) {
            case ResultadoHerramienta.Exito exito -> respuesta.put("ok", true).put("resultado", exito.contenido());
            case ResultadoHerramienta.Fallo fallo -> respuesta.put("ok", false).put("resultado", fallo.motivo());
        }
        ObjectNode raiz = JSON.createObjectNode();
        ObjectNode funcion = raiz.putObject("toolResponse").putArray("functionResponses").addObject();
        funcion.put("id", id).put("name", nombre);
        funcion.set("response", respuesta);
        return escribir(raiz);
    }

    static List<DelServidor> leer(String json) {
        JsonNode raiz = leerArbol(json);
        List<DelServidor> leidos = new ArrayList<>();
        if (raiz.has("setupComplete")) {
            leidos.add(new DelServidor.SetupCompleto());
        }
        leerContenido(raiz.path("serverContent"), leidos);
        for (JsonNode llamada : raiz.path("toolCall").path("functionCalls")) {
            leidos.add(new DelServidor.PedidoDeHerramienta(llamada.path("id").asText(""),
                    new InvocacionHerramienta(llamada.path("name").asText(""), comoArgumentos(llamada.path("args")))));
        }
        if (raiz.has("goAway")) {
            leidos.add(new DelServidor.Adios());
        }
        return leidos;
    }

    private static void leerContenido(JsonNode contenido, List<DelServidor> leidos) {
        if (contenido.isMissingNode()) {
            return;
        }
        agregarTexto(contenido.path("inputTranscription").path("text"), leidos, true);
        for (JsonNode parte : contenido.path("modelTurn").path("parts")) {
            String datos = parte.path("inlineData").path("data").asText("");
            if (!datos.isEmpty()) {
                leidos.add(new DelServidor.Audio(Base64.getDecoder().decode(datos)));
            }
        }
        agregarTexto(contenido.path("outputTranscription").path("text"), leidos, false);
        if (contenido.path("interrupted").asBoolean(false)) {
            leidos.add(new DelServidor.Interrumpido());
        }
        if (contenido.path("turnComplete").asBoolean(false)) {
            leidos.add(new DelServidor.TurnoCompleto());
        }
    }

    private static void agregarTexto(JsonNode texto, List<DelServidor> leidos, boolean esDeLaPersona) {
        String valor = texto.asText("");
        if (!valor.isEmpty()) {
            leidos.add(esDeLaPersona ? new DelServidor.Oido(valor) : new DelServidor.Dicho(valor));
        }
    }

    /**
     * El esquema de parametros en el formato de Gemini (tipos en mayusculas). Todo {@code STRING},
     * por lo mismo que en el chat ({@code HerramientaToolCallback}): lo que emite un modelo llega
     * como texto y la validacion real la hace el caso de uso. Sin parametros no se manda
     * {@code parameters}: un {@code OBJECT} sin propiedades lo rechaza la API.
     */
    private static ObjectNode declaracion(DefinicionHerramienta herramienta) {
        ObjectNode declaracion = JSON.createObjectNode();
        declaracion.put("name", herramienta.nombre()).put("description", herramienta.descripcion());
        if (herramienta.parametros().isEmpty()) {
            return declaracion;
        }
        ObjectNode parametros = declaracion.putObject("parameters").put("type", "OBJECT");
        ObjectNode propiedades = parametros.putObject("properties");
        ArrayNode obligatorios = parametros.putArray("required");
        for (ParametroHerramienta parametro : herramienta.parametros()) {
            propiedades.putObject(parametro.nombre()).put("type", "STRING").put("description", parametro.descripcion());
            if (parametro.obligatorio()) {
                obligatorios.add(parametro.nombre());
            }
        }
        return declaracion;
    }

    /** Un {@code null} o un objeto anidado no pueden tumbar la conversacion: se pasan como texto. */
    private static Map<String, String> comoArgumentos(JsonNode argumentos) {
        Map<String, String> leidos = new LinkedHashMap<>();
        argumentos.properties().forEach(entrada -> {
            JsonNode valor = entrada.getValue();
            if (valor != null && !valor.isNull()) {
                leidos.put(entrada.getKey(), valor.isValueNode() ? valor.asText() : valor.toString());
            }
        });
        return leidos;
    }

    private static JsonNode leerArbol(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String escribir(JsonNode nodo) {
        try {
            return JSON.writeValueAsString(nodo);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
