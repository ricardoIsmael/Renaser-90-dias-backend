package com.renaser.os.community.infrastructure.adapter.in.rest.sobrenombre;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.community.application.ports.out.sobrenombre.SobrenombresPort;
import com.renaser.os.community.application.services.SobrenombreService;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los siete endpoints de administración de sobrenombres y palabras prohibidas, de punta a punta con el servicio REAL detrás: solo ADMIN y
 * ALCHEMIST activos los usan (spec 046 FR-005 y FR-006). Con el servicio simulado esta prueba no probaría nada, por eso entra con
 * {@code @Import}.
 */
@WebMvcTest(SobrenombresAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SobrenombreService.class, RelojDePrueba.class})
class SobrenombresAdminControllerTest {

    private static final Instant AHORA = Instant.parse("2027-03-10T15:00:00Z");
    private static final UUID PERSONA = UUID.fromString("00000000-0000-4000-8000-000000000042");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private SobrenombresPort sobrenombres;
    @MockitoBean
    private AlcanceDeFormacionFinder formaciones;
    @Autowired
    private RelojDePrueba reloj;

    private UUID actor(UserRole rol, UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(id))).thenReturn(Optional.of(new UserSummary(UserId.of(id), "Actor", null, rol, estado)));
        reloj.fijar(AHORA);
        return id;
    }

    /** Las siete rutas, cada una con un cuerpo válido: si responden 403 es por quién llama, no por lo que se manda. */
    private static Map<String, Supplier<MockHttpServletRequestBuilder>> rutas() {
        Map<String, Supplier<MockHttpServletRequestBuilder>> rutas = new LinkedHashMap<>();
        rutas.put("GET /sobrenombres", () -> get("/api/v1/admin/sobrenombres"));
        rutas.put("GET /personas/{id}/sobrenombre", () -> get("/api/v1/admin/personas/{id}/sobrenombre", PERSONA));
        rutas.put("PUT /personas/{id}/sobrenombre", () -> put("/api/v1/admin/personas/{id}/sobrenombre", PERSONA)
                .contentType(MediaType.APPLICATION_JSON).content("{\"sobrenombre\":\"Puma\",\"motivo\":\"Lo pidió por soporte\"}"));
        rutas.put("DELETE /personas/{id}/sobrenombre", () -> delete("/api/v1/admin/personas/{id}/sobrenombre", PERSONA)
                .param("motivo", "Pedido de la persona"));
        rutas.put("GET /sobrenombres/palabras-prohibidas", () -> get("/api/v1/admin/sobrenombres/palabras-prohibidas"));
        rutas.put("POST /sobrenombres/palabras-prohibidas", () -> post("/api/v1/admin/sobrenombres/palabras-prohibidas")
                .contentType(MediaType.APPLICATION_JSON).content("{\"palabra\":\"tigre\"}"));
        rutas.put("DELETE /sobrenombres/palabras-prohibidas/{palabra}",
                () -> delete("/api/v1/admin/sobrenombres/palabras-prohibidas/{palabra}", "tigre"));
        return rutas;
    }

    @ParameterizedTest(name = "{0} recibe 403 en las siete rutas y no se toca nada")
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE", "MENTOR_LEAD", "TRAINEE", "IT_SUPPORT"})
    @DisplayName("spec 046 FR-005, FR-006: quien no modera el Muro recibe 403 en cada ruta de administración")
    void quienNoModeraRecibe403(UserRole rol) throws Exception {
        UUID actorId = actor(rol, UserStatus.ACTIVE);

        for (var ruta : rutas().entrySet()) {
            mockMvc.perform(ruta.getValue().get().header("X-Actor-Id", actorId.toString()))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(sobrenombres, formaciones);
    }

    @ParameterizedTest(name = "{0} responde 200 en las siete rutas")
    @EnumSource(value = UserRole.class, names = {"ADMIN", "ALCHEMIST"})
    @DisplayName("spec 046 FR-005, FR-006: ADMIN y ALCHEMIST activos usan las siete rutas de administración")
    void moderadorRecibe200(UserRole rol) throws Exception {
        UUID actorId = actor(rol, UserStatus.ACTIVE);

        for (var ruta : rutas().entrySet()) {
            mockMvc.perform(ruta.getValue().get().header("X-Actor-Id", actorId.toString()))
                    .andExpect(status().isOk());
        }
        verify(sobrenombres).agregarPalabra("tigre", UserId.of(actorId), AHORA);
        verify(sobrenombres).quitarPalabra("tigre");
    }

    @Test
    @DisplayName("un ADMIN suspendido recibe 403 en una ruta de administración, aunque su token sea válido")
    void moderadorSuspendidoRecibe403() throws Exception {
        UUID actorId = actor(UserRole.ADMIN, UserStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/admin/sobrenombres").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sobrenombres);
    }
}
