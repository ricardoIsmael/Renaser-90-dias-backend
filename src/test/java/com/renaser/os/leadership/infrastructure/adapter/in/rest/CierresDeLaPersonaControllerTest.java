package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarCierresDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarCierresDePersonaUseCase.ConsultaCierresDePersona;
import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
import com.renaser.os.rocks.api.CierresDelDiaFinder.DiaDelCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.ResumenDeCierres;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/leadership/groups/{g}/people/{p}/cierres} (spec 017): el contrato, que nada se cachee y cómo se traduce cada
 * error. Quién puede leer (rol, cuenta, grupo, persona) y la constancia son del servicio REAL y se prueban en
 * {@code CierresDePersonaServiceTest}; acá el caso de uso es un doble.
 */
@WebMvcTest(CierresDeLaPersonaController.class)
@AutoConfigureMockMvc(addFilters = false)
class CierresDeLaPersonaControllerTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);

    private final UserId lider = UserId.of(UUID.randomUUID());
    private final UUID fenix = UUID.randomUUID();
    private final UUID ana = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ConsultarCierresDePersonaUseCase cierres;

    @BeforeEach
    void preparar() {
        reset(cierres);
        when(cierres.cierresDe(any())).thenReturn(new CierresDeLaSemana(LUNES, LocalDate.of(2026, 10, 12),
                List.of(new DiaDelCierre(LUNES, "SIN_CERRAR", null, null, List.of())), new ResumenDeCierres(0, 1, List.of())));
    }

    private ResultActions pedir(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/leadership/groups/" + fenix + "/people/" + ana + "/cierres" + query)
                .header("X-Actor-Id", lider.toString()));
    }

    @Test
    @DisplayName("devuelve la semana con el JSON del contrato y sin cache")
    void contrato() throws Exception {
        pedir("")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.semana").value("2026-10-26"))
                .andExpect(jsonPath("$.primeraSemana").value("2026-10-12"))
                .andExpect(jsonPath("$.dias", hasSize(1)))
                .andExpect(jsonPath("$.dias[0].estado").value("SIN_CERRAR"))
                .andExpect(jsonPath("$.resumen.sinCerrar").value(1));
    }

    @Test
    @DisplayName("el caso de uso recibe quien pregunta, el grupo, la persona y la semana (o null)")
    void laConsultaLlegaCompleta() throws Exception {
        pedir("?semana=2026-10-19").andExpect(status().isOk());
        pedir("").andExpect(status().isOk());

        ArgumentCaptor<ConsultaCierresDePersona> consulta = ArgumentCaptor.forClass(ConsultaCierresDePersona.class);
        verify(cierres, org.mockito.Mockito.times(2)).cierresDe(consulta.capture());
        assertThat(consulta.getAllValues().get(0)).isEqualTo(new ConsultaCierresDePersona(lider, fenix, ana, LocalDate.of(2026, 10, 19)));
        assertThat(consulta.getAllValues().get(1)).isEqualTo(new ConsultaCierresDePersona(lider, fenix, ana, null));
    }

    @Test
    @DisplayName("una semana que no se puede pedir es 400; una fecha que no es fecha tambien")
    void semanaInvalida() throws Exception {
        doThrow(new IllegalArgumentException("INVALID_DATE: esa semana todavía no llega")).when(cierres).cierresDe(any());

        pedir("?semana=2026-11-02").andExpect(status().isBadRequest());
        pedir("?semana=no-es-fecha").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("autorizacion negativa: quien no es del liderazgo, o una cuenta suspendida, recibe 403; una persona que no es del grupo, 404")
    void errores() throws Exception {
        doThrow(new NotAuthorizedException("No tienes acceso")).when(cierres).cierresDe(any());
        pedir("").andExpect(status().isForbidden());

        doThrow(new NoSuchElementException("Esa persona no esta en ese grupo")).when(cierres).cierresDe(any());
        pedir("").andExpect(status().isNotFound());
    }
}
