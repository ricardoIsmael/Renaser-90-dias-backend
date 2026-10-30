package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.CambioDeDia;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.ConfigurarMiRadarCommand;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;
import com.renaser.os.habits.application.ports.in.radar.ConsultarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarUltimoRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase;
import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar.Modo;
import com.renaser.os.habits.domain.model.radar.DiaDelRadar;
import com.renaser.os.habits.domain.model.radar.DiaDelRadar.MotivoSinHorario;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadarId;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mi Radar por HTTP (spec 012, D-286; contrato en {@code specs/012-mi-radar-en-yo/contracts/api.md}): el contrato de
 * {@code GET}/{@code PUT /api/v1/radar/mi-radar}, la hora prevista en el registro y en el historial, y la regla de que
 * registrar con Mi Radar apagado es un 409 y NO un 403 (en la app un 403 apaga el Radar entero).
 */
@WebMvcTest(RadarController.class)
@AutoConfigureMockMvc(addFilters = false)
class RadarControllerMiRadarTest {

    private static final String CUERPO_DEL_REGISTRO = """
            {"whatAmIDoing":"h","whatAmIThinking":"p","whatAmIFeeling":"s","energyLevel":6,"whatAmIAvoiding":"e"}""";

    @Autowired MockMvc mvc;
    @MockitoBean RegistrarCheckInRadarUseCase registrar;
    @MockitoBean ConsultarUltimoRadarUseCase ultimo;
    @MockitoBean ConsultarHistorialRadarUseCase historial;
    @MockitoBean ConsultarMiRadarUseCase consultarMiRadar;
    @MockitoBean ConfigurarMiRadarUseCase configurarMiRadar;
    @MockitoBean UserSummaryFinder users;
    private final UserId actor = UserId.of(UUID.randomUUID());

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void actor(UserStatus status) {
        when(users.findById(actor)).thenReturn(Optional.of(
                new UserSummary(actor, "Fixture", null, UserRole.TRAINEE, status)));
    }

    private ResultActions leer() throws Exception {
        return mvc.perform(get("/api/v1/radar/mi-radar").header("X-Actor-Id", actor.toString()));
    }

    private ResultActions guardar(String cuerpo) throws Exception {
        return mvc.perform(put("/api/v1/radar/mi-radar").header("X-Actor-Id", actor.toString())
                .contentType("application/json").content(cuerpo));
    }

    private static DiaDelRadar dia(int numero, boolean editable, Modo modo, List<Integer> horas, boolean sinRadar,
                                   LocalTime despertar, LocalTime dormir, MotivoSinHorario motivo) {
        return new DiaDelRadar(numero, LocalDate.of(2026, 10, 25).plusDays(numero), modo, 17, horas.size(), sinRadar,
                editable, despertar, dormir, horas, motivo);
    }

    private static PlanDeMiRadar plan(boolean encendido) {
        List<Integer> horas = IntStream.rangeClosed(6, 22).boxed().toList();
        List<DiaDelRadar> dias = IntStream.rangeClosed(1, 7).mapToObj(numero -> numero == 7
                ? dia(7, true, Modo.AUTOMATICO, List.of(), false, null, LocalTime.of(22, 30), MotivoSinHorario.SIN_DESPERTAR)
                : dia(numero, numero > 5, Modo.AUTOMATICO, horas, false, LocalTime.of(5, 30), LocalTime.of(22, 30), null))
                .toList();
        return new PlanDeMiRadar(encendido, LocalDate.of(2026, 10, 30), dias);
    }

    /** Como llega en el historial y en las fichas: solo el dia de hoy. */
    private static PlanDeMiRadar deHoy(boolean encendido) {
        return new PlanDeMiRadar(encendido, LocalDate.of(2026, 10, 30), List.of(
                dia(5, false, Modo.AUTOMATICO, IntStream.rangeClosed(6, 22).boxed().toList(), false,
                        LocalTime.of(5, 30), LocalTime.of(22, 30), null)));
    }

    // ── GET /mi-radar ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET: devuelve encendido, hoy y los siete dias con modo, veces, maximo, editable, Despertar, Dormir, horas y motivo")
    void leerLaSemana() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultarMiRadar.consultar(actor, actor)).thenReturn(plan(true));

        leer().andExpect(status().isOk())
                .andExpect(jsonPath("$.encendido").value(true))
                .andExpect(jsonPath("$.hoy").value("2026-10-30"))
                .andExpect(jsonPath("$.dias", hasSize(7)))
                .andExpect(jsonPath("$.dias[0].dia").value(1))
                .andExpect(jsonPath("$.dias[0].fecha").value("2026-10-26"))
                .andExpect(jsonPath("$.dias[0].modo").value("AUTOMATICO"))
                .andExpect(jsonPath("$.dias[0].veces").value(17))
                .andExpect(jsonPath("$.dias[0].maximo").value(17))
                .andExpect(jsonPath("$.dias[0].editable").value(false))
                .andExpect(jsonPath("$.dias[0].despertar").value("05:30"))
                .andExpect(jsonPath("$.dias[0].dormir").value("22:30"))
                .andExpect(jsonPath("$.dias[0].horas[0]").value(6))
                .andExpect(jsonPath("$.dias[0].horas[16]").value(22))
                .andExpect(jsonPath("$.dias[0].motivo").doesNotExist())
                .andExpect(jsonPath("$.dias[5].editable").value(true));
    }

    @Test
    @DisplayName("GET: un dia sin Despertar va con despertar null, sin horas y con su motivo")
    void leerUnDiaSinDespertar() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultarMiRadar.consultar(actor, actor)).thenReturn(plan(true));

        leer().andExpect(jsonPath("$.dias[6].despertar").doesNotExist())
                .andExpect(jsonPath("$.dias[6].dormir").value("22:30"))
                .andExpect(jsonPath("$.dias[6].horas", hasSize(0)))
                .andExpect(jsonPath("$.dias[6].veces").value(0))
                .andExpect(jsonPath("$.dias[6].motivo").value("SIN_DESPERTAR"));
    }

    @Test
    @DisplayName("GET: apagado devuelve encendido:false con los dias a la vista")
    void leerApagado() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultarMiRadar.consultar(actor, actor)).thenReturn(plan(false));

        leer().andExpect(status().isOk())
                .andExpect(jsonPath("$.encendido").value(false))
                .andExpect(jsonPath("$.dias", hasSize(7)));
    }

    // ── PUT /mi-radar ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT: manda al caso de uso el actor como actor y como participante (no se puede pedir el de otro) y SOLO los dias que se tocan")
    void guardarLosDiasTocados() throws Exception {
        actor(UserStatus.ACTIVE);
        when(configurarMiRadar.configurar(any())).thenReturn(plan(true));

        guardar("{\"encendido\":true,\"dias\":[{\"dia\":6,\"modo\":\"PERSONALIZADO\",\"veces\":3},"
                + "{\"dia\":7,\"sinRadar\":true}]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.encendido").value(true))
                .andExpect(jsonPath("$.dias", hasSize(7)));

        verify(configurarMiRadar).configurar(new ConfigurarMiRadarCommand(actor, actor, true,
                List.of(new CambioDeDia(6, Modo.PERSONALIZADO, 3, null), new CambioDeDia(7, null, null, true))));
    }

    @Test
    @DisplayName("PUT: sin «dias» es una lista vacia: encender o apagar no toca ningun dia")
    void sinDias() throws Exception {
        actor(UserStatus.ACTIVE);
        when(configurarMiRadar.configurar(any())).thenReturn(plan(false));

        guardar("{\"encendido\":false}").andExpect(status().isOk());

        verify(configurarMiRadar).configurar(new ConfigurarMiRadarCommand(actor, actor, false, List.of()));
    }

    @Test
    @DisplayName("PUT: un modo que no existe, un dia sin numero o un elemento null son 400 y no llegan al caso de uso")
    void cuerpoConDiasInvalidosEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar("{\"encendido\":true,\"dias\":[{\"dia\":6,\"modo\":\"RARO\"}]}").andExpect(status().isBadRequest());
        guardar("{\"encendido\":true,\"dias\":[{\"modo\":\"AUTOMATICO\"}]}").andExpect(status().isBadRequest());
        guardar("{\"encendido\":true,\"dias\":[null]}").andExpect(status().isBadRequest());

        verifyNoInteractions(configurarMiRadar);
    }

    @Test
    @DisplayName("PUT: lo que rechaza el dominio (veces fuera de 1 a 21, dia fuera de 1 a 7, mas de las que caben) es 400 con su mensaje")
    void reglasDelDominioSon400() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new IllegalArgumentException("Las veces por día van de 1 a 21")).when(configurarMiRadar).configurar(any());

        guardar("{\"encendido\":true,\"dias\":[{\"dia\":6,\"modo\":\"PERSONALIZADO\",\"veces\":0}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Las veces por día van de 1 a 21"));
    }

    @Test
    @DisplayName("FR-005: cambiar hoy o un dia pasado es 409 con su motivo y NO 403 (en la app un 403 apaga el Radar entero)")
    void cambiarHoyEs409() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new IllegalStateException("Hoy y los días que pasaron no se pueden cambiar")).when(configurarMiRadar)
                .configurar(any());

        guardar("{\"encendido\":true,\"dias\":[{\"dia\":5,\"modo\":\"PERSONALIZADO\",\"veces\":3}]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hoy y los días que pasaron no se pueden cambiar"));
    }

    @Test
    @DisplayName("PUT: omitir «encendido» es 400 y no toca nada; un cuerpo que no es JSON tambien")
    void cuerpoInvalidoEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar("{\"dias\":[]}").andExpect(status().isBadRequest());
        guardar("{}").andExpect(status().isBadRequest());
        guardar("no es json").andExpect(status().isBadRequest());

        verifyNoInteractions(configurarMiRadar);
    }

    // ── autorizacion negativa ───────────────────────────────────────────────

    @Test
    @DisplayName("una cuenta SUSPENDIDA recibe 403 en GET y en PUT y no llega al caso de uso")
    void cuentaSuspendida() throws Exception {
        actor(UserStatus.SUSPENDED);

        leer().andExpect(status().isForbidden());
        guardar("{\"encendido\":true}").andExpect(status().isForbidden());

        verifyNoInteractions(consultarMiRadar, configurarMiRadar);
    }

    @Test
    @DisplayName("lo que el caso de uso niega (programa sin activar, cuenta suspendida) es 403")
    void elCasoDeUsoNiega() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new NotAuthorizedException("El Codigo Renaser es exclusivo de aprendices")).when(configurarMiRadar)
                .configurar(any());
        doThrow(new NotAuthorizedException("Cuenta suspendida")).when(consultarMiRadar).consultar(any(), any());

        guardar("{\"encendido\":true}").andExpect(status().isForbidden());
        leer().andExpect(status().isForbidden());
    }

    // ── el registro ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("FR-008a: registrar con Mi Radar apagado desde el dia 8 es 409 y NO 403 (un 403 apagaria el Radar en la app)")
    void registrarApagadoEs409() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new IllegalStateException("Mi Radar está apagado")).when(registrar).registrar(any());

        mvc.perform(post("/api/v1/radar").header("X-Actor-Id", actor.toString()).contentType("application/json")
                        .content(CUERPO_DEL_REGISTRO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Mi Radar está apagado"));
    }

    @Test
    @DisplayName("registrar en un dia «Sin Radar» tambien es 409 y NO 403")
    void registrarDiaSinRadarEs409() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new IllegalStateException("Hoy no tienes Mi Radar")).when(registrar).registrar(any());

        mvc.perform(post("/api/v1/radar").header("X-Actor-Id", actor.toString()).contentType("application/json")
                        .content(CUERPO_DEL_REGISTRO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hoy no tienes Mi Radar"));
    }

    @Test
    @DisplayName("el registro responde su hora prevista (14) y, si fue libre, null")
    void registrarResponde() throws Exception {
        actor(UserStatus.ACTIVE);
        RegistroRadar libre = RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), actor, "h", "p", "s", 6,
                "e", Instant.parse("2026-10-20T19:06:00Z"));
        when(registrar.registrar(any())).thenReturn(libre.conHoraPrevista(14), libre);

        for (Integer esperada : new Integer[]{14, null}) {
            ResultActions respuesta = mvc.perform(post("/api/v1/radar").header("X-Actor-Id", actor.toString())
                            .contentType("application/json").content(CUERPO_DEL_REGISTRO))
                    .andExpect(status().isOk());
            if (esperada == null) {
                respuesta.andExpect(jsonPath("$.horaPrevista").doesNotExist());
            } else {
                respuesta.andExpect(jsonPath("$.horaPrevista").value(esperada));
            }
        }
    }

    // ── el historial ────────────────────────────────────────────────────────

    @Test
    @DisplayName("el historial trae miRadar y la hora prevista de cada registro")
    void historialConMiRadar() throws Exception {
        actor(UserStatus.ACTIVE);
        RegistroRadar registro = RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), actor, "h", "p", "s",
                6, "e", Instant.parse("2026-10-20T19:06:00Z")).conHoraPrevista(14);
        when(historial.historial(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new HistorialRadarPage(List.of(registro), null, deHoy(true)));

        mvc.perform(get("/api/v1/radar/history").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].horaPrevista").value(14))
                .andExpect(jsonPath("$.entries[0].energyLevel").value(6))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.miRadar.encendido").value(true))
                .andExpect(jsonPath("$.miRadar.hoy").value("2026-10-30"))
                .andExpect(jsonPath("$.miRadar.dias", hasSize(1)))
                .andExpect(jsonPath("$.miRadar.dias[0].despertar").value("05:30"))
                .andExpect(jsonPath("$.miRadar.dias[0].veces").value(17));
    }

    @Test
    @DisplayName("el historial pide paginas de 84, no de 20: desde el dia 8 un dia puede tener hasta 21 registros (03-23)")
    void historialPideDeOchentaYCuatro() throws Exception {
        actor(UserStatus.ACTIVE);
        when(historial.historial(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new HistorialRadarPage(List.of(), null, deHoy(false)));

        mvc.perform(get("/api/v1/radar/history").header("X-Actor-Id", actor.toString())).andExpect(status().isOk());

        verify(historial).historial(actor, actor, null, 84);
    }

    @Test
    @DisplayName("el historial de la semana 1 va con miRadar en null: Mi Radar no aplica antes del dia 8")
    void historialDeLaSemana1SinMiRadar() throws Exception {
        actor(UserStatus.ACTIVE);
        when(historial.historial(any(), any(), isNull(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new HistorialRadarPage(List.of(), null, null));

        mvc.perform(get("/api/v1/radar/history").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(0)))
                .andExpect(jsonPath("$.miRadar").doesNotExist());
    }
}
