package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.ConteoDelDia;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lo que el barrido horario tiene que hacer con UNA persona en esta corrida: qué días (re)calcular
 * y qué semanas cerrar. Lo decide {@link CierreSemanal} solo con fechas.
 *
 * @param desde            primer día a calcular (el siguiente a la última semana cerrada)
 * @param hasta            último día a calcular (ayer, o el día 90 si ya pasó)
 * @param semanasPorCerrar viernes de las semanas terminadas sin foto, de la más vieja a la más nueva
 */
public record PlanDeCierre(CalendarioDeMedicion calendario, LocalDate hoyLocal, LocalDate desde, LocalDate hasta,
                           List<LocalDate> semanasPorCerrar) {

    public PlanDeCierre {
        Objects.requireNonNull(calendario, "calendario es obligatorio");
        Objects.requireNonNull(hoyLocal, "hoyLocal es obligatorio");
        semanasPorCerrar = List.copyOf(semanasPorCerrar);
    }

    public static PlanDeCierre para(CalendarioDeMedicion calendario, LocalDate hoyLocal, LocalDate ultimaSemanaCerrada) {
        return new PlanDeCierre(calendario, hoyLocal,
                CierreSemanal.primerDiaAbierto(ultimaSemanaCerrada, calendario),
                CierreSemanal.ultimoDiaCerrado(hoyLocal, calendario),
                CierreSemanal.semanasPorCerrar(hoyLocal, ultimaSemanaCerrada, calendario));
    }

    public boolean hayDiasPorCalcular() {
        return !desde.isAfter(hasta);
    }

    /** Si esta corrida no tiene nada que hacer con esta persona (por ejemplo, justo después del cierre). */
    public boolean tieneTrabajo() {
        return hayDiasPorCalcular() || !semanasPorCerrar.isEmpty();
    }

    /**
     * Los días medidos del plan combinando lo que reportaron {@code habits} y {@code rocks}. Un día
     * medido sin nada reportado queda con conteos en cero (se guarda: "no había nada programado" es
     * un dato). Un día pausado o fuera del programa no aparece.
     */
    public Map<LocalDate, CumplimientoDelDia> diasMedidos(List<ConteoDelDia> habitos, List<ConteoDelDia> objetivos) {
        Map<LocalDate, ConteoDelDia> habitosPorFecha = porFecha(habitos);
        Map<LocalDate, ConteoDelDia> objetivosPorFecha = porFecha(objetivos);
        Map<LocalDate, CumplimientoDelDia> dias = new TreeMap<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            if (calendario.seMide(fecha)) {
                dias.put(fecha, CumplimientoDelDia.de(fecha, habitosPorFecha.get(fecha), objetivosPorFecha.get(fecha)));
            }
        }
        return dias;
    }

    private static Map<LocalDate, ConteoDelDia> porFecha(List<ConteoDelDia> conteos) {
        if (conteos == null) {
            return Map.of();
        }
        return conteos.stream().collect(Collectors.toMap(ConteoDelDia::fecha, Function.identity(), (a, b) -> a));
    }
}
