package com.renaser.os.leadership.domain.model.reporte;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Cómo se ordenan los mentores en el reporte del período (SDD 002, RL-23).
 *
 * <p>Por el cumplimiento de sus aprendices, de mayor a menor, con el valor SIN redondear (el redondeo
 * es solo de pantalla). <b>Quien no tiene muestra queda fuera del orden</b> y se nombra aparte: ponerlo
 * último con un cero diría que le fue peor que a todos, y no se sabe.
 */
public final class OrdenDelReporte {

    private static final Collator ALFABETICO = Collator.getInstance(Locale.forLanguageTag("es"));

    private OrdenDelReporte() {
    }

    /**
     * @param valor  el número por el que se ordena; null = sin muestra
     * @param nombre desempata, y ordena la lista de los que no entran
     */
    public static <T> Clasificacion<T> clasificar(List<T> entradas, Function<T, BigDecimal> valor,
                                                  Function<T, String> nombre) {
        Comparator<T> porNombre = Comparator.comparing(e -> nombreOVacio(nombre.apply(e)), ALFABETICO);
        List<T> conMuestra = entradas.stream()
                .filter(e -> valor.apply(e) != null)
                .sorted(Comparator.comparing(valor, Comparator.reverseOrder()).thenComparing(porNombre))
                .toList();
        List<T> sinMuestra = entradas.stream().filter(e -> valor.apply(e) == null).sorted(porNombre).toList();
        return new Clasificacion<>(conMuestra, sinMuestra);
    }

    private static String nombreOVacio(String nombre) {
        return nombre == null ? "" : nombre;
    }

    public record Clasificacion<T>(List<T> ordenados, List<T> sinMuestra) {
    }
}
