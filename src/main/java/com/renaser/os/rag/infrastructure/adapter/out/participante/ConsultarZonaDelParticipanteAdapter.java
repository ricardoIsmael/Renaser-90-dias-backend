package com.renaser.os.rag.infrastructure.adapter.out.participante;

import com.renaser.os.rag.application.ports.out.participante.ConsultarZonaDelParticipantePort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/**
 * La zona de {@code participantes_programa.timezone}, por el contrato publico de {@code users}
 * (D-41). {@link ParticipacionPrograma} ya trae {@code America/Lima} para quien no esta inscrito;
 * si la persona no existe se usa la misma zona del padron.
 */
@Component
class ConsultarZonaDelParticipanteAdapter implements ConsultarZonaDelParticipantePort {

    static final ZoneId ZONA_DEL_PADRON = ZoneId.of("America/Lima");

    private final ParticipacionProgramaFinder participacionFinder;

    ConsultarZonaDelParticipanteAdapter(ParticipacionProgramaFinder participacionFinder) {
        this.participacionFinder = participacionFinder;
    }

    @Override
    public ZoneId de(UserId actorId) {
        return participacionFinder.deParticipante(actorId)
                .map(ParticipacionPrograma::zona)
                .orElse(ZONA_DEL_PADRON);
    }
}
