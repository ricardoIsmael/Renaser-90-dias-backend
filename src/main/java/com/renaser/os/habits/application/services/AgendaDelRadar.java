package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorarioResuelto;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorariosDelDia;
import com.renaser.os.habits.domain.model.radar.DiaDelRadar;
import com.renaser.os.habits.domain.model.radar.HorarioDelRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Arma Mi Radar con el horario de la persona (spec 019): para cada dia de la semana busca su Despertar y su Dormir
 * ({@link HorarioDelDiaFinder}, que ya resuelve la hora propia de cada dia —spec 008—, lo apagado y lo pausado) y deja que
 * {@link HorarioDelRadar} decida las horas. No autoriza: quien lo usa ya lo hizo. Una sola cuenta para tres lectores: la
 * propia persona (la semana), el registro (hoy) y las fichas (hoy).
 *
 * <p>Cada dia se resuelve con la PROXIMA fecha en que cae (hoy incluido): es la que dice como sonara su alarma semanal.
 */
@Component
class AgendaDelRadar {

    private final HorarioDelDiaFinder horarios;
    private final Clock clock;

    AgendaDelRadar(HorarioDelDiaFinder horarios, Clock clock) {
        this.horarios = horarios;
        this.clock = clock;
    }

    /** Hoy en la zona de la persona (regla 02). */
    LocalDate hoy(ZoneId zona) {
        return clock.now().atZone(zona).toLocalDate();
    }

    /** Los siete dias de la semana que se muestra. */
    PlanDeMiRadar semana(UserId participanteId, ZoneId zona, MiRadar guardado) {
        LocalDate hoy = hoy(zona);
        LocalDate lunes = HorarioDelRadar.lunesDeLaSemanaMostrada(hoy);
        List<DiaDelRadar> dias = IntStream.rangeClosed(1, 7).mapToObj(dia -> diaResuelto(participanteId, guardado, hoy,
                HorarioDelRadar.proximaFecha(dia, hoy), lunes.plusDays(dia - 1L), dia)).toList();
        return new PlanDeMiRadar(guardado.encendido(), hoy, dias);
    }

    /** Solo el dia de hoy: lo que necesitan registrar y las fichas. */
    DiaDelRadar deHoy(UserId participanteId, ZoneId zona, MiRadar guardado) {
        LocalDate hoy = hoy(zona);
        return diaResuelto(participanteId, guardado, hoy, hoy, hoy, hoy.getDayOfWeek().getValue());
    }

    private DiaDelRadar diaResuelto(UserId participanteId, MiRadar guardado, LocalDate hoy, LocalDate fechaDelHorario,
                                    LocalDate fechaMostrada, int dia) {
        HorariosDelDia delDia = horarios.deFecha(participanteId, fechaDelHorario);
        return HorarioDelRadar.planDelDia(guardado.dia(dia), fechaMostrada, horaDe(delDia, "WAKE_UP"),
                horaDe(delDia, "SLEEP"), HorarioDelRadar.esEditable(DayOfWeek.of(dia), hoy));
    }

    /**
     * La hora del habito de sistema que se pide ({@code claveSistema}): la primera que no este apagada, en pausa ni por
     * venir y tenga hora. Sin ninguna, {@code null}: ese dia no hay horario y no se inventa uno.
     */
    private static LocalTime horaDe(HorariosDelDia delDia, String claveSistema) {
        return delDia.habitos().stream()
                .filter(habito -> claveSistema.equals(habito.claveSistema()))
                .filter(AgendaDelRadar::cuenta)
                .map(HorarioResuelto::horaDisparo)
                .findFirst().orElse(null);
    }

    private static boolean cuenta(HorarioResuelto habito) {
        return !habito.apagado() && !habito.pausado() && !habito.todaviaNoLeToca() && habito.horaDisparo() != null;
    }
}
