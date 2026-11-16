package com.renaser.os.shared.web.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * Las rutas de la API que <b>no piden sesión</b>: las que {@code SecurityConfig} deja en {@code permitAll()}. El tope
 * absoluto de sesión ({@link FiltroDeTopeDeSesion}) las deja pasar aunque la sesión que traigan haya vencido: quien llega
 * con un token de más de 90 días tiene que poder pedir un correo de restablecimiento, entrar de nuevo o confirmar su
 * correo, y no recibir un 401 que lo deje sin salida (spec 050, plan §7).
 *
 * <p>Es una copia, a propósito corta, de lo que enumera {@code SecurityConfig}: {@code RutasSinSesionTest} lee ese
 * archivo y falla si una ruta {@code permitAll()} nueva no está aquí, o si aquí sobra una que allá ya no es pública.
 * Preferimos la copia con prueba a reescribir el filtro de seguridad de toda la API por este tramo.
 */
final class RutasSinSesion {

    /** Método ({@code *} = cualquiera) y patrón, con la misma sintaxis que usa {@code SecurityConfig}. */
    record Ruta(String metodo, String patron) {
    }

    static final List<Ruta> LAS_PUBLICAS = List.of(
            new Ruta("*", "/api/v1/auth/login"),
            new Ruta("*", "/api/v1/auth/social"),
            new Ruta("*", "/api/v1/auth/social/complete"),
            new Ruta("*", "/api/v1/auth/password/**"),
            new Ruta("*", "/api/v1/auth/email-verification/**"),
            new Ruta("GET", "/api/v1/integraciones/google/callback"),
            new Ruta("POST", "/api/v1/account-requests"),
            new Ruta("POST", "/api/v1/account-requests/check-email"),
            new Ruta("POST", "/api/v1/account-requests/exists"),
            new Ruta("POST", "/api/v1/account-requests/verify-email"),
            new Ruta("GET", "/api/v1/account-requests/*/status"),
            new Ruta("POST", "/api/v1/account-deletion/request-code"),
            new Ruta("POST", "/api/v1/account-deletion/confirm"),
            new Ruta("POST", "/api/v1/webhooks/whatsapp"));

    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    private RutasSinSesion() {
    }

    static boolean esDeLasPublicas(HttpServletRequest pedido) {
        return esDeLasPublicas(pedido.getMethod(), rutaSinContexto(pedido));
    }

    static boolean esDeLasPublicas(String metodo, String ruta) {
        PathContainer camino = PathContainer.parsePath(ruta);
        for (Ruta publica : LAS_PUBLICAS) {
            boolean metodoCoincide = "*".equals(publica.metodo()) || publica.metodo().equalsIgnoreCase(metodo);
            PathPattern patron = PARSER.parse(publica.patron());
            if (metodoCoincide && patron.matches(camino)) {
                return true;
            }
        }
        return false;
    }

    private static String rutaSinContexto(HttpServletRequest pedido) {
        String ruta = pedido.getRequestURI();
        String contexto = pedido.getContextPath();
        return contexto != null && !contexto.isEmpty() && ruta.startsWith(contexto) ? ruta.substring(contexto.length())
                : ruta;
    }
}
