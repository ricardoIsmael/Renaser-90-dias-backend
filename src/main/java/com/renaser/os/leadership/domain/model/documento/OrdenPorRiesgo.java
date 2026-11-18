package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.AprendizDelMentor;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * El orden de los aprendices de un mentor en su documento (FR-036): por riesgo, como la lista del grupo del líder —rojo, ámbar, sin
 * datos y verde—, dentro de cada color el menor porcentaje primero (sin porcentaje, al final) y después por nombre en el orden
 * alfabético castellano.
 */
public final class OrdenPorRiesgo {

    private OrdenPorRiesgo() {
    }

    public static Comparator<AprendizDelMentor> orden() {
        Collator castellano = Collator.getInstance(Locale.of("es"));
        return Comparator.comparing(AprendizDelMentor::estado)
                .thenComparing(AprendizDelMentor::porcentaje, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AprendizDelMentor::nombre, Comparator.nullsLast(castellano::compare));
    }
}
