package com.renaser.os.rocks.infrastructure.adapter.out.persistence.verdugo;

import com.renaser.os.rocks.domain.model.verdugo.DestinoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugoId;
import com.renaser.os.rocks.domain.model.verdugo.ResultadoVerdugo;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class EventoVerdugoPersistenceMapper {

    EventoVerdugo toDomain(EventoVerdugoJpaEntity e) {
        DestinoVerdugo destinoTipo = e.getRocaDiariaId() != null ? DestinoVerdugo.ROCA_DIARIA
                : DestinoVerdugo.REGISTRO_HABITO;
        var destinoId = e.getRocaDiariaId() != null ? e.getRocaDiariaId() : e.getRegistroHabitoId();
        ResultadoVerdugo resultado = e.getResultado() == null ? null : toDomainResultado(e.getResultado());
        return EventoVerdugo.rehydrate(EventoVerdugoId.of(e.getId()), UserId.of(e.getParticipanteId()), destinoTipo,
                destinoId, e.getDisparadoEn(), resultado, e.getResueltoEn(), e.getCreadoEn(), e.getActualizadoEn());
    }

    EventoVerdugoJpaEntity toEntity(EventoVerdugo v) {
        var registroHabitoId = v.destinoTipo() == DestinoVerdugo.REGISTRO_HABITO ? v.destinoId() : null;
        var rocaDiariaId = v.destinoTipo() == DestinoVerdugo.ROCA_DIARIA ? v.destinoId() : null;
        ResultadoVerdugoJpa resultado = v.resultado() == null ? null : toJpaResultado(v.resultado());
        return new EventoVerdugoJpaEntity(v.id().value(), v.participanteId().value(), registroHabitoId,
                rocaDiariaId, v.disparadoEn(), resultado, v.resueltoEn(), v.creadoEn(), v.actualizadoEn());
    }

    private ResultadoVerdugoJpa toJpaResultado(ResultadoVerdugo resultado) {
        return switch (resultado) {
            case COMPLETADO -> ResultadoVerdugoJpa.COMPLETADO;
            case POSTERGADO -> ResultadoVerdugoJpa.POSTERGADO;
            case POSPUESTO_30 -> ResultadoVerdugoJpa.POSPUESTO_30;
            case IGNORADO -> ResultadoVerdugoJpa.IGNORADO;
        };
    }

    private ResultadoVerdugo toDomainResultado(ResultadoVerdugoJpa jpa) {
        return switch (jpa) {
            case COMPLETADO -> ResultadoVerdugo.COMPLETADO;
            case POSTERGADO -> ResultadoVerdugo.POSTERGADO;
            case POSPUESTO_30 -> ResultadoVerdugo.POSPUESTO_30;
            case IGNORADO -> ResultadoVerdugo.IGNORADO;
        };
    }
}
