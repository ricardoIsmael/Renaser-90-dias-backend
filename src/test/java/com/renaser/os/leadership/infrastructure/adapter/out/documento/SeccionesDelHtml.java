package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Las secciones de un documento, en el orden en que aparecen: cada una lleva {@code data-seccion="nombre"}. */
final class SeccionesDelHtml {

    private static final Pattern SECCION = Pattern.compile("data-seccion=\"([^\"]+)\"");

    private SeccionesDelHtml() {
    }

    static List<String> de(String html) {
        List<String> secciones = new ArrayList<>();
        Matcher encontradas = SECCION.matcher(html);
        while (encontradas.find()) {
            secciones.add(encontradas.group(1));
        }
        return secciones;
    }
}
