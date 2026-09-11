package com.renaser.os.community.domain.model.acompanamiento;

import java.util.Collection;
import java.util.Optional;

/**
 * El límite de un grupo, medido en aprendices. La recepción no tiene tope comercial (D-05);
 * el grupo estable arranca en 10 y se puede configurar hasta 15 (D-01).
 */
public record CupoCelula(Integer maximoAprendices) {

    private static final int MINIMO_CONFIGURABLE = 10;
    private static final int MAXIMO_CONFIGURABLE = 15;

    public CupoCelula {
        if (maximoAprendices != null
                && (maximoAprendices < MINIMO_CONFIGURABLE || maximoAprendices > MAXIMO_CONFIGURABLE)) {
            throw new IllegalArgumentException("La capacidad de un grupo regular va de "
                    + MINIMO_CONFIGURABLE + " a " + MAXIMO_CONFIGURABLE + ", llego " + maximoAprendices);
        }
    }

    public static CupoCelula regular(int maximoAprendices) {
        return new CupoCelula(maximoAprendices);
    }

    /** Sin tope: la recepción admite a todos los que empiezan el mismo día. */
    public static CupoCelula recepcion() {
        return new CupoCelula(null);
    }

    public Optional<Integer> maximo() {
        return Optional.ofNullable(maximoAprendices);
    }

    public boolean admiteOtroAprendiz(Collection<FuncionAcompanamiento> ocupantesVigentes) {
        return maximoAprendices == null || ocupados(ocupantesVigentes) < maximoAprendices;
    }

    /**
     * Cuántos aprendices sobran respecto del límite actual. Bajar la capacidad por debajo de
     * la ocupación no expulsa a nadie: bloquea altas hasta que el grupo vuelva bajo el
     * límite (RF-28, plan.md §4.8).
     */
    public int excedente(Collection<FuncionAcompanamiento> ocupantesVigentes) {
        if (maximoAprendices == null) {
            return 0;
        }
        return Math.max(0, ocupados(ocupantesVigentes) - maximoAprendices);
    }

    private static int ocupados(Collection<FuncionAcompanamiento> ocupantesVigentes) {
        return (int) ocupantesVigentes.stream().filter(FuncionAcompanamiento::consumeCupo).count();
    }
}
