package com.renaser.os.rag.infrastructure.adapter.in.websocket;

import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El handshake de la voz en vivo (D-162): sin sesion no hay socket, y una cuenta suspendida tampoco
 * lo abre aunque su sesion siga viva.
 */
class VozEnVivoHandshakeInterceptorTest {

    private final ConversarEnVivoUseCase useCase = mock(ConversarEnVivoUseCase.class);
    private final VozEnVivoHandshakeInterceptor interceptor = new VozEnVivoHandshakeInterceptor(useCase);
    private final MockHttpServletResponse respuestaHttp = new MockHttpServletResponse();
    private final Map<String, Object> atributos = new HashMap<>();

    private boolean handshake(MockHttpServletRequest pedido) {
        return interceptor.beforeHandshake(new ServletServerHttpRequest(pedido),
                new ServletServerHttpResponse(respuestaHttp), mock(WebSocketHandler.class), atributos);
    }

    private static MockHttpServletRequest pedidoDe(String usuario) {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/api/v1/renasia/voz/en-vivo");
        if (usuario != null) {
            pedido.setUserPrincipal(new UsernamePasswordAuthenticationToken(usuario, null, List.of()));
        }
        return pedido;
    }

    @Test
    @DisplayName("con sesion de una cuenta activa, el actor queda en los atributos")
    void cuentaActiva() {
        UUID actor = UUID.randomUUID();
        when(useCase.puedeConversar(UserId.of(actor))).thenReturn(true);

        assertThat(handshake(pedidoDe(actor.toString()))).isTrue();

        assertThat(atributos).containsEntry(VozEnVivoHandshakeInterceptor.ATRIBUTO_ACTOR, UserId.of(actor));
    }

    @Test
    @DisplayName("sin sesion es 403, aunque venga X-Actor-Id")
    void sinSesion() {
        MockHttpServletRequest pedido = pedidoDe(null);
        pedido.addHeader("X-Actor-Id", UUID.randomUUID().toString());

        assertThat(handshake(pedido)).isFalse();

        assertThat(respuestaHttp.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(useCase, never()).puedeConversar(any());
        assertThat(atributos).isEmpty();
    }

    @Test
    @DisplayName("una cuenta suspendida (o sin USE_APP) es 403")
    void suspendida() {
        UUID actor = UUID.randomUUID();
        when(useCase.puedeConversar(UserId.of(actor))).thenReturn(false);

        assertThat(handshake(pedidoDe(actor.toString()))).isFalse();

        assertThat(respuestaHttp.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(atributos).isEmpty();
    }

    @Test
    @DisplayName("un usuario que no es un UUID es 403")
    void nombreQueNoEsUuid() {
        assertThat(handshake(pedidoDe("anonymousUser"))).isFalse();

        assertThat(respuestaHttp.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }
}
