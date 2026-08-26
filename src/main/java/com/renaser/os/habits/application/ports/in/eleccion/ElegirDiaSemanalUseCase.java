package com.renaser.os.habits.application.ports.in.eleccion;

import com.renaser.os.habits.domain.model.eleccion.EleccionDiaSemanal;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Hueco #12 — el aprendiz elige que dia de ESTA semana hace un habito de eleccion semanal
 * (weeklyChoice.ts). Traduccion simplificada: no valida el desbloqueo escalonado
 * (staggering, D-H2, deliberadamente fuera de alcance) ni genera el track del dia elegido
 * cuando es HOY (D-H3 sigue abierto — {@code GenerarTracksDelDiaUseCase} todavia no filtra
 * por eleccion semanal, ver docs/MODULO_HABITS.md).
 */
public interface ElegirDiaSemanalUseCase {

    EleccionDiaSemanal elegir(ElegirDiaSemanalCommand command);

    record ElegirDiaSemanalCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId,
                                    @NotNull LocalDate fechaElegida) {
        public ElegirDiaSemanalCommand {
            SelfValidating.validateConstructorArgs(ElegirDiaSemanalCommand.class, actorId, habitoId, fechaElegida);
        }
    }
}
