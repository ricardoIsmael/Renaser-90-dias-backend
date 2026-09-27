package com.renaser.os.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;

/**
 * V-7 (D-180): cuanto tarda el SERVIDOR en cada pedido, para poder comparar antes y despues de un
 * cambio sin adivinar que parte de los ~3 s que ve la app es red y que parte es nuestra.
 *
 * <ul>
 *   <li>Header {@code Server-Timing: app;dur=<ms>}: lo muestran las herramientas de red del
 *       navegador y {@code curl -i}, al lado del tiempo total del pedido.</li>
 *   <li>Una linea INFO {@code [http] GET /api/v1/habit-tracks/today 200 42ms} por pedido.</li>
 * </ul>
 *
 * <p><b>Primero de la cadena</b> ({@link Ordered#HIGHEST_PRECEDENCE}): mide tambien la sesion de
 * Redis y Spring Security, que son parte de lo que paga cada pedido.
 *
 * <p><b>El header se escribe justo antes del primer byte del cuerpo</b>, no al final: cuando la
 * cadena termina, la respuesta casi siempre ya se envio y un header agregado ahi se pierde sin
 * error. Por eso la respuesta va envuelta y el header se pone la primera vez que alguien pide el
 * stream, el writer, hace flush o manda un error. El {@code dur} del header mide hasta ese
 * momento; la linea del log, el pedido entero.
 *
 * <p><b>Que NO se registra:</b> la query string (puede traer datos) ni identificadores. La ruta
 * sale del patron del controller ({@code /api/v1/users/{id}}), que ademas agrupa los pedidos por
 * endpoint; si el pedido no llego a un controller (401, 404), los UUID de la ruta se reemplazan
 * por {@code {id}}. {@code /actuator} queda afuera: el health check del CD lo llama cada pocos
 * segundos y solo haria ruido.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TiempoDeRespuestaFilter extends OncePerRequestFilter {

    static final String HEADER = "Server-Timing";

    private static final Logger log = LoggerFactory.getLogger(TiempoDeRespuestaFilter.class);
    private static final Pattern UUID_EN_RUTA =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        RespuestaCronometrada cronometrada = new RespuestaCronometrada(response, inicio);
        try {
            chain.doFilter(request, cronometrada);
        } finally {
            cronometrada.anotarDuracion();
            registrar(request, cronometrada, milisDesde(inicio));
        }
    }

    private static void registrar(HttpServletRequest request, HttpServletResponse response, long milis) {
        // Un pedido asincrono (el chat del asistente por SSE) sigue vivo despues de esta linea: su
        // estado todavia no es el final, y la duracion es la de la parte sincrona.
        String estado = request.isAsyncStarted() ? "async" : String.valueOf(response.getStatus());
        log.info("[http] {} {} {} {}ms", request.getMethod(), rutaSinDatos(request), estado, milis);
    }

    /** El patron del controller si lo hubo; si no, la ruta con los UUID tapados. Nunca la query string. */
    static String rutaSinDatos(HttpServletRequest request) {
        Object patron = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (patron instanceof String texto && !texto.isEmpty() && !"/**".equals(texto)) {
            return texto;
        }
        return UUID_EN_RUTA.matcher(request.getRequestURI()).replaceAll("{id}");
    }

    private static long milisDesde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }

    /** Pone el header una sola vez, antes de que la respuesta se confirme. */
    private static final class RespuestaCronometrada extends HttpServletResponseWrapper {

        private final long inicioNanos;
        private boolean anotada;

        RespuestaCronometrada(HttpServletResponse response, long inicioNanos) {
            super(response);
            this.inicioNanos = inicioNanos;
        }

        void anotarDuracion() {
            if (anotada || isCommitted()) {
                return;
            }
            anotada = true;
            setHeader(HEADER, "app;dur=" + milisDesde(inicioNanos));
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            anotarDuracion();
            return super.getOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            anotarDuracion();
            return super.getWriter();
        }

        @Override
        public void flushBuffer() throws IOException {
            anotarDuracion();
            super.flushBuffer();
        }

        @Override
        public void sendError(int sc) throws IOException {
            anotarDuracion();
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            anotarDuracion();
            super.sendError(sc, msg);
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            anotarDuracion();
            super.sendRedirect(location);
        }
    }
}
