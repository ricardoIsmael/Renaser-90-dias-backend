package com.renaser.os.rocks.application.ports.in.rocasemanal;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Planning Semanal (W-02): crea las 3 Rocas Semanales (una por eje) de la semana entrante. */
public interface CrearPlanSemanalUseCase {

    List<RocaSemanal> crear(CrearPlanSemanalCommand command);

    record CrearPlanSemanalCommand(@NotNull UserId actorId,
                                    @NotNull @Size(min = 3, max = 3) List<@Valid ItemRocaSemanal> rocas) {

        public CrearPlanSemanalCommand {
            SelfValidating.validateConstructorArgs(CrearPlanSemanalCommand.class, actorId, rocas);
        }
    }

    record ItemRocaSemanal(EjeObjetivo eje, String titulo, String accionCritica1, String accionCritica2,
                            String accionCritica3, String obstaculo, String contingencia,
                            Integer autoevaluacionInicio) {

        public ItemRocaSemanal {
            if (eje == null) {
                throw new IllegalArgumentException("eje es obligatorio en cada roca semanal");
            }
        }
    }
}
