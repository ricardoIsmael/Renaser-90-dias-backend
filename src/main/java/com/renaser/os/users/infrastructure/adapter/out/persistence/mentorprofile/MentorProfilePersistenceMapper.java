package com.renaser.os.users.infrastructure.adapter.out.persistence.mentorprofile;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.mentorprofile.MentorLevel;
import com.renaser.os.users.domain.model.mentorprofile.MentorOperationalStatus;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import org.springframework.stereotype.Component;

@Component
class MentorProfilePersistenceMapper {

    MentorProfile toDomain(MentorProfileJpaEntity e) {
        return MentorProfile.rehydrate(
                UserId.of(e.getUsuarioId()),
                toDomainLevel(e.getNivel()),
                toDomainStatus(e.getEstadoOperativo()),
                e.getBio(),
                e.getCreadoEn(),
                e.getActualizadoEn());
    }

    MentorProfileJpaEntity toEntity(MentorProfile p) {
        return new MentorProfileJpaEntity(
                p.userId().value(),
                toJpaLevel(p.level()),
                toJpaStatus(p.operationalStatus()),
                p.bio(),
                p.createdAt(),
                p.updatedAt());
    }

    private NivelMentorJpa toJpaLevel(MentorLevel level) {
        return NivelMentorJpa.valueOf(level.name());
    }

    private MentorLevel toDomainLevel(NivelMentorJpa jpa) {
        return MentorLevel.valueOf(jpa.name());
    }

    private EstadoOperativoJpa toJpaStatus(MentorOperationalStatus status) {
        return switch (status) {
            case GREEN -> EstadoOperativoJpa.VERDE;
            case YELLOW -> EstadoOperativoJpa.AMARILLO;
            case RED -> EstadoOperativoJpa.ROJO;
        };
    }

    private MentorOperationalStatus toDomainStatus(EstadoOperativoJpa jpa) {
        return switch (jpa) {
            case VERDE -> MentorOperationalStatus.GREEN;
            case AMARILLO -> MentorOperationalStatus.YELLOW;
            case ROJO -> MentorOperationalStatus.RED;
        };
    }
}
