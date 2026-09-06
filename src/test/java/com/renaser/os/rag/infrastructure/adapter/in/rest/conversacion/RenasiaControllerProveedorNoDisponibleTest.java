package com.renaser.os.rag.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.rag.application.ports.in.conversacion.ObtenerHistorialUseCase;
import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase;
import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El camino exacto del bug: la busqueda de contexto (embedding) ocurre de forma SINCRONA antes
 * de que el stream arranque, asi que un fallo del proveedor ahi sale del controller como
 * excepcion y lo traduce {@code GlobalExceptionHandler}. Antes era 500; tiene que ser 503 con
 * {@code Retry-After}. Auditoria NFR 2026-09-06.
 */
@WebMvcTest(RenasiaController.class)
@AutoConfigureMockMvc(addFilters = false)
class RenasiaControllerProveedorNoDisponibleTest {

    private static final String RUTA = "/api/v1/renasia/mensajes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private PreguntarRenasiaUseCase preguntarUseCase;
    @MockitoBean
    private ObtenerHistorialUseCase obtenerHistorialUseCase;

    private UUID actorId;

    @BeforeEach
    void actorActivo() {
        actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(Optional.of(
                new UserSummary(UserId.of(actorId), "Actor", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("si el proveedor de IA no puede ahora, la API responde 503 con Retry-After — no 500")
    void proveedorNoDisponibleEs503ConRetryAfter() throws Exception {
        when(preguntarUseCase.preguntar(any())).thenThrow(new ProveedorIaNoDisponibleException(
                "El asistente alcanzo su limite de uso por ahora.", Duration.ofSeconds(60)));

        mockMvc.perform(post(RUTA)
                        .header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hola\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.message").value("El asistente alcanzo su limite de uso por ahora."));
    }
}
