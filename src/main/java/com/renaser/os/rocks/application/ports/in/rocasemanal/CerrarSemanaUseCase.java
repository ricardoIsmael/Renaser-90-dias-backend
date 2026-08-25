package com.renaser.os.rocks.application.ports.in.rocasemanal;

import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Revisión de cierre de semana (W-04) — sin restricción de ventana, idempotente. */
public interface CerrarSemanaUseCase {

    RocaSemanal cerrar(CerrarSemanaCommand command);

    record CerrarSemanaCommand(@NotNull UserId actorId, @NotNull RocaSemanalId rocaSemanalId,
                                @Min(1) @Max(10) int autoevaluacionFin, @NotBlank String bloqueoPrincipal,
                                @NotBlank String correccion) {

        public CerrarSemanaCommand {
            SelfValidating.validateConstructorArgs(CerrarSemanaCommand.class, actorId, rocaSemanalId,
                    autoevaluacionFin, bloqueoPrincipal, correccion);
        }
    }
}
