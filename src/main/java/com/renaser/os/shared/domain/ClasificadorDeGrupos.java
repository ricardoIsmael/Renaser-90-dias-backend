package com.renaser.os.shared.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A qué grupo de límite pertenece un pedido de la API: {@code (método, ruta) → grupo}, o ninguno si la ruta está exenta (spec 050,
 * FR-013 y FR-014). Es lógica pura, sin Spring: la ruta es la del pedido sin el contexto, tal como llega ({@code /api/v1/wall}),
 * con los ids de verdad.
 *
 * <h2>Orden de decisión (gana el primero que coincide)</h2>
 * webhook de WhatsApp → exportaciones → subidas → chat → Muro → IA fuera de SER → SER → administración → lecturas con POST →
 * escritura (cualquier verbo que escribe) → lectura (el resto). Por eso un {@code …/export.csv} o un {@code …/upload-url} de
 * administración cuenta como exportación o subida y no como administración.
 *
 * <h2>Lo que queda exento, a propósito y por escrito</h2>
 * <ul>
 *   <li>lo que no cuelga de {@code /api/v1/} (el WebSocket {@code /ws}, la salud, la documentación) y el {@code OPTIONS} del CORS;</li>
 *   <li>las rutas públicas de autenticación y alta (login, social, restablecer contraseña, verificación de correo, códigos por
 *       WhatsApp, solicitudes y eliminación de cuenta), que no tienen sesión que contar y ya llevan sus límites propios (D14).
 *       Son las que los controllers marcan {@code @PublicEndpoint}, salvo el webhook de WhatsApp (que sí se limita, por su origen)
 *       y {@code POST /auth/logout} (que tiene sesión y se cuenta como escritura). {@code TodaRutaCaeEnUnGrupoTest} lo comprueba
 *       contra los controllers.</li>
 * </ul>
 *
 * <p><b>Lecturas con POST.</b> {@code POST /users/me} es la lectura del perfil propio con verbo POST (la app la pide al entrar);
 * contarla como escritura la dejaría en 60 por minuto. Es la lista corta de excepciones; una nueva se agrega aquí y en su prueba.
 */
public final class ClasificadorDeGrupos {

    static final String PREFIJO_DE_LA_API = "/api/v1/";

    private static final Set<String> LECTURAS = Set.of("GET", "HEAD");
    private static final Set<String> SOLO_POST = Set.of("POST");
    private static final Set<String> CUALQUIERA = Set.of();

    /** Lo que {@code SecurityConfig} deja público (ver la Javadoc de la clase). Método vacío = cualquiera. */
    private static final List<Regla> EXENTAS = List.of(
            regla(null, CUALQUIERA, "/api/v1/auth/login"),
            regla(null, CUALQUIERA, "/api/v1/auth/social"),
            regla(null, CUALQUIERA, "/api/v1/auth/social/complete"),
            regla(null, CUALQUIERA, "/api/v1/auth/password/**"),
            regla(null, CUALQUIERA, "/api/v1/auth/email-verification/**"),
            regla(null, CUALQUIERA, "/api/v1/auth/whatsapp-code/**"),
            regla(null, Set.of("GET"), "/api/v1/integraciones/google/callback"),
            regla(null, SOLO_POST, "/api/v1/account-requests"),
            regla(null, SOLO_POST, "/api/v1/account-requests/check-email"),
            regla(null, SOLO_POST, "/api/v1/account-requests/exists"),
            regla(null, SOLO_POST, "/api/v1/account-requests/verify-email"),
            regla(null, Set.of("GET"), "/api/v1/account-requests/*/status"),
            regla(null, SOLO_POST, "/api/v1/account-deletion/request-code"),
            regla(null, SOLO_POST, "/api/v1/account-deletion/confirm"));

    /** Las reglas de grupo, en el orden de decisión. */
    private static final List<Regla> REGLAS = List.of(
            regla(GrupoDeLimite.WEBHOOK_WHATSAPP, SOLO_POST, "/api/v1/webhooks/whatsapp"),
            regla(GrupoDeLimite.EXPORTACIONES, CUALQUIERA, terminaEn("/export.csv", "/exportar.csv")),
            regla(GrupoDeLimite.SUBIDAS, CUALQUIERA, terminaEn("/upload-url", "/presign")),
            regla(GrupoDeLimite.CHAT, SOLO_POST, ClasificadorDeGrupos::esMensajeDelChat),
            regla(GrupoDeLimite.MURO, SOLO_POST, "/api/v1/wall", "/api/v1/wall/*/comments"),
            regla(GrupoDeLimite.IA_FUERA_DE_SER, SOLO_POST, "/api/v1/analisis-80-20/**", "/api/v1/espejo-sombra/**",
                    "/api/v1/mapa-renacimiento/**"),
            regla(GrupoDeLimite.SER, SOLO_POST, "/api/v1/renasia/**"),
            regla(GrupoDeLimite.ADMIN, CUALQUIERA, "/api/v1/admin/**"),
            regla(GrupoDeLimite.LECTURA, SOLO_POST, "/api/v1/users/me"));

    private ClasificadorDeGrupos() {
    }

    /** El grupo del pedido; vacío si está exento (ver la Javadoc de la clase). */
    public static Optional<GrupoDeLimite> grupoDe(String metodo, String ruta) {
        String verbo = metodo == null ? "" : metodo.toUpperCase(Locale.ROOT);
        if (estaExento(verbo, ruta)) {
            return Optional.empty();
        }
        return Optional.of(REGLAS.stream().filter(regla -> regla.coincide(verbo, ruta)).findFirst()
                .map(Regla::grupo).orElseGet(() -> LECTURAS.contains(verbo) ? GrupoDeLimite.LECTURA : GrupoDeLimite.ESCRITURA));
    }

    /** {@code true} si el pedido no cuenta para ningún grupo: fuera de la API, un {@code OPTIONS} o una ruta pública exenta. */
    public static boolean estaExento(String metodo, String ruta) {
        String verbo = metodo == null ? "" : metodo.toUpperCase(Locale.ROOT);
        return ruta == null || !ruta.startsWith(PREFIJO_DE_LA_API) || "OPTIONS".equals(verbo)
                || EXENTAS.stream().anyMatch(regla -> regla.coincide(verbo, ruta));
    }

    private static boolean esMensajeDelChat(String ruta) {
        return ruta.startsWith("/api/v1/chat/") && Arrays.asList(ruta.split("/")).contains("messages");
    }

    private static Predicate<String> terminaEn(String... finales) {
        return ruta -> Arrays.stream(finales).anyMatch(ruta::endsWith);
    }

    private static Regla regla(GrupoDeLimite grupo, Set<String> metodos, String... patrones) {
        return new Regla(grupo, metodos, ruta -> Arrays.stream(patrones).anyMatch(patron -> coincide(patron, ruta)));
    }

    private static Regla regla(GrupoDeLimite grupo, Set<String> metodos, Predicate<String> ruta) {
        return new Regla(grupo, metodos, ruta);
    }

    /** {@code *} es un segmento cualquiera y {@code **} al final es «lo que siga», incluso nada. Una barra final no cuenta. */
    static boolean coincide(String patron, String ruta) {
        String[] delPatron = patron.split("/");
        String[] delPedido = ruta.split("/");
        for (int i = 0; i < delPatron.length; i++) {
            if ("**".equals(delPatron[i])) {
                return true;
            }
            if (i >= delPedido.length || !("*".equals(delPatron[i]) || delPatron[i].equals(delPedido[i]))) {
                return false;
            }
        }
        return delPatron.length == delPedido.length;
    }

    private record Regla(GrupoDeLimite grupo, Set<String> metodos, Predicate<String> ruta) {

        boolean coincide(String verbo, String rutaDelPedido) {
            return (metodos.isEmpty() || metodos.contains(verbo)) && ruta.test(rutaDelPedido);
        }
    }
}
