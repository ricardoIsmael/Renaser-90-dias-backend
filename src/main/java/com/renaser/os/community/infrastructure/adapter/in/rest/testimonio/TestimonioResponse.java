package com.renaser.os.community.infrastructure.adapter.in.rest.testimonio;

import com.renaser.os.community.application.ports.in.testimonio.ConsultarTestimoniosUseCase.TestimonioVista;
import com.renaser.os.community.domain.model.testimonio.Testimonio;

public record TestimonioResponse(String id, String userId, String wallPostId, String nombre, String rol,
                                  String avatarUrl, String fotoEventoUrl, String texto, int estrellas,
                                  String createdAt) {

    public static TestimonioResponse from(TestimonioVista vista) {
        Testimonio t = vista.testimonio();
        return new TestimonioResponse(t.id().toString(), t.usuarioId() != null ? t.usuarioId().toString() : null,
                t.publicacionMuroId() != null ? t.publicacionMuroId().toString() : null, t.nombre(), t.rolTexto(),
                vista.avatarUrl(), vista.fotoEventoUrl() != null ? vista.fotoEventoUrl().toString() : null,
                t.texto(), t.estrellas(), t.creadoEn().toString());
    }
}
