package com.renaser.os.rag.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.rag.application.ports.in.conversacion.ObtenerHistorialUseCase;
import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renasia exige sesion real (2026-09-03). Es la unica excepcion a {@code permitAll()} en
 * {@link SecurityConfig}, y esta prueba existe para que nadie la saque sin darse cuenta.
 *
 * <p><b>Por que importa.</b> En el resto de la API, cuando no hay sesion el actor se resuelve del
 * header {@code X-Actor-Id}, que lo escribe el propio cliente. Sobre un agente conversacional eso
 * significa que cualquiera puede hablarle como si fuera otra persona, leer su progreso a traves de
 * las herramientas del agente y gastarle la cuota diaria. Por eso el caso que se prueba abajo no es
 * "sin nada", es "con el header de actor puesto": ese header ya no alcanza.
 *
 * <p>A diferencia de {@code WallControllerAuthorizationTest}, aca los filtros van ENCENDIDOS (sin
 * {@code addFilters = false}): lo que se prueba es justamente la cadena de seguridad, no el
 * interceptor de permisos. {@link SecurityConfig} es autocontenido — declara su propio
 * {@code SecurityContextRepository} — asi que alcanza con importarlo y darle la propiedad de CORS.
 */
@WebMvcTest(RenasiaController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class RenasiaControllerAutenticacionTest {

    private static final String HEADER_ACTOR_ID = "X-Actor-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreguntarRenasiaUseCase preguntarUseCase;
    @MockitoBean
    private ObtenerHistorialUseCase obtenerHistorialUseCase;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @Test
    @DisplayName("preguntar sin sesion es rechazado, aunque venga el header de actor")
    void preguntarSinSesionEsRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/renasia/mensajes")
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hola\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(preguntarUseCase);
    }

    @Test
    @DisplayName("el historial sin sesion tambien es rechazado, aunque venga el header de actor")
    void historialSinSesionEsRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/renasia/mensajes")
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(obtenerHistorialUseCase);
    }
}
