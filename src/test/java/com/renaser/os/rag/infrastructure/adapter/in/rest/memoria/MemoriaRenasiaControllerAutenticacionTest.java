package com.renaser.os.rag.infrastructure.adapter.in.rest.memoria;

import com.renaser.os.rag.application.ports.in.memoria.BorrarMemoriaUseCase;
import com.renaser.os.rag.application.ports.in.memoria.ConsultarMemoriaUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La memoria es dato personal: con el header {@code X-Actor-Id} solo (que lo escribe el cliente)
 * cualquiera podria leer o borrar la de otra persona. Vive bajo {@code /api/v1/renasia/**}, que exige
 * sesion real en {@link SecurityConfig}; esta prueba existe para que eso no se pierda si alguien mueve
 * la ruta. Mismo patron que {@code PropuestasRenasiaControllerAutenticacionTest}.
 */
@WebMvcTest(MemoriaRenasiaController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class MemoriaRenasiaControllerAutenticacionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarMemoriaUseCase consultarUseCase;
    @MockitoBean
    private BorrarMemoriaUseCase borrarUseCase;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @Test
    @DisplayName("sin sesion no se lee ni se borra la memoria de nadie, aunque venga el header de actor")
    void sinSesionEsRechazado() throws Exception {
        String actor = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/renasia/memoria").header("X-Actor-Id", actor)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/renasia/memoria/recuerdos/{id}", UUID.randomUUID()).header("X-Actor-Id", actor))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/renasia/memoria").header("X-Actor-Id", actor)).andExpect(status().isForbidden());

        verifyNoInteractions(consultarUseCase, borrarUseCase);
    }
}
