package com.renaser.os.points.application.ports.in.semaforo;

import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Pausa con fecha de regreso del semáforo del staff con programa propio (respuesta del dueño,
 * 2026-09-25). El aprendiz no puede pausarlo: para él es obligatorio.
 */
public interface PausarSemaforoUseCase {

    /** Pausa desde hoy hasta {@code hasta}; si ya hay una pausa en curso, le cambia la fecha. */
    DetalleDelSemaforo pausar(PausarSemaforoCommand command);

    /** Vuelve a medir desde hoy. Sin pausa en curso no hace nada. */
    DetalleDelSemaforo reanudar(UserId actorId);

    record PausarSemaforoCommand(@NotNull UserId actorId, @NotNull LocalDate hasta) {

        public PausarSemaforoCommand {
            SelfValidating.validateConstructorArgs(PausarSemaforoCommand.class, actorId, hasta);
        }
    }
}
