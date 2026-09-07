package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.CambiarEstadoHabitoEnFechaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.EditarPreferenciaHorarioCommand;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.ResultadoEdicionPreferencia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HabitPreferenceController.class)
@AutoConfigureMockMvc(addFilters = false)
class HabitPreferenceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean EditarPreferenciaHorarioUseCase editar;
    @MockitoBean ConsultarPreferenciasHorarioUseCase consultar;
    @MockitoBean CambiarEstadoHabitoEnFechaUseCase cambiarEstadoEnFecha;
    @MockitoBean UserSummaryFinder users;
    private final UserId actor = UserId.of(UUID.randomUUID());
    private final HabitoId habito = HabitoId.of(UUID.randomUUID());
    private final LocalDate fecha = LocalDate.of(2026, 9, 9);

    @AfterEach
    void limpiar() { SecurityContextHolder.clearContext(); }

    void actor(UserStatus status) {
        when(users.findById(actor)).thenReturn(Optional.of(
                new UserSummary(actor, "Fixture", null, UserRole.TRAINEE, status)));
    }

    @Test
    void enviaFechaExactaAlCasoDeUso() throws Exception {
        actor(UserStatus.ACTIVE);
        when(editar.editar(any())).thenReturn(new ResultadoEdicionPreferencia(habito, LocalTime.of(9, 0),
                null, true, fecha, 0, 3, 3, "FREE"));
        mvc.perform(patch("/api/v1/habit-preferences/{id}", habito.value()).header("X-Actor-Id", actor.toString())
                .contentType("application/json").content("""
                    {"triggerTime":"09:00:00","limitTime":null,"reminderEnabled":false,
                     "reminderMinutesBefore":null,"date":"2026-09-09"}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deferredEffectiveDate").value("2026-09-09"));
        verify(editar).editar(new EditarPreferenciaHorarioCommand(actor, habito, LocalTime.of(9, 0),
                null, false, null, fecha));
    }

    @Test
    void clienteAnteriorPuedeOmitirFecha() throws Exception {
        actor(UserStatus.ACTIVE);
        when(editar.editar(any())).thenReturn(new ResultadoEdicionPreferencia(habito, LocalTime.of(9, 0),
                null, true, fecha, 0, 3, 3, "FREE"));
        mvc.perform(patch("/api/v1/habit-preferences/{id}", habito.value()).header("X-Actor-Id", actor.toString())
                .contentType("application/json").content("""
                    {"triggerTime":"09:00:00","limitTime":null,"reminderEnabled":false,"reminderMinutesBefore":null}
                    """))
                .andExpect(status().isOk());
        verify(editar).editar(new EditarPreferenciaHorarioCommand(actor, habito, LocalTime.of(9, 0),
                null, false, null, null));
    }

    @Test
    void consultaLaFechaSolicitadaYRechazaFormatoInvalido() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultar.consultar(actor, fecha)).thenReturn(new ConsultarPreferenciasHorarioUseCase.ResumenPreferenciasHorario(
                List.of(), new ConsultarPreferenciasHorarioUseCase.CuotaEdicion(0, 3, 3, "FREE")));
        mvc.perform(get("/api/v1/habit-preferences").param("date", fecha.toString()).header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk());
        verify(consultar).consultar(actor, fecha);
        mvc.perform(get("/api/v1/habit-preferences").param("date", "2026-02-30").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suspendidoNoPuedeConsultarNiEditarFechas() throws Exception {
        actor(UserStatus.SUSPENDED);
        mvc.perform(get("/api/v1/habit-preferences").param("date", fecha.toString()).header("X-Actor-Id", actor.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/habit-preferences/{id}", habito.value()).header("X-Actor-Id", actor.toString())
                .contentType("application/json").content("""
                    {"triggerTime":"09:00:00","limitTime":null,"reminderEnabled":false,
                     "reminderMinutesBefore":null,"date":"2026-09-09"}
                    """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(editar, consultar);
    }

    /**
     * El interruptor de un dia va por su propia ruta y con `active` como unico campo: ni hora, ni
     * cuota, ni la restriccion de "solo fechas futuras" que si aplica a mover el horario.
     */
    @Test
    void apagarUnDiaLlegaAlCasoDeUsoConEsaFecha() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(patch("/api/v1/habit-preferences/{id}/days/{date}", habito.value(), "2026-09-09")
                .header("X-Actor-Id", actor.toString())
                .contentType("application/json").content("""
                    {"active":false}
                    """))
                .andExpect(status().isNoContent());

        verify(cambiarEstadoEnFecha).cambiarEstadoEnFecha(actor, habito, fecha, false);
    }

    @Test
    void volverAEncenderUnDiaTambienLlegaAlCasoDeUso() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(patch("/api/v1/habit-preferences/{id}/days/{date}", habito.value(), "2026-09-09")
                .header("X-Actor-Id", actor.toString())
                .contentType("application/json").content("""
                    {"active":true}
                    """))
                .andExpect(status().isNoContent());

        verify(cambiarEstadoEnFecha).cambiarEstadoEnFecha(actor, habito, fecha, true);
    }

    /**
     * `active` es `Boolean` y no `boolean` justamente para que omitirlo de un 400 de VALIDACION y
     * no uno opaco de deserializacion — el bug del 2026-09-03 con `reminderEnabled`.
     */
    @Test
    void omitirActiveNoEscribeNada() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(patch("/api/v1/habit-preferences/{id}/days/{date}", habito.value(), "2026-09-09")
                .header("X-Actor-Id", actor.toString())
                .contentType("application/json").content("{}"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(cambiarEstadoEnFecha);
    }
}
