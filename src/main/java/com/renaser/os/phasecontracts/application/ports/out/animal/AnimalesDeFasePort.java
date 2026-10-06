package com.renaser.os.phasecontracts.application.ports.out.animal;

import com.renaser.os.phasecontracts.domain.model.animal.AnimalDeFase;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;

import java.util.List;

public interface AnimalesDeFasePort {

    /** Las cuatro fases en orden; las que no tienen fila vienen sin personalizar. */
    List<AnimalDeFase> todos();

    AnimalDeFase porFase(FasePrograma fase);

    void guardar(AnimalDeFase animal);
}
