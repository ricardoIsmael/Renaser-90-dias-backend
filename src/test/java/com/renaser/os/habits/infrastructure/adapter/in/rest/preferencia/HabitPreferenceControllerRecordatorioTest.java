package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.CambiarEstadoHabitoEnFechaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.CuotaEdicion;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.HorarioDeHabito;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.ResumenPreferenciasHorario;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase.DiaDeLaSemana;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.habits.domain.model.preferencia.SemanaDelHabito;
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
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 008 (D-282): el aviso de cada dia de la semana. Contrato en
 * {@code specs/008-recordatorio-por-dia/contracts/habit-preferences-weekdays.md}.
 */
@WebMvcTest(HabitPreferenceController.class)
@AutoConfigureMockMvc(addFilters = false)
class HabitPreferenceControllerRecordatorioTest {

    @Autowired MockMvc mvc;
    @MockitoBean EditarPreferenciaHorarioUseCase editar;
    @MockitoBean ConsultarPreferenciasHorarioUseCase consultar;
    @MockitoBean CambiarEstadoHabitoEnFechaUseCase cambiarEstadoEnFecha;
    @MockitoBean EditarHorarioSemanalUseCase horarioSemanal;
    @MockitoBean UserSummaryFinder users;
    private final UserId actor = UserId.of(UUID.randomUUID());
    private final HabitoId habito = HabitoId.of(UUID.randomUUID());

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void actor(UserStatus status) {
        when(users.findById(actor)).thenReturn(Optional.of(
                new UserSummary(actor, "Fixture", null, UserRole.TRAINEE, status)));
    }

    private String ruta(String dia) {
        return "/api/v1/habit-preferences/" + habito.value() + "/weekdays/" + dia + "/reminder";
    }

    @Test
    void guardarElAvisoDeUnDiaLlegaAlCasoDeUsoYResponde204() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(put(ruta("SATURDAY")).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"reminderEnabled\":true,\"reminderMinutesList\":[30,10]}"))
                .andExpect(status().isNoContent());

        verify(horarioSemanal).fijarRecordatorio(actor, habito, DayOfWeek.SATURDAY, true, List.of(30, 10));
    }

    @Test
    void sinAvisoPuedeOmitirLaLista() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(put(ruta("SUNDAY")).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"reminderEnabled\":false}"))
                .andExpect(status().isNoContent());

        verify(horarioSemanal).fijarRecordatorio(eq(actor), eq(habito), eq(DayOfWeek.SUNDAY), eq(false), isNull());
    }

    @Test
    void unValorFueraDeRangoEs400() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new IllegalArgumentException("Los minutos del recordatorio deben estar entre 0 y 32767"))
                .when(horarioSemanal).fijarRecordatorio(any(), any(), any(), anyBoolean(), anyList());

        mvc.perform(put(ruta("SATURDAY")).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"reminderEnabled\":true,\"reminderMinutesList\":[-5]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void omitirReminderEnabledEs400SinTocarNada() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(put(ruta("SATURDAY")).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"reminderMinutesList\":[30]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(horarioSemanal);
    }

    @Test
    void unDiaQueNoExisteEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(put(ruta("FUNDAY")).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"reminderEnabled\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(horarioSemanal);
    }

    /** Prueba negativa obligatoria: una cuenta suspendida no tiene USE_APP. */
    @Test
    void unaCuentaSuspendidaNoPuedeGuardarElAviso() throws Exception {
        actor(UserStatus.SUSPENDED);

        mvc.perform(put(ruta("SATURDAY")).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"reminderEnabled\":true,\"reminderMinutesList\":[30]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(horarioSemanal);
    }

    /** FR-013: guardar la hora no manda ni borra el aviso: son dos escrituras separadas. */
    @Test
    void guardarLaHoraDeUnDiaNoTocaElAviso() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(put("/api/v1/habit-preferences/{id}/weekdays/SATURDAY", habito.value())
                .header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"triggerTime\":\"09:00:00\",\"limitTime\":null}"))
                .andExpect(status().isNoContent());

        verify(horarioSemanal).fijar(actor, habito, DayOfWeek.SATURDAY, LocalTime.of(9, 0), null);
        verify(horarioSemanal, never()).fijarRecordatorio(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void elGetDeLaSemanaTraeElAvisoResueltoYSiEsPropio() throws Exception {
        actor(UserStatus.ACTIVE);
        when(horarioSemanal.consultar(actor, habito)).thenReturn(List.of(
                new DiaDeLaSemana(DayOfWeek.SATURDAY, LocalTime.of(9, 0), null, true, true, true, List.of(30), true),
                new DiaDeLaSemana(DayOfWeek.SUNDAY, LocalTime.of(7, 0), null, false, true, false, List.of(), true),
                new DiaDeLaSemana(DayOfWeek.MONDAY, LocalTime.of(7, 0), null, false, true, true, List.of(10, 0),
                        false)));

        mvc.perform(get("/api/v1/habit-preferences/{id}/weekdays", habito.value())
                .header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekdays[0].weekday").value("SATURDAY"))
                .andExpect(jsonPath("$.weekdays[0].custom").value(true))
                .andExpect(jsonPath("$.weekdays[0].reminderEnabled").value(true))
                .andExpect(jsonPath("$.weekdays[0].reminderMinutesList[0]").value(30))
                .andExpect(jsonPath("$.weekdays[0].customReminder").value(true))
                .andExpect(jsonPath("$.weekdays[1].custom").value(false))
                .andExpect(jsonPath("$.weekdays[1].reminderEnabled").value(false))
                .andExpect(jsonPath("$.weekdays[1].reminderMinutesList").isEmpty())
                .andExpect(jsonPath("$.weekdays[1].customReminder").value(true))
                .andExpect(jsonPath("$.weekdays[2].reminderMinutesList[1]").value(0))
                .andExpect(jsonPath("$.weekdays[2].customReminder").value(false));
    }

    private static List<SemanaDelHabito.Dia> sieteDias() {
        return Stream.of(DayOfWeek.values()).map(d -> new SemanaDelHabito.Dia(d, LocalTime.of(7, 0), null,
                d == DayOfWeek.SATURDAY, true, d == DayOfWeek.SATURDAY ? RecordatorioDelDia.con(List.of(30))
                        : RecordatorioDelDia.con(List.of(10)), d == DayOfWeek.SATURDAY)).toList();
    }

    /** La lista de preferencias suma los siete dias ya resueltos; los campos de siempre no cambian. */
    @Test
    void elGetDeLaListaTraeLosSieteDiasPorHabito() throws Exception {
        actor(UserStatus.ACTIVE);
        var fecha = LocalDate.of(2026, 10, 20);
        var item = new HorarioDeHabito(habito, "Gimnasio", LocalTime.of(7, 0), null, true, true, 10, null,
                List.of(10), sieteDias());
        when(consultar.consultar(actor, fecha)).thenReturn(new ResumenPreferenciasHorario(List.of(item),
                new CuotaEdicion(0, 3, 3, "WEEK")));

        mvc.perform(get("/api/v1/habit-preferences").param("date", fecha.toString())
                .header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habits[0].reminderMinutesBefore").value(10))
                .andExpect(jsonPath("$.habits[0].weekdays.length()").value(7))
                .andExpect(jsonPath("$.habits[0].weekdays[5].weekday").value("SATURDAY"))
                .andExpect(jsonPath("$.habits[0].weekdays[5].triggerTime").value("07:00:00"))
                .andExpect(jsonPath("$.habits[0].weekdays[5].active").value(true))
                .andExpect(jsonPath("$.habits[0].weekdays[5].reminderEnabled").value(true))
                .andExpect(jsonPath("$.habits[0].weekdays[5].reminderMinutesList[0]").value(30))
                .andExpect(jsonPath("$.habits[0].weekdays[5].customTime").value(true))
                .andExpect(jsonPath("$.habits[0].weekdays[5].customReminder").value(true))
                .andExpect(jsonPath("$.habits[0].weekdays[0].customReminder").value(false))
                .andExpect(jsonPath("$.habits[0].weekdays[0].reminderMinutesList[0]").value(10));
    }
}
