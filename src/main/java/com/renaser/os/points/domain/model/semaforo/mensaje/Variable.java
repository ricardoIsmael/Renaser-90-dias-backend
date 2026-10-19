package com.renaser.os.points.domain.model.semaforo.mensaje;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Los datos de la persona que un texto del catálogo puede pedir entre llaves (FR-005). Un nombre entre llaves
 * que no esté acá es un error del catálogo, no un dato que falta: {@link #enTexto} lo rechaza.
 */
public enum Variable {

    NOMBRE("nombre"),
    DIA("dia"),
    FASE("fase"),
    HABITO_FLOJO("habitoFlojo"),
    HABITO_FUERTE("habitoFuerte"),
    FALTAN("faltan");

    private static final Pattern ENTRE_LLAVES = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern LLAVE_SUELTA = Pattern.compile("[{}]");

    private final String clave;

    Variable(String clave) {
        this.clave = clave;
    }

    /** El texto tal como aparece en el catálogo, por ejemplo {@code {nombre}}. */
    public String marcador() {
        return "{" + clave + "}";
    }

    /**
     * Las variables que pide un texto.
     *
     * @throws IllegalArgumentException si el texto tiene una llave suelta o una variable que no existe: un
     *                                  catálogo así dejaría llaves a la vista de la persona
     */
    public static Set<Variable> enTexto(String texto) {
        Set<Variable> pedidas = EnumSet.noneOf(Variable.class);
        Matcher llaves = ENTRE_LLAVES.matcher(texto);
        while (llaves.find()) {
            pedidas.add(porClave(llaves.group(1)));
        }
        if (LLAVE_SUELTA.matcher(ENTRE_LLAVES.matcher(texto).replaceAll("")).find()) {
            throw new IllegalArgumentException("El texto tiene una llave suelta: " + texto);
        }
        return pedidas;
    }

    private static Variable porClave(String clave) {
        for (Variable variable : values()) {
            if (variable.clave.equals(clave)) {
                return variable;
            }
        }
        throw new IllegalArgumentException("Variable desconocida en el texto: {" + clave + "}");
    }
}
