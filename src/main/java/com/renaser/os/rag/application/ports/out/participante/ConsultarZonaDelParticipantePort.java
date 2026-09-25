package com.renaser.os.rag.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

import java.time.ZoneId;

/**
 * La zona horaria de una persona, para saber en que dia calendario esta (regla 02). Quien no esta
 * inscrito en el programa (un mentor, un administrador) vive en la zona del padron,
 * {@code America/Lima}.
 */
public interface ConsultarZonaDelParticipantePort {

    ZoneId de(UserId actorId);
}
