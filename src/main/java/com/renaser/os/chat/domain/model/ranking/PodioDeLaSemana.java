package com.renaser.os.chat.domain.model.ranking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Quiénes salen en el podio de la semana (D-262): los tres primeros en el podio y el 4.º y el 5.º debajo.
 *
 * <ul>
 *   <li><b>Solo con puntaje.</b> Quien cerró la semana en cero no sale: un podio con ceros no felicita a nadie. Si
 *   nadie tiene puntaje, el podio está vacío y no se publica.</li>
 *   <li><b>Los empates comparten puesto</b>, como en una competencia: 96,4 · 96,4 · 90 son 1.º, 1.º y 3.º. El orden
 *   entre empatados es el de la lista que llega (el del ranking, estable por id).</li>
 *   <li><b>Con menos de tres</b> se publica con los que haya.</li>
 * </ul>
 */
public final class PodioDeLaSemana {

    public static final int EN_EL_PODIO = 3;
    public static final int MOSTRADOS = 5;

    /** Una fila del ranking, ya ordenada de mayor a menor. */
    public record Aprendiz(String nombreCompleto, BigDecimal puntaje) {
        public Aprendiz {
            Objects.requireNonNull(puntaje, "puntaje es obligatorio");
        }
    }

    /** @param lugar 1, 2 o 3 en el podio; 4 o 5 debajo, salvo empate con alguien de más arriba */
    public record Puesto(int lugar, String nombre, BigDecimal puntaje) {
    }

    private final List<Puesto> puestos;

    private PodioDeLaSemana(List<Puesto> puestos) {
        this.puestos = List.copyOf(puestos);
    }

    public static PodioDeLaSemana de(List<Aprendiz> ranking) {
        List<Aprendiz> conPuntaje = ranking.stream()
                .filter(a -> a.puntaje().signum() > 0)
                .sorted(Comparator.comparing(Aprendiz::puntaje).reversed())
                .limit(MOSTRADOS)
                .toList();
        List<Puesto> puestos = new ArrayList<>(conPuntaje.size());
        for (int i = 0; i < conPuntaje.size(); i++) {
            Aprendiz aprendiz = conPuntaje.get(i);
            boolean empataConElAnterior = i > 0 && aprendiz.puntaje().compareTo(conPuntaje.get(i - 1).puntaje()) == 0;
            int lugar = empataConElAnterior ? puestos.get(i - 1).lugar() : i + 1;
            puestos.add(new Puesto(lugar, NombreCorto.de(aprendiz.nombreCompleto()), aprendiz.puntaje()));
        }
        return new PodioDeLaSemana(puestos);
    }

    public boolean estaVacio() {
        return puestos.isEmpty();
    }

    /** Los primeros tres de la lista (o menos), en orden: el centro del podio es el primero. */
    public List<Puesto> podio() {
        return puestos.subList(0, Math.min(EN_EL_PODIO, puestos.size()));
    }

    /** El 4.º y el 5.º, que van debajo del podio, en pequeño. */
    public List<Puesto> debajo() {
        return puestos.size() <= EN_EL_PODIO ? List.of() : puestos.subList(EN_EL_PODIO, puestos.size());
    }

    public List<Puesto> todos() {
        return puestos;
    }
}
