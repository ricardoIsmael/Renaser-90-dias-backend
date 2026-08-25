package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.curso.SeccionCursoId;
import com.renaser.os.academy.domain.model.curso.TipoVideoLeccion;
import org.springframework.stereotype.Component;

@Component
class LeccionPersistenceMapper {

    Leccion toDomain(LeccionJpaEntity e) {
        SeccionCursoId seccionId = e.getSeccionId() == null ? null : SeccionCursoId.of(e.getSeccionId());
        return new Leccion(LeccionId.of(e.getId()), CursoId.of(e.getCursoId()), seccionId, e.getTitulo(),
                e.getOrden(), e.getCuerpoHtml(), e.getCuerpoMd(), toDomainVideoTipo(e.getVideoTipo()),
                e.getVideoUrl(), e.getVideoMiniaturaUrl(), e.getVideoDuracionMs(), e.getCreadoEn(),
                e.getActualizadoEn());
    }

    private TipoVideoLeccion toDomainVideoTipo(TipoVideoLeccionJpa jpa) {
        if (jpa == null) {
            return null;
        }
        return switch (jpa) {
            case YOUTUBE -> TipoVideoLeccion.YOUTUBE;
            case STORAGE -> TipoVideoLeccion.STORAGE;
        };
    }
}
