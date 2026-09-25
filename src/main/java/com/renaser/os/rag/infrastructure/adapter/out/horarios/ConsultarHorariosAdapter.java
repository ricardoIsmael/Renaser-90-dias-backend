package com.renaser.os.rag.infrastructure.adapter.out.horarios;

import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Implementa {@link ConsultarHorariosPort} delegando en el contrato publico de {@code habits}
 * (D-41). Es una traduccion y nada mas: que dia es "hoy" para el aprendiz, que horario rige y
 * cuanto cupo le queda lo resuelve {@code habits}. Mismo patron que
 * {@code ConsultarAgendaHabitosAdapter}.
 */
@Component
class ConsultarHorariosAdapter implements ConsultarHorariosPort {

    private final HorarioDelDiaFinder horarioDelDiaFinder;

    ConsultarHorariosAdapter(HorarioDelDiaFinder horarioDelDiaFinder) {
        this.horarioDelDiaFinder = horarioDelDiaFinder;
    }

    @Override
    public HorariosDelDia deFecha(UserId participanteId, LocalDate fecha) {
        HorarioDelDiaFinder.HorariosDelDia dia = horarioDelDiaFinder.deFecha(participanteId, fecha);
        return new HorariosDelDia(dia.fecha(), dia.diaPrograma(),
                dia.habitos().stream().map(ConsultarHorariosAdapter::aHorarioDeHabito).toList(),
                aCuota(dia.cuota()));
    }

    private static HorarioDeHabito aHorarioDeHabito(HorarioDelDiaFinder.HorarioResuelto horario) {
        return new HorarioDeHabito(horario.habitoId(), horario.titulo(), horario.horaDisparo(), horario.horaLimite(),
                horario.personalizado(), horario.apagado(), horario.pausado(), horario.obligatorio(),
                aCambio(horario.cambioProgramado()));
    }

    private static CambioProgramado aCambio(HorarioDelDiaFinder.CambioHorarioProgramado cambio) {
        return cambio == null ? null : new CambioProgramado(cambio.horaDisparo(), cambio.horaLimite(), cambio.desde());
    }

    private static CuotaCambios aCuota(HorarioDelDiaFinder.CuotaCambiosHorario cuota) {
        return new CuotaCambios(cuota.usados(), cuota.restantes(), cuota.limite(), cuota.semanaDeAcomodoLibre());
    }
}
