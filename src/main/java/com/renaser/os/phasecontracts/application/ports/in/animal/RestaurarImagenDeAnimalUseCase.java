package com.renaser.os.phasecontracts.application.ports.in.animal;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface RestaurarImagenDeAnimalUseCase {

    /** Vuelve a la imagen que trae la app. */
    List<AnimalDeFaseVista> restaurar(UserId actorId, int fase);
}
