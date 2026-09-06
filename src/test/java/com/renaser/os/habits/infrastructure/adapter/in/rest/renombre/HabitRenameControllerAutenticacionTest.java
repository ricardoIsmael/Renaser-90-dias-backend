package com.renaser.os.habits.infrastructure.adapter.in.rest.renombre;

import com.renaser.os.habits.application.ports.in.renombre.QuitarRenombreHabitoUseCase;
import com.renaser.os.habits.application.ports.in.renombre.RenombrarHabitoUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auditoria NFR 2026-09-06: {@code "/api/v1/habits"} en {@code SecurityConfig} era una coincidencia
 * EXACTA y dejaba fuera {@code /api/v1/habits/{id}/rename}; {@code "/api/v1/users/me/**"} dejaba
 * fuera {@code POST /api/v1/users/invite} y {@code PATCH /api/v1/users/{id}/role}. Con solo el
 * UUID de otra persona en {@code X-Actor-Id} se podia renombrar o quitar sus habitos, e invitar
 * usuarios o cambiar roles como un admin. Estas pruebas fijan que sin sesion real esas rutas se
 * rechazan ANTES de llegar a ningun caso de uso.
 *
 * <p>La de {@code /users/invite} se hace desde este slice a proposito, sin {@code UserController}
 * cargado: si la respuesta es 403 y no 404, es porque la cadena de seguridad la corto antes de
 * que el enrutamiento buscara un controller — que es exactamente la garantia que se quiere.
 */
@WebMvcTest(HabitRenameController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class HabitRenameControllerAutenticacionTest {

    private static final String HEADER_ACTOR_ID = "X-Actor-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RenombrarHabitoUseCase renombrarUseCase;
    @MockitoBean
    private QuitarRenombreHabitoUseCase quitarUseCase;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @Test
    @DisplayName("renombrar un habito sin sesion es rechazado aunque venga el header de actor")
    void renombrarSinSesionEsRechazado() throws Exception {
        mockMvc.perform(put("/api/v1/habits/" + UUID.randomUUID() + "/rename")
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Otro nombre\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(renombrarUseCase);
    }

    @Test
    @DisplayName("quitar un habito del plan sin sesion es rechazado")
    void quitarSinSesionEsRechazado() throws Exception {
        mockMvc.perform(delete("/api/v1/habits/" + UUID.randomUUID() + "/rename")
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(quitarUseCase);
    }

    @Test
    @DisplayName("invitar usuarios sin sesion es rechazado por la cadena de seguridad, antes del enrutamiento")
    void invitarUsuariosSinSesionEsRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/users/invite")
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguien@ejemplo.com\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cambiar el rol de un usuario sin sesion es rechazado por la cadena de seguridad")
    void cambiarRolSinSesionEsRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/role")
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }
}
