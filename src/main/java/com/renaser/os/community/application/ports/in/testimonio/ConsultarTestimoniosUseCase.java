package com.renaser.os.community.application.ports.in.testimonio;

import com.renaser.os.community.domain.model.testimonio.Testimonio;

import java.net.URI;
import java.util.List;

public interface ConsultarTestimoniosUseCase {

    List<TestimonioVista> listarDestacados();

    record TestimonioVista(Testimonio testimonio, String avatarUrl, URI fotoEventoUrl) {
    }
}
