package com.renaser.os.community.application.ports.in.acompanamiento;

import com.renaser.os.community.domain.model.cohorte.CohorteId;

import java.util.List;
import java.util.UUID;

/** Rotación del mentor de cada grupo regular de una cohorte, conservando grupo, alumnos y chat. */
public interface RotarMentoresUseCase {

    /**
     * @param claveOperacion identifica el período. Repetirla no abre otros intervalos: los
     *                       cambios ya aplicados se reconocen y se saltean.
     */
    ResultadoRotacion rotar(CohorteId cohorteId, String claveOperacion);

    /** Recorre las cohortes que hoy toca rotar según su política. Lo llama el job. */
    List<ResultadoRotacion> rotarLasQueCorresponda();

    /**
     * @param sinSustituto grupos que conservan su mentor porque no había con quién relevarlo.
     * @param sinCobertura grupos que quedan sin mentor; soporte cubre y se avisa (P-03).
     * @param yaAplicados  cambios que esta corrida encontró hechos. Con un job que se reintenta
     *                     es lo normal, no un error.
     */
    record ResultadoRotacion(UUID cohorteId, String claveOperacion, int cambiosAplicados, int yaAplicados,
                              List<UUID> sinSustituto, List<UUID> sinCobertura) {
    }
}
