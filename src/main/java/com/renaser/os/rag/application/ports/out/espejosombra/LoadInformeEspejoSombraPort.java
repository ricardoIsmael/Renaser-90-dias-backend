package com.renaser.os.rag.application.ports.out.espejosombra;

import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombraId;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoadInformeEspejoSombraPort {

    Optional<InformeEspejoSombra> byId(InformeEspejoSombraId id);

    /** Usado para la idempotencia del scheduler: si ya hay fila, no se regenera (UNIQUE de la tabla). */
    Optional<InformeEspejoSombra> porParticipanteYSemana(UserId participanteId, LocalDate semanaInicio);

    /** Más recientes primero. Lista vacía si el participante no tiene ningún informe todavía. */
    List<InformeEspejoSombra> deParticipante(UserId participanteId);
}
