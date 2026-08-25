package com.renaser.os.community.infrastructure.adapter.out.persistence.testimonio;

import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.testimonio.Testimonio;
import com.renaser.os.community.domain.model.testimonio.TestimonioId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class TestimonioPersistenceMapper {

    Testimonio toDomain(TestimonioJpaEntity e) {
        return Testimonio.rehydrate(TestimonioId.of(e.getId()),
                e.getUsuarioId() != null ? UserId.of(e.getUsuarioId()) : null,
                e.getPublicacionMuroId() != null ? PublicacionId.of(e.getPublicacionMuroId()) : null, e.getNombre(),
                e.getRolTexto(), e.getAvatarUrl(), e.getFotoEventoRuta(), e.getTexto(), e.getEstrellas(),
                e.isDestacado(), e.getCreadoEn());
    }

    TestimonioJpaEntity toEntity(Testimonio t) {
        return new TestimonioJpaEntity(t.id().value(), t.usuarioId() != null ? t.usuarioId().value() : null,
                t.publicacionMuroId() != null ? t.publicacionMuroId().value() : null, t.nombre(), t.rolTexto(),
                t.avatarUrl(), t.fotoEventoRuta(), t.texto(), (short) t.estrellas(), t.destacado(), t.creadoEn());
    }
}
