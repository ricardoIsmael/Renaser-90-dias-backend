package com.renaser.os.habits.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.habits.application.ports.out.participante.ListarParticipantesActivosPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Delega en el contrato publico de `users` (D-41). Es una operacion EN LOTE — la usa el
 * barrido nocturno — asi que no se resuelve llamando al finder por participante: eso
 * seria un N+1 sobre todo el padron.
 */
@Component
class ListarParticipantesActivosPersistenceAdapter implements ListarParticipantesActivosPort {

    private final ParticipacionProgramaFinder participacionFinder;

    ListarParticipantesActivosPersistenceAdapter(ParticipacionProgramaFinder participacionFinder) {
        this.participacionFinder = participacionFinder;
    }

    @Override
    public List<UserId> todos() {
        return participacionFinder.participantesInscritosActivos();
    }
}
