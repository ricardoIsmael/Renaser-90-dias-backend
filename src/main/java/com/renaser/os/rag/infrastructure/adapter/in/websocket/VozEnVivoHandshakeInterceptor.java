package com.renaser.os.rag.infrastructure.adapter.in.websocket;

import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase;
import com.renaser.os.shared.domain.UserId;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * Quien abre la voz en vivo (D-162), resuelto por la <b>sesion real</b> y nunca por un header que
 * escribe el cliente.
 *
 * <p><b>Como se autentica.</b> El handshake es un {@code GET} HTTP comun a
 * {@code /api/v1/renasia/voz/en-vivo} con el header {@code X-Auth-Token} (el mismo id de sesion que
 * usa la app en todo el API). Esa ruta cae bajo {@code /api/v1/renasia/**}, que en
 * {@code SecurityConfig} exige sesion: Spring Session busca la sesion en Redis por ese header y
 * Spring Security llena el usuario del pedido. Sin sesion, el filtro corta con 403 antes de llegar
 * aca. Este interceptor lee ese usuario ({@link ServerHttpRequest#getPrincipal()}) y le pregunta al
 * caso de uso si puede conversar (cuenta activa y {@code USE_APP}); si no, 403 y no hay socket.
 *
 * <p>Hace falta porque {@code PermissionEnforcementInterceptor} solo mira metodos de controller, y
 * un WebSocket no lo es: sin esto, una cuenta suspendida con la sesion todavia viva abria el socket.
 */
@Component
class VozEnVivoHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATRIBUTO_ACTOR = "actorId";

    private final ConversarEnVivoUseCase conversarEnVivo;

    VozEnVivoHandshakeInterceptor(ConversarEnVivoUseCase conversarEnVivo) {
        this.conversarEnVivo = conversarEnVivo;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Optional<UserId> actor = actorDe(request.getPrincipal());
        if (actor.isEmpty() || !conversarEnVivo.puedeConversar(actor.get())) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ATRIBUTO_ACTOR, actor.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
        // nada que hacer
    }

    private static Optional<UserId> actorDe(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UserId.of(principal.getName()));
        } catch (IllegalArgumentException nombreQueNoEsUuid) {
            return Optional.empty();
        }
    }
}
