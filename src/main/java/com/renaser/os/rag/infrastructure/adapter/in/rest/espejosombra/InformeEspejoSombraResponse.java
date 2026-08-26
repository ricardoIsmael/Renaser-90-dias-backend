package com.renaser.os.rag.infrastructure.adapter.in.rest.espejosombra;

import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;
import com.renaser.os.rag.domain.model.espejosombra.PreguntaConfrontacion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Proyección explícita (CLAUDE.MD §5.4.2/§8) — nunca el dominio serializado directo. */
public record InformeEspejoSombraResponse(UUID id, UUID participanteId, LocalDate semanaInicio, int cantidadEntradas,
                                           String patronDominante, int pctPasado, int pctPresente, int pctFuturo,
                                           String insight, List<String> preguntasConfrontacion, Instant creadoEn) {

    public static InformeEspejoSombraResponse from(InformeEspejoSombra informe) {
        List<String> preguntas = informe.preguntas().stream()
                .sorted(Comparator.comparingInt(PreguntaConfrontacion::orden))
                .map(PreguntaConfrontacion::pregunta)
                .toList();
        return new InformeEspejoSombraResponse(informe.id().value(), informe.participanteId().value(),
                informe.semanaInicio(), informe.cantidadEntradas(), informe.patronDominante(),
                informe.distribucion().pctPasado(), informe.distribucion().pctPresente(),
                informe.distribucion().pctFuturo(), informe.insight(), preguntas, informe.creadoEn());
    }
}
