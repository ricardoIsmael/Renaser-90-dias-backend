package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/**
 * "Editar dia de programa" del panel admin de aprendices (gap #7). Capacidad OPERATIVA
 * legitima (un admin corrigiendo un desfase a mano), no una regla de negocio nueva — el
 * limite [0, 90] es la invariante de dominio que ya impone {@code ParticipacionPrograma}
 * (ver {@code fijarDia}), aca solo se repite en el comando para fallar rapido con un 400
 * en vez de esperar a la excepcion de dominio.
 */
public interface SetTraineeProgramDayUseCase {

    void fijarDia(SetProgramDayCommand command);

    record SetProgramDayCommand(UserId actorId, UserId traineeId, @Min(0) @Max(90) int newProgramDay) {

        public SetProgramDayCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(traineeId, "traineeId es obligatorio");
            if (newProgramDay < 0 || newProgramDay > 90) {
                throw new IllegalArgumentException("newProgramDay debe estar entre 0 y 90");
            }
        }
    }
}
