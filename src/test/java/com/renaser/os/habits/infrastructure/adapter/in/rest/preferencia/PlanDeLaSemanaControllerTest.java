package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.PlanDeLaSemanaUseCase;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioDeFecha;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.PlanDeLaSemana.DiaDeLaFecha;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.habits.domain.model.preferencia.SemanaDelHabito;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 028 (D-296): «Solo esta semana». Contrato en {@code specs/028-planificacion-semana-y-mes/contracts/habit-preferences-week.md}. */
@WebMvcTest(PlanDeLaSemanaController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlanDeLaSemanaControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PlanDeLaSemanaUseCase useCase;
    @MockitoBean UserSummaryFinder users;
    private final UserId actor = UserId.of(UUID.randomUUID());
    private final HabitoId habito = HabitoId.of(UUID.randomUUID());
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void actor(UserStatus status) {
        when(users.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Fixture", null, UserRole.TRAINEE, status)));
    }

    private String ruta() {
        return "/api/v1/habit-preferences/" + habito.value() + "/week";
    }

    @Test
    void elGetTraeLosSieteDiasConSuFechaYSiSonPropiosYEditables() throws Exception {
        actor(UserStatus.ACTIVE);
        var base = Stream.of(DayOfWeek.values()).map(d -> new SemanaDelHabito.Dia(d, LocalTime.of(7, 0), null, false, true,
                RecordatorioDelDia.con(List.of(10)), false, CombinacionDeAlarma.COMO_HOY, false)).toList();
        var dias = SemanaCalendaria.desde(LUNES).fechas().stream()
                .map(f -> new DiaDeLaFecha(f, base.get(f.getDayOfWeek().getValue() - 1), f.getDayOfWeek() == DayOfWeek.SATURDAY,
                        f.isAfter(LocalDate.of(2026, 11, 4))))
                .toList();
        when(useCase.consultar(actor, habito, SemanaCalendaria.desde(LUNES))).thenReturn(dias);

        mvc.perform(get(ruta()).param("weekStart", "2026-11-02").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-11-02"))
                .andExpect(jsonPath("$.weekEnd").value("2026-11-08"))
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[5].date").value("2026-11-07"))
                .andExpect(jsonPath("$.days[5].weekday").value("SATURDAY"))
                .andExpect(jsonPath("$.days[5].triggerTime").value("07:00:00"))
                .andExpect(jsonPath("$.days[5].customDate").value(true))
                .andExpect(jsonPath("$.days[5].editable").value(true))
                .andExpect(jsonPath("$.days[5].reminderMinutesList[0]").value(10))
                .andExpect(jsonPath("$.days[5].alarm.sound").value(true))
                .andExpect(jsonPath("$.days[2].editable").value(false))
                .andExpect(jsonPath("$.days[2].customDate").value(false));
    }

    @Test
    void unaSemanaQueNoEmpiezaUnLunesEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(get(ruta()).param("weekStart", "2026-11-03").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(useCase);
    }

    @Test
    void guardarMandaSoloLoTocadoAlCasoDeUso() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(put(ruta()).header("X-Actor-Id", actor.toString()).contentType("application/json").content("""
                {"weekStart":"2026-11-02","days":[
                  {"date":"2026-11-07","triggerTime":"09:00"},
                  {"date":"2026-11-08","reminderEnabled":true,"reminderMinutesList":[30,0],
                   "alarm":{"sound":true,"vibration":false,"voice":false,"repeat":true}},
                  {"date":"2026-11-06","active":false},
                  {"date":"2026-11-05","reset":true}]}"""))
                .andExpect(status().isNoContent());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CambioDeFecha>> cambios = ArgumentCaptor.forClass(List.class);
        verify(useCase).guardar(any(), any(), any(), cambios.capture());
        var lista = cambios.getValue();
        assertThat(lista).hasSize(4);
        assertThat(lista.get(0).horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(lista.get(0).recordatorio()).isNull();
        assertThat(lista.get(1).recordatorio().antelaciones()).containsExactly(30, 0);
        assertThat(lista.get(1).alarma()).isEqualTo(CombinacionDeAlarma.de(true, false, false, true));
        assertThat(lista.get(2).activo()).isFalse();
        assertThat(lista.get(3).volverAlPlanDeSiempre()).isTrue();
    }

    @Test
    void unDiaQueVuelveAlPlanNoPuedeLlevarOtrosCambios() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(put(ruta()).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"weekStart\":\"2026-11-02\",\"days\":[{\"date\":\"2026-11-07\",\"reset\":true,\"triggerTime\":\"09:00\"}]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(useCase);
    }

    @Test
    void sinDiasOSinFechaEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(put(ruta()).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"weekStart\":\"2026-11-02\",\"days\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(put(ruta()).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"weekStart\":\"2026-11-02\",\"days\":[{\"triggerTime\":\"09:00\"}]}")).andExpect(status().isBadRequest());

        verifyNoInteractions(useCase);
    }

    @Test
    void unAvisoActivoSinAntelacionesOUnaAlarmaIncompletaEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(put(ruta()).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"weekStart\":\"2026-11-02\",\"days\":[{\"date\":\"2026-11-07\",\"reminderEnabled\":true}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ruta()).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"weekStart\":\"2026-11-02\",\"days\":[{\"date\":\"2026-11-07\",\"alarm\":{\"sound\":true}}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ruta()).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"weekStart\":\"2026-11-02\",\"days\":[{\"date\":\"2026-11-07\",\"alarm\":"
                        + "{\"sound\":false,\"vibration\":false,\"voice\":false,\"repeat\":false}}]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(useCase);
    }

    @Test
    void apagarUnObligatorioEs409() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new IllegalStateException("Este hábito es obligatorio y no se puede apagar")).when(useCase).guardar(any(), any(), any(), any());

        mvc.perform(put(ruta()).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"weekStart\":\"2026-11-02\",\"days\":[{\"date\":\"2026-11-07\",\"active\":false}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unHabitoQueNoLeCorrespondeEs404() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new NoSuchElementException("Habito no encontrado")).when(useCase).guardar(any(), any(), any(), any());

        mvc.perform(put(ruta()).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"weekStart\":\"2026-11-02\",\"days\":[{\"date\":\"2026-11-07\",\"triggerTime\":\"09:00\"}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void volverAlPlanDeSiempreEs204() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(delete(ruta()).param("weekStart", "2026-11-02").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isNoContent());

        verify(useCase).volverAlPlanDeSiempre(actor, habito, SemanaCalendaria.desde(LUNES));
    }

    /** Prueba negativa obligatoria: una cuenta suspendida no tiene USE_APP. */
    @Test
    void unaCuentaSuspendidaNoPuedeLeerNiGuardarNiVolverAlPlan() throws Exception {
        actor(UserStatus.SUSPENDED);

        mvc.perform(get(ruta()).param("weekStart", "2026-11-02").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(put(ruta()).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"weekStart\":\"2026-11-02\",\"days\":[{\"date\":\"2026-11-07\",\"triggerTime\":\"09:00\"}]}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(ruta()).param("weekStart", "2026-11-02").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(useCase);
    }
}
