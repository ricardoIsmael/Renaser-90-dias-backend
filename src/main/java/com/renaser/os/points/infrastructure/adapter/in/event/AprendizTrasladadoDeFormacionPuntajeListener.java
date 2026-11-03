package com.renaser.os.points.infrastructure.adapter.in.event;

import com.renaser.os.points.application.ports.in.puntaje.ReiniciarPuntajeAlCambiarDeFormacionUseCase;
import com.renaser.os.users.api.AprendizTrasladadoDeFormacionEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Pasar a un aprendiz de formación reinicia sus puntos y su racha (spec 022, Q8-A). */
@Component
class AprendizTrasladadoDeFormacionPuntajeListener {

    private final ReiniciarPuntajeAlCambiarDeFormacionUseCase reiniciar;

    AprendizTrasladadoDeFormacionPuntajeListener(ReiniciarPuntajeAlCambiarDeFormacionUseCase reiniciar) {
        this.reiniciar = reiniciar;
    }

    @ApplicationModuleListener
    void on(AprendizTrasladadoDeFormacionEvent event) {
        reiniciar.reiniciar(event.aprendizId(), event.desde(), event.hacia());
    }
}
