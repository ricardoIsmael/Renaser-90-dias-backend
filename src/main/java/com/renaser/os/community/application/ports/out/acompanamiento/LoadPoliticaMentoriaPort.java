package com.renaser.os.community.application.ports.out.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.cohorte.CohorteId;

import java.util.Optional;

public interface LoadPoliticaMentoriaPort {

    /**
     * Vacío cuando la cohorte se creó después de V45 y nadie configuró nada. Quien llama
     * resuelve con {@code PoliticaMentoria.porDefecto}: la ausencia de fila no es un error.
     */
    Optional<PoliticaMentoria> porCohorte(CohorteId cohorteId);
}
