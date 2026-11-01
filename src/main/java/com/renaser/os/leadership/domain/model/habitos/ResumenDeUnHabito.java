package com.renaser.os.leadership.domain.model.habitos;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Un hábito del catálogo en un día, visto de todos los alumnos (spec 029): cuántos lo cumplieron, cuántos todavía pueden y a cuántos se
 * les pasó. Lo esperado es la suma de los tres; quien no tenía nada que medir (pausa, opcional, no le tocaba) no está en ninguno.
 */
public record ResumenDeUnHabito(UUID habitoId, String titulo, String categoriaClave, String categoriaCodigo, String formacion,
                                int cumplieron, int pendientes, int sinCumplir) {

    /** Primero los hábitos con menos cumplimiento (FR-008); a igual cumplimiento, por nombre y, al final, por id (orden estable). */
    public static final Comparator<ResumenDeUnHabito> PEOR_PRIMERO = Comparator
            .comparing(ResumenDeUnHabito::cumplimiento)
            .thenComparing(ResumenDeUnHabito::titulo, porTitulo())
            .thenComparing(ResumenDeUnHabito::habitoId);

    public ResumenDeUnHabito {
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        if (cumplieron < 0 || pendientes < 0 || sinCumplir < 0 || cumplieron + pendientes + sinCumplir == 0) {
            throw new IllegalArgumentException("Un hábito que a nadie le tocó ese día no tiene resumen");
        }
    }

    public int esperados() {
        return cumplieron + pendientes + sinCumplir;
    }

    /** La fracción de los esperados que cumplió; con ella se ordena sin redondear. */
    public Fraccion cumplimiento() {
        return new Fraccion(cumplieron, esperados());
    }

    /** {@code numerador / denominador} comparable por multiplicación cruzada: sin decimales que desempaten mal. */
    public record Fraccion(int numerador, int denominador) implements Comparable<Fraccion> {

        @Override
        public int compareTo(Fraccion otra) {
            return Long.compare((long) numerador * otra.denominador, (long) otra.numerador * denominador);
        }
    }

    private static Comparator<String> porTitulo() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
        collator.setStrength(Collator.PRIMARY);
        return collator::compare;
    }
}
