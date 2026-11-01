package com.renaser.os.habits.application.ports.in.habitoadmin;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * «Copiar a otra formación» (spec 023, historia 3): crea en la formación elegida un hábito con el mismo contenido —título, descripción, tipo,
 * evidencia, opcionalidad, audiencia por rol, horarios y guía—, en la categoría de esa formación que Administración elija, y ya INDEPENDIENTE: editar,
 * desactivar o eliminar uno no cambia al otro. Solo ADMIN/ALCHEMIST.
 */
public interface CopiarHabitoAOtraFormacionUseCase {

    HabitoDelCatalogo copiar(CopiarHabitoCommand command);

    record CopiarHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId, @NotNull Formacion destino,
                               @NotBlank String categoriaDestino) {
        public CopiarHabitoCommand {
            SelfValidating.validateConstructorArgs(CopiarHabitoCommand.class, actorId, habitoId, destino, categoriaDestino);
        }
    }
}
