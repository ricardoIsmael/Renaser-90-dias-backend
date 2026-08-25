package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

public interface ConsultarTracksDelDiaUseCase {

    /** Autoservicio: actorId debe ser el propio participanteId (ver CLAUDE.MD §5.3.4, requireSelf). */
    List<RegistroHabito> consultar(UserId actorId, UserId participanteId, LocalDate fecha);
}
