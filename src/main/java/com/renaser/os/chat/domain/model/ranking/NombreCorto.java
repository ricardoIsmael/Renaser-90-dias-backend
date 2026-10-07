package com.renaser.os.chat.domain.model.ranking;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Cómo aparece una persona en el podio (D-262): su primer nombre y la inicial de su apellido, «Liz M.».
 *
 * <p><b>Qué palabra es el apellido.</b> La cuenta guarda un solo {@code nombre_completo}, con los nombres antes
 * que los apellidos (lo pide la pantalla de alta, igual que supone {@code PrimerNombre}). Sin separar nombres de
 * apellidos, se toma (sin contar «de», «del», «la»…):
 * <ul>
 *   <li>con 2 o 3 palabras, la segunda: «Liz Mendoza» y «Liz Mendoza Ruiz» son «Liz M.»;</li>
 *   <li>con 4 o más, la penúltima: «Liz Mariela Mendoza Ruiz» es «Liz M.» (dos nombres y dos apellidos).</li>
 * </ul>
 * Con tres palabras es ambiguo («María José Ñahui» saldría «María J.»): se eligió la lectura de un nombre y dos
 * apellidos, la forma más común del padrón. Supuesto técnico, a confirmar con el dueño.
 */
public final class NombreCorto {

    private static final Locale ESPANOL = Locale.forLanguageTag("es");
    private static final Set<String> PARTICULAS = Set.of("de", "del", "la", "las", "los", "y", "da", "das", "do",
            "dos", "di", "van", "von");
    /** Quien no tiene nombre legible igual ocupa su puesto. */
    static final String SIN_NOMBRE = "Aprendiz";

    private NombreCorto() {
    }

    public static String de(String nombreCompleto) {
        List<String> palabras = palabrasDe(nombreCompleto);
        if (palabras.isEmpty()) {
            return SIN_NOMBRE;
        }
        String nombre = conMayuscula(palabras.get(0));
        if (palabras.size() == 1) {
            return nombre;
        }
        int apellido = palabras.size() >= 4 ? palabras.size() - 2 : 1;
        return nombre + " " + inicial(palabras.get(apellido)) + ".";
    }

    private static List<String> palabrasDe(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return List.of();
        }
        return Stream.of(nombreCompleto.strip().split("\\s+"))
                .filter(palabra -> !PARTICULAS.contains(palabra.toLowerCase(ESPANOL)))
                .toList();
    }

    private static String conMayuscula(String palabra) {
        int primera = palabra.codePointAt(0);
        return Character.toString(primera).toUpperCase(ESPANOL) + palabra.substring(Character.charCount(primera));
    }

    private static String inicial(String palabra) {
        return Character.toString(palabra.codePointAt(0)).toUpperCase(ESPANOL);
    }
}
