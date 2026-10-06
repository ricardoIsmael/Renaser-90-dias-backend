package com.renaser.os.phasecontracts.application.ports.in.animal;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface VerAnimalesDeFaseUseCase {

    /** Las cuatro fases en orden, para cualquier cuenta activa. */
    List<AnimalDeFaseVista> ver(UserId actorId);
}
