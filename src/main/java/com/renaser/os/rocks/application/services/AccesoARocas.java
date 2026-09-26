package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;

import java.util.NoSuchElementException;

/**
 * La guarda de siempre para operar rocas: participante existente, cuenta no suspendida, y aprendiz
 * o staff con el programa andando (E-169).
 *
 * <p>Es la MISMA regla que {@code requireProgreso} de {@link RocaDiariaService},
 * {@link RocaSemanalService} y {@link DashboardRocasService}, que la tienen copiada cada uno. Esta
 * clase existe para que los casos de uso nuevos (D-177) no sumen una cuarta copia; unificar las tres
 * viejas queda fuera de este cambio porque no agrega comportamiento.
 */
final class AccesoARocas {

    private AccesoARocas() {
    }

    /** SUSPENDIDO -> 403. Sin programa andando -> 403. Sin participacion -> {@link NoSuchElementException}. */
    static ProgresoParticipanteRocks exigir(ConsultarProgresoParticipanteRocksPort progresoPort, UserId actorId) {
        ProgresoParticipanteRocks progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (progreso.rol() != RolParticipante.TRAINEE && !progreso.programaActivado()) {
            throw new NotAuthorizedException("Solo un aprendiz opera sus propias rocas");
        }
        return progreso;
    }
}
