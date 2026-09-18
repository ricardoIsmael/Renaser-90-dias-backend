package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.notifications.application.ports.out.push.ResultadoEnvioPush;
import com.renaser.os.notifications.application.ports.out.push.TransportePush;
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
public class WebPushAdapter implements TransportePush {

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
    public boolean atiende(PlataformaPush plataforma) {
        return plataforma == PlataformaPush.WEB;
    }

    @Override
    public String nombre() {
        return "web-push";
    }

    /**
     * La entrega web, ahora devolviendo qué pasó en vez de tragárselo.
     *
     * <p>El comportamiento no cambia —sigue siendo best-effort y la bandeja sigue siendo el
     * contrato real—, pero un endpoint vencido ya no se reintenta para siempre en silencio: el
     * proveedor responde 404 o 410 para una suscripción muerta, y eso se traduce a token inválido.
     *
     * @param rutaApp destino al tocar la notificación. Antes iba fijo en "/", así que un aviso de
     *                acompañamiento abría el inicio en vez del alumno.
     */
    @Override
    public ResultadoEnvioPush entregar(TokenPush token, String titulo, String cuerpo, String rutaApp) {
        if (vapidPublicKey.isBlank() || vapidPrivateKey.isBlank() || vapidSubject.isBlank()) {
            log.warn("[notifications.WebPushAdapter] WEB_PUSH_VAPID_* no configurado; no se envia push web");
            return ResultadoEnvioPush.sinTransporte(token.id(), PlataformaPush.WEB.name());
        }
        try {
            WebSubscription suscripcion = objectMapper.readValue(token.token(), WebSubscription.class);
            if (suscripcion.endpoint() == null || suscripcion.endpoint().isBlank()
                    || suscripcion.keys() == null || suscripcion.keys().p256dh() == null
                    || suscripcion.keys().auth() == null) {
                return ResultadoEnvioPush.invalido(token.id(), "Suscripcion web incompleta");
            }
            if (!esDestinoDePushAceptable(suscripcion.endpoint())) {
                // Invalido, no "fallo": el token no sirve y no tiene sentido reintentarlo.
                return ResultadoEnvioPush.invalido(token.id(), "Endpoint de push no permitido");
            }
            Subscription subscription = new Subscription(suscripcion.endpoint(),
                    new Subscription.Keys(suscripcion.keys().p256dh(), suscripcion.keys().auth()));
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", titulo,
                    "body", cuerpo,
                    "data", Map.of("url", rutaApp == null || rutaApp.isBlank() ? "/" : rutaApp)));
            HttpResponse response = servicio().send(new Notification(subscription, payload));
            int status = response.getStatusLine().getStatusCode();
            EntityUtils.consumeQuietly(response.getEntity());
            return interpretar(token, status);
        } catch (Exception e) {
            // Push es best-effort: la fila de la bandeja ya se guardo y no se revierte por un
            // endpoint de navegador vencido o por una caida temporal del proveedor.
            log.warn("[notifications.WebPushAdapter] fallo el envio web: {}", e.getMessage());
            return ResultadoEnvioPush.temporal(token.id(), e.getMessage());
        }
    }

    /**
     * 404 y 410 son la forma que tiene Web Push de decir "esa suscripcion ya no existe" — el
     * navegador se desinstalo o el usuario revoco el permiso. Reintentarlas es tirar trabajo.
     */
    private static ResultadoEnvioPush interpretar(TokenPush token, int status) {
        if (status == 404 || status == 410) {
            return ResultadoEnvioPush.invalido(token.id(), "HTTP " + status);
        }
        if (status == 429 || status >= 500) {
            return ResultadoEnvioPush.temporal(token.id(), "HTTP " + status);
        }
        if (status >= 400) {
            log.warn("[notifications.WebPushAdapter] proveedor web respondio {}", status);
            return ResultadoEnvioPush.invalido(token.id(), "HTTP " + status);
        }
        return ResultadoEnvioPush.entregado(token.id());
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

    /**
     * Que el destino del push sea una direccion publica de internet, por HTTPS.
     *
     * <p><b>El agujero que cierra (2026-09-18).</b> {@code endpoint} sale del JSON que el cliente
     * registro como token y se usaba TAL CUAL como destino del POST saliente. Cualquier usuario
     * autenticado podia registrar {@code {"endpoint":"http://169.254.169.254/..."}} y dispararlo
     * generandose una notificacion a si mismo: el servicio de metadatos de la instancia EC2, desde
     * dentro de la VPC. Es ciego —el cuerpo se descarta— pero {@code interpretar} devuelve un
     * oraculo por codigo de estado, que alcanza para barrer la red interna.
     *
     * <p>Tres controles, de mas barato a mas caro:
     * <ol>
     *   <li>solo {@code https}: descarta el metadatos de AWS, que solo habla HTTP;</li>
     *   <li>el host no puede ser una IP literal: un endpoint de push real siempre es un nombre;</li>
     *   <li>ninguna de las direcciones a las que resuelve puede ser privada, de loopback,
     *       link-local ni comodin.</li>
     * </ol>
     *
     * <p><b>Lo que NO cubre, dicho sin vueltas:</b> entre esta comprobacion y el envio hay una
     * ventana en la que el DNS puede cambiar de respuesta (rebinding). Cerrarla exige fijar la IP
     * validada en el cliente HTTP, y {@code PushService} no expone donde hacerlo. El riesgo baja de
     * "cualquiera apunta al metadatos" a "hace falta controlar un dominio y ganar una carrera", y
     * queda anotado en vez de disimulado.
     */
    private static boolean esDestinoDePushAceptable(String endpoint) {
        java.net.URI uri;
        try {
            uri = java.net.URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        // Una IP literal nunca es un endpoint de push de un navegador; y los hosts entre corchetes
        // (IPv6) los devuelve `getHost` con los corchetes, asi que se miran aparte.
        if (host.startsWith("[") || host.matches("[0-9.]+")) {
            return false;
        }
        try {
            for (java.net.InetAddress direccion : java.net.InetAddress.getAllByName(host)) {
                if (direccion.isAnyLocalAddress() || direccion.isLoopbackAddress()
                        || direccion.isLinkLocalAddress() || direccion.isSiteLocalAddress()
                        || direccion.isMulticastAddress()) {
                    return false;
                }
            }
        } catch (java.net.UnknownHostException e) {
            return false;
        }
        return true;
    }
}
