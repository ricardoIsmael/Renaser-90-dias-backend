package com.renaser.os.habits.application.ports.out.carrera;

import java.time.Instant;

/** El borrado de las rutas viejas (spec 042, FR-042): la ruta se va, el resumen de la carrera se queda. */
public interface RetencionDeRutasPort {

    /** Borra la ruta de hasta {@code lote} carreras que empezaron antes de {@code limite}; devuelve cuántas borró. */
    int borrarRutasAnterioresA(Instant limite, int lote);
}
