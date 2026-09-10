package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.habits.application.ports.out.registro.ConsultarObligacionesHistoricasPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Servicio propio y no un método más de los que ya existen, por el mismo criterio que
 * {@code evidence.RegistrosConEvidenciaFinderService}: es la única pieza de {@code habits} cuyo
 * consumidor está afuera, no escribe nada, y tenerla aparte deja a la vista qué expone el módulo.
 *
 * <p>Sin {@code @Transactional}: es una sola lectura.
 */
@Service
class ObligacionesHistoricasFinderService implements ObligacionesHistoricasFinder {

    private final ConsultarObligacionesHistoricasPort consultarPort;

    ObligacionesHistoricasFinderService(ConsultarObligacionesHistoricasPort consultarPort) {
        this.consultarPort = consultarPort;
    }

    @Override
    public List<ObligacionHabito> porParticipantesEntre(Collection<UserId> participantes, LocalDate desde,
                                                         LocalDate hasta) {
        if (participantes.isEmpty()) {
            return List.of();
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El rango va al reves: " + desde + " → " + hasta);
        }
        return consultarPort.entre(participantes, desde, hasta);
    }
}
