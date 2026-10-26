package com.renaser.os.users.domain.model.rolpersonalizado;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Un nombre de rol tal como se compara para decidir si se repite: sin mayúsculas, sin tildes y con los espacios
 * colapsados. «Mentor Junior», «mentor  junior» y «Méntor junior» son el mismo nombre (spec 002, «Nombre repetido o
 * igual a un rol base»).
 */
final class NombreComparable {

    private NombreComparable() {
    }

    static String de(String nombre) {
        if (nombre == null) {
            return "";
        }
        String descompuesto = Normalizer.normalize(nombre.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return descompuesto.replaceAll("\\p{M}", "").replaceAll("\\s+", " ");
    }
}
