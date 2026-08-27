package com.renaser.os.habits.application.ports.in.guiaadmin;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** Panel admin, hueco #11. Solo ADMIN/ALCHEMIST. Trae TODAS las guias del habito, no solo la vigente hoy. */
public interface ConsultarGuiasDeHabitoUseCase {

    List<GuiaConAdjuntos> listar(UserId actorId, HabitoId habitoId);
}
