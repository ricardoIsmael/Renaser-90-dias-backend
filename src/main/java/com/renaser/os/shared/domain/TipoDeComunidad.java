package com.renaser.os.shared.domain;

import java.util.Arrays;

/**
 * Los tipos de comunidad de una formación (spec 024, D-301): las cuatro clases de espacio de la Tribu. Cada formación los tiene prendidos o apagados
 * (columna {@code formaciones.tipos_de_comunidad}, V138); apagar uno lo esconde de toda la formación sin borrar sus mensajes.
 *
 * <p>Un tipo nuevo es un valor más de este enum y de la restricción de esa columna; las reglas de cada tipo (quién escribe a quién, quién modera, qué avisa) no
 * cambian por estar dentro de una formación.
 */
public enum TipoDeComunidad {

    /** La comunidad general de la formación («Formación Renaser Global» en la Escuela): una por formación, a la que entra toda persona nueva. */
    GENERAL,

    /** El grupo con su mentor («&lt;Mentor&gt; y sus aprendices»). */
    GRUPO,

    /** El soporte de cada aprendiz con el equipo. */
    SOPORTE,

    /** Los chats 1 a 1. */
    DIRECTO;

    public String codigo() {
        return name();
    }

    /**
     * @throws IllegalArgumentException si el código no es de un tipo (400)
     */
    public static TipoDeComunidad desde(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El tipo de comunidad es obligatorio.");
        }
        String limpio = codigo.trim();
        return Arrays.stream(values())
                .filter(tipo -> tipo.name().equalsIgnoreCase(limpio))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe el tipo de comunidad «" + limpio + "»."));
    }
}
