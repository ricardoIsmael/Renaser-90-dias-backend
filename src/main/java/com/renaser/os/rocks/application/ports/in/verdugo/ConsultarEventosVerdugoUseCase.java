package com.renaser.os.rocks.application.ports.in.verdugo;

import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugo;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ConsultarEventosVerdugoUseCase {

    List<EventoVerdugo> misEventos(UserId actorId);
}
