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
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.habits.domain.model.preferencia.SemanaDelHabito;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
 * Spec 009 (D-283): como suena la alarma de cada habito y de cada dia. Contrato en
 * {@code specs/009-tipos-de-alarma/contracts/habit-preferences-alarm.md}.
 */
@WebMvcTest(HabitPreferenceController.class)
@AutoConfigureMockMvc(addFilters = false)
class HabitPreferenceControllerAlarmaTest {

    private static final String SUENA = "{\"sound\":true,\"vibration\":true,\"voice\":false,\"repeat\":true}";

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

    private String general() {
        return "/api/v1/habit-preferences/" + habito.value() + "/alarm";
    }

    private String dia(String dia) {
        return "/api/v1/habit-preferences/" + habito.value() + "/weekdays/" + dia + "/alarm";
    }

    private org.springframework.test.web.servlet.ResultActions guardar(String ruta, String cuerpo) throws Exception {
        return mvc.perform(put(ruta).header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content(cuerpo));
    }

    // ---- PUT general -----------------------------------------------------------------------------

    @Test
    void guardarLaAlarmaGeneralLlegaAlCasoDeUsoYResponde204() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar(general(), SUENA).andExpect(status().isNoContent());

        verify(editar).fijarCombinacion(actor, habito, CombinacionDeAlarma.de(true, true, false, true));
    }

    @Test
    void soloVibrarEsValidoYLaVozConSonidoTambien() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar(general(), "{\"sound\":false,\"vibration\":true,\"voice\":false,\"repeat\":false}")
                .andExpect(status().isNoContent());
        guardar(general(), "{\"sound\":true,\"vibration\":false,\"voice\":true,\"repeat\":false}")
                .andExpect(status().isNoContent());

        verify(editar).fijarCombinacion(actor, habito, CombinacionDeAlarma.de(false, true, false, false));
        verify(editar).fijarCombinacion(actor, habito, CombinacionDeAlarma.de(true, false, true, false));
    }

    @Test
    void sinSonidoNiVibracionEs400YNoTocaNada() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar(general(), "{\"sound\":false,\"vibration\":false,\"voice\":false,\"repeat\":true}")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(editar);
    }

    @Test
    void conVozPeroSinSonidoEs400YNoTocaNada() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar(general(), "{\"sound\":false,\"vibration\":true,\"voice\":true,\"repeat\":false}")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(editar);
    }

    /** Omitir un campo es un 400 y no un {@code false}: apagaria el sonido o la vibracion sin que nadie lo pida. */
    @Test
    void omitirUnCampoEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar(general(), "{\"sound\":true,\"vibration\":true,\"voice\":false}").andExpect(status().isBadRequest());
        guardar(general(), "{\"vibration\":true,\"voice\":false,\"repeat\":false}").andExpect(status().isBadRequest());
        guardar(general(), "{}").andExpect(status().isBadRequest());

        verifyNoInteractions(editar);
    }

    @Test
    void unHabitoInexistenteEs404() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new NoSuchElementException("Habito no encontrado")).when(editar).fijarCombinacion(any(), any(), any());

        guardar(general(), SUENA).andExpect(status().isNotFound());
    }

    @Test
    void unHabitoPersonalAjenoEs403() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new NotAuthorizedException("Solo puedes editar tus propios habitos")).when(editar)
                .fijarCombinacion(any(), any(), any());

        guardar(general(), SUENA).andExpect(status().isForbidden());
    }

    /** Prueba negativa obligatoria: una cuenta suspendida no tiene USE_APP. */
    @Test
    void unaCuentaSuspendidaNoPuedeGuardarLaAlarmaGeneral() throws Exception {
        actor(UserStatus.SUSPENDED);

        guardar(general(), SUENA).andExpect(status().isForbidden());

        verifyNoInteractions(editar);
    }

    // ---- PUT de un dia ---------------------------------------------------------------------------

    @Test
    void guardarLaAlarmaDeUnDiaLlegaAlCasoDeUsoYResponde204() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar(dia("SATURDAY"), "{\"sound\":false,\"vibration\":true,\"voice\":false,\"repeat\":false}")
                .andExpect(status().isNoContent());

        verify(horarioSemanal).fijarCombinacion(actor, habito, DayOfWeek.SATURDAY,
                CombinacionDeAlarma.de(false, true, false, false));
    }

    @Test
    void laAlarmaDeUnDiaConSonidoYVibracionApagadosEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar(dia("SATURDAY"), "{\"sound\":false,\"vibration\":false,\"voice\":false,\"repeat\":false}")
                .andExpect(status().isBadRequest());
        guardar(dia("SATURDAY"), "{\"sound\":false,\"vibration\":true,\"voice\":true,\"repeat\":false}")
                .andExpect(status().isBadRequest());

        verify(horarioSemanal, never()).fijarCombinacion(any(), any(), any(), any());
    }

    @Test
    void unDiaQueNoExisteEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar(dia("FUNDAY"), SUENA).andExpect(status().isBadRequest());

        verifyNoInteractions(horarioSemanal);
    }

    @Test
    void laAlarmaDeUnDiaDeUnHabitoInexistenteEs404YDeUnoAjenoEs403() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new NoSuchElementException("Habito no encontrado")).when(horarioSemanal)
                .fijarCombinacion(any(), any(), any(), any());
        guardar(dia("SATURDAY"), SUENA).andExpect(status().isNotFound());

        doThrow(new NotAuthorizedException("Solo puedes editar tus propios habitos")).when(horarioSemanal)
                .fijarCombinacion(any(), any(), any(), any());
        guardar(dia("SATURDAY"), SUENA).andExpect(status().isForbidden());
    }

    /** Prueba negativa obligatoria: una cuenta suspendida no tiene USE_APP. */
    @Test
    void unaCuentaSuspendidaNoPuedeGuardarLaAlarmaDeUnDia() throws Exception {
        actor(UserStatus.SUSPENDED);

        guardar(dia("SATURDAY"), SUENA).andExpect(status().isForbidden());

        verifyNoInteractions(horarioSemanal);
    }

    /** Hora, aviso y alarma son tres escrituras separadas: guardar una no manda las otras. */
    @Test
    void guardarLaHoraOElAvisoNoTocaLaAlarma() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(put("/api/v1/habit-preferences/{id}/weekdays/SATURDAY", habito.value())
                .header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"triggerTime\":\"09:00:00\",\"limitTime\":null}"))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/habit-preferences/{id}/weekdays/SATURDAY/reminder", habito.value())
                .header("X-Actor-Id", actor.toString()).contentType("application/json")
                .content("{\"reminderEnabled\":false}"))
                .andExpect(status().isNoContent());

        verify(horarioSemanal, never()).fijarCombinacion(any(), any(), any(), any());
        verify(editar, never()).fijarCombinacion(any(), any(), any());
    }

    @Test
    void guardarLaAlarmaNoTocaLaHoraNiElAviso() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar(dia("SATURDAY"), SUENA).andExpect(status().isNoContent());

        verify(horarioSemanal, never()).fijar(any(), any(), any(), any(), any());
        verify(horarioSemanal, never()).fijarRecordatorio(any(), any(), any(), anyBoolean(), any());
    }

    // ---- lecturas ---------------------------------------------------------------------------------

    @Test
    void elGetDeLaSemanaTraeLaAlarmaResueltaYSiEsPropia() throws Exception {
        actor(UserStatus.ACTIVE);
        var solaVibracion = CombinacionDeAlarma.de(false, true, false, false);
        var conVoz = CombinacionDeAlarma.de(true, true, true, true);
        when(horarioSemanal.consultar(actor, habito)).thenReturn(List.of(
                new DiaDeLaSemana(DayOfWeek.SATURDAY, LocalTime.of(9, 0), null, false, true, true, List.of(30), false,
                        solaVibracion, true),
                new DiaDeLaSemana(DayOfWeek.SUNDAY, LocalTime.of(7, 0), null, false, true, true, List.of(10), false,
                        conVoz, false)));

        mvc.perform(get("/api/v1/habit-preferences/{id}/weekdays", habito.value())
                .header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekdays[0].alarm.sound").value(false))
                .andExpect(jsonPath("$.weekdays[0].alarm.vibration").value(true))
                .andExpect(jsonPath("$.weekdays[0].alarm.voice").value(false))
                .andExpect(jsonPath("$.weekdays[0].alarm.repeat").value(false))
                .andExpect(jsonPath("$.weekdays[0].customAlarm").value(true))
                .andExpect(jsonPath("$.weekdays[1].alarm.sound").value(true))
                .andExpect(jsonPath("$.weekdays[1].alarm.voice").value(true))
                .andExpect(jsonPath("$.weekdays[1].alarm.repeat").value(true))
                .andExpect(jsonPath("$.weekdays[1].customAlarm").value(false))
                // Lo de la 008 sigue donde estaba.
                .andExpect(jsonPath("$.weekdays[0].reminderMinutesList[0]").value(30))
                .andExpect(jsonPath("$.weekdays[0].customReminder").value(false));
    }

    private static List<SemanaDelHabito.Dia> sieteDias() {
        return Stream.of(DayOfWeek.values()).map(d -> new SemanaDelHabito.Dia(d, LocalTime.of(7, 0), null,
                false, true, RecordatorioDelDia.con(List.of(10)), false,
                d == DayOfWeek.SATURDAY ? CombinacionDeAlarma.de(false, true, false, false)
                        : CombinacionDeAlarma.de(true, true, true, false), d == DayOfWeek.SATURDAY)).toList();
    }

    @Test
    void elGetDeLaListaTraeLaAlarmaGeneralDeCadaHabitoYLaDeCadaDia() throws Exception {
        actor(UserStatus.ACTIVE);
        var fecha = LocalDate.of(2026, 10, 20);
        var item = new HorarioDeHabito(habito, "Gimnasio", LocalTime.of(7, 0), null, true, true, 10, null,
                List.of(10), sieteDias(), CombinacionDeAlarma.de(true, true, true, false));
        when(consultar.consultar(actor, fecha)).thenReturn(new ResumenPreferenciasHorario(List.of(item),
                new CuotaEdicion(0, 3, 3, "WEEK")));

        mvc.perform(get("/api/v1/habit-preferences").param("date", fecha.toString())
                .header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habits[0].alarm.sound").value(true))
                .andExpect(jsonPath("$.habits[0].alarm.vibration").value(true))
                .andExpect(jsonPath("$.habits[0].alarm.voice").value(true))
                .andExpect(jsonPath("$.habits[0].alarm.repeat").value(false))
                .andExpect(jsonPath("$.habits[0].weekdays[5].weekday").value("SATURDAY"))
                .andExpect(jsonPath("$.habits[0].weekdays[5].alarm.sound").value(false))
                .andExpect(jsonPath("$.habits[0].weekdays[5].alarm.vibration").value(true))
                .andExpect(jsonPath("$.habits[0].weekdays[5].customAlarm").value(true))
                .andExpect(jsonPath("$.habits[0].weekdays[0].alarm.voice").value(true))
                .andExpect(jsonPath("$.habits[0].weekdays[0].customAlarm").value(false))
                // Lo de la 008 y lo de siempre no cambia.
                .andExpect(jsonPath("$.habits[0].reminderMinutesBefore").value(10))
                .andExpect(jsonPath("$.habits[0].weekdays[5].customReminder").value(false));
    }

    /** Un servicio que arma el item sin alarma (forma anterior) responde la de hoy: nunca un campo en null. */
    @Test
    void sinAlarmaElItemTraeLaDeHoy() throws Exception {
        actor(UserStatus.ACTIVE);
        var fecha = LocalDate.of(2026, 10, 20);
        var item = new HorarioDeHabito(habito, "Gimnasio", LocalTime.of(7, 0), null, false, false, null, null);
        when(consultar.consultar(actor, fecha)).thenReturn(new ResumenPreferenciasHorario(List.of(item),
                new CuotaEdicion(0, 3, 3, "WEEK")));

        mvc.perform(get("/api/v1/habit-preferences").param("date", fecha.toString())
                .header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habits[0].alarm.sound").value(true))
                .andExpect(jsonPath("$.habits[0].alarm.vibration").value(true))
                .andExpect(jsonPath("$.habits[0].alarm.voice").value(false))
                .andExpect(jsonPath("$.habits[0].alarm.repeat").value(false))
                .andExpect(jsonPath("$.habits[0].weekdays").isEmpty());
    }
}
