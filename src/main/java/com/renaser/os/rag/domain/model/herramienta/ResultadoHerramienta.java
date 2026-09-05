package com.renaser.os.rag.domain.model.herramienta;

/**
 * Lo que devuelve ejecutar una herramienta. Sellada para que el compilador obligue a contemplar
 * el fallo: una herramienta que falla NO es una excepcion que sube por el stack, es un resultado
 * que el modelo tiene que poder leer y explicarle a la persona ("no encontre ese habito").
 *
 * <p>Es el mismo criterio que {@code AccessDecision} en el diseno de autorizacion (CLAUDE.MD
 * seccion 5.3.4): variantes cerradas, {@code switch} exhaustivo, nada de {@code null} como
 * tercer estado.
 *
 * <p>El {@code contenido} viaja como texto porque es lo que vuelve al modelo, no a una pantalla:
 * se escribe para ser leido por el asistente y repetido en sus palabras.
 */
public sealed interface ResultadoHerramienta {

    static ResultadoHerramienta exito(String contenido) {
        return new Exito(contenido);
    }

    static ResultadoHerramienta fallo(String motivo) {
        return new Fallo(motivo);
    }

    record Exito(String contenido) implements ResultadoHerramienta {
    }

    /**
     * {@code motivo} tiene que ser apto para mostrarse: el modelo lo va a parafrasear delante del
     * aprendiz. Nunca un stack trace, nunca un id interno, nunca el mensaje crudo de una
     * excepcion (regla de logging: el detalle va al log, no al usuario).
     */
    record Fallo(String motivo) implements ResultadoHerramienta {
    }
}
