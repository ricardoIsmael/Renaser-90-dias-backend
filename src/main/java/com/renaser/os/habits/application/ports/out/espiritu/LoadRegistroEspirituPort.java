package com.renaser.os.habits.application.ports.out.espiritu;

import com.renaser.os.habits.domain.model.espiritu.RegistroEspiritu;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface LoadRegistroEspirituPort {

    Optional<RegistroEspiritu> porParticipanteYDia(UserId participanteId, int dia);
}
