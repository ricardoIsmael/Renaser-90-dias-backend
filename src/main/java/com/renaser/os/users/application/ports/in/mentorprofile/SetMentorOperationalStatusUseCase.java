package com.renaser.os.users.application.ports.in.mentorprofile;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.mentorprofile.MentorOperationalStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Mover el semaforo operativo de un mentor (VERDE/AMARILLO/ROJO de {@code perfiles_mentor}).
 *
 * <p><b>Por que existe aparte de {@link UpdateMentorProfileUseCase},</b> que ya sabe cambiar ese
 * campo: porque el permiso es distinto. Aquel exige {@code MANAGE_MENTOR_PROFILE}
 * (ADMIN/ALCHEMIST) y cubre tambien el <b>nivel</b> N0-N3, que es una promocion. Este exige
 * {@code SET_MENTOR_OPERATIONAL_STATUS} y lo alcanza tambien el MENTOR_LEAD, para el que el
 * semaforo es su herramienta de seguimiento (SDD 002, decision DL-09 del 2026-09-09).
 * {@code @RequiresPermission} se declara por endpoint, no por campo del body: separar la
 * operacion es lo que permite separar el permiso sin abrirle el nivel a nadie.
 *
 * <p><b>No lleva motivo.</b> Se penso y se descarto: {@code perfiles_mentor} no tiene donde
 * guardarlo, y aceptar un texto para tirarlo seria mentirle a quien lo escribe (SDD 002, CL-07).
 * El lugar del porque es una observacion registrada sobre el mentor, que si es append-only y si
 * aparece en el reporte.
 */
public interface SetMentorOperationalStatusUseCase {

    void setOperationalStatus(SetMentorOperationalStatusCommand command);

    record SetMentorOperationalStatusCommand(
            @NotNull UserId mentorUserId,
            @NotNull MentorOperationalStatus newStatus,
            @NotNull UserId actorId) {

        public SetMentorOperationalStatusCommand {
            SelfValidating.validateConstructorArgs(SetMentorOperationalStatusCommand.class,
                    mentorUserId, newStatus, actorId);
        }
    }
}
