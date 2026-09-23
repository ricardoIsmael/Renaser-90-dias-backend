package com.renaser.os.rag.infrastructure.adapter.in.rest.propuesta;

import com.renaser.os.rag.application.ports.in.propuesta.ResolverPropuestaUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirmar una propuesta EJECUTA una escritura en nombre de alguien: con el header
 * {@code X-Actor-Id} solo (que lo escribe el cliente) cualquiera podria disparar la propuesta de
 * otra persona. Estas rutas viven bajo {@code /api/v1/renasia/**}, que exige sesion real en
 * {@link SecurityConfig}; esta prueba existe para que eso no se pierda si alguien mueve la ruta.
 * Mismo patron que {@code RenasiaControllerAutenticacionTest}.
 */
@WebMvcTest(PropuestasRenasiaController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class PropuestasRenasiaControllerAutenticacionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResolverPropuestaUseCase resolverUseCase;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @Test
    @DisplayName("confirmar sin sesion es rechazado, aunque venga el header de actor")
    void confirmarSinSesionEsRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/renasia/propuestas/{id}/confirmar", UUID.randomUUID())
                        .header("X-Actor-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(resolverUseCase);
    }

    @Test
    @DisplayName("cancelar sin sesion tambien es rechazado")
    void cancelarSinSesionEsRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/renasia/propuestas/{id}/cancelar", UUID.randomUUID())
                        .header("X-Actor-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(resolverUseCase);
    }
}
