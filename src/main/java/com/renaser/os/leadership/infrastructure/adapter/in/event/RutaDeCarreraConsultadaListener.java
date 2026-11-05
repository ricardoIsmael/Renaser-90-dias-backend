package com.renaser.os.leadership.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.RutaDeCarreraConsultadaEvent;
import com.renaser.os.leadership.application.ports.in.RegistrarLecturaDeRutaUseCase;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Lleva la lectura de la ruta de una carrera (spec 042) a {@code consultas_de_ficha}. {@code @ApplicationModuleListener}: después del
 * commit de la lectura, en su propia transacción, y el outbox lo reentrega si falla (el id de la fila sale del evento).
 */
@Component
class RutaDeCarreraConsultadaListener {

    private final RegistrarLecturaDeRutaUseCase registrar;

    RutaDeCarreraConsultadaListener(RegistrarLecturaDeRutaUseCase registrar) {
        this.registrar = registrar;
    }

    @ApplicationModuleListener
    void on(RutaDeCarreraConsultadaEvent lectura) {
        registrar.registrar(lectura);
    }
}
