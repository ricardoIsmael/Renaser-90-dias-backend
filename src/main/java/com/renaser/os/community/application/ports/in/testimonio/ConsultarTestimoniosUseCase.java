package com.renaser.os.community.application.ports.in.testimonio;

import com.renaser.os.community.domain.model.testimonio.Testimonio;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.net.URI;
import java.util.List;

public interface ConsultarTestimoniosUseCase {

    /**
     * Los testimonios de la formación de quien pide (spec 024). Solo Administración, Alquimista y Soporte TI eligen una; sin elegir leen «Todas»,
     * cada testimonio con la suya.
     */
    List<TestimonioVista> listarDestacados(UserId actorId, Formacion formacion);

    record TestimonioVista(Testimonio testimonio, String avatarUrl, URI fotoEventoUrl) {
    }
}
