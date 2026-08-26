package com.renaser.os.rag.infrastructure.adapter.out.persistence.espejosombra;

import com.renaser.os.rag.domain.model.espejosombra.DistribucionTemporal;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombraId;
import com.renaser.os.rag.domain.model.espejosombra.PreguntaConfrontacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class InformeEspejoSombraPersistenceMapper {

    InformeEspejoSombra toDomain(InformeEspejoSombraJpaEntity e) {
        DistribucionTemporal distribucion = new DistribucionTemporal(e.getPctPasado(), e.getPctPresente(),
                e.getPctFuturo());
        List<PreguntaConfrontacion> preguntas = e.getPreguntas().stream()
                .map(p -> new PreguntaConfrontacion(p.getOrden(), p.getPregunta()))
                .toList();
        return InformeEspejoSombra.rehydrate(InformeEspejoSombraId.of(e.getId()), UserId.of(e.getParticipanteId()),
                e.getSemanaInicio(), e.getCantidadEntradas(), e.getPatronDominante(), distribucion, e.getInsight(),
                preguntas, e.getCreadoEn());
    }

    InformeEspejoSombraJpaEntity toEntity(InformeEspejoSombra d) {
        InformeEspejoSombraJpaEntity e = new InformeEspejoSombraJpaEntity();
        e.setId(d.id().value());
        e.setParticipanteId(d.participanteId().value());
        e.setSemanaInicio(d.semanaInicio());
        e.setCantidadEntradas((short) d.cantidadEntradas());
        e.setPatronDominante(d.patronDominante());
        e.setPctPasado((short) d.distribucion().pctPasado());
        e.setPctPresente((short) d.distribucion().pctPresente());
        e.setPctFuturo((short) d.distribucion().pctFuturo());
        e.setInsight(d.insight());
        List<PreguntaConfrontacionEmbeddable> preguntas = d.preguntas().stream()
                .map(p -> new PreguntaConfrontacionEmbeddable((short) p.orden(), p.pregunta()))
                .toList();
        e.setPreguntas(new ArrayList<>(preguntas));
        e.setCreadoEn(d.creadoEn());
        return e;
    }
}
