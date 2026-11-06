package com.renaser.os.onboarding.application.ports.out.respuesta;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/** Escribir una respuesta de texto por la clave estable de su pregunta, solo si la persona todavia no tiene una (spec 043). */
public interface SembrarRespuestaPorClavePort {

    /** @return {@code true} si la escribio; {@code false} si ya habia una o la pregunta no existe */
    boolean siNoHay(UserId persona, String clavePregunta, String texto, Instant cuando);
}
