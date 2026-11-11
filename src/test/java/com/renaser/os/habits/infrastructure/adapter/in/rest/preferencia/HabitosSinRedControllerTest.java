package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.DesbloqueosSinRedUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.QuitarHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase;
import com.renaser.os.habits.application.ports.in.habito.HabitoPersonalSinRedUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.CambiarEstadoHabitoEnFechaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.PlanDeLaSemanaSinRedUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.PlanDeLaSemanaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.PreferenciasSinRedUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.ResultadoDeRepeticion;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.habits.infrastructure.adapter.in.rest.desbloqueo.HabitUnlockController;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habito.MisHabitosController;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Planificar hábitos y alarmas con las tres cabeceras de lo hecho sin red (spec 048, fase 3, FR-019): con {@code X-Id-Cliente} y {@code X-Hecho-En} cada escritura va por el caso de uso de
 * lo hecho sin red, con su hora real; sin ellas, exactamente por lo de siempre; lo que no se aplica es un 409 con {@code codigo}; una cuenta suspendida, 403.
 */
@WebMvcTest(controllers = {HabitPreferenceController.class, PlanDeLaSemanaController.class, RepetirSemanaController.class,
        HabitUnlockController.class, MisHabitosController.class})
@AutoConfigureMockMvc(addFilters = false)
class HabitosSinRedControllerTest {

    private static final String ID = "5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10";
    private static final String HORA = "2026-11-10T02:40:13.250Z";

    @Autowired MockMvc mvc;
    @MockitoBean EditarPreferenciaHorarioUseCase editar;
    @MockitoBean ConsultarPreferenciasHorarioUseCase consultar;
    @MockitoBean CambiarEstadoHabitoEnFechaUseCase cambiarEstadoEnFecha;
    @MockitoBean EditarHorarioSemanalUseCase horarioSemanal;
    @MockitoBean PreferenciasSinRedUseCase sinRed;
    @MockitoBean PlanDeLaSemanaUseCase planDeLaSemana;
    @MockitoBean PlanDeLaSemanaSinRedUseCase semanaSinRed;
    @MockitoBean RepetirSemanaUseCase repetir;
    @MockitoBean ConsultarDesbloqueosHabitoUseCase consultarDesbloqueos;
    @MockitoBean ElegirHabitoUseCase elegir;
    @MockitoBean CambiarEstadoHabitoDelPlanUseCase cambiarEstado;
    @MockitoBean QuitarHabitoDelPlanUseCase quitar;
    @MockitoBean DesbloqueosSinRedUseCase desbloqueosSinRed;
    @MockitoBean ConsultarMisHabitosUseCase consultarMisHabitos;
    @MockitoBean CrearHabitoPersonalUseCase crearHabito;
    @MockitoBean HabitoPersonalSinRedUseCase habitoSinRed;
    @MockitoBean UserSummaryFinder users;
    private final UserId actor = UserId.of(UUID.randomUUID());
    private final UUID habito = UUID.randomUUID();

    @BeforeEach
    void actorActivo() {
        when(users.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Fixture", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequestBuilder conCabeceras(MockHttpServletRequestBuilder pedido) {
        return pedido.header("X-Actor-Id", actor.toString()).header("X-Id-Cliente", ID).header("X-Hecho-En", HORA)
                .header("X-Origen-De-La-Hora", "RELOJ_DEL_SISTEMA").contentType("application/json");
    }

    private MockHttpServletRequestBuilder sinCabeceras(MockHttpServletRequestBuilder pedido) {
        return pedido.header("X-Actor-Id", actor.toString()).contentType("application/json");
    }

    @Test
    @DisplayName("PUT de la hora de un día con las cabeceras va por lo hecho sin red con su hora real; sin ellas, por lo de siempre")
    void horaDeUnDia() throws Exception { // spec 048 FR-019
        String cuerpo = "{\"triggerTime\":\"06:30:00\",\"limitTime\":\"08:00:00\"}";
        mvc.perform(conCabeceras(put("/api/v1/habit-preferences/" + habito + "/weekdays/MONDAY")).content(cuerpo)).andExpect(status().isNoContent());

        ArgumentCaptor<HechoSinRed> hecho = ArgumentCaptor.forClass(HechoSinRed.class);
        verify(sinRed).fijarDiaDeLaSemana(eq(actor), eq(HabitoId.of(habito)), eq(DayOfWeek.MONDAY), eq(LocalTime.of(6, 30)), eq(LocalTime.of(8, 0)), hecho.capture());
        assertThat(hecho.getValue().hechoEn()).isEqualTo(Instant.parse(HORA));
        assertThat(hecho.getValue().idCliente()).isEqualTo(UUID.fromString(ID));
        assertThat(hecho.getValue().origenDeLaHora()).isEqualTo(OrigenDeLaHora.RELOJ_DEL_SISTEMA);
        verifyNoInteractions(horarioSemanal);

        mvc.perform(sinCabeceras(put("/api/v1/habit-preferences/" + habito + "/weekdays/MONDAY")).content(cuerpo)).andExpect(status().isNoContent());
        verify(horarioSemanal).fijar(actor, HabitoId.of(habito), DayOfWeek.MONDAY, LocalTime.of(6, 30), LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("borrar el día de la semana, apagarlo, el aviso y la alarma tienen su camino sin red; sin cabeceras no tocan lo hecho sin red")
    void quitarApagarAvisoYAlarma() throws Exception { // spec 048 FR-019
        mvc.perform(conCabeceras(delete("/api/v1/habit-preferences/" + habito + "/weekdays/TUESDAY"))).andExpect(status().isNoContent());
        mvc.perform(conCabeceras(delete("/api/v1/habit-preferences/" + habito + "/weekdays/TUESDAY/active"))).andExpect(status().isNoContent());
        mvc.perform(conCabeceras(put("/api/v1/habit-preferences/" + habito + "/weekdays/TUESDAY/reminder"))
                .content("{\"reminderEnabled\":true,\"reminderMinutesList\":[15]}")).andExpect(status().isNoContent());
        mvc.perform(conCabeceras(put("/api/v1/habit-preferences/" + habito + "/alarm"))
                .content("{\"sound\":true,\"vibration\":true,\"voice\":false,\"repeat\":true}")).andExpect(status().isNoContent());

        verify(sinRed).quitarDiaDeLaSemana(eq(actor), eq(HabitoId.of(habito)), eq(DayOfWeek.TUESDAY), any());
        verify(sinRed).apagarDiaDeLaSemana(eq(actor), eq(HabitoId.of(habito)), eq(DayOfWeek.TUESDAY), any());
        verify(sinRed).fijarAvisoDelDia(eq(actor), eq(HabitoId.of(habito)), eq(DayOfWeek.TUESDAY), eq(true), eq(List.of(15)), any());
        verify(sinRed).fijarAlarma(eq(actor), eq(HabitoId.of(habito)), any(), any());
        verifyNoInteractions(horarioSemanal, editar);
    }

    @Test
    @DisplayName("lo que no se aplicó es un 409 con codigo y message listos para mostrar; una cuenta suspendida, 403")
    void noAplicadoYSuspendida() throws Exception { // spec 048 FR-019
        doThrow(new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR)).when(sinRed)
                .fijarDiaDeLaSemana(any(), any(), any(), any(), any(), any());
        mvc.perform(conCabeceras(put("/api/v1/habit-preferences/" + habito + "/weekdays/MONDAY")).content("{\"triggerTime\":\"06:30:00\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR"))
                .andExpect(jsonPath("$.message").value("Esto cambió en tu cuenta después de que lo hiciste sin conexión. Se quedó lo más nuevo."));

        doThrow(new NotAuthorizedException("Cuenta suspendida", NotAuthorizedException.CUENTA_SUSPENDIDA)).when(sinRed)
                .fijarDiaDeLaSemana(any(), any(), any(), any(), any(), any());
        mvc.perform(conCabeceras(put("/api/v1/habit-preferences/" + habito + "/weekdays/MONDAY")).content("{\"triggerTime\":\"06:30:00\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CUENTA_SUSPENDIDA"));
    }

    @Test
    @DisplayName("«solo esta semana», repetir y deshacer la semana van por lo hecho sin red con las cabeceras")
    void semanas() throws Exception { // spec 048 FR-019
        when(semanaSinRed.repetir(any(), any(), any(), eq(true), any())).thenReturn(
                new ResultadoDeRepeticion(SemanaCalendaria.desde(LocalDate.of(2026, 11, 16)), 3, 0, 1, List.of()));
        when(semanaSinRed.deshacerSemana(any(), any(), any())).thenReturn(3);

        mvc.perform(conCabeceras(post("/api/v1/habit-preferences/week-repeat"))
                .content("{\"sourceWeekStart\":\"2026-11-09\",\"targetWeekStart\":\"2026-11-16\",\"replace\":true}")).andExpect(status().isOk());
        mvc.perform(conCabeceras(delete("/api/v1/habit-preferences/weeks/2026-11-16"))).andExpect(status().isOk());
        mvc.perform(conCabeceras(delete("/api/v1/habit-preferences/" + habito + "/week").param("weekStart", "2026-11-16"))).andExpect(status().isNoContent());

        verify(semanaSinRed).repetir(eq(actor), any(), any(), eq(true), any());
        verify(semanaSinRed).deshacerSemana(eq(actor), any(), any());
        verify(semanaSinRed).volverAlPlanDeSiempre(eq(actor), eq(HabitoId.of(habito)), any(), any());
        verifyNoInteractions(repetir, planDeLaSemana);
    }

    @Test
    @DisplayName("quitar un hábito del plan va por lo hecho sin red con las cabeceras y por lo de siempre sin ellas")
    void quitarDelPlan() throws Exception { // spec 048 FR-019
        mvc.perform(conCabeceras(delete("/api/v1/habit-unlocks/" + habito))).andExpect(status().isNoContent());
        verify(desbloqueosSinRed).quitar(any(), any());
        verifyNoInteractions(quitar);

        mvc.perform(sinCabeceras(delete("/api/v1/habit-unlocks/" + habito))).andExpect(status().isNoContent());
        verify(quitar).quitar(any());
    }
}
