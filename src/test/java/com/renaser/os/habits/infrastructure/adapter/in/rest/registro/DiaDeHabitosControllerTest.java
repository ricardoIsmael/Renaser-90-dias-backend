package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.application.ports.in.registro.*;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.DiaDeHabitos;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HabitTrackController.class)
@AutoConfigureMockMvc(addFilters = false)
class DiaDeHabitosControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConsultarTracksDelDiaConCatalogoUseCase consultar;
    @MockitoBean CompletarRegistroUseCase completar;
    @MockitoBean SubirEvidenciaRegistroUseCase evidencia;
    @MockitoBean SolicitarUrlEvidenciaRegistroUseCase url;
    @MockitoBean UserSummaryFinder users;
    @MockitoBean com.renaser.os.shared.application.Interruptores interruptores;
    private final UserId actor = UserId.of(UUID.randomUUID());

    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    private void actor(UserStatus estado) {
        when(users.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Actor", null,
                UserRole.TRAINEE, estado)));
    }

    @Test void devuelveElContextoDelActorYNoCacheaElDia() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultar.consultarHoyConContextoDe(actor)).thenReturn(new DiaDeHabitos(
                LocalDate.of(2026, 10, 16), ZoneId.of("America/Lima"),
                Instant.parse("2026-10-17T05:00:00Z"), Instant.parse("2026-10-17T04:59:59Z"), List.of()));
        mvc.perform(get("/api/v1/habit-tracks/today/snapshot").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.fechaLocal").value("2026-10-16"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Lima"))
                .andExpect(jsonPath("$.cierraEn").value("2026-10-17T05:00:00Z"))
                .andExpect(jsonPath("$.ahoraServidor").value("2026-10-17T04:59:59Z"))
                .andExpect(jsonPath("$.tracks").isEmpty());
        verify(consultar).consultarHoyConContextoDe(actor);
    }

    @Test void conservaElArrayAnterior() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultar.consultarHoyDe(actor)).thenReturn(List.of());
        mvc.perform(get("/api/v1/habit-tracks/today").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void cuentaSuspendidaNoPuedeLeerElSnapshot() throws Exception {
        actor(UserStatus.SUSPENDED);
        mvc.perform(get("/api/v1/habit-tracks/today/snapshot").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(consultar);
    }

    @Test void elSnapshotRespetaElInterruptorDeHabitos() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new com.renaser.os.shared.domain.FuncionalidadApagadaException("habitos", "En mantenimiento"))
                .when(interruptores).exigirPrendidaPara("habitos", actor);
        mvc.perform(get("/api/v1/habit-tracks/today/snapshot").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(consultar);
    }

    @Test void laDenegacionDelCasoDeUsoNoExponeElContexto() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultar.consultarHoyConContextoDe(actor))
                .thenThrow(new com.renaser.os.shared.domain.NotAuthorizedException("No autorizado"));
        mvc.perform(get("/api/v1/habit-tracks/today/snapshot").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isForbidden());
        verify(consultar).consultarHoyConContextoDe(actor);
    }
}
