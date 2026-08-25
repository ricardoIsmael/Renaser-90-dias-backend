package com.renaser.os.users.application.ports.out.participante;

import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;

public interface SaveParticipacionProgramaPort {

    ParticipacionPrograma save(ParticipacionPrograma participacion);
}
