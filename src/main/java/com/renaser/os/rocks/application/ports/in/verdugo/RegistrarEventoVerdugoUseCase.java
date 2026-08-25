package com.renaser.os.rocks.application.ports.in.verdugo;

import com.renaser.os.rocks.domain.model.verdugo.DestinoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.ResultadoVerdugo;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Registra la reacción del aprendiz al Modo Verdugo (mismo contrato que
 * `POST /api/v1/enforcer-events` del repo viejo). {@code IGNORADO} se
 * rechaza acá mismo, antes de llegar al dominio — es exclusivo del barrido
 * nocturno.
 */
public interface RegistrarEventoVerdugoUseCase {

    EventoVerdugo registrar(RegistrarEventoVerdugoCommand command);

    record RegistrarEventoVerdugoCommand(@NotNull UserId actorId, @NotNull DestinoVerdugo destinoTipo,
                                          @NotNull UUID destinoId, @NotNull Instant disparadoEn,
                                          @NotNull ResultadoVerdugo resultado) {

        public RegistrarEventoVerdugoCommand {
            SelfValidating.validateConstructorArgs(RegistrarEventoVerdugoCommand.class, actorId, destinoTipo,
                    destinoId, disparadoEn, resultado);
            if (resultado == ResultadoVerdugo.IGNORADO) {
                throw new IllegalArgumentException("IGNORADO se asigna server-side (barrido de las 23:55), no por el cliente");
            }
        }
    }
}
