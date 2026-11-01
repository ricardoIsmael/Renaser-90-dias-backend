package com.renaser.os.leadership.infrastructure.adapter.in.event;

import com.renaser.os.leadership.application.ports.in.RegistrarLecturaDelDiagnosticoUseCase;
import com.renaser.os.onboarding.api.DiagnosticoConsultadoEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Lleva la lectura de un diagnóstico (spec 026) a {@code consultas_de_ficha}. {@code @ApplicationModuleListener}: después del commit de la lectura, en su
 * propia transacción, y el outbox lo reentrega si falla (el id de la fila sale del evento, así que reentregarlo no la duplica).
 */
@Component
class DiagnosticoConsultadoListener {

    private final RegistrarLecturaDelDiagnosticoUseCase registrar;

    DiagnosticoConsultadoListener(RegistrarLecturaDelDiagnosticoUseCase registrar) {
        this.registrar = registrar;
    }

    @ApplicationModuleListener
    void on(DiagnosticoConsultadoEvent lectura) {
        registrar.registrar(lectura);
    }
}
