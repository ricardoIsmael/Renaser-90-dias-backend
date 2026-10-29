package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.ConfigurarMiRadarCommand;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;
import com.renaser.os.habits.application.ports.in.radar.ConsultarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarUltimoRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar.DiaPropio;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    // ── GET /mi-radar ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET: encendido devuelve {encendido:true, horas:[...]} de menor a mayor")
    void leerEncendido() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultarMiRadar.consultar(actor, actor)).thenReturn(MiRadar.encendido(List.of(20, 9, 14)));

        leer().andExpect(status().isOk())
                .andExpect(jsonPath("$.encendido").value(true))
                .andExpect(jsonPath("$.horas", hasSize(3)))
                .andExpect(jsonPath("$.horas[0]").value(9))
                .andExpect(jsonPath("$.horas[2]").value(20));
    }

    @Test
    @DisplayName("GET: apagado devuelve {encendido:false, horas:[]}")
    void leerApagado() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultarMiRadar.consultar(actor, actor)).thenReturn(MiRadar.apagado());

        leer().andExpect(status().isOk())
                .andExpect(jsonPath("$.encendido").value(false))
                .andExpect(jsonPath("$.horas", hasSize(0)))
                .andExpect(jsonPath("$.dias", hasSize(0)));
    }

    @Test
    @DisplayName("GET: con dias devuelve los dias con horas propias (lunes = 1) y «Sin Radar ese dia» con horas vacias")
    void leerConDias() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultarMiRadar.consultar(actor, actor)).thenReturn(MiRadar.encendido(List.of(9, 14, 20),
                List.of(new DiaPropio(7, List.of()), new DiaPropio(6, List.of(11)))));

        leer().andExpect(status().isOk())
                .andExpect(jsonPath("$.encendido").value(true))
                .andExpect(jsonPath("$.horas", hasSize(3)))
                .andExpect(jsonPath("$.dias", hasSize(2)))
                .andExpect(jsonPath("$.dias[0].dia").value(6))
                .andExpect(jsonPath("$.dias[0].horas[0]").value(11))
                .andExpect(jsonPath("$.dias[1].dia").value(7))
                .andExpect(jsonPath("$.dias[1].horas", hasSize(0)));
    }

    // ── PUT /mi-radar ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT: encender manda al caso de uso el actor como actor y como participante (no se puede pedir el de otro) y devuelve lo guardado")
    void encender() throws Exception {
        actor(UserStatus.ACTIVE);
        when(configurarMiRadar.configurar(any())).thenReturn(MiRadar.encendido(List.of(9, 14, 20)));

        guardar("{\"encendido\":true,\"horas\":[20,9,14]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.encendido").value(true))
                .andExpect(jsonPath("$.horas[1]").value(14));

        verify(configurarMiRadar).configurar(new ConfigurarMiRadarCommand(actor, actor, true, List.of(20, 9, 14),
                List.of()));
    }

    @Test
    @DisplayName("PUT: con dias validos los manda al caso de uso como dias propios y devuelve lo guardado")
    void encenderConDias() throws Exception {
        actor(UserStatus.ACTIVE);
        MiRadar guardado = MiRadar.encendido(List.of(9, 14),
                List.of(new DiaPropio(6, List.of(11)), new DiaPropio(7, List.of())));
        when(configurarMiRadar.configurar(any())).thenReturn(guardado);

        guardar("{\"encendido\":true,\"horas\":[14,9],\"dias\":[{\"dia\":6,\"horas\":[11]},"
                + "{\"dia\":7,\"horas\":[]}]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.encendido").value(true))
                .andExpect(jsonPath("$.horas[0]").value(9))
                .andExpect(jsonPath("$.dias", hasSize(2)))
                .andExpect(jsonPath("$.dias[0].dia").value(6))
                .andExpect(jsonPath("$.dias[0].horas[0]").value(11))
                .andExpect(jsonPath("$.dias[1].horas", hasSize(0)));

        verify(configurarMiRadar).configurar(new ConfigurarMiRadarCommand(actor, actor, true, List.of(14, 9),
                List.of(new DiaPropio(6, List.of(11)), new DiaPropio(7, List.of()))));
    }

    @Test
    @DisplayName("PUT: sin «dias» equivale a dias vacio: lo que llega al caso de uso es una lista vacia")
    void sinDiasEquivaleADiasVacios() throws Exception {
        actor(UserStatus.ACTIVE);
        when(configurarMiRadar.configurar(any())).thenReturn(MiRadar.encendido(List.of(9)));

        guardar("{\"encendido\":true,\"horas\":[9]}").andExpect(status().isOk());
        guardar("{\"encendido\":true,\"horas\":[9],\"dias\":null}").andExpect(status().isOk());
        guardar("{\"encendido\":true,\"horas\":[9],\"dias\":[]}").andExpect(status().isOk());

        verify(configurarMiRadar, times(3)).configurar(new ConfigurarMiRadarCommand(actor, actor, true, List.of(9),
                List.of()));
    }

    @Test
    @DisplayName("PUT: un dia fuera de 1 a 7 es 400 con el mensaje del dominio y no llega al caso de uso")
    void diaFueraDeRangoEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        for (int dia : new int[]{0, 8, -1, 100}) {
            guardar("{\"encendido\":true,\"horas\":[9],\"dias\":[{\"dia\":" + dia + ",\"horas\":[11]}]}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("El día de Mi Radar va de 1 (lunes) a 7 (domingo)"));
        }
        verifyNoInteractions(configurarMiRadar);
    }

    @Test
    @DisplayName("PUT: las horas propias de un dia fuera de las reglas (2, 24, repetidas, mas de 12) son 400")
    void horasPropiasInvalidasSon400() throws Exception {
        actor(UserStatus.ACTIVE);

        for (String horas : List.of("[2]", "[24]", "[9,9]", "[3,4,5,6,7,8,9,10,11,12,13,14,15]", "[9,null]")) {
            guardar("{\"encendido\":true,\"horas\":[9],\"dias\":[{\"dia\":6,\"horas\":" + horas + "}]}")
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(configurarMiRadar);
    }

    @Test
    @DisplayName("PUT: un dia sin numero o sin «horas» (ausente o null), o un elemento null, es 400: «Sin Radar» se dice con []")
    void diaIncompletoEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        for (String dias : List.of("[{\"horas\":[11]}]", "[{\"dia\":6}]", "[{\"dia\":6,\"horas\":null}]",
                "[null]", "[{}]", "{\"dia\":6,\"horas\":[11]}", "[6]")) {
            guardar("{\"encendido\":true,\"horas\":[9],\"dias\":" + dias + "}")
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(configurarMiRadar);
    }

    @Test
    @DisplayName("PUT: lo que rechaza el dominio dentro del caso de uso (dia repetido, dias sin horas de todos los dias) es 400 con su mensaje")
    void reglasDelDominioSon400() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new IllegalArgumentException("No repitas el mismo día")).when(configurarMiRadar).configurar(any());

        guardar("{\"encendido\":true,\"horas\":[9],\"dias\":[{\"dia\":6,\"horas\":[10]},"
                + "{\"dia\":6,\"horas\":[11]}]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No repitas el mismo día"));
    }

    @Test
    @DisplayName("PUT: apagar con horas de sobra las manda al caso de uso, que las ignora")
    void apagar() throws Exception {
        actor(UserStatus.ACTIVE);
        when(configurarMiRadar.configurar(any())).thenReturn(MiRadar.apagado());

        guardar("{\"encendido\":false,\"horas\":[9]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.encendido").value(false))
                .andExpect(jsonPath("$.horas", hasSize(0)));
    }

    @Test
    @DisplayName("PUT: las horas fuera de las reglas que rechaza el dominio son 400")
    void horasInvalidasSon400() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new IllegalArgumentException("Elige al menos una hora")).when(configurarMiRadar).configurar(any());

        guardar("{\"encendido\":true,\"horas\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Elige al menos una hora"));
    }

    @Test
    @DisplayName("PUT: omitir «encendido» es 400 y no toca nada; un cuerpo que no es JSON tambien")
    void cuerpoInvalidoEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        guardar("{\"horas\":[9]}").andExpect(status().isBadRequest());
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
        guardar("{\"encendido\":true,\"horas\":[9]}").andExpect(status().isForbidden());

        verifyNoInteractions(consultarMiRadar, configurarMiRadar);
    }

    @Test
    @DisplayName("lo que el caso de uso niega (programa sin activar, cuenta suspendida) es 403")
    void elCasoDeUsoNiega() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new NotAuthorizedException("El Codigo Renaser es exclusivo de aprendices")).when(configurarMiRadar)
                .configurar(any());
        doThrow(new NotAuthorizedException("Cuenta suspendida")).when(consultarMiRadar).consultar(any(), any());

        guardar("{\"encendido\":true,\"horas\":[9]}").andExpect(status().isForbidden());
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
                .thenReturn(new HistorialRadarPage(List.of(registro), null, MiRadar.encendido(List.of(14))));

        mvc.perform(get("/api/v1/radar/history").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].horaPrevista").value(14))
                .andExpect(jsonPath("$.entries[0].energyLevel").value(6))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.miRadar.encendido").value(true))
                .andExpect(jsonPath("$.miRadar.horas[0]").value(14))
                .andExpect(jsonPath("$.miRadar.dias", hasSize(0)));
    }

    @Test
    @DisplayName("el historial trae los dias propios de Mi Radar dentro de miRadar")
    void historialConDiasPropios() throws Exception {
        actor(UserStatus.ACTIVE);
        when(historial.historial(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new HistorialRadarPage(List.of(), null,
                        MiRadar.encendido(List.of(14), List.of(new DiaPropio(7, List.of())))));

        mvc.perform(get("/api/v1/radar/history").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miRadar.dias", hasSize(1)))
                .andExpect(jsonPath("$.miRadar.dias[0].dia").value(7))
                .andExpect(jsonPath("$.miRadar.dias[0].horas", hasSize(0)));
    }

    @Test
    @DisplayName("el historial pide paginas de 84, no de 20: desde el dia 8 un dia puede tener hasta 21 registros (03-23)")
    void historialPideDeOchentaYCuatro() throws Exception {
        actor(UserStatus.ACTIVE);
        when(historial.historial(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new HistorialRadarPage(List.of(), null, MiRadar.apagado()));

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
