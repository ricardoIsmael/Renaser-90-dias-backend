package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/**
 * "Editar dia de programa" del panel admin de aprendices (gap #7). Capacidad OPERATIVA
 * legitima (un admin corrigiendo un desfase a mano), no una regla de negocio nueva — el
 * limite [1, duracion - 1] (D-194; 1..89 en la Escuela, 1..364 en el Diplomado de un anio, spec 022) lo impone
 * {@code ParticipacionPrograma.fijarDia} con LA DURACION DE LA PERSONA; aca solo se repite el limite de cualquier programa
 * (1..364) para fallar rapido con un 400 en vez de esperar a la excepcion de dominio. Antes del Dia 1 el dominio responde {@code IllegalStateException} (409, D-195).
 */
public interface SetTraineeProgramDayUseCase {

    void fijarDia(SetProgramDayCommand command);

    /**
     * {@code motivo} (D-82) explica POR QUE se movio el dia — se guarda en la bitacora
     * `ajustes_dia_programa`. Es <b>opcional en el comando a proposito</b>: el endpoint ya
     * existia sin este campo y el panel admin actual todavia no lo manda; romperlo seria
     * peor que guardar un ajuste sin explicacion. El dominio normaliza el vacio a
     * {@code AjusteDiaPrograma.MOTIVO_NO_REGISTRADO}, asi que la fila nunca queda en NULL
     * y se ve de una que nadie lo registro.
     */
    record SetProgramDayCommand(UserId actorId, UserId traineeId, @Min(1) @Max(DuracionDelPrograma.MAXIMA - 1) int newProgramDay,
                                 String motivo) {

        public SetProgramDayCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(traineeId, "traineeId es obligatorio");
            if (newProgramDay < ParticipacionPrograma.PRIMER_DIA_AJUSTABLE
                    || newProgramDay > DuracionDelPrograma.MAXIMA - 1) {
                throw new IllegalArgumentException("El día tiene que estar entre 1 y " + (DuracionDelPrograma.MAXIMA - 1)
                        + ", y antes del último día de su programa: el 0 y el último día no se fijan a mano");
            }
        }

        /** Firma historica (sin motivo): se conserva para no obligar a los llamadores
         * existentes a pasar un campo que no tienen. */
        public SetProgramDayCommand(UserId actorId, UserId traineeId, int newProgramDay) {
            this(actorId, traineeId, newProgramDay, null);
        }
    }
}
