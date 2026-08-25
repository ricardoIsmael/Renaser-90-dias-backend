package com.renaser.os.points.application.ports.out.puntaje;

import com.renaser.os.shared.domain.UserId;

public interface VerificarActorAdministrativoPort {

    /** true si el actor existe, está ACTIVO y su rol es ADMIN o ALQUIMISTA. */
    boolean esAdministrativoActivo(UserId actorId);
}
