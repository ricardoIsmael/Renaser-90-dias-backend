package com.renaser.os.rag.infrastructure.adapter.in.rest.analisis;

import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisisDeAlumnoUseCase;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisisDeAlumnoUseCase.ConsultaDeAnalisisDeAlumno;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.ReglaDeLasVerdes;
import com.renaser.os.rocks.api.Pareto8020Finder.ResumenDelCierre;
import com.renaser.os.shared.domain.FuncionalidadNoDisponibleException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.ObjetoDeOtraFormacionException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
 * {@code GET /api/v1/mentor/groups/{g}/learners/{u}/analisis-80-20} (spec 031, 031-2 A): el contrato y cómo se traduce cada error. Quién
 * puede leer, la formación y el interruptor de la spec 034 son del servicio REAL ({@code AnalisisDelAlumnoServiceTest}).
 */
@WebMvcTest(AnalisisDelAlumnoController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalisisDelAlumnoControllerTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 19);

    private final UserId mentora = UserId.of(UUID.randomUUID());
    private final UUID fenix = UUID.randomUUID();
    private final UUID ana = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ConsultarAnalisisDeAlumnoUseCase consultar;

    @BeforeEach
    void preparar() {
        reset(consultar);
        CifrasDel8020 cifras = new CifrasDel8020(INICIO, INICIO.plusDays(6), 12, 9, 4, 0, 66, 47, 75, new ReglaDeLasVerdes(5, 3, 26, 55), null,
                List.of(), List.of(), List.of(), false, new ResumenDelCierre(0, 0, List.of(), List.of()));
        when(consultar.deAlumno(any())).thenReturn(new VistaDelAnalisis(Tipo.SEMANA, 10, INICIO, INICIO.plusDays(6), true, null, null, cifras,
                List.of(), "PENDIENTE", List.of(), 5, null, null, null));
    }

    private ResultActions pedir(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/mentor/groups/" + fenix + "/learners/" + ana + "/analisis-80-20" + query)
                .header("X-Actor-Id", mentora.toString()));
    }

    @Test
    @DisplayName("devuelve el periodo con el JSON del contrato, sin caché, y el caso de uso recibe quién, el grupo, el alumno y el periodo")
    void contrato() throws Exception {
        pedir("?periodo=MES&inicio=2026-10-12").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.cifras.planificadas").value(12));

        ArgumentCaptor<ConsultaDeAnalisisDeAlumno> consulta = ArgumentCaptor.forClass(ConsultaDeAnalisisDeAlumno.class);
        verify(consultar).deAlumno(consulta.capture());
        assertThat(consulta.getValue()).isEqualTo(new ConsultaDeAnalisisDeAlumno(mentora, fenix, UserId.of(ana), TipoDePeriodo.MES,
                LocalDate.of(2026, 10, 12)));
    }

    @Test
    @DisplayName("403 si no acompaña ese grupo o la cuenta está suspendida; 404 si es de otra formación o el interruptor de la ficha está apagado")
    void errores() throws Exception {
        doThrow(new NotAuthorizedException("No acompañas ese grupo")).when(consultar).deAlumno(any());
        pedir("").andExpect(status().isForbidden());

        doThrow(new ObjetoDeOtraFormacionException()).when(consultar).deAlumno(any());
        pedir("").andExpect(status().isNotFound());

        doThrow(new FuncionalidadNoDisponibleException("radar.fichas")).when(consultar).deAlumno(any());
        pedir("").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un periodo que no es SEMANA ni MES es 400")
    void periodoInvalido() throws Exception {
        pedir("?periodo=ANO").andExpect(status().isBadRequest());
    }
}
