package com.renaser.os.notifications.infrastructure.adapter.in.rest.tokenpush;

import com.renaser.os.notifications.application.ports.in.tokenpush.ConfirmarAlarmasLocalesUseCase;
import com.renaser.os.notifications.application.ports.in.tokenpush.ConfirmarAlarmasLocalesUseCase.ConfirmarAlarmasLocalesCommand;
import com.renaser.os.notifications.application.ports.in.tokenpush.RegistrarTokenPushUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.SecurityConfig;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de {@code POST /api/v1/push-tokens/alarmas-locales} (D-217), con la cadena de
 * seguridad encendida y sesion real, como {@code RocaMensualControllerTest}: lo que importa es que el
 * dueño del token salga de la SESION y que sin sesion o con la cuenta suspendida no se marque nada.
 */
@WebMvcTest(TokenPushController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class TokenPushControllerTest {

    private static final String RUTA = "/api/v1/push-tokens/alarmas-locales";
    private static final Instant CONFIRMADAS = Instant.parse("2026-09-29T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrarTokenPushUseCase registrarUseCase;
    @MockitoBean
    private ConfirmarAlarmasLocalesUseCase confirmarUseCase;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    private UUID actorId;

    @BeforeEach
    void aprendizActivo() {
        actorId = actor(UserStatus.ACTIVE);
    }

    private UUID actor(UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(id))).thenReturn(Optional.of(
                new UserSummary(UserId.of(id), "Aprendiz", null, UserRole.TRAINEE, estado)));
        return id;
    }

    private static MockHttpSession sesionDe(UUID actor) {
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(actor.toString(), null, List.of()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        return sesion;
    }

    private ResultActions confirmar(MockHttpSession sesion, String cuerpo) throws Exception {
        var pedido = post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        return mockMvc.perform(sesion == null ? pedido : pedido.session(sesion));
    }

    @Test
    @DisplayName("200 con la hora anotada; el dueño sale de la sesion, no del cuerpo")
    void confirmaConElActorDeLaSesion() throws Exception {
        when(confirmarUseCase.confirmar(any())).thenReturn(CONFIRMADAS);

        confirmar(sesionDe(actorId), "{\"token\":\"ExponentPushToken[abc]\",\"usuarioId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmadasEn").value(CONFIRMADAS.toString()));

        ArgumentCaptor<ConfirmarAlarmasLocalesCommand> comando =
                ArgumentCaptor.forClass(ConfirmarAlarmasLocalesCommand.class);
        verify(confirmarUseCase).confirmar(comando.capture());
        assertThat(comando.getValue().usuarioId()).isEqualTo(UserId.of(actorId));
        assertThat(comando.getValue().token()).isEqualTo("ExponentPushToken[abc]");
    }

    @Test
    @DisplayName("token vacio o ausente -> 400 sin llegar al caso de uso")
    void cuerpoInvalido() throws Exception {
        confirmar(sesionDe(actorId), "{\"token\":\"  \"}").andExpect(status().isBadRequest());
        confirmar(sesionDe(actorId), "{}").andExpect(status().isBadRequest());
        verifyNoInteractions(confirmarUseCase);
    }

    @Test
    @DisplayName("token que no es de quien llama -> 404")
    void tokenAjeno() throws Exception {
        when(confirmarUseCase.confirmar(any())).thenThrow(new NoSuchElementException("no registrado"));

        confirmar(sesionDe(actorId), "{\"token\":\"de-otra-persona\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin sesion -> 403 (la cadena de seguridad no tiene entry point de 401), sin marcar nada")
    void sinSesion() throws Exception {
        confirmar(null, "{\"token\":\"ExponentPushToken[abc]\"}").andExpect(status().isForbidden());
        verifyNoInteractions(confirmarUseCase);
    }

    @Test
    @DisplayName("cuenta SUSPENDIDA con sesion valida -> 403, sin marcar nada")
    void suspendida() throws Exception {
        UUID suspendida = actor(UserStatus.SUSPENDED);

        confirmar(sesionDe(suspendida), "{\"token\":\"ExponentPushToken[abc]\"}").andExpect(status().isForbidden());
        verifyNoInteractions(confirmarUseCase);
    }
}
