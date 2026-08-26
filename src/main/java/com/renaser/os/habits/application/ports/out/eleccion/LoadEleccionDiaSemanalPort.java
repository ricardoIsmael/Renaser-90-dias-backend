package com.renaser.os.habits.application.ports.out.eleccion;

import com.renaser.os.habits.domain.model.eleccion.EleccionDiaSemanal;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

public interface LoadEleccionDiaSemanalPort {

    /** Las elecciones de ese habito dentro de la semana que arranca en {@code semanaInicio}. */
    List<EleccionDiaSemanal> deHabitoEnSemana(UserId participanteId, HabitoId habitoId, LocalDate semanaInicio);
}
