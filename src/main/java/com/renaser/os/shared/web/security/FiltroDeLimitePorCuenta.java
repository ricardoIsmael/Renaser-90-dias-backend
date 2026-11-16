package com.renaser.os.shared.web.security;

import com.renaser.os.shared.application.DecisionDeLimite;
import com.renaser.os.shared.application.PoliticaDeLimites;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.ClasificadorDeGrupos;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.PatronDeRuta;
import com.renaser.os.shared.domain.SujetoDeLimite;
import com.renaser.os.shared.domain.UnidadDeConteoPorIp;
import com.renaser.os.shared.web.DireccionIpDelCliente;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * El filtro del límite por cuenta (spec 050, tramo C; FR-012 a FR-021): clasifica cada pedido de {@code /api/v1} en un grupo
 * ({@link ClasificadorDeGrupos}), le pregunta a la {@link PoliticaDeLimites} y, si el grupo está en {@code APLICAR} y la cuenta se pasó,
 * responde <b>429</b> con {@code Retry-After} y el cuerpo de siempre más {@code codigo: DEMASIADOS_PEDIDOS}. Todo lo demás pasa.
 *
 * <p><b>A quién se cuenta.</b> A la cuenta de la sesión. Un pedido sin sesión que no es una ruta pública exenta (el webhook de
 * WhatsApp y las pocas rutas que {@code SecurityConfig} aún deja abiertas) se cuenta por su origen: la unidad de conteo de la IP del
 * cliente, que decide {@link DireccionIpDelCliente}. Las rutas públicas de autenticación y alta no se cuentan aquí: tienen sus
 * límites propios (D14).
 *
 * <p><b>Dónde corre.</b> Después de la cadena de Spring Security (que ya cargó la sesión y puso las cabeceras de CORS: el 429 las
 * lleva) y de {@code FiltroDeSoloLectura} (una cuenta en «ver como» recibe su 403 de siempre <i>antes</i> de contarse, FR-021), y
 * antes de Spring MVC: un rechazo no deserializa el cuerpo ni toca un caso de uso. Se registra como instancia (E-307), en
 * {@code FiltroDeLimitePorCuentaConfig}.
 *
 * <p><b>Qué deja en el registro.</b> Una línea por pedido que habría sido bloqueado y por cada bloqueo, con grupo, método, ruta sin
 * ids y el id de la cuenta; nunca cabeceras, token ni cuerpo. Que el límite falle nunca cambia la respuesta: ante cualquier error
 * el pedido pasa (FR-017).
 */
public class FiltroDeLimitePorCuenta extends OncePerRequestFilter {

    static final String CODIGO = "DEMASIADOS_PEDIDOS";
    static final String MENSAJE = "Espera un momento y vuelve a intentarlo.";

    private static final Logger log = LoggerFactory.getLogger(FiltroDeLimitePorCuenta.class);

    private final PoliticaDeLimites politica;
    private final Clock clock;

    public FiltroDeLimitePorCuenta(PoliticaDeLimites politica, Clock clock) {
        this.politica = Objects.requireNonNull(politica, "politica");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String ruta = rutaSinContexto(pedido);
        Optional<GrupoDeLimite> grupo = ClasificadorDeGrupos.grupoDe(pedido.getMethod(), ruta);
        DecisionDeLimite decision = grupo.isPresent() ? decidir(grupo.get(), pedido, ruta) : DecisionDeLimite.pasar();
        if (decision.rechaza()) {
            responder429(respuesta, decision.reintentarEnSegundos());
            return;
        }
        cadena.doFilter(pedido, respuesta);
    }

    /** Nunca lanza: si algo falla al contar, el pedido pasa. */
    private DecisionDeLimite decidir(GrupoDeLimite grupo, HttpServletRequest pedido, String ruta) {
        try {
            SujetoDeLimite sujeto = sujetoDe(pedido);
            DecisionDeLimite decision = politica.decidir(grupo, sujeto);
            if (decision.rechaza() || decision.habriaBloqueado()) {
                anotar(decision, grupo, sujeto, pedido.getMethod(), ruta);
            }
            return decision;
        } catch (RuntimeException falla) {
            log.warn("El límite por cuenta falló ({}): el pedido pasa", falla.getClass().getSimpleName());
            return DecisionDeLimite.pasar();
        }
    }

    private static SujetoDeLimite sujetoDe(HttpServletRequest pedido) {
        Authentication sesion = SecurityContextHolder.getContext().getAuthentication();
        if (sesion != null && sesion.isAuthenticated() && !(sesion instanceof AnonymousAuthenticationToken)) {
            return SujetoDeLimite.cuenta(sesion.getName());
        }
        return SujetoDeLimite.origen(UnidadDeConteoPorIp.de(DireccionIpDelCliente.de(pedido)));
    }

    /** Una línea clave=valor, sin cabeceras ni cuerpo: lo que hace falta para fijar los topes y ver un abuso. */
    private static void anotar(DecisionDeLimite decision, GrupoDeLimite grupo, SujetoDeLimite sujeto, String metodo, String ruta) {
        String resultado = decision.rechaza() ? "bloqueado" : "habria_bloqueado";
        String linea = "limite_por_cuenta resultado=" + resultado + " grupo=" + grupo.clave() + " metodo=" + metodo
                + " ruta=" + PatronDeRuta.sinIds(ruta) + " ventana=" + decision.ventanaPasada() + " cuenta=" + sujeto.paraElRegistro();
        if (decision.rechaza()) {
            log.warn(linea);
        } else {
            log.info(linea);
        }
    }

    private static String rutaSinContexto(HttpServletRequest pedido) {
        String ruta = pedido.getRequestURI();
        String contexto = pedido.getContextPath();
        return contexto != null && !contexto.isEmpty() && ruta.startsWith(contexto) ? ruta.substring(contexto.length()) : ruta;
    }

    /**
     * El formato de error de siempre más el {@code codigo} (como {@code ApiErrorConCodigo}); textos fijos, sin nada del pedido y
     * sin decir el grupo ni el tope (FR-015).
     */
    private void responder429(HttpServletResponse respuesta, int segundos) throws IOException {
        respuesta.setStatus(429);
        respuesta.setHeader("Retry-After", String.valueOf(segundos));
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.getWriter().write("{\"message\":\"" + MENSAJE + "\",\"timestamp\":\"" + clock.now() + "\",\"codigo\":\"" + CODIGO + "\"}");
    }
}
