package com.renaser.os.academy.infrastructure.adapter.out.persistence.asignacion;

import com.renaser.os.academy.domain.model.asignacion.AsignacionCurso;
import com.renaser.os.academy.domain.model.asignacion.AsignacionCursoId;
import com.renaser.os.academy.domain.model.asignacion.GrupoId;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class AsignacionCursoPersistenceMapper {

    AsignacionCurso toDomain(AsignacionCursoJpaEntity e) {
        UserId usuarioId = e.getUsuarioId() == null ? null : UserId.of(e.getUsuarioId());
        GrupoId grupoId = e.getGrupoId() == null ? null : GrupoId.of(e.getGrupoId());
        UserId asignadaPor = e.getAsignadaPor() == null ? null : UserId.of(e.getAsignadaPor());
        return new AsignacionCurso(AsignacionCursoId.of(e.getId()), CursoId.of(e.getCursoId()), usuarioId, grupoId,
                e.getDesde(), e.getHasta(), e.getRevocadaEn(), asignadaPor, e.getCreadoEn());
    }
}
