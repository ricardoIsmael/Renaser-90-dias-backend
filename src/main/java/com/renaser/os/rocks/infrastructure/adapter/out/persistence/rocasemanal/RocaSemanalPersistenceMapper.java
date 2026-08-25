package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocasemanal;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
class RocaSemanalPersistenceMapper {

    RocaSemanal toDomain(RocaSemanalJpaEntity e) {
        List<AccionCritica> acciones = e.getAcciones().stream()
                .map(a -> new AccionCritica(a.getOrden(), a.getDescripcion()))
                .sorted(Comparator.comparingInt(AccionCritica::orden))
                .toList();
        Integer autoevaluacionInicio = e.getAutoevaluacionInicio() == null ? null : e.getAutoevaluacionInicio().intValue();
        Integer autoevaluacionFin = e.getAutoevaluacionFin() == null ? null : e.getAutoevaluacionFin().intValue();
        return RocaSemanal.rehydrate(RocaSemanalId.of(e.getId()), RocaMaestraId.of(e.getRocaMaestraId()),
                e.getNumeroSemana(), e.getTitulo(), acciones, e.getObstaculo(), e.getContingencia(),
                autoevaluacionInicio, autoevaluacionFin, e.getBloqueoPrincipal(), e.getCorreccion(), e.getCreadoEn(),
                e.getActualizadoEn());
    }

    RocaSemanalJpaEntity toEntity(RocaSemanal r) {
        List<AccionCriticaEmbeddable> acciones = r.acciones().stream()
                .map(a -> new AccionCriticaEmbeddable((short) a.orden(), a.descripcion()))
                .toList();
        Short autoevaluacionInicio = r.autoevaluacionInicio() == null ? null : r.autoevaluacionInicio().shortValue();
        Short autoevaluacionFin = r.autoevaluacionFin() == null ? null : r.autoevaluacionFin().shortValue();
        return new RocaSemanalJpaEntity(r.id().value(), r.rocaMaestraId().value(), (short) r.numeroSemana(),
                r.titulo(), new java.util.ArrayList<>(acciones), r.obstaculo(), r.contingencia(),
                autoevaluacionInicio, autoevaluacionFin, r.bloqueoPrincipal(), r.correccion(), r.creadoEn(),
                r.actualizadoEn());
    }
}
