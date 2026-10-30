package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.in.registro.SolicitarUrlEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-288 (spec 014): {@code GET /api/v1/habit-tracks/day?date=}. Aca solo se prueba lo del transporte: la misma forma
 * que {@code /today}, la cabecera de cache, los 400 de fecha ausente o mal formada, y los 403 de siempre. La regla de
 * que dias se pueden ver se prueba en {@code DiaCerradoConsultaTest} con el reloj en la madrugada UTC.
 */
@WebMvcTest(HabitTrackController.class)
@AutoConfigureMockMvc(addFilters = false)
class DiaCerradoControllerTest {

    private static final String RUTA = "/api/v1/habit-tracks/day";
    private static final LocalDate AYER = LocalDate.of(2026, 10, 27);

    @Autowired MockMvc mvc;
    @MockitoBean ConsultarTracksDelDiaConCatalogoUseCase consultar;
    @MockitoBean CompletarRegistroUseCase completar;
    @MockitoBean SubirEvidenciaRegistroUseCase evidencia;
    @MockitoBean SolicitarUrlEvidenciaRegistroUseCase url;
    @MockitoBean UserSummaryFinder users;
    @MockitoBean com.renaser.os.shared.application.Interruptores interruptores;

    private final UserId actor = UserId.of(UUID.randomUUID());

    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    private void actor(UserStatus estado) {
        when(users.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Actor", null,
                UserRole.TRAINEE, estado)));
    }

    private TrackDelDiaConCatalogo unRegistro(LocalDate fecha) {
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), actor,
                HabitoId.of(UUID.randomUUID()), fecha, 12, TipoDia.TODOS, false, Instant.parse("2026-10-27T10:00:00Z"));
        return new TrackDelDiaConCatalogo(registro, "MEDITAR", TipoHabito.CHECKBOX, null, LocalTime.of(6, 0),
                LocalTime.of(7, 0), null, false, false);
    }

    @Test void devuelveLaMismaFormaQueTodayYLaGuardaCincoMinutosSoloEnPrivado() throws Exception {
        actor(UserStatus.ACTIVE);
        List<TrackDelDiaConCatalogo> dia = List.of(unRegistro(AYER));
        when(consultar.consultarDiaCerradoDe(actor, AYER)).thenReturn(dia);
        when(consultar.consultarHoyDe(actor)).thenReturn(dia);

        String delDia = mvc.perform(get(RUTA).param("date", "2026-10-27").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, max-age=300"))
                .andExpect(header().string("Vary", org.hamcrest.Matchers.containsString("X-Auth-Token")))
                .andExpect(jsonPath("$[0].tituloHabito").value("MEDITAR"))
                .andExpect(jsonPath("$[0].tipoHabito").value("CHECKBOX"))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].fechaEjecucion").value("2026-10-27"))
                .andReturn().getResponse().getContentAsString();
        String deHoy = mvc.perform(get("/api/v1/habit-tracks/today").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        // Los dos endpoints arman el mismo objeto: mismos campos, misma serializacion.
        assertThat(delDia).isEqualTo(deHoy);
        verify(consultar).consultarDiaCerradoDe(actor, AYER);
    }

    @Test void unDiaSinRegistrosEsUnArrayVacio() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultar.consultarDiaCerradoDe(actor, AYER)).thenReturn(List.of());

        mvc.perform(get(RUTA).param("date", "2026-10-27").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void sinFechaEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        mvc.perform(get(RUTA).header("X-Actor-Id", actor.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consultar);
    }

    @Test void unaFechaMalFormadaEs400() throws Exception {
        actor(UserStatus.ACTIVE);

        for (String mala : List.of("ayer", "27-10-2026", "2026-13-45", "2026-10-27T10:00:00Z", "")) {
            mvc.perform(get(RUTA).param("date", mala).header("X-Actor-Id", actor.toString()))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(consultar);
    }

    @Test void unaFechaFueraDeLaVentanaEs400ConElMotivoYSinCache() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultar.consultarDiaCerradoDe(actor, LocalDate.of(2026, 10, 28))).thenThrow(
                new IllegalArgumentException("Ese día todavía no cerró. Solo puedes ver los días que ya cerraron."));

        mvc.perform(get(RUTA).param("date", "2026-10-28").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("todavía no cerró")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.not("private, max-age=300")));
        verify(consultar, never()).consultarHoyDe(any());
    }

    @Test void cuentaSuspendidaEs403SinLlegarAlCasoDeUso() throws Exception {
        actor(UserStatus.SUSPENDED);

        mvc.perform(get(RUTA).param("date", "2026-10-27").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultar);
    }

    @Test void laDenegacionDelCasoDeUsoEs403() throws Exception {
        actor(UserStatus.ACTIVE);
        when(consultar.consultarDiaCerradoDe(actor, AYER)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        mvc.perform(get(RUTA).param("date", "2026-10-27").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isForbidden());
    }

    @Test void respetaElInterruptorDeHabitos() throws Exception {
        actor(UserStatus.ACTIVE);
        doThrow(new com.renaser.os.shared.domain.FuncionalidadApagadaException("habitos", "En mantenimiento"))
                .when(interruptores).exigirPrendidaPara("habitos", actor);

        mvc.perform(get(RUTA).param("date", "2026-10-27").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(consultar);
    }
}
