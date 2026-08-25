package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionCelulaUseCase;
import com.renaser.os.community.api.CelulaCreadaEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Escucha {@link CelulaCreadaEvent}, publicado por `community` (`CelulaService.crear`) y
 * crea la conversacion CELULA correspondiente, de forma idempotente — protegida ademas
 * por {@code conversaciones.celula_id UNIQUE} (V1__baseline_renaser.sql:1281).
 *
 * <p>Solo crea la fila de la conversacion: agregar a los miembros de la celula como
 * participantes queda fuera de alcance de este encargo (`community` no publica hoy un
 * evento de "miembro agregado/quitado de celula" — ver docs/MODULO_CHAT.md §6).
 */
@Component
class CelulaCreadaChatListener {

    private final CrearConversacionCelulaUseCase crearConversacionCelulaUseCase;

    CelulaCreadaChatListener(CrearConversacionCelulaUseCase crearConversacionCelulaUseCase) {
        this.crearConversacionCelulaUseCase = crearConversacionCelulaUseCase;
    }

    @ApplicationModuleListener
    void on(CelulaCreadaEvent event) {
        crearConversacionCelulaUseCase.crearParaCelula(event.celulaId());
    }
}
