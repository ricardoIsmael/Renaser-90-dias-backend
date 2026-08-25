package com.renaser.os.rocks.application.ports.out.verdugo;

import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugo;

public interface SaveEventoVerdugoPort {

    EventoVerdugo save(EventoVerdugo evento);
}
