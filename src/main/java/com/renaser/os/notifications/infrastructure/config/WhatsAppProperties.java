package com.renaser.os.notifications.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code renaser.whatsapp.*} (spec 041, FR-028): lo que hace falta para hablar con el proveedor de WhatsApp (YCloud). <b>Ninguna clave va en el codigo ni en
 * el repositorio</b>: {@code apiKey} y {@code webhookSecreto} llegan por variable de entorno ({@code WHATSAPP_API_KEY}, {@code WHATSAPP_WEBHOOK_SECRETO}) cargada desde
 * SSM, como el resto de los secretos. {@code numeroEmisor} (el numero NUEVO del dueño, conectado por el en YCloud) no es secreto pero tampoco se versiona.
 *
 * <p>Sin {@code apiKey}, {@code numeroEmisor} y {@code plantilla}, el canal de WhatsApp <b>no hace nada</b> y el chat sigue funcionando. Sin
 * {@code webhookSecreto} el webhook no procesa nada (responde 500): no queda abierto.
 *
 * @param url       la API de YCloud; existe para que las pruebas apunten a un servidor falso
 * @param plantilla el nombre de la plantilla aprobada por Meta; se cambia aca si Meta pide otro nombre
 * @param idioma    el idioma de la plantilla ({@code es})
 * @param timeoutMs lo maximo que se espera la respuesta de YCloud antes de dar el envio por «no se supo»
 */
@ConfigurationProperties(prefix = "renaser.whatsapp")
public record WhatsAppProperties(@DefaultValue("") String apiKey, @DefaultValue("") String webhookSecreto,
                                 @DefaultValue("") String numeroEmisor,
                                 @DefaultValue("renaser_evento_recordatorio_v2") String plantilla,
                                 @DefaultValue("es") String idioma,
                                 @DefaultValue("https://api.ycloud.com/v2") String url,
                                 @DefaultValue("10000") int timeoutMs) {

    /** Hay con que enviar: clave, numero de emision y plantilla. */
    public boolean listoParaEnviar() {
        return !apiKey.isBlank() && !numeroEmisor.isBlank() && !plantilla.isBlank();
    }

    /** Hay con que comprobar la firma del webhook. */
    public boolean tieneSecretoDelWebhook() {
        return !webhookSecreto.isBlank();
    }

    /** Nunca imprime la clave ni el secreto. */
    @Override
    public String toString() {
        return "WhatsAppProperties[plantilla=" + plantilla + ", idioma=" + idioma + ", url=" + url + ", configurado=" + listoParaEnviar() + "]";
    }
}
