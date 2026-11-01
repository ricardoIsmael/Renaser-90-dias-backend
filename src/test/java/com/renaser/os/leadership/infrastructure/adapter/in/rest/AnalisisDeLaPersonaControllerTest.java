package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase.ConsultaAnalisisDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase.ConsultaAnalisisDeUnaPersona;
import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.ReglaDeLasVerdes;
import com.renaser.os.rocks.api.Pareto8020Finder.ResumenDelCierre;
import com.renaser.os.shared.domain.FuncionalidadNoDisponibleException;
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
 * {@code GET /api/v1/leadership/groups/{g}/people/{p}/analisis-80-20} y {@code /people/{p}/analisis-80-20} (spec 031): el contrato, que nada se
 * cachee y cómo se traduce cada error. Quién puede leer, el interruptor de la spec 034 y la constancia son del servicio REAL
 * ({@code AnalisisDePersonaServiceTest}); acá el caso de uso es un doble.
 */
@WebMvcTest(AnalisisDeLaPersonaController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalisisDeLaPersonaControllerTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 19);

    private final UserId lider = UserId.of(UUID.randomUUID());
    private final UUID fenix = UUID.randomUUID();
    private final UUID ana = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ConsultarAnalisisDePersonaUseCase analisis;

    private static VistaDelAnalisis vista() {
        CifrasDel8020 cifras = new CifrasDel8020(INICIO, INICIO.plusDays(6), 12, 9, 4, 0, 66, 47, 75, new ReglaDeLasVerdes(5, 3, 26, 55), null,
                List.of(), List.of(), List.of(), false, new ResumenDelCierre(0, 0, List.of(), List.of()));
        return new VistaDelAnalisis(Tipo.SEMANA, 10, INICIO, INICIO.plusDays(6), true, INICIO.minusDays(7), null, cifras, List.of(), "PENDIENTE",
                List.of(), 5, null, null, null);
    }

    @BeforeEach
    void preparar() {
        reset(analisis);
        when(analisis.analisisDe(any())).thenReturn(vista());
        when(analisis.analisisDeUnaPersona(any())).thenReturn(vista());
    }

    private ResultActions pedirPorGrupo(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/leadership/groups/" + fenix + "/people/" + ana + "/analisis-80-20" + query)
                .header("X-Actor-Id", lider.toString()));
    }

    private ResultActions pedirSinGrupo(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/leadership/people/" + ana + "/analisis-80-20" + query).header("X-Actor-Id", lider.toString()));
    }

    @Test
    @DisplayName("devuelve el periodo con el JSON del contrato y sin caché, por las dos rutas")
    void contrato() throws Exception {
        for (ResultActions respuesta : List.of(pedirPorGrupo(""), pedirSinGrupo(""))) {
            respuesta.andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(jsonPath("$.tipo").value("SEMANA"))
                    .andExpect(jsonPath("$.numero").value(10))
                    .andExpect(jsonPath("$.inicio").value("2026-10-19"))
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.anterior").value("2026-10-12"))
                    .andExpect(jsonPath("$.cifras.planificadas").value(12))
                    .andExpect(jsonPath("$.cifras.verdes.porcentajeDelImpacto").value(55));
        }
    }

    @Test
    @DisplayName("el caso de uso recibe quien pregunta, el grupo, la persona, el periodo (SEMANA por defecto; MES en minúsculas también) y el inicio")
    void laConsultaLlegaCompleta() throws Exception {
        pedirPorGrupo("?periodo=mes&inicio=2026-10-12").andExpect(status().isOk());
        pedirPorGrupo("").andExpect(status().isOk());
        pedirSinGrupo("?periodo=SEMANA").andExpect(status().isOk());

        ArgumentCaptor<ConsultaAnalisisDePersona> porGrupo = ArgumentCaptor.forClass(ConsultaAnalisisDePersona.class);
        verify(analisis, org.mockito.Mockito.times(2)).analisisDe(porGrupo.capture());
        assertThat(porGrupo.getAllValues().get(0)).isEqualTo(new ConsultaAnalisisDePersona(lider, fenix, ana, Tipo.MES, LocalDate.of(2026, 10, 12)));
        assertThat(porGrupo.getAllValues().get(1)).isEqualTo(new ConsultaAnalisisDePersona(lider, fenix, ana, Tipo.SEMANA, null));
        ArgumentCaptor<ConsultaAnalisisDeUnaPersona> sinGrupo = ArgumentCaptor.forClass(ConsultaAnalisisDeUnaPersona.class);
        verify(analisis).analisisDeUnaPersona(sinGrupo.capture());
        assertThat(sinGrupo.getValue()).isEqualTo(new ConsultaAnalisisDeUnaPersona(lider, ana, Tipo.SEMANA, null));
    }

    @Test
    @DisplayName("un periodo que no es SEMANA ni MES, una fecha que no es fecha o un periodo que no existe, es 400")
    void pedidoInvalido() throws Exception {
        pedirPorGrupo("?periodo=DIA").andExpect(status().isBadRequest());
        pedirPorGrupo("?inicio=no-es-fecha").andExpect(status().isBadRequest());
        doThrow(new IllegalArgumentException("Ese periodo todavía no llega")).when(analisis).analisisDe(any());
        pedirPorGrupo("?inicio=2027-01-04").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("autorización negativa: quien no es del liderazgo, o una cuenta suspendida, recibe 403; una persona que no es del grupo o sin Día 1, 404")
    void errores() throws Exception {
        doThrow(new NotAuthorizedException("No tienes acceso")).when(analisis).analisisDe(any());
        pedirPorGrupo("").andExpect(status().isForbidden());

        doThrow(new NoSuchElementException("Esa persona no esta en ese grupo")).when(analisis).analisisDe(any());
        pedirPorGrupo("").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("034: con «Código Renaser en las fichas» apagado el líder recibe la misma respuesta de «esto no está»: no revela que existe")
    void interruptorApagado() throws Exception {
        doThrow(new FuncionalidadNoDisponibleException("radar.fichas")).when(analisis).analisisDe(any());

        pedirPorGrupo("").andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("FUNCIONALIDAD_NO_DISPONIBLE"));
    }
}
