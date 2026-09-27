package com.renaser.os.community.domain.model.celula;

import java.util.Locale;
import java.util.Set;

/**
 * La imagen que alguien sube para que sea la foto de un grupo (D-212), antes de prepararla. Acota lo que
 * se acepta, sin mirar todavía el contenido: eso lo hace quien la prepara, que la lee de verdad.
 *
 * <p><b>Tipos:</b> JPEG y PNG, lo que el servidor sabe leer sin librerías aparte. La app manda JPEG (el
 * selector de fotos la recorta a 512 px y la reencodea). <b>Peso:</b> hasta {@link #PESO_MAXIMO} bytes;
 * la de la app pesa unos 100 KB, así que el tope solo frena lo que no viene de ella.
 */
public record FotoSubidaDelGrupo(byte[] contenido, String tipo) {

    /** 2 MB. Mismo tope que {@code spring.servlet.multipart.max-file-size}. */
    public static final int PESO_MAXIMO = 2 * 1024 * 1024;

    private static final Set<String> TIPOS_ADMITIDOS = Set.of("image/jpeg", "image/png");

    public FotoSubidaDelGrupo {
        if (contenido == null || contenido.length == 0) {
            throw new IllegalArgumentException("La foto llegó vacía");
        }
        if (contenido.length > PESO_MAXIMO) {
            throw new IllegalArgumentException("La foto pesa más de 2 MB");
        }
        String limpio = tipo == null ? "" : tipo.strip().toLowerCase(Locale.ROOT);
        if (!TIPOS_ADMITIDOS.contains(limpio)) {
            throw new IllegalArgumentException("La foto tiene que ser JPEG o PNG");
        }
        tipo = limpio;
    }
}
