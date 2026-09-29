package com.renaser.os.habits.application.ports.in.medicion;

import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Registrar el número de un día de un hábito medible sin conocer el id del track (D-226): por
 * participante, clave del hábito y fecha. Es la puerta para la <b>fase 2</b> — los pasos que la app
 * lea de Health Connect (Android) o HealthKit (iOS) y mande al servidor con su origen, o la lectura
 * automática de la captura. Hoy la app registra los km por {@code POST /habit-tracks/{id}/complete},
 * porque ya tiene el track en la mano (es el mismo en el que subió la captura); los dos caminos
 * terminan en el mismo {@code CompletarRegistroUseCase}: misma política, mismos puntos, mismo evento.
 *
 * <p>Vive en {@code application/ports/in} y no en {@code habits.api}: los datos de salud los lee el
 * teléfono (Health Connect y HealthKit no tienen API de servidor), así que el llamador de la fase 2 es
 * un adaptador REST de este mismo módulo. Publicarlo en {@code api} dejaría a cualquier otro módulo
 * escribir km sin necesidad.
 */
public interface RegistrarMedicionDiariaUseCase {

    RegistroHabito registrar(RegistrarMedicionCommand command);

    /** {@code actorId} tiene que ser el propio participante: nadie registra km de otro. */
    record RegistrarMedicionCommand(@NotNull UserId actorId, @NotNull UserId participanteId,
                                     @NotBlank String claveSistema, @NotNull LocalDate fecha,
                                     @NotNull MedicionDiaria medicion) {

        public RegistrarMedicionCommand {
            SelfValidating.validateConstructorArgs(RegistrarMedicionCommand.class, actorId, participanteId,
                    claveSistema, fecha, medicion);
        }
    }
}
