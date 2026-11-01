package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.rag.api.AnalisisListoEvent;
import com.renaser.os.rag.application.ports.out.analisis.SaveAnalisisDelPeriodoPort;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodo;
import com.renaser.os.rag.domain.model.analisis.EstadoDelAnalisis;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda el resultado de un intento y, si el análisis quedó listo, publica el aviso. Es su propia clase porque el guardado y el evento
 * tienen que ir en UNA transacción corta (el outbox solo entrega lo publicado dentro de una), mientras que la IA que los antecede no puede
 * ir dentro de ninguna (C-1).
 */
@Component
class RegistroDelAnalisis {

    private final SaveAnalisisDelPeriodoPort guardar;
    private final ApplicationEventPublisher publisher;

    RegistroDelAnalisis(SaveAnalisisDelPeriodoPort guardar, ApplicationEventPublisher publisher) {
        this.guardar = guardar;
        this.publisher = publisher;
    }

    /** @return {@code true} si quedó guardado; {@code false} si otro proceso ya había dejado el resultado de ese periodo */
    @Transactional
    boolean registrar(AnalisisDelPeriodo analisis) {
        boolean guardado = guardar.guardar(analisis);
        if (guardado && analisis.estado() == EstadoDelAnalisis.LISTO) {
            publisher.publishEvent(new AnalisisListoEvent(analisis.id().value(), analisis.participanteId().value(),
                    analisis.tipo().name(), analisis.inicio(), analisis.generadoEn()));
        }
        return guardado;
    }
}
