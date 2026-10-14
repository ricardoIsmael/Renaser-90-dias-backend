package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.plataforma.AvisarCircuitoDeIaUseCase;
import com.renaser.os.notifications.application.ports.in.plataforma.AvisarCircuitoDeIaUseCase.CambioDeCircuito;
import com.renaser.os.rag.api.CircuitoDeIaCambiadoEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * El circuito de SER se abrió o se cerró (spec 002, FR-016): push a Soporte TI, como máximo 4 por hora por parte. La
 * lógica (a quién, el tope, el texto) está en {@link AvisarCircuitoDeIaUseCase}; acá solo se traduce el evento.
 */
@Component
class CircuitoDeIaCambiadoNotificationListener {

    private final AvisarCircuitoDeIaUseCase avisar;

    CircuitoDeIaCambiadoNotificationListener(AvisarCircuitoDeIaUseCase avisar) {
        this.avisar = avisar;
    }

    @ApplicationModuleListener
    void on(CircuitoDeIaCambiadoEvent evento) {
        avisar.avisar(new CambioDeCircuito(evento.parte(), evento.estado(), evento.causa(), evento.occurredAt()));
    }
}
