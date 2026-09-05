package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.PuntosEnJuego;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los dos agregados de 2026-09-05 a la proyeccion de {@code GET /habit-tracks/today}: los puntos
 * en juego, y que "hoy" sea el dia del APRENDIZ.
 *
 * <p><b>Este es el test de regresion de E-105.</b> El reloj esta puesto a las 01:50 UTC del 6,
 * que en Lima (UTC-5) son las 20:50 del 5: la fecha del servidor y la del aprendiz NO coinciden.
 * Contra la implementacion vieja — {@code HabitTrackController} pasando {@code LocalDate.now()}
 * — la consulta salia con el dia 6 y la pantalla de habitos quedaba vacia. Si alguien vuelve a
 * resolver "hoy" con el reloj del servidor, este test se pone rojo.
 *
 * <p>Vive en una clase aparte de {@code TracksDelDiaProyeccionServiceTest} para no aflojar a
 * {@code LENIENT} la verificacion estricta de stubs que esa clase ya tenia.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracksDelDiaPuntosEnJuegoTest {

    private static final Instant MADRUGADA_UTC = Instant.parse("2026-09-06T01:50:00Z");
    private static final LocalDate DIA_EN_LIMA = LocalDate.of(2026, 9, 5);
    private static final LocalDate DIA_EN_EL_SERVIDOR = LocalDate.of(2026, 9, 6);

    private static final UserId PARTICIPANTE = UserId.of(UUID.randomUUID());
    private static final HabitoId HABITO = HabitoId.of(UUID.randomUUID());
    private static final RegistroHabitoId REGISTRO = RegistroHabitoId.of(UUID.randomUUID());

    @Mock
    private ConsultarTracksDelDiaUseCase consultarTracksUseCase;
    @Mock
    private GenerarTracksDelDiaUseCase generarTracksUseCase;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadHorarioHabitoPort loadHorarioPort;
    @Mock
    private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock
    private LoadGuiaHabitoPort loadGuiaPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;

    private TracksDelDiaProyeccionService servicio() {
        return new TracksDelDiaProyeccionService(consultarTracksUseCase, generarTracksUseCase, loadHabitoPort,
                loadHorarioPort, loadPreferenciaPort, loadGuiaPort, progresoPort, FixedClock.at(MADRUGADA_UTC));
    }

    private void participanteEnLima() {
        when(progresoPort.deParticipante(PARTICIPANTE)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false)));
    }

    private static RegistroHabito registro(EstadoRegistro estado) {
        return RegistroHabito.rehydrate(REGISTRO, PARTICIPANTE, HABITO, DIA_EN_LIMA, 5, TipoDia.TODOS, false, estado,
                estado == EstadoRegistro.COMPLETADO ? 10 : 0, null, null, null, null, MADRUGADA_UTC, MADRUGADA_UTC);
    }

    private void catalogoConHorario(LocalTime disparo, LocalTime limite) {
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(Habito.crearDeSistema(HABITO, "Meditacion nocturna",
                TipoHabito.CHECKBOX, "MENTE", ExigenciaEvidencia.OPCIONAL, MADRUGADA_UTC)));
        when(loadHorarioPort.porHabitos(any())).thenReturn(disparo == null && limite == null ? List.of()
                : List.of(HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), HABITO, 1, null, TipoDia.TODOS,
                        disparo, limite, MADRUGADA_UTC)));
        when(loadGuiaPort.porHabitos(any())).thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteYHabitos(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("E-105: consultarHoyDe usa el dia del aprendiz, no la fecha del servidor")
    void consultaElDiaDelAprendizYNoElDelServidor() {
        participanteEnLima();
        catalogoConHorario(LocalTime.of(21, 0), LocalTime.of(22, 0));
        when(consultarTracksUseCase.consultar(PARTICIPANTE, PARTICIPANTE, DIA_EN_LIMA))
                .thenReturn(List.of(registro(EstadoRegistro.PENDIENTE)));

        List<TrackDelDiaConCatalogo> vista = servicio().consultarHoyDe(PARTICIPANTE);

        assertThat(vista).hasSize(1);
        verify(consultarTracksUseCase).consultar(PARTICIPANTE, PARTICIPANTE, DIA_EN_LIMA);
        verify(consultarTracksUseCase, never()).consultar(PARTICIPANTE, PARTICIPANTE, DIA_EN_EL_SERVIDOR);
    }

    @Test
    @DisplayName("un track vivo trae puntos en juego y plazo, calculados en la zona del aprendiz")
    void traeLosPuntosEnJuegoDeUnTrackVivo() {
        participanteEnLima();
        catalogoConHorario(LocalTime.of(21, 0), LocalTime.of(22, 0));
        when(consultarTracksUseCase.consultar(PARTICIPANTE, PARTICIPANTE, DIA_EN_LIMA))
                .thenReturn(List.of(registro(EstadoRegistro.PENDIENTE)));

        PuntosEnJuego enJuego = servicio().consultarHoyDe(PARTICIPANTE).get(0).puntosEnJuego();

        assertThat(enJuego).isNotNull();
        // 20:50 en Lima, antes del cierre de las 22:00: paga el puntaje completo.
        assertThat(enJuego.siCompletaAhora()).isEqualTo(10);
        assertThat(enJuego.maximo()).isEqualTo(10);
        // 22:00 Lima = 03:00 UTC, + 10 min de gracia + la extension recortada contra la
        // medianoche local (05:00 UTC): el plazo no puede pasarse del dia del aprendiz.
        assertThat(enJuego.plazo()).isEqualTo(Instant.parse("2026-09-06T05:00:00Z"));
    }

    @Test
    @DisplayName("un track ya completado no tiene nada en juego")
    void elCompletadoNoTienePuntosEnJuego() {
        participanteEnLima();
        catalogoConHorario(LocalTime.of(21, 0), LocalTime.of(22, 0));
        when(consultarTracksUseCase.consultar(PARTICIPANTE, PARTICIPANTE, DIA_EN_LIMA))
                .thenReturn(List.of(registro(EstadoRegistro.COMPLETADO)));

        assertThat(servicio().consultarHoyDe(PARTICIPANTE).get(0).puntosEnJuego()).isNull();
    }

    @Test
    @DisplayName("un habito sin horario paga completo y no tiene plazo")
    void sinHorarioPagaCompletoYNoVence() {
        participanteEnLima();
        catalogoConHorario(null, null);
        when(consultarTracksUseCase.consultar(PARTICIPANTE, PARTICIPANTE, DIA_EN_LIMA))
                .thenReturn(List.of(registro(EstadoRegistro.PENDIENTE)));

        PuntosEnJuego enJuego = servicio().consultarHoyDe(PARTICIPANTE).get(0).puntosEnJuego();

        assertThat(enJuego.siCompletaAhora()).isEqualTo(10);
        assertThat(enJuego.plazo()).isNull();
    }
}
