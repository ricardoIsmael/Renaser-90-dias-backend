package com.renaser.os.onboarding.domain.model.diagnostico;

import java.util.List;

/** Se quiso terminar el diagnóstico con preguntas obligatorias sin responder: 400 con los ids, para llevar a la persona a la primera. */
public class DiagnosticoIncompletoException extends IllegalArgumentException {

    public static final String CODIGO = "DIAGNOSTICO_INCOMPLETO";

    private final transient List<Integer> faltan;

    public DiagnosticoIncompletoException(List<Integer> faltan) {
        super("Te falta responder " + (faltan.size() == 1 ? "una pregunta obligatoria" : faltan.size() + " preguntas obligatorias"));
        this.faltan = List.copyOf(faltan);
    }

    public List<Integer> faltan() {
        return faltan;
    }
}
