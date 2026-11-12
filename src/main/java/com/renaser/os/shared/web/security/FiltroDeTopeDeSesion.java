package com.renaser.os.shared.web.security;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.TopeDeSesion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * El <b>tope absoluto</b> de la sesión (spec 050, tramo E; FR-028 a FR-031; D6): una sesión que se creó hace más de
 * {@code renaser.sesion.tope-absoluto} (90 días de partida) se invalida y el pedido responde
 * <b>401 {@code SESION_VENCIDA}</b>, aunque se haya usado ayer. Los 30 días de <i>inactividad</i>
 * ({@code spring.session.timeout}) no se tocan: son de Spring Session y se renuevan con cada pedido; este plazo, no.
 *
 * <p>Dos cosas que importan a la app:
 * <ul>
 *   <li>El 401 es el mismo que ya trata como «sesión vencida»: {@code apiFetch} lo ve en un pedido que mandó sesión,
 *       descarta el token y lleva al login <b>conservando la cola de envíos</b> (spec 048, FR-001 y D15). Solo un 401
 *       del servidor cierra la sesión; la falta de señal nunca (spec 048, FR-001), y este filtro no se entera de eso.</li>
 *   <li>Las rutas públicas ({@link RutasSinSesion}) pasan aunque la sesión haya vencido: quien trae un token viejo tiene
 *       que poder pedir el restablecimiento o entrar de nuevo. La sesión se invalida igual.</li>
 * </ul>
 *
 * <p>Corre <b>después</b> de la cadena de Spring Security (ya cargó la sesión y puso las cabeceras de CORS, sin las que
 * el build web no podría leer el {@code codigo}) y antes de {@link FiltroDeSoloLectura}. Mide con el {@link Clock} del
 * puerto, nunca con {@code Instant.now()} (FR-031). Se registra como instancia (E-307) en
 * {@code FiltroDeTopeDeSesionConfig}.
 *
 * <p>Sin tope ({@link TopeDeSesion#sinTope()}: la propiedad vacía o en cero) no hace nada: es la vuelta atrás.
 */
public class FiltroDeTopeDeSesion extends OncePerRequestFilter {

    /** La propiedad del tope, con su valor de partida: la leen este filtro y el handshake del WebSocket. */
    public static final String PROPIEDAD = "${renaser.sesion.tope-absoluto:90d}";

    static final String CODIGO = "SESION_VENCIDA";
    static final String MENSAJE = "Tu sesión venció. Vuelve a iniciar sesión.";

    private static final Logger log = LoggerFactory.getLogger(FiltroDeTopeDeSesion.class);

    private final Clock clock;
    private final TopeDeSesion tope;

    public FiltroDeTopeDeSesion(Clock clock, TopeDeSesion tope) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tope = Objects.requireNonNull(tope, "tope");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        HttpSession sesion = tope.activo() ? pedido.getSession(false) : null;
        Instant ahora = clock.now();
        if (sesion == null || !tope.vencida(Instant.ofEpochMilli(sesion.getCreationTime()), ahora)) {
            cadena.doFilter(pedido, respuesta);
            return;
        }
        dejarAnotado(sesion, ahora);
        sesion.invalidate();
        SecurityContextHolder.clearContext();
        if (RutasSinSesion.esDeLasPublicas(pedido)) {
            cadena.doFilter(pedido, respuesta);
            return;
        }
        responder(respuesta, ahora);
    }

    /** Una línea por sesión cortada, con su edad y sin ningún dato de la persona ni el id de la sesión. */
    private void dejarAnotado(HttpSession sesion, Instant ahora) {
        long dias = Duration.between(Instant.ofEpochMilli(sesion.getCreationTime()), ahora).toDays();
        log.info("Sesión cerrada por el tope absoluto: tenía {} días desde que se inició (tope {} días).", dias,
                tope.duracion().toDays());
    }

    /** El formato de error de siempre más el {@code codigo}; textos fijos, sin comillas que escapar. */
    private void responder(HttpServletResponse respuesta, Instant ahora) throws IOException {
        respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.getWriter().write("{\"message\":\"" + MENSAJE + "\",\"timestamp\":\"" + ahora + "\",\"codigo\":\""
                + CODIGO + "\"}");
    }
}
