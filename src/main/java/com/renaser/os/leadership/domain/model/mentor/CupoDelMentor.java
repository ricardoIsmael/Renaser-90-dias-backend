package com.renaser.os.leadership.domain.model.mentor;

import java.util.Collection;

/**
 * El cupo de un mentor (spec 056, FR-003, decisión del dueño del 18/11): los aprendices que acompaña hoy sobre la suma de los cupos de sus
 * grupos. No se guarda: el servidor guarda el cupo por grupo, no por mentor. Un mentor sin grupo tiene 0 de 0.
 *
 * @param acompanados los aprendices vigentes de sus grupos, sin repetir a quien está en dos
 * @param maximo      la suma de los cupos de sus grupos
 */
public record CupoDelMentor(int acompanados, int maximo) {

    public CupoDelMentor {
        if (acompanados < 0 || maximo < 0) {
            throw new IllegalArgumentException("Un cupo no puede ser negativo: " + acompanados + "/" + maximo);
        }
    }

    /** @param maximosDeSusGrupos el tope de cada uno de sus grupos regulares */
    public static CupoDelMentor de(int acompanados, Collection<Integer> maximosDeSusGrupos) {
        return new CupoDelMentor(acompanados, maximosDeSusGrupos.stream().mapToInt(Integer::intValue).sum());
    }
}
