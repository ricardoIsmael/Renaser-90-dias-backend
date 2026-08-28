package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Autoservicio: el participante solo ve el catalogo activo y sus propios habitos PERSONAL. */
@Service
public class MisHabitosService implements ConsultarMisHabitosUseCase {

    private final LoadHabitoPort loadPort;

    public MisHabitosService(LoadHabitoPort loadPort) {
        this.loadPort = loadPort;
    }

    @Override
    public List<Habito> consultar(UserId actor) {
        List<Habito> habitos = new ArrayList<>(loadPort.catalogoActivo());
        habitos.addAll(loadPort.personalesActivosDe(actor));
        return habitos;
    }
}
