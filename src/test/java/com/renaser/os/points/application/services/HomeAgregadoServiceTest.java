package com.renaser.os.points.application.services;

import com.renaser.os.points.api.DiasConHabitoCumplidoFinder;
import com.renaser.os.points.api.HabitoDelDiaResumen;
import com.renaser.os.points.api.HabitosDelDiaFinder;
import com.renaser.os.points.api.NotificacionesNoLeidasFinder;
import com.renaser.os.points.api.ProximoEventoFinder;
import com.renaser.os.points.api.RocaDelDiaResumen;
import com.renaser.os.points.api.PorcentajeRocasFinder;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private DiasConHabitoCumplidoFinder diasConHabitoCumplidoFinder;
    @Mock
    private RocasDelDiaFinder rocasDelDiaFinder;
    @Mock
    private ProximoEventoFinder proximoEventoFinder;
    @Mock
    private NotificacionesNoLeidasFinder notificacionesNoLeidasFinder;
    @Mock
    private PorcentajeRocasFinder porcentajeRocasFinder;

    private final UserId actor = UserId.of(UUID.randomUUID());

    private HomeAgregadoService nuevoServicio() {
        return new HomeAgregadoService(consultarPuntajeUseCase, participacionProgramaFinder, habitosDelDiaFinder,
                diasConHabitoCumplidoFinder, rocasDelDiaFinder, proximoEventoFinder, notificacionesNoLeidasFinder,
                porcentajeRocasFinder, CLOCK);
    }

    private HomeAgregadoService nuevoServicioCon(FixedClock reloj) {
        return new HomeAgregadoService(consultarPuntajeUseCase, participacionProgramaFinder, habitosDelDiaFinder,
                diasConHabitoCumplidoFinder, rocasDelDiaFinder, proximoEventoFinder, notificacionesNoLeidasFinder,
                porcentajeRocasFinder, reloj);
    }

    /**
     * Quien no planifico una sola accion en la semana no tiene coherencia: viaja {@code null}, y la
     * app muestra un guion. Es la diferencia entre "no armo su semana" y "la armo y no cumplio
     * nada" (que es un 0 que hay que mirar). Antes las dos cosas se veian como
     * "100 % · Nivel de excelencia".
     */
    @org.junit.jupiter.api.Test
    void sinAccionesPlanificadasLaCoherenciaViajaNula() {
        HomeAgregadoService service = nuevoServicio();
        when(consultarPuntajeUseCase.consultar(actor, actor)).thenReturn(
                PuntajeParticipante.rehydrate(actor, new BigDecimal("100.00"), 10, 0, 0, CLOCK.now()));
        when(participacionProgramaFinder.deParticipante(actor)).thenReturn(Optional.of(participacionInscrita()));
        when(porcentajeRocasFinder.porcentajePorParticipante(List.of(actor), HOY_LIMA)).thenReturn(Map.of());

        ResumenHome resumen = service.consultar(actor);

        assertThat(resumen.coherencia()).isNull();
    }

    private ParticipacionPrograma participacionInscrita() {
        return new ParticipacionPrograma(actor, true, 12, LocalDate.of(2026, 5, 1), LIMA,
                FasePrograma.PHASE_1_REBIRTH, UUID.randomUUID(), UserId.of(UUID.randomUUID()), UserRole.TRAINEE,
                false, true);
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
        // D-128: la coherencia sale de las acciones diarias de la semana, no de la columna guardada.
        when(porcentajeRocasFinder.porcentajePorParticipante(List.of(actor), HOY_LIMA))
                .thenReturn(Map.of(actor, new BigDecimal("87.5")));
        // Tres dias seguidos terminando hoy. El puntaje guardado dice 4/9 y ya no se usa.
        when(diasConHabitoCumplidoFinder.entre(eq(actor), any(), eq(HOY_LIMA))).thenReturn(List.of(
                HOY_LIMA.minusDays(2), HOY_LIMA.minusDays(1), HOY_LIMA));

        ResumenHome resumen = service.consultar(actor);

        assertThat(resumen.puntosLiga()).isEqualTo(150);
        // DERIVADA de las acciones diarias de la semana (D-128), no leida de `puntaje`: esa columna
        // nunca la escribio nadie y quedaba en 100 para todo el mundo.
        assertThat(resumen.coherencia()).isEqualByComparingTo("87.5");
        // DERIVADA de los dias con habito cumplido, no leida de `puntaje` (que dice 4 y 9): la fila
        // guardada vale 0 para todo el mundo porque nadie invoca a quien la avanza.
        assertThat(resumen.rachaActual()).isEqualTo(3);
        assertThat(resumen.rachaMaxima()).isEqualTo(3);
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
    @DisplayName("la racha se cuenta en el dia LOCAL del participante, no en el del servidor")
    void laRachaUsaElDiaLocalDelParticipante() {
        /*
         * Este test existe por `.claude/rules/02` §3, que lo pide con nombre y apellido: el resto
         * del archivo fija el reloj a las 10:00 UTC, una hora que en Lima cae el MISMO dia
         * calendario y por eso no puede distinguir si el codigo usa la zona del participante o la
         * del servidor.
         *
         * Aca son las 03:00 UTC del 27: en Lima (UTC-5) todavia son las 22:00 del **26**. Si
         * `rachaDe` usara la fecha del servidor pediria hasta el 27 y contaria el 26 como "anteayer",
         * devolviendo 0. Es exactamente la forma del bug E-91.
         */
        FixedClock relojDeMadrugada = FixedClock.at(Instant.parse("2026-08-27T03:00:00Z"));
        HomeAgregadoService service = nuevoServicioCon(relojDeMadrugada);
        when(consultarPuntajeUseCase.consultar(actor, actor)).thenReturn(
                PuntajeParticipante.rehydrate(actor, new BigDecimal("50.00"), 10, 0, 0, relojDeMadrugada.now()));
        when(participacionProgramaFinder.deParticipante(actor)).thenReturn(Optional.of(participacionInscrita()));
        when(diasConHabitoCumplidoFinder.entre(eq(actor), any(), eq(HOY_LIMA))).thenReturn(
                List.of(HOY_LIMA.minusDays(1), HOY_LIMA));

        ResumenHome resumen = service.consultar(actor);

        assertThat(resumen.rachaActual()).isEqualTo(2);
    }

    @Test
    @DisplayName("un programa que todavia no empezo no tiene racha, y no se le pide al finder un rango al reves")
    void programaQueTodaviaNoEmpezoNoTieneRacha() {
        /*
         * La forma exacta del fallo del 2026-09-16 en produccion: cuenta creada ese dia, con
         * `fechaInicio` en el futuro. `rachaDe` pedia "desde el inicio hasta hoy" y el finder lanzaba
         * IllegalArgumentException("El rango va al reves"), que no esta entre las excepciones que
         * degradan el widget: el /home entero respondia 400 y la app mostraba ese texto en rojo.
         *
         * El inicio se elige entre manana y dentro de 3 dias (`ParticipacionPrograma.opcionesDeActivacion`);
         * se prueba el mas lejano porque es el que deja a la cuenta mas dias en este estado.
         */
        HomeAgregadoService service = nuevoServicio();
        when(consultarPuntajeUseCase.consultar(actor, actor)).thenReturn(
                PuntajeParticipante.rehydrate(actor, new BigDecimal("100.00"), 100, 0, 0, CLOCK.now()));
        ParticipacionPrograma empiezaEnTresDias = new ParticipacionPrograma(actor, true, 0, HOY_LIMA.plusDays(3), LIMA,
                FasePrograma.PHASE_1_REBIRTH, UUID.randomUUID(), UserId.of(UUID.randomUUID()), UserRole.TRAINEE,
                false, true);
        when(participacionProgramaFinder.deParticipante(actor)).thenReturn(Optional.of(empiezaEnTresDias));

        ResumenHome resumen = service.consultar(actor);

        assertThat(resumen.rachaActual()).isZero();
        assertThat(resumen.rachaMaxima()).isZero();
        verify(diasConHabitoCumplidoFinder, never()).entre(any(), any(), any());
    }

    @Test
    @DisplayName("sin dias cumplidos la racha es 0, no la que quedo guardada en el puntaje")
    void sinActividadLaRachaEsCero() {
        HomeAgregadoService service = nuevoServicio();
        when(consultarPuntajeUseCase.consultar(actor, actor)).thenReturn(
                PuntajeParticipante.rehydrate(actor, new BigDecimal("50.00"), 10, 7, 12, CLOCK.now()));
        when(participacionProgramaFinder.deParticipante(actor)).thenReturn(Optional.of(participacionInscrita()));
        when(diasConHabitoCumplidoFinder.entre(eq(actor), any(), eq(HOY_LIMA))).thenReturn(List.of());

        ResumenHome resumen = service.consultar(actor);

        // El puntaje guardado dice 7/12. Se ignora a proposito: esa fila no la actualiza nadie.
        assertThat(resumen.rachaActual()).isZero();
        assertThat(resumen.rachaMaxima()).isZero();
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
