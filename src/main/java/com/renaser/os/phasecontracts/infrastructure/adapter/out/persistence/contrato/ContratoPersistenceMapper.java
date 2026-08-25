package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.contrato;

import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFaseId;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class ContratoPersistenceMapper {

    ContratoFase toDomain(ContratoFaseJpaEntity e) {
        return ContratoFase.rehydrate(
                ContratoFaseId.of(e.getId()),
                UserId.of(e.getParticipanteId()),
                toDomainFase(e.getFase()),
                e.getBucket(),
                e.getRutaFirma(),
                e.getFirmadoEn(),
                e.getCreadoEn());
    }

    ContratoFaseJpaEntity toEntity(ContratoFase c) {
        return new ContratoFaseJpaEntity(
                c.id().value(),
                c.participanteId().value(),
                toJpaFase(c.fase()),
                c.bucket(),
                c.rutaFirma(),
                c.firmadoEn(),
                c.creadoEn());
    }

    FaseProgramaJpa toJpaFase(FasePrograma fase) {
        return switch (fase) {
            case FASE_1_RENACER -> FaseProgramaJpa.FASE_1_RENACER;
            case FASE_2_DESARROLLO -> FaseProgramaJpa.FASE_2_DESARROLLO;
            case FASE_3_GUERRERO_ALQUIMISTA -> FaseProgramaJpa.FASE_3_GUERRERO_ALQUIMISTA;
            case FASE_4_ASCENSION -> FaseProgramaJpa.FASE_4_ASCENSION;
        };
    }

    private FasePrograma toDomainFase(FaseProgramaJpa jpa) {
        return switch (jpa) {
            case FASE_1_RENACER -> FasePrograma.FASE_1_RENACER;
            case FASE_2_DESARROLLO -> FasePrograma.FASE_2_DESARROLLO;
            case FASE_3_GUERRERO_ALQUIMISTA -> FasePrograma.FASE_3_GUERRERO_ALQUIMISTA;
            case FASE_4_ASCENSION -> FasePrograma.FASE_4_ASCENSION;
        };
    }
}
