package com.renaser.os.mentoring.domain.model.huecos;

import com.renaser.os.shared.domain.TramoDelDia;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Los tramos que se repiten libres en la semana (FR-016): los que están libres en al menos
 * {@link ReglasDeHuecos#DIAS_PARA_QUE_SE_REPITA} días, miden al menos el mínimo útil y dicen en qué días se apoyan.
 *
 * <p>Solo cuentan los días con plan (un día en blanco no es «tiempo libre» calculado) y los huecos que cada día ya
 * lista. Con menos de 3 días con plan no sale ninguno. Se recorre una vez la semana partida en los cortes de todos los
 * huecos: cada pedazo tiene el conjunto de días en que está entero libre, y los pedazos seguidos con los mismos días
 * se juntan. Así cada tramo que sale está libre entero en todos los días que nombra.
 */
public final class HuecosQueSeRepiten {

    private HuecosQueSeRepiten() {
    }

    public static List<HuecoQueSeRepite> de(List<DiaDeHuecos> semana) {
        List<DiaDeHuecos> conPlan = semana.stream().filter(DiaDeHuecos::conPlan).toList();
        if (conPlan.size() < ReglasDeHuecos.DIAS_PARA_QUE_SE_REPITA) {
            return List.of();
        }
        List<Integer> cortes = cortesDe(conPlan);
        List<HuecoQueSeRepite> repetidos = new ArrayList<>();
        Racha racha = null;
        for (int i = 0; i + 1 < cortes.size(); i++) {
            int desde = cortes.get(i);
            int hasta = cortes.get(i + 1);
            List<Integer> libres = diasLibresEntre(conPlan, desde, hasta);
            if (racha != null && racha.sigueConLosMismosDias(libres)) {
                racha = racha.hasta(hasta);
                continue;
            }
            racha = cerrar(racha, repetidos);
            racha = libres.size() >= ReglasDeHuecos.DIAS_PARA_QUE_SE_REPITA ? new Racha(desde, hasta, libres) : null;
        }
        cerrar(racha, repetidos);
        return List.copyOf(repetidos);
    }

    private static List<Integer> cortesDe(List<DiaDeHuecos> dias) {
        TreeSet<Integer> cortes = new TreeSet<>();
        for (DiaDeHuecos dia : dias) {
            for (TramoDelDia hueco : dia.huecos()) {
                cortes.add(hueco.desde());
                cortes.add(hueco.hasta());
            }
        }
        return List.copyOf(cortes);
    }

    private static List<Integer> diasLibresEntre(List<DiaDeHuecos> dias, int desde, int hasta) {
        return dias.stream()
                .filter(dia -> dia.huecos().stream().anyMatch(hueco -> hueco.desde() <= desde && hueco.hasta() >= hasta))
                .map(dia -> dia.fecha().getDayOfWeek().getValue())
                .sorted()
                .toList();
    }

    /** Suma la racha a la lista si mide lo mínimo; devuelve {@code null} para seguir sin racha abierta. */
    private static Racha cerrar(Racha racha, List<HuecoQueSeRepite> repetidos) {
        if (racha != null && racha.minutos() >= ReglasDeHuecos.MINUTOS_DEL_HUECO_UTIL) {
            repetidos.add(new HuecoQueSeRepite(new TramoDelDia(racha.desde(), racha.hasta()), racha.dias()));
        }
        return null;
    }

    /** Pedazos seguidos de la semana libres en los mismos días. */
    private record Racha(int desde, int hasta, List<Integer> dias) {

        boolean sigueConLosMismosDias(List<Integer> libres) {
            return dias.equals(libres);
        }

        Racha hasta(int nuevoFin) {
            return new Racha(desde, nuevoFin, dias);
        }

        int minutos() {
            return hasta - desde;
        }
    }
}
