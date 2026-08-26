package com.renaser.os.habits.infrastructure.adapter.out.persistence.diario;

import com.renaser.os.habits.api.EntradaDiarioSummary;
import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.habits.domain.model.diario.EntradaDiarioId;
import com.renaser.os.habits.domain.model.diario.TipoEntradaDiario;
import com.renaser.os.shared.domain.UserId;

/** A mano, no MapStruct: hay traduccion de enums y de value objects (CLAUDE.MD §5.4.5). */
final class EntradaDiarioPersistenceMapper {

    private EntradaDiarioPersistenceMapper() {
    }

    static EntradaDiario aDominio(EntradaDiarioJpaEntity entity) {
        return EntradaDiario.rehydrate(EntradaDiarioId.of(entity.getId()), UserId.of(entity.getParticipanteId()),
                entity.getFecha(), TipoEntradaDiario.valueOf(entity.getTipo().name()), entity.getContenidoTexto(),
                entity.getAudioBucket(), entity.getAudioRuta(), entity.getTranscripcion(), entity.getCreadoEn(),
                entity.getActualizadoEn());
    }

    static EntradaDiarioJpaEntity aEntidad(EntradaDiario entrada) {
        return new EntradaDiarioJpaEntity(entrada.id().value(), entrada.participanteId().value(), entrada.fecha(),
                TipoEntradaDiarioJpa.valueOf(entrada.tipo().name()), entrada.contenidoTexto(), entrada.audioBucket(),
                entrada.audioRuta(), entrada.transcripcion(), entrada.creadoEn(), entrada.actualizadoEn());
    }

    /**
     * Proyeccion publica (D-50). El {@code tipo} viaja como String a proposito: no se filtra
     * el enum interno de `habits` fuera de su {@code @NamedInterface}.
     */
    static EntradaDiarioSummary aResumen(EntradaDiarioJpaEntity entity) {
        return new EntradaDiarioSummary(entity.getId(), UserId.of(entity.getParticipanteId()), entity.getFecha(),
                entity.getTipo().name(), entity.getContenidoTexto(), entity.getTranscripcion());
    }
}
