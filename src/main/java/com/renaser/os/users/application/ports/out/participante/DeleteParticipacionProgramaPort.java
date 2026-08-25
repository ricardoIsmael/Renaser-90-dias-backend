package com.renaser.os.users.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

/** Baja del "seguimiento personal" (D-33: hard delete, no soft-toggle — replica
 * `deleteTraineeProfileForMentor` del backend viejo; cascada limpia habitos/rocas). */
public interface DeleteParticipacionProgramaPort {

    /** @return true si habia una fila y se borro; false si no habia nada (idempotente). */
    boolean deleteByParticipanteId(UserId participanteId);
}
