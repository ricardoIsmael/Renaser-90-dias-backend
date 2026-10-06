package com.renaser.os.phasecontracts.application.services;

import com.renaser.os.phasecontracts.application.ports.in.animal.AnimalDeFaseVista;
import com.renaser.os.phasecontracts.application.ports.in.animal.RestaurarImagenDeAnimalUseCase;
import com.renaser.os.phasecontracts.application.ports.out.animal.AnimalesDeFasePort;
import com.renaser.os.phasecontracts.domain.model.animal.AnimalDeFase;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class RestaurarImagenDeAnimalService implements RestaurarImagenDeAnimalUseCase {

    private final GuardiaDeAnimalesDeFase guardia;
    private final AnimalesDeFasePort animales;
    private final AnimalesDeFaseParaMostrar paraMostrar;
    private final Clock clock;

    RestaurarImagenDeAnimalService(GuardiaDeAnimalesDeFase guardia, AnimalesDeFasePort animales,
                                   AnimalesDeFaseParaMostrar paraMostrar, Clock clock) {
        this.guardia = guardia;
        this.animales = animales;
        this.paraMostrar = paraMostrar;
        this.clock = clock;
    }

    @Override
    public List<AnimalDeFaseVista> restaurar(UserId actorId, int fase) {
        guardia.exigirQuePuedaCambiarlos(actorId);
        AnimalDeFase animal = animales.porFase(FasePrograma.porNumero(fase));
        if (animal.tieneImagenPropia()) {
            animal.restaurarImagen(actorId, clock.now());
            animales.guardar(animal);
        }
        return paraMostrar.todos();
    }
}
