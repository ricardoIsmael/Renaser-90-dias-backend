package com.renaser.os.community.application.ports.in.testimonio;

import com.renaser.os.community.application.ports.in.testimonio.ConsultarTestimoniosUseCase.TestimonioVista;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public interface CrearTestimonioUseCase {

    /** {@code actorId} null = formulario publico sin sesion — el testimonio manual del
     * codigo viejo no exige estar logueado (testimonios/repository.ts:24-37). */
    TestimonioVista crear(CrearTestimonioCommand command);

    record CrearTestimonioCommand(UserId actorId, @NotBlank String nombre, String rolTexto, @NotBlank String texto,
                                   @Min(1) @Max(5) int estrellas) {

        public CrearTestimonioCommand {
            SelfValidating.validateConstructorArgs(CrearTestimonioCommand.class, actorId, nombre, rolTexto, texto,
                    estrellas);
        }
    }
}
