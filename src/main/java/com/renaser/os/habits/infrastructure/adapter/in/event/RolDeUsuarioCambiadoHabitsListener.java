package com.renaser.os.habits.infrastructure.adapter.in.event;

import com.renaser.os.habits.application.ports.in.audiencia.AnotarCambioDeRolUseCase;
import com.renaser.os.users.api.RolDeUsuarioCambiadoEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Spec 001 (D-264): habits anota el cambio de rol de una persona para que, desde su día siguiente, reciba los hábitos
 * globales de su rol nuevo. Mismo patrón que el semáforo con {@code EstadoDeCuentaCambiadoEvent}: el módulo que
 * necesita la historia la anota al recibir el evento, porque users solo guarda el rol actual.
 *
 * <p>{@code occurredAt} y no el reloj de ahora: si el outbox reentrega el evento al día siguiente, el cambio sigue
 * siendo del día en que ocurrió. A diferencia de {@code PublicacionCreadaHabitoListener}, un fallo SÍ se propaga: sin
 * esta fila la persona recibiría los hábitos de su rol viejo, y el outbox reintenta (la escritura es idempotente).
 */
@Component
class RolDeUsuarioCambiadoHabitsListener {

    private final AnotarCambioDeRolUseCase anotarCambioDeRol;

    RolDeUsuarioCambiadoHabitsListener(AnotarCambioDeRolUseCase anotarCambioDeRol) {
        this.anotarCambioDeRol = anotarCambioDeRol;
    }

    @ApplicationModuleListener
    void on(RolDeUsuarioCambiadoEvent evento) {
        anotarCambioDeRol.anotar(evento.usuarioId(), evento.rolAnterior().name(), evento.rolNuevo().name(),
                evento.occurredAt());
    }
}
