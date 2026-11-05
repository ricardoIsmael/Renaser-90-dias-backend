package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.application.ports.out.preferencia.SavePreferenciaPort;
import com.renaser.os.notifications.application.ports.out.whatsapp.EnvioDeWhatsAppPort;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * La baja de los recordatorios por WhatsApp (spec 041, FR-012 y FR-027): apaga {@code RECORDATORIO_EVENTO_WHATSAPP} de la persona, el unico lugar donde vive el
 * permiso (el mismo interruptor que ve en Yo → Notificaciones), y le contesta UNA vez. Los mensajes del chat y los avisos de la app dependen de
 * {@code RECORDATORIO_EVENTO} y siguen.
 *
 * <p>Un segundo toque (o un «BAJA» repetido) no vuelve a contestar. Nada de lo que la persona escribe se guarda.
 */
@Component
class BajaDeWhatsAppService {

    static final String CONFIRMACION = "Listo, ya no te enviaremos recordatorios de eventos por WhatsApp. "
            + "Los seguirás viendo en tu chat de Renaser y en los avisos de la app; puedes apagarlos en Yo → Notificaciones. "
            + "Si cambias de opinión, enciéndelo ahí mismo.";
    private static final Logger log = LoggerFactory.getLogger(BajaDeWhatsAppService.class);

    private final LoadPreferenciasPort lectura;
    private final SavePreferenciaPort escritura;
    private final EnvioDeWhatsAppPort envio;
    private final Clock clock;

    BajaDeWhatsAppService(LoadPreferenciasPort lectura, SavePreferenciaPort escritura, EnvioDeWhatsAppPort envio, Clock clock) {
        this.lectura = lectura;
        this.escritura = escritura;
        this.envio = envio;
        this.clock = clock;
    }

    /**
     * @param quien      la persona dueña de la entrega a la que respondio o cuyo boton toco
     * @param numeroDeLaRespuesta a donde contestar (el numero de quien escribio); {@code null} = no se contesta
     */
    void darDeBaja(UserId quien, String numeroDeLaRespuesta) {
        boolean estabaEncendida = lectura.habilitadaPara(quien, TipoNotificacion.RECORDATORIO_EVENTO_WHATSAPP)
                .orElse(PreferenciaNotificacion.DEFAULT_HABILITADA);
        if (!estabaEncendida) {
            return;
        }
        escritura.upsert(PreferenciaNotificacion.de(quien, TipoNotificacion.RECORDATORIO_EVENTO_WHATSAPP, false, clock));
        log.info("[notifications.evento-whatsapp] {} se dio de baja del WhatsApp de eventos", quien);
        if (numeroDeLaRespuesta != null && !numeroDeLaRespuesta.isBlank()) {
            envio.enviarTexto(numeroDeLaRespuesta, CONFIRMACION);
        }
    }
}
