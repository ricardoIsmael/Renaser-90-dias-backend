package com.renaser.os.habits.application.ports.out.desbloqueo;

import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface LoadDesbloqueoHabitoPort {

    List<DesbloqueoHabito> deParticipante(UserId participanteId);
}
