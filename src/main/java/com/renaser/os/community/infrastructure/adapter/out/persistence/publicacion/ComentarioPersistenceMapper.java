package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.community.domain.model.publicacion.Comentario;
import com.renaser.os.community.domain.model.publicacion.ComentarioId;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class ComentarioPersistenceMapper {

    Comentario toDomain(ComentarioJpaEntity e) {
        return Comentario.rehydrate(ComentarioId.of(e.getId()), PublicacionId.of(e.getPublicacionId()),
                UserId.of(e.getAutorId()), e.getTexto(), e.isOculto(), e.getCreadoEn(), e.getActualizadoEn());
    }

    ComentarioJpaEntity toEntity(Comentario c) {
        return new ComentarioJpaEntity(c.id().value(), c.publicacionId().value(), c.autorId().value(), c.texto(),
                c.oculto(), c.creadoEn(), c.actualizadoEn());
    }
}
