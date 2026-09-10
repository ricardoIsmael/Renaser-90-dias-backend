package com.renaser.os.community.infrastructure.adapter.out.persistence.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.PeriodoAsignacion;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class AsignacionCelulaPersistenceMapper {

    AsignacionCelula toDomain(AsignacionCelulaJpaEntity entidad) {
        PeriodoAsignacion periodo = entidad.getFin() == null
                ? PeriodoAsignacion.abierto(entidad.getInicio())
                : PeriodoAsignacion.cerrado(entidad.getInicio(), entidad.getFin());
        return AsignacionCelula.rehydrate(
                AsignacionId.of(entidad.getId()),
                CelulaId.of(entidad.getCelulaId()),
                UserId.of(entidad.getUsuarioId()),
                entidad.getFuncion(),
                periodo,
                entidad.getMotivo(),
                entidad.getActorId() == null ? null : UserId.of(entidad.getActorId()),
                entidad.getClaveOperacion());
    }

    AsignacionCelulaJpaEntity toEntity(AsignacionCelula asignacion) {
        return new AsignacionCelulaJpaEntity(
                asignacion.id().value(),
                asignacion.celulaId().value(),
                asignacion.usuarioId().value(),
                asignacion.funcion(),
                asignacion.periodo().inicio(),
                asignacion.periodo().fin(),
                asignacion.motivo(),
                asignacion.actorId() == null ? null : asignacion.actorId().value(),
                asignacion.claveOperacion(),
                null);
    }
}
