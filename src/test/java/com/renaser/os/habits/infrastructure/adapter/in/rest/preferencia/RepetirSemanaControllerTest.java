package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.DiaDeLaOferta;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.HabitoDeLaOferta;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.HabitoExcluido;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.OfertaDeRepeticion;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.ResultadoDeRepeticion;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 028 (D-296): repetir la semana. Contrato en {@code specs/028-planificacion-semana-y-mes/contracts/habit-preferences-week-repeat.md}. */
@WebMvcTest(RepetirSemanaController.class)
@AutoConfigureMockMvc(addFilters = false)
class RepetirSemanaControllerTest {

    private static final SemanaCalendaria MODELO = SemanaCalendaria.desde(LocalDate.of(2026, 10, 26));
    private static final SemanaCalendaria DESTINO = MODELO.siguiente();

    @Autowired MockMvc mvc;
    @MockitoBean RepetirSemanaUseCase useCase;
    @MockitoBean UserSummaryFinder users;
    private final UserId actor = UserId.of(UUID.randomUUID());

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void actor(UserStatus status) {
        when(users.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Fixture", null, UserRole.TRAINEE, status)));
    }

    @Test
    void sinOfertaResponde204() throws Exception {
        actor(UserStatus.ACTIVE);
        when(useCase.ofrecer(actor)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/habit-preferences/week-repeat").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void laOfertaTraeLasSemanasLaHuellaLosDiasYLosExcluidos() throws Exception {
        actor(UserStatus.ACTIVE);
        var despertar = HabitoId.of(UUID.randomUUID());
        var viejo = HabitoId.of(UUID.randomUUID());
        var dia = new DiaDeLaOferta(LocalDate.of(2026, 11, 7), DayOfWeek.SATURDAY, true, LocalTime.of(9, 0), true, List.of(30), null,
                true, false);
        when(useCase.ofrecer(actor)).thenReturn(Optional.of(new OfertaDeRepeticion(MODELO, DESTINO, "9f2c4a1b7d3e8a05", true,
                List.of(new HabitoDeLaOferta(despertar, "Despertar", List.of(dia))),
                List.of(new HabitoExcluido(viejo, "Viejo", HabitoExcluido.NO_DISPONIBLE)))));

        mvc.perform(get("/api/v1/habit-preferences/week-repeat").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceWeekStart").value("2026-10-26"))
                .andExpect(jsonPath("$.targetWeekStart").value("2026-11-02"))
                .andExpect(jsonPath("$.fingerprint").value("9f2c4a1b7d3e8a05"))
                .andExpect(jsonPath("$.replacesOwnChanges").value(true))
                .andExpect(jsonPath("$.habits[0].title").value("Despertar"))
                .andExpect(jsonPath("$.habits[0].days[0].date").value("2026-11-07"))
                .andExpect(jsonPath("$.habits[0].days[0].weekday").value("SATURDAY"))
                .andExpect(jsonPath("$.habits[0].days[0].triggerTime").value("09:00:00"))
                .andExpect(jsonPath("$.habits[0].days[0].customTime").value(true))
                .andExpect(jsonPath("$.habits[0].days[0].reminderMinutesList[0]").value(30))
                .andExpect(jsonPath("$.habits[0].days[0].replaces").value(true))
                .andExpect(jsonPath("$.excluded[0].title").value("Viejo"))
                .andExpect(jsonPath("$.excluded[0].reason").value("NOT_AVAILABLE"));
    }

    @Test
    void repetirManda200ConElResultado() throws Exception {
        actor(UserStatus.ACTIVE);
        when(useCase.repetir(actor, MODELO, DESTINO, false)).thenReturn(new ResultadoDeRepeticion(DESTINO, 5, 0, 3, List.of()));

        mvc.perform(post("/api/v1/habit-preferences/week-repeat").header("X-Actor-Id", actor.toString())
                .contentType("application/json")
                .content("{\"sourceWeekStart\":\"2026-10-26\",\"targetWeekStart\":\"2026-11-02\",\"replace\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetWeekStart").value("2026-11-02"))
                .andExpect(jsonPath("$.copiedDays").value(5))
                .andExpect(jsonPath("$.removedDays").value(0))
                .andExpect(jsonPath("$.habits").value(3));
    }

    @Test
    void reemplazarSinConfirmarEs409() throws Exception {
        actor(UserStatus.ACTIVE);
        when(useCase.repetir(actor, MODELO, DESTINO, false)).thenThrow(new IllegalStateException("Esa semana ya tiene cambios tuyos"));

        mvc.perform(post("/api/v1/habit-preferences/week-repeat").header("X-Actor-Id", actor.toString())
                .contentType("application/json")
                .content("{\"sourceWeekStart\":\"2026-10-26\",\"targetWeekStart\":\"2026-11-02\",\"replace\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void sinReplaceOConSemanasQueNoSonLunesEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(post("/api/v1/habit-preferences/week-repeat").header("X-Actor-Id", actor.toString())
                .contentType("application/json").content("{\"sourceWeekStart\":\"2026-10-26\",\"targetWeekStart\":\"2026-11-02\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/habit-preferences/week-repeat").header("X-Actor-Id", actor.toString())
                .contentType("application/json")
                .content("{\"sourceWeekStart\":\"2026-10-27\",\"targetWeekStart\":\"2026-11-02\",\"replace\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(useCase);
    }

    @Test
    void deshacerLaSemanaDevuelveCuantasFechas() throws Exception {
        actor(UserStatus.ACTIVE);
        when(useCase.deshacerSemana(actor, DESTINO)).thenReturn(5);

        mvc.perform(delete("/api/v1/habit-preferences/weeks/2026-11-02").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removedDays").value(5));

        verify(useCase).deshacerSemana(actor, DESTINO);
    }

    @Test
    void deshacerUnaSemanaQueNoEmpiezaUnLunesEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(delete("/api/v1/habit-preferences/weeks/2026-11-03").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isBadRequest());
    }

    /** Prueba negativa obligatoria: una cuenta suspendida no tiene USE_APP. */
    @Test
    void unaCuentaSuspendidaNoPuedeNada() throws Exception {
        actor(UserStatus.SUSPENDED);

        mvc.perform(get("/api/v1/habit-preferences/week-repeat").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/habit-preferences/week-repeat").header("X-Actor-Id", actor.toString())
                .contentType("application/json")
                .content("{\"sourceWeekStart\":\"2026-10-26\",\"targetWeekStart\":\"2026-11-02\",\"replace\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/habit-preferences/weeks/2026-11-02").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(useCase);
    }
}
