package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.RegistrosDeHabitoFinder;
import com.renaser.os.habits.application.ports.out.registro.LeerRegistrosDelRangoPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementa {@link RegistrosDeHabitoFinder} (spec 015). Servicio propio, como
 * {@link ObligacionesHistoricasFinderService}: es una pieza que solo lee y cuyo consumidor está afuera. Sin
 * {@code @Transactional}: es una sola lectura.
 */
@Service
class RegistrosDeHabitoFinderService implements RegistrosDeHabitoFinder {

    private final LeerRegistrosDelRangoPort registrosPort;

    RegistrosDeHabitoFinderService(LeerRegistrosDelRangoPort registrosPort) {
        this.registrosPort = registrosPort;
    }

    @Override
    public List<RegistroDeHabito> deParticipanteEntre(UserId participanteId, LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El rango va al reves: " + desde + " → " + hasta);
        }
        return registrosPort.deParticipanteEntre(participanteId, desde, hasta);
    }
}
