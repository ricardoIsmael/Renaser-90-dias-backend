package com.renaser.os.users.application.ports.out.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.CifrasDeFormacionFinder.CifrasDePersonas;

/** Cuenta las personas de una formación (una consulta por cifra, sin cargar filas). */
public interface CifrasDeFormacionPort {

    CifrasDePersonas de(Formacion formacion);

    int personalSinFormacion();
}
