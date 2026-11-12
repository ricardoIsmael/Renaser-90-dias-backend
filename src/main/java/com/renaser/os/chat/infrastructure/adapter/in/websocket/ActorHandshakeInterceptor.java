package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.TopeDeSesion;
import com.renaser.os.shared.web.security.FiltroDeTopeDeSesion;
import com.renaser.os.shared.web.security.SesionDeSoloLectura;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resuelve QUIEN se conecta al WebSocket del chat a partir de la <b>sesion real</b>, y rechaza
 * el handshake si no hay una.
 *
 * <p><b>Corregido 2026-09-06 (auditoria NFR, 2a pasada; S-2 de la auditoria del 2026-09-01).</b>
 * Antes este interceptor leia {@code X-Actor-Id} del handshake — el mismo header que escribe el
 * propio cliente — y con eso quedaba identificado. Es decir: cualquiera podia abrir un socket
 * "como" cualquier otra persona con solo conocer su UUID (que catorce DTOs devuelven), y desde
 * ahi suscribirse a sus conversaciones. Las rutas HTTP se fueron cerrando con
 * {@code authenticated()} a lo largo del dia; el canal en vivo habia quedado igual que antes.
 *
 * <p>Ahora el actor sale de Spring Session: el cliente manda el mismo id de sesion opaco que usa
 * en HTTP ({@code X-Auth-Token}, docs/MODULO_AUTH.md), se busca la sesion en Redis y se lee de
 * ella el {@code SecurityContext} que guardo el login. Sin sesion, o con una sesion sin
 * autenticacion, el handshake termina en 403 y no hay socket.
 *
 * <p>Se acepta el token tambien como parametro de consulta ({@code /ws?token=...}) porque la API
 * {@code WebSocket} de los navegadores <b>no permite cabeceras propias</b> en el handshake; la app
 * nativa puede usar cualquiera de las dos vias. Hoy ningun cliente del repo abre este socket (el
 * movil conversa por REST), asi que el cambio no rompe a nadie — y justamente por eso la puerta
 * no podia quedar como estaba.
 *
 * <p>El atributo {@link #ATRIBUTO_ACTOR_ID} no cambia de nombre ni de tipo: es lo que
 * {@link SubscripcionAutorizadaInterceptor} lee para autorizar cada suscripcion.
 *
 * <p><b>El tope absoluto de la sesion (spec 050, tramo E; FR-028).</b> Una sesion que se creo hace mas que
 * {@code renaser.sesion.tope-absoluto} no abre el socket: se borra y el handshake responde 401. Es la misma regla que
 * {@code FiltroDeTopeDeSesion} aplica al HTTP; aca hace falta aparte porque el token tambien puede llegar por
 * {@code ?token=} (el navegador), y ese camino no lo ve el filtro.
 */
@Component
class ActorHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATRIBUTO_ACTOR_ID = "actorId";
    /**
     * El id de la sesion HTTP, para poder VOLVER a mirarla mientras el socket viva.
     *
     * <p>Antes solo se guardaba el actor, y eso convertia al handshake en la unica
     * comprobacion de toda la vida de la conexion: cerrar las sesiones de alguien —por
     * suspension o por robo de token— no le quitaba el socket ni lo que ese socket ya
     * estuviera recibiendo. Ver {@link SesionViva}.
     */
    static final String ATRIBUTO_ID_SESION = "idSesion";
    static final String HEADER_SESION = "X-Auth-Token";
    static final String PARAMETRO_SESION = "token";

    private final SessionRepository<? extends Session> sessionRepository;
    private final TopeDeSesion topeDeSesion;
    private final Clock clock;

    /** El de producción: el tope sale de la propiedad (vacía o {@code 0} = sin tope), como en el filtro HTTP. */
    @Autowired
    ActorHandshakeInterceptor(SessionRepository<? extends Session> sessionRepository,
                              @Value(FiltroDeTopeDeSesion.PROPIEDAD) Duration tope, Clock clock) {
        this(sessionRepository, TopeDeSesion.de(tope), clock);
    }

    ActorHandshakeInterceptor(SessionRepository<? extends Session> sessionRepository, TopeDeSesion topeDeSesion,
                              Clock clock) {
        this.sessionRepository = sessionRepository;
        this.topeDeSesion = topeDeSesion;
        this.clock = clock;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String idSesion = idDeSesion(request);
        Session sesion = idSesion == null ? null : sesionVigente(idSesion);
        if (sesion != null && venceElTope(sesion)) {
            sessionRepository.deleteById(idSesion);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        UUID actorId = sesion == null ? null : actorDeLaSesion(sesion);
        if (actorId == null) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ATRIBUTO_ACTOR_ID, actorId);
        attributes.put(ATRIBUTO_ID_SESION, idSesion);
        marcarSiEsDeSoloLectura(sesion, attributes);
        return true;
    }

    /**
     * «Ver como rol» (spec 002, fase 2; D-273): una sesión de solo lectura puede abrir el socket y suscribirse (leer),
     * pero no mandar nada. La marca se copia a los atributos del socket para que
     * {@link SubscripcionAutorizadaInterceptor} rechace el {@code SEND} sin volver a Redis en cada frame.
     */
    private static void marcarSiEsDeSoloLectura(Session sesion, Map<String, Object> attributes) {
        Object abiertaPor = sesion.getAttribute(SesionDeSoloLectura.ABIERTA_POR);
        if (abiertaPor instanceof String) {
            attributes.put(SesionDeSoloLectura.ABIERTA_POR, abiertaPor);
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
        // nada que hacer
    }

    /** Primero el header (app nativa); si no viene, el parametro de consulta (navegador). */
    private static String idDeSesion(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HEADER_SESION);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        List<String> valores = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams()
                .get(PARAMETRO_SESION);
        if (valores == null || valores.isEmpty() || valores.getFirst() == null || valores.getFirst().isBlank()) {
            return null;
        }
        return valores.getFirst().trim();
    }

    private boolean venceElTope(Session sesion) {
        return topeDeSesion.vencida(sesion.getCreationTime(), clock.now());
    }

    private Session sesionVigente(String idSesion) {
        Session sesion = sessionRepository.findById(idSesion);
        return sesion == null || sesion.isExpired() ? null : sesion;
    }

    /** El UUID del usuario autenticado en esa sesion, o {@code null} si no hay sesion valida. */
    private static UUID actorDeLaSesion(Session sesion) {
        Object contexto = sesion.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (!(contexto instanceof SecurityContext securityContext)) {
            return null;
        }
        Authentication authentication = securityContext.getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException nombreQueNoEsUuid) {
            return null;
        }
    }
}
