package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.FotoDelGrupo;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;

/**
 * La foto propia de un grupo (D-212, decisión del dueño del 2026-09-27): la cambian «Admin y el mentor de
 * ese grupo». El administrador, la de cualquier grupo; el mentor, solo la del grupo que acompaña hoy
 * (su asignación vigente con función MENTOR). Nadie más: ni los aprendices, ni el mentor de otro grupo,
 * ni el Alquimista, ni una cuenta suspendida.
 *
 * <p>En los tres métodos, en este orden: la cuenta está activa (403), el grupo existe (404) y quien pide
 * puede cambiarla (403).
 */
public interface CambiarFotoDelGrupoUseCase {

    /**
     * Guarda la foto nueva y borra la anterior del almacenamiento.
     *
     * @throws IllegalArgumentException si no es una imagen JPEG o PNG que se pueda leer, o pesa más de 2 MB
     */
    FotoDelGrupo cambiar(CambiarFotoDelGrupoCommand command);

    /** El grupo vuelve a la foto de Renaser. Sin foto propia no hace nada. */
    void volverALaDeRenaser(UserId actorId, CelulaId celulaId);

    /** Si el grupo tiene foto propia y desde cuándo, para quien puede cambiarla. */
    Optional<FotoDelGrupo> actual(UserId actorId, CelulaId celulaId);

    record CambiarFotoDelGrupoCommand(@NotNull UserId actorId, @NotNull CelulaId celulaId, @NotNull byte[] foto,
                                      String tipo) {

        public CambiarFotoDelGrupoCommand {
            SelfValidating.validateConstructorArgs(CambiarFotoDelGrupoCommand.class, actorId, celulaId, foto, tipo);
        }
    }
}
