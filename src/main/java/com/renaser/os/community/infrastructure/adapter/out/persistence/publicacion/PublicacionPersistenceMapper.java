package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.TipoPublicacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

/** `medias_publicacion` no es un `@Entity` propio: se lee/escribe con SQL nativo en
 * {@link PublicacionPersistenceAdapter} (mismo criterio que las evidencias de `rocks`) —
 * el mapper la recibe/entrega ya resuelta. */
@Component
class PublicacionPersistenceMapper {

    Publicacion toDomain(PublicacionJpaEntity e, List<MediaPublicacion> media) {
        return Publicacion.rehydrate(PublicacionId.of(e.getId()), UserId.of(e.getAutorId()), toDomainTipo(e.getTipo()),
                e.getCategoriaClave(), e.getTexto(), media, e.isOculta(), e.getCreadoEn(), e.getActualizadoEn());
    }

    PublicacionJpaEntity toEntity(Publicacion p) {
        return new PublicacionJpaEntity(p.id().value(), p.autorId().value(), toJpaTipo(p.tipo()), p.categoriaClave(),
                p.texto(), p.oculta(), p.creadoEn(), p.actualizadoEn());
    }

    TipoPublicacionJpa toJpaTipo(TipoPublicacion tipo) {
        return switch (tipo) {
            case MANUAL -> TipoPublicacionJpa.MANUAL;
            case HITO_AUTOMATICO -> TipoPublicacionJpa.HITO_AUTOMATICO;
            case GUERRERO_CAIDO -> TipoPublicacionJpa.GUERRERO_CAIDO;
        };
    }

    private TipoPublicacion toDomainTipo(TipoPublicacionJpa jpa) {
        return switch (jpa) {
            case MANUAL -> TipoPublicacion.MANUAL;
            case HITO_AUTOMATICO -> TipoPublicacion.HITO_AUTOMATICO;
            case GUERRERO_CAIDO -> TipoPublicacion.GUERRERO_CAIDO;
        };
    }
}
