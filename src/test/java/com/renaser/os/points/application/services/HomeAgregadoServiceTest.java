package com.renaser.os.points.application.services;

import com.renaser.os.points.api.HabitoDelDiaResumen;
import com.renaser.os.points.api.HabitosDelDiaFinder;
import com.renaser.os.points.api.NotificacionesNoLeidasFinder;
import com.renaser.os.points.api.ProximoEventoFinder;
import com.renaser.os.points.api.RocaDelDiaResumen;
import com.renaser.os.points.api.RocasDelDiaFinder;
import com.renaser.os.points.application.ports.in.home.ConsultarResumenHomeUseCase.ResumenHome;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeAgregadoServiceTest {

    // 10:00 UTC en America/Lima (UTC-5) es 05:00 local, mismo dia calendario.
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));
    private static final LocalDate HOY_LIMA = LocalDate.of(2026, 8, 26);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Mock
    private ConsultarPuntajeUseCase consultarPuntajeUseCase;
    @Mock
    private ParticipacionProgramaFinder participacionProgramaFinder;
    @Mock
    private HabitosDelDiaFinder habitosDelDiaFinder;
    @Mock
    private RocasDelDiaFinder rocasDelDiaFinder;
    @Mock
    private ProximoEventoFinder proximoEventoFinder;
    @Mock
    private NotificacionesNoLeidasFinder notificacionesNoLeidasFinder;

    private final UserId actor = UserId.of(UUID.randomUUID());

    private HomeAgregadoService nuevoServicio() {
        return new HomeAgregadoService(consultarPuntajeUseCase, participacionProgramaFinder, habitosDelDiaFinder,
                rocasDelDiaFinder, proximoEventoFinder, notificacionesNoLeidasFinder, CLOCK);
    }

    private ParticipacionPrograma participacionInscrita() {
        return new ParticipacionPrograma(actor, true, 12, LocalDate.of(2026, 5, 1), LIMA,
                FasePrograma.PHASE_1_REBIRTH, UUID.randomUUID(), UserId.of(UUID.randomUUID()), UserRole.TRAINEE,
                false);
    }

    @Test
    @DisplayName("consultar() proyecta puntaje/coherencia/racha del propio actor mas los 5 widgets con dato real")
    void consultarProyectaTodoLoQueTieneFinder() {
        HomeAgregadoService service = nuevoServicio();
        PuntajeParticipante puntaje = PuntajeParticipante.rehydrate(actor, new BigDecimal("87.50"), 150, 4, 9,
                CLOCK.now());
        when(consultarPuntajeUseCase.consultar(actor, actor)).thenReturn(puntaje);
        when(participacionProgramaFinder.deParticipante(actor)).thenReturn(Optional.of(participacionInscrita()));
        when(habitosDelDiaFinder.deHoy(actor, HOY_LIMA)).thenReturn(List.of(
                new HabitoDelDiaResumen(UUID.randomUUID(), "Meditar", "COMPLETADO"),
                new HabitoDelDiaResumen(UUID.randomUUID(), "Leer", "PENDIENTE")));
        when(rocasDelDiaFinder.deHoy(actor)).thenReturn(List.of(
                new RocaDelDiaResumen(UUID.randomUUID(), "Roca 1", "desc", true),
                new RocaDelDiaResumen(UUID.randomUUID(), "Roca 2", "desc", false),
                new RocaDelDiaResumen(UUID.randomUUID(), "Roca 3", "desc", true)));
        UUID eventoId = UUID.randomUUID();
        Instant inicioEvento = Instant.parse("2026-08-27T15:00:00Z");
        when(proximoEventoFinder.proximoEventoDe(actor))
                .thenReturn(Optional.of(new ProximoEventoFinder.ProximoEvento(eventoId, "Retiro", inicioEvento)));
        when(notificacionesNoLeidasFinder.contarNoLeidas(actor)).thenReturn(3L);

        ResumenHome resumen = service.consultar(actor);

        assertThat(resumen.puntosLiga()).isEqualTo(150);
        assertThat(resumen.coherencia()).isEqualByComparingTo("87.50");
        assertThat(resumen.rachaActual()).isEqualTo(4);
        assertThat(resumen.rachaMaxima()).isEqualTo(9);
        assertThat(resumen.diaPrograma()).isEqualTo(12);
        assertThat(resumen.inscrito()).isTrue();
        assertThat(resumen.fase()).isEqualTo(FasePrograma.PHASE_1_REBIRTH);
        assertThat(resumen.habitosHoy().completados()).isEqualTo(1);
        assertThat(resumen.habitosHoy().total()).isEqualTo(2);
        assertThat(resumen.rocasHoy().completadas()).isEqualTo(2);
        assertThat(resumen.rocasHoy().total()).isEqualTo(3);
        assertThat(resumen.proximoEvento().eventoId()).isEqualTo(eventoId);
        assertThat(resumen.proximoEvento().titulo()).isEqualTo("Retiro");
        assertThat(resumen.proximoEvento().iniciaEn()).isEqualTo(inicioEvento);
        assertThat(resumen.notificacionesNoLeidas()).isEqualTo(3L);
    }

    @Test
    @DisplayName("solo el bloqueo sin finder posible (weekStatus/avatarState) sigue documentado")
    void documentaSoloElBloqueoRealMenteSinFinder() {
        HomeAgregadoService service = nuevoServicio();
        when(consultarPuntajeUseCase.consultar(actor, actor)).thenReturn(PuntajeParticipante.inicial(actor, CLOCK));
        when(participacionProgramaFinder.deParticipante(actor)).thenReturn(Optional.of(participacionInscrita()));
        when(habitosDelDiaFinder.deHoy(actor, HOY_LIMA)).thenReturn(List.of());
        when(rocasDelDiaFinder.deHoy(actor)).thenReturn(List.of());
        when(proximoEventoFinder.proximoEventoDe(actor)).thenReturn(Optional.empty());
        when(notificacionesNoLeidasFinder.contarNoLeidas(actor)).thenReturn(0L);

        ResumenHome resumen = service.consultar(actor);

        assertThat(resumen.bloqueos()).hasSize(1);
        assertThat(resumen.bloqueos().get(0)).contains("weekStatus");
    }

    @Test
    @DisplayName("un actor suspendido no ve su resumen de Inicio, y ningun otro finder se invoca")
    void actorSuspendidoEsRechazadoSinTocarOtrosModulos() {
        HomeAgregadoService service = nuevoServicio();
        when(consultarPuntajeUseCase.consultar(actor, actor)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);

        verify(participacionProgramaFinder, never()).deParticipante(any());
        verify(habitosDelDiaFinder, never()).deHoy(any(), any());
        verify(rocasDelDiaFinder, never()).deHoy(any());
        verify(proximoEventoFinder, never()).proximoEventoDe(any());
        verify(notificacionesNoLeidasFinder, never()).contarNoLeidas(any());
    }

    @Test
    @DisplayName("participacionProgramaFinder vacio (usuario inexistente) se propaga, no se esconde")
    void participanteInexistenteSePropaga() {
        HomeAgregadoService service = nuevoServicio();
        when(consultarPuntajeUseCase.consultar(actor, actor)).thenReturn(PuntajeParticipante.inicial(actor, CLOCK));
        when(participacionProgramaFinder.deParticipante(actor)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("habitosHoy se degrada a null si el actor no tiene progreso de habitos (no es un error de toda la request)")
    void habitosHoySeDegradaANullSinProgreso() {
        HomeAgregadoService service = nuevoServicio();
        when(consultarPuntajeUseCase.consultar(actor, actor)).thenReturn(PuntajeParticipante.inicial(actor, CLOCK));
        when(participacionProgramaFinder.deParticipante(actor)).thenReturn(Optional.of(participacionInscrita()));
        when(habitosDelDiaFinder.deHoy(actor, HOY_LIMA))
                .thenThrow(new NoSuchElementException("Participante no encontrado"));
        when(rocasDelDiaFinder.deHoy(actor)).thenReturn(List.of());
        when(proximoEventoFinder.proximoEventoDe(actor)).thenReturn(Optional.empty());
        when(notificacionesNoLeidasFinder.contarNoLeidas(actor)).thenReturn(0L);

        ResumenHome resumen = service.consultar(actor);

        assertThat(resumen.habitosHoy()).isNull();
        assertThat(resumen.rocasHoy()).isNotNull();
    }

    @Test
    @DisplayName("proximoEvento y notificacionesNoLeidas se degradan a null ante NotAuthorizedException del finder")
    void proximoEventoYNotificacionesSeDegradanAnteNotAuthorized() {
        HomeAgregadoService service = nuevoServicio();
        when(consultarPuntajeUseCase.consultar(actor, actor)).thenReturn(PuntajeParticipante.inicial(actor, CLOCK));
        when(participacionProgramaFinder.deParticipante(actor)).thenReturn(Optional.of(participacionInscrita()));
        when(habitosDelDiaFinder.deHoy(actor, HOY_LIMA)).thenReturn(List.of());
        when(rocasDelDiaFinder.deHoy(actor)).thenReturn(List.of());
        when(proximoEventoFinder.proximoEventoDe(actor)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        when(notificacionesNoLeidasFinder.contarNoLeidas(actor)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        ResumenHome resumen = service.consultar(actor);

        assertThat(resumen.proximoEvento()).isNull();
        assertThat(resumen.notificacionesNoLeidas()).isNull();
    }
}
