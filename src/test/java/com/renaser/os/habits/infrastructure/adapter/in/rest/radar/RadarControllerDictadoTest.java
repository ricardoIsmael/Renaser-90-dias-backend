package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;
import com.renaser.os.habits.application.ports.in.radar.ConsultarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarUltimoRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase.RegistrarCheckInRadarCommand;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadarId;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El Código Renaser con la voz por HTTP (spec 030, D-297; contrato en {@code specs/030-codigo-renaser-con-audio/contracts/api.md}):
 * {@code dictado} es un campo OPCIONAL del pedido (ausente o {@code null} = {@code false}), viaja al comando y vuelve en el
 * registro y en el historial siempre como booleano. Un cliente que manda un campo que el servidor no conoce no recibe un 400:
 * es lo que permite que una app nueva hable con un backend anterior.
 */
@WebMvcTest(RadarController.class)
@AutoConfigureMockMvc(addFilters = false)
class RadarControllerDictadoTest {

    private static final String BASE = """
            "whatAmIDoing":"h","whatAmIThinking":"p","whatAmIFeeling":"s","energyLevel":6,"whatAmIAvoiding":"e\"""";

    @Autowired MockMvc mvc;
    @MockitoBean RegistrarCheckInRadarUseCase registrar;
    @MockitoBean ConsultarUltimoRadarUseCase ultimo;
    @MockitoBean ConsultarHistorialRadarUseCase historial;
    @MockitoBean ConsultarMiRadarUseCase consultarMiRadar;
    @MockitoBean ConfigurarMiRadarUseCase configurarMiRadar;
    @MockitoBean UserSummaryFinder users;
    private final UserId actor = UserId.of(UUID.randomUUID());

    @BeforeEach
    void actorActivo() {
        when(users.findById(actor)).thenReturn(Optional.of(
                new UserSummary(actor, "Fixture", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(registrar.registrar(any())).thenAnswer(inv -> {
            RegistrarCheckInRadarCommand c = inv.getArgument(0);
            RegistroRadar registro = RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), actor, c.queHago(),
                    c.quePienso(), c.queSiento(), c.nivelEnergia(), c.queEvito(), Instant.parse("2026-10-31T19:06:00Z"));
            return c.dictado() ? registro.comoDictado() : registro;
        });
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private ResultActions registrar(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/radar").header("X-Actor-Id", actor.toString())
                .contentType("application/json").content("{" + cuerpo + "}"));
    }

    private RegistrarCheckInRadarCommand comandoEnviado() {
        ArgumentCaptor<RegistrarCheckInRadarCommand> captor = ArgumentCaptor.forClass(RegistrarCheckInRadarCommand.class);
        verify(registrar).registrar(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("dictado:true llega al comando y el registro responde dictado:true")
    void dictadoVerdadero() throws Exception {
        registrar(BASE + ",\"dictado\":true").andExpect(status().isOk())
                .andExpect(jsonPath("$.dictado").value(true));

        assertThat(comandoEnviado().dictado()).isTrue();
    }

    @Test
    @DisplayName("sin el campo (un APK anterior) vale false, y la respuesta lo dice como booleano, no como ausente")
    void sinElCampo() throws Exception {
        registrar(BASE).andExpect(status().isOk()).andExpect(jsonPath("$.dictado").value(false));

        assertThat(comandoEnviado().dictado()).isFalse();
    }

    @Test
    @DisplayName("dictado:null y dictado:false valen false")
    void nuloYFalso() throws Exception {
        registrar(BASE + ",\"dictado\":null").andExpect(status().isOk()).andExpect(jsonPath("$.dictado").value(false));
        registrar(BASE + ",\"dictado\":false").andExpect(status().isOk()).andExpect(jsonPath("$.dictado").value(false));
    }

    @Test
    @DisplayName("un campo que el servidor no conoce no es un 400: una app nueva puede hablar con un backend anterior")
    void campoDesconocidoNoEsUn400() throws Exception {
        registrar(BASE + ",\"campoDelFuturo\":1").andExpect(status().isOk());
    }

    @Test
    @DisplayName("las validaciones de siempre no cambian: un texto vacío sigue siendo 400 aunque venga dictado")
    void laValidacionNoCambia() throws Exception {
        registrar("\"whatAmIDoing\":\"\",\"whatAmIThinking\":\"p\",\"whatAmIFeeling\":\"s\",\"energyLevel\":6,"
                + "\"whatAmIAvoiding\":\"e\",\"dictado\":true").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el historial dice dictado en cada registro, true solo en los dictados")
    void historialConDictado() throws Exception {
        RegistroRadar escrito = RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), actor, "h", "p", "s", 6,
                "e", Instant.parse("2026-10-31T18:06:00Z"));
        RegistroRadar dictado = RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), actor, "h", "p", "s", 6,
                "e", Instant.parse("2026-10-31T19:06:00Z")).comoDictado();
        when(historial.historial(any(), any(), any(), anyInt()))
                .thenReturn(new HistorialRadarPage(List.of(dictado, escrito), null, null));

        mvc.perform(get("/api/v1/radar/history").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[0].dictado").value(true))
                .andExpect(jsonPath("$.entries[1].dictado").value(false));
    }
}
