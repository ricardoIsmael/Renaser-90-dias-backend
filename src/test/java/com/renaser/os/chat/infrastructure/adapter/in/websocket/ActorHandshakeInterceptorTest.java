package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * S-2 (auditoria 2026-09-01), cerrado 2026-09-06: el WebSocket se identifica por la sesion real,
 * nunca por un header que escribe el cliente.
 */
@ExtendWith(MockitoExtension.class)
class ActorHandshakeInterceptorTest {

    private static final UUID ACTOR = UUID.randomUUID();

    @Mock
    private SessionRepository<Session> sessionRepository;
    @Mock
    private WebSocketHandler handler;

    private ActorHandshakeInterceptor interceptor() {
        return new ActorHandshakeInterceptor(sessionRepository);
    }

    private static MapSession sesionAutenticadaDe(UUID actor) {
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(actor.toString(), null, List.of()));
        MapSession sesion = new MapSession("sesion-valida");
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        return sesion;
    }

    @Test
    @DisplayName("con X-Auth-Token de una sesion autenticada, el actor de la sesion queda en los atributos")
    void sesionValidaPorHeader() {
        when(sessionRepository.findById("sesion-valida")).thenReturn(sesionAutenticadaDe(ACTOR));
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/ws");
        peticion.addHeader("X-Auth-Token", "sesion-valida");
        Map<String, Object> atributos = new HashMap<>();

        boolean permitido = interceptor().beforeHandshake(new ServletServerHttpRequest(peticion),
                new ServletServerHttpResponse(new MockHttpServletResponse()), handler, atributos);

        assertThat(permitido).isTrue();
        assertThat(atributos).containsEntry(ActorHandshakeInterceptor.ATRIBUTO_ACTOR_ID, ACTOR);
    }

    @Test
    @DisplayName("el navegador no puede mandar cabeceras en el handshake: se acepta ?token= con la misma sesion")
    void sesionValidaPorParametro() {
        when(sessionRepository.findById("sesion-valida")).thenReturn(sesionAutenticadaDe(ACTOR));
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/ws");
        peticion.setQueryString("token=sesion-valida");
        peticion.setRequestURI("/ws");
        Map<String, Object> atributos = new HashMap<>();

        boolean permitido = interceptor().beforeHandshake(new ServletServerHttpRequest(peticion),
                new ServletServerHttpResponse(new MockHttpServletResponse()), handler, atributos);

        assertThat(permitido).isTrue();
        assertThat(atributos).containsEntry(ActorHandshakeInterceptor.ATRIBUTO_ACTOR_ID, ACTOR);
    }

    @Test
    @DisplayName("X-Actor-Id solo ya NO identifica a nadie: 403 y sin socket (la regresion que importa)")
    void elHeaderDelClienteYaNoAlcanza() {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/ws");
        peticion.addHeader("X-Actor-Id", ACTOR.toString());
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        Map<String, Object> atributos = new HashMap<>();

        boolean permitido = interceptor().beforeHandshake(new ServletServerHttpRequest(peticion),
                new ServletServerHttpResponse(respuesta), handler, atributos);

        assertThat(permitido).isFalse();
        assertThat(atributos).isEmpty();
    }

    @Test
    @DisplayName("un token que no corresponde a ninguna sesion es 403")
    void tokenDesconocido() {
        when(sessionRepository.findById(anyString())).thenReturn(null);
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/ws");
        peticion.addHeader("X-Auth-Token", "no-existe");
        ServletServerHttpResponse respuesta = new ServletServerHttpResponse(new MockHttpServletResponse());

        boolean permitido = interceptor().beforeHandshake(new ServletServerHttpRequest(peticion), respuesta, handler,
                new HashMap<>());

        assertThat(permitido).isFalse();
        respuesta.close();
        assertThat(respuesta.getServletResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("una sesion que existe pero no tiene autenticacion tampoco abre el socket")
    void sesionSinAutenticacion() {
        when(sessionRepository.findById("anonima")).thenReturn(new MapSession("anonima"));
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/ws");
        peticion.addHeader("X-Auth-Token", "anonima");

        boolean permitido = interceptor().beforeHandshake(new ServletServerHttpRequest(peticion),
                new ServletServerHttpResponse(new MockHttpServletResponse()), handler, new HashMap<>());

        assertThat(permitido).isFalse();
    }
}
