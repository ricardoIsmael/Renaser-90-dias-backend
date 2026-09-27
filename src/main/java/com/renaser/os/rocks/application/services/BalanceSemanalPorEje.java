package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.RocasDelAprendizFinder.BalanceDelEje;
import com.renaser.os.rocks.application.ports.in.dashboard.ConsultarDashboardRocasUseCase.DashboardRocas;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lo planificado y lo completado POR EJE en los dias ya terminados de la semana de programa (D-177,
 * {@code consultar_desvio_de_la_semana} del acompanante). La grilla del dashboard cuenta por dia y no
 * por eje, y "Cuerpo 0 de 3" es justo lo que la persona necesita escuchar para ver donde se esta
 * quedando.
 *
 * <p><b>Solo dias terminados.</b> Hoy todavia se puede completar, y contarlo como no hecho a las 10 de
 * la manana seria un desvio que no existe.
 *
 * <p>Una lectura por dia (a lo sumo seis, apoyadas en {@code rocas_diarias_dia_idx}; once en la semana
 * 13 de quien empezo un domingo, que suma los dias de la que seria la 14, D-203): no hay consulta por
 * rango en {@code LoadRocaDiariaPort} y no se agrega una para esto. La autorizacion ya la hizo el
 * dashboard, que se consulta antes.
 */
@Component
class BalanceSemanalPorEje {

    private final LoadRocaDiariaPort loadRocaDiariaPort;

    BalanceSemanalPorEje(LoadRocaDiariaPort loadRocaDiariaPort) {
        this.loadRocaDiariaPort = loadRocaDiariaPort;
    }

    /** Los tres ejes siempre, en el orden de {@link EjeObjetivo}; en cero si no hubo nada. */
    List<BalanceDelEje> deDiasTerminados(UserId aprendizId, DashboardRocas tablero, LocalDate hoy) {
        int[] planificadas = new int[EjeObjetivo.values().length];
        int[] completadas = new int[EjeObjetivo.values().length];
        for (LocalDate fecha : diasTerminados(tablero, hoy)) {
            for (RocaDiaria roca : loadRocaDiariaPort.deParticipanteYFecha(aprendizId, fecha)) {
                planificadas[roca.eje().ordinal()]++;
                completadas[roca.eje().ordinal()] += roca.completada() ? 1 : 0;
            }
        }
        List<BalanceDelEje> balance = new ArrayList<>();
        Arrays.stream(EjeObjetivo.values()).forEach(eje -> balance.add(
                new BalanceDelEje(eje.name(), planificadas[eje.ordinal()], completadas[eje.ordinal()])));
        return List.copyOf(balance);
    }

    /** Los dias de la semana que ya terminaron; ninguno sin Dia 1 elegido, porque la semana va sin fechas (D-203). */
    private static List<LocalDate> diasTerminados(DashboardRocas tablero, LocalDate hoy) {
        if (tablero.inicioSemana() == null) {
            return List.of();
        }
        List<LocalDate> dias = new ArrayList<>();
        for (LocalDate fecha = tablero.inicioSemana(); fecha.isBefore(hoy) && !fecha.isAfter(tablero.finSemana());
             fecha = fecha.plusDays(1)) {
            dias.add(fecha);
        }
        return dias;
    }
}
