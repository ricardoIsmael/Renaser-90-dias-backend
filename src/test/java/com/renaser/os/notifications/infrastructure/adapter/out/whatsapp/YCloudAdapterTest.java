package com.renaser.os.notifications.infrastructure.adapter.out.whatsapp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.notifications.domain.model.whatsapp.CausaDeFalla;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;
import com.renaser.os.notifications.domain.model.whatsapp.ResultadoDelEnvio;
import com.renaser.os.notifications.infrastructure.config.WhatsAppProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El adaptador de YCloud contra un servidor falso (spec 041, FR-018, FR-023, FR-028): la forma del pedido, que ningun fallo se reintente y que nada secreto llegue al
 * registro. La clave de aca es de mentira; las pruebas nunca llaman a Meta ni a YCloud.
 */
class YCloudAdapterTest {

    private static final String CLAVE = "clave-de-mentira-que-no-debe-aparecer-en-ningun-log";
    private static final String DESTINO = "+51987654321";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> HUECOS = List.of("Rosa", "Escuela Renaser", "Clase de manifestación", "empieza en 10 min, a las 19:00",
            "Entra aquí: https://meet.google.com/abc-defg-hij");

    private HttpServer servidor;
    private final List<String> metodos = new CopyOnWriteArrayList<>();
    private final List<String> rutas = new CopyOnWriteArrayList<>();
    private final List<String> clavesRecibidas = new CopyOnWriteArrayList<>();
    private final List<String> cuerpos = new CopyOnWriteArrayList<>();
    private final AtomicInteger estado = new AtomicInteger(200);
    private volatile String respuesta = "{\"id\":\"ycloud-1\",\"status\":\"accepted\",\"wamid\":\"wamid.ABC\"}";
    private volatile long demoraMs;
    private ListAppender<ILoggingEvent> registro;
    private Logger logDelAdaptador;

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            metodos.add(intercambio.getRequestMethod());
            rutas.add(intercambio.getRequestURI().getPath());
            clavesRecibidas.add(String.valueOf(intercambio.getRequestHeaders().getFirst("X-API-Key")));
            cuerpos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (demoraMs > 0) {
                try {
                    Thread.sleep(demoraMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            try {
                intercambio.sendResponseHeaders(estado.get(), bytes.length);
                intercambio.getResponseBody().write(bytes);
            } catch (IOException clienteCerro) {
                // El cliente se fue por el tiempo agotado: es lo que se prueba.
            } finally {
                intercambio.close();
            }
        });
        servidor.start();
        registro = new ListAppender<>();
        registro.start();
        logDelAdaptador = (Logger) LoggerFactory.getLogger(YCloudAdapter.class);
        logDelAdaptador.setLevel(Level.DEBUG);
        logDelAdaptador.addAppender(registro);
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
        logDelAdaptador.detachAppender(registro);
    }

    private YCloudAdapter adaptador(String clave, String emisor, int timeoutMs) {
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v2";
        return new YCloudAdapter(new WhatsAppProperties(clave, "secreto-del-webhook", emisor, "renaser_evento_recordatorio_v1", "es", url, timeoutMs));
    }

    private ResultadoDelEnvio enviar(YCloudAdapter adaptador, UUID externalId) {
        return adaptador.enviarRecordatorio(DESTINO, externalId, HUECOS, "BAJA:" + externalId);
    }

    // spec 041 FR-018
    @Test
    @DisplayName("el pedido es un POST a /v2/whatsapp/messages/sendDirectly con X-API-Key, la plantilla, sus cinco huecos y el boton «No recibir más»")
    void laFormaDelPedido() throws Exception {
        UUID entrega = UUID.randomUUID();

        enviar(adaptador(CLAVE, "+51900000000", 2000), entrega);

        assertThat(metodos).containsExactly("POST");
        assertThat(rutas).containsExactly("/v2/whatsapp/messages/sendDirectly");
        assertThat(clavesRecibidas).containsExactly(CLAVE);
        JsonNode cuerpo = JSON.readTree(cuerpos.getFirst());
        assertThat(cuerpo.get("from").asText()).isEqualTo("+51900000000");
        assertThat(cuerpo.get("to").asText()).isEqualTo(DESTINO);
        assertThat(cuerpo.get("type").asText()).isEqualTo("template");
        assertThat(cuerpo.get("externalId").asText()).isEqualTo(entrega.toString());
        JsonNode plantilla = cuerpo.get("template");
        assertThat(plantilla.get("name").asText()).isEqualTo("renaser_evento_recordatorio_v1");
        assertThat(plantilla.get("language").get("code").asText()).isEqualTo("es");
        JsonNode cuerpoDeLaPlantilla = plantilla.get("components").get(0);
        assertThat(cuerpoDeLaPlantilla.get("type").asText()).isEqualTo("body");
        List<String> huecos = new ArrayList<>();
        cuerpoDeLaPlantilla.get("parameters").forEach(p -> huecos.add(p.get("text").asText()));
        assertThat(huecos).containsExactlyElementsOf(HUECOS);
        JsonNode boton = plantilla.get("components").get(1);
        assertThat(boton.get("type").asText()).isEqualTo("button");
        assertThat(boton.get("sub_type").asText()).isEqualTo("quick_reply");
        assertThat(boton.get("index").asInt()).isZero();
        assertThat(boton.get("parameters").get(0).get("payload").asText()).isEqualTo("BAJA:" + entrega);
    }

    // spec 041 FR-022
    @Test
    @DisplayName("una respuesta aceptada trae el id del proveedor y el wamid")
    void aceptado() {
        ResultadoDelEnvio resultado = enviar(adaptador(CLAVE, "+51900000000", 2000), UUID.randomUUID());

        assertThat(resultado.estado()).isEqualTo(EstadoDeEntrega.ACEPTADO);
        assertThat(resultado.proveedorId()).isEqualTo("ycloud-1");
        assertThat(resultado.wamid()).isEqualTo("wamid.ABC");
    }

    // spec 041 FR-022
    @Test
    @DisplayName("un 200 que dice «failed» es un rechazo de WhatsApp, con su motivo")
    void rechazado() {
        respuesta = "{\"id\":\"ycloud-2\",\"status\":\"failed\",\"errorMessage\":\"Template renaser_evento_recordatorio_v1 is not approved\"}";

        ResultadoDelEnvio resultado = enviar(adaptador(CLAVE, "+51900000000", 2000), UUID.randomUUID());

        assertThat(resultado.estado()).isEqualTo(EstadoDeEntrega.FALLIDO);
        assertThat(resultado.causa()).isEqualTo(CausaDeFalla.PLANTILLA);
        assertThat(resultado.motivo()).contains("not approved");
    }

    // spec 041 FR-024
    @Test
    @DisplayName("401 y 403 son «la clave no se acepto»: fallo de Administracion, sin reintento")
    void laClaveNoSeAcepto() {
        for (int http : new int[] {401, 403}) {
            estado.set(http);
            respuesta = "{\"message\":\"invalid api key\"}";
            ResultadoDelEnvio resultado = enviar(adaptador(CLAVE, "+51900000000", 2000), UUID.randomUUID());

            assertThat(resultado.estado()).isEqualTo(EstadoDeEntrega.FALLIDO);
            assertThat(resultado.causa()).isEqualTo(CausaDeFalla.CLAVE);
        }
        assertThat(metodos).as("una llamada por intento, ninguna de mas").hasSize(2);
    }

    // spec 041 FR-024
    @Test
    @DisplayName("un 400 que habla de saldo se reconoce como «sin saldo»")
    void sinSaldo() {
        estado.set(400);
        respuesta = "{\"message\":\"Insufficient balance\"}";

        assertThat(enviar(adaptador(CLAVE, "+51900000000", 2000), UUID.randomUUID()).causa()).isEqualTo(CausaDeFalla.SALDO);
    }

    // spec 041 FR-023
    @Test
    @DisplayName("ni un 429 se reintenta: una sola llamada, fallo «limite»")
    void ni429SeReintenta() {
        estado.set(429);
        respuesta = "{\"message\":\"too many requests\"}";

        ResultadoDelEnvio resultado = enviar(adaptador(CLAVE, "+51900000000", 2000), UUID.randomUUID());

        assertThat(resultado.estado()).isEqualTo(EstadoDeEntrega.FALLIDO);
        assertThat(resultado.causa()).isEqualTo(CausaDeFalla.LIMITE);
        assertThat(metodos).hasSize(1);
    }

    // spec 041 FR-021, FR-023, SC-006
    @Test
    @DisplayName("un 5xx es «no se supo» (pudo haber salido): una sola llamada y nada de reenviar")
    void un5xxNoSeSupo() {
        estado.set(503);
        respuesta = "{\"message\":\"unavailable\"}";

        ResultadoDelEnvio resultado = enviar(adaptador(CLAVE, "+51900000000", 2000), UUID.randomUUID());

        assertThat(resultado.estado()).isEqualTo(EstadoDeEntrega.NO_SE_SUPO);
        assertThat(metodos).hasSize(1);
    }

    // spec 041 FR-021, SC-006
    @Test
    @DisplayName("si YCloud no contesta a tiempo es «no se supo»: sin excepcion y sin segunda llamada")
    void tiempoAgotado() {
        demoraMs = 1500;

        ResultadoDelEnvio resultado = enviar(adaptador(CLAVE, "+51900000000", 300), UUID.randomUUID());

        assertThat(resultado.estado()).isEqualTo(EstadoDeEntrega.NO_SE_SUPO);
        assertThat(metodos).hasSize(1);
    }

    // spec 041 FR-018, FR-028
    @Test
    @DisplayName("sin la clave, el numero de emision o la plantilla el adaptador no esta configurado y no sale a la red")
    void sinConfiguracion() {
        assertThat(adaptador("", "+51900000000", 2000).configurado()).isFalse();
        assertThat(adaptador(CLAVE, "", 2000).configurado()).isFalse();
        assertThat(adaptador(CLAVE, "+51900000000", 2000).configurado()).isTrue();

        ResultadoDelEnvio resultado = enviar(adaptador("", "+51900000000", 2000), UUID.randomUUID());
        adaptador("", "+51900000000", 2000).enviarTexto(DESTINO, "hola");

        assertThat(resultado.estado()).isEqualTo(EstadoDeEntrega.FALLIDO);
        assertThat(metodos).as("no hubo ninguna llamada").isEmpty();
    }

    // spec 041 FR-027
    @Test
    @DisplayName("la respuesta a una baja es un texto normal (sin plantilla) y si falla no lanza")
    void respuestaDeTexto() throws Exception {
        adaptador(CLAVE, "+51900000000", 2000).enviarTexto(DESTINO, "Listo, ya no te enviaremos recordatorios.");
        JsonNode cuerpo = JSON.readTree(cuerpos.getFirst());
        assertThat(cuerpo.get("type").asText()).isEqualTo("text");
        assertThat(cuerpo.get("text").get("body").asText()).isEqualTo("Listo, ya no te enviaremos recordatorios.");

        estado.set(500);
        adaptador(CLAVE, "+51900000000", 2000).enviarTexto(DESTINO, "otra vez");
    }

    // spec 041 FR-028, SC-014
    @Test
    @DisplayName("ningun registro lleva la clave, el cuerpo del mensaje ni el numero completo, ni en un exito ni en un fallo")
    void nadaSecretoEnElRegistro() {
        YCloudAdapter adaptador = adaptador(CLAVE, "+51900000000", 300);
        enviar(adaptador, UUID.randomUUID());
        estado.set(500);
        respuesta = "{\"message\":\"error con +51 987 654 321 y Rosa\"}";
        enviar(adaptador, UUID.randomUUID());
        estado.set(200);
        demoraMs = 1500;
        enviar(adaptador, UUID.randomUUID());
        adaptador.enviarTexto(DESTINO, "Listo, Rosa");

        String todo = String.join("\n", registro.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        assertThat(todo).doesNotContain(CLAVE).doesNotContain("987654321").doesNotContain("987 654 321").doesNotContain("Rosa")
                .doesNotContain("manifestación").doesNotContain("meet.google.com");
    }
}
