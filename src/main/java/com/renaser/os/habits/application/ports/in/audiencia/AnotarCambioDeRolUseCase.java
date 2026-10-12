package com.renaser.os.habits.application.ports.in.audiencia;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/**
 * Anota que una persona cambió de rol, para que sus hábitos globales sigan el rol nuevo desde su día siguiente
 * (spec 001, US4). Lo dispara el evento {@code users.api.RolDeUsuarioCambiadoEvent}.
 */
public interface AnotarCambioDeRolUseCase {

    void anotar(UserId persona, String rolAnterior, String rolNuevo, Instant cuando);
}
