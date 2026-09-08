package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.notifications.application.ports.out.push.PushPort;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

/**
 * Entrega Web Push a las suscripciones del navegador.
 *
 * <p>Las suscripciones IOS/ANDROID se dejan intactas para que el adaptador nativo pueda
 * reemplazar este canal después. El scheduler de hábitos ya publica los dos eventos por
 * participante; acá solo se selecciona el canal WEB y se entrega cada evento una vez.</p>
 *
 * <p>Sin VAPID configurado el backend conserva la notificación en la bandeja y deja un warning
 * claro. Eso permite arrancar localmente sin secretos, pero nunca simula una entrega exitosa.</p>
 */
@Component
public class WebPushAdapter implements PushPort {

    private static final Logger log = LoggerFactory.getLogger(WebPushAdapter.class);

    /**
     * Jackson 2 propio: Spring Boot 4 registra Jackson 3 como bean global y no expone este tipo.
     * Mantenerlo local evita tumbar el contexto completo por una dependencia exclusiva de Web Push.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String vapidPublicKey;
    private final String vapidPrivateKey;
    private final String vapidSubject;
    private volatile PushService pushService;

    public WebPushAdapter(@Value("${renaser.notifications.web-push.vapid-public-key:}") String vapidPublicKey,
                          @Value("${renaser.notifications.web-push.vapid-private-key:}") String vapidPrivateKey,
                          @Value("${renaser.notifications.web-push.vapid-subject:mailto:soporte@renaser.com}") String vapidSubject) {
        this.vapidPublicKey = vapidPublicKey == null ? "" : vapidPublicKey.trim();
        this.vapidPrivateKey = vapidPrivateKey == null ? "" : vapidPrivateKey.trim();
        this.vapidSubject = vapidSubject == null ? "" : vapidSubject.trim();
    }

    @Override
    public void enviar(List<TokenPush> tokens, String titulo, String cuerpo) {
        if (tokens == null || tokens.isEmpty()) return;
        for (TokenPush token : tokens) {
            if (token.plataforma() == PlataformaPush.WEB) {
                enviarWeb(token, titulo, cuerpo);
            }
        }
    }

    private void enviarWeb(TokenPush token, String titulo, String cuerpo) {
        if (vapidPublicKey.isBlank() || vapidPrivateKey.isBlank() || vapidSubject.isBlank()) {
            log.warn("[notifications.WebPushAdapter] WEB_PUSH_VAPID_* no configurado; no se envia push web");
            return;
        }
        try {
            WebSubscription suscripcion = objectMapper.readValue(token.token(), WebSubscription.class);
            if (suscripcion.endpoint() == null || suscripcion.endpoint().isBlank()
                    || suscripcion.keys() == null || suscripcion.keys().p256dh() == null
                    || suscripcion.keys().auth() == null) {
                log.warn("[notifications.WebPushAdapter] suscripcion web invalida; se omite");
                return;
            }
            Subscription subscription = new Subscription(suscripcion.endpoint(),
                    new Subscription.Keys(suscripcion.keys().p256dh(), suscripcion.keys().auth()));
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", titulo,
                    "body", cuerpo,
                    "data", Map.of("url", "/")));
            HttpResponse response = servicio().send(new Notification(subscription, payload));
            int status = response.getStatusLine().getStatusCode();
            EntityUtils.consumeQuietly(response.getEntity());
            if (status >= 400) {
                log.warn("[notifications.WebPushAdapter] proveedor web respondio {}", status);
            }
        } catch (Exception e) {
            // Push es best-effort: la fila de la bandeja ya se guardo y no se revierte por un
            // endpoint de navegador vencido o por una caida temporal del proveedor.
            log.warn("[notifications.WebPushAdapter] fallo el envio web: {}", e.getMessage());
        }
    }

    private PushService servicio() throws GeneralSecurityException {
        PushService actual = pushService;
        if (actual != null) return actual;
        synchronized (this) {
            if (pushService == null) {
                pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            }
            return pushService;
        }
    }

    private record WebSubscription(String endpoint, WebSubscriptionKeys keys) {
    }

    private record WebSubscriptionKeys(String p256dh, String auth) {
    }
}
