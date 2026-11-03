package com.renaser.os.community.api;

import com.renaser.os.shared.domain.Formacion;

/**
 * Cuántos grupos tiene cada formación, para el centro «Formaciones» de Administración (spec 022, FR-025, FR-026). Cuenta los grupos como los lista
 * {@code GET /admin/cells/dashboard?formation=…}: todos los grupos de la formación, de bienvenida y regulares.
 */
public interface GruposPorFormacionFinder {

    ResumenDeGrupos de(Formacion formacion);

    /**
     * @param total      todos los grupos de la formación = {@code regulares + recepcion}
     * @param conMentor  los regulares que tienen mentor
     * @param sinMentor  los regulares que no tienen mentor (lo que Administración tiene que resolver)
     * @param recepcion  los grupos de bienvenida
     */
    record ResumenDeGrupos(int total, int conMentor, int sinMentor, int recepcion) {
    }
}
