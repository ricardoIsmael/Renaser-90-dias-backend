package com.renaser.os.habits.application.ports.out.eleccion;

import com.renaser.os.habits.domain.model.eleccion.EleccionDiaSemanal;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;

public interface SaveEleccionDiaSemanalPort {

    EleccionDiaSemanal save(EleccionDiaSemanal eleccion);

    /** Cambiar de idea es MOVER la eleccion de la semana, no acumular — borra antes de guardar la nueva. */
    void borrarDeSemana(UserId participanteId, HabitoId habitoId, LocalDate semanaInicio);
}
