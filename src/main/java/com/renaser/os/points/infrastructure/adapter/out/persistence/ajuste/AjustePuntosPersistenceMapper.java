package com.renaser.os.points.infrastructure.adapter.out.persistence.ajuste;

import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.domain.model.ajuste.AjustePuntos;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class AjustePuntosPersistenceMapper {

    AjustePuntos toDomain(AjustePuntosJpaEntity e) {
        return AjustePuntos.rehydrate(e.getId(), UserId.of(e.getParticipanteId()), toDomainMotivo(e.getMotivo()),
                e.getDelta(), e.getDeltaAplicado(), e.getSaldoPosterior(), e.getNota(), e.getCreadoEn());
    }

    AjustePuntosJpaEntity toEntity(AjustePuntos a) {
        return new AjustePuntosJpaEntity(a.id(), a.participanteId().value(), toJpaMotivo(a.motivo()),
                (short) a.delta(), (short) a.deltaAplicado(), a.saldoPosterior(), a.nota(), a.creadoEn());
    }

    private MotivoPuntosJpa toJpaMotivo(MotivoPuntos motivo) {
        return switch (motivo) {
            case HABIT_COMPLETED -> MotivoPuntosJpa.HABITO_COMPLETADO;
            case HABIT_EXTENDED -> MotivoPuntosJpa.HABITO_EXTENDIDO;
            case MISSED_HABIT -> MotivoPuntosJpa.HABITO_PERDIDO;
            case LATE_HABIT -> MotivoPuntosJpa.HABITO_TARDE;
            case STREAK_BONUS -> MotivoPuntosJpa.BONO_RACHA;
            case SANCTUARY_BREAK -> MotivoPuntosJpa.SANTUARIO_ROTO;
            case INVALID_EVIDENCE -> MotivoPuntosJpa.EVIDENCIA_INVALIDA;
            case INVALID_EVIDENCE_REVOKED -> MotivoPuntosJpa.EVIDENCIA_INVALIDA_REVERTIDA;
            case PHONE_FREE_WEEK_MISSED -> MotivoPuntosJpa.SEMANA_SIN_CELULAR_PERDIDA;
            case ROCK_COMPLETED -> MotivoPuntosJpa.ROCA_COMPLETADA;
            case ROCK_EXTENDED -> MotivoPuntosJpa.ROCA_EXTENDIDA;
            case MANUAL_ADJUSTMENT -> MotivoPuntosJpa.AJUSTE_MANUAL;
        };
    }

    private MotivoPuntos toDomainMotivo(MotivoPuntosJpa jpa) {
        return switch (jpa) {
            case HABITO_COMPLETADO -> MotivoPuntos.HABIT_COMPLETED;
            case HABITO_EXTENDIDO -> MotivoPuntos.HABIT_EXTENDED;
            case HABITO_PERDIDO -> MotivoPuntos.MISSED_HABIT;
            case HABITO_TARDE -> MotivoPuntos.LATE_HABIT;
            case BONO_RACHA -> MotivoPuntos.STREAK_BONUS;
            case SANTUARIO_ROTO -> MotivoPuntos.SANCTUARY_BREAK;
            case EVIDENCIA_INVALIDA -> MotivoPuntos.INVALID_EVIDENCE;
            case EVIDENCIA_INVALIDA_REVERTIDA -> MotivoPuntos.INVALID_EVIDENCE_REVOKED;
            case SEMANA_SIN_CELULAR_PERDIDA -> MotivoPuntos.PHONE_FREE_WEEK_MISSED;
            case ROCA_COMPLETADA -> MotivoPuntos.ROCK_COMPLETED;
            case ROCA_EXTENDIDA -> MotivoPuntos.ROCK_EXTENDED;
            case AJUSTE_MANUAL -> MotivoPuntos.MANUAL_ADJUSTMENT;
        };
    }
}
