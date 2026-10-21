package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.CambiarEstadoHabitoEnFechaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.shared.web.SecurityConfig;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La ruta nueva del aviso por dia queda detras de la cadena de seguridad real: con solo el UUID de otra
 * persona en {@code X-Actor-Id} y sin sesion, se rechaza antes de llegar al caso de uso.
 */
@WebMvcTest(HabitPreferenceController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class HabitPreferenceControllerRecordatorioAutenticacionTest {

    @Autowired MockMvc mvc;
    @MockitoBean EditarPreferenciaHorarioUseCase editar;
    @MockitoBean ConsultarPreferenciasHorarioUseCase consultar;
    @MockitoBean CambiarEstadoHabitoEnFechaUseCase cambiarEstadoEnFecha;
    @MockitoBean EditarHorarioSemanalUseCase horarioSemanal;
    @MockitoBean UserSummaryFinder users;

    @Test
    void guardarElAvisoDeUnDiaSinSesionEsRechazadoAunqueVengaElHeaderDeActor() throws Exception {
        mvc.perform(put("/api/v1/habit-preferences/" + UUID.randomUUID() + "/weekdays/SATURDAY/reminder")
                .header("X-Actor-Id", UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"reminderEnabled\":true,\"reminderMinutesList\":[30]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(horarioSemanal);
    }

    /** Spec 009: las dos rutas de la alarma quedan detras de la misma cadena de seguridad. */
    @Test
    void guardarLaAlarmaSinSesionEsRechazadoAunqueVengaElHeaderDeActor() throws Exception {
        String cuerpo = "{\"sound\":true,\"vibration\":true,\"voice\":false,\"repeat\":true}";
        var habito = UUID.randomUUID();

        mvc.perform(put("/api/v1/habit-preferences/" + habito + "/alarm")
                .header("X-Actor-Id", UUID.randomUUID().toString()).contentType("application/json").content(cuerpo))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/habit-preferences/" + habito + "/weekdays/SATURDAY/alarm")
                .header("X-Actor-Id", UUID.randomUUID().toString()).contentType("application/json").content(cuerpo))
                .andExpect(status().isForbidden());

        verifyNoInteractions(editar, horarioSemanal);
    }
}
