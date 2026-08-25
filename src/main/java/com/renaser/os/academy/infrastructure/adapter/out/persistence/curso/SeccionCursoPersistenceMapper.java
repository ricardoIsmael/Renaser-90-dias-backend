package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.SeccionCurso;
import com.renaser.os.academy.domain.model.curso.SeccionCursoId;
import org.springframework.stereotype.Component;

@Component
class SeccionCursoPersistenceMapper {

    SeccionCurso toDomain(SeccionCursoJpaEntity e) {
        return new SeccionCurso(SeccionCursoId.of(e.getId()), CursoId.of(e.getCursoId()), e.getTitulo(),
                e.getOrden(), e.getDiaDesbloqueo() == null ? null : e.getDiaDesbloqueo().intValue());
    }
}
