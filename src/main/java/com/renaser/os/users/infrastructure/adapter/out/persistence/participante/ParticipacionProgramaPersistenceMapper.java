package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/** Mapper a mano (D-28): la columna `dia_programa` es `smallint` (short) contra el
 * `int` del dominio, y `fase`/`timezone` necesitan traduccion — no es mapeo plano. */
@Component
class ParticipacionProgramaPersistenceMapper {

    ParticipacionPrograma toDomain(ParticipacionProgramaJpaEntity e) {
        return ParticipacionPrograma.rehydrate(
                UserId.of(e.getUsuarioId()),
                e.getMentorId() == null ? null : UserId.of(e.getMentorId()),
                e.getCelulaId(),
                e.getDiaPrograma(),
                toDomainFase(e.getFase()),
                e.getFechaInicio(),
                e.getProgramaActivadoEn(),
                ZoneId.of(e.getTimezone()),
                e.isProgramaCompletado(),
                e.getDiaPostPrograma(),
                e.getCreadoEn(),
                e.getActualizadoEn());
    }

    ParticipacionProgramaJpaEntity toEntity(ParticipacionPrograma p) {
        return new ParticipacionProgramaJpaEntity(
                p.participanteId().value(),
                p.mentorId() == null ? null : p.mentorId().value(),
                p.celulaId(),
                (short) p.diaPrograma(),
                toJpaFase(p.fase()),
                p.fechaInicio(),
                p.programaActivadoEn(),
                p.timezone().getId(),
                p.programaCompletado(),
                (short) p.diaPostPrograma(),
                p.creadoEn(),
                p.actualizadoEn());
    }

    private FaseProgramaJpa toJpaFase(FasePrograma fase) {
        return switch (fase) {
            case PHASE_1_REBIRTH -> FaseProgramaJpa.FASE_1_RENACER;
            case PHASE_2_DEVELOPMENT -> FaseProgramaJpa.FASE_2_DESARROLLO;
            case PHASE_3_ALCHEMIST_WARRIOR -> FaseProgramaJpa.FASE_3_GUERRERO_ALQUIMISTA;
            case PHASE_4_ASCENSION -> FaseProgramaJpa.FASE_4_ASCENSION;
        };
    }

    private FasePrograma toDomainFase(FaseProgramaJpa jpa) {
        return switch (jpa) {
            case FASE_1_RENACER -> FasePrograma.PHASE_1_REBIRTH;
            case FASE_2_DESARROLLO -> FasePrograma.PHASE_2_DEVELOPMENT;
            case FASE_3_GUERRERO_ALQUIMISTA -> FasePrograma.PHASE_3_ALCHEMIST_WARRIOR;
            case FASE_4_ASCENSION -> FasePrograma.PHASE_4_ASCENSION;
        };
    }
}
