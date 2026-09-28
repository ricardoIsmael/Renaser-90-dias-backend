package com.renaser.os.onboarding.domain.model.caja;

import java.util.Arrays;
import java.util.Optional;

/** Por qué una caja quedó con problema (spec §4). Viaja tal cual por la API y se guarda en el detalle. */
public enum MotivoProblema {
    PERDIDA,
    DANADA,
    DEVUELTA,
    OTRO;

    /** @throws IllegalArgumentException si no es uno de los cuatro (400) */
    public static MotivoProblema de(String valor) {
        return Arrays.stream(values())
                .filter(motivo -> motivo.name().equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El motivo tiene que ser uno de "
                        + Arrays.toString(values())));
    }

    /**
     * El motivo guardado en el detalle de un paso, sin volver a validar: uno que este código no conoce (de una
     * versión más nueva) se lee como vacío en vez de romper la lectura.
     */
    public static Optional<MotivoProblema> leido(String guardado) {
        return Arrays.stream(values()).filter(motivo -> motivo.name().equals(guardado)).findFirst();
    }
}
