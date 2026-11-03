package com.renaser.os.habits.application.ports.in.medicion;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.medicion.UnidadMedicion;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * La meta diaria de un hábito medible, vista y cambiada por Administración desde «Hábitos del programa» (spec 037). Un
 * hábito que no mide nada no tiene meta: para esta pantalla es un 404, como uno que no existe.
 */
public interface MetaDeMedicionAdminUseCases {

    MetaDeMedicion consultar(UserId actorId, HabitoId habitoId);

    /** {@code meta} nula vuelve a la del programa. */
    MetaDeMedicion fijar(FijarMetaCommand command);

    /**
     * @param meta         la que rige (la fijada o, sin ella, la del programa)
     * @param porDefecto   la del programa
     * @param esLaPorDefecto {@code true} si nadie fijó otra
     */
    record MetaDeMedicion(UnidadMedicion unidad, BigDecimal meta, BigDecimal porDefecto, BigDecimal minimo,
                          BigDecimal maximo, boolean esLaPorDefecto) {
    }

    record FijarMetaCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId, BigDecimal meta) {

        public FijarMetaCommand {
            SelfValidating.validateConstructorArgs(FijarMetaCommand.class, actorId, habitoId, meta);
        }
    }
}
