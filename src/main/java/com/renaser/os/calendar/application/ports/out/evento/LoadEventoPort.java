package com.renaser.os.calendar.application.ports.out.evento;

import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadEventoPort {

    Optional<Evento> byId(EventoId id);

    /** Candidatos que PODRIAN tener una ocurrencia en [desde,hasta] — el podado final
     * (incluida `repeticiones`) lo hace {@code ExpansorOcurrencias}. Solo PUBLICADO. */
    List<Evento> candidatosParaVisor(Instant desde, Instant hasta);

    /**
     * Candidatos para el generador de recordatorios: PUBLICADO y (arranca en
     * [ahora,hastaMax] O es recurrente O notifica-al-crear y se creo despues de
     * {@code desdeAnuncio}) — mismo criterio que {@code generar()} en reminderService.ts.
     */
    List<Evento> candidatosParaRecordatorios(Instant ahora, Instant hastaMax, Instant desdeAnuncio);
}
