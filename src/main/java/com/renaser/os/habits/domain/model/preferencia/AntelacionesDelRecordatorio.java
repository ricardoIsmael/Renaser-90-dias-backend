package com.renaser.os.habits.domain.model.preferencia;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * TODAS las antelaciones que eligió el aprendiz para el recordatorio de un hábito (V81, D-217,
 * 2026-09-28): «30 min antes y a la hora» son dos avisos, y {@code minutos_recordatorio} es un solo
 * número. Con solo ese número, un teléfono nuevo o reinstalado volvía con un aviso (E-398).
 *
 * <p><b>Convive con el número de siempre, que no cambia de significado.</b> El APK de producción manda y
 * lee {@code reminderMinutesBefore}: sigue siendo la antelación MÁS TEMPRANA (el máximo del conjunto).
 * El conjunto es {@code null} cuando no se conoce —filas anteriores a V81— y la app lo trata como
 * «desconocido», sin pisar lo que el teléfono tenga guardado.
 *
 * <p>Los valores válidos son los de {@link AntelacionDelRecordatorio}: no hay topes nuevos.
 */
public final class AntelacionesDelRecordatorio {

    private AntelacionesDelRecordatorio() {
    }

    /**
     * Sin repetidos y de la más temprana a la más tardía (de mayor a menor), que es el orden en que
     * suenan. Vacío si llegó vacío.
     *
     * @throws IllegalArgumentException si algún valor es nulo o está fuera del rango
     */
    public static List<Integer> normalizar(Collection<Integer> antelaciones) {
        Objects.requireNonNull(antelaciones, "antelaciones es obligatorio");
        for (Integer minutos : antelaciones) {
            if (minutos == null) {
                throw new IllegalArgumentException("Las antelaciones del recordatorio no pueden tener valores vacios");
            }
            AntelacionDelRecordatorio.requireDentroDelRango(minutos);
        }
        return antelaciones.stream().distinct().sorted(Comparator.reverseOrder()).toList();
    }

    /**
     * El conjunto que queda cuando llega SOLO el número (el APK viejo, el acompañante, una pantalla que
     * solo cambia la hora):
     * <ul>
     *   <li>recordatorio apagado, o sin minutos → {@code null};</li>
     *   <li>el número es la más temprana del conjunto guardado → el conjunto no cambia (tocar la hora no
     *   borra «30 min y a la hora»);</li>
     *   <li>no había conjunto y el número es el mismo de antes → sigue sin conocerse ({@code null});</li>
     *   <li>si no → {@code [minutos]}: la persona eligió otra antelación y es la única que se sabe.</li>
     * </ul>
     */
    public static List<Integer> trasMinutosSueltos(List<Integer> conjuntoAnterior, Integer minutosAnteriores,
                                                   boolean activo, Integer minutosNuevos) {
        if (!activo || minutosNuevos == null) {
            return null;
        }
        if (conjuntoAnterior != null && !conjuntoAnterior.isEmpty()
                && conjuntoAnterior.getFirst().equals(minutosNuevos)) {
            return conjuntoAnterior;
        }
        if (conjuntoAnterior == null && minutosNuevos.equals(minutosAnteriores)) {
            return null;
        }
        return List.of(minutosNuevos);
    }
}
