package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.ColorPareto;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamaestra.EjeObjetivoJpa;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class RocaDiariaPersistenceMapper {

    RocaDiaria toDomain(RocaDiariaJpaEntity e) {
        RocaSemanalId rocaSemanalId = e.getRocaSemanalId() == null ? null : RocaSemanalId.of(e.getRocaSemanalId());
        return RocaDiaria.rehydrate(RocaDiariaId.of(e.getId()), UserId.of(e.getParticipanteId()), e.getFecha(),
                e.getPosicion(), e.getTitulo(), e.getDescripcion(), toDomainColor(e.getColor()),
                e.getPuntajeImpacto(), e.isEsDelegable(), toDomainEje(e.getEje()), rocaSemanalId, e.getHoraInicio(),
                e.getHoraFin(), e.isCompletada(), e.getCompletadaEn(), e.getPuntosOtorgados(), e.getCreadoEn(),
                e.getActualizadoEn());
    }

    RocaDiariaJpaEntity toEntity(RocaDiaria r) {
        UUID rocaSemanalId = r.rocaSemanalId() == null ? null : r.rocaSemanalId().value();
        return new RocaDiariaJpaEntity(r.id().value(), r.participanteId().value(), r.fecha(), (short) r.posicion(),
                r.titulo(), r.descripcion(), toJpaColor(r.color()), (short) r.puntajeImpacto(), r.esDelegable(),
                toJpaEje(r.eje()), rocaSemanalId, r.horaInicio(), r.horaFin(), r.completada(), r.completadaEn(),
                (short) r.puntosOtorgados(), r.creadoEn(), r.actualizadoEn());
    }

    private ColorParetoJpa toJpaColor(ColorPareto color) {
        return switch (color) {
            case VERDE -> ColorParetoJpa.VERDE;
            case AMARILLA -> ColorParetoJpa.AMARILLA;
            case ROJA -> ColorParetoJpa.ROJA;
        };
    }

    private ColorPareto toDomainColor(ColorParetoJpa jpa) {
        return switch (jpa) {
            case VERDE -> ColorPareto.VERDE;
            case AMARILLA -> ColorPareto.AMARILLA;
            case ROJA -> ColorPareto.ROJA;
        };
    }

    private EjeObjetivoJpa toJpaEje(EjeObjetivo eje) {
        return switch (eje) {
            case CUERPO -> EjeObjetivoJpa.CUERPO;
            case TRABAJO -> EjeObjetivoJpa.TRABAJO;
            case RELACIONES -> EjeObjetivoJpa.RELACIONES;
        };
    }

    private EjeObjetivo toDomainEje(EjeObjetivoJpa jpa) {
        return switch (jpa) {
            case CUERPO -> EjeObjetivo.CUERPO;
            case TRABAJO -> EjeObjetivo.TRABAJO;
            case RELACIONES -> EjeObjetivo.RELACIONES;
        };
    }
}
