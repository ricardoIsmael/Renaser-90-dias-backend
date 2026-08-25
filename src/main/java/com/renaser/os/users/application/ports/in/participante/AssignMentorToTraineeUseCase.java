package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Asigna un mentor a un participante. Administrativo (ADMIN/ALCHEMIST) — "nadie
 * activa/desactiva el programa de otro salvo administrativo" se extiende aca a
 * "nadie reasigna el mentor de otro salvo administrativo".
 *
 * <p><b>NO actualiza ningun contador de mentor.</b> `perfiles_mentor` no tiene columna
 * de conteo (la version anterior, {@code total_trainees_managed}, se elimino a proposito
 * en el baseline actual — P-17, ver comentario en `V1__baseline_renaser.sql`: es
 * derivable con {@code COUNT(*) ... WHERE mentor_id = ?}, no se duplica). Escribir un
 * contador que no existe en el schema violaria CLAUDE.MD §0.6 (no inventar columnas).
 */
public interface AssignMentorToTraineeUseCase {

    void assignMentor(AssignMentorCommand command);

    record AssignMentorCommand(@NotNull UserId actorId, @NotNull UserId traineeId, @NotNull UserId mentorId) {

        public AssignMentorCommand {
            SelfValidating.validateConstructorArgs(AssignMentorCommand.class, actorId, traineeId, mentorId);
        }
    }
}
