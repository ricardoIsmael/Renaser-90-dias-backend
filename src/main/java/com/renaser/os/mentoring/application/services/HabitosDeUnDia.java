package com.renaser.os.mentoring.application.services;

import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorarioResuelto;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorariosDelDia;
import com.renaser.os.habits.api.RegistrosDeHabitoFinder.RegistroDeHabito;
import com.renaser.os.mentoring.domain.model.huecos.Cumplible;
import com.renaser.os.mentoring.domain.model.huecos.HabitoConHora;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Los hábitos de UN día de la persona, vistos para los huecos: cuáles ocupan tiempo y cuáles ya pasaron para
 * compararlos con lo real (FR-003).
 *
 * <p><b>Hoy y los días ya vividos:</b> cuentan los hábitos que de verdad le tocaron, que son los de
 * {@code registros_habito}; la hora de cada uno es la que muestra Planificar para esa fecha. <b>Los días por venir</b>
 * (y hoy, mientras el generador no creó sus registros): los que le tocarán según su plan, que son los que el horario
 * resuelto no marca como apagados, pausados ni «todavía no le toca», y fuera del programa (antes del Día 1 o después
 * del 90) ninguno (FR-011). Un hábito sin hora fija no ocupa nada (FR-004).
 *
 * @param ocupan    los que ocupan tiempo ese día
 * @param cumplibles los registros del día, para lo real; vacío en un día planificado
 */
record HabitosDeUnDia(List<HabitoConHora> ocupan, List<Cumplible> cumplibles) {

    private static final int PRIMER_DIA_DEL_PROGRAMA = 1;
    private static final int ULTIMO_DIA_DEL_PROGRAMA = 90;

    static HabitosDeUnDia de(LocalDate hoy, HorariosDelDia horarios, List<RegistroDeHabito> registros) {
        boolean sePlanea = horarios.fecha().isAfter(hoy) || (horarios.fecha().equals(hoy) && registros.isEmpty());
        return sePlanea ? new HabitosDeUnDia(segunElPlan(horarios), List.of()) : segunLosRegistros(horarios, registros);
    }

    static HabitosDeUnDia ninguno() {
        return new HabitosDeUnDia(List.of(), List.of());
    }

    private static List<HabitoConHora> segunElPlan(HorariosDelDia horarios) {
        if (horarios.diaPrograma() < PRIMER_DIA_DEL_PROGRAMA || horarios.diaPrograma() > ULTIMO_DIA_DEL_PROGRAMA) {
            return List.of();
        }
        return horarios.habitos().stream()
                .filter(habito -> !habito.apagado() && !habito.pausado() && !habito.todaviaNoLeToca())
                .flatMap(habito -> conHora(habito).stream())
                .toList();
    }

    private static HabitosDeUnDia segunLosRegistros(HorariosDelDia horarios, List<RegistroDeHabito> registros) {
        Map<UUID, HorarioResuelto> porHabito = horarios.habitos().stream()
                .collect(Collectors.toMap(HorarioResuelto::habitoId, Function.identity(), (a, b) -> a));
        List<HabitoConHora> ocupan = registros.stream()
                .map(registro -> porHabito.get(registro.habitoId()))
                .filter(habito -> habito != null)
                .flatMap(habito -> conHora(habito).stream())
                .toList();
        List<Cumplible> cumplibles = registros.stream().map(registro -> cumplible(horarios.fecha(), registro,
                porHabito.get(registro.habitoId()))).toList();
        return new HabitosDeUnDia(ocupan, cumplibles);
    }

    private static Cumplible cumplible(LocalDate fecha, RegistroDeHabito registro, HorarioResuelto horario) {
        boolean cumplido = registro.estado() == EstadoObligacion.COMPLETADO;
        return new Cumplible(fecha, horario == null ? null : horario.horaDisparo(),
                horario == null ? null : horario.horaLimite(), cumplido ? registro.completadoEn() : null,
                registro.opcional());
    }

    private static Optional<HabitoConHora> conHora(HorarioResuelto habito) {
        return habito.horaDisparo() == null ? Optional.empty()
                : Optional.of(new HabitoConHora(habito.claveSistema(), habito.horaDisparo(), habito.titulo()));
    }
}
