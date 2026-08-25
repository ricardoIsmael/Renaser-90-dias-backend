package com.renaser.os.community.domain.model.publicacion;

import java.util.regex.Pattern;

/**
 * Un archivo del carrusel de una publicacion (tabla `medias_publicacion`). Value object —
 * vive siempre dentro de una {@link Publicacion}, nunca se referencia solo.
 *
 * <p>`bucket` + `ruta` (no una URL completa): el Muro pasa a usar
 * {@code AlmacenamientoPort} igual que `phasecontracts`/`support` (CLAUDE.MD sec. 5.4.5,
 * P-03 del baseline — "ruta, no URL"). La app publicada hoy manda una URL absoluta
 * directa (wall/schema.ts:27-30); esa compatibilidad hacia atras vive en el adaptador REST
 * (`infrastructure/adapter/in/rest/publicacion`), no aca — ver CM-06.
 */
public record MediaPublicacion(String bucket, String ruta, String mime, int orden) {

    /** Default de la columna `bucket` en el SQL (V1__baseline_renaser.sql:1096). */
    public static final String BUCKET_DEFAULT = "wall";

    private static final Pattern MIME_VALIDO = Pattern.compile("^(image|video)/.+");

    public MediaPublicacion {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("El bucket del archivo no puede ser vacio");
        }
        if (ruta == null || ruta.isBlank()) {
            throw new IllegalArgumentException("La ruta del archivo no puede ser vacia");
        }
        if (mime == null || !MIME_VALIDO.matcher(mime).matches()) {
            throw new IllegalArgumentException("mime debe empezar con image/ o video/: " + mime);
        }
        if (orden < 0) {
            throw new IllegalArgumentException("orden no puede ser negativo");
        }
    }
}
