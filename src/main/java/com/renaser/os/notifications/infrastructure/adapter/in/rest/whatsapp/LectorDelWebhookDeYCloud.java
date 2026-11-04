package com.renaser.os.notifications.infrastructure.adapter.in.rest.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.notifications.application.ports.in.whatsapp.AtenderWebhookDeWhatsAppUseCase.EstadoDeUnMensaje;
import com.renaser.os.notifications.application.ports.in.whatsapp.AtenderWebhookDeWhatsAppUseCase.RespuestaRecibida;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Lee el cuerpo del webhook de YCloud (spec 041, FR-026) y lo traduce a lo que el caso de uso entiende. Solo se atienden dos eventos:
 * {@code whatsapp.message.updated} (el estado de un mensaje nuestro) y {@code whatsapp.inbound_message.received} (lo que una persona responde). Cualquier otro
 * se ignora. Los campos que no se usan (texto de conversaciones ajenas, nombres, numeros) no pasan de aca.
 */
@Component
class LectorDelWebhookDeYCloud {

    private static final ObjectMapper JSON = new ObjectMapper();

    sealed interface Aviso permits Estado, Respuesta, Otro {
    }

    record Estado(EstadoDeUnMensaje datos) implements Aviso {
    }

    record Respuesta(RespuestaRecibida datos) implements Aviso {
    }

    record Otro() implements Aviso {
    }

    /** Vacio si el cuerpo no es JSON. */
    Optional<Aviso> leer(byte[] cuerpo) {
        try {
            JsonNode raiz = JSON.readTree(cuerpo);
            if (raiz == null || !raiz.isObject()) {
                return Optional.empty();
            }
            return Optional.of(switch (texto(raiz, "type") == null ? "" : texto(raiz, "type")) {
                case "whatsapp.message.updated" -> new Estado(estado(raiz.path("whatsappMessage")));
                case "whatsapp.inbound_message.received" -> new Respuesta(respuesta(raiz.path("whatsappInboundMessage")));
                default -> new Otro();
            });
        } catch (JsonProcessingException noEsJson) {
            return Optional.empty();
        } catch (IOException ilegible) {
            return Optional.empty();
        }
    }

    private static EstadoDeUnMensaje estado(JsonNode mensaje) {
        String error = primero(texto(mensaje, "errorMessage"), texto(mensaje.path("whatsappApiError"), "message"));
        String codigo = primero(texto(mensaje, "errorCode"), texto(mensaje.path("whatsappApiError"), "code"));
        String motivo = error == null && codigo == null ? null : (codigo == null ? error : (error == null ? codigo : codigo + " · " + error));
        return new EstadoDeUnMensaje(texto(mensaje, "externalId"), texto(mensaje, "id"), texto(mensaje, "status"), motivo, texto(mensaje, "wamid"));
    }

    private static RespuestaRecibida respuesta(JsonNode entrante) {
        String tipo = texto(entrante, "type");
        boolean esBoton = "button".equals(tipo);
        String carga = esBoton ? texto(entrante.path("button"), "payload") : null;
        String texto = primero(texto(entrante.path("text"), "body"), texto(entrante.path("button"), "text"),
                texto(entrante.path("interactive").path("button_reply"), "title"));
        return new RespuestaRecibida(esBoton, carga, texto, texto(entrante.path("context"), "id"), texto(entrante, "from"), texto(entrante, "to"));
    }

    private static String primero(String... valores) {
        for (String valor : valores) {
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo == null ? null : nodo.get(campo);
        return valor == null || valor.isNull() || valor.isContainerNode() || valor.asText().isBlank() ? null : valor.asText().strip();
    }
}
