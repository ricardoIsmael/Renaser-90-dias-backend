package com.renaser.os.habits.application.ports.in.guiaadmin;

import com.renaser.os.habits.domain.model.guia.ContenidoGuia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Alta o edicion de una guia, identificada por (habito, diaInicio) — UNIQUE real en
 * {@code guias_habito}. Si ya existe una guia con ese {@code diaInicio} para el habito, se
 * edita su contenido; si no existe, se crea. {@code closePrevious}: si viene en true y hay
 * una guia anterior ABIERTA ({@code diaFin == null}) para el mismo habito, se la cierra en
 * {@code diaInicio - 1} antes de crear/actualizar esta (ver {@code GuiaHabito.cerrarEn}).
 * Solo ADMIN/ALCHEMIST. Devuelve la guia CON sus adjuntos ya resueltos (si edita una guia
 * existente que ya tenia adjuntos) para que el controller no tenga que volver a listar y
 * re-autorizar (ver {@code GuiaHabitoAdminController}).
 */
public interface UpsertGuiaHabitoUseCase {

    GuiaConAdjuntos upsert(UpsertGuiaHabitoCommand command);

    record UpsertGuiaHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId, int diaInicio, Integer diaFin,
                                    @NotNull ContenidoGuia contenido, boolean closePrevious) {
        public UpsertGuiaHabitoCommand {
            SelfValidating.validateConstructorArgs(UpsertGuiaHabitoCommand.class, actorId, habitoId, diaInicio,
                    diaFin, contenido, closePrevious);
        }
    }
}
