package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.in.registro.SolicitarUrlEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import com.renaser.os.habits.application.ports.in.registro.CompletarHechoSinRedUseCase;

/** Spec 048: el endpoint de completar va por el camino de lo hecho sin red solo cuando trae la hora real y el id de envío. */
@WebMvcTest(HabitTrackController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompletarHechoSinRedControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ConsultarTracksDelDiaConCatalogoUseCase consultar;
    @MockitoBean CompletarRegistroUseCase completar;
    @MockitoBean CompletarHechoSinRedUseCase completarSinRed;
    @MockitoBean SubirEvidenciaRegistroUseCase evidencia;
    @MockitoBean SolicitarUrlEvidenciaRegistroUseCase url;
    @MockitoBean UserSummaryFinder users;
    @MockitoBean com.renaser.os.shared.application.Interruptores interruptores;

    private final UserId actor = UserId.of(UUID.randomUUID());

    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    private RegistroHabito registro() {
        RegistroHabito r = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), actor, HabitoId.of(UUID.randomUUID()),
                LocalDate.of(2026, 11, 9), 40, TipoDia.TODOS, false, Instant.parse("2026-11-09T10:00:00Z"));
        r.completar(0, null, null, null, Instant.parse("2026-11-10T15:00:00Z"));
        return r;
    }

    @Test void conHoraRealVaPorLoHechoSinRedYDiceElMotivo() throws Exception { // spec 048 FR-014 FR-015
        when(users.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Actor", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        RegistroHabito r = registro();
        when(completarSinRed.completar(any(), any())).thenReturn(
                new CompletarHechoSinRedUseCase.CompletadoSinRed(r, CompletarHechoSinRedUseCase.MotivoSinPuntos.LLEGO_DESPUES_DE_48_H));

        mvc.perform(post("/api/v1/habit-tracks/" + r.id().value() + "/complete").header("X-Actor-Id", actor.toString())
                        .contentType("application/json")
                        .content("{\"hechoEn\":\"2026-11-10T02:10:00Z\",\"idCliente\":\"" + UUID.randomUUID()
                                + "\",\"origenDeLaHora\":\"RELOJ_DEL_SISTEMA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.motivoSinPuntos").value("LLEGO_DESPUES_DE_48_H"));
        verify(completar, never()).completar(any());
    }

    @Test void sinHoraRealSigueComoAntes() throws Exception {
        when(users.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Actor", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        RegistroHabito r = registro();
        when(completar.completar(any())).thenReturn(r);

        mvc.perform(post("/api/v1/habit-tracks/" + r.id().value() + "/complete").header("X-Actor-Id", actor.toString())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoSinPuntos").doesNotExist());
        verify(completarSinRed, never()).completar(any(), any());
    }
}
