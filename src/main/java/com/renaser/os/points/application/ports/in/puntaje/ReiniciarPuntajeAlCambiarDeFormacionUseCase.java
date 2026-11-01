package com.renaser.os.points.application.ports.in.puntaje;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

/**
 * Los puntos y la racha de un aprendiz empiezan de cero en la formación nueva (spec 022, Q8-A); lo anterior queda en el ledger y en su historial. Lo
 * dispara el evento de {@code users} al trasladarlo.
 */
public interface ReiniciarPuntajeAlCambiarDeFormacionUseCase {

    /** Idempotente: si ya está en el inicial no escribe nada. */
    void reiniciar(UserId aprendiz, Formacion desde, Formacion hacia);
}
