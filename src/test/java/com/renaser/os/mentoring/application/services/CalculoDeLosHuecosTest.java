package com.renaser.os.mentoring.application.services;

import com.renaser.os.calendar.api.EventosDelParticipanteFinder;
import com.renaser.os.calendar.api.EventosDelParticipanteFinder.AgendaDeEventos;
import com.renaser.os.calendar.api.EventosDelParticipanteFinder.EventoDeLaAgenda;
import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.habits.api.HorarioDelDiaFinder.CuotaCambiosHorario;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorarioResuelto;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorariosDelDia;
import com.renaser.os.habits.api.RegistrosDeHabitoFinder;
import com.renaser.os.habits.api.RegistrosDeHabitoFinder.RegistroDeHabito;
import com.renaser.os.mentoring.domain.model.huecos.DiaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.LoReal;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.rag.api.AgendaOcupadaFinder;
import com.renaser.os.rocks.api.HorasDeLasAccionesFinder;
import com.renaser.os.rocks.api.HorasDeLasAccionesFinder.HoraDeUnaAccion;
import com.renaser.os.shared.domain.TramoDelDia;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Juntar lo que cada módulo sabe de la persona y llevarlo al dominio (spec 015). La semana es la del lunes 19 al
 * domingo 25 de octubre de 2026 en Lima; «hoy» es el jueves 22.
 */
class CalculoDeLosHuecosTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 19);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 22);
    private static final LocalDate DOMINGO = LUNES.plusDays(6);

    private final UserId persona = UserId.of(UUID.randomUUID());
    private final HorarioDelDiaFinder horarios = mock(HorarioDelDiaFinder.class);
    private final RegistrosDeHabitoFinder registros = mock(RegistrosDeHabitoFinder.class);
    private final HorasDeLasAccionesFinder acciones = mock(HorasDeLasAccionesFinder.class);
    private final EventosDelParticipanteFinder eventos = mock(EventosDelParticipanteFinder.class);
    private final AgendaOcupadaFinder agenda = mock(AgendaOcupadaFinder.class);
    private final UUID despertar = UUID.randomUUID();
    private final UUID dormir = UUID.randomUUID();
    private final UUID agua = UUID.randomUUID();

    private CalculoDeLosHuecos calculo;

    @BeforeEach
    void preparar() {
        calculo = new CalculoDeLosHuecos(horarios, registros, acciones, eventos, agenda);
        when(registros.deParticipanteEntre(any(), any(), any())).thenReturn(List.of());
        when(acciones.entre(any(), any(), any())).thenReturn(List.of());
        when(agenda.semanaDe(any())).thenReturn(Map.of());
        when(eventos.entre(any(), any(), any())).thenReturn(
                new AgendaDeEventos(LIMA, LUNES.minusDays(1), DOMINGO, List.of()));
        for (int i = 0; i < 7; i++) {
            LocalDate fecha = LUNES.plusDays(i);
            when(horarios.deFecha(persona, fecha)).thenReturn(new HorariosDelDia(fecha, 10 + i, plan(), cuota()));
        }
    }

    private static CuotaCambiosHorario cuota() {
        return new CuotaCambiosHorario(0, 3, 3, false);
    }

    /** Despertar 06:00, agua 07:00 (plazo 09:00) y Dormir 22:00: todos los dias. */
    private List<HorarioResuelto> plan() {
        return List.of(
                new HorarioResuelto(despertar, "Despertar", LocalTime.of(6, 0), null, false, false, false, true, null,
                        false, "WAKE_UP"),
                new HorarioResuelto(agua, "Agua", LocalTime.of(7, 0), LocalTime.of(9, 0), false, false, false, false,
                        null, false, null),
                new HorarioResuelto(dormir, "Dormir", LocalTime.of(22, 0), null, false, false, false, true, null,
                        false, "SLEEP"));
    }

    private ParticipacionPrograma participacion(boolean activado) {
        return new ParticipacionPrograma(persona, true, activado ? 10 : 0, LocalDate.of(2026, 10, 10), LIMA,
                FasePrograma.PHASE_2_DEVELOPMENT, null, null, UserRole.TRAINEE, false, activado);
    }

    @Test
    @DisplayName("una semana sin nada mas que los habitos: siete dias con la ventana de Despertar a Dormir")
    void semanaSoloConHabitos() {
        SemanaDeHuecos semana = calculo.calcular(participacion(true), LUNES, LocalDate.of(2026, 10, 18));

        assertThat(semana.lunes()).isEqualTo(LUNES);
        assertThat(semana.dias()).hasSize(7);
        DiaDeHuecos primero = semana.dias().get(0);
        // Despertar 06:00-06:30 y agua 07:00-07:30 dentro de 06:00-22:00.
        assertThat(primero.ventana().desde()).isEqualTo(360);
        assertThat(primero.ventana().hasta()).isEqualTo(1320);
        assertThat(primero.minutosOcupados()).isEqualTo(60);
        assertThat(primero.huecos()).containsExactly(new TramoDelDia(390, 420), new TramoDelDia(450, 1320));
        assertThat(semana.minutosOcupados()).isEqualTo(7 * 60);
        // Los siete dias libres en 07:30 a 22:00: se repite.
        assertThat(semana.repetidos()).hasSize(2);
    }

    @Test
    @DisplayName("lo planificado de los dias por venir pide el horario a la fecha de cada dia")
    void pideElHorarioDeCadaFecha() {
        calculo.calcular(participacion(true), LUNES, LocalDate.of(2026, 10, 18));

        for (int i = 0; i < 7; i++) {
            verify(horarios).deFecha(persona, LUNES.plusDays(i));
        }
    }

    @Test
    @DisplayName("sin Dia 1 elegido no hay habitos del programa: no se pide ni horario ni registros, lo demas se calcula")
    void sinDiaUno() {
        when(agenda.semanaDe(persona)).thenReturn(Map.of(DayOfWeek.TUESDAY, List.of(new TramoDelDia(540, 1080))));

        SemanaDeHuecos semana = calculo.calcular(participacion(false), LUNES, HOY);

        verify(horarios, never()).deFecha(any(), any());
        verify(registros, never()).deParticipanteEntre(any(), any(), any());
        assertThat(semana.dias().get(1).ocupados()).containsExactly(new TramoDelDia(540, 1080));
        assertThat(semana.dias().get(0).conPlan()).isFalse();
        assertThat(semana.real()).isEqualTo(LoReal.NADA);
    }

    @Test
    @DisplayName("un evento al que no voy no ocupa; con Voy, Quizas o sin respuesta si; y uno que cruza la medianoche del domingo anterior deja su pedazo en el lunes")
    void eventos() {
        when(eventos.entre(persona, LUNES.minusDays(1), DOMINGO)).thenReturn(new AgendaDeEventos(LIMA,
                LUNES.minusDays(1), DOMINGO, List.of(
                evento(LUNES.minusDays(1).atTime(23, 0), LUNES.atTime(1, 0), "ASISTE"),
                evento(LUNES.atTime(10, 0), LUNES.atTime(11, 0), "NO_ASISTE"),
                evento(LUNES.atTime(12, 0), null, "QUIZAS"),
                evento(LUNES.atTime(15, 0), LUNES.atTime(16, 0), null))));
        when(horarios.deFecha(persona, LUNES)).thenReturn(new HorariosDelDia(LUNES, 10, List.of(), cuota()));

        SemanaDeHuecos semana = calculo.calcular(participacion(true), LUNES, LocalDate.of(2026, 10, 18));

        // 00:00-01:00 (la noche anterior), 12:00-13:00 (sin duracion: 60 min) y 15:00-16:00. El "no voy" no esta.
        assertThat(semana.dias().get(0).ocupados()).containsExactly(new TramoDelDia(0, 60),
                new TramoDelDia(720, 780), new TramoDelDia(900, 960));
        verify(eventos).entre(persona, LUNES.minusDays(1), DOMINGO);
    }

    @Test
    @DisplayName("las acciones con hora ocupan (30 min por defecto); las sin hora no")
    void acciones() {
        when(acciones.entre(persona, LUNES, DOMINGO)).thenReturn(List.of(
                new HoraDeUnaAccion(LUNES, LocalTime.of(11, 0), LocalTime.of(12, 30), false, null),
                new HoraDeUnaAccion(LUNES, LocalTime.of(14, 0), null, false, null),
                new HoraDeUnaAccion(LUNES, null, null, false, null)));
        when(horarios.deFecha(persona, LUNES)).thenReturn(new HorariosDelDia(LUNES, 10, List.of(), cuota()));

        SemanaDeHuecos semana = calculo.calcular(participacion(true), LUNES, LocalDate.of(2026, 10, 18));

        assertThat(semana.dias().get(0).ocupados()).containsExactly(new TramoDelDia(660, 750),
                new TramoDelDia(840, 870));
    }

    @Test
    @DisplayName("las horas ocupadas que la persona guardo con SER cuentan en el dia de la semana que corresponde")
    void agendaGuardada() {
        when(agenda.semanaDe(persona)).thenReturn(Map.of(DayOfWeek.WEDNESDAY, List.of(new TramoDelDia(540, 1080))));
        when(horarios.deFecha(persona, LUNES.plusDays(2))).thenReturn(
                new HorariosDelDia(LUNES.plusDays(2), 12, List.of(), cuota()));

        SemanaDeHuecos semana = calculo.calcular(participacion(true), LUNES, LocalDate.of(2026, 10, 18));

        assertThat(semana.dias().get(2).ocupados()).containsExactly(new TramoDelDia(540, 1080));
        assertThat(semana.dias().get(1).ocupados()).doesNotContain(new TramoDelDia(540, 1080));
    }

    @Test
    @DisplayName("lo real de los dias vividos sale de registros_habito: a tiempo, tarde y no cumplido")
    void loReal() {
        LocalDate lunes = LUNES;
        // Lunes: agua cumplida a las 08:30 (plazo 09:00) -> a tiempo; despertar a las 05:55 (su hora es 06:00) -> a tiempo.
        // Martes: agua cumplida a las 10:00 -> tarde; despertar sin cumplir -> no cumplido.
        when(registros.deParticipanteEntre(persona, LUNES, DOMINGO)).thenReturn(List.of(
                new RegistroDeHabito(agua, lunes, EstadoObligacion.COMPLETADO, false, lima(lunes, 8, 30)),
                new RegistroDeHabito(despertar, lunes, EstadoObligacion.COMPLETADO, false, lima(lunes, 5, 55)),
                new RegistroDeHabito(agua, lunes.plusDays(1), EstadoObligacion.COMPLETADO, false,
                        lima(lunes.plusDays(1), 10, 0)),
                new RegistroDeHabito(despertar, lunes.plusDays(1), EstadoObligacion.EXPIRADO, false, null)));

        SemanaDeHuecos semana = calculo.calcular(participacion(true), LUNES, HOY);

        assertThat(semana.real()).isEqualTo(new LoReal(2, 1, 1));
        // El lunes y el martes ocupan lo que tuvo registro; el miercoles (vivido) no tuvo registros: sin habitos.
        assertThat(semana.dias().get(2).conPlan()).isFalse();
        // El viernes todavia no llego: se planea con el horario.
        assertThat(semana.dias().get(4).conPlan()).isTrue();
    }

    @Test
    @DisplayName("el dia de hoy es el de la zona de la persona: a las 22:00 de Lima del domingo, la semana sigue en curso")
    void hoyEsElDeLaZona() {
        // El instante 2026-10-26T03:00Z ya es lunes en UTC y sigue siendo domingo 25 en Lima.
        LocalDate hoyEnLima = Instant.parse("2026-10-26T03:00:00Z").atZone(LIMA).toLocalDate();
        List<RegistroDeHabito> registrosDeHoy = new ArrayList<>();
        registrosDeHoy.add(new RegistroDeHabito(despertar, DOMINGO, EstadoObligacion.PENDIENTE, false, null));
        when(registros.deParticipanteEntre(persona, LUNES, DOMINGO)).thenReturn(registrosDeHoy);

        SemanaDeHuecos semana = calculo.calcular(participacion(true), LUNES, hoyEnLima);

        assertThat(hoyEnLima).isEqualTo(DOMINGO);
        // El domingo (hoy) tiene registros: ocupa solo lo que tuvo registro, no el plan entero.
        assertThat(semana.dias().get(6).ocupados()).containsExactly(new TramoDelDia(360, 390));
        // Y su despertar sin cumplir hoy no es todavia una falta.
        assertThat(semana.real().noCumplidos()).isZero();
    }

    @Test
    @DisplayName("una acción cumplida cuenta como cumplida a tiempo si no tiene hora; sin cumplir en un dia vivido, no cumplida")
    void accionesEnLoReal() {
        when(acciones.entre(persona, LUNES, DOMINGO)).thenReturn(List.of(
                new HoraDeUnaAccion(LUNES, LocalTime.of(11, 0), LocalTime.of(12, 0), true, lima(LUNES, 11, 50)),
                new HoraDeUnaAccion(LUNES, LocalTime.of(14, 0), LocalTime.of(15, 0), true, lima(LUNES, 16, 0)),
                new HoraDeUnaAccion(LUNES.plusDays(1), null, null, true, lima(LUNES.plusDays(1), 9, 0)),
                new HoraDeUnaAccion(LUNES.plusDays(1), LocalTime.of(9, 0), null, false, null)));

        SemanaDeHuecos semana = calculo.calcular(participacion(true), LUNES, HOY);

        assertThat(semana.real()).isEqualTo(new LoReal(2, 1, 1));
    }

    private static EventoDeLaAgenda evento(LocalDateTime inicia, LocalDateTime termina, String asistencia) {
        return new EventoDeLaAgenda(UUID.randomUUID(), "Evento", inicia, termina, asistencia);
    }

    private static Instant lima(LocalDate dia, int hora, int minuto) {
        return dia.atTime(hora, minuto).atZone(LIMA).toInstant();
    }
}
