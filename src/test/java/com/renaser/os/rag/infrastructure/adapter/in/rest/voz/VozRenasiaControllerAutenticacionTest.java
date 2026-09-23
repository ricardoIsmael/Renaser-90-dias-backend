package com.renaser.os.rag.infrastructure.adapter.in.rest.voz;

import com.renaser.os.rag.application.ports.in.voz.SintetizarVozUseCase;
import com.renaser.os.shared.web.SecurityConfig;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La voz vive bajo {@code /api/v1/renasia/**}, que exige sesion real en {@link SecurityConfig}: el
 * header {@code X-Actor-Id} solo no alcanza. Cada llamada ocupa CPU del servicio de voz; sin esta
 * guarda cualquiera podria usarlo de TTS gratis. Mismo patron que
 * {@code PropuestasRenasiaControllerAutenticacionTest}.
 */
@WebMvcTest(VozRenasiaController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class VozRenasiaControllerAutenticacionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SintetizarVozUseCase sintetizarVozUseCase;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @Test
    @DisplayName("pedir voz sin sesion es rechazado, aunque venga el header de actor")
    void sinSesionEsRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/renasia/voz")
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Hola\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sintetizarVozUseCase);
    }
}
