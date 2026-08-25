package com.renaser.os.onboarding.application.ports.in.grabacionv90;

import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Upsert por {@code (usuarioId, fase, eje, indice)}: si el slot ya existia (re-grabado),
 * se actualiza y se reinicia el veredicto de IA (ver {@code GrabacionV90.marcarGrabada}).
 */
public interface RegistrarGrabacionV90UseCase {

    GrabacionV90 registrar(RegistrarGrabacionV90Command command);

    record RegistrarGrabacionV90Command(@NotNull UserId usuarioId, @NotBlank String fase, @NotBlank String eje,
                                         @PositiveOrZero short indice, String clavePregunta, @NotNull Long mediaId,
                                         BigDecimal duracionSegundos, String transcripcion) {

        public RegistrarGrabacionV90Command {
            SelfValidating.validateConstructorArgs(RegistrarGrabacionV90Command.class, usuarioId, fase, eje, indice,
                    clavePregunta, mediaId, duracionSegundos, transcripcion);
        }
    }
}
