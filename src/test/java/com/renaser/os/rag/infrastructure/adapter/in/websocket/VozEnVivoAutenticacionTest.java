package com.renaser.os.rag.infrastructure.adapter.in.websocket;

import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase;
import com.renaser.os.rag.application.ports.in.voz.VozDelOrbeUseCase;
import com.renaser.os.rag.infrastructure.adapter.in.rest.voz.VozRenasiaController;
import com.renaser.os.shared.web.SecurityConfig;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El handshake de {@code /api/v1/renasia/voz/en-vivo} pasa por el filtro de seguridad como cualquier
 * {@code GET}: cae bajo {@code /api/v1/renasia/**}, que exige sesion real ({@link SecurityConfig}).
 * Sin sesion es 403 antes de llegar al handler, aunque venga {@code X-Actor-Id} (D-162). Mismo patron
 * que {@code VozRenasiaControllerAutenticacionTest}.
 */
@WebMvcTest(VozRenasiaController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class VozEnVivoAutenticacionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VozDelOrbeUseCase vozDelOrbe;
    @MockitoBean
    private ConversarEnVivoUseCase conversarEnVivo;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @Test
    @DisplayName("abrir la voz en vivo sin sesion es rechazado con 403, aunque venga el header de actor")
    void sinSesionEsRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/renasia/voz/en-vivo")
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .header("Upgrade", "websocket")
                        .header("Connection", "Upgrade")
                        .header("Sec-WebSocket-Version", "13")
                        .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ=="))
                .andExpect(status().isForbidden());

        verifyNoInteractions(conversarEnVivo);
    }
}
