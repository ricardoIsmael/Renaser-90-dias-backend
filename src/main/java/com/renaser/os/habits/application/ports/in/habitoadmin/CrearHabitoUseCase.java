package com.renaser.os.habits.application.ports.in.habitoadmin;

import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * Alta de un habito de catalogo (SISTEMA) desde el panel admin. Solo ADMIN/ALCHEMIST.
 *
 * <p>E-659 (issue #44): el alta crea tambien su horario ({@code horarios_habito}), porque sin una fila ahi el
 * habito no se genera para nadie. La hora es opcional: sin ella el habito vale todo el dia, como DESPERTAR.
 */
public interface CrearHabitoUseCase {

    HabitoDelCatalogo crear(CrearHabitoCommand command);

    record CrearHabitoCommand(@NotNull UserId actorId, @NotBlank @Size(max = 120) String titulo,
                               @NotNull TipoHabito tipo, @NotNull DetallesHabito detalles, Audiencia audiencia,
                               LocalTime horaDisparo) {
        /** Spec 001: sin audiencia = para todos, como todo hábito anterior a la función. */
        public CrearHabitoCommand {
            SelfValidating.validateConstructorArgs(CrearHabitoCommand.class, actorId, titulo, tipo, detalles,
                    audiencia, horaDisparo);
            audiencia = audiencia == null ? Audiencia.PARA_TODOS : audiencia;
        }

        /** Sin hora: el habito vale todo el dia (E-659). */
        public CrearHabitoCommand(UserId actorId, String titulo, TipoHabito tipo, DetallesHabito detalles,
                                  Audiencia audiencia) {
            this(actorId, titulo, tipo, detalles, audiencia, null);
        }

        public CrearHabitoCommand(UserId actorId, String titulo, TipoHabito tipo, DetallesHabito detalles) {
            this(actorId, titulo, tipo, detalles, Audiencia.PARA_TODOS, null);
        }
    }
}
