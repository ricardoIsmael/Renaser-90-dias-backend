package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarSinRedUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarUltimoRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarSinRedUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadarId;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/radar} y {@code PUT /api/v1/radar/mi-radar} con las tres cabeceras de lo hecho sin red (spec 048, fase 3,
 * FR-019): con {@code X-Id-Cliente} y {@code X-Hecho-En} van por el caso de uso de lo hecho sin red; sin ellas, exactamente por
 * lo de siempre; lo que no se aplica es un 409 con {@code codigo}.
 */
@WebMvcTest(RadarController.class)
@AutoConfigureMockMvc(addFilters = false)
class RadarControllerSinRedTest {

    private static final String ID = "5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10";
    private static final String CUERPO = """
            {"whatAmIDoing":"h","whatAmIThinking":"p","whatAmIFeeling":"s","energyLevel":6,"whatAmIAvoiding":"e"}""";

    @Autowired MockMvc mvc;
    @MockitoBean RegistrarCheckInRadarUseCase registrar;
    @MockitoBean ConsultarUltimoRadarUseCase ultimo;
    @MockitoBean ConsultarHistorialRadarUseCase historial;
    @MockitoBean ConsultarMiRadarUseCase consultarMiRadar;
    @MockitoBean ConfigurarMiRadarUseCase configurarMiRadar;
    @MockitoBean RegistrarCheckInRadarSinRedUseCase registrarSinRed;
    @MockitoBean ConfigurarMiRadarSinRedUseCase configurarMiRadarSinRed;
    @MockitoBean UserSummaryFinder users;
    private final UserId actor = UserId.of(UUID.randomUUID());

    @BeforeEach
    void actorActivo() {
        when(users.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Fixture", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        RegistroRadar registro = RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), actor, "h", "p", "s", 6, "e",
                Instant.parse("2026-11-10T02:40:13.250Z"));
        when(registrar.registrar(any())).thenReturn(registro);
        when(registrarSinRed.registrar(any(), any())).thenReturn(registro);
        PlanDeMiRadar plan = new PlanDeMiRadar(true, LocalDate.of(2026, 11, 10), List.of());
        when(configurarMiRadar.configurar(any())).thenReturn(plan);
        when(configurarMiRadarSinRed.configurar(any(), any())).thenReturn(plan);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private ResultActions registrarSinRed(String id, String hechoEn) throws Exception {
        return mvc.perform(post("/api/v1/radar").header("X-Actor-Id", actor.toString()).header("X-Id-Cliente", id)
                .header("X-Hecho-En", hechoEn).header("X-Origen-De-La-Hora", "RELOJ_DEL_SISTEMA")
                .contentType("application/json").content(CUERPO));
    }

    @Test
    @DisplayName("POST /radar con las cabeceras va por lo hecho sin red, con su hora real, y responde createdAt = la hora real guardada")
    void registrarConCabeceras() throws Exception { // spec 048 FR-019
        registrarSinRed(ID, "2026-11-10T02:40:13.250Z").andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value("2026-11-10T02:40:13.250Z"));

        ArgumentCaptor<HechoSinRed> hecho = ArgumentCaptor.forClass(HechoSinRed.class);
        verify(registrarSinRed).registrar(any(), hecho.capture());
        assertThat(hecho.getValue().idCliente()).isEqualTo(UUID.fromString(ID));
        assertThat(hecho.getValue().hechoEn()).isEqualTo(Instant.parse("2026-11-10T02:40:13.250Z"));
        assertThat(hecho.getValue().origenDeLaHora()).isEqualTo(OrigenDeLaHora.RELOJ_DEL_SISTEMA);
        verifyNoInteractions(registrar);
    }

    @Test
    @DisplayName("POST /radar sin las cabeceras responde exactamente lo de hoy")
    void registrarSinCabeceras() throws Exception { // spec 048 FR-019
        mvc.perform(post("/api/v1/radar").header("X-Actor-Id", actor.toString()).contentType("application/json").content(CUERPO))
                .andExpect(status().isOk());

        verify(registrar).registrar(any());
        verifyNoInteractions(registrarSinRed);
    }

    @Test
    @DisplayName("POST /radar: la hora ocupada o sin Mi Radar es un 409 con su codigo y mensaje (YA_ESTABA_HECHO, MI_RADAR_NO_APLICA)")
    void noAplicadoEsUn409ConCodigo() throws Exception { // spec 048 FR-019
        doThrow(new NoAplicadoSinRedException(CodigoNoAplicado.YA_ESTABA_HECHO)).when(registrarSinRed).registrar(any(), any());
        registrarSinRed(ID, "2026-11-10T02:40:13Z").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("YA_ESTABA_HECHO"))
                .andExpect(jsonPath("$.message").value("Esto ya estaba hecho en tu cuenta. Se quedó lo que ya había."));

        doThrow(new NoAplicadoSinRedException(CodigoNoAplicado.MI_RADAR_NO_APLICA)).when(registrarSinRed).registrar(any(), any());
        registrarSinRed(UUID.randomUUID().toString(), "2026-11-10T02:40:13Z").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MI_RADAR_NO_APLICA"))
                .andExpect(jsonPath("$.message").value("Mi Radar no tenía esa hora para ti, así que no se guardó ese Código Renaser."));
    }

    @Test
    @DisplayName("POST /radar con una cuenta suspendida es 403 también con las cabeceras")
    void suspendidaEs403() throws Exception { // spec 048 FR-019
        doThrow(new NotAuthorizedException("Cuenta suspendida", NotAuthorizedException.CUENTA_SUSPENDIDA)).when(registrarSinRed)
                .registrar(any(), any());

        registrarSinRed(ID, "2026-11-10T02:40:13Z").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_SUSPENDIDA"));
    }

    @Test
    @DisplayName("PUT /radar/mi-radar con las cabeceras va por lo hecho sin red; sin ellas, por lo de siempre; un choque es un 409 con codigo")
    void miRadarConYSinCabeceras() throws Exception { // spec 048 FR-019
        String cuerpo = "{\"encendido\":true,\"dias\":[]}";
        mvc.perform(put("/api/v1/radar/mi-radar").header("X-Actor-Id", actor.toString()).header("X-Id-Cliente", ID)
                .header("X-Hecho-En", "2026-11-10T02:40:13Z").contentType("application/json").content(cuerpo)).andExpect(status().isOk());
        verify(configurarMiRadarSinRed).configurar(any(), any());
        verifyNoInteractions(configurarMiRadar);

        mvc.perform(put("/api/v1/radar/mi-radar").header("X-Actor-Id", actor.toString()).contentType("application/json").content(cuerpo))
                .andExpect(status().isOk());
        verify(configurarMiRadar).configurar(any());

        doThrow(new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR)).when(configurarMiRadarSinRed)
                .configurar(any(), any());
        mvc.perform(put("/api/v1/radar/mi-radar").header("X-Actor-Id", actor.toString()).header("X-Id-Cliente", UUID.randomUUID().toString())
                .header("X-Hecho-En", "2026-11-10T02:40:13Z").contentType("application/json").content(cuerpo))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR"));
    }

    @Test
    @DisplayName("cabeceras mal hechas son un 400 de siempre y no llegan a ningún caso de uso")
    void cabecerasMalHechas() throws Exception { // spec 048 FR-019
        registrarSinRed("no-es-uuid", "2026-11-10T02:40:13Z").andExpect(status().isBadRequest());
        registrarSinRed(ID, "ayer").andExpect(status().isBadRequest());

        verifyNoInteractions(registrar, registrarSinRed);
    }
}
