package com.renaser.os.notifications.infrastructure.adapter.in.rest.whatsapp;

import com.renaser.os.notifications.application.ports.in.whatsapp.AtenderWebhookDeWhatsAppUseCase;
import com.renaser.os.notifications.domain.model.whatsapp.FirmaDeYCloud;
import com.renaser.os.notifications.infrastructure.config.WhatsAppProperties;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.shared.web.security.SinInterruptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El webhook de YCloud (spec 041, FR-026, SC-010): su unica puerta es la firma. Aqui se prueba el controller con la firma REAL y el caso de uso grabando lo que le llega;
 * el secreto es de mentira, ninguna clave va en el codigo.
 */
class WebhookDeWhatsAppControllerTest {

    private static final String SECRETO = "whsec_de-mentira";
    private static final Instant AHORA = Instant.parse("2026-11-04T20:00:00Z");

    private final List<AtenderWebhookDeWhatsAppUseCase.EstadoDeUnMensaje> estados = new ArrayList<>();
    private final List<AtenderWebhookDeWhatsAppUseCase.RespuestaRecibida> respuestas = new ArrayList<>();
    private WebhookDeWhatsAppController controller;

    @BeforeEach
    void armar() {
        controller = controllerConSecreto(SECRETO);
    }

    private WebhookDeWhatsAppController controllerConSecreto(String secreto) {
        var propiedades = new WhatsAppProperties("clave", secreto, "+51900000000", "plantilla", "es", "http://localhost", 1000, "renaser_codigo_v1");
        var atender = new AtenderWebhookDeWhatsAppUseCase() {
            @Override
            public void estadoDeUnMensaje(EstadoDeUnMensaje estado) {
                estados.add(estado);
            }

            @Override
            public void respuestaRecibida(RespuestaRecibida respuesta) {
                respuestas.add(respuesta);
            }
        };
        return new WebhookDeWhatsAppController(new FirmaDelWebhookDeWhatsApp(propiedades, FixedClock.at(AHORA)), new LectorDelWebhookDeYCloud(), atender);
    }

    private static byte[] cuerpo(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String firma(byte[] cuerpo, Instant t) {
        String segundos = String.valueOf(t.getEpochSecond());
        return "t=" + segundos + ",s=" + FirmaDeYCloud.firmar(SECRETO, segundos, cuerpo);
    }

    private static final String ESTADO = """
            {"type":"whatsapp.message.updated","whatsappMessage":{"id":"ycloud-1","externalId":"7d3b6f0e-7f0a-4a52-9d2e-7c9b1f9d1111",
             "status":"delivered","wamid":"wamid.ABC","errorMessage":null}}""";
    private static final String BOTON = """
            {"type":"whatsapp.inbound_message.received","whatsappInboundMessage":{"from":"+51987654321","to":"+51900000000","type":"button",
             "button":{"payload":"BAJA:7d3b6f0e-7f0a-4a52-9d2e-7c9b1f9d1111","text":"No recibir más"},"context":{"id":"wamid.ABC","from":"+51900000000"}}}""";
    private static final String TEXTO = """
            {"type":"whatsapp.inbound_message.received","whatsappInboundMessage":{"from":"+51987654321","to":"+51900000000","type":"text",
             "text":{"body":"BAJA"},"context":{"id":"wamid.ABC"}}}""";

    // spec 041 FR-026
    @Test
    @DisplayName("una firma valida de un aviso de estado lo atiende y responde 200")
    void unEstado() {
        byte[] cuerpo = cuerpo(ESTADO);

        var respuesta = controller.recibir(cuerpo, firma(cuerpo, AHORA));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(estados).singleElement().satisfies(e -> {
            assertThat(e.externalId()).isEqualTo("7d3b6f0e-7f0a-4a52-9d2e-7c9b1f9d1111");
            assertThat(e.proveedorId()).isEqualTo("ycloud-1");
            assertThat(e.estado()).isEqualTo("delivered");
            assertThat(e.wamid()).isEqualTo("wamid.ABC");
        });
        assertThat(respuestas).isEmpty();
    }

    // spec 041 FR-026, FR-027
    @Test
    @DisplayName("el toque de un boton llega con su carga y el wamid del mensaje al que responde")
    void unBoton() {
        byte[] cuerpo = cuerpo(BOTON);

        controller.recibir(cuerpo, firma(cuerpo, AHORA));

        assertThat(respuestas).singleElement().satisfies(r -> {
            assertThat(r.esBoton()).isTrue();
            assertThat(r.carga()).isEqualTo("BAJA:7d3b6f0e-7f0a-4a52-9d2e-7c9b1f9d1111");
            assertThat(r.respondeAWamid()).isEqualTo("wamid.ABC");
            assertThat(r.de()).isEqualTo("+51987654321");
        });
    }

    // spec 041 FR-026, FR-027
    @Test
    @DisplayName("un texto llega con su contenido y el mensaje al que responde")
    void unTexto() {
        byte[] cuerpo = cuerpo(TEXTO);

        controller.recibir(cuerpo, firma(cuerpo, AHORA));

        assertThat(respuestas).singleElement().satisfies(r -> {
            assertThat(r.esBoton()).isFalse();
            assertThat(r.texto()).isEqualTo("BAJA");
            assertThat(r.respondeAWamid()).isEqualTo("wamid.ABC");
        });
    }

    // spec 041 FR-026, SC-010
    @Test
    @DisplayName("una firma invalida se rechaza con 401 y no se procesa nada")
    void firmaInvalida() {
        byte[] cuerpo = cuerpo(ESTADO);

        assertThat(controller.recibir(cuerpo, "t=" + AHORA.getEpochSecond() + ",s=" + "0".repeat(64)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.recibir(cuerpo, null).getStatusCode()).as("sin cabecera").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(estados).isEmpty();
    }

    // spec 041 FR-026, SC-010
    @Test
    @DisplayName("una firma de hace mas de 5 minutos se rechaza")
    void firmaVieja() {
        byte[] cuerpo = cuerpo(ESTADO);

        var respuesta = controller.recibir(cuerpo, firma(cuerpo, AHORA.minusSeconds(301)));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(estados).isEmpty();
    }

    // spec 041 FR-026, SC-010
    @Test
    @DisplayName("un cuerpo alterado despues de firmar se rechaza")
    void cuerpoAlterado() {
        byte[] original = cuerpo(TEXTO);
        String cabecera = firma(original, AHORA);
        byte[] alterado = cuerpo(TEXTO.replace("BAJA", "OTRA"));

        assertThat(controller.recibir(alterado, cabecera).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuestas).isEmpty();
    }

    // spec 041 FR-026, SC-010
    @Test
    @DisplayName("sin el secreto configurado no procesa nada y responde 500: no queda abierto")
    void sinSecreto() {
        var sinSecreto = controllerConSecreto("");
        byte[] cuerpo = cuerpo(ESTADO);

        var respuesta = sinSecreto.recibir(cuerpo, firma(cuerpo, AHORA));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(estados).isEmpty();
    }

    // spec 041 FR-026
    @Test
    @DisplayName("un evento que no se usa se acepta (200) y no toca nada; un cuerpo que no es JSON se rechaza con 400")
    void otrosEventosYBasura() {
        byte[] otro = cuerpo("{\"type\":\"whatsapp.template.reviewed\"}");
        byte[] basura = cuerpo("esto no es json");

        assertThat(controller.recibir(otro, firma(otro, AHORA)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.recibir(basura, firma(basura, AHORA)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(estados).isEmpty();
        assertThat(respuestas).isEmpty();
    }

    // spec 041 FR-026
    @Test
    @DisplayName("la ruta es publica (sin sesion), esta declarada como tal y no tiene interruptor: una baja se respeta siempre")
    void declaraSuApertura() throws NoSuchMethodException {
        var metodo = WebhookDeWhatsAppController.class.getDeclaredMethod("recibir", byte[].class, String.class);

        assertThat(metodo.isAnnotationPresent(PublicEndpoint.class)).isTrue();
        assertThat(metodo.isAnnotationPresent(PostMapping.class)).isTrue();
        assertThat(WebhookDeWhatsAppController.class.isAnnotationPresent(SinInterruptor.class)).isTrue();
    }
}
