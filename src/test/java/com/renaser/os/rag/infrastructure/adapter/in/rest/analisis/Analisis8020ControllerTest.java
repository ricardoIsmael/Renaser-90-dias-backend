package com.renaser.os.rag.infrastructure.adapter.in.rest.analisis;

import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.api.Analisis8020Finder.TextoVisible;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisis8020UseCase;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisis8020UseCase.ConsultaDeAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.ReglaDeLasVerdes;
import com.renaser.os.rocks.api.Pareto8020Finder.ResumenDelCierre;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
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

/** {@code GET /api/v1/analisis-80-20} (spec 031): el contrato, que nada se cachee y cómo se traduce cada error. Sobre sí misma, siempre. */
@WebMvcTest(Analisis8020Controller.class)
@AutoConfigureMockMvc(addFilters = false)
class Analisis8020ControllerTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 19);

    private final UserId ana = UserId.of(UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ConsultarAnalisis8020UseCase consultar;

    private static VistaDelAnalisis listo() {
        CifrasDel8020 cifras = new CifrasDel8020(INICIO, INICIO.plusDays(6), 12, 9, 4, 0, 66, 47, 75, new ReglaDeLasVerdes(5, 3, 26, 55), null,
                List.of(), List.of(), List.of(), false, new ResumenDelCierre(0, 0, List.of(), List.of()));
        return new VistaDelAnalisis(Tipo.SEMANA, 10, INICIO, INICIO.plusDays(6), true, INICIO.minusDays(7), null, cifras, List.of(), "LISTO",
                List.of(), 5, null, new TextoVisible("Entrenar movió más.", "La llamada quedó pendiente.", "Bajas por las tardes.",
                List.of("Haz la llamada primero.")), Instant.parse("2026-10-26T06:00:00Z"));
    }

    @BeforeEach
    void preparar() {
        reset(consultar);
        when(consultar.deLaPersona(any())).thenReturn(listo());
    }

    private ResultActions pedir(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/analisis-80-20" + query).header("X-Actor-Id", ana.toString()));
    }

    @Test
    @DisplayName("devuelve las cifras y las cuatro partes en su orden, y sin caché")
    void contrato() throws Exception {
        pedir("").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.cifras.cumplidas").value(9))
                .andExpect(jsonPath("$.analisis.loQueMasMovio").value("Entrenar movió más."))
                .andExpect(jsonPath("$.analisis.loQueSeTeFue").value("La llamada quedó pendiente."))
                .andExpect(jsonPath("$.analisis.comoEstuviste").value("Bajas por las tardes."))
                .andExpect(jsonPath("$.analisis.proximosPasos[0]").value("Haz la llamada primero."))
                .andExpect(jsonPath("$.siguiente").doesNotExist());
    }

    @Test
    @DisplayName("el caso de uso recibe a quien pregunta (nunca otro id), el periodo y el inicio; SEMANA por defecto")
    void laConsultaEsSobreSiMisma() throws Exception {
        pedir("?periodo=MES&inicio=2026-10-12").andExpect(status().isOk());
        pedir("").andExpect(status().isOk());

        ArgumentCaptor<ConsultaDeAnalisis> consulta = ArgumentCaptor.forClass(ConsultaDeAnalisis.class);
        verify(consultar, org.mockito.Mockito.times(2)).deLaPersona(consulta.capture());
        assertThat(consulta.getAllValues().get(0)).isEqualTo(new ConsultaDeAnalisis(ana, TipoDePeriodo.MES, LocalDate.of(2026, 10, 12)));
        assertThat(consulta.getAllValues().get(1)).isEqualTo(new ConsultaDeAnalisis(ana, TipoDePeriodo.SEMANA, null));
    }

    @Test
    @DisplayName("no hay ruta ni parámetro para leer a otra persona: un participanteId en la query se ignora")
    void sinParametroDePersona() throws Exception {
        pedir("?participanteId=" + UUID.randomUUID()).andExpect(status().isOk());

        ArgumentCaptor<ConsultaDeAnalisis> consulta = ArgumentCaptor.forClass(ConsultaDeAnalisis.class);
        verify(consultar).deLaPersona(consulta.capture());
        assertThat(consulta.getValue().actorId()).isEqualTo(ana);
    }

    @Test
    @DisplayName("un periodo que no es SEMANA ni MES o un periodo que no llega, es 400; sin el programa activado, 404")
    void errores() throws Exception {
        pedir("?periodo=DIA").andExpect(status().isBadRequest());
        doThrow(new IllegalArgumentException("Ese periodo todavía no llega")).when(consultar).deLaPersona(any());
        pedir("?inicio=2027-01-04").andExpect(status().isBadRequest());
        doThrow(new NoSuchElementException("Todavía no activó su programa")).when(consultar).deLaPersona(any());
        pedir("").andExpect(status().isNotFound());
    }
}
