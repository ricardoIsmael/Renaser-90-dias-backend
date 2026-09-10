package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.PeriodoGrupo;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class CelulaPersistenceMapper {

    Celula toDomain(CelulaJpaEntity e) {
        return Celula.rehydrate(CelulaId.of(e.getId()), e.getNombre(),
                e.getMentorId() != null ? UserId.of(e.getMentorId()) : null, CohorteId.of(e.getCohorteId()),
                e.getUrlVideollamada(), e.getProximaSesionEn(), e.getCreadoEn(), e.getActualizadoEn(),
                e.getTipo(), e.getCapacidadMaxima(), Celula.periodoDe(e.getPeriodoInicio(), e.getPeriodoFin()));
    }

    /**
     * {@code creadoEn}/{@code actualizadoEn} viajan SIEMPRE con valor: el agregado los fija en su
     * factoria y nunca son null. La columna es NOT NULL con DEFAULT now(), y un NULL explicito no
     * activa el DEFAULT — lo pisa y el INSERT revienta (defecto conocido del repo). Las dos de V48
     * si son nulables, asi que ahi el null es un valor legitimo: grupo sin periodo.
     */
    CelulaJpaEntity toEntity(Celula c) {
        PeriodoGrupo periodo = c.periodo();
        return new CelulaJpaEntity(c.id().value(), c.nombre(), c.mentorId() != null ? c.mentorId().value() : null,
                c.cohorteId().value(), c.urlVideollamada(), c.proximaSesionEn(), c.creadoEn(), c.actualizadoEn(),
                c.tipo(), c.capacidadMaxima(), periodo != null ? periodo.inicio() : null,
                periodo != null ? periodo.fin() : null);
    }
}
