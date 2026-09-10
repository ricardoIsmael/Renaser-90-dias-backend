package com.renaser.os.community.application.ports.out.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadAsignacionesPort {

    /** Historial completo de una persona: vigentes y cerradas. La evaluación necesita las dos. */
    List<AsignacionCelula> porUsuario(UserId usuarioId);

    /** Todas las asignaciones de un grupo, para leer su composición en cualquier instante. */
    List<AsignacionCelula> porCelula(CelulaId celulaId);

    /** Idempotencia: repetir un comando encuentra lo que ya hizo en vez de volver a hacerlo. */
    Optional<AsignacionCelula> porClaveOperacion(String claveOperacion);
}
