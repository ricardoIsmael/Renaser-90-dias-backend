package com.renaser.os.phasecontracts.application.services;

import com.renaser.os.phasecontracts.application.ports.in.animal.AnimalDeFaseVista;
import com.renaser.os.phasecontracts.application.ports.in.animal.VerAnimalesDeFaseUseCase;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class VerAnimalesDeFaseService implements VerAnimalesDeFaseUseCase {

    private final AnimalesDeFaseParaMostrar paraMostrar;

    VerAnimalesDeFaseService(AnimalesDeFaseParaMostrar paraMostrar) {
        this.paraMostrar = paraMostrar;
    }

    @Override
    public List<AnimalDeFaseVista> ver(UserId actorId) {
        return paraMostrar.todos();
    }
}
