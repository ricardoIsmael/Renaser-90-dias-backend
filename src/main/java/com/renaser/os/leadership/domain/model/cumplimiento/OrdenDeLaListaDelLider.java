package com.renaser.os.leadership.domain.model.cumplimiento;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * El orden de la lista de un grupo para el líder (spec 005, FR-007): por color —rojo, amarillo, sin datos, verde y
 * no se mide—, dentro de cada color el menor porcentaje primero (sin porcentaje, al final), después por nombre y, ante
 * homónimos, por id para que sea estable.
 *
 * <p>«Por nombre» es el orden alfabético castellano: comparando {@code String} a secas, una «Ángela» quedaría
 * después de «Zoila». Se crea un {@code Collator} por comparador y no uno compartido entre hilos.
 */
public final class OrdenDeLaListaDelLider {

    private OrdenDeLaListaDelLider() {
    }

    public static Comparator<PersonaDeLaLista> orden() {
        Collator castellano = Collator.getInstance(Locale.of("es"));
        return Comparator.comparing(PersonaDeLaLista::color)
                .thenComparing(PersonaDeLaLista::porcentaje, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PersonaDeLaLista::nombre, Comparator.nullsLast(castellano::compare))
                .thenComparing(PersonaDeLaLista::personaId);
    }
}
