package com.renaser.os.habits.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** Usado solo por los schedulers de barrido (expiracion de registros/rachas). */
public interface ListarParticipantesActivosPort {

    List<UserId> todos();
}
