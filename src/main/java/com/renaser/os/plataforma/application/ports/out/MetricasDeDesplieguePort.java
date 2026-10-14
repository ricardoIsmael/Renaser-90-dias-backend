package com.renaser.os.plataforma.application.ports.out;

import com.renaser.os.plataforma.domain.model.despliegue.ConteoDeDespliegue;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Los contadores por hora de las funcionalidades en despliegue (V104; contrato fase 2 §3.3). */
public interface MetricasDeDesplieguePort {

    /**
     * Suma estos conteos a los guardados, todos o ninguno (una sola sentencia): dos instancias que escriben la misma
     * hora suman, no se pisan.
     */
    void sumar(Collection<ConteoDeDespliegue> conteos);

    /** Las filas de una funcionalidad desde esa hora (incluida). */
    List<ConteoDeDespliegue> desde(String clave, Instant hora);

    /** Borra lo anterior a {@code limite}; devuelve cuántas filas. */
    int borrarAnterioresA(Instant limite);
}
