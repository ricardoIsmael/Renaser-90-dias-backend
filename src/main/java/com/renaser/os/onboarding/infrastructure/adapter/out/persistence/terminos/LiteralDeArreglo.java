package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.terminos;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Arma el literal de un {@code text[]} de PostgreSQL ({@code {"uno","dos"}}) con cada elemento entrecomillado y con la barra y la comilla escapadas. Una
 * cláusula legal puede traer comas, comillas, llaves y saltos de línea: sin esto, el texto del usuario cambiaría el arreglo.
 */
final class LiteralDeArreglo {

    private LiteralDeArreglo() {
    }

    static String deTextos(List<String> textos) {
        return textos.stream().map(LiteralDeArreglo::elemento).collect(Collectors.joining(",", "{", "}"));
    }

    private static String elemento(String texto) {
        return "\"" + texto.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
