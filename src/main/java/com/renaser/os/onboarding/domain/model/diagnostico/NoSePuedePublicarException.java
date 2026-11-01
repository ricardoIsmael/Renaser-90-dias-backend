package com.renaser.os.onboarding.domain.model.diagnostico;

import java.util.List;

/** El borrador no se puede publicar todavía: 400 con los motivos, para decírselos a Administración uno por uno. */
public class NoSePuedePublicarException extends IllegalArgumentException {

    public static final String CODIGO = "NO_SE_PUEDE_PUBLICAR";

    private final transient List<String> motivos;

    public NoSePuedePublicarException(List<String> motivos) {
        super(motivos.isEmpty() ? "No se puede publicar" : motivos.get(0));
        this.motivos = List.copyOf(motivos);
    }

    public List<String> motivos() {
        return motivos;
    }
}
