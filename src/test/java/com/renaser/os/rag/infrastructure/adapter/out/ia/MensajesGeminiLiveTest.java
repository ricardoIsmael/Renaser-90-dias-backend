package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.rag.infrastructure.adapter.out.ia.MensajesGeminiLive.DelServidor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** La forma exacta de los mensajes de Gemini Live, verificada contra la API real en la Fase 0 (D-162). */
class MensajesGeminiLiveTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode arbol(String json) throws Exception {
        return JSON.readTree(json);
    }

    @Test
    @DisplayName("setup: modelo con prefijo models/, solo audio, voz Kore, prompt, transcripciones y herramientas")
    void setup() throws Exception {
        DefinicionHerramienta sinParametros = DefinicionHerramienta.sinParametros("consultar_habitos_del_dia", "Habitos.");
        DefinicionHerramienta conParametro = new DefinicionHerramienta("marcar_habito_completado", "Marca.",
                List.of(ParametroHerramienta.obligatorio("registro_id", TipoParametroHerramienta.IDENTIFICADOR, "El id.")));

        JsonNode setup = arbol(MensajesGeminiLive.setup("gemini-3.8-live", "Kore", "Eres el acompanante.",
                List.of(sinParametros, conParametro))).path("setup");

        assertThat(setup.path("model").asText()).isEqualTo("models/gemini-3.8-live");
        assertThat(setup.path("generationConfig").path("responseModalities").get(0).asText()).isEqualTo("AUDIO");
        assertThat(setup.at("/generationConfig/speechConfig/voiceConfig/prebuiltVoiceConfig/voiceName").asText())
                .isEqualTo("Kore");
        assertThat(setup.at("/systemInstruction/parts/0/text").asText()).isEqualTo("Eres el acompanante.");
        assertThat(setup.has("inputAudioTranscription")).isTrue();
        assertThat(setup.has("outputAudioTranscription")).isTrue();
        JsonNode declaraciones = setup.at("/tools/0/functionDeclarations");
        assertThat(declaraciones.get(0).path("name").asText()).isEqualTo("consultar_habitos_del_dia");
        assertThat(declaraciones.get(0).has("parameters")).isFalse();
        assertThat(declaraciones.get(1).at("/parameters/type").asText()).isEqualTo("OBJECT");
        assertThat(declaraciones.get(1).at("/parameters/properties/registro_id/type").asText()).isEqualTo("STRING");
        assertThat(declaraciones.get(1).at("/parameters/required/0").asText()).isEqualTo("registro_id");
    }

    @Test
    @DisplayName("sin herramientas no se manda tools")
    void setupSinHerramientas() throws Exception {
        JsonNode setup = arbol(MensajesGeminiLive.setup("models/x", "Kore", "p", List.of())).path("setup");

        assertThat(setup.path("model").asText()).isEqualTo("models/x");
        assertThat(setup.has("tools")).isFalse();
    }

    @Test
    @DisplayName("audio: realtimeInput con base64 y audio/pcm;rate=16000")
    void audio() throws Exception {
        JsonNode audio = arbol(MensajesGeminiLive.audio(new byte[]{1, 2, 3})).at("/realtimeInput/audio");

        assertThat(Base64.getDecoder().decode(audio.path("data").asText())).containsExactly(1, 2, 3);
        assertThat(audio.path("mimeType").asText()).isEqualTo("audio/pcm;rate=16000");
    }

    @Test
    @DisplayName("respuesta de herramienta: el mismo id y nombre, con ok y resultado")
    void respuestaDeHerramienta() throws Exception {
        JsonNode respuesta = arbol(MensajesGeminiLive.respuestaDeHerramienta("id-1", "consultar_habitos_del_dia",
                ResultadoHerramienta.fallo("No encontre ese habito."))).at("/toolResponse/functionResponses/0");

        assertThat(respuesta.path("id").asText()).isEqualTo("id-1");
        assertThat(respuesta.path("name").asText()).isEqualTo("consultar_habitos_del_dia");
        assertThat(respuesta.at("/response/ok").asBoolean(true)).isFalse();
        assertThat(respuesta.at("/response/resultado").asText()).isEqualTo("No encontre ese habito.");
    }

    @Test
    @DisplayName("lee setupComplete")
    void leeSetupCompleto() {
        assertThat(MensajesGeminiLive.leer("{\"setupComplete\":{}}")).containsExactly(new DelServidor.SetupCompleto());
    }

    @Test
    @DisplayName("lee de un mismo mensaje la transcripcion de entrada, el audio, la de salida y el fin del turno")
    void leeContenido() {
        String audio = Base64.getEncoder().encodeToString(new byte[]{9, 8});
        List<DelServidor> leidos = MensajesGeminiLive.leer("{\"serverContent\":{"
                + "\"inputTranscription\":{\"text\":\" Hola\"},"
                + "\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"" + audio + "\"}}]},"
                + "\"outputTranscription\":{\"text\":\"Buenas\"},"
                + "\"turnComplete\":true}}");

        assertThat(leidos).hasSize(4);
        assertThat(leidos.get(0)).isEqualTo(new DelServidor.Oido(" Hola"));
        assertThat(((DelServidor.Audio) leidos.get(1)).pcm24kHz()).containsExactly(9, 8);
        assertThat(leidos.get(2)).isEqualTo(new DelServidor.Dicho("Buenas"));
        assertThat(leidos.get(3)).isEqualTo(new DelServidor.TurnoCompleto());
    }

    @Test
    @DisplayName("lee interrupted y goAway")
    void leeInterrupcionYAdios() {
        assertThat(MensajesGeminiLive.leer("{\"serverContent\":{\"interrupted\":true}}"))
                .containsExactly(new DelServidor.Interrumpido());
        assertThat(MensajesGeminiLive.leer("{\"goAway\":{\"timeLeft\":\"10s\"}}")).containsExactly(new DelServidor.Adios());
    }

    @Test
    @DisplayName("lee toolCall con sus argumentos como texto; un null se descarta y un objeto viaja como JSON")
    void leePedidoDeHerramienta() {
        List<DelServidor> leidos = MensajesGeminiLive.leer("{\"toolCall\":{\"functionCalls\":[{\"id\":\"c-1\","
                + "\"name\":\"marcar_habito_completado\",\"args\":{\"registro_id\":\"abc\",\"n\":3,\"nada\":null,"
                + "\"obj\":{\"a\":1}}}]}}");

        assertThat(leidos).containsExactly(new DelServidor.PedidoDeHerramienta("c-1",
                new InvocacionHerramienta("marcar_habito_completado",
                        Map.of("registro_id", "abc", "n", "3", "obj", "{\"a\":1}"))));
    }

    @Test
    @DisplayName("un mensaje que no conoce (usageMetadata, generationComplete) no produce nada")
    void ignoraLoDesconocido() {
        assertThat(MensajesGeminiLive.leer("{\"usageMetadata\":{\"totalTokenCount\":10}}")).isEmpty();
        assertThat(MensajesGeminiLive.leer("{\"serverContent\":{\"generationComplete\":true}}")).isEmpty();
    }

    @Test
    @DisplayName("setup: detector de voz poco sensible al arranque, con colchon y silencio de fin de turno (E-239)")
    void setupConDeteccionDeVozPocoSensible() throws Exception {
        JsonNode deteccion = arbol(MensajesGeminiLive.setup("gemini-3.8-live", "Kore", "p", List.of()))
                .at("/setup/realtimeInputConfig/automaticActivityDetection");

        assertThat(deteccion.path("startOfSpeechSensitivity").asText()).isEqualTo("START_SENSITIVITY_LOW");
        // E-243: con 200 ms la primera silaba se perdia ("Desactiva" llegaba como "Activa").
        assertThat(deteccion.path("prefixPaddingMs").asInt()).isEqualTo(600);
        assertThat(deteccion.path("silenceDurationMs").asInt()).isEqualTo(800);
        // La deteccion automatica sigue activa: no se manda `disabled`.
        assertThat(deteccion.has("disabled")).isFalse();
    }
}
