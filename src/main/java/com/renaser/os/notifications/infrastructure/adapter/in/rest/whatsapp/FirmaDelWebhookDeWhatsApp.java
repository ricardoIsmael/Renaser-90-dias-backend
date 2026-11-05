package com.renaser.os.notifications.infrastructure.adapter.in.rest.whatsapp;

import com.renaser.os.notifications.domain.model.whatsapp.FirmaDeYCloud;
import com.renaser.os.notifications.infrastructure.config.WhatsAppProperties;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Component;

/**
 * La unica puerta del webhook de WhatsApp (spec 041, FR-026): lo abre el proveedor, que no tiene sesion, asi que la autenticacion es la firma
 * {@code YCloud-Signature}. <b>Sin el secreto configurado no procesa nada</b> (500, no queda abierto) y sin firma valida o con mas de 5 minutos de diferencia, tampoco (401).
 */
@Component
class FirmaDelWebhookDeWhatsApp {

    enum Veredicto { SIN_SECRETO, FIRMA_INVALIDA, VALIDA }

    private final WhatsAppProperties propiedades;
    private final Clock clock;

    FirmaDelWebhookDeWhatsApp(WhatsAppProperties propiedades, Clock clock) {
        this.propiedades = propiedades;
        this.clock = clock;
    }

    Veredicto revisar(byte[] cuerpo, String cabecera) {
        if (!propiedades.tieneSecretoDelWebhook()) {
            return Veredicto.SIN_SECRETO;
        }
        return FirmaDeYCloud.valida(cuerpo, cabecera, propiedades.webhookSecreto(), clock.now()) ? Veredicto.VALIDA : Veredicto.FIRMA_INVALIDA;
    }
}
