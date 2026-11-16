package com.renaser.os.shared.domain;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * La ruta de un pedido sin lo que la vuelve única, para ponerla en el registro (spec 050, FR-019): {@code /api/v1/wall/3f2a…/comments}
 * queda {@code /api/v1/wall/{id}/comments}. Así una línea del registro dice <i>qué</i> se pidió sin decir <i>de quién</i>, y el
 * registro no se llena de rutas distintas por cada publicación.
 *
 * <p>Lo que la ruta trae no es de fiar (lo escribe quien hace el pedido): todo segmento que no sea un nombre corto de letras,
 * números, punto, guion o guion bajo se reemplaza también, y la ruta se corta a un largo fijo. Nada de lo que sale de aquí puede
 * partir una línea del registro.
 */
public final class PatronDeRuta {

    private static final Pattern IDENTIFICADOR = Pattern.compile(
            "[0-9]+|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9a-fA-F]{20,}");
    private static final Pattern NOMBRE_SEGURO = Pattern.compile("[A-Za-z0-9._-]{1,40}");
    private static final int SEGMENTOS_MAXIMOS = 12;
    private static final String MARCA = "{id}";

    private PatronDeRuta() {
    }

    public static String sinIds(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return "/";
        }
        return Arrays.stream(ruta.split("/", -1)).limit(SEGMENTOS_MAXIMOS)
                .map(PatronDeRuta::sinElId).collect(Collectors.joining("/"));
    }

    private static String sinElId(String segmento) {
        if (segmento.isEmpty()) {
            return segmento;
        }
        boolean esUnNombre = NOMBRE_SEGURO.matcher(segmento).matches() && !IDENTIFICADOR.matcher(segmento).matches();
        return esUnNombre ? segmento : MARCA;
    }
}
