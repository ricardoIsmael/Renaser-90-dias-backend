package com.renaser.os.habits.application.ports.in.espiritu;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface ConsultarEstadoEspirituUseCase {

    /** Autoservicio: avanza el state machine lazy (ensureAdvanced) y devuelve la vista dia-por-dia. */
    EstadoEspiritu consultar(UserId actorId);

    /** {@code diaActual}: el mayor {@code dia} con track creado, o {@code null} sin ninguno todavia. */
    record EstadoEspiritu(List<DiaEspiritu> dias, Integer diaActual) {
    }

    /**
     * {@code estado}: {@code LOCKED} (sin track — catalogo sin desbloquear todavia),
     * {@code CURRENT} (PENDIENTE), {@code SUBMITTED} (ENTREGADO) o {@code MISSED} (PERDIDO)
     * — mismos cuatro valores del contrato viejo ({@code SpiritDayView.state}, D-36: literal,
     * no traducido).
     */
    record DiaEspiritu(int dia, String titulo, String estado, Instant desbloqueadoEn, Instant fechaLimite,
                        Instant entregadoEn, String resumenTexto) {
    }
}
