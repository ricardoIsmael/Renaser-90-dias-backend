package com.renaser.os.onboarding.domain.model.diagnostico;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee y escribe los dos únicos formatos JSON que guardan las respuestas del diagnóstico en {@code valor_json}: una lista de textos
 * ({@code ["a","b"]}, las casillas) y un objeto de texto a entero ({@code {"fila":4}}, la cuadrícula). El dominio no usa Jackson (regla de la
 * arquitectura), y estos dos formatos son lo bastante chicos como para leerlos a mano. Un texto que no es de ese formato es una
 * {@link IllegalArgumentException} (400), nunca un valor a medias.
 */
public final class ValoresJson {

    private ValoresJson() {
    }

    public static List<String> lista(String json) {
        Lector l = new Lector(json);
        l.espacios();
        l.esperar('[');
        List<String> valores = new ArrayList<>();
        l.espacios();
        if (l.sigue(']')) {
            l.fin();
            return valores;
        }
        do {
            l.espacios();
            valores.add(l.texto());
            l.espacios();
        } while (l.coma());
        l.esperar(']');
        l.fin();
        return valores;
    }

    public static Map<String, Integer> objetoDeEnteros(String json) {
        Lector l = new Lector(json);
        l.espacios();
        l.esperar('{');
        Map<String, Integer> valores = new LinkedHashMap<>();
        l.espacios();
        if (l.sigue('}')) {
            l.fin();
            return valores;
        }
        do {
            l.espacios();
            String clave = l.texto();
            l.espacios();
            l.esperar(':');
            l.espacios();
            valores.put(clave, l.entero());
            l.espacios();
        } while (l.coma());
        l.esperar('}');
        l.fin();
        return valores;
    }

    public static String deLista(List<String> valores) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < valores.size(); i++) {
            sb.append(i == 0 ? "" : ",").append(entrecomillado(valores.get(i)));
        }
        return sb.append(']').toString();
    }

    public static String deObjeto(Map<String, Integer> valores) {
        StringBuilder sb = new StringBuilder("{");
        boolean primero = true;
        for (Map.Entry<String, Integer> e : valores.entrySet()) {
            sb.append(primero ? "" : ",").append(entrecomillado(e.getKey())).append(':').append(e.getValue());
            primero = false;
        }
        return sb.append('}').toString();
    }

    private static String entrecomillado(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static final class Lector {
        private final String s;
        private int i;

        Lector(String json) {
            if (json == null) {
                throw new IllegalArgumentException("La respuesta no tiene el formato esperado");
            }
            this.s = json;
        }

        void espacios() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        boolean sigue(char c) {
            espacios();
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        boolean coma() {
            return sigue(',');
        }

        void esperar(char c) {
            if (!sigue(c)) {
                throw malFormado();
            }
        }

        void fin() {
            espacios();
            if (i != s.length()) {
                throw malFormado();
            }
        }

        String texto() {
            if (i >= s.length() || s.charAt(i) != '"') {
                throw malFormado();
            }
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    sb.append(escapado());
                } else {
                    sb.append(c);
                }
            }
            throw malFormado();
        }

        private char escapado() {
            if (i >= s.length()) {
                throw malFormado();
            }
            char c = s.charAt(i++);
            return switch (c) {
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> unicode();
                case '"', '\\', '/' -> c;
                default -> throw malFormado();
            };
        }

        private char unicode() {
            if (i + 4 > s.length()) {
                throw malFormado();
            }
            try {
                char c = (char) Integer.parseInt(s.substring(i, i + 4), 16);
                i += 4;
                return c;
            } catch (NumberFormatException e) {
                throw malFormado();
            }
        }

        int entero() {
            int inicio = i;
            if (i < s.length() && s.charAt(i) == '-') {
                i++;
            }
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            try {
                return Integer.parseInt(s.substring(inicio, i));
            } catch (NumberFormatException e) {
                throw malFormado();
            }
        }

        private IllegalArgumentException malFormado() {
            return new IllegalArgumentException("La respuesta no tiene el formato esperado");
        }
    }
}
