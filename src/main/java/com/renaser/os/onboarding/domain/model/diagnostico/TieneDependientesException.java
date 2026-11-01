package com.renaser.os.onboarding.domain.model.diagnostico;

import java.util.List;

/** Se quiso quitar una pregunta de la que dependen otras («se muestra solo si»): 409 hasta que Administración pida quitarlas también. */
public class TieneDependientesException extends IllegalStateException {

    public static final String CODIGO = "TIENE_DEPENDIENTES";

    private final transient List<Integer> dependientes;

    public TieneDependientesException(List<Integer> dependientes) {
        super("Otras preguntas dependen de ésta: quita también las que dependen de ella, o cancela");
        this.dependientes = List.copyOf(dependientes);
    }

    public List<Integer> dependientes() {
        return dependientes;
    }
}
