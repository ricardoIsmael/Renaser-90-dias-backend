package com.renaser.os.notifications.application.ports.in.whatsapp;

/**
 * Lo que YCloud avisa al webhook (spec 041, FR-026 y FR-027): el estado de un mensaje nuestro y lo que una persona responde. La firma ya la comprobo
 * quien recibe el pedido HTTP; aca solo se atiende. Atender el mismo aviso dos veces no hace dano (el estado no retrocede y una baja dos veces es una baja).
 */
public interface AtenderWebhookDeWhatsAppUseCase {

    /** El estado de un mensaje (entregado, leido, fallo). Lo que no es un mensaje nuestro no toca nada. */
    void estadoDeUnMensaje(EstadoDeUnMensaje estado);

    /** Lo que una persona respondio: el toque de «No recibir más» o un texto. Solo se atiende la baja; nada de lo recibido se guarda. */
    void respuestaRecibida(RespuestaRecibida respuesta);

    /**
     * @param externalId  nuestro id de la entrega (el {@code externalId} que mandamos), o {@code null}
     * @param proveedorId el id del mensaje en YCloud, o {@code null}
     * @param estado      el estado como lo dice YCloud ({@code accepted}, {@code sent}, {@code delivered}, {@code read}, {@code failed})
     * @param error       el motivo corto si fallo, o {@code null}
     * @param wamid       el id del mensaje en WhatsApp si ya paso a WhatsApp, o {@code null}
     */
    record EstadoDeUnMensaje(String externalId, String proveedorId, String estado, String error, String wamid) {
    }

    /**
     * @param esBoton        si es el toque de un boton de respuesta rapida
     * @param carga          la carga del boton ({@code BAJA:<id>}), si es un boton
     * @param texto          el texto que escribio la persona (o el titulo del boton)
     * @param respondeAWamid el id del mensaje al que responde ({@code context.id}), o {@code null} si no responde a ninguno
     * @param de             el numero de quien escribe (no se guarda)
     * @param para           nuestro numero, al que escribio
     */
    record RespuestaRecibida(boolean esBoton, String carga, String texto, String respondeAWamid, String de, String para) {
    }
}
