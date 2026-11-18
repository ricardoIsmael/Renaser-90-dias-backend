package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * Lo mínimo para escribir el HTML del documento sin que un texto de una persona pueda salir de su lugar: {@link #e} es la ÚNICA puerta por
 * la que entra al documento un texto que no es nuestro (spec 056, FR-035 y FR-036). Todo lo demás son piezas que reciben HTML ya escapado.
 */
final class Html {

    private Html() {
    }

    /** Un texto de una persona (nombre, lo que escribió, un grupo) listo para ir dentro de una etiqueta o de un atributo. Null es vacío. */
    static String e(Object texto) {
        return texto == null ? "" : HtmlUtils.htmlEscape(String.valueOf(texto), "UTF-8");
    }

    /** Una sección con su nombre para ordenarla y comprobarla, y su título; {@code contenido} ya viene escapado. */
    static String seccion(String nombre, String titulo, String contenido) {
        return seccion(nombre, "", titulo, contenido);
    }

    static String seccion(String nombre, String clase, String titulo, String contenido) {
        String atributoDeClase = clase.isEmpty() ? "" : " class=\"" + clase + "\"";
        return "<section" + atributoDeClase + " data-seccion=\"" + nombre + "\"><h2>" + e(titulo) + "</h2>" + contenido + "</section>\n";
    }

    static String parrafo(String contenido) {
        return "<p>" + contenido + "</p>";
    }

    /** Una tabla con su encabezado; las celdas ya vienen escapadas. */
    static String tabla(List<String> encabezados, List<List<String>> filas) {
        StringBuilder tabla = new StringBuilder("<table><thead><tr>");
        encabezados.forEach(encabezado -> tabla.append("<th scope=\"col\">").append(encabezado).append("</th>"));
        tabla.append("</tr></thead><tbody>");
        for (List<String> fila : filas) {
            tabla.append("<tr>");
            fila.forEach(celda -> tabla.append("<td>").append(celda).append("</td>"));
            tabla.append("</tr>");
        }
        return tabla.append("</tbody></table>").toString();
    }

    static String lista(List<String> items) {
        StringBuilder lista = new StringBuilder("<ul>");
        items.forEach(item -> lista.append("<li>").append(item).append("</li>"));
        return lista.append("</ul>").toString();
    }
}
