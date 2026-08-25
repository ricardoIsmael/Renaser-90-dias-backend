package com.renaser.os.rocks.application.ports.in.rocasemanal;

import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Edita una Roca Semanal (W-03) dentro de su ventana de rectificación.
 *
 * <p>El nombre se conserva tal cual lo pidió el encargo, pero la ventana real
 * NO es una franja fija de 48 h — ver {@code VentanaPlanificacionSemanal} y
 * RK-5 en `docs/MODULO_ROCKS.md`: en plazo dura hasta que cierra la ventana
 * semanal (domingo 12:00 → lunes 09:00); a destiempo son 2 h desde que se creó.
 */
public interface EditarDentroDe48hUseCase {

    RocaSemanal editar(EditarRocaSemanalCommand command);

    /** Campo {@code null} = no se toca (PATCH parcial). */
    record EditarRocaSemanalCommand(@NotNull UserId actorId, @NotNull RocaSemanalId rocaSemanalId, String titulo,
                                     List<String> accionesCriticas, String obstaculo, String contingencia,
                                     Integer autoevaluacionInicio) {

        public EditarRocaSemanalCommand {
            SelfValidating.validateConstructorArgs(EditarRocaSemanalCommand.class, actorId, rocaSemanalId, titulo,
                    accionesCriticas, obstaculo, contingencia, autoevaluacionInicio);
            if (accionesCriticas != null && accionesCriticas.size() != 3) {
                throw new IllegalArgumentException("si se envian acciones criticas deben ser exactamente 3");
            }
        }
    }
}
