package com.renaser.os.users.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;

import java.util.Optional;

/** Lectura del agregado propio de este modulo (solo la fila de `participantes_programa`,
 * sin el JOIN a `usuarios` — eso es {@link ConsultarResumenParticipacionPort}). */
public interface LoadParticipacionProgramaPort {

    Optional<ParticipacionPrograma> byParticipanteId(UserId participanteId);
}
