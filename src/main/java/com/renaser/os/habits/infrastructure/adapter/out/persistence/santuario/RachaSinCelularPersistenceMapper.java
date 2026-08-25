package com.renaser.os.habits.infrastructure.adapter.out.persistence.santuario;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.EstadoRacha;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelularId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class RachaSinCelularPersistenceMapper {

    RachaSinCelular toDomain(RachaSinCelularJpaEntity e) {
        return RachaSinCelular.rehydrate(RachaSinCelularId.of(e.getId()), UserId.of(e.getParticipanteId()),
                RegistroHabitoId.of(e.getRegistroHabitoId()), e.getIniciadaEn(), e.getTerminadaEn(),
                e.getHorasObjetivo(), toDomainEstado(e.getEstado()), e.getDuracionMinutos(), e.getMotivoRuptura(),
                e.getCreadoEn(), e.getActualizadoEn());
    }

    RachaSinCelularJpaEntity toEntity(RachaSinCelular r) {
        return new RachaSinCelularJpaEntity(r.id().value(), r.registroHabitoId().value(), r.participanteId().value(),
                r.iniciadaEn(), r.terminadaEn(), (short) r.horasObjetivo(), toJpaEstado(r.estado()),
                r.duracionMinutos(), r.motivoRuptura(), r.creadoEn(), r.actualizadoEn());
    }

    private EstadoRachaJpa toJpaEstado(EstadoRacha estado) {
        return switch (estado) {
            case ACTIVA -> EstadoRachaJpa.ACTIVA;
            case COMPLETADA -> EstadoRachaJpa.COMPLETADA;
            case ROTA -> EstadoRachaJpa.ROTA;
            case EXPIRADA -> EstadoRachaJpa.EXPIRADA;
        };
    }

    private EstadoRacha toDomainEstado(EstadoRachaJpa jpa) {
        return switch (jpa) {
            case ACTIVA -> EstadoRacha.ACTIVA;
            case COMPLETADA -> EstadoRacha.COMPLETADA;
            case ROTA -> EstadoRacha.ROTA;
            case EXPIRADA -> EstadoRacha.EXPIRADA;
        };
    }
}
