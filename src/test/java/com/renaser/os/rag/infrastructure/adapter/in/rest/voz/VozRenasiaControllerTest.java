package com.renaser.os.rag.infrastructure.adapter.in.rest.voz;

import com.renaser.os.rag.application.ports.in.voz.VozDelOrbeUseCase;
import com.renaser.os.rag.application.ports.in.voz.VozDelOrbeUseCase.AudioDelOrbe;
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
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private VozDelOrbeUseCase vozDelOrbe;

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

    /** Un audio que ya termino de generarse (o que fallo sin sonido). */
    private static AudioDelOrbe audio(boolean tieneSonido) {
        return new AudioDelOrbe() {
            @Override
            public boolean tieneSonido() {
                return tieneSonido;
            }

            @Override
            public void escribirEn(OutputStream destino) throws java.io.IOException {
                destino.write(WAV);
            }
        };
    }

    @Test
    @DisplayName("POST con voz del servidor es 201 con la ruta de donde escuchar el audio")
    void prepararEs201ConLaRuta() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        UUID id = UUID.randomUUID();
        when(vozDelOrbe.preparar(UserId.of(actorId), "Hola")).thenReturn(Optional.of(id));

        mockMvc.perform(post(RUTA).header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Hola")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.audio").value(RUTA + "/" + id));
    }

    @Test
    @DisplayName("POST sin proveedor de voz (noop) es 204 sin cuerpo: la app usa el TTS del telefono")
    void sinVozEs204() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(vozDelOrbe.preparar(UserId.of(actorId), "Hola")).thenReturn(Optional.empty());

        mockMvc.perform(post(RUTA).header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Hola")))
                .andExpect(status().isNoContent())
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    @DisplayName("GET transmite el audio como audio/wav")
    void escucharTransmiteElWav() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        UUID id = UUID.randomUUID();
        when(vozDelOrbe.buscar(UserId.of(actorId), id)).thenReturn(Optional.of(audio(true)));

        MvcResult inicio = mockMvc.perform(get(RUTA + "/" + id).header("X-Actor-Id", actorId.toString()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(inicio))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"))
                .andExpect(content().bytes(WAV));
    }

    @Test
    @DisplayName("GET de un audio que fallo sin sonido es 204")
    void escucharSinSonidoEs204() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        UUID id = UUID.randomUUID();
        when(vozDelOrbe.buscar(UserId.of(actorId), id)).thenReturn(Optional.of(audio(false)));

        mockMvc.perform(get(RUTA + "/" + id).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET de un id ajeno, vencido o inexistente es 404")
    void escucharAjenoEs404() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        UUID id = UUID.randomUUID();
        when(vozDelOrbe.buscar(UserId.of(actorId), id)).thenReturn(Optional.empty());

        mockMvc.perform(get(RUTA + "/" + id).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("autorizacion negativa: un usuario SUSPENDIDO recibe 403 al preparar y al escuchar")
    void suspendidoEs403() throws Exception {
        UUID actorId = actor(UserStatus.SUSPENDED);

        mockMvc.perform(post(RUTA).header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Hola")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA + "/" + UUID.randomUUID()).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(vozDelOrbe);
    }

    @Test
    @DisplayName("un texto de mas de 400 caracteres es 400 y nunca llega al caso de uso")
    void textoDemasiadoLargoEs400() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        String texto = "a".repeat(VozDelOrbeUseCase.LARGO_MAXIMO_TEXTO + 1);

        mockMvc.perform(post(RUTA).header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(texto)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vozDelOrbe);
    }

    @Test
    @DisplayName("un texto en blanco es 400 y nunca llega al caso de uso")
    void textoEnBlancoEs400() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);

        mockMvc.perform(post(RUTA).header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("   ")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vozDelOrbe);
    }
}
