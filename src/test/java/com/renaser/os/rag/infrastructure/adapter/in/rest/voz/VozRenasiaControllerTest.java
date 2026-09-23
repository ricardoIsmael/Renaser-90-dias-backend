package com.renaser.os.rag.infrastructure.adapter.in.rest.voz;

import com.renaser.os.rag.application.ports.in.voz.SintetizarVozUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de la voz del orbe. Filtros apagados, como {@code PropuestasRenasiaControllerTest}:
 * aca se prueba el interceptor de permisos, la validacion y el mapeo 200/204. Que sin sesion se
 * rechace lo prueba {@link VozRenasiaControllerAutenticacionTest}.
 */
@WebMvcTest(VozRenasiaController.class)
@AutoConfigureMockMvc(addFilters = false)
class VozRenasiaControllerTest {

    private static final String RUTA = "/api/v1/renasia/voz";
    private static final byte[] WAV = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private SintetizarVozUseCase sintetizarVozUseCase;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private UUID actor(UserStatus status) {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(Optional.of(
                new UserSummary(UserId.of(actorId), "Actor", null, UserRole.TRAINEE, status)));
        return actorId;
    }

    private static String cuerpo(String texto) {
        return "{\"texto\":\"" + texto + "\"}";
    }

    @Test
    @DisplayName("con voz del servidor devuelve 200 audio/wav con los bytes del caso de uso")
    void conVozEs200Wav() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(sintetizarVozUseCase.sintetizar(UserId.of(actorId), "Hola")).thenReturn(Optional.of(WAV));

        mockMvc.perform(post(RUTA).header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Hola")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"))
                .andExpect(content().bytes(WAV));
    }

    @Test
    @DisplayName("sin proveedor de voz (noop) es 204 sin cuerpo: la app usa el TTS del telefono")
    void sinVozEs204() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(sintetizarVozUseCase.sintetizar(UserId.of(actorId), "Hola")).thenReturn(Optional.empty());

        mockMvc.perform(post(RUTA).header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Hola")))
                .andExpect(status().isNoContent())
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    @DisplayName("autorizacion negativa: un usuario SUSPENDIDO recibe 403 y el caso de uso ni se llama")
    void suspendidoEs403() throws Exception {
        UUID actorId = actor(UserStatus.SUSPENDED);

        mockMvc.perform(post(RUTA).header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Hola")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sintetizarVozUseCase);
    }

    @Test
    @DisplayName("un texto de mas de 400 caracteres es 400 y nunca llega al caso de uso")
    void textoDemasiadoLargoEs400() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        String texto = "a".repeat(SintetizarVozUseCase.LARGO_MAXIMO_TEXTO + 1);

        mockMvc.perform(post(RUTA).header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(texto)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sintetizarVozUseCase);
    }

    @Test
    @DisplayName("un texto en blanco es 400 y nunca llega al caso de uso")
    void textoEnBlancoEs400() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);

        mockMvc.perform(post(RUTA).header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("   ")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sintetizarVozUseCase);
    }
}
