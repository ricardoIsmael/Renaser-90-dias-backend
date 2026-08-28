package com.renaser.os.habits.application.ports.in.audioterapiaadmin;

import com.renaser.os.shared.domain.UserId;

/** Solo ADMIN/ALCHEMIST -- panel admin de catalogo, mismo criterio que {@code HabitoAdminGuard}. */
public interface ActualizarDuracionAudioterapiaUseCase {

    AudioterapiaActualizada actualizar(ActualizarDuracionAudioterapiaCommand command);

    record ActualizarDuracionAudioterapiaCommand(UserId actorId, int semana, int duracionDias) {
    }

    /** Proyeccion propia del puerto `in` -- nunca el record del puerto `out` (ArchitectureTest lo prohibe). */
    record AudioterapiaActualizada(int semana, String titulo, int duracionDias) {
    }
}
