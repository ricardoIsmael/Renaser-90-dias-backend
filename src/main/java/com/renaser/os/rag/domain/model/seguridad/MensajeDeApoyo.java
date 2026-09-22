package com.renaser.os.rag.domain.model.seguridad;

import java.util.Optional;

/**
 * El texto que se le muestra a la persona cuando {@link PatronDeMalestarRepetido} se cumple: el
 * ofrecimiento de un recurso de ayuda real (la linea de salud mental del MINSA).
 *
 * <p><b>Por que es configuracion y no una constante.</b> El texto del MINSA —numero, horario,
 * alcance— <b>todavia no esta confirmado</b>, y este repo tiene una regla explicita contra rellenar
 * con supuestos lo que el dueno no confirmo. Un numero de telefono inventado es peor que no tener
 * ninguno: manda a alguien que esta mal a llamar a la nada. Asi que el texto entra por
 * {@code renaser.renasia.apoyo.mensaje} y <b>viene vacio por defecto</b>.
 *
 * <p><b>Que pasa mientras siga vacio.</b> El aviso a ADMIN/ALQUIMISTA se emite igual —un humano se
 * entera y puede actuar, que es la mitad que no depende de ningun texto— y a la persona no se le
 * muestra nada. Preferimos no decirle nada antes que decirle algo falso. Se completa la propiedad y
 * empieza a mostrarse, sin desplegar codigo.
 */
public record MensajeDeApoyo(String texto) {

    public MensajeDeApoyo {
        texto = texto == null ? "" : texto.strip();
    }

    /** El estado de hoy: la propiedad vacia. */
    public static MensajeDeApoyo sinConfigurar() {
        return new MensajeDeApoyo("");
    }

    public boolean estaConfigurado() {
        return !texto.isBlank();
    }

    /** El texto a mostrar, o vacio si nadie lo configuro todavia. */
    public Optional<String> paraMostrar() {
        return estaConfigurado() ? Optional.of(texto) : Optional.empty();
    }
}
