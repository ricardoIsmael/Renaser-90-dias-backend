package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasAgendadasUseCase.RocasAgendadas;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-217: las acciones agendadas de hoy hasta el domingo, para las alarmas del telefono.
 *
 * <p>Fixture coherente con {@code AgregarRocaDiariaServiceTest}: programa iniciado el martes 2026-09-01
 * (Dia 1); la semana 4 va del lunes 21 al domingo 27 (D-203). Los relojes a las 03:00 UTC caen en Lima
 * en el dia ANTERIOR (regla 02): con la fecha del servidor, el rango arrancaria un dia tarde.
 */
class RocasAgendadasServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final ConsultarProgresoParticipanteRocksPort progresoPort = mock(ConsultarProgresoParticipanteRocksPort.class);
    private final LoadRocaDiariaPort diarias = mock(LoadRocaDiariaPort.class);

    private RocasAgendadas agendadasCon(ProgresoParticipanteRocks progreso, String instante) {
        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(progreso));
        when(diarias.deParticipanteEntreFechas(any(), any(), any())).thenReturn(List.of());
        return new RocasAgendadasService(progresoPort, diarias, FixedClock.at(Instant.parse(instante)))
                .agendadas(aprendiz);
    }

    private static ProgresoParticipanteRocks enCurso(int dia) {
        return new ProgresoParticipanteRocks(dia, INICIO, LIMA, RolParticipante.TRAINEE, false, true);
    }

    @Test
    @DisplayName("martes 22 a la noche en Lima (03:00 UTC del miercoles) -> del martes 22 al domingo 27")
    void unMartesHastaElDomingo() {
        RocasAgendadas agendadas = agendadasCon(enCurso(22), "2026-09-23T03:00:00Z");

        assertThat(agendadas.desde()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(agendadas.hasta()).isEqualTo(LocalDate.of(2026, 9, 27));
        verify(diarias).deParticipanteEntreFechas(aprendiz, LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 27));
    }

    @Test
    @DisplayName("el domingo 27 incluye el lunes 28, que se agenda el domingo (E-340)")
    void unDomingoIncluyeElLunes() {
        RocasAgendadas agendadas = agendadasCon(enCurso(27), "2026-09-28T03:00:00Z");

        assertThat(agendadas.desde()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(agendadas.hasta()).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    @DisplayName("sin Dia 1 elegido no hay semanas: hoy y mañana, como /today y /tomorrow")
    void sinDiaUno() {
        RocasAgendadas agendadas = agendadasCon(
                new ProgresoParticipanteRocks(0, null, LIMA, RolParticipante.TRAINEE, false, false),
                "2026-09-23T03:00:00Z");

        assertThat(agendadas.desde()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(agendadas.hasta()).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("pasado el dia 90 el rango no queda al reves: solo hoy")
    void pasadoElDia90() {
        RocasAgendadas agendadas = agendadasCon(new ProgresoParticipanteRocks(90, INICIO.minusDays(100), LIMA,
                RolParticipante.TRAINEE, false, true, LocalDate.of(2026, 9, 20)), "2026-09-23T15:00:00Z");

        assertThat(agendadas.desde()).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(agendadas.hasta()).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("cuenta suspendida -> 403 sin leer rocas")
    void suspendido() {
        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(
                new ProgresoParticipanteRocks(22, INICIO, LIMA, RolParticipante.TRAINEE, true, true)));

        assertThatThrownBy(() -> new RocasAgendadasService(progresoPort, diarias,
                FixedClock.at(Instant.parse("2026-09-23T03:00:00Z"))).agendadas(aprendiz))
                .isInstanceOf(NotAuthorizedException.class);
        verify(diarias, never()).deParticipanteEntreFechas(any(), any(), any());
    }
}
