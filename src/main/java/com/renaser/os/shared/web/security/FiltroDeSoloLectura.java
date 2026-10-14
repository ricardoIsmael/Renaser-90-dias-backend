package com.renaser.os.shared.web.security;

import com.renaser.os.shared.domain.Clock;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

/**
 * Hace cumplir el <b>solo lectura</b> de «ver como rol» (spec 002, fase 2, US5; contrato fase-2.md §5; D-270) en todo
 * el servidor, antes de cualquier controller: con una sesión marcada por {@link SesionDeSoloLectura},
 * <ul>
 *   <li>todo pedido que escribe ({@code POST}, {@code PUT}, {@code PATCH}, {@code DELETE}) responde
 *       <b>403 {@code MODO_SOLO_LECTURA}</b>, salvo los dos que cierran esa misma sesión
 *       ({@code DELETE /api/v1/soporte-ti/ver-como} y {@code POST /api/v1/auth/logout}) y {@code POST /api/v1/users/me},
 *       que es una LECTURA del perfil propio con verbo POST (la app la pide al entrar: sin ella, «ver como» no
 *       arrancaría);</li>
 *   <li>abrir un WebSocket que no sea el del chat ({@code /ws}) también es 403: la voz en vivo con SER escribe la
 *       conversación y gasta la cuota con cada frase. En {@code /ws} el chat rechaza el {@code SEND} de STOMP por su
 *       cuenta y deja suscribirse (leer);</li>
 *   <li>pasados los 30 minutos (aunque se siga usando), la sesión se cierra y el pedido responde
 *       <b>401 {@code VER_COMO_VENCIDO}</b>.</li>
 * </ul>
 *
 * <p>Corre <b>después</b> de la cadena de Spring Security (que ya cargó la sesión y puso las cabeceras de CORS: sin
 * ellas, el build web no podría leer el {@code codigo} del 403) y antes de que Spring MVC elija el controller, así que
 * un rechazo no deserializa el cuerpo ni toca un caso de uso. Un {@code GET} pasa siempre: lo que algunos {@code GET}
 * generan para la propia cuenta de prueba (los registros del día, por ejemplo) es dato de esa cuenta, y está excluida de
 * todo lo que ven los demás.
 *
 * <p>Se registra como instancia, no como bean del módulo (E-307), en {@code FiltroDeSoloLecturaConfig}.
 */
public class FiltroDeSoloLectura extends OncePerRequestFilter {

    static final String CODIGO_VENCIDO = "VER_COMO_VENCIDO";
    static final String MENSAJE_VENCIDO = "La vista «ver como» venció: vuelve a abrirla desde la consola.";
    static final String RUTA_DEL_CHAT_EN_VIVO = "/ws";

    private static final Set<String> METODOS_DE_LECTURA = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(),
            HttpMethod.OPTIONS.name());
    /**
     * Método y ruta exactos que una sesión de solo lectura puede pedir aunque no sean de lectura por su verbo: los dos
     * que la cierran y la lectura del perfil propio, que la app pide con POST.
     */
    private static final Set<String> PERMITIDOS_AUNQUE_NO_SEAN_GET = Set.of("DELETE /api/v1/soporte-ti/ver-como",
            "POST /api/v1/auth/logout", "POST /api/v1/users/me");

    private final Clock clock;

    public FiltroDeSoloLectura(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        HttpSession sesion = pedido.getSession(false);
        if (!SesionDeSoloLectura.loEs(sesion)) {
            cadena.doFilter(pedido, respuesta);
            return;
        }
        if (vencida(sesion)) {
            sesion.invalidate();
            responder(respuesta, HttpServletResponse.SC_UNAUTHORIZED, MENSAJE_VENCIDO, CODIGO_VENCIDO);
            return;
        }
        if (!permitido(pedido)) {
            responder(respuesta, HttpServletResponse.SC_FORBIDDEN, SesionDeSoloLectura.MENSAJE,
                    SesionDeSoloLectura.CODIGO);
            return;
        }
        pedido.setAttribute(SesionDeSoloLectura.EN_EL_PEDIDO, sesion.getAttribute(SesionDeSoloLectura.ABIERTA_POR));
        cadena.doFilter(pedido, respuesta);
    }

    private boolean vencida(HttpSession sesion) {
        return SesionDeSoloLectura.vencida(sesion.getAttribute(SesionDeSoloLectura.VENCE_EN), clock.now());
    }

    /** Leer, sí; escribir, solo para salir; WebSocket, solo el del chat. */
    static boolean permitido(HttpServletRequest pedido) {
        String ruta = rutaSinContexto(pedido);
        if (abreUnWebSocket(pedido)) {
            return RUTA_DEL_CHAT_EN_VIVO.equals(ruta);
        }
        String metodo = pedido.getMethod().toUpperCase(java.util.Locale.ROOT);
        return METODOS_DE_LECTURA.contains(metodo) || PERMITIDOS_AUNQUE_NO_SEAN_GET.contains(metodo + " " + ruta);
    }

    private static boolean abreUnWebSocket(HttpServletRequest pedido) {
        return "websocket".equalsIgnoreCase(pedido.getHeader("Upgrade"));
    }

    private static String rutaSinContexto(HttpServletRequest pedido) {
        String ruta = pedido.getRequestURI();
        String contexto = pedido.getContextPath();
        return contexto != null && !contexto.isEmpty() && ruta.startsWith(contexto) ? ruta.substring(contexto.length())
                : ruta;
    }

    /** El formato de error de siempre más el {@code codigo} (como {@code ApiErrorConCodigo}); textos fijos, sin comillas. */
    private void responder(HttpServletResponse respuesta, int estado, String mensaje, String codigo)
            throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.getWriter().write("{\"message\":\"" + mensaje + "\",\"timestamp\":\"" + clock.now()
                + "\",\"codigo\":\"" + codigo + "\"}");
    }
}
