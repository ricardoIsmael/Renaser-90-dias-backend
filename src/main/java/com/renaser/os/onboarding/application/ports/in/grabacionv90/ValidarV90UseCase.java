package com.renaser.os.onboarding.application.ports.in.grabacionv90;

import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Contrato async + polling (CLAUDE.MD §7, preservado 1:1): {@link #solicitarValidacion}
 * responde de inmediato (marca PROCESANDO y encola el trabajo real, sin bloquear el hilo
 * de request esperando a la IA); {@link #consultarEstado} es el GET de polling.
 *
 * <p>SIN IA en este alcance: como {@code ValidacionIAPort} siempre responde "no disponible"
 * (ver su javadoc), toda grabacion termina en {@code REVISION_MANUAL} tras 3 intentos — el
 * contrato esta completo, falta conectar el adaptador de IA real.
 */
public interface ValidarV90UseCase {

    void solicitarValidacion(SolicitarValidacionV90Command command);

    EstadoValidacionV90 consultarEstado(ConsultarEstadoV90Query query);

    record SolicitarValidacionV90Command(@NotNull UserId usuarioId, @NotNull Long grabacionId) {

        public SolicitarValidacionV90Command {
            SelfValidating.validateConstructorArgs(SolicitarValidacionV90Command.class, usuarioId, grabacionId);
        }
    }

    record ConsultarEstadoV90Query(@NotNull UserId usuarioId, @NotNull Long grabacionId) {

        public ConsultarEstadoV90Query {
            SelfValidating.validateConstructorArgs(ConsultarEstadoV90Query.class, usuarioId, grabacionId);
        }
    }

    record EstadoValidacionV90(EstadoIAv90 estado, short intentosIa, String feedbackJson) {
    }
}
