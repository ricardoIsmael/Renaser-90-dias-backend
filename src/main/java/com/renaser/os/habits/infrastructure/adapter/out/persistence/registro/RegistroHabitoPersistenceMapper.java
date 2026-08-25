package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class RegistroHabitoPersistenceMapper {

    RegistroHabito toDomain(RegistroHabitoJpaEntity e) {
        return RegistroHabito.rehydrate(RegistroHabitoId.of(e.getId()), UserId.of(e.getParticipanteId()),
                HabitoId.of(e.getHabitoId()), e.getFechaEjecucion(), e.getDiaPrograma(), toDomainTipoDia(e.getTipoDia()),
                e.isEsOpcional(), toDomainEstado(e.getEstado()), e.getPuntosOtorgados(), e.getRespuestaTexto(),
                e.getCalificacionProductividad() != null ? e.getCalificacionProductividad().intValue() : null,
                e.getEntradaDiarioId(), e.getCompletadoEn(), e.getCreadoEn(), e.getActualizadoEn());
    }

    RegistroHabitoJpaEntity toEntity(RegistroHabito r) {
        return new RegistroHabitoJpaEntity(r.id().value(), r.participanteId().value(), r.habitoId().value(),
                r.fechaEjecucion(), (short) r.diaPrograma(), toJpaTipoDia(r.tipoDia()), r.esOpcional(),
                toJpaEstado(r.estado()), (short) r.puntosOtorgados(), r.respuestaTexto(),
                r.calificacionProductividad() != null ? r.calificacionProductividad().shortValue() : null,
                r.entradaDiarioId(), r.completadoEn(), r.creadoEn(), r.actualizadoEn());
    }

    private EstadoRegistroJpa toJpaEstado(EstadoRegistro estado) {
        return switch (estado) {
            case PENDIENTE -> EstadoRegistroJpa.PENDIENTE;
            case EN_CURSO -> EstadoRegistroJpa.EN_CURSO;
            case COMPLETADO -> EstadoRegistroJpa.COMPLETADO;
            case FALLIDO -> EstadoRegistroJpa.FALLIDO;
            case EXPIRADO -> EstadoRegistroJpa.EXPIRADO;
        };
    }

    private EstadoRegistro toDomainEstado(EstadoRegistroJpa jpa) {
        return switch (jpa) {
            case PENDIENTE -> EstadoRegistro.PENDIENTE;
            case EN_CURSO -> EstadoRegistro.EN_CURSO;
            case COMPLETADO -> EstadoRegistro.COMPLETADO;
            case FALLIDO -> EstadoRegistro.FALLIDO;
            case EXPIRADO -> EstadoRegistro.EXPIRADO;
        };
    }

    private TipoDiaJpa toJpaTipoDia(TipoDia tipoDia) {
        return switch (tipoDia) {
            case DISCIPLINA -> TipoDiaJpa.DISCIPLINA;
            case INTOXICACION -> TipoDiaJpa.INTOXICACION;
            case TODOS -> TipoDiaJpa.TODOS;
            case DOMINGO -> TipoDiaJpa.DOMINGO;
        };
    }

    private TipoDia toDomainTipoDia(TipoDiaJpa jpa) {
        return switch (jpa) {
            case DISCIPLINA -> TipoDia.DISCIPLINA;
            case INTOXICACION -> TipoDia.INTOXICACION;
            case TODOS -> TipoDia.TODOS;
            case DOMINGO -> TipoDia.DOMINGO;
        };
    }
}
