package com.renaser.os.onboarding.application.ports.in.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface AvanzarEstadoUseCase {

    EstadoOnboarding avanzar(AvanzarEstadoCommand command);

    /**
     * flujo/seccion/paso/progresoFlujoJson son todos opcionales: el cliente manda solo lo
     * que cambio (ver {@code EstadoOnboarding.avanzar}). progresoFlujoJson es JSON crudo,
     * dato opaco (CLAUDE.MD, decision de este modulo).
     */
    record AvanzarEstadoCommand(@NotNull UserId usuarioId, String flujo, String seccion, Integer paso,
                                 String progresoFlujoJson) {

        public AvanzarEstadoCommand {
            SelfValidating.validateConstructorArgs(AvanzarEstadoCommand.class, usuarioId, flujo, seccion, paso,
                    progresoFlujoJson);
        }
    }
}
