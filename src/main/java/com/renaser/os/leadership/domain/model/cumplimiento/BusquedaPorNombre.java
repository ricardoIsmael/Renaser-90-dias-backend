package com.renaser.os.leadership.domain.model.cumplimiento;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;

/**
 * Cómo se busca y se ordena por nombre en la lista de personas del líder (spec 021): sin tildes ni mayúsculas, como
 * escribe la gente en un buscador («jose» encuentra a «José»). Pura: no sabe de quién es el nombre.
 */
public final class BusquedaPorNombre {

    private BusquedaPorNombre() {
    }

    /** Minúsculas, sin tildes y sin espacios sobrantes; vacío si no hay texto. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Cada palabra buscada tiene que estar en el nombre; un texto vacío no filtra a nadie. */
    public static boolean coincide(String nombre, String busqueda) {
        String buscado = normalizar(busqueda);
        if (buscado.isEmpty()) {
            return true;
        }
        String propio = normalizar(nombre);
        for (String palabra : buscado.split(" ")) {
            if (!propio.contains(palabra)) {
                return false;
            }
        }
        return true;
    }

    /** Por nombre sin tildes ni mayúsculas. Ante homónimos, quien llama desempata por id para que sea estable. */
    public static Comparator<String> porNombre() {
        return Comparator.comparing(BusquedaPorNombre::normalizar);
    }
}
