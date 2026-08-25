package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato publico de `community` sobre las celulas (D-41: ningun modulo consulta la
 * tabla de otro de frente). `celulas` es de `community`; otros modulos que necesiten
 * saber quien lidera una celula pasan por aca.
 *
 * <p>Su primer consumidor es `calendar`: para un evento de audiencia CELULA, los
 * destinatarios son los participantes activos de la celula (eso lo da
 * `users.api.ParticipacionProgramaFinder`) MAS el mentor que la lidera, que no tiene
 * fila en `participantes_programa` por ser mentor y se perderia sin esto.
 */
public interface CelulaFinder {

    /** Mentor que lidera la celula, vacio si no existe la celula o no tiene mentor asignado. */
    Optional<UserId> mentorDe(UUID celulaId);
}
