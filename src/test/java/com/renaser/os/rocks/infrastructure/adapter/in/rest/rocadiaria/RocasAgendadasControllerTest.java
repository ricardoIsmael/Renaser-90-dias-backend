package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria;

import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasAgendadasUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasAgendadasUseCase.RocasAgendadas;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.SecurityConfig;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de {@code GET /api/v1/rocks/upcoming} (D-217), con sesion real. */
@WebMvcTest(RocasAgendadasController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class RocasAgendadasControllerTest {

    private static final String RUTA = "/api/v1/rocks/upcoming";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarRocasAgendadasUseCase agendadasUseCase;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    private UUID actor(UserRole rol, UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(id))).thenReturn(Optional.of(
                new UserSummary(UserId.of(id), "Alguien", null, rol, estado)));
        return id;
    }

    private static MockHttpSession sesionDe(UUID actor) {
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(actor.toString(), null, List.of()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        return sesion;
    }

    @Test
    @DisplayName("200 con el rango y las rocas de la persona de la sesion")
    void devuelveElRango() throws Exception {
        UUID aprendiz = actor(UserRole.TRAINEE, UserStatus.ACTIVE);
        when(agendadasUseCase.agendadas(UserId.of(aprendiz))).thenReturn(new RocasAgendadas(
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 4), List.of()));

        mockMvc.perform(get(RUTA).session(sesionDe(aprendiz)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-09-29"))
                .andExpect(jsonPath("$.hasta").value("2026-10-04"))
                .andExpect(jsonPath("$.rocas").isArray());
        verify(agendadasUseCase).agendadas(UserId.of(aprendiz));
    }

    @Test
    @DisplayName("sin sesion -> 403, sin leer nada")
    void sinSesion() throws Exception {
        mockMvc.perform(get(RUTA)).andExpect(status().isForbidden());
        verifyNoInteractions(agendadasUseCase);
    }

    @Test
    @DisplayName("cuenta SUSPENDIDA con sesion valida -> 403")
    void suspendida() throws Exception {
        UUID suspendido = actor(UserRole.TRAINEE, UserStatus.SUSPENDED);

        mockMvc.perform(get(RUTA).session(sesionDe(suspendido))).andExpect(status().isForbidden());
        verifyNoInteractions(agendadasUseCase);
    }
}
