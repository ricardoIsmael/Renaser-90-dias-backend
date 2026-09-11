package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.application.ports.out.push.ResultadoEnvioPush;
import com.renaser.os.notifications.application.ports.out.push.TransportePush;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Entrega a iOS y Android por Expo Push.
 *
 * <p>Se elige Expo porque la app YA es Expo: usar FCM y APNs directo obligaría a manejar dos
 * credenciales, dos formatos y dos ciclos de renovación para el mismo mensaje (plan.md §9).
 *
 * <p><b>Sin credencial configurada no simula nada.</b> Devuelve {@code SIN_TRANSPORTE} y lo dice
 * en el log. Fingir una entrega exitosa dejaría a soporte creyendo que el aviso salió — el mismo
 * criterio que ya aplica {@code WebPushAdapter} cuando falta VAPID.
 *
 * <p>El cuerpo del push es discreto a propósito: dice que hay novedades, no de quién ni de qué.
 * El detalle se carga dentro de la app contra un endpoint que revalida permisos, porque entre que
 * se envía el push y se lo toca el mentor puede haber rotado.
 */
@Component
class ExpoPushTransporte implements TransportePush {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushTransporte.class);
    private static final URI ENDPOINT = URI.create("https://exp.host/--/api/v2/push/send");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * Un token de Expo se ve así: {@code ExponentPushToken[xxxxxxxx]}. Comprobarlo antes de salir
     * a la red evita gastar una llamada en algo que el proveedor va a rechazar igual.
     */
    private static final String PREFIJO_EXPO = "ExponentPushToken[";

    private final HttpClient http;
    private final String accessToken;
    private final boolean habilitado;

    ExpoPushTransporte(@Value("${renaser.notifications.expo-push.access-token:}") String accessToken,
                        @Value("${renaser.notifications.expo-push.habilitado:true}") boolean habilitado) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.habilitado = habilitado;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public boolean atiende(PlataformaPush plataforma) {
        return plataforma == PlataformaPush.IOS || plataforma == PlataformaPush.ANDROID;
    }

    @Override
    public String nombre() {
        return "expo";
    }

    @Override
    public ResultadoEnvioPush entregar(TokenPush token, String titulo, String cuerpo, String rutaApp) {
        if (!habilitado) {
            return ResultadoEnvioPush.sinTransporte(token.id(), token.plataforma().name());
        }
        if (!token.token().startsWith(PREFIJO_EXPO)) {
            return ResultadoEnvioPush.invalido(token.id(), "No tiene forma de token de Expo");
        }
        try {
            HttpRequest peticion = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .headers(cabecerasDeAutorizacion())
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpoJson(token, titulo, cuerpo, rutaApp),
                            StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            return interpretar(token, respuesta.statusCode(), respuesta.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultadoEnvioPush.temporal(token.id(), "Envio interrumpido");
        } catch (Exception e) {
            // Red caída, DNS, timeout: pasajero por definición.
            return ResultadoEnvioPush.temporal(token.id(), e.getMessage());
        }
    }

    /**
     * Expo distingue lo que se reintenta de lo que no. Un 429 o un 5xx son del proveedor y valen
     * un reintento; un {@code DeviceNotRegistered} significa que la app se desinstaló y volver a
     * intentarlo es tirar trabajo para siempre.
     */
    private static ResultadoEnvioPush interpretar(TokenPush token, int estado, String cuerpo) {
        String texto = cuerpo == null ? "" : cuerpo;
        if (estado == 429 || estado >= 500) {
            return ResultadoEnvioPush.temporal(token.id(), "HTTP " + estado);
        }
        if (estado >= 400) {
            return ResultadoEnvioPush.invalido(token.id(), "HTTP " + estado);
        }
        String enMinusculas = texto.toLowerCase(Locale.ROOT);
        if (enMinusculas.contains("devicenotregistered") || enMinusculas.contains("invalidcredentials")) {
            return ResultadoEnvioPush.invalido(token.id(), "El proveedor rechazo el token");
        }
        if (enMinusculas.contains("messageratteexceeded") || enMinusculas.contains("messageratelimit")) {
            return ResultadoEnvioPush.temporal(token.id(), "Limite de tasa del proveedor");
        }
        return ResultadoEnvioPush.entregado(token.id());
    }

    private String[] cabecerasDeAutorizacion() {
        // El token de acceso es opcional en Expo; cuando está, va como Bearer y NUNCA se loguea.
        return accessToken.isEmpty()
                ? new String[] {"X-Renaser-Push", "1"}
                : new String[] {"Authorization", "Bearer " + accessToken};
    }

    private static String cuerpoJson(TokenPush token, String titulo, String cuerpo, String rutaApp) {
        StringBuilder json = new StringBuilder(256);
        json.append("{\"to\":").append(comillas(token.token()))
                .append(",\"title\":").append(comillas(titulo))
                .append(",\"body\":").append(comillas(cuerpo))
                .append(",\"sound\":\"default\"");
        if (rutaApp != null && !rutaApp.isBlank()) {
            json.append(",\"data\":{\"route\":").append(comillas(rutaApp)).append("}");
        }
        return json.append('}').toString();
    }

    /** Escape mínimo. Se arma a mano para no arrastrar un ObjectMapper por tres campos. */
    private static String comillas(String valor) {
        if (valor == null) {
            return "null";
        }
        StringBuilder salida = new StringBuilder(valor.length() + 2).append('"');
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '"' -> salida.append("\\\"");
                case '\\' -> salida.append("\\\\");
                case '\n' -> salida.append("\\n");
                case '\r' -> salida.append("\\r");
                case '\t' -> salida.append("\\t");
                default -> {
                    if (c < 0x20) {
                        salida.append(String.format("\\u%04x", (int) c));
                    } else {
                        salida.append(c);
                    }
                }
            }
        }
        return salida.append('"').toString();
    }
}
