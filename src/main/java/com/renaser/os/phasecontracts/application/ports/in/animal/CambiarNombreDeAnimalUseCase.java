package com.renaser.os.phasecontracts.application.ports.in.animal;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface CambiarNombreDeAnimalUseCase {

    /** Un nombre vacío vuelve al que trae la app. */
    List<AnimalDeFaseVista> cambiar(UserId actorId, int fase, String nombre);
}
