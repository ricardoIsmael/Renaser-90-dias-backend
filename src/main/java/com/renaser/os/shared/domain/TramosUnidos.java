package com.renaser.os.shared.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Los tramos de un día ya ordenados y unidos: los que se pisan o se tocan son uno solo, así que ninguno se cruza con
 * otro y no hay huecos de cero minutos. Ordenar es O(n log n) y sacar lo libre es un solo recorrido, O(n).
 *
 * <p>El resultado no depende del orden en que llegan los tramos. Es el algoritmo de {@code AgendaOcupada} (D-160) sin
 * su tope de tramos, que es una regla del acompañante y no de la aritmética; ahora lo usan los dos.
 */
public record TramosUnidos(List<TramoDelDia> tramos) {

    public TramosUnidos {
        tramos = unir(Objects.requireNonNull(tramos, "tramos es obligatorio"));
    }

    /** Lo libre dentro de {@code [desde, hasta)}, en orden. */
    public List<TramoDelDia> libresEntre(int desde, int hasta) {
        List<TramoDelDia> libres = new ArrayList<>();
        int cursor = desde;
        for (TramoDelDia ocupado : tramos) {
            if (ocupado.hasta() <= cursor || ocupado.desde() >= hasta) {
                continue;
            }
            if (ocupado.desde() > cursor) {
                libres.add(new TramoDelDia(cursor, ocupado.desde()));
            }
            cursor = Math.max(cursor, ocupado.hasta());
        }
        if (cursor < hasta) {
            libres.add(new TramoDelDia(cursor, hasta));
        }
        return libres;
    }

    /** Lo ocupado que cae dentro de {@code [desde, hasta)}, recortado a esa franja. */
    public List<TramoDelDia> dentroDe(int desde, int hasta) {
        List<TramoDelDia> dentro = new ArrayList<>();
        for (TramoDelDia ocupado : tramos) {
            int inicio = Math.max(ocupado.desde(), desde);
            int fin = Math.min(ocupado.hasta(), hasta);
            if (inicio < fin) {
                dentro.add(new TramoDelDia(inicio, fin));
            }
        }
        return dentro;
    }

    /** Ordena y junta los que se pisan o se tocan: 09:00-12:00 y 11:00-13:00 son 09:00-13:00. */
    private static List<TramoDelDia> unir(List<TramoDelDia> tramos) {
        List<TramoDelDia> ordenados = new ArrayList<>(tramos);
        ordenados.sort(Comparator.comparingInt(TramoDelDia::desde));
        List<TramoDelDia> unidos = new ArrayList<>();
        for (TramoDelDia tramo : ordenados) {
            TramoDelDia ultimo = unidos.isEmpty() ? null : unidos.get(unidos.size() - 1);
            if (ultimo != null && tramo.desde() <= ultimo.hasta()) {
                unidos.set(unidos.size() - 1, new TramoDelDia(ultimo.desde(), Math.max(ultimo.hasta(), tramo.hasta())));
            } else {
                unidos.add(tramo);
            }
        }
        return List.copyOf(unidos);
    }
}
