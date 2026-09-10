package com.renaser.os.users.application.ports.in.mentorprofile;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.mentorprofile.EspecialidadMentor;
import com.renaser.os.users.domain.model.mentorprofile.MentorLevel;
import com.renaser.os.users.domain.model.mentorprofile.MentorOperationalStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Campos null = "no cambiar". level/operationalStatus/especialidad requieren
 * actor.canManageRoles() (ADMIN/ALCHEMIST); bio la puede tocar el propio mentor (ver
 * MentorProfileService).
 *
 * <p>Ese "null = no cambiar" tiene una consecuencia: por aca no se BORRA la especialidad. Es a
 * proposito — sin declarar es el estado en el que nace un perfil, no algo que se pida.
 */
public interface UpdateMentorProfileUseCase {

    void update(UpdateMentorProfileCommand command);

    record UpdateMentorProfileCommand(
            @NotNull UserId mentorUserId,
            MentorLevel newLevel,
            MentorOperationalStatus newOperationalStatus,
            String newBio,
            @NotNull UserId actorId,
            EspecialidadMentor especialidad) {

        public UpdateMentorProfileCommand {
            // El ORDEN importa: `validateConstructorArgs` empareja estos valores con los
            // componentes del record por POSICION. `especialidad` va AL FINAL, detras de actorId,
            // justamente por eso — meterla en el medio corre todos los de atras y el @NotNull de
            // actorId terminaria evaluandose contra otro campo.
            SelfValidating.validateConstructorArgs(UpdateMentorProfileCommand.class,
                    mentorUserId, newLevel, newOperationalStatus, newBio, actorId, especialidad);
        }

        /** Sobrecarga previa a V48: no toca la especialidad. Se conserva para no obligar a tocar a
         * los llamadores que no la mandan. */
        public UpdateMentorProfileCommand(UserId mentorUserId, MentorLevel newLevel,
                                           MentorOperationalStatus newOperationalStatus, String newBio,
                                           UserId actorId) {
            this(mentorUserId, newLevel, newOperationalStatus, newBio, actorId, null);
        }
    }
}
