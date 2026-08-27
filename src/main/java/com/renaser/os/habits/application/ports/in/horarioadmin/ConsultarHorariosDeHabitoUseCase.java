package com.renaser.os.habits.application.ports.in.horarioadmin;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** Panel admin, hueco #11. Solo ADMIN/ALCHEMIST. */
public interface ConsultarHorariosDeHabitoUseCase {

    List<HorarioHabito> listar(UserId actorId, HabitoId habitoId);
}
