package com.renaser.os.chat.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * El chat de soporte de un aprendiz (D-136), para quien necesita mandar a alguien ahí: el aviso de una
 * emergencia (D-244) lleva a quien atiende directo a esa conversación.
 */
public interface SoporteDelAprendizFinder {

    /** El id de su chat de soporte; vacío si todavía no lo tiene. */
    Optional<UUID> conversacionDeSoporteDe(UserId aprendizId);
}
