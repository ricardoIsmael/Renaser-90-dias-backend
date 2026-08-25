package com.renaser.os.calendar.application.ports.in.evento;

import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface ListarEventosParaVisorUseCase {

    /** Rango maximo de 90 dias — EventRangeQuery del repo viejo (schema.ts). */
    int RANGO_MAXIMO_DIAS = 90;

    List<OcurrenciaVista> listar(UserId actorId, Instant desde, Instant hasta);

    record OcurrenciaVista(Evento evento, String coverUrl, Instant inicioOcurrencia, Instant iniciaEn,
                            Integer duracionMinutos, String titulo, EstadoConfirmacion viewerRsvpStatus) {
    }
}
