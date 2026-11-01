package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

/**
 * En qué formación y con qué duración hace su programa propio el personal que elige su Día 1 (spec 022, Q9-A): en la formación en la que trabaja
 * (la activa) y con la duración que esa formación ofrece. Administración y Alquimista, que no tienen una activa, lo hacen en la Escuela.
 */
interface ProgramaPropioDelPersonal {

    Programa para(UserId persona);

    record Programa(Formacion formacion, int duracionDias) {
    }

    /** Lo de siempre, antes de las formaciones: la Escuela y 90 días. */
    static ProgramaPropioDelPersonal escuela() {
        return persona -> new Programa(Formacion.POR_DEFECTO, DuracionDelPrograma.POR_DEFECTO);
    }
}
