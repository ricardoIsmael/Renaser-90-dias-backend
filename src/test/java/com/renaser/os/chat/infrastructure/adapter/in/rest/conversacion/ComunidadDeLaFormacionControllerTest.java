package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.ConsultarComunidadDeLaFormacionUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.ConsultarComunidadDeLaFormacionUseCase.TipoDeLaComunidad;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.TipoDeComunidad;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qué tipos de comunidad tiene prendidos una formación y cuántas conversaciones esconde cada uno (spec 024, FR-033): solo para quien administra
 * ({@code MANAGE_ROLES}) con la cuenta activa.
 */
@WebMvcTest(ComunidadDeLaFormacionController.class)
@AutoConfigureMockMvc(addFilters = false)
class ComunidadDeLaFormacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarComunidadDeLaFormacionUseCase consultar;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private UserId sesionDe(UserRole rol, UserStatus estado) {
        UserId actor = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Actor", null, rol, estado)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.value().toString(), null, List.of()));
        return actor;
    }

    @Test
    @DisplayName("ALCHEMIST activo ve los cuatro tipos con su estado y cuántas conversaciones esconde cada uno")
    void alquimistaVeLosTipos() throws Exception {
        UserId alquimista = sesionDe(UserRole.ALCHEMIST, UserStatus.ACTIVE);
        when(consultar.consultar(alquimista, Formacion.DIPLOMADO)).thenReturn(List.of(
                new TipoDeLaComunidad(TipoDeComunidad.GENERAL, true, 1), new TipoDeLaComunidad(TipoDeComunidad.GRUPO, true, 4),
                new TipoDeLaComunidad(TipoDeComunidad.SOPORTE, false, 30), new TipoDeLaComunidad(TipoDeComunidad.DIRECTO, true, 12)));

        mockMvc.perform(get("/api/v1/admin/formaciones/DIPLOMADO/comunidad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formation").value("DIPLOMADO"))
                .andExpect(jsonPath("$.tipos.length()").value(4))
                .andExpect(jsonPath("$.tipos[2].tipo").value("SOPORTE"))
                .andExpect(jsonPath("$.tipos[2].prendido").value(false))
                .andExpect(jsonPath("$.tipos[2].conversaciones").value(30));
    }

    @Test
    @DisplayName("autorización negativa: aprendiz, mentor y líder reciben 403 y no se lee nada")
    void otrosRolesNoLoVen() throws Exception {
        for (UserRole rol : List.of(UserRole.TRAINEE, UserRole.MENTOR, UserRole.MENTOR_LEAD)) {
            sesionDe(rol, UserStatus.ACTIVE);
            mockMvc.perform(get("/api/v1/admin/formaciones/ESCUELA/comunidad")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(consultar);
    }

    @Test
    @DisplayName("autorización negativa: una cuenta SUSPENDIDA recibe 403 aunque el rol sea ADMIN")
    void adminSuspendidoNoLoVe() throws Exception {
        sesionDe(UserRole.ADMIN, UserStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/admin/formaciones/ESCUELA/comunidad")).andExpect(status().isForbidden());

        verifyNoInteractions(consultar);
    }
}
