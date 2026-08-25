package com.renaser.os.community.application.ports.in.testimonio;

import com.renaser.os.community.application.ports.in.testimonio.ConsultarTestimoniosUseCase.TestimonioVista;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public interface PromoverPublicacionATestimonioUseCase {

    /** Solo ADMIN/ALCHEMIST (testimonios/service.ts:64-66). La portada es la primera foto
     * del carrusel de la publicacion. */
    TestimonioVista promover(PromoverPublicacionCommand command);

    record PromoverPublicacionCommand(@NotNull UserId actorId, @NotNull PublicacionId publicacionId,
                                       @Min(1) @Max(5) int estrellas) {

        public PromoverPublicacionCommand {
            SelfValidating.validateConstructorArgs(PromoverPublicacionCommand.class, actorId, publicacionId,
                    estrellas);
        }
    }
}
