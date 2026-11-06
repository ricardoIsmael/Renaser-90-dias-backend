package com.renaser.os.notifications.infrastructure.adapter.out.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;
import com.renaser.os.notifications.infrastructure.config.WhatsAppProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** La plantilla de AUTENTICACION del codigo (spec 043) contra un YCloud falso: la forma del pedido y que sin plantilla no salga nada. */
class YCloudAdapterCodigoTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer servidor;
    private final List<String> cuerpos = new CopyOnWriteArrayList<>();
    private final List<String> rutas = new CopyOnWriteArrayList<>();

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            rutas.add(intercambio.getRequestURI().getPath());
            cuerpos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{\"id\":\"ycloud-9\",\"status\":\"accepted\",\"wamid\":\"wamid.Z\"}".getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(200, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    private YCloudAdapter adaptador(String plantillaCodigo) {
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v2";
        return new YCloudAdapter(new WhatsAppProperties("clave", "", "+51913409458", "renaser_evento_recordatorio_v2", "es", url, 2000, plantillaCodigo));
    }

    // spec 043 FR-004
    @Test
    @DisplayName("el codigo va en el cuerpo y en el boton «Copiar código» de la plantilla de autenticacion, con su nombre y su idioma")
    void formaDelPedido() throws Exception {
        var resultado = adaptador("renaser_codigo_v1").enviarCodigo("+51987654321", "482913");

        assertThat(resultado.estado()).isEqualTo(EstadoDeEntrega.ACEPTADO);
        assertThat(rutas).containsExactly("/v2/whatsapp/messages/sendDirectly");
        JsonNode cuerpo = JSON.readTree(cuerpos.get(0));
        assertThat(cuerpo.get("from").asText()).isEqualTo("+51913409458");
        assertThat(cuerpo.get("to").asText()).isEqualTo("+51987654321");
        assertThat(cuerpo.get("type").asText()).isEqualTo("template");
        JsonNode plantilla = cuerpo.get("template");
        assertThat(plantilla.get("name").asText()).isEqualTo("renaser_codigo_v1");
        assertThat(plantilla.get("language").get("code").asText()).isEqualTo("es");
        JsonNode componentes = plantilla.get("components");
        assertThat(componentes.get(0).get("type").asText()).isEqualTo("body");
        assertThat(componentes.get(0).get("parameters").get(0).get("text").asText()).isEqualTo("482913");
        assertThat(componentes.get(1).get("type").asText()).isEqualTo("button");
        assertThat(componentes.get(1).get("sub_type").asText()).isEqualTo("url");
        assertThat(componentes.get(1).get("index").asText()).isEqualTo("0");
        assertThat(componentes.get(1).get("parameters").get(0).get("text").asText()).isEqualTo("482913");
    }

    // spec 043 FR-002
    @Test
    @DisplayName("sin la plantilla del codigo no esta configurado para codigos y no sale a la red")
    void sinPlantillaNoSale() {
        YCloudAdapter sinPlantilla = adaptador("");

        assertThat(sinPlantilla.configuradoParaCodigos()).isFalse();
        assertThat(sinPlantilla.enviarCodigo("+51987654321", "482913").estado()).isEqualTo(EstadoDeEntrega.FALLIDO);
        assertThat(cuerpos).isEmpty();
        assertThat(adaptador("renaser_codigo_v1").configuradoParaCodigos()).isTrue();
    }
}
