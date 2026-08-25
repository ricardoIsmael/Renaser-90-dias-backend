package com.renaser.os.rocks.application.ports.out.verdugo;

import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugo;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

public interface LoadEventoVerdugoPort {

    List<EventoVerdugo> deParticipante(UserId participanteId);

    /** Eventos sin resolver disparados en la fecha dada — el barrido de las 23:55 los busca por esto. */
    List<EventoVerdugo> pendientesDeFecha(LocalDate fecha);
}
